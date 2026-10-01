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
import android.hardware.display.DisplayManager
import android.view.Surface

/**
 * The only class that talks to [WindowManager].
 *
 * The window is exactly the size of the touch zone — a narrow strip on one edge — so the
 * control never puts an invisible sheet over the rest of the screen.
 */
class OverlayWindowHost(context: Context) {

    val windowContext: Context = run {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val displayContext = if (display != null) context.createDisplayContext(display) else context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else displayContext
    }
    private val appContext = windowContext
    private val windowManager: WindowManager? = windowContext.getSystemService(WindowManager::class.java)

    private var attachedView: OverlayControlView? = null
    private var currentParams: WindowManager.LayoutParams? = null

    val isAttached: Boolean get() = attachedView != null

    fun attach(view: OverlayControlView, placement: WindowPlacement): Boolean {
        val manager = windowManager ?: return false
        if (attachedView != null) detach()

        if (!placement.isValid) return false
        view.displayRotation = placement.rotation
        val params = buildParams(placement)
        return runCatching { manager.addView(view, params) }
            .onSuccess {
                attachedView = view
                currentParams = params
            }
            .onFailure { Log.w(TAG, "Could not attach the overlay window", it) }
            .isSuccess
    }

    fun updatePlacement(placement: WindowPlacement): Boolean {
        val manager = windowManager ?: return false
        val view = attachedView ?: return false
        val params = currentParams ?: return false
        if (!placement.isValid) return false
        if (params.x != placement.xOffset || params.y != placement.yOffset || params.width != placement.windowWidthPx || params.height != placement.windowHeightPx) view.cancelGesture()
        view.displayRotation = placement.rotation

        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = placement.xOffset
        params.y = placement.yOffset
        params.width = placement.windowWidthPx
        params.height = placement.windowHeightPx

        return runCatching { manager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "Could not move the overlay window", it) }.isSuccess
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
                    rotation = currentRotation(),
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

        val cutoutInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { display.cutout?.let { intArrayOf(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom) } }
                .getOrNull() ?: intArrayOf(0, 0, 0, 0)
        } else intArrayOf(0, 0, 0, 0)
        fun systemDimension(name: String): Int {
            val id = appContext.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) appContext.resources.getDimensionPixelSize(id) else 0
        }
        val status = systemDimension("status_bar_height")
        val sideNav = (real.widthPixels - app.widthPixels).coerceAtLeast(0)
        val bottomNav = (real.heightPixels - app.heightPixels - status).coerceAtLeast(0)
        val rotation = currentRotation()
        return ScreenBounds(
            widthPx = real.widthPixels,
            heightPx = real.heightPixels,
            insetLeftPx = maxOf(cutoutInsets[0], if (rotation == DisplayRotation.RIGHT) sideNav else 0),
            insetRightPx = maxOf(cutoutInsets[2], if (rotation != DisplayRotation.RIGHT) sideNav else 0),
            insetTopPx = maxOf(status, cutoutInsets[1]),
            insetBottomPx = maxOf(bottomNav, cutoutInsets[3]),
            rotation = rotation,
        )
    }

    private fun buildParams(placement: WindowPlacement): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            placement.windowWidthPx,
            placement.windowHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "Virtual Volume control"
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            x = placement.xOffset
            y = placement.yOffset
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun currentRotation(): DisplayRotation = DisplayRotation.fromSurface(
        appContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0,
    )

    companion object {
        private const val TAG = "OverlayWindowHost"
    }
}
