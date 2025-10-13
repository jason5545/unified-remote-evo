package com.unifiedremote.evo.network

import java.io.DataOutputStream

/**
 * 二進制寫入器
 */
class BinaryWriter(private val output: DataOutputStream) {

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

    fun writeType(type: Int) = output.writeByte(type)

    fun writeString(value: String?) {
        if (value != null) {
            output.write(value.toByteArray(Charsets.UTF_8))
        }
        output.writeByte(0)
    }

    fun writeInt(value: Int) = output.writeInt(value)
    fun writeBoolean(value: Boolean) = output.writeByte(if (value) 1 else 0)
    fun writeByte(value: Byte) = output.writeByte(value.toInt())
    fun writeDouble(value: Double) = output.writeDouble(value)

    fun writeBinary(data: ByteArray) {
        output.writeInt(data.size)
        output.write(data)
    }

    fun writeEnd() = output.writeByte(TYPE_END)

    fun writeField(name: String, value: Any?) {
        when (value) {
            null -> return
            is Int -> {
                writeType(TYPE_INT)
                writeString(name)
                writeInt(value)
            }
            is Boolean -> {
                writeType(TYPE_BOOL)
                writeString(name)
                writeBoolean(value)
            }
            is String -> {
                writeType(TYPE_STRING)
                writeString(name)
                writeString(value)
            }
            is Byte -> {
                writeType(TYPE_BYTE)
                writeString(name)
                writeByte(value)
            }
            is Double -> {
                writeType(TYPE_NUMBER)
                writeString(name)
                writeDouble(value)
            }
            is ByteArray -> {
                writeType(TYPE_BINARY)
                writeString(name)
                writeBinary(value)
            }
            is List<*> -> {
                writeType(TYPE_ARRAY)
                writeString(name)
                writeArray(value)
            }
            else -> {
                writeType(TYPE_NAMED_DICT)
                writeString(name)
                writeDictionary(value)
            }
        }
    }

    fun writeDictionary(obj: Any) = writeEnd()

    fun writeArray(list: List<*>) {
        for (item in list) {
            when (item) {
                is Int -> {
                    writeType(TYPE_INT)
                    writeInt(item)
                }
                is String -> {
                    writeType(TYPE_STRING)
                    writeString(item)
                }
                is Boolean -> {
                    writeType(TYPE_BOOL)
                    writeBoolean(item)
                }
                else -> {
                    if (item != null) {
                        writeType(TYPE_NAMED_DICT)
                        writeDictionary(item)
                    }
                }
            }
        }
        writeEnd()
    }
}
