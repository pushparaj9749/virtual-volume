package dev.virtualvolume.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import dev.virtualvolume.app.core.data.ScreenEdge
import kotlin.math.abs
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * The floating volume rocker itself: a thin bar that brightens while it is touched.
 *
 * The view owns no policy. It draws whatever [ControlStyle] describes, forwards raw
 * relative coordinates to [GestureEngine], and reports steps through [listener]. Keeping it
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

    var displayRotation: DisplayRotation = DisplayRotation.NATURAL
        set(value) {
            if (field != value) cancelGesture()
            field = value
            invalidate()
        }

    var physicalEdge: ScreenEdge = ScreenEdge.RIGHT

    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var horizontalDrag: Boolean? = null

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
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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
        val canonicalWidth = if (displayRotation.isHorizontal) height.toFloat() else width.toFloat()
        val canonicalHeight = if (displayRotation.isHorizontal) width.toFloat() else height.toFloat()
        val saved = canvas.save()
        when (displayRotation) {
            DisplayRotation.NATURAL -> Unit
            DisplayRotation.LEFT -> { canvas.translate(0f, height.toFloat()); canvas.rotate(-90f) }
            DisplayRotation.INVERTED -> { canvas.translate(width.toFloat(), height.toFloat()); canvas.rotate(180f) }
            DisplayRotation.RIGHT -> { canvas.translate(width.toFloat(), 0f); canvas.rotate(90f) }
        }
        val padding = ControlPainter.contentPadding(style).toFloat()
        val barLength = style.lengthPx.coerceIn(1f, (canonicalHeight - padding * 2f).coerceAtLeast(1f))
        val half = (style.thicknessPx / 2f).coerceAtMost(canonicalWidth / 2f)
        val edgeDistance = maxOf(half + 1f, 8f * resources.displayMetrics.density).coerceAtMost(canonicalWidth / 2f)
        val centerX = if (physicalEdge == ScreenEdge.RIGHT) canonicalWidth - edgeDistance else edgeDistance
        val top = (canonicalHeight - barLength) / 2f
        barRect.set(centerX - half, top, centerX + half, top + barLength)
        painter.draw(canvas, barRect, displayedLevel, activeProgress)
        canvas.restoreToCount(saved)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                horizontalDrag = null
                parent?.requestDisallowInterceptTouchEvent(true)
                return engine.onDown(0f, event.eventTime)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> cancelGesture()
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) { cancelGesture(); return true }
                val x = event.getX(index)
                val y = event.getY(index)
                val dx = x - downX
                val dy = y - downY
                if (horizontalDrag == null && maxOf(abs(dx), abs(dy)) > gestureConfig.touchSlopPx) {
                    if (!displayRotation.isHorizontal && abs(dx) > abs(dy) * 1.5f) {
                        cancelGesture() // A sideways brush is not a split tap.
                        return true
                    }
                    horizontalDrag = displayRotation.isHorizontal && abs(dx) > abs(dy)
                }
                // In landscape, both screen-up/down AND dragging along the bar work.
                val drag = if (horizontalDrag == true) {
                    if (displayRotation == DisplayRotation.RIGHT) -dx else dx
                } else dy
                if (event.actionMasked == MotionEvent.ACTION_MOVE) engine.onMove(drag, event.eventTime)
                else {
                    gestureConfig = gestureConfig.copy(tapSplitY = canonicalLength() / 2f)
                    engine.onUp(drag, event.eventTime, canonicalTapPosition(x, y))
                    pointerId = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            else -> return pointerId != -1
        }
        return true
    }

    fun cancelGesture() {
        engine.onCancel()
        pointerId = -1
        horizontalDrag = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun canonicalLength(): Float = if (displayRotation.isHorizontal) width.toFloat() else height.toFloat()

    private fun canonicalTapPosition(x: Float, y: Float): Float = when (displayRotation) {
        DisplayRotation.NATURAL -> y
        DisplayRotation.LEFT -> x
        DisplayRotation.INVERTED -> height - y
        DisplayRotation.RIGHT -> width - x
    }

    override fun performClick(): Boolean {
        listener?.onGestureStart()
        listener?.onStep(if (gestureConfig.tapMode == dev.virtualvolume.app.core.data.TapMode.ALWAYS_DECREASE) -1 else 1, true)
        listener?.onGestureEnd()
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, targetLevel * 100f)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            listener?.onGestureStart()
            listener?.onStep(if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) 1 else -1, true)
            listener?.onGestureEnd()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
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
