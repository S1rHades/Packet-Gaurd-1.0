package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    state: ConnectionState,
    metrics: LiveMetrics,
    profile: ActiveProfile,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    struggling: Boolean = false,
    onFindNew: () -> Unit = {},
    onDismissStruggle: () -> Unit = {},
    onOpenSetting: () -> Unit = {}
) {
    // Connected, but most live checks fail: amber headline and shield, and a one-line offer.
    val weak = struggling && state is ConnectionState.Connected
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
        StatusHeadline(state, weak, metrics.successRate, Modifier.staggeredEnter(1))
        Spacer(Modifier.height(8.dp))
        // The shield keeps its place in every state; what comes and goes sits below it.
        ShieldButton(state = state, onClick = onToggle, weak = weak, modifier = Modifier.staggeredEnter(2))
        ShieldCaption(state, Modifier.staggeredEnter(2))
        StruggleStrip(weak, onFindNew, onDismissStruggle, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        StatusCard(state, metrics, profile, onOpenSetting, Modifier.staggeredEnter(3))
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
private fun StatusHeadline(state: ConnectionState, weak: Boolean, successRate: Int?, modifier: Modifier = Modifier) {
    val headline = when {
        state is ConnectionState.Connected && weak -> Headline(
            stringResource(R.string.pg_connect_weak),
            successRate?.let { stringResource(R.string.pg_connect_weak_sub, it) } ?: stringResource(R.string.pg_connect_weak_sub_unknown),
            4
        )
        state is ConnectionState.Connected -> Headline(
            stringResource(R.string.pg_connect_protected),
            if (state.mode == Mode.VPN) stringResource(R.string.pg_connect_protected_vpn) else stringResource(R.string.pg_connect_protected_proxy),
            2
        )
        state is ConnectionState.Connecting -> Headline(stringResource(R.string.pg_connect_connecting), stringResource(R.string.pg_connect_connecting_sub), 1)
        state is ConnectionState.Failed -> Headline(stringResource(R.string.pg_connect_failed), state.message, 3)
        else -> Headline(stringResource(R.string.pg_connect_unprotected), stringResource(R.string.pg_connect_unprotected_sub), 0)
    }
    val color by animateColorAsState(
        when (headline.kind) {
            2 -> Palette.Success
            1 -> Palette.Sky
            3 -> Palette.Error
            4 -> Palette.Warning
            else -> Palette.TextSecondary
        },
        tween(Motion.SLOW), label = "headlineColor"
    )
    // One spoken item that's announced when it changes (Connecting → Protected), instead of
    // the animated texts, which briefly hold both the old and new words.
    val spoken = "${headline.label.lowercase().replaceFirstChar { it.uppercase() }}. ${headline.sub}"
    Column(
        modifier.clearAndSetSemantics { contentDescription = spoken; liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                // Always one line, so the shield below never moves: a long headline
                // ("CONNECTED, BUT SITES MAY NOT OPEN" in some languages) shrinks a little instead.
                val style = MaterialTheme.typography.labelLarge
                BasicText(
                    h.label,
                    style = style.copy(color = color, textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
                )
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

/** "Tap to connect" under the shield. Nothing while connecting, but the space stays, so nothing moves. */
@Composable
private fun ShieldCaption(state: ConnectionState, modifier: Modifier = Modifier) {
    val text = when (state) {
        is ConnectionState.Connected -> stringResource(R.string.pg_shield_tap_disconnect)
        ConnectionState.Disconnected -> stringResource(R.string.pg_shield_tap_connect)
        is ConnectionState.Failed -> stringResource(R.string.pg_shield_tap_retry)
        ConnectionState.Connecting -> ""
    }
    // The shield itself is announced with its action, so the caption is only visual.
    Box(modifier.heightIn(min = 20.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "shieldCaption"
        ) { t -> Text(t, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun StatusCard(
    state: ConnectionState,
    metrics: LiveMetrics,
    profile: ActiveProfile,
    onOpenSetting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state is ConnectionState.Connected
    val inUse = active || state is ConnectionState.Connecting
    var details by rememberSaveable { mutableStateOf(false) }
    AppCard(modifier, borderColor = if (active) Palette.Glow.copy(alpha = 0.55f) else Palette.Border) {
        InfoRow(stringResource(R.string.pg_connect_mode), profile.modeLabel)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Palette.Border)
        // The setting by name; tapping it opens the Strategy settings.
        SettingRow(
            label = stringResource(if (inUse) R.string.pg_connect_setting else R.string.pg_connect_last_used),
            value = profile.name,
            onClick = onOpenSetting
        )
        // The raw engine flags are for experts: folded away until asked for.
        DetailsToggle(details, onToggle = { details = !details })
        AnimatedVisibility(details, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                profile.args,
                color = Palette.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
        }
        if (active) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Palette.Border)
            HealthLine(metrics)
        }
    }
}

/** One line: a coloured dot, "Sites opening", then "98% · 96 ms". Wraps with large text. */
@Composable
private fun HealthLine(metrics: LiveMetrics) {
    val parts = listOfNotNull(
        metrics.successRate?.let { stringResource(R.string.pg_percent, it) },
        metrics.latencyMs?.let { stringResource(R.string.pg_ms, it) }
    )
    val dot = metrics.successRate?.let { scoreColor(it) } ?: Palette.Sky
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        if (parts.isEmpty()) {
            Text(stringResource(R.string.pg_connect_checking), color = Palette.TextSecondary)
        } else {
            Text(stringResource(R.string.pg_connect_sites_opening), color = Palette.TextSecondary, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            RollingText(parts.joinToString(" · "), fontSize = 17.sp)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextSecondary)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "settingValue",
            modifier = Modifier.weight(1f)
        ) { v -> Text(v, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End) }
        Chevron()
    }
}

@Composable
private fun DetailsToggle(open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(Motion.MEDIUM), label = "detailsChevron")
    TextButton(
        onClick = onToggle,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        Text(stringResource(R.string.pg_details), color = Palette.Sky, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ExpandMore, null, tint = Palette.Sky, modifier = Modifier.size(18.dp).rotate(turn))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    // Read as one item ("Mode, System-wide"); the label keeps its room and the value wraps.
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextSecondary)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "infoValue",
            modifier = Modifier.weight(1f, fill = false)
        ) { v -> Text(v, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End) }
    }
}

/**
 * "Sites stopped opening": most live checks failed. A compact strip under the shield (so the
 * shield doesn't move) with a quick way to find a new setting, and a close button.
 */
@Composable
private fun StruggleStrip(show: Boolean, onFind: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        show, modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Glass.ControlShape)
                .background(Palette.Surface.copy(alpha = 0.55f))
                .background(Palette.Warning.copy(alpha = 0.10f))
                .border(1.dp, Palette.Warning.copy(alpha = 0.7f), Glass.ControlShape)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, null, tint = Palette.Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.pg_struggle_strip), fontSize = 14.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            // Capped, so the message keeps room for whole words; long labels take two short lines.
            TextButton(
                onClick = onFind,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 132.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) { Text(stringResource(R.string.pg_struggle_find), color = Palette.Warning, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center) }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.pg_struggle_dismiss), tint = Palette.TextSecondary)
            }
        }
    }
}
