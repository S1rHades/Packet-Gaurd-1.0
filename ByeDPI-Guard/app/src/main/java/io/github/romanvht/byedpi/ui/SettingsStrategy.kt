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
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences

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
        args = command
        onMessage(StrategyApplier.apply(ctx, command, null))
        refresh()
    }

    SettingsGroup("Strategy source") {
        SwitchRow(
            "Use a command-line strategy",
            cmdEnabled,
            {
                cmdEnabled = it
                onChanged()
            },
            subtitle = if (cmdEnabled) "The ciadpi arguments below are used. Benchmark winners land here."
            else "The options in the Engine tab are used instead."
        )
    }

    AnimatedVisibility(cmdEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("Active strategy") {
            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                label = { Text("ciadpi arguments") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = if (args.trim() == active) "Active" else "Save and apply",
                    onClick = { applyNow(args.trim()) },
                    enabled = args.isNotBlank() && args.trim() != active,
                    modifier = Modifier.weight(1f),
                    height = 48
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    ClipboardUtils.paste(ctx)?.takeIf { it.isNotBlank() }?.let { args = it.trim() }
                }) { Icon(Icons.Filled.ContentPaste, "Paste", tint = Palette.TextSecondary) }
                IconButton(onClick = { args = "" }) {
                    Icon(Icons.Filled.Clear, "Clear", tint = Palette.TextSecondary)
                }
            }
            HelperText(
                "Separate options with spaces, for example  -o1 -a1 -r-5+se",
                Modifier.padding(top = 8.dp)
            )
        }
    }

    AnimatedVisibility(cmdEnabled && items.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup(
            "Saved strategies",
            trailing = {
                Box {
                    IconButton(onClick = { clearMenu = true }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear history", tint = Palette.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = clearMenu,
                        onDismissRequest = { clearMenu = false },
                        containerColor = Palette.SurfaceHigh
                    ) {
                        DropdownMenuItem(text = { Text("Delete unpinned") }, onClick = {
                            history.clearUnpinnedHistory()
                            clearMenu = false
                            refresh()
                        })
                        DropdownMenuItem(text = { Text("Delete all") }, onClick = {
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
            title = "Rename strategy",
            label = "Name",
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
            title = "Edit strategy",
            label = "ciadpi arguments",
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
    onApply: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (isActive) Palette.Glow.copy(alpha = 0.7f) else Palette.Border, tween(Motion.MEDIUM), label = "historyBorder"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onApply)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cmd.pinned) {
                    Icon(Icons.Filled.PushPin, "Pinned", tint = Palette.Sky, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    cmd.name?.takeIf { it.isNotBlank() } ?: "Unnamed strategy",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ACTIVE",
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
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Palette.TextSecondary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.SurfaceHigh) {
                listOf(
                    "Apply" to onApply,
                    (if (cmd.pinned) "Unpin" else "Pin") to onTogglePin,
                    "Rename" to onRename,
                    "Edit" to onEdit,
                    "Copy" to onCopy,
                    "Delete" to onDelete
                ).forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (label == "Delete") Palette.Error else Color.White) },
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
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
