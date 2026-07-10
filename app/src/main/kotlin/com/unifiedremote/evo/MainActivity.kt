package com.unifiedremote.evo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.unifiedremote.evo.controller.KeyboardController
import com.unifiedremote.evo.controller.MouseController
import com.unifiedremote.evo.data.DeviceHistoryManager
import com.unifiedremote.evo.data.SavedDevice
import com.unifiedremote.evo.data.SensitivityManager
import com.unifiedremote.evo.data.ThemeManager
import com.unifiedremote.evo.data.ThemeMode
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.UnifiedConnectionManager
import com.unifiedremote.evo.service.RemoteControlService
import com.unifiedremote.evo.ui.screens.DebugScreen
import com.unifiedremote.evo.ui.screens.SensitivitySettingsScreen
import com.unifiedremote.evo.ui.screens.ServerConfigScreen
import com.unifiedremote.evo.ui.screens.TouchpadScreen
import com.unifiedremote.evo.ui.theme.UnifiedRemoteEvoTheme
import com.unifiedremote.evo.viewmodel.BleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // ✅ 使用 ViewModel 管理 BLE 狀態（取代本地 bleManager）
    private val bleViewModel: BleViewModel by viewModels()

    private var connectionManager: UnifiedConnectionManager? = null
    private var bluetoothPermissionGranted by mutableStateOf(false)
    private lateinit var deviceHistoryManager: DeviceHistoryManager
    private lateinit var sensitivityManager: SensitivityManager
    private lateinit var themeManager: ThemeManager
    private var shouldAutoConnect by mutableStateOf(true)  // 是否應該自動連線
    private var serviceStartRequested = false
    private var notificationPermissionRequested = false

    // 背景服務
    private var remoteControlService: RemoteControlService? = null
    private var serviceBound by mutableStateOf(false)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RemoteControlService.RemoteControlBinder
            remoteControlService = binder?.getService()
            serviceBound = true
            ConnectionLogger.log("✅ 已綁定到背景服務", ConnectionLogger.LogLevel.INFO)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteControlService = null
            serviceBound = false
            ConnectionLogger.log("⚠️ 背景服務已解除綁定", ConnectionLogger.LogLevel.WARNING)
        }
    }

    // 藍牙權限請求
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        bluetoothPermissionGranted = isGranted
        if (!isGranted) {
            ConnectionLogger.log("藍牙權限被拒絕", ConnectionLogger.LogLevel.WARNING)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化日誌系統
        ConnectionLogger.init(this)

        // 初始化裝置歷史管理器
        deviceHistoryManager = DeviceHistoryManager(this)

        // 初始化靈敏度管理器
        sensitivityManager = SensitivityManager(this)

        // 初始化主題管理器
        themeManager = ThemeManager(this)

        // 檢查藍牙權限
        checkBluetoothPermission()

        // Set Compose content
        setContent {
            var mouseController by remember { mutableStateOf<MouseController?>(null) }
            var keyboardController by remember { mutableStateOf<KeyboardController?>(null) }
            var savedDevices by remember { mutableStateOf<List<SavedDevice>>(emptyList()) }
            var currentDeviceId by remember { mutableStateOf<String?>(null) }
            var autoConnectStatus by remember { mutableStateOf<String?>(null) }

            // ✅ 使用 ViewModel 的統一狀態
            val bleUiState by bleViewModel.uiState.collectAsStateWithLifecycle()

            // ✅ 統一的初始化流程（確保執行順序）
            LaunchedEffect(Unit) {
                // 先載入已儲存裝置
                savedDevices = deviceHistoryManager.getAllDevices()
            }

            // ✅ 自動連線邏輯：檢查背景服務與權限就緒後再執行
            LaunchedEffect(
                serviceBound,
                shouldAutoConnect,
                bluetoothPermissionGranted,
                bleUiState.permissionsGranted,
                bleUiState.isBluetoothOn
            ) {
                if (!shouldAutoConnect) {
                    return@LaunchedEffect
                }

                if (!serviceBound) {
                    autoConnectStatus = "等待背景服務啟動以自動連線"
                    return@LaunchedEffect
                }

                ConnectionLogger.log(
                    "自動連線檢查：serviceBound=$serviceBound, shouldAutoConnect=$shouldAutoConnect",
                    ConnectionLogger.LogLevel.DEBUG
                )

                autoConnectStatus = "準備自動連線..."
                val lastDevice = deviceHistoryManager.getLastDevice()
                if (lastDevice == null) {
                    autoConnectStatus = "目前沒有可自動連線的裝置"
                    ConnectionLogger.log("自動連線：目前沒有可用的儲存裝置", ConnectionLogger.LogLevel.DEBUG)
                    return@LaunchedEffect
                }

                ConnectionLogger.log(
                    "自動連線目標：${lastDevice.name} (${lastDevice.type})",
                    ConnectionLogger.LogLevel.DEBUG
                )
                autoConnectStatus = "自動連線目標：${lastDevice.name}"

                val requiresBluetooth = lastDevice.type != com.unifiedremote.evo.network.ConnectionType.TCP
                if (requiresBluetooth && !bluetoothPermissionGranted) {
                    autoConnectStatus = "等待藍牙權限以自動連線"
                    ConnectionLogger.log("自動連線延後：尚未取得藍牙權限", ConnectionLogger.LogLevel.INFO)
                    return@LaunchedEffect
                }

                if (lastDevice.type == com.unifiedremote.evo.network.ConnectionType.BLE_EMULSTICK) {
                    if (!bleUiState.permissionsGranted || !bleUiState.isBluetoothOn) {
                        autoConnectStatus = "等待 BLE 權限或藍牙開啟"
                        ConnectionLogger.log("自動連線延後：等待 BLE 權限或藍牙開啟", ConnectionLogger.LogLevel.INFO)
                        return@LaunchedEffect
                    }
                }

                val targetName = lastDevice.name
                autoConnectStatus = "正在連線：$targetName"
                ConnectionLogger.log(
                    "自動連線執行：$targetName (${lastDevice.type})",
                    ConnectionLogger.LogLevel.INFO
                )
                delay(500)  // 短暫延遲確保 UI 已載入並且服務可用
                connectSaved(
                    device = lastDevice,
                    onSuccess = { mouse, keyboard, deviceId ->
                        mouseController = mouse
                        keyboardController = keyboard
                        currentDeviceId = deviceId
                        savedDevices = deviceHistoryManager.getAllDevices()
                        autoConnectStatus = "自動連線成功：$targetName"
                    },
                    onFailure = { reason ->
                        autoConnectStatus = "自動連線失敗：$reason"
                        ConnectionLogger.log("❌ 自動連線失敗：$reason", ConnectionLogger.LogLevel.WARNING)
                    }
                )
                shouldAutoConnect = false  // 確保只嘗試自動連線一次
            }

            val themeMode = themeManager.themeMode
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // ✅ 普通藍牙可用性（保留原邏輯）
            val bluetoothAvailable = remember { derivedStateOf {
                bluetoothPermissionGranted && UnifiedConnectionManager.isBluetoothAvailable()
            } }.value

            UnifiedRemoteEvoTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnifiedRemoteEvoApp(
                        onConnectTcp = { host, port ->
                            connectTcp(
                                host = host,
                                port = port,
                                onSuccess = { mouse, keyboard, deviceId ->
                                    mouseController = mouse
                                    keyboardController = keyboard
                                    currentDeviceId = deviceId
                                    savedDevices = deviceHistoryManager.getAllDevices()
                                }
                            )
                        },
                        onConnectBluetooth = { device ->
                            connectBluetooth(
                                device = device,
                                onSuccess = { mouse, keyboard, deviceId ->
                                    mouseController = mouse
                                    keyboardController = keyboard
                                    currentDeviceId = deviceId
                                    savedDevices = deviceHistoryManager.getAllDevices()
                                }
                            )
                        },
                        // ✅ 使用 ViewModel 的掃描方法
                        onStartBleScan = {
                            bleViewModel.startScan()
                        },
                        // ✅ 改用 MAC 地址連線
                        onConnectBleDevice = { address ->
                            connectBleEmulstickDeviceByAddress(
                                address = address,
                                onSuccess = { mouse, keyboard, deviceId ->
                                    mouseController = mouse
                                    keyboardController = keyboard
                                    currentDeviceId = deviceId
                                    savedDevices = deviceHistoryManager.getAllDevices()
                                }
                            )
                        },
                        onConnectSaved = { device ->
                            connectSaved(
                                device = device,
                                onSuccess = { mouse, keyboard, deviceId ->
                                    mouseController = mouse
                                    keyboardController = keyboard
                                    currentDeviceId = deviceId
                                    savedDevices = deviceHistoryManager.getAllDevices()
                                }
                            )
                        },
                        onRemoveSaved = { device ->
                            deviceHistoryManager.removeDevice(device.id)
                            savedDevices = deviceHistoryManager.getAllDevices()
                        },
                        onDisconnect = {
                            disconnectFromServer()
                            mouseController = null
                            keyboardController = null
                            currentDeviceId = null
                        },
                        // XInput 模式切換（透過 ViewModel）
                        onXInputModeChange = { enabled ->
                            lifecycleScope.launch {
                                val result = if (enabled) {
                                    bleViewModel.switchToXInputMode()
                                } else {
                                    bleViewModel.switchToCompositeMode()
                                }

                                result.onSuccess {
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        if (enabled) "已切換到 Xbox 360 控制器模式" else "已切換回鍵盤/滑鼠模式",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure {
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "切換失敗：${it.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        bluetoothAvailable = bluetoothAvailable,
                        // ✅ 使用 ViewModel 的狀態
                        bleAvailable = bleUiState.canScan,
                        bleScannedDevices = bleUiState.scannedDevices,
                        bleConnectionState = bleUiState.connectionState,
                        bleViewModel = bleViewModel,
                        savedDevices = savedDevices,
                        autoConnectStatus = autoConnectStatus,
                        mouseController = mouseController,
                        keyboardController = keyboardController,
                        sensitivityManager = sensitivityManager,
                        themeManager = themeManager,
                        connectionManager = connectionManager,
                        currentDeviceId = currentDeviceId
                    )
                }
            }
        }
    }

    /**
     * Activity 變為可見時重新檢查 BLE 狀態
     */
    override fun onResume() {
        super.onResume()
        // ✅ 觸發 ViewModel 重新檢查權限和藍牙狀態
        bleViewModel.onVisible()
    }

    /**
     * 權限請求結果（原廠實作：權限授予後自動處理）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 檢查是否所有權限都被授予
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            when (requestCode) {
                405 -> {  // POST_NOTIFICATIONS 授予（Android 13+）
                    ConnectionLogger.log("✅ 通知權限已授予", ConnectionLogger.LogLevel.INFO)
                    // 繼續檢查藍牙權限
                    checkBluetoothPermission()
                }
                403 -> {  // BLUETOOTH_CONNECT 授予
                    ConnectionLogger.log("✅ 藍牙權限已授予", ConnectionLogger.LogLevel.INFO)
                    // 原廠：授予後會啟動藍牙啟用對話框（如果藍牙未啟用）
                    // 我們簡化：直接請求下一個權限
                    checkBluetoothPermission()
                }
                404 -> {  // BLUETOOTH_SCAN 或定位權限授予
                    ConnectionLogger.log("✅ BLE 掃描權限已授予，自動開始掃描", ConnectionLogger.LogLevel.INFO)
                    bleViewModel.onVisible()
                    bleViewModel.startScan()
                }
            }
        } else {
            // 權限被拒絕
            when (requestCode) {
                405 -> {
                    ConnectionLogger.log("❌ 通知權限被拒絕（前景服務可能無法運作）", ConnectionLogger.LogLevel.ERROR)
                }
                403 -> {
                    ConnectionLogger.log("❌ 藍牙權限被拒絕", ConnectionLogger.LogLevel.ERROR)
                }
                404 -> {
                    ConnectionLogger.log("❌ BLE 掃描權限被拒絕", ConnectionLogger.LogLevel.ERROR)
                }
            }
        }
    }

    /**
     * 檢查並請求藍牙權限
     */
    private fun checkBluetoothPermission() {
        ConnectionLogger.log("📋 檢查藍牙權限...", ConnectionLogger.LogLevel.DEBUG)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notificationGranted) {
                bluetoothPermissionGranted = false
                if (!notificationPermissionRequested) {
                    notificationPermissionRequested = true
                    ConnectionLogger.log("📋 請求通知權限（前景服務需要）", ConnectionLogger.LogLevel.INFO)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 405)
                } else {
                    ConnectionLogger.log("📋 通知權限仍未授予，持續啟動服務", ConnectionLogger.LogLevel.WARNING)
                }
                // Avoid starting foreground service without notification permission on Android 13+
                return
            } else {
                notificationPermissionRequested = false
            }
        }

        startAndBindService()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val scanGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            ConnectionLogger.log(
                "📋 Android 12+ 權限檢查：CONNECT=$connectGranted, SCAN=$scanGranted",
                ConnectionLogger.LogLevel.DEBUG
            )

            bluetoothPermissionGranted = connectGranted
            ConnectionLogger.log(
                "📋 設定權限狀態：bluetoothPermissionGranted=$connectGranted",
                ConnectionLogger.LogLevel.DEBUG
            )

            if (!connectGranted) {
                ConnectionLogger.log("📋 請求 BLUETOOTH_CONNECT 權限", ConnectionLogger.LogLevel.INFO)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 403)
            } else if (!scanGranted) {
                ConnectionLogger.log("📋 請求 BLUETOOTH_SCAN 權限", ConnectionLogger.LogLevel.INFO)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), 404)
            } else {
                ConnectionLogger.log("✅ 所有藍牙權限已授予", ConnectionLogger.LogLevel.INFO)
            }
        } else {
            val locationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            ConnectionLogger.log(
                "📋 Android 11- 權限檢查：LOCATION=$locationGranted",
                ConnectionLogger.LogLevel.DEBUG
            )

            bluetoothPermissionGranted = true
            ConnectionLogger.log(
                "📋 設定權限狀態：bluetoothPermissionGranted=true",
                ConnectionLogger.LogLevel.DEBUG
            )

            if (!locationGranted) {
                ConnectionLogger.log("需要位置權限以進行 BLE 掃描", ConnectionLogger.LogLevel.INFO)
                val permissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                requestPermissions(permissions, 404)
            } else {
                ConnectionLogger.log("✅ 所有藍牙權限已授予", ConnectionLogger.LogLevel.INFO)
            }
        }
    }

    /**
     * TCP 連線（透過背景服務）
     */
    private fun connectTcp(
        host: String,
        port: Int,
        onSuccess: (MouseController, KeyboardController, String) -> Unit,
        onFailure: (String) -> Unit = { reason ->
            ConnectionLogger.log("? TCP 自動連線失敗: $reason", ConnectionLogger.LogLevel.ERROR)
        }
    ) {
        lifecycleScope.launch {
            try {
                val service = remoteControlService
                if (service == null) {
                    ConnectionLogger.log("❌ 背景服務尚未準備好", ConnectionLogger.LogLevel.ERROR)
                    onFailure("背景服務尚未準備好")
                    return@launch
                }

                ConnectionLogger.log("🔄 正在透過 TCP 連線到 $host:$port...", ConnectionLogger.LogLevel.INFO)

                // 設定連線回呼
                service.onConnected = {
                    ConnectionLogger.log("✅ 已連線到伺服器", ConnectionLogger.LogLevel.INFO)
                    // 儲存裝置至歷史
                    val device = SavedDevice.createTcp(host, port)
                    deviceHistoryManager.saveDevice(device)
                }

                service.onDisconnected = {
                    ConnectionLogger.log("❌ 與伺服器中斷連線", ConnectionLogger.LogLevel.WARNING)
                }

                // 透過服務建立連線
                service.connectTcp(host, port)

                // ✅ 等待連線真正完成（Connected 或 Error）
                val finalState = service.connectionState.first { state ->
                    state is com.unifiedremote.evo.service.ServiceConnectionState.Connected ||
                    state is com.unifiedremote.evo.service.ServiceConnectionState.Error
                }

                when (finalState) {
                    is com.unifiedremote.evo.service.ServiceConnectionState.Connected -> {
                        ConnectionLogger.log("✅ TCP 連線成功", ConnectionLogger.LogLevel.INFO)

                        // 從服務取得連線管理器並建立控制器
                        val manager = service.getUnifiedConnectionManager()
                        if (manager != null) {
                            connectionManager = manager
                            val mouse = MouseController(manager)
                            val keyboard = KeyboardController(manager)
                            val deviceId = "tcp_${host}_${port}"
                            onSuccess(mouse, keyboard, deviceId)
                        } else {
                            ConnectionLogger.log("❌ 無法取得連線管理器", ConnectionLogger.LogLevel.ERROR)
                            onFailure("無法取得連線管理器")
                        }
                    }
                    is com.unifiedremote.evo.service.ServiceConnectionState.Error -> {
                        ConnectionLogger.log("❌ TCP 連線失敗: ${finalState.message}", ConnectionLogger.LogLevel.ERROR)
                        onFailure(finalState.message)
                    }
                    else -> {
                        ConnectionLogger.log("❌ TCP 連線失敗：未知狀態", ConnectionLogger.LogLevel.ERROR)
                        onFailure("連線狀態未知")
                    }
                }
            } catch (e: Exception) {
                ConnectionLogger.log("❌ TCP 連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
                onFailure(e.message ?: "未知錯誤")
            }
        }
    }

    /**
     * 藍牙連線（透過背景服務）
     */
    @SuppressLint("MissingPermission")
    private fun connectBluetooth(
        device: BluetoothDevice,
        onSuccess: (MouseController, KeyboardController, String) -> Unit,
        onFailure: (String) -> Unit = { reason ->
            ConnectionLogger.log("❌ 藍牙連線失敗: $reason", ConnectionLogger.LogLevel.ERROR)
        }
    ) {
        lifecycleScope.launch {
            try {
                val service = remoteControlService
                if (service == null) {
                    ConnectionLogger.log("❌ 背景服務尚未準備好", ConnectionLogger.LogLevel.ERROR)
                    return@launch
                }

                ConnectionLogger.log("🔄 正在透過藍牙連線到 ${device.name} (${device.address})...", ConnectionLogger.LogLevel.INFO)

                // 設定連線回呼
                service.onConnected = {
                    ConnectionLogger.log("✅ 已連線到伺服器", ConnectionLogger.LogLevel.INFO)
                    // 儲存裝置至歷史
                    val savedDevice = SavedDevice.createBluetooth(
                        address = device.address,
                        name = device.name ?: "未命名藍牙裝置"
                    )
                    deviceHistoryManager.saveDevice(savedDevice)
                }

                service.onDisconnected = {
                    ConnectionLogger.log("❌ 與伺服器中斷連線", ConnectionLogger.LogLevel.WARNING)
                }

                // 透過服務建立連線
                service.connectBluetooth(device)

                // ✅ 等待連線真正完成（Connected 或 Error）
                val finalState = service.connectionState.first { state ->
                    state is com.unifiedremote.evo.service.ServiceConnectionState.Connected ||
                    state is com.unifiedremote.evo.service.ServiceConnectionState.Error
                }

                when (finalState) {
                    is com.unifiedremote.evo.service.ServiceConnectionState.Connected -> {
                        ConnectionLogger.log("✅ 藍牙連線成功", ConnectionLogger.LogLevel.INFO)

                        // 從服務取得連線管理器並建立控制器
                        val manager = service.getUnifiedConnectionManager()
                        if (manager != null) {
                            connectionManager = manager
                            val mouse = MouseController(manager)
                            val keyboard = KeyboardController(manager)
                            val deviceId = "bt_${device.address}"
                            onSuccess(mouse, keyboard, deviceId)
                        } else {
                            ConnectionLogger.log("❌ 無法取得連線管理器", ConnectionLogger.LogLevel.ERROR)
                            onFailure("無法取得連線管理器")
                        }
                    }
                    is com.unifiedremote.evo.service.ServiceConnectionState.Error -> {
                        ConnectionLogger.log("❌ 藍牙連線失敗: ${finalState.message}", ConnectionLogger.LogLevel.ERROR)
                        // ⚠️ 不呼叫 onSuccess，保持在設定畫面
                    }
                    else -> {
                        ConnectionLogger.log("❌ 藍牙連線失敗：未知狀態", ConnectionLogger.LogLevel.ERROR)
                        onFailure("連線狀態未知")
                    }
                }
            } catch (e: Exception) {
                ConnectionLogger.log("❌ 藍牙連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
                onFailure(e.message ?: "未知錯誤")
            }
        }
    }

    /**
     * BLE EmulStick 連線（使用 MAC 地址）
     * ✅ 改用 ViewModel 的 BleManager 進行連線
     */
    @SuppressLint("MissingPermission")
    private fun connectBleEmulstickDeviceByAddress(
        address: String,
        onSuccess: (MouseController, KeyboardController, String) -> Unit,
        onFailure: (String) -> Unit = { reason ->
            ConnectionLogger.log("❌ BLE 自動連線失敗: $reason", ConnectionLogger.LogLevel.ERROR)
        }
    ) {
        lifecycleScope.launch {
            try {
                ConnectionLogger.log("🔄 正在連線到 $address...", ConnectionLogger.LogLevel.INFO)

                // ✅ 使用 ViewModel 的連線方法
                bleViewModel.connectToDevice(address)

                // 等待連線完成
                val finalState = bleViewModel.uiState.first { state ->
                    state.connectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Connected ||
                    state.connectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Error
                }

                when (val connState = finalState.connectionState) {
                    is com.unifiedremote.evo.network.ble.BleConnectionState.Connected -> {
                        ConnectionLogger.log("✅ 已連線到 ${connState.deviceName} (${connState.deviceAddress})", ConnectionLogger.LogLevel.INFO)

                        // ✅ 智慧名稱處理：
                        // 1. 如果新名稱是「未知裝置」，保留已儲存的正確名稱
                        // 2. 如果已儲存的是「未知裝置」，更新為新的正確名稱（自動修復）
                        val existingDevice = deviceHistoryManager.getAllDevices()
                            .firstOrNull { it.bluetoothAddress == connState.deviceAddress }

                        val deviceName = when {
                            // 情況 1：新名稱是「未知裝置」，但已有正確名稱 → 保留舊名稱
                            connState.deviceName == "未知裝置" && existingDevice != null && existingDevice.name != "未知裝置" -> {
                                ConnectionLogger.log("保留已儲存的裝置名稱：${existingDevice.name}", ConnectionLogger.LogLevel.DEBUG)
                                existingDevice.name
                            }
                            // 情況 2：已儲存的是「未知裝置」，但新名稱正確 → 更新為新名稱（自動修復）
                            existingDevice?.name == "未知裝置" && connState.deviceName != "未知裝置" -> {
                                ConnectionLogger.log("✅ 自動修復：將「未知裝置」更新為 ${connState.deviceName}", ConnectionLogger.LogLevel.INFO)
                                connState.deviceName
                            }
                            // 情況 3：使用新名稱
                            else -> connState.deviceName
                        }

                        // 儲存裝置至歷史
                        val savedDevice = SavedDevice.createBleEmulstick(
                            deviceName = deviceName,
                            address = connState.deviceAddress
                        )
                        deviceHistoryManager.saveDevice(savedDevice)

                        // 建立控制器（使用 ViewModel 的 BLE 管理器）
                        val bleMouse = com.unifiedremote.evo.network.ble.BleMouseController(bleViewModel.bleManager)
                        val bleKeyboard = com.unifiedremote.evo.network.ble.BleKeyboardController(bleViewModel.bleManager)

                        // 建立 dummy connection（僅用於滿足建構子，不會實際使用）
                        val dummyConnection = UnifiedConnectionManager(this@MainActivity)

                        // 使用適配器包裝 BLE 控制器，提供標準介面
                        val mouseAdapter = com.unifiedremote.evo.network.ble.BleMouseControllerAdapter(bleMouse, dummyConnection)
                        val keyboardAdapter = com.unifiedremote.evo.network.ble.BleKeyboardControllerAdapter(bleKeyboard, dummyConnection)

                        val deviceId = "ble_${connState.deviceAddress}"
                        onSuccess(mouseAdapter, keyboardAdapter, deviceId)
                    }
                    is com.unifiedremote.evo.network.ble.BleConnectionState.Error -> {
                        ConnectionLogger.log("❌ BLE 連線錯誤: ${connState.message}", ConnectionLogger.LogLevel.ERROR)
                        onFailure(connState.message)
                    }
                    else -> {
                        ConnectionLogger.log("❌ BLE 連線失敗：未知狀態", ConnectionLogger.LogLevel.ERROR)
                        onFailure("連線狀態未知")
                    }
                }
            } catch (e: Exception) {
                ConnectionLogger.log("❌ BLE 連線失敗: ${e.message}", ConnectionLogger.LogLevel.ERROR)
                onFailure(e.message ?: "未知錯誤")
            }
        }
    }

    /**
     * 連線至已儲存的裝置
     */
    @SuppressLint("MissingPermission")
    private fun connectSaved(
        device: SavedDevice,
        onSuccess: (MouseController, KeyboardController, String) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        when (device.type) {
            com.unifiedremote.evo.network.ConnectionType.TCP -> {
                val host = device.host ?: return
                val port = device.port ?: 9512
                connectTcp(host, port, onSuccess, onFailure)
            }
            com.unifiedremote.evo.network.ConnectionType.BLUETOOTH -> {
                val address = device.bluetoothAddress ?: return
                // 取得藍牙裝置
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val bluetoothDevice = adapter?.getRemoteDevice(address)
                if (bluetoothDevice != null) {
                    connectBluetooth(bluetoothDevice, onSuccess, onFailure)
                } else {
                    ConnectionLogger.log("找不到藍牙裝置: $address", ConnectionLogger.LogLevel.ERROR)
                }
            }
            com.unifiedremote.evo.network.ConnectionType.BLE_EMULSTICK -> {
                // ✅ 已儲存的 BLE 裝置，直接用 MAC 地址連線
                val address = device.bluetoothAddress ?: return
                connectBleEmulstickDeviceByAddress(address, onSuccess, onFailure)
            }
        }
    }

    private fun disconnectFromServer() {
        // TCP/藍牙透過背景服務斷線
        remoteControlService?.disconnectAll()
        connectionManager = null

        // BLE 仍然透過 ViewModel 中斷連線
        bleViewModel.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager?.close()
        // ✅ BLE 由 ViewModel 管理，會在 ViewModel.onCleared() 時自動清理

        // 解除綁定背景服務
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * 啟動並綁定背景服務
     */
    private fun startAndBindService() {
        if (!serviceStartRequested) {
            RemoteControlService.start(this)
            serviceStartRequested = true
        }

        if (!serviceBound) {
            val intent = Intent(this, RemoteControlService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun UnifiedRemoteEvoApp(
    onConnectTcp: (String, Int) -> Unit,
    onConnectBluetooth: (BluetoothDevice) -> Unit,
    onStartBleScan: () -> Unit,
    // ✅ 改用 MAC 地址而不是 BluetoothDevice
    onConnectBleDevice: (String) -> Unit,
    onConnectSaved: (SavedDevice) -> Unit,
    onRemoveSaved: (SavedDevice) -> Unit,
    onDisconnect: () -> Unit,
    onXInputModeChange: (Boolean) -> Unit,
    bluetoothAvailable: Boolean,
    bleAvailable: Boolean,
    // ✅ 改用 SavedDevice 而不是 BluetoothDevice
    bleScannedDevices: List<SavedDevice>,
    bleConnectionState: com.unifiedremote.evo.network.ble.BleConnectionState,
    bleViewModel: BleViewModel,
    savedDevices: List<SavedDevice>,
    autoConnectStatus: String?,
    mouseController: MouseController?,
    keyboardController: KeyboardController?,
    sensitivityManager: SensitivityManager,
    themeManager: ThemeManager,
    connectionManager: UnifiedConnectionManager?,
    currentDeviceId: String?
) {
    var currentScreen by remember { mutableStateOf(Screen.CONFIG) }
    val isConnected = mouseController != null && keyboardController != null
    val sensitivitySettings = sensitivityManager.settings
    val themeMode = themeManager.themeMode

    // 取得已配對的藍牙裝置（僅在藍牙可用時）
    val pairedDevices = remember(bluetoothAvailable) {
        if (bluetoothAvailable) {
            UnifiedConnectionManager.getPairedBluetoothDevices()
        } else {
            emptyList()
        }
    }

    when (currentScreen) {
        Screen.CONFIG -> {
            if (isConnected) {
                currentScreen = Screen.TOUCHPAD
            } else {
                ServerConfigScreen(
                    onConnectTcp = { host, port ->
                        onConnectTcp(host, port)
                    },
                    onConnectBluetooth = { device ->
                        onConnectBluetooth(device)
                    },
                    onStartBleScan = {
                        onStartBleScan()
                    },
                    onConnectBleDevice = { device ->
                        onConnectBleDevice(device)
                    },
                    onConnectSaved = { device ->
                        onConnectSaved(device)
                    },
                    onRemoveSaved = { device ->
                        onRemoveSaved(device)
                    },
                    bluetoothAvailable = bluetoothAvailable,
                    bleAvailable = bleAvailable,
                    pairedDevices = pairedDevices,
                    bleScannedDevices = bleScannedDevices,
                    bleConnectionState = bleConnectionState,
                    savedDevices = savedDevices,
                    autoConnectStatus = autoConnectStatus
                )
            }
        }
        Screen.TOUCHPAD -> {
            if (isConnected && mouseController != null && keyboardController != null) {
                TouchpadScreen(
                    mouseController = mouseController,
                    keyboardController = keyboardController,
                    mouseSensitivity = sensitivitySettings.mouseSensitivity,
                    verticalScrollSensitivity = sensitivitySettings.verticalScrollSensitivity,
                    horizontalScrollSensitivity = sensitivitySettings.horizontalScrollSensitivity,
                    onShowDebug = { currentScreen = Screen.DEBUG },
                    onShowSettings = { currentScreen = Screen.SETTINGS },
                    onDisconnect = {
                        onDisconnect()
                        currentScreen = Screen.CONFIG
                    },
                    // 連線狀態（三模式）
                    tcpConnectionState = connectionManager?.getTcpConnectionState(),
                    bluetoothConnectionState = connectionManager?.getBluetoothConnectionState(),
                    // BLE XInput 支援（傳遞 ViewModel）
                    bleViewModel = if (bleConnectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Connected) {
                        bleViewModel
                    } else null,
                    onXInputModeChange = onXInputModeChange,
                    // 裝置切換支援
                    savedDevices = savedDevices,
                    currentDeviceId = currentDeviceId,
                    onSwitchDevice = { device ->
                        // 先中斷當前連線
                        onDisconnect()
                        // 連線至新裝置
                        onConnectSaved(device)
                    }
                )
            } else {
                currentScreen = Screen.CONFIG
            }
        }
        Screen.SETTINGS -> {
            SensitivitySettingsScreen(
                currentSettings = sensitivitySettings,
                onSettingsChange = { newSettings ->
                    sensitivityManager.saveSettings(newSettings)
                },
                currentThemeMode = themeMode,
                onThemeModeChange = { newMode ->
                    themeManager.saveThemeMode(newMode)
                },
                onBack = { currentScreen = Screen.TOUCHPAD }
            )
        }
        Screen.DEBUG -> {
            DebugScreen(
                onBack = { currentScreen = Screen.TOUCHPAD },
                bleViewModel = if (bleConnectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Connected) {
                    bleViewModel
                } else {
                    null
                }
            )
        }
    }
}

enum class Screen {
    CONFIG,
    TOUCHPAD,
    SETTINGS,
    DEBUG
}
