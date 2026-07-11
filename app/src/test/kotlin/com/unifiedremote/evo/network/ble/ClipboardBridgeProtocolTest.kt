package com.unifiedremote.evo.network.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class ClipboardBridgeProtocolTest {
    @Test
    fun `frames preserve whole UTF-8 message with length crc and sequence`() {
        val text = "Hello 中文 Evo ✅"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val frames = ClipboardBridgeProtocol.frames(text, messageId = 42)

        assertEquals(12, frames.first().size)
        assertEquals(ClipboardBridgeProtocol.BLE_MAGIC, frames.first()[0].toInt() and 0xFF)
        assertEquals(ClipboardBridgeProtocol.TYPE_START, frames.first()[2].toInt() and 0xFF)
        assertEquals(42, u16(frames.first(), 3))
        assertEquals(bytes.size, u16(frames.first(), 5))
        assertEquals(CRC32().apply { update(bytes) }.value, u32(frames.first(), 7))

        val dataFrames = frames.drop(1).dropLast(1)
        val rebuilt = dataFrames.flatMap { frame ->
            val count = frame[7].toInt() and 0xFF
            frame.copyOfRange(8, 8 + count).asIterable()
        }.toByteArray()
        assertArrayEquals(bytes, rebuilt)
        dataFrames.forEachIndexed { index, frame -> assertEquals(index, u16(frame, 5)) }
        assertEquals(dataFrames.size, u16(frames.last(), 5))
    }

    @Test
    fun `fallback excludes ambiguous timeout and completed paste`() {
        assertTrue(ClipboardBridgeProtocol.allowsSafeFallback(ClipboardBridgeProtocol.STATUS_HELPER_UNAVAILABLE))
        assertTrue(ClipboardBridgeProtocol.allowsSafeFallback(ClipboardBridgeProtocol.STATUS_USB_ERROR))
        assertFalse(ClipboardBridgeProtocol.allowsSafeFallback(0x87))
        assertFalse(ClipboardBridgeProtocol.allowsSafeFallback(ClipboardBridgeProtocol.STATUS_PASTED))
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}
