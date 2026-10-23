package io.github.romanvht.byedpi.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.romanvht.byedpi.data.PrivateDnsState
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.PrivateDnsUtils
import io.github.romanvht.byedpi.utility.checkIp
import io.github.romanvht.byedpi.utility.checkIpAndPortInCmd
import io.github.romanvht.byedpi.utility.checkNotLocalIp
import io.github.romanvht.byedpi.utility.checkPort
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getSelectedApps

private val DNS_PRESETS = listOf(
    "" to "System default",
    "1.1.1.1" to "Cloudflare",
    "8.8.8.8" to "Google",
    "9.9.9.9" to "Quad9",
    "77.88.8.8" to "Yandex"
)

private fun dnsSummary(ip: String): String {
    val preset = DNS_PRESETS.firstOrNull { it.first == ip } ?: return ip
    return if (preset.first.isBlank()) preset.second else "${preset.second} (${preset.first})"
}

@Composable
fun GeneralSettings(bypassActive: Boolean, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getPreferences() }

    var mode by rememberStringPref("byedpi_mode", "vpn")
    var autoConnect by rememberBoolPref("auto_connect", false)
    var autostart by rememberBoolPref("autostart", false)
    var ipv6 by rememberBoolPref("ipv6_enable", false)
    var applist by rememberStringPref("applist_type", "disable")
    var dns by rememberStringPref("dns_ip", "1.1.1.1")

    var showDns by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var selectedCount by remember { mutableIntStateOf(prefs.getSelectedApps().size) }
    var privateDns by remember { mutableStateOf(PrivateDnsUtils.getState(ctx)) }
    var batteryOk by remember { mutableStateOf(PermissionUtils.isBatteryOptimizationDisabled(ctx)) }

    LifecycleResumeEffect(Unit) {
        privateDns = PrivateDnsUtils.getState(ctx)
        batteryOk = PermissionUtils.isBatteryOptimizationDisabled(ctx)
        onPauseOrDispose { }
    }

    val isVpn = mode == "vpn"
    val proxyFromCmd = remember(mode) { prefs.checkIpAndPortInCmd().let { it.first != null || it.second != null } }

    SettingsGroup("Connection mode") {
        Segmented(
            items = listOf("vpn", "proxy"),
            selected = mode,
            onSelect = {
                mode = it
                onChanged()
            },
            label = { if (it == "vpn") "System-wide VPN" else "Local proxy" },
            enabled = !bypassActive
        )
        HelperText(
            if (bypassActive) "Disconnect to change the mode."
            else if (isVpn) "Every app on the phone goes through the bypass."
            else "Only apps you point at the local SOCKS5 proxy use the bypass.",
            Modifier.padding(top = 10.dp)
        )
    }

    SettingsGroup("Behavior") {
        SwitchRow("Connect when the app opens", autoConnect, { autoConnect = it })
        SwitchRow(
            "Start on boot", autostart, { autostart = it },
            subtitle = "Turns protection back on after the phone restarts"
        )
    }

    AnimatedVisibility(isVpn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("Network") {
            val pd = privateDns
            if (pd is PrivateDnsState.Configured) {
                InfoBanner(
                    "Android's Private DNS (${pd.hostname}) is on, so it handles DNS for the tunnel.",
                    Icons.Filled.Lock,
                    action = { TextButton(onClick = { PrivateDnsUtils.openSettings(ctx) }) { Text("Change") } }
                )
            } else {
                NavRow(
                    "DNS server",
                    value = dnsSummary(dns),
                    icon = Icons.Filled.Dns,
                    onClick = { showDns = true },
                    trailing = { Chevron() }
                )
            }
            SwitchRow(
                "IPv6", ipv6, {
                    ipv6 = it
                    onChanged()
                },
                subtitle = "Route IPv6 traffic through the tunnel too"
            )
        }
    }

    AnimatedVisibility(isVpn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("Per-app routing") {
            Segmented(
                items = listOf("disable", "blacklist", "whitelist"),
                selected = applist,
                onSelect = {
                    applist = it
                    onChanged()
                },
                label = {
                    when (it) {
                        "blacklist" -> "Exclude"
                        "whitelist" -> "Only"
                        else -> "All apps"
                    }
                }
            )
            HelperText(
                when (applist) {
                    "blacklist" -> "Selected apps skip the bypass; everything else uses it."
                    "whitelist" -> "Only the selected apps use the bypass."
                    else -> "Every app uses the bypass."
                },
                Modifier.padding(top = 10.dp)
            )
            AnimatedVisibility(applist != "disable", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                NavRow(
                    "Choose apps",
                    value = if (selectedCount == 1) "1 app selected" else "$selectedCount apps selected",
                    icon = Icons.Filled.Apps,
                    onClick = { showApps = true },
                    trailing = { Chevron() },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    AnimatedVisibility(!proxyFromCmd, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("Local proxy address") {
            PrefTextField(
                "IP address", "byedpi_proxy_ip", "127.0.0.1",
                validate = ::checkIp, error = "Enter a valid IP address"
            )
            PrefTextField(
                "Port", "byedpi_proxy_port", "1080", numeric = true,
                validate = ::checkPort, error = "Port must be between 1 and 65535"
            )
            HelperText("The engine listens here. In proxy mode, point your apps' SOCKS5 settings at this address.")
        }
    }

    SettingsGroup("System") {
        NavRow(
            "Battery optimization",
            value = if (batteryOk) "Unrestricted — Android won't stop protection" else "Restricted — tap to let protection run in the background",
            icon = Icons.Filled.BatteryChargingFull,
            onClick = { PermissionUtils.requestBatteryOptimization(ctx) },
            trailing = { Chevron() }
        )
        NavRow(
            "Always-on VPN",
            value = "Open Android's VPN settings",
            icon = Icons.Filled.VpnLock,
            onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } },
            trailing = { Chevron() }
        )
    }

    if (showDns) {
        DnsDialog(
            current = dns,
            onPick = {
                dns = it
                showDns = false
                onChanged()
            },
            onDismiss = { showDns = false }
        )
    }
    if (showApps) {
        AppPickerDialog(onDismiss = {
            showApps = false
            selectedCount = prefs.getSelectedApps().size
            onChanged()
        })
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.TextSecondary)
}

@Composable
private fun DnsDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val isPreset = DNS_PRESETS.any { it.first == current }
    var choice by remember { mutableStateOf(if (isPreset) current else "custom") }
    var custom by remember { mutableStateOf(if (isPreset) "" else current) }
    val customValid = custom.isNotBlank() && checkNotLocalIp(custom.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("DNS server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (DNS_PRESETS.map { it.first to dnsSummary(it.first) } + ("custom" to "Custom")).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { choice = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = choice == value,
                            onClick = { choice = value },
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow)
                        )
                        Text(label)
                    }
                }
                AnimatedVisibility(choice == "custom") {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.trim() },
                        label = { Text("IP address") },
                        singleLine = true,
                        isError = custom.isNotBlank() && !customValid,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice != "custom" || customValid,
                onClick = { onPick(if (choice == "custom") custom.trim() else choice) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
