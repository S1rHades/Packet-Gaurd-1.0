package io.github.romanvht.byedpi.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.romanvht.byedpi.activities.MainActivity
import io.github.romanvht.byedpi.activities.SettingsActivity
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

@Composable
fun AdvancedScreen(
    bypassActive: Boolean,
    onChanged: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getPreferences() }

    var mode by remember { mutableStateOf(prefs.mode()) }
    var args by remember { mutableStateOf(prefs.getCmdArgs()) }
    var history by remember { mutableStateOf(HistoryUtils(ctx).getHistory().take(8)) }
    var custom by remember { mutableStateOf(prefs.getString(PREF_CUSTOM_PROFILES, "") ?: "") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Advanced Settings", style = MaterialTheme.typography.titleLarge)

        // ---- mode ----
        AppCard {
            SectionTitle("Connection mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mode.VPN to "System-wide (VPN)", Mode.Proxy to "Local proxy").forEach { (m, label) ->
                    FilterChip(
                        selected = mode == m,
                        enabled = !bypassActive,
                        onClick = {
                            mode = m
                            prefs.edit { putString("byedpi_mode", if (m == Mode.VPN) "vpn" else "proxy") }
                            onChanged()
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Palette.AccentBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Text(
                if (bypassActive) "Disconnect to change the mode."
                else "VPN routes every app through the bypass. Proxy mode only listens on 127.0.0.1 " +
                    "for apps you configure yourself.",
                color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ---- active strategy ----
        AppCard {
            SectionTitle("Active strategy")
            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                label = { Text("ciadpi arguments") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val msg = StrategyApplier.apply(ctx, args.trim(), null)
                        history = HistoryUtils(ctx).getHistory().take(8)
                        onChanged()
                        onMessage(msg)
                    },
                    enabled = args.isNotBlank(),
                    border = BorderStroke(1.dp, Palette.Border)
                ) { Text("Save and apply") }
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Benchmark winners are applied here automatically.",
                    color = Palette.TextSecondary, fontSize = 12.sp
                )
            }
            if (history.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Palette.Border)
                Text("Recent strategies", color = Palette.TextSecondary, fontSize = 12.sp)
                history.forEach { cmd ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            args = cmd.text
                            onMessage(StrategyApplier.apply(ctx, cmd.text, null))
                            onChanged()
                        }.padding(vertical = 8.dp)
                    ) {
                        cmd.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Text(
                            cmd.text, color = Palette.TextSecondary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ---- behaviour ----
        AppCard {
            SectionTitle("Behavior")
            PrefSwitch("Connect when the app opens", "auto_connect", false)
            PrefSwitch("Start on boot", "autostart", false)
        }

        // ---- benchmark parameters ----
        AppCard {
            SectionTitle("Benchmark parameters")
            PrefField("Requests per site", "byedpi_proxytest_requests", "1", numeric = true)
            PrefField("Request timeout (seconds)", "byedpi_proxytest_timeout", "5", numeric = true)
            PrefField("Delay between profiles (seconds)", "byedpi_proxytest_delay", "1", numeric = true)
            PrefField("Parallel requests", "byedpi_proxytest_limit", "20", numeric = true)
            PrefField("SNI used by fake-packet profiles", "byedpi_proxytest_sni", "google.com", numeric = false)
            Text(
                "A response counts as reachable when the full body arrives; HTTP codes are shown " +
                    "next to the latency.",
                color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ---- custom profiles ----
        AppCard {
            SectionTitle("Custom profiles")
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    prefs.edit { putString(PREF_CUSTOM_PROFILES, it) }
                },
                label = { Text("One per line: Name | arguments") },
                placeholder = { Text("My split | -s1+s -d3\n-o1 -r1+s") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors()
            )
            Text(
                "They appear under Benchmark → Profiles → Custom.",
                color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ---- tools ----
        AppCard {
            SectionTitle("Tools")
            ToolButton("Full settings (DNS, IPv6, per-app routing…)") {
                if (blocked(bypassActive, onMessage)) return@ToolButton
                ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
            }
            ToolButton("Classic interface") { ctx.startActivity(Intent(ctx, MainActivity::class.java)) }
            ToolButton("System VPN settings (Always-on)") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
            }
            ToolButton("Battery optimisation exemption") { PermissionUtils.requestBatteryOptimization(ctx) }
        }

        Text(
            "Built on ByeByeDPI and ByeDPI (GPL-3.0). Source is included with this build.",
            color = Palette.TextSecondary, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

private fun blocked(bypassActive: Boolean, onMessage: (String) -> Unit): Boolean {
    if (TestService.isRunning) {
        onMessage("Stop the benchmark first")
        return true
    }
    if (bypassActive) {
        onMessage("Disconnect before opening full settings")
        return true
    }
    return false
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Palette.Border),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) { Text(label) }
}

@Composable
private fun PrefSwitch(title: String, key: String, default: Boolean) {
    val ctx: Context = LocalContext.current
    val prefs = remember { ctx.getPreferences() }
    var checked by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit { putBoolean(key, it) }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.AccentBlue)
        )
    }
}

@Composable
private fun PrefField(label: String, key: String, default: String, numeric: Boolean) {
    val ctx: Context = LocalContext.current
    val prefs = remember { ctx.getPreferences() }
    var value by remember { mutableStateOf(prefs.getString(key, default) ?: default) }
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = if (numeric) raw.filter(Char::isDigit).take(4) else raw.trim()
            value = cleaned
            if (cleaned.isNotEmpty()) prefs.edit { putString(key, cleaned) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = fieldColors()
    )
}
