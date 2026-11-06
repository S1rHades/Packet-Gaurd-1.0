package io.github.romanvht.byedpi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

private data class ListEditorState(val id: String?, val name: String, val domains: String)

@Composable
fun BenchmarkScreen(
    vm: BenchmarkViewModel,
    bypassActive: Boolean,
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val test = ui.test
    val running = test.isRunning

    var editor by remember { mutableStateOf<ListEditorState?>(null) }
    var deleting by remember { mutableStateOf<ListItemUi?>(null) }

    // Pick up lists/profiles changed elsewhere each time this tab opens.
    LaunchedEffect(Unit) { vm.reload() }

    val ctx = LocalContext.current
    val sni = vm.sni
    val customName = stringResource(R.string.pg_custom_strategy)
    val ranked = remember(test.strategies, ui.profiles) {
        rankStrategies(test.strategies) { ui.nameFor(it, sni, customName) }
    }
    val winner = remember(ranked) { winnerOf(ranked) }
    val perDomain = remember(test.strategies, ui.profiles) {
        bestPerDomain(test.strategies) { ui.nameFor(it, sni, customName) }
    }
    val dashboard = remember(test.strategies, ui.profiles, ui.lists) {
        buildDashboard(test.strategies, ui.lists) { ui.nameFor(it, sni, customName) }
    }

    val hasResults = test.strategies.isNotEmpty()
    val anyDone = test.strategies.any { it.isCompleted }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            ScreenHeader(
                stringResource(R.string.pg_tab_benchmark),
                stringResource(R.string.pg_bench_subtitle),
                Modifier.staggeredEnter(0)
            )
        }

        // 1 — winning profile
        item(key = "winner") {
            WinnerCard(
                winner = winner,
                hasResults = hasResults && anyDone,
                applied = winner != null && ui.applied == winner.result.command,
                running = running,
                onApply = { w -> vm.apply(w.result.command, ctx.getString(R.string.pg_bench_winner_label, w.name)) },
                modifier = Modifier.animateItem().staggeredEnter(1)
            )
        }

        // 2 — start / stop and progress
        item(key = "run") {
            Column(
                Modifier.animateItem().staggeredEnter(2),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = bypassActive && !running,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    InfoBanner(
                        stringResource(R.string.pg_bench_pause_warning),
                        Icons.Filled.Info
                    )
                }
                PrimaryButton(
                    text = when {
                        test.isStopping -> stringResource(R.string.pg_stopping)
                        running -> stringResource(R.string.pg_stop)
                        else -> stringResource(R.string.pg_bench_start)
                    },
                    icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    onClick = { if (running) vm.stop() else vm.start() },
                    enabled = !test.isStopping,
                    container = if (running) Palette.SurfaceHigher else Palette.AccentBlue,
                    modifier = Modifier.fillMaxWidth(),
                    height = 56
                )
                AnimatedVisibility(
                    visible = running || hasResults,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val total = test.strategies.size.coerceAtLeast(1)
                        val partial = test.strategies.getOrNull(test.currentStrategy - 1)
                            ?.takeIf { !it.isCompleted && it.totalRequests > 0 }
                            ?.let { it.currentProgress / it.totalRequests.toFloat() } ?: 0f
                        val fraction = ((test.strategies.count { it.isCompleted } + partial) / total).coerceIn(0f, 1f)
                        val animated by animateFloatAsState(fraction, tween(Motion.SLOW), label = "benchProgress")
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Palette.Glow, trackColor = Palette.SurfaceHigh
                        )
                        val label = when {
                            running && test.currentStrategy > 0 -> {
                                val cmd = test.strategies.getOrNull(test.currentStrategy - 1)?.command
                                stringResource(R.string.pg_bench_testing, test.currentStrategy, test.strategies.size) +
                                    (cmd?.let { " · ${ui.nameFor(it, sni, customName)}" } ?: "")
                            }
                            running -> stringResource(R.string.pg_preparing)
                            test.failed || test.wasInterrupted -> stringResource(R.string.pg_bench_interrupted)
                            else -> pluralStringResource(R.plurals.pg_bench_finished, test.strategies.size, test.strategies.size)
                        }
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                            label = "benchStatus"
                        ) { Text(it, color = Palette.TextSecondary, fontSize = 13.sp) }
                    }
                }
            }
        }

        // 3 — results dashboard: takes over from the old leaderboard, right under the run controls
        if (!dashboard.isEmpty) {
            item(key = "dashboard") {
                ResultsDashboard(
                    dash = dashboard,
                    appliedCommand = ui.applied,
                    canApply = !running,
                    onApply = { p -> vm.apply(p.command, ctx.getString(R.string.pg_bench_result_label, p.name)) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // 4 — best profile per domain
        item(key = "domains") {
            DomainTable(perDomain, Modifier.animateItem().staggeredEnter(3))
        }

        // 5 — link lists
        item(key = "lists") {
            AppCard(Modifier.animateItem().staggeredEnter(4)) {
                SectionTitle(stringResource(R.string.pg_link_lists)) {
                    Text(pluralStringResource(R.plurals.pg_sites, ui.activeSites, ui.activeSites), color = Palette.TextSecondary, fontSize = 12.sp)
                }
                ui.lists.forEach { list ->
                    key(list.id) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !running) { vm.toggleList(list.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = list.active,
                                onCheckedChange = { vm.toggleList(list.id) },
                                enabled = !running,
                                colors = checkboxColors()
                            )
                            Column(Modifier.weight(1f)) {
                                Text(list.name, fontWeight = FontWeight.SemiBold)
                                Text(pluralStringResource(R.plurals.pg_sites, list.domains.size, list.domains.size), color = Palette.TextSecondary, fontSize = 12.sp)
                            }
                            IconButton(
                                enabled = !running,
                                onClick = {
                                    editor = ListEditorState(list.id, list.name, list.domains.joinToString("\n"))
                                }
                            ) { Icon(Icons.Filled.Edit, stringResource(R.string.pg_edit_named, list.name), tint = Palette.TextSecondary) }
                            IconButton(enabled = !running, onClick = { deleting = list }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.pg_delete_named, list.name), tint = Palette.TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        stringResource(R.string.pg_new_list),
                        onClick = { editor = ListEditorState(null, "", "") },
                        enabled = !running,
                        icon = Icons.Filled.Add
                    )
                    TextButton(onClick = vm::resetLists, enabled = !running) { Text(stringResource(R.string.pg_restore_defaults)) }
                }
            }
        }

        // 6 — profiles to test
        item(key = "profiles") {
            AppCard(Modifier.animateItem().staggeredEnter(5)) {
                SectionTitle(stringResource(R.string.pg_profiles_to_test)) {
                    Text(stringResource(R.string.pg_n_selected, ui.selected.size), color = Palette.TextSecondary, fontSize = 12.sp)
                }
                PillTabs(
                    items = ProfilePack.entries.toList(),
                    selected = ui.pack,
                    onSelect = vm::selectPack,
                    label = { pack -> "${stringResource(pack.label)} (${ui.profiles.count { it.pack == pack }})" }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::selectAllVisible, enabled = !running) { Text(stringResource(R.string.pg_select_all)) }
                    TextButton(onClick = vm::clearVisible, enabled = !running) { Text(stringResource(R.string.pg_clear)) }
                }
                if (ui.pack == ProfilePack.Custom && ui.visibleProfiles.isEmpty()) {
                    HelperText(stringResource(R.string.pg_bench_no_custom))
                }
                if (ui.pack == ProfilePack.Community) {
                    HelperText(stringResource(R.string.pg_bench_community_help))
                }
            }
        }
        items(ui.visibleProfiles, key = { "p:" + it.pack.name + it.command }) { p ->
            val selected = p.command in ui.selected
            val bg by animateColorAsState(
                if (selected) Palette.SurfaceHigh else Palette.Surface, tween(Motion.MEDIUM), label = "profileBg"
            )
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable(enabled = !running) { vm.toggleProfile(p.command) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { vm.toggleProfile(p.command) },
                    enabled = !running,
                    colors = checkboxColors()
                )
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        p.command, color = Palette.TextSecondary, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

    }

    editor?.let { state ->
        ListEditorDialog(
            state = state,
            vm = vm,
            onDismiss = { editor = null }
        )
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface,
            title = { Text(stringResource(R.string.pg_delete_list_title, list.name)) },
            text = { Text(stringResource(R.string.pg_delete_list_body)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteList(list.id); deleting = null }) {
                    Text(stringResource(R.string.pg_delete), color = Palette.Error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pg_cancel)) } }
        )
    }
}

@Composable
private fun WinnerCard(
    winner: RankedStrategy?,
    hasResults: Boolean,
    applied: Boolean,
    running: Boolean,
    onApply: (RankedStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = when {
        winner != null -> 2
        hasResults && !running -> 1
        else -> 0
    }
    AppCard(modifier, borderColor = if (winner != null) Palette.Glow else Palette.Border) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(Motion.SLOW)) + scaleIn(tween(Motion.SLOW), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(Motion.FAST))
            },
            label = "winnerState"
        ) { st ->
            when (st) {
                0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.SurfaceHigh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.EmojiEvents, null, tint = Palette.TextSecondary) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pg_winning_profile), style = MaterialTheme.typography.titleMedium)
                        HelperText(
                            stringResource(if (running) R.string.pg_winner_running else R.string.pg_winner_idle),
                            Modifier.padding(top = 2.dp)
                        )
                    }
                }
                1 -> Column {
                    SectionTitle(stringResource(R.string.pg_no_winner))
                    HelperText(
                        stringResource(R.string.pg_no_winner_help)
                    )
                }
                else -> {
                    val w = winner ?: return@AnimatedContent
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                    .background(Palette.Warning.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.EmojiEvents, null, tint = Palette.Warning) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (running) R.string.pg_leading else R.string.pg_winning_profile),
                                    color = Palette.TextSecondary, fontSize = 12.sp
                                )
                                AnimatedContent(
                                    targetState = w.name,
                                    transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                                    label = "winnerName"
                                ) { Text(it, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text(
                            w.result.command, color = Palette.TextSecondary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatChip(stringResource(R.string.pg_success), "${w.result.successCount}/${w.result.totalRequests}", Modifier.weight(1f))
                            StatChip(stringResource(R.string.pg_avg_latency), w.avgLatencyMs?.let { stringResource(R.string.pg_ms, it) } ?: "—", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = when {
                                applied -> stringResource(R.string.pg_applied)
                                running -> stringResource(R.string.pg_wait_run)
                                else -> stringResource(R.string.pg_apply_winner)
                            },
                            icon = if (applied) Icons.Filled.Check else null,
                            onClick = { onApply(w) },
                            enabled = !running && !applied,
                            modifier = Modifier.fillMaxWidth(),
                            height = 48
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = Palette.TextSecondary, fontSize = 11.sp)
        RollingText(value, fontSize = 16.sp, color = Palette.Success)
    }
}

@Composable
private fun DomainTable(rows: List<DomainBest>, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        SectionTitle(stringResource(R.string.pg_best_per_domain))
        if (rows.isEmpty()) {
            HelperText(stringResource(R.string.pg_best_per_domain_help))
            return@AppCard
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(stringResource(R.string.pg_domain), Modifier.weight(1.3f), color = Palette.TextSecondary, fontSize = 12.sp)
            Text(stringResource(R.string.pg_profile), Modifier.weight(1.2f), color = Palette.TextSecondary, fontSize = 12.sp)
            Text(stringResource(R.string.pg_latency), Modifier.weight(0.7f), color = Palette.TextSecondary, fontSize = 12.sp)
        }
        HorizontalDivider(color = Palette.Border)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.site, Modifier.weight(1.3f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (row.ok) {
                    Text(
                        row.strategyName ?: "—", Modifier.weight(1.2f), fontSize = 12.sp,
                        color = Palette.Glow, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        (row.latencyMs?.let { stringResource(R.string.pg_ms, it) } ?: "—") + (row.httpCode?.let { " · $it" } ?: ""),
                        Modifier.weight(0.7f), fontSize = 12.sp
                    )
                } else {
                    Text(stringResource(R.string.pg_not_reachable), Modifier.weight(1.9f), fontSize = 12.sp, color = Palette.Error)
                }
            }
        }
    }
}

@Composable
private fun ListEditorDialog(state: ListEditorState, vm: BenchmarkViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(state.name) }
    var domains by remember { mutableStateOf(state.domains) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDomains(uri) { text -> domains = (domains.trimEnd() + "\n" + text).trim() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(stringResource(if (state.id == null) R.string.pg_new_link_list else R.string.pg_edit_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pg_list_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text(stringResource(R.string.pg_domains_or_links)) },
                    placeholder = { Text("example.com\nhttps://news.example.org/page") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    colors = fieldColors()
                )
                Text(
                    stringResource(R.string.pg_list_help),
                    color = Palette.TextSecondary, fontSize = 12.sp
                )
                SecondaryButton(
                    stringResource(R.string.pg_import_file),
                    onClick = { picker.launch(arrayOf("text/*")) },
                    icon = Icons.Filled.UploadFile
                )
                error?.let { Text(it, color = Palette.Error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = vm.saveList(state.id, name, domains)
                if (problem == null) onDismiss() else error = problem
            }) { Text(stringResource(R.string.pg_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pg_cancel)) } }
    )
}

@Composable
private fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = Palette.AccentBlue,
    uncheckedColor = Palette.Gray
)
