package dev.virtualvolume.app.core.audio

import android.media.AudioManager
import android.util.Log
import dev.virtualvolume.app.core.data.VolumeStreamType

/**
 * Thin abstraction over the platform volume APIs so the controller (and its tests) never
 * touch [AudioManager] directly.
 */
interface VolumeBackend {
    fun maxVolume(stream: VolumeStreamType): Int
    fun currentVolume(stream: VolumeStreamType): Int
    fun setVolume(stream: VolumeStreamType, index: Int)
}

/** [VolumeBackend] backed by the real system [AudioManager]. */
class AudioManagerBackend(private val audioManager: AudioManager) : VolumeBackend {

    override fun maxVolume(stream: VolumeStreamType): Int =
        audioManager.getStreamMaxVolume(stream.streamConstant)

    override fun currentVolume(stream: VolumeStreamType): Int =
        audioManager.getStreamVolume(stream.streamConstant)

    override fun setVolume(stream: VolumeStreamType, index: Int) {
        // No FLAG_SHOW_UI: the floating control is the UI, and the system volume dialog
        // would fight it for the same gesture.
        audioManager.setStreamVolume(stream.streamConstant, index, 0)
    }
}

/** Safe reader that never throws into the gesture path. */
class SafeVolumeReader(private val backend: VolumeBackend) {

    fun maxVolume(stream: VolumeStreamType): Int =
        runCatching { backend.maxVolume(stream) }
            .onFailure { Log.w(TAG, "Could not read max volume for $stream", it) }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?: 0

    fun currentVolume(stream: VolumeStreamType): Int =
        runCatching { backend.currentVolume(stream) }
            .onFailure { Log.w(TAG, "Could not read volume for $stream", it) }
            .getOrNull()
            ?: -1

    companion object {
        private const val TAG = "SafeVolumeReader"
    }
}
