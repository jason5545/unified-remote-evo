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
        const val HEARTBEAT_INTERVAL = 5000L  // 5秒（快速偵測伺服器無回應）
        const val HEARTBEAT_TIMEOUT = 2000L   // 2秒容錯時間
        val RECONNECT_DELAYS = longArrayOf(500, 1000, 2000)
        const val MAX_RECONNECT_ATTEMPTS = 10
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

    suspend fun connect() = withContext(Dispatchers.IO) {
        _connectionState.value = UnifiedConnectionState.Connecting("正在連線到 $host:$port...")
        log("開始連線 $host:$port (timeout=${CONNECTION_TIMEOUT}ms)", ConnectionLogger.LogLevel.INFO)

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
        log("斷開連線", ConnectionLogger.LogLevel.INFO)
        isConnected = false
        handshakeCompleted = false
        serverSession = null

        heartbeatJob?.cancel()
        heartbeatJob = null

        receiveJob?.cancel()
        receiveJob = null

        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            log("關閉 Socket 錯誤: ${e.message}", ConnectionLogger.LogLevel.WARNING)
        }

        input = null
        output = null
        socket = null

        _connectionState.value = UnifiedConnectionState.Disconnected
        onDisconnected?.invoke()
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
            log("開始接收封包迴圈", ConnectionLogger.LogLevel.DEBUG)

            while (isActive && isConnected) {
                try {
                    log("等待讀取封包長度...", ConnectionLogger.LogLevel.DEBUG)
                    val length = input?.readInt() ?: break
                    log("收到封包長度: $length bytes", ConnectionLogger.LogLevel.DEBUG)

                    if (length <= 0 || length > BUFFER_SIZE) {
                        log("無效的封包長度: $length", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    val encrypted = input?.readByte() ?: 0
                    log("加密標記: $encrypted", ConnectionLogger.LogLevel.DEBUG)

                    val dataLength = length - 1  // 扣除加密標記
                    val data = ByteArray(dataLength)
                    input?.readFully(data) ?: break
                    log("讀取封包資料完成: $dataLength bytes", ConnectionLogger.LogLevel.DEBUG)

                    val packet = PacketSerializer.deserialize(data)
                    if (packet != null) {
                        log("反序列化成功: Action=${packet.action}, KeepAlive=${packet.keepAlive}", ConnectionLogger.LogLevel.DEBUG)
                        if (packet.keepAlive == true) {
                            lastHeartbeatReceivedTime = System.currentTimeMillis()
                            log("收到心跳回應", ConnectionLogger.LogLevel.INFO)
                        } else {
                            handlePacket(packet)
                        }
                    } else {
                        log("反序列化失敗", ConnectionLogger.LogLevel.ERROR)
                    }

                    delay(5)

                } catch (e: EOFException) {
                    log("連線已關閉", ConnectionLogger.LogLevel.WARNING)
                    break
                } catch (e: Exception) {
                    log("接收錯誤: ${e.message}", ConnectionLogger.LogLevel.ERROR)
                    break
                }
            }

            if (isConnected) {
                disconnect()
                scheduleReconnect()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = launch {
            // 初始化：首次啟動時設定為當前時間
            lastHeartbeatReceivedTime = System.currentTimeMillis()

            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL)

                val now = System.currentTimeMillis()

                // 檢查心跳超時：如果上次收到心跳回應的時間距離現在超過閾值
                // 閾值 = HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT（考慮正常延遲）
                val timeSinceLastReceived = now - lastHeartbeatReceivedTime
                if (lastHeartbeatSentTime > 0 && timeSinceLastReceived > (HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT)) {
                    log("心跳超時：已 ${timeSinceLastReceived}ms 未收到伺服器回應", ConnectionLogger.LogLevel.ERROR)
                    disconnect()
                    scheduleReconnect()
                    break
                }

                // 傳送心跳封包
                val heartbeat = Packet(keepAlive = true)
                send(heartbeat)
                lastHeartbeatSentTime = now

                log("傳送心跳封包（上次收到回應：${timeSinceLastReceived}ms 前）", ConnectionLogger.LogLevel.DEBUG)
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            log("達到最大重連次數，停止重連", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = UnifiedConnectionState.Error("連線失敗：已達最大重連次數")
            return
        }

        val delayIndex = reconnectAttempt.coerceAtMost(RECONNECT_DELAYS.size - 1)
        val delay = RECONNECT_DELAYS[delayIndex]

        log("將在 ${delay}ms 後重連（第 ${reconnectAttempt + 1} 次）", ConnectionLogger.LogLevel.WARNING)
        _connectionState.value = UnifiedConnectionState.Reconnecting(
            attempt = reconnectAttempt + 1,
            maxAttempts = MAX_RECONNECT_ATTEMPTS,
            nextDelayMs = delay
        )

        launch {
            delay(delay)
            reconnectAttempt++
            connect()
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
