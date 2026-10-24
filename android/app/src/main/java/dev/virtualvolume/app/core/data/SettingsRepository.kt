package dev.virtualvolume.app.core.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Pure mapping between [VolumeSettings] and DataStore preferences.
 *
 * Kept free of Android types so the round-trip is covered by JVM unit tests.
 */
internal object VolumeSettingsCodec {

    val KEY_ENABLED = booleanPreferencesKey("enabled")
    val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    val KEY_EDGE = stringPreferencesKey("edge")
    val KEY_OFFSET_FRACTION = floatPreferencesKey("offset_fraction")
    val KEY_LENGTH_DP = floatPreferencesKey("length_dp")
    val KEY_THICKNESS_DP = floatPreferencesKey("thickness_dp")
    val KEY_IDLE_OPACITY = floatPreferencesKey("idle_opacity")
    val KEY_ACTIVE_OPACITY = floatPreferencesKey("active_opacity")
    val KEY_TOUCH_WIDTH_DP = floatPreferencesKey("touch_zone_width_dp")
    val KEY_TOUCH_LENGTH_DP = floatPreferencesKey("touch_zone_length_dp")
    val KEY_SENSITIVITY = floatPreferencesKey("swipe_sensitivity")
    val KEY_ANIMATION_MS = longPreferencesKey("animation_duration_ms")
    val KEY_ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val KEY_VOLUME_STEP = intPreferencesKey("volume_step")
    val KEY_AUDIO_STREAM = stringPreferencesKey("audio_stream")
    val KEY_TAP_MODE = stringPreferencesKey("tap_mode")
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_TILE_ADDED = booleanPreferencesKey("tile_added")
    val KEY_SERVICE_NOTICE = stringPreferencesKey("service_notice")
    val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    fun read(prefs: Preferences): VolumeSettings {
        val defaults = VolumeSettings.DEFAULT
        return VolumeSettings(
            enabled = prefs[KEY_ENABLED] ?: defaults.enabled,
            startOnBoot = prefs[KEY_START_ON_BOOT] ?: defaults.startOnBoot,
            edge = ScreenEdge.fromName(prefs[KEY_EDGE]),
            offsetFraction = prefs[KEY_OFFSET_FRACTION] ?: defaults.offsetFraction,
            lengthDp = prefs[KEY_LENGTH_DP] ?: defaults.lengthDp,
            thicknessDp = prefs[KEY_THICKNESS_DP] ?: defaults.thicknessDp,
            idleOpacity = prefs[KEY_IDLE_OPACITY] ?: defaults.idleOpacity,
            activeOpacity = prefs[KEY_ACTIVE_OPACITY] ?: defaults.activeOpacity,
            touchZoneWidthDp = prefs[KEY_TOUCH_WIDTH_DP] ?: defaults.touchZoneWidthDp,
            touchZoneLengthDp = prefs[KEY_TOUCH_LENGTH_DP] ?: defaults.touchZoneLengthDp,
            swipeSensitivity = prefs[KEY_SENSITIVITY] ?: defaults.swipeSensitivity,
            animationDurationMs = prefs[KEY_ANIMATION_MS] ?: defaults.animationDurationMs,
            animationsEnabled = prefs[KEY_ANIMATIONS_ENABLED] ?: defaults.animationsEnabled,
            hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            volumeStep = prefs[KEY_VOLUME_STEP] ?: defaults.volumeStep,
            audioStream = VolumeStreamType.fromName(prefs[KEY_AUDIO_STREAM]),
            tapMode = TapMode.fromName(prefs[KEY_TAP_MODE]),
            themeMode = ThemeMode.fromName(prefs[KEY_THEME_MODE]),
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            tileAdded = prefs[KEY_TILE_ADDED] ?: false,
            serviceNotice = prefs[KEY_SERVICE_NOTICE],
        ).sanitized()
    }

    fun write(settings: VolumeSettings, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[KEY_ENABLED] = settings.enabled
        prefs[KEY_START_ON_BOOT] = settings.startOnBoot
        prefs[KEY_EDGE] = settings.edge.name
        prefs[KEY_OFFSET_FRACTION] = settings.offsetFraction
        prefs[KEY_LENGTH_DP] = settings.lengthDp
        prefs[KEY_THICKNESS_DP] = settings.thicknessDp
        prefs[KEY_IDLE_OPACITY] = settings.idleOpacity
        prefs[KEY_ACTIVE_OPACITY] = settings.activeOpacity
        prefs[KEY_TOUCH_WIDTH_DP] = settings.touchZoneWidthDp
        prefs[KEY_TOUCH_LENGTH_DP] = settings.touchZoneLengthDp
        prefs[KEY_SENSITIVITY] = settings.swipeSensitivity
        prefs[KEY_ANIMATION_MS] = settings.animationDurationMs
        prefs[KEY_ANIMATIONS_ENABLED] = settings.animationsEnabled
        prefs[KEY_HAPTICS_ENABLED] = settings.hapticsEnabled
        prefs[KEY_VOLUME_STEP] = settings.volumeStep
        prefs[KEY_AUDIO_STREAM] = settings.audioStream.name
        prefs[KEY_TAP_MODE] = settings.tapMode.name
        prefs[KEY_THEME_MODE] = settings.themeMode.name
        prefs[KEY_ONBOARDING_COMPLETED] = settings.onboardingCompleted
        prefs[KEY_TILE_ADDED] = settings.tileAdded
        settings.serviceNotice?.let { prefs[KEY_SERVICE_NOTICE] = it } ?: prefs.remove(KEY_SERVICE_NOTICE)
    }
}

/** Single source of truth for persisted user configuration. */
class SettingsRepository(
    private val store: androidx.datastore.core.DataStore<Preferences>,
) {

    /** Emits the current settings, recovering from a corrupted file with defaults. */
    val settings: Flow<VolumeSettings> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { VolumeSettingsCodec.read(it) }

    suspend fun update(transform: (VolumeSettings) -> VolumeSettings): VolumeSettings {
        var updated = VolumeSettings.DEFAULT
        store.edit { prefs ->
            val current = VolumeSettingsCodec.read(prefs)
            updated = transform(current).sanitized()
            VolumeSettingsCodec.write(updated, prefs)
        }
        return updated
    }
}
