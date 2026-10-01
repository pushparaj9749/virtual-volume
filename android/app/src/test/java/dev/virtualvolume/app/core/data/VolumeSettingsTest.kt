package dev.virtualvolume.app.core.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSettingsTest {

    @Test
    fun `defaults are sane and never invisible`() {
        val defaults = VolumeSettings.DEFAULT

        assertTrue(defaults.idleOpacity >= VolumeSettings.MIN_IDLE_OPACITY)
        assertTrue(defaults.idleOpacity > 0f)
        assertTrue(defaults.activeOpacity >= defaults.idleOpacity)
        assertEquals(ScreenEdge.RIGHT, defaults.edge)
        assertEquals(VolumeStreamType.MEDIA, defaults.audioStream)
        assertEquals(TapMode.SPLIT, defaults.tapMode)
        assertEquals(ThemeMode.DARK, defaults.themeMode)
        assertEquals(1, defaults.volumeStep)
        assertTrue(defaults.touchZoneLengthDp > defaults.lengthDp)
    }

    @Test
    fun `every value is clamped into range when sanitised`() {
        val wild = VolumeSettings(
            offsetFraction = 4f,
            lengthDp = 5000f,
            thicknessDp = -1f,
            idleOpacity = 0f,
            activeOpacity = 3f,
            touchZoneWidthDp = 1f,
            touchZoneLengthDp = 9999f,
            swipeSensitivity = 0f,
            animationDurationMs = -50L,
            volumeStep = 99,
        ).sanitized()

        assertEquals(VolumeSettings.MAX_OFFSET_FRACTION, wild.offsetFraction, 0f)
        assertEquals(VolumeSettings.MAX_LENGTH_DP, wild.lengthDp, 0f)
        assertEquals(VolumeSettings.MIN_THICKNESS_DP, wild.thicknessDp, 0f)
        assertEquals(VolumeSettings.MIN_IDLE_OPACITY, wild.idleOpacity, 0f)
        assertEquals(VolumeSettings.MAX_OPACITY, wild.activeOpacity, 0f)
        assertEquals(VolumeSettings.MIN_TOUCH_WIDTH_DP, wild.touchZoneWidthDp, 0f)
        assertEquals(VolumeSettings.MAX_TOUCH_LENGTH_DP, wild.touchZoneLengthDp, 0f)
        assertEquals(VolumeSettings.MIN_SENSITIVITY, wild.swipeSensitivity, 0f)
        assertEquals(VolumeSettings.MIN_ANIMATION_MS, wild.animationDurationMs)
        assertEquals(VolumeSettings.MAX_VOLUME_STEP, wild.volumeStep)
    }

    @Test
    fun `the idle control can never be fully transparent`() {
        val invisible = VolumeSettings.DEFAULT.copy(idleOpacity = 0f).sanitized()

        assertTrue(invisible.idleOpacity > 0f)
    }

    @Test
    fun `settings survive a write and read round trip`() {
        val settings = VolumeSettings(
            enabled = true,
            startOnBoot = false,
            edge = ScreenEdge.LEFT,
            offsetFraction = 0.77f,
            lengthDp = 210f,
            thicknessDp = 9f,
            idleOpacity = 0.55f,
            activeOpacity = 0.95f,
            touchZoneWidthDp = 52f,
            touchZoneLengthDp = 240f,
            swipeSensitivity = 1.8f,
            animationDurationMs = 240L,
            animationsEnabled = false,
            hapticsEnabled = false,
            volumeStep = 3,
            audioStream = VolumeStreamType.RING,
            tapMode = TapMode.ALWAYS_DECREASE,
            themeMode = ThemeMode.LIGHT,
            onboardingCompleted = true,
        ).sanitized()

        val prefs = mutablePreferencesOf().apply { VolumeSettingsCodec.write(settings, this) }
        val restored = VolumeSettingsCodec.read(prefs)

        assertEquals(settings, restored)
    }

    @Test
    fun `missing preferences fall back to the defaults`() {
        val restored = VolumeSettingsCodec.read(emptyPreferences())

        assertEquals(VolumeSettings.DEFAULT.sanitized(), restored)
    }

    @Test
    fun `unknown enum names fall back instead of crashing`() {
        assertEquals(ScreenEdge.DEFAULT, ScreenEdge.fromName("SIDEWAYS"))
        assertEquals(VolumeStreamType.DEFAULT, VolumeStreamType.fromName(null))
        assertEquals(TapMode.DEFAULT, TapMode.fromName("tap-all"))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromName("SEPIA"))
    }

    @Test
    fun `screen edges know their opposite`() {
        assertEquals(ScreenEdge.LEFT, ScreenEdge.RIGHT.opposite)
        assertEquals(ScreenEdge.RIGHT, ScreenEdge.LEFT.opposite)
    }
}
