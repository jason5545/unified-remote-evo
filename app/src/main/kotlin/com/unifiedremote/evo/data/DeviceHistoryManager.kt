package com.unifiedremote.evo.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 裝置歷史管理器
 * 管理連線過的裝置列表
 */
class DeviceHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "device_history"
        private const val KEY_DEVICES = "devices"
        private const val KEY_LAST_DEVICE_ID = "last_device_id"
        private const val MAX_DEVICES = 10  // 最多儲存 10 個裝置
    }

    /**
     * 取得所有儲存的裝置（按最後連線時間排序）
     */
    fun getAllDevices(): List<SavedDevice> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<SavedDevice>>() {}.type
            val devices: List<SavedDevice> = gson.fromJson(json, type)

            // 按最後連線時間排序（最新的在前）
            devices.sortedByDescending { it.lastConnected }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 取得最後連線的裝置
     */
    fun getLastDevice(): SavedDevice? {
        val lastDeviceId = prefs.getString(KEY_LAST_DEVICE_ID, null) ?: return null
        return getAllDevices().find { it.id == lastDeviceId }
    }

    /**
     * 儲存裝置（如果已存在則更新連線時間）
     */
    fun saveDevice(device: SavedDevice) {
        val devices = getAllDevices().toMutableList()

        // 移除舊的相同裝置（如果存在）
        devices.removeAll { it.id == device.id }

        // 加入新裝置（更新時間）
        val updatedDevice = device.copy(lastConnected = System.currentTimeMillis())
        devices.add(0, updatedDevice)  // 加到最前面

        // 限制最大數量
        val trimmedDevices = devices.take(MAX_DEVICES)

        // 儲存
        val json = gson.toJson(trimmedDevices)
        prefs.edit()
            .putString(KEY_DEVICES, json)
            .putString(KEY_LAST_DEVICE_ID, updatedDevice.id)
            .apply()
    }

    /**
     * 刪除裝置
     */
    fun removeDevice(deviceId: String) {
        val devices = getAllDevices().toMutableList()
        devices.removeAll { it.id == deviceId }

        val json = gson.toJson(devices)
        val editor = prefs.edit().putString(KEY_DEVICES, json)

        // 如果刪除的是最後連線的裝置，清除記錄
        if (prefs.getString(KEY_LAST_DEVICE_ID, null) == deviceId) {
            editor.remove(KEY_LAST_DEVICE_ID)
        }

        editor.apply()
    }

    /**
     * 清除所有裝置
     */
    fun clearAll() {
        prefs.edit()
            .remove(KEY_DEVICES)
            .remove(KEY_LAST_DEVICE_ID)
            .apply()
    }

    /**
     * 取得裝置數量
     */
    fun getDeviceCount(): Int {
        return getAllDevices().size
    }

    /**
     * 檢查裝置是否已儲存
     */
    fun isDeviceSaved(deviceId: String): Boolean {
        return getAllDevices().any { it.id == deviceId }
    }
}
