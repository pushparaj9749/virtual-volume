package dev.virtualvolume.app.overlay

/**
 * Everything the floating control needs to draw itself.
 *
 * Expressed in pixels and plain colours so the same style object can drive the overlay
 * window and the in-app preview without either of them knowing about Compose or dp.
 */
data class ControlStyle(
    val lengthPx: Float,
    val thicknessPx: Float,
    val idleAlpha: Float,
    val activeAlpha: Float,
    val touchWidthPx: Float,
    val touchLengthPx: Float,
    val animationDurationMs: Long,
    val animationsEnabled: Boolean,
    val trackColor: Int = DEFAULT_TRACK_COLOR,
    val fillColor: Int = DEFAULT_FILL_COLOR,
    val glowColor: Int = DEFAULT_GLOW_COLOR,
) {
    /** Alpha the control shows while nobody is touching it. Never zero. */
    val effectiveIdleAlpha: Float get() = idleAlpha.coerceIn(MIN_ALPHA, 1f)
    val effectiveActiveAlpha: Float get() = activeAlpha.coerceIn(MIN_ALPHA, 1f)

    companion object {
        const val MIN_ALPHA = 0.08f

        const val DEFAULT_TRACK_COLOR = 0x33FFFFFF.toInt()
        const val DEFAULT_FILL_COLOR = 0xFFB9E6C9.toInt()
        const val DEFAULT_GLOW_COLOR = 0xFFB9E6C9.toInt()
    }
}
