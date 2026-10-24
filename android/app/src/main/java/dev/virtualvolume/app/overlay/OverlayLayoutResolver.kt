package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.ScreenEdge
import kotlin.math.roundToInt

/** Rotation from the display's natural orientation, matching Android's Surface constants. */
enum class DisplayRotation(val quarterTurns: Int) {
    NATURAL(0), LEFT(1), INVERTED(2), RIGHT(3);

    val isHorizontal: Boolean get() = quarterTurns % 2 == 1

    companion object {
        fun fromSurface(value: Int): DisplayRotation = entries.firstOrNull { it.quarterTurns == value } ?: NATURAL
    }
}

enum class DisplayEdge { LEFT, TOP, RIGHT, BOTTOM }

/** Live display bounds. Insets are in the CURRENT orientation, not the stored one. */
data class ScreenBounds(
    val widthPx: Int,
    val heightPx: Int,
    val insetLeftPx: Int = 0,
    val insetRightPx: Int = 0,
    val insetTopPx: Int = 0,
    val insetBottomPx: Int = 0,
    val rotation: DisplayRotation = DisplayRotation.NATURAL,
) {
    val usableHeightPx: Int get() = (heightPx - insetTopPx - insetBottomPx).coerceAtLeast(0)
    val usableWidthPx: Int get() = (widthPx - insetLeftPx - insetRightPx).coerceAtLeast(0)

    companion object { val EMPTY = ScreenBounds(0, 0) }
}

/** Edge and offset are relative to the device's NATURAL orientation (usually portrait). */
data class ControlPlacement(
    val edge: ScreenEdge,
    val offsetFraction: Float,
    val lengthPx: Float,
    val thicknessPx: Float,
    val touchWidthPx: Float,
    val touchLengthPx: Float,
)

/** Absolute TOP | LEFT window coordinates, resolved afresh after every rotation. */
data class WindowPlacement(
    val edge: DisplayEdge,
    val xOffset: Int,
    val yOffset: Int,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val rotation: DisplayRotation = DisplayRotation.NATURAL,
) {
    val isValid: Boolean get() = windowWidthPx > 0 && windowHeightPx > 0 && xOffset >= 0 && yOffset >= 0
}

/** Pure physical-edge transform, independent of layout direction or Android classes. */
object OverlayLayoutResolver {
    const val MAX_TOUCH_LENGTH_RATIO = 0.72f
    const val MIN_VERTICAL_MARGIN_DP = 8

    fun edgeFor(edge: ScreenEdge, rotation: DisplayRotation): DisplayEdge {
        val right = when (rotation) {
            DisplayRotation.NATURAL -> DisplayEdge.RIGHT
            DisplayRotation.LEFT -> DisplayEdge.TOP
            DisplayRotation.INVERTED -> DisplayEdge.LEFT
            DisplayRotation.RIGHT -> DisplayEdge.BOTTOM
        }
        if (edge == ScreenEdge.RIGHT) return right
        return when (right) {
            DisplayEdge.RIGHT -> DisplayEdge.LEFT
            DisplayEdge.LEFT -> DisplayEdge.RIGHT
            DisplayEdge.TOP -> DisplayEdge.BOTTOM
            DisplayEdge.BOTTOM -> DisplayEdge.TOP
        }
    }

    fun resolve(bounds: ScreenBounds, placement: ControlPlacement, density: Float): WindowPlacement {
        val edge = edgeFor(placement.edge, bounds.rotation)
        fun invalid() = WindowPlacement(edge, 0, 0, 0, 0, bounds.rotation)
        if (bounds.widthPx <= 0 || bounds.heightPx <= 0 || !density.isFinite() || density <= 0f) return invalid()
        if (!placement.touchWidthPx.isFinite() || !placement.touchLengthPx.isFinite()) return invalid()

        val left = bounds.insetLeftPx.coerceIn(0, bounds.widthPx)
        val right = (bounds.widthPx - bounds.insetRightPx.coerceAtLeast(0)).coerceIn(left, bounds.widthPx)
        val top = bounds.insetTopPx.coerceIn(0, bounds.heightPx)
        val bottom = (bounds.heightPx - bounds.insetBottomPx.coerceAtLeast(0)).coerceIn(top, bounds.heightPx)
        val horizontal = bounds.rotation.isHorizontal
        val alongAvailable = if (horizontal) right - left else bottom - top
        val acrossAvailable = if (horizontal) bottom - top else right - left
        val margin = (MIN_VERTICAL_MARGIN_DP * density).roundToInt().coerceAtLeast(0)
        val along = alongAvailable - margin * 2
        if (along <= 0 || acrossAvailable <= 0) return invalid()

        val length = placement.touchLengthPx.roundToInt().coerceIn(1, (along * MAX_TOUCH_LENGTH_RATIO).roundToInt().coerceAtLeast(1))
        val thickness = placement.touchWidthPx.roundToInt().coerceIn(1, acrossAvailable)
        val naturalFraction = placement.offsetFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.32f
        // Natural top points down/right at 0/90 and up/left at 180/270.
        val fraction = if (bounds.rotation.quarterTurns >= 2) 1f - naturalFraction else naturalFraction
        val offset = margin + ((along - length) * fraction).roundToInt()

        val x = when (edge) {
            DisplayEdge.LEFT -> left
            DisplayEdge.RIGHT -> right - thickness
            else -> left + offset
        }
        val y = when (edge) {
            DisplayEdge.TOP -> top
            DisplayEdge.BOTTOM -> bottom - thickness
            else -> top + offset
        }
        return WindowPlacement(edge, x, y, if (horizontal) length else thickness,
            if (horizontal) thickness else length, bounds.rotation)
    }
}
