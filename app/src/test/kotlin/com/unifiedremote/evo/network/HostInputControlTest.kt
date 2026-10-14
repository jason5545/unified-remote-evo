package com.unifiedremote.evo.network

import com.unifiedremote.evo.data.Capabilities
import com.unifiedremote.evo.data.Packet
import org.junit.Assert.*
import org.junit.Test

class HostInputControlTest {
    @Test fun lockAndUnlockRequestsRoundTripThroughTheActualWireCodec() {
        for (disabled in listOf(true, false)) {
            val request = HostInputControl.request(disabled, "session-1")
            val decoded = PacketSerializer.deserialize(PacketSerializer.serialize(request))!!
            assertEquals(HostInputControl.ACTION, decoded.action)
            assertEquals(HostInputControl.ACTION, decoded.request)
            assertEquals(disabled, decoded.hostInputDisabled)
            assertEquals("session-1", decoded.session)
        }
    }

    @Test fun onlyConfirmedResponsesFromTheCurrentSessionUpdateState() {
        val response = Packet(action = 12, response = 12, session = "session-1", hostInputDisabled = true)
        val decoded = PacketSerializer.deserialize(PacketSerializer.serialize(response))!!
        assertTrue(HostInputControl.response(decoded, "session-1")!!.disabled)
        assertNull(HostInputControl.response(decoded, "old-session"))
        assertNull(HostInputControl.response(decoded, null))
        assertNull(HostInputControl.response(decoded.copy(hostInputDisabled = null), "session-1"))
        assertNull(HostInputControl.response(decoded.copy(response = null), "session-1"))
    }

    @Test fun capabilityDoesNotEnableTheLegacyFastHandshake() {
        val packet = Packet(capabilities = Capabilities(hostInputControl = true))
        val decoded = PacketSerializer.deserialize(PacketSerializer.serialize(packet))!!
        assertTrue(decoded.capabilities!!.hostInputControl!!)
        assertNull(decoded.capabilities!!.fast)
    }

    @Test fun permissionFailureReportsUnlockedStateAndTheRealError() {
        val packet = Packet(action = 12, response = 12, session = "session-1",
                            hostInputDisabled = false, hostInputError = "沒有輸入監控權限")
        val decoded = PacketSerializer.deserialize(PacketSerializer.serialize(packet))!!
        val state = HostInputControl.response(decoded, "session-1")!!
        assertFalse(state.disabled)
        assertFalse(state.pending)
        assertEquals("沒有輸入監控權限", state.error)
    }
}
