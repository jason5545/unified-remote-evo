package com.unifiedremote.evo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// AMOLED 暗色主題（純黑背景，節省 AMOLED 螢幕電力）
private val AmoledColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AmoledBackground,       // 純黑背景
    surface = AmoledSurface,             // 接近黑的 surface
    surfaceVariant = AmoledScrollBar,    // 滾輪條等元件
    onBackground = Color(0xFFE6E1E5),    // 背景上的文字
    onSurface = Color(0xFFE6E1E5),       // Surface 上的文字
    onPrimary = Color(0xFF381E72),       // Primary 上的文字
    onSecondary = Color(0xFF332D41),     // Secondary 上的文字
    onTertiary = Color(0xFF492532)       // Tertiary 上的文字
)

// 原本的暗色主題
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface
)

@Composable
fun UnifiedRemoteEvoTheme(
    darkTheme: Boolean = true,
    useAmoled: Boolean = true,  // 預設使用 AMOLED 模式
    content: @Composable () -> Unit
) {
    val colorScheme = if (useAmoled) {
        AmoledColorScheme
    } else {
        DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // AMOLED 模式：狀態列也使用純黑
            window.statusBarColor = if (useAmoled) {
                Color.Black.toArgb()
            } else {
                colorScheme.primary.toArgb()
            }
            // 導覽列也使用純黑（AMOLED）
            window.navigationBarColor = if (useAmoled) {
                Color.Black.toArgb()
            } else {
                colorScheme.surface.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
