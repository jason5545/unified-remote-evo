package com.unifiedremote.evo.controller

import com.unifiedremote.evo.data.*
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.UnifiedConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * 鍵盤控制器
 */
open class KeyboardController(
    private val connection: UnifiedConnectionManager
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    companion object {
        const val REMOTE_ID = "Relmtech.Basic Input"  // 使用已載入的 Remote ID
    }

    /**
     * 建立標準控制指令封包
     */
    private fun createControlPacket(action: Action): Packet {
        val control = Control(
            type = 8,  // Type = 8 表示控制項
            onAction = action
        )

        val layout = Layout(
            controls = mutableListOf(control)
        )

        return Packet(
            action = 7,
            request = 7,
            id = REMOTE_ID,
            destination = null,  // 可選，指定目標裝置
            run = action,        // 保留 run 欄位（相容性）
            layout = layout      // 標準模式：Layout + Controls
        )
    }

    open fun type(text: String) {
        launch {
            val extras = Extras().apply {
                add("Text", text)  // 參數名大寫
            }
            val action = Action("Text", "Core.Input", extras)

            val preview = if (text.length > 20) "${text.take(20)}..." else text
            ConnectionLogger.log("發送文字: '$preview'", ConnectionLogger.LogLevel.DEBUG)

            connection.send(createControlPacket(action))
        }
    }

    open fun press(key: String, modifiers: List<String> = emptyList()) {
        launch {
            val extras = Extras().apply {
                add("KeyCode", key.uppercase())  // 參數名 KeyCode，值大寫（符合原版 APK）
                modifiers.forEach { modifier ->
                    add("Modifier", modifier.uppercase())  // Modifier 也大寫
                }
            }
            val action = Action("Press", "Core.Input", extras)

            val logMsg = if (modifiers.isEmpty()) {
                "發送按鍵: ${key.uppercase()}"
            } else {
                "發送組合鍵: ${modifiers.joinToString("+"){ it.uppercase() }}+${key.uppercase()}"
            }
            android.util.Log.d("KeyboardController", logMsg)
            ConnectionLogger.log(logMsg, ConnectionLogger.LogLevel.DEBUG)

            connection.send(createControlPacket(action))
        }
    }

    object Shortcuts {
        const val UP = "up"
        const val DOWN = "down"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val ENTER = "enter"
        const val ESCAPE = "escape"
        const val BACKSPACE = "backspace"
        const val DELETE = "delete"
        const val TAB = "tab"
        const val SPACE = "space"
        const val HOME = "home"
        const val END = "end"
        const val PAGE_UP = "pageup"
        const val PAGE_DOWN = "pagedown"
        const val CTRL = "ctrl"
        const val SHIFT = "shift"
        const val ALT = "alt"
    }

    open fun enter() = press(Shortcuts.ENTER)
    open fun escape() = press(Shortcuts.ESCAPE)
    open fun backspace() {
        press(Shortcuts.BACKSPACE)
    }
    open fun delete() = press(Shortcuts.DELETE)
    open fun tab() = press(Shortcuts.TAB)
    open fun space() = press(Shortcuts.SPACE)

    open fun up() = press(Shortcuts.UP)
    open fun down() = press(Shortcuts.DOWN)
    open fun left() = press(Shortcuts.LEFT)
    open fun right() = press(Shortcuts.RIGHT)

    open fun ctrlC() = press("c", listOf(Shortcuts.CTRL))
    open fun ctrlV() = press("v", listOf(Shortcuts.CTRL))
    open fun ctrlX() = press("x", listOf(Shortcuts.CTRL))
    open fun ctrlZ() = press("z", listOf(Shortcuts.CTRL))
    open fun ctrlA() = press("a", listOf(Shortcuts.CTRL))
}
