package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.NetworkMemory
import android.content.Context
import android.net.VpnService
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import io.github.romanvht.byedpi.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Makes a ciadpi command line the active strategy, the same way upstream's screens do, and can
 * put back whatever was in use before, so trying a profile is never a risk.
 */
object StrategyApplier {

    /** What was active before the last apply. */
    private data class Previous(val cmdEnabled: Boolean, val args: String)

    /** One "Applied" notice for the app's snackbar; [canUndo] offers to put the old one back. */
    data class Notice(val message: String, val canUndo: Boolean)

    @Volatile private var previous: Previous? = null

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    /** Bumped on every apply or undo, so open screens can re-read the active strategy. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /**
     * Saves [command] (tidied with [normalizeCommand]) as the active strategy and records it in
     * upstream's history. When [label] is given the entry is also pinned and named so it shows in
     * the strategy picker. Restarts the tunnel when it is running, then posts a [Notice].
     * Returns the command as saved.
     */
    fun apply(ctx: Context, command: String, label: String?, why: String? = null): String {
        val clean = normalizeCommand(command).ifEmpty { command.trim() }
        val prefs = ctx.getPreferences()
        val before = Previous(prefs.getCmdEnable(), prefs.getCmdArgs())
        val changed = !(before.cmdEnabled && before.args == clean)
        prefs.edit(commit = true) {
            putBoolean("byedpi_enable_cmd_settings", true)
            putString("byedpi_cmd_args", clean)
        }
        val history = HistoryUtils(ctx)
        history.addCommand(clean)
        if (label != null) {
            history.pinCommand(clean)
            history.renameCommand(clean, label)
        }
        previous = if (changed) before else null
        // What you choose on this network is what it switches back to when you return to it.
        runCatching { NetworkMemory.remember(ctx, clean) }
        _changes.update { it + 1 }
        val restart = restartIfRunning(ctx)
        _notices.tryEmit(Notice(if (why != null) "$why $restart" else restart, canUndo = changed))
        return clean
    }

    /** Puts back the strategy that was active before the last [apply]. Returns a short message. */
    fun undo(ctx: Context): String {
        val p = previous ?: return ctx.getString(R.string.pg_undo_nothing)
        previous = null
        ctx.getPreferences().edit(commit = true) {
            putBoolean("byedpi_enable_cmd_settings", p.cmdEnabled)
            putString("byedpi_cmd_args", p.args)
        }
        if (p.cmdEnabled) runCatching { NetworkMemory.remember(ctx, p.args) }
        _changes.update { it + 1 }
        restartIfRunning(ctx)
        return ctx.getString(R.string.pg_undo_done)
    }

    private fun restartIfRunning(ctx: Context): String {
        val mode = ctx.getPreferences().mode()
        return when {
            appStatus.first != AppStatus.Running || TestService.isRunning || CommandGenerator.isRunning ->
                ctx.getString(R.string.pg_applied_connect)

            mode == Mode.VPN && VpnService.prepare(ctx) != null ->
                ctx.getString(R.string.pg_applied_reconnect)

            else -> {
                ServiceManager.restart(ctx, mode)
                ctx.getString(R.string.pg_applied_reconnecting)
            }
        }
    }
}
