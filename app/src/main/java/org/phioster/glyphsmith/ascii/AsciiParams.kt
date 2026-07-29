package org.phioster.glyphsmith.ascii

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.effects.EffectStack

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
    /**
     * Replaces the selected set's glyphs. Empty means the set is used as it ships.
     *
     * One field serves two features: `auto-order` writes the measured coverage order into
     * it, and the ramp editor writes a hand-arranged one. Because it is an ordinary field it
     * travels into presets and exports without anything else having to know about it.
     */
    val rampOverride: String = "",
    val invert: Boolean = false,
    val fontStyle: FontStyle = FontStyle.REGULAR,
    val glyphFont: GlyphFont = GlyphFont.AUTO,
    /** -1..1, added to normalised luminance. */
    val brightness: Float = 0f,
    /** 0.2..3, applied around mid grey. */
    val contrast: Float = 1f,
    /** 0.2..3, gamma applied before mapping. */
    val gamma: Float = 1f,
    /** How the quantisation error is handled when a cell picks its glyph. */
    val ditherMode: DitherMode = DitherMode.NONE,
    /** 0..100 — how much of the error is actually propagated. */
    val ditherStrength: Int = 100,
    /** Alternate scan direction per row; suppresses the diagonal worm pattern. */
    val serpentine: Boolean = true,
    /**
     * Pattern size as a percentage, for every threshold-based mode. Independent of
     * [cellSize] on purpose — driving the two apart is what makes these algorithms break
     * down in the interesting way rather than simply getting coarser.
     */
    val ditherScale: Int = 100,
    /** Cells per period of a modulation pattern. */
    val modScale: Int = 8,
    /** Rotation of the modulation pattern in degrees. */
    val modAngle: Int = 0,
    /** 0..100 % of a period; animate it and the pattern travels. */
    val modPhase: Int = 0,
    /** Shape of an individual orb — only the orb and beehive modes read these. */
    val orbCount: Int = 1,
    val orbSize: Int = 100,
    val orbIntensity: Int = 0,
    val orbRandom: Int = 0,
    val orbOffset: Int = 0,
    val orbDirection: Int = 0,
    val orbSeed: Int = 1,
    /** Let strong edges pick a directional glyph instead of a brightness-matched one. */
    val edgeEnabled: Boolean = false,
    /** 0..100 — gradient magnitude a cell needs before it counts as an edge. */
    val edgeThreshold: Int = 25,
    val edgeSetId: String = "ascii",
    /** Draw only the edges and leave flat areas blank — line art instead of tone. */
    val edgeOnly: Boolean = false,
    val colorMode: ColorMode = ColorMode.SINGLE,
    val inkColor: Int = DEFAULT_INK,
    val paletteId: String = "phosphor",
    /** Edited palette stops. Non-empty means the UI has customised [paletteId]'s colours. */
    val paletteOverride: List<Int> = emptyList(),
    /** Per-stop locks; a locked stop survives a shuffle. Shorter than the palette means unlocked. */
    val paletteLocks: List<Boolean> = emptyList(),
    /**
     * How many colour levels to take from the palette, independent of the glyph [depth].
     * 0 means all of them. Mismatching the two deliberately is how a palette reads as a
     * harder posterisation than the character ramp.
     */
    val paletteDepth: Int = 0,
    val transparentBackground: Boolean = false,
    val backgroundColor: Int = DEFAULT_BACKGROUND,
    /** Glyph size in output pixels; the exported image is grid × this. Ignored in canvas mode. */
    val fontSizePx: Int = 14,
    /**
     * Fixed output size. Off: the grid decides how big the image is. On: the image is
     * exactly [canvasWidth]×[canvasHeight] and the glyphs are sized to fill it.
     */
    val canvasEnabled: Boolean = false,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1440,
    /** Post-effects applied to the rendered glyphs, not to the source image. */
    val effects: EffectStack = EffectStack(),
    /** Drives selected parameters over time to animate a still image. */
    val animation: AnimationParams = AnimationParams(),
    /** Animated noise added to the dither threshold — works with every dither mode. */
    val temporal: TemporalParams = TemporalParams(),
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
    /** The glyphs the ramp is built from: the override when set, else the chosen set. */
    fun baseGlyphs(): String = rampOverride.ifEmpty { charSet.glyphs }

    fun effectiveRamp(): String {
        val base = baseGlyphs()
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

    /** The orb controls gathered up for the engine. */
    fun orbOptions(): OrbOptions = OrbOptions(
        count = orbCount,
        size = orbSize,
        intensity = orbIntensity,
        random = orbRandom,
        offset = orbOffset,
        direction = orbDirection,
        seed = orbSeed,
    )

    /** Upper bound the offset slider runs to — the offset wraps at the ramp length. */
    fun offsetMax(): Int = effectiveRamp().length.coerceAtLeast(1)

    /** The palette the UI edits: the edited stops when there are any, else the named one. */
    fun activePalette(): Palette {
        val named = Palettes.byId(paletteId)
        return if (paletteOverride.isEmpty()) named else named.copy(colors = paletteOverride)
    }

    /**
     * The palette the engine actually samples — [activePalette] narrowed to [paletteDepth].
     * Kept separate so the stop editor still shows every stop it is editing rather than the
     * reduced set that happens to be in use.
     */
    fun renderPalette(): Palette {
        val base = activePalette()
        return base.copy(colors = Palettes.withDepth(base.colors, paletteDepth))
    }

    /** Whether the stop at [index] is pinned against a shuffle. */
    fun isStopLocked(index: Int): Boolean = paletteLocks.getOrElse(index) { false }

    companion object {
        const val MAX_INJECTION = 10
        const val MAX_DEPTH = 64
        const val DEFAULT_INK = 0xFF33FF66.toInt()
        const val DEFAULT_BACKGROUND = 0xFF060A07.toInt()
        val CELL_SIZE_RANGE = 2..48
        val FONT_SIZE_RANGE = 6..48
        val CANVAS_SIZE_RANGE = 64..8192
        val DITHER_SCALE_RANGE = 25..400
        val PALETTE_DEPTH_RANGE = 2..32
        val MOD_SCALE_RANGE = 2..64
        val MOD_ANGLE_RANGE = 0..359
    }
}
