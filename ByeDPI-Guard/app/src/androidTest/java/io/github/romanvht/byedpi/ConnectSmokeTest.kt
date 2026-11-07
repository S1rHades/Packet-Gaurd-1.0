package io.github.romanvht.byedpi

import android.Manifest
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.romanvht.byedpi.ui.ModernActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end on an emulator: open the app, tap the shield, and reach "Protected" with the real
 * engine and VPN running, then disconnect. Runs in CI (connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class ConnectSmokeTest {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    private fun shell(cmd: String) = inst.uiAutomation.executeShellCommand(cmd).close()

    /**
     * Taps the element matching [selector] once it's there. Compose can redraw it between finding
     * and tapping (StaleObjectException), so it looks it up again and retries until [timeoutMs].
     */
    private fun tap(selector: androidx.test.uiautomator.BySelector, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val target = device.wait(Until.findObject(selector), 1_000) ?: continue
            try {
                target.click()
                return true
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(300)
            }
        }
        return false
    }

    /** What's on screen, for the failure message: text and descriptions of every node. */
    private fun screen(): String {
        val out = java.io.ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(out) }
        val xml = out.toString()
        val shown = Regex("""(?:text|content-desc)="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        return "On screen: " + shown.joinToString(" | ").take(3000)
    }

    @Test
    fun shieldConnectsAndDisconnects() {
        // No first-run guide, permission prompts or update check in the way.
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean("pg_first_run_done", true)
            .putBoolean("battery_optimization_requested", true)
            .putBoolean("updates_auto_check", false)
            .putString("byedpi_mode", "vpn")
            .commit()
        // Android's one-time VPN consent, given the way the consent dialog would.
        shell("appops set ${ctx.packageName} ACTIVATE_VPN allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityScenario.launch(ModernActivity::class.java).use {
            assertTrue(
                "The shield's Connect button should appear. ${screen()}",
                tap(By.desc(ctx.getString(R.string.pg_shield_connect)), 60_000)
            )

            // The Connect status is one spoken item: "Protected. …"
            val protectedText = ctx.getString(R.string.pg_connect_protected).lowercase()
            val connected = device.wait(Until.hasObject(By.descContains(protectedText.replaceFirstChar { it.uppercase() })), 60_000)
            assertTrue("Should reach Protected within a minute. ${screen()}", connected)

            assertTrue(
                "The shield should offer Disconnect. ${screen()}",
                tap(By.desc(ctx.getString(R.string.pg_shield_disconnect)), 15_000)
            )
            assertTrue(
                "Should disconnect. ${screen()}",
                device.wait(Until.hasObject(By.desc(ctx.getString(R.string.pg_shield_connect))), 30_000)
            )
        }
    }
}
