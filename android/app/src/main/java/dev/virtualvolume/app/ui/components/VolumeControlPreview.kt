package dev.virtualvolume.app.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.overlay.ControlSpecFactory
import dev.virtualvolume.app.overlay.GestureListener
import dev.virtualvolume.app.overlay.OverlayControlView
import kotlin.math.roundToInt

/**
 * The real floating control, rendered inside the app.
 *
 * This is not a mock-up: it is the same [OverlayControlView] the overlay service puts on
 * screen, driven by the same [ControlSpecFactory] maths, so what you see and touch here is
 * exactly what runs over other apps.
 */
@Composable
fun VolumeControlPreview(
    settings: VolumeSettings,
    level: Float,
    maxVolume: Int,
    modifier: Modifier = Modifier,
    onStep: ((Int) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val currentOnStep by rememberUpdatedState(onStep)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OverlayControlView(context).apply {
                // The preview lives inside a scrolling column; claim the gesture so a
                // practice swipe is not stolen by the scroll.
                setOnTouchListener { view: View, _ ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
        },
        update = { view ->
            view.style = ControlSpecFactory.style(settings, density)
            view.physicalEdge = settings.edge
            view.gestureConfig = ControlSpecFactory.gestureConfig(
                settings = settings,
                density = density,
                maxVolume = maxVolume,
                touchSlopPx = (MIN_TOUCH_SLOP_DP * density).roundToInt(),
            )
            view.listener = object : GestureListener {
                override fun onGestureStart() = Unit
                override fun onStep(deltaSteps: Int, fromTap: Boolean) {
                    currentOnStep?.invoke(deltaSteps)
                }

                override fun onGestureEnd() = Unit
            }
            view.setLevel(level, animate = false)
        },
    )
}

private const val MIN_TOUCH_SLOP_DP = 10f
