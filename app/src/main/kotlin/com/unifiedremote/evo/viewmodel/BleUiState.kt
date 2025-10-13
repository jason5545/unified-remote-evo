package com.unifiedremote.evo.viewmodel

import com.unifiedremote.evo.network.ble.BleConnectionState
import com.unifiedremote.evo.data.SavedDevice

/**
 * BLE UI 狀態（統一的反應式來源）
 */
data class BleUiState(
    /** 所有必要權限是否已授予 */
    val permissionsGranted: Boolean = false,

    /** 藍牙是否開啟 */
    val isBluetoothOn: Boolean = false,

    /** 目前是否正在掃描 */
    val isScanning: Boolean = false,

    /** 連線狀態 */
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,

    /** 已掃描到的裝置列表 */
    val scannedDevices: List<SavedDevice> = emptyList(),

    /** 錯誤訊息 */
    val errorMessage: String? = null
) {
    /**
     * 計算屬性：是否可以開始掃描
     * 條件：權限已授予 && 藍牙已開啟 && 目前沒有在掃描
     */
    val canScan: Boolean
        get() = permissionsGranted && isBluetoothOn && !isScanning

    /**
     * 計算屬性：是否可以連線到裝置
     */
    val canConnect: Boolean
        get() = permissionsGranted && isBluetoothOn && scannedDevices.isNotEmpty()
}
