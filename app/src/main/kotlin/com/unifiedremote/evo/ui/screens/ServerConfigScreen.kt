package com.unifiedremote.evo.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.data.SavedDevice
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.ConnectionType
import com.unifiedremote.evo.network.BluetoothSetupStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ServerConfigScreen(
    onConnectTcp: (String, Int) -> Unit,
    onConnectBluetooth: (BluetoothDevice) -> Unit,
    onStartBleScan: () -> Unit,
    // ✅ 改用 MAC 地址而不是 BluetoothDevice
    onConnectBleDevice: (String) -> Unit,
    onConnectSaved: (SavedDevice) -> Unit,
    onRemoveSaved: (SavedDevice) -> Unit,
    bluetoothAvailable: Boolean,
    bleAvailable: Boolean,
    pairedDevices: List<BluetoothDevice>,
    // ✅ 改用 SavedDevice 而不是 BluetoothDevice
    bleScannedDevices: List<SavedDevice>,
    bleConnectionState: com.unifiedremote.evo.network.ble.BleConnectionState,
    savedDevices: List<SavedDevice>,
    bluetoothSetupStatus: BluetoothSetupStatus = BluetoothSetupStatus.READY,
    onEnableBluetooth: () -> Unit = {},
    onRequestBluetoothPermissions: () -> Unit = {},
    autoConnectStatus: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(ConnectionType.TCP) }
    var host by remember { mutableStateOf(TextFieldValue("100.")) }
    var port by remember { mutableStateOf(TextFieldValue("9512")) }
    var isConnecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingBluetoothDevice by remember { mutableStateOf<SavedDevice?>(null) }

    fun connectSaved(device: SavedDevice) {
        isConnecting = true
        onConnectSaved(device)
        scope.launch {
            delay(3000)
            isConnecting = false
        }
    }

    LaunchedEffect(bluetoothSetupStatus, pendingBluetoothDevice) {
        if (bluetoothSetupStatus == BluetoothSetupStatus.READY) {
            pendingBluetoothDevice?.let { device ->
                pendingBluetoothDevice = null
                connectSaved(device)
            }
        }
    }

    fun selectBluetoothMode(type: ConnectionType) {
        pendingBluetoothDevice = null
        selectedType = type
        when (bluetoothSetupStatus) {
            BluetoothSetupStatus.OFF -> onEnableBluetooth()
            BluetoothSetupStatus.PERMISSION_REQUIRED -> onRequestBluetoothPermissions()
            else -> Unit
        }
    }

    // 取得版本資訊
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val versionInfo = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "#${packageInfo.versionCode} (${packageInfo.versionName})"
        } catch (e: Exception) {
            "#unknown"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        if (autoConnectStatus != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = autoConnectStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Text(
            text = "Unified Remote Evo",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        BluetoothSetupPrompt(bluetoothSetupStatus, onEnableBluetooth, onRequestBluetoothPermissions)

        // 歷史裝置列表
        if (savedDevices.isNotEmpty()) {
            Text(
                text = "最近連線的裝置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column {
                    savedDevices.forEachIndexed { index, device ->
                        SavedDeviceItem(
                            device = device,
                            onClick = {
                                if (device.type != ConnectionType.TCP && bluetoothSetupStatus != BluetoothSetupStatus.READY) {
                                    selectBluetoothMode(device.type)
                                    if (bluetoothSetupStatus != BluetoothSetupStatus.UNSUPPORTED) {
                                        pendingBluetoothDevice = device
                                    }
                                } else {
                                    pendingBluetoothDevice = null
                                    connectSaved(device)
                                }
                            },
                            onRemove = { onRemoveSaved(device) },
                            enabled = !isConnecting
                        )
                        if (index < savedDevices.size - 1) {
                            Divider()
                        }
                    }
                }
            }

            Text(
                text = "或新增裝置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // 連線類型選擇

        Text(
            text = "連線類型",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // TCP 按鈕
            FilterChip(
                selected = selectedType == ConnectionType.TCP,
                onClick = { pendingBluetoothDevice = null; selectedType = ConnectionType.TCP },
                label = { Text("WiFi") },
                modifier = Modifier.weight(1f)
            )

            // 藍牙按鈕
            FilterChip(
                selected = selectedType == ConnectionType.BLUETOOTH,
                onClick = { selectBluetoothMode(ConnectionType.BLUETOOTH) },
                label = { Text("藍牙") },
                enabled = bluetoothSetupStatus != BluetoothSetupStatus.UNSUPPORTED,
                modifier = Modifier.weight(1f)
            )

            // BLE 按鈕
            FilterChip(
                selected = selectedType == ConnectionType.BLE_EMULSTICK,
                onClick = { selectBluetoothMode(ConnectionType.BLE_EMULSTICK) },
                label = { Text("BLE") },
                enabled = bluetoothSetupStatus != BluetoothSetupStatus.UNSUPPORTED,
                modifier = Modifier.weight(1f)
            )
        }

        // TCP 設定
        if (selectedType == ConnectionType.TCP) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("伺服器位址") },
                placeholder = { Text("100.x.x.x (Tailscale IP)") },
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("埠號") },
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )

            Button(
                onClick = {
                    val portNum = port.text.toIntOrNull() ?: 9512
                    isConnecting = true
                    onConnectTcp(host.text, portNum)
                    scope.launch {
                        delay(3000)
                        isConnecting = false
                    }
                },
                enabled = !isConnecting && host.text.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    if (isConnecting) "連線中..." else "連線",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isConnecting) "正在連線..." else "針對 Tailscale 環境最佳化",
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnecting)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 藍牙裝置列表
        if (selectedType == ConnectionType.BLUETOOTH && bluetoothAvailable) {
            if (pairedDevices.isEmpty()) {
                Text(
                    text = "未找到已配對的藍牙裝置\n請先在系統設定中配對裝置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                Text(
                    text = "選擇藍牙裝置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn {
                        items(pairedDevices) { device ->
                            BluetoothDeviceItem(
                                device = device,
                                onClick = {
                                    isConnecting = true
                                    onConnectBluetooth(device)
                                    scope.launch {
                                        delay(3000)
                                        isConnecting = false
                                    }
                                },
                                enabled = !isConnecting
                            )
                            if (pairedDevices.indexOf(device) < pairedDevices.size - 1) {
                                Divider()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isConnecting) "正在連線..." else "點選裝置進行連線",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnecting)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // BLE EmulStick 連線
        if (selectedType == ConnectionType.BLE_EMULSTICK && bluetoothSetupStatus == BluetoothSetupStatus.READY) {
            val isScanning = bleConnectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Scanning
            val isBleConnecting = bleConnectionState is com.unifiedremote.evo.network.ble.BleConnectionState.Connecting
            val clipboardManager = LocalClipboardManager.current

            // 手動輸入 MAC 地址
            var manualMacAddress by remember { mutableStateOf(TextFieldValue("")) }

            // 診斷：追蹤按鈕狀態
            LaunchedEffect(isScanning, bleAvailable) {
                ConnectionLogger.log(
                    "📋 按鈕狀態：isScanning=$isScanning, bleAvailable=$bleAvailable, enabled=${!isScanning && bleAvailable}",
                    ConnectionLogger.LogLevel.DEBUG
                )
            }

            // Log 顯示區域（BLE 專用） - 使用 reactive StateFlow
            val allLogs by ConnectionLogger.logsFlow.collectAsState()
            val logs = remember(allLogs) {
                allLogs.filter { entry ->
                    entry.message.contains("BLE", ignoreCase = true) ||
                    entry.message.contains("EmulStick", ignoreCase = true) ||
                    entry.message.contains("掃描", ignoreCase = true) ||
                    entry.message.contains("Scan", ignoreCase = true) ||
                    entry.message.contains("GATT", ignoreCase = true) ||
                    entry.message.contains("診斷", ignoreCase = true) ||
                    entry.message.contains("權限", ignoreCase = true)
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    // Log 標題和複製按鈕
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "除錯日誌",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val logText = logs.joinToString("\n")
                                clipboardManager.setText(AnnotatedString(logText))
                            },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("複製", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Divider()

                    // Log 內容
                    val reversedLogs = logs.reversed()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        reverseLayout = true  // 最新的 log 在下方
                    ) {
                        items(reversedLogs.size) { index ->
                            val entry = reversedLogs[index]
                            val level = when (entry.level) {
                                ConnectionLogger.LogLevel.DEBUG -> "DEBUG"
                                ConnectionLogger.LogLevel.INFO -> "INFO "
                                ConnectionLogger.LogLevel.WARNING -> "WARN "
                                ConnectionLogger.LogLevel.ERROR -> "ERROR"
                            }
                            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", locale)
                                .format(java.util.Date(entry.timestamp))
                            Text(
                                text = "[$timestamp] [$level] ${entry.message}",
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 手動輸入 MAC 地址區域
            Text(
                text = "手動連線（跳過掃描）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = manualMacAddress,
                onValueChange = { manualMacAddress = it },
                label = { Text("MAC 地址") },
                placeholder = { Text("F4:B8:98:31:12:C8") },
                enabled = !isBleConnecting,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    val mac = manualMacAddress.text.trim().uppercase()
                    ConnectionLogger.log(
                        "📋 手動連線到 MAC 地址: $mac",
                        ConnectionLogger.LogLevel.INFO
                    )
                    onConnectBleDevice(mac)
                },
                enabled = !isBleConnecting && manualMacAddress.text.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    if (isBleConnecting) "連線中..." else "直接連線",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // 掃描區域
            Text(
                text = "或掃描裝置",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // 如果還沒掃描或掃描結果為空，顯示掃描按鈕
            if (bleScannedDevices.isEmpty()) {
                Button(
                    onClick = {
                        ConnectionLogger.log(
                            "📋 掃描按鈕被點擊！enabled=${!isScanning && bleAvailable}, 開始呼叫 onStartBleScan()",
                            ConnectionLogger.LogLevel.INFO
                        )
                        onStartBleScan()
                    },
                    enabled = !isScanning && bleAvailable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        if (isScanning) "掃描中..." else "掃描 EmulStick 裝置",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isScanning) {
                        "正在掃描 EmulStick 裝置..."
                    } else {
                        "點擊按鈕掃描 EmulStick 裝置\n適用於 EmulStick 硬體接收器"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isScanning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 顯示掃描到的裝置列表
                Text(
                    text = "找到 ${bleScannedDevices.size} 個 EmulStick 裝置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn {
                        items(bleScannedDevices) { device ->
                            // ✅ 使用 SavedDeviceItem 顯示 BLE 裝置
                            BleDeviceItem(
                                device = device,
                                onClick = {
                                    ConnectionLogger.log(
                                        "🔘 使用者選擇裝置: ${device.name} (${device.bluetoothAddress})",
                                        ConnectionLogger.LogLevel.INFO
                                    )
                                    // ✅ 傳遞 MAC 地址而不是 BluetoothDevice
                                    onConnectBleDevice(device.bluetoothAddress ?: "")
                                },
                                enabled = !isBleConnecting
                            )
                            if (bleScannedDevices.indexOf(device) < bleScannedDevices.size - 1) {
                                Divider()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 重新掃描按鈕
                OutlinedButton(
                    onClick = {
                        onStartBleScan()
                    },
                    enabled = !isScanning && !isBleConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新掃描")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isBleConnecting) {
                        "正在連線..."
                    } else if (isScanning) {
                        "正在掃描..."
                    } else {
                        "點選裝置進行連線"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBleConnecting || isScanning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 版本資訊（固定在底部）
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "版本 $versionInfo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
        )
    }
}

/**
 * 歷史裝置項目
 */
@Composable
private fun SavedDeviceItem(
    device: SavedDevice,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                device.getSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            IconButton(
                onClick = onRemove,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除裝置",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

/**
 * 藍牙裝置項目
 */
@SuppressLint("MissingPermission")
@Composable
private fun BluetoothDeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                device.name ?: "未命名裝置",
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

/**
 * BLE 裝置項目（使用 SavedDevice）
 */
@Composable
private fun BleDeviceItem(
    device: SavedDevice,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                device.bluetoothAddress ?: "未知地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    )
}
