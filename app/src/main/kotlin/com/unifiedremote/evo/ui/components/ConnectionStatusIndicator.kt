package com.unifiedremote.evo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.network.UnifiedConnectionState
import com.unifiedremote.evo.network.ble.BleConnectionState
import kotlinx.coroutines.delay

/**
 * 連線狀態指示器（不干擾的浮動提示）
 *
 * 特性：
 * - 小型半透明提示，位於螢幕頂部
 * - 連線成功後 3 秒自動隱藏
 * - 重連時持續顯示
 * - 不干擾觸控板操作
 *
 * @param tcpState TCP 連線狀態（Unified Remote TCP 模式）
 * @param bluetoothState 藍牙連線狀態（Unified Remote 藍牙模式）
 * @param bleState BLE 連線狀態（EmulStick BLE 模式）
 * @param modifier Modifier
 */
@Composable
fun ConnectionStatusIndicator(
    tcpState: UnifiedConnectionState? = null,
    bluetoothState: UnifiedConnectionState? = null,
    bleState: BleConnectionState? = null,
    modifier: Modifier = Modifier
) {
    // 只顯示錯誤狀態，連線成功完全不顯示（包括首次和重連後）
    val displayState = when {
        tcpState is UnifiedConnectionState.Error ->
            StatusInfo.fromTcpState(tcpState)
        bluetoothState is UnifiedConnectionState.Error ->
            StatusInfo.fromBluetoothState(bluetoothState)
        bleState is BleConnectionState.Error ->
            StatusInfo.fromBleState(bleState)
        else -> null
    }

    // 控制可見性（只有錯誤狀態，持續顯示直到狀態改變）
    val isVisible = displayState != null

    AnimatedVisibility(
        visible = isVisible && displayState != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = EaseOut)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = EaseIn)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        displayState?.let { info ->
            ConnectionStatusBadge(
                icon = info.icon,
                text = info.text,
                backgroundColor = info.backgroundColor,
                isAnimating = info.isAnimating
            )
        }
    }
}

/**
 * 連線狀態徽章（實際 UI 元件）
 */
@Composable
private fun ConnectionStatusBadge(
    icon: String,
    text: String,
    backgroundColor: Color,
    isAnimating: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 動畫透明度（用於重連狀態的呼吸效果）
    val alpha by rememberInfiniteTransition(label = "alphaAnimation").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                backgroundColor.copy(
                    alpha = if (isAnimating) alpha else 0.9f
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

/**
 * 狀態資訊資料類別
 */
private data class StatusInfo(
    val icon: String,
    val text: String,
    val backgroundColor: Color,
    val isConnected: Boolean = false,
    val isAnimating: Boolean = false
) {
    companion object {
        fun fromTcpState(state: UnifiedConnectionState): StatusInfo {
            return when (state) {
                is UnifiedConnectionState.Connecting -> StatusInfo(
                    icon = "🔌",
                    text = state.message,
                    backgroundColor = Color(0xFF2196F3),
                    isAnimating = true
                )
                is UnifiedConnectionState.Connected -> StatusInfo(
                    icon = "✅",
                    text = "已連線：${state.deviceInfo}",
                    backgroundColor = Color(0xFF4CAF50),
                    isConnected = true
                )
                is UnifiedConnectionState.Reconnecting -> StatusInfo(
                    icon = "🔄",
                    text = "重連中（${state.attempt}/${state.maxAttempts}）...",
                    backgroundColor = Color(0xFFFF9800),
                    isAnimating = true
                )
                is UnifiedConnectionState.Error -> StatusInfo(
                    icon = "❌",
                    text = state.message,
                    backgroundColor = Color(0xFFF44336)
                )
                is UnifiedConnectionState.Disconnected -> StatusInfo(
                    icon = "⚠️",
                    text = "已斷線",
                    backgroundColor = Color(0xFF9E9E9E)
                )
            }
        }

        fun fromBluetoothState(state: UnifiedConnectionState): StatusInfo {
            return when (state) {
                is UnifiedConnectionState.Connecting -> StatusInfo(
                    icon = "📡",
                    text = "${state.message} (藍牙)",
                    backgroundColor = Color(0xFF2196F3),
                    isAnimating = true
                )
                is UnifiedConnectionState.Connected -> StatusInfo(
                    icon = "✅",
                    text = "已連線：${state.deviceInfo} (藍牙)",
                    backgroundColor = Color(0xFF4CAF50),
                    isConnected = true
                )
                is UnifiedConnectionState.Reconnecting -> StatusInfo(
                    icon = "🔄",
                    text = "藍牙重連中（${state.attempt}/${state.maxAttempts}）...",
                    backgroundColor = Color(0xFFFF9800),
                    isAnimating = true
                )
                is UnifiedConnectionState.Error -> StatusInfo(
                    icon = "❌",
                    text = "藍牙：${state.message}",
                    backgroundColor = Color(0xFFF44336)
                )
                is UnifiedConnectionState.Disconnected -> StatusInfo(
                    icon = "⚠️",
                    text = "藍牙已斷線",
                    backgroundColor = Color(0xFF9E9E9E)
                )
            }
        }

        fun fromBleState(state: BleConnectionState): StatusInfo {
            return when (state) {
                is BleConnectionState.Scanning -> StatusInfo(
                    icon = "🔍",
                    text = "掃描中...",
                    backgroundColor = Color(0xFF2196F3),
                    isAnimating = true
                )
                is BleConnectionState.Connecting -> StatusInfo(
                    icon = "📡",
                    text = "正在連線到 ${state.deviceName}...",
                    backgroundColor = Color(0xFF2196F3),
                    isAnimating = true
                )
                is BleConnectionState.Connected -> StatusInfo(
                    icon = "✅",
                    text = "已連線：${state.deviceName}",
                    backgroundColor = Color(0xFF4CAF50),
                    isConnected = true
                )
                is BleConnectionState.Reconnecting -> StatusInfo(
                    icon = "🔄",
                    text = "BLE 重連中（${state.attempt}/${state.maxAttempts}）...",
                    backgroundColor = Color(0xFFFF9800),
                    isAnimating = true
                )
                is BleConnectionState.Error -> StatusInfo(
                    icon = "❌",
                    text = "BLE：${state.message}",
                    backgroundColor = Color(0xFFF44336)
                )
                is BleConnectionState.Disconnected -> StatusInfo(
                    icon = "⚠️",
                    text = "BLE 已斷線",
                    backgroundColor = Color(0xFF9E9E9E)
                )
            }
        }
    }
}
