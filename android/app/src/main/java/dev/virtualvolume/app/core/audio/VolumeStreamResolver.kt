package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType

/**
 * What the phone is doing with audio right now, as far as volume goes.
 *
 * Derived from `AudioManager.mode` by the platform layer, but modelled here as a plain
 * enum so the selection rules stay free of Android types and are unit tested on the JVM.
 */
enum class CallAudioMode {
    /** No call. Media, alarms and ringtones behave normally. */
    IDLE,

    /** A phone or VoIP call is in progress. */
    IN_CALL,
    ;

    val isInCall: Boolean get() = this == IN_CALL
}

/**
 * Picks the stream the floating control should drive.
 *
 * This mirrors what the physical rocker does: while a call is up, the hardware buttons
 * change *call* volume, not media volume. When [followCalls] is enabled the control does
 * the same, and when it is disabled the user's chosen stream is always used.
 *
 * Pure Kotlin by design — no Android imports — so every rule below is covered by JVM tests.
 */
object VolumeStreamResolver {

    fun resolve(
        configured: VolumeStreamType,
        callMode: CallAudioMode,
        followCalls: Boolean,
    ): VolumeStreamType {
        if (!followCalls) return configured
        return if (callMode.isInCall) {
            VolumeStreamType.VOICE_CALL
        } else {
            configured
        }
    }

    /**
     * True when the control is driving a stream the user did not pick, i.e. it switched to
     * call volume because a call started. The UI and the notification use this to say so
     * instead of silently changing what the bar controls.
     */
    fun isFollowingCall(configured: VolumeStreamType, active: VolumeStreamType): Boolean =
        active != configured
}
