package org.phioster.glyphsmith.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One complete look. Every colour the interface uses comes from exactly these eight. */
data class TermPalette(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val ink: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val amber: Color,
    val danger: Color,
    /** Light themes need the Material scheme built the other way round. */
    val light: Boolean = false,
)

object TermThemes {

    val MATRIX = TermPalette(
        id = "matrix",
        name = "Matrix",
        background = Color(0xFF060A07),
        surface = Color(0xFF0B120D),
        surfaceHigh = Color(0xFF121C15),
        ink = Color(0xFF33FF66),
        inkDim = Color(0xFF1C7A3C),
        inkFaint = Color(0xFF0F4A26),
        amber = Color(0xFFFFAA22),
        danger = Color(0xFFFF5555),
    )

    val AMBER = TermPalette(
        id = "amber",
        name = "Amber CRT",
        background = Color(0xFF0C0700),
        surface = Color(0xFF160D01),
        surfaceHigh = Color(0xFF231503),
        ink = Color(0xFFFFAA22),
        inkDim = Color(0xFF9C5C00),
        inkFaint = Color(0xFF5A3400),
        amber = Color(0xFFFFE0A3),
        danger = Color(0xFFFF6B4A),
    )

    val ICE = TermPalette(
        id = "ice",
        name = "Ice",
        background = Color(0xFF03090E),
        surface = Color(0xFF07141D),
        surfaceHigh = Color(0xFF0C2130),
        ink = Color(0xFF46C8F0),
        inkDim = Color(0xFF1D7BA8),
        inkFaint = Color(0xFF0F4560),
        amber = Color(0xFFFFC46B),
        danger = Color(0xFFFF6187),
    )

    val HANDHELD = TermPalette(
        id = "handheld",
        name = "Handheld",
        background = Color(0xFF0F380F),
        surface = Color(0xFF1A4A18),
        surfaceHigh = Color(0xFF2A5C24),
        ink = Color(0xFF9BBC0F),
        inkDim = Color(0xFF6E8C10),
        inkFaint = Color(0xFF44600C),
        amber = Color(0xFFD7E894),
        danger = Color(0xFFE8705A),
    )

    val ROSE = TermPalette(
        id = "rose",
        name = "Rose",
        background = Color(0xFF120610),
        surface = Color(0xFF1E0B1B),
        surfaceHigh = Color(0xFF2C1128),
        ink = Color(0xFFFF6FC4),
        inkDim = Color(0xFFA83B7E),
        inkFaint = Color(0xFF66234C),
        amber = Color(0xFFFFC978),
        danger = Color(0xFFFF4D4D),
    )

    /**
     * The one light theme. Parchment and iron gall, for the medieval look Script Slayer
     * itself wears — and a useful reminder that the panels have to stay readable when the
     * ground is brighter than the ink rather than darker.
     */
    val PARCHMENT = TermPalette(
        id = "parchment",
        name = "Parchment",
        background = Color(0xFFEFE3C8),
        surface = Color(0xFFE3D4B4),
        surfaceHigh = Color(0xFFD6C39C),
        ink = Color(0xFF241A0E),
        inkDim = Color(0xFF5C4A2E),
        inkFaint = Color(0xFF9A8663),
        amber = Color(0xFF9A5B12),
        danger = Color(0xFF8E2B2B),
        light = true,
    )

    val all = listOf(MATRIX, AMBER, ICE, HANDHELD, ROSE, PARCHMENT)

    fun byId(id: String): TermPalette = all.firstOrNull { it.id == id } ?: MATRIX
}

/**
 * The active palette.
 *
 * This is deliberately a mutable global backed by Compose state rather than a
 * CompositionLocal. Reading `Term.Ink` during composition subscribes to that state, so
 * switching the theme recomposes everything that draws with it — and every one of the
 * couple of hundred existing call sites keeps working untouched, including the handful that
 * sit outside a composable scope where a CompositionLocal would not be reachable at all.
 *
 * The app shows one theme at a time, so there is nothing here for a second reader to race
 * with; if that ever stops being true, this is the thing to revisit.
 */
object Term {
    var palette: TermPalette by mutableStateOf(TermThemes.MATRIX)

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val Ink: Color get() = palette.ink
    val InkDim: Color get() = palette.inkDim
    val InkFaint: Color get() = palette.inkFaint
    val Amber: Color get() = palette.amber
    val Danger: Color get() = palette.danger
}

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
    val palette = Term.palette
    val colors = if (palette.light) {
        lightColorScheme(
            primary = palette.ink,
            onPrimary = palette.background,
            secondary = palette.inkDim,
            onSecondary = palette.background,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.inkDim,
            error = palette.danger,
        )
    } else {
        darkColorScheme(
            primary = palette.ink,
            onPrimary = palette.background,
            secondary = palette.inkDim,
            onSecondary = palette.ink,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.inkDim,
            error = palette.danger,
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = TerminalTypography,
        content = content,
    )
}
