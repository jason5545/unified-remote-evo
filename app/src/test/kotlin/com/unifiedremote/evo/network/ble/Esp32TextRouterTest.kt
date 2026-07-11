package com.unifiedremote.evo.network.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Esp32TextRouterTest {
    @Test
    fun `letters reported by the regression stay on HID keyboard path`() {
        for (char in charArrayOf('H', 'E', 'h', 'e')) {
            assertEquals(Esp32TextPath.HID_KEYBOARD, Esp32TextRouter.pathFor(char))
            assertNotNull(AsciiToHidMapper.mapAsciiToHid(char))
        }
    }

    @Test
    fun `all printable ASCII stays on HID keyboard path`() {
        for (codePoint in 0x20..0x7E) {
            val char = codePoint.toChar()
            assertEquals(
                "U+${codePoint.toString(16).uppercase()} must use HID",
                Esp32TextPath.HID_KEYBOARD,
                Esp32TextRouter.pathFor(char)
            )
            assertNotNull(AsciiToHidMapper.mapAsciiToHid(char))
        }
    }

    @Test
    fun `control keys and non ASCII use their dedicated paths`() {
        assertEquals(Esp32TextPath.ENTER, Esp32TextRouter.pathFor('\n'))
        assertEquals(Esp32TextPath.TAB, Esp32TextRouter.pathFor('\t'))
        assertEquals(Esp32TextPath.CH5_UNICODE, Esp32TextRouter.pathFor('測'))
    }
}
