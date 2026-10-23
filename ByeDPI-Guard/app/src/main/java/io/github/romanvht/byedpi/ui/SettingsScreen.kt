package io.github.romanvht.byedpi.ui

import android.content.Context
import android.net.VpnService
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

enum class SettingsTab(val label: String, val icon: ImageVector) {
    General("General", Icons.Filled.Tune),
    Strategy("Strategy", Icons.Filled.Terminal),
    Engine("Engine", Icons.Filled.Memory),
    Benchmark("Benchmark", Icons.Filled.Speed),
    About("About", Icons.Filled.Info)
}

/**
 * Settings, organised in tabs. Everything upstream's preference screens offered lives here,
 * written to the same SharedPreferences keys, so the native engine reads it unchanged.
 */
@Composable
fun SettingsScreen(
    bypassActive: Boolean,
    onChanged: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(SettingsTab.General) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    val changed: () -> Unit = {
        dirty = true
        onChanged()
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            ScreenHeader("Settings", modifier = Modifier.staggeredEnter(0))
            Gap(14)
            PillTabs(
                items = SettingsTab.entries.toList(),
                selected = tab,
                onSelect = { tab = it },
                label = { it.label },
                icon = { it.icon },
                modifier = Modifier.staggeredEnter(1)
            )
        }
        AnimatedVisibility(
            visible = bypassActive && dirty,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            InfoBanner(
                "Reconnect to apply your changes.",
                Icons.Filled.Sync,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                action = {
                    TextButton(onClick = {
                        onMessage(reconnect(ctx))
                        dirty = false
                    }) { Text("Reconnect") }
                }
            )
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { tabTransition() },
            label = "settingsTab",
            modifier = Modifier.weight(1f)
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (current) {
                    SettingsTab.General -> GeneralSettings(bypassActive, changed)
                    SettingsTab.Strategy -> StrategySettings(changed, onMessage)
                    SettingsTab.Engine -> EngineSettings(changed)
                    SettingsTab.Benchmark -> BenchmarkSettings()
                    SettingsTab.About -> AboutSettings(onMessage)
                }
            }
        }
    }
}

/** Restarts the running service so it picks up new preferences. */
fun reconnect(ctx: Context): String {
    if (TestService.isRunning) return "Stop the benchmark first"
    if (appStatus.first != AppStatus.Running) return "Changes will apply when you connect"
    val mode = ctx.getPreferences().mode()
    if (mode == Mode.VPN && VpnService.prepare(ctx) != null) return "Open the Connect tab to grant VPN access"
    ServiceManager.restart(ctx, mode)
    return "Reconnecting…"
}

/** A full-width settings card column helper used by the tab bodies. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AppCard(modifier.fillMaxWidth()) {
        SectionTitle(title, trailing = trailing)
        content()
    }
}
