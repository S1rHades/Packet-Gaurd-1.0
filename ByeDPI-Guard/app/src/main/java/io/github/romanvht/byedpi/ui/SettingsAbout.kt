package io.github.romanvht.byedpi.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Info
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

private const val ENGINE_VERSION = "ByeDPI 0.17.3"

@get:StringRes
private val Section.label: Int
    get() = when (this) {
        Section.SETTINGS -> R.string.pg_section_settings
        Section.HISTORY -> R.string.pg_saved_strategies
        Section.APPS -> R.string.pg_section_apps
        Section.DOMAIN_LISTS -> R.string.pg_section_lists
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
            if (SettingsUtils.exportSettings(ctx, uri, exportSections)) onMessage(ctx.getString(R.string.pg_settings_exported))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = SettingsUtils.readSettings(ctx, uri)
    }

    // First thing on this screen: where Packet Guard comes from.
    InfoBanner(
        stringResource(R.string.pg_about_origin),
        Icons.Filled.Info,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    SettingsGroup("Packet Guard") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Packet Guard", style = MaterialTheme.typography.titleLarge)
                HelperText(stringResource(R.string.pg_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION))
            }
        }
        HelperText(
            stringResource(R.string.pg_about_blurb),
            Modifier.padding(top = 12.dp)
        )
    }

    UpdatesGroup()

    SettingsGroup(stringResource(R.string.pg_backup)) {
        NavRow(
            stringResource(R.string.pg_export_settings), onClick = { askExport = true },
            value = stringResource(R.string.pg_export_settings_help),
            icon = Icons.Filled.Upload
        )
        NavRow(
            stringResource(R.string.pg_import_settings), onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            value = stringResource(R.string.pg_import_settings_help),
            icon = Icons.Filled.Download
        )
        NavRow(
            stringResource(R.string.pg_reset_all), onClick = { confirmReset = true },
            value = stringResource(R.string.pg_reset_all_help),
            icon = Icons.Filled.RestartAlt
        )
    }

    SettingsGroup(stringResource(R.string.pg_diagnostics)) {
        NavRow(
            stringResource(R.string.pg_copy_diagnostics), onClick = {
                ClipboardUtils.copy(ctx, DiagnosticUtils.buildReport(ctx), "diagnostics")
            },
            value = stringResource(R.string.pg_copy_diagnostics_help),
            icon = Icons.Filled.BugReport
        )
    }

    SettingsGroup(stringResource(R.string.pg_open_source)) {
        NavRow(
            "ByeDPI", onClick = { ctx.openUrl("https://github.com/hufrea/byedpi") },
            value = stringResource(R.string.pg_credit_byedpi),
            icon = Icons.Filled.Code,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
        NavRow(
            "ByeByeDPI", onClick = { ctx.openUrl("https://github.com/romanvht/ByeByeDPI") },
            value = stringResource(R.string.pg_credit_byebyedpi),
            icon = Icons.Filled.Code,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
        NavRow(
            "hev-socks5-tunnel", onClick = { ctx.openUrl("https://github.com/heiher/hev-socks5-tunnel") },
            value = stringResource(R.string.pg_credit_hev),
            icon = Icons.Filled.Hub,
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextSecondary) }
        )
    }

    if (askExport) {
        SectionPickerDialog(
            title = stringResource(R.string.pg_export),
            available = Section.entries.toSet(),
            confirmLabel = stringResource(R.string.pg_choose_file),
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
            title = stringResource(R.string.pg_import),
            available = SettingsUtils.getAvailableSections(settings),
            confirmLabel = stringResource(R.string.pg_import),
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
            title = { Text(stringResource(R.string.pg_reset_confirm_title)) },
            text = { Text(stringResource(R.string.pg_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    SettingsUtils.resetSettings(ctx) { ctx.findActivity()?.recreate() }
                }) { Text(stringResource(R.string.pg_reset), color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.pg_cancel)) } }
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
                            .toggleable(value = section in chosen, role = Role.Checkbox) { chosen = if (it) chosen + section else chosen - section }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = section in chosen,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Palette.AccentBlue, uncheckedColor = Palette.Gray)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(section.label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onConfirm(chosen) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pg_cancel)) } }
    )
}
