package dev.virtualvolume.app.core.audio

import android.media.AudioManager
import android.os.Build
import dev.virtualvolume.app.core.data.VolumeStreamType

interface VolumeBackend {
    fun minVolume(stream: VolumeStreamType): Int = 0
    fun maxVolume(stream: VolumeStreamType): Int
    fun currentVolume(stream: VolumeStreamType): Int
    fun isFixedVolume(): Boolean = false
    fun setVolume(stream: VolumeStreamType, index: Int)
}

/** Only this adapter knows AudioManager. No hidden APIs or simulated values. */
class AudioManagerBackend(private val audioManager: AudioManager?) : VolumeBackend {
    override fun minVolume(stream: VolumeStreamType): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) requireNotNull(audioManager).getStreamMinVolume(stream.streamConstant) else 0
    override fun maxVolume(stream: VolumeStreamType): Int = requireNotNull(audioManager).getStreamMaxVolume(stream.streamConstant)
    override fun currentVolume(stream: VolumeStreamType): Int = requireNotNull(audioManager).getStreamVolume(stream.streamConstant)
    override fun isFixedVolume(): Boolean = audioManager?.isVolumeFixed ?: true
    override fun setVolume(stream: VolumeStreamType, index: Int) {
        // This is the same public AudioManager path used by system volume controls. The
        // flag asks AudioService to show its native volume panel; it does not draw an
        // app-owned substitute, and every drag step keeps that panel in sync.
        requireNotNull(audioManager).setStreamVolume(
            stream.streamConstant,
            index,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}
