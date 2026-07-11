package com.unifiedremote.evo.network.ble

import java.util.zip.CRC32

object ClipboardBridgeProtocol {
    const val VERSION = 1
    const val BLE_MAGIC = 0xCB
    const val MAX_TEXT_BYTES = 4096
    const val BLE_DATA_BYTES = 12

    const val TYPE_START = 1
    const val TYPE_DATA = 2
    const val TYPE_COMMIT = 3

    const val STATUS_QUEUED = 1
    const val STATUS_PASTED = 2
    const val STATUS_HELPER_UNAVAILABLE = 0x81
    const val STATUS_BUSY = 0x82
    const val STATUS_TOO_LARGE = 0x83
    const val STATUS_USB_ERROR = 0x86

    fun frames(text: String, messageId: Int, pasteAfterWrite: Boolean = true): List<ByteArray> {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.isNotEmpty()) { "Clipboard text is empty" }
        require(payload.size <= MAX_TEXT_BYTES) {
            "UTF-8 text is ${payload.size} bytes; maximum is $MAX_TEXT_BYTES"
        }
        require(messageId in 0..0xFFFF) { "Message ID is outside UInt16" }

        val crc = CRC32().apply { update(payload) }.value
        val result = mutableListOf<ByteArray>()
        val start = ByteArray(12)
        start[0] = BLE_MAGIC.toByte()
        start[1] = VERSION.toByte()
        start[2] = TYPE_START.toByte()
        putU16(start, 3, messageId)
        putU16(start, 5, payload.size)
        putU32(start, 7, crc)
        start[11] = if (pasteAfterWrite) 1 else 0
        result += start

        var offset = 0
        var sequence = 0
        while (offset < payload.size) {
            val count = minOf(BLE_DATA_BYTES, payload.size - offset)
            val frame = ByteArray(8 + count)
            frame[0] = BLE_MAGIC.toByte()
            frame[1] = VERSION.toByte()
            frame[2] = TYPE_DATA.toByte()
            putU16(frame, 3, messageId)
            putU16(frame, 5, sequence)
            frame[7] = count.toByte()
            payload.copyInto(frame, destinationOffset = 8, startIndex = offset, endIndex = offset + count)
            result += frame
            offset += count
            sequence++
        }

        val commit = ByteArray(7)
        commit[0] = BLE_MAGIC.toByte()
        commit[1] = VERSION.toByte()
        commit[2] = TYPE_COMMIT.toByte()
        putU16(commit, 3, messageId)
        putU16(commit, 5, sequence)
        result += commit
        return result
    }

    fun allowsSafeFallback(status: Int): Boolean = status in setOf(
        STATUS_HELPER_UNAVAILABLE,
        STATUS_BUSY,
        STATUS_TOO_LARGE,
        STATUS_USB_ERROR
    )

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}

sealed interface ClipboardBridgeAttempt {
    data object Pasted : ClipboardBridgeAttempt
    data class SafeFallback(val reason: String) : ClipboardBridgeAttempt
    data class Failed(val reason: String) : ClipboardBridgeAttempt
}
