package dev.virtualvolume.app.core.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Restrained haptic punctuation for the floating control.
 *
 * A single short tick per volume step is enough to feel like a detent on a physical
 * rocker; anything more turns into noise, so this class exposes only short effects and
 * drops repeats that arrive faster than [MIN_TICK_INTERVAL_MS].
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = resolveVibrator(context)

    private var lastTickAt = 0L

    fun tick(now: Long = System.currentTimeMillis()) {
        if (now - lastTickAt < MIN_TICK_INTERVAL_MS) return
        lastTickAt = now
        play(VibrationEffects.tick())
    }

    fun confirm() = play(VibrationEffects.confirm())

    private fun play(effect: VibrationEffect) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching { device.vibrate(effect) }
            .onFailure { Log.w(TAG, "Haptic feedback unavailable", it) }
    }

    companion object {
        private const val TAG = "Haptics"
        private const val MIN_TICK_INTERVAL_MS = 35L

        private fun resolveVibrator(context: Context): Vibrator? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }
}

/** Effect factory kept separate so the timing constants stay reviewable. */
internal object VibrationEffects {

    fun tick(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneOff(0L, TICK_LENGTH_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    fun confirm(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneOff(0L, CONFIRM_LENGTH_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private const val TICK_LENGTH_MS = 12L
    private const val CONFIRM_LENGTH_MS = 22L
}
