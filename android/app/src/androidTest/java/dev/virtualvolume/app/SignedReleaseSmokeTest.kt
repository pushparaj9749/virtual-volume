package dev.virtualvolume.app

import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Black-box UI test of the signed, R8-minified RELEASE APK, not the debug app. */
@RunWith(AndroidJUnit4::class)
class SignedReleaseSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val audio = instrumentation.targetContext.getSystemService(AudioManager::class.java)
    private val pkg = "dev.virtualvolume.app"

    private fun shell(command: String) = device.executeShellCommand(command)
    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue(message, predicate())
    }
    private fun click(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 12_000)
        assertNotNull("Release UI exposes '$text'", node)
        node!!.click()
    }
    private fun control() = device.wait(Until.findObject(By.desc("Virtual volume control")), 12_000)
        ?: throw AssertionError("The release overlay is not accessible/attached")
    private fun serviceRunning() = shell("dumpsys activity services $pkg").contains("dev.virtualvolume.app.overlay.OverlayService")
    private fun level() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private fun screenshot(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "release-$name.png")))
    }

    @Test fun signedReleaseInstalls_onboards_changesVolume_rotates_andTogglesFromTile() {
        // The normal debug CI deliberately skips this; release.yml supplies the signed APK.
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseSmoke") == "true")
        assertNotNull(instrumentation.targetContext.packageManager.getLaunchIntentForPackage(pkg))
        device.wakeUp()
        shell("wm dismiss-keyguard")
        device.setOrientationNatural()
        val original = level()
        try {
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            shell("am start -W -n $pkg/.MainActivity")
            assertNotNull(device.wait(Until.findObject(By.text("Virtual Volume")), 12_000))
            click("Continue")
            assertNotNull(device.wait(Until.findObject(By.text("Grant permission")), 8_000))
            shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
            device.pressHome()
            shell("am start -W -n $pkg/.MainActivity")
            click("Continue")
            click("Enable Virtual Volume")
            val portrait = control().visibleBounds
            assertTrue(portrait.height() > portrait.width())
            assertTrue(portrait.right >= device.displayWidth * .8)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
            val before = level()
            device.click(portrait.centerX(), portrait.top + portrait.height() / 4)
            waitFor("Signed APK tap increases actual media volume") { level() == before + 1 }
            device.swipe(portrait.centerX(), portrait.top + portrait.height() * 3 / 4,
                portrait.centerX(), portrait.top + portrait.height() / 4, 20)
            waitFor("Signed APK upward drag increases actual media volume") { level() > before + 1 }
            screenshot("portrait")

            device.setOrientationLeft()
            waitFor("Control rotates horizontally to the physical top edge") {
                val bounds = control().visibleBounds
                bounds.width() > bounds.height() && bounds.top < device.displayHeight / 3
            }
            screenshot("landscape-left")
            device.setOrientationRight()
            waitFor("Control rotates horizontally to the physical bottom edge") {
                val bounds = control().visibleBounds
                bounds.width() > bounds.height() && bounds.bottom > device.displayHeight * 2 / 3
            }
            screenshot("landscape-right")
            device.setOrientationNatural()

            click("Continue")
            click("Continue")
            click("Got it")
            assertNotNull(device.wait(Until.findObject(By.text("Status: ON")), 12_000))
            screenshot("dashboard")
            val component = "$pkg/dev.virtualvolume.app.tile.VolumeTileService"
            shell("cmd statusbar add-tile $component") // Test-only simulation of manual QS Edit.
            shell("cmd statusbar expand-settings")
            click("Virtual Volume")
            waitFor("Release tile OFF stops the actual foreground service") { !serviceRunning() }
            click("Virtual Volume")
            waitFor("Release tile ON starts the actual foreground service") { serviceRunning() }
            shell("cmd statusbar collapse")
            assertNotNull(control())
            device.pressHome()
            assertNotNull(control()) // Remains available after leaving the app.
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
            shell("cmd statusbar remove-tile $pkg/dev.virtualvolume.app.tile.VolumeTileService")
            shell("cmd statusbar collapse")
            shell("am force-stop $pkg")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }
}
