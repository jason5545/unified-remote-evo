package com.unifiedremote.evo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 主題模式
 */
enum class ThemeMode {
    LIGHT,      // 淺色模式
    DARK,       // 深色模式
    SYSTEM      // 跟隨系統
}

/**
 * 主題設定管理器
 */
class ThemeManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    // 使用 mutableStateOf 讓 Compose 能夠觀察變化
    var themeMode by mutableStateOf(loadThemeMode())
        private set

    /**
     * 載入主題設定
     */
    private fun loadThemeMode(): ThemeMode {
        val mode = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(mode ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    /**
     * 儲存主題設定
     */
    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().apply {
            putString("theme_mode", mode.name)
            apply()
        }
        themeMode = mode
    }
}
