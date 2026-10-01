package dev.virtualvolume.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.core.platform.OverlayStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings the control back after a reboot or an app update — but only when the user asked
 * for it (the "Start after reboot" switch) and Android allows it.
 *
 * On Android 12+ a background app may not start a foreground service from a broadcast.
 * Holding the user-granted SYSTEM_ALERT_WINDOW permission is one of the documented
 * exemptions, so this normally works; when it does not, the failure is recorded in
 * [OverlayRuntime] and surfaced on the dashboard instead of being swallowed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val container = appContext.appContainer
                val settings = container.settingsRepository.settings.first()
                if (!settings.enabled || !settings.startOnBoot) return@launch
                if (!OverlayPermission.isGranted(appContext)) return@launch

                when (val result = container.overlayServiceController.start()) {
                    is OverlayStartResult.Started -> Unit
                    is OverlayStartResult.MissingOverlayPermission ->
                        OverlayRuntime.reportError("Overlay permission missing after reboot")

                    is OverlayStartResult.Blocked -> {
                        Log.w(TAG, "Restart after boot refused: ${result.message}")
                        OverlayRuntime.reportError(result.message)
                    }
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not restore the volume control after boot", throwable)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
