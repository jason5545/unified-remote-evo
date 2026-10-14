package com.unifiedremote.evo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.network.BluetoothSetupStatus

@Composable
fun BluetoothSetupPrompt(status: BluetoothSetupStatus, onEnable: () -> Unit, onPermissions: () -> Unit) {
    if (status == BluetoothSetupStatus.READY) return
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (status) {
                BluetoothSetupStatus.OFF -> {
                    Text("藍牙已關閉", style = MaterialTheme.typography.titleMedium)
                    Text("開啟藍牙後即可使用藍牙／BLE 連線。")
                    Button(onClick = onEnable, modifier = Modifier.padding(top = 8.dp)) { Text("開啟藍牙") }
                }
                BluetoothSetupStatus.PERMISSION_REQUIRED -> {
                    Text("需要藍牙權限", style = MaterialTheme.typography.titleMedium)
                    Text("允許附近裝置權限後，即可連線或掃描接收器。")
                    Button(onClick = onPermissions, modifier = Modifier.padding(top = 8.dp)) { Text("授予藍牙權限") }
                }
                BluetoothSetupStatus.UNSUPPORTED -> Text("此裝置不支援藍牙")
                BluetoothSetupStatus.READY -> Unit
            }
        }
    }
}
