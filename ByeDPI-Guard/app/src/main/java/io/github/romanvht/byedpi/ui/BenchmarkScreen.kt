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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
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
    val gen by vm.generator.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val test = ui.test
    // Either kind of run blocks editing; the generator drives TestService itself.
    val running = test.isRunning || gen.running
    val generate = mode == BenchMode.Generate

    var editor by remember { mutableStateOf<ListEditorState?>(null) }
    var deleting by remember { mutableStateOf<ListItemUi?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    // Pick up lists/profiles changed elsewhere each time this tab opens.
    LaunchedEffect(Unit) { vm.reload() }

    val sni = vm.sni
    val customName = stringResource(R.string.pg_custom_strategy)
    val perDomain = remember(test.strategies, ui.profiles) {
        bestPerDomain(test.strategies) { ui.nameFor(it, sni, customName) }
    }
    val dashboard = remember(test.strategies, ui.profiles, ui.lists) {
        buildDashboard(test.strategies, ui.lists) { ui.nameFor(it, sni, customName) }
    }
    val anyDone = test.strategies.any { it.isCompleted }
    val decision = remember(dashboard, ui.lists) { decide(dashboard, ui.lists) }

    // Results first: after a test the setup folds into one line ("Tested … · Test again").
    val hasResults = !running && (if (generate) gen.result != null || gen.failed else anyDone)
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(running) { if (running) setupOpen = false }
    val showSetup = running || !hasResults || setupOpen

    // The start button, also pinned to the bottom (in thumb reach) while step 2 is off screen.
    val MainAction: @Composable (Modifier) -> Unit = { m ->
        PrimaryButton(
            text = when {
                test.isStopping || gen.stopping -> stringResource(R.string.pg_stopping)
                running -> stringResource(R.string.pg_stop)
                generate -> stringResource(R.string.pg_gen_start)
                else -> stringResource(R.string.pg_step2_start)
            },
            icon = if (running) Icons.Filled.Stop else if (generate) Icons.Filled.AutoAwesome else Icons.Filled.PlayArrow,
            onClick = { if (running) vm.stop() else vm.start() },
            enabled = !test.isStopping && !gen.stopping,
            container = if (running) Palette.SurfaceHigher else Palette.AccentBlue,
            modifier = m,
            height = 64
        )
    }
    val listState = rememberLazyListState()
    val pinAction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val setup = info.visibleItemsInfo.firstOrNull { it.key == "setup" }
            val setupShown = setup != null && setup.offset + setup.size <= info.viewportEndOffset
            val resultsShown = info.visibleItemsInfo.any { it.key == "results" || it.key == "summary" }
            info.totalItemsCount > 0 && !setupShown && !resultsShown && (setup != null || info.visibleItemsInfo.none { it.key == "summary" })
        }
    }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp), // room to scroll past the pinned button,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            ScreenHeader(
                stringResource(R.string.pg_bench_simple_title),
                stringResource(R.string.pg_bench_simple_subtitle),
                Modifier.staggeredEnter(0)
            )
        }

        // The setup: what to open and how to test. Folds into one line once there's an answer.
        if (showSetup) {
            item(key = "setup") {
                AppCard(Modifier.animateItem().staggeredEnter(1), contentPadding = PaddingValues(20.dp)) {
                    if (ui.lists.isEmpty()) {
                        BigHint(stringResource(R.string.pg_step1_empty))
                    } else {
                        ListTiles(ui.lists, enabled = !running, onToggle = vm::toggleList)
                        BigHint(stringResource(R.string.pg_step1_hint), Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    ModeChoice(mode, plan, enabled = !running, onMode = vm::selectMode, onPlan = vm::selectPlan)
                    Spacer(Modifier.height(14.dp))
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
                    MainAction(Modifier.fillMaxWidth())
                    if (generate) {
                        AnimatedVisibility(
                            visible = gen.running || gen.failed,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) { GenProgress(gen, plan) }
                    } else {
                        AnimatedVisibility(
                            visible = running,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            BigProgress(test, ui, sni, customName)
                        }
                    }
                    if (!running && !hasResults) {
                        BigHint(stringResource(R.string.pg_step2_keep_open), Modifier.padding(top = 12.dp))
                    }
                    if (hasResults && setupOpen) {
                        TextButton(onClick = { setupOpen = false }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.pg_setup_close))
                        }
                    }
                }
            }
        } else {
            item(key = "summary") {
                val tested = ui.lists.filter { it.active }.joinToString(", ") { it.name }
                TestedSummary(
                    stringResource(R.string.pg_tested_summary, tested.ifEmpty { "—" }),
                    onTestAgain = { setupOpen = true },
                    modifier = Modifier.animateItem().staggeredEnter(1)
                )
            }
        }

        // The answer, first thing after a test.
        if (generate) {
            if (gen.result != null || (!gen.running && gen.failed)) {
                item(key = "results") {
                    AppCard(
                        Modifier.animateItem().staggeredEnter(2),
                        borderColor = if (gen.result?.command != null && !gen.running) Palette.Glow else Palette.Border,
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        GenResultPanel(
                            gen = gen,
                            applied = ui.applied,
                            onApply = { command -> vm.apply(command, ctx.getString(R.string.pg_gen_made)) },
                            onSearchLonger = {
                                vm.selectPlan(SearchPlan.Thorough)
                                vm.start()
                            }
                        )
                    }
                }
            }
        } else if (decision.recommended != null) {
            item(key = "results") {
                RecommendedCard(
                    decision, running = running, applied = ui.applied,
                    onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_winner_label, c.name)) },
                    modifier = Modifier.animateItem().staggeredEnter(2)
                )
            }
            if (!running) {
                decision.combined?.let { comb ->
                    item(key = "combined") {
                        CombinedCard(
                            comb, applied = ui.applied,
                            onApply = { vm.apply(it.command, ctx.getString(R.string.pg_comb_label)) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item(key = "goals") {
                    GoalPicker(
                        decision, applied = ui.applied,
                        onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_result_label, c.name)) },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "groups") { GroupsCard(decision.groups, Modifier.animateItem()) }
                item(key = "all") {
                    AllResultsCard(
                        decision, applied = ui.applied, canApply = !running,
                        onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_result_label, c.name)) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else if (anyDone && !running) {
            item(key = "results") {
                AppCard(Modifier.animateItem().staggeredEnter(2), contentPadding = PaddingValues(20.dp)) {
                    ResultPanel(
                        winner = null,
                        finished = true,
                        running = false,
                        applied = false,
                        onApply = {},
                        onTryGenerator = {
                            vm.suggestGenerator()
                            vm.start()
                        }
                    )
                }
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
    AnimatedVisibility(
        visible = pinAction,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Background.copy(alpha = 0.92f))))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
        ) { MainAction(Modifier.fillMaxWidth()) }
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
        if (running && !test.isStopping) TimeLeft(ui.startedAt, fraction)
    }
}

/**
 * The search's stages as a row of short bars: checking the sites, each round, then the re-test.
 * Finished stages are green, the current one blue, the rest empty, so the finish line is visible.
 */
@Composable
private fun StageStrip(gen: GenState, plan: SearchPlan, modifier: Modifier = Modifier) {
    val rounds = if (gen.rounds > 0) gen.rounds else plan.rounds
    val total = rounds + 2
    val finished = !gen.running && gen.result != null
    val current = when {
        finished -> total
        gen.phase == GenPhase.Checking -> 0
        gen.phase == GenPhase.Searching -> gen.round.coerceIn(1, rounds)
        gen.phase == GenPhase.Verifying -> total - 1
        else -> -1
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val color = when {
                i < current -> Palette.Success
                i == current && gen.running -> Palette.Glow
                else -> Glass.Inset
            }
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/** "About 3 minutes left", worked out from how fast the run has gone so far. */
@Composable
private fun TimeLeft(startedAt: Long, fraction: Float) {
    if (startedAt <= 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val minutes = minutesLeft(now - startedAt, fraction) ?: return
    BigHint(
        pluralStringResource(R.plurals.pg_time_left, minutes, minutes),
        Modifier.padding(top = 6.dp),
        color = Palette.Sky
    )
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
    onApply: (RankedStrategy) -> Unit,
    onTryGenerator: () -> Unit
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
                BigNote(Icons.Filled.SentimentDissatisfied, Palette.Error, stringResource(R.string.pg_step3_none_title), bold = true)
                BigHint(stringResource(R.string.pg_step3_none_help), Modifier.padding(top = 8.dp))
                // End on a next step, not on a zero.
                PrimaryButton(
                    text = stringResource(R.string.pg_try_generator),
                    icon = Icons.Filled.AutoAwesome,
                    onClick = onTryGenerator,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    height = 60
                )
            }
            else -> {
                val w = winner ?: return@AnimatedContent
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Glow.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.EmojiEvents, null, tint = Palette.Sky, modifier = Modifier.size(30.dp)) }
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
                    if (!running) CommandExplainer(w.result.command, Modifier.padding(top = 12.dp), max = 3)
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
                // Show what will be saved, so a messy paste is never a surprise.
                val parsed = remember(domains) { parseDomains(domains) }
                if (parsed.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.pg_list_preview, parsed.size, parsed.size) +
                            ": " + parsed.take(3).joinToString(", ") + if (parsed.size > 3) "…" else "",
                        color = Palette.Sky, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
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

// ---- command line generator -------------------------------------------------------------------

/** Step 2's two ways to test, as big tiles; the generator also offers a quick or thorough search. */
@Composable
private fun ModeChoice(
    mode: BenchMode,
    plan: SearchPlan,
    enabled: Boolean,
    onMode: (BenchMode) -> Unit,
    onPlan: (SearchPlan) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeTile(
            icon = Icons.Filled.PlayArrow,
            title = stringResource(R.string.pg_mode_ready_title),
            subtitle = stringResource(R.string.pg_mode_ready_sub),
            badge = stringResource(R.string.pg_recommended),
            selected = mode == BenchMode.ReadyMade,
            enabled = enabled,
            onClick = { onMode(BenchMode.ReadyMade) }
        )
        ModeTile(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.pg_mode_gen_title),
            subtitle = stringResource(R.string.pg_mode_gen_sub),
            selected = mode == BenchMode.Generate,
            enabled = enabled,
            onClick = { onMode(BenchMode.Generate) }
        )
        AnimatedVisibility(
            visible = mode == BenchMode.Generate,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Segmented(
                items = SearchPlan.entries.toList(),
                selected = plan,
                onSelect = onPlan,
                label = { stringResource(if (it == SearchPlan.Quick) R.string.pg_gen_quick else R.string.pg_gen_thorough) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ModeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: String? = null
) {
    val accent by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.MEDIUM), label = "modeOn")
    val border by animateColorAsState(if (selected) Palette.Glow else Glass.InsetBorder, tween(Motion.MEDIUM), label = "modeBorder")
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Glass.Inset)
            .background(Palette.Glow.copy(alpha = 0.16f * accent))
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Sky.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Palette.Sky, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (badge != null) MetricPill(badge, Palette.Success, Modifier.padding(vertical = 3.dp))
            Text(subtitle, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            null,
            tint = if (selected) Palette.Success else Palette.TextSecondary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** The search's progress: a big percentage, a thick bar and what it's doing in plain words. */
@Composable
private fun GenProgress(gen: GenState, plan: SearchPlan) {
    val fraction = if (gen.budget == 0) 0f else (gen.done.toFloat() / gen.budget).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(Motion.SLOW), label = "genProgress")
    val tried = (gen.done - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(stringResource(R.string.pg_percent, (animated * 100).toInt()), fontSize = 34.sp)
            Spacer(Modifier.width(10.dp))
            if (gen.running) {
                Icon(Icons.Filled.HourglassTop, null, tint = Palette.Sky, modifier = Modifier.padding(bottom = 8.dp).size(20.dp))
            }
        }
        ProgressBar(animated, Modifier.padding(top = 10.dp))
        StageStrip(gen, plan, Modifier.padding(top = 8.dp, bottom = 10.dp))
        val label = when {
            gen.stopping -> stringResource(R.string.pg_stopping)
            gen.failed && !gen.running -> stringResource(R.string.pg_gen_failed)
            gen.phase == GenPhase.Checking -> stringResource(R.string.pg_gen_checking)
            gen.phase == GenPhase.Searching ->
                stringResource(R.string.pg_gen_round, gen.round, gen.rounds) + "\n" +
                    stringResource(if (gen.round <= 1) R.string.pg_gen_probing else R.string.pg_gen_building) + "\n" +
                    pluralStringResource(R.plurals.pg_gen_tried, tried, tried)
            gen.phase == GenPhase.Verifying -> stringResource(R.string.pg_gen_verifying)
            gen.result != null -> pluralStringResource(R.plurals.pg_gen_finished, gen.result.tested, gen.result.tested)
            else -> ""
        }
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "genStatus"
        ) { BigHint(it, color = if (gen.failed && !gen.running) Palette.Warning else Color.White) }
        if (gen.running && !gen.stopping) TimeLeft(gen.startedAt, fraction)
        val leader = gen.leader
        if (gen.running && leader != null) {
            BigHint(
                stringResource(R.string.pg_gen_best_so_far_opens, leader.opened, leader.sites),
                Modifier.padding(top = 6.dp),
                color = Palette.Sky
            )
        }
    }
}

/** Step 3 for the generator: the command line it made, in plain words, with one big button. */
@Composable
private fun GenResultPanel(gen: GenState, applied: String?, onApply: (String) -> Unit, onSearchLonger: () -> Unit) {
    val result = gen.result
    val state = when {
        gen.running -> 0
        result == null -> 0
        result.nothingBlocked -> 1
        result.command == null -> 2
        else -> 3
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(Motion.SLOW)) + scaleIn(tween(Motion.SLOW), initialScale = 0.96f)) togetherWith fadeOut(tween(Motion.FAST))
        },
        label = "genResult"
    ) { st ->
        when (st) {
            0 -> BigNote(Icons.Filled.AutoAwesome, Palette.TextSecondary, stringResource(R.string.pg_gen_idle))
            1 -> BigNote(Icons.Filled.CheckCircle, Palette.Success, stringResource(R.string.pg_gen_nothing_blocked), bold = true)
            2 -> Column {
                BigNote(Icons.Filled.SentimentDissatisfied, Palette.Error, stringResource(R.string.pg_step3_none_title), bold = true)
                BigHint(stringResource(R.string.pg_gen_none_help), Modifier.padding(top = 8.dp))
                if (gen.plan != SearchPlan.Thorough) {
                    PrimaryButton(
                        text = stringResource(R.string.pg_gen_try_thorough),
                        icon = Icons.Filled.AutoAwesome,
                        onClick = onSearchLonger,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        height = 60
                    )
                }
            }
            else -> {
                val r = result ?: return@AnimatedContent
                val command = r.command ?: return@AnimatedContent
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Glow.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Palette.Sky, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pg_gen_made), color = Palette.TextSecondary, fontSize = 14.sp)
                            Text(stringResource(R.string.pg_gen_title_result), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        command,
                        fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Palette.Sky,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).glassInset().padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    HowItWasMade(r, Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(r.score.overall, size = 64.dp)
                        Spacer(Modifier.width(14.dp))
                        ScoreParts(r.score, Modifier.weight(1f))
                    }
                    CommandExplainer(command, Modifier.padding(top = 10.dp), max = 4)
                    Fact(
                        Icons.Filled.CheckCircle,
                        scoreColor(percentOf(r.opened, r.blocked)),
                        stringResource(R.string.pg_gen_opens, r.opened, r.blocked),
                        Modifier.padding(top = 10.dp)
                    )
                    r.latencyMs?.let { ms ->
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
                    val inUse = applied == command
                    PrimaryButton(
                        text = stringResource(if (inUse) R.string.pg_step3_in_use else R.string.pg_gen_use),
                        icon = if (inUse) Icons.Filled.Check else null,
                        onClick = { onApply(command) },
                        enabled = !inUse,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        height = 60
                    )
                    if (r.alternatives.isNotEmpty()) {
                        Text(
                            stringResource(R.string.pg_gen_others), color = Palette.TextSecondary, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                        )
                        r.alternatives.forEach { alt ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).glassInset().padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ScoreRing(alt.score.overall, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(alt.command, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.pg_gen_opens, alt.opened, alt.sites) +
                                            (alt.latencyMs?.let { "  ·  " + stringResource(R.string.pg_ms, it) } ?: ""),
                                        color = Palette.TextSecondary, fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = { onApply(alt.command) }, enabled = applied != alt.command) {
                                    Text(stringResource(if (applied == alt.command) R.string.pg_step3_in_use else R.string.pg_apply))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Whether the winner is new, what it was built from, and how it compares with lines tested before. */
@Composable
private fun HowItWasMade(r: GenResult, modifier: Modifier = Modifier) {
    val note = Modifier.padding(top = 6.dp)
    Column(modifier.fillMaxWidth()) {
        when {
            r.generated -> {
                MetricPill(stringResource(R.string.pg_gen_new_badge), Palette.Glow)
                val how = when (r.how) {
                    Origin.Combined.name -> stringResource(R.string.pg_gen_how_combined)
                    Origin.Fallback.name -> stringResource(R.string.pg_gen_how_fallback)
                    Origin.Explored.name -> stringResource(R.string.pg_gen_how_explored)
                    else -> stringResource(R.string.pg_gen_how_tuned)
                }
                val from = r.madeFrom.orEmpty()
                Text(
                    if (from.isEmpty()) how else stringResource(R.string.pg_gen_how_from, how, from.joinToString("  +  ")),
                    color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = note
                )
                r.knownBest?.takeIf { it.score.overall <= r.score.overall }?.let { known ->
                    Text(
                        stringResource(R.string.pg_gen_beats_known, r.score.overall, known.score.overall),
                        color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = note
                    )
                }
            }
            r.knownWon -> Text(
                stringResource(R.string.pg_gen_known_won, r.built),
                color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
            )
            else -> Text(
                pluralStringResource(R.plurals.pg_gen_probe_won, r.built, r.built),
                color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
        if (r.learnedFrom > 0) {
            Text(
                pluralStringResource(R.plurals.pg_gen_learned_from, r.learnedFrom, r.learnedFrom),
                color = Palette.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = note
            )
        }
    }
}

