package com.unifiedremote.evo.network

import kotlinx.coroutines.flow.StateFlow

/**
 * 遙控器介面（統一 TCP 與 BLE 模式）
 */
interface RemoteController {

    /** 連線狀態 */
    val connectionState: StateFlow<ConnectionState>

    /** 連線到遠端裝置 */
    suspend fun connect()

    /** 中斷連線 */
    fun disconnect()

    /** 檢查是否已連線 */
    fun isConnected(): Boolean

    // ============ 滑鼠控制 ============

    /** 移動滑鼠 */
    suspend fun moveMouse(deltaX: Int, deltaY: Int)

    /** 點擊滑鼠按鍵 */
    suspend fun clickMouse(button: MouseButton)

    /** 滾動滑鼠滾輪 */
    suspend fun scrollMouse(deltaY: Int)

    /** 水平滾動 */
    suspend fun scrollMouseHorizontal(deltaX: Int)

    // ============ 鍵盤控制 ============

    /** 按下按鍵 */
    suspend fun pressKey(key: String, modifier: String? = null)

    /** 輸入文字 */
    suspend fun typeText(text: String)

    /** 釋放資源 */
    fun release()
}

/**
 * 連線狀態
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val target: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 滑鼠按鍵
 */
enum class MouseButton {
    LEFT,
    RIGHT,
    MIDDLE
}
