package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.ScreenEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orientation handling is the part that silently breaks in overlay apps, so the resolver
 * is pinned against explicit portrait and both landscape geometries.
 *
 * Nothing here is a stored coordinate: the same [ControlPlacement] is resolved against
 * whatever the display reports *now*.
 */
class OverlayLayoutResolverTest {

    private val portrait = ScreenBounds(
        widthPx = 1080,
        heightPx = 2400,
        insetLeftPx = 0,
        insetRightPx = 0,
        insetTopPx = 100,
        insetBottomPx = 120,
    )

    // Rotated 90°: the cutout is now on the left.
    private val landscapeCutoutLeft = ScreenBounds(
        widthPx = 2400,
        heightPx = 1080,
        insetLeftPx = 100,
        insetRightPx = 0,
        insetTopPx = 0,
        insetBottomPx = 0,
    )

    // Rotated 270°: the cutout is now on the right.
    private val landscapeCutoutRight = ScreenBounds(
        widthPx = 2400,
        heightPx = 1080,
        insetLeftPx = 0,
        insetRightPx = 100,
        insetTopPx = 0,
        insetBottomPx = 0,
    )

    private val placement = ControlPlacement(
        edge = ScreenEdge.RIGHT,
        offsetFraction = 0.5f,
        lengthPx = 396f,
        thicknessPx = 15f,
        touchWidthPx = 120f,
        touchLengthPx = 600f,
    )

    @Test
    fun `portrait position sits inside the usable area`() {
        val result = OverlayLayoutResolver.resolve(portrait, placement, density = 3f)

        // margin 24, top edge 124, bottom edge 2256, usable 2132, travel 1532
        assertEquals(ScreenEdge.RIGHT, result.edge)
        assertEquals(0, result.xOffset)
        assertEquals(890, result.yOffset)
        assertEquals(120, result.windowWidthPx)
        assertEquals(600, result.windowHeightPx)
        assertTrue(result.isValid)
        assertTrue(result.yOffset + result.windowHeightPx <= portrait.heightPx - portrait.insetBottomPx)
    }

    @Test
    fun `offset zero hugs the top and offset one hugs the bottom of the usable area`() {
        val top = OverlayLayoutResolver.resolve(portrait, placement.copy(offsetFraction = 0f), 3f)
        val bottom = OverlayLayoutResolver.resolve(portrait, placement.copy(offsetFraction = 1f), 3f)

        assertEquals(124, top.yOffset)
        assertEquals(1656, bottom.yOffset)
        assertEquals(2256, bottom.yOffset + bottom.windowHeightPx)
    }

    @Test
    fun `the position is recomputed for landscape instead of reusing portrait coordinates`() {
        val portraitResult = OverlayLayoutResolver.resolve(portrait, placement, 3f)
        val landscapeResult = OverlayLayoutResolver.resolve(landscapeCutoutLeft, placement, 3f)

        // margin 24, usable 1032, travel 432 -> y = 24 + 216
        assertEquals(240, landscapeResult.yOffset)
        assertTrue(landscapeResult.yOffset != portraitResult.yOffset)
        assertTrue(landscapeResult.yOffset + landscapeResult.windowHeightPx <= landscapeCutoutLeft.heightPx)
    }

    @Test
    fun `both landscape rotations keep the control on the right and clear of the cutout`() {
        val cutoutLeft = OverlayLayoutResolver.resolve(landscapeCutoutLeft, placement, 3f)
        val cutoutRight = OverlayLayoutResolver.resolve(landscapeCutoutRight, placement, 3f)

        assertEquals(ScreenEdge.RIGHT, cutoutLeft.edge)
        assertEquals(ScreenEdge.RIGHT, cutoutRight.edge)
        assertEquals(0, cutoutLeft.xOffset)
        assertEquals(100, cutoutRight.xOffset)
        assertEquals(cutoutLeft.yOffset, cutoutRight.yOffset)
    }

    @Test
    fun `the left edge mirrors the right edge`() {
        val right = OverlayLayoutResolver.resolve(portrait, placement, 3f)
        val left = OverlayLayoutResolver.resolve(
            portrait.copy(insetLeftPx = 60, insetRightPx = 0),
            placement.copy(edge = ScreenEdge.LEFT),
            3f,
        )

        assertEquals(ScreenEdge.LEFT, left.edge)
        assertEquals(60, left.xOffset)
        assertEquals(right.yOffset, left.yOffset)
    }

    @Test
    fun `the relative offset survives a rotation`() {
        val fraction = 0.25f
        val expected = listOf(portrait, landscapeCutoutLeft, landscapeCutoutRight).map { bounds ->
            val result = OverlayLayoutResolver.resolve(
                bounds,
                placement.copy(offsetFraction = fraction),
                3f,
            )
            val margin = 24
            val topEdge = bounds.insetTopPx + margin
            val usable = bounds.heightPx - bounds.insetBottomPx - margin - topEdge
            val travel = usable - result.windowHeightPx
            (result.yOffset - topEdge).toFloat() / travel.toFloat()
        }

        expected.forEach { resolved ->
            assertEquals(fraction, resolved, 0.002f)
        }
    }

    @Test
    fun `the touch zone shrinks on a small screen instead of overflowing`() {
        val small = ScreenBounds(widthPx = 400, heightPx = 400)
        val result = OverlayLayoutResolver.resolve(
            small,
            placement.copy(offsetFraction = 1f),
            density = 2f,
        )

        // usable 368, max touch length 265 -> travel 103
        assertEquals(265, result.windowHeightPx)
        assertEquals(119, result.yOffset)
        assertEquals(384, result.yOffset + result.windowHeightPx)
        assertTrue(result.isValid)
    }

    @Test
    fun `an out of range offset is clamped rather than pushing the window off screen`() {
        val above = OverlayLayoutResolver.resolve(portrait, placement.copy(offsetFraction = 4f), 3f)
        val below = OverlayLayoutResolver.resolve(portrait, placement.copy(offsetFraction = -2f), 3f)

        assertEquals(1656, above.yOffset)
        assertEquals(124, below.yOffset)
    }

    @Test
    fun `unknown screen bounds produce an invalid placement`() {
        val result = OverlayLayoutResolver.resolve(ScreenBounds.EMPTY, placement, 3f)

        assertFalse(result.isValid)
        assertEquals(0, result.windowWidthPx)
    }
}
