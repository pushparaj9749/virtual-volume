package dev.virtualvolume.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.virtualvolume.app.core.data.VolumeSettings
import dev.virtualvolume.app.core.data.VolumeStreamType
import dev.virtualvolume.app.core.platform.OverlayPermission
import dev.virtualvolume.app.core.platform.OverlayRuntime
import dev.virtualvolume.app.overlay.DisplayEdge
import dev.virtualvolume.app.overlay.DisplayRotation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs against an installed native app and the emulator's real AudioManager/WindowManager. */
@RunWith(AndroidJUnit4::class)
class NativeIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: Context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = app.appContainer
    private var originalVolume = 0

    private fun shell(command: String): String = device.executeShellCommand(command)
    private fun waitFor(message: String, predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 15_000, condition = predicate)
        assertTrue(message, predicate())
    }
    private fun index() = container.volumeController.snapshot(VolumeStreamType.MEDIA).index
    private fun middle() { container.volumeController.setIndex(VolumeStreamType.MEDIA,
        container.volumeController.snapshot(VolumeStreamType.MEDIA).max / 2) }
    private fun screenshot(name: String) {
        val folder = File(app.getExternalFilesDir(null), "qa").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(folder, "$name.png")))
    }

    @Before fun prepare() {
        device.wakeUp()
        shell("wm dismiss-keyguard")
        device.setOrientationNatural()
        originalVolume = index()
        runBlocking {
            container.overlayServiceController.setEnabled(false)
            container.settingsRepository.update { VolumeSettings.DEFAULT }
        }
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
    }

    @After fun cleanUp() {
        runBlocking { container.overlayServiceController.setEnabled(false) }
        container.volumeController.setIndex(VolumeStreamType.MEDIA, originalVolume)
        shell("cmd statusbar remove-tile ${app.packageName}/dev.virtualvolume.app.tile.VolumeTileService")
        shell("cmd statusbar collapse")
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test fun onboarding_realGestures_rotation_switching_sleep_andPermissionRevocation() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Onboarding welcome loaded") {
                compose.onAllNodesWithText("Virtual Volume").fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("01-welcome-dark")
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Grant permission").assertIsDisplayed()
            compose.onNodeWithText("Continue").assertIsNotEnabled()
            compose.onNodeWithText("Grant permission").performClick()
            assertTrue("Permission CTA opens Android Settings, not an internal fake permission dialog",
                device.wait(Until.hasObject(By.pkg("com.android.settings")), 8_000))
            device.pressBack()
            assertFalse(OverlayPermission.isGranted(app))
            shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            scenario.recreate()
            waitFor("Real overlay permission granted") { OverlayPermission.isGranted(app) }
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Enable Virtual Volume").performClick()
            waitFor("Window actually attached") { OverlayRuntime.isServiceRunning.value && OverlayRuntime.placement.value != null }
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Try it now").assertIsDisplayed()
            screenshot("02-interactive-tutorial")

            middle()
            val p = OverlayRuntime.placement.value!!
            val x = p.xOffset + p.windowWidthPx / 2
            val beforeTap = index()
            device.click(x, p.yOffset + p.windowHeightPx / 4)
            waitFor("Upper split tap raises real media volume by exactly one") { index() == beforeTap + 1 }
            device.click(x, p.yOffset + p.windowHeightPx * 3 / 4)
            waitFor("Lower split tap lowers real media volume by exactly one") { index() == beforeTap }
            device.swipe(x, p.yOffset + p.windowHeightPx * 3 / 4, x, p.yOffset + p.windowHeightPx / 4, 20)
            waitFor("Swipe up raises real media volume") { index() > beforeTap }
            val high = index()
            device.swipe(x, p.yOffset + p.windowHeightPx / 4, x, p.yOffset + p.windowHeightPx * 3 / 4, 20)
            waitFor("Continuous downward drag lowers real media volume") { index() < high }

            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Add it to Quick Settings").assertIsDisplayed()
            screenshot("03-quick-settings-guide")
            compose.onNodeWithText("Got it").performClick()
            compose.onNodeWithText("Status: ON").assertIsDisplayed()
            assertTrue(runBlocking { container.settingsRepository.settings.first() }.onboardingCompleted)
            screenshot("04-dashboard-dark")

            device.setOrientationLeft()
            waitFor("Natural right edge becomes landscape top") { OverlayRuntime.placement.value?.rotation == DisplayRotation.LEFT }
            assertEquals(DisplayEdge.TOP, OverlayRuntime.placement.value!!.edge)
            screenshot("05-landscape-left")
            middle()
            val l = OverlayRuntime.placement.value!!
            val beforeLandscape = index()
            device.swipe(l.xOffset + l.windowWidthPx * 3 / 4, l.yOffset + l.windowHeightPx / 2,
                l.xOffset + l.windowWidthPx / 4, l.yOffset + l.windowHeightPx / 2, 20)
            waitFor("Landscape drag along physical edge raises volume") { index() > beforeLandscape }

            device.setOrientationRight()
            waitFor("Natural right edge becomes the other landscape bottom") { OverlayRuntime.placement.value?.rotation == DisplayRotation.RIGHT }
            assertEquals(DisplayEdge.BOTTOM, OverlayRuntime.placement.value!!.edge)
            screenshot("06-landscape-right")
            device.setOrientationNatural()
            waitFor("Portrait relative placement restored") { OverlayRuntime.placement.value?.rotation == DisplayRotation.NATURAL }
            assertEquals(p.xOffset, OverlayRuntime.placement.value!!.xOffset)
            assertEquals(p.yOffset, OverlayRuntime.placement.value!!.yOffset)

            device.pressHome()
            assertTrue(OverlayRuntime.isServiceRunning.value)
            app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 5_000))
            assertTrue(OverlayRuntime.isServiceRunning.value)
            screenshot("07-overlay-over-another-app")
            val beforeSleep = index()
            device.sleep()
            waitFor("Screen really powered off") { app.getSystemService(PowerManager::class.java)?.isInteractive == false }
            assertTrue("Service preserves enabled state while asleep", OverlayRuntime.isServiceRunning.value)
            assertEquals(beforeSleep, index())
            device.wakeUp()
            shell("wm dismiss-keyguard")
            waitFor("Screen on") { app.getSystemService(PowerManager::class.java)?.isInteractive == true }

            // Shell app-ops is test-only; the application never grants its own permission.
            shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
            waitFor("Revoked permission removes the real overlay") { !OverlayRuntime.isServiceRunning.value }
            assertNull(OverlayRuntime.placement.value)
            assertNotNull(runBlocking { container.settingsRepository.settings.first() }.serviceNotice)

            shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            shell("am start -W -n ${app.packageName}/dev.virtualvolume.app.MainActivity")
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            runBlocking { container.overlayServiceController.setEnabled(true) }
            waitFor("Explicit recovery succeeds") { OverlayRuntime.isServiceRunning.value }
            runBlocking { container.settingsRepository.update { it.copy(themeMode = dev.virtualvolume.app.core.data.ThemeMode.LIGHT, hapticsEnabled = false, volumeStep = 2) } }
            scenario.recreate()
            compose.onNodeWithText("Status: ON").assertIsDisplayed()
            screenshot("08-dashboard-light")
            val persisted = runBlocking { container.settingsRepository.settings.first() }
            assertEquals(2, persisted.volumeStep)
            assertFalse(persisted.hapticsEnabled)
            assertTrue(persisted.onboardingCompleted)

            // The notification's OFF action must persist OFF through a service restart.
            app.startService(Intent(app, dev.virtualvolume.app.overlay.OverlayService::class.java)
                .setAction(dev.virtualvolume.app.overlay.OverlayService.ACTION_STOP))
            waitFor("Notification stop shuts down service") { !OverlayRuntime.isServiceRunning.value }
            assertFalse(runBlocking { container.settingsRepository.settings.first() }.enabled)
        }
    }

    @Test fun quickSettingsTile_togglesTheRealOverlay_andPersistedState() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        val component = "${app.packageName}/dev.virtualvolume.app.tile.VolumeTileService"
        // Test infrastructure simulates the user's manual Edit step. No production code adds tiles.
        shell("cmd statusbar add-tile $component")
        shell("cmd statusbar expand-settings")
        val tile = device.wait(Until.findObject(By.text("Virtual Volume")), 10_000)
        assertNotNull("The installed Quick Settings tile is discoverable", tile)
        tile!!.click()
        waitFor("Tile ON attaches window") { OverlayRuntime.isServiceRunning.value }
        assertTrue(runBlocking { container.settingsRepository.settings.first() }.enabled)
        screenshot("09-tile-on")
        device.wait(Until.findObject(By.text("Virtual Volume")), 5_000)!!.click()
        waitFor("Tile OFF detaches window") { !OverlayRuntime.isServiceRunning.value }
        assertFalse(runBlocking { container.settingsRepository.settings.first() }.enabled)
    }
}
