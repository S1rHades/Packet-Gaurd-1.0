package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.data.Mode

@Composable
fun ConnectScreen(
    state: ConnectionState,
    metrics: LiveMetrics,
    profile: ActiveProfile,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ByeDPI Guard",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(28.dp))
        StatusHeadline(state)
        Spacer(Modifier.height(24.dp))
        PowerButton(state = state, onClick = onToggle)
        Spacer(Modifier.height(28.dp))
        StatusCard(state, metrics, profile)
    }
}

@Composable
private fun StatusHeadline(state: ConnectionState) {
    val (label, color, sub) = when (state) {
        is ConnectionState.Connected -> Triple(
            "BYPASS ACTIVE", Palette.Success,
            if (state.mode == Mode.VPN) "All apps are using the bypass profile" else "Local proxy is running"
        )
        ConnectionState.Connecting -> Triple("CONNECTING…", Palette.Glow, "Starting engine and tunnel")
        ConnectionState.Disconnected -> Triple("NOT ACTIVE", Palette.TextSecondary, "Tap the button to start")
        is ConnectionState.Failed -> Triple("CONNECTION FAILED", Palette.Error, state.message)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(sub, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusCard(state: ConnectionState, metrics: LiveMetrics, profile: ActiveProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        border = BorderStroke(1.dp, Palette.Border)
    ) {
        Column(Modifier.padding(20.dp)) {
            InfoRow("Status", statusText(state))
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Palette.Border)
            InfoRow("Mode", profile.modeLabel)
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Palette.Border)
            InfoRow("Profile", profile.name)
            Text(
                profile.args,
                color = Palette.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Palette.Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Latency", metrics.latencyMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                MetricTile("Success rate", metrics.successRate?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

private fun statusText(s: ConnectionState) = when (s) {
    is ConnectionState.Connected -> "Running"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Disconnected -> "Disconnected"
    is ConnectionState.Failed -> "Error"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Palette.TextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Palette.SurfaceHigh, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
