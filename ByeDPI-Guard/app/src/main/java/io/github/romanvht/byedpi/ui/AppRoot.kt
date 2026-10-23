package io.github.romanvht.byedpi.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Connect("Connect", Icons.Filled.Shield),
    Benchmark("Benchmark", Icons.Filled.Speed),
    Settings("Settings", Icons.Filled.Settings)
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
    val benchUi by benchVm.ui.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Connect) }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            onTabRequestHandled()
        }
    }
    val bypassActive = state is ConnectionState.Connected || state is ConnectionState.Connecting

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    LaunchedEffect(Unit) {
        launch { connectVm.messages.collect { snackbar.showSnackbar(it) } }
        launch { benchVm.messages.collect { snackbar.showSnackbar(it) } }
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
        if (r.resultCode == Activity.RESULT_OK) connectVm.connect() else say("VPN permission was denied")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestBatteryOnce(ctx) // the bypass still works if notifications are denied
    }

    val toggle: () -> Unit = {
        when (state) {
            is ConnectionState.Connected, ConnectionState.Connecting -> connectVm.disconnect()
            else -> when {
                TestService.isRunning -> say("A benchmark is running. Stop it first.")
                else -> {
                    val consent = if (ctx.getPreferences().mode() == Mode.VPN) VpnService.prepare(ctx) else null
                    if (consent != null) vpnPermission.launch(consent) else connectVm.connect()
                }
            }
        }
    }

    // One-time startup work: permissions, then optional auto-connect.
    var startupDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startupDone) return@LaunchedEffect
        startupDone = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.hasNotificationPermission(ctx)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestBatteryOnce(ctx)
        }
        if (ctx.getPreferences().getBoolean("auto_connect", false) &&
            state is ConnectionState.Disconnected && !TestService.isRunning
        ) toggle()
    }

    Scaffold(
        containerColor = Palette.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Palette.Sky,
                            indicatorColor = Palette.AccentBlue,
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
                Tab.Connect -> ConnectScreen(state, metrics, profile, onToggle = toggle)
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
