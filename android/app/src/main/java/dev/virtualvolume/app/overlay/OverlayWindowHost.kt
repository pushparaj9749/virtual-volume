package dev.virtualvolume.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowInsets
import dev.virtualvolume.app.core.data.ScreenEdge

/**
 * The only class that talks to [WindowManager].
 *
 * The window is exactly the size of the touch zone — a narrow strip on one edge — so the
 * control never puts an invisible sheet over the rest of the screen.
 */
class OverlayWindowHost(context: Context) {

    private val appContext: Context = context.applicationContext
    private val windowManager: WindowManager? =
        appContext.getSystemService(WindowManager::class.java)

    private var attachedView: OverlayControlView? = null
    private var currentParams: WindowManager.LayoutParams? = null

    val isAttached: Boolean get() = attachedView != null

    fun attach(view: OverlayControlView, placement: WindowPlacement): Boolean {
        val manager = windowManager ?: return false
        if (attachedView != null) detach()

        val params = buildParams(placement)
        return runCatching { manager.addView(view, params) }
            .onSuccess {
                attachedView = view
                currentParams = params
            }
            .onFailure { Log.w(TAG, "Could not attach the overlay window", it) }
            .isSuccess
    }

    fun updatePlacement(placement: WindowPlacement) {
        val manager = windowManager ?: return
        val view = attachedView ?: return
        val params = currentParams ?: return

        params.gravity = gravityFor(placement.edge)
        params.x = placement.xOffset
        params.y = placement.yOffset
        params.width = placement.windowWidthPx
        params.height = placement.windowHeightPx

        runCatching { manager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Could not move the overlay window", it) }
    }

    fun detach() {
        val manager = windowManager
        val view = attachedView
        if (manager != null && view != null) {
            runCatching { manager.removeViewImmediate(view) }
                .onFailure { Log.w(TAG, "Could not detach the overlay window", it) }
        }
        attachedView = null
        currentParams = null
    }

    /** Live screen size and the insets that must be kept clear. */
    fun currentBounds(): ScreenBounds {
        val manager = windowManager ?: return ScreenBounds.EMPTY
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val metrics = manager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                ScreenBounds(
                    widthPx = metrics.bounds.width(),
                    heightPx = metrics.bounds.height(),
                    insetLeftPx = insets.left,
                    insetRightPx = insets.right,
                    insetTopPx = insets.top,
                    insetBottomPx = insets.bottom,
                )
            }.getOrElse { ScreenBounds.EMPTY }
        } else {
            legacyBounds(manager)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyBounds(manager: WindowManager): ScreenBounds {
        val display: Display = manager.defaultDisplay ?: return ScreenBounds.EMPTY
        val real = DisplayMetrics()
        val app = DisplayMetrics()
        runCatching {
            display.getRealMetrics(real)
            display.getMetrics(app)
        }.onFailure { return ScreenBounds.EMPTY }

        // Without WindowMetrics the difference between the real and app metrics is the
        // best available approximation of the system bar size for this rotation.
        val barHeight = (real.heightPixels - app.heightPixels).coerceIn(0, real.heightPixels)
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { display.cutout }.getOrNull()
        } else {
            null
        }

        return ScreenBounds(
            widthPx = real.widthPixels,
            heightPx = real.heightPixels,
            insetLeftPx = cutout?.safeInsetLeft ?: 0,
            insetRightPx = cutout?.safeInsetRight ?: 0,
            insetTopPx = maxOf(barHeight, cutout?.safeInsetTop ?: 0),
            insetBottomPx = cutout?.safeInsetBottom ?: 0,
        )
    }

    private fun buildParams(placement: WindowPlacement): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            placement.windowWidthPx,
            placement.windowHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = gravityFor(placement.edge)
            x = placement.xOffset
            y = placement.yOffset
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    /**
     * Absolute left/right gravity on purpose: the user chose a physical side of the
     * device, and `START`/`END` would flip that choice in a right-to-left locale.
     */
    private fun gravityFor(edge: ScreenEdge): Int = when (edge) {
        ScreenEdge.LEFT -> Gravity.TOP or Gravity.LEFT
        ScreenEdge.RIGHT -> Gravity.TOP or Gravity.RIGHT
    }

    companion object {
        private const val TAG = "OverlayWindowHost"
    }
}
