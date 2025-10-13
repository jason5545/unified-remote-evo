package com.unifiedremote.evo.network.ble

import java.util.UUID

/**
 * EmulStick GATT 服務與特徵值常量
 *
 * 基於 EmulStick 藍牙協定的 UUID 定義
 * 資料來源：apk_analysis/emulstick_decompiled/.../ble/GattInfo.java
 */
object GattConstants {

    // ============ 標準藍牙 SIG 服務 ============

    /** HID 服務（Human Interface Device over GATT Profile） */
    val SERVICE_HID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")

    /** 裝置資訊服務 */
    val SERVICE_DEVICE_INFO: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")

    // 裝置資訊特徵值
    val CHAR_SYSTEM_ID: UUID = UUID.fromString("00002A23-0000-1000-8000-00805f9b34fb")
    val CHAR_MODEL_NUMBER: UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
    val CHAR_SERIAL_NUMBER: UUID = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb")
    val CHAR_FIRMWARE_VERSION: UUID = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb")
    val CHAR_HARDWARE_VERSION: UUID = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb")
    val CHAR_SOFTWARE_VERSION: UUID = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb")
    val CHAR_PNP_ID: UUID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb")

    // ============ EmulStick 自訂服務 ============

    /** EmulStick 主服務 */
    val SERVICE_EMULSTICK: UUID = UUID.fromString("0000F800-0000-1000-8000-00805f9b34fb")

    /** CH1 特徵值 - 鍵盤 HID 報告（Ver ≥1: SingleKeyboard） */
    val CHAR_CH1: UUID = UUID.fromString("0000F801-0000-1000-8000-00805f9b34fb")

    /** CH2 特徵值 - 遊戲手把 HID 報告（保留，但本專案不使用） */
    val CHAR_CH2: UUID = UUID.fromString("0000F802-0000-1000-8000-00805f9b34fb")

    /** CH3 特徵值 - 滑鼠 HID 報告（Ver ≥1: MouseV1） */
    val CHAR_CH3: UUID = UUID.fromString("0000F803-0000-1000-8000-00805f9b34fb")

    /** CH4 特徵值 - 額外輸入 */
    val CHAR_CH4: UUID = UUID.fromString("0000F804-0000-1000-8000-00805f9b34fb")

    /** CH5 特徵值 - Unicode 輸入（僅 ESP32-S3 Evo）*/
    val CHAR_CH5_UNICODE: UUID = UUID.fromString("0000F805-0000-1000-8000-00805f9b34fb")

    /** COMMAND 特徵值 - 控制指令 */
    val CHAR_COMMAND: UUID = UUID.fromString("0000F80F-0000-1000-8000-00805f9b34fb")

    /** 標準 CCCD（Client Characteristic Configuration Descriptor） */
    val DESC_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ============ 連線參數 ============

    /** 裝置名稱前綴（小寫） */
    const val DEVICE_NAME_PREFIX = "emulstick"

    /** 掃描逾時（毫秒） */
    const val SCAN_TIMEOUT_MS = 4000L  // 4 秒（與 EmulStick 原始實作一致）

    /** 連線逾時（秒） */
    const val CONNECTION_TIMEOUT_SEC = 5

    /** 重連逾時（秒） */
    const val RECONNECTION_TIMEOUT_SEC = 10

    // ============ BLE 指令常量 ============

    /** 取得加密文字 */
    const val CMD_GET_CIPHERTEXT: Byte = 0x91.toByte()  // -111

    /** 設定通用參數 */
    const val CMD_SET_COMMON: Byte = 0x40.toByte()  // 64

    /** 設定模擬裝置類型 */
    const val CMD_SET_EMULDEVICE: Byte = 0x50.toByte()  // 80

    /** 設定複合裝置 */
    const val CMD_SET_COMPOSITE: Byte = 0x51.toByte()  // 81

    // ============ 模式切換常數 ============

    /** Xbox 360 控制器裝置識別碼（XInput 模式） */
    const val XBOX360_VID = 0x045E  // 1118, Microsoft
    const val XBOX360_PID = 0x028E  // 654, Xbox 360 Controller

    /** TI Composite 裝置識別碼（組合模式） */
    const val TI_COMPOSITE_VID = 0x0451  // 1105, Texas Instruments
    const val TI_COMPOSITE_PID = 0xE010  // 57360, Composite Device

    /** WCH Composite 裝置識別碼（組合模式） */
    const val WCH_COMPOSITE_VID = 0x4348  // 17224, WCH
    const val WCH_COMPOSITE_PID = 0xE010  // 57360, Composite Device

    /** EmulStick V0 裝置識別碼（組合模式，舊版） */
    const val EMULSTICK_V0_VID = 0x0451  // 1105, Texas Instruments
    const val EMULSTICK_V0_PID = 0x16B4  // 5812, EmulStick V0

    /** Single Keyboard 裝置識別碼（單鍵盤模式） */
    const val SINGLE_KB_VID = 0x045E  // 1118, Microsoft
    const val SINGLE_KB_PID = 0x002D  // 45, Microsoft Keyboard

    /** 取得模擬狀態 */
    const val CMD_GET_EMULATE: Byte = 0xA1.toByte()  // -95

    /** 回報模擬狀態 */
    const val CMD_REPORT_EMULATE: Byte = 0xA0.toByte()  // -96

    /** LED 開關 */
    const val CMD_SWITCH_LED: Byte = 0x61.toByte()  // 97

    /** 設定 MITM 密碼 */
    const val CMD_SET_MITM_PASSWORD: Byte = 0xE1.toByte()  // -31

    /** 設定裝置名稱 */
    const val CMD_SET_DEVICE_NAME: Byte = 0xE2.toByte()  // -30

    // ============ CCCD 值 ============

    /** 啟用通知 */
    val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)

    /** 停用通知 */
    val DISABLE_NOTIFICATION_VALUE = byteArrayOf(0x00, 0x00)

    // ============ 身份驗證 ============

    /**
     * 明文密碼對照表
     *
     * 根據 Software Version 的前 3 字元判斷版本
     * 參考：apk_analysis/emulstick_decompiled/.../BluetoothLeService.java 第 355 行
     */
    val PLAIN_TEXT_MAP = mapOf(
        "1.0" to "uEt2#3uhF5+Ygu%0",
        "2.0" to "Iu&I2U8;7^5*iI4z"
    )
}
