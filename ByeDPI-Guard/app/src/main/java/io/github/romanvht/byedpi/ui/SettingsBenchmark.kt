package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

private fun intIn(min: Int, max: Int): (String) -> Boolean = { v -> v.toIntOrNull()?.let { it in min..max } ?: false }

@Composable
fun BenchmarkSettings() {
    var custom by rememberStringPref(PREF_CUSTOM_PROFILES, "")
    val parsed = remember(custom) { parseCustomProfiles(custom).size }

    val showAdvanced by rememberBoolPref(PREF_SHOW_ADVANCED, false)

    // Request counts and timeouts only matter to experts; the defaults suit everyone else.
    if (showAdvanced) SettingsGroup(stringResource(R.string.pg_bench_params)) {
        PrefTextField(
            stringResource(R.string.pg_bench_requests), "byedpi_proxytest_requests", "1", numeric = true,
            validate = intIn(1, 50), error = between(1, 50)
        )
        PrefTextField(
            stringResource(R.string.pg_bench_timeout), "byedpi_proxytest_timeout", "5", numeric = true,
            validate = intIn(1, 60), error = between(1, 60)
        )
        PrefTextField(
            stringResource(R.string.pg_bench_delay), "byedpi_proxytest_delay", "1", numeric = true,
            validate = intIn(0, 60), error = between(0, 60)
        )
        PrefTextField(
            stringResource(R.string.pg_bench_parallel), "byedpi_proxytest_limit", "20", numeric = true,
            validate = intIn(1, 100), error = between(1, 100)
        )
        PrefTextField(
            stringResource(R.string.pg_bench_sni), "byedpi_proxytest_sni", "google.com",
            validate = { it.isNotBlank() && it.contains('.') }, error = stringResource(R.string.pg_err_domain)
        )
        HelperText(
            stringResource(R.string.pg_bench_params_help)
        )
    }

    SettingsGroup(stringResource(R.string.pg_bench_custom)) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text(stringResource(R.string.pg_bench_custom_label)) },
            placeholder = { Text("My split | -s1+s -d3\n-o1 -r1+s") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors()
        )
        HelperText(
            if (parsed == 0) stringResource(R.string.pg_bench_custom_empty)
            else pluralStringResource(R.plurals.pg_bench_custom_count, parsed, parsed),
            Modifier.padding(top = 8.dp)
        )
    }
}
