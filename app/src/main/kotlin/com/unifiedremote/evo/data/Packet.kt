package com.unifiedremote.evo.data

import com.unifiedremote.evo.network.serialization.BinarySerializable
import com.unifiedremote.evo.network.serialization.BinaryField
import com.unifiedremote.evo.network.serialization.BinaryIgnore

/**
 * Unified Remote 封包結構
 */
@BinarySerializable
data class Packet(
    @BinaryField("Action", 1) var action: Byte? = null,
    @BinaryField("Request", 2) var request: Byte? = null,
    @BinaryField("Response", 3) var response: Byte? = null,
    @BinaryField("KeepAlive", 4) var keepAlive: Boolean? = null,
    @BinaryField("Session", 5) var session: String? = null,
    @BinaryField("Source", 6) var source: String? = null,
    @BinaryField("Destination", 7) var destination: String? = null,
    @BinaryField("Version", 8) var version: Int? = null,
    @BinaryField("Password", 9) var password: String? = null,
    @BinaryField("Platform", 10) var platform: String? = null,
    @BinaryField("Security", 11) var security: Byte? = null,
    @BinaryField("ID", 12) var id: String? = null,      // Remote ID
    @BinaryField("Hash", 13) var hash: Int? = null,     // Hash for syncing
    @BinaryField("Capabilities", 14) var capabilities: Capabilities? = null,
    @BinaryField("Run", 15) var run: Action? = null,
    @BinaryField("Layout", 16) var layout: Layout? = null   // Layout 結構（用於控制指令）
)

/**
 * 指令結構
 */
@BinarySerializable
data class Action(
    @BinaryField("Name", 1) var name: String,
    @BinaryField("Target", 2) var target: String? = null,
    @BinaryField("Extras", 3) var extras: Extras? = null
)

/**
 * 參數列表
 */
@BinarySerializable
data class Extras(
    @BinaryField("Values", 1) var values: MutableList<Extra> = mutableListOf()
) {
    // 輔助函式（不需要 @BinaryIgnore，只有屬性會被序列化）
    fun add(key: String, value: String) {
        values.add(Extra(key, value))
    }

    fun add(key: String, value: Int) {
        values.add(Extra(key, value.toString()))
    }

    fun add(key: String, value: Boolean) {
        values.add(Extra(key, value.toString()))
    }
}

/**
 * 參數
 */
@BinarySerializable
data class Extra(
    @BinaryField("Key", 1) var key: String,
    @BinaryField("Value", 2) var value: String
)

/**
 * 客戶端能力
 */
@BinarySerializable
data class Capabilities(
    @BinaryField("Fast", 1) var fast: Boolean? = null,
    @BinaryField("ClientNonce", 2) var clientNonce: Boolean? = null,
    @BinaryField("Encryption2", 3) var encryption2: Boolean? = null,
    @BinaryField("Sync", 4) var sync: Boolean? = null,
    @BinaryField("Actions", 5) var actions: Boolean? = null,
    @BinaryField("Grid", 6) var grid: Boolean? = null,
    @BinaryField("Loading", 7) var loading: Boolean? = null,
    @BinaryField("Business", 8) var business: Boolean? = null
)

/**
 * Layout 結構（用於傳送控制指令）
 */
@BinarySerializable
data class Layout(
    @BinaryField("ID", 1) var id: String? = null,
    @BinaryField("Hash", 2) var hash: Int? = null,
    @BinaryField("Controls", 3) var controls: MutableList<Control>? = null
)

/**
 * Control 結構（包裝 Action）
 */
@BinarySerializable
data class Control(
    @BinaryField("Type", 1) var type: Byte? = null,      // Type = 8 表示控制項
    @BinaryField("OnAction", 2) var onAction: Action? = null // 實際執行的 Action
)
