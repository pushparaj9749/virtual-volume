package dev.virtualvolume.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Best-effort restoration, opt-in only. The receiver never fights Android's start limits. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val app = context.applicationContext
        scope.launch {
            try {
                withTimeout(8_000) {
                    val container = app.appContainer
                    val settings = container.settingsRepository.settings.first()
                    val restoringBoot = intent.action == Intent.ACTION_BOOT_COMPLETED
                    if (!settings.enabled || (restoringBoot && !settings.startOnBoot)) return@withTimeout
                    val message = if (!OverlayPermission.isGranted(app)) {
                        "Overlay permission is missing. Open Virtual Volume to grant it and resume the control."
                    } else when (container.overlayServiceController.start()) {
                        OverlayStartResult.Started -> null
                        OverlayStartResult.MissingOverlayPermission -> "Grant overlay permission in Virtual Volume to resume the control."
                        is OverlayStartResult.Blocked -> "Android blocked automatic restoration. Open Virtual Volume and tap Resume control; no background restrictions were bypassed."
                    }
                    container.settingsRepository.update { it.copy(serviceNotice = message) }
                }
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) dev.virtualvolume.app.core.platform.OverlayRuntime.reportError("Automatic restoration was unavailable. Open Virtual Volume to resume.")
            } finally { pending.finish(); scope.cancel() }
        }
    }
}
