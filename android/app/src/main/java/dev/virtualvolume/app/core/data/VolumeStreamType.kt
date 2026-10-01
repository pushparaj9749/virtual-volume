package dev.virtualvolume.app.core.data

import android.media.AudioManager

/**
 * Audio streams the floating control can drive.
 *
 * Media is the default because it is the stream people reach for a physical volume
 * rocker the most often. Every other stream is modelled the same way so it can be
 * offered later without touching the gesture or overlay layers.
 */
enum class VolumeStreamType(
    val streamConstant: Int,
    val label: String,
    val settingsKey: String,
) {
    MEDIA(AudioManager.STREAM_MUSIC, "Media", "volume_music"),
    RING(AudioManager.STREAM_RING, "Ring", "volume_ring"),
    ALARM(AudioManager.STREAM_ALARM, "Alarm", "volume_alarm"),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "Notifications", "volume_notification"),
    VOICE_CALL(AudioManager.STREAM_VOICE_CALL, "Calls", "volume_voice"),
    SYSTEM(AudioManager.STREAM_SYSTEM, "System", "volume_system"),
    ;

    companion object {
        val DEFAULT: VolumeStreamType = MEDIA

        fun fromName(name: String?): VolumeStreamType =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
