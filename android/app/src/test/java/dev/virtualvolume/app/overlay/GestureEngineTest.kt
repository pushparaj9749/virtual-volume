package dev.virtualvolume.app.overlay

import dev.virtualvolume.app.core.data.TapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture contract is the product: taps, swipes, dead zones and caps are all pinned
 * here so a change to the engine cannot silently change how the rocker feels.
 */
class GestureEngineTest {

    private class RecordingListener : GestureListener {
        val steps = mutableListOf<Int>()
        var starts = 0
        var ends = 0
        var lastFromTap: Boolean? = null

        override fun onGestureStart() {
            starts += 1
        }

        override fun onStep(deltaSteps: Int, fromTap: Boolean) {
            steps += deltaSteps
            lastFromTap = fromTap
        }

        override fun onGestureEnd() {
            ends += 1
        }
    }

    private val config = GestureConfig(
        touchSlopPx = 20f,
        tapTimeoutMs = 240L,
        pixelsPerStep = 20f,
        tapMode = TapMode.SPLIT,
        tapSplitY = 100f,
        maxStepsPerEvent = 3,
    )

    private fun engine(listener: GestureListener, clock: () -> Long) =
        GestureEngine(configProvider = { config }, listener = listener, clock = clock)

    @Test
    fun `tap on the upper half raises one step`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 40f, at = 0L)
        engine.onUp(y = 40f, at = 120L)

        assertEquals(listOf(1), listener.steps)
        assertEquals(true, listener.lastFromTap)
        assertEquals(1, listener.starts)
        assertEquals(1, listener.ends)
    }

    @Test
    fun `tap on the lower half lowers one step`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 160f, at = 0L)
        engine.onUp(y = 160f, at = 120L)

        assertEquals(listOf(-1), listener.steps)
    }

    @Test
    fun `a long press without movement changes nothing`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 160f, at = 0L)
        engine.onUp(y = 160f, at = 900L)

        assertTrue(listener.steps.isEmpty())
        assertEquals(1, listener.ends)
    }

    @Test
    fun `movement inside the slop still counts as a tap`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 90f, at = 0L)
        engine.onMove(y = 105f, at = 40L) // 15px, below the 20px slop
        engine.onUp(y = 105f, at = 90L)

        // 105 is below the split, so this is a "lower" tap, not a drag.
        assertEquals(listOf(-1), listener.steps)
        assertEquals(GesturePhase.IDLE, engine.phase)
    }

    @Test
    fun `dragging up by three step lengths raises three steps`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 200f, at = 0L)
        engine.onMove(y = 178f, at = 16L) // crosses the slop, slop is discounted
        assertEquals(listOf<Int>(), listener.steps)
        engine.onMove(y = 120f, at = 32L) // 180 - 120 = 60px = 3 steps
        engine.onUp(y = 120f, at = 48L)

        assertEquals(listOf(3), listener.steps)
    }

    @Test
    fun `dragging down lowers by the same distance`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 100f, at = 0L)
        engine.onMove(y = 122f, at = 16L)
        engine.onMove(y = 180f, at = 32L)
        engine.onUp(y = 180f, at = 48L)

        assertEquals(listOf(-3), listener.steps)
    }

    @Test
    fun `a fast swipe is capped and lifting cannot replay a volume jump`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 300f, at = 0L)
        engine.onMove(y = 100f, at = 16L) // (300 - 20) - 100 = 180px = 9 steps wanted
        assertEquals(listOf(3), listener.steps)
        engine.onUp(y = 100f, at = 32L)
        assertEquals(listOf(3), listener.steps)
    }

    @Test
    fun `reversing direction re-anchors instead of unwinding`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 200f, at = 0L)
        engine.onMove(y = 140f, at = 16L) // up: 40px past the slop = 2 steps
        assertEquals(listOf(2), listener.steps)

        engine.onMove(y = 160f, at = 32L) // still above the anchor: gives one step back
        assertEquals(listOf(2, -1), listener.steps)

        engine.onMove(y = 220f, at = 48L) // continued down: 60px = 3 steps
        assertEquals(listOf(2, -1, -3), listener.steps)

        engine.onMove(y = 260f, at = 64L)
        assertEquals(listOf(2, -1, -3, -2), listener.steps)
    }

    @Test
    fun `cancel emits no steps but does end the gesture`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onDown(y = 200f, at = 0L)
        engine.onMove(y = 120f, at = 16L)
        listener.steps.clear()
        engine.onCancel()

        assertTrue(listener.steps.isEmpty())
        assertEquals(1, listener.ends)
        assertEquals(GesturePhase.IDLE, engine.phase)
    }

    @Test
    fun `always-raise tap mode ignores the tap position`() {
        val alwaysRaise = config.copy(tapMode = TapMode.ALWAYS_INCREASE)
        val listener = RecordingListener()
        val engine = GestureEngine({ alwaysRaise }, listener) { 0L }

        engine.onDown(y = 500f, at = 0L)
        engine.onUp(y = 500f, at = 60L)

        assertEquals(listOf(1), listener.steps)
    }

    @Test
    fun `a fast swipe with no move event is not mistaken for a tap`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }
        engine.onDown(200f, 0L)
        engine.onUp(100f, 100L)
        assertEquals(false, listener.lastFromTap)
        assertEquals(listOf(3), listener.steps)
    }

    @Test
    fun `holding still after a capped swipe does not keep increasing`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }
        engine.onDown(300f, 0L)
        engine.onMove(0f, 16L)
        repeat(10) { engine.onMove(0f, 32L + it) }
        engine.onUp(0f, 200L)
        assertEquals(listOf(3), listener.steps)
    }

    @Test
    fun `reversal at a limit responds without unwinding a long drag`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }
        engine.onDown(500f, 0L)
        engine.onMove(200f, 16L)
        engine.onMove(220f, 32L)
        assertEquals(listOf(3, -1), listener.steps)
    }

    @Test
    fun `split taps can use a rotated axis independently of screen up down`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }
        engine.onDown(0f, 0L)
        engine.onUp(0f, 100L, tapPosition = 170f)
        assertEquals(listOf(-1), listener.steps)
    }

    @Test
    fun `invalid coordinates cancel instead of sending bogus steps`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }
        engine.onDown(200f, 0L)
        engine.onMove(Float.NaN, 10L)
        engine.onUp(200f, 20L)
        assertTrue(listener.steps.isEmpty())
        assertEquals(1, listener.ends)
    }

    @Test
    fun `events outside a gesture are ignored`() {
        val listener = RecordingListener()
        val engine = engine(listener) { 0L }

        engine.onMove(y = 10f, at = 0L)
        engine.onUp(y = 10f, at = 10L)
        engine.onCancel()

        assertTrue(listener.steps.isEmpty())
        assertEquals(0, listener.starts)
        assertEquals(0, listener.ends)
    }
}
