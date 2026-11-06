package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.romanvht.byedpi.data.Command
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

@Composable
fun StrategySettings(onChanged: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getPreferences() }
    val history = remember { HistoryUtils(ctx) }

    var cmdEnabled by rememberBoolPref("byedpi_enable_cmd_settings", true)
    var active by remember { mutableStateOf(prefs.getCmdArgs()) }
    var args by remember { mutableStateOf(active) }
    var items by remember { mutableStateOf(history.getHistory()) }
    var renaming by remember { mutableStateOf<Command?>(null) }
    var editing by remember { mutableStateOf<Command?>(null) }
    var clearMenu by remember { mutableStateOf(false) }

    fun refresh() {
        items = history.getHistory()
        active = prefs.getCmdArgs()
        onChanged()
    }

    fun applyNow(command: String) {
        args = StrategyApplier.apply(ctx, command, null)
        refresh()
    }

    // An Undo from the snackbar changes the active strategy behind this screen's back.
    val changes by StrategyApplier.changes.collectAsStateWithLifecycle()
    LaunchedEffect(changes) {
        val before = active
        refresh()
        cmdEnabled = prefs.getCmdEnable()
        if (args.trim() == before || normalizeCommand(args) == before) args = active
    }
    val cleaned = normalizeCommand(args)

    // How each saved strategy did in the last test, so a name comes with a result.
    val test by TestService.state.collectAsStateWithLifecycle()
    val gen by CommandGenerator.state.collectAsStateWithLifecycle()
    val lastResults = remember(test.strategies, gen.result) {
        buildMap {
            test.strategies.filter { it.isCompleted }.forEach { r ->
                put(normalizeCommand(r.command), r.siteResults.count { it.successCount > 0 } to r.siteResults.size)
            }
            gen.result?.let { g -> g.command?.let { put(normalizeCommand(it), g.opened to g.blocked) } }
        }
    }

    // One tab, two ways to say the same thing: type a command line, or use switches.
    SettingsGroup(stringResource(R.string.pg_strategy_source)) {
        Segmented(
            items = listOf(true, false),
            selected = cmdEnabled,
            onSelect = {
                cmdEnabled = it
                onChanged()
            },
            label = { stringResource(if (it) R.string.pg_strategy_type_it else R.string.pg_strategy_switches) }
        )
        HelperText(
            stringResource(if (cmdEnabled) R.string.pg_strategy_cmd_on else R.string.pg_strategy_cmd_off),
            Modifier.padding(top = 10.dp)
        )
    }

    AnimatedVisibility(!cmdEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { EngineSettings(onChanged) }
    }

    AnimatedVisibility(cmdEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup(stringResource(R.string.pg_strategy_active)) {
            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                label = { Text(stringResource(R.string.pg_ciadpi_args)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = stringResource(if (cleaned == active) R.string.pg_active else R.string.pg_save_apply),
                    onClick = { applyNow(args) },
                    enabled = cleaned.isNotEmpty() && cleaned != active,
                    modifier = Modifier.weight(1f),
                    height = 48
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    ClipboardUtils.paste(ctx)?.takeIf { it.isNotBlank() }?.let { args = it.trim() }
                }) { Icon(Icons.Filled.ContentPaste, stringResource(R.string.pg_paste), tint = Palette.TextSecondary) }
                IconButton(onClick = { args = "" }) {
                    Icon(Icons.Filled.Clear, stringResource(R.string.pg_clear), tint = Palette.TextSecondary)
                }
            }
            // Show what a messy paste will be saved as, before it is.
            AnimatedVisibility(cleaned.isNotEmpty() && cleaned != args.trim()) {
                HelperText(
                    stringResource(R.string.pg_understood_as, cleaned),
                    Modifier.padding(top = 8.dp),
                    color = Palette.Sky
                )
            }
            CommandExplainer(cleaned, Modifier.padding(top = 12.dp))
            HelperText(
                stringResource(R.string.pg_strategy_help),
                Modifier.padding(top = 8.dp)
            )
        }
    }

    AnimatedVisibility(cmdEnabled && items.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup(
            stringResource(R.string.pg_saved_strategies),
            trailing = {
                Box {
                    IconButton(onClick = { clearMenu = true }) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.pg_clear_history), tint = Palette.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = clearMenu,
                        onDismissRequest = { clearMenu = false },
                        containerColor = Palette.SurfaceHigh
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.pg_delete_unpinned)) }, onClick = {
                            history.clearUnpinnedHistory()
                            clearMenu = false
                            refresh()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pg_delete_all)) }, onClick = {
                            history.clearAllHistory()
                            clearMenu = false
                            refresh()
                        })
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.sortedByDescending { it.pinned }.forEach { cmd ->
                    key(cmd.text) {
                        HistoryRow(
                            cmd = cmd,
                            isActive = cmd.text == active,
                            lastResult = lastResults[normalizeCommand(cmd.text)],
                            onApply = { applyNow(cmd.text) },
                            onTogglePin = {
                                if (cmd.pinned) history.unpinCommand(cmd.text) else history.pinCommand(cmd.text)
                                refresh()
                            },
                            onRename = { renaming = cmd },
                            onEdit = { editing = cmd },
                            onCopy = { ClipboardUtils.copy(ctx, cmd.text, "command") },
                            onDelete = {
                                history.deleteCommand(cmd.text)
                                refresh()
                            }
                        )
                    }
                }
            }
        }
    }

    renaming?.let { cmd ->
        TextInputDialog(
            title = stringResource(R.string.pg_rename_strategy),
            label = stringResource(R.string.pg_name),
            initial = cmd.name.orEmpty(),
            onDismiss = { renaming = null },
            onConfirm = { name ->
                history.renameCommand(cmd.text, name.trim())
                renaming = null
                refresh()
            }
        )
    }
    editing?.let { cmd ->
        TextInputDialog(
            title = stringResource(R.string.pg_edit_strategy),
            label = stringResource(R.string.pg_ciadpi_args),
            initial = cmd.text,
            monospace = true,
            onDismiss = { editing = null },
            onConfirm = { text ->
                val newText = text.trim()
                editing = null
                if (newText.isNotBlank() && newText != cmd.text) {
                    val wasActive = cmd.text == active
                    history.editCommand(cmd.text, newText)
                    if (wasActive) applyNow(newText) else refresh()
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    cmd: Command,
    isActive: Boolean,
    lastResult: Pair<Int, Int>?,
    onApply: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (isActive) Palette.Glow.copy(alpha = 0.7f) else Glass.InsetBorder, tween(Motion.MEDIUM), label = "historyBorder"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Glass.ControlShape)
            .background(Glass.Inset)
            .border(1.dp, border, Glass.ControlShape)
            .clickable(onClick = onApply)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cmd.pinned) {
                    Icon(Icons.Filled.PushPin, stringResource(R.string.pg_pinned), tint = Palette.Sky, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    cmd.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.pg_unnamed_strategy),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pg_active_badge),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Palette.AccentBlue)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                cmd.text, color = Palette.TextSecondary, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            lastResult?.let { (opened, sites) ->
                Text(
                    stringResource(R.string.pg_last_test, opened, sites),
                    color = scoreColor(percentOf(opened, sites)), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.pg_more), tint = Palette.TextSecondary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.SurfaceHigh) {
                val deleteLabel = stringResource(R.string.pg_delete)
                listOf(
                    stringResource(R.string.pg_apply) to onApply,
                    stringResource(if (cmd.pinned) R.string.pg_unpin else R.string.pg_pin) to onTogglePin,
                    stringResource(R.string.pg_rename) to onRename,
                    stringResource(R.string.pg_edit) to onEdit,
                    stringResource(R.string.pg_copy) to onCopy,
                    deleteLabel to onDelete
                ).forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (label == deleteLabel) Palette.Error else Color.White) },
                        onClick = {
                            menu = false
                            action()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    monospace: Boolean = false
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                textStyle = if (monospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.pg_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pg_cancel)) } }
    )
}
