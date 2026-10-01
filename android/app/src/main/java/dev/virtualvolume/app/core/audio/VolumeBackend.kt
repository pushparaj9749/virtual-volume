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
        requireNotNull(audioManager).setStreamVolume(stream.streamConstant, index, 0)
    }
}
