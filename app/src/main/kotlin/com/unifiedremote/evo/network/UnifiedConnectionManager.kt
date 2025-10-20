package com.unifiedremote.evo.network

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.unifiedremote.evo.data.Packet
import com.unifiedremote.evo.data.Action
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 統一連線管理器
 * 整合 TCP 和藍牙連線
 */
class UnifiedConnectionManager(
    private val context: Context
) {
    private var currentConnection: Any? = null  // ConnectionManager 或 BluetoothConnectionManager
    private var connectionType: ConnectionType? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onPacketReceived: ((Packet) -> Unit)? = null
    var onLog: ((String, ConnectionLogger.LogLevel) -> Unit)? = null

    /**
     * 使用 TCP 連線
     */
    suspend fun connectTcp(host: String, port: Int = 9512) {
        disconnect()

        val manager = ConnectionManager(host, port).apply {
            this.onConnected = this@UnifiedConnectionManager.onConnected
            this.onDisconnected = this@UnifiedConnectionManager.onDisconnected
            this.onPacketReceived = this@UnifiedConnectionManager.onPacketReceived
            this.onLog = { msg, level ->
                this@UnifiedConnectionManager.onLog?.invoke("[TCP] $msg", level)
            }
        }

        currentConnection = manager
        connectionType = ConnectionType.TCP

        manager.connect()
    }

    /**
     * 使用藍牙連線
     */
    suspend fun connectBluetooth(device: BluetoothDevice) {
        disconnect()

        val manager = BluetoothConnectionManager(device).apply {
            this.onConnected = this@UnifiedConnectionManager.onConnected
            this.onDisconnected = this@UnifiedConnectionManager.onDisconnected
            this.onPacketReceived = this@UnifiedConnectionManager.onPacketReceived
            this.onLog = this@UnifiedConnectionManager.onLog
        }

        currentConnection = manager
        connectionType = ConnectionType.BLUETOOTH

        manager.connect()
    }

    /**
     * 中斷連線
     */
    fun disconnect() {
        when (val conn = currentConnection) {
            is ConnectionManager -> conn.disconnect()
            is BluetoothConnectionManager -> conn.disconnect()
        }
        currentConnection = null
        connectionType = null
    }

    /**
     * 傳送封包
     */
    suspend fun send(packet: Packet) {
        when (val conn = currentConnection) {
            is ConnectionManager -> conn.send(packet)
            is BluetoothConnectionManager -> conn.send(packet)
            else -> onLog?.invoke("未連線，無法傳送封包", ConnectionLogger.LogLevel.WARNING)
        }
    }

    /**
     * 傳送 Action（便利方法）
     */
    suspend fun sendAction(action: Action) {
        val packet = Packet(run = action)
        send(packet)
    }

    /**
     * 關閉連線管理器
     */
    fun close() {
        when (val conn = currentConnection) {
            is ConnectionManager -> conn.close()
            is BluetoothConnectionManager -> conn.close()
        }
        currentConnection = null
        connectionType = null
    }

    /**
     * 取得當前連線類型
     */
    fun getCurrentConnectionType(): ConnectionType? = connectionType

    /**
     * 是否已連線
     */
    fun isConnected(): Boolean = currentConnection != null

    /**
     * 取得 TCP 連線狀態（如果使用 TCP）
     */
    fun getTcpConnectionState(): StateFlow<UnifiedConnectionState>? {
        return (currentConnection as? ConnectionManager)?.connectionState
    }

    /**
     * 取得藍牙連線狀態（如果使用藍牙）
     */
    fun getBluetoothConnectionState(): StateFlow<UnifiedConnectionState>? {
        return (currentConnection as? BluetoothConnectionManager)?.connectionState
    }

    companion object {
        /**
         * 檢查藍牙是否可用
         */
        fun isBluetoothAvailable(): Boolean {
            return BluetoothConnectionManager.isBluetoothAvailable()
        }

        /**
         * 取得已配對的藍牙裝置
         */
        fun getPairedBluetoothDevices(): List<BluetoothDevice> {
            return BluetoothConnectionManager.getPairedDevices()
        }
    }
}
