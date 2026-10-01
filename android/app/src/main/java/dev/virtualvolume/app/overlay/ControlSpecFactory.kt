package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.audio.VolumeMath
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.data.VolumeSettings.Companion.MAX_OPACITY
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns persisted settings into the pixel-space objects the overlay needs.
 *
 * Pure so the dp → px maths (and the rule that the touch zone always contains the bar
 * plus its glow) is covered by unit tests.
 */
object ControlSpecFactory {

    fun style(settings: VolumeSettings, density: Float): ControlStyle {
        val lengthPx = settings.lengthDp * density
        val thicknessPx = settings.thicknessDp * density
        return ControlStyle(
            lengthPx = lengthPx,
            thicknessPx = thicknessPx,
            idleAlpha = settings.idleOpacity.coerceIn(ControlStyle.MIN_ALPHA, MAX_OPACITY),
            activeAlpha = settings.activeOpacity.coerceIn(ControlStyle.MIN_ALPHA, MAX_OPACITY),
            touchWidthPx = touchWidthPx(settings, thicknessPx, density),
            touchLengthPx = touchLengthPx(settings, lengthPx, thicknessPx, density),
            animationDurationMs = settings.animationDurationMs,
            animationsEnabled = settings.animationsEnabled,
        )
    }

    fun placement(settings: VolumeSettings, density: Float): ControlPlacement {
        val lengthPx = settings.lengthDp * density
        val thicknessPx = settings.thicknessDp * density
        return ControlPlacement(
            edge = settings.edge,
            offsetFraction = settings.offsetFraction,
            lengthPx = lengthPx,
            thicknessPx = thicknessPx,
            touchWidthPx = touchWidthPx(settings, thicknessPx, density),
            touchLengthPx = touchLengthPx(settings, lengthPx, thicknessPx, density),
        )
    }

    fun gestureConfig(
        settings: VolumeSettings,
        density: Float,
        maxVolume: Int,
        touchSlopPx: Int,
    ): GestureConfig {
        val lengthPx = settings.lengthDp * density
        val thicknessPx = settings.thicknessDp * density
        val slop = max(touchSlopPx.toFloat(), MIN_TOUCH_SLOP_DP * density)
        return GestureConfig(
            touchSlopPx = slop,
            pixelsPerStep = VolumeMath.pixelsPerStep(
                controlLengthPx = lengthPx,
                maxVolume = maxVolume,
                sensitivity = settings.swipeSensitivity,
            ),
            tapMode = settings.tapMode,
            tapSplitY = touchLengthPx(settings, lengthPx, thicknessPx, density) / 2f,
        )
    }

    /** The touch zone is always wide enough for the bar plus its widest glow ring. */
    fun touchWidthPx(settings: VolumeSettings, thicknessPx: Float, density: Float): Float {
        val padding = contentPaddingPx(thicknessPx)
        return max(settings.touchZoneWidthDp * density, thicknessPx + padding * 2f)
    }

    /** ... and always tall enough that the bar is never clipped. */
    fun touchLengthPx(
        settings: VolumeSettings,
        lengthPx: Float,
        thicknessPx: Float,
        density: Float,
    ): Float {
        val padding = contentPaddingPx(thicknessPx)
        return max(settings.touchZoneLengthDp * density, lengthPx + padding * 2f)
    }

    private fun contentPaddingPx(thicknessPx: Float): Float =
        max((thicknessPx * ControlPainter.GLOW_SPREAD_MULTIPLIER).roundToInt().toFloat(),
            ControlPainter.MIN_CONTENT_PADDING.toFloat())

    private const val MIN_TOUCH_SLOP_DP = 10f
}
