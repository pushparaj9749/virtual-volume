package dev.virtualvolume.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMathTest {

    @Test
    fun `indexes are clamped to the device range`() {
        assertEquals(0, VolumeMath.clamp(-4, max = 15))
        assertEquals(0, VolumeMath.clamp(0, max = 15))
        assertEquals(7, VolumeMath.clamp(7, max = 15))
        assertEquals(15, VolumeMath.clamp(15, max = 15))
        assertEquals(15, VolumeMath.clamp(99, max = 15))
    }

    @Test
    fun `steps are applied and clamped`() {
        assertEquals(9, VolumeMath.applyStep(current = 7, deltaSteps = 2, stepSize = 1, max = 15))
        assertEquals(13, VolumeMath.applyStep(current = 7, deltaSteps = 2, stepSize = 3, max = 15))
        assertEquals(15, VolumeMath.applyStep(current = 14, deltaSteps = 5, stepSize = 1, max = 15))
        assertEquals(0, VolumeMath.applyStep(current = 1, deltaSteps = -5, stepSize = 1, max = 15))
        assertEquals(7, VolumeMath.applyStep(current = 7, deltaSteps = 0, stepSize = 1, max = 15))
    }

    @Test
    fun `at sensitivity one the full bar sweeps the whole range`() {
        assertEquals(20f, VolumeMath.pixelsPerStep(300f, maxVolume = 15, sensitivity = 1f), 0.001f)
    }

    @Test
    fun `higher sensitivity needs fewer pixels per step`() {
        assertEquals(10f, VolumeMath.pixelsPerStep(300f, maxVolume = 15, sensitivity = 2f), 0.001f)
        assertEquals(40f, VolumeMath.pixelsPerStep(300f, maxVolume = 15, sensitivity = 0.5f), 0.001f)
    }

    @Test
    fun `a missing range falls back to a usable default`() {
        assertEquals(
            VolumeMath.DEFAULT_PIXELS_PER_STEP,
            VolumeMath.pixelsPerStep(300f, maxVolume = 0, sensitivity = 1f),
            0.001f,
        )
        assertEquals(
            VolumeMath.DEFAULT_PIXELS_PER_STEP,
            VolumeMath.pixelsPerStep(0f, maxVolume = 15, sensitivity = 1f),
            0.001f,
        )
    }

    @Test
    fun `extreme settings stay inside sane bounds`() {
        assertEquals(
            VolumeMath.MIN_PIXELS_PER_STEP,
            VolumeMath.pixelsPerStep(100f, maxVolume = 15, sensitivity = 500f),
            0.001f,
        )
        assertEquals(
            VolumeMath.MAX_PIXELS_PER_STEP,
            VolumeMath.pixelsPerStep(100000f, maxVolume = 1, sensitivity = 0.2f),
            0.001f,
        )
    }
}
