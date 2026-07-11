package com.unifiedremote.evo.network.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.unifiedremote.evo.network.ConnectionLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LED 狀態資料類別（NumLock/CapsLock/ScrollLock）
 *
 * 用於追蹤 PC 端的 LED 狀態，透過 BLE notification 從 EmulStick 接收器取得。
 *
 * 參考：原廠 KbStatus.java 第 25-94 行
 */
data class LedStatus(
    val numLock: Boolean = false,
    val capsLock: Boolean = false,
    val scrollLock: Boolean = false
) {
    companion object {
        const val NUM_LOCK_BIT = 0x01
        const val CAPS_LOCK_BIT = 0x02
        const val SCROLL_LOCK_BIT = 0x04

        /**
         * 從 byte 解析 LED 狀態
         *
         * 格式（1 byte）：
         * - Bit 0 (0x01): NumLock
         * - Bit 1 (0x02): CapsLock
         * - Bit 2 (0x04): ScrollLock
         * - Bit 3-7: 保留
         *
         * 參考：原廠 KbStatus.java setLedStatus() 方法
         */
        fun fromByte(value: Byte): LedStatus {
            val intValue = value.toInt() and 0xFF
            return LedStatus(
                numLock = (intValue and NUM_LOCK_BIT) != 0,
                capsLock = (intValue and CAPS_LOCK_BIT) != 0,
                scrollLock = (intValue and SCROLL_LOCK_BIT) != 0
            )
        }
    }
}

/**
 * EmulStick 硬體型號列舉
 *
 * 用於識別連線裝置的硬體類型，決定使用何種輸入模式。
 */
enum class EmulStickHardware {
    /** 原廠 TI CC2650（僅支援 Big5 Alt 碼） */
    ORIGINAL_TI,

    /** 原廠 WCH CH582（僅支援 Big5 Alt 碼） */
    ORIGINAL_WCH,

    /** ESP32-S3 Evo（支援 BLE CH5 Unicode transport）*/
    ESP32S3_EVO,

    /** 未知硬體（預設為原廠模式） */
    UNKNOWN;

    /**
     * 是否支援 BLE CH5 Unicode transport
     */
    fun supportsCH5UnicodeTransport(): Boolean {
        return this == ESP32S3_EVO
    }

    /**
     * 取得硬體名稱（UI 顯示用）
     */
    fun getDisplayName(): String {
        return when (this) {
            ORIGINAL_TI -> "原廠 TI"
            ORIGINAL_WCH -> "原廠 WCH"
            ESP32S3_EVO -> "ESP32-S3 Evo"
            UNKNOWN -> "未知裝置"
        }
    }
}

/**
 * BLE 連線管理器
 *
 * 負責 EmulStick 藍牙裝置的掃描、連線、資料傳輸
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val MAX_CONNECT_RETRIES = 3
    }

    // ============ BLE Action Queue（Handler 佇列機制，模仿原廠實作） ============

    /**
     * BLE Action（GATT 操作封裝）
     *
     * 用於佇列中的操作類型：
     * - WriteCharacteristic: GATT 寫入操作
     * - Delay: 延遲操作
     */
    private sealed class BleAction {
        data class WriteCharacteristic(
            val data: ByteArray,
            val characteristic: BluetoothGattCharacteristic
        ) : BleAction() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as WriteCharacteristic
                if (!data.contentEquals(other.data)) return false
                if (characteristic != other.characteristic) return false
                return true
            }

            override fun hashCode(): Int {
                var result = data.contentHashCode()
                result = 31 * result + characteristic.hashCode()
                return result
            }
        }

        data class Delay(val milliseconds: Long) : BleAction()
    }

    /**
     * BLE Action Queue（Handler 佇列機制）
     *
     * 模仿原廠 EmulStick APK 的 MsgHandler 機制（BluetoothLeService.java:1008-1052）
     *
     * 工作原理：
     * 1. 所有 GATT 操作（寫入特徵值、延遲）加入佇列
     * 2. Handler 在主執行緒中循序處理
     * 3. 每次處理一個操作，確保 100% 的執行順序
     * 4. 遇到延遲操作，直接 Thread.sleep()
     * 5. GATT 寫入完成後，透過 onWriteComplete() 通知佇列繼續
     *
     * 目的：
     * - 解決 GATT 報告丟失問題（Alt 碼輸入錯誤）
     * - 確保所有 HID 報告依序傳送，不會因為 GATT 佇列溢出而丟失
     * - 與原廠行為完全一致
     */
    private inner class BleActionQueue {
        private val queue = mutableListOf<BleAction>()
        private val handler = Handler(Looper.getMainLooper())
        @Volatile
        private var isProcessing = false

        fun enqueue(action: BleAction) {
            synchronized(queue) {
                queue.add(action)
                Log.d(TAG, "🔄 佇列加入操作：${actionToString(action)}，佇列長度=${queue.size}")
                if (!isProcessing) {
                    processNext()
                }
            }
        }

        fun onWriteComplete(success: Boolean) {
            synchronized(queue) {
                if (!success) {
                    Log.e(TAG, "❌ GATT 寫入失敗，清空佇列")
                    queue.clear()
                    isProcessing = false
                    return
                }

                // 移除已完成的操作
                if (queue.isNotEmpty()) {
                    val completedAction = queue.removeAt(0)
                    Log.d(TAG, "✅ 操作完成並移除：${actionToString(completedAction)}，剩餘 ${queue.size} 個操作")
                }

                // 處理下一個
                processNext()
            }
        }

        private fun processNext() {
            synchronized(queue) {
                // 處理所有連續的延遲指令（與原廠完全相同）
                while (queue.isNotEmpty() && queue[0] is BleAction.Delay) {
                    val delay = queue.removeAt(0) as BleAction.Delay
                    Log.d(TAG, "⏱️ 執行延遲：${delay.milliseconds}ms")
                    Thread.sleep(delay.milliseconds)
                }

                // 處理下一個寫入操作
                if (queue.isNotEmpty()) {
                    val action = queue[0]
                    when (action) {
                        is BleAction.WriteCharacteristic -> {
                            isProcessing = true
                            Log.d(TAG, "📤 處理寫入操作：${actionToString(action)}")
                            val success = performWrite(action.characteristic, action.data)
                            if (!success) {
                                Log.e(TAG, "❌ GATT 寫入失敗，清空佇列")
                                queue.clear()
                                isProcessing = false
                            } else {
                                // 如果成功，會在 onCharacteristicWrite callback 中呼叫 onWriteComplete()
                            }
                        }
                        is BleAction.Delay -> {
                            // 已在上面的 while 迴圈處理
                        }
                    }
                } else {
                    isProcessing = false
                    Log.d(TAG, "✅ 佇列已清空，處理完成")
                }
            }
        }

        private fun performWrite(
            characteristic: BluetoothGattCharacteristic,
            data: ByteArray
        ): Boolean {
            val gatt = bluetoothGatt ?: return false
            characteristic.value = data
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            return gatt.writeCharacteristic(characteristic)
        }

        private fun actionToString(action: BleAction): String {
            return when (action) {
                is BleAction.WriteCharacteristic -> {
                    val charName = when (action.characteristic.uuid) {
                        GattConstants.CHAR_CH1 -> "CH1(鍵盤)"
                        GattConstants.CHAR_CH3 -> "CH3(滑鼠)"
                        else -> "未知"
                    }
                    "寫入$charName [${action.data.joinToString(" ") { "%02X".format(it) }}]"
                }
                is BleAction.Delay -> "延遲 ${action.milliseconds}ms"
            }
        }
    }

    private val actionQueue = BleActionQueue()

    // ============ 狀態管理 ============

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    // 掃描到的裝置列表（使用 SavedDevice 包裝）
    private val _scannedDevices = MutableStateFlow<List<com.unifiedremote.evo.data.SavedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<com.unifiedremote.evo.data.SavedDevice>> = _scannedDevices.asStateFlow()

    // LED 狀態（NumLock/CapsLock/ScrollLock）
    // 透過 BLE notification 從 EmulStick 接收器取得 PC 端的 LED 狀態
    private val _ledStatus = MutableStateFlow(LedStatus())
    val ledStatus: StateFlow<LedStatus> = _ledStatus.asStateFlow()

    // ✅ 裝置名稱快取（MAC 地址 → 裝置名稱）
    // 用於保存掃描時取得的裝置名稱，避免直接連線時名稱遺失
    private val deviceNameCache = mutableMapOf<String, String>()


    // ============ 藍牙元件 ============

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var ch1Characteristic: BluetoothGattCharacteristic? = null
    private var ch2Characteristic: BluetoothGattCharacteristic? = null  // CustomIn 報告（Direct 模式）
    private var ch3Characteristic: BluetoothGattCharacteristic? = null  // MouseV1 用（Ver ≥1）
    private var ch5UnicodeCharacteristic: BluetoothGattCharacteristic? = null  // Unicode 輸入（僅 ESP32-S3）
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastConnectAddress: String? = null
    private var connectRetryCount = 0
    private var connectionTimeoutRunnable: Runnable? = null

    private fun registerConnectAttempt(address: String): Boolean {
        if (lastConnectAddress != address) {
            lastConnectAddress = address
            connectRetryCount = 0
        }

        if (connectRetryCount >= MAX_CONNECT_RETRIES) {
            Log.w(TAG, "已達最大連線重試次數 ($MAX_CONNECT_RETRIES)")
            ConnectionLogger.log(
                "⚠️ BLE 連線失敗：已達最大重試次數 ($MAX_CONNECT_RETRIES)",
                ConnectionLogger.LogLevel.ERROR
            )
            _connectionState.value = BleConnectionState.Error("連線失敗：已達最大重試次數")
            return false
        }

        connectRetryCount++
        return true
    }

    private fun resetConnectRetryState() {
        connectRetryCount = 0
        lastConnectAddress = null
    }

    private fun scheduleConnectionTimeout(device: BluetoothDevice) {
        cancelConnectionTimeout()
        val runnable = Runnable { onConnectionTimeout(device) }
        connectionTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        connectionTimeoutRunnable = null
    }

    private fun onConnectionTimeout(device: BluetoothDevice) {
        connectionTimeoutRunnable = null
        Log.w(TAG, "BLE 連線逾時: ${device.address}")
        ConnectionLogger.log(
            "⚠️ BLE 連線逾時：${device.address}",
            ConnectionLogger.LogLevel.WARNING
        )
        _connectionState.value = BleConnectionState.Error("連線逾時，請稍後再試")
        bluetoothGatt?.disconnect()
    }

    // ============ 身份驗證狀態 ============

    /** System ID (8 bytes)，用於生成 AES 密鑰 */
    private var systemId: ByteArray? = null

    /** Firmware Version（韌體版本） */
    internal var firmwareVersion: String? = null

    /** Hardware Revision（硬體版本，UI 顯示用） */
    private var hardwareVersion: String? = null

    /** Software Version（軟體版本，身份驗證用，例如 "1.0" 或 "2.0"） */
    internal var softwareVersion: String? = null

    /** PNP ID（7 bytes），用於判斷廠商（TI/WCH）和版本 */
    internal var pnpId: ByteArray? = null

    /** 硬體型號（根據 Device Information 偵測） */
    private var hardwareType: EmulStickHardware = EmulStickHardware.UNKNOWN

    /** 驗證是否完成 */
    private var isAuthenticationComplete = false
    @Volatile private var clipboardBridgeQuery: CompletableDeferred<Boolean>? = null
    private val pendingClipboardStatuses = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
    private val nextClipboardMessageId = AtomicInteger(1)

    // 掃描逾時（單段掃描，與原廠一致）
    private val TOTAL_SCAN_DURATION_MS = 4000L  // 4 秒

    // 掃描狀態
    private var isScanning = false

    // ============ 掃描與連線 ============

    /**
     * 開始掃描 BLE 裝置（單段掃描，模仿原廠）
     */
    fun startScan() {
        ConnectionLogger.log("🎯 進入 BleManager.startScan()", ConnectionLogger.LogLevel.DEBUG)

        // 1. 檢查硬體支援
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            val errorMsg = "裝置不支援 BLE"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg)
            return
        }

        // 2. 檢查藍牙適配器
        if (bluetoothAdapter == null) {
            val errorMsg = "找不到藍牙適配器"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg)
            return
        }

        ConnectionLogger.log(
            "📋 BluetoothAdapter 狀態：isEnabled=${bluetoothAdapter?.isEnabled}, " +
                    "address=${bluetoothAdapter?.address}",
            ConnectionLogger.LogLevel.DEBUG
        )

        // 3. 檢查藍牙是否啟用
        if (!bluetoothAdapter!!.isEnabled) {
            val errorMsg = "藍牙未啟用"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg，請先在系統設定中啟用藍牙", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg)
            return
        }

        // 4. 檢查是否正在掃描
        if (isScanning) {
            ConnectionLogger.log("⚠️ 已在掃描中，忽略重複請求", ConnectionLogger.LogLevel.WARNING)
            Log.w(TAG, "已在掃描中，忽略重複請求")
            return
        }

        // 5. 檢查 BluetoothLeScanner
        if (bluetoothLeScanner == null) {
            val errorMsg = "無法取得 BluetoothLeScanner"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg)
            return
        }

        ConnectionLogger.log("✅ 所有前置檢查通過，準備開始掃描", ConnectionLogger.LogLevel.DEBUG)

        // 6. 立即從配對清單取得 EmulStick 裝置（不需要掃描）
        val bondedDevices = getBondedEmulStickDevices()
        _scannedDevices.value = bondedDevices
        ConnectionLogger.log(
            "📋 從配對清單找到 ${bondedDevices.size} 個 EmulStick 裝置",
            ConnectionLogger.LogLevel.INFO
        )
        if (bondedDevices.isNotEmpty()) {
            bondedDevices.forEach { device ->
                ConnectionLogger.log(
                    "✓ 已配對裝置：${device.name} (${device.bluetoothAddress})",
                    ConnectionLogger.LogLevel.INFO
                )
            }
        }

        // 7. 啟動 BLE 掃描（尋找新裝置或正在廣播的裝置）
        startSingleScan()
    }

    /**
     * 開始單段掃描（模仿原廠 BleScan.kt）
     */
    private fun startSingleScan() {
        isScanning = true
        _connectionState.value = BleConnectionState.Scanning

        ConnectionLogger.log(
            "🔄 開始掃描（${TOTAL_SCAN_DURATION_MS}ms）...",
            ConnectionLogger.LogLevel.INFO
        )
        Log.i(TAG, "開始掃描：無過濾器（與原廠診斷 APP 一致）")

        // 掃描設定（與原廠完全相同）
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // mode = 1
            .build()

        try {
            // 使用無過濾器（與原廠診斷 APP 完全相同）
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            ConnectionLogger.log("✅ 掃描已啟動", ConnectionLogger.LogLevel.INFO)
        } catch (e: SecurityException) {
            val errorMsg = "權限錯誤：${e.message}"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg, e)
            isScanning = false
            return
        } catch (e: Exception) {
            val errorMsg = "掃描啟動失敗：${e.message}"
            _connectionState.value = BleConnectionState.Error(errorMsg)
            ConnectionLogger.log("❌ $errorMsg", ConnectionLogger.LogLevel.ERROR)
            Log.e(TAG, errorMsg, e)
            isScanning = false
            return
        }

        // 4 秒後停止掃描（與原廠完全相同）
        mainHandler.postDelayed({
            onScanComplete()
        }, TOTAL_SCAN_DURATION_MS)
    }

    /**
     * 掃描完成
     */
    private fun onScanComplete() {
        isScanning = false
        stopScan()

        val deviceCount = _scannedDevices.value.size
        ConnectionLogger.log(
            "✅ 掃描完成：總共找到 $deviceCount 個裝置",
            ConnectionLogger.LogLevel.INFO
        )

        if (deviceCount == 0) {
            _connectionState.value = BleConnectionState.Error("找不到 BLE 裝置")
            ConnectionLogger.log("❌ 未找到任何 EmulStick 裝置", ConnectionLogger.LogLevel.WARNING)
        } else {
            _connectionState.value = BleConnectionState.Disconnected
            ConnectionLogger.log(
                "✅ 找到 $deviceCount 個 EmulStick 裝置",
                ConnectionLogger.LogLevel.INFO
            )
        }
    }

    /**
     * 停止掃描
     */
    fun stopScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "停止掃描")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceAddress = device.address
            val rssi = result.rssi

            // 取得 Service UUIDs（如果有）
            val scanRecord = result.scanRecord
            val serviceUuids = scanRecord?.serviceUuids?.joinToString(", ") { it.toString() } ?: "無"

            // ⚠️ 重要修正：直接使用 device.name（與診斷 APP 完全相同）
            val deviceName = device.name

            // 🔍 診斷：記錄所有掃描到的裝置（包括非 EmulStick）
            ConnectionLogger.log(
                "🔍 掃描到裝置: ${deviceName ?: "[名稱為null]"} ($deviceAddress) RSSI=$rssi Service UUIDs=[$serviceUuids]",
                ConnectionLogger.LogLevel.DEBUG
            )
            Log.d(TAG, "掃描到: ${deviceName ?: "null"} ($deviceAddress) RSSI=$rssi")

            // 過濾條件 1：檢查名稱是否為 null（與診斷 APP 完全相同）
            if (deviceName == null) {
                ConnectionLogger.log(
                    "❌ 忽略裝置（名稱為 null）: ($deviceAddress)",
                    ConnectionLogger.LogLevel.DEBUG
                )
                Log.v(TAG, "忽略裝置（名稱為 null）: ($deviceAddress)")
                return
            }

            // 過濾條件 2：檢查名稱是否包含 "emulstick"（與診斷 APP 完全相同）
            if (!deviceName.trim().contains("emulstick", ignoreCase = true)) {
                ConnectionLogger.log(
                    "❌ 忽略裝置（名稱不符合）: $deviceName ($deviceAddress)",
                    ConnectionLogger.LogLevel.DEBUG
                )
                Log.v(TAG, "忽略裝置（名稱不符合）: $deviceName ($deviceAddress)")
                return
            }

            // ✅ 找到 EmulStick 裝置
            Log.d(TAG, "✅ 發現 EmulStick 裝置: $deviceName ($deviceAddress)")
            ConnectionLogger.log(
                "✅ 符合條件：$deviceName ($deviceAddress)",
                ConnectionLogger.LogLevel.INFO
            )

            // ✅ 將裝置名稱加入快取（避免直接連線時名稱遺失）
            deviceNameCache[deviceAddress] = deviceName

            // ✅ 使用工廠方法建立 SavedDevice
            val savedDevice = com.unifiedremote.evo.data.SavedDevice.createBleEmulstick(
                deviceName = deviceName,
                address = deviceAddress
            )

            val currentList = _scannedDevices.value
            if (!currentList.any { it.bluetoothAddress == deviceAddress }) {
                _scannedDevices.value = currentList + savedDevice
                Log.d(TAG, "新 EmulStick 裝置加入列表，目前共 ${_scannedDevices.value.size} 個")
                ConnectionLogger.log(
                    "✅ 新裝置加入列表，目前共 ${_scannedDevices.value.size} 個",
                    ConnectionLogger.LogLevel.INFO
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED(1)"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REG_FAILED(2)"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED(4)"
                SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR(3)"
                5 -> "OUT_OF_HARDWARE_RESOURCES(5)"
                6 -> "SCANNING_TOO_FREQUENTLY(6)"
                else -> "UNKNOWN($errorCode)"
            }

            Log.e(TAG, "掃描失敗: $errorMsg")
            ConnectionLogger.log("❌ BLE 掃描失敗: $errorMsg", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = BleConnectionState.Error("掃描失敗: $errorMsg")
            isScanning = false
        }
    }

    /**
     * 連線到指定裝置（使用 MAC 地址）
     */
    fun connect(address: String): Boolean {
        stopScan()
        return connectByAddress(address)
    }

    /**
     * 直接用 MAC 地址連線（不需要掃描）
     *
     * 這是 EmulStick 建議的連線方式，不依賴 BLE 掃描
     *
     * @param address MAC 地址（例如："60:B6:E1:B4:6A:76"）
     * @return 是否成功開始連線
     */
    fun connectByAddress(address: String): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = BleConnectionState.Error("藍牙未啟用")
            return false
        }

        stopScan()

        if (!registerConnectAttempt(address)) {
            return false
        }

        try {
            // 直接用 MAC 地址取得 BluetoothDevice（不需要掃描）
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            Log.d(TAG, "準備連線到裝置: $address")
            connectToDevice(device)
            return true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "無效的 MAC 地址: $address", e)
            _connectionState.value = BleConnectionState.Error("無效的 MAC 地址")
            return false
        }
    }

    /**
     * 連線到指定裝置（內部方法）
     */
    private fun connectToDevice(device: BluetoothDevice) {
        // ✅ 優先使用快取的裝置名稱
        val deviceName = device.name
            ?: deviceNameCache[device.address]
            ?: "未知裝置"

        _connectionState.value = BleConnectionState.Connecting(deviceName)
        Log.d(TAG, "連線到裝置: $deviceName (${device.address})")

        cancelConnectionTimeout()
        bluetoothGatt = device.connectGatt(
            context,
            false,  // autoConnect = false（立即連線）
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
        scheduleConnectionTimeout(device)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    cancelConnectionTimeout()
                    Log.d(TAG, "已連線到 GATT 伺服器，開始探索服務...")

                    // 設定高優先權（提升效能，與 EmulStick 原始實作一致）
                    val prioritySet = gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    Log.d(TAG, "設定連線優先權: ${if (prioritySet) "成功" else "失敗"}")

                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    cancelConnectionTimeout()
                    Log.d(TAG, "已中斷 GATT 連線")
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "服務探索成功")
                ConnectionLogger.log("📡 服務探索成功，開始身份驗證流程", ConnectionLogger.LogLevel.INFO)

                // 尋找 EmulStick 主服務
                val emulstickService = gatt.getService(GattConstants.SERVICE_EMULSTICK)
                if (emulstickService == null) {
                    Log.e(TAG, "找不到 EmulStick 服務")
                    ConnectionLogger.log("❌ 找不到 EmulStick 服務", ConnectionLogger.LogLevel.ERROR)
                    _connectionState.value = BleConnectionState.Error("找不到 EmulStick 服務")
                    disconnect()
                    return
                }

                // 取得 CH1 特徵值（舊版滑鼠/鍵盤，Ver 0/-1）
                ch1Characteristic = emulstickService.getCharacteristic(GattConstants.CHAR_CH1)
                if (ch1Characteristic == null) {
                    Log.e(TAG, "找不到 CH1 特徵值")
                    ConnectionLogger.log("❌ 找不到 CH1 特徵值", ConnectionLogger.LogLevel.ERROR)
                    _connectionState.value = BleConnectionState.Error("找不到 CH1 特徵值")
                    disconnect()
                    return
                }

                // 取得 CH3 特徵值（新版 MouseV1，Ver ≥1）
                ch3Characteristic = emulstickService.getCharacteristic(GattConstants.CHAR_CH3)
                if (ch3Characteristic == null) {
                    Log.e(TAG, "找不到 CH3 特徵值")
                    ConnectionLogger.log("❌ 找不到 CH3 特徵值", ConnectionLogger.LogLevel.ERROR)
                    _connectionState.value = BleConnectionState.Error("找不到 CH3 特徵值")
                    disconnect()
                    return
                }
                Log.d(TAG, "✅ 已取得 CH3 特徵值（MouseV1 用）")
                ConnectionLogger.log("✅ 已取得 CH3 特徵值（MouseV1 用）", ConnectionLogger.LogLevel.INFO)

                // 嘗試取得 CH2 特徵值（CustomIn 報告，Direct 模式）
                // CH2 為選用功能，如果不支援也不影響基本操作
                ch2Characteristic = emulstickService.getCharacteristic(GattConstants.CHAR_CH2)
                if (ch2Characteristic != null) {
                    Log.d(TAG, "✅ 已取得 CH2 特徵值（CustomIn Direct 模式支援）")
                    ConnectionLogger.log(
                        "✅ 已取得 CH2 特徵值（CustomIn Direct 模式支援）",
                        ConnectionLogger.LogLevel.INFO
                    )
                } else {
                    Log.d(TAG, "⚠️ 未找到 CH2 特徵值（不支援 CustomIn Direct 模式，將使用 Alt 碼）")
                    ConnectionLogger.log(
                        "⚠️ 未找到 CH2 特徵值（將使用 Alt 碼模式）",
                        ConnectionLogger.LogLevel.DEBUG
                    )
                }

                // 嘗試取得 CH5 特徵值（Unicode 輸入，僅 ESP32-S3 Evo 支援）
                // 注意：此時硬體型號尚未偵測，所以我們先嘗試取得，稍後再根據硬體型號決定是否使用
                ch5UnicodeCharacteristic = emulstickService.getCharacteristic(GattConstants.CHAR_CH5_UNICODE)
                if (ch5UnicodeCharacteristic != null) {
                    Log.d(TAG, "✅ 已取得 CH5 特徵值（Unicode 輸入，待硬體偵測後確認）")
                    ConnectionLogger.log(
                        "✅ 已取得 CH5 特徵值（Unicode transport，待硬體偵測確認）",
                        ConnectionLogger.LogLevel.INFO
                    )
                } else {
                    Log.d(TAG, "⚠️ 未找到 CH5 特徵值（硬體可能為原廠 EmulStick）")
                    ConnectionLogger.log(
                        "⚠️ 未找到 CH5 特徵值（可能為原廠硬體）",
                        ConnectionLogger.LogLevel.DEBUG
                    )
                }

                // 取得 COMMAND 特徵值（身份驗證用）
                commandCharacteristic = emulstickService.getCharacteristic(GattConstants.CHAR_COMMAND)
                if (commandCharacteristic == null) {
                    Log.e(TAG, "找不到 COMMAND 特徵值")
                    ConnectionLogger.log("❌ 找不到 COMMAND 特徵值", ConnectionLogger.LogLevel.ERROR)
                    _connectionState.value = BleConnectionState.Error("找不到 COMMAND 特徵值")
                    disconnect()
                    return
                }

                // ⭐ 啟用 CH1 characteristic 的 notification（接收 LED 狀態）
                // 參考：原廠 BluetoothLeService.java 第 1276 行
                // 注意：這是非同步操作，完成後會在 onDescriptorWrite callback 中啟用 COMMAND notification
                if (!enableCharacteristicNotification(gatt, ch1Characteristic!!)) {
                    Log.e(TAG, "啟用 CH1 通知失敗")
                    ConnectionLogger.log("❌ 啟用 CH1 通知失敗（LED 狀態接收）", ConnectionLogger.LogLevel.ERROR)
                    _connectionState.value = BleConnectionState.Error("啟用 CH1 通知失敗")
                    disconnect()
                    return
                }
                Log.d(TAG, "✅ 已提交 CH1 CCCD 寫入請求（用於接收 LED 狀態）")
                ConnectionLogger.log("⏳ 已啟用 CH1 notification，等待完成後啟用 COMMAND notification...", ConnectionLogger.LogLevel.INFO)
            } else {
                Log.e(TAG, "服務探索失敗，狀態: $status")
                ConnectionLogger.log("❌ 服務探索失敗，狀態: $status", ConnectionLogger.LogLevel.ERROR)
                _connectionState.value = BleConnectionState.Error("服務探索失敗")
                disconnect()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = (status == BluetoothGatt.GATT_SUCCESS)
            if (success) {
                Log.v(TAG, "特徵值寫入成功")
            } else {
                Log.w(TAG, "特徵值寫入失敗，狀態: $status")
            }

            // 通知佇列繼續處理下一個操作
            actionQueue.onWriteComplete(success)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "特徵值讀取失敗，狀態: $status")
                return
            }

            when (characteristic.uuid) {
                GattConstants.CHAR_SYSTEM_ID -> {
                    systemId = characteristic.value
                    val hexString = AesCryptUtil.byteArrayToHexString(systemId!!)
                    Log.d(TAG, "已讀取 System ID: $hexString")
                    ConnectionLogger.log("📋 已讀取 System ID: $hexString", ConnectionLogger.LogLevel.INFO)

                    // 繼續讀取 Firmware Version
                    val deviceInfoService = gatt.getService(GattConstants.SERVICE_DEVICE_INFO)
                    val fwVersionChar = deviceInfoService?.getCharacteristic(GattConstants.CHAR_FIRMWARE_VERSION)
                    if (fwVersionChar != null) {
                        gatt.readCharacteristic(fwVersionChar)
                    }
                }

                GattConstants.CHAR_FIRMWARE_VERSION -> {
                    firmwareVersion = String(characteristic.value, Charsets.UTF_8)
                    Log.d(TAG, "已讀取 Firmware Version: $firmwareVersion")
                    ConnectionLogger.log("📋 已讀取 Firmware Version: $firmwareVersion", ConnectionLogger.LogLevel.INFO)

                    // 繼續讀取 Hardware Revision
                    val deviceInfoService = gatt.getService(GattConstants.SERVICE_DEVICE_INFO)
                    val hwVersionChar = deviceInfoService?.getCharacteristic(GattConstants.CHAR_HARDWARE_VERSION)
                    if (hwVersionChar != null) {
                        gatt.readCharacteristic(hwVersionChar)
                    }
                }

                GattConstants.CHAR_HARDWARE_VERSION -> {
                    hardwareVersion = String(characteristic.value, Charsets.UTF_8)
                    Log.d(TAG, "已讀取 Hardware Revision: $hardwareVersion")
                    ConnectionLogger.log("📋 已讀取 Hardware Revision: $hardwareVersion", ConnectionLogger.LogLevel.INFO)

                    // 繼續讀取 Software Version
                    val deviceInfoService = gatt.getService(GattConstants.SERVICE_DEVICE_INFO)
                    val swVersionChar = deviceInfoService?.getCharacteristic(GattConstants.CHAR_SOFTWARE_VERSION)
                    if (swVersionChar != null) {
                        gatt.readCharacteristic(swVersionChar)
                    }
                }

                GattConstants.CHAR_SOFTWARE_VERSION -> {
                    softwareVersion = String(characteristic.value, Charsets.UTF_8)
                    Log.d(TAG, "已讀取 Software Version: $softwareVersion")
                    ConnectionLogger.log("📋 已讀取 Software Version: $softwareVersion", ConnectionLogger.LogLevel.INFO)

                    // 繼續讀取 PNP ID
                    val deviceInfoService = gatt.getService(GattConstants.SERVICE_DEVICE_INFO)
                    val pnpIdChar = deviceInfoService?.getCharacteristic(GattConstants.CHAR_PNP_ID)
                    if (pnpIdChar != null) {
                        gatt.readCharacteristic(pnpIdChar)
                    } else {
                        // 如果沒有 PNP ID characteristic，繼續完成流程
                        Log.w(TAG, "⚠️ 找不到 PNP ID characteristic，將使用預設策略")
                        detectHardwareType()
                        checkDeviceInfoComplete(gatt)
                    }
                }

                GattConstants.CHAR_PNP_ID -> {
                    pnpId = characteristic.value
                    val hexString = AesCryptUtil.byteArrayToHexString(pnpId!!)
                    Log.d(TAG, "已讀取 PNP ID: $hexString")
                    ConnectionLogger.log("📋 已讀取 PNP ID: $hexString", ConnectionLogger.LogLevel.INFO)

                    // 偵測硬體型號
                    detectHardwareType()

                    // 檢查是否都讀取完成
                    checkDeviceInfoComplete(gatt)
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor 寫入成功: ${descriptor.uuid}")

                // 檢查是否為 CCCD（Client Characteristic Configuration Descriptor）
                if (descriptor.uuid == GattConstants.DESC_CCCD) {
                    // 判斷是哪個 characteristic 的 CCCD
                    when (descriptor.characteristic.uuid) {
                        GattConstants.CHAR_CH1 -> {
                            // CH1 notification 啟用完成 → 啟用 COMMAND notification
                            Log.d(TAG, "✅ CH1 notification 啟用完成，繼續啟用 COMMAND notification")
                            ConnectionLogger.log("✅ CH1 notification 啟用完成，繼續啟用 COMMAND notification", ConnectionLogger.LogLevel.INFO)

                            if (!enableCharacteristicNotification(gatt, commandCharacteristic!!)) {
                                Log.e(TAG, "啟用 COMMAND 通知失敗")
                                ConnectionLogger.log("❌ 啟用 COMMAND 通知失敗", ConnectionLogger.LogLevel.ERROR)
                                _connectionState.value = BleConnectionState.Error("啟用 COMMAND 通知失敗")
                                disconnect()
                            }
                        }
                        GattConstants.CHAR_COMMAND -> {
                            // COMMAND notification 啟用完成 → 開始讀取裝置資訊
                            Log.d(TAG, "✅ COMMAND notification 啟用完成，開始讀取裝置資訊")
                            ConnectionLogger.log("✅ COMMAND notification 啟用完成，開始讀取裝置資訊", ConnectionLogger.LogLevel.INFO)

                            // CCCD 寫入完成後，才開始讀取裝置資訊（避免 GATT 操作佇列衝突）
                            readDeviceInfo(gatt)
                        }
                        else -> {
                            Log.d(TAG, "⚠️ 未預期的 CCCD 寫入: ${descriptor.characteristic.uuid}")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Descriptor 寫入失敗: ${descriptor.uuid}, status=$status")
                ConnectionLogger.log("❌ Descriptor 寫入失敗: status=$status", ConnectionLogger.LogLevel.ERROR)
                _connectionState.value = BleConnectionState.Error("啟用通知失敗")
                disconnect()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == GattConstants.CHAR_COMMAND) {
                val data = characteristic.value
                if (data.isNotEmpty()) {
                    when (data[0]) {
                        GattConstants.CMD_GET_CIPHERTEXT -> {
                            // 收到密文回應
                            if (data.size >= 17) {
                                val cipherFromDongle = data.copyOfRange(1, 17)  // 提取 16 bytes 密文
                                Log.d(TAG, "收到密文回應，長度: ${cipherFromDongle.size}")
                                ConnectionLogger.log(
                                    "📥 收到密文回應：${String(cipherFromDongle)}",
                                    ConnectionLogger.LogLevel.INFO
                                )

                                // 驗證密文
                                if (verifyCipherText(cipherFromDongle)) {
                                    onAuthenticationComplete(gatt)
                                } else {
                                    Log.e(TAG, "密文驗證失敗，斷線")
                                    ConnectionLogger.log("❌ 密文驗證失敗，斷線", ConnectionLogger.LogLevel.ERROR)
                                    _connectionState.value = BleConnectionState.Error("身份驗證失敗")
                                    disconnect()
                                }
                            } else {
                                Log.e(TAG, "密文長度不足，預期 17 bytes，實際: ${data.size}")
                                ConnectionLogger.log("❌ 密文長度不足", ConnectionLogger.LogLevel.ERROR)
                            }
                        }
                        GattConstants.CMD_GET_EMULATE -> {
                            // 收到裝置模式回應
                            // 格式：[0xA0/0xA1, vid_low, vid_high, pid_low, pid_high, ver]
                            if (data.size >= 6) {
                                val vid = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                                val pid = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
                                val ver = if (data.size >= 6) data[5].toInt() and 0xFF else 0

                                Log.i(TAG, "📡 收到裝置模式回應：VID=0x${vid.toString(16).uppercase()}, PID=0x${pid.toString(16).uppercase()}, Ver=$ver")
                                ConnectionLogger.log(
                                    "📡 收到裝置模式回應：VID=0x${vid.toString(16).uppercase()}, PID=0x${pid.toString(16).uppercase()}, Ver=$ver",
                                    ConnectionLogger.LogLevel.INFO
                                )

                                val deviceMode = DeviceMode.fromVidPid(vid, pid)
                                updateConnectionStateWithMode(deviceMode)

                                Log.i(TAG, "🔍 偵測到裝置模式：$deviceMode")
                                ConnectionLogger.log("🔍 偵測到裝置模式：$deviceMode", ConnectionLogger.LogLevel.INFO)
                            }
                        }
                        GattConstants.CMD_GET_CLIPBOARD_BRIDGE -> {
                            val ready = data.size >= 5 &&
                                data[1].toInt() == 1 &&
                                (data[2].toInt() and 0xFF) == ClipboardBridgeProtocol.VERSION
                            clipboardBridgeQuery?.complete(ready)
                            clipboardBridgeQuery = null
                            Log.d(TAG, "Clipboard Bridge helper ready=$ready")
                        }
                        GattConstants.CMD_CLIPBOARD_STATUS -> {
                            if (data.size >= 4) {
                                val messageId = (data[1].toInt() and 0xFF) or
                                    ((data[2].toInt() and 0xFF) shl 8)
                                val clipboardStatus = data[3].toInt() and 0xFF
                                if (clipboardStatus != ClipboardBridgeProtocol.STATUS_QUEUED) {
                                    pendingClipboardStatuses[messageId]?.complete(clipboardStatus)
                                }
                                Log.d(TAG, "Clipboard Bridge #$messageId status=0x${clipboardStatus.toString(16)}")
                            }
                        }
                    }
                }
            } else if (characteristic.uuid == GattConstants.CHAR_CH1) {
                // ⭐ 接收 LED 狀態 notification（HID Keyboard Output Report）
                // 參考：原廠 ReportKeyboard.java 第 80-101 行
                val data = characteristic.value
                if (data.isNotEmpty()) {
                    // Ver ≥1 格式：無 Report ID，第一個 byte 直接是 LED 狀態
                    // 格式：1 byte = [ScrollLock(bit2)|CapsLock(bit1)|NumLock(bit0)]
                    val ledStatusByte = data[0]
                    val newLedStatus = LedStatus.fromByte(ledStatusByte)

                    // 只在狀態改變時更新並記錄（避免洪水日誌）
                    if (newLedStatus != _ledStatus.value) {
                        val oldStatus = _ledStatus.value
                        _ledStatus.value = newLedStatus

                        Log.d(TAG, "🔔 LED 狀態更新：NumLock=${newLedStatus.numLock}, CapsLock=${newLedStatus.capsLock}, ScrollLock=${newLedStatus.scrollLock}")
                        ConnectionLogger.log(
                            "🔔 LED 狀態更新：NumLock=${newLedStatus.numLock}, CapsLock=${newLedStatus.capsLock}, ScrollLock=${newLedStatus.scrollLock}",
                            ConnectionLogger.LogLevel.INFO
                        )

                        // 記錄變更詳情（方便除錯）
                        if (oldStatus.numLock != newLedStatus.numLock) {
                            Log.d(TAG, "   NumLock: ${oldStatus.numLock} → ${newLedStatus.numLock}")
                        }
                        if (oldStatus.capsLock != newLedStatus.capsLock) {
                            Log.d(TAG, "   CapsLock: ${oldStatus.capsLock} → ${newLedStatus.capsLock}")
                        }
                        if (oldStatus.scrollLock != newLedStatus.scrollLock) {
                            Log.d(TAG, "   ScrollLock: ${oldStatus.scrollLock} → ${newLedStatus.scrollLock}")
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ 收到空的 CH1 notification（預期 LED 狀態）")
                }
            }
        }
    }

    // ============ 資料傳輸 ============

    /**
     * 傳送滑鼠移動
     *
     * @param deltaX X 軸移動量
     * @param deltaY Y 軸移動量
     * @param buttons 按鈕狀態
     */
    fun sendMouseMove(deltaX: Int, deltaY: Int, buttons: Int = 0) {
        val connected = isConnected()
        Log.d(TAG, "📍 sendMouseMove() 被呼叫：deltaX=$deltaX, deltaY=$deltaY, isConnected=$connected")

        if (!connected) {
            Log.w(TAG, "❌ 未連線，無法傳送滑鼠移動（connectionState=${_connectionState.value}）")
            ConnectionLogger.log("❌ 未連線，無法傳送滑鼠移動", ConnectionLogger.LogLevel.WARNING)
            return
        }

        Log.d(TAG, "✅ 已連線，準備傳送滑鼠移動")
        ConnectionLogger.log("✅ 已連線，準備傳送滑鼠移動", ConnectionLogger.LogLevel.DEBUG)

        // 大幅度移動需要分割
        val reports = HidReportBuilder.buildSplitMouseReports(deltaX, deltaY, buttons)
        Log.d(TAG, "📦 建立 ${reports.size} 個 HID 報告")
        ConnectionLogger.log("📦 建立 ${reports.size} 個 HID 報告", ConnectionLogger.LogLevel.DEBUG)

        reports.forEach { report ->
            writeMouseReport(report)
        }
    }

    /**
     * 傳送滑鼠點擊
     *
     * @param button 按鈕（使用 HidReportBuilder.MOUSE_BUTTON_* 常量）
     */
    fun sendMouseClick(button: Int) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送滑鼠點擊")
            return
        }

        // 按下
        writeMouseReport(HidReportBuilder.buildMouseReport(buttons = button))
        // 稍微延遲後釋放
        mainHandler.postDelayed({
            writeMouseReport(HidReportBuilder.buildMouseReport(buttons = 0))
        }, 50)
    }

    /**
     * 傳送滑鼠滾輪
     *
     * @param wheelDelta 滾輪值（負數向上，正數向下）
     *
     * 注意：根據原廠實作，滾輪傳送後需要立即重置為 0
     * 參考：ReportMouse.java:128 - usageCache.setValue(0)
     */
    fun sendMouseScroll(wheelDelta: Int) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送滑鼠滾輪")
            return
        }

        // 傳送滾輪移動
        writeMouseReport(HidReportBuilder.buildMouseReport(wheel = wheelDelta))

        // 立即傳送重置報告（模仿原廠 usageCache.setValue(0)）
        mainHandler.postDelayed({
            writeMouseReport(HidReportBuilder.buildMouseReport(wheel = 0))
        }, 10)  // 10ms 後重置
    }

    /**
     * 傳送鍵盤按鍵
     *
     * 重大修正（2025-10-20）：
     * - 問題：使用 delay() 不會等待 GATT 寫入完成，導致字元順序混亂
     * - 修正：確保使用 actionQueue（writeKeyboardReport 已經會自動加入佇列）
     * - 延遲參數：增加延遲確保穩定性（參考原廠 bleAltDelay = 12ms）
     *
     * @param modifiers 修飾鍵
     * @param keys 按鍵 Usage ID
     */
    suspend fun sendKeyPress(modifiers: Int = 0, vararg keys: Int) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送鍵盤按鍵")
            return
        }

        // 按下（writeKeyboardReport 會自動加入 actionQueue）
        writeKeyboardReport(HidReportBuilder.buildKeyboardReport(modifiers, *keys))
        actionQueue.enqueue(BleAction.Delay(50))  // 按下延遲（增加到 50ms 確保穩定）

        // 釋放
        writeKeyboardReport(HidReportBuilder.buildEmptyKeyboardReport())
        actionQueue.enqueue(BleAction.Delay(50))  // 字元間隔延遲（增加到 50ms 確保穩定）

        // 等待 actionQueue 處理完成（重要！）
        // 注意：這是非阻塞延遲，讓協程釋放執行緒給 actionQueue 處理
        delay(110)  // 50ms 按下 + 50ms 釋放 + 10ms 緩衝
    }

    /**
     * 傳送 Alt 碼（用於 Big5 Alt 碼模式）
     *
     * 工作原理：
     * 1. 確保 NumLock 開啟
     * 2. 按住 Alt 鍵
     * 3. 使用數字鍵台輸入十進制數字
     * 4. 放開 Alt 鍵，Windows 自動轉換為對應字元
     *
     * ⚠️ 注意：
     * - Windows 標準 Alt 碼只支援 0-255
     * - 大於 255 的數值在某些環境下會被 modulo 256
     * - 僅在特定環境下可運作（繁體中文系統地區設定、特定應用程式）
     *
     * @param decimalCode 十進制 Alt 碼（例如 Big5 編碼）
     */
    suspend fun sendAltCode(decimalCode: Int) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送 Alt 碼")
            return
        }

        Log.d(TAG, "📤 傳送 Alt 碼：$decimalCode (0x${decimalCode.toString(16).uppercase()})")
        ConnectionLogger.log("📤 傳送 Alt 碼：$decimalCode", ConnectionLogger.LogLevel.INFO)

        // Step 0: 清空鍵盤狀態（與原廠 clear() 一致，無延遲）
        writeKeyboardReport(HidReportBuilder.buildEmptyKeyboardReport())
        Log.v(TAG, "  🧹 清空鍵盤狀態")

        // Step 1: 按住 Alt 鍵（與原廠一致，無延遲）
        writeKeyboardReport(HidReportBuilder.buildKeyboardReport(HidReportBuilder.MODIFIER_LEFT_ALT))
        Log.v(TAG, "  ⬇️ 按住 Alt 鍵")

        // Step 2: 逐位輸入十進制數字（使用數字鍵台）
        val digits = decimalCode.toString()
        Log.v(TAG, "  🔢 輸入數字序列：${digits.toList()} (${digits.length} 位)")

        for ((index, digit) in digits.withIndex()) {
            val keypadUsage = digitToKeypadUsage(digit)

            // 2.1 按下數字鍵（繼續按住 Alt）
            writeKeyboardReport(
                HidReportBuilder.buildKeyboardReport(
                    HidReportBuilder.MODIFIER_LEFT_ALT,
                    keypadUsage
                )
            )
            actionQueue.enqueue(BleAction.Delay(12))  // 與原廠一致
            Log.v(TAG, "    [${index + 1}/${digits.length}] 按下數字 '$digit' (Usage=0x${keypadUsage.toString(16)})")

            // 2.2 釋放數字鍵（繼續按住 Alt）
            writeKeyboardReport(HidReportBuilder.buildKeyboardReport(HidReportBuilder.MODIFIER_LEFT_ALT))
            actionQueue.enqueue(BleAction.Delay(12))  // 與原廠一致
        }

        // Step 3: 釋放 Alt（清空狀態）
        writeKeyboardReport(HidReportBuilder.buildEmptyKeyboardReport())
        actionQueue.enqueue(BleAction.Delay(12))  // 與原廠一致
        Log.v(TAG, "  ⬆️ 釋放 Alt 鍵")

        Log.d(TAG, "✅ Alt 碼傳送完成：$decimalCode")
        ConnectionLogger.log("✅ Alt 碼傳送完成", ConnectionLogger.LogLevel.DEBUG)
    }

    /**
     * 數字字元轉數字鍵台 Usage ID
     *
     * @param digit 數字字元（'0'-'9'）
     * @return 數字鍵台 Usage ID
     */
    private fun digitToKeypadUsage(digit: Char): Int {
        return when (digit) {
            '0' -> HidReportBuilder.KeyboardUsage.KEYPAD_0  // 0x62
            '1' -> HidReportBuilder.KeyboardUsage.KEYPAD_1  // 0x59
            '2' -> HidReportBuilder.KeyboardUsage.KEYPAD_2  // 0x5A
            '3' -> HidReportBuilder.KeyboardUsage.KEYPAD_3  // 0x5B
            '4' -> HidReportBuilder.KeyboardUsage.KEYPAD_4  // 0x5C
            '5' -> HidReportBuilder.KeyboardUsage.KEYPAD_5  // 0x5D
            '6' -> HidReportBuilder.KeyboardUsage.KEYPAD_6  // 0x5E
            '7' -> HidReportBuilder.KeyboardUsage.KEYPAD_7  // 0x5F
            '8' -> HidReportBuilder.KeyboardUsage.KEYPAD_8  // 0x60
            '9' -> HidReportBuilder.KeyboardUsage.KEYPAD_9  // 0x61
            else -> HidReportBuilder.KeyboardUsage.KEYPAD_0  // 預設 0
        }
    }

    /**
     * 傳送 Unicode Alt 碼序列（已棄用，代理到 Alt+X Unicode 模式）
     *
     * ⚠️ 此方法已改用 Alt+X Unicode 模式（代理模式）
     *
     * 原因：
     * - Windows 10 對 Alt + + 4F60 格式的支援不穩定（RDP 環境常失效）
     * - Alt+X 模式更可靠（54C8 + Alt+X → 哈）
     * - 效能接近（Alt+X: ~170ms vs Alt++: ~156ms）
     *
     * 新舊方式比較：
     * - ❌ 舊：Alt + + 4F60 → 按住 Alt，按 +，輸入 4F60，放開 Alt
     * - ✅ 新：54C8 + Alt+X → 輸入 54C8，按 Alt+X
     *
     * @param char Unicode 字元
     * @deprecated 內部已改為代理到 sendCharWithAltX()，建議直接呼叫新方法
     */
    @Deprecated(
        message = "此方法內部已改用 Alt+X Unicode 模式，建議直接使用 sendCharWithAltX()",
        replaceWith = ReplaceWith("sendCharWithAltX(char)"),
        level = DeprecationLevel.WARNING
    )
    suspend fun sendUnicodeAltCode(char: Char) {
        // 🔄 代理模式：委託給新的 Alt+X Unicode 實作
        Log.d(TAG, "⚠️ sendUnicodeAltCode() 已棄用，委託到 sendCharWithAltX()")
        ConnectionLogger.log("⚠️ 使用已棄用方法（sendUnicodeAltCode），已自動切換到 Alt+X 模式", ConnectionLogger.LogLevel.WARNING)

        sendCharWithAltX(char)
    }

    /**
     * 十六進制數字轉鍵盤 Usage ID（包含 A-F）
     *
     * 注意：
     * - 數字 0-9 使用數字鍵台
     * - 字母 A-F 使用主鍵盤（因為數字鍵台沒有字母）
     *
     * @param digit 十六進制數字字元（'0'-'9', 'A'-'F'）
     * @return 鍵盤 Usage ID
     */
    private fun hexDigitToKeyUsage(digit: Char): Int {
        return when (digit) {
            // 數字 0-9 使用數字鍵台
            '0' -> HidReportBuilder.KeyboardUsage.KEYPAD_0  // 0x62
            '1' -> HidReportBuilder.KeyboardUsage.KEYPAD_1  // 0x59
            '2' -> HidReportBuilder.KeyboardUsage.KEYPAD_2  // 0x5A
            '3' -> HidReportBuilder.KeyboardUsage.KEYPAD_3  // 0x5B
            '4' -> HidReportBuilder.KeyboardUsage.KEYPAD_4  // 0x5C
            '5' -> HidReportBuilder.KeyboardUsage.KEYPAD_5  // 0x5D
            '6' -> HidReportBuilder.KeyboardUsage.KEYPAD_6  // 0x5E
            '7' -> HidReportBuilder.KeyboardUsage.KEYPAD_7  // 0x5F
            '8' -> HidReportBuilder.KeyboardUsage.KEYPAD_8  // 0x60
            '9' -> HidReportBuilder.KeyboardUsage.KEYPAD_9  // 0x61
            // 字母 A-F 使用主鍵盤（Keyboard A-F）
            'A' -> HidReportBuilder.KeyboardUsage.KEY_A  // 0x04
            'B' -> HidReportBuilder.KeyboardUsage.KEY_B  // 0x05
            'C' -> HidReportBuilder.KeyboardUsage.KEY_C  // 0x06
            'D' -> HidReportBuilder.KeyboardUsage.KEY_D  // 0x07
            'E' -> HidReportBuilder.KeyboardUsage.KEY_E  // 0x08
            'F' -> HidReportBuilder.KeyboardUsage.KEY_F  // 0x09
            else -> HidReportBuilder.KeyboardUsage.KEYPAD_0  // 預設 0
        }
    }

    // ============ Alt+X Unicode 模式（新實作）============

    /**
     * 使用 Alt+X Unicode 模式傳送單個字元
     *
     * Windows Alt+X 工作原理：
     * 1. 輸入 Unicode 十六進制（如：54C8）
     * 2. 按 Alt+X
     * 3. Windows 自動轉換為對應字元（哈）
     *
     * 測試結果：
     * - ✅ 記事本（Notepad）：成功
     * - ✅ WordPad：成功
     * - ✅ Microsoft Word：成功
     * - ✅ RDP 環境：成功
     *
     * 效能：約 170ms/字元（比 Big5 Alt 碼快 3.5 倍）
     *
     * 限制：
     * - 僅支援 BMP 字元（U+0000 - U+FFFF）
     * - 不支援 Emoji（需要 Surrogate Pair）
     * - 部分應用程式可能不支援（如 VS Code、Chrome）
     *
     * @param char 要傳送的字元
     */
    suspend fun sendCharWithAltX(char: Char) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送 Alt+X Unicode")
            return
        }

        // 取得 Unicode 十六進制（大寫，4 位數）
        val unicodeHex = char.code.toString(16).uppercase().padStart(4, '0')

        Log.d(TAG, "📤 傳送 Alt+X Unicode：'$char' (U+$unicodeHex)")
        ConnectionLogger.log("📤 傳送 Alt+X：'$char' (U+$unicodeHex)", ConnectionLogger.LogLevel.INFO)

        // 1. 傳送十六進制字元（使用標準 ASCII HID 報告）
        for ((index, hexChar) in unicodeHex.withIndex()) {
            Log.v(TAG, "  🔢 步驟 1.${index + 1}: 傳送 '$hexChar'")
            sendAsciiKeyPress(hexChar)
            actionQueue.enqueue(BleAction.Delay(12))
        }

        // 2. 傳送 Alt+X 組合鍵
        Log.v(TAG, "  ⌨️ 步驟 2: 傳送 Alt+X")
        sendKeyComboInternal(
            modifier = HidReportBuilder.MODIFIER_LEFT_ALT.toInt(),
            key = HidReportBuilder.KeyboardUsage.KEY_X
        )
        actionQueue.enqueue(BleAction.Delay(50))  // 等待 Windows 轉換

        Log.d(TAG, "✅ Alt+X Unicode 傳送完成：'$char'")
        ConnectionLogger.log("✅ Alt+X 傳送完成", ConnectionLogger.LogLevel.INFO)
    }

    /**
     * 使用 Alt+X Unicode 模式傳送文字
     *
     * 智慧判斷：
     * - ASCII 字元（0-127）：直接傳送 HID 報告（快速）
     * - 非 ASCII 字元：使用 Alt+X Unicode 模式
     *
     * @param text 要傳送的文字（支援中英文混合）
     */
    suspend fun sendTextWithAltX(text: String) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送文字")
            return
        }

        Log.d(TAG, "📤 開始傳送文字（Alt+X 模式）：$text")
        ConnectionLogger.log("📤 傳送文字（Alt+X）：$text", ConnectionLogger.LogLevel.INFO)

        var charCount = 0

        for (char in text) {
            when {
                // 換行
                char == '\n' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_ENTER)
                    actionQueue.enqueue(BleAction.Delay(12))
                }

                // Tab
                char == '\t' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_TAB)
                    actionQueue.enqueue(BleAction.Delay(12))
                }

                // ASCII 字元（0-127）：直接傳送 HID 報告
                char.code <= 127 -> {
                    sendAsciiCharDirect(char)
                }

                // 非 ASCII 字元：使用 Alt+X Unicode 模式
                else -> {
                    sendCharWithAltX(char)
                    charCount++
                }
            }
        }

        Log.d(TAG, "✅ 文字傳送完成：$text（Alt+X 字元數：$charCount）")
        ConnectionLogger.log("✅ 文字傳送完成（Alt+X 字元：$charCount）", ConnectionLogger.LogLevel.INFO)
    }

    /**
     * 使用 Big5 Alt 碼模式傳送字元（混合模式）
     *
     * 混合模式策略：
     * - ASCII 字元（英文、數字、標點符號）→ 直接傳送 HID 報告（快速）
     * - 中文字元 → 使用 Big5 Alt 碼
     *
     * @param char 要傳送的字元
     */
    suspend fun sendCharWithBig5Mode(char: Char) {
        // ASCII 字元（0-127）直接傳送 HID 報告
        if (char.code <= 127) {
            Log.d(TAG, "📤 傳送 ASCII 字元：'$char' (HID 直接傳送)")
            sendAsciiCharDirect(char)
            return
        }

        // 中文字元使用 Big5 Alt 碼
        val big5Code = Big5Encoder.charToBig5Code(char)

        if (big5Code == null) {
            Log.w(TAG, "⚠️ 無法將字元 '$char' 轉換為 Big5 編碼，跳過")
            return
        }

        Log.d(TAG, "📤 傳送 Big5 Alt 碼：'$char' → $big5Code (0x${big5Code.toString(16).uppercase()})")
        sendAltCode(big5Code)
    }

    /**
     * 使用 Big5 Alt 碼模式傳送字串（混合模式）
     *
     * @param text 要傳送的字串
     */
    suspend fun sendTextWithBig5AltCode(text: String) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送文字")
            return
        }

        Log.d(TAG, "📤 開始傳送 Big5 模式文字：\"$text\" (${text.length} 字元)")
        ConnectionLogger.log("📤 傳送文字（Big5 模式）：$text", ConnectionLogger.LogLevel.INFO)

        val startTime = System.currentTimeMillis()
        var asciiCount = 0
        var big5Count = 0

        for (char in text) {
            when {
                // 換行
                char == '\n' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_ENTER)
                    actionQueue.enqueue(BleAction.Delay(12))
                }

                // Tab
                char == '\t' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_TAB)
                    actionQueue.enqueue(BleAction.Delay(12))
                }

                // ASCII 字元（0-127）：直接傳送 HID 報告
                char.code <= 127 -> {
                    sendAsciiCharDirect(char)
                    asciiCount++
                }

                // 中文字元：使用 Big5 Alt 碼
                else -> {
                    val big5Code = Big5Encoder.charToBig5Code(char)
                    if (big5Code != null) {
                        sendAltCode(big5Code)
                        big5Count++
                    } else {
                        Log.w(TAG, "⚠️ 無法將字元 '$char' 轉換為 Big5 編碼，跳過")
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "✅ Big5 模式文字傳送完成，耗時 ${duration}ms（ASCII: $asciiCount 字，Big5: $big5Count 字）")
        ConnectionLogger.log(
            "✅ 文字傳送完成（ASCII: $asciiCount, Big5: $big5Count，耗時 ${duration}ms）",
            ConnectionLogger.LogLevel.INFO
        )
    }


    /**
     * 傳送單個 ASCII 按鍵（用於輸入 Unicode 十六進制）
     *
     * 僅支援：0-9, A-F, 空白
     *
     * @param char 要傳送的字元
     */
    private suspend fun sendAsciiKeyPress(char: Char) {
        val usage = when (char) {
            // 數字 0-9
            '0' -> HidReportBuilder.KeyboardUsage.KEY_0
            '1' -> HidReportBuilder.KeyboardUsage.KEY_1
            '2' -> HidReportBuilder.KeyboardUsage.KEY_2
            '3' -> HidReportBuilder.KeyboardUsage.KEY_3
            '4' -> HidReportBuilder.KeyboardUsage.KEY_4
            '5' -> HidReportBuilder.KeyboardUsage.KEY_5
            '6' -> HidReportBuilder.KeyboardUsage.KEY_6
            '7' -> HidReportBuilder.KeyboardUsage.KEY_7
            '8' -> HidReportBuilder.KeyboardUsage.KEY_8
            '9' -> HidReportBuilder.KeyboardUsage.KEY_9

            // 字母 A-F（十六進制）
            'A' -> HidReportBuilder.KeyboardUsage.KEY_A
            'B' -> HidReportBuilder.KeyboardUsage.KEY_B
            'C' -> HidReportBuilder.KeyboardUsage.KEY_C
            'D' -> HidReportBuilder.KeyboardUsage.KEY_D
            'E' -> HidReportBuilder.KeyboardUsage.KEY_E
            'F' -> HidReportBuilder.KeyboardUsage.KEY_F

            // 空白
            ' ' -> HidReportBuilder.KeyboardUsage.KEY_SPACE

            else -> {
                Log.w(TAG, "不支援的字元：'$char'")
                return
            }
        }

        // 傳送按鍵（按下 + 釋放）
        writeKeyboardReport(
            HidReportBuilder.buildKeyboardReport(0, usage)
        )
        actionQueue.enqueue(BleAction.Delay(12))

        writeKeyboardReport(
            HidReportBuilder.buildEmptyKeyboardReport()
        )
        actionQueue.enqueue(BleAction.Delay(12))
    }

    /**
     * 傳送組合鍵（Modifier + Key）
     *
     * 用於傳送 Alt+X
     *
     * @param modifier 修飾鍵（如 MODIFIER_LEFT_ALT）
     * @param key 主鍵（如 KEY_X）
     */
    private suspend fun sendKeyComboInternal(modifier: Int, key: Int) {
        // 1. 按住 Modifier + 按下 Key
        writeKeyboardReport(
            HidReportBuilder.buildKeyboardReport(modifier, key)
        )
        actionQueue.enqueue(BleAction.Delay(12))

        // 2. 釋放 Key（保持 Modifier）
        writeKeyboardReport(
            HidReportBuilder.buildKeyboardReport(modifier)
        )
        actionQueue.enqueue(BleAction.Delay(12))

        // 3. 釋放 Modifier
        writeKeyboardReport(
            HidReportBuilder.buildEmptyKeyboardReport()
        )
        actionQueue.enqueue(BleAction.Delay(12))
    }

    /**
     * 傳送 ASCII 字元（使用標準 HID 報告）
     *
     * 支援：a-z, A-Z, 0-9, 基本標點符號
     *
     * @param char ASCII 字元
     */
    private suspend fun sendAsciiCharDirect(char: Char) {
        val (modifier, usage) = when (char) {
            // 小寫字母
            in 'a'..'z' -> {
                0 to (HidReportBuilder.KeyboardUsage.KEY_A + (char - 'a'))
            }

            // 大寫字母（需要 Shift）
            in 'A'..'Z' -> {
                HidReportBuilder.MODIFIER_LEFT_SHIFT.toInt() to (HidReportBuilder.KeyboardUsage.KEY_A + (char - 'A'))
            }

            // 數字
            '0' -> 0 to HidReportBuilder.KeyboardUsage.KEY_0
            in '1'..'9' -> {
                0 to (HidReportBuilder.KeyboardUsage.KEY_1 + (char - '1'))
            }

            // 空白
            ' ' -> 0 to HidReportBuilder.KeyboardUsage.KEY_SPACE

            // 基本標點符號
            '.' -> 0 to HidReportBuilder.KeyboardUsage.KEY_PERIOD
            ',' -> 0 to HidReportBuilder.KeyboardUsage.KEY_COMMA
            '/' -> 0 to HidReportBuilder.KeyboardUsage.KEY_SLASH
            '-' -> 0 to HidReportBuilder.KeyboardUsage.KEY_MINUS
            '=' -> 0 to HidReportBuilder.KeyboardUsage.KEY_EQUAL

            else -> {
                Log.w(TAG, "不支援的 ASCII 字元：'$char' (${char.code})")
                return
            }
        }

        // 傳送按鍵
        writeKeyboardReport(
            HidReportBuilder.buildKeyboardReport(modifier, usage)
        )
        actionQueue.enqueue(BleAction.Delay(12))

        writeKeyboardReport(
            HidReportBuilder.buildEmptyKeyboardReport()
        )
        actionQueue.enqueue(BleAction.Delay(12))
    }

    /**
     * 透過 BLE CH5 傳送 Unicode code point（僅 ESP32-S3）。
     *
     * 目前 ESP 韌體不是送 native USB HID Unicode report，而是把 code point
     * 轉成十六進位鍵盤輸入，再送 Windows Alt+X。因此只適用於支援 Alt+X
     * 轉換的 Windows 程式，不能視為 macOS／Linux 通用文字輸入。
     *
     * @param char Unicode 字元
     */
    suspend fun sendUnicodeChar(char: Char) {
        sendUnicodeCodePoint(char.code)
    }

    /** 傳送完整 Unicode scalar，避免 supplementary-plane 字元被拆成 surrogate。 */
    suspend fun sendUnicodeCodePoint(codepoint: Int) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送 Unicode 字元")
            return
        }

        // 檢查硬體是否支援 CH5 Unicode transport
        if (!hardwareType.supportsCH5UnicodeTransport()) {
            Log.w(TAG, "硬體不支援 CH5 Unicode transport：${hardwareType.getDisplayName()}")
            ConnectionLogger.log(
                "⚠️ 硬體不支援 CH5 Unicode transport（${hardwareType.getDisplayName()}）",
                ConnectionLogger.LogLevel.WARNING
            )
            return
        }

        // 檢查 CH5 characteristic 是否可用
        val ch5 = ch5UnicodeCharacteristic
        if (ch5 == null) {
            Log.e(TAG, "CH5 characteristic 不可用")
            ConnectionLogger.log(
                "❌ CH5 characteristic 不可用（硬體可能未實作 Unicode transport）",
                ConnectionLogger.LogLevel.ERROR
            )
            return
        }

        if (!Character.isValidCodePoint(codepoint) || codepoint in 0xD800..0xDFFF) {
            Log.w(TAG, "拒絕無效 Unicode code point：0x${codepoint.toString(16)}")
            return
        }
        val display = String(Character.toChars(codepoint))
        Log.d(TAG, "📤 傳送 Unicode 字元：'$display' (U+${codepoint.toString(16).uppercase()})")
        ConnectionLogger.log(
            "📤 傳送 CH5 Unicode：'$display' (U+${codepoint.toString(16).uppercase()})",
            ConnectionLogger.LogLevel.INFO
        )

        // 建構 32-bit Unicode code point（Little Endian）
        val data = ByteArray(4)
        data[0] = (codepoint and 0xFF).toByte()
        data[1] = ((codepoint shr 8) and 0xFF).toByte()
        data[2] = ((codepoint shr 16) and 0xFF).toByte()
        data[3] = ((codepoint shr 24) and 0xFF).toByte()

        // 傳送到 CH5
        actionQueue.enqueue(BleAction.WriteCharacteristic(data, ch5))
        actionQueue.enqueue(BleAction.Delay(20))

        Log.d(TAG, "✅ CH5 Unicode 傳送完成：'$display'")
        ConnectionLogger.log("✅ CH5 Unicode 傳送完成", ConnectionLogger.LogLevel.DEBUG)
    }

    suspend fun sendClipboardBridgeText(text: String): ClipboardBridgeAttempt {
        if (!isConnected() || hardwareType != EmulStickHardware.ESP32S3_EVO ||
            !supportsClipboardBridgeFirmware()) {
            return ClipboardBridgeAttempt.SafeFallback("接收器不支援 Clipboard Bridge")
        }
        val ch2 = ch2Characteristic
            ?: return ClipboardBridgeAttempt.SafeFallback("CH2 characteristic 不可用")
        val sysId = systemId
            ?: return ClipboardBridgeAttempt.SafeFallback("System ID 尚未就緒")
        if (sysId.size < 8) {
            return ClipboardBridgeAttempt.SafeFallback("System ID 長度不足")
        }
        val command = commandCharacteristic
            ?: return ClipboardBridgeAttempt.SafeFallback("COMMAND characteristic 不可用")

        val query = CompletableDeferred<Boolean>()
        clipboardBridgeQuery?.cancel()
        clipboardBridgeQuery = query
        val queryCommand = byteArrayOf(
            GattConstants.CMD_GET_CLIPBOARD_BRIDGE,
            sysId[6],
            sysId[7]
        )
        actionQueue.enqueue(BleAction.WriteCharacteristic(queryCommand, command))
        val helperReady = withTimeoutOrNull(1_500) { query.await() } ?: false
        if (clipboardBridgeQuery === query) clipboardBridgeQuery = null
        if (!helperReady) {
            return ClipboardBridgeAttempt.SafeFallback("目標端 Clipboard Bridge helper 尚未連線")
        }

        val messageId = nextClipboardMessageId.getAndUpdate { current ->
            if (current >= 0xFFFF) 1 else current + 1
        }
        val frames = try {
            ClipboardBridgeProtocol.frames(text, messageId)
        } catch (error: IllegalArgumentException) {
            return ClipboardBridgeAttempt.SafeFallback(error.message ?: "Clipboard Bridge 封包建立失敗")
        }

        val status = CompletableDeferred<Int>()
        pendingClipboardStatuses[messageId] = status
        frames.forEach { frame ->
            actionQueue.enqueue(BleAction.WriteCharacteristic(frame, ch2))
        }

        val terminalStatus = withTimeoutOrNull(12_000) { status.await() }
        pendingClipboardStatuses.remove(messageId)
        return when {
            terminalStatus == ClipboardBridgeProtocol.STATUS_PASTED -> ClipboardBridgeAttempt.Pasted
            terminalStatus != null && ClipboardBridgeProtocol.allowsSafeFallback(terminalStatus) ->
                ClipboardBridgeAttempt.SafeFallback("Clipboard Bridge 回報 0x${terminalStatus.toString(16)}")
            terminalStatus != null -> ClipboardBridgeAttempt.Failed(
                "Clipboard Bridge 回報 0x${terminalStatus.toString(16)}；為避免重複貼上，不自動重送"
            )
            else -> ClipboardBridgeAttempt.Failed(
                "Clipboard Bridge ACK 逾時；目標端可能已貼上，為避免重複不自動重送"
            )
        }
    }

    private fun supportsClipboardBridgeFirmware(): Boolean {
        val parts = firmwareVersion?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
        if (parts.size < 2) return false
        return parts[0] > 1 || (parts[0] == 1 && parts[1] >= 2)
    }

    /**
     * 使用 CustomIn Direct 模式傳送文字
     *
     * 直接傳送 UTF-8 編碼的文字到 PC，無需使用 Alt 碼。
     *
     * 格式參考：原廠 ReportCustom.java:23-41
     * - Type: 0x20 (BLEDATA_UNICODE_TEXT)
     * - Length: UTF-8 資料長度（1-17 bytes）
     * - Data: UTF-8 編碼的文字資料
     *
     * 需要：
     * - 接收器韌體版本 Ver ≥1
     * - Windows 內建 HID Class Driver（無需額外安裝驅動）
     * - CH2 characteristic 支援
     *
     * 速度：
     * - ~20ms/報告 (比 Alt 碼快約 12 倍)
     * - 支援所有 Unicode 字元（包括 Big5 範圍外的字元）
     *
     * @param text 要傳送的文字（支援所有 Unicode 字元）
     * @throws Exception 如果傳送失敗（例如：不支援 CustomIn 報告）
     */
    /**
     * 使用混合模式傳送文字（智慧選擇 HID 或 CustomIn）
     *
     * 智慧混合策略：
     * - ASCII 字元（英文、數字、符號）：使用 HID 鍵盤報告（快速，~10ms/字元）
     * - 非 ASCII 字元（中文、特殊符號）：使用 CustomIn 報告（UTF-8，~20ms/報告）
     * - 特殊字元（換行、Tab）：使用 HID 功能鍵
     *
     * 優點：
     * - 英文輸入速度最快（HID 報告，~10ms/字元）
     * - 中文支援完整（CustomIn UTF-8，~20ms/報告）
     * - 不需要 Big5 轉換，支援所有 Unicode 字元
     *
     * @param text 要傳送的文字（支援所有 Unicode 字元）
     * @throws Exception 如果傳送失敗（例如：不支援 CustomIn 報告）
     */
    suspend fun sendTextDirect(text: String) {
        if (!isConnected()) {
            Log.w(TAG, "未連線，無法傳送文字")
            throw Exception("未連線")
        }

        // 檢查 CH2 characteristic 是否可用（CustomIn 報告需要）
        val ch2 = ch2Characteristic
        if (ch2 == null) {
            Log.e(TAG, "CH2 characteristic 不可用，接收器可能不支援 CustomIn 報告")
            throw Exception("CH2 characteristic 不存在，接收器可能不支援 CustomIn 報告")
        }

        Log.d(TAG, "📤 開始混合模式傳送：'$text' (${text.length} 字元)")
        ConnectionLogger.log(
            "📤 混合模式傳送：共 ${text.length} 字元",
            ConnectionLogger.LogLevel.INFO
        )

        // 分段處理文字：ASCII 用 HID，非 ASCII 用 CustomIn
        var i = 0
        var asciiCount = 0
        var customInCount = 0

        while (i < text.length) {
            val char = text[i]

            when {
                // 特殊字元：使用 HID 功能鍵
                char == '\n' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_ENTER)
                    delay(10)
                    asciiCount++
                }
                char == '\t' -> {
                    sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_TAB)
                    delay(10)
                    asciiCount++
                }

                // ASCII 可列印字元（0x20-0x7E）：使用 HID 鍵盤報告
                char.code in 0x20..0x7E -> {
                    sendAsciiChar(char)
                    delay(10)
                    asciiCount++
                }

                // 非 ASCII 字元：收集連續的非 ASCII 字元，用 CustomIn 報告傳送
                else -> {
                    // 找出連續的非 ASCII 字元
                    val startIndex = i
                    while (i < text.length &&
                        text[i].code !in 0x20..0x7E &&
                        text[i] != '\n' &&
                        text[i] != '\t') {
                        i++
                    }
                    val nonAsciiText = text.substring(startIndex, i)

                    // 使用 CustomIn 報告傳送
                    sendTextViaCustomIn(nonAsciiText)
                    customInCount += nonAsciiText.length

                    i--  // 因為外層迴圈會 i++，這裡先減 1
                }
            }

            i++
        }

        Log.d(TAG, "✅ 混合模式傳送完成：ASCII=$asciiCount 字元，CustomIn=$customInCount 字元")
        ConnectionLogger.log(
            "✅ 混合模式傳送完成：ASCII=$asciiCount，CustomIn=$customInCount",
            ConnectionLogger.LogLevel.INFO
        )
    }

    /**
     * 傳送 ASCII 字元（使用 HID 鍵盤報告）
     *
     * @param char ASCII 字元（0x20-0x7E）
     */
    private suspend fun sendAsciiChar(char: Char) {
        // 取得 HID 鍵碼和修飾鍵
        val (keyCode, modifier) = getHidKeyCode(char)

        if (keyCode != null) {
            sendKeyPress(modifier, keyCode.toInt())
        } else {
            Log.w(TAG, "無法轉換為 HID 鍵碼: '$char' (${char.code})")
        }
    }

    /**
     * 取得 ASCII 字元對應的 HID 鍵碼
     *
     * @return Pair(鍵碼, 修飾鍵)，如果無法轉換則回傳 (null, 0)
     */
    private fun getHidKeyCode(char: Char): Pair<Byte?, Int> {
        return when (char) {
            // 字母（小寫）
            in 'a'..'z' -> Pair((HidReportBuilder.KeyboardUsage.KEY_A.toInt() + (char - 'a')).toByte(), 0)

            // 字母（大寫）
            in 'A'..'Z' -> Pair(
                (HidReportBuilder.KeyboardUsage.KEY_A.toInt() + (char - 'A')).toByte(),
                HidReportBuilder.MODIFIER_LEFT_SHIFT
            )

            // 數字
            in '0'..'9' -> {
                val keyCode = when (char) {
                    '0' -> HidReportBuilder.KeyboardUsage.KEY_0
                    else -> (HidReportBuilder.KeyboardUsage.KEY_1.toInt() + (char - '1')).toByte()
                }
                Pair(keyCode.toByte(), 0)
            }

            // 符號（不需要 Shift）
            ' ' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SPACE.toByte(), 0)
            '-' -> Pair(HidReportBuilder.KeyboardUsage.KEY_MINUS.toByte(), 0)
            '=' -> Pair(HidReportBuilder.KeyboardUsage.KEY_EQUAL.toByte(), 0)
            '[' -> Pair(HidReportBuilder.KeyboardUsage.KEY_LEFT_BRACKET.toByte(), 0)
            ']' -> Pair(HidReportBuilder.KeyboardUsage.KEY_RIGHT_BRACKET.toByte(), 0)
            '\\' -> Pair(HidReportBuilder.KeyboardUsage.KEY_BACKSLASH.toByte(), 0)
            ';' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SEMICOLON.toByte(), 0)
            '\'' -> Pair(HidReportBuilder.KeyboardUsage.KEY_APOSTROPHE.toByte(), 0)
            '`' -> Pair(HidReportBuilder.KeyboardUsage.KEY_GRAVE.toByte(), 0)
            ',' -> Pair(HidReportBuilder.KeyboardUsage.KEY_COMMA.toByte(), 0)
            '.' -> Pair(HidReportBuilder.KeyboardUsage.KEY_PERIOD.toByte(), 0)
            '/' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SLASH.toByte(), 0)

            // 符號（需要 Shift）
            '!' -> Pair(HidReportBuilder.KeyboardUsage.KEY_1.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '@' -> Pair(HidReportBuilder.KeyboardUsage.KEY_2.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '#' -> Pair(HidReportBuilder.KeyboardUsage.KEY_3.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '$' -> Pair(HidReportBuilder.KeyboardUsage.KEY_4.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '%' -> Pair(HidReportBuilder.KeyboardUsage.KEY_5.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '^' -> Pair(HidReportBuilder.KeyboardUsage.KEY_6.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '&' -> Pair(HidReportBuilder.KeyboardUsage.KEY_7.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '*' -> Pair(HidReportBuilder.KeyboardUsage.KEY_8.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '(' -> Pair(HidReportBuilder.KeyboardUsage.KEY_9.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            ')' -> Pair(HidReportBuilder.KeyboardUsage.KEY_0.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '_' -> Pair(HidReportBuilder.KeyboardUsage.KEY_MINUS.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '+' -> Pair(HidReportBuilder.KeyboardUsage.KEY_EQUAL.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '{' -> Pair(HidReportBuilder.KeyboardUsage.KEY_LEFT_BRACKET.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '}' -> Pair(HidReportBuilder.KeyboardUsage.KEY_RIGHT_BRACKET.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '|' -> Pair(HidReportBuilder.KeyboardUsage.KEY_BACKSLASH.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            ':' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SEMICOLON.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '"' -> Pair(HidReportBuilder.KeyboardUsage.KEY_APOSTROPHE.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '~' -> Pair(HidReportBuilder.KeyboardUsage.KEY_GRAVE.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '<' -> Pair(HidReportBuilder.KeyboardUsage.KEY_COMMA.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '>' -> Pair(HidReportBuilder.KeyboardUsage.KEY_PERIOD.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)
            '?' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SLASH.toByte(), HidReportBuilder.MODIFIER_LEFT_SHIFT)

            else -> Pair(null, 0)
        }
    }

    /**
     * 透過 CustomIn 報告傳送文字（僅非 ASCII 字元）
     *
     * @param text 要傳送的文字（應該只包含非 ASCII 字元）
     */
    private suspend fun sendTextViaCustomIn(text: String) {
        val ch2 = ch2Characteristic ?: return

        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        var offset = 0

        while (offset < utf8Bytes.size) {
            // 找到安全的切割點（不會切在 UTF-8 字元中間）
            val length = CustomInReportBuilder.findSafeCutPoint(
                utf8Bytes,
                offset,
                CustomInReportBuilder.MAX_PAYLOAD_SIZE
            )

            // 建構 CustomIn 報告
            val report = CustomInReportBuilder.buildCustomInReport(utf8Bytes, offset, length)

            // 傳送到 CH2 characteristic
            actionQueue.enqueue(BleAction.WriteCharacteristic(report, ch2))
            actionQueue.enqueue(BleAction.Delay(20))  // 每個報告間隔 20ms

            offset += length
        }
    }

    /**
     * CustomIn 報告建構器（EmulStick Direct 模式）
     *
     * 用於直接傳送 UTF-8 文字到 PC，無需使用 Alt 碼。
     * 需要接收器韌體版本 Ver ≥1 支援。
     *
     * 格式參考：原廠 ReportCustom.java:23-41
     */
    private object CustomInReportBuilder {
        /** Type 標記：Unicode 文字 */
        const val TYPE_UNICODE_TEXT: Byte = 0x20

        /** 最大 payload 大小（bytes）*/
        const val MAX_PAYLOAD_SIZE = 17  // 20 (BLE MTU) - 1 (Type) - 1 (Length) - 1 (預留)

        /**
         * 建構 CustomIn 報告（完全符合原廠格式）
         *
         * 格式：[Type(0x20)][Length][UTF-8 Data...]
         *
         * @param utf8Bytes UTF-8 編碼的資料
         * @param offset 資料起始位置
         * @param length 資料長度（最多 17 bytes）
         * @return CustomIn HID 報告
         */
        fun buildCustomInReport(utf8Bytes: ByteArray, offset: Int, length: Int): ByteArray {
            require(length <= MAX_PAYLOAD_SIZE) {
                "資料長度 $length 超過最大限制 $MAX_PAYLOAD_SIZE"
            }

            val reportSize = 1 + 1 + length  // Type + Length + Data
            val report = ByteArray(reportSize)

            report[0] = TYPE_UNICODE_TEXT  // Type = 0x20
            report[1] = length.toByte()    // Length
            System.arraycopy(utf8Bytes, offset, report, 2, length)  // Data from offset 2

            return report
        }

        /**
         * 找到安全的 UTF-8 切割點
         *
         * 確保不會在 UTF-8 多位元組字元中間切割，避免亂碼。
         *
         * @param utf8Bytes UTF-8 資料
         * @param start 起始位置
         * @param maxLength 最大長度
         * @return 安全的切割點（從 start 開始的長度）
         */
        fun findSafeCutPoint(utf8Bytes: ByteArray, start: Int, maxLength: Int): Int {
            if (start + maxLength >= utf8Bytes.size) {
                return utf8Bytes.size - start  // 剩餘資料不超過 maxLength，全部傳送
            }

            var cutPoint = start + maxLength

            // 往回找到完整的 UTF-8 字元邊界
            while (cutPoint > start) {
                val byte = utf8Bytes[cutPoint]

                // UTF-8 字元的開始 byte：
                // - 0xxxxxxx (ASCII, 1 byte)
                // - 110xxxxx (2-byte 字元開始)
                // - 1110xxxx (3-byte 字元開始)
                // - 11110xxx (4-byte 字元開始)
                //
                // continuation byte: 10xxxxxx

                if ((byte.toInt() and 0xC0) != 0x80) {
                    // 不是 continuation byte，是字元開始
                    break
                }

                cutPoint--
            }

            return cutPoint - start
        }
    }

    /**
     * 確保 NumLock 開啟（用於 Alt 碼輸入）
     *
     * Windows Alt 碼輸入必須使用數字鍵台，而數字鍵台需要 NumLock 開啟。
     * 原廠實作（KbImeInputFragment.java）會在傳送 Alt 碼前檢查並開啟 NumLock。
     *
     * ⭐ 新實作：透過 BLE notification 即時追蹤真實的 NumLock 狀態
     * - 從 ledStatus.value 取得當前 NumLock 狀態（PC → EmulStick → BLE → Android）
     * - 只在 NumLock 關閉時才傳送切換按鍵
     * - 避免不必要的按鍵操作，提升效率
     *
     * 邏輯：
     * - 如果 NumLock 已開啟：不做任何操作 ✅
     * - 如果 NumLock 關閉：傳送一次 NumLock 按鍵 → 開啟 ✅
     *
     * 參考：
     * - 原廠 KbImeInputFragment.java 第 1224-1245 行（檢查邏輯）
     * - 原廠 KbStatus.java 第 25-94 行（LED 狀態追蹤）
     *
     * 延遲設定：
     * - 按下 NumLock：12ms（原廠設定）
     * - 釋放 NumLock：12ms（原廠設定）
     * - 等待生效：50ms（保守估計，原廠使用 sendDelay()）
     * - 總延遲：已開啟 0ms，關閉時 ~75ms
     *
     * ⚠️ 使用佇列機制：所有操作（包括延遲）都加入 actionQueue，確保順序正確
     */
    private suspend fun ensureNumLockEnabled() {
        val currentNumLockState = _ledStatus.value.numLock
        Log.d(TAG, "🔒 檢查 NumLock 狀態：當前=$currentNumLockState")
        ConnectionLogger.log("🔒 檢查 NumLock 狀態：當前=$currentNumLockState", ConnectionLogger.LogLevel.DEBUG)

        if (currentNumLockState) {
            // NumLock 已經開啟，不需要任何操作
            Log.d(TAG, "✅ NumLock 已開啟，無需操作")
            ConnectionLogger.log("✅ NumLock 已開啟，無需操作", ConnectionLogger.LogLevel.DEBUG)
            return
        }

        // NumLock 關閉，傳送切換按鍵
        Log.d(TAG, "🔄 NumLock 關閉，傳送切換按鍵")
        ConnectionLogger.log("🔄 NumLock 關閉，傳送切換按鍵", ConnectionLogger.LogLevel.INFO)

        // 按下 NumLock
        writeKeyboardReport(
            HidReportBuilder.buildKeyboardReport(
                0,  // 無修飾鍵
                HidReportBuilder.KeyboardUsage.KEY_NUM_LOCK  // 0x53
            )
        )
        actionQueue.enqueue(BleAction.Delay(12))  // ⚠️ 改用佇列延遲，12ms（原廠設定）

        // 釋放 NumLock
        writeKeyboardReport(HidReportBuilder.buildEmptyKeyboardReport())
        actionQueue.enqueue(BleAction.Delay(50))  // 等待 NumLock 生效

        Log.d(TAG, "✅ 已傳送 NumLock 切換指令")
        ConnectionLogger.log("✅ 已傳送 NumLock 切換指令", ConnectionLogger.LogLevel.INFO)
    }

    /**
     * 寫入滑鼠報告（使用 CH3）
     *
     * EmulStick Ver ≥1 的 MouseV1 格式使用 CH3 characteristic
     *
     * ⚠️ 使用佇列機制：所有 GATT 寫入都會加入 actionQueue，確保順序正確
     */
    private fun writeMouseReport(data: ByteArray) {
        val characteristic = ch3Characteristic

        Log.d(TAG, "📤 writeMouseReport() 被呼叫：資料長度=${data.size}, CH3=${characteristic != null}")

        if (characteristic == null) {
            Log.e(TAG, "❌ ch3Characteristic 為 null，無法寫入滑鼠報告")
            ConnectionLogger.log("❌ CH3 characteristic 不可用", ConnectionLogger.LogLevel.ERROR)
            return
        }

        // 加入佇列（而非直接寫入）
        actionQueue.enqueue(BleAction.WriteCharacteristic(data, characteristic))
        Log.v(TAG, "✅ 滑鼠報告已加入佇列（CH3, 資料：${data.joinToString(" ") { "%02X".format(it) }}）")
        ConnectionLogger.log("✅ 滑鼠報告已加入佇列（CH3, ${data.size} bytes）", ConnectionLogger.LogLevel.DEBUG)
    }

    /**
     * 寫入鍵盤報告（使用 CH1）
     *
     * EmulStick Ver ≥1 的 SingleKeyboard 格式使用 CH1 characteristic
     *
     * ⚠️ 使用佇列機制：所有 GATT 寫入都會加入 actionQueue，確保順序正確
     */
    private fun writeKeyboardReport(data: ByteArray) {
        val characteristic = ch1Characteristic

        Log.d(TAG, "📤 writeKeyboardReport() 被呼叫：資料長度=${data.size}, CH1=${characteristic != null}")

        if (characteristic == null) {
            Log.e(TAG, "❌ ch1Characteristic 為 null，無法寫入鍵盤報告")
            ConnectionLogger.log("❌ CH1 characteristic 不可用", ConnectionLogger.LogLevel.ERROR)
            return
        }

        // ⚠️ 加強日誌：顯示實際傳送的 HEX 資料
        val hexData = data.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "📤 準備加入佇列：鍵盤報告（CH1）：[$hexData]")
        ConnectionLogger.log("📤 鍵盤報告（CH1）：[$hexData]", ConnectionLogger.LogLevel.INFO)

        // 加入佇列（而非直接寫入）
        actionQueue.enqueue(BleAction.WriteCharacteristic(data, characteristic))
        Log.d(TAG, "✅ 鍵盤報告已加入佇列（CH1）")
        ConnectionLogger.log("✅ 鍵盤報告已加入佇列", ConnectionLogger.LogLevel.INFO)
    }

    // ============ 配對裝置管理 ============

    /**
     * 從系統配對清單取得 EmulStick 裝置
     *
     * 這是解決「綁定鎖定模式」的關鍵方法：
     * - EmulStick 配對後不再廣播 BLE 封包
     * - 但 Android 系統保留配對資訊
     * - 透過 getBondedDevices() 可以找到已配對裝置
     * - 然後直接用 MAC 地址連線（無需掃描）
     *
     * @return 已配對的 EmulStick 裝置清單
     */
    private fun getBondedEmulStickDevices(): List<com.unifiedremote.evo.data.SavedDevice> {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bluetoothManager == null) {
                Log.w(TAG, "無法取得 BluetoothManager")
                return emptyList()
            }

            val adapter = bluetoothManager.adapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "藍牙未啟用")
                return emptyList()
            }

            // 取得所有已配對裝置
            val bondedDevices = adapter.bondedDevices ?: emptySet()
            Log.d(TAG, "系統中共有 ${bondedDevices.size} 個已配對裝置")

            // 過濾出 EmulStick 裝置（裝置名稱包含 "emulstick"）
            val emulStickDevices = bondedDevices
                .filter { device ->
                    val deviceName = device.name ?: ""
                    val isEmulStick = deviceName.lowercase().contains("emulstick")
                    if (isEmulStick) {
                        Log.d(TAG, "找到已配對的 EmulStick: $deviceName (${device.address})")
                    }
                    isEmulStick
                }
                .map { device ->
                    com.unifiedremote.evo.data.SavedDevice.createBleEmulstick(
                        deviceName = device.name ?: "EmulStick",
                        address = device.address
                    )
                }

            return emulStickDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "權限錯誤：無法存取已配對裝置", e)
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "取得已配對裝置時發生錯誤", e)
            return emptyList()
        }
    }

    // ============ 連線管理 ============

    /**
     * 檢查是否已連線
     */
    fun isConnected(): Boolean {
        return _connectionState.value is BleConnectionState.Connected
    }

    /**
     * 取得裝置 System ID（用於模式切換指令）
     *
     * System ID 在連線時已讀取並儲存在 systemId 變數中。
     * 這個方法用於其他控制器（例如 BleXInputController）取得 System ID 以傳送模式切換指令。
     *
     * @return System ID (8 bytes) 或 null（尚未連線或讀取失敗）
     */
    fun getSystemId(): ByteArray? {
        return systemId?.copyOf()  // 返回副本，避免外部修改
    }

    /**
     * 取得 PNP ID
     *
     * @return PNP ID（7 bytes）或 null
     */
    fun getPnpId(): ByteArray? {
        return pnpId?.copyOf()
    }

    /**
     * 解析 PNP VID（Vendor ID）
     *
     * PNP ID 格式（7 bytes）：
     * - Byte 0: Vendor ID Source (1 = Bluetooth SIG)
     * - Byte 1-2: Vendor ID (Little Endian)
     * - Byte 3-4: Product ID (Little Endian)
     * - Byte 5-6: Product Version (Little Endian)
     *
     * 參考：BleDevInfo.java 第 280-294 行
     *
     * @return VID 或預設值 0x0451 (1105, TI)
     */
    fun getPnpVid(): Int {
        val pnp = pnpId
        if (pnp == null || pnp.isEmpty()) {
            return 13  // 預設為 TI (與原廠一致)
        }

        // 檢查 Vendor ID Source
        if (pnp[0] != 1.toByte()) {
            return 13  // 不是 USB Source，預設為 TI
        }

        // 解析 VID (Little Endian)
        val lowByte = pnp[1].toInt() and 0xFF
        val highByte = pnp[2].toInt() and 0xFF
        return lowByte or (highByte shl 8)
    }

    /**
     * 判斷是否為 Ver 0 裝置
     *
     * 根據 Firmware Version 判斷：
     * - Ver 0: 1.1.x 或 1.2.0
     *
     * 參考：BleDevInfo.java 第 318-321 行
     *
     * @return true 如果是 Ver 0 裝置
     */
    fun isDeviceV0(): Boolean {
        val fw = firmwareVersion ?: return false
        val parts = fw.split(".")
        if (parts.size < 3) return false

        return try {
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].toInt()
            major == 1 && (minor == 1 || (minor == 2 && patch == 0))
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * 取得廠商資訊
     *
     * 根據 PNP VID 判斷晶片廠商及對應的切換指令類型。
     *
     * @return VendorInfo 包含廠商名稱、VID 和切換指令類型
     */
    fun getVendorInfo(): VendorInfo {
        val vid = getPnpVid()

        return when (vid) {
            2007 -> VendorInfo(  // 0x07D7 (WCH - 沁恒微電子)
                name = "WCH",
                fullName = "WinChipHead",
                vid = vid,
                switchCommandType = "0x51 (BLECMD_SET_COMPOSITE)"
            )
            13 -> {  // 0x0D (TI - Texas Instruments，預設值)
                val isV0 = isDeviceV0()
                VendorInfo(
                    name = "TI",
                    fullName = "Texas Instruments",
                    vid = vid,
                    switchCommandType = if (isV0) {
                        "0x40 (BLECMD_SET_COMMON)"
                    } else {
                        "0x50 (BLECMD_SET_EMULDEVICE)"
                    }
                )
            }
            else -> VendorInfo(
                name = "Unknown",
                fullName = "Unknown Vendor",
                vid = vid,
                switchCommandType = "0x50 (預設策略)"
            )
        }
    }

    /**
     * 取得硬體型號
     *
     * 用於判斷是否支援 CH5 Unicode transport，決定使用何種文字輸入方式。
     *
     * @return 硬體型號（連線後可用）
     */
    fun getHardwareType(): EmulStickHardware {
        return hardwareType
    }

    /**
     * 寫入特徵值（通用方法，供外部控制器使用）
     *
     * 這個方法提供給 XInput 等特殊模式控制器使用，用於直接傳送 GATT 寫入操作。
     * 與內部的 writeMouseReport/writeKeyboardReport 不同，這個方法需要調用者指定完整的 UUID。
     *
     * @param characteristicUuid 特徵值 UUID
     * @param data 要寫入的資料
     * @return 是否成功加入佇列
     */
    fun writeCharacteristic(characteristicUuid: java.util.UUID, data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false

        // 根據 UUID 找到對應的特徵值
        val characteristic = when (characteristicUuid) {
            GattConstants.CHAR_CH1 -> ch1Characteristic
            GattConstants.CHAR_CH3 -> ch3Characteristic
            GattConstants.CHAR_CH5_UNICODE -> ch5UnicodeCharacteristic
            GattConstants.CHAR_COMMAND -> commandCharacteristic
            else -> {
                // 嘗試從服務中動態查找
                gatt.getService(GattConstants.SERVICE_EMULSTICK)
                    ?.getCharacteristic(characteristicUuid)
            }
        }

        if (characteristic == null) {
            Log.e(TAG, "找不到特徵值：$characteristicUuid")
            ConnectionLogger.log("❌ 找不到特徵值：$characteristicUuid", ConnectionLogger.LogLevel.ERROR)
            return false
        }

        // 加入佇列
        actionQueue.enqueue(BleAction.WriteCharacteristic(data, characteristic))
        Log.d(TAG, "✅ 已將寫入操作加入佇列（UUID=$characteristicUuid，資料長度=${data.size}）")
        ConnectionLogger.log(
            "✅ 已將寫入操作加入佇列（UUID=$characteristicUuid，${data.size} bytes）",
            ConnectionLogger.LogLevel.DEBUG
        )
        return true
    }

    /**
     * 中斷連線
     */
    fun disconnect() {
        Log.d(TAG, "中斷連線")
        stopScan()
        cancelConnectionTimeout()
        resetConnectRetryState()
        bluetoothGatt?.disconnect()
        cleanup()
    }

    /**
     * 更新連線狀態（給 ViewModel 使用，用於自動重連機制）
     */
    internal fun updateConnectionState(state: BleConnectionState) {
        _connectionState.value = state
        ConnectionLogger.log(
            "🔄 ViewModel 更新連線狀態：$state",
            ConnectionLogger.LogLevel.DEBUG
        )
    }

    /**
     * 清理資源
     */
    private fun cleanup() {
        cancelConnectionTimeout()
        bluetoothGatt?.close()
        bluetoothGatt = null
        ch1Characteristic = null
        ch2Characteristic = null
        ch3Characteristic = null
        ch5UnicodeCharacteristic = null
        commandCharacteristic = null

        // 重置身份驗證狀態
        systemId = null
        firmwareVersion = null
        hardwareVersion = null
        softwareVersion = null
        hardwareType = EmulStickHardware.UNKNOWN
        isAuthenticationComplete = false
        clipboardBridgeQuery?.complete(false)
        clipboardBridgeQuery = null
        pendingClipboardStatuses.values.forEach { it.complete(0x87) }
        pendingClipboardStatuses.clear()

        if (_connectionState.value !is BleConnectionState.Error) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    // ============ 身份驗證 ============

    /**
     * 啟用特徵值通知
     */
    private fun enableCharacteristicNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        // 啟用本地通知
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            Log.e(TAG, "setCharacteristicNotification 失敗")
            return false
        }

        // 寫入 CCCD（Client Characteristic Configuration Descriptor）
        val descriptor = characteristic.getDescriptor(GattConstants.DESC_CCCD)
        if (descriptor == null) {
            Log.e(TAG, "找不到 CCCD descriptor")
            return false
        }

        descriptor.value = GattConstants.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    /**
     * 讀取裝置資訊
     */
    private fun readDeviceInfo(gatt: BluetoothGatt) {
        Log.d(TAG, "開始讀取裝置資訊")
        ConnectionLogger.log("📋 開始讀取裝置資訊（System ID、Firmware、Hardware、Software Version）", ConnectionLogger.LogLevel.INFO)

        val deviceInfoService = gatt.getService(GattConstants.SERVICE_DEVICE_INFO)
        if (deviceInfoService == null) {
            Log.e(TAG, "找不到 Device Information Service")
            ConnectionLogger.log("❌ 找不到 Device Information Service", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = BleConnectionState.Error("找不到 Device Information Service")
            disconnect()
            return
        }

        // 讀取 System ID（0x2A23）
        val systemIdChar = deviceInfoService.getCharacteristic(GattConstants.CHAR_SYSTEM_ID)
        if (systemIdChar == null) {
            Log.e(TAG, "找不到 System ID 特徵值")
            ConnectionLogger.log("❌ 找不到 System ID 特徵值", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = BleConnectionState.Error("找不到 System ID 特徵值")
            disconnect()
            return
        }

        // 先讀取 System ID
        val readSuccess = gatt.readCharacteristic(systemIdChar)
        if (!readSuccess) {
            Log.e(TAG, "讀取 System ID 失敗")
            ConnectionLogger.log("❌ 讀取 System ID 失敗", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = BleConnectionState.Error("讀取 System ID 失敗")
            disconnect()
        }
    }

    /**
     * 偵測硬體型號
     *
     * 根據 Device Information Service 的 Hardware Version 判斷硬體型號。
     *
     * 識別邏輯：
     * - Hardware Version 包含 "ESP32-S3" → ESP32-S3 Evo（支援 CH5 Unicode transport）
     * - Model Number 包含 "TiComposite" → 原廠 TI
     * - Model Number 包含 "WchComposite" → 原廠 WCH
     * - 其他 → UNKNOWN（預設為原廠模式，使用 Big5 Alt 碼）
     */
    private fun detectHardwareType() {
        val hwVer = hardwareVersion ?: ""
        val fwVer = firmwareVersion ?: ""

        hardwareType = when {
            // ESP32-S3 特徵：Hardware Version 包含 "ESP32-S3"
            hwVer.contains("ESP32-S3", ignoreCase = true) -> {
                Log.i(TAG, "🚀 偵測到 ESP32-S3 Evo 硬體")
                ConnectionLogger.log(
                    "🚀 偵測到 ESP32-S3 Evo 硬體（支援 HID／CH5 自動分流）",
                    ConnectionLogger.LogLevel.INFO
                )
                EmulStickHardware.ESP32S3_EVO
            }
            // 原廠 TI：通常 Firmware 或 Hardware Version 包含 "TI" 或型號資訊
            hwVer.contains("TI", ignoreCase = true) ||
            hwVer.contains("CC2650", ignoreCase = true) -> {
                Log.i(TAG, "📡 偵測到原廠 TI CC2650 硬體")
                ConnectionLogger.log(
                    "📡 偵測到原廠 TI CC2650 硬體（使用 Big5 Alt 碼模式）",
                    ConnectionLogger.LogLevel.INFO
                )
                EmulStickHardware.ORIGINAL_TI
            }
            // 原廠 WCH：通常 Firmware 或 Hardware Version 包含 "WCH" 或型號資訊
            hwVer.contains("WCH", ignoreCase = true) ||
            hwVer.contains("CH582", ignoreCase = true) -> {
                Log.i(TAG, "📡 偵測到原廠 WCH CH582 硬體")
                ConnectionLogger.log(
                    "📡 偵測到原廠 WCH CH582 硬體（使用 Big5 Alt 碼模式）",
                    ConnectionLogger.LogLevel.INFO
                )
                EmulStickHardware.ORIGINAL_WCH
            }
            // 未知硬體，預設為原廠模式（使用 Big5 Alt 碼，最安全）
            else -> {
                Log.w(TAG, "⚠️ 未知硬體型號（HW=$hwVer, FW=$fwVer），預設為原廠模式")
                ConnectionLogger.log(
                    "⚠️ 未知硬體型號，預設為原廠模式（使用 Big5 Alt 碼）",
                    ConnectionLogger.LogLevel.WARNING
                )
                EmulStickHardware.UNKNOWN
            }
        }

        Log.i(TAG, "✅ 硬體型號偵測完成：${hardwareType.getDisplayName()}")
        ConnectionLogger.log(
            "✅ 硬體型號：${hardwareType.getDisplayName()}",
            ConnectionLogger.LogLevel.INFO
        )
    }

    /**
     * 檢查裝置資訊是否都已讀取完成
     */
    private fun checkDeviceInfoComplete(gatt: BluetoothGatt) {
        if (systemId != null && firmwareVersion != null && hardwareVersion != null && softwareVersion != null) {
            Log.d(TAG, "裝置資訊讀取完成")
            ConnectionLogger.log(
                "✅ 裝置資訊讀取完成：",
                ConnectionLogger.LogLevel.INFO
            )
            ConnectionLogger.log(
                "   - System ID: ${AesCryptUtil.byteArrayToHexString(systemId!!)}",
                ConnectionLogger.LogLevel.INFO
            )
            ConnectionLogger.log(
                "   - Firmware Version: $firmwareVersion",
                ConnectionLogger.LogLevel.INFO
            )
            ConnectionLogger.log(
                "   - Hardware Revision: $hardwareVersion（硬體版本，UI 顯示）",
                ConnectionLogger.LogLevel.INFO
            )
            ConnectionLogger.log(
                "   - Software Version: $softwareVersion（軟體版本，用於身份驗證）",
                ConnectionLogger.LogLevel.INFO
            )

            // 傳送密文請求
            requestCipherText(gatt)
        }
    }

    /**
     * 傳送「取得密文」指令
     */
    private fun requestCipherText(gatt: BluetoothGatt) {
        val sysId = systemId ?: return

        if (sysId.size < 8) {
            Log.e(TAG, "System ID 長度不足 8 bytes")
            ConnectionLogger.log("❌ System ID 長度不足 8 bytes", ConnectionLogger.LogLevel.ERROR)
            _connectionState.value = BleConnectionState.Error("System ID 長度不足")
            disconnect()
            return
        }

        // 指令格式：[0x91, systemId[6], systemId[7]]
        val command = byteArrayOf(
            GattConstants.CMD_GET_CIPHERTEXT,
            sysId[6],
            sysId[7]
        )

        Log.d(TAG, "傳送密文請求：[0x${command[0].toString(16)}, 0x${command[1].toString(16)}, 0x${command[2].toString(16)}]")
        ConnectionLogger.log(
            "📤 傳送密文請求：[0x91, 0x${sysId[6].toString(16)}, 0x${sysId[7].toString(16)}]",
            ConnectionLogger.LogLevel.INFO
        )

        val cmdChar = commandCharacteristic ?: return
        cmdChar.value = command
        cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT  // 需要回應
        gatt.writeCharacteristic(cmdChar)
    }

    /**
     * 驗證密文
     */
    private fun verifyCipherText(cipherFromDongle: ByteArray): Boolean {
        val sysId = systemId ?: return false
        val swVersion = softwareVersion ?: return false

        // 1. 取得明文（根據軟體版本）
        val version = swVersion.take(3)  // 取前 3 字元（"1.0" 或 "2.0"）
        val plainText = GattConstants.PLAIN_TEXT_MAP[version]
        if (plainText == null) {
            Log.e(TAG, "不支援的軟體版本: $version")
            ConnectionLogger.log("❌ 不支援的軟體版本: $version（硬體版本=$hardwareVersion）", ConnectionLogger.LogLevel.ERROR)
            ConnectionLogger.log("   plainMap 中只有: ${GattConstants.PLAIN_TEXT_MAP.keys}", ConnectionLogger.LogLevel.ERROR)
            return false
        }

        Log.d(TAG, "使用明文密碼：$plainText（軟體版本 $version）")
        ConnectionLogger.log("🔑 使用明文密碼（軟體版本 $version）", ConnectionLogger.LogLevel.DEBUG)

        // 2. 生成密鑰（System ID 轉 16 進位字串）
        val key = AesCryptUtil.byteArrayToHexString(sysId)
        Log.d(TAG, "AES 密鑰：$key")
        ConnectionLogger.log("🔐 AES 密鑰：$key", ConnectionLogger.LogLevel.DEBUG)

        // 3. AES 加密
        val encrypted = AesCryptUtil.encrypt(key, plainText)
        Log.d(TAG, "加密結果（Base64）：$encrypted")
        ConnectionLogger.log("🔒 加密結果（Base64）：$encrypted", ConnectionLogger.LogLevel.DEBUG)

        // 4. 取前 16 字元，轉成 UTF-8 bytes
        val expectedCipher = encrypted.take(16).toByteArray(Charsets.UTF_8)
        Log.d(TAG, "預期密文（前 16 字元）：${String(expectedCipher)}")
        ConnectionLogger.log("🔍 預期密文：${String(expectedCipher)}", ConnectionLogger.LogLevel.DEBUG)

        // 5. 比對結果
        val verified = expectedCipher.contentEquals(cipherFromDongle)
        if (verified) {
            Log.d(TAG, "✅ 密文驗證成功")
            ConnectionLogger.log("✅ 密文驗證成功", ConnectionLogger.LogLevel.INFO)
        } else {
            Log.e(TAG, "❌ 密文驗證失敗")
            ConnectionLogger.log("❌ 密文驗證失敗", ConnectionLogger.LogLevel.ERROR)
            ConnectionLogger.log(
                "   接收到的密文：${String(cipherFromDongle)}",
                ConnectionLogger.LogLevel.DEBUG
            )
        }

        return verified
    }

    /**
     * 驗證完成
     */
    private fun onAuthenticationComplete(gatt: BluetoothGatt) {
        cancelConnectionTimeout()
        resetConnectRetryState()
        isAuthenticationComplete = true
        val deviceAddress = gatt.device.address

        // ✅ 優先使用快取的裝置名稱（從掃描時取得），避免直接連線時名稱為 null
        val deviceName = gatt.device.name
            ?: deviceNameCache[deviceAddress]
            ?: "未知裝置"

        _connectionState.value = BleConnectionState.Connected(deviceName, deviceAddress)
        Log.d(TAG, "🎉 身份驗證完成，BLE 連線建立完成: $deviceName")
        ConnectionLogger.log("🎉 身份驗證完成，BLE 連線建立完成: $deviceName", ConnectionLogger.LogLevel.INFO)

        // 自動查詢裝置當前模式
        queryCurrentDeviceMode()
    }

    /**
     * 查詢裝置當前模式
     */
    private fun queryCurrentDeviceMode() {
        val sysId = systemId
        if (sysId == null || sysId.size < 8) {
            Log.w(TAG, "⚠️ System ID 不可用，無法查詢裝置模式")
            return
        }

        val command = byteArrayOf(GattConstants.CMD_GET_EMULATE, sysId[6], sysId[7])

        Log.d(TAG, "📤 傳送查詢裝置模式指令：[0xA1, 0x${sysId[6].toString(16)}, 0x${sysId[7].toString(16)}]")
        ConnectionLogger.log("📤 傳送查詢裝置模式指令", ConnectionLogger.LogLevel.INFO)

        val commandChar = bluetoothGatt
            ?.getService(GattConstants.SERVICE_EMULSTICK)
            ?.getCharacteristic(GattConstants.CHAR_COMMAND)

        if (commandChar != null) {
            commandChar.value = command
            commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            bluetoothGatt?.writeCharacteristic(commandChar)
        }
    }

    /**
     * 更新連線狀態（附帶裝置模式）
     */
    private fun updateConnectionStateWithMode(deviceMode: DeviceMode) {
        val currentState = _connectionState.value
        if (currentState is BleConnectionState.Connected) {
            _connectionState.value = BleConnectionState.Connected(
                deviceName = currentState.deviceName,
                deviceAddress = currentState.deviceAddress,
                currentDeviceMode = deviceMode
            )
            Log.d(TAG, "✅ 已更新連線狀態：模式 = $deviceMode")
        }
    }

    /**
     * 釋放資源
     */
    fun release() {
        disconnect()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

/**
 * 廠商資訊
 *
 * 包含晶片廠商名稱、PNP VID 和切換指令類型資訊。
 */
data class VendorInfo(
    val name: String,           // 廠商簡稱（例如：TI、WCH）
    val fullName: String,       // 廠商全名（例如：Texas Instruments）
    val vid: Int,               // PNP Vendor ID
    val switchCommandType: String  // 切換指令類型（例如：0x50 (BLECMD_SET_EMULDEVICE)）
)
