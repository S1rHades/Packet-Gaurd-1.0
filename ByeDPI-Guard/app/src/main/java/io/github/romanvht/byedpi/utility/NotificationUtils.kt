package io.github.romanvht.byedpi.utility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PAUSE_ACTION
import io.github.romanvht.byedpi.data.RESUME_ACTION
import io.github.romanvht.byedpi.data.STOP_ACTION
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver
import io.github.romanvht.byedpi.ui.ModernActivity

/**
 * Packet Guard's notifications share the app's look: the shield icon in the app's accent blue,
 * the same words as the Connect and Benchmark screens, and the same actions. On Android 16 and
 * later the ongoing ones ask to be shown as Live Updates (a chip in the status bar and a place
 * at the top of the shade, with a running timer or progress bar); older versions show the same
 * notification without the chip. The user can turn Live Updates off for the app in Settings.
 */
object LiveNotifications {
    /** The app's [io.github.romanvht.byedpi.ui.Palette.Glow]: one accent across app and notifications. */
    const val ACCENT = 0xFF3B82F6.toInt()

    /** Request codes, so each notification's tap and action intents stay its own. */
    private const val OPEN_CONNECT = 10
    private const val PAUSE = 11
    private const val STOP = 12
    private const val RESUME = 13

    fun openApp(context: Context, tab: String, request: Int = OPEN_CONNECT): PendingIntent =
        PendingIntent.getActivity(
            context, request,
            Intent(context, ModernActivity::class.java)
                .putExtra(ModernActivity.EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceAction(context: Context, action: String, mode: Mode, request: Int, withPid: Boolean = true): PendingIntent =
        PendingIntent.getBroadcast(
            context, request * 10 + mode.ordinal,
            Intent(context, ServiceActionReceiver::class.java).setAction(action)
                .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name)
                .apply { if (withPid) putExtra(ServiceActionReceiver.EXTRA_PID, Process.myPid()) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** A builder with the parts every Packet Guard notification shares. */
    fun base(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

    /** Ongoing and, on Android 16+, a Live Update. */
    fun NotificationCompat.Builder.live(): NotificationCompat.Builder = this
        .setOngoing(true)
        .setRequestPromotedOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    /** The saved strategy's name, or the command line itself, for "Setting: …". */
    fun strategyLabel(context: Context): String {
        val prefs = context.getPreferences()
        if (!prefs.getCmdEnable()) return context.getString(R.string.pg_notif_switches)
        val cmd = prefs.getCmdArgs().trim()
        val named = runCatching { HistoryUtils(context).getHistory().firstOrNull { it.text.trim() == cmd }?.name }.getOrNull()
        return named?.takeIf { it.isNotBlank() } ?: cmd.ifEmpty { context.getString(R.string.pg_notif_switches) }.let {
            if (it.length > 48) it.take(47) + "…" else it
        }
    }

    fun connection(context: Context, channelId: String, mode: Mode, since: Long): Notification {
        val where = when (mode) {
            Mode.VPN -> context.getString(R.string.pg_notif_mode_vpn)
            Mode.Proxy -> {
                val prefs = context.getPreferences()
                val (ip, port) = prefs.checkIpAndPortInCmd()
                context.getString(
                    R.string.pg_notif_mode_proxy,
                    "${ip ?: prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1")}:${port ?: prefs.getStringNotNull("byedpi_proxy_port", "1080")}"
                )
            }
        }
        val setting = context.getString(R.string.pg_notif_setting, strategyLabel(context))
        return base(context, channelId)
            .live()
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(context.getString(R.string.pg_notif_protected))
            .setContentText(where)
            .setSubText(if (mode == Mode.VPN) "VPN" else context.getString(R.string.proxy_channel_name))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$where\n$setting"))
            // A running "protected for" timer, shown in the notification and the status bar chip.
            .setWhen(since)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(openApp(context, "connect"))
            .addAction(0, context.getString(R.string.service_pause_btn), serviceAction(context, PAUSE_ACTION, mode, PAUSE))
            .addAction(0, context.getString(R.string.pg_shield_disconnect), serviceAction(context, STOP_ACTION, mode, STOP))
            .build()
    }

    fun paused(context: Context, channelId: String, mode: Mode): Notification =
        base(context, channelId)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(context.getString(R.string.pg_notif_paused))
            .setContentText(context.getString(R.string.pg_notif_paused_text))
            .setContentIntent(openApp(context, "connect"))
            .addAction(0, context.getString(R.string.pg_notif_resume), serviceAction(context, RESUME_ACTION, mode, RESUME, withPid = false))
            .build()

    /**
     * A test in progress: Benchmark ("4 of 12") or a command line search ("Round 2 of 3"),
     * with a progress bar that Android 16 shows as a Live Update.
     */
    fun progress(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        done: Int,
        total: Int,
        stop: PendingIntent,
        open: PendingIntent,
    ): Notification {
        val max = total.coerceAtLeast(1)
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(max).setColor(ACCENT)))
            .setProgress(done.coerceIn(0, max))
            .setProgressIndeterminate(total <= 0)
        return base(context, channelId)
            .live()
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentTitle(title)
            .setContentText(text)
            .setShortCriticalText("${done.coerceIn(0, max) * 100 / max}%")
            .setStyle(style)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.test_stop), stop)
            .build()
    }
}

fun registerNotificationChannel(context: Context, id: String, @StringRes name: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            id,
            context.getString(name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setShowBadge(false)

        manager.createNotificationChannel(channel)
    }
}
