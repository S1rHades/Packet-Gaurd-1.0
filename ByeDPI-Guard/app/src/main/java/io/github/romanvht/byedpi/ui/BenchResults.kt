package io.github.romanvht.byedpi.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.R

/** The colour for a 0–100 score: green from 80, amber from 50, red below. */
fun scoreTint(score: Int): Color = when {
    score >= 80 -> Palette.Success
    score >= 50 -> Palette.Warning
    else -> Palette.Error
}

/** The overall score as a ring with the number in the middle. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shown by animateFloatAsState(score / 100f, tween(Motion.SLOW), label = "scoreRing")
    val tint = scoreTint(score)
    val label = stringResource(R.string.pg_score_a11y, score)
    Box(modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.10f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(tint, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$score", fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold)
    }
}

/** The three parts of the score, each as a labelled bar, so the number can be understood. */
@Composable
fun ScoreParts(score: CmdScore, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScorePart(stringResource(R.string.pg_score_reach), score.reach)
        ScorePart(stringResource(R.string.pg_score_success), score.success)
        ScorePart(stringResource(R.string.pg_score_speed), score.speed)
    }
}

@Composable
private fun ScorePart(label: String, value: Int) {
    val shown by animateFloatAsState(value / 100f, tween(Motion.SLOW), label = "scorePart")
    // Read as one item: "Sites, 75 percent".
    Row(
        Modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(value / 100f, 0f..1f) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.widthIn(min = 72.dp).padding(end = 6.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Glass.Inset)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(3.dp)).background(scoreTint(value)))
        }
        Text("$value", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 34.dp).padding(start = 8.dp))
    }
}

@Composable
private fun reliabilityText(r: Reliability) = stringResource(
    when (r) {
        Reliability.Always -> R.string.pg_rel_always
        Reliability.Mostly -> R.string.pg_rel_mostly
        Reliability.Sometimes -> R.string.pg_rel_sometimes
    }
)

private fun reliabilityTint(r: Reliability) = when (r) {
    Reliability.Always -> Palette.Success
    Reliability.Mostly -> Palette.Warning
    Reliability.Sometimes -> Palette.Error
}

private fun speedTint(s: Speed) = when (s) {
    Speed.Fast -> Palette.Success
    Speed.Ok -> Palette.Warning
    Speed.Slow -> Palette.Error
}

/** Sites opened, speed and dependability as three plain chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactChips(c: Choice, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val all = c.stat.reachable >= c.stat.sites
        MetricPill(
            if (all) stringResource(R.string.pg_chip_opens_all, c.stat.sites)
            else stringResource(R.string.pg_chip_opens, c.stat.reachable, c.stat.sites),
            if (all) Palette.Success else scoreTint(c.score.reach),
            large = true
        )
        c.speed?.let { s ->
            MetricPill(stringResource(s.label) + (c.stat.avgLatencyMs?.let { " · " + stringResource(R.string.pg_ms, it) } ?: ""), speedTint(s), large = true)
        }
        MetricPill(reliabilityText(c.reliability), reliabilityTint(c.reliability), large = true)
    }
}

/** Gains in green and losses in red, against the recommendation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeoffChips(list: List<Tradeoff>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { t ->
            val text = when (t) {
                is Tradeoff.Sites -> if (t.diff > 0) pluralStringResource(R.plurals.pg_tr_more_sites, t.diff, t.diff)
                else pluralStringResource(R.plurals.pg_tr_fewer_sites, -t.diff, -t.diff)
                is Tradeoff.Latency -> if (t.diffMs < 0) stringResource(R.string.pg_tr_faster, -t.diffMs)
                else stringResource(R.string.pg_tr_slower, t.diffMs)
                is Tradeoff.Dependability -> stringResource(if (t.diffPoints > 0) R.string.pg_tr_more_dep else R.string.pg_tr_less_dep)
                is Tradeoff.ListSites -> stringResource(R.string.pg_group_some, t.opened, t.sites)
            }
            MetricPill(text, if (t.good) Palette.Success else Palette.Error, large = true)
        }
    }
}

/** The answer: one setting, its score, why, and one button. */
@Composable
fun RecommendedCard(
    d: Decision,
    running: Boolean,
    applied: String?,
    onApply: (Choice) -> Unit,
    onApplyGoal: (Choice) -> Unit,
    onApplyCombined: (Combined) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = d.recommended ?: return
    AppCard(modifier, borderColor = Palette.Glow, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MetricPill(stringResource(if (running) R.string.pg_step3_best_so_far else R.string.pg_rec_badge), Palette.Glow, large = true)
                AnimatedContent(
                    targetState = c.name,
                    transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                    label = "recName"
                ) { Text(it, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            ScoreRing(c.score.overall, size = 64.dp)
        }
        FactChips(c, Modifier.padding(top = 12.dp))
        if (!running) {
            Text(
                stringResource(
                    when (d.reason) {
                        Reason.OnlyAllSites -> R.string.pg_rec_why_only_all
                        Reason.AllSitesFastest -> R.string.pg_rec_why_all_best
                        Reason.MostSites -> R.string.pg_rec_why_most
                        else -> R.string.pg_rec_why_balance
                    }
                ),
                color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        ScoreParts(c.score, Modifier.padding(top = 12.dp))
        if (!running) CommandExplainer(c.command, Modifier.padding(top = 12.dp), max = 3)
        val inUse = applied == c.command
        PrimaryButton(
            text = when {
                inUse -> stringResource(R.string.pg_step3_in_use)
                running -> stringResource(R.string.pg_step3_wait)
                else -> stringResource(R.string.pg_rec_use)
            },
            icon = if (inUse) Icons.Filled.Check else null,
            onClick = { onApply(c) },
            enabled = !running && !inUse,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            height = 60
        )
        // Everything else about the answer lives in the same card: other goals, then how it
        // does on each site group, then one line for all groups when that opens more.
        if (!running) {
            if (d.goals.isNotEmpty()) {
                SectionDivider()
                GoalSection(d, applied, onApplyGoal)
            }
            if (d.groups.isNotEmpty()) {
                SectionDivider()
                GroupsSection(d.groups)
            }
            d.combined?.let { comb ->
                SectionDivider()
                CombinedSection(comb, applied, onApplyCombined)
            }
        }
    }
}

@Composable
private fun SectionDivider() = HorizontalDivider(
    color = Color.White.copy(alpha = 0.08f),
    modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
)

private fun goalKey(g: GoalOption) = g.kind.name + (g.listId ?: "")

/** "Care about something else?": one chip per goal, and the option for the chosen one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSection(d: Decision, applied: String?, onApply: (Choice) -> Unit, modifier: Modifier = Modifier) {
    if (d.goals.isEmpty()) return
    var picked by rememberSaveable { mutableStateOf(goalKey(d.goals.first())) }
    val goal = d.goals.firstOrNull { goalKey(it) == picked } ?: d.goals.first()
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pg_goal_question), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            d.goals.forEach { g ->
                Pill(
                    text = when (g.kind) {
                        GoalKind.Fastest -> stringResource(R.string.pg_goal_fastest)
                        GoalKind.Reliable -> stringResource(R.string.pg_goal_reliable)
                        GoalKind.Group -> stringResource(R.string.pg_goal_for, g.listName.orEmpty())
                    },
                    selected = goalKey(g) == goalKey(goal),
                    onClick = { picked = goalKey(g) }
                )
            }
        }
        AnimatedContent(
            targetState = goal,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "goal"
        ) { g ->
            Column(Modifier.fillMaxWidth().padding(top = 14.dp).glassInset().padding(14.dp)) {
                if (g.same) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = Palette.Success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pg_goal_same), fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.choice.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (g.kind) {
                                GoalKind.Fastest -> stringResource(R.string.pg_goal_fastest_sub, g.choice.stat.avgLatencyMs ?: 0L)
                                GoalKind.Reliable -> stringResource(R.string.pg_goal_reliable_sub, g.choice.stat.successRate)
                                GoalKind.Group -> stringResource(R.string.pg_goal_group_sub, g.listOpened, g.listSites, g.listName.orEmpty())
                            },
                            color = Palette.TextSecondary, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    ScoreRing(g.choice.score.overall, size = 44.dp)
                }
                if (g.tradeoffs.isNotEmpty()) {
                    Text(stringResource(R.string.pg_goal_vs), color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    TradeoffChips(g.tradeoffs)
                }
                val inUse = applied == g.choice.command
                SecondaryButton(
                    stringResource(if (inUse) R.string.pg_step3_in_use else R.string.pg_goal_use),
                    onClick = { onApply(g.choice) },
                    enabled = !inUse,
                    icon = if (inUse) Icons.Filled.Check else null,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        }
    }
}

/** When each group has its own best setting: one line that uses each where it works best (inside the recommendation). */
@Composable
private fun CombinedSection(c: Combined, applied: String?, onApply: (Combined) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Hub, null, tint = Palette.Sky, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.pg_comb_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.pg_comb_body, c.expectedOpened, c.sites),
            color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp)
        )
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).glassInset().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.parts.forEach { (group, setting) ->
                Row {
                    Text(group ?: stringResource(R.string.pg_comb_else), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(setting, fontSize = 13.sp, color = Palette.Sky, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.2f))
                }
            }
        }
        val inUse = applied == c.command
        SecondaryButton(
            text = stringResource(if (inUse) R.string.pg_step3_in_use else R.string.pg_comb_use),
            onClick = { onApply(c) },
            enabled = !inUse,
            icon = if (inUse) Icons.Filled.Check else null,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        HelperText(stringResource(R.string.pg_comb_note), Modifier.padding(top = 8.dp))
    }
}

/** How the recommendation does on each site group (inside the recommendation). */
@Composable
private fun GroupsSection(groups: List<GroupRow>, modifier: Modifier = Modifier) {
    if (groups.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pg_groups_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        HelperText(stringResource(R.string.pg_groups_help), Modifier.padding(top = 2.dp, bottom = 8.dp))
        groups.forEachIndexed { i, g ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val all = g.opened >= g.sites
                MetricPill(
                    if (all) stringResource(R.string.pg_group_all) else stringResource(R.string.pg_group_some, g.opened, g.sites),
                    if (all) Palette.Success else if (g.opened > 0) Palette.Warning else Palette.Error,
                    large = true
                )
            }
        }
    }
}

/** Every working setting, best score first, folded until asked for. */
@Composable
fun AllResultsCard(d: Decision, applied: String?, canApply: Boolean, onApply: (Choice) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val expandedText = stringResource(if (open) R.string.pg_a11y_expanded else R.string.pg_a11y_collapsed)
    AppCard(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = expandedText }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pg_all_results, d.ranked.size), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pg_score_help), color = Palette.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Palette.TextSecondary)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) {
                d.ranked.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(c.score.overall, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                c.command, fontSize = 11.sp, color = Palette.TextSecondary, fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.pg_chip_opens, c.stat.reachable, c.stat.sites) +
                                    (c.stat.avgLatencyMs?.let { " · " + stringResource(R.string.pg_ms, it) } ?: "") +
                                    " · " + reliabilityText(c.reliability),
                                fontSize = 12.sp, color = Palette.TextSecondary
                            )
                        }
                        TextButton(onClick = { onApply(c) }, enabled = canApply && applied != c.command, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (applied == c.command) R.string.pg_step3_in_use else R.string.pg_apply))
                        }
                    }
                }
                if (d.outclassed > 0) {
                    HelperText(pluralStringResource(R.plurals.pg_outclassed, d.outclassed, d.outclassed), Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
