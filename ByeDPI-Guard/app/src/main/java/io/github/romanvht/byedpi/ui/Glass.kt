package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Packet Guard's glass design language: translucent navy panels with a light top edge,
 * floating over soft blue glows. Every card, control and bar in the app is built from these.
 */
object Glass {
    val CardShape = RoundedCornerShape(22.dp)
    val ControlShape = RoundedCornerShape(16.dp)

    /** Selected pills, primary buttons and highlighted bars. */
    val Accent: Brush get() = Brush.verticalGradient(listOf(Palette.Sky, Palette.Glow, Palette.AccentBlue))

    /** Background of small inset elements (fields, chips, tracks). */
    val Inset = Color.White.copy(alpha = 0.06f)
    val InsetBorder = Color.White.copy(alpha = 0.12f)
}

/** A glass panel. [borderColor] replaces the light edge, e.g. to highlight the winning card. */
fun Modifier.glass(shape: Shape = Glass.CardShape, borderColor: Color? = null): Modifier = this
    .clip(shape)
    .background(Palette.Surface.copy(alpha = 0.62f))
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.015f))))
    .then(
        if (borderColor != null) Modifier.border(1.5.dp, borderColor, shape)
        else Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))),
            shape
        )
    )

/** A smaller inset surface inside a glass panel: input tracks, chips, mini tiles. */
fun Modifier.glassInset(shape: Shape = Glass.ControlShape): Modifier = this
    .clip(shape)
    .background(Glass.Inset)
    .border(1.dp, Glass.InsetBorder, shape)

private fun DrawScope.glow(color: Color, x: Float, y: Float, r: Float) {
    val center = Offset(size.width * x, size.height * y)
    val radius = size.width * r
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius, center)
}

/** Soft colored light behind a group of glass panels. */
fun Modifier.ambientGlow(): Modifier = drawBehind {
    glow(Palette.Glow.copy(alpha = 0.22f), 0.12f, 0.10f, 0.65f)
    glow(Palette.Sky.copy(alpha = 0.12f), 0.95f, 0.45f, 0.55f)
    glow(Palette.AccentBlue.copy(alpha = 0.28f), 0.30f, 0.90f, 0.70f)
}

/** The app's backdrop: navy with fixed glows the screens' glass panels float over. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                glow(Palette.Glow.copy(alpha = 0.20f), 0.05f, 0.08f, 0.95f)
                glow(Palette.Sky.copy(alpha = 0.10f), 1.0f, 0.42f, 0.75f)
                glow(Palette.AccentBlue.copy(alpha = 0.30f), 0.20f, 0.92f, 1.0f)
            }
    ) {
        // Screens drawn straight on the backdrop (no Scaffold or Surface) still get light text.
        CompositionLocalProvider(LocalContentColor provides Palette.TextPrimary) { content() }
    }
}
