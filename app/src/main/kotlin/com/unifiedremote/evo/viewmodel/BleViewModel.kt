package com.unifiedremote.evo.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.ble.BleManager
import com.unifiedremote.evo.network.ble.BleConnectionState
import com.unifiedremote.evo.network.ble.BleXInputController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * BLE ViewModel - 統一管理 BLE 狀態、權限、掃描
 *
 * 核心原則：
 * 1. 所有系統 API 呼叫都在此處（不在 Composable 內）
 * 2. 輸出統一的 StateFlow<BleUiState>
 * 3. 權限狀態在每次前景可見時重新檢查
 * 4. 藍牙狀態透過廣播接收器監聽
 */
class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication<Application>()

    // BLE 管理器（internal 以便 MainActivity 可以訪問）
    internal val bleManager: BleManager = BleManager(context)
    val isBluetoothSupported: Boolean = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter != null

    // XInput 控制器（延遲初始化，連線後才建立）
    private var _xInputController: BleXInputController? = null

    // XInput 模式狀態
    private val _isXInputMode = MutableStateFlow(false)
    val isXInputMode: StateFlow<Boolean> = _isXInputMode.asStateFlow()

    // 內部狀態
    private val _permissionsGranted = MutableStateFlow(false)
    private val _isBluetoothOn = MutableStateFlow(false)

    // 自動重連狀態
    private var lastConnectedDeviceAddress: String? = null
    private var isManualDisconnect = false
    private var currentReconnectAttempt = 0

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val AUTO_RECONNECT_DELAY = 2000L
    }

    // UI 狀態輸出（合併所有來源）
    val uiState: StateFlow<BleUiState> = combine(
        _permissionsGranted,
        _isBluetoothOn,
        bleManager.connectionState,
        bleManager.scannedDevices
    ) { permissionsGranted, isBluetoothOn, connectionState, scannedDevices ->
        BleUiState(
            permissionsGranted = permissionsGranted,
            isBluetoothOn = isBluetoothOn,
            isScanning = connectionState is BleConnectionState.Scanning,
            connectionState = connectionState,
            scannedDevices = scannedDevices,
            errorMessage = if (connectionState is BleConnectionState.Error) {
                connectionState.message
            } else {
                null
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BleUiState()
    )

    // 藍牙狀態廣播接收器
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                val isOn = state == BluetoothAdapter.STATE_ON
                ConnectionLogger.log(
                    "📻 藍牙狀態變化：${if (isOn) "開啟" else "關閉"}",
                    ConnectionLogger.LogLevel.INFO
                )
                _isBluetoothOn.value = isOn
            }
        }
    }

    init {
        // 註冊藍牙狀態廣播接收器
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, bluetoothStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        ConnectionLogger.log("📡 已註冊藍牙狀態監聽器", ConnectionLogger.LogLevel.DEBUG)

        // 初始檢查
        checkPermissionsAndBluetooth()

        // 監聽裝置模式變化，自動同步 isXInputMode 狀態
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is BleConnectionState.Connected) {
                    val shouldBeXInputMode = state.currentDeviceMode == com.unifiedremote.evo.network.ble.DeviceMode.XINPUT
                    if (_isXInputMode.value != shouldBeXInputMode) {
                        _isXInputMode.value = shouldBeXInputMode
                        ConnectionLogger.log(
                            "🔄 自動同步 XInput 模式狀態：$shouldBeXInputMode",
                            ConnectionLogger.LogLevel.INFO
                        )
                    }
                }
            }
        }

        // 監聽連線狀態變化，實現自動重連機制
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    is BleConnectionState.Connected -> {
                        // 連線成功，記錄裝置位址並重置重連計數器
                        lastConnectedDeviceAddress = state.deviceAddress
                        currentReconnectAttempt = 0
                        isManualDisconnect = false
                        ConnectionLogger.log(
                            "✅ 連線成功，記錄裝置位址：${state.deviceAddress}",
                            ConnectionLogger.LogLevel.DEBUG
                        )
                    }

                    is BleConnectionState.Disconnected -> {
                        // 只有在非主動斷線且有上次連線位址時才嘗試重連
                        if (!isManualDisconnect && lastConnectedDeviceAddress != null) {
                            handleAutoReconnect()
                        } else {
                            ConnectionLogger.log(
                                "⚠️ 不執行自動重連 (主動斷線=$isManualDisconnect, 上次位址=$lastConnectedDeviceAddress)",
                                ConnectionLogger.LogLevel.DEBUG
                            )
                        }
                    }

                    is BleConnectionState.Error -> {
                        // 連線錯誤，嘗試重連（如果不是超過最大次數）
                        if (!isManualDisconnect && lastConnectedDeviceAddress != null) {
                            handleAutoReconnect()
                        }
                    }

                    else -> {
                        // 其他狀態不處理（Scanning, Connecting, Reconnecting）
                    }
                }
            }
        }
    }

    /**
     * 自動重連處理
     */
    private fun handleAutoReconnect() {
        val deviceAddress = lastConnectedDeviceAddress ?: return

        if (currentReconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            ConnectionLogger.log(
                "❌ 已達最大重連次數 ($MAX_RECONNECT_ATTEMPTS)，停止重連",
                ConnectionLogger.LogLevel.ERROR
            )
            bleManager.updateConnectionState(
                BleConnectionState.Error("連線失敗：已達最大重連次數")
            )
            // 清除記錄的裝置位址，避免下次意外觸發重連
            lastConnectedDeviceAddress = null
            currentReconnectAttempt = 0
            return
        }

        currentReconnectAttempt++
        ConnectionLogger.log(
            "🔄 準備自動重連 (第 $currentReconnectAttempt/$MAX_RECONNECT_ATTEMPTS 次) 到裝置 $deviceAddress",
            ConnectionLogger.LogLevel.INFO
        )

        // 更新狀態為 Reconnecting
        bleManager.updateConnectionState(
            BleConnectionState.Reconnecting(
                deviceAddress = deviceAddress,
                attempt = currentReconnectAttempt,
                maxAttempts = MAX_RECONNECT_ATTEMPTS
            )
        )

        // 延遲後嘗試重連
        viewModelScope.launch {
            delay(AUTO_RECONNECT_DELAY)
            ConnectionLogger.log(
                "🔌 開始重連到 $deviceAddress",
                ConnectionLogger.LogLevel.INFO
            )
            try {
                val started = bleManager.connect(deviceAddress)
                if (!started) {
                    ConnectionLogger.log(
                        "⚠️ BLE 自動重連已停止：已達最大重試次數",
                        ConnectionLogger.LogLevel.ERROR
                    )
                    lastConnectedDeviceAddress = null
                    currentReconnectAttempt = 0
                    bleManager.disconnect()
                    return@launch
                }
            } catch (e: Exception) {
                ConnectionLogger.log(
                    "❌ 重連失敗: ${e.message}",
                    ConnectionLogger.LogLevel.ERROR
                )
            }
        }
    }

    /**
     * Activity 變為可見時呼叫（在 onResume）
     * 強制重新檢查權限和藍牙狀態
     */
    fun onVisible() {
        ConnectionLogger.log("👁️ Activity 變為可見，重新檢查狀態", ConnectionLogger.LogLevel.DEBUG)
        checkPermissionsAndBluetooth()
    }

    /**
     * 檢查權限和藍牙狀態
     */
    private fun checkPermissionsAndBluetooth() {
        ConnectionLogger.log("🔍 開始檢查權限和藍牙狀態...", ConnectionLogger.LogLevel.DEBUG)

        // 1. 檢查權限
        val permissionsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            val scanGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val connectGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            ConnectionLogger.log(
                "📋 權限檢查 (Android 12+): SCAN=$scanGranted, CONNECT=$connectGranted",
                ConnectionLogger.LogLevel.DEBUG
            )

            scanGranted && connectGranted
        } else {
            // Android 11-
            val fineLocationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseLocationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            ConnectionLogger.log(
                "📋 權限檢查 (Android 11-): FINE=$fineLocationGranted, COARSE=$coarseLocationGranted",
                ConnectionLogger.LogLevel.DEBUG
            )

            fineLocationGranted && coarseLocationGranted
        }

        _permissionsGranted.value = permissionsGranted
        ConnectionLogger.log(
            if (permissionsGranted) {
                "✅ 所有權限已授予"
            } else {
                "❌ 缺少必要權限"
            },
            ConnectionLogger.LogLevel.INFO
        )

        // 2. 檢查藍牙狀態
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        val isBluetoothOn = try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }
        _isBluetoothOn.value = isBluetoothOn

        ConnectionLogger.log(
            if (isBluetoothOn) {
                "✅ 藍牙已開啟"
            } else {
                "❌ 藍牙未開啟或不支援"
            },
            ConnectionLogger.LogLevel.INFO
        )

        // 3. 輸出最終狀態
        ConnectionLogger.log(
            "📊 最終狀態 - canScan=${uiState.value.canScan} (permissions=$permissionsGranted, bluetooth=$isBluetoothOn)",
            ConnectionLogger.LogLevel.INFO
        )
    }

    /**
     * 開始掃描
     */
    fun startScan() {
        ConnectionLogger.log("🔘 ViewModel.startScan() 被呼叫", ConnectionLogger.LogLevel.DEBUG)
        ConnectionLogger.log(
            "📊 掃描前狀態 - canScan=${uiState.value.canScan}, " +
                    "permissions=${uiState.value.permissionsGranted}, " +
                    "bluetooth=${uiState.value.isBluetoothOn}, " +
                    "scanning=${uiState.value.isScanning}",
            ConnectionLogger.LogLevel.DEBUG
        )

        if (!uiState.value.canScan) {
            when {
                !uiState.value.permissionsGranted ->
                    ConnectionLogger.log("❌ 無法掃描：缺少權限", ConnectionLogger.LogLevel.ERROR)
                !uiState.value.isBluetoothOn ->
                    ConnectionLogger.log("❌ 無法掃描：藍牙未開啟", ConnectionLogger.LogLevel.ERROR)
                uiState.value.isScanning ->
                    ConnectionLogger.log("⚠️ 無法掃描：已在掃描中", ConnectionLogger.LogLevel.WARNING)
            }
            return
        }

        viewModelScope.launch {
            try {
                bleManager.startScan()
            } catch (e: Exception) {
                ConnectionLogger.log(
                    "❌ 掃描啟動失敗: ${e.message}\n${e.stackTraceToString()}",
                    ConnectionLogger.LogLevel.ERROR
                )
            }
        }
    }

    /**
     * 停止掃描
     */
    fun stopScan() {
        ConnectionLogger.log("⏹️ ViewModel.stopScan() 被呼叫", ConnectionLogger.LogLevel.DEBUG)
        bleManager.stopScan()
    }

    /**
     * 連線到裝置
     */
    fun connectToDevice(address: String) {
        ConnectionLogger.log("🔌 ViewModel.connectToDevice($address) 被呼叫", ConnectionLogger.LogLevel.DEBUG)

        if (!uiState.value.permissionsGranted) {
            ConnectionLogger.log("❌ 無法連線：缺少權限", ConnectionLogger.LogLevel.ERROR)
            return
        }

        if (!uiState.value.isBluetoothOn) {
            ConnectionLogger.log("❌ 無法連線：藍牙未開啟", ConnectionLogger.LogLevel.ERROR)
            return
        }

        viewModelScope.launch {
            try {
                val started = bleManager.connect(address)
                if (!started) {
                    ConnectionLogger.log(
                        "⚠️ 無法開始 BLE 連線：已達最大重試次數",
                        ConnectionLogger.LogLevel.ERROR
                    )
                    bleManager.disconnect()
                }
            } catch (e: Exception) {
                ConnectionLogger.log(
                    "❌ 連線失敗: ${e.message}",
                    ConnectionLogger.LogLevel.ERROR
                )
            }
        }
    }

    /**
     * 中斷連線
     */
    fun disconnect() {
        ConnectionLogger.log("🔌 ViewModel.disconnect() 被呼叫（主動斷線）", ConnectionLogger.LogLevel.DEBUG)
        // 標記為主動斷線，避免觸發自動重連
        isManualDisconnect = true
        // 清除記錄的裝置位址
        lastConnectedDeviceAddress = null
        currentReconnectAttempt = 0
        // 執行斷線
        bleManager.disconnect()
        // 重置 XInput 狀態
        _isXInputMode.value = false
        _xInputController = null
    }

    /**
     * 取得或建立 XInput 控制器
     */
    private fun getOrCreateXInputController(): BleXInputController {
        if (_xInputController == null) {
            _xInputController = BleXInputController(bleManager)
            ConnectionLogger.log("🎮 已建立 BleXInputController 實例", ConnectionLogger.LogLevel.DEBUG)
        }
        return _xInputController!!
    }

    /**
     * 切換到 XInput 模式
     */
    suspend fun switchToXInputMode(): Result<Unit> {
        ConnectionLogger.log("🎮 ViewModel.switchToXInputMode() 被呼叫", ConnectionLogger.LogLevel.DEBUG)

        val controller = getOrCreateXInputController()
        val result = controller.switchToXInputMode()

        return result.also {
            if (it.isSuccess) {
                _isXInputMode.value = true
                ConnectionLogger.log("✅ 已切換到 XInput 模式", ConnectionLogger.LogLevel.INFO)
            } else {
                ConnectionLogger.log(
                    "❌ 切換到 XInput 模式失敗: ${it.exceptionOrNull()?.message}",
                    ConnectionLogger.LogLevel.ERROR
                )
            }
        }
    }

    /**
     * 切換回組合模式
     */
    suspend fun switchToCompositeMode(): Result<Unit> {
        ConnectionLogger.log("🎮 ViewModel.switchToCompositeMode() 被呼叫", ConnectionLogger.LogLevel.DEBUG)

        val controller = getOrCreateXInputController()
        val result = controller.switchToCompositeMode()

        return result.also {
            if (it.isSuccess) {
                _isXInputMode.value = false
                ConnectionLogger.log("✅ 已切換回組合模式", ConnectionLogger.LogLevel.INFO)
            } else {
                ConnectionLogger.log(
                    "❌ 切換回組合模式失敗: ${it.exceptionOrNull()?.message}",
                    ConnectionLogger.LogLevel.ERROR
                )
            }
        }
    }

    /**
     * 取得 XInput 控制器實例（供 UI 使用）
     */
    fun getXInputController(): BleXInputController? {
        // 只在已連線時回傳控制器
        return if (bleManager.connectionState.value is BleConnectionState.Connected) {
            getOrCreateXInputController()
        } else {
            null
        }
    }

    /**
     * 取得晶片廠商資訊
     *
     * 只在已連線到 BLE 裝置時才可用。
     *
     * @return VendorInfo 或 null（未連線時）
     */
    fun getVendorInfo(): com.unifiedremote.evo.network.ble.VendorInfo? {
        return if (bleManager.connectionState.value is BleConnectionState.Connected) {
            bleManager.getVendorInfo()
        } else {
            null
        }
    }

    /**
     * 取得 PNP ID 原始資料（除錯用）
     */
    fun getPnpIdRaw(): ByteArray? {
        return if (bleManager.connectionState.value is BleConnectionState.Connected) {
            bleManager.pnpId
        } else {
            null
        }
    }

    /**
     * 取得韌體版本
     */
    fun getFirmwareVersion(): String? {
        return if (bleManager.connectionState.value is BleConnectionState.Connected) {
            bleManager.firmwareVersion
        } else {
            null
        }
    }

    /**
     * 取得軟體版本（用於判斷明文密碼）
     */
    fun getSoftwareVersion(): String? {
        return if (bleManager.connectionState.value is BleConnectionState.Connected) {
            bleManager.softwareVersion
        } else {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 解除註冊廣播接收器
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
            ConnectionLogger.log("📡 已解除註冊藍牙狀態監聽器", ConnectionLogger.LogLevel.DEBUG)
        } catch (e: Exception) {
            // 忽略重複解除註冊的錯誤
        }

        // 清理 BLE 連線
        bleManager.disconnect()
    }
}
