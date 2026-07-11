package com.unifiedremote.evo.network.ble

internal enum class Esp32TextPath {
    HID_KEYBOARD,
    CH5_UNICODE,
    ENTER,
    TAB
}

/**
 * ESP32-S3 direct-text routing.
 *
 * CH5 carries a Unicode code point to the receiver, but the current receiver
 * firmware emits that value as a Windows Alt+X keyboard sequence. Printable
 * ASCII must therefore stay on the normal HID keyboard path.
 */
internal object Esp32TextRouter {
    fun pathFor(char: Char): Esp32TextPath = when (char) {
        '\n' -> Esp32TextPath.ENTER
        '\t' -> Esp32TextPath.TAB
        else -> if (AsciiToHidMapper.isHidSupported(char)) {
            Esp32TextPath.HID_KEYBOARD
        } else {
            Esp32TextPath.CH5_UNICODE
        }
    }
}
