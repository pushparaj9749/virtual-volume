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
import dev.virtualvolume.app.core.audio.CallAudioMode
import dev.virtualvolume.app.core.audio.VolumeResult
import dev.virtualvolume.app.core.audio.VolumeState
import dev.virtualvolume.app.core.audio.VolumeStreamResolver
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    /**
     * The stream actually being driven right now. Usually [VolumeSettings.audioStream],
     * but during a call it becomes call volume — the same thing the hardware keys do.
     */
    private var activeStream: VolumeStreamType = VolumeStreamType.DEFAULT

    /** Latest call state, shared by the volume observer and the gesture path. */
    private val callMode = MutableStateFlow(CallAudioMode.IDLE)
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
        observeCallAudioMode()
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
        syncActiveStream()
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
                // The selected stream (or the call-following toggle) may have changed, so
                // the target is re-resolved before anything is drawn.
                syncActiveStream()
                applySettings(latest)
                refreshVolume()

                if (wasEnabled && !latest.enabled) {
                    // Turned off from the dashboard or the Quick Settings tile.
                    stopSelf()
                }
            }
        }
    }

    /**
     * Watches the stream the control is *currently* driving, so the bar mirrors the real
     * device volume even while it has switched to call volume mid-call.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeVolume() {
        scope.launch {
            container.settingsRepository.settings
                .combine(callMode) { latest, mode ->
                    VolumeStreamResolver.resolve(latest.audioStream, mode, latest.followActiveCall)
                }
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

    /**
     * Follows phone and VoIP calls: when one starts the control switches to call volume,
     * when it ends it goes back to the stream the user chose. The poll only runs while
     * [VolumeSettings.followActiveCall] is on.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCallAudioMode() {
        scope.launch {
            container.settingsRepository.settings
                .map { it.followActiveCall }
                .distinctUntilChanged()
                .flatMapLatest { follow ->
                    if (follow) {
                        container.callAudioMonitor.modeChanges()
                    } else {
                        flowOf(CallAudioMode.IDLE)
                    }
                }
                .collect { mode ->
                    callMode.value = mode
                    if (syncActiveStream(mode)) {
                        // Re-reads the new stream's level; the drag mapping is rebuilt
                        // there too, because max volume differs between media and call.
                        refreshVolume()
                    }
                }
        }
    }

    // endregion

    // region gesture handling

    /**
     * Re-reads the real volume for [activeStream].
     *
     * It also re-resolves the stream first, so the very first touch after a call has
     * started is already aimed at call volume even if the poll has not fired yet.
     */
    private fun refreshVolume() {
        if (syncActiveStream()) applyGestureConfig()
        val snapshot = container.volumeController.snapshot(activeStream)
        if (snapshot.index < 0) return
        volume = snapshot
        controlView?.setLevel(snapshot.fraction, animate = false)
    }

    /**
     * Re-resolves which stream the control drives and reports whether it changed.
     *
     * Call volume wins while a call is up (when the user allows it), exactly like the
     * physical rocker. Nothing is written here — the next gesture decides what to change.
     */
    private fun syncActiveStream(
        mode: CallAudioMode = container.callAudioMonitor.currentMode(),
    ): Boolean {
        val resolved = resolveActiveStream(mode)
        if (resolved == activeStream) return false
        activeStream = resolved
        publishStreamInNotification()
        return true
    }

    private fun resolveActiveStream(mode: CallAudioMode): VolumeStreamType {
        val preferred = VolumeStreamResolver.resolve(
            configured = settings.audioStream,
            callMode = mode,
            followCalls = settings.followActiveCall,
        )
        if (preferred == settings.audioStream) return preferred
        // Some devices expose no call stream at all. Falling back keeps the control
        // usable during the call instead of leaving a bar that changes nothing.
        return if (container.volumeController.snapshot(preferred).max > 0) {
            preferred
        } else {
            settings.audioStream
        }
    }

    private fun applyStep(deltaSteps: Int, fromTap: Boolean) {
        val result = container.volumeController.changeBy(
            stream = activeStream,
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
        view.gestureConfig = gestureConfig(newSettings)
        reposition()
        publishStreamInNotification()
    }

    /**
     * Gesture tuning depends on the maximum volume of the stream in use, which is
     * different for media and for calls, so it is rebuilt whenever either changes.
     */
    private fun gestureConfig(newSettings: VolumeSettings = settings): GestureConfig {
        val maxVolume = volume.max.takeIf { it > 0 && volume.stream == activeStream }
            ?: container.volumeController.snapshot(activeStream).max
        return ControlSpecFactory.gestureConfig(
            settings = newSettings,
            density = resources.displayMetrics.density,
            maxVolume = maxVolume,
            touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop,
        )
    }

    private fun applyGestureConfig() {
        controlView?.gestureConfig = gestureConfig()
    }

    private fun publishStreamInNotification() {
        val stream = activeStream
        if (stream == lastNotifiedStream) return
        lastNotifiedStream = stream
        // Say so out loud when the control is not on the stream the user picked, so a
        // call never looks like the app randomly stopped responding to the bar.
        val subtitle = if (stream != settings.audioStream) {
            getString(R.string.notification_text_call, stream.label)
        } else {
            getString(R.string.notification_text_stream, stream.label)
        }
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
