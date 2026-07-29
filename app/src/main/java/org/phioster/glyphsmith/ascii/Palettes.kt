package org.phioster.glyphsmith.ascii

import kotlin.random.Random

/**
 * A palette maps a cell's luminance onto a colour, darkest entry first.
 *
 * Used by [ColorMode.PALETTE]; [ColorMode.SOURCE] samples the source image instead and
 * [ColorMode.SINGLE] paints everything in one ink colour.
 */
data class Palette(
    val id: String,
    val name: String,
    val category: String,
    val colors: List<Int>,
)

object Palettes {

    const val MONOCHROME = "MONOCHROME"
    const val TERMINAL = "TERMINAL"
    const val RETRO = "RETRO"
    const val WARM = "WARM"
    const val COOL = "COOL"
    const val NEON = "NEON"

    private fun palette(id: String, name: String, category: String, vararg colors: Long) =
        Palette(id, name, category, colors.map { it.toInt() })

    val all: List<Palette> = listOf(
        // Monochrome
        palette("grayscale", "Grayscale", MONOCHROME, 0xFF000000, 0xFF444444, 0xFF888888, 0xFFBBBBBB, 0xFFFFFFFF),
        palette("ink", "Ink", MONOCHROME, 0xFF0A0A0A, 0xFF2B2B2B, 0xFF6E6E6E, 0xFFD8D8D8),
        palette("bone", "Bone", MONOCHROME, 0xFF14110E, 0xFF4A4238, 0xFF9C8F7A, 0xFFEDE3D2),

        // Terminal
        palette("phosphor", "Phosphor", TERMINAL, 0xFF06170C, 0xFF0E3B1E, 0xFF1C7A3C, 0xFF33FF66, 0xFFB6FFCE),
        palette("amber", "Amber", TERMINAL, 0xFF1A0E00, 0xFF4D2C00, 0xFF9C5C00, 0xFFFFAA22, 0xFFFFE0A3),
        palette("ice", "Ice", TERMINAL, 0xFF04121C, 0xFF0B3348, 0xFF1D7BA8, 0xFF46C8F0, 0xFFCDF2FF),

        // Retro
        palette("gameboy", "Game Boy", RETRO, 0xFF0F380F, 0xFF306230, 0xFF8BAC0F, 0xFF9BBC0F),
        palette("c64", "Commodore", RETRO, 0xFF352879, 0xFF6C5EB5, 0xFF7ABFC7, 0xFFAAFF66, 0xFFEEEEEE),
        palette("cga", "CGA", RETRO, 0xFF000000, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFFFFF),
        palette("spectrum", "Spectrum", RETRO, 0xFF000000, 0xFF0000D7, 0xFFD70000, 0xFFD7D700, 0xFFFFFFFF),

        // Warm
        palette("sepia", "Sepia", WARM, 0xFF1C1108, 0xFF4A2F1B, 0xFF8A6136, 0xFFC9A06B, 0xFFF2E2C6),
        palette("ember", "Ember", WARM, 0xFF160500, 0xFF5A1200, 0xFFB33A00, 0xFFFF8A1F, 0xFFFFD9A0),
        palette("sunset", "Sunset", WARM, 0xFF2B0A3D, 0xFF7A1F5C, 0xFFC94F5A, 0xFFF59B4B, 0xFFFFE9A8),

        // Cool
        palette("ocean", "Ocean", COOL, 0xFF02121F, 0xFF0A3350, 0xFF17688E, 0xFF3FB0C8, 0xFFBDECF2),
        palette("arctic", "Arctic", COOL, 0xFF0B1622, 0xFF223A52, 0xFF4C7FA5, 0xFF95C6DE, 0xFFF0FAFF),
        palette("twilight", "Twilight", COOL, 0xFF0D0A1F, 0xFF2A2350, 0xFF574A96, 0xFF9088D6, 0xFFD9D4FF),

        // Neon
        palette("neon", "Neon", NEON, 0xFF12021F, 0xFF5B0F8C, 0xFFC42BC4, 0xFFFF4FA3, 0xFF7CF9FF),
        palette("vapor", "Vapor", NEON, 0xFF190B2E, 0xFF44287A, 0xFFB44BC4, 0xFFFF7AC8, 0xFF9FF7F0),
        palette("acid", "Acid", NEON, 0xFF06140A, 0xFF14572B, 0xFF3BC44A, 0xFFA8FF3B, 0xFFF2FFB0),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    private val byId: Map<String, Palette> = all.associateBy { it.id }

    val default: Palette = byId.getValue("phosphor")

    fun byId(id: String): Palette = byId[id] ?: default

    fun inCategory(category: String): List<Palette> = all.filter { it.category == category }

    /**
     * Colour for a normalised luminance in 0..1, linearly interpolated between palette stops
     * so a five-entry palette still gives a smooth ramp.
     */
    fun sample(palette: Palette, t: Float): Int {
        val colors = palette.colors
        if (colors.isEmpty()) return 0xFFFFFFFF.toInt()
        if (colors.size == 1) return colors[0]
        val clamped = t.coerceIn(0f, 1f)
        val scaled = clamped * (colors.size - 1)
        val lower = scaled.toInt().coerceAtMost(colors.size - 2)
        val frac = scaled - lower
        return lerpColor(colors[lower], colors[lower + 1], frac)
    }

    /** Rec. 709 luminance of a packed colour, 0..1 — the key palettes are ordered by. */
    fun luminanceOf(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    /**
     * Turns loose colours into a usable palette: opaque, deduplicated, darkest first.
     *
     * The sort is not cosmetic. [sample] maps a cell's luminance straight onto the list
     * position, so an unsorted palette makes bright areas come out dark and the image reads
     * as noise. Anything built from an image has to come through here.
     */
    fun fromColors(colors: List<Int>): List<Int> = colors
        .map { it or (0xFF shl 24) }
        .distinct()
        .sortedBy { luminanceOf(it) }

    /**
     * Resamples [colors] down to [depth] stops, keeping both ends.
     *
     * A depth of 0 — or one that is not actually a reduction — leaves the palette alone.
     * Interpolating rather than dropping entries keeps the ramp evenly spaced instead of
     * lop-sided towards whichever end happened to survive.
     */
    fun withDepth(colors: List<Int>, depth: Int): List<Int> {
        if (depth <= 0 || colors.size < 2 || depth >= colors.size) return colors
        if (depth == 1) return listOf(colors.first())
        val palette = Palette("resampled", "resampled", "", colors)
        return List(depth) { i -> sample(palette, i.toFloat() / (depth - 1)) }
    }

    /**
     * Shuffles the stops that are not locked, leaving the locked ones exactly where they are.
     *
     * The result is deliberately *not* re-sorted. A shuffle is for finding a look by accident,
     * and a palette that always snaps back to dark-to-light can only ever produce the same
     * ordering it started with.
     */
    fun shuffle(colors: List<Int>, locked: List<Boolean>, random: Random): List<Int> {
        val movable = colors.indices.filterNot { locked.getOrElse(it) { false } }
        if (movable.size < 2) return colors
        val shuffled = movable.map { colors[it] }.shuffled(random)
        val out = colors.toMutableList()
        movable.forEachIndexed { i, index -> out[index] = shuffled[i] }
        return out
    }

    private fun lerpColor(from: Int, to: Int, frac: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * frac).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
