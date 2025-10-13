package com.unifiedremote.evo.network.ble

import android.content.Context
import com.unifiedremote.evo.network.ConnectionState
import com.unifiedremote.evo.network.MouseButton
import com.unifiedremote.evo.network.RemoteController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE 模式遙控器（使用 EmulStick 藍牙協定）
 */
class BleRemoteController(context: Context) : RemoteController {

    private val bleManager = BleManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        // 監聽 BLE 連線狀態並轉換為統一的 ConnectionState
        scope.launch {
            bleManager.connectionState.collect { bleState ->
                _connectionState.value = when (bleState) {
                    is BleConnectionState.Disconnected -> ConnectionState.Disconnected
                    is BleConnectionState.Scanning -> ConnectionState.Connecting
                    is BleConnectionState.Connecting -> ConnectionState.Connecting
                    is BleConnectionState.Reconnecting -> ConnectionState.Connecting
                    is BleConnectionState.Connected -> ConnectionState.Connected(bleState.deviceName)
                    is BleConnectionState.Error -> ConnectionState.Error(bleState.message)
                }
            }
        }
    }

    override suspend fun connect() {
        bleManager.startScan()
    }

    override fun disconnect() {
        bleManager.disconnect()
    }

    override fun isConnected(): Boolean {
        return bleManager.isConnected()
    }

    // ============ 滑鼠控制 ============

    override suspend fun moveMouse(deltaX: Int, deltaY: Int) {
        bleManager.sendMouseMove(deltaX, deltaY)
    }

    override suspend fun clickMouse(button: MouseButton) {
        val hidButton = when (button) {
            MouseButton.LEFT -> HidReportBuilder.MOUSE_BUTTON_LEFT
            MouseButton.RIGHT -> HidReportBuilder.MOUSE_BUTTON_RIGHT
            MouseButton.MIDDLE -> HidReportBuilder.MOUSE_BUTTON_MIDDLE
        }
        bleManager.sendMouseClick(hidButton)
    }

    override suspend fun scrollMouse(deltaY: Int) {
        // HID 滾輪值範圍 -15 ~ +15
        val clampedDelta = deltaY.coerceIn(-15, 15)
        bleManager.sendMouseScroll(clampedDelta)
    }

    override suspend fun scrollMouseHorizontal(deltaX: Int) {
        // EmulStick 標準 HID 報告不支援水平滾輪
        // 需要額外實作或忽略
    }

    // ============ 鍵盤控制 ============

    override suspend fun pressKey(key: String, modifier: String?) {
        // 將按鍵名稱轉換為 HID Usage ID
        val usageId = keyNameToUsageId(key)
        val modifierBits = modifierNameToBits(modifier)

        bleManager.sendKeyPress(modifierBits, usageId)
    }

    override suspend fun typeText(text: String) {
        // 逐字元傳送（需要轉換為 HID 按鍵序列）
        // 這裡先簡單實作，實際需要完整的字元映射表
        text.forEach { char ->
            val (usageId, needShift) = charToUsageId(char)
            val modifier = if (needShift) HidReportBuilder.MODIFIER_LEFT_SHIFT else 0
            bleManager.sendKeyPress(modifier, usageId)
        }
    }

    override fun release() {
        bleManager.release()
        scope.cancel()
    }

    // ============ 按鍵映射 ============

    private fun keyNameToUsageId(key: String): Int {
        return when (key.lowercase()) {
            "enter" -> HidReportBuilder.KeyboardUsage.KEY_ENTER
            "backspace", "back" -> HidReportBuilder.KeyboardUsage.KEY_BACKSPACE
            "tab" -> HidReportBuilder.KeyboardUsage.KEY_TAB
            "escape", "esc" -> HidReportBuilder.KeyboardUsage.KEY_ESC
            "space" -> HidReportBuilder.KeyboardUsage.KEY_SPACE
            "delete", "del" -> HidReportBuilder.KeyboardUsage.KEY_DELETE

            "up" -> HidReportBuilder.KeyboardUsage.KEY_UP_ARROW
            "down" -> HidReportBuilder.KeyboardUsage.KEY_DOWN_ARROW
            "left" -> HidReportBuilder.KeyboardUsage.KEY_LEFT_ARROW
            "right" -> HidReportBuilder.KeyboardUsage.KEY_RIGHT_ARROW

            "home" -> HidReportBuilder.KeyboardUsage.KEY_HOME
            "end" -> HidReportBuilder.KeyboardUsage.KEY_END
            "pageup" -> HidReportBuilder.KeyboardUsage.KEY_PAGE_UP
            "pagedown" -> HidReportBuilder.KeyboardUsage.KEY_PAGE_DOWN

            "f1" -> HidReportBuilder.KeyboardUsage.KEY_F1
            "f2" -> HidReportBuilder.KeyboardUsage.KEY_F2
            "f3" -> HidReportBuilder.KeyboardUsage.KEY_F3
            "f4" -> HidReportBuilder.KeyboardUsage.KEY_F4
            "f5" -> HidReportBuilder.KeyboardUsage.KEY_F5
            "f6" -> HidReportBuilder.KeyboardUsage.KEY_F6
            "f7" -> HidReportBuilder.KeyboardUsage.KEY_F7
            "f8" -> HidReportBuilder.KeyboardUsage.KEY_F8
            "f9" -> HidReportBuilder.KeyboardUsage.KEY_F9
            "f10" -> HidReportBuilder.KeyboardUsage.KEY_F10
            "f11" -> HidReportBuilder.KeyboardUsage.KEY_F11
            "f12" -> HidReportBuilder.KeyboardUsage.KEY_F12

            else -> 0  // 未知按鍵
        }
    }

    private fun modifierNameToBits(modifier: String?): Int {
        return when (modifier?.lowercase()) {
            "ctrl" -> HidReportBuilder.MODIFIER_LEFT_CTRL
            "shift" -> HidReportBuilder.MODIFIER_LEFT_SHIFT
            "alt" -> HidReportBuilder.MODIFIER_LEFT_ALT
            "gui", "win" -> HidReportBuilder.MODIFIER_LEFT_GUI
            else -> 0
        }
    }

    private fun charToUsageId(char: Char): Pair<Int, Boolean> {
        return when (char) {
            in 'a'..'z' -> Pair(HidReportBuilder.KeyboardUsage.KEY_A + (char - 'a'), false)
            in 'A'..'Z' -> Pair(HidReportBuilder.KeyboardUsage.KEY_A + (char - 'A'), true)
            in '0'..'9' -> {
                if (char == '0') {
                    Pair(HidReportBuilder.KeyboardUsage.KEY_0, false)
                } else {
                    Pair(HidReportBuilder.KeyboardUsage.KEY_1 + (char - '1'), false)
                }
            }
            ' ' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SPACE, false)
            '.' -> Pair(HidReportBuilder.KeyboardUsage.KEY_PERIOD, false)
            ',' -> Pair(HidReportBuilder.KeyboardUsage.KEY_COMMA, false)
            '/' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SLASH, false)
            '-' -> Pair(HidReportBuilder.KeyboardUsage.KEY_MINUS, false)
            '=' -> Pair(HidReportBuilder.KeyboardUsage.KEY_EQUAL, false)
            else -> Pair(0, false)  // 不支援的字元
        }
    }
}
