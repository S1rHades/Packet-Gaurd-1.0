package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.TestState

/** "YouTube, Discord" for the switched-on site lists. */
internal fun activeListNames(ui: BenchUi): String = ui.lists.filter { it.active }.joinToString(", ") { it.name }

/**
 * While a test or a search runs, this one card replaces the setup: what is happening, how far it
 * is, how long is left, and Stop at the bottom. The setup itself shows as one read-only line.
 */
@Composable
internal fun RunningCard(
    test: TestState,
    gen: GenState,
    ui: BenchUi,
    plan: SearchPlan,
    sni: String,
    customName: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGen = gen.running
    val stopping = test.isStopping || gen.stopping
    AppCard(modifier, borderColor = Palette.Glow, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isGen) R.string.pg_running_gen_title else R.string.pg_running_test_title),
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            if (isGen) {
                val p = gen.plan ?: plan
                MetricPill(
                    stringResource(if (p == SearchPlan.Quick) R.string.pg_gen_quick else R.string.pg_gen_thorough).substringBefore('\n'),
                    Palette.Sky
                )
            }
        }
        if (isGen) GenRunningBody(gen) else BigProgress(test, ui, sni, customName)
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = Palette.Sky, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pg_running_keep), color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (isGen) HelperText(stringResource(R.string.pg_gen_stop_note), Modifier.padding(top = 4.dp, start = 26.dp))
        SecondaryButton(
            text = stringResource(if (stopping) R.string.pg_stopping else R.string.pg_stop),
            icon = Icons.Filled.Stop,
            onClick = onStop,
            enabled = !stopping,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 52.dp)
        )
        val names = activeListNames(ui)
        if (names.isNotEmpty()) {
            HelperText(
                stringResource(R.string.pg_running_testing, names),
                Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** The search's progress: percentage, time left, bar, labelled steps, what it is doing now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenRunningBody(gen: GenState) {
    val fraction = if (gen.budget == 0) 0f else (gen.done.toFloat() / gen.budget).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(Motion.SLOW), label = "genProgress")
    val tried = (gen.done - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.clearAndSetSemantics {}) { RollingText(stringResource(R.string.pg_percent, (animated * 100).toInt()), fontSize = 34.sp) }
            Spacer(Modifier.weight(1f))
            if (!gen.stopping) TimeLeft(gen.startedAt, fraction, Modifier.padding(bottom = 6.dp))
        }
        ProgressBar(animated, Modifier.padding(top = 8.dp))
        GenSteps(gen, Modifier.padding(top = 12.dp))
        val (title, sub) = when {
            gen.stopping -> stringResource(R.string.pg_stopping) to null
            gen.phase == GenPhase.Checking -> stringResource(R.string.pg_gen_checking) to null
            gen.phase == GenPhase.Searching -> stringResource(R.string.pg_gen_round, gen.round, gen.rounds) to
                stringResource(if (gen.round <= 1) R.string.pg_gen_probing else R.string.pg_gen_building)
            gen.phase == GenPhase.Verifying -> stringResource(R.string.pg_gen_verifying) to null
            else -> "" to null
        }
        AnimatedContent(
            targetState = title to sub,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "genStatus",
            modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
        ) { (t, s) ->
            Column {
                Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (s != null) Text(s, color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tried > 0) MetricPill(pluralStringResource(R.plurals.pg_gen_tried, tried, tried), Palette.TextSecondary)
            gen.leader?.let { MetricPill(stringResource(R.string.pg_best_so_far_chip, it.opened, it.sites), Palette.Sky) }
        }
    }
}

/** Check sites · Build · Test all · Pick, with the finished steps green and the current one blue. */
@Composable
private fun GenSteps(gen: GenState, modifier: Modifier = Modifier) {
    val labels = listOf(R.string.pg_step_check, R.string.pg_step_build, R.string.pg_step_verify, R.string.pg_step_pick)
    val current = when (gen.phase) {
        GenPhase.Checking -> 0
        GenPhase.Searching -> 1
        GenPhase.Verifying -> 2
        GenPhase.Done -> 4
        GenPhase.Idle -> -1
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, res ->
            val color = when {
                i < current -> Palette.Success
                i == current -> Palette.Glow
                else -> Glass.Inset
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(
                    stringResource(res), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (i <= current) Color.White else Palette.TextSecondary,
                    fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * After a test, the setup folds into one line: which sites and how many settings, with Edit
 * (opens the full test card) and Test again.
 */
@Composable
internal fun TestSummaryCard(ui: BenchUi, onEdit: () -> Unit, onTest: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier, contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pg_test_summary_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(activeListNames(ui), pluralStringResource(R.plurals.pg_n_settings, ui.selected.size, ui.selected.size))
                        .filter { it.isNotEmpty() }.joinToString("  ·  "),
                    color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pg_edit)) }
        }
        SecondaryButton(
            text = stringResource(R.string.pg_test_again),
            icon = Icons.Filled.PlayArrow,
            onClick = onTest,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp).heightIn(min = 52.dp)
        )
    }
}

/**
 * "Make one just for me" in its own card: a clear second choice next to the ready-made test.
 * Folded to one row when results are already on screen.
 */
@Composable
internal fun GeneratorCard(
    gen: GenState,
    plan: SearchPlan,
    folded: Boolean,
    enabled: Boolean,
    onToggleFold: () -> Unit,
    onPlan: (SearchPlan) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier, contentPadding = PaddingValues(20.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .then(if (folded) Modifier.clickable(role = Role.Button, onClick = onToggleFold) else Modifier)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Palette.Sky, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pg_mode_gen_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (folded) {
                    Text(
                        stringResource(R.string.pg_mode_gen_sub), color = Palette.TextSecondary, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (folded) Icon(Icons.Filled.ChevronRight, null, tint = Palette.Sky)
            else if (gen.result != null || gen.failed) {
                // Unfolded from a result: let it fold away again.
                Icon(Icons.Filled.ExpandLess, null, tint = Palette.Sky, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onToggleFold).padding(12.dp))
            }
        }
        if (!folded) {
            HelperText(stringResource(R.string.pg_mode_gen_sub), Modifier.padding(top = 4.dp, bottom = 12.dp))
            Segmented(
                items = SearchPlan.entries.toList(),
                selected = plan,
                onSelect = onPlan,
                label = { stringResource(if (it == SearchPlan.Quick) R.string.pg_gen_quick else R.string.pg_gen_thorough) },
                enabled = enabled
            )
            SecondaryButton(
                text = stringResource(R.string.pg_gen_start),
                icon = Icons.Filled.AutoAwesome,
                onClick = onGenerate,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)
            )
            if (gen.failed && !gen.running) {
                HelperText(stringResource(R.string.pg_gen_failed), Modifier.padding(top = 10.dp), color = Palette.Warning)
            }
        }
    }
}

/** The custom lines, edited where they are used: one per line, "Name | command" or just the command. */
@Composable
internal fun CustomLinesDialog(onDismiss: () -> Unit) {
    var custom by rememberStringPref(PREF_CUSTOM_PROFILES, "")
    val parsed = parseCustomProfiles(custom).size
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(stringResource(R.string.pg_bench_custom)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text(stringResource(R.string.pg_bench_custom_label)) },
                    placeholder = { Text("My split | -s1+s -d3\n-o1 -r1+s") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
                if (parsed > 0) {
                    HelperText(pluralStringResource(R.plurals.pg_bench_custom_count, parsed, parsed), Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pg_done))
            }
        }
    )
}
