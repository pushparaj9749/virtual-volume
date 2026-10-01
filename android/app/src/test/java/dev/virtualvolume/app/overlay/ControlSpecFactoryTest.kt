package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.VolumeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dp → px conversion that decides what the user actually touches.
 *
 * The invariant that matters most: the touch zone always contains the bar plus its glow,
 * whatever combination of sliders the user picks.
 */
class ControlSpecFactoryTest {

    private val density = 3f

    @Test
    fun `defaults convert to the expected pixel geometry`() {
        val style = ControlSpecFactory.style(VolumeSettings.DEFAULT, density)

        assertEquals(396f, style.lengthPx, 0.001f) // 132dp
        assertEquals(15f, style.thicknessPx, 0.001f) // 5dp
        assertEquals(570f, style.touchLengthPx, 0.001f) // 190dp wins over the minimum
        assertEquals(120f, style.touchWidthPx, 0.001f) // 40dp wins over the minimum
        assertEquals(0.42f, style.idleAlpha, 0.001f)
        assertEquals(1f, style.activeAlpha, 0.001f)
    }

    @Test
    fun `the touch zone always contains the bar and its glow`() {
        val settings = VolumeSettings(
            lengthDp = VolumeSettings.MAX_LENGTH_DP,
            thicknessDp = VolumeSettings.MAX_THICKNESS_DP,
            touchZoneWidthDp = VolumeSettings.MIN_TOUCH_WIDTH_DP,
            touchZoneLengthDp = VolumeSettings.MIN_TOUCH_LENGTH_DP,
        ).sanitized()

        val style = ControlSpecFactory.style(settings, density)
        val padding = ControlPainter.contentPadding(style)

        assertTrue(style.touchLengthPx >= style.lengthPx + padding * 2f)
        assertTrue(style.touchWidthPx >= style.thicknessPx + padding * 2f)
    }

    @Test
    fun `opacity below the floor is lifted so the control stays findable`() {
        val style = ControlSpecFactory.style(
            VolumeSettings.DEFAULT.copy(idleOpacity = 0f).sanitized(),
            density,
        )

        assertTrue(style.idleAlpha >= ControlStyle.MIN_ALPHA)
    }

    @Test
    fun `the gesture config maps the bar length onto the volume range`() {
        val config = ControlSpecFactory.gestureConfig(
            settings = VolumeSettings.DEFAULT,
            density = density,
            maxVolume = 15,
            touchSlopPx = 24,
        )

        // 396px bar / 15 levels / sensitivity 1.0
        assertEquals(26.4f, config.pixelsPerStep, 0.001f)
        assertEquals(285f, config.tapSplitY, 0.001f) // half the touch zone
        assertEquals(30f, config.touchSlopPx, 0.001f) // 10dp floor beats the 24px system slop
    }

    @Test
    fun `a large system touch slop is respected`() {
        val config = ControlSpecFactory.gestureConfig(
            settings = VolumeSettings.DEFAULT,
            density = density,
            maxVolume = 15,
            touchSlopPx = 120,
        )

        assertEquals(120f, config.touchSlopPx, 0.001f)
    }

    @Test
    fun `placement keeps the relative offset, not a coordinate`() {
        val placement = ControlSpecFactory.placement(
            VolumeSettings.DEFAULT.copy(offsetFraction = 0.42f),
            density,
        )

        assertEquals(0.42f, placement.offsetFraction, 0.001f)
        assertEquals(396f, placement.lengthPx, 0.001f)
        assertEquals(570f, placement.touchLengthPx, 0.001f)
    }
}
