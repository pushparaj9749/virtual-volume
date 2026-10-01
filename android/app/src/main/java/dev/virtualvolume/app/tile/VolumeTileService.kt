package dev.virtualvolume.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.virtualvolume.app.R
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.core.platform.OverlayStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: ON means the floating control is live.
 *
 * The tile never invents its own state — it renders [OverlayRuntime] plus the overlay
 * permission, and toggling writes the same preference the dashboard writes, so the two
 * cannot drift apart. The tile is added manually by the user through
 * Quick Settings → Edit; the app explains how, it does not try to inject itself.
 */
class VolumeTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()

        if (!OverlayPermission.isGranted(this)) {
            openOverlaySettings()
            return
        }

        val shouldRun = !OverlayRuntime.isServiceRunning.value
        render(running = shouldRun)

        scope.launch {
            val container = appContainer
            container.settingsRepository.update { it.copy(enabled = shouldRun) }
            val result = if (shouldRun) {
                container.overlayServiceController.start()
            } else {
                container.overlayServiceController.stop()
                OverlayStartResult.Started
            }
            if (result is OverlayStartResult.Blocked) {
                OverlayRuntime.reportError(result.message)
            }
            render()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(running: Boolean = OverlayRuntime.isServiceRunning.value) {
        val tile = qsTile ?: return
        val granted = OverlayPermission.isGranted(this)

        tile.state = when {
            !granted -> Tile.STATE_UNAVAILABLE
            running -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !granted -> getString(R.string.tile_subtitle_permission)
                running -> getString(R.string.tile_subtitle_on)
                else -> getString(R.string.tile_subtitle_off)
            }
        }
        tile.updateTile()
    }

    private fun openOverlaySettings() {
        val intent = OverlayPermission.settingsIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
