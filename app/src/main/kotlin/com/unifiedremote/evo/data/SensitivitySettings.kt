package com.unifiedremote.evo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 靈敏度設定
 */
data class SensitivitySettings(
    val mouseSensitivity: Float = 1.0f,      // 滑鼠移動靈敏度 (0.5 - 3.0)
    val verticalScrollSensitivity: Float = 3.0f,  // 垂直滾輪靈敏度 (1.0 - 10.0)
    val horizontalScrollSensitivity: Float = 3.0f // 水平滾輪靈敏度 (1.0 - 10.0)
)

/**
 * 靈敏度設定管理器
 */
class SensitivityManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("sensitivity_settings", Context.MODE_PRIVATE)

    // 使用 mutableStateOf 讓 Compose 能夠觀察變化
    var settings by mutableStateOf(loadSettings())
        private set

    /**
     * 載入設定
     */
    private fun loadSettings(): SensitivitySettings {
        return SensitivitySettings(
            mouseSensitivity = prefs.getFloat("mouse_sensitivity", 1.0f),
            verticalScrollSensitivity = prefs.getFloat("vertical_scroll_sensitivity", 3.0f),
            horizontalScrollSensitivity = prefs.getFloat("horizontal_scroll_sensitivity", 3.0f)
        )
    }

    /**
     * 儲存設定
     */
    fun saveSettings(newSettings: SensitivitySettings) {
        prefs.edit().apply {
            putFloat("mouse_sensitivity", newSettings.mouseSensitivity)
            putFloat("vertical_scroll_sensitivity", newSettings.verticalScrollSensitivity)
            putFloat("horizontal_scroll_sensitivity", newSettings.horizontalScrollSensitivity)
            apply()
        }
        settings = newSettings
    }

    /**
     * 重置為預設值
     */
    fun resetToDefaults() {
        saveSettings(SensitivitySettings())
    }
}
