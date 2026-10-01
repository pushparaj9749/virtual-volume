package dev.virtualvolume.app.overlay

import android.os.SystemClock
import dev.virtualvolume.app.core.data.TapMode
import kotlin.math.abs

/** Tuning values for one control. All distances in pixels. */
data class GestureConfig(
    /** Movement below this is treated as a finger resting, not a swipe. */
    val touchSlopPx: Float = 24f,
    /** A press shorter than this, without leaving the slop, is a tap. */
    val tapTimeoutMs: Long = 240L,
    /** Pixels of drag that equal one volume step. */
    val pixelsPerStep: Float = 24f,
    val tapMode: TapMode = TapMode.DEFAULT,
    /** Y coordinate separating "raise" from "lower" for split taps. */
    val tapSplitY: Float = 200f,
    /** Cap applied to a single event so a fast fling cannot jump many levels at once. */
    val maxStepsPerEvent: Int = 3,
)

interface GestureListener {
    /** Finger went down: the control lights up. */
    fun onGestureStart()

    /**
     * [deltaSteps] is positive to raise, negative to lower.
     * [fromTap] is true for a tap, false for a drag — callers use it to decide whether
     * the resulting volume change should animate.
     */
    fun onStep(deltaSteps: Int, fromTap: Boolean)

    /** Finger left or the gesture was cancelled: the control dims again. */
    fun onGestureEnd()
}

enum class GesturePhase { IDLE, PENDING_TAP, DRAGGING }

/**
 * Translates raw touch coordinates into discrete volume steps.
 *
 * Deliberately free of `MotionEvent` so the whole gesture contract is covered by JVM
 * unit tests: the overlay view only forwards `y` and timestamps.
 *
 * Three protections keep a pocket or a resting thumb from changing the volume:
 *  - a touch-slop dead zone before a press becomes a drag,
 *  - the slop is *consumed* rather than counted, so crossing it never jumps a step,
 *  - a per-event step cap, so a fast fling ramps instead of teleporting.
 *
 * Steps are tracked as an absolute position ([committedSteps]) so dragging back down
 * lowers the volume again, and the anchor is re-based on reversal so the response to a
 * change of direction is immediate instead of having to unwind first.
 */
class GestureEngine(
    private val configProvider: () -> GestureConfig,
    private val listener: GestureListener,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {

    var phase: GesturePhase = GesturePhase.IDLE
        private set

    private var anchorY = 0f
    private var downY = 0f
    private var downAt = 0L
    private var committedSteps = 0
    private var direction = 0

    fun onDown(y: Float, at: Long = clock()): Boolean {
        phase = GesturePhase.PENDING_TAP
        anchorY = y
        downY = y
        downAt = at
        committedSteps = 0
        direction = 0
        listener.onGestureStart()
        return true
    }

    fun onMove(y: Float, at: Long = clock()) {
        if (phase == GesturePhase.IDLE) return
        val config = configProvider()
        val fromDown = downY - y // positive = finger moved up

        if (phase == GesturePhase.PENDING_TAP) {
            if (abs(fromDown) < config.touchSlopPx) return
            // Commit to a drag. The slop itself is discounted rather than discarded, so a
            // deliberate fast swipe still lands where the finger went while a resting
            // thumb or a pocket never crosses the threshold at all.
            phase = GesturePhase.DRAGGING
            val sign = if (fromDown > 0) 1 else -1
            direction = sign
            committedSteps = 0
            anchorY = downY - sign * config.touchSlopPx
        }

        emit(forRaw(y, anchorY - y, config))
    }

    fun onUp(y: Float, at: Long = clock()) {
        if (phase == GesturePhase.IDLE) return
        val config = configProvider()

        when (phase) {
            GesturePhase.PENDING_TAP -> {
                if (at - downAt <= config.tapTimeoutMs) {
                    listener.onStep(tapDirection(y, config), fromTap = true)
                }
            }

            GesturePhase.DRAGGING -> {
                // Catch up anything the per-event cap held back during a fast swipe.
                emit(forRaw(y, anchorY - y, config))
            }

            GesturePhase.IDLE -> Unit
        }

        reset()
        listener.onGestureEnd()
    }

    fun onCancel() {
        if (phase == GesturePhase.IDLE) return
        reset()
        listener.onGestureEnd()
    }

    private fun emit(delta: Int) {
        if (delta == 0) return
        committedSteps += delta
        listener.onStep(delta, fromTap = false)
    }

    private fun forRaw(y: Float, raw: Float, config: GestureConfig): Int {
        val currentDirection = when {
            raw > 0f -> 1
            raw < 0f -> -1
            else -> 0
        }
        if (currentDirection != 0 && direction != 0 && currentDirection != direction) {
            // Reversal: re-base so the new direction responds immediately.
            direction = currentDirection
            committedSteps = 0
            anchorY = y
            return 0
        }
        if (currentDirection != 0) direction = currentDirection

        val desired = (raw / config.pixelsPerStep).toInt() // truncation = symmetric dead zone
        val delta = desired - committedSteps
        val capped = config.maxStepsPerEvent.coerceAtLeast(1)
        return delta.coerceIn(-capped, capped)
    }

    private fun tapDirection(y: Float, config: GestureConfig): Int = when (config.tapMode) {
        TapMode.ALWAYS_INCREASE -> 1
        TapMode.ALWAYS_DECREASE -> -1
        TapMode.SPLIT -> if (y <= config.tapSplitY) 1 else -1
    }

    private fun reset() {
        phase = GesturePhase.IDLE
        anchorY = 0f
        downY = 0f
        downAt = 0L
        committedSteps = 0
        direction = 0
    }
}
