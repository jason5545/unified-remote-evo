package com.unifiedremote.evo.network.ble

/**
 * ASCII 字元到 HID Usage ID 對應工具
 *
 * 用於將 ASCII 字元轉換為 HID 鍵盤報告格式
 */
object AsciiToHidMapper {

    /**
     * 將 ASCII 字元轉換為 HID Usage ID
     *
     * @param char ASCII 字元（0-127）
     * @return Pair<modifier, keyCode> 或 null
     *         - modifier: 修飾鍵 bit mask（例如 Shift）
     *         - keyCode: HID Usage ID
     *
     * 範例：
     * - 'a' → Pair(0, 0x04)
     * - 'A' → Pair(HID_LEFT_SHIFT, 0x04)
     * - '1' → Pair(0, 0x1E)
     */
    fun mapAsciiToHid(char: Char): Pair<Int, Int>? {
        return when (char) {
            // 字母（需要 Shift 的大寫）
            in 'A'..'Z' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x04 + (char - 'A'))
            // 字母（小寫）
            in 'a'..'z' -> Pair(0, 0x04 + (char - 'a'))

            // 數字
            in '1'..'9' -> Pair(0, 0x1E + (char - '1'))
            '0' -> Pair(0, 0x27)

            // 空白
            ' ' -> Pair(0, 0x2C)

            // 標點符號（基本）
            '.' -> Pair(0, 0x37)  // Period
            ',' -> Pair(0, 0x36)  // Comma
            '/' -> Pair(0, 0x38)  // Slash
            ';' -> Pair(0, 0x33)  // Semicolon
            '\'' -> Pair(0, 0x34)  // Apostrophe
            '[' -> Pair(0, 0x2F)  // Left Bracket
            ']' -> Pair(0, 0x30)  // Right Bracket
            '\\' -> Pair(0, 0x31)  // Backslash
            '`' -> Pair(0, 0x35)  // Grave
            '-' -> Pair(0, 0x2D)  // Minus
            '=' -> Pair(0, 0x2E)  // Equal

            // 需要 Shift 的符號
            '!' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x1E)  // Shift + 1
            '@' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x1F)  // Shift + 2
            '#' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x20)  // Shift + 3
            '$' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x21)  // Shift + 4
            '%' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x22)  // Shift + 5
            '^' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x23)  // Shift + 6
            '&' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x24)  // Shift + 7
            '*' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x25)  // Shift + 8
            '(' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x26)  // Shift + 9
            ')' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x27)  // Shift + 0
            '_' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x2D)  // Shift + -
            '+' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x2E)  // Shift + =
            '{' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x2F)  // Shift + [
            '}' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x30)  // Shift + ]
            '|' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x31)  // Shift + \
            ':' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x33)  // Shift + ;
            '"' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x34)  // Shift + '
            '<' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x36)  // Shift + ,
            '>' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x37)  // Shift + .
            '?' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x38)  // Shift + /
            '~' -> Pair(HidReportBuilder.MODIFIER_LEFT_SHIFT, 0x35)  // Shift + `

            // 其他字元回傳 null（使用 Big5 處理）
            else -> null
        }
    }

    /**
     * 檢查字元是否可以用 HID 直接發送
     *
     * @param char 要檢查的字元
     * @return true 如果可以用 HID 直接發送
     */
    fun isHidSupported(char: Char): Boolean {
        return mapAsciiToHid(char) != null
    }
}
