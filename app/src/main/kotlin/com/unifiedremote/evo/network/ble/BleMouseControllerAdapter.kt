package com.unifiedremote.evo.network.ble

import com.unifiedremote.evo.controller.MouseController
import com.unifiedremote.evo.network.UnifiedConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * BLE 滑鼠控制器適配器
 * 讓 BleMouseController 符合標準 MouseController 介面
 */
class BleMouseControllerAdapter(
    private val bleController: BleMouseController,
    dummyConnection: UnifiedConnectionManager
) : MouseController(dummyConnection), CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    override fun move(deltaX: Int, deltaY: Int) {
        launch {
            bleController.move(deltaX, deltaY)
        }
    }

    override fun click(button: String) {
        launch {
            bleController.click(button)
        }
    }

    override fun doubleClick() {
        launch {
            bleController.doubleClick()
        }
    }

    override fun down(button: String) {
        launch {
            bleController.down(button)
        }
    }

    override fun up(button: String) {
        launch {
            bleController.up(button)
        }
    }

    override fun scroll(delta: Int) {
        launch {
            bleController.scroll(delta)
        }
    }

    override fun hscroll(delta: Int) {
        launch {
            bleController.hscroll(delta)
        }
    }
}
