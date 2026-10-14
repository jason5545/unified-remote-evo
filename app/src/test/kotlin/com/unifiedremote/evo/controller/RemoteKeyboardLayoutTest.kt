package com.unifiedremote.evo.controller

import com.unifiedremote.evo.network.RemotePlatform
import org.junit.Assert.*
import org.junit.Test

class RemoteKeyboardLayoutTest {
    @Test fun detectMacFromHandshakeOnly() {
        for (name in listOf("mac", "macOS", "OSX", " Darwin ")) {
            assertEquals(RemotePlatform.MAC, RemotePlatform.fromWireName(name))
        }
        assertEquals(RemotePlatform.WINDOWS, RemotePlatform.fromWireName("windows"))
        assertEquals(RemotePlatform.UNKNOWN, RemotePlatform.fromWireName(null))
        assertEquals(RemotePlatform.UNKNOWN, RemotePlatform.fromWireName("EmulStick"))
    }

    @Test fun macShortcutsUseNativeModifiers() {
        val layout = RemoteKeyboardLayout(RemotePlatform.MAC)
        assertEquals("command", layout.primaryModifier)
        assertEquals(listOf("control", "shift", "option", "command"), layout.modifiers.map { it.key })
        assertEquals(listOf("command"), layout.applicationSwitchModifiers)
        assertEquals("q", layout.applicationCloseKey)
        assertEquals(listOf("command"), layout.applicationCloseModifiers)
    }

    @Test fun macFunctionRowHasMediaKeysAndRawFKeyOverride() {
        val layout = RemoteKeyboardLayout(RemotePlatform.MAC)
        assertEquals("brightnessdown", layout.functionKey(1).key)
        assertEquals("brightnessup", layout.functionKey(2).key)
        assertEquals("missioncontrol", layout.functionKey(3).key)
        assertEquals("playpause", layout.functionKey(8).key)
        assertEquals("mute", layout.functionKey(10).key)
        assertEquals("volumedown", layout.functionKey(11).key)
        assertEquals("volumeup", layout.functionKey(12).key)
        for (number in 1..12) assertEquals("f$number", layout.functionKey(number, true).key)
    }

    @Test fun windowsAndUnknownReceiversKeepExistingKeys() {
        for (platform in listOf(RemotePlatform.WINDOWS, RemotePlatform.UNKNOWN, RemotePlatform.LINUX)) {
            val layout = RemoteKeyboardLayout(platform)
            assertEquals("ctrl", layout.primaryModifier)
            assertEquals(listOf("ctrl", "shift", "alt", "win"), layout.modifiers.map { it.key })
            assertEquals(listOf("alt"), layout.applicationSwitchModifiers)
            assertEquals("f4", layout.applicationCloseKey)
            for (number in 1..12) assertEquals("f$number", layout.functionKey(number).key)
        }
    }
}
