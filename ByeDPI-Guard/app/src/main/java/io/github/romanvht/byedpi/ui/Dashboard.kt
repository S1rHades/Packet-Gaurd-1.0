package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.R
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Glass surfaces: translucent navy over soft blue glows, with a light top edge.
// ---------------------------------------------------------------------------------------------

private val GlassShape = RoundedCornerShape(20.dp)

fun Modifier.glass(shape: Shape = GlassShape): Modifier = this
    .clip(shape)
    .background(Palette.Surface.copy(alpha = 0.62f))
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.015f))))
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))),
        shape
    )

/** The colored light the glass cards sit on. */
private fun Modifier.ambientGlow(): Modifier = drawBehind {
    fun glow(color: Color, x: Float, y: Float, r: Float) = drawCircle(
        Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.width * r),
        radius = size.width * r,
        center = Offset(size.width * x, size.height * y)
    )
    glow(Palette.Glow.copy(alpha = 0.22f), 0.12f, 0.10f, 0.65f)
    glow(Palette.Sky.copy(alpha = 0.12f), 0.95f, 0.45f, 0.55f)
    glow(Palette.AccentBlue.copy(alpha = 0.28f), 0.30f, 0.90f, 0.70f)
}

/** Green from 80 %, amber from 40 %, red below. */
private fun scoreColor(percent: Int): Color = when {
    percent >= 80 -> Palette.Success
    percent >= 40 -> Palette.Warning
    else -> Palette.Error
}

private fun percentOf(part: Int, whole: Int): Int = if (whole <= 0) 0 else part * 100 / whole

// ---------------------------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------------------------

/**
 * Benchmark results as a dashboard: headline numbers, a chart comparing profiles on one metric
 * at a time, and how well each link list could be opened.
 */
@Composable
fun ResultsDashboard(
    dash: Dashboard,
    appliedCommand: String?,
    canApply: Boolean,
    onApply: (ProfileStat) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().ambientGlow(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.pg_dash_title), style = MaterialTheme.typography.titleMedium)
            HelperText(stringResource(R.string.pg_dash_subtitle))
        }
        KpiGrid(dash)
        CompareCard(dash, appliedCommand, canApply, onApply)
        if (dash.lists.isNotEmpty()) ListsCard(dash)
    }
}

// ---- headline numbers -----------------------------------------------------------------------

@Composable
private fun KpiGrid(dash: Dashboard) {
    val none = stringResource(R.string.pg_dash_no_working)
    val fastest = dash.fastest
    val avg = dash.averageLatencyMs
    val fasterBy = if (fastest?.avgLatencyMs != null && avg != null && avg > 0 && dash.working > 1)
        ((avg - fastest.avgLatencyMs) * 100 / avg).toInt().takeIf { it > 0 } else null
    val unlockedPct = percentOf(dash.sitesUnlocked, dash.sitesTotal)
    val workingPct = percentOf(dash.working, dash.profiles.size)
    val workingSeries = remember(dash) {
        var n = 0
        dash.profiles.map { p -> if (p.working) n++; n.toFloat() }
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            icon = Icons.Filled.Bolt, tint = Palette.Glow,
            label = stringResource(R.string.pg_dash_kpi_fastest),
            value = fastest?.avgLatencyMs?.let { stringResource(R.string.pg_ms, it) } ?: "—",
            caption = fastest?.name ?: none,
            pill = fasterBy?.let { stringResource(R.string.pg_dash_faster_than_avg, it) to Palette.Success },
            spark = remember(dash) { sparkSeries(dash.profiles, DashMetric.Latency) },
            index = 0, modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.Filled.LockOpen, tint = Palette.Success,
            label = stringResource(R.string.pg_dash_kpi_unlocked),
            value = stringResource(R.string.pg_dash_fraction, dash.sitesUnlocked, dash.sitesTotal),
            caption = stringResource(R.string.pg_dash_by_any_profile),
            pill = stringResource(R.string.pg_percent, unlockedPct) to scoreColor(unlockedPct),
            spark = remember(dash) { sparkSeries(dash.profiles, DashMetric.Reach) },
            index = 1, modifier = Modifier.weight(1f)
        )
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val reliable = dash.mostReliable
        KpiCard(
            icon = Icons.Filled.Verified, tint = Palette.Sky,
            label = stringResource(R.string.pg_dash_kpi_reliable),
            value = reliable?.let { stringResource(R.string.pg_percent, it.successRate) } ?: "—",
            caption = reliable?.name ?: none,
            pill = dash.overallSuccessRate?.let { stringResource(R.string.pg_dash_avg_rate, it) to Palette.Sky },
            spark = remember(dash) { sparkSeries(dash.profiles, DashMetric.Success) },
            index = 2, modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.Filled.Layers, tint = Palette.Warning,
            label = stringResource(R.string.pg_dash_kpi_working),
            value = stringResource(R.string.pg_dash_fraction, dash.working, dash.profiles.size),
            caption = stringResource(R.string.pg_dash_reached_one_site),
            pill = stringResource(R.string.pg_percent, workingPct) to scoreColor(workingPct),
            spark = workingSeries,
            index = 3, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KpiCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    caption: String,
    pill: Pair<String, Color>?,
    spark: List<Float>,
    index: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxHeight()
            .staggeredEnter(index)
            .glass()
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.20f), Color.Transparent), center = Offset.Zero, radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset.Zero
                )
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.NorthEast, null, tint = Palette.TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Palette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        RollingText(value, fontSize = 22.sp)
        Text(caption, color = Palette.TextSecondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f).height(8.dp))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (pill != null) MetricPill(pill.first, pill.second, Modifier.padding(end = 52.dp))
            Sparkline(spark, tint, Modifier.align(Alignment.CenterEnd).width(46.dp).height(22.dp))
        }
    }
}

@Composable
private fun MetricPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** A small trend line that draws itself in, with a soft fill under it. */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val preview = LocalInspectionMode.current
    val reveal = remember(values) { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(values) { reveal.animateTo(1f, tween(Motion.SLOW * 2)) }
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
            return@Canvas
        }
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 2.dp.toPx()
        fun pt(i: Int) = Offset(
            i * size.width / (values.size - 1),
            pad + (size.height - 2 * pad) * (1f - if (values.max() == min) 0.5f else (values[i] - min) / span)
        )
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        clipRect(right = size.width * reveal.value) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---- profile comparison ---------------------------------------------------------------------

@Composable
private fun CompareCard(
    dash: Dashboard,
    appliedCommand: String?,
    canApply: Boolean,
    onApply: (ProfileStat) -> Unit
) {
    var metric by rememberSaveable { mutableStateOf(DashMetric.Reach) }
    val bars = remember(dash, metric) { chartBars(dash.profiles, metric) }
    var selected by remember(dash, metric) { mutableStateOf(bars.firstOrNull()?.profile?.command) }
    val current = bars.firstOrNull { it.profile.command == selected }?.profile

    Column(Modifier.fillMaxWidth().staggeredEnter(4).glass().padding(16.dp)) {
        Text(stringResource(R.string.pg_dash_compare), style = MaterialTheme.typography.titleSmall)
        HelperText(stringResource(R.string.pg_dash_compare_hint), Modifier.padding(top = 2.dp, bottom = 12.dp))
        Segmented(
            items = DashMetric.entries.toList(),
            selected = metric,
            onSelect = { metric = it },
            label = { stringResource(it.label) }
        )
        if (bars.isEmpty()) {
            HelperText(stringResource(R.string.pg_dash_no_working), Modifier.padding(top = 14.dp))
            return@Column
        }
        BarChart(bars, metric, selected, onSelect = { selected = it }, Modifier.padding(top = 16.dp))
        if (current != null) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                label = "dashProfile"
            ) { p ->
                ProfileDetail(p, dash, applied = appliedCommand == p.command, canApply = canApply, onApply = { onApply(p) })
            }
        }
    }
}

@Composable
private fun barValue(p: ProfileStat, metric: DashMetric): String = when (metric) {
    DashMetric.Latency -> p.avgLatencyMs?.let { stringResource(R.string.pg_ms, it) } ?: "—"
    DashMetric.Reach -> stringResource(R.string.pg_dash_fraction, p.reachable, p.sites)
    DashMetric.Success -> stringResource(R.string.pg_percent, p.successRate)
}

@Composable
private fun BarChart(
    bars: List<DashBar>,
    metric: DashMetric,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(190.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { i, bar ->
            val isSelected = bar.profile.command == selected
            val preview = LocalInspectionMode.current
            val grow = remember(bar.profile.command, metric) { Animatable(if (preview) bar.height.coerceIn(0.04f, 1f) else 0f) }
            LaunchedEffect(bar.profile.command, metric, bar.height) {
                delay(i * 45L)
                grow.animateTo(bar.height.coerceIn(0.04f, 1f), tween(Motion.SLOW, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            val labelColor by animateColorAsState(
                if (isSelected) Color.White else Palette.TextSecondary, tween(Motion.MEDIUM), label = "barLabel"
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) { onSelect(bar.profile.command) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    barValue(bar.profile, metric), color = labelColor, fontSize = 9.sp, maxLines = 1,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Bar(grow.value, isSelected)
                }
                Text(
                    stringResource(R.string.pg_dash_rank, i + 1), color = labelColor, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, selected: Boolean) {
    val glowAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.MEDIUM), label = "barGlow")
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction)
            .drawWithContent {
                if (glowAlpha > 0f) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Palette.Glow.copy(alpha = 0.45f * glowAlpha), Color.Transparent),
                            center = Offset(size.width / 2, 0f), radius = size.width * 1.6f
                        ),
                        radius = size.width * 1.6f, center = Offset(size.width / 2, 0f)
                    )
                }
                drawContent()
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
                )
            )
            .background(
                Brush.verticalGradient(listOf(Palette.Sky, Palette.Glow, Palette.AccentBlue)),
                alpha = glowAlpha
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f + 0.16f * glowAlpha), shape)
    )
}

@Composable
private fun ProfileDetail(
    p: ProfileStat,
    dash: Dashboard,
    applied: Boolean,
    canApply: Boolean,
    onApply: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Text(
            p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            p.command, color = Palette.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(stringResource(R.string.pg_latency), barValue(p, DashMetric.Latency), Palette.Glow, Modifier.weight(1f))
            MiniStat(stringResource(R.string.pg_dash_metric_reach), barValue(p, DashMetric.Reach), scoreColor(percentOf(p.reachable, p.sites)), Modifier.weight(1f))
            MiniStat(stringResource(R.string.pg_dash_metric_success), barValue(p, DashMetric.Success), scoreColor(p.successRate), Modifier.weight(1f))
        }
        if (dash.lists.isNotEmpty()) {
            Text(
                stringResource(R.string.pg_dash_opens), color = Palette.TextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
            dash.lists.forEach { list ->
                val reached = p.reachByList[list.id] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        list.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.9f)
                    )
                    ProgressTrack(percentOf(reached, list.sites), Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text(
                        stringResource(R.string.pg_dash_fraction, reached, list.sites),
                        fontSize = 11.sp, color = Palette.TextSecondary
                    )
                }
            }
        }
        SecondaryButton(
            stringResource(if (applied) R.string.pg_applied else R.string.pg_use_profile),
            onClick = onApply,
            enabled = canApply && !applied,
            icon = if (applied) Icons.Filled.Check else null,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.glass(RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, color = Palette.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        RollingText(value, fontSize = 14.sp, color = color)
    }
}

/** A thin rounded bar that fills to [percent] in its score color. */
@Composable
private fun ProgressTrack(percent: Int, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(percent.coerceIn(0, 100) / 100f, tween(Motion.SLOW), label = "track")
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill)
                .clip(RoundedCornerShape(3.dp))
                .background(scoreColor(percent))
        )
    }
}

// ---- link lists -----------------------------------------------------------------------------

@Composable
private fun ListsCard(dash: Dashboard) {
    val fastest = dash.fastestList?.id
    val mostOpen = dash.mostOpenList?.id
    Column(Modifier.fillMaxWidth().staggeredEnter(5).glass().padding(16.dp)) {
        Text(stringResource(R.string.pg_dash_lists), style = MaterialTheme.typography.titleSmall)
        HelperText(stringResource(R.string.pg_dash_lists_help), Modifier.padding(top = 2.dp, bottom = 6.dp))
        dash.lists.forEachIndexed { i, list ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            list.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        if (list.id == mostOpen) {
                            Spacer(Modifier.width(6.dp))
                            MetricPill(stringResource(R.string.pg_dash_badge_most_open), Palette.Success)
                        }
                        if (list.id == fastest) {
                            Spacer(Modifier.width(6.dp))
                            MetricPill(stringResource(R.string.pg_dash_badge_fastest), Palette.Glow)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pg_percent, list.reachPercent),
                        color = scoreColor(list.reachPercent), fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                ProgressTrack(list.reachPercent, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val details = buildList {
                        add(stringResource(R.string.pg_dash_sites_value, list.reachable, list.sites))
                        list.avgLatencyMs?.let { add(stringResource(R.string.pg_dash_avg_ms, it)) }
                    }.joinToString(" · ")
                    Text(details, color = Palette.TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f).width(8.dp))
                    Text(
                        list.bestProfile?.let { stringResource(R.string.pg_dash_best_profile, it) }
                            ?: stringResource(R.string.pg_dash_list_none),
                        color = if (list.bestProfile != null) Palette.Sky else Palette.Error,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
