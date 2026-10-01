package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.TapMode
import kotlin.math.abs
import kotlin.math.sign

/** Pixel-space tuning. The engine itself has no Android dependency. */
data class GestureConfig(
    val touchSlopPx: Float = 24f,
    val tapTimeoutMs: Long = 260L,
    val pixelsPerStep: Float = 24f,
    val tapMode: TapMode = TapMode.DEFAULT,
    val tapSplitY: Float = 200f,
    val maxStepsPerEvent: Int = 3,
)

interface GestureListener {
    fun onGestureStart()
    fun onStep(deltaSteps: Int, fromTap: Boolean)
    fun onGestureEnd()
}

enum class GesturePhase { IDLE, PENDING_TAP, DRAGGING }

/**
 * Relative, incremental dragging: touching never jumps to an absolute slider position.
 * The dead zone is consumed once. Reversals discard the previous fractional remainder,
 * and large events are capped AND rebased, so holding still or lifting cannot replay a
 * queued burst of volume changes. Cancel never generates a tap or a final step.
 */
class GestureEngine(
    private val configProvider: () -> GestureConfig,
    private val listener: GestureListener,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    var phase: GesturePhase = GesturePhase.IDLE
        private set
    private var downY = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var remainder = 0f
    private var direction = 0f

    fun onDown(y: Float, at: Long = clock()): Boolean {
        if (!y.isFinite()) return false
        onCancel()
        phase = GesturePhase.PENDING_TAP
        downY = y
        lastY = y
        downAt = at
        remainder = 0f
        direction = 0f
        listener.onGestureStart()
        return true
    }

    fun onMove(y: Float, at: Long = clock()) {
        if (phase == GesturePhase.IDLE) return
        if (!y.isFinite() || at < downAt) { onCancel(); return }
        val config = configProvider()
        val slop = config.touchSlopPx.coerceAtLeast(1f)
        if (phase == GesturePhase.PENDING_TAP) {
            val distance = downY - y
            if (abs(distance) <= slop) return
            phase = GesturePhase.DRAGGING
            lastY = downY - sign(distance) * slop
        }
        val distance = lastY - y
        lastY = y
        if (distance == 0f) return
        val nextDirection = sign(distance)
        if (direction != 0f && direction != nextDirection) remainder = 0f
        direction = nextDirection
        remainder += distance
        val pixels = config.pixelsPerStep.takeIf { it.isFinite() && it > 0f } ?: 24f
        val desired = (remainder / pixels).toInt()
        if (desired == 0) return
        remainder -= desired * pixels // Consume overflow too; no deferred jumps.
        val delta = desired.coerceIn(-config.maxStepsPerEvent.coerceAtLeast(1), config.maxStepsPerEvent.coerceAtLeast(1))
        listener.onStep(delta, fromTap = false)
    }

    /** tapPosition is along the ORIGINAL physical bar, independently of the drag axis. */
    fun onUp(y: Float, at: Long = clock(), tapPosition: Float = y) {
        if (phase == GesturePhase.IDLE) return
        onMove(y, at)
        if (phase == GesturePhase.IDLE) return
        val config = configProvider()
        if (phase == GesturePhase.PENDING_TAP && at - downAt in 0..config.tapTimeoutMs && tapPosition.isFinite()) {
            val delta = when (config.tapMode) {
                TapMode.ALWAYS_INCREASE -> 1
                TapMode.ALWAYS_DECREASE -> -1
                TapMode.SPLIT -> if (tapPosition <= config.tapSplitY) 1 else -1
            }
            listener.onStep(delta, fromTap = true)
        }
        finish()
    }

    fun onCancel() {
        if (phase != GesturePhase.IDLE) finish()
    }

    private fun finish() {
        phase = GesturePhase.IDLE
        remainder = 0f
        direction = 0f
        listener.onGestureEnd()
    }
}
