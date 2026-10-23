package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Packet Guard palette: deep navy surfaces, one blue accent, quiet gray borders. */
object Palette {
    val Background = Color(0xFF0F172A)   // app background
    val Surface = Color(0xFF131C31)      // cards
    val SurfaceHigh = Color(0xFF1A2540)  // raised elements, inputs, inactive pills
    val SurfaceHigher = Color(0xFF22304F)
    val Border = Color(0xFF2A3650)       // subtle borders
    val AccentBlue = Color(0xFF1E40AF)   // brand accent
    val Glow = Color(0xFF3B82F6)         // highlights and glows
    val Sky = Color(0xFF60A5FA)          // top of the shield gradient
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val Gray = Color(0xFF475569)         // idle state
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Glow,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentBlue,
    onPrimaryContainer = Color.White,
    secondary = Palette.Sky,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigher,
    outline = Palette.Border,
    outlineVariant = Palette.Border,
    error = Palette.Error
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PacketGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
