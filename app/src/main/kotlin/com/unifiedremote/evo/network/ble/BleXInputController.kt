package com.unifiedremote.evo.network.ble

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * XInput (Xbox 360) 控制器
 *
 * 實作 EmulStick 的 Xbox 360 控制器模式，支援：
 * - 雙搖桿（左/右）
 * - 雙扳機（LT/RT）
 * - D-Pad（方向鍵）
 * - 10 個按鈕（A/B/X/Y/LB/RB/BACK/START/L3/R3）
 *
 * 技術要點：
 * - 使用 CH1 characteristic (0xF801) 傳送 HID Report
 * - 20-byte Xbox 360 HID Report 格式
 * - 透過 BLECMD_SET_EMULDEVICE (0x50) 切換模式
 * - VID/PID: Microsoft Xbox 360 Controller (0x045E/0x028E)
 *
 * 參考：EMULSTICK_XINPUT_ANALYSIS.md
 */
class BleXInputController(private val bleManager: BleManager) {

    companion object {
        private const val TAG = "BleXInputController"

        // 數值範圍
        private const val STICK_MAX = 32767
        private const val STICK_MIN = -32767
        private const val TRIGGER_MAX = 255
    }

    // ============ 狀態管理 ============

    private val _connectionState = MutableStateFlow<XInputConnectionState>(XInputConnectionState.Disconnected)
    val connectionState: StateFlow<XInputConnectionState> = _connectionState.asStateFlow()

    // ============ HID Report 狀態 ============

    /**
     * 當前 Xbox 360 HID Report（20 bytes）
     *
     * 格式：
     * [0]     固定值 0x00
     * [1]     封包長度 0x14 (20)
     * [2]     按鈕組 1 + D-Pad
     * [3]     按鈕組 2
     * [4]     左扳機 (LT) 0-255
     * [5]     右扳機 (RT) 0-255
     * [6-7]   左搖桿 X (Little Endian, -32767 ~ 32767)
     * [8-9]   左搖桿 Y (Little Endian, -32767 ~ 32767)
     * [10-11] 右搖桿 X (Little Endian, -32767 ~ 32767)
     * [12-13] 右搖桿 Y (Little Endian, -32767 ~ 32767)
     * [14-19] 保留 0x00
     */
    private val currentReport = ByteArray(20)

    init {
        // 初始化 Report
        currentReport[0] = 0x00  // 固定值
        currentReport[1] = 0x14  // 封包長度 (20)
    }

    // ============ 模式切換 ============

    /**
     * 切換到 Xbox 360 模式
     *
     * 傳送 BLECMD_SET_EMULDEVICE 指令，將 EmulStick 切換為 Xbox 360 控制器模式。
     * 切換後，CH1 將用於傳送 Xbox 360 HID Report，鍵盤功能將不可用。
     *
     * @return Result<Unit> 成功或失敗
     */
    suspend fun switchToXInputMode(): Result<Unit> {
        return try {
            val systemId = bleManager.getSystemId()
                ?: return Result.failure(Exception("SystemID 不可用，請先連線到 EmulStick 裝置"))

            if (systemId.size < 8) {
                return Result.failure(Exception("SystemID 長度不足（預期 8 bytes，實際 ${systemId.size} bytes）"))
            }

            // 建立切換指令：[CMD][SystemID[6]][SystemID[7]][VID_Low][VID_High][PID_Low][PID_High]
            val command = byteArrayOf(
                GattConstants.CMD_SET_EMULDEVICE,
                systemId[6],
                systemId[7],
                (GattConstants.XBOX360_VID and 0xFF).toByte(),
                ((GattConstants.XBOX360_VID shr 8) and 0xFF).toByte(),
                (GattConstants.XBOX360_PID and 0xFF).toByte(),
                ((GattConstants.XBOX360_PID shr 8) and 0xFF).toByte()
            )

            Log.d(TAG, "傳送 XInput 模式切換指令：[${command.joinToString(" ") { "%02X".format(it) }}]")
            com.unifiedremote.evo.network.ConnectionLogger.log(
                "🎮 切換到 Xbox 360 模式",
                com.unifiedremote.evo.network.ConnectionLogger.LogLevel.INFO
            )

            // 傳送指令到 COMMAND characteristic
            val success = bleManager.writeCharacteristic(
                GattConstants.CHAR_COMMAND,
                command
            )

            if (success) {
                _connectionState.value = XInputConnectionState.XInputMode
                Log.d(TAG, "✅ 已切換到 XInput 模式")
                Result.success(Unit)
            } else {
                Result.failure(Exception("寫入 COMMAND characteristic 失敗"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "切換到 XInput 模式失敗", e)
            com.unifiedremote.evo.network.ConnectionLogger.log(
                "❌ 切換到 XInput 模式失敗：${e.message}",
                com.unifiedremote.evo.network.ConnectionLogger.LogLevel.ERROR
            )
            Result.failure(e)
        }
    }

    /**
     * 切換回組合模式（鍵盤/滑鼠模式）
     *
     * 根據原廠實作，根據 PNP VID 和裝置版本選擇正確的切換策略：
     * 1. PNP VID = 2007 (0x07D7, WCH)：使用 BLECMD_SET_COMPOSITE (0x51)
     * 2. PNP VID = 13 (0x0D, TI) + Ver 0：使用 BLECMD_SET_COMMON (0x40)
     * 3. PNP VID = 13 (0x0D, TI) + Ver ≥1：使用 BLECMD_SET_EMULDEVICE (0x50) + TiComposite VID/PID
     *
     * 參考：MainActivity.java 第 986-1011 行
     *
     * @return Result<Unit> 成功或失敗
     */
    suspend fun switchToCompositeMode(): Result<Unit> {
        return try {
            val systemId = bleManager.getSystemId()
                ?: return Result.failure(Exception("SystemID 不可用"))

            if (systemId.size < 8) {
                return Result.failure(Exception("SystemID 長度不足"))
            }

            // 取得 PNP VID
            val pnpVid = bleManager.getPnpVid()
            Log.d(TAG, "PNP VID: $pnpVid (0x${pnpVid.toString(16).uppercase()})")

            // 根據 PNP VID 選擇切換策略
            val command: ByteArray = when (pnpVid) {
                2007 -> {  // 0x07D7 (WCH - 沁恒微電子)
                    // WCH 廠商：使用 BLECMD_SET_COMPOSITE (0x51)
                    Log.d(TAG, "偵測到 WCH 廠商 (VID=$pnpVid)，使用 BLECMD_SET_COMPOSITE")
                    byteArrayOf(
                        GattConstants.CMD_SET_COMPOSITE,
                        systemId[6],
                        systemId[7]
                    )
                }
                13 -> {  // 0x0D (TI - Texas Instruments，預設值)
                    // TI 廠商：根據版本選擇策略
                    if (bleManager.isDeviceV0()) {
                        // Ver 0 裝置：使用 BLECMD_SET_COMMON (0x40)
                        Log.d(TAG, "偵測到 TI Ver 0 裝置 (VID=$pnpVid)，使用 BLECMD_SET_COMMON")
                        byteArrayOf(
                            GattConstants.CMD_SET_COMMON,
                            systemId[6],
                            systemId[7]
                        )
                    } else {
                        // Ver ≥1 裝置：使用 BLECMD_SET_EMULDEVICE (0x50) + TiComposite VID/PID
                        Log.d(TAG, "偵測到 TI Ver ≥1 裝置 (VID=$pnpVid)，使用 BLECMD_SET_EMULDEVICE + TiComposite VID/PID")
                        byteArrayOf(
                            GattConstants.CMD_SET_EMULDEVICE,
                            systemId[6],
                            systemId[7],
                            (GattConstants.TI_COMPOSITE_VID and 0xFF).toByte(),
                            ((GattConstants.TI_COMPOSITE_VID shr 8) and 0xFF).toByte(),
                            (GattConstants.TI_COMPOSITE_PID and 0xFF).toByte(),
                            ((GattConstants.TI_COMPOSITE_PID shr 8) and 0xFF).toByte()
                        )
                    }
                }
                else -> {
                    // 不支援的廠商，嘗試使用 TI Ver ≥1 策略作為預設值
                    Log.w(TAG, "⚠️ 未知 PNP VID: $pnpVid，嘗試使用 TI Ver ≥1 策略")
                    byteArrayOf(
                        GattConstants.CMD_SET_EMULDEVICE,
                        systemId[6],
                        systemId[7],
                        (GattConstants.TI_COMPOSITE_VID and 0xFF).toByte(),
                        ((GattConstants.TI_COMPOSITE_VID shr 8) and 0xFF).toByte(),
                        (GattConstants.TI_COMPOSITE_PID and 0xFF).toByte(),
                        ((GattConstants.TI_COMPOSITE_PID shr 8) and 0xFF).toByte()
                    )
                }
            }

            Log.d(TAG, "傳送組合模式切換指令：[${command.joinToString(" ") { "%02X".format(it) }}]")
            com.unifiedremote.evo.network.ConnectionLogger.log(
                "⌨️ 切換回組合模式（PNP VID=$pnpVid, 指令長度=${command.size}）",
                com.unifiedremote.evo.network.ConnectionLogger.LogLevel.INFO
            )

            val success = bleManager.writeCharacteristic(
                GattConstants.CHAR_COMMAND,
                command
            )

            if (success) {
                _connectionState.value = XInputConnectionState.CompositeMode
                Log.d(TAG, "✅ 已切換回組合模式")
                Result.success(Unit)
            } else {
                Result.failure(Exception("寫入 COMMAND characteristic 失敗"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "切換回組合模式失敗", e)
            com.unifiedremote.evo.network.ConnectionLogger.log(
                "❌ 切換回組合模式失敗：${e.message}",
                com.unifiedremote.evo.network.ConnectionLogger.LogLevel.ERROR
            )
            Result.failure(e)
        }
    }

    // ============ 搖桿控制 ============

    /**
     * 設定左搖桿位置
     *
     * @param x -1.0f（左）~ 1.0f（右）
     * @param y -1.0f（下）~ 1.0f（上）
     */
    suspend fun setLeftStick(x: Float, y: Float) {
        val xValue = (x.coerceIn(-1f, 1f) * STICK_MAX).toInt()
        val yValue = -(y.coerceIn(-1f, 1f) * STICK_MAX).toInt()  // Y 軸反向

        // Bytes 6-7: Left X (Little Endian)
        currentReport[6] = (xValue and 0xFF).toByte()
        currentReport[7] = ((xValue shr 8) and 0xFF).toByte()

        // Bytes 8-9: Left Y (Little Endian)
        currentReport[8] = (yValue and 0xFF).toByte()
        currentReport[9] = ((yValue shr 8) and 0xFF).toByte()

        sendReport()
    }

    /**
     * 設定右搖桿位置
     *
     * @param x -1.0f（左）~ 1.0f（右）
     * @param y -1.0f（下）~ 1.0f（上）
     */
    suspend fun setRightStick(x: Float, y: Float) {
        val xValue = (x.coerceIn(-1f, 1f) * STICK_MAX).toInt()
        val yValue = -(y.coerceIn(-1f, 1f) * STICK_MAX).toInt()  // Y 軸反向

        // Bytes 10-11: Right X (Little Endian)
        currentReport[10] = (xValue and 0xFF).toByte()
        currentReport[11] = ((xValue shr 8) and 0xFF).toByte()

        // Bytes 12-13: Right Y (Little Endian)
        currentReport[12] = (yValue and 0xFF).toByte()
        currentReport[13] = ((yValue shr 8) and 0xFF).toByte()

        sendReport()
    }

    // ============ 扳機控制 ============

    /**
     * 設定扳機
     *
     * @param left 左扳機（LT）：0.0f ~ 1.0f
     * @param right 右扳機（RT）：0.0f ~ 1.0f
     */
    suspend fun setTriggers(left: Float, right: Float) {
        val leftValue = (left.coerceIn(0f, 1f) * TRIGGER_MAX).toInt()
        val rightValue = (right.coerceIn(0f, 1f) * TRIGGER_MAX).toInt()

        currentReport[4] = leftValue.toByte()   // LT (Byte 4)
        currentReport[5] = rightValue.toByte()  // RT (Byte 5)

        sendReport()
    }

    // ============ 按鈕控制 ============

    /**
     * 按下按鈕
     *
     * @param button 按鈕類型（XInputButton 枚舉）
     */
    suspend fun pressButton(button: XInputButton) {
        when (button) {
            // Byte 3 按鈕
            XInputButton.A -> currentReport[3] = (currentReport[3].toInt() or 0x10).toByte()  // Bit 4
            XInputButton.B -> currentReport[3] = (currentReport[3].toInt() or 0x20).toByte()  // Bit 5
            XInputButton.X -> currentReport[3] = (currentReport[3].toInt() or 0x80).toByte()  // Bit 7
            XInputButton.Y -> currentReport[3] = (currentReport[3].toInt() or 0x40).toByte()  // Bit 6
            XInputButton.LB -> currentReport[3] = (currentReport[3].toInt() or 0x01).toByte() // Bit 0
            XInputButton.RB -> currentReport[3] = (currentReport[3].toInt() or 0x02).toByte() // Bit 1

            // Byte 2 按鈕
            XInputButton.BACK -> currentReport[2] = (currentReport[2].toInt() or 0x20).toByte()  // Bit 5
            XInputButton.START -> currentReport[2] = (currentReport[2].toInt() or 0x10).toByte() // Bit 4
            XInputButton.L3 -> currentReport[2] = (currentReport[2].toInt() or 0x80).toByte()    // Bit 7
            XInputButton.R3 -> currentReport[2] = (currentReport[2].toInt() or 0x40).toByte()    // Bit 6
        }

        sendReport()
    }

    /**
     * 釋放按鈕
     *
     * @param button 按鈕類型（XInputButton 枚舉）
     */
    suspend fun releaseButton(button: XInputButton) {
        when (button) {
            // Byte 3 按鈕
            XInputButton.A -> currentReport[3] = (currentReport[3].toInt() and 0xEF).toByte()  // 清除 Bit 4
            XInputButton.B -> currentReport[3] = (currentReport[3].toInt() and 0xDF).toByte()  // 清除 Bit 5
            XInputButton.X -> currentReport[3] = (currentReport[3].toInt() and 0x7F).toByte()  // 清除 Bit 7
            XInputButton.Y -> currentReport[3] = (currentReport[3].toInt() and 0xBF).toByte()  // 清除 Bit 6
            XInputButton.LB -> currentReport[3] = (currentReport[3].toInt() and 0xFE).toByte() // 清除 Bit 0
            XInputButton.RB -> currentReport[3] = (currentReport[3].toInt() and 0xFD).toByte() // 清除 Bit 1

            // Byte 2 按鈕
            XInputButton.BACK -> currentReport[2] = (currentReport[2].toInt() and 0xDF).toByte()  // 清除 Bit 5
            XInputButton.START -> currentReport[2] = (currentReport[2].toInt() and 0xEF).toByte() // 清除 Bit 4
            XInputButton.L3 -> currentReport[2] = (currentReport[2].toInt() and 0x7F).toByte()    // 清除 Bit 7
            XInputButton.R3 -> currentReport[2] = (currentReport[2].toInt() and 0xBF).toByte()    // 清除 Bit 6
        }

        sendReport()
    }

    // ============ D-Pad 控制 ============

    /**
     * 設定 D-Pad 方向
     *
     * @param direction D-Pad 方向（DPadDirection 枚舉）
     */
    suspend fun setDPad(direction: DPadDirection) {
        // 清除 D-Pad 位元（Byte 2 低 4 位）
        currentReport[2] = (currentReport[2].toInt() and 0xF0).toByte()

        // 設定新方向
        val dpadValue = when (direction) {
            DPadDirection.CENTER -> 0x00
            DPadDirection.UP -> 0x01
            DPadDirection.UP_RIGHT -> 0x02
            DPadDirection.RIGHT -> 0x04
            DPadDirection.DOWN_RIGHT -> 0x05
            DPadDirection.DOWN -> 0x08
            DPadDirection.DOWN_LEFT -> 0x09
            DPadDirection.LEFT -> 0x0A
            DPadDirection.UP_LEFT -> 0x06
        }

        currentReport[2] = (currentReport[2].toInt() or dpadValue).toByte()

        sendReport()
    }

    // ============ 輔助方法 ============

    /**
     * 釋放所有按鍵
     *
     * 將所有按鈕、D-Pad、搖桿、扳機都歸零。
     */
    suspend fun releaseAll() {
        // 清除按鈕和 D-Pad（Bytes 2-3）
        currentReport[2] = 0x00.toByte()
        currentReport[3] = 0x00.toByte()

        // 扳機歸零（Bytes 4-5）
        currentReport[4] = 0x00.toByte()
        currentReport[5] = 0x00.toByte()

        // 搖桿歸零（Bytes 6-13）
        for (i in 6..13) {
            currentReport[i] = 0x00.toByte()
        }

        sendReport()
    }

    /**
     * 傳送 HID Report
     *
     * 將當前的 Report 狀態傳送到 CH1 characteristic。
     */
    private suspend fun sendReport() {
        val success = bleManager.writeCharacteristic(
            GattConstants.CHAR_CH1,
            currentReport.copyOf()
        )

        if (!success) {
            Log.w(TAG, "傳送 Xbox 360 HID Report 失敗")
        }
    }
}

// ============ 資料類別與枚舉 ============

/**
 * XInput 按鈕枚舉
 */
enum class XInputButton {
    A,      // A 鈕
    B,      // B 鈕
    X,      // X 鈕
    Y,      // Y 鈕
    LB,     // 左肩鈕
    RB,     // 右肩鈕
    BACK,   // 返回鈕
    START,  // 開始鈕
    L3,     // 左搖桿按下
    R3      // 右搖桿按下
}

/**
 * D-Pad 方向枚舉
 */
enum class DPadDirection {
    CENTER,         // 中心（無方向）
    UP,             // 上
    UP_RIGHT,       // 右上
    RIGHT,          // 右
    DOWN_RIGHT,     // 右下
    DOWN,           // 下
    DOWN_LEFT,      // 左下
    LEFT,           // 左
    UP_LEFT         // 左上
}

/**
 * XInput 連線狀態
 */
sealed class XInputConnectionState {
    object Disconnected : XInputConnectionState()    // 未連線
    object CompositeMode : XInputConnectionState()   // 組合模式（鍵盤/滑鼠）
    object XInputMode : XInputConnectionState()      // XInput 模式（Xbox 360）
}
