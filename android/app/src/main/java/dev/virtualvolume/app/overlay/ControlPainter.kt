package dev.virtualvolume.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * Single implementation of the control's appearance.
 *
 * Used by the overlay window and by the in-app preview, which is why it draws through a
 * raw [Canvas]: Compose hands one over with `drawIntoCanvas`. Changing the look happens
 * in exactly one place.
 *
 * The "glow" is layered translucent strokes rather than a blur mask, because
 * `BlurMaskFilter` is ignored on hardware-accelerated canvases.
 */
class ControlPainter(style: ControlStyle) {

    private var style: ControlStyle = style
        private set

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        paintStyle = Paint.Style.FILL
        color = style.trackColor
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        paintStyle = Paint.Style.FILL
        color = style.fillColor
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        paintStyle = Paint.Style.STROKE
        color = style.glowColor
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        paintStyle = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val rect = RectF()

    fun update(newStyle: ControlStyle) {
        if (newStyle == style) return
        style = newStyle
        trackPaint.color = newStyle.trackColor
        fillPaint.color = newStyle.fillColor
        glowPaint.color = newStyle.glowColor
    }

    /**
     * @param bar bounds of the control in canvas coordinates
     * @param level 0..1 fill of the bar, taken from the real device volume
     * @param activeProgress 0 (idle) .. 1 (being touched)
     */
    fun draw(canvas: Canvas, bar: RectF, level: Float, activeProgress: Float) {
        val progress = activeProgress.coerceIn(0f, 1f)
        val alpha = lerpAlpha(style.effectiveIdleAlpha, style.effectiveActiveAlpha, progress)
        val radius = bar.width() / 2f

        if (progress > 0.01f) {
            drawGlow(canvas, bar, progress, alpha)
        }

        trackPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(bar, radius, radius, trackPaint)

        val fillHeight = bar.height() * level.coerceIn(0f, 1f)
        if (fillHeight > radius * 0.5f) {
            rect.set(bar.left, bar.bottom - fillHeight, bar.right, bar.bottom)
            val fillRadius = radius.coerceAtMost(fillHeight / 2f)
            fillPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, fillRadius, fillRadius, fillPaint)
        } else {
            // Always leave a visible nub at the bottom so the control is never invisible.
            val nub = radius.coerceAtMost(bar.height() * 0.12f)
            rect.set(bar.left, bar.bottom - nub * 2f, bar.right, bar.bottom)
            fillPaint.alpha = (alpha * 200f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }

        if (progress > 0.4f) {
            // Small handle marker at the fill edge while the control is live.
            val markerY = bar.bottom - fillHeight.coerceAtLeast(radius)
            val markerAlpha = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f) * alpha
            capPaint.alpha = (markerAlpha * 160f).roundToInt().coerceIn(0, 255)
            val half = bar.width() * 0.28f
            rect.set(bar.centerX() - half, markerY - half * 0.45f, bar.centerX() + half, markerY + half * 0.45f)
            canvas.drawRoundRect(rect, half * 0.45f, half * 0.45f, capPaint)
        }
    }

    private fun drawGlow(canvas: Canvas, bar: RectF, progress: Float, alpha: Float) {
        val layers = 3
        for (i in 1..layers) {
            val fraction = i.toFloat() / layers
            val spread = bar.width() * (0.5f + fraction * 1.6f) * progress
            val layerAlpha = (1f - fraction) * 0.22f * progress * alpha
            rect.set(
                bar.left - spread,
                bar.top - spread,
                bar.right + spread,
                bar.bottom + spread,
            )
            glowPaint.alpha = (layerAlpha * 255f).roundToInt().coerceIn(0, 255)
            glowPaint.strokeWidth = (bar.width() * 0.35f).coerceAtLeast(1f)
            val radius = rect.width() / 2f
            canvas.drawRoundRect(rect, radius, radius, glowPaint)
        }
    }

    private fun lerpAlpha(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    companion object {
        /**
         * Space to keep free around the bar so the widest glow ring is not clipped by
         * the window bounds.
         */
        fun contentPadding(style: ControlStyle): Int =
            maxOf((style.thicknessPx * GLOW_SPREAD_MULTIPLIER).roundToInt(), MIN_CONTENT_PADDING)

        const val GLOW_SPREAD_MULTIPLIER = 2.1f
        const val MIN_CONTENT_PADDING = 10
    }
}
