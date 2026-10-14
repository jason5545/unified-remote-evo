package com.unifiedremote.evo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothSetupStatusTest {
    @Test fun disabledRadioHasAnEnableActionInsteadOfLookingUnsupported() {
        assertEquals(BluetoothSetupStatus.OFF, BluetoothSetupStatus.from(true, true, false))
        assertEquals(BluetoothSetupStatus.READY, BluetoothSetupStatus.from(true, true, true))
    }

    @Test fun missingPermissionAndMissingHardwareHaveDifferentRemedies() {
        assertEquals(BluetoothSetupStatus.PERMISSION_REQUIRED, BluetoothSetupStatus.from(true, false, false))
        assertEquals(BluetoothSetupStatus.UNSUPPORTED, BluetoothSetupStatus.from(false, true, false))
    }
}
