package dev.virtualvolume.app.core.audio

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import dev.virtualvolume.app.core.data.VolumeStreamType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits the hardware volume index whenever the user (or another app) changes it, so the
 * floating control always mirrors the real device volume rather than a remembered number.
 *
 * Observation uses a [ContentObserver] on the system settings row that holds the stream
 * volume. Not every OEM writes that row, so the app also re-reads the stream directly on
 * every interaction — this observer only makes the idle display stay honest.
 */
class AudioVolumeMonitor(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())

    fun observe(stream: VolumeStreamType, read: (VolumeStreamType) -> Int): Flow<Int> =
        callbackFlow {
            val observer = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    trySend(read(stream))
                }
            }
            val registered = register(observer, stream)
            trySend(read(stream))
            awaitClose {
                if (registered) runCatching { resolver.unregisterContentObserver(observer) }
            }
        }.distinctUntilChanged()

    private fun register(observer: ContentObserver, stream: VolumeStreamType): Boolean {
        val uri = runCatching { Settings.System.getUriFor(stream.settingsKey) }
            .onFailure { Log.w(TAG, "No settings URI for $stream, observing the whole table", it) }
            .getOrNull()
            ?: Settings.System.CONTENT_URI
        return runCatching { resolver.registerContentObserver(uri, true, observer) }
            .onFailure { Log.w(TAG, "Could not observe volume changes", it) }
            .isSuccess
    }

    companion object {
        private const val TAG = "AudioVolumeMonitor"
    }
}
