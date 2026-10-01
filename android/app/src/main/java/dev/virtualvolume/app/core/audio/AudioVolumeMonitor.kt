package dev.virtualvolume.app.core.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import dev.virtualvolume.app.core.data.VolumeStreamType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Android has no public per-stream volume callback. Settings observation and public audio
 * route callbacks provide prompt reads; a modest screen-on sampler covers OEMs that do
 * not update Settings. It has no wake lock and never wakes a sleeping device.
 */
class AudioVolumeMonitor(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    fun observe(stream: VolumeStreamType, read: (VolumeStreamType) -> Int): Flow<Int> = callbackFlow {
        fun sample() { trySend(read(stream)) }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = sample()
        }
        val audio = app.getSystemService(AudioManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = sample()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = sample()
        }
        val registered = runCatching { app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer) }.isSuccess
        runCatching { audio?.registerAudioDeviceCallback(callback, handler) }
        sample()
        val sampler = launch {
            while (true) {
                delay(700L)
                if (power?.isInteractive != false) sample()
            }
        }
        awaitClose {
            sampler.cancel()
            if (registered) runCatching { app.contentResolver.unregisterContentObserver(observer) }
            runCatching { audio?.unregisterAudioDeviceCallback(callback) }
        }
    }.distinctUntilChanged()
}
