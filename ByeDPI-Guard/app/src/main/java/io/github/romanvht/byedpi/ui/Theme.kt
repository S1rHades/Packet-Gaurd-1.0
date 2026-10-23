package io.github.romanvht.byedpi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Dark, minimal, NordVPN-inspired palette. */
object Palette {
    val Background = Color(0xFF0F172A)   // deep navy / slate
    val Surface = Color(0xFF131C31)      // cards
    val SurfaceHigh = Color(0xFF1A2540)  // raised elements and inputs
    val Border = Color(0xFF2A3650)       // subtle gray borders
    val AccentBlue = Color(0xFF1E40AF)   // brand accent
    val Glow = Color(0xFF3B82F6)         // lighter blue for glows and highlights on dark
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val Gray = Color(0xFF475569)         // disconnected state
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.AccentBlue,
    onPrimary = Color.White,
    secondary = Palette.Glow,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Border,
    error = Palette.Error
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
)

@Composable
fun ByeGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}
