package dev.virtualvolume.app.core.audio

import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Reports whether a phone or VoIP call is in progress.
 *
 * `AudioManager.mode` is the only signal that answers this without permissions: telephony
 * flips it to `MODE_IN_CALL`, and VoIP apps (WhatsApp, Signal, Meet…) flip it to
 * `MODE_IN_COMMUNICATION`. Because Android exposes no public callback for mode changes it
 * is polled — a read-only `getMode()` call, no wake locks and nothing woken up while the
 * screen is off, because a call keeps the process active anyway.
 *
 * The alternative, `TelephonyManager` call state, would need `READ_PHONE_STATE`; the
 * floating control deliberately asks for no phone, contacts or network permission.
 *
 * Selection is still refreshed the instant a gesture begins, so even a call that started
 * between two polls is handled before the first volume step is applied.
 */
class CallAudioMonitor(
    private val audioManager: AudioManager?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /** The current mode, safe to call from any thread and never throws. */
    fun currentMode(): CallAudioMode = runCatching { mapMode(audioManager?.mode ?: AudioManager.MODE_NORMAL) }
        .onFailure { Log.w(TAG, "Could not read the audio mode", it) }
        .getOrDefault(CallAudioMode.IDLE)

    /** Emits the mode now, then whenever it changes. */
    fun modeChanges(pollIntervalMs: Long = DEFAULT_POLL_MS): Flow<CallAudioMode> = flow {
        while (true) {
            emit(currentMode())
            delay(pollIntervalMs)
        }
    }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    companion object {
        private const val TAG = "CallAudioMonitor"

        /** Cheap enough to feel instant, slow enough to be invisible in the battery stats. */
        const val DEFAULT_POLL_MS = 1_000L

        // MODE_IN_CALL is deprecated for *setting* the mode; reading it is still the only
        // way to tell a cellular call from an idle phone. MODE_COMMUNICATION_REDIRECT
        // (API 34) is the same state with its audio routed to another device.
        @Suppress("DEPRECATION")
        fun mapMode(audioMode: Int): CallAudioMode = when (audioMode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_COMMUNICATION_REDIRECT,
            -> CallAudioMode.IN_CALL

            else -> CallAudioMode.IDLE
        }
    }
}
