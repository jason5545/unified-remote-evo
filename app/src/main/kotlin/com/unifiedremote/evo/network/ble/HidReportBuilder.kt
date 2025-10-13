package com.unifiedremote.evo.network.ble

import kotlin.math.abs

/**
 * HID Report 建構器
 *
 * 建構符合 USB HID 標準的滑鼠與鍵盤報告
 * 資料來源：apk_analysis/emulstick_decompiled/.../hid/ReportMouse.java & ReportKeyboard.java
 */
object HidReportBuilder {

    // ============ 滑鼠按鈕常量 ============

    const val MOUSE_BUTTON_LEFT = 0x01
    const val MOUSE_BUTTON_RIGHT = 0x02
    const val MOUSE_BUTTON_MIDDLE = 0x04
    const val MOUSE_BUTTON_SIDE_4 = 0x08
    const val MOUSE_BUTTON_SIDE_5 = 0x10

    // ============ 鍵盤修飾鍵常量 ============

    const val MODIFIER_LEFT_CTRL = 0x01
    const val MODIFIER_LEFT_SHIFT = 0x02
    const val MODIFIER_LEFT_ALT = 0x04
    const val MODIFIER_LEFT_GUI = 0x08
    const val MODIFIER_RIGHT_CTRL = 0x10
    const val MODIFIER_RIGHT_SHIFT = 0x20
    const val MODIFIER_RIGHT_ALT = 0x40
    const val MODIFIER_RIGHT_GUI = 0x80

    // ============ Report IDs ============

    const val REPORT_ID_KEYBOARD = 1
    // MouseV1 (Ver ≥1) 不使用 Report ID

    // ============ 滑鼠 HID Report ============

    /**
     * 建構滑鼠 HID 報告（EmulStick MouseV1 格式，Ver ≥1）
     *
     * 格式（6 bytes，無 Report ID）：
     * [0] 按鈕狀態（bit mask）
     * [1-2] X 軸移動（16-bit signed little-endian，-2047 ~ +2047）
     * [3-4] Y 軸移動（16-bit signed little-endian，-2047 ~ +2047）
     * [5] 滾輪（8-bit signed，-15 ~ +15）
     *
     * 注意：
     * - 此格式用於 EmulStick Ver ≥1（預設的「組合鍵鼠設備」模式）
     * - Report ID = 0（表示不加 Report ID byte）
     * - 使用 CH3 characteristic (0xF803)
     * - 舊版 Mouse 格式（7 bytes with Report ID=3, CH1）僅用於 Ver 0/-1
     *
     * 參考：apk_analysis/emulstick_decompiled/.../hid/ReportInfo.java (MouseV1)
     *
     * @param deltaX X 軸相對移動量
     * @param deltaY Y 軸相對移動量
     * @param buttons 按鈕狀態（bit mask）
     * @param wheel 滾輪值（負數向上，正數向下）
     * @return 6 bytes HID 報告（無 Report ID）
     */
    fun buildMouseReport(
        deltaX: Int = 0,
        deltaY: Int = 0,
        buttons: Int = 0,
        wheel: Int = 0
    ): ByteArray {
        // 限制範圍
        val clampedX = deltaX.coerceIn(-2047, 2047)
        val clampedY = deltaY.coerceIn(-2047, 2047)
        val clampedWheel = wheel.coerceIn(-15, 15)

        return byteArrayOf(
            buttons.toByte(),                       // [0] 按鈕
            (clampedX and 0xFF).toByte(),           // [1] X 低位元組
            ((clampedX shr 8) and 0xFF).toByte(),   // [2] X 高位元組
            (clampedY and 0xFF).toByte(),           // [3] Y 低位元組
            ((clampedY shr 8) and 0xFF).toByte(),   // [4] Y 高位元組
            clampedWheel.toByte()                   // [5] 滾輪
        )
    }

    /**
     * 將大幅度移動分割為多個報告（避免超出 ±2047 限制）
     *
     * EmulStick 原始實作：將移動分割為多個小於 2047 的區塊
     * 參考：MousePad.java 第 138-141 行
     *
     * @param deltaX X 軸移動量
     * @param deltaY Y 軸移動量
     * @param buttons 按鈕狀態
     * @return 多個滑鼠報告
     */
    fun buildSplitMouseReports(
        deltaX: Int,
        deltaY: Int,
        buttons: Int = 0
    ): List<ByteArray> {
        val maxDelta = maxOf(abs(deltaX), abs(deltaY))
        val splitCount = (maxDelta / 2047) + 1

        if (splitCount <= 1) {
            return listOf(buildMouseReport(deltaX, deltaY, buttons))
        }

        return (0 until splitCount).map {
            buildMouseReport(
                deltaX = deltaX / splitCount,
                deltaY = deltaY / splitCount,
                buttons = buttons
            )
        }
    }

    // ============ 鍵盤 HID Report ============

    /**
     * 建構鍵盤 HID 報告（EmulStick SingleKeyboard 格式，Ver ≥1）
     *
     * 格式（8 bytes，無 Report ID）：
     * [0] 修飾鍵（bit mask）
     *     - Bit 0: Left Ctrl  (0xE0)
     *     - Bit 1: Left Shift (0xE1)
     *     - Bit 2: Left Alt   (0xE2)
     *     - Bit 3: Left GUI   (0xE3)
     *     - Bit 4: Right Ctrl (0xE4)
     *     - Bit 5: Right Shift(0xE5)
     *     - Bit 6: Right Alt  (0xE6)
     *     - Bit 7: Right GUI  (0xE7)
     * [1] 保留（0x00）
     * [2-7] 按鍵代碼（最多 6 個按鍵同時按下）
     *
     * 注意：
     * - 此格式用於 EmulStick Ver ≥1（預設的「組合鍵鼠設備」模式）
     * - Report ID = 0（表示不加 Report ID byte）
     * - 使用 CH1 characteristic (0xF801)
     * - 舊版 Keyboard 格式（9 bytes with Report ID=1）僅用於 Ver 0/-1
     *
     * 參考：apk_analysis/emulstick_decompiled/.../hid/ReportInfo.java (SingleKeyboard)
     *
     * @param modifiers 修飾鍵 bit mask
     * @param keys 按鍵 Usage ID 陣列（最多 6 個）
     * @return 8 bytes HID 報告（無 Report ID）
     */
    fun buildKeyboardReport(
        modifiers: Int = 0,
        vararg keys: Int
    ): ByteArray {
        val report = ByteArray(8)
        report[0] = modifiers.toByte()
        report[1] = 0  // 保留

        // 填入按鍵代碼（最多 6 個）
        keys.take(6).forEachIndexed { index, key ->
            report[2 + index] = key.toByte()
        }

        return report
    }

    /**
     * 建構空白鍵盤報告（釋放所有按鍵，EmulStick SingleKeyboard 格式）
     */
    fun buildEmptyKeyboardReport(): ByteArray {
        return ByteArray(8)  // 8 bytes 全為 0
    }

    // ============ 常用鍵盤 Usage ID ============

    object KeyboardUsage {
        const val KEY_A = 0x04
        const val KEY_B = 0x05
        const val KEY_C = 0x06
        const val KEY_D = 0x07
        const val KEY_E = 0x08
        const val KEY_F = 0x09
        const val KEY_G = 0x0A
        const val KEY_H = 0x0B
        const val KEY_I = 0x0C
        const val KEY_J = 0x0D
        const val KEY_K = 0x0E
        const val KEY_L = 0x0F
        const val KEY_M = 0x10
        const val KEY_N = 0x11
        const val KEY_O = 0x12
        const val KEY_P = 0x13
        const val KEY_Q = 0x14
        const val KEY_R = 0x15
        const val KEY_S = 0x16
        const val KEY_T = 0x17
        const val KEY_U = 0x18
        const val KEY_V = 0x19
        const val KEY_W = 0x1A
        const val KEY_X = 0x1B
        const val KEY_Y = 0x1C
        const val KEY_Z = 0x1D

        const val KEY_1 = 0x1E
        const val KEY_2 = 0x1F
        const val KEY_3 = 0x20
        const val KEY_4 = 0x21
        const val KEY_5 = 0x22
        const val KEY_6 = 0x23
        const val KEY_7 = 0x24
        const val KEY_8 = 0x25
        const val KEY_9 = 0x26
        const val KEY_0 = 0x27

        const val KEY_ENTER = 0x28
        const val KEY_ESC = 0x29
        const val KEY_BACKSPACE = 0x2A
        const val KEY_TAB = 0x2B
        const val KEY_SPACE = 0x2C
        const val KEY_MINUS = 0x2D
        const val KEY_EQUAL = 0x2E

        const val KEY_LEFT_BRACKET = 0x2F
        const val KEY_RIGHT_BRACKET = 0x30
        const val KEY_BACKSLASH = 0x31
        const val KEY_SEMICOLON = 0x33
        const val KEY_APOSTROPHE = 0x34
        const val KEY_GRAVE = 0x35
        const val KEY_COMMA = 0x36
        const val KEY_PERIOD = 0x37
        const val KEY_SLASH = 0x38

        const val KEY_CAPS_LOCK = 0x39

        const val KEY_F1 = 0x3A
        const val KEY_F2 = 0x3B
        const val KEY_F3 = 0x3C
        const val KEY_F4 = 0x3D
        const val KEY_F5 = 0x3E
        const val KEY_F6 = 0x3F
        const val KEY_F7 = 0x40
        const val KEY_F8 = 0x41
        const val KEY_F9 = 0x42
        const val KEY_F10 = 0x43
        const val KEY_F11 = 0x44
        const val KEY_F12 = 0x45

        const val KEY_PRINT_SCREEN = 0x46
        const val KEY_SCROLL_LOCK = 0x47
        const val KEY_PAUSE = 0x48
        const val KEY_INSERT = 0x49
        const val KEY_HOME = 0x4A
        const val KEY_PAGE_UP = 0x4B
        const val KEY_DELETE = 0x4C
        const val KEY_END = 0x4D
        const val KEY_PAGE_DOWN = 0x4E

        const val KEY_RIGHT_ARROW = 0x4F
        const val KEY_LEFT_ARROW = 0x50
        const val KEY_DOWN_ARROW = 0x51
        const val KEY_UP_ARROW = 0x52

        const val KEY_NUM_LOCK = 0x53

        // 數字鍵台（Keypad）
        const val KEYPAD_SLASH = 0x54   // 數字鍵台 /
        const val KEYPAD_ASTERISK = 0x55  // 數字鍵台 *
        const val KEYPAD_MINUS = 0x56   // 數字鍵台 -
        const val KEYPAD_PLUS = 0x57    // 數字鍵台 +
        const val KEYPAD_ENTER = 0x58   // 數字鍵台 Enter
        const val KEYPAD_1 = 0x59       // 數字鍵台 1
        const val KEYPAD_2 = 0x5A       // 數字鍵台 2
        const val KEYPAD_3 = 0x5B       // 數字鍵台 3
        const val KEYPAD_4 = 0x5C       // 數字鍵台 4
        const val KEYPAD_5 = 0x5D       // 數字鍵台 5
        const val KEYPAD_6 = 0x5E       // 數字鍵台 6
        const val KEYPAD_7 = 0x5F       // 數字鍵台 7
        const val KEYPAD_8 = 0x60       // 數字鍵台 8
        const val KEYPAD_9 = 0x61       // 數字鍵台 9
        const val KEYPAD_0 = 0x62       // 數字鍵台 0
        const val KEYPAD_DOT = 0x63     // 數字鍵台 .

        // 修飾鍵 Usage ID（用於偵測）
        const val KEY_LEFT_CTRL = 0xE0
        const val KEY_LEFT_SHIFT = 0xE1
        const val KEY_LEFT_ALT = 0xE2
        const val KEY_LEFT_GUI = 0xE3
        const val KEY_RIGHT_CTRL = 0xE4
        const val KEY_RIGHT_SHIFT = 0xE5
        const val KEY_RIGHT_ALT = 0xE6
        const val KEY_RIGHT_GUI = 0xE7
    }
}
