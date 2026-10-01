package dev.virtualvolume.app.di

import android.content.Context
import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.virtualvolume.app.core.audio.AudioManagerBackend
import dev.virtualvolume.app.core.audio.AudioVolumeMonitor
import dev.virtualvolume.app.core.audio.VolumeController
import dev.virtualvolume.app.core.data.SettingsRepository
import dev.virtualvolume.app.core.platform.Haptics
import dev.virtualvolume.app.core.platform.OverlayServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container.
 *
 * The app has one process and a handful of singletons; a graph library would add build
 * complexity without buying anything here. Everything is created lazily so process start
 * stays cheap for the Quick Settings tile and the boot receiver.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            scope = storageScope,
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile("virtual_volume_settings") },
        )
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(dataStore) }

    private val audioManager: AudioManager by lazy {
        appContext.getSystemService(AudioManager::class.java)
            ?: error("AudioManager is unavailable on this device")
    }

    val volumeController: VolumeController by lazy {
        VolumeController(AudioManagerBackend(audioManager))
    }

    val audioVolumeMonitor: AudioVolumeMonitor by lazy { AudioVolumeMonitor(appContext) }

    val haptics: Haptics by lazy { Haptics(appContext) }

    val overlayServiceController: OverlayServiceController by lazy {
        OverlayServiceController(appContext, settingsRepository)
    }
}
