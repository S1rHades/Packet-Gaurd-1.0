package io.github.romanvht.byedpi.ui

import io.github.romanvht.byedpi.utility.HistoryUtils
import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.update.AppUpdates
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Connect(R.string.pg_tab_connect, Icons.Filled.Shield),
    Benchmark(R.string.pg_tab_benchmark, Icons.Filled.Speed),
    Settings(R.string.pg_tab_settings, Icons.Filled.Settings)
}

private const val BATTERY_REQUESTED = "battery_optimization_requested"

private fun requestBatteryOnce(ctx: Context) {
    val prefs = ctx.getPreferences()
    if (!prefs.getBoolean(BATTERY_REQUESTED, false) && !PermissionUtils.isBatteryOptimizationDisabled(ctx)) {
        PermissionUtils.requestBatteryOptimization(ctx)
        prefs.edit { putBoolean(BATTERY_REQUESTED, true) }
    }
}

@Composable
fun AppRoot(requestedTab: Tab? = null, onTabRequestHandled: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val connectVm: ConnectViewModel = viewModel()
    val benchVm: BenchmarkViewModel = viewModel()

    val state by connectVm.state.collectAsStateWithLifecycle()
    val metrics by connectVm.metrics.collectAsStateWithLifecycle()
    val profile by connectVm.profile.collectAsStateWithLifecycle()
    val refreshing by connectVm.refreshing.collectAsStateWithLifecycle()
    val struggling by connectVm.struggling.collectAsStateWithLifecycle()
    val benchUi by benchVm.ui.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Connect) }
    // Read before anything else marks the app as used (e.g. the update check's version note).
    var firstRunDone by remember { mutableStateOf(FirstRun.isDone(ctx)) }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            onTabRequestHandled()
        }
    }
    val bypassActive = state is ConnectionState.Connected || state is ConnectionState.Connecting

    // Android habit (Jakob's law): Back from another tab goes to Connect before leaving the app.
    BackHandler(enabled = tab != Tab.Connect) { tab = Tab.Connect }

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    LaunchedEffect(Unit) {
        // Updates from GitHub: a quiet check shortly after start, then every 6 hours.
        AppUpdates.startAutoChecks(ctx)
        AppUpdates.consumeJustUpdated(ctx) // the Connect screen shows a "What's new" card
        launch { connectVm.messages.collect { snackbar.showSnackbar(it) } }
        launch { benchVm.messages.collect { snackbar.showSnackbar(it) } }
        // Every "Applied" comes with Undo, which puts the previous strategy back.
        launch {
            StrategyApplier.notices.collect { n ->
                connectVm.refreshProfile()
                launch {
                    val r = snackbar.showSnackbar(
                        n.message,
                        actionLabel = if (n.canUndo) ctx.getString(R.string.pg_undo) else null,
                        withDismissAction = false,
                        duration = if (n.canUndo) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        val msg = StrategyApplier.undo(ctx)
                        connectVm.refreshProfile()
                        snackbar.showSnackbar(msg)
                    }
                }
            }
        }
    }

    // Keep the screen awake during a benchmark, as upstream's test screen does.
    val view = LocalView.current
    DisposableEffect(benchUi.test.isRunning) {
        view.keepScreenOn = benchUi.test.isRunning
        onDispose { view.keepScreenOn = false }
    }

    LifecycleResumeEffect(Unit) {
        ServiceManager.refresh(ctx)
        connectVm.syncFromService()
        connectVm.refreshProfile()
        onPauseOrDispose { }
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) connectVm.connect() else say(ctx.getString(R.string.pg_vpn_denied))
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestBatteryOnce(ctx) // the bypass still works if notifications are denied
    }

    val toggle: () -> Unit = {
        when (state) {
            is ConnectionState.Connected, ConnectionState.Connecting -> connectVm.disconnect()
            else -> when {
                TestService.isRunning || CommandGenerator.isRunning -> say(ctx.getString(R.string.pg_benchmark_running))
                else -> {
                    val consent = if (ctx.getPreferences().mode() == Mode.VPN) VpnService.prepare(ctx) else null
                    when {
                        consent == null -> connectVm.connect()
                        // Some Android TV and OEM builds have no VPN consent screen.
                        runCatching { vpnPermission.launch(consent) }.isFailure -> say(ctx.getString(R.string.pg_err_engine_start))
                    }
                }
            }
        }
    }

    // The first-run guide's quick test: connect with what it found, or say nothing worked.
    LaunchedEffect(Unit) {
        benchVm.firstSetupDone.collect { applied ->
            if (applied) {
                tab = Tab.Connect
                if (state !is ConnectionState.Connected && state !is ConnectionState.Connecting) toggle()
            } else {
                say(ctx.getString(R.string.pg_fr_nothing))
            }
        }
    }

    // One-time startup work, after the first-run guide: permissions, then optional auto-connect.
    var startupDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstRunDone) {
        if (!firstRunDone || startupDone) return@LaunchedEffect
        startupDone = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.hasNotificationPermission(ctx)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestBatteryOnce(ctx)
        }
        if (ctx.getPreferences().getBoolean("auto_connect", false) &&
            state is ConnectionState.Disconnected && !TestService.isRunning && !CommandGenerator.isRunning
        ) toggle()
    }

    // A shared setting from a QR code, link or paste: show what it does, then add (or use) it.
    val incoming by StrategyInbox.pending.collectAsStateWithLifecycle()
    incoming?.let { shared ->
        val name = shared.name.ifBlank { stringResource(R.string.pg_shared_default) }
        ImportStrategyDialog(
            shared,
            onAdd = {
                HistoryUtils(ctx).apply {
                    addCommand(shared.command)
                    pinCommand(shared.command)
                    renameCommand(shared.command, name)
                }
                StrategyInbox.clear()
                say(ctx.getString(R.string.pg_import_added))
            },
            onUse = {
                StrategyInbox.clear()
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { StrategyApplier.apply(ctx, shared.command, name) }
            },
            onDismiss = { StrategyInbox.clear() }
        )
    }

    if (!firstRunDone) {
        FirstRunGuide(
            onDone = {
                FirstRun.markDone(ctx)
                firstRunDone = true
            },
            onFind = {
                FirstRun.markDone(ctx)
                firstRunDone = true
                tab = Tab.Benchmark
                benchVm.runFirstSetup()
            }
        )
        return
    }

    GlassBackground {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = Palette.Surface.copy(alpha = 0.78f),
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(Color.White.copy(alpha = 0.10f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                }
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) }, // the label below names it
                        label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Palette.Sky,
                            indicatorColor = Palette.Glow.copy(alpha = 0.30f),
                            unselectedIconColor = Palette.TextSecondary,
                            unselectedTextColor = Palette.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { tabTransition() },
            label = "mainTab",
            modifier = Modifier.padding(padding)
        ) { current ->
            when (current) {
                Tab.Connect -> ConnectScreen(
                    state, metrics, profile, onToggle = toggle,
                    refreshing = refreshing, onRefresh = connectVm::refreshNow,
                    struggling = struggling,
                    onFindNew = {
                        // The same quick test as the first-run guide: test, switch on the best, reconnect.
                        connectVm.dismissStruggle()
                        tab = Tab.Benchmark
                        benchVm.runFirstSetup()
                    },
                    onDismissStruggle = connectVm::dismissStruggle
                )
                Tab.Benchmark -> BenchmarkScreen(benchVm, bypassActive = bypassActive)
                Tab.Settings -> SettingsScreen(
                    bypassActive = bypassActive,
                    onChanged = { connectVm.refreshProfile() },
                    onMessage = { say(it) }
                )
            }
        }
    }
    }
}
