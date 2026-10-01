package dev.virtualvolume.app.overlay

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import dev.virtualvolume.app.R
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.audio.VolumeResult
import dev.virtualvolume.app.core.audio.VolumeState
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.data.VolumeStreamType
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.di.AppContainer
import dev.virtualvolume.app.tile.TileStateSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the floating volume control on screen.
 *
 * Lifecycle notes, because they are the part Android is strict about:
 *  - The window is added in [onCreate] and removed in [onDestroy]; nothing outlives the
 *    service, so there is no leaked overlay if the app is killed.
 *  - Position is recomputed — never remembered as raw coordinates — whenever the
 *    configuration, the display or the screen state changes, which is what keeps the
 *    control on the correct edge after a rotation.
 *  - When the display is off the service simply stays alive and keeps its state. Android
 *    does not deliver touch events to overlays while the panel is powered down, and this
 *    app does not pretend otherwise.
 */
class OverlayService : LifecycleService() {

    private lateinit var container: AppContainer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val windowHost: OverlayWindowHost by lazy { OverlayWindowHost(this) }
    private var controlView: OverlayControlView? = null

    private var settings = VolumeSettings.DEFAULT
    private var volume = VolumeState.UNKNOWN
    private var hasReceivedSettings = false
    private var lastNotifiedStream: VolumeStreamType? = null

    private var screenReceiver: BroadcastReceiver? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var componentCallbacks: ComponentCallbacks? = null

    private val gestureListener = object : GestureListener {
        override fun onGestureStart() {
            refreshVolume()
        }

        override fun onStep(deltaSteps: Int, fromTap: Boolean) = applyStep(deltaSteps, fromTap)

        override fun onGestureEnd() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        container = appContainer

        OverlayNotification.ensureChannel(this)
        startForeground(OverlayNotification.NOTIFICATION_ID, OverlayNotification.build(this))

        OverlayRuntime.setServiceRunning(true)
        OverlayRuntime.reportError(null)
        TileStateSync.refresh(this)

        if (!OverlayPermission.isGranted(this)) {
            Log.w(TAG, "Overlay permission is missing; the control cannot be shown")
            OverlayRuntime.reportError(getString(R.string.error_overlay_revoked_body))
            stopSelf()
            return
        }

        attachControl()
        observeSettings()
        observeVolume()
        registerSystemListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                START_NOT_STICKY
            }

            else -> START_STICKY
        }
    }

    override fun onDestroy() {
        unregisterSystemListeners()
        windowHost.detach()
        controlView = null
        scope.cancel()
        OverlayRuntime.setServiceRunning(false)
        OverlayRuntime.reportError(null)
        TileStateSync.refresh(this)
        super.onDestroy()
    }

    // region setup

    private fun attachControl() {
        val view = OverlayControlView(this).apply {
            listener = gestureListener
        }
        controlView = view
        applySettings(settings)
        refreshVolume()
        if (!windowHost.attach(view, currentPlacement())) {
            OverlayRuntime.reportError(getString(R.string.error_overlay_revoked_body))
            stopSelf()
        }
    }

    private fun observeSettings() {
        scope.launch {
            container.settingsRepository.settings.collect { latest ->
                val wasEnabled = hasReceivedSettings && settings.enabled
                hasReceivedSettings = true
                settings = latest
                applySettings(latest)

                if (wasEnabled && !latest.enabled) {
                    // Turned off from the dashboard or the Quick Settings tile.
                    stopSelf()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeVolume() {
        scope.launch {
            container.settingsRepository.settings
                .map { it.audioStream }
                .distinctUntilChanged()
                .flatMapLatest { stream ->
                    container.audioVolumeMonitor.observe(stream) { candidate ->
                        container.volumeController.snapshot(candidate).index
                    }
                }
                .collect { index ->
                    // Re-read the whole snapshot: max volume can differ per stream and
                    // per output device, so it is never assumed from the last reading.
                    if (index >= 0) refreshVolume()
                }
        }
    }

    // endregion

    // region gesture handling

    private fun refreshVolume() {
        val snapshot = container.volumeController.snapshot(settings.audioStream)
        if (snapshot.index < 0) return
        volume = snapshot
        controlView?.setLevel(snapshot.fraction, animate = false)
    }

    private fun applyStep(deltaSteps: Int, fromTap: Boolean) {
        val result = container.volumeController.changeBy(
            stream = settings.audioStream,
            deltaSteps = deltaSteps,
            stepSize = settings.volumeStep,
        )
        when (result) {
            is VolumeResult.Success -> {
                volume = result.state
                controlView?.setLevel(result.state.fraction, animate = fromTap)
                if (settings.hapticsEnabled) container.haptics.tick()
            }

            is VolumeResult.Failure -> {
                Log.w(TAG, "Volume change refused: ${result.message}")
                OverlayRuntime.reportError(result.message)
            }
        }
    }

    // endregion

    // region configuration changes

    private fun registerSystemListeners() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        if (!OverlayPermission.isGranted(this@OverlayService)) {
                            OverlayRuntime.reportError(
                                getString(R.string.error_overlay_revoked_body),
                            )
                            stopSelf()
                            return
                        }
                        // Rotation and density can change while the panel is off, so the
                        // position is resolved again instead of trusted.
                        reposition()
                        refreshVolume()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiver = receiver

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = reposition()
        }
        getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(listener, mainHandler)
        displayListener = listener

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = reposition()
            override fun onLowMemory() = Unit
        }
        registerComponentCallbacks(callbacks)
        componentCallbacks = callbacks
    }

    private fun unregisterSystemListeners() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        displayListener?.let { listener ->
            runCatching { getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(listener) }
        }
        displayListener = null
        componentCallbacks?.let { runCatching { unregisterComponentCallbacks(it) } }
        componentCallbacks = null
    }

    /** Resolves the stored relative placement against the current screen. */
    private fun reposition() {
        if (controlView == null) return
        val density = resources.displayMetrics.density
        val bounds = windowHost.currentBounds()
        if (bounds.widthPx <= 0 || bounds.heightPx <= 0) return
        val placement = OverlayLayoutResolver.resolve(
            bounds = bounds,
            placement = ControlSpecFactory.placement(settings, density),
            density = density,
        )
        if (!placement.isValid) return
        windowHost.updatePlacement(placement)
    }

    // endregion

    private fun applySettings(newSettings: VolumeSettings) {
        val density = resources.displayMetrics.density
        val view = controlView ?: return
        view.style = ControlSpecFactory.style(newSettings, density)
        view.gestureConfig = ControlSpecFactory.gestureConfig(
            settings = newSettings,
            density = density,
            maxVolume = volume.max.takeIf { it > 0 }
                ?: container.volumeController.snapshot(newSettings.audioStream).max,
            touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop,
        )
        reposition()
        publishStreamInNotification(newSettings)
    }

    private fun publishStreamInNotification(newSettings: VolumeSettings) {
        if (newSettings.audioStream == lastNotifiedStream) return
        lastNotifiedStream = newSettings.audioStream
        val subtitle = getString(R.string.notification_text_stream, newSettings.audioStream.label)
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(
                OverlayNotification.NOTIFICATION_ID,
                OverlayNotification.build(this, subtitle),
            )
        }
    }

    private fun currentPlacement(): WindowPlacement {
        val density = resources.displayMetrics.density
        return OverlayLayoutResolver.resolve(
            bounds = windowHost.currentBounds(),
            placement = ControlSpecFactory.placement(settings, density),
            density = density,
        )
    }

    companion object {
        private const val TAG = "OverlayService"

        const val ACTION_START = "dev.virtualvolume.app.action.START"
        const val ACTION_STOP = "dev.virtualvolume.app.action.STOP"
    }
}
