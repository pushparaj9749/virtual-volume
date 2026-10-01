package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.ScreenEdge
import kotlin.math.roundToInt

/** Visible screen area plus the insets that must not be covered, in pixels. */
data class ScreenBounds(
    val widthPx: Int,
    val heightPx: Int,
    val insetLeftPx: Int = 0,
    val insetRightPx: Int = 0,
    val insetTopPx: Int = 0,
    val insetBottomPx: Int = 0,
) {
    val usableHeightPx: Int get() = (heightPx - insetTopPx - insetBottomPx).coerceAtLeast(0)
    val usableWidthPx: Int get() = (widthPx - insetLeftPx - insetRightPx).coerceAtLeast(0)

    companion object {
        val EMPTY = ScreenBounds(0, 0)
    }
}

/** Where the user wants the control, expressed without any screen coordinates. */
data class ControlPlacement(
    val edge: ScreenEdge,
    val offsetFraction: Float,
    val lengthPx: Float,
    val thicknessPx: Float,
    val touchWidthPx: Float,
    val touchLengthPx: Float,
)

/**
 * A resolved window position.
 *
 * [xOffset] is the distance from the chosen edge and [yOffset] the distance from the top,
 * both measured inside the usable area. The platform layer turns [edge] into an *absolute*
 * `LEFT`/`RIGHT` gravity — deliberately not `START`/`END`, which would swap the sides in a
 * right-to-left locale after the user picked a physical edge. Because the offsets are
 * recomputed from the live bounds on every rotation, "right" stays right in portrait and in
 * both landscape orientations.
 */
data class WindowPlacement(
    val edge: ScreenEdge,
    val xOffset: Int,
    val yOffset: Int,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
) {
    val isValid: Boolean
        get() = windowWidthPx > 0 && windowHeightPx > 0 && xOffset >= 0 && yOffset >= 0
}

/**
 * Converts the user's relative placement into concrete window coordinates for the
 * *current* display configuration.
 *
 * Everything is derived from the live bounds and insets, never from a stored coordinate,
 * which is what makes rotation safe: the same [ControlPlacement] resolves to a different
 * (still correct) position in portrait and in each landscape orientation.
 */
object OverlayLayoutResolver {

    /** The touch zone never eats more than this share of the usable height. */
    const val MAX_TOUCH_LENGTH_RATIO = 0.72f

    /** Fallback when insets are unavailable: keep clear of the status bar area. */
    const val MIN_VERTICAL_MARGIN_DP = 8

    fun resolve(bounds: ScreenBounds, placement: ControlPlacement, density: Float): WindowPlacement {
        if (bounds.widthPx <= 0 || bounds.heightPx <= 0) {
            return WindowPlacement(placement.edge, 0, 0, 0, 0)
        }

        val margin = (MIN_VERTICAL_MARGIN_DP * density).roundToInt()
        val topEdge = (bounds.insetTopPx + margin).coerceAtMost(bounds.heightPx)
        val bottomEdge = (bounds.heightPx - bounds.insetBottomPx - margin).coerceAtLeast(topEdge)
        val usable = (bottomEdge - topEdge).coerceAtLeast(0)

        val maxTouchLength = (usable * MAX_TOUCH_LENGTH_RATIO).roundToInt()
        val touchLength = placement.touchLengthPx.roundToInt()
            .coerceIn(1, maxTouchLength.coerceAtLeast(1))

        val travel = (usable - touchLength).coerceAtLeast(0)
        val fraction = placement.offsetFraction.coerceIn(0f, 1f)
        val yOffset = topEdge + (travel * fraction).roundToInt()

        val xOffset = when (placement.edge) {
            ScreenEdge.LEFT -> bounds.insetLeftPx.coerceAtLeast(0)
            ScreenEdge.RIGHT -> bounds.insetRightPx.coerceAtLeast(0)
        }

        val touchWidth = placement.touchWidthPx.roundToInt().coerceIn(1, bounds.widthPx)

        return WindowPlacement(
            edge = placement.edge,
            xOffset = xOffset,
            yOffset = yOffset.coerceIn(0, (bounds.heightPx - touchLength).coerceAtLeast(0)),
            windowWidthPx = touchWidth,
            windowHeightPx = touchLength,
        )
    }
}
