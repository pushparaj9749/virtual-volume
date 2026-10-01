package dev.virtualvolume.app.core.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.virtualvolume.app.core.data.SettingsRepository
import dev.virtualvolume.app.overlay.OverlayService
import dev.virtualvolume.app.tile.TileStateSync
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface OverlayStartResult {
    data object Started : OverlayStartResult
    data object MissingOverlayPermission : OverlayStartResult
    data class Blocked(val message: String) : OverlayStartResult
}

/** Serializes app/tile toggles. A stop never starts a new service just to stop it. */
class OverlayServiceController(context: Context, private val settings: SettingsRepository) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var requestedRunning = false

    suspend fun setEnabled(enabled: Boolean): OverlayStartResult = mutex.withLock { applyEnabled(enabled) }

    suspend fun toggle(): OverlayStartResult = mutex.withLock {
        applyEnabled(!(OverlayRuntime.isServiceRunning.value || requestedRunning))
    }

    private suspend fun applyEnabled(enabled: Boolean): OverlayStartResult {
        if (enabled && !OverlayPermission.isGranted(app)) {
            OverlayRuntime.reportError("Allow Display over other apps to enable the floating control.")
            return OverlayStartResult.MissingOverlayPermission
        }
        requestedRunning = enabled
        OverlayRuntime.reportError(null)
        settings.update { it.copy(enabled = enabled, serviceNotice = null) }
        val result = if (enabled) start() else { stop(); OverlayStartResult.Started }
        if (result is OverlayStartResult.Blocked) {
            requestedRunning = false
            settings.update { it.copy(enabled = false, serviceNotice = result.message) }
        }
        TileStateSync.refresh(app)
        return result
    }

    /** Used only for a previously enabled service restart, including boot. */
    fun start(): OverlayStartResult {
        if (!OverlayPermission.isGranted(app)) return OverlayStartResult.MissingOverlayPermission
        OverlayRuntime.reportError(null)
        return runCatching {
            ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java).setAction(OverlayService.ACTION_START))
            OverlayStartResult.Started
        }.getOrElse {
            val message = "Android blocked the service start. Open Virtual Volume and tap Resume control. If it repeats, check your phone's background/battery settings."
            OverlayRuntime.reportError(message)
            OverlayStartResult.Blocked(message)
        }
    }

    fun stop() {
        requestedRunning = false
        runCatching { app.stopService(Intent(app, OverlayService::class.java)) }
            .onFailure { OverlayRuntime.reportError("The service could not stop. Open the app and try again.") }
    }

    fun serviceStopped() { requestedRunning = false }
}
