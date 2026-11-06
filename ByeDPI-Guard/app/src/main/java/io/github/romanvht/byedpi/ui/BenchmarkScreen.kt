package io.github.romanvht.byedpi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.TestState

private data class ListEditorState(val id: String?, val name: String, val domains: String)

/**
 * The benchmark as three plain steps, sized for anyone to follow:
 * 1 pick what to open, 2 start the test, 3 use the best profile. The results dashboard follows,
 * and everything for experienced users (profile packs, list editing, per-site results)
 * sits in a collapsed "Advanced options" section.
 */
@Composable
fun BenchmarkScreen(
    vm: BenchmarkViewModel,
    bypassActive: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val test = ui.test
    val running = test.isRunning

    var editor by remember { mutableStateOf<ListEditorState?>(null) }
    var deleting by remember { mutableStateOf<ListItemUi?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    // Pick up lists/profiles changed elsewhere each time this tab opens.
    LaunchedEffect(Unit) { vm.reload() }

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
    val anyDone = test.strategies.any { it.isCompleted }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            ScreenHeader(
                stringResource(R.string.pg_bench_simple_title),
                stringResource(R.string.pg_bench_simple_subtitle),
                Modifier.staggeredEnter(0)
            )
        }

        // Step 1 — what to open
        item(key = "step1") {
            StepCard(1, stringResource(R.string.pg_step1_title), Modifier.animateItem().staggeredEnter(1)) {
                if (ui.lists.isEmpty()) {
                    BigHint(stringResource(R.string.pg_step1_empty))
                } else {
                    ListTiles(ui.lists, enabled = !running, onToggle = vm::toggleList)
                    BigHint(stringResource(R.string.pg_step1_hint), Modifier.padding(top = 12.dp))
                }
            }
        }

        // Step 2 — run
        item(key = "step2") {
            StepCard(2, stringResource(R.string.pg_step2_title), Modifier.animateItem().staggeredEnter(2)) {
                AnimatedVisibility(
                    visible = bypassActive && !running,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    InfoBanner(
                        stringResource(R.string.pg_step2_pause),
                        Icons.Filled.Info,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                PrimaryButton(
                    text = when {
                        test.isStopping -> stringResource(R.string.pg_stopping)
                        running -> stringResource(R.string.pg_stop)
                        else -> stringResource(R.string.pg_step2_start)
                    },
                    icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    onClick = { if (running) vm.stop() else vm.start() },
                    enabled = !test.isStopping,
                    container = if (running) Palette.SurfaceHigher else Palette.AccentBlue,
                    modifier = Modifier.fillMaxWidth(),
                    height = 64
                )
                AnimatedVisibility(
                    visible = running || test.strategies.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    BigProgress(test, ui, sni, customName)
                }
                if (!running && test.strategies.isEmpty()) {
                    BigHint(stringResource(R.string.pg_step2_keep_open), Modifier.padding(top = 12.dp))
                }
            }
        }

        // Step 3 — the answer
        item(key = "step3") {
            StepCard(
                3, stringResource(R.string.pg_step3_title), Modifier.animateItem().staggeredEnter(3),
                highlight = winner != null && !running
            ) {
                ResultPanel(
                    winner = winner,
                    finished = anyDone && !running,
                    running = running,
                    applied = winner != null && ui.applied == winner.result.command,
                    onApply = { w -> vm.apply(w.result.command, ctx.getString(R.string.pg_bench_winner_label, w.name)) }
                )
            }
        }

        // Details for anyone who wants them
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

        // Advanced options, closed by default
        item(key = "advancedToggle") {
            AdvancedToggle(advanced, onToggle = { advanced = !advanced }, modifier = Modifier.animateItem())
        }
        if (advanced) {
            item(key = "advProfiles") {
                AppCard(Modifier.animateItem()) {
                    SectionTitle(stringResource(R.string.pg_profiles_to_test)) {
                        Text(
                            stringResource(R.string.pg_n_selected, ui.selected.size),
                            color = Palette.TextSecondary, fontSize = 13.sp
                        )
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
                ProfileRow(p, selected = p.command in ui.selected, enabled = !running, onToggle = { vm.toggleProfile(p.command) }, Modifier.animateItem())
            }
            item(key = "advLists") {
                ManageLists(
                    lists = ui.lists,
                    running = running,
                    onEdit = { list -> editor = ListEditorState(list.id, list.name, list.domains.joinToString("\n")) },
                    onDelete = { deleting = it },
                    onNew = { editor = ListEditorState(null, "", "") },
                    onReset = vm::resetLists,
                    modifier = Modifier.animateItem()
                )
            }
            item(key = "advDomains") {
                DomainTable(perDomain, Modifier.animateItem())
            }
        }
    }

    editor?.let { state ->
        ListEditorDialog(state = state, vm = vm, onDismiss = { editor = null })
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

// ---- building blocks --------------------------------------------------------------------------

/** A numbered step: a big number badge, a plain title, and its content. */
@Composable
private fun StepCard(
    number: Int,
    title: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier, borderColor = if (highlight) Palette.Glow else Palette.Border, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Glass.Accent).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$number", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
        }
        content()
    }
}

/** Larger helper text for the simple steps. */
@Composable
private fun BigHint(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary) {
    Text(text, color = color, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier)
}

/** Link lists as big on/off tiles, two per row. */
@Composable
private fun ListTiles(lists: List<ListItemUi>, enabled: Boolean, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lists.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { list ->
                    key(list.id) {
                        ListTile(list, enabled, onClick = { onToggle(list.id) }, modifier = Modifier.weight(1f))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ListTile(list: ListItemUi, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val on = list.active
    val accent by animateFloatAsState(if (on) 1f else 0f, tween(Motion.MEDIUM), label = "tileOn")
    val border by animateColorAsState(
        if (on) Palette.Glow else Glass.InsetBorder, tween(Motion.MEDIUM), label = "tileBorder"
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(Glass.Inset)
            .background(Palette.Glow.copy(alpha = 0.18f * accent))
            .border(if (on) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Palette.Sky.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(list.name.take(1).uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Sky)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (on) Icons.Filled.CheckCircle else Icons.Filled.Add,
                contentDescription = null,
                tint = if (on) Palette.Success else Palette.TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(list.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.pg_sites, list.domains.size, list.domains.size),
            color = Palette.TextSecondary, fontSize = 13.sp
        )
    }
}

/** Big percentage, a thick bar and one plain sentence about what's happening. */
@Composable
private fun BigProgress(test: TestState, ui: BenchUi, sni: String, customName: String) {
    val running = test.isRunning
    val total = test.strategies.size.coerceAtLeast(1)
    val partial = test.strategies.getOrNull(test.currentStrategy - 1)
        ?.takeIf { !it.isCompleted && it.totalRequests > 0 }
        ?.let { it.currentProgress / it.totalRequests.toFloat() } ?: 0f
    val fraction = ((test.strategies.count { it.isCompleted } + partial) / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(Motion.SLOW), label = "benchProgress")

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(stringResource(R.string.pg_percent, (animated * 100).toInt()), fontSize = 34.sp)
            Spacer(Modifier.width(10.dp))
            if (running) {
                Icon(Icons.Filled.HourglassTop, null, tint = Palette.Sky, modifier = Modifier.padding(bottom = 8.dp).size(20.dp))
            }
        }
        ProgressBar(animated, Modifier.padding(vertical = 10.dp))
        val label = when {
            running && test.currentStrategy > 0 -> {
                val cmd = test.strategies.getOrNull(test.currentStrategy - 1)?.command
                stringResource(R.string.pg_step2_trying, test.currentStrategy, test.strategies.size) +
                    (cmd?.let { "\n" + ui.nameFor(it, sni, customName) } ?: "")
            }
            running -> stringResource(R.string.pg_preparing)
            test.failed || test.wasInterrupted -> stringResource(R.string.pg_bench_interrupted)
            else -> pluralStringResource(R.plurals.pg_bench_finished, test.strategies.size, test.strategies.size)
        }
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "benchStatus"
        ) { BigHint(it, color = Color.White) }
    }
}

/** A thick, rounded progress bar in the accent gradient. */
@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Glass.Inset)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Glass.Accent)
        )
    }
}

/** Step 3: the one answer most people need, in plain words. */
@Composable
private fun ResultPanel(
    winner: RankedStrategy?,
    finished: Boolean,
    running: Boolean,
    applied: Boolean,
    onApply: (RankedStrategy) -> Unit
) {
    val state = when {
        winner != null -> 2
        finished -> 1
        else -> 0
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(Motion.SLOW)) + scaleIn(tween(Motion.SLOW), initialScale = 0.96f)) togetherWith fadeOut(tween(Motion.FAST))
        },
        label = "resultState"
    ) { st ->
        when (st) {
            0 -> BigNote(Icons.Filled.EmojiEvents, Palette.TextSecondary, stringResource(R.string.pg_step3_idle))
            1 -> Column {
                BigNote(Icons.Filled.SentimentDissatisfied, Palette.Warning, stringResource(R.string.pg_step3_none_title), bold = true)
                BigHint(stringResource(R.string.pg_step3_none_help), Modifier.padding(top = 8.dp))
            }
            else -> {
                val w = winner ?: return@AnimatedContent
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Warning.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.EmojiEvents, null, tint = Palette.Warning, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (running) R.string.pg_step3_best_so_far else R.string.pg_step3_best),
                                color = Palette.TextSecondary, fontSize = 14.sp
                            )
                            AnimatedContent(
                                targetState = w.name,
                                transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                                label = "winnerName"
                            ) { Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp) }
                        }
                    }
                    val sites = w.result.siteResults.size
                    val opened = w.result.siteResults.count { it.successCount > 0 }
                    Fact(
                        Icons.Filled.CheckCircle,
                        scoreColor(percentOf(opened, sites)),
                        stringResource(R.string.pg_step3_opens, opened, sites),
                        Modifier.padding(top = 16.dp)
                    )
                    w.avgLatencyMs?.let { ms ->
                        val speed = Speed.of(ms)
                        Fact(
                            Icons.Filled.Bolt,
                            when (speed) {
                                Speed.Fast -> Palette.Success
                                Speed.Ok -> Palette.Warning
                                Speed.Slow -> Palette.Error
                            },
                            stringResource(R.string.pg_step3_speed, stringResource(speed.label)) + "  ·  " +
                                stringResource(R.string.pg_ms, ms),
                            Modifier.padding(top = 8.dp)
                        )
                    }
                    PrimaryButton(
                        text = when {
                            applied -> stringResource(R.string.pg_step3_in_use)
                            running -> stringResource(R.string.pg_step3_wait)
                            else -> stringResource(R.string.pg_step3_use)
                        },
                        icon = if (applied) Icons.Filled.Check else null,
                        onClick = { onApply(w) },
                        enabled = !running && !applied,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        height = 60
                    )
                }
            }
        }
    }
}

@Composable
private fun BigNote(icon: ImageVector, tint: Color, text: String, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text, fontSize = 17.sp, lineHeight = 23.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) Color.White else Palette.TextSecondary
        )
    }
}

/** One fact about the winner: a colored icon and a short sentence. */
@Composable
private fun Fact(icon: ImageVector, tint: Color, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glassInset().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The single row that opens or closes the advanced section. */
@Composable
private fun AdvancedToggle(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(Motion.MEDIUM), label = "advArrow")
    Row(
        modifier
            .fillMaxWidth()
            .glass()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Sky.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Tune, null, tint = Palette.Sky, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pg_adv_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            HelperText(stringResource(R.string.pg_adv_subtitle))
        }
        Icon(Icons.Filled.ExpandMore, null, tint = Palette.TextSecondary, modifier = Modifier.rotate(arrow))
    }
}

@Composable
private fun ProfileRow(p: ProfileItem, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (selected) Palette.Glow.copy(alpha = 0.14f) else Color.Transparent, tween(Motion.MEDIUM), label = "profileBg"
    )
    Row(
        modifier
            .fillMaxWidth()
            .glassInset()
            .background(bg)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled, colors = checkboxColors())
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                p.command, color = Palette.TextSecondary, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ManageLists(
    lists: List<ListItemUi>,
    running: Boolean,
    onEdit: (ListItemUi) -> Unit,
    onDelete: (ListItemUi) -> Unit,
    onNew: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier) {
        SectionTitle(stringResource(R.string.pg_adv_lists))
        lists.forEach { list ->
            key(list.id) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(list.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            pluralStringResource(R.plurals.pg_sites, list.domains.size, list.domains.size),
                            color = Palette.TextSecondary, fontSize = 13.sp
                        )
                    }
                    IconButton(enabled = !running, onClick = { onEdit(list) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.pg_edit_named, list.name), tint = Palette.TextSecondary)
                    }
                    IconButton(enabled = !running, onClick = { onDelete(list) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.pg_delete_named, list.name), tint = Palette.TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(R.string.pg_new_list), onClick = onNew, enabled = !running, icon = Icons.Filled.Add)
            TextButton(onClick = onReset, enabled = !running) { Text(stringResource(R.string.pg_restore_defaults), textAlign = TextAlign.Center) }
        }
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
