package dev.virtualvolume.app.tile

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Asks System UI to re-query the tile so its on/off state matches the app.
 *
 * [TileService.requestListeningState] exists on Android 10+. On older releases the tile
 * refreshes on its own the next time the panel is opened, which is acceptable.
 */
object TileStateSync {

    fun refresh(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, VolumeTileService::class.java),
            )
        }
    }
}
