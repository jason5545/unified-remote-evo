package com.unifiedremote.evo.network

import com.unifiedremote.evo.data.Packet

data class HostInputControlState(
    val supported: Boolean = false,
    val disabled: Boolean = false,
    val pending: Boolean = false,
    val error: String? = null,
)

object HostInputControl {
    const val ACTION: Byte = 12

    fun request(disabled: Boolean, session: String) = Packet(
        action = ACTION, request = ACTION, session = session, hostInputDisabled = disabled,
    )

    fun response(packet: Packet, session: String?): HostInputControlState? {
        if (session == null || packet.session != session || packet.action != ACTION || packet.response != ACTION) return null
        val disabled = packet.hostInputDisabled ?: return null
        return HostInputControlState(supported = true, disabled = disabled, error = packet.hostInputError)
    }
}
