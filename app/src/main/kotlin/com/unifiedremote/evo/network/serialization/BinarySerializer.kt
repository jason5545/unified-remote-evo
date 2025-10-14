package com.unifiedremote.evo.network.serialization

import com.unifiedremote.evo.data.*
import com.unifiedremote.evo.network.BinaryReader
import com.unifiedremote.evo.network.BinaryWriter
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * 註解驅動的二進制序列化引擎
 *
 * 使用 Kotlin 反射 + 自訂註解，自動處理物件的序列化/反序列化
 *
 * 特點:
 * - 100% 相容 Unified Remote 原廠二進制格式
 * - 自動處理巢狀物件和陣列
 * - 反射結果快取（效能最佳化）
 * - 除錯模式（可追蹤序列化過程）
 *
 * 使用方式:
 * ```kotlin
 * val data = BinarySerializer.serialize(packet)
 * val packet = BinarySerializer.deserialize<Packet>(data)
 * ```
 */
object BinarySerializer {

    /**
     * 除錯模式（預設關閉）
     * 設為 true 可在 logcat 看到詳細的序列化過程
     */
    var debugMode = false

    /**
     * 反射快取（避免重複反射，提升效能）
     * Key: KClass, Value: 該類別的欄位資訊列表（已排序）
     */
    private val fieldCache = mutableMapOf<KClass<*>, List<FieldInfo>>()

    /**
     * 欄位資訊（包含屬性與註解）
     */
    data class FieldInfo(
        val property: KProperty1<Any, Any?>,
        val annotation: BinaryField
    )

    // ===== 序列化 =====

    /**
     * 序列化物件為二進制格式
     *
     * @param obj 要序列化的物件（必須有 @BinarySerializable 註解）
     * @return 二進制資料
     */
    fun <T : Any> serialize(obj: T): ByteArray {
        val baos = ByteArrayOutputStream()
        val writer = BinaryWriter(DataOutputStream(baos))

        if (debugMode) println("========== 開始序列化 ${obj::class.simpleName} ==========")

        writer.writeType(BinaryWriter.TYPE_ROOT_DICT)
        serializeObject(writer, obj, obj::class)
        writer.writeEnd()

        if (debugMode) println("========== 序列化完成（${baos.size()} bytes）==========\n")

        return baos.toByteArray()
    }

    /**
     * 序列化物件（遞迴處理巢狀結構）
     *
     * @param writer 二進制寫入器
     * @param obj 要序列化的物件
     * @param kClass 物件的類別資訊
     */
    private fun serializeObject(writer: BinaryWriter, obj: Any, kClass: KClass<*>) {
        val fields = getFieldsInfo(kClass)

        // 按 order 排序（確保順序穩定）
        fields.sortedBy { it.annotation.order }.forEach { fieldInfo ->
            val value = fieldInfo.property.get(obj)

            // 跳過 null 值（與原廠行為一致）
            if (value != null) {
                writeField(writer, fieldInfo.annotation.name, value)
            }
        }
    }

    /**
     * 寫入欄位（根據型別自動選擇寫入方式）
     *
     * @param writer 二進制寫入器
     * @param name 欄位名稱
     * @param value 欄位值
     */
    private fun writeField(writer: BinaryWriter, name: String, value: Any) {
        if (debugMode) {
            println("  寫入欄位: $name = $value (${value::class.simpleName})")
        }

        when (value) {
            // 基本型別
            is Int -> {
                writer.writeType(BinaryWriter.TYPE_INT)
                writer.writeString(name)
                writer.writeInt(value)
            }
            is Boolean -> {
                writer.writeType(BinaryWriter.TYPE_BOOL)
                writer.writeString(name)
                writer.writeBoolean(value)
            }
            is String -> {
                writer.writeType(BinaryWriter.TYPE_STRING)
                writer.writeString(name)
                writer.writeString(value)
            }
            is Byte -> {
                writer.writeType(BinaryWriter.TYPE_BYTE)
                writer.writeString(name)
                writer.writeByte(value)
            }
            is Double -> {
                writer.writeType(BinaryWriter.TYPE_NUMBER)
                writer.writeString(name)
                writer.writeDouble(value)
            }
            is ByteArray -> {
                writer.writeType(BinaryWriter.TYPE_BINARY)
                writer.writeString(name)
                writer.writeBinary(value)
            }

            // 陣列
            is List<*> -> {
                writer.writeType(BinaryWriter.TYPE_ARRAY)
                writer.writeString(name)
                serializeArray(writer, value)
            }

            // 巢狀物件
            else -> {
                if (value::class.hasAnnotation<BinarySerializable>()) {
                    writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
                    writer.writeString(name)
                    serializeObject(writer, value, value::class)
                    writer.writeEnd()
                } else {
                    if (debugMode) {
                        println("  警告: ${value::class.simpleName} 沒有 @BinarySerializable 註解，跳過")
                    }
                }
            }
        }
    }

    /**
     * 序列化陣列
     *
     * @param writer 二進制寫入器
     * @param list 陣列
     */
    private fun serializeArray(writer: BinaryWriter, list: List<*>) {
        if (debugMode) println("    序列化陣列（${list.size} 項）")

        for (item in list) {
            if (item != null) {
                when (item) {
                    // 基本型別陣列項目
                    is Int -> {
                        writer.writeType(BinaryWriter.TYPE_INT)
                        writer.writeInt(item)
                    }
                    is String -> {
                        writer.writeType(BinaryWriter.TYPE_STRING)
                        writer.writeString(item)
                    }
                    is Boolean -> {
                        writer.writeType(BinaryWriter.TYPE_BOOL)
                        writer.writeBoolean(item)
                    }

                    // 物件陣列項目（如 Extra, Control）
                    else -> {
                        if (item::class.hasAnnotation<BinarySerializable>()) {
                            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
                            writer.writeString(null)  // ⚠️ 陣列項目沒有名稱
                            serializeObject(writer, item, item::class)
                            writer.writeEnd()
                        }
                    }
                }
            }
        }
        writer.writeEnd()
    }

    // ===== 反序列化 =====

    /**
     * 反序列化二進制資料為物件
     *
     * @param data 二進制資料
     * @param kClass 目標類別
     * @return 反序列化的物件，失敗時回傳 null
     */
    fun <T : Any> deserialize(data: ByteArray, kClass: KClass<T>): T? {
        return try {
            if (debugMode) println("========== 開始反序列化 ${kClass.simpleName} ==========")

            val reader = BinaryReader(DataInputStream(ByteArrayInputStream(data)))
            val type = reader.readType()
            if (type != BinaryReader.TYPE_ROOT_DICT) {
                if (debugMode) println("錯誤: 根型別不是 ROOT_DICT")
                return null
            }

            val map = reader.readDictionary()
            val result = deserializeObject(map, kClass)

            if (debugMode) println("========== 反序列化完成 ==========\n")

            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 反序列化二進制資料為物件（inline 版本，用於型別推斷）
     *
     * @param data 二進制資料
     * @return 反序列化的物件，失敗時回傳 null
     */
    inline fun <reified T : Any> deserialize(data: ByteArray): T? {
        return deserialize(data, T::class)
    }

    /**
     * 反序列化物件（從 Map 建構物件）
     *
     * @param map 欄位名稱 → 值的對應
     * @param kClass 目標類別
     * @return 建構的物件
     */
    private fun <T : Any> deserializeObject(map: Map<String, Any?>, kClass: KClass<T>): T {
        val constructor = kClass.primaryConstructor
            ?: throw IllegalArgumentException("${kClass.simpleName} 沒有主要建構子")

        val fields = getFieldsInfo(kClass)
        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { param ->
            val fieldInfo = fields.find { it.property.name == param.name }
            if (fieldInfo != null) {
                val wireValue = map[fieldInfo.annotation.name]
                val convertedValue = convertValue(wireValue, param.type)

                if (debugMode && wireValue != null) {
                    println("  還原欄位: ${param.name} = $convertedValue")
                }

                args[param] = convertedValue
            }
        }

        return constructor.callBy(args)
    }

    /**
     * 型別轉換（Map/List → 物件）
     *
     * @param value 原始值（來自 BinaryReader）
     * @param targetType 目標型別
     * @return 轉換後的值
     */
    private fun convertValue(value: Any?, targetType: KType): Any? {
        if (value == null) return null

        val classifier = targetType.classifier as? KClass<*> ?: return value

        return when {
            // 巢狀物件型別判斷
            classifier == Action::class && value is Map<*, *> ->
                deserializeObject(value as Map<String, Any?>, Action::class)

            classifier == Extras::class && value is Map<*, *> ->
                deserializeObject(value as Map<String, Any?>, Extras::class)

            classifier == Layout::class && value is Map<*, *> ->
                deserializeObject(value as Map<String, Any?>, Layout::class)

            classifier == Control::class && value is Map<*, *> ->
                deserializeObject(value as Map<String, Any?>, Control::class)

            classifier == Capabilities::class && value is Map<*, *> ->
                deserializeObject(value as Map<String, Any?>, Capabilities::class)

            // 陣列型別判斷（List 或 MutableList）
            (classifier == List::class || classifier == MutableList::class) && value is List<*> -> {
                val itemType = targetType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                when (itemType) {
                    Extra::class -> value.filterIsInstance<Map<String, Any?>>()
                        .map { deserializeObject(it, Extra::class) }
                        .toMutableList()

                    Control::class -> value.filterIsInstance<Map<String, Any?>>()
                        .map { deserializeObject(it, Control::class) }
                        .toMutableList()

                    else -> value
                }
            }

            else -> value
        }
    }

    // ===== 反射快取 =====

    /**
     * 取得類別的欄位資訊（有快取）
     *
     * @param kClass 類別
     * @return 欄位資訊列表
     */
    private fun getFieldsInfo(kClass: KClass<*>): List<FieldInfo> {
        return fieldCache.getOrPut(kClass) {
            kClass.memberProperties
                .mapNotNull { prop ->
                    val annotation = prop.findAnnotation<BinaryField>()
                    if (annotation != null && !prop.hasAnnotation<BinaryIgnore>()) {
                        @Suppress("UNCHECKED_CAST")
                        FieldInfo(prop as KProperty1<Any, Any?>, annotation)
                    } else {
                        null
                    }
                }
        }
    }
}
