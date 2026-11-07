package io.github.romanvht.byedpi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import android.util.Log
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.START_ACTION
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREF_LAST_BOOT = "autostart_last_boot_count"
    }

    /**
     * Start at most once per boot. On Android 15 a force-stopped app gets BOOT_COMPLETED again
     * when it's next opened, and on Android 10+ it only arrives after the first unlock, which can
     * be hours after boot. Android 7+ counts boots, so remember which boot was handled; older
     * versions fall back to "within 5 minutes of boot".
     */
    private fun firstTimeThisBoot(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return SystemClock.elapsedRealtime() <= 5 * 60 * 1000
        }
        val boot = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull() ?: return SystemClock.elapsedRealtime() <= 5 * 60 * 1000
        val prefs = context.getPreferences()
        if (prefs.getString(PREF_LAST_BOOT, null) == boot.toString()) return false
        prefs.edit { putString(PREF_LAST_BOOT, boot.toString()) }
        return true
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_REBOOT ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val preferences = context.getPreferences()
            if (!firstTimeThisBoot(context)) return

            val autorunEnabled = preferences.getBoolean("autostart", false)

            if(autorunEnabled) {
                val mode = preferences.mode()
                if (mode == Mode.VPN && VpnService.prepare(context) != null) return
                val pending = goAsync()

                CoroutineScope(Dispatchers.Main.immediate).launch {
                    try {
                        ServiceManager.handleAction(context.applicationContext, START_ACTION, mode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service after boot", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
