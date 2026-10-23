package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

private val DESYNC_METHODS = listOf(
    "none" to "None",
    "split" to "Split",
    "disorder" to "Disorder",
    "fake" to "Fake",
    "oob" to "OOB",
    "disoob" to "Disordered OOB"
)

private fun intIn(min: Int, max: Int): (String) -> Boolean = { v -> v.toIntOrNull()?.let { it in min..max } ?: false }

/**
 * The visual strategy editor (upstream's "UI settings"): the same options, keys and value
 * ranges, grouped by what they affect and shown only when they apply.
 */
@Composable
fun EngineSettings(onChanged: () -> Unit) {
    var cmdEnabled by rememberBoolPref("byedpi_enable_cmd_settings", true)
    var desync by rememberStringPref("byedpi_desync_method", "oob")
    var hostsMode by rememberStringPref("byedpi_hosts_mode", "disable")
    var http by rememberBoolPref("byedpi_desync_http", true)
    var https by rememberBoolPref("byedpi_desync_https", true)
    var udp by rememberBoolPref("byedpi_desync_udp", true)
    var tlsrec by rememberBoolPref("byedpi_tlsrec_enabled", false)

    val editable = !cmdEnabled
    val dim by animateFloatAsState(if (editable) 1f else 0.55f, tween(Motion.MEDIUM), label = "engineDim")

    // Upstream rule: with no protocol ticked, every protocol is desynced.
    val allProtocols = !http && !https && !udp
    val httpOn = editable && (allProtocols || http)
    val httpsOn = editable && (allProtocols || https)
    val udpOn = editable && (allProtocols || udp)

    AnimatedVisibility(cmdEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        InfoBanner(
            "A command-line strategy is active, so these options are not used right now.",
            Icons.Filled.Info,
            action = {
                TextButton(onClick = {
                    cmdEnabled = false
                    onChanged()
                }) { Text("Use these") }
            }
        )
    }

    Column(Modifier.alpha(dim)) {
        SettingsGroup("Desync method") {
            PillTabs(
                items = DESYNC_METHODS.map { it.first },
                selected = desync,
                onSelect = {
                    desync = it
                    onChanged()
                },
                label = { key -> DESYNC_METHODS.first { it.first == key }.second },
                enabled = editable
            )
            HelperText(
                when (desync) {
                    "split" -> "Splits the first packet in two at the chosen position."
                    "disorder" -> "Splits the packet and sends the parts out of order."
                    "fake" -> "Sends a decoy packet with a short TTL before the real one."
                    "oob" -> "Inserts an out-of-band byte at the split point."
                    "disoob" -> "Out-of-band byte combined with disordered sending."
                    else -> "Traffic is passed through without desync tricks."
                },
                Modifier.padding(top = 10.dp)
            )
            AnimatedVisibility(desync != "none", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp)) {
                    PrefTextField(
                        "Split position", "byedpi_split_position", "1",
                        numeric = true, signed = true, enabled = editable,
                        validate = intIn(Int.MIN_VALUE, Int.MAX_VALUE), error = "Enter a whole number"
                    )
                    BoolPrefRow("Count position from the host / SNI", "byedpi_split_at_host", false, editable, onChanged)
                }
            }
            AnimatedVisibility(desync == "fake", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    PrefTextField(
                        "Fake packet TTL", "byedpi_fake_ttl", "8", numeric = true, enabled = editable,
                        validate = intIn(1, 255), error = "Between 1 and 255"
                    )
                    PrefTextField(
                        "Fake packet offset", "byedpi_fake_offset", "0", numeric = true, enabled = editable,
                        validate = intIn(0, Int.MAX_VALUE), error = "Enter 0 or more"
                    )
                    PrefTextField(
                        "SNI of the fake packet", "byedpi_fake_sni", "www.iana.org", enabled = editable,
                        validate = { it.isNotBlank() }, error = "Enter a domain"
                    )
                }
            }
            AnimatedVisibility(desync == "oob" || desync == "disoob", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    "Out-of-band byte", "byedpi_oob_data", "a", maxLength = 1, enabled = editable,
                    validate = { it.length == 1 }, error = "Exactly one character"
                )
            }
            PrefTextField(
                "Default TTL (0 = system)", "byedpi_default_ttl", "0", numeric = true, enabled = editable,
                validate = intIn(0, 255), error = "Between 0 and 255"
            )
            BoolPrefRow("Drop SACK", "byedpi_drop_sack", false, editable, onChanged)
        }

        Gap(14)
        SettingsGroup("Protocols") {
            SwitchRow("HTTP", http, { http = it; onChanged() }, enabled = editable)
            SwitchRow("HTTPS (TLS)", https, { https = it; onChanged() }, enabled = editable)
            SwitchRow("UDP / QUIC", udp, { udp = it; onChanged() }, enabled = editable)
            HelperText("Leave all three off to desync every protocol.")
        }

        Gap(14)
        SettingsGroup("HTTP tweaks") {
            BoolPrefRow("Mixed-case Host header", "byedpi_host_mixed_case", false, httpOn, onChanged)
            BoolPrefRow("Mixed-case domain", "byedpi_domain_mixed_case", false, httpOn, onChanged)
            BoolPrefRow("Remove spaces in Host header", "byedpi_host_remove_spaces", false, httpOn, onChanged)
        }

        Gap(14)
        SettingsGroup("TLS record split") {
            SwitchRow("Split the TLS record", tlsrec, { tlsrec = it; onChanged() }, enabled = httpsOn)
            AnimatedVisibility(tlsrec, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    PrefTextField(
                        "Record split position", "byedpi_tlsrec_position", "0",
                        numeric = true, signed = true, enabled = httpsOn && tlsrec,
                        validate = intIn(2 * Short.MIN_VALUE, 2 * Short.MAX_VALUE), error = "Between -65536 and 65534"
                    )
                    BoolPrefRow("Count position from the SNI", "byedpi_tlsrec_at_sni", false, httpsOn && tlsrec, onChanged)
                }
            }
        }

        Gap(14)
        SettingsGroup("UDP") {
            PrefTextField(
                "Fake packets before UDP traffic", "byedpi_udp_fake_count", "1", numeric = true, enabled = udpOn,
                validate = intIn(0, 255), error = "Between 0 and 255"
            )
        }

        Gap(14)
        SettingsGroup("Sites to apply desync to") {
            Segmented(
                items = listOf("disable", "blacklist", "whitelist"),
                selected = hostsMode,
                onSelect = {
                    hostsMode = it
                    onChanged()
                },
                label = {
                    when (it) {
                        "blacklist" -> "All except"
                        "whitelist" -> "Only these"
                        else -> "All sites"
                    }
                },
                enabled = editable
            )
            AnimatedVisibility(hostsMode == "blacklist", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    "Sites to leave untouched", "byedpi_hosts_blacklist", "", singleLine = false, enabled = editable,
                    hint = "One domain per line", modifier = Modifier.padding(top = 8.dp)
                )
            }
            AnimatedVisibility(hostsMode == "whitelist", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    "Sites to desync", "byedpi_hosts_whitelist", "", singleLine = false, enabled = editable,
                    hint = "One domain per line", modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Gap(14)
        SettingsGroup("Proxy engine") {
            PrefTextField(
                "Max connections", "byedpi_max_connections", "512", numeric = true, enabled = editable,
                validate = intIn(1, Short.MAX_VALUE.toInt()), error = "Between 1 and 32767"
            )
            PrefTextField(
                "Buffer size (bytes)", "byedpi_buffer_size", "16384", numeric = true, enabled = editable,
                validate = intIn(1, Int.MAX_VALUE / 4), error = "Enter a positive number"
            )
            BoolPrefRow("Don't resolve domains", "byedpi_no_domain", false, editable, onChanged)
            BoolPrefRow("TCP Fast Open", "byedpi_tcp_fast_open", false, editable, onChanged)
        }
    }
}

@Composable
private fun BoolPrefRow(title: String, key: String, default: Boolean, enabled: Boolean, onChanged: () -> Unit) {
    var value by rememberBoolPref(key, default)
    SwitchRow(title, value, {
        value = it
        onChanged()
    }, enabled = enabled)
}
