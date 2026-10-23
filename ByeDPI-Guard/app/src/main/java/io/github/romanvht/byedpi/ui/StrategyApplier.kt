package io.github.romanvht.byedpi.ui

import android.content.Context
import android.net.VpnService
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

/** Makes a ciadpi command line the active strategy, the same way upstream's screens do. */
object StrategyApplier {

    /**
     * Saves [command] as the active strategy and records it in upstream's history.
     * When [label] is given the entry is also pinned and named so it shows in the strategy picker.
     * Restarts the tunnel when it is running. Returns a short message for the UI.
     */
    fun apply(ctx: Context, command: String, label: String?): String {
        val prefs = ctx.getPreferences()
        prefs.edit(commit = true) {
            putBoolean("byedpi_enable_cmd_settings", true)
            putString("byedpi_cmd_args", command)
        }
        val history = HistoryUtils(ctx)
        history.addCommand(command)
        if (label != null) {
            history.pinCommand(command)
            history.renameCommand(command, label)
        }

        val mode = prefs.mode()
        return when {
            appStatus.first != AppStatus.Running || TestService.isRunning ->
                "Applied. Tap Connect to use it."

            mode == Mode.VPN && VpnService.prepare(ctx) != null ->
                "Applied. Reconnect to use it."

            else -> {
                ServiceManager.restart(ctx, mode)
                "Applied. Reconnecting…"
            }
        }
    }
}
