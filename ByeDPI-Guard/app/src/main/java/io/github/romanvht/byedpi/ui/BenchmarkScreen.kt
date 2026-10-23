package io.github.romanvht.byedpi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Link Benchmarker", style = MaterialTheme.typography.titleLarge)
            Text(
                "Find which profile unblocks your sites fastest on this network.",
                color = Palette.TextSecondary, modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (bypassActive && !running) item {
            AppCard(borderColor = Palette.Glow) {
                Text(
                    "Starting a benchmark pauses the bypass while profiles are tested. " +
                        "Reconnect when it finishes.",
                    fontSize = 13.sp
                )
            }
        }

        // ---- link lists ----
        item {
            AppCard {
                SectionTitle("Link lists")
                ui.lists.forEach { list ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { editor = ListEditorState(null, "", "") },
                        enabled = !running,
                        border = BorderStroke(1.dp, Palette.Border)
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New list")
                    }
                    TextButton(onClick = vm::resetLists, enabled = !running) { Text("Restore defaults") }
                }
                Text(
                    "${ui.activeSites} sites selected",
                    color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---- profiles ----
        item {
            AppCard {
                SectionTitle("Profiles to test")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfilePack.entries.forEach { pack ->
                        FilterChip(
                            selected = ui.pack == pack,
                            onClick = { vm.selectPack(pack) },
                            label = { Text("${pack.label} (${ui.profiles.count { it.pack == pack }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Palette.AccentBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::selectAllVisible, enabled = !running) { Text("Select all") }
                    TextButton(onClick = vm::clearVisible, enabled = !running) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    Text("${ui.selected.size} selected", color = Palette.TextSecondary, fontSize = 12.sp)
                }
                if (ui.pack == ProfilePack.Custom && ui.visibleProfiles.isEmpty()) {
                    Text(
                        "No custom profiles yet. Add your own ciadpi commands under Advanced → Custom profiles.",
                        color = Palette.TextSecondary, fontSize = 13.sp
                    )
                }
                if (ui.pack == ProfilePack.Community) {
                    Text(
                        "These are upstream's community-tested strategies. Testing all of them takes a while.",
                        color = Palette.TextSecondary, fontSize = 12.sp
                    )
                }
            }
        }
        items(ui.visibleProfiles, key = { it.pack.name + it.command }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(14.dp))
                    .clickable(enabled = !running) { vm.toggleProfile(p.command) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = p.command in ui.selected,
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

        // ---- run ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { if (running) vm.stop() else vm.start() },
                    enabled = !test.isStopping,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.AccentBlue)
                ) {
                    Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            test.isStopping -> "Stopping…"
                            running -> "Stop"
                            else -> "Start Optimization"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (running || test.strategies.isNotEmpty()) {
                    val total = test.strategies.size.coerceAtLeast(1)
                    val partial = test.strategies.getOrNull(test.currentStrategy - 1)
                        ?.takeIf { !it.isCompleted && it.totalRequests > 0 }
                        ?.let { it.currentProgress / it.totalRequests.toFloat() } ?: 0f
                    val fraction = ((test.strategies.count { it.isCompleted } + partial) / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Palette.Glow, trackColor = Palette.SurfaceHigh
                    )
                    val label = when {
                        running && test.currentStrategy > 0 -> {
                            val cmd = test.strategies.getOrNull(test.currentStrategy - 1)?.command
                            "Testing ${test.currentStrategy}/${test.strategies.size}" +
                                (cmd?.let { " · ${ui.nameFor(it, sni)}" } ?: "")
                        }
                        running -> "Preparing…"
                        test.failed || test.wasInterrupted -> "The last run was interrupted. Results below are partial."
                        else -> "Finished"
                    }
                    Text(label, color = Palette.TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // ---- results ----
        if (ranked.isNotEmpty()) {
            item {
                WinnerCard(
                    winner = winner,
                    applied = winner != null && ui.applied == winner.result.command,
                    running = running,
                    onApply = { w -> vm.apply(w.result.command, "Benchmark winner · ${w.name}") }
                )
            }
            item { DomainTable(perDomain) }
            item {
                Text("All profiles", style = MaterialTheme.typography.titleMedium)
            }
            items(ranked, key = { it.result.command }) { r ->
                RankedRow(
                    r = r,
                    applied = ui.applied == r.result.command,
                    canApply = !running,
                    onApply = { vm.apply(r.result.command, "Benchmark · ${r.name}") }
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
    applied: Boolean,
    running: Boolean,
    onApply: (RankedStrategy) -> Unit
) {
    AppCard(borderColor = if (winner != null) Palette.Glow else Palette.Border) {
        if (winner == null) {
            SectionTitle("No working profile yet")
            Text(
                "None of the tested profiles reached any site. Try another pack, or check that the " +
                    "selected sites are actually reachable from your network.",
                color = Palette.TextSecondary, fontSize = 13.sp
            )
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = Palette.Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Leading so far" else "Winning profile", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(winner.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            winner.result.command, color = Palette.TextSecondary, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${winner.result.successCount}/${winner.result.totalRequests} requests OK" +
                (winner.avgLatencyMs?.let { " · avg $it ms" } ?: ""),
            color = Palette.Success, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onApply(winner) },
            enabled = !running && !applied,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.AccentBlue)
        ) {
            Text(
                when {
                    applied -> "Applied"
                    running -> "Wait for the run to finish"
                    else -> "Apply Winning Profile"
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DomainTable(rows: List<DomainBest>) {
    AppCard {
        SectionTitle("Best profile per domain")
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
private fun RankedRow(r: RankedStrategy, applied: Boolean, canApply: Boolean, onApply: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        border = BorderStroke(1.dp, Palette.Border)
    ) {
        Column(Modifier.padding(14.dp)) {
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
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Palette.TextSecondary
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
                OutlinedButton(
                    onClick = onApply,
                    enabled = canApply && !applied,
                    border = BorderStroke(1.dp, Palette.Border)
                ) { Text(if (applied) "Applied" else "Use this profile") }
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
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("text/*")) },
                    border = BorderStroke(1.dp, Palette.Border)
                ) {
                    Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import from file")
                }
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
