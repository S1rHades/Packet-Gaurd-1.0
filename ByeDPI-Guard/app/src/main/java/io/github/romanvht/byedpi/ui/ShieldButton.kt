package io.github.romanvht.byedpi.ui

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

/**
 * The Packet Guard shield outline (same geometry as the launcher icon), normalised to the
 * bounds it is drawn in: rounded shoulders on top, a soft point at the bottom.
 */
fun shieldPath(size: Size): Path {
    // Source geometry lives in a 24-unit box spanning x 3.4..20.6 and y 1.5..22.6.
    val sx = size.width / 17.2f
    val sy = size.height / 21.1f
    fun x(v: Float) = (v - 3.4f) * sx
    fun y(v: Float) = (v - 1.5f) * sy
    return Path().apply {
        moveTo(x(12f), y(1.5f))
        lineTo(x(19.6f), y(4.5f))
        quadraticTo(x(20.6f), y(4.9f), x(20.6f), y(5.9f))
        lineTo(x(20.6f), y(11f))
        cubicTo(x(20.6f), y(16.3f), x(17.1f), y(20.7f), x(12f), y(22.6f))
        cubicTo(x(6.9f), y(20.7f), x(3.4f), y(16.3f), x(3.4f), y(11f))
        lineTo(x(3.4f), y(5.9f))
        quadraticTo(x(3.4f), y(4.9f), x(4.4f), y(4.5f))
        close()
    }
}

object ShieldShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(shieldPath(size))
}

/** Width : height of the shield. */
private const val SHIELD_RATIO = 17.2f / 21.1f

/**
 * The main connect control, shaped as the app's shield.
 *  - Off: dark shield, gray rim, power glyph
 *  - Connecting: rim lights up, shield-shaped waves radiate outward, spinner inside
 *  - On: blue gradient shield with a breathing glow and a check mark
 *  - Failed: red rim, retry glyph
 */
@Composable
fun ShieldButton(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 196.dp
) {
    val connecting = state is ConnectionState.Connecting
    val connected = state is ConnectionState.Connected
    val failed = state is ConnectionState.Failed
    val haptics = LocalHapticFeedback.current

    val infinite = rememberInfiniteTransition(label = "shield")
    val wave by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "wave"
    )
    val breathe by infinite.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )

    val top by animateColorAsState(
        when {
            connected -> Palette.Sky
            connecting -> Color(0xFF2B4C8F)
            else -> Palette.SurfaceHigher
        },
        tween(Motion.SLOW), label = "shieldTop"
    )
    val bottom by animateColorAsState(
        when {
            connected -> Palette.AccentBlue
            connecting -> Palette.SurfaceHigh
            else -> Palette.Surface
        },
        tween(Motion.SLOW), label = "shieldBottom"
    )
    val rim by animateColorAsState(
        when {
            failed -> Palette.Error
            connected -> Palette.Sky
            connecting -> Palette.Glow
            else -> Palette.Gray
        },
        tween(Motion.SLOW), label = "shieldRim"
    )
    val glyph by animateColorAsState(
        if (connected || connecting) Color.White else Palette.TextSecondary, tween(Motion.SLOW), label = "glyph"
    )
    val glowAlpha by animateFloatAsState(if (connected) 1f else 0f, tween(Motion.SLOW), label = "glowAlpha")

    val interaction = remember { MutableInteractionSource() }
    val shieldW = height * SHIELD_RATIO
    val outer = height * 1.42f

    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val shieldSize = Size(shieldW.toPx(), height.toPx())
            val path = shieldPath(shieldSize)
            val origin = Offset(c.x - shieldSize.width / 2f, c.y - shieldSize.height / 2f)

            if (glowAlpha > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Palette.Glow.copy(alpha = 0.42f * glowAlpha * breathe), Color.Transparent),
                        center = c, radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f, center = c
                )
            }
            translate(origin.x, origin.y) {
                val pivot = Offset(shieldSize.width / 2f, shieldSize.height / 2f)
                if (connecting) {
                    for (i in 0..1) {
                        val t = (wave + i * 0.5f) % 1f
                        scale(1f + 0.36f * t, pivot) {
                            drawPath(path, Palette.Glow.copy(alpha = (1f - t) * 0.6f), style = Stroke(2.5.dp.toPx()))
                        }
                    }
                } else {
                    // A quiet halo outline so the idle shield still reads as a button.
                    scale(1.12f, pivot) {
                        drawPath(path, rim.copy(alpha = if (connected) 0.35f * breathe else 0.22f), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }

        val label = when (state) {
            is ConnectionState.Connected -> stringResource(R.string.pg_shield_disconnect)
            ConnectionState.Connecting -> stringResource(R.string.pg_shield_cancel)
            is ConnectionState.Failed -> stringResource(R.string.pg_shield_retry)
            ConnectionState.Disconnected -> stringResource(R.string.pg_shield_connect)
        }
        // TalkBack: the action ("Connect") plus whether it is on now ("Protected").
        val stateLabel = stringResource(
            when {
                connected -> R.string.pg_notif_protected
                connecting -> R.string.pg_status_connecting
                failed -> R.string.pg_status_error
                else -> R.string.pg_status_disconnected
            }
        )
        Box(
            Modifier
                .size(shieldW, height)
                .pressScale(interaction, 0.94f)
                .graphicsLayer { shadowElevation = if (connected) 18.dp.toPx() else 6.dp.toPx(); shape = ShieldShape; clip = false }
                .clip(ShieldShape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .border(2.dp, rim, ShieldShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics { contentDescription = label; stateDescription = stateLabel },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    connected -> 2
                    connecting -> 1
                    failed -> 3
                    else -> 0
                },
                transitionSpec = {
                    (scaleIn(tween(Motion.MEDIUM), initialScale = 0.6f) + fadeIn(tween(Motion.MEDIUM))) togetherWith
                        (scaleOut(tween(Motion.FAST), targetScale = 0.6f) + fadeOut(tween(Motion.FAST)))
                },
                label = "shieldGlyph"
            ) { s ->
                val glyphSize = height * 0.30f
                when (s) {
                    1 -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(height * 0.24f)
                    )
                    2 -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(glyphSize))
                    3 -> Icon(Icons.Rounded.Refresh, null, tint = Palette.Error, modifier = Modifier.size(glyphSize))
                    else -> Icon(Icons.Filled.PowerSettingsNew, null, tint = glyph, modifier = Modifier.size(glyphSize))
                }
            }
        }
    }
}
