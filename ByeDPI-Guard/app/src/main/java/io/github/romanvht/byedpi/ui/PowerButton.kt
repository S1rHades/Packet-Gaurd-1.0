package io.github.romanvht.byedpi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central circular connect button.
 *  - Disconnected / Failed: gray ring and icon
 *  - Connecting: pulsing blue rings radiating outward
 *  - Connected: solid blue disc with a soft glow
 */
@Composable
fun PowerButton(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp
) {
    val connecting = state is ConnectionState.Connecting
    val connected = state is ConnectionState.Connected

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulseValue"
    )

    val fill by animateColorAsState(
        targetValue = when {
            connected -> Palette.AccentBlue
            connecting -> Palette.SurfaceHigh
            else -> Palette.Surface
        },
        animationSpec = tween(450), label = "fill"
    )
    val ring by animateColorAsState(
        targetValue = if (connected || connecting) Palette.Glow else Palette.Gray,
        animationSpec = tween(450), label = "ring"
    )
    val iconTint by animateColorAsState(
        targetValue = if (connected || connecting) Color.White else Palette.Gray,
        animationSpec = tween(450), label = "icon"
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = this.size.minDimension / 2f
            val core = outer * 0.60f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)

            if (connected) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.Glow.copy(alpha = 0.40f), Color.Transparent),
                        center = c, radius = outer
                    ),
                    radius = outer, center = c
                )
            }
            if (connecting) {
                for (i in 0..1) {
                    val t = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        color = Palette.Glow.copy(alpha = (1f - t) * 0.55f),
                        radius = core + (outer - core) * t,
                        center = c,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        val label = when (state) {
            is ConnectionState.Connected -> "Disconnect"
            ConnectionState.Connecting -> "Cancel connection"
            else -> "Connect"
        }
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, ring, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(size * 0.24f)
            )
        }
    }
}
