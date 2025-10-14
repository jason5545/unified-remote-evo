package com.unifiedremote.evo.network.serialization

/**
 * 標記可序列化的類別
 *
 * 使用此註解的類別可以被 BinarySerializer 自動序列化/反序列化
 *
 * 範例:
 * ```kotlin
 * @BinarySerializable
 * data class Packet(
 *     @BinaryField("Action", 1) var action: Byte? = null
 * )
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class BinarySerializable

/**
 * 標記需要序列化的欄位
 *
 * @param name 二進制格式中的欄位名稱（必須與 Unified Remote 原廠協定相同）
 * @param order 序列化順序（確保順序穩定，避免反射順序不確定性）
 *
 * 範例:
 * ```kotlin
 * @BinaryField("Action", 1) var action: Byte? = null
 * @BinaryField("Session", 2) var session: String? = null
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class BinaryField(
    val name: String,
    val order: Int = 0
)

/**
 * 標記忽略的欄位（不序列化）
 *
 * 用於輔助函式或計算屬性
 *
 * 範例:
 * ```kotlin
 * @BinaryIgnore
 * fun add(key: String, value: String) { ... }
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class BinaryIgnore
