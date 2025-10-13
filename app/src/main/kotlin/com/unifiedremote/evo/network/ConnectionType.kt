package com.unifiedremote.evo.network

/**
 * 連線類型
 */
enum class ConnectionType {
    TCP,           // TCP/IP 連線（WiFi、Tailscale）
    BLUETOOTH,     // 傳統藍牙連線（SPP）
    BLE_EMULSTICK  // BLE 連線（EmulStick HID）
}
