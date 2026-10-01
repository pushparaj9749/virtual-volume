package dev.virtualvolume.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * The floating volume rocker itself: a thin bar that brightens while it is touched.
 *
 * The view owns no policy. It draws whatever [ControlStyle] describes, forwards raw
 * `y` coordinates to [GestureEngine], and reports steps through [listener]. Keeping it
 * this thin is what lets the onboarding tutorial and the settings preview reuse the exact
 * same drawing and gesture code.
 */
class OverlayControlView(context: Context) : View(context) {

    /** Values the gesture engine works with; replaced whenever settings change. */
    var gestureConfig: GestureConfig = GestureConfig()

    var style: ControlStyle = DEFAULT_STYLE
        set(value) {
            field = value
            painter.update(value)
            if (!value.animationsEnabled) {
                cancelAnimators()
                activeProgress = if (isActive) 1f else 0f
                displayedLevel = targetLevel
            }
            invalidate()
        }

    /** Receives the interpreted gestures. */
    var listener: GestureListener? = null

    var isActive: Boolean = false
        private set

    private val painter = ControlPainter(style)
    private val barRect = RectF()

    private var activeProgress = 0f
    private var displayedLevel = 0f
    private var targetLevel = 0f

    private var activeAnimator: ValueAnimator? = null
    private var levelAnimator: ValueAnimator? = null

    private val engine = GestureEngine(
        configProvider = { gestureConfig },
        listener = object : GestureListener {
            // The visual state is updated first, then the event is forwarded, so a
            // listener that reads back into this view always sees a consistent state.
            // Forwarding is what lets the service re-read the real system volume the
            // instant the finger lands, so the first drag step is never computed from a
            // level that went stale while the control was idle.
            override fun onGestureStart() {
                setActive(true)
                listener?.onGestureStart()
            }

            override fun onStep(deltaSteps: Int, fromTap: Boolean) {
                listener?.onStep(deltaSteps, fromTap)
            }

            override fun onGestureEnd() {
                setActive(false)
                listener?.onGestureEnd()
            }
        },
    )

    init {
        contentDescription = CONTENT_DESCRIPTION
        isFocusable = false
    }

    /** Sets the fill level. [animate] is used for taps, dragging updates immediately. */
    fun setLevel(level: Float, animate: Boolean) {
        val target = level.coerceIn(0f, 1f)
        targetLevel = target
        levelAnimator?.cancel()
        if (!animate || !style.animationsEnabled || style.animationDurationMs <= 0) {
            displayedLevel = target
            invalidate()
            return
        }
        levelAnimator = ValueAnimator.ofFloat(displayedLevel, target).apply {
            duration = style.animationDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                displayedLevel = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = ControlPainter.contentPadding(style).toFloat()
        val available = (height - padding * 2f).coerceAtLeast(1f)
        val barLength = style.lengthPx.coerceIn(1f, available)
        val centerX = width / 2f
        val top = (height - barLength) / 2f
        val halfThickness = (style.thicknessPx / 2f).coerceAtMost(width / 2f)

        barRect.set(centerX - halfThickness, top, centerX + halfThickness, top + barLength)
        painter.draw(canvas, barRect, displayedLevel, activeProgress)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> engine.onDown(event.y)
        MotionEvent.ACTION_MOVE -> {
            engine.onMove(event.y)
            true
        }

        MotionEvent.ACTION_UP -> {
            engine.onUp(event.y)
            true
        }

        MotionEvent.ACTION_CANCEL -> {
            engine.onCancel()
            true
        }

        else -> false
    }

    /**
     * Accessibility entry point: a TalkBack double-tap raises the volume by one step,
     * which is the closest equivalent of the swipe the control is built around.
     */
    override fun performClick(): Boolean {
        listener?.onStep(1, fromTap = true)
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        cancelAnimators()
        super.onDetachedFromWindow()
    }

    private fun setActive(active: Boolean) {
        isActive = active
        activeAnimator?.cancel()
        val target = if (active) 1f else 0f
        if (!style.animationsEnabled || style.animationDurationMs <= 0) {
            activeProgress = target
            invalidate()
            return
        }
        activeAnimator = ValueAnimator.ofFloat(activeProgress, target).apply {
            duration = style.animationDurationMs
            interpolator = if (active) DecelerateInterpolator() else AccelerateInterpolator()
            addUpdateListener { animation ->
                activeProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun cancelAnimators() {
        activeAnimator?.cancel()
        levelAnimator?.cancel()
        activeAnimator = null
        levelAnimator = null
    }

    companion object {
        private const val CONTENT_DESCRIPTION = "Virtual volume control"

        val DEFAULT_STYLE = ControlStyle(
            lengthPx = 396f,
            thicknessPx = 15f,
            idleAlpha = 0.42f,
            activeAlpha = 1f,
            touchWidthPx = 120f,
            touchLengthPx = 570f,
            animationDurationMs = 180L,
            animationsEnabled = true,
        )
    }
}
