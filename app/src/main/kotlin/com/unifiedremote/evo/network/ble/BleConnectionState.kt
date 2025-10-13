package com.unifiedremote.evo.network.ble

/**
 * 裝置模式列舉
 *
 * 用於識別 EmulStick 接收器當前的工作模式
 */
enum class DeviceMode {
    /** 組合模式（預設）：鍵盤 + 滑鼠 + 手把 */
    COMPOSITE,

    /** XInput 模式：Xbox 360 控制器 */
    XINPUT,

    /** 單鍵盤模式 */
    SINGLE_KEYBOARD,

    /** 未知模式（尚未偵測或不支援） */
    UNKNOWN;

    companion object {
        /**
         * 根據 VID/PID 判斷裝置模式
         *
         * 參考：原廠 BluetoothLeService.java updateEmulDeviceInfo() 方法
         */
        fun fromVidPid(vid: Int, pid: Int): DeviceMode {
            return when {
                // Xbox 360 控制器（XInput 模式）
                vid == GattConstants.XBOX360_VID && pid == GattConstants.XBOX360_PID -> XINPUT

                // TI/WCH Composite（組合模式）
                (vid == GattConstants.TI_COMPOSITE_VID && pid == GattConstants.TI_COMPOSITE_PID) ||
                (vid == GattConstants.WCH_COMPOSITE_VID && pid == GattConstants.WCH_COMPOSITE_PID) ||
                (vid == GattConstants.EMULSTICK_V0_VID && pid == GattConstants.EMULSTICK_V0_PID) -> COMPOSITE

                // 單鍵盤模式
                vid == GattConstants.SINGLE_KB_VID && pid == GattConstants.SINGLE_KB_PID -> SINGLE_KEYBOARD

                // 未知
                else -> UNKNOWN
            }
        }
    }
}

/**
 * BLE 連線狀態
 */
sealed class BleConnectionState {
    /** 已中斷連線 */
    data object Disconnected : BleConnectionState()

    /** 掃描中 */
    data object Scanning : BleConnectionState()

    /** 連線中 */
    data class Connecting(val deviceName: String) : BleConnectionState()

    /** 已連線 */
    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val currentDeviceMode: DeviceMode = DeviceMode.UNKNOWN  // 預設為未知，待查詢
    ) : BleConnectionState()

    /** 重連中 */
    data class Reconnecting(
        val deviceAddress: String,
        val attempt: Int,
        val maxAttempts: Int
    ) : BleConnectionState()

    /** 錯誤 */
    data class Error(val message: String, val cause: Throwable? = null) : BleConnectionState()
}
