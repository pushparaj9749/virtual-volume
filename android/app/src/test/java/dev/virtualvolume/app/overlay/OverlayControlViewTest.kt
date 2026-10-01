package dev.virtualvolume.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.virtualvolume.app.core.data.TapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real [OverlayControlView] with real motion events.
 *
 * This is the seam between the pure gesture engine and the window that actually receives
 * touches, so it is the one place where both halves are exercised together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayControlViewTest {

    private class Recorder : GestureListener {
        val steps = mutableListOf<Int>()
        var starts = 0
        var ends = 0

        override fun onGestureStart() {
            starts += 1
        }

        override fun onStep(deltaSteps: Int, fromTap: Boolean) {
            steps += deltaSteps
        }

        override fun onGestureEnd() {
            ends += 1
        }
    }

    private val style = ControlStyle(
        lengthPx = 300f,
        thicknessPx = 15f,
        idleAlpha = 0.4f,
        activeAlpha = 1f,
        touchWidthPx = 120f,
        touchLengthPx = 600f,
        animationDurationMs = 0L,
        animationsEnabled = false,
    )

    private val config = GestureConfig(
        touchSlopPx = 20f,
        tapTimeoutMs = 240L,
        pixelsPerStep = 20f,
        tapMode = TapMode.SPLIT,
        tapSplitY = 300f,
    )

    private fun buildView(listener: GestureListener): OverlayControlView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return OverlayControlView(context).apply {
            this.style = this@OverlayControlViewTest.style
            gestureConfig = config
            this.listener = listener
            measure(
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 120, 600)
        }
    }

    private fun down(x: Float, y: Float, time: Long) =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun move(x: Float, y: Float, time: Long) =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, x, y, 0)

    private fun up(x: Float, y: Float, time: Long) =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, x, y, 0)

    @Test
    fun `a swipe up on the view raises the volume in steps`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.dispatchTouchEvent(down(60f, 400f, 0L))
        view.dispatchTouchEvent(move(60f, 300f, 16L))
        view.dispatchTouchEvent(up(60f, 300f, 32L))

        // The view must forward the drag into positive volume steps. Exact per-event
        // capping is pinned in GestureEngineTest; here we prove the wiring end to end.
        assertTrue("swipe up must raise the volume", recorder.steps.sum() > 0)
        assertTrue(recorder.steps.all { it > 0 })
        assertEquals(1, recorder.starts)
        assertEquals(1, recorder.ends)
    }

    @Test
    fun `a swipe down on the view lowers the volume`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.dispatchTouchEvent(down(60f, 200f, 0L))
        view.dispatchTouchEvent(move(60f, 300f, 16L))
        view.dispatchTouchEvent(up(60f, 300f, 32L))

        assertTrue(recorder.steps.sum() < 0)
    }

    @Test
    fun `a tap on the upper half raises by one step`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.dispatchTouchEvent(down(60f, 120f, 0L))
        view.dispatchTouchEvent(up(60f, 120f, 90L))

        assertEquals(listOf(1), recorder.steps)
    }

    @Test
    fun `a tap on the lower half lowers by one step`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.dispatchTouchEvent(down(60f, 500f, 0L))
        view.dispatchTouchEvent(up(60f, 500f, 90L))

        assertEquals(listOf(-1), recorder.steps)
    }

    @Test
    fun `a cancelled gesture leaves the volume alone`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.dispatchTouchEvent(down(60f, 400f, 0L))
        view.dispatchTouchEvent(move(60f, 300f, 16L))
        val stepsBeforeCancel = recorder.steps.size

        view.dispatchTouchEvent(
            MotionEvent.obtain(32L, 32L, MotionEvent.ACTION_CANCEL, 60f, 300f, 0),
        )

        // A cancelled gesture must not add any further volume steps.
        assertEquals(stepsBeforeCancel, recorder.steps.size)
        assertEquals(1, recorder.ends)
    }

    @Test
    fun `an accessibility click raises one step`() {
        val recorder = Recorder()
        val view = buildView(recorder)

        view.performClick()

        assertEquals(listOf(1), recorder.steps)
    }

    @Test
    fun `the control draws at every level without failing`() {
        val recorder = Recorder()
        val view = buildView(recorder)
        val bitmap = Bitmap.createBitmap(120, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        listOf(0f, 0.05f, 0.5f, 1f).forEach { level ->
            view.setLevel(level, animate = false)
            view.draw(canvas)
        }

        // Active state draws the glow rings and the handle marker.
        view.dispatchTouchEvent(down(60f, 300f, 0L))
        view.draw(canvas)
        view.dispatchTouchEvent(up(60f, 300f, 400L))
        view.draw(canvas)

        assertTrue(true)
    }
}
