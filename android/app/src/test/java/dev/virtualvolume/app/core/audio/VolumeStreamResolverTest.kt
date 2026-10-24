package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which stream the floating control drives is the behaviour users compare against the
 * hardware rocker, so every rule is pinned down here.
 */
class VolumeStreamResolverTest {

    @Test
    fun `an idle phone drives the selected stream`() {
        val resolved = VolumeStreamResolver.resolve(
            configured = VolumeStreamType.MEDIA,
            callMode = CallAudioMode.IDLE,
            followCalls = true,
        )

        assertEquals(VolumeStreamType.MEDIA, resolved)
    }

    @Test
    fun `a call switches the selected stream to call volume`() {
        val resolved = VolumeStreamResolver.resolve(
            configured = VolumeStreamType.MEDIA,
            callMode = CallAudioMode.IN_CALL,
            followCalls = true,
        )

        assertEquals(VolumeStreamType.VOICE_CALL, resolved)
    }

    @Test
    fun `every non-call stream is replaced by call volume during a call`() {
        val nonCallStreams = VolumeStreamType.entries.filter { it != VolumeStreamType.VOICE_CALL }

        nonCallStreams.forEach { configured ->
            assertEquals(
                "during a call $configured should follow the rocker",
                VolumeStreamType.VOICE_CALL,
                VolumeStreamResolver.resolve(configured, CallAudioMode.IN_CALL, followCalls = true),
            )
        }
    }

    @Test
    fun `turning the option off keeps the selected stream during a call`() {
        val resolved = VolumeStreamResolver.resolve(
            configured = VolumeStreamType.ALARM,
            callMode = CallAudioMode.IN_CALL,
            followCalls = false,
        )

        assertEquals(VolumeStreamType.ALARM, resolved)
    }

    @Test
    fun `the volume goes back to the selected stream when the call ends`() {
        val duringCall = VolumeStreamResolver.resolve(
            VolumeStreamType.NOTIFICATION,
            CallAudioMode.IN_CALL,
            followCalls = true,
        )
        val afterCall = VolumeStreamResolver.resolve(
            VolumeStreamType.NOTIFICATION,
            CallAudioMode.IDLE,
            followCalls = true,
        )

        assertEquals(VolumeStreamType.VOICE_CALL, duringCall)
        assertEquals(VolumeStreamType.NOTIFICATION, afterCall)
    }

    @Test
    fun `choosing calls explicitly is stable before and during a call`() {
        assertEquals(
            VolumeStreamType.VOICE_CALL,
            VolumeStreamResolver.resolve(VolumeStreamType.VOICE_CALL, CallAudioMode.IDLE, true),
        )
        assertEquals(
            VolumeStreamType.VOICE_CALL,
            VolumeStreamResolver.resolve(VolumeStreamType.VOICE_CALL, CallAudioMode.IN_CALL, true),
        )
    }

    @Test
    fun `the ui can tell when the control is following a call`() {
        assertTrue(
            VolumeStreamResolver.isFollowingCall(
                configured = VolumeStreamType.MEDIA,
                active = VolumeStreamType.VOICE_CALL,
            ),
        )
        assertFalse(
            VolumeStreamResolver.isFollowingCall(
                configured = VolumeStreamType.VOICE_CALL,
                active = VolumeStreamType.VOICE_CALL,
            ),
        )
    }

    @Test
    fun `only a real in-call mode counts as a call`() {
        assertTrue(CallAudioMode.IN_CALL.isInCall)
        assertFalse(CallAudioMode.IDLE.isInCall)
    }
}
