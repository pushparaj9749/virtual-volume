package dev.virtualvolume.app.overlay

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewConfiguration
import androidx.core.app.ServiceCompat
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
import dev.virtualvolume.app.tile.TileStateSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** User-enabled, special-use foreground service. No wake locks, hidden APIs or restart loops. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayService : LifecycleService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val container by lazy { appContainer }
    private val windowHost by lazy { OverlayWindowHost(this) }
    private var control: OverlayControlView? = null
    private var settings = VolumeSettings.DEFAULT
    private var volume = VolumeState.UNKNOWN
    private var stopping = false
    private var notifiedStream: VolumeStreamType? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var configListener: ComponentCallbacks? = null
    private var permissionListener: AppOpsManager.OnOpChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        OverlayNotification.ensureChannel(this)
        val foreground = runCatching {
            ServiceCompat.startForeground(this, OverlayNotification.NOTIFICATION_ID,
                OverlayNotification.build(this),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }.isSuccess
        if (!foreground) {
            failAndStop("Android could not start the foreground service. Open Virtual Volume and enable it again.")
            return
        }
        registerSystemListeners()
        observeSettings()
        observeAudio()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            // Notification OFF must also persist OFF, otherwise a sticky restart/boot revives it.
            stopping = true
            scope.launch {
                runCatching { container.settingsRepository.update { it.copy(enabled = false, serviceNotice = null) } }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        return START_STICKY // The first DataStore emission still gates every restart.
    }

    private fun observeSettings() = scope.launch {
        try {
            container.settingsRepository.settings.collect { latest ->
                settings = latest
                if (stopping) return@collect
                if (!latest.enabled) { stopSelf(); return@collect }
                if (!OverlayPermission.isGranted(this@OverlayService)) {
                    failAndStop(getString(R.string.error_overlay_revoked_body))
                    return@collect
                }
                if (control == null) {
                    control = OverlayControlView(windowHost.windowContext).apply {
                        listener = object : GestureListener {
                            override fun onGestureStart() = refreshVolume()
                            override fun onStep(deltaSteps: Int, fromTap: Boolean) = applyStep(deltaSteps, fromTap)
                            override fun onGestureEnd() = Unit
                        }
                    }
                    applySettings()
                    if (!windowHost.attach(control!!, placement())) {
                        failAndStop("Android could not display the control. Check overlay permission, then tap Resume control in the app.")
                        return@collect
                    }
                    OverlayRuntime.setPlacement(placement())
                    OverlayRuntime.setServiceRunning(true)
                    OverlayRuntime.reportError(null)
                    TileStateSync.refresh(this@OverlayService)
                } else applySettings()
                setScreenVisibility()
                if (notifiedStream != latest.audioStream) {
                    notifiedStream = latest.audioStream
                    runCatching { getSystemService(NotificationManager::class.java)?.notify(OverlayNotification.NOTIFICATION_ID,
                        OverlayNotification.build(this@OverlayService, getString(R.string.notification_text_stream, latest.audioStream.label))) }
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            failAndStop("Settings could not be read. Reopen Virtual Volume and retry; your control is safely stopped.")
        }
    }

    private fun observeAudio() = scope.launch {
        container.settingsRepository.settings.map { it.audioStream }.distinctUntilChanged().flatMapLatest { stream ->
            merge(container.audioVolumeMonitor.observe(stream) { container.volumeController.snapshot(it) },
                container.volumeController.changes.filter { it.stream == stream })
        }.collect { refreshVolume() }
    }

    private fun refreshVolume() {
        volume = container.volumeController.snapshot(settings.audioStream)
        if (volume.available) control?.setLevel(volume.fraction, animate = false)
        configureGesture()
    }

    private fun applyStep(delta: Int, fromTap: Boolean) {
        when (val result = container.volumeController.changeBy(settings.audioStream, delta, settings.volumeStep)) {
            is VolumeResult.Success -> {
                volume = result.state
                control?.setLevel(volume.fraction, animate = fromTap)
                if (result.changed && settings.hapticsEnabled) container.haptics.tick()
                OverlayRuntime.reportError(null)
            }
            is VolumeResult.Failure -> OverlayRuntime.reportError(result.message)
        }
    }

    private fun applySettings() {
        val view = control ?: return
        view.style = ControlSpecFactory.style(settings, windowHost.windowContext.resources.displayMetrics.density)
        view.physicalEdge = settings.edge
        refreshVolume()
        reposition()
    }

    private fun configureGesture() {
        control?.gestureConfig = ControlSpecFactory.gestureConfig(settings,
            windowHost.windowContext.resources.displayMetrics.density, volume.max - volume.min,
            ViewConfiguration.get(windowHost.windowContext).scaledTouchSlop)
    }

    private fun placement(): WindowPlacement = OverlayLayoutResolver.resolve(windowHost.currentBounds(),
        ControlSpecFactory.placement(settings, windowHost.windowContext.resources.displayMetrics.density),
        windowHost.windowContext.resources.displayMetrics.density)

    private fun reposition() {
        if (stopping || !windowHost.isAttached) return
        if (!OverlayPermission.isGranted(this)) { failAndStop(getString(R.string.error_overlay_revoked_body)); return }
        val resolved = placement()
        if (!windowHost.updatePlacement(resolved)) failAndStop("Android could not reposition the control. Open the app and enable it again.")
        else OverlayRuntime.setPlacement(resolved)
    }

    private fun setScreenVisibility() {
        val usable = getSystemService(PowerManager::class.java)?.isInteractive != false &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true
        if (!usable) control?.cancelGesture()
        control?.visibility = if (usable) View.VISIBLE else View.GONE
        if (usable) { reposition(); refreshVolume() }
    }

    private fun failAndStop(message: String) {
        if (stopping) return
        stopping = true
        OverlayRuntime.reportError(message)
        scope.launch {
            runCatching { container.settingsRepository.update { it.copy(serviceNotice = message) } }
            stopSelf()
        }
    }

    private fun registerSystemListeners() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = setScreenVisibility()
        }.also { receiver ->
            ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) { applySettings(); setScreenVisibility() }
        }.also { getSystemService(DisplayManager::class.java)?.registerDisplayListener(it, handler) }
        configListener = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) { applySettings(); setScreenVisibility() }
            override fun onLowMemory() = Unit
        }.also { registerComponentCallbacks(it) }
        permissionListener = AppOpsManager.OnOpChangedListener { op, pkg ->
            if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && pkg == packageName) handler.post {
                if (!OverlayPermission.isGranted(this)) failAndStop(getString(R.string.error_overlay_revoked_body))
            }
        }.also { listener ->
            runCatching { getSystemService(AppOpsManager::class.java)?.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, listener) }
        }
    }

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        displayListener?.let { getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(it) }
        configListener?.let { unregisterComponentCallbacks(it) }
        permissionListener?.let { runCatching { getSystemService(AppOpsManager::class.java)?.stopWatchingMode(it) } }
        handler.removeCallbacksAndMessages(null)
        windowHost.detach()
        control = null
        scope.cancel()
        container.overlayServiceController.serviceStopped()
        OverlayRuntime.setPlacement(null)
        OverlayRuntime.setServiceRunning(false)
        TileStateSync.refresh(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.virtualvolume.app.action.START"
        const val ACTION_STOP = "dev.virtualvolume.app.action.STOP"
    }
}
