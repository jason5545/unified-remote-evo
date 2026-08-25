package com.unifiedremote.evo.network.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DeviceMode.fromVidPid 單元測試
 *
 * 涵蓋 Xbox 360 XInput (028E) 與 ESP32-S3 Evo Xbox One GIP (02EA) 兩種手把 PID。
 */
class DeviceModeTest {

    @Test
    fun xbox360PidSelectsXInput() {
        assertEquals(DeviceMode.XINPUT, DeviceMode.fromVidPid(GattConstants.XBOX360_VID, GattConstants.XBOX360_PID))
    }

    @Test
    fun xboxOneGipPidSelectsXInput() {
        assertEquals(DeviceMode.XINPUT, DeviceMode.fromVidPid(GattConstants.XBOX360_VID, GattConstants.XBOXONE_PID))
    }

    @Test
    fun compositePidsSelectComposite() {
        assertEquals(
            DeviceMode.COMPOSITE,
            DeviceMode.fromVidPid(GattConstants.TI_COMPOSITE_VID, GattConstants.TI_COMPOSITE_PID)
        )
        assertEquals(
            DeviceMode.COMPOSITE,
            DeviceMode.fromVidPid(GattConstants.WCH_COMPOSITE_VID, GattConstants.WCH_COMPOSITE_PID)
        )
        assertEquals(
            DeviceMode.COMPOSITE,
            DeviceMode.fromVidPid(GattConstants.EMULSTICK_V0_VID, GattConstants.EMULSTICK_V0_PID)
        )
    }

    @Test
    fun unknownPidReturnsUnknown() {
        assertEquals(DeviceMode.UNKNOWN, DeviceMode.fromVidPid(0x1234, 0x5678))
    }
}
