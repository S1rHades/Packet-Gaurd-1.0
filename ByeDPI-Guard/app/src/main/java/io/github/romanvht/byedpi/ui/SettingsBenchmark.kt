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

private fun intIn(min: Int, max: Int): (String) -> Boolean = { v -> v.toIntOrNull()?.let { it in min..max } ?: false }

@Composable
fun BenchmarkSettings() {
    var custom by rememberStringPref(PREF_CUSTOM_PROFILES, "")
    val parsed = remember(custom) { parseCustomProfiles(custom).size }

    SettingsGroup("Test parameters") {
        PrefTextField(
            "Requests per site", "byedpi_proxytest_requests", "1", numeric = true,
            validate = intIn(1, 50), error = "Between 1 and 50"
        )
        PrefTextField(
            "Request timeout (seconds)", "byedpi_proxytest_timeout", "5", numeric = true,
            validate = intIn(1, 60), error = "Between 1 and 60"
        )
        PrefTextField(
            "Pause between profiles (seconds)", "byedpi_proxytest_delay", "1", numeric = true,
            validate = intIn(0, 60), error = "Between 0 and 60"
        )
        PrefTextField(
            "Parallel requests", "byedpi_proxytest_limit", "20", numeric = true,
            validate = intIn(1, 100), error = "Between 1 and 100"
        )
        PrefTextField(
            "SNI used by fake-packet profiles", "byedpi_proxytest_sni", "google.com",
            validate = { it.isNotBlank() && it.contains('.') }, error = "Enter a domain"
        )
        HelperText(
            "A site counts as reachable when its full response arrives. More requests per site give " +
                "steadier results but take longer."
        )
    }

    SettingsGroup("Custom profiles") {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("One per line:  Name | arguments") },
            placeholder = { Text("My split | -s1+s -d3\n-o1 -r1+s") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors()
        )
        HelperText(
            if (parsed == 0) "They appear in Benchmark → Profiles to test → Custom."
            else "$parsed custom profile${if (parsed == 1) "" else "s"} · shown under Benchmark → Custom.",
            Modifier.padding(top = 8.dp)
        )
    }
}
