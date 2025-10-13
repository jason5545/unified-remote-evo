package com.unifiedremote.evo.network

/**
 * 統一連線狀態定義（TCP / 藍牙 / BLE 共用）
 */
sealed class UnifiedConnectionState {
    /**
     * 已斷線
     */
    data object Disconnected : UnifiedConnectionState()

    /**
     * 連線中
     * @param message 連線訊息（如 "正在連線到 192.168.1.100..."）
     */
    data class Connecting(val message: String = "正在連線...") : UnifiedConnectionState()

    /**
     * 已連線
     * @param deviceInfo 裝置資訊（如伺服器位址、藍牙裝置名稱等）
     */
    data class Connected(val deviceInfo: String) : UnifiedConnectionState()

    /**
     * 重連中
     * @param attempt 目前重連次數
     * @param maxAttempts 最大重連次數
     * @param nextDelayMs 下次重連延遲（毫秒）
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val nextDelayMs: Long
    ) : UnifiedConnectionState()

    /**
     * 錯誤
     * @param message 錯誤訊息
     */
    data class Error(val message: String) : UnifiedConnectionState()
}
