package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller is the only thing that writes to the audio service, so its clamping and
 * failure behaviour is verified against a fake backend instead of a device.
 */
class VolumeControllerTest {

    private class FakeBackend(
        private val max: Int = 15,
        private var current: Int = 7,
        private val failOnWrite: Boolean = false,
        private val min: Int = 0,
        private val ignoreWrites: Boolean = false,
        private val fixed: Boolean = false,
    ) : VolumeBackend {
        var writes = mutableListOf<Int>()
        var lastWrite: Int? = null

        override fun minVolume(stream: VolumeStreamType): Int = min
        override fun isFixedVolume(): Boolean = fixed

        override fun maxVolume(stream: VolumeStreamType): Int = max

        override fun currentVolume(stream: VolumeStreamType): Int = current

        override fun setVolume(stream: VolumeStreamType, index: Int) {
            if (failOnWrite) throw SecurityException("blocked by the system")
            writes += index
            lastWrite = index
            if (!ignoreWrites) current = index
        }
    }

    @Test
    fun `snapshot reports the real stream values`() {
        val backend = FakeBackend(max = 25, current = 3)
        val controller = VolumeController(backend)

        val state = controller.snapshot(VolumeStreamType.MEDIA)

        assertEquals(3, state.index)
        assertEquals(25, state.max)
        assertEquals(3f / 25f, state.fraction, 0.0001f)
    }

    @Test
    fun `setting an index above the maximum is clamped down`() {
        val backend = FakeBackend(max = 15)
        val result = VolumeController(backend).setIndex(VolumeStreamType.MEDIA, 40)

        assertEquals(15, backend.lastWrite)
        assertTrue(result is VolumeResult.Success)
        assertEquals(15, (result as VolumeResult.Success).state.index)
    }

    @Test
    fun `setting a negative index is clamped to zero`() {
        val backend = FakeBackend(max = 15)
        VolumeController(backend).setIndex(VolumeStreamType.MEDIA, -5)

        assertEquals(0, backend.lastWrite)
    }

    @Test
    fun `steps move the volume by the configured size`() {
        val backend = FakeBackend(max = 15, current = 7)
        val result = VolumeController(backend).changeBy(VolumeStreamType.MEDIA, deltaSteps = 2, stepSize = 3)

        assertEquals(13, backend.lastWrite)
        assertEquals(13, (result as VolumeResult.Success).state.index)
    }

    @Test
    fun `the volume can never leave the device range`() {
        val backend = FakeBackend(max = 15, current = 14)
        VolumeController(backend).changeBy(VolumeStreamType.MEDIA, deltaSteps = 10, stepSize = 2)
        assertEquals(15, backend.lastWrite)

        val quiet = FakeBackend(max = 15, current = 1)
        VolumeController(quiet).changeBy(VolumeStreamType.MEDIA, deltaSteps = -10, stepSize = 2)
        assertEquals(0, quiet.lastWrite)
    }

    @Test
    fun `no write happens when the volume is already at the limit`() {
        val backend = FakeBackend(max = 15, current = 15)
        val result = VolumeController(backend).changeBy(VolumeStreamType.MEDIA, deltaSteps = 3, stepSize = 1)

        assertNull(backend.lastWrite)
        assertTrue(result is VolumeResult.Success)
    }

    @Test
    fun `a refused write is reported instead of thrown`() {
        val backend = FakeBackend(failOnWrite = true)
        val result = VolumeController(backend).changeBy(VolumeStreamType.MEDIA, deltaSteps = 1, stepSize = 1)

        assertTrue(result is VolumeResult.Failure)
        assertTrue((result as VolumeResult.Failure).message.isNotBlank())
    }

    @Test
    fun `an unavailable stream reports failure rather than writing blindly`() {
        val backend = FakeBackend(max = 0)
        val result = VolumeController(backend).setIndex(VolumeStreamType.MEDIA, 5)

        assertTrue(result is VolumeResult.Failure)
        assertNull(backend.lastWrite)
    }

    @Test fun `nonzero stream minimum is respected`() {
        val backend = FakeBackend(max = 10, current = 4, min = 2)
        val result = VolumeController(backend).setIndex(VolumeStreamType.VOICE_CALL, -20) as VolumeResult.Success
        assertEquals(2, result.state.index)
        assertEquals(2, backend.lastWrite)
        assertEquals(0f, result.state.fraction, 0f)
    }

    @Test fun `safe volume or OEM ignored writes never appear as fake values`() {
        val backend = FakeBackend(current = 7, ignoreWrites = true)
        val controller = VolumeController(backend)
        val result = controller.setIndex(VolumeStreamType.MEDIA, 15)
        assertTrue(result is VolumeResult.Failure)
        assertEquals(7, controller.snapshot(VolumeStreamType.MEDIA).index)
    }

    @Test fun `fixed volume returns an actionable failure without a write`() {
        val backend = FakeBackend(fixed = true)
        val result = VolumeController(backend).changeBy(VolumeStreamType.MEDIA, 1, 1)
        assertTrue(result is VolumeResult.Failure)
        assertNull(backend.lastWrite)
    }

    @Test
    fun `the selected stream is the one that gets written`() {
        val backend = object : VolumeBackend {
            var stream: VolumeStreamType? = null
            override fun maxVolume(stream: VolumeStreamType) = 10
            override fun currentVolume(stream: VolumeStreamType) = 4
            override fun setVolume(stream: VolumeStreamType, index: Int) {
                this.stream = stream
            }
        }

        VolumeController(backend).changeBy(VolumeStreamType.RING, deltaSteps = 1, stepSize = 1)

        assertEquals(VolumeStreamType.RING, backend.stream)
    }
}
