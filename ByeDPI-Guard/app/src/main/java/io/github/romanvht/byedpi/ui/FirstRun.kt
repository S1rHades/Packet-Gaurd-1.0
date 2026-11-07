package io.github.romanvht.byedpi.ui

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.getPreferences

/** Whether the first-run guide has been seen (or skipped). */
object FirstRun {
    private const val PREF_DONE = "pg_first_run_done"

    /**
     * True once the guide was finished or skipped, and for anyone who used Packet Guard before
     * the guide existed (they're marked done the first time this is asked).
     */
    fun isDone(ctx: Context): Boolean {
        val prefs = ctx.getPreferences()
        if (prefs.getBoolean(PREF_DONE, false)) return true
        val usedBefore = listOf("updates_last_version", "battery_optimization_requested", "byedpi_cmd_args", "byedpi_mode")
            .any { prefs.contains(it) }
        if (usedBefore) markDone(ctx)
        return usedBefore
    }

    fun markDone(ctx: Context) = ctx.getPreferences().edit { putBoolean(PREF_DONE, true) }
}

private const val PAGES = 3

/**
 * Three short screens on first launch: what Packet Guard does, the VPN permission, and
 * "find what works on your network", which runs a quick test and switches on the best setting.
 * Skip is always there, and the guide never comes back.
 */
@Composable
fun FirstRunGuide(onDone: () -> Unit, onFind: () -> Unit, modifier: Modifier = Modifier, startPage: Int = 0) {
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(startPage) }
    var vpnNote by rememberSaveable { mutableStateOf<Boolean?>(null) } // true: allowed, false: declined

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        vpnNote = r.resultCode == Activity.RESULT_OK
        page = 2
    }

    BackHandler { if (page > 0) page-- else onDone() }

    GlassBackground(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dots(page, Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pg_fr_skip), fontSize = 16.sp)
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(Motion.MEDIUM)) { it / 4 * dir } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                        (slideOutHorizontally(tween(Motion.FAST)) { -it / 4 * dir } + fadeOut(tween(Motion.FAST)))
                },
                label = "firstRun",
                modifier = Modifier.weight(1f)
            ) { p ->
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (p) {
                        0 -> {
                            BrandMark(96)
                            Page(stringResource(R.string.pg_fr1_title), stringResource(R.string.pg_fr1_body))
                        }
                        1 -> {
                            Badge { Icon(Icons.Filled.VpnKey, null, tint = Palette.Sky, modifier = Modifier.size(44.dp)) }
                            Page(stringResource(R.string.pg_fr2_title), stringResource(R.string.pg_fr2_body))
                        }
                        else -> {
                            Badge { Icon(Icons.Filled.AutoAwesome, null, tint = Palette.Sky, modifier = Modifier.size(44.dp)) }
                            Page(stringResource(R.string.pg_fr3_title), stringResource(R.string.pg_fr3_body))
                            if (vpnNote == false) {
                                Text(
                                    stringResource(R.string.pg_fr2_denied), color = Palette.Warning, fontSize = 14.sp,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
            when (page) {
                0 -> PrimaryButton(
                    stringResource(R.string.pg_fr_next), onClick = { page = 1 },
                    modifier = Modifier.fillMaxWidth(), height = 64
                )
                1 -> PrimaryButton(
                    stringResource(R.string.pg_fr2_allow),
                    onClick = {
                        val consent = VpnService.prepare(ctx)
                        if (consent == null || runCatching { vpnLauncher.launch(consent) }.isFailure) page = 2
                    },
                    modifier = Modifier.fillMaxWidth(), height = 64
                )
                else -> {
                    PrimaryButton(
                        stringResource(R.string.pg_fr3_find), onClick = onFind, icon = Icons.Filled.AutoAwesome,
                        modifier = Modifier.fillMaxWidth(), height = 64
                    )
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.pg_fr3_later), fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Page(title: String, body: String) {
    Text(
        title, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 28.dp)
    )
    Text(
        body, fontSize = 17.sp, lineHeight = 25.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp)
    )
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
    Box(
        Modifier.size(104.dp).clip(RoundedCornerShape(32.dp)).background(Palette.Glow.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Dots(page: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pg_fr_step, page + 1, PAGES)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGES) { i ->
            Box(
                Modifier
                    .size(width = if (i == page) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == page) Palette.Glow else Color.White.copy(alpha = 0.25f))
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}
