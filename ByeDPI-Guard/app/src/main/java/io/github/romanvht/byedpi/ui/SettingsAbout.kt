package io.github.romanvht.byedpi.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.data.AppSettings
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.DiagnosticUtils
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.SettingsUtils.Section
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ENGINE_VERSION = "ByeDPI 0.17.3"

private val Section.label: String
    get() = when (this) {
        Section.SETTINGS -> "Settings"
        Section.HISTORY -> "Saved strategies"
        Section.APPS -> "Per-app selection"
        Section.DOMAIN_LISTS -> "Benchmark link lists"
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

@Composable
fun AboutSettings(onMessage: (String) -> Unit) {
    val ctx = LocalContext.current

    var exportSections by remember { mutableStateOf<Set<Section>>(Section.entries.toSet()) }
    var askExport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppSettings?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            SettingsUtils.exportSettings(ctx, uri, exportSections)
            onMessage("Settings exported")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = SettingsUtils.readSettings(ctx, uri)
    }

    SettingsGroup("Packet Guard") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Packet Guard", style = MaterialTheme.typography.titleLarge)
                HelperText("Version ${BuildConfig.VERSION_NAME} · $ENGINE_VERSION")
            }
        }
        HelperText(
            "Packet Guard runs a DPI-bypass engine on your phone and routes your traffic through it. " +
                "There is no remote server: your connections go straight to the sites you visit.",
            Modifier.padding(top = 12.dp)
        )
    }

    SettingsGroup("Backup and restore") {
        NavRow(
            "Export settings", onClick = { askExport = true },
            value = "Save settings, strategies, app selection and link lists to a file",
            icon = Icons.Filled.Upload
        )
        NavRow(
            "Import settings", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            value = "Restore from a Packet Guard backup file",
            icon = Icons.Filled.Download
        )
        NavRow(
            "Reset everything", onClick = { confirmReset = true },
            value = "Return every setting and list to its default",
            icon = Icons.Filled.RestartAlt
        )
    }

    SettingsGroup("Diagnostics") {
        NavRow(
            "Copy diagnostic report", onClick = {
                ClipboardUtils.copy(ctx, DiagnosticUtils.buildReport(ctx), "diagnostics")
            },
            value = "Device, network and engine details to paste into a bug report",
            icon = Icons.Filled.BugReport
        )
    }

    SettingsGroup("Open source") {
        NavRow(
            "ByeDPI", onClick = { ctx.openUrl("https://github.com/hufrea/byedpi") },
            value = "The bypass engine, by hufrea · GPL-3.0",
            icon = Icons.Filled.Code,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
        NavRow(
            "ByeByeDPI for Android", onClick = { ctx.openUrl("https://github.com/romanvht/ByeDPIAndroid") },
            value = "The Android app Packet Guard is built on, by romanvht · GPL-3.0",
            icon = Icons.Filled.Code,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
        NavRow(
            "hev-socks5-tunnel", onClick = { ctx.openUrl("https://github.com/heiher/hev-socks5-tunnel") },
            value = "The VPN-to-proxy tunnel, by heiher · MIT",
            icon = Icons.Filled.Hub,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
    }

    if (askExport) {
        SectionPickerDialog(
            title = "Export",
            available = Section.entries.toSet(),
            confirmLabel = "Choose file",
            onDismiss = { askExport = false },
            onConfirm = { sections ->
                askExport = false
                exportSections = sections
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                exportLauncher.launch("packet-guard-$stamp.json")
            }
        )
    }

    pendingImport?.let { settings ->
        SectionPickerDialog(
            title = "Import",
            available = SettingsUtils.getAvailableSections(settings),
            confirmLabel = "Import",
            onDismiss = { pendingImport = null },
            onConfirm = { sections ->
                pendingImport = null
                SettingsUtils.importSettings(ctx, settings, sections) { ctx.findActivity()?.recreate() }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Palette.Surface,
            title = { Text("Reset everything?") },
            text = { Text("All settings, saved strategies and link lists go back to their defaults. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    SettingsUtils.resetSettings(ctx) { ctx.findActivity()?.recreate() }
                }) { Text("Reset", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionPickerDialog(
    title: String,
    available: Set<Section>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<Section>) -> Unit
) {
    val options = Section.entries.filter { it in available }
    var chosen by remember { mutableStateOf(options.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { section ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { chosen = if (section in chosen) chosen - section else chosen + section },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = section in chosen,
                            onCheckedChange = { chosen = if (it) chosen + section else chosen - section },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.AccentBlue, uncheckedColor = Palette.Gray)
                        )
                        Text(section.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onConfirm(chosen) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
