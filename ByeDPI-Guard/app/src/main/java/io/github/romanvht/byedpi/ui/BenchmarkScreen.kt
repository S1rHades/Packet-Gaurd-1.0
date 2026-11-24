package io.github.romanvht.byedpi.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.ExpandLess
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

    val stopping = test.isStopping || gen.stopping
    var linesOpen by rememberSaveable { mutableStateOf(false) }

    // Once there's an answer, the setup folds into summaries; Edit opens it again.
    var editTest by rememberSaveable { mutableStateOf(false) }
    var genOpen by rememberSaveable { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (running) { editTest = false; genOpen = false } }
    val hasAnswer = if (generate) gen.result != null || gen.failed else anyDone
    val folded = hasAnswer && !editTest

    // While a test runs and its card is scrolled away, Stop stays pinned in thumb reach.
    val listState = rememberLazyListState()
    val pinAction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val card = info.visibleItemsInfo.firstOrNull { it.key == "running" }
            val shown = card != null && card.offset < info.viewportEndOffset - 200 && card.offset + card.size > 200
            running && info.totalItemsCount > 0 && !shown
        }
    }
    val StopAction: @Composable (Modifier) -> Unit = { m ->
        PrimaryButton(
            text = stringResource(if (stopping) R.string.pg_stopping else R.string.pg_stop),
            icon = Icons.Filled.Stop,
            onClick = vm::stop,
            enabled = !stopping,
            container = Palette.SurfaceHigher,
            modifier = m,
            height = 64
        )
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

        if (running) {
            // While running: one focused card, nothing else to scroll past.
            item(key = "running") {
                RunningCard(test, gen, ui, plan, sni, customName, onStop = vm::stop, modifier = Modifier.animateItem().staggeredEnter(1))
            }
        } else {
            // 1. The answer first.
            if (generate) {
                if (gen.result != null || gen.failed) {
                    item(key = "results") {
                        AppCard(
                            Modifier.animateItem().staggeredEnter(1),
                            borderColor = if (gen.result?.command != null) Palette.Glow else Palette.Border,
                            contentPadding = PaddingValues(20.dp)
                        ) {
                            GenResultPanel(
                                gen = gen,
                                applied = ui.applied,
                                onApply = { command -> vm.apply(command, ctx.getString(R.string.pg_gen_made)) },
                                onSearchLonger = {
                                    vm.selectPlan(SearchPlan.Thorough)
                                    vm.startGenerator()
                                },
                                onMakeAnother = vm::startGenerator
                            )
                        }
                    }
                }
            } else if (decision.recommended != null) {
                item(key = "results") {
                    RecommendedCard(
                        decision, running = false, applied = ui.applied,
                        onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_winner_label, c.name)) },
                        modifier = Modifier.animateItem().staggeredEnter(1)
                    )
                }
                // The generator's line is compared here only when it isn't the recommendation already.
                val made = gen.result?.command
                val madeRan = made != null && made != decision.recommended?.command &&
                    test.strategies.any { it.isCompleted && it.command == made }
                item(key = "other") {
                    OtherOptionsCard(
                        decision, applied = ui.applied,
                        onApplyGoal = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_result_label, c.name)) },
                        onApplyCombined = { vm.apply(it.command, ctx.getString(R.string.pg_comb_label)) },
                        modifier = Modifier.animateItem(),
                        extra = if (madeRan && made != null) {
                            {
                                MadeVsBestSection(
                                    decision, madeCommand = made, applied = ui.applied, canApply = true,
                                    onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_result_label, c.name)) }
                                )
                            }
                        } else null
                    )
                }
            } else if (anyDone) {
                item(key = "results") {
                    AppCard(Modifier.animateItem().staggeredEnter(1), contentPadding = PaddingValues(20.dp)) {
                        ResultPanel(
                            winner = null,
                            finished = true,
                            running = false,
                            applied = false,
                            onApply = {},
                            onTryGenerator = {
                                vm.selectPlan(SearchPlan.Thorough)
                                vm.startGenerator()
                            }
                        )
                    }
                }
            }

            // 2. The two ways to test: the ready-made test and "Make one just for me", each in
            //    its own card. With an answer on screen both fold to a line.
            item(key = "test") {
                if (folded) {
                    TestSummaryCard(ui, onEdit = { editTest = true }, onTest = vm::startTest, modifier = Modifier.animateItem())
                } else {
                    TestCard(
                        ui = ui, anyDone = anyDone, generate = generate, bypassActive = bypassActive,
                        linesOpen = linesOpen, onToggleLines = { linesOpen = !linesOpen },
                        onToggleList = vm::toggleList, onSelectPack = vm::selectPack, onSelectAll = vm::selectAllVisible,
                        onClear = vm::clearVisible, onToggleProfile = vm::toggleProfile, onEditCustom = { customOpen = true },
                        onTest = vm::startTest,
                        modifier = Modifier.animateItem().staggeredEnter(2)
                    )
                }
            }
            item(key = "gen") {
                GeneratorCard(
                    gen = gen, plan = plan, folded = hasAnswer && !genOpen, enabled = true,
                    onToggleFold = { genOpen = !genOpen }, onPlan = vm::selectPlan, onGenerate = vm::startGenerator,
                    modifier = Modifier.animateItem().staggeredEnter(3)
                )
            }

            // 3. Every result, folded, for anyone who wants to compare them all.
            if (!generate && decision.recommended != null) {
                item(key = "all") {
                    AllResultsCard(
                        decision, applied = ui.applied, canApply = true,
                        onApply = { c -> vm.apply(c.command, ctx.getString(R.string.pg_bench_result_label, c.name)) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // Advanced options, closed by default: editing site lists and the best line per site.
        item(key = "advancedToggle") {
            AdvancedToggle(advanced, onToggle = { advanced = !advanced }, modifier = Modifier.animateItem())
        }
        if (advanced) {
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
            // Test timing and your own lines, moved here from Settings.
            item(key = "advParams") {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(16.dp)) { BenchmarkSettings() }
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
        ) { StopAction(Modifier.fillMaxWidth()) }
    }
    }

    if (customOpen) {
        CustomLinesDialog(onDismiss = { customOpen = false; vm.reload() })
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

/** The ready-made test, in one card: which sites it opens, which settings it tries, and Start. */
@Composable
internal fun TestCard(
    ui: BenchUi,
    anyDone: Boolean,
    generate: Boolean,
    bypassActive: Boolean,
    linesOpen: Boolean,
    onToggleLines: () -> Unit,
    onToggleList: (String) -> Unit,
    onSelectPack: (ProfilePack) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onToggleProfile: (String) -> Unit,
    onEditCustom: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier, contentPadding = PaddingValues(20.dp)) {
        Text(stringResource(R.string.pg_test_sites), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        if (ui.lists.isEmpty()) {
            BigHint(stringResource(R.string.pg_step1_empty))
        } else {
            ListTiles(ui.lists, enabled = true, onToggle = onToggleList)
            BigHint(stringResource(R.string.pg_step1_hint), Modifier.padding(top = 10.dp))
        }

        Spacer(Modifier.height(16.dp))
        TestLines(ui, linesOpen, enabled = true, onToggleOpen = onToggleLines, onSelectPack, onSelectAll, onClear, onToggleProfile, onEditCustom)

        Spacer(Modifier.height(16.dp))
        if (bypassActive) {
            InfoBanner(stringResource(R.string.pg_step2_pause), Icons.Filled.Info, modifier = Modifier.padding(bottom = 12.dp))
        }
        PrimaryButton(
            text = stringResource(if (anyDone && !generate) R.string.pg_test_again else R.string.pg_step2_start),
            icon = Icons.Filled.PlayArrow,
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
            height = 60
        )
        HelperText(stringResource(R.string.pg_test_caption), Modifier.padding(top = 10.dp))
    }
}

/**
 * Which command lines the Test button tries: a one-line summary ("9 selected · Basic 9"), and
 * when opened the packs (Basic, Community, Custom) with a checkbox per line.
 */
@Composable
private fun TestLines(
    ui: BenchUi,
    open: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onSelectPack: (ProfilePack) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onToggleProfile: (String) -> Unit,
    onEditCustom: () -> Unit
) {
    val expandedText = stringResource(if (open) R.string.pg_a11y_expanded else R.string.pg_a11y_collapsed)
    val byPack = ProfilePack.entries.mapNotNull { pack ->
        val n = ui.profiles.count { it.pack == pack && it.command in ui.selected }
        if (n == 0) null else "${stringResource(pack.label)} $n"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onToggleOpen)
            .semantics { stateDescription = expandedText }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pg_test_lines), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                (listOf(stringResource(R.string.pg_n_selected, ui.selected.size)) + byPack).joinToString("  ·  "),
                color = if (ui.selected.isEmpty()) Palette.Warning else Palette.TextSecondary, fontSize = 13.sp
            )
        }
        // With large text the word would squeeze the title; the arrow (and TalkBack's state) still say it.
        if (LocalDensity.current.fontScale < 1.3f) {
            Text(stringResource(if (open) R.string.pg_test_lines_hide else R.string.pg_test_lines_choose), color = Palette.Sky, fontSize = 14.sp)
        }
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Palette.Sky)
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.padding(top = 8.dp)) {
            PillTabs(
                items = ProfilePack.entries.toList(),
                selected = ui.pack,
                onSelect = onSelectPack,
                label = { pack -> "${stringResource(pack.label)} (${ui.profiles.count { it.pack == pack }})" }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pg_select_all)) }
                TextButton(onClick = onClear, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pg_clear)) }
            }
            if (ui.pack == ProfilePack.Custom) {
                TextButton(onClick = onEditCustom, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pg_bench_custom_edit))
                }
            }
            if (ui.pack == ProfilePack.Custom && ui.visibleProfiles.isEmpty()) {
                HelperText(stringResource(R.string.pg_bench_no_custom))
            }
            if (ui.pack == ProfilePack.Community) {
                HelperText(stringResource(R.string.pg_bench_community_help))
            }
            ui.visibleProfiles.forEach { p ->
                key(p.pack.name + p.command) {
                    ProfileRow(p, selected = p.command in ui.selected, enabled = enabled, onToggle = { onToggleProfile(p.command) }, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ---- building blocks --------------------------------------------------------------------------

/** Larger helper text for the simple steps. */
@Composable
internal fun BigHint(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary) {
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
            .toggleable(value = list.active, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                // Decorative initial: the tile is read as its name, site count and checked state.
                Modifier.size(36.dp).clip(CircleShape).background(Palette.Sky.copy(alpha = 0.18f)).clearAndSetSemantics {},
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
internal fun BigProgress(test: TestState, ui: BenchUi, sni: String, customName: String) {
    val running = test.isRunning
    val total = test.strategies.size.coerceAtLeast(1)
    val partial = test.strategies.getOrNull(test.currentStrategy - 1)
        ?.takeIf { !it.isCompleted && it.totalRequests > 0 }
        ?.let { it.currentProgress / it.totalRequests.toFloat() } ?: 0f
    val fraction = ((test.strategies.count { it.isCompleted } + partial) / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(Motion.SLOW), label = "benchProgress")

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.clearAndSetSemantics {}) { RollingText(stringResource(R.string.pg_percent, (animated * 100).toInt()), fontSize = 34.sp) }
            Spacer(Modifier.weight(1f))
            if (running && !test.isStopping) TimeLeft(ui.startedAt, fraction, Modifier.padding(bottom = 6.dp))
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
            label = "benchStatus",
            // Read out when it changes ("Trying profile 4 of 8"), without the ticking percentage.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) { BigHint(it, color = Color.White) }
    }
}

/** "About 3 minutes left", worked out from how fast the run has gone so far. */
@Composable
internal fun TimeLeft(startedAt: Long, fraction: Float, modifier: Modifier = Modifier) {
    if (startedAt <= 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val minutes = minutesLeft(now - startedAt, fraction) ?: return
    // Grey, not link blue: it's information, not something to tap.
    BigHint(
        pluralStringResource(R.plurals.pg_time_left, minutes, minutes),
        modifier.padding(top = 6.dp),
        color = Palette.TextSecondary
    )
}

/** A thick, rounded progress bar in the accent gradient. */
@Composable
internal fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    // For TalkBack, in steps of 5% so it doesn't change on every frame of the animation.
    val stepped = (fraction * 20).toInt() / 20f
    Box(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(stepped, 0f..1f) }
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
    val expandedText = stringResource(if (open) R.string.pg_a11y_expanded else R.string.pg_a11y_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .glass()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = expandedText }
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
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, colors = checkboxColors())
        Spacer(Modifier.width(14.dp))
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

/** The search's progress: a big percentage, a thick bar and what it's doing in plain words. */
/** Step 3 for the generator: the command line it made, in plain words, with one big button. */
@Composable
internal fun GenResultPanel(
    gen: GenState,
    applied: String?,
    onApply: (String) -> Unit,
    onSearchLonger: () -> Unit,
    onMakeAnother: () -> Unit = {}
) {
    val ctx = LocalContext.current
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
                // When a line tested before stays the best, don't call it "made for you".
                val keptName = if (r.knownWon) r.compared.firstOrNull { it.command == command }?.name else null
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Glow.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Palette.Sky, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (r.knownWon) R.string.pg_gen_kept_eyebrow else R.string.pg_gen_made),
                                color = Palette.TextSecondary, fontSize = 14.sp
                            )
                            Text(
                                keptName ?: stringResource(if (r.knownWon) R.string.pg_gen_kept_title else R.string.pg_gen_title_result),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // The verdict: the score and what it opens, in words.
                    val allSites = r.sites
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(r.score.overall, size = 60.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (allSites != null) stringResource(R.string.pg_chip_opens, r.opened, allSites)
                                else stringResource(R.string.pg_gen_opens, r.opened, r.blocked),
                                fontSize = 17.sp, fontWeight = FontWeight.Bold
                            )
                            if (allSites != null && r.openedBlocked != null && r.blocked > 0) {
                                Text(
                                    stringResource(R.string.pg_gen_verdict_blocked, r.openedBlocked, r.blocked),
                                    color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                                )
                            }
                            r.latencyMs?.let { ms ->
                                val speed = Speed.of(ms)
                                Text(
                                    stringResource(R.string.pg_step3_speed, stringResource(speed.label)) + " · " + stringResource(R.string.pg_ms, ms),
                                    color = if (speed == Speed.Slow) Palette.Warning else Palette.TextSecondary, fontSize = 13.sp
                                )
                            }
                        }
                    }
                    // Against the best of the rest, tested in the same run.
                    val mine = r.compared.firstOrNull { it.command == command }
                    val other = r.compared.firstOrNull { it.command != command }
                    if (mine != null && other != null) {
                        MiniCompare(
                            mine, keptName ?: stringResource(R.string.pg_gen_made), other,
                            Modifier.padding(top = 14.dp)
                        )
                    }
                    val inUse = applied == command
                    PrimaryButton(
                        text = stringResource(if (inUse) R.string.pg_step3_in_use else R.string.pg_gen_use),
                        icon = if (inUse) Icons.Filled.Check else null,
                        onClick = { onApply(command) },
                        enabled = !inUse,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        height = 56
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onMakeAnother, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pg_gen_make_another)) }
                        TextButton(
                            onClick = { io.github.romanvht.byedpi.utility.ClipboardUtils.copy(ctx, command, "command") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.pg_copy_command)) }
                    }
                    Fold(stringResource(R.string.pg_details)) {
                        Text(
                            command,
                            fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Palette.Sky,
                            modifier = Modifier.fillMaxWidth().glassInset().padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                        HowItWasMade(r, Modifier.padding(top = 10.dp))
                        ScoreParts(r.score, Modifier.padding(top = 12.dp))
                        CommandExplainer(command, Modifier.padding(top = 12.dp), max = 8)
                    }
                    if (r.compared.size > 1) {
                        Fold(stringResource(R.string.pg_compare_all, r.compared.size)) {
                            CompareLines(r.compared, winner = command, applied = applied, onApply = onApply)
                        }
                    } else if (r.alternatives.isNotEmpty()) {
                        Fold(stringResource(R.string.pg_gen_others)) {
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
                                            stringResource(R.string.pg_row_opens, alt.opened, alt.sites) +
                                                (alt.latencyMs?.let { " · " + stringResource(R.string.pg_ms, it) } ?: ""),
                                            color = Palette.TextSecondary, fontSize = 12.sp
                                        )
                                    }
                                    TextButton(onClick = { onApply(alt.command) }, enabled = applied != alt.command, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(if (applied == alt.command) R.string.pg_step3_in_use else R.string.pg_use))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The answer next to the best of the rest, from the same run, with why it's ahead in words:
 * by how many points, or on a tie, that it opens more sites or is simpler.
 */
@Composable
private fun MiniCompare(mine: GenAlternative, mineName: String, other: GenAlternative, modifier: Modifier = Modifier) {
    @Composable
    fun row(name: String, a: GenAlternative, bold: Boolean) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(a.score.overall, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                name, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.pg_row_opens, a.opened, a.sites), color = Palette.TextSecondary, fontSize = 12.sp)
        }
    }
    val otherName = other.name ?: when (other.source) {
        "community" -> stringResource(R.string.pg_src_community)
        "basic" -> stringResource(R.string.pg_src_basic)
        "custom" -> stringResource(R.string.pg_src_custom)
        else -> stringResource(R.string.pg_gen_vs_best)
    }
    val diff = mine.score.overall - other.score.overall
    val why = when {
        diff > 0 -> stringResource(R.string.pg_cmp_ahead, diff)
        diff == 0 && mine.opened > other.opened -> stringResource(R.string.pg_cmp_tie_sites)
        diff == 0 && mine.command.length < other.command.length -> stringResource(R.string.pg_cmp_tie_simple)
        diff == 0 -> stringResource(R.string.pg_cmp_tie)
        else -> null
    }
    Column(modifier.fillMaxWidth().glassInset().padding(horizontal = 12.dp, vertical = 8.dp)) {
        row(mineName, mine, bold = true)
        row(otherName, other, bold = false)
        if (why != null) Text(why, color = Palette.Success, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Every line of the search's final run, ranked: the new lines next to the best community,
 * ready-made and own lines, all tested together on the same sites, so the scores compare directly.
 */
@Composable
internal fun CompareLines(
    lines: List<GenAlternative>,
    winner: String,
    applied: String?,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pg_gen_compare_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        HelperText(stringResource(R.string.pg_gen_compare_help), Modifier.padding(top = 2.dp, bottom = 8.dp))
        lines.forEachIndexed { i, line ->
            val isWinner = line.command == winner
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .glassInset()
                    .then(if (isWinner) Modifier.border(1.dp, Palette.Glow, Glass.ControlShape) else Modifier)
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextSecondary,
                    modifier = Modifier.width(18.dp)
                )
                ScoreRing(line.score.overall, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (label, tint) = when (line.source) {
                            "new" -> stringResource(R.string.pg_src_new) to Palette.Glow
                            "community" -> stringResource(R.string.pg_src_community) to Palette.Sky
                            "basic" -> stringResource(R.string.pg_src_basic) to Palette.Sky
                            "custom" -> stringResource(R.string.pg_src_custom) to Palette.Sky
                            "probe" -> stringResource(R.string.pg_src_probe) to Palette.TextSecondary
                            else -> stringResource(R.string.pg_src_before) to Palette.TextSecondary
                        }
                        MetricPill(label, tint)
                    }
                    // The name gets its own line, so "Community #12" isn't cut to "Commun…".
                    line.name?.let {
                        Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        line.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.TextSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        stringResource(R.string.pg_row_opens, line.opened, line.sites) +
                            (line.latencyMs?.let { " · " + stringResource(R.string.pg_ms, it) } ?: "") +
                            (line.successRate?.let { " · " + stringResource(R.string.pg_percent, it) } ?: ""),
                        fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = { onApply(line.command) }, enabled = applied != line.command, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (applied == line.command) R.string.pg_step3_in_use else R.string.pg_use))
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

