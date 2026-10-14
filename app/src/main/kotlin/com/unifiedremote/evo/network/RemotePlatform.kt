package com.unifiedremote.evo.network

import java.util.Locale

enum class RemotePlatform {
    UNKNOWN, WINDOWS, MAC, LINUX;

    companion object {
        fun fromWireName(name: String?): RemotePlatform = when (name?.trim()?.lowercase(Locale.ROOT)) {
            "mac", "macos", "osx", "darwin" -> MAC
            "windows", "win", "win32", "win64" -> WINDOWS
            "linux" -> LINUX
            else -> UNKNOWN
        }
    }
}
