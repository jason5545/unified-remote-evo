package com.unifiedremote.evo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.ble.BleConnectionState
import com.unifiedremote.evo.viewmodel.BleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    bleViewModel: BleViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by ConnectionLogger.logsFlow.collectAsState()  // Reactive StateFlow
    val minLogLevel by ConnectionLogger.minLogLevel.collectAsState()  // 當前日誌等級
    val stats = ConnectionLogger.getStats()
    var showCopiedMessage by remember { mutableStateOf(false) }
    var showLogLevelMenu by remember { mutableStateOf(false) }

    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val logsText = logs.joinToString("\n") { entry ->
            "[${entry.level}] ${entry.message}"
        }
        val clip = ClipData.newPlainText("除錯日誌", logsText)
        clipboard.setPrimaryClip(clip)
        showCopiedMessage = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("除錯日誌") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    // 日誌等級選擇器
                    Box {
                        IconButton(onClick = { showLogLevelMenu = true }) {
                            Text(
                                text = when (minLogLevel) {
                                    ConnectionLogger.LogLevel.DEBUG -> "🔍"
                                    ConnectionLogger.LogLevel.INFO -> "ℹ️"
                                    ConnectionLogger.LogLevel.WARNING -> "⚠️"
                                    ConnectionLogger.LogLevel.ERROR -> "❌"
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showLogLevelMenu,
                            onDismissRequest = { showLogLevelMenu = false }
                        ) {
                            ConnectionLogger.LogLevel.values().forEach { level ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = when (level) {
                                                    ConnectionLogger.LogLevel.DEBUG -> "🔍"
                                                    ConnectionLogger.LogLevel.INFO -> "ℹ️"
                                                    ConnectionLogger.LogLevel.WARNING -> "⚠️"
                                                    ConnectionLogger.LogLevel.ERROR -> "❌"
                                                }
                                            )
                                            Text(level.displayName)
                                            if (level == minLogLevel) {
                                                Text("✓", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        ConnectionLogger.setMinLogLevel(level)
                                        showLogLevelMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = copyToClipboard) {
                        Text("📋")
                    }
                    IconButton(onClick = { ConnectionLogger.clear() }) {
                        Text("🗑️")
                    }
                }
            )
        },
        snackbarHost = {
            if (showCopiedMessage) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showCopiedMessage = false }) {
                            Text("確定")
                        }
                    }
                ) {
                    Text("已複製到剪貼簿")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 晶片廠商資訊（僅在 BLE 連線時顯示）
            val vendorInfo = bleViewModel?.getVendorInfo()
            val connectionState = bleViewModel?.bleManager?.connectionState?.collectAsState()?.value
            val deviceMode = (connectionState as? BleConnectionState.Connected)?.currentDeviceMode

            if (vendorInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "晶片廠商資訊",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "廠商：${vendorInfo.fullName} (${vendorInfo.name})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PNP VID：${vendorInfo.vid} (0x${vendorInfo.vid.toString(16).uppercase()})",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // PNP ID 原始資料（除錯用）
                        val pnpIdRaw = bleViewModel.getPnpIdRaw()
                        if (pnpIdRaw != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "PNP ID Raw：[${pnpIdRaw.joinToString(" ") { "0x%02X".format(it) }}]",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        // 韌體版本
                        val fwVersion = bleViewModel.getFirmwareVersion()
                        if (fwVersion != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "韌體版本：$fwVersion",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // 軟體版本（用於判斷明文密碼）
                        val swVersion = bleViewModel.getSoftwareVersion()
                        if (swVersion != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "軟體版本：$swVersion",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "切換指令：${vendorInfo.switchCommandType}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // 裝置模式（僅在已連線時顯示）
                        if (deviceMode != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "裝置模式：${deviceMode.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when(deviceMode) {
                                    com.unifiedremote.evo.network.ble.DeviceMode.COMPOSITE -> Color(0xFF4CAF50)  // 綠色
                                    com.unifiedremote.evo.network.ble.DeviceMode.XINPUT -> Color(0xFF2196F3)     // 藍色
                                    com.unifiedremote.evo.network.ble.DeviceMode.SINGLE_KEYBOARD -> Color(0xFFFF9800)  // 橘色
                                    com.unifiedremote.evo.network.ble.DeviceMode.UNKNOWN -> Color.Gray          // 灰色
                                }
                            )
                        }
                    }
                }
            }

            // 統計資訊
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = if (vendorInfo != null) 8.dp else 16.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "統計資訊",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("總計: ${stats.totalLogs}")
                        Text("錯誤: ${stats.errorCount}", color = Color.Red)
                        Text("警告: ${stats.warningCount}", color = Color(0xFFFFA500))
                        Text("資訊: ${stats.infoCount}")
                    }
                }
            }

            // 日誌列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(logs.reversed()) { entry ->
                    val color = when (entry.level) {
                        ConnectionLogger.LogLevel.DEBUG -> Color.Gray
                        ConnectionLogger.LogLevel.INFO -> Color.White
                        ConnectionLogger.LogLevel.WARNING -> Color(0xFFFFA500)
                        ConnectionLogger.LogLevel.ERROR -> Color.Red
                    }

                    Text(
                        text = entry.message,
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
