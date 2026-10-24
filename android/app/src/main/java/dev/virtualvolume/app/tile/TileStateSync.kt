package dev.virtualvolume.app.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/**
 * Asks System UI to re-query the tile so its on/off state matches the app.
 *
 * [TileService.requestListeningState] is public on API 24+. The active tile also
 * observes the runtime while System UI is listening.
 */
object TileStateSync {

    fun refresh(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, VolumeTileService::class.java),
            )
        }
    }
}
