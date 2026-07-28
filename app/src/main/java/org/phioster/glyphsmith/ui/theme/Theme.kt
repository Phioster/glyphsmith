package org.phioster.glyphsmith.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Terminal palette: near-black ground, phosphor green ink, amber for anything destructive. */
object Term {
    val Background = Color(0xFF060A07)
    val Surface = Color(0xFF0B120D)
    val SurfaceHigh = Color(0xFF121C15)
    val Ink = Color(0xFF33FF66)
    val InkDim = Color(0xFF1C7A3C)
    val InkFaint = Color(0xFF0F4A26)
    val Amber = Color(0xFFFFAA22)
    val Danger = Color(0xFFFF5555)
}

private val TerminalColors = darkColorScheme(
    primary = Term.Ink,
    onPrimary = Term.Background,
    secondary = Term.InkDim,
    onSecondary = Term.Ink,
    background = Term.Background,
    onBackground = Term.Ink,
    surface = Term.Surface,
    onSurface = Term.Ink,
    surfaceVariant = Term.SurfaceHigh,
    onSurfaceVariant = Term.InkDim,
    error = Term.Danger,
)

private val mono = FontFamily.Monospace

private val TerminalTypography = Typography(
    titleLarge = TextStyle(fontFamily = mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = mono, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = mono, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = mono, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, letterSpacing = 1.sp),
)

@Composable
fun GlyphsmithTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColors,
        typography = TerminalTypography,
        content = content,
    )
}
