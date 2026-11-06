package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.update.AppUpdates
import io.github.romanvht.byedpi.update.UpdatePhase
import io.github.romanvht.byedpi.update.UpdateProblem
import io.github.romanvht.byedpi.update.UpdateState

@Composable
private fun problemText(p: UpdateProblem): String = stringResource(
    when (p) {
        UpdateProblem.Offline -> R.string.pg_upd_err_offline
        UpdateProblem.Timeout -> R.string.pg_upd_err_timeout
        UpdateProblem.Secure -> R.string.pg_upd_err_secure
        UpdateProblem.NoApk -> R.string.pg_upd_err_no_apk
        UpdateProblem.Server -> R.string.pg_upd_err_server
        UpdateProblem.Download -> R.string.pg_upd_err_download
        UpdateProblem.NeedsPermission -> R.string.pg_upd_allow
        UpdateProblem.Cancelled -> R.string.pg_upd_err_cancelled
        UpdateProblem.Conflict -> R.string.pg_upd_err_conflict
        UpdateProblem.Storage -> R.string.pg_upd_err_storage
        UpdateProblem.Install -> R.string.pg_upd_err_install
    }
)

/** A thin bar in the accent gradient that glides as the download fills. */
@Composable
private fun DownloadBar(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.MEDIUM), label = "updateProgress")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Glass.Inset)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(4.dp)).background(Glass.Accent))
    }
}

@Composable
private fun UpdateButton(state: UpdateState, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(
        text = when (state.phase) {
            UpdatePhase.Downloading -> stringResource(R.string.pg_upd_downloading, (state.progress * 100).toInt())
            UpdatePhase.Installing -> stringResource(R.string.pg_upd_installing_short)
            else -> stringResource(R.string.pg_upd_install)
        },
        icon = Icons.Filled.SystemUpdate,
        onClick = onUpdate,
        enabled = state.phase == UpdatePhase.Available,
        modifier = modifier.fillMaxWidth(),
        height = 52
    )
}

/**
 * The Connect screen's notice when a newer version is out: one button, its progress while it
 * downloads, and the reason if it fails. Can be hidden until the next app start.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by AppUpdates.state.collectAsStateWithLifecycle()
    var hidden by remember { mutableStateOf(false) }
    val release = state.release
    val show = !hidden && release != null &&
        state.phase in setOf(UpdatePhase.Available, UpdatePhase.Downloading, UpdatePhase.Installing)
    AnimatedVisibility(show, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        if (release == null) return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .glass(borderColor = Palette.Glow.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Glow.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.SystemUpdate, null, tint = Palette.Sky, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pg_upd_banner, release.version), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    release.notes.firstOrNull()?.let {
                        Text(it, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 2)
                    }
                }
                if (state.phase == UpdatePhase.Available) {
                    // A full 48 dp target: a small "Later" is easy to miss with a thumb.
                    androidx.compose.material3.TextButton(
                        onClick = { hidden = true },
                        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)
                    ) {
                        Text(stringResource(R.string.pg_upd_later), color = Palette.TextSecondary)
                    }
                }
            }
            if (state.phase == UpdatePhase.Downloading) DownloadBar(state.progress, Modifier.padding(top = 12.dp))
            state.problem?.let { HelperText(problemText(it), Modifier.padding(top = 10.dp), color = Palette.Warning) }
            UpdateButton(state, onUpdate = { AppUpdates.update(ctx) }, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * Shown once on the Connect screen after an update: the new version and what changed, so an
 * update ends on a clear, friendly note rather than a silent restart.
 */
@Composable
fun WhatsNewCard(modifier: Modifier = Modifier) {
    val just by AppUpdates.justUpdated.collectAsStateWithLifecycle()
    AnimatedVisibility(just != null, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val j = just ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .glass(borderColor = Palette.Success.copy(alpha = 0.55f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Success.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.CheckCircle, null, tint = Palette.Success, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pg_upd_updated, j.version), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(if (j.notes.isEmpty()) R.string.pg_whats_new_kept else R.string.pg_upd_whats_new),
                        color = Palette.TextSecondary, fontSize = 13.sp
                    )
                }
            }
            j.notes.take(4).forEach { note ->
                Row(Modifier.padding(top = 8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Sky))
                    Spacer(Modifier.width(8.dp))
                    Text(note, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            SecondaryButton(
                stringResource(R.string.pg_got_it),
                onClick = { AppUpdates.dismissWhatsNew() },
                icon = Icons.Filled.Check,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
    }
}

/** Settings → About → Updates: the current version, a check button, what's new, and the auto-check switch. */
@Composable
fun UpdatesGroup() {
    val ctx = LocalContext.current
    val state by AppUpdates.state.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(AppUpdates.autoCheck(ctx)) }

    SettingsGroup(stringResource(R.string.pg_upd_title)) {
        HelperText(stringResource(R.string.pg_upd_current, AppUpdates.currentVersion))
        val (icon, tint, line) = when (state.phase) {
            UpdatePhase.Checking -> Triple(Icons.Filled.Refresh, Palette.Sky, stringResource(R.string.pg_upd_checking))
            UpdatePhase.UpToDate -> Triple(Icons.Filled.CheckCircle, Palette.Success, stringResource(R.string.pg_upd_up_to_date))
            UpdatePhase.Available, UpdatePhase.Downloading, UpdatePhase.Installing ->
                Triple(Icons.Filled.SystemUpdate, Palette.Glow, stringResource(R.string.pg_upd_available, state.release?.version.orEmpty()))
            UpdatePhase.Failed -> Triple(Icons.Filled.Warning, Palette.Warning, state.problem?.let { problemText(it) } ?: "")
            UpdatePhase.Idle -> Triple(Icons.Filled.Refresh, Palette.TextSecondary, stringResource(R.string.pg_upd_never))
        }
        AnimatedContent(
            targetState = Triple(icon, tint, line),
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "updateStatus"
        ) { (i, t, text) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).glassInset().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(i, null, tint = t, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val release = state.release
        if (release != null && release.notes.isNotEmpty() && state.phase != UpdatePhase.UpToDate) {
            Text(
                stringResource(R.string.pg_upd_whats_new), color = Palette.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            release.notes.forEach { note ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Box(
                        Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Sky)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(note, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
        if (state.phase == UpdatePhase.Downloading) DownloadBar(state.progress, Modifier.padding(top = 12.dp))
        if (state.phase == UpdatePhase.Installing) HelperText(stringResource(R.string.pg_upd_installing), Modifier.padding(top = 10.dp))
        if (state.problem != null && state.phase != UpdatePhase.Failed) {
            HelperText(problemText(state.problem!!), Modifier.padding(top = 10.dp), color = Palette.Warning)
        }
        if (release != null && state.phase != UpdatePhase.UpToDate && state.phase != UpdatePhase.Checking) {
            UpdateButton(state, onUpdate = { AppUpdates.update(ctx) }, modifier = Modifier.padding(top = 14.dp))
        } else {
            SecondaryButton(
                stringResource(R.string.pg_upd_check),
                onClick = { AppUpdates.checkNow(ctx) },
                enabled = !state.busy,
                icon = Icons.Filled.Refresh,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
        SwitchRow(
            stringResource(R.string.pg_upd_auto), auto,
            {
                auto = it
                AppUpdates.setAutoCheck(ctx, it)
            },
            subtitle = stringResource(R.string.pg_upd_auto_help),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
