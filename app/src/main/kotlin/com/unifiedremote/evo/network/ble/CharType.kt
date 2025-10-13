package com.unifiedremote.evo.network.ble

/**
 * 字元類型
 *
 * 用於 Big5 混合模式的字元分類
 */
enum class CharType {
    /** 英文、數字、標點符號 → HID 鍵盤報告 */
    ASCII,

    /** 中文字元 → Big5 + Alt 碼 */
    CJK,

    /** 其他 Unicode → Big5 + Alt 碼（如果 Big5 支援） */
    Unicode,

    /** 換行 → HID Enter 鍵 */
    NewLine,

    /** Tab → HID Tab 鍵 */
    Tab
}

/**
 * 字元分類工具
 */
object CharClassifier {
    /**
     * 分類字元
     *
     * @param char 要分類的字元
     * @return 字元類型
     */
    fun classifyChar(char: Char): CharType {
        return when {
            char == '\n' -> CharType.NewLine
            char == '\t' -> CharType.Tab
            char.code <= 127 -> CharType.ASCII        // 0-127: ASCII（英文、數字、符號）
            char.code in 0x4E00..0x9FFF -> CharType.CJK  // CJK 統一表意文字（中日韓）
            else -> CharType.Unicode                  // 其他 Unicode 字元
        }
    }
}
