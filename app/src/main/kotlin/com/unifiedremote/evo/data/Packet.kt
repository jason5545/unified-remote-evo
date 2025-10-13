package com.unifiedremote.evo.data

/**
 * Unified Remote 封包結構
 */
data class Packet(
    var action: Byte? = null,
    var request: Byte? = null,
    var response: Byte? = null,
    var keepAlive: Boolean? = null,
    var run: Action? = null,
    var session: String? = null,
    var source: String? = null,
    var destination: String? = null,
    var version: Int? = null,
    var password: String? = null,
    var platform: String? = null,
    var security: Byte? = null,
    var capabilities: Capabilities? = null,
    var id: String? = null,      // Remote ID
    var hash: Int? = null,       // Hash for syncing
    var layout: Layout? = null   // Layout 結構（用於控制指令）
)

/**
 * 指令結構
 */
data class Action(
    var name: String,
    var target: String? = null,
    var extras: Extras? = null
)

/**
 * 參數列表
 */
data class Extras(
    var values: MutableList<Extra> = mutableListOf()
) {
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
data class Extra(
    var key: String,
    var value: String
)

/**
 * 客戶端能力
 */
data class Capabilities(
    var fast: Boolean? = null,
    var clientNonce: Boolean? = null,
    var encryption2: Boolean? = null,
    var sync: Boolean? = null,
    var actions: Boolean? = null,
    var grid: Boolean? = null,
    var loading: Boolean? = null,
    var business: Boolean? = null
)

/**
 * Layout 結構（用於傳送控制指令）
 */
data class Layout(
    var id: String? = null,
    var hash: Int? = null,
    var controls: MutableList<Control>? = null
)

/**
 * Control 結構（包裝 Action）
 */
data class Control(
    var type: Byte? = null,      // Type = 8 表示控制項
    var onAction: Action? = null // 實際執行的 Action
)
