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
        /*
         * Old paper rather than new paper.
         *
         * The first version of this was cream — clean, bright, fresh from the ream. That is
         * not what parchment looks like after a century. Three things happen to it, and all
         * three are here: the sheet yellows and greys as the sizing breaks down, so the
         * background loses its brightness and picks up a dirty cast; the surfaces people
         * actually touch darken further, which is why the raised ones are muddier rather than
         * lighter; and iron gall ink oxidises from black to brown, so nothing on the page is
         * ever fully dark.
         *
         * The contrast is deliberately lower than a light theme would normally take. Aged
         * paper is a narrow range — the whole character is that nothing is bright and nothing
         * is black — but the ink still clears the background by enough to read comfortably.
         */
        background = Color(0xFFD9C9A6),
        surface = Color(0xFFCBB893),
        surfaceHigh = Color(0xFFB8A47C),
        ink = Color(0xFF33281A),
        inkDim = Color(0xFF6B583C),
        inkFaint = Color(0xFF9C8A68),
        amber = Color(0xFF8A4A0E),
        danger = Color(0xFF7A2420),
        light = true,
    )

    val MEDIEVAL = TermPalette(
        id = "medieval",
        name = "Medieval",
        /*
         * Vellum with steel fittings — the page and the armour that guarded it.
         *
         * The trick is that these are two different temperatures and neither one wins. The
         * ground is vellum gone grey rather than gold: parchment kept indoors and out of the
         * sun, so it holds a little warmth but none of the honey [PARCHMENT] has. Everything
         * raised above it is plate — cold, hard, and *darker* than the page, because polished
         * steel in flat light reads as a grey mass rather than a highlight.
         *
         * The ink is iron and takes a faint blue with it, which is what separates this from
         * simply being a colder parchment. The two accents come off a shield rather than a
         * screen: gilt for what is active, and the deep red heralds call gules for what is
         * wrong.
         */
        background = Color(0xFFC8C0AC),
        surface = Color(0xFFB0AB99),
        surfaceHigh = Color(0xFF8E8C82),
        ink = Color(0xFF23252A),
        inkDim = Color(0xFF4E5158),
        inkFaint = Color(0xFF83817A),
        amber = Color(0xFF8C6A22),
        danger = Color(0xFF6E1F1F),
        light = true,
    )

    val all = listOf(MATRIX, AMBER, ICE, HANDHELD, ROSE, PARCHMENT, MEDIEVAL)

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
