package com.unifiedremote.evo.network.ble

/**
 * 鍵盤 LED 狀態（從 PC 的 Keyboard Output Report 取得）
 *
 * Windows 會透過 HID Output Report 回傳鍵盤 LED 狀態：
 * - Bit 0: NumLock
 * - Bit 1: CapsLock
 * - Bit 2: ScrollLock
 *
 * 參考：USB HID Usage Tables - LED Page (0x08)
 */
data class KeyboardLedState(
    val numLock: Boolean = false,
    val capsLock: Boolean = false,
    val scrollLock: Boolean = false
) {
    companion object {
        /**
         * 從 LED 狀態 byte 解析為 KeyboardLedState
         *
         * @param ledByte LED 狀態 byte（bit mask）
         * @return KeyboardLedState 物件
         */
        fun fromByte(ledByte: Byte): KeyboardLedState {
            return KeyboardLedState(
                numLock = (ledByte.toInt() and 0x01) != 0,
                capsLock = (ledByte.toInt() and 0x02) != 0,
                scrollLock = (ledByte.toInt() and 0x04) != 0
            )
        }
    }

    override fun toString(): String {
        return "LED[NumLock=$numLock, CapsLock=$capsLock, ScrollLock=$scrollLock]"
    }
}
