package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType

/** A volume reading for a stream. [index] is `-1` when the stream could not be read. */
data class VolumeState(
    val stream: VolumeStreamType,
    val index: Int,
    val max: Int,
) {
    val fraction: Float
        get() = if (max <= 0) 0f else (index.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    companion object {
        val UNKNOWN = VolumeState(VolumeStreamType.DEFAULT, index = -1, max = 0)
    }
}

/** Outcome of a volume change attempt. */
sealed interface VolumeResult {
    data class Success(val state: VolumeState) : VolumeResult
    data class Failure(val message: String) : VolumeResult
}

/**
 * Range arithmetic for hardware volume indexes.
 *
 * Every value handed to [android.media.AudioManager] passes through here first so the app
 * can never request an index outside the device's real range.
 */
object VolumeMath {

    fun clamp(index: Int, max: Int, min: Int = 0): Int = index.coerceIn(min, max)

    /** Applies a signed step delta and clamps to the device range. */
    fun applyStep(current: Int, deltaSteps: Int, stepSize: Int, max: Int, min: Int = 0): Int =
        clamp(current + deltaSteps * stepSize, max, min)

    /**
     * How many pixels of drag equal one volume step.
     *
     * At sensitivity 1.0 dragging the full length of the control sweeps the whole volume
     * range, which makes the bar behave like a vertical slider you can feel.
     */
    fun pixelsPerStep(controlLengthPx: Float, maxVolume: Int, sensitivity: Float): Float {
        if (controlLengthPx <= 0f || maxVolume <= 0) return DEFAULT_PIXELS_PER_STEP
        val safeSensitivity = sensitivity.coerceAtLeast(0.1f)
        val raw = (controlLengthPx / maxVolume) / safeSensitivity
        return raw.coerceIn(MIN_PIXELS_PER_STEP, MAX_PIXELS_PER_STEP)
    }

    const val DEFAULT_PIXELS_PER_STEP = 24f
    const val MIN_PIXELS_PER_STEP = 6f
    const val MAX_PIXELS_PER_STEP = 220f
}
