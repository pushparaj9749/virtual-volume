package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.ScreenEdge
import org.junit.Assert.*
import org.junit.Test

class OverlayLayoutResolverTest {
    private val placement = ControlPlacement(ScreenEdge.RIGHT, 0.25f, 396f, 15f, 96f, 492f)
    private val portrait = ScreenBounds(1080, 2400, insetTopPx = 100, insetBottomPx = 120)
    private val landscapeLeft = ScreenBounds(2400, 1080, insetLeftPx = 100, insetRightPx = 120, rotation = DisplayRotation.LEFT)
    private val landscapeRight = ScreenBounds(2400, 1080, insetLeftPx = 120, insetRightPx = 100, rotation = DisplayRotation.RIGHT)

    @Test fun `physical right rotates to top left and bottom rather than following screen right`() {
        assertEquals(DisplayEdge.RIGHT, OverlayLayoutResolver.resolve(portrait, placement, 3f).edge)
        assertEquals(DisplayEdge.TOP, OverlayLayoutResolver.resolve(landscapeLeft, placement, 3f).edge)
        assertEquals(DisplayEdge.LEFT, OverlayLayoutResolver.resolve(portrait.copy(rotation = DisplayRotation.INVERTED), placement, 3f).edge)
        assertEquals(DisplayEdge.BOTTOM, OverlayLayoutResolver.resolve(landscapeRight, placement, 3f).edge)
    }

    @Test fun `relative physical offset survives both landscape rotations including reversed travel`() {
        val p = OverlayLayoutResolver.resolve(portrait, placement, 3f)
        val l = OverlayLayoutResolver.resolve(landscapeLeft, placement, 3f)
        val r = OverlayLayoutResolver.resolve(landscapeRight, placement, 3f)
        val travel = (2400 - 100 - 120 - 48 - 492).toFloat()
        assertEquals(0.25f, (p.yOffset - 124) / travel, 0.001f)
        assertEquals(0.25f, (l.xOffset - 124) / travel, 0.001f)
        assertEquals(0.25f, 1f - (r.xOffset - 144) / travel, 0.001f)
        assertEquals(0, l.yOffset)
        assertEquals(1080 - 96, r.yOffset)
    }

    @Test fun `the touch zone swaps dimensions along with the device`() {
        val p = OverlayLayoutResolver.resolve(portrait, placement, 3f)
        val l = OverlayLayoutResolver.resolve(landscapeLeft, placement, 3f)
        assertEquals(96, p.windowWidthPx)
        assertEquals(492, p.windowHeightPx)
        assertEquals(p.windowWidthPx, l.windowHeightPx)
        assertEquals(p.windowHeightPx, l.windowWidthPx)
    }

    @Test fun `left physical edge is opposite for every rotation and ignores locale`() {
        DisplayRotation.entries.forEach { rotation ->
            val right = OverlayLayoutResolver.edgeFor(ScreenEdge.RIGHT, rotation)
            val left = OverlayLayoutResolver.edgeFor(ScreenEdge.LEFT, rotation)
            assertEquals((right.ordinal + 2) % 4, left.ordinal)
        }
    }

    @Test fun `every combination stays inside system bars and cutouts`() {
        listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT).forEach { edge ->
            DisplayRotation.entries.forEach { rotation ->
                listOf(0f, 0.32f, 1f, -9f, 99f, Float.NaN).forEach { offset ->
                    val bounds = ScreenBounds(1440, 900, 85, 40, 30, 90, rotation)
                    val r = OverlayLayoutResolver.resolve(bounds, placement.copy(edge = edge, offsetFraction = offset), 2f)
                    assertTrue(r.isValid)
                    assertTrue(r.xOffset >= 85)
                    assertTrue(r.yOffset >= 30)
                    assertTrue(r.xOffset + r.windowWidthPx <= 1400)
                    assertTrue(r.yOffset + r.windowHeightPx <= 810)
                }
            }
        }
    }

    @Test fun `small displays shrink instead of creating a full screen touch sheet`() {
        val r = OverlayLayoutResolver.resolve(ScreenBounds(200, 200), placement, 2f)
        assertTrue(r.isValid)
        assertTrue(r.windowHeightPx <= 200 * OverlayLayoutResolver.MAX_TOUCH_LENGTH_RATIO)
        assertTrue(r.xOffset + r.windowWidthPx <= 200)
        assertTrue(r.yOffset + r.windowHeightPx <= 200)
    }

    @Test fun `empty or fully inset screens are not valid windows`() {
        assertFalse(OverlayLayoutResolver.resolve(ScreenBounds.EMPTY, placement, 3f).isValid)
        assertFalse(OverlayLayoutResolver.resolve(ScreenBounds(200, 200, insetTopPx = 200), placement, 3f).isValid)
    }

    @Test fun `screen rotation constants map deterministically`() {
        assertEquals(DisplayRotation.LEFT, DisplayRotation.fromSurface(1))
        assertEquals(DisplayRotation.RIGHT, DisplayRotation.fromSurface(3))
        assertEquals(DisplayRotation.NATURAL, DisplayRotation.fromSurface(999))
    }
}
