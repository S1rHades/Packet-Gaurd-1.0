package io.github.romanvht.byedpi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getSelectedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

private data class InstalledApp(val label: String, val packageName: String)

/** Full-screen picker for per-app routing; writes the same "selected_apps" set upstream used. */
@Composable
fun AppPickerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getPreferences() }
    var selected by remember { mutableStateOf(prefs.getSelectedApps().toSet()) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != ctx.packageName }
                .map { info ->
                    InstalledApp(
                        runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                        info.packageName
                    )
                }
                .toList()
        }
    }

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
        prefs.edit { putStringSet("selected_apps", selected) }
    }

    // Selected apps first, then alphabetical; order is fixed when the list loads so rows don't jump.
    val ordered = remember(apps) {
        val initial = selected
        apps?.sortedWith(compareBy<InstalledApp>({ it.packageName !in initial }, { it.label.lowercase() }))
    }
    val shown = remember(ordered, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ordered else ordered?.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Palette.Background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.pg_back)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pg_choose_apps), style = MaterialTheme.typography.titleLarge)
                        HelperText(stringResource(R.string.pg_n_selected, selected.size))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.pg_search_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Palette.TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                val list = shown
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Glow)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        items(list, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                checked = app.packageName in selected,
                                onToggle = { toggle(app.packageName) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val bg by animateColorAsState(
        if (checked) Palette.SurfaceHigh else Palette.Background, tween(Motion.MEDIUM), label = "appRowBg"
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)) {
            icon?.let { Image(it, null, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = Palette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Palette.AccentBlue, uncheckedColor = Palette.Gray)
        )
    }
}
