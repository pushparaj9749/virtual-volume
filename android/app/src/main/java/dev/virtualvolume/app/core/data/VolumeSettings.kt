package dev.virtualvolume.app.core.data

/**
 * Every user-tunable value of the floating control.
 *
 * Position is stored as [offsetFraction] — a proportion of the usable height of the
 * *current* orientation — rather than as raw screen coordinates. That is what keeps the
 * control on the same relative spot after a rotation instead of drifting off screen.
 */
data class VolumeSettings(
    val enabled: Boolean = false,
    val startOnBoot: Boolean = true,
    val edge: ScreenEdge = ScreenEdge.DEFAULT,
    val offsetFraction: Float = 0.32f,
    val lengthDp: Float = 132f,
    val thicknessDp: Float = 5f,
    val idleOpacity: Float = 0.42f,
    val activeOpacity: Float = 1f,
    val touchZoneWidthDp: Float = 40f,
    val touchZoneLengthDp: Float = 190f,
    val swipeSensitivity: Float = 1f,
    val animationDurationMs: Long = 180L,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val volumeStep: Int = 1,
    val audioStream: VolumeStreamType = VolumeStreamType.DEFAULT,
    val tapMode: TapMode = TapMode.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val onboardingCompleted: Boolean = false,
) {

    /** Clamps every value into the supported range. Called on every write. */
    fun sanitized(): VolumeSettings = copy(
        offsetFraction = offsetFraction.coerceIn(MIN_OFFSET_FRACTION, MAX_OFFSET_FRACTION),
        lengthDp = lengthDp.coerceIn(MIN_LENGTH_DP, MAX_LENGTH_DP),
        thicknessDp = thicknessDp.coerceIn(MIN_THICKNESS_DP, MAX_THICKNESS_DP),
        // The control must never be fully transparent while idle: a control you cannot
        // find is a control you cannot use.
        idleOpacity = idleOpacity.coerceIn(MIN_IDLE_OPACITY, MAX_OPACITY),
        activeOpacity = activeOpacity.coerceIn(MIN_ACTIVE_OPACITY, MAX_OPACITY),
        touchZoneWidthDp = touchZoneWidthDp.coerceIn(MIN_TOUCH_WIDTH_DP, MAX_TOUCH_WIDTH_DP),
        touchZoneLengthDp = touchZoneLengthDp.coerceIn(MIN_TOUCH_LENGTH_DP, MAX_TOUCH_LENGTH_DP),
        swipeSensitivity = swipeSensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
        animationDurationMs = animationDurationMs.coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS),
        volumeStep = volumeStep.coerceIn(MIN_VOLUME_STEP, MAX_VOLUME_STEP),
    )

    companion object {
        const val MIN_OFFSET_FRACTION = 0f
        const val MAX_OFFSET_FRACTION = 1f

        const val MIN_LENGTH_DP = 64f
        const val MAX_LENGTH_DP = 280f

        const val MIN_THICKNESS_DP = 2f
        const val MAX_THICKNESS_DP = 12f

        const val MIN_IDLE_OPACITY = 0.12f
        const val MIN_ACTIVE_OPACITY = 0.4f
        const val MAX_OPACITY = 1f

        const val MIN_TOUCH_WIDTH_DP = 28f
        const val MAX_TOUCH_WIDTH_DP = 96f

        const val MIN_TOUCH_LENGTH_DP = 120f
        const val MAX_TOUCH_LENGTH_DP = 320f

        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 3f

        const val MIN_ANIMATION_MS = 60L
        const val MAX_ANIMATION_MS = 600L

        const val MIN_VOLUME_STEP = 1
        const val MAX_VOLUME_STEP = 5

        val DEFAULT = VolumeSettings()
    }
}
