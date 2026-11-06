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
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

private val DESYNC_METHODS = listOf(
    "none" to R.string.pg_desync_none,
    "split" to R.string.pg_desync_split,
    "disorder" to R.string.pg_desync_disorder,
    "fake" to R.string.pg_desync_fake,
    "oob" to R.string.pg_desync_oob,
    "disoob" to R.string.pg_desync_disoob
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
    val showAdvanced by rememberBoolPref(PREF_SHOW_ADVANCED, false)

    val editable = !cmdEnabled
    val dim by animateFloatAsState(if (editable) 1f else 0.55f, tween(Motion.MEDIUM), label = "engineDim")

    // Upstream rule: with no protocol ticked, every protocol is desynced.
    val allProtocols = !http && !https && !udp
    val httpOn = editable && (allProtocols || http)
    val httpsOn = editable && (allProtocols || https)
    val udpOn = editable && (allProtocols || udp)

    AnimatedVisibility(cmdEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        InfoBanner(
            stringResource(R.string.pg_engine_cmd_active),
            Icons.Filled.Info,
            action = {
                TextButton(onClick = {
                    cmdEnabled = false
                    onChanged()
                }) { Text(stringResource(R.string.pg_engine_use_these)) }
            }
        )
    }

    Column(Modifier.alpha(dim)) {
        SettingsGroup(stringResource(R.string.pg_engine_desync)) {
            PillTabs(
                items = DESYNC_METHODS.map { it.first },
                selected = desync,
                onSelect = {
                    desync = it
                    onChanged()
                },
                label = { key -> stringResource(DESYNC_METHODS.first { it.first == key }.second) },
                enabled = editable
            )
            HelperText(
                when (desync) {
                    "split" -> stringResource(R.string.pg_desync_help_split)
                    "disorder" -> stringResource(R.string.pg_desync_help_disorder)
                    "fake" -> stringResource(R.string.pg_desync_help_fake)
                    "oob" -> stringResource(R.string.pg_desync_help_oob)
                    "disoob" -> stringResource(R.string.pg_desync_help_disoob)
                    else -> stringResource(R.string.pg_desync_help_none)
                },
                Modifier.padding(top = 10.dp)
            )
            AnimatedVisibility(desync != "none", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp)) {
                    PrefTextField(
                        stringResource(R.string.pg_engine_split_pos), "byedpi_split_position", "1",
                        numeric = true, signed = true, enabled = editable,
                        validate = intIn(Int.MIN_VALUE, Int.MAX_VALUE), error = stringResource(R.string.pg_err_int)
                    )
                    BoolPrefRow(stringResource(R.string.pg_engine_split_at_host), "byedpi_split_at_host", false, editable, onChanged)
                }
            }
            AnimatedVisibility(desync == "fake", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    PrefTextField(
                        stringResource(R.string.pg_engine_fake_ttl), "byedpi_fake_ttl", "8", numeric = true, enabled = editable,
                        validate = intIn(1, 255), error = between(1, 255)
                    )
                    PrefTextField(
                        stringResource(R.string.pg_engine_fake_offset), "byedpi_fake_offset", "0", numeric = true, enabled = editable,
                        validate = intIn(0, Int.MAX_VALUE), error = stringResource(R.string.pg_err_non_negative)
                    )
                    PrefTextField(
                        stringResource(R.string.pg_engine_fake_sni), "byedpi_fake_sni", "www.iana.org", enabled = editable,
                        validate = { it.isNotBlank() }, error = stringResource(R.string.pg_err_domain)
                    )
                }
            }
            AnimatedVisibility(desync == "oob" || desync == "disoob", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    stringResource(R.string.pg_engine_oob_byte), "byedpi_oob_data", "a", maxLength = 1, enabled = editable,
                    validate = { it.length == 1 }, error = stringResource(R.string.pg_err_one_char)
                )
            }
            PrefTextField(
                stringResource(R.string.pg_engine_default_ttl), "byedpi_default_ttl", "0", numeric = true, enabled = editable,
                validate = intIn(0, 255), error = between(0, 255)
            )
            BoolPrefRow(stringResource(R.string.pg_engine_drop_sack), "byedpi_drop_sack", false, editable, onChanged)
        }

        Gap(14)
        SettingsGroup(stringResource(R.string.pg_engine_protocols)) {
            SwitchRow("HTTP", http, { http = it; onChanged() }, enabled = editable)
            SwitchRow("HTTPS (TLS)", https, { https = it; onChanged() }, enabled = editable)
            SwitchRow("UDP / QUIC", udp, { udp = it; onChanged() }, enabled = editable)
            HelperText(stringResource(R.string.pg_engine_protocols_help))
        }

        Gap(14)
        SettingsGroup(stringResource(R.string.pg_engine_http_tweaks)) {
            BoolPrefRow(stringResource(R.string.pg_engine_host_mixed), "byedpi_host_mixed_case", false, httpOn, onChanged)
            BoolPrefRow(stringResource(R.string.pg_engine_domain_mixed), "byedpi_domain_mixed_case", false, httpOn, onChanged)
            BoolPrefRow(stringResource(R.string.pg_engine_host_spaces), "byedpi_host_remove_spaces", false, httpOn, onChanged)
        }

        Gap(14)
        SettingsGroup(stringResource(R.string.pg_engine_tlsrec)) {
            SwitchRow(stringResource(R.string.pg_engine_tlsrec_enable), tlsrec, { tlsrec = it; onChanged() }, enabled = httpsOn)
            AnimatedVisibility(tlsrec, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    PrefTextField(
                        stringResource(R.string.pg_engine_tlsrec_pos), "byedpi_tlsrec_position", "0",
                        numeric = true, signed = true, enabled = httpsOn && tlsrec,
                        validate = intIn(2 * Short.MIN_VALUE, 2 * Short.MAX_VALUE), error = between(-65536, 65534)
                    )
                    BoolPrefRow(stringResource(R.string.pg_engine_tlsrec_at_sni), "byedpi_tlsrec_at_sni", false, httpsOn && tlsrec, onChanged)
                }
            }
        }

        Gap(14)
        SettingsGroup("UDP") {
            PrefTextField(
                stringResource(R.string.pg_engine_udp_fake), "byedpi_udp_fake_count", "1", numeric = true, enabled = udpOn,
                validate = intIn(0, 255), error = between(0, 255)
            )
        }

        Gap(14)
        SettingsGroup(stringResource(R.string.pg_engine_hosts)) {
            Segmented(
                items = listOf("disable", "blacklist", "whitelist"),
                selected = hostsMode,
                onSelect = {
                    hostsMode = it
                    onChanged()
                },
                label = {
                    when (it) {
                        "blacklist" -> stringResource(R.string.pg_engine_hosts_black)
                        "whitelist" -> stringResource(R.string.pg_engine_hosts_white)
                        else -> stringResource(R.string.pg_engine_hosts_all)
                    }
                },
                enabled = editable
            )
            AnimatedVisibility(hostsMode == "blacklist", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    stringResource(R.string.pg_engine_blacklist), "byedpi_hosts_blacklist", "", singleLine = false, enabled = editable,
                    hint = stringResource(R.string.pg_one_domain_per_line), modifier = Modifier.padding(top = 8.dp)
                )
            }
            AnimatedVisibility(hostsMode == "whitelist", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PrefTextField(
                    stringResource(R.string.pg_engine_whitelist), "byedpi_hosts_whitelist", "", singleLine = false, enabled = editable,
                    hint = stringResource(R.string.pg_one_domain_per_line), modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Connection limits and buffers are for experts: only with "Show advanced settings".
        if (showAdvanced) Gap(14)
        if (showAdvanced) SettingsGroup(stringResource(R.string.pg_engine_proxy)) {
            PrefTextField(
                stringResource(R.string.pg_engine_max_conn), "byedpi_max_connections", "512", numeric = true, enabled = editable,
                validate = intIn(1, Short.MAX_VALUE.toInt()), error = between(1, 32767)
            )
            PrefTextField(
                stringResource(R.string.pg_engine_buffer), "byedpi_buffer_size", "16384", numeric = true, enabled = editable,
                validate = intIn(1, Int.MAX_VALUE / 4), error = stringResource(R.string.pg_err_positive)
            )
            BoolPrefRow(stringResource(R.string.pg_engine_no_domain), "byedpi_no_domain", false, editable, onChanged)
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
