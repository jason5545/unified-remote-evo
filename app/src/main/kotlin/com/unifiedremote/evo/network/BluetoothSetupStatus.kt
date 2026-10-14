package com.unifiedremote.evo.network

enum class BluetoothSetupStatus {
    UNSUPPORTED, PERMISSION_REQUIRED, OFF, READY;

    companion object {
        fun from(supported: Boolean, permissionsGranted: Boolean, enabled: Boolean): BluetoothSetupStatus = when {
            !supported -> UNSUPPORTED
            !permissionsGranted -> PERMISSION_REQUIRED
            !enabled -> OFF
            else -> READY
        }
    }
}
