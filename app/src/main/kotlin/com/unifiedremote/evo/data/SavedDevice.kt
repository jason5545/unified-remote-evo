package com.unifiedremote.evo.data

import com.unifiedremote.evo.network.ConnectionType

/**
 * 儲存的裝置資訊
 */
data class SavedDevice(
    val id: String,                  // 唯一識別碼
    val name: String,                // 裝置名稱（顯示用）
    val type: ConnectionType,        // 連線類型（TCP/藍牙）
    val host: String? = null,        // TCP: 主機位址
    val port: Int? = null,           // TCP: 埠號
    val bluetoothAddress: String? = null,  // 藍牙: MAC 位址
    val lastConnected: Long = System.currentTimeMillis()  // 最後連線時間
) {
    companion object {
        /**
         * 建立 TCP 裝置
         */
        fun createTcp(host: String, port: Int, name: String? = null): SavedDevice {
            return SavedDevice(
                id = "tcp_${host}_${port}",
                name = name ?: "$host:$port",
                type = ConnectionType.TCP,
                host = host,
                port = port
            )
        }

        /**
         * 建立藍牙裝置
         */
        fun createBluetooth(address: String, name: String): SavedDevice {
            return SavedDevice(
                id = "bt_$address",
                name = name,
                type = ConnectionType.BLUETOOTH,
                bluetoothAddress = address
            )
        }

        /**
         * 建立 BLE EmulStick 裝置
         */
        fun createBleEmulstick(deviceName: String, address: String): SavedDevice {
            return SavedDevice(
                id = "ble_$address",
                name = deviceName,
                type = ConnectionType.BLE_EMULSTICK,
                bluetoothAddress = address
            )
        }
    }

    /**
     * 取得顯示文字
     */
    fun getDisplayText(): String {
        return when (type) {
            ConnectionType.TCP -> "$name (TCP)"
            ConnectionType.BLUETOOTH -> "$name (藍牙)"
            ConnectionType.BLE_EMULSTICK -> "$name (BLE)"
        }
    }

    /**
     * 取得副標題文字
     */
    fun getSubtitle(): String {
        return when (type) {
            ConnectionType.TCP -> host?.let { "$it:${port ?: 9512}" } ?: ""
            ConnectionType.BLUETOOTH -> bluetoothAddress ?: ""
            ConnectionType.BLE_EMULSTICK -> bluetoothAddress ?: ""
        }
    }
}
