package com.unifiedremote.evo.network

import java.io.DataInputStream

/**
 * 二進制讀取器
 */
class BinaryReader(private val input: DataInputStream) {

    companion object {
        const val TYPE_ROOT_DICT = 1
        const val TYPE_NAMED_DICT = 2
        const val TYPE_INT = 3
        const val TYPE_BOOL = 4
        const val TYPE_STRING = 5
        const val TYPE_ARRAY = 6
        const val TYPE_BINARY = 7
        const val TYPE_BYTE = 8
        const val TYPE_NUMBER = 9
        const val TYPE_END = 0
    }

    fun readType(): Int = input.readByte().toInt()

    fun readString(): String {
        val bytes = mutableListOf<Byte>()
        var byte: Byte
        while (true) {
            byte = input.readByte()
            if (byte == 0.toByte()) break
            bytes.add(byte)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    fun readInt(): Int = input.readInt()
    fun readBoolean(): Boolean = input.readByte() != 0.toByte()
    fun readByte(): Byte = input.readByte()
    fun readDouble(): Double = input.readDouble()

    fun readBinary(): ByteArray {
        val length = input.readInt()
        val data = ByteArray(length)
        input.readFully(data)
        return data
    }

    fun readDictionary(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        while (true) {
            val type = readType()
            if (type == TYPE_END) break
            val key = readString()
            val value = readValue(type)
            map[key] = value
        }
        return map
    }

    fun readArray(): List<Any?> {
        val list = mutableListOf<Any?>()
        while (true) {
            val type = readType()
            if (type == TYPE_END) break
            val value = readValue(type)
            list.add(value)
        }
        return list
    }

    private fun readValue(type: Int): Any? = when (type) {
        TYPE_ROOT_DICT, TYPE_NAMED_DICT -> readDictionary()
        TYPE_INT -> readInt()
        TYPE_BOOL -> readBoolean()
        TYPE_STRING -> readString()
        TYPE_ARRAY -> readArray()
        TYPE_BINARY -> readBinary()
        TYPE_BYTE -> readByte()
        TYPE_NUMBER -> readDouble()
        else -> null
    }
}
