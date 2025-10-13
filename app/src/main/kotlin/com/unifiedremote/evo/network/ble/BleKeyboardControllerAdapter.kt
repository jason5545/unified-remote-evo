package com.unifiedremote.evo.network.ble

import com.unifiedremote.evo.controller.KeyboardController
import com.unifiedremote.evo.network.UnifiedConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * BLE 鍵盤控制器適配器
 * 讓 BleKeyboardController 符合標準 KeyboardController 介面
 */
class BleKeyboardControllerAdapter(
    private val bleController: BleKeyboardController,
    dummyConnection: UnifiedConnectionManager
) : KeyboardController(dummyConnection), CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    /** 提供對底層 BLE 鍵盤控制器的存取（供 UI 使用） */
    val bleKeyboardController: BleKeyboardController
        get() = bleController

    override fun type(text: String) {
        launch {
            bleController.type(text)
        }
    }

    override fun press(key: String, modifiers: List<String>) {
        launch {
            if (modifiers.isEmpty()) {
                bleController.press(key)
            } else if (modifiers.size == 1) {
                bleController.press(key, modifiers[0])
            } else {
                // 多個修飾鍵：使用 combo
                val keys = modifiers + key
                bleController.combo(*keys.toTypedArray())
            }
        }
    }

    override fun enter() {
        launch {
            bleController.press("enter")
        }
    }

    override fun escape() {
        launch {
            bleController.press("escape")
        }
    }

    override fun backspace() {
        launch {
            bleController.press("backspace")
        }
    }

    override fun delete() {
        launch {
            bleController.press("delete")
        }
    }

    override fun tab() {
        launch {
            bleController.press("tab")
        }
    }

    override fun space() {
        launch {
            bleController.press("space")
        }
    }

    override fun up() {
        launch {
            bleController.press("up")
        }
    }

    override fun down() {
        launch {
            bleController.press("down")
        }
    }

    override fun left() {
        launch {
            bleController.press("left")
        }
    }

    override fun right() {
        launch {
            bleController.press("right")
        }
    }

    override fun ctrlC() {
        launch {
            bleController.combo("ctrl", "c")
        }
    }

    override fun ctrlV() {
        launch {
            bleController.combo("ctrl", "v")
        }
    }

    override fun ctrlX() {
        launch {
            bleController.combo("ctrl", "x")
        }
    }

    override fun ctrlZ() {
        launch {
            bleController.combo("ctrl", "z")
        }
    }

    override fun ctrlA() {
        launch {
            bleController.combo("ctrl", "a")
        }
    }
}
