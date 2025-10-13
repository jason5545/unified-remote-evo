package com.unifiedremote.evo.network.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * BLE 文字輸入模式
 *
 * 針對原廠 EmulStick（TI/WCH）提供兩種中文輸入模式：
 * - ALT_CODE：Alt+X Unicode 模式（穩定，支援所有 Unicode，約 170ms/字元）
 * - BIG5_ALT_CODE：Big5 Alt 碼混合模式（ASCII 用 HID 約 50ms/字元，中文用 Big5 Alt 碼約 600ms/字元）
 *
 * 注意：ESP32-S3 Evo 不使用此設定，永遠使用 HID Unicode 模式（最快）
 */
enum class BleTextInputMode {
    /** Alt+X Unicode 模式（支援所有 Unicode，穩定可靠，約 170ms/字元）*/
    ALT_CODE,

    /** Big5 Alt 碼混合模式（ASCII 用 HID，中文用 Big5 Alt 碼）*/
    BIG5_ALT_CODE
}

/**
 * BLE 鍵盤控制器（EmulStick HID 模式）
 *
 * 使用混合模式支援中英文輸入：
 * - ASCII 字元：HID 直接發送（快速）
 * - 中文字元：Alt+X Unicode（Windows 原生支援）
 */
class BleKeyboardController(
    private val bleManager: BleManager
) : CoroutineScope {

    companion object {
        private const val TAG = "BleKeyboardController"
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    // 混合鍵盤控制器（支援中英文）
    private val hybridController = BleKeyboardControllerHybrid(bleManager)

    /** 文字輸入模式（預設使用 Alt+X Unicode 模式）*/
    private var textInputMode = BleTextInputMode.ALT_CODE

    /**
     * 設定文字輸入模式
     *
     * 僅對原廠 EmulStick（TI/WCH）有效，ESP32-S3 Evo 永遠使用 HID Unicode 模式。
     *
     * @param mode 輸入模式（ALT_CODE 或 BIG5_ALT_CODE）
     */
    fun setTextInputMode(mode: BleTextInputMode) {
        textInputMode = mode
        Log.d(TAG, "BLE 文字輸入模式切換為: $mode")
    }

    /**
     * 取得目前文字輸入模式
     */
    fun getTextInputMode(): BleTextInputMode = textInputMode

    /**
     * 按下單個按鍵
     */
    fun press(key: String, modifier: String? = null) {
        launch {
            val usageId = keyToUsageId(key)
            val modifierBits = modifierToBits(modifier)
            bleManager.sendKeyPress(modifierBits, usageId)
        }
    }

    /**
     * 輸入文字（混合模式：支援中英文）
     *
     * 使用混合模式智慧切換：
     * - ASCII 字元（英文、數字、符號）→ HID 鍵盤報告（快速 ~20ms/字元）
     * - 中文字元 → Alt+X Unicode 模式（~170ms/字元）
     * - 特殊字元（換行、Tab）→ HID 功能鍵
     *
     * @param text 要輸入的文字（支援中英文混合）
     */
    fun type(text: String) {
        launch {
            sendText(text)
        }
    }

    /**
     * 根據硬體型號和使用者設定選擇最佳輸入模式
     *
     * - ESP32-S3 Evo：使用 HID Unicode 模式（快速，~40ms/字元，忽略 textInputMode）
     * - 原廠 TI/WCH：根據 textInputMode 選擇
     *   - ALT_CODE：Alt+X Unicode 模式（~170ms/字元，穩定可靠）
     *   - BIG5_ALT_CODE：Big5 Alt 碼混合模式（ASCII 約 50ms/字元，中文約 600ms/字元）
     */
    private suspend fun sendText(text: String) {
        val hardwareType = bleManager.getHardwareType()

        when (hardwareType) {
            EmulStickHardware.ESP32S3_EVO -> {
                // ESP32-S3 Evo：使用 HID Unicode 模式（最快）
                sendTextUnicode(text)
            }
            else -> {
                // 原廠 TI/WCH：根據使用者設定選擇模式
                when (textInputMode) {
                    BleTextInputMode.ALT_CODE -> {
                        // 使用 Alt+X Unicode 模式（穩定可靠）
                        hybridController.sendTextHybrid(text)
                    }
                    BleTextInputMode.BIG5_ALT_CODE -> {
                        // 使用 Big5 Alt 碼混合模式
                        bleManager.sendTextWithBig5AltCode(text)
                    }
                }
            }
        }
    }

    /**
     * 使用 HID Unicode 模式輸入文字（ESP32-S3 Evo 專用）
     *
     * 使用 CH5 characteristic 直接發送 Unicode 字元，
     * 速度比 Alt+X Unicode 快 4.25 倍（~40ms vs ~170ms）。
     *
     * @param text 要輸入的文字
     */
    private suspend fun sendTextUnicode(text: String) {
        for (char in text) {
            when (char) {
                '\n' -> bleManager.sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_ENTER)
                '\t' -> bleManager.sendKeyPress(0, HidReportBuilder.KeyboardUsage.KEY_TAB)
                else -> {
                    // 直接發送 Unicode（CH5）
                    bleManager.sendUnicodeChar(char)
                }
            }
        }
    }

    /**
     * 按鍵組合（如 Ctrl+C）
     */
    fun combo(vararg keys: String) {
        launch {
            // 簡單實作：最後一個是主鍵，其他都是修飾鍵
            if (keys.isEmpty()) return@launch

            val modifierBits = keys.dropLast(1).fold(0) { acc, mod ->
                acc or modifierToBits(mod)
            }
            val mainKey = keyToUsageId(keys.last())
            bleManager.sendKeyPress(modifierBits, mainKey)
        }
    }

    // ============ 按鍵映射 ============

    private fun keyToUsageId(key: String): Int {
        val lowerKey = key.lowercase()

        return when {
            // 特殊鍵（必須放在字母檢查之前，避免 "escape" 被誤判為 "e"）
            lowerKey == "enter" || lowerKey == "return" -> HidReportBuilder.KeyboardUsage.KEY_ENTER
            lowerKey == "backspace" || lowerKey == "back" -> HidReportBuilder.KeyboardUsage.KEY_BACKSPACE
            lowerKey == "delete" || lowerKey == "del" -> HidReportBuilder.KeyboardUsage.KEY_DELETE
            lowerKey == "tab" -> HidReportBuilder.KeyboardUsage.KEY_TAB
            lowerKey == "escape" || lowerKey == "esc" -> HidReportBuilder.KeyboardUsage.KEY_ESC
            lowerKey == "space" -> HidReportBuilder.KeyboardUsage.KEY_SPACE
            lowerKey == "home" -> HidReportBuilder.KeyboardUsage.KEY_HOME
            lowerKey == "end" -> HidReportBuilder.KeyboardUsage.KEY_END
            lowerKey == "pageup" -> HidReportBuilder.KeyboardUsage.KEY_PAGE_UP
            lowerKey == "pagedown" -> HidReportBuilder.KeyboardUsage.KEY_PAGE_DOWN
            lowerKey == "insert" -> HidReportBuilder.KeyboardUsage.KEY_INSERT

            // 方向鍵
            lowerKey == "up" || lowerKey == "arrowup" -> HidReportBuilder.KeyboardUsage.KEY_UP_ARROW
            lowerKey == "down" || lowerKey == "arrowdown" -> HidReportBuilder.KeyboardUsage.KEY_DOWN_ARROW
            lowerKey == "left" || lowerKey == "arrowleft" -> HidReportBuilder.KeyboardUsage.KEY_LEFT_ARROW
            lowerKey == "right" || lowerKey == "arrowright" -> HidReportBuilder.KeyboardUsage.KEY_RIGHT_ARROW

            // 功能鍵
            lowerKey == "f1" -> HidReportBuilder.KeyboardUsage.KEY_F1
            lowerKey == "f2" -> HidReportBuilder.KeyboardUsage.KEY_F2
            lowerKey == "f3" -> HidReportBuilder.KeyboardUsage.KEY_F3
            lowerKey == "f4" -> HidReportBuilder.KeyboardUsage.KEY_F4
            lowerKey == "f5" -> HidReportBuilder.KeyboardUsage.KEY_F5
            lowerKey == "f6" -> HidReportBuilder.KeyboardUsage.KEY_F6
            lowerKey == "f7" -> HidReportBuilder.KeyboardUsage.KEY_F7
            lowerKey == "f8" -> HidReportBuilder.KeyboardUsage.KEY_F8
            lowerKey == "f9" -> HidReportBuilder.KeyboardUsage.KEY_F9
            lowerKey == "f10" -> HidReportBuilder.KeyboardUsage.KEY_F10
            lowerKey == "f11" -> HidReportBuilder.KeyboardUsage.KEY_F11
            lowerKey == "f12" -> HidReportBuilder.KeyboardUsage.KEY_F12

            // 單字母（必須加入長度檢查，避免多字元字串被誤判）
            lowerKey.length == 1 && lowerKey[0] in 'a'..'z' -> {
                HidReportBuilder.KeyboardUsage.KEY_A + (lowerKey[0] - 'a')
            }

            // 數字
            lowerKey == "0" -> HidReportBuilder.KeyboardUsage.KEY_0
            lowerKey.length == 1 && lowerKey[0] in '1'..'9' -> {
                HidReportBuilder.KeyboardUsage.KEY_1 + (lowerKey[0] - '1')
            }

            else -> 0  // 未知按鍵
        }
    }

    private fun modifierToBits(modifier: String?): Int {
        return when (modifier?.lowercase()) {
            "ctrl", "control" -> HidReportBuilder.MODIFIER_LEFT_CTRL
            "shift" -> HidReportBuilder.MODIFIER_LEFT_SHIFT
            "alt" -> HidReportBuilder.MODIFIER_LEFT_ALT
            "gui", "win", "windows", "cmd", "command" -> HidReportBuilder.MODIFIER_LEFT_GUI
            "rightctrl" -> HidReportBuilder.MODIFIER_RIGHT_CTRL
            "rightshift" -> HidReportBuilder.MODIFIER_RIGHT_SHIFT
            "rightalt" -> HidReportBuilder.MODIFIER_RIGHT_ALT
            "rightgui" -> HidReportBuilder.MODIFIER_RIGHT_GUI
            else -> 0
        }
    }

    private fun charToUsageId(char: Char): Pair<Int, Boolean> {
        return when (char) {
            // 小寫字母
            in 'a'..'z' -> Pair(HidReportBuilder.KeyboardUsage.KEY_A + (char - 'a'), false)
            // 大寫字母
            in 'A'..'Z' -> Pair(HidReportBuilder.KeyboardUsage.KEY_A + (char - 'A'), true)
            // 數字
            '0' -> Pair(HidReportBuilder.KeyboardUsage.KEY_0, false)
            in '1'..'9' -> Pair(HidReportBuilder.KeyboardUsage.KEY_1 + (char - '1'), false)
            // 符號
            ' ' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SPACE, false)
            '.' -> Pair(HidReportBuilder.KeyboardUsage.KEY_PERIOD, false)
            ',' -> Pair(HidReportBuilder.KeyboardUsage.KEY_COMMA, false)
            '/' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SLASH, false)
            '-' -> Pair(HidReportBuilder.KeyboardUsage.KEY_MINUS, false)
            '=' -> Pair(HidReportBuilder.KeyboardUsage.KEY_EQUAL, false)
            ';' -> Pair(HidReportBuilder.KeyboardUsage.KEY_SEMICOLON, false)
            '\'' -> Pair(HidReportBuilder.KeyboardUsage.KEY_APOSTROPHE, false)
            '[' -> Pair(HidReportBuilder.KeyboardUsage.KEY_LEFT_BRACKET, false)
            ']' -> Pair(HidReportBuilder.KeyboardUsage.KEY_RIGHT_BRACKET, false)
            '\\' -> Pair(HidReportBuilder.KeyboardUsage.KEY_BACKSLASH, false)
            '`' -> Pair(HidReportBuilder.KeyboardUsage.KEY_GRAVE, false)
            '\n' -> Pair(HidReportBuilder.KeyboardUsage.KEY_ENTER, false)
            '\t' -> Pair(HidReportBuilder.KeyboardUsage.KEY_TAB, false)
            else -> Pair(0, false)  // 不支援的字元
        }
    }
}
