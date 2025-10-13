package com.unifiedremote.evo.network.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * BLE 滑鼠控制器（EmulStick HID 模式）
 */
class BleMouseController(
    private val bleManager: BleManager
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    fun move(deltaX: Int, deltaY: Int) {
        android.util.Log.d("BleMouseController", "📍 move() 被呼叫：deltaX=$deltaX, deltaY=$deltaY")
        com.unifiedremote.evo.network.ConnectionLogger.log(
            "📍 move() 被呼叫：deltaX=$deltaX, deltaY=$deltaY",
            com.unifiedremote.evo.network.ConnectionLogger.LogLevel.DEBUG
        )
        launch {
            bleManager.sendMouseMove(deltaX, deltaY)
        }
    }

    fun click(button: String = "left") {
        com.unifiedremote.evo.network.ConnectionLogger.log(
            "🖱️ click() 被呼叫：button=$button",
            com.unifiedremote.evo.network.ConnectionLogger.LogLevel.DEBUG
        )
        launch {
            val hidButton = when (button.lowercase()) {
                "left" -> HidReportBuilder.MOUSE_BUTTON_LEFT
                "right" -> HidReportBuilder.MOUSE_BUTTON_RIGHT
                "middle" -> HidReportBuilder.MOUSE_BUTTON_MIDDLE
                else -> HidReportBuilder.MOUSE_BUTTON_LEFT
            }
            bleManager.sendMouseClick(hidButton)
        }
    }

    fun doubleClick() {
        launch {
            // 發送兩次左鍵點擊
            bleManager.sendMouseClick(HidReportBuilder.MOUSE_BUTTON_LEFT)
            kotlinx.coroutines.delay(50)
            bleManager.sendMouseClick(HidReportBuilder.MOUSE_BUTTON_LEFT)
        }
    }

    fun down(button: String = "left") {
        launch {
            val hidButton = when (button.lowercase()) {
                "left" -> HidReportBuilder.MOUSE_BUTTON_LEFT
                "right" -> HidReportBuilder.MOUSE_BUTTON_RIGHT
                "middle" -> HidReportBuilder.MOUSE_BUTTON_MIDDLE
                else -> HidReportBuilder.MOUSE_BUTTON_LEFT
            }
            // 持續按住（不釋放）
            bleManager.sendMouseMove(0, 0, hidButton)
        }
    }

    fun up(button: String = "left") {
        launch {
            // 釋放按鍵（發送無按鍵狀態）
            bleManager.sendMouseMove(0, 0, 0)
        }
    }

    fun scroll(delta: Int) {
        launch {
            // HID 滾輪範圍 -15 ~ +15，可能需要分多次發送
            val clampedDelta = delta.coerceIn(-15, 15)
            bleManager.sendMouseScroll(clampedDelta)
        }
    }

    fun hscroll(delta: Int) {
        // EmulStick MouseV1 格式不支援水平滾輪（只有垂直滾輪 Z 軸）
        // UI 層會在 BLE 模式下隱藏水平滾輪條
    }
}
