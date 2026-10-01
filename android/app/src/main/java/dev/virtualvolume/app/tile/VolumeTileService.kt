package dev.virtualvolume.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.virtualvolume.app.MainActivity
import dev.virtualvolume.app.R
import dev.virtualvolume.app.appContainer
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.core.platform.OverlayStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A manually added, user-initiated tile. State changes only when the real window attaches. */
class VolumeTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    private var toggling = false

    override fun onTileAdded() {
        super.onTileAdded()
        scope.launch { appContainer.settingsRepository.update { it.copy(tileAdded = true) } }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        scope.launch { appContainer.settingsRepository.update { it.copy(tileAdded = false) } }
    }

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        render()
        listening = scope.launch {
            combine(OverlayRuntime.isServiceRunning, appContainer.settingsRepository.settings) { running, _ -> running }
                .collect { render() }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (toggling) return
        if (!OverlayPermission.isGranted(this)) { openApp(); return }
        toggling = true
        scope.launch {
            try {
                val result = appContainer.overlayServiceController.toggle()
                if (result !is OverlayStartResult.Started) openApp()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                OverlayRuntime.reportError("The tile could not save its state. Open Virtual Volume to retry.")
                openApp()
            } finally { toggling = false; render() }
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val granted = OverlayPermission.isGranted(this)
        val running = granted && OverlayRuntime.isServiceRunning.value
        // Missing permission remains clickable so tapping can guide the user to setup.
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(when {
            !granted -> R.string.tile_subtitle_permission
            running -> R.string.tile_subtitle_on
            else -> R.string.tile_subtitle_off
        })
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
