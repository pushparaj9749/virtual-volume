package dev.virtualvolume.app.core.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `AudioManager.mode` is the only permission-free signal that a call is up, so the mapping
 * from the platform constant to [CallAudioMode] is verified directly.
 */
class CallAudioMonitorTest {

    @Test
    fun `a cellular call maps to in call`() {
        assertEquals(
            CallAudioMode.IN_CALL,
            CallAudioMonitor.mapMode(AudioManager.MODE_IN_CALL),
        )
    }

    @Test
    fun `a voip call maps to in call`() {
        // WhatsApp, Signal and Meet all set MODE_IN_COMMUNICATION during a call.
        assertEquals(
            CallAudioMode.IN_CALL,
            CallAudioMonitor.mapMode(AudioManager.MODE_IN_COMMUNICATION),
        )
    }

    @Test
    fun `a call whose audio is redirected still counts as in call`() {
        assertEquals(
            CallAudioMode.IN_CALL,
            CallAudioMonitor.mapMode(AudioManager.MODE_COMMUNICATION_REDIRECT),
        )
    }

    @Test
    fun `ordinary audio modes map to idle`() {
        listOf(
            AudioManager.MODE_NORMAL,
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_INVALID,
        ).forEach { mode ->
            assertEquals("mode $mode", CallAudioMode.IDLE, CallAudioMonitor.mapMode(mode))
        }
    }
}
