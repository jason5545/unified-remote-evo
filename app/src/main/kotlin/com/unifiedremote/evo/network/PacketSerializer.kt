package com.unifiedremote.evo.network

import com.unifiedremote.evo.data.Packet
import com.unifiedremote.evo.network.serialization.BinarySerializer

/**
 * Packet 序列化器（相容層）
 *
 * 內部使用註解驅動的 BinarySerializer 引擎
 *
 * 保留此相容層的好處:
 * 1. 現有程式碼無需修改（ConnectionManager, BluetoothConnectionManager）
 * 2. 漸進式遷移（先驗證新引擎，再逐步移除舊程式碼）
 * 3. 回滾容易（如果出問題，可以快速恢復）
 *
 * 使用方式:
 * ```kotlin
 * val data = PacketSerializer.serialize(packet)
 * val packet = PacketSerializer.deserialize(data)
 * ```
 */
object PacketSerializer {

    /**
     * 序列化 Packet 為二進制格式
     *
     * @param packet 要序列化的封包
     * @return 二進制資料
     */
    fun serialize(packet: Packet): ByteArray {
        return BinarySerializer.serialize(packet)
    }

    /**
     * 反序列化二進制資料為 Packet
     *
     * @param data 二進制資料
     * @return 反序列化的封包，失敗時回傳 null
     */
    fun deserialize(data: ByteArray): Packet? {
        return BinarySerializer.deserialize(data)
    }
}
