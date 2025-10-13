package com.unifiedremote.evo.network.ble

import android.util.Log
import java.nio.charset.Charset

/**
 * Big5 編碼轉換工具
 *
 * 將 Unicode 字元轉換為 Big5 十進制碼，用於 Windows Alt + 數字碼輸入
 */
object Big5Encoder {
    private const val TAG = "Big5Encoder"
    private val big5Charset = Charset.forName("Big5")

    /**
     * 將字元轉換為 Big5 十進制碼
     *
     * @param char 中文字元
     * @return Big5 十進制碼，如果不支援則回傳 null
     *
     * 範例：
     * - '你' → 42148 (0xA741)
     * - '好' → 42606 (0xA66E)
     * - '𤼈' → null（Big5 不支援罕用字）
     */
    fun charToBig5Code(char: Char): Int? {
        try {
            // 1. 轉換為 Big5 位元組
            val big5Bytes = char.toString().toByteArray(big5Charset)

            // 2. Big5 中文字元通常是 2 bytes
            if (big5Bytes.size != 2) {
                Log.v(TAG, "字元 '$char' 無法轉換為 Big5（位元組數量：${big5Bytes.size}）")
                return null  // 不支援的字元
            }

            // 3. 轉換為十進制（無符號）
            val high = big5Bytes[0].toInt() and 0xFF
            val low = big5Bytes[1].toInt() and 0xFF
            val decimalCode = high * 256 + low

            Log.v(TAG, "字元 '$char' → Big5: $decimalCode (0x${decimalCode.toString(16).uppercase()})")
            return decimalCode
        } catch (e: Exception) {
            Log.w(TAG, "字元 '$char' 編碼失敗：${e.message}")
            return null  // 編碼失敗
        }
    }

    /**
     * 檢查字元是否可以用 Big5 編碼
     *
     * @param char 要檢查的字元
     * @return true 如果可以用 Big5 編碼
     */
    fun isBig5Supported(char: Char): Boolean {
        return charToBig5Code(char) != null
    }

    /**
     * 批次轉換文字為 Big5 碼
     *
     * @param text 要轉換的文字
     * @return 對應的 Big5 碼列表（不支援的字元會跳過）
     */
    fun textToBig5Codes(text: String): List<Pair<Char, Int>> {
        return text.mapNotNull { char ->
            charToBig5Code(char)?.let { code ->
                Pair(char, code)
            }
        }
    }
}
