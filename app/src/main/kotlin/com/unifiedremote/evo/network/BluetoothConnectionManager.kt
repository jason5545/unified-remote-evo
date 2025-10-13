package com.unifiedremote.evo.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.unifiedremote.evo.data.Packet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * 藍牙連線管理器
 * 實作與 Unified Remote Server 的藍牙通訊
 */
@SuppressLint("MissingPermission")
class BluetoothConnectionManager(
    private val device: BluetoothDevice
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // 統一連線狀態管理
    private val _connectionState = MutableStateFlow<UnifiedConnectionState>(UnifiedConnectionState.Disconnected)
    val connectionState: StateFlow<UnifiedConnectionState> = _connectionState.asStateFlow()

    companion object {
        // Unified Remote 藍牙服務 UUID
        val SERVICE_UUID: UUID = UUID.fromString("B4406055-BAC6-4426-BB64-9D390B668328")

        const val BUFFER_SIZE = 2048              // 2KB（原版 1KB，提升效能）
        const val CONNECTION_TIMEOUT = 20000L     // 20秒 timeout
        const val HEARTBEAT_INTERVAL = 15000L     // 15秒心跳（與 TCP 一致）
        const val HEARTBEAT_TIMEOUT = 5000L       // 5秒無回應視為斷線
        const val AUTO_RECONNECT_DELAY = 2000L    // 2秒後自動重連
        const val MAX_RECONNECT_ATTEMPTS = 5      // 最多重連 5 次

        /**
         * 取得已配對的藍牙裝置列表
         */
        @SuppressLint("MissingPermission")
        fun getPairedDevices(): List<BluetoothDevice> {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()

            if (!adapter.isEnabled) {
                return emptyList()
            }

            return try {
                adapter.bondedDevices.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * 檢查藍牙是否可用
         */
        fun isBluetoothAvailable(): Boolean {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            return adapter != null && adapter.isEnabled
        }
    }

    private var socket: BluetoothSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var isConnected = false
    private var reconnectAttempt = 0
    private var currentStrategy = 0  // 0-3: 四種連線策略
    private var deviceId: String = UUID.randomUUID().toString()

    private var heartbeatJob: Job? = null
    private var lastHeartbeatTime = 0L
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
     * 建立藍牙 Socket（四種策略）
     */
    private fun createSocket(device: BluetoothDevice, strategy: Int): BluetoothSocket? {
        return try {
            when (strategy) {
                // 策略 0: Insecure RFCOMM (預設，最常成功)
                0 -> {
                    log("使用策略 0: createInsecureRfcommSocketToServiceRecord", ConnectionLogger.LogLevel.DEBUG)
                    device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
                }
                // 策略 1: Secure RFCOMM
                1 -> {
                    log("使用策略 1: createRfcommSocketToServiceRecord", ConnectionLogger.LogLevel.DEBUG)
                    device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                }
                // 策略 2: Insecure RFCOMM Channel 1 (反射)
                2 -> {
                    log("使用策略 2: createInsecureRfcommSocket(1) 反射方法", ConnectionLogger.LogLevel.DEBUG)
                    val method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    method.invoke(device, 1) as BluetoothSocket
                }
                // 策略 3: RFCOMM Channel 1 (反射)
                3 -> {
                    log("使用策略 3: createRfcommSocket(1) 反射方法", ConnectionLogger.LogLevel.DEBUG)
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    method.invoke(device, 1) as BluetoothSocket
                }
                else -> null
            }
        } catch (e: Exception) {
            log("策略 $strategy 建立 Socket 失敗: ${e.message}", ConnectionLogger.LogLevel.WARNING)
            null
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        _connectionState.value = UnifiedConnectionState.Connecting("正在連線到 ${device.name}...")
        log("開始藍牙連線: ${device.name} (${device.address})", ConnectionLogger.LogLevel.INFO)

        try {
            // 建立 Socket
            socket = createSocket(device, currentStrategy)

            if (socket == null) {
                throw Exception("無法建立藍牙 Socket（策略 $currentStrategy）")
            }

            log("Socket 已建立，正在連線...", ConnectionLogger.LogLevel.DEBUG)

            // 使用 withTimeout 實作連線 timeout
            withTimeout(CONNECTION_TIMEOUT) {
                socket?.connect()
            }

            log("藍牙連線建立成功", ConnectionLogger.LogLevel.INFO)

            // 建立 I/O 串流
            input = DataInputStream(BufferedInputStream(socket?.inputStream, BUFFER_SIZE))
            output = DataOutputStream(BufferedOutputStream(socket?.outputStream, BUFFER_SIZE))
            log("I/O 串流建立完成", ConnectionLogger.LogLevel.DEBUG)

            isConnected = true
            reconnectAttempt = 0
            currentStrategy = 0  // 重置策略

            log("藍牙連線成功", ConnectionLogger.LogLevel.INFO)
            _connectionState.value = UnifiedConnectionState.Connected(device.name ?: device.address)
            withContext(Dispatchers.Main) {
                onConnected?.invoke()
            }

            startReceiving()
            startHeartbeat()

            // 立即發送客戶端握手封包
            sendClientHandshake()

        } catch (e: TimeoutCancellationException) {
            log("連線逾時（${CONNECTION_TIMEOUT}ms）", ConnectionLogger.LogLevel.ERROR)
            tryNextStrategy()
        } catch (e: IOException) {
            log("連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
            tryNextStrategy()
        } catch (e: Exception) {
            log("連線失敗: ${e.javaClass.simpleName} - ${e.message}", ConnectionLogger.LogLevel.ERROR)
            e.printStackTrace()
            tryNextStrategy()
        }
    }

    /**
     * 嘗試下一個連線策略
     */
    private suspend fun tryNextStrategy() {
        disconnect()

        currentStrategy++

        if (currentStrategy > 3) {
            // 所有策略都失敗，重置並重連
            currentStrategy = 0
            scheduleReconnect()
        } else {
            // 立即嘗試下一個策略
            log("嘗試下一個連線策略...", ConnectionLogger.LogLevel.INFO)
            delay(500)  // 短暫延遲
            connect()
        }
    }

    fun disconnect() {
        log("斷開藍牙連線", ConnectionLogger.LogLevel.INFO)
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
            log("未連線，無法發送封包", ConnectionLogger.LogLevel.WARNING)
            return@withContext
        }

        try {
            if (packet.source == null) {
                packet.source = deviceId
            }

            val data = PacketSerializer.serialize(packet)

            // 封包格式與 TCP 相同
            output?.writeInt(data.size + 1)
            output?.writeByte(0)  // 0 = 未加密
            output?.write(data)
            output?.flush()

            val actionInfo = packet.action?.let { "Action=$it" } ?: packet.run?.name ?: "keepalive"
            log("發送封包: $actionInfo (${data.size} bytes)", ConnectionLogger.LogLevel.DEBUG)

        } catch (e: Exception) {
            log("發送封包失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
            disconnect()
            scheduleReconnect()
        }
    }

    private fun startReceiving() {
        receiveJob = launch {
            log("開始接收封包迴圈", ConnectionLogger.LogLevel.DEBUG)

            while (isActive && isConnected) {
                try {
                    val length = input?.readInt() ?: break
                    log("收到封包長度: $length bytes", ConnectionLogger.LogLevel.DEBUG)

                    if (length <= 0 || length > BUFFER_SIZE) {
                        log("無效的封包長度: $length", ConnectionLogger.LogLevel.ERROR)
                        break
                    }

                    val encrypted = input?.readByte() ?: 0
                    log("加密標記: $encrypted", ConnectionLogger.LogLevel.DEBUG)

                    val dataLength = length - 1
                    val data = ByteArray(dataLength)
                    input?.readFully(data) ?: break
                    log("讀取封包資料完成: $dataLength bytes", ConnectionLogger.LogLevel.DEBUG)

                    val packet = PacketSerializer.deserialize(data)
                    if (packet != null) {
                        log("反序列化成功: Action=${packet.action}, KeepAlive=${packet.keepAlive}", ConnectionLogger.LogLevel.DEBUG)
                        if (packet.keepAlive == true) {
                            lastHeartbeatTime = System.currentTimeMillis()
                            log("收到心跳回應", ConnectionLogger.LogLevel.INFO)
                        } else {
                            handlePacket(packet)
                        }
                    } else {
                        log("反序列化失敗", ConnectionLogger.LogLevel.ERROR)
                    }

                    // 藍牙不需要延遲（原版沒有）

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
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL)

                val now = System.currentTimeMillis()
                if (lastHeartbeatTime > 0 && (now - lastHeartbeatTime) > HEARTBEAT_TIMEOUT) {
                    log("心跳超時", ConnectionLogger.LogLevel.ERROR)
                    disconnect()
                    scheduleReconnect()
                    break
                }

                val heartbeat = Packet(keepAlive = true)
                send(heartbeat)

                lastHeartbeatTime = now
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            log("達到最大重連次數，停止重連", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = UnifiedConnectionState.Error("連線失敗：已達最大重連次數")
            return
        }

        log("將在 ${AUTO_RECONNECT_DELAY}ms 後重連（第 ${reconnectAttempt + 1} 次）", ConnectionLogger.LogLevel.WARNING)
        _connectionState.value = UnifiedConnectionState.Reconnecting(
            attempt = reconnectAttempt + 1,
            maxAttempts = MAX_RECONNECT_ATTEMPTS,
            nextDelayMs = AUTO_RECONNECT_DELAY
        )

        launch {
            delay(AUTO_RECONNECT_DELAY)
            reconnectAttempt++
            connect()
        }
    }

    private suspend fun handlePacket(packet: Packet) {
        val action = packet.action?.toInt() ?: return

        log("收到封包: Action=$action", ConnectionLogger.LogLevel.INFO)

        if (!handshakeCompleted) {
            when (action) {
                0 -> handleInitialHandshake(packet)
                1 -> handleAuthResponse(packet)
            }
        } else {
            log("收到封包: ${packet.run?.name}", ConnectionLogger.LogLevel.INFO)
            withContext(Dispatchers.Main) {
                onPacketReceived?.invoke(packet)
            }
        }
    }

    private suspend fun handleInitialHandshake(packet: Packet) {
        log("處理初始握手", ConnectionLogger.LogLevel.INFO)

        serverSession = packet.session
        serverCapabilities = packet.capabilities
        log("Session: $serverSession", ConnectionLogger.LogLevel.DEBUG)
        log("Platform: ${packet.platform}", ConnectionLogger.LogLevel.DEBUG)
        log("Version: ${packet.version}", ConnectionLogger.LogLevel.DEBUG)

        if (packet.version == 1) {
            val authPacket = Packet(
                action = 1,
                request = 1,
                password = "",
                capabilities = if (packet.capabilities != null) {
                    com.unifiedremote.evo.data.Capabilities(
                        fast = false,
                        actions = true,
                        sync = true,
                        grid = true,
                        loading = true,
                        encryption2 = true
                    )
                } else null
            )
            send(authPacket)
            log("已發送認證封包", ConnectionLogger.LogLevel.INFO)
        }
    }

    private suspend fun handleAuthResponse(packet: Packet) {
        log("處理認證回應: Security=${packet.security}", ConnectionLogger.LogLevel.INFO)

        if (serverCapabilities?.fast != null) {
            log("伺服器支援 Fast 能力，發送 Capabilities 協商 (Action=11)", ConnectionLogger.LogLevel.INFO)

            val handshakePacket = Packet(
                action = 11,
                request = 11,
                session = serverSession,
                capabilities = com.unifiedremote.evo.data.Capabilities(
                    fast = true
                )
            )
            send(handshakePacket)
        }

        handshakeCompleted = true
        log("握手完成", ConnectionLogger.LogLevel.INFO)

        // 載入 Basic Input Remote
        loadRemote("Relmtech.Basic Input")
    }

    private suspend fun loadRemote(remoteId: String) {
        log("載入 Remote: $remoteId", ConnectionLogger.LogLevel.INFO)

        val loadPacket = Packet(
            action = 3,
            request = 3,
            id = remoteId,
            session = serverSession
        )
        send(loadPacket)
    }

    private suspend fun sendClientHandshake() {
        log("發送客戶端握手封包", ConnectionLogger.LogLevel.INFO)

        val clientNonce = UUID.randomUUID().toString()

        val handshake = Packet(
            action = 0,
            request = 0,
            source = deviceId,
            platform = "android",
            version = 10,
            password = clientNonce
        )

        send(handshake)
        log("客戶端握手封包已發送", ConnectionLogger.LogLevel.INFO)
    }

    private fun log(message: String, level: ConnectionLogger.LogLevel) {
        onLog?.invoke("[Bluetooth] $message", level)
    }

    fun close() {
        disconnect()
        job.cancel()
    }
}
