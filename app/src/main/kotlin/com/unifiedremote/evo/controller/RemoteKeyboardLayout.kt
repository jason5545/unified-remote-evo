package com.unifiedremote.evo.controller

import com.unifiedremote.evo.network.RemotePlatform

data class RemoteModifierKey(val key: String, val label: String)
data class RemoteFunctionKey(val key: String, val label: String)

class RemoteKeyboardLayout(val platform: RemotePlatform) {
    val isMac: Boolean get() = platform == RemotePlatform.MAC
    val primaryModifier: String get() = if (isMac) "command" else "ctrl"
    val modifiers: List<RemoteModifierKey> get() = if (isMac) listOf(
        RemoteModifierKey("control", "⌃ Ctrl"), RemoteModifierKey("shift", "⇧ Shift"),
        RemoteModifierKey("option", "⌥ Opt"), RemoteModifierKey("command", "⌘ Cmd"),
    ) else listOf("ctrl", "shift", "alt", "win").map { RemoteModifierKey(it, it.uppercase()) }

    fun modifierLabel(key: String): String = modifiers.firstOrNull { it.key == key }?.label ?: key.uppercase()

    val applicationSwitchLabel: String get() = if (isMac) "⌘ Tab" else "Alt+Tab"
    val applicationSwitchModifiers: List<String> get() = listOf(if (isMac) "command" else "alt")
    val applicationCloseLabel: String get() = if (isMac) "⌘ Q" else "Alt+F4"
    val applicationCloseKey: String get() = if (isMac) "q" else "f4"
    val applicationCloseModifiers: List<String> get() = listOf(if (isMac) "command" else "alt")

    fun functionKey(number: Int, standardFKeys: Boolean = false): RemoteFunctionKey {
        require(number in 1..12)
        if (!isMac || standardFKeys) return RemoteFunctionKey("f$number", "F$number")
        return macFunctions[number - 1]
    }

    companion object {
        private val macFunctions = listOf(
            RemoteFunctionKey("brightnessdown", "亮度 −"), RemoteFunctionKey("brightnessup", "亮度 ＋"),
            RemoteFunctionKey("missioncontrol", "視窗總覽"), RemoteFunctionKey("spotlight", "Spotlight"),
            RemoteFunctionKey("illuminationdown", "鍵盤光 −"), RemoteFunctionKey("illuminationup", "鍵盤光 ＋"),
            RemoteFunctionKey("previous", "上一首"), RemoteFunctionKey("playpause", "播放／暫停"),
            RemoteFunctionKey("next", "下一首"), RemoteFunctionKey("mute", "靜音"),
            RemoteFunctionKey("volumedown", "音量 −"), RemoteFunctionKey("volumeup", "音量 ＋"),
        )
    }
}
