package com.unifiedremote.evo.network.ble

import android.util.Log
import kotlinx.coroutines.delay

/**
 * BLE 混合鍵盤控制器（Alt+X Unicode + HID 混合模式）
 *
 * 智慧切換輸入模式：
 * - ASCII 字元（英文、數字、符號）→ HID 鍵盤報告（快速 ~20ms/字元）
 * - 中文字元 → Alt+X Unicode 模式（~170ms/字元，穩定可靠）
 * - 特殊字元（換行、Tab）→ HID 功能鍵
 *
 * 優點：
 * - 英文輸入快速（~20ms/字元）
 * - 中文輸入穩定（~170ms/字元，支援所有 Unicode）
 * - 比 Big5 Alt 碼快 3.5 倍（Big5 ~600ms/字元，且已失效）
 * - 無需 Big5 編碼轉換，直接使用 Unicode
 *
 * 技術變更（2025-10-12）：
 * - ❌ 舊：Big5 Alt 碼（Alt+42148 → 哈）- Windows 10 不支援
 * - ✅ 新：Alt+X Unicode（54C8 + Alt+X → 哈）- 穩定可靠
 */
class BleKeyboardControllerHybrid(
    private val bleManager: BleManager
) {
    companion object {
        private const val TAG = "BleKeyboardHybrid"

        // 延遲時間常數（參考原廠 EmulStick APK: BluetoothLeService.bleAltDelay = 12ms）
        private const val HID_KEY_DELAY = 12L        // HID 按鍵間隔（ms）- 原廠設定
        private const val ALT_CODE_DELAY = 12L       // Alt 碼數字間隔（ms）- 原廠設定
        private const val ALT_RELEASE_DELAY = 12L    // 放開 Alt 後等待（ms）- 原廠設定
    }

    /**
     * 傳送文字（混合模式：ASCII 走 HID，中文走 Alt+X Unicode）
     *
     * @param text 要傳送的文字（中英文混合）
     */
    suspend fun sendTextHybrid(text: String) {
        if (text.isEmpty()) {
            Log.d(TAG, "文字為空，略過傳送")
            return
        }

        Log.d(TAG, "開始傳送混合文字（Alt+X 模式）：\"$text\"（共 ${text.length} 字元）")
        var asciiCount = 0
        var unicodeCount = 0
        var specialCount = 0

        for (char in text) {
            when (CharClassifier.classifyChar(char)) {
                CharType.NewLine -> {
                    sendEnter()
                    specialCount++
                }
                CharType.Tab -> {
                    sendTab()
                    specialCount++
                }
                CharType.ASCII -> {
                    sendAsciiChar(char)
                    asciiCount++
                }
                CharType.CJK, CharType.Unicode -> {
                    // 🔄 改用 Alt+X Unicode 模式（不再使用 Big5 Alt 碼）
                    sendUnicodeChar(char)
                    unicodeCount++
                }
            }
        }

        Log.d(TAG, "✅ 混合文字傳送完成：ASCII=$asciiCount, Unicode=$unicodeCount, 特殊=$specialCount")
    }

    /**
     * 傳送 ASCII 字元（HID 模式）
     *
     * @param char ASCII 字元
     */
    private suspend fun sendAsciiChar(char: Char) {
        val mapping = AsciiToHidMapper.mapAsciiToHid(char)
        if (mapping == null) {
            Log.w(TAG, "字元 '$char' 無法對應到 HID，跳過")
            return
        }

        val (modifier, keyCode) = mapping
        bleManager.sendKeyPress(modifier, keyCode)
        delay(HID_KEY_DELAY)
    }

    /**
     * 傳送中文/Unicode 字元（使用 Alt+X Unicode 模式）
     *
     * 技術變更（2025-10-12）：
     * - ❌ 舊：Big5 Alt 碼（Alt+42148）- Windows 10 不支援
     * - ❌ 舊：Unicode Alt 碼（Alt + + 4F60）- RDP 環境不穩定
     * - ✅ 新：Alt+X Unicode（54C8 + Alt+X）- 穩定可靠
     *
     * 效能：約 170ms/字元（比 Big5 Alt 碼快 3.5 倍）
     *
     * 優點：
     * - 支援所有 Unicode BMP 字元（U+0000 - U+FFFF）
     * - 無需 Big5 編碼轉換
     * - Windows 10/11 原生支援
     * - RDP 環境穩定
     *
     * @param char 中文/Unicode 字元
     */
    private suspend fun sendUnicodeChar(char: Char) {
        // 🔄 直接使用 Alt+X Unicode 模式（不再使用 Big5 編碼）
        Log.d(TAG, "傳送字元 '$char' (Alt+X Unicode)")
        bleManager.sendCharWithAltX(char)
    }

    /**
     * 傳送 Enter 鍵
     */
    private suspend fun sendEnter() {
        bleManager.sendKeyPress(keys = intArrayOf(HidReportBuilder.KeyboardUsage.KEY_ENTER))
        delay(HID_KEY_DELAY)
    }

    /**
     * 傳送 Tab 鍵
     */
    private suspend fun sendTab() {
        bleManager.sendKeyPress(keys = intArrayOf(HidReportBuilder.KeyboardUsage.KEY_TAB))
        delay(HID_KEY_DELAY)
    }
}
