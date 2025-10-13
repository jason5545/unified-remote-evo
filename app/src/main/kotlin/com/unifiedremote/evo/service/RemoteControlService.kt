package com.unifiedremote.evo.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unifiedremote.evo.MainActivity
import com.unifiedremote.evo.R
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.ConnectionType
import com.unifiedremote.evo.network.UnifiedConnectionManager
import com.unifiedremote.evo.network.ble.BleManager
import com.unifiedremote.evo.network.ble.BleConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 遙控器背景服務
 * 保持 TCP/藍牙/BLE 連線在背景執行
 */
class RemoteControlService : Service() {

    private val binder = RemoteControlBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 連線管理器
    private var unifiedConnectionManager: UnifiedConnectionManager? = null
    private var bleManager: BleManager? = null
    private var currentConnectionType: ConnectionType? = null

    // 連線狀態
    private val _connectionState = MutableStateFlow<ServiceConnectionState>(ServiceConnectionState.Disconnected)
    val connectionState: StateFlow<ServiceConnectionState> = _connectionState

    // 連線回呼
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        ConnectionLogger.log("📡 RemoteControlService 已建立", ConnectionLogger.LogLevel.INFO)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即啟動前景通知（避免系統終止 Service）
        startForeground(NOTIFICATION_ID, createNotification("準備中...", "正在初始化"))
        ConnectionLogger.log("📡 RemoteControlService 已啟動", ConnectionLogger.LogLevel.INFO)
        return START_STICKY  // Service 被終止後自動重啟
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * TCP 連線
     */
    suspend fun connectTcp(host: String, port: Int = 9512) {
        disconnectAll()
        currentConnectionType = ConnectionType.TCP

        _connectionState.value = ServiceConnectionState.Connecting("TCP", "$host:$port")
        updateNotification("連線中...", "正在連線到 $host:$port")

        try {
            val manager = UnifiedConnectionManager(this).apply {
                onConnected = {
                    _connectionState.value = ServiceConnectionState.Connected(ConnectionType.TCP, "$host:$port")
                    updateNotification("已連線", "TCP: $host:$port")
                    this@RemoteControlService.onConnected?.invoke()
                    ConnectionLogger.log("✅ TCP 已連線", ConnectionLogger.LogLevel.INFO)
                }

                onDisconnected = {
                    _connectionState.value = ServiceConnectionState.Disconnected
                    updateNotification("已斷線", "連線已中斷")
                    this@RemoteControlService.onDisconnected?.invoke()
                    ConnectionLogger.log("❌ TCP 已斷線", ConnectionLogger.LogLevel.WARNING)
                }

                onLog = { message, level ->
                    ConnectionLogger.log("[TCP] $message", level)
                }

                connectTcp(host, port)
            }

            unifiedConnectionManager = manager
        } catch (e: Exception) {
            _connectionState.value = ServiceConnectionState.Error("TCP 連線失敗: ${e.message}")
            updateNotification("連線失敗", e.message ?: "未知錯誤")
            ConnectionLogger.log("❌ TCP 連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
        }
    }

    /**
     * 藍牙連線
     */
    @SuppressLint("MissingPermission")
    suspend fun connectBluetooth(device: BluetoothDevice) {
        disconnectAll()
        currentConnectionType = ConnectionType.BLUETOOTH

        val deviceName = device.name ?: "未命名裝置"
        _connectionState.value = ServiceConnectionState.Connecting("藍牙", deviceName)
        updateNotification("連線中...", "正在連線到 $deviceName")

        try {
            val manager = UnifiedConnectionManager(this).apply {
                onConnected = {
                    _connectionState.value = ServiceConnectionState.Connected(ConnectionType.BLUETOOTH, deviceName)
                    updateNotification("已連線", "藍牙: $deviceName")
                    this@RemoteControlService.onConnected?.invoke()
                    ConnectionLogger.log("✅ 藍牙已連線", ConnectionLogger.LogLevel.INFO)
                }

                onDisconnected = {
                    _connectionState.value = ServiceConnectionState.Disconnected
                    updateNotification("已斷線", "連線已中斷")
                    this@RemoteControlService.onDisconnected?.invoke()
                    ConnectionLogger.log("❌ 藍牙已斷線", ConnectionLogger.LogLevel.WARNING)
                }

                onLog = { message, level ->
                    ConnectionLogger.log("[藍牙] $message", level)
                }

                connectBluetooth(device)
            }

            unifiedConnectionManager = manager
        } catch (e: Exception) {
            _connectionState.value = ServiceConnectionState.Error("藍牙連線失敗: ${e.message}")
            updateNotification("連線失敗", e.message ?: "未知錯誤")
            ConnectionLogger.log("❌ 藍牙連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
        }
    }

    /**
     * BLE 連線
     */
    @SuppressLint("MissingPermission")
    suspend fun connectBle(deviceAddress: String) {
        disconnectAll()
        currentConnectionType = ConnectionType.BLE_EMULSTICK

        _connectionState.value = ServiceConnectionState.Connecting("BLE", deviceAddress)
        updateNotification("連線中...", "正在連線到 BLE 裝置")

        try {
            val manager = BleManager(this).apply {
                // 監聽 BLE 連線狀態
                serviceScope.launch {
                    connectionState.collect { state ->
                        when (state) {
                            is BleConnectionState.Connected -> {
                                _connectionState.value = ServiceConnectionState.Connected(
                                    ConnectionType.BLE_EMULSTICK,
                                    state.deviceName
                                )
                                updateNotification("已連線", "BLE: ${state.deviceName}")
                                this@RemoteControlService.onConnected?.invoke()
                                ConnectionLogger.log("✅ BLE 已連線", ConnectionLogger.LogLevel.INFO)
                            }
                            is BleConnectionState.Disconnected -> {
                                _connectionState.value = ServiceConnectionState.Disconnected
                                updateNotification("已斷線", "連線已中斷")
                                this@RemoteControlService.onDisconnected?.invoke()
                                ConnectionLogger.log("❌ BLE 已斷線", ConnectionLogger.LogLevel.WARNING)
                            }
                            is BleConnectionState.Error -> {
                                _connectionState.value = ServiceConnectionState.Error("BLE 錯誤: ${state.message}")
                                updateNotification("連線錯誤", state.message)
                                ConnectionLogger.log("❌ BLE 錯誤: ${state.message}", ConnectionLogger.LogLevel.ERROR)
                            }
                            else -> {
                                // Connecting, Authenticating 等狀態
                                updateNotification("連線中...", state.toString())
                            }
                        }
                    }
                }

                // 開始連線
                connectByAddress(deviceAddress)
            }

            bleManager = manager
        } catch (e: Exception) {
            _connectionState.value = ServiceConnectionState.Error("BLE 連線失敗: ${e.message}")
            updateNotification("連線失敗", e.message ?: "未知錯誤")
            ConnectionLogger.log("❌ BLE 連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
        }
    }

    /**
     * 斷開所有連線
     */
    fun disconnectAll() {
        unifiedConnectionManager?.disconnect()
        unifiedConnectionManager = null

        bleManager?.disconnect()
        bleManager = null

        currentConnectionType = null
        _connectionState.value = ServiceConnectionState.Disconnected
        updateNotification("已斷線", "無連線")
    }

    /**
     * 取得當前連線管理器
     */
    fun getUnifiedConnectionManager(): UnifiedConnectionManager? = unifiedConnectionManager

    /**
     * 取得當前 BLE 管理器
     */
    fun getBleManager(): BleManager? = bleManager

    /**
     * 取得當前連線類型
     */
    fun getCurrentConnectionType(): ConnectionType? = currentConnectionType

    /**
     * 建立通知頻道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "遙控器服務",
                NotificationManager.IMPORTANCE_LOW  // LOW = 不會發出聲音
            ).apply {
                description = "保持遙控器連線在背景執行"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 建立通知
     */
    private fun createNotification(title: String, text: String): Notification {
        // 點擊通知時開啟 MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // 使用預設圖示
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 無法滑動移除
            .setPriority(NotificationCompat.PRIORITY_LOW)  // 低優先級（不打擾）
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        ConnectionLogger.log("📡 RemoteControlService 已銷毀", ConnectionLogger.LogLevel.INFO)
        disconnectAll()
        serviceScope.cancel()
    }

    /**
     * Binder 介面
     */
    inner class RemoteControlBinder : Binder() {
        fun getService(): RemoteControlService = this@RemoteControlService
    }

    companion object {
        private const val CHANNEL_ID = "remote_control_service"
        private const val NOTIFICATION_ID = 1001

        /**
         * 啟動服務
         */
        fun start(context: Context) {
            val intent = Intent(context, RemoteControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服務
         */
        fun stop(context: Context) {
            val intent = Intent(context, RemoteControlService::class.java)
            context.stopService(intent)
        }
    }
}

/**
 * 服務連線狀態
 */
sealed class ServiceConnectionState {
    object Disconnected : ServiceConnectionState()
    data class Connecting(val type: String, val target: String) : ServiceConnectionState()
    data class Connected(val type: ConnectionType, val target: String) : ServiceConnectionState()
    data class Error(val message: String) : ServiceConnectionState()
}
