package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.data.Mode
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    state: ConnectionState,
    metrics: LiveMetrics,
    profile: ActiveProfile,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    // Pull down to check the connection now, as on most status screens (Jakob's law).
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandHeader(Modifier.align(Alignment.Start).staggeredEnter(0))
        UpdateBanner(Modifier.padding(top = 16.dp))
        WhatsNewCard(Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(20.dp))
        StatusHeadline(state, Modifier.staggeredEnter(1))
        Spacer(Modifier.height(8.dp))
        ShieldButton(state = state, onClick = onToggle, modifier = Modifier.staggeredEnter(2))
        Spacer(Modifier.height(8.dp))
        StatusCard(state, metrics, profile, Modifier.staggeredEnter(3))
    }
    }
}

@Composable
fun BrandMark(size: Int = 28, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size((size * 17.2f / 21.1f).dp, size.dp)
            .clip(ShieldShape)
            .background(Brush.verticalGradient(listOf(Palette.Sky, Palette.AccentBlue)))
    )
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(26)
        Spacer(Modifier.width(10.dp))
        Text("Packet Guard", style = MaterialTheme.typography.titleLarge)
    }
}

private data class Headline(val label: String, val sub: String, val kind: Int)

@Composable
private fun StatusHeadline(state: ConnectionState, modifier: Modifier = Modifier) {
    val headline = when (state) {
        is ConnectionState.Connected -> Headline(
            stringResource(R.string.pg_connect_protected),
            if (state.mode == Mode.VPN) stringResource(R.string.pg_connect_protected_vpn) else stringResource(R.string.pg_connect_protected_proxy),
            2
        )
        ConnectionState.Connecting -> Headline(stringResource(R.string.pg_connect_connecting), stringResource(R.string.pg_connect_connecting_sub), 1)
        ConnectionState.Disconnected -> Headline(stringResource(R.string.pg_connect_unprotected), stringResource(R.string.pg_connect_unprotected_sub), 0)
        is ConnectionState.Failed -> Headline(stringResource(R.string.pg_connect_failed), state.message, 3)
    }
    val color by animateColorAsState(
        when (headline.kind) {
            2 -> Palette.Success
            1 -> Palette.Glow
            3 -> Palette.Error
            else -> Palette.TextSecondary
        },
        tween(Motion.SLOW), label = "headlineColor"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.size(8.dp))
            AnimatedContent(
                targetState = headline,
                transitionSpec = {
                    (slideInVertically(tween(Motion.MEDIUM)) { it } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                        (slideOutVertically(tween(Motion.FAST)) { -it } + fadeOut(tween(Motion.FAST)))
                },
                label = "headline"
            ) { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.label, color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = headline.sub,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "headlineSub"
        ) { sub ->
            Text(sub, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusCard(state: ConnectionState, metrics: LiveMetrics, profile: ActiveProfile, modifier: Modifier = Modifier) {
    val active = state is ConnectionState.Connected
    AppCard(modifier, borderColor = if (active) Palette.Glow.copy(alpha = 0.55f) else Palette.Border) {
        InfoRow(stringResource(R.string.pg_connect_status), statusText(state))
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Palette.Border)
        InfoRow(stringResource(R.string.pg_connect_mode), profile.modeLabel)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Palette.Border)
        InfoRow(stringResource(R.string.pg_profile), profile.name)
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
            MetricTile(stringResource(R.string.pg_latency), metrics.latencyMs?.let { stringResource(R.string.pg_ms, it) } ?: "—", Modifier.weight(1f))
            MetricTile(stringResource(R.string.pg_success_rate), metrics.successRate?.let { stringResource(R.string.pg_percent, it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun statusText(s: ConnectionState) = when (s) {
    is ConnectionState.Connected -> stringResource(R.string.pg_status_running)
    ConnectionState.Connecting -> stringResource(R.string.pg_status_connecting)
    ConnectionState.Disconnected -> stringResource(R.string.pg_status_disconnected)
    is ConnectionState.Failed -> stringResource(R.string.pg_status_error)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextSecondary)
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "infoValue"
        ) { v -> Text(v, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .glassInset()
            .padding(14.dp)
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        RollingText(value, fontSize = 22.sp)
    }
}
