package com.unifiedremote.evo.controller

import com.unifiedremote.evo.data.*
import com.unifiedremote.evo.network.UnifiedConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * 滑鼠控制器
 */
open class MouseController(
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

    open fun move(deltaX: Int, deltaY: Int) {
        launch {
            val extras = Extras().apply {
                add("X", deltaX)  // 參數名大寫
                add("Y", deltaY)
            }
            val action = Action("MoveBy", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }

    open fun click(button: String = "left") {
        launch {
            val extras = Extras().apply {
                add("Button", button.capitalize())  // 參數名大寫，值首字母大寫
            }
            val action = Action("Click", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }

    open fun doubleClick() {
        launch {
            val action = Action("Double", "Core.Input", null)
            connection.send(createControlPacket(action))
        }
    }

    open fun down(button: String = "left") {
        launch {
            val extras = Extras().apply {
                add("Button", button.capitalize())
            }
            val action = Action("MouseDown", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }

    open fun up(button: String = "left") {
        launch {
            val extras = Extras().apply {
                add("Button", button.capitalize())
            }
            val action = Action("MouseUp", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }

    open fun scroll(delta: Int) {
        launch {
            val extras = Extras().apply {
                add("V", delta)  // 垂直滾輪用 V
            }
            val action = Action("Vert", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }

    open fun hscroll(delta: Int) {
        launch {
            val extras = Extras().apply {
                add("H", delta)  // 水平滾輪用 H
            }
            val action = Action("Horz", "Core.Input", extras)
            connection.send(createControlPacket(action))
        }
    }
}
