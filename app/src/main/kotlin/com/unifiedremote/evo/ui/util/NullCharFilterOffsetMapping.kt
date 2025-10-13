package com.unifiedremote.evo.ui.util

import androidx.compose.ui.text.input.OffsetMapping

/**
 * 自訂 OffsetMapping：處理空字元（\u0000）過濾的游標位置映射
 *
 * 當 visualTransformation 過濾掉空字元時，原始文字和轉換後文字長度不同，
 * 需要正確映射游標位置，避免 IndexOutOfBoundsException。
 *
 * 範例：
 * - 原始文字："abc\u0000\u0000def" (長度 8)
 * - 轉換後文字："abcdef" (長度 6)
 * - originalToTransformed(3) = 3 (abc|)
 * - transformedToOriginal(3) = 3 (abc|)
 * - originalToTransformed(5) = 3 (abc\u0000\u0000|def → abc|def)
 */
class NullCharFilterOffsetMapping(private val originalText: String) : OffsetMapping {

    // 建立映射表：原始位置 → 可見位置
    private val originalToVisibleMap = buildMap<Int, Int> {
        var visiblePos = 0
        for (i in originalText.indices) {
            put(i, visiblePos)
            if (originalText[i] != '\u0000') {
                visiblePos++
            }
        }
        // 末端位置
        put(originalText.length, visiblePos)
    }

    // 建立映射表：可見位置 → 原始位置
    private val visibleToOriginalMap = buildMap<Int, Int> {
        var visiblePos = 0
        for (i in originalText.indices) {
            put(visiblePos, i)
            if (originalText[i] != '\u0000') {
                visiblePos++
            }
        }
        // 末端位置
        put(visiblePos, originalText.length)
    }

    /**
     * 將原始文字的游標位置轉換為可見文字的游標位置
     */
    override fun originalToTransformed(offset: Int): Int {
        // 安全處理：確保 offset 在合法範圍內
        val safeOffset = offset.coerceIn(0, originalText.length)
        return originalToVisibleMap[safeOffset] ?: 0
    }

    /**
     * 將可見文字的游標位置轉換為原始文字的游標位置
     */
    override fun transformedToOriginal(offset: Int): Int {
        // 計算可見文字的長度
        val visibleLength = originalText.count { it != '\u0000' }
        // 安全處理：確保 offset 在合法範圍內
        val safeOffset = offset.coerceIn(0, visibleLength)
        return visibleToOriginalMap[safeOffset] ?: originalText.length
    }
}
