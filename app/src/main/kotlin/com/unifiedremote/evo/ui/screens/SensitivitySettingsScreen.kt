package com.unifiedremote.evo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.data.SensitivitySettings
import com.unifiedremote.evo.data.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitivitySettingsScreen(
    currentSettings: SensitivitySettings,
    onSettingsChange: (SensitivitySettings) -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mouseSensitivity by remember { mutableStateOf(currentSettings.mouseSensitivity) }
    var verticalScrollSensitivity by remember { mutableStateOf(currentSettings.verticalScrollSensitivity) }
    var horizontalScrollSensitivity by remember { mutableStateOf(currentSettings.horizontalScrollSensitivity) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // 標題列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "設定",
                style = MaterialTheme.typography.headlineMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 重置按鈕
                TextButton(onClick = {
                    mouseSensitivity = 1.0f
                    verticalScrollSensitivity = 3.0f
                    horizontalScrollSensitivity = 3.0f
                }) {
                    Text("重置")
                }
                // 關閉按鈕
                TextButton(onClick = onBack) {
                    Text("關閉")
                }
            }
        }

        // 說明
        Text(
            text = "調整應用程式外觀和操作靈敏度，所有設定會即時生效並自動儲存。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 主題設定區塊
        Text(
            text = "主題",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeModeOption(
                    selected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    title = "淺色模式",
                    icon = "☀️"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThemeModeOption(
                    selected = currentThemeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    title = "深色模式",
                    icon = "🌙"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ThemeModeOption(
                    selected = currentThemeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    title = "跟隨系統",
                    icon = "🔄"
                )
            }
        }

        Divider(modifier = Modifier.padding(bottom = 24.dp))

        // 靈敏度標題
        Text(
            text = "靈敏度",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 滑鼠移動靈敏度
        SensitivitySlider(
            title = "滑鼠移動靈敏度",
            value = mouseSensitivity,
            valueRange = 0.5f..3.0f,
            steps = 24,  // 0.1 間隔
            onValueChange = {
                mouseSensitivity = it
                onSettingsChange(
                    SensitivitySettings(
                        mouseSensitivity = it,
                        verticalScrollSensitivity = verticalScrollSensitivity,
                        horizontalScrollSensitivity = horizontalScrollSensitivity
                    )
                )
            },
            displayValue = String.format("%.1fx", mouseSensitivity),
            description = "控制滑鼠游標移動速度"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 垂直滾輪靈敏度
        SensitivitySlider(
            title = "垂直滾輪靈敏度",
            value = verticalScrollSensitivity,
            valueRange = 1.0f..10.0f,
            steps = 17,  // 0.5 間隔
            onValueChange = {
                verticalScrollSensitivity = it
                onSettingsChange(
                    SensitivitySettings(
                        mouseSensitivity = mouseSensitivity,
                        verticalScrollSensitivity = it,
                        horizontalScrollSensitivity = horizontalScrollSensitivity
                    )
                )
            },
            displayValue = String.format("%.1fx", verticalScrollSensitivity),
            description = "控制右側滾輪條的滾動速度"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 水平滾輪靈敏度
        SensitivitySlider(
            title = "水平滾輪靈敏度",
            value = horizontalScrollSensitivity,
            valueRange = 1.0f..10.0f,
            steps = 17,  // 0.5 間隔
            onValueChange = {
                horizontalScrollSensitivity = it
                onSettingsChange(
                    SensitivitySettings(
                        mouseSensitivity = mouseSensitivity,
                        verticalScrollSensitivity = verticalScrollSensitivity,
                        horizontalScrollSensitivity = it
                    )
                )
            },
            displayValue = String.format("%.1fx", horizontalScrollSensitivity),
            description = "控制底部滾輪條的滾動速度"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 調整提示",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• 主題變更會立即套用\n" +
                            "• 靈敏度數值越大，移動/滾動速度越快\n" +
                            "• 建議先調整滑鼠靈敏度，再調整滾輪\n" +
                            "• 若操作不順手，可隨時點選「重置」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 主題模式選項
 */
@Composable
private fun ThemeModeOption(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 靈敏度滑桿組件
 */
@Composable
private fun SensitivitySlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    displayValue: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
