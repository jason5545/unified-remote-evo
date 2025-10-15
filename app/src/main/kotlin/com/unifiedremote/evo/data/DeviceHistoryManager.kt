package com.unifiedremote.evo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.ConnectionType
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Maintains the list of saved devices and the last successful connection.
 */
class DeviceHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TAG = "DeviceHistoryManager"
        private const val PREFS_NAME = "device_history"
        private const val KEY_DEVICES = "devices"
        private const val KEY_LAST_DEVICE_ID = "last_device_id"
        private const val MAX_DEVICES = 10
        private const val DEFAULT_TCP_PORT = 9512
    }

    fun getAllDevices(): List<SavedDevice> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedDevice>>() {}.type
            val devices: List<SavedDevice> = gson.fromJson(json, type)
            sanitizeDevices(devices)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse saved devices JSON, trying legacy migration", e)
            val migrated = migrateLegacyDevices(json)
            if (migrated.isNotEmpty()) {
                persistDevices(migrated)
            }
            migrated
        }
    }

    fun getLastDevice(): SavedDevice? {
        val devices = getAllDevices()
        if (devices.isEmpty()) {
            ConnectionLogger.log("DeviceHistory: getLastDevice -> no devices", ConnectionLogger.LogLevel.DEBUG)
            return null
        }

        val storedId = prefs.getString(KEY_LAST_DEVICE_ID, null)
        ConnectionLogger.log(
            "DeviceHistory: storedId=$storedId, candidates=${devices.joinToString { it.id }}",
            ConnectionLogger.LogLevel.DEBUG
        )
        val exactMatch = devices.firstOrNull { it.id == storedId }
        if (exactMatch != null) {
            ConnectionLogger.log(
                "DeviceHistory: using stored match ${exactMatch.id}",
                ConnectionLogger.LogLevel.DEBUG
            )
            return exactMatch
        }

        val fallback = devices.first()
        ConnectionLogger.log(
            "DeviceHistory: fallback to ${fallback.id}",
            ConnectionLogger.LogLevel.DEBUG
        )
        prefs.edit().putString(KEY_LAST_DEVICE_ID, fallback.id).apply()
        return fallback
    }

    fun saveDevice(device: SavedDevice) {
        val now = System.currentTimeMillis()
        val sanitized = sanitizeDevice(device.copy(lastConnected = now)) ?: return

        val updated = mutableListOf<SavedDevice>()
        updated += sanitized
        updated += getAllDevices().filter { it.id != sanitized.id }

        persistDevices(updated, sanitized.id)
    }

    fun removeDevice(deviceId: String) {
        val remaining = getAllDevices().filterNot { it.id == deviceId }
        persistDevices(remaining)
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_DEVICES)
            .remove(KEY_LAST_DEVICE_ID)
            .apply()
    }

    fun getDeviceCount(): Int = getAllDevices().size

    fun isDeviceSaved(deviceId: String): Boolean = getAllDevices().any { it.id == deviceId }

    private fun persistDevices(devices: List<SavedDevice>, preferredLastId: String? = null) {
        val normalized = sanitizeDevices(devices)
        if (normalized.isEmpty()) {
            clearAll()
            return
        }

        val editor = prefs.edit()
            .putString(KEY_DEVICES, gson.toJson(normalized))

        val storedId = preferredLastId ?: prefs.getString(KEY_LAST_DEVICE_ID, null)
        val resolvedLastId = storedId?.takeIf { id -> normalized.any { it.id == id } }
            ?: normalized.first().id

        editor.putString(KEY_LAST_DEVICE_ID, resolvedLastId)
        editor.apply()
        ConnectionLogger.log(
            "DeviceHistory: persistDevices -> saved ${normalized.size} entries, lastId=$resolvedLastId",
            ConnectionLogger.LogLevel.DEBUG
        )
    }

    private fun sanitizeDevices(devices: List<SavedDevice>?): List<SavedDevice> {
        if (devices.isNullOrEmpty()) {
            return emptyList()
        }

        val deduped = LinkedHashMap<String, SavedDevice>()
        for (device in devices) {
            val sanitized = sanitizeDevice(device) ?: continue
            val current = deduped[sanitized.id]
            if (current == null || sanitized.lastConnected >= current.lastConnected) {
                deduped[sanitized.id] = sanitized
            }
        }

        return deduped.values
            .sortedByDescending { it.lastConnected }
            .take(MAX_DEVICES)
    }

    private fun sanitizeDevice(device: SavedDevice): SavedDevice? {
        val safeLastConnected = if (device.lastConnected > 0) device.lastConnected else 0L

        return when (device.type) {
            ConnectionType.TCP -> {
                val host = device.host?.takeIf { it.isNotBlank() } ?: return null
                val port = device.port ?: DEFAULT_TCP_PORT
                val name = device.name.ifBlank { "$host:$port" }
                device.copy(
                    name = name,
                    host = host,
                    port = port,
                    lastConnected = safeLastConnected
                )
            }
            ConnectionType.BLUETOOTH -> {
                val address = device.bluetoothAddress?.takeIf { it.isNotBlank() } ?: return null
                val name = device.name.ifBlank { address }
                device.copy(
                    name = name,
                    bluetoothAddress = address,
                    lastConnected = safeLastConnected
                )
            }
            ConnectionType.BLE_EMULSTICK -> {
                val address = device.bluetoothAddress?.takeIf { it.isNotBlank() } ?: return null
                val name = device.name.ifBlank { address }
                device.copy(
                    name = name,
                    bluetoothAddress = address,
                    lastConnected = safeLastConnected
                )
            }
        }
    }

    private fun migrateLegacyDevices(json: String): List<SavedDevice> {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonArray) {
                emptyList()
            } else {
                val migrated = root.asJsonArray.mapNotNull { element ->
                    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    parseLegacyDevice(obj)
                }
                sanitizeDevices(migrated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Legacy saved device migration failed", e)
            emptyList()
        }
    }

    private fun parseLegacyDevice(obj: JsonObject): SavedDevice? {
        val host = obj.optString("host") ?: obj.optString("ip")
        val port = obj.optInt("port") ?: obj.optInt("tcpPort")
        val address = obj.optString("bluetoothAddress")
            ?: obj.optString("address")
            ?: obj.optString("mac")
        val rawType = obj.optString("type") ?: obj.optString("connectionType") ?: obj.optString("mode")
        val numericType = obj.optInt("type") ?: obj.optInt("connectionType")
        val isBle = obj.optBoolean("isBle")
        val connectionType = resolveLegacyType(rawType, numericType, host, address, isBle) ?: return null

        val lastConnected = obj.optLong("lastConnected")
            ?: obj.optLong("lastSeen")
            ?: 0L
        val safeLastConnected = if (lastConnected > 0) lastConnected else 0L

        val name = obj.optString("name") ?: obj.optString("deviceName") ?: ""
        val idFromJson = obj.optString("id") ?: obj.optString("deviceId")

        return when (connectionType) {
            ConnectionType.TCP -> {
                val resolvedHost = host ?: return null
                val resolvedPort = port ?: DEFAULT_TCP_PORT
                SavedDevice(
                    id = idFromJson ?: "tcp_${'$'}resolvedHost_${'$'}resolvedPort",
                    name = name.ifBlank { "${'$'}resolvedHost:${'$'}resolvedPort" },
                    type = ConnectionType.TCP,
                    host = resolvedHost,
                    port = resolvedPort,
                    lastConnected = safeLastConnected
                )
            }
            ConnectionType.BLUETOOTH -> {
                val resolvedAddress = address ?: return null
                SavedDevice(
                    id = idFromJson ?: "bt_${'$'}resolvedAddress",
                    name = name.ifBlank { resolvedAddress },
                    type = ConnectionType.BLUETOOTH,
                    bluetoothAddress = resolvedAddress,
                    lastConnected = safeLastConnected
                )
            }
            ConnectionType.BLE_EMULSTICK -> {
                val resolvedAddress = address ?: return null
                SavedDevice(
                    id = idFromJson ?: "ble_${'$'}resolvedAddress",
                    name = name.ifBlank { resolvedAddress },
                    type = ConnectionType.BLE_EMULSTICK,
                    bluetoothAddress = resolvedAddress,
                    lastConnected = safeLastConnected
                )
            }
        }
    }

    private fun resolveLegacyType(
        raw: String?,
        numeric: Int?,
        host: String?,
        address: String?,
        isBleFlag: Boolean?
    ): ConnectionType? {
        raw?.trim()?.lowercase(Locale.US)?.let { value ->
            when (value) {
                "tcp", "socket", "wifi", "connectiontype.tcp" -> return ConnectionType.TCP
                "bluetooth", "bt", "rfcomm", "connectiontype.bluetooth" -> return ConnectionType.BLUETOOTH
                "ble", "ble_emulstick", "ble-emulstick", "blehid", "emulstick", "hid", "blexinput", "ble_xinput" ->
                    return ConnectionType.BLE_EMULSTICK
            }
            value.toIntOrNull()?.let { mapped -> mapNumericType(mapped)?.let { return it } }
        }

        numeric?.let { mapNumericType(it)?.let { return it } }

        if (isBleFlag == true) {
            return ConnectionType.BLE_EMULSTICK
        }

        return when {
            !address.isNullOrBlank() && address.count { it == ':' } == 5 && isBleFlag != true -> ConnectionType.BLUETOOTH
            !address.isNullOrBlank() && isBleFlag == true -> ConnectionType.BLE_EMULSTICK
            !address.isNullOrBlank() && address.startsWith("ble_", true) -> ConnectionType.BLE_EMULSTICK
            !address.isNullOrBlank() -> ConnectionType.BLUETOOTH
            !host.isNullOrBlank() -> ConnectionType.TCP
            else -> null
        }
    }

    private fun mapNumericType(value: Int): ConnectionType? {
        return when (value) {
            0 -> ConnectionType.TCP
            1 -> ConnectionType.BLUETOOTH
            2 -> ConnectionType.BLE_EMULSTICK
            else -> null
        }
    }

    private fun JsonObject.optString(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.optInt(key: String): Int? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> runCatching { element.asInt }.getOrNull()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toIntOrNull()
            else -> null
        }
    }

    private fun JsonObject.optLong(key: String): Long? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> runCatching { element.asLong }.getOrNull()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toLongOrNull()
            else -> null
        }
    }

    private fun JsonObject.optBoolean(key: String): Boolean? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isBoolean -> runCatching { element.asBoolean }.getOrNull()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toBooleanStrictOrNull()
            else -> null
        }
    }
}
