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

    val sni = vm.sni
    val ranked = remember(test.strategies, ui.profiles) {
        rankStrategies(test.strategies) { ui.nameFor(it, sni) }
    }
    val winner = remember(ranked) { winnerOf(ranked) }
    val perDomain = remember(test.strategies, ui.profiles) {
        bestPerDomain(test.strategies) { ui.nameFor(it, sni) }
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
                "Benchmark",
                "Find which profile unblocks your sites fastest on this network.",
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
                onApply = { w -> vm.apply(w.result.command, "Benchmark winner · ${w.name}") },
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
                        "Starting a benchmark pauses protection while profiles are tested. Reconnect when it finishes.",
                        Icons.Filled.Info
                    )
                }
                PrimaryButton(
                    text = when {
                        test.isStopping -> "Stopping…"
                        running -> "Stop"
                        else -> "Start Optimization"
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
                                "Testing ${test.currentStrategy}/${test.strategies.size}" +
                                    (cmd?.let { " · ${ui.nameFor(it, sni)}" } ?: "")
                            }
                            running -> "Preparing…"
                            test.failed || test.wasInterrupted -> "The last run was interrupted. Results are partial."
                            else -> "Finished · ${test.strategies.size} profiles tested"
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

        // 3 — best profile per domain
        item(key = "domains") {
            DomainTable(perDomain, Modifier.animateItem().staggeredEnter(3))
        }

        // 4 — link lists
        item(key = "lists") {
            AppCard(Modifier.animateItem().staggeredEnter(4)) {
                SectionTitle("Link lists") {
                    Text("${ui.activeSites} sites", color = Palette.TextSecondary, fontSize = 12.sp)
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
                                Text("${list.domains.size} sites", color = Palette.TextSecondary, fontSize = 12.sp)
                            }
                            IconButton(
                                enabled = !running,
                                onClick = {
                                    editor = ListEditorState(list.id, list.name, list.domains.joinToString("\n"))
                                }
                            ) { Icon(Icons.Filled.Edit, "Edit ${list.name}", tint = Palette.TextSecondary) }
                            IconButton(enabled = !running, onClick = { deleting = list }) {
                                Icon(Icons.Filled.Delete, "Delete ${list.name}", tint = Palette.TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        "New list",
                        onClick = { editor = ListEditorState(null, "", "") },
                        enabled = !running,
                        icon = Icons.Filled.Add
                    )
                    TextButton(onClick = vm::resetLists, enabled = !running) { Text("Restore defaults") }
                }
            }
        }

        // 5 — profiles to test
        item(key = "profiles") {
            AppCard(Modifier.animateItem().staggeredEnter(5)) {
                SectionTitle("Profiles to test") {
                    Text("${ui.selected.size} selected", color = Palette.TextSecondary, fontSize = 12.sp)
                }
                PillTabs(
                    items = ProfilePack.entries.toList(),
                    selected = ui.pack,
                    onSelect = vm::selectPack,
                    label = { pack -> "${pack.label} (${ui.profiles.count { it.pack == pack }})" }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::selectAllVisible, enabled = !running) { Text("Select all") }
                    TextButton(onClick = vm::clearVisible, enabled = !running) { Text("Clear") }
                }
                if (ui.pack == ProfilePack.Custom && ui.visibleProfiles.isEmpty()) {
                    HelperText("No custom profiles yet. Add your own under Settings → Benchmark → Custom profiles.")
                }
                if (ui.pack == ProfilePack.Community) {
                    HelperText("Upstream's community-tested strategies. Testing all of them takes a while.")
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

        // 6 — full ranking
        if (ranked.isNotEmpty()) {
            item(key = "rankingTitle") {
                Text("All results", style = MaterialTheme.typography.titleMedium, modifier = Modifier.animateItem().padding(top = 6.dp))
            }
            items(ranked, key = { "r:" + it.result.command }) { r ->
                RankedRow(
                    r = r,
                    applied = ui.applied == r.result.command,
                    canApply = !running,
                    onApply = { vm.apply(r.result.command, "Benchmark · ${r.name}") },
                    modifier = Modifier.animateItem()
                )
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
            title = { Text("Delete “${list.name}”?") },
            text = { Text("The list will no longer be tested. You can bring built-in lists back with Restore defaults.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteList(list.id); deleting = null }) {
                    Text("Delete", color = Palette.Error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
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
                        Text("Winning profile", style = MaterialTheme.typography.titleMedium)
                        HelperText(
                            if (running) "Testing… the best profile so far will appear here."
                            else "Run an optimization to find the fastest working profile for your network.",
                            Modifier.padding(top = 2.dp)
                        )
                    }
                }
                1 -> Column {
                    SectionTitle("No working profile yet")
                    HelperText(
                        "None of the tested profiles reached any site. Try another pack, or check that the " +
                            "selected sites are reachable from your network."
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
                                    if (running) "Leading so far" else "Winning profile",
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
                            StatChip("Success", "${w.result.successCount}/${w.result.totalRequests}", Modifier.weight(1f))
                            StatChip("Avg latency", w.avgLatencyMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = when {
                                applied -> "Applied"
                                running -> "Wait for the run to finish"
                                else -> "Apply Winning Profile"
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
        SectionTitle("Best profile per domain")
        if (rows.isEmpty()) {
            HelperText("After a run, each site is listed with the profile that reached it fastest.")
            return@AppCard
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("Domain", Modifier.weight(1.3f), color = Palette.TextSecondary, fontSize = 12.sp)
            Text("Profile", Modifier.weight(1.2f), color = Palette.TextSecondary, fontSize = 12.sp)
            Text("Latency", Modifier.weight(0.7f), color = Palette.TextSecondary, fontSize = 12.sp)
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
                        (row.latencyMs?.let { "$it ms" } ?: "—") + (row.httpCode?.let { " · $it" } ?: ""),
                        Modifier.weight(0.7f), fontSize = 12.sp
                    )
                } else {
                    Text("not reachable", Modifier.weight(1.9f), fontSize = 12.sp, color = Palette.Error)
                }
            }
        }
    }
}

@Composable
private fun RankedRow(
    r: RankedStrategy,
    applied: Boolean,
    canApply: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.MEDIUM), label = "expandArrow")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        border = BorderStroke(1.dp, Palette.Border)
    ) {
        Column(Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${r.rank}", color = Palette.TextSecondary, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${r.result.successCount}/${r.result.totalRequests} OK" +
                            (r.avgLatencyMs?.let { " · avg $it ms" } ?: ""),
                        color = if (r.result.successCount > 0) Color.White else Palette.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Palette.TextSecondary,
                    modifier = Modifier.rotate(arrow)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    r.result.command, color = Palette.TextSecondary, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                r.result.siteResults.sortedByDescending { it.successCount }.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            s.site, Modifier.weight(1f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (s.successCount > 0)
                                "${s.latencyMs ?: "?"} ms" + (s.httpCode?.let { " · $it" } ?: "")
                            else "failed",
                            fontSize = 12.sp,
                            color = if (s.successCount > 0) Palette.Success else Palette.Error
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    if (applied) "Applied" else "Use this profile",
                    onClick = onApply,
                    enabled = canApply && !applied
                )
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
        title = { Text(if (state.id == null) "New link list" else "Edit list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text("Domains or links") },
                    placeholder = { Text("example.com\nhttps://news.example.org/page") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    colors = fieldColors()
                )
                Text(
                    "One per line. Paths and query strings are ignored; each domain is tested.",
                    color = Palette.TextSecondary, fontSize = 12.sp
                )
                SecondaryButton(
                    "Import from file",
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = Palette.AccentBlue,
    uncheckedColor = Palette.Gray
)
