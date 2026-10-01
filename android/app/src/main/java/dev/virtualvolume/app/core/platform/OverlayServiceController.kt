package dev.virtualvolume.app.core.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.virtualvolume.app.overlay.OverlayService

/** Why a start attempt did not (or did) succeed. Surfaced verbatim in the dashboard. */
sealed interface OverlayStartResult {
    data object Started : OverlayStartResult
    data object MissingOverlayPermission : OverlayStartResult
    data class Blocked(val message: String) : OverlayStartResult
}

/**
 * The only place that starts or stops the overlay service, so permission checks and
 * failure reporting can never be forgotten by a caller.
 */
class OverlayServiceController(private val context: Context) {

    private val appContext: Context = context.applicationContext

    fun start(): OverlayStartResult {
        if (!OverlayPermission.isGranted(appContext)) {
            return OverlayStartResult.MissingOverlayPermission
        }
        return try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_START),
            )
            OverlayStartResult.Started
        } catch (throwable: Throwable) {
            // Android 12+ refuses background foreground-service starts in some situations.
            // We never swallow this silently: the caller shows the message.
            Log.w(TAG, "Overlay service start refused", throwable)
            OverlayRuntime.reportError(throwable.message ?: throwable.javaClass.simpleName)
            OverlayStartResult.Blocked(
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    fun stop() {
        runCatching {
            appContext.startService(
                Intent(appContext, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_STOP),
            )
        }.onFailure { Log.w(TAG, "Could not deliver stop", it) }
    }

    companion object {
        private const val TAG = "OverlayServiceCtl"
    }
}
