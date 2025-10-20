package com.unifiedremote.evo.network

import com.unifiedremote.evo.data.Packet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * 連線管理器（針對 Tailscale 最佳化）
 */
class ConnectionManager(
    private val host: String,
    private val port: Int = 9512
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // 統一連線狀態管理
    private val _connectionState = MutableStateFlow<UnifiedConnectionState>(UnifiedConnectionState.Disconnected)
    val connectionState: StateFlow<UnifiedConnectionState> = _connectionState.asStateFlow()

    companion object {
        const val CONNECTION_TIMEOUT = 10000  // 10秒（Tailscale 可能較慢）
        const val SOCKET_TIMEOUT = 30000      // 30秒讀取 timeout
        const val BUFFER_SIZE = 16384
        const val HEARTBEAT_INTERVAL = 15000L  // 15秒（針對 Tailscale 最佳化）
        const val HEARTBEAT_TIMEOUT = 10000L   // 10秒容錯時間（考慮網路延遲）
        // 指數退避重連策略：2s → 5s → 10s → 20s → 30s → 60s
        val RECONNECT_DELAYS = longArrayOf(2000, 5000, 10000, 20000, 30000, 60000)
        const val MAX_RECONNECT_ATTEMPTS = 30  // 增加最大重試次數（約 30 分鐘）
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var isConnected = false
    private var reconnectAttempt = 0
    private var deviceId: String = UUID.randomUUID().toString()

    private var heartbeatJob: Job? = null
    private var lastHeartbeatSentTime = 0L       // 上次傳送心跳的時間
    private var lastHeartbeatReceivedTime = 0L   // 上次收到心跳回應的時間
    private var receiveJob: Job? = null

    // 握手狀態
    private var handshakeCompleted = false
    private var serverSession: String? = null
    private var serverCapabilities: com.unifiedremote.evo.data.Capabilities? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onPacketReceived: ((Packet) -> Unit)? = null
    var onLog: ((String, ConnectionLogger.LogLevel) -> Unit)? = null

    /**
     * 手動重連（重置重連計數器）
     */
    suspend fun reconnect() {
        log("手動重連：重置重連計數器", ConnectionLogger.LogLevel.INFO)
        disconnect()
        reconnectAttempt = 0  // 重置計數器
        connect()
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        _connectionState.value = UnifiedConnectionState.Connecting("正在連線到 $host:$port...")
        log("開始連線 $host:$port (timeout=${CONNECTION_TIMEOUT}ms, 嘗試=${reconnectAttempt + 1})", ConnectionLogger.LogLevel.INFO)

        try {
            log("建立 Socket...", ConnectionLogger.LogLevel.DEBUG)
            socket = Socket().apply {
                tcpNoDelay = true
                soTimeout = SOCKET_TIMEOUT
            }

            log("正在連線到 $host:$port...", ConnectionLogger.LogLevel.DEBUG)
            val address = InetSocketAddress(host, port)
            log("解析位址完成: ${address.address?.hostAddress}", ConnectionLogger.LogLevel.DEBUG)

            socket?.connect(address, CONNECTION_TIMEOUT)
            log("TCP 連線建立成功", ConnectionLogger.LogLevel.DEBUG)

            input = DataInputStream(BufferedInputStream(socket?.getInputStream(), BUFFER_SIZE))
            output = DataOutputStream(BufferedOutputStream(socket?.getOutputStream(), BUFFER_SIZE))
            log("I/O 串流建立完成", ConnectionLogger.LogLevel.DEBUG)

            isConnected = true
            reconnectAttempt = 0

            log("連線成功", ConnectionLogger.LogLevel.INFO)
            _connectionState.value = UnifiedConnectionState.Connected("$host:$port")
            withContext(Dispatchers.Main) {
                onConnected?.invoke()
            }

            startReceiving()
            startHeartbeat()

            // 立即傳送客戶端握手封包
            sendClientHandshake()

        } catch (e: java.net.UnknownHostException) {
            log("連線失敗: 無法解析主機名稱 $host", ConnectionLogger.LogLevel.ERROR)
            disconnect()
            scheduleReconnect()
        } catch (e: java.net.SocketTimeoutException) {
            log("連線失敗: 連線逾時 (${CONNECTION_TIMEOUT}ms)", ConnectionLogger.LogLevel.ERROR)
            disconnect()
            scheduleReconnect()
        } catch (e: java.net.ConnectException) {
            log("連線失敗: 連線被拒絕 - 請確認伺服器已啟動", ConnectionLogger.LogLevel.ERROR)
            disconnect()
            scheduleReconnect()
        } catch (e: Exception) {
            log("連線失敗: ${e.javaClass.simpleName} - ${e.message}", ConnectionLogger.LogLevel.ERROR)
            e.printStackTrace()
            disconnect()
            scheduleReconnect()
        }
    }

    fun disconnect() {
        log("中斷連線", ConnectionLogger.LogLevel.INFO)

        // 立即設為未連線，避免重複觸發
        val wasConnected = isConnected
        isConnected = false
        handshakeCompleted = false
        serverSession = null

        // 取消所有背景任務
        heartbeatJob?.cancel()
        heartbeatJob = null

        receiveJob?.cancel()
        receiveJob = null

        // 依序關閉資源（順序很重要）
        try {
            // 1. 先關閉輸出串流（確保資料送出）
            output?.let {
                try {
                    it.flush()
                    it.close()
                    log("輸出串流已關閉", ConnectionLogger.LogLevel.DEBUG)
                } catch (e: Exception) {
                    log("關閉輸出串流錯誤: ${e.message}", ConnectionLogger.LogLevel.WARNING)
                }
            }

            // 2. 關閉輸入串流
            input?.let {
                try {
                    it.close()
                    log("輸入串流已關閉", ConnectionLogger.LogLevel.DEBUG)
                } catch (e: Exception) {
                    log("關閉輸入串流錯誤: ${e.message}", ConnectionLogger.LogLevel.WARNING)
                }
            }

            // 3. 最後關閉 Socket
            socket?.let {
                try {
                    if (!it.isClosed) {
                        it.close()
                        log("Socket 已關閉", ConnectionLogger.LogLevel.DEBUG)
                    }
                } catch (e: Exception) {
                    log("關閉 Socket 錯誤: ${e.message}", ConnectionLogger.LogLevel.WARNING)
                }
            }
        } finally {
            // 確保清空參考
            input = null
            output = null
            socket = null
        }

        _connectionState.value = UnifiedConnectionState.Disconnected

        // 只有真的斷線時才觸發回調（避免重複觸發）
        if (wasConnected) {
            onDisconnected?.invoke()
        }
    }

    suspend fun send(packet: Packet) = withContext(Dispatchers.IO) {
        if (!isConnected) {
            log("未連線，無法傳送封包 (isConnected=$isConnected, socket=${socket != null}, output=${output != null})", ConnectionLogger.LogLevel.WARNING)
            return@withContext
        }

        try {
            if (packet.source == null) {
                packet.source = deviceId
            }

            val data = PacketSerializer.serialize(packet)

            // 重要：長度 = 資料長度 + 1（加密標記）
            output?.writeInt(data.size + 1)
            output?.writeByte(0)  // 0 = 未加密
            output?.write(data)
            output?.flush()

            val actionInfo = packet.action?.let { "Action=$it" } ?: packet.run?.name ?: "keepalive"
            log("傳送封包: $actionInfo (${data.size} bytes)", ConnectionLogger.LogLevel.DEBUG)

        } catch (e: Exception) {
            log("傳送封包失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
            disconnect()
            scheduleReconnect()
        }
    }

    private fun startReceiving() {
        receiveJob = launch {
            log("接收迴圈啟動", ConnectionLogger.LogLevel.INFO)
            var packetCount = 0

            while (isActive && isConnected) {
                try {
                    // 讀取封包長度（4 bytes）
                    val length = input?.readInt()
                    if (length == null) {
                        log("讀取封包長度失敗：串流已關閉", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    if (length <= 0 || length > BUFFER_SIZE) {
                        log("無效的封包長度: $length (範圍: 1-$BUFFER_SIZE)", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    // 讀取加密標記（1 byte）
                    val encrypted = input?.readByte()
                    if (encrypted == null) {
                        log("讀取加密標記失敗：串流已關閉", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    // 讀取封包資料
                    val dataLength = length - 1  // 扣除加密標記
                    val data = ByteArray(dataLength)
                    val readSuccess = try {
                        input?.readFully(data)
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (!readSuccess) {
                        log("讀取封包資料失敗：串流已關閉", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    packetCount++
                    log("收到封包 #$packetCount: ${dataLength} bytes (加密=$encrypted)", ConnectionLogger.LogLevel.DEBUG)

                    // 反序列化封包
                    val packet = PacketSerializer.deserialize(data)
                    if (packet != null) {
                        if (packet.keepAlive == true) {
                            lastHeartbeatReceivedTime = System.currentTimeMillis()
                            log("收到心跳回應 #$packetCount", ConnectionLogger.LogLevel.DEBUG)
                        } else {
                            log("收到資料封包 #$packetCount: Action=${packet.action}", ConnectionLogger.LogLevel.DEBUG)
                            handlePacket(packet)
                        }
                    } else {
                        log("反序列化失敗：封包 #$packetCount 無效", ConnectionLogger.LogLevel.ERROR)
                    }

                    delay(5)  // 避免過度佔用 CPU

                } catch (e: EOFException) {
                    log("伺服器中斷連線（已接收 $packetCount 個封包）", ConnectionLogger.LogLevel.WARNING)
                    break
                } catch (e: java.net.SocketTimeoutException) {
                    log("接收逾時：超過 ${SOCKET_TIMEOUT}ms 無資料（已接收 $packetCount 個封包）", ConnectionLogger.LogLevel.ERROR)
                    break
                } catch (e: java.net.SocketException) {
                    log("Socket 異常: ${e.message}（已接收 $packetCount 個封包）", ConnectionLogger.LogLevel.ERROR)
                    break
                } catch (e: Exception) {
                    log("接收錯誤: ${e.javaClass.simpleName} - ${e.message}（已接收 $packetCount 個封包）", ConnectionLogger.LogLevel.ERROR)
                    e.printStackTrace()
                    break
                }
            }

            log("接收迴圈結束（isActive=$isActive, isConnected=$isConnected, 共接收 $packetCount 個封包）", ConnectionLogger.LogLevel.INFO)

            // 只有在仍處於連線狀態時才觸發重連（避免手動斷線時重連）
            if (isConnected) {
                log("偵測到異常斷線，排程重連...", ConnectionLogger.LogLevel.WARNING)
                disconnect()
                scheduleReconnect()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = launch {
            // 初始化：首次啟動時設定為當前時間
            lastHeartbeatReceivedTime = System.currentTimeMillis()
            lastHeartbeatSentTime = 0L

            log("心跳機制啟動（間隔=${HEARTBEAT_INTERVAL}ms, 容錯=${HEARTBEAT_TIMEOUT}ms）", ConnectionLogger.LogLevel.INFO)

            while (isActive && isConnected) {
                val now = System.currentTimeMillis()
                val timeSinceLastReceived = now - lastHeartbeatReceivedTime

                // 先檢查心跳逾時（在傳送前檢查，避免延遲一個週期）
                if (lastHeartbeatSentTime > 0 && timeSinceLastReceived > (HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT)) {
                    log("心跳逾時：已 ${timeSinceLastReceived}ms 未收到伺服器回應（超過閾值 ${HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT}ms）", ConnectionLogger.LogLevel.ERROR)
                    disconnect()
                    scheduleReconnect()
                    break
                }

                // 傳送心跳封包
                try {
                    val heartbeat = Packet(keepAlive = true)
                    send(heartbeat)
                    lastHeartbeatSentTime = now

                    log("傳送心跳封包（上次收到回應：${timeSinceLastReceived}ms 前）", ConnectionLogger.LogLevel.DEBUG)
                } catch (e: Exception) {
                    log("傳送心跳失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
                    disconnect()
                    scheduleReconnect()
                    break
                }

                // 等待下一個週期
                delay(HEARTBEAT_INTERVAL)
            }

            log("心跳機制已停止", ConnectionLogger.LogLevel.INFO)
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            log("達到最大重連次數 ($MAX_RECONNECT_ATTEMPTS)，停止自動重連", ConnectionLogger.LogLevel.ERROR)
            log("提示：請檢查網路連線或伺服器狀態，可手動觸發重連", ConnectionLogger.LogLevel.INFO)
            _connectionState.value = UnifiedConnectionState.Error("連線失敗：已達最大重連次數（${MAX_RECONNECT_ATTEMPTS} 次）")
            return
        }

        // 使用指數退避策略
        val delayIndex = reconnectAttempt.coerceAtMost(RECONNECT_DELAYS.size - 1)
        val delay = RECONNECT_DELAYS[delayIndex]

        val delaySeconds = delay / 1000.0
        log("排程重連：第 ${reconnectAttempt + 1}/$MAX_RECONNECT_ATTEMPTS 次，將在 ${delaySeconds}秒 後重試", ConnectionLogger.LogLevel.WARNING)

        _connectionState.value = UnifiedConnectionState.Reconnecting(
            attempt = reconnectAttempt + 1,
            maxAttempts = MAX_RECONNECT_ATTEMPTS,
            nextDelayMs = delay
        )

        launch {
            delay(delay)

            // 重連前檢查是否仍需要重連（避免重複觸發）
            if (!isConnected && reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempt++
                log("開始第 ${reconnectAttempt} 次重連嘗試...", ConnectionLogger.LogLevel.INFO)
                connect()
            } else {
                log("重連已取消（isConnected=$isConnected, attempt=$reconnectAttempt）", ConnectionLogger.LogLevel.DEBUG)
            }
        }
    }

    private suspend fun handlePacket(packet: Packet) {
        val action = packet.action?.toInt() ?: return

        log("收到封包: Action=$action", ConnectionLogger.LogLevel.INFO)

        if (!handshakeCompleted) {
            when (action) {
                0 -> handleInitialHandshake(packet)  // 伺服器初始握手
                1 -> handleAuthResponse(packet)       // 認證回應
            }
        } else {
            // 握手完成後，處理一般封包
            log("收到封包: ${packet.run?.name}", ConnectionLogger.LogLevel.INFO)
            withContext(Dispatchers.Main) {
                onPacketReceived?.invoke(packet)
            }
        }
    }

    private suspend fun handleInitialHandshake(packet: Packet) {
        log("處理初始握手", ConnectionLogger.LogLevel.INFO)

        serverSession = packet.session
        serverCapabilities = packet.capabilities  // 儲存伺服器 Capabilities
        log("Session: $serverSession", ConnectionLogger.LogLevel.DEBUG)
        log("Platform: ${packet.platform}", ConnectionLogger.LogLevel.DEBUG)
        log("Version: ${packet.version}", ConnectionLogger.LogLevel.DEBUG)
        log("Server Capabilities: ${packet.capabilities}", ConnectionLogger.LogLevel.DEBUG)

        // 回應認證封包 (Action=1, Request=1)
        if (packet.version == 1) {
            val authPacket = Packet(
                action = 1,
                request = 1,
                password = "",  // 無密碼
                capabilities = if (packet.capabilities != null) {
                    // 只有伺服器有 Capabilities 時才傳送客戶端 Capabilities
                    log("伺服器支援 Capabilities，傳送客戶端能力", ConnectionLogger.LogLevel.DEBUG)
                    com.unifiedremote.evo.data.Capabilities(
                        fast = false,  // ⚠️ Action=1 時 fast 仍為 false
                        actions = true,
                        sync = true,
                        grid = true,
                        loading = true,
                        encryption2 = true
                        // ⚠️ 不設定 clientNonce（原版 APP 從未設定）
                    )
                } else {
                    log("伺服器不支援 Capabilities，不傳送", ConnectionLogger.LogLevel.DEBUG)
                    null
                }
            )
            send(authPacket)
            log("已傳送認證封包 (Action=1, Capabilities=${authPacket.capabilities != null})", ConnectionLogger.LogLevel.INFO)
        }
    }

    private suspend fun handleAuthResponse(packet: Packet) {
        log("處理認證回應: Security=${packet.security}", ConnectionLogger.LogLevel.INFO)

        // 檢查伺服器是否支援 Fast 能力
        if (serverCapabilities?.fast != null) {
            log("伺服器支援 Fast 能力，傳送 Capabilities 協商 (Action=11)", ConnectionLogger.LogLevel.INFO)

            val handshakePacket = Packet(
                action = 11,
                request = 11,
                session = serverSession,
                capabilities = com.unifiedremote.evo.data.Capabilities(
                    fast = true  // ⚠️ Action=11 時才設為 true
                )
            )
            send(handshakePacket)
            log("已傳送 Capabilities 協商封包 (Fast=true)", ConnectionLogger.LogLevel.INFO)
        } else {
            log("伺服器不支援 Fast 能力，跳過 Action=11", ConnectionLogger.LogLevel.INFO)
        }

        handshakeCompleted = true
        log("握手完成", ConnectionLogger.LogLevel.INFO)

        // 載入 Basic Input Remote (Action=3, Request=3)
        loadRemote("Relmtech.Basic Input")
    }

    private suspend fun loadRemote(remoteId: String) {
        log("載入 Remote: $remoteId", ConnectionLogger.LogLevel.INFO)

        val loadPacket = Packet(
            action = 3,
            request = 3,  // 必須與 action 相同
            id = remoteId,
            session = serverSession
        )
        send(loadPacket)
        log("已傳送載入 Remote 封包 (Action=3, Request=3, ID=$remoteId)", ConnectionLogger.LogLevel.INFO)
    }

    private suspend fun sendClientHandshake() {
        log("傳送客戶端握手封包", ConnectionLogger.LogLevel.INFO)

        val clientNonce = UUID.randomUUID().toString()

        val handshake = Packet(
            action = 0,
            request = 0,
            source = deviceId,
            platform = "android",
            version = 10,
            password = clientNonce
            // ⚠️ Action=0 時不傳送 Capabilities！
        )

        send(handshake)
        log("客戶端握手封包已傳送 (Action=0, Request=0, Version=10, 無 Capabilities)", ConnectionLogger.LogLevel.INFO)
    }

    private fun log(message: String, level: ConnectionLogger.LogLevel) {
        onLog?.invoke(message, level)
    }

    fun close() {
        disconnect()
        job.cancel()
    }
}
