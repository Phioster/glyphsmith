package org.phioster.glyphsmith.ascii

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.effects.GlowParams

enum class ColorMode { SINGLE, SOURCE, PALETTE }

enum class FontStyle { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

/**
 * Every knob that turns a bitmap into a character grid. Serializable because a preset is
 * literally this object written to disk.
 *
 * The control set mirrors Script Slayer's ASCII Settings panel: depth, character category
 * and set, injected characters, character offset, font style, palette / transparent
 * background / background colour.
 */
@Serializable
data class AsciiParams(
    val charSetId: String = "ascii-standard-10",
    /** Width in source pixels of one glyph cell — the single biggest quality/size lever. */
    val cellSize: Int = 8,
    /**
     * How many glyph levels to use, 1..[MAX_DEPTH]. A set shorter than this is used whole,
     * so the effective depth is `min(depth, set.length)`.
     */
    val depth: Int = 10,
    /** Rotates the luminance→glyph mapping, wrapping around the ramp. */
    val offset: Int = 0,
    /** Up to [MAX_INJECTION] custom characters appended to the dense end of the ramp. */
    val injection: String = "",
    val invert: Boolean = false,
    val fontStyle: FontStyle = FontStyle.REGULAR,
    val glyphFont: GlyphFont = GlyphFont.AUTO,
    /** -1..1, added to normalised luminance. */
    val brightness: Float = 0f,
    /** 0.2..3, applied around mid grey. */
    val contrast: Float = 1f,
    /** 0.2..3, gamma applied before mapping. */
    val gamma: Float = 1f,
    val colorMode: ColorMode = ColorMode.SINGLE,
    val inkColor: Int = DEFAULT_INK,
    val paletteId: String = "phosphor",
    /** Edited palette stops. Non-empty means the UI has customised [paletteId]'s colours. */
    val paletteOverride: List<Int> = emptyList(),
    val transparentBackground: Boolean = false,
    val backgroundColor: Int = DEFAULT_BACKGROUND,
    /** Glyph size in output pixels; the exported image is grid × this. */
    val fontSizePx: Int = 14,
    /** Post-effect applied to the rendered glyphs, not to the source image. */
    val glow: GlowParams = GlowParams(),
) {
    val charSet: CharacterSet get() = CharacterSets.byId(charSetId)

    /** Depth actually reachable with the current set — what the UI shows as `n/64`. */
    val effectiveDepth: Int get() = depth.coerceIn(1, MAX_DEPTH)

    /**
     * The ramp actually used for mapping: the set narrowed to [depth] levels, then the
     * injected characters, then optionally reversed.
     *
     * Injection lands at the dense end on purpose — injected glyphs show up in the
     * brightest areas, which is predictable and keeps the tonal ramp below it intact.
     */
    fun effectiveRamp(): String {
        val base = charSet.glyphs
        val levels = effectiveDepth
        val narrowed = when {
            levels >= base.length -> base
            levels == 1 -> base.takeLast(1)
            else -> buildString {
                for (i in 0 until levels) {
                    val index = (i * (base.length - 1)) / (levels - 1)
                    append(base[index])
                }
            }
        }
        val injected = narrowed + injection.take(MAX_INJECTION)
        return if (invert) injected.reversed() else injected
    }

    /** Upper bound the offset slider runs to — the offset wraps at the ramp length. */
    fun offsetMax(): Int = effectiveRamp().length.coerceAtLeast(1)

    /** The palette in force: the edited stops when there are any, else the named palette. */
    fun activePalette(): Palette {
        val named = Palettes.byId(paletteId)
        return if (paletteOverride.isEmpty()) named else named.copy(colors = paletteOverride)
    }

    companion object {
        const val MAX_INJECTION = 10
        const val MAX_DEPTH = 64
        const val DEFAULT_INK = 0xFF33FF66.toInt()
        const val DEFAULT_BACKGROUND = 0xFF060A07.toInt()
        val CELL_SIZE_RANGE = 2..48
        val FONT_SIZE_RANGE = 6..48
    }
}
