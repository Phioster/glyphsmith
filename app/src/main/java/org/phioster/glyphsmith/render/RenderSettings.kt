package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.core.image.Adjustments
import org.phioster.glyphsmith.core.dither.OrbOptions
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.color.Palette

enum class ColorMode { SINGLE, SOURCE, PALETTE }

enum class FontStyle { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

/**
 * Which face to draw glyphs with. `AUTO` picks per ramp — see `Fonts.resolve`.
 *
 * The enum lives here rather than with the font machinery because it is a stored settings
 * value: it travels in every preset, so it belongs to the settings vocabulary. Resolving one
 * to an actual typeface needs Android and stays in the glyph module.
 */
enum class GlyphFont { AUTO, DEJAVU, UNIFONT, SYSTEM }

/**
 * Every knob that turns a bitmap into a render. Serializable because a preset is literally
 * this object written to disk.
 *
 * Named for what it is rather than for one of the two things it drives. Most of what is here
 * — sampling, adjustments, dither, palette, effects, layers, animation, output — belongs to
 * both render modules; the glyph half is the ramp, the character set, the font and the edge
 * mapping, and [RenderMode] decides whether those are read at all. It was called AsciiParams
 * back when there was only one module and the distinction did not exist.
 *
 * The glyph control set mirrors the reference app's settings panel: depth, character category
 * and set, injected characters, character offset, font style, palette / transparent
 * background / background colour.
 */
@Serializable
data class RenderSettings(
    /**
     * Whether levels become characters or colours.
     *
     * Lives here rather than in the UI state because this object *is* a preset and the undo
     * unit — putting the mode anywhere else would mean presets and undo silently losing it.
     * Defaults to the glyph mode so a preset written before the field existed is unchanged.
     */
    val renderMode: RenderMode = RenderMode.GlyphMatrix,
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
    /**
     * The adjustments that run *before* the dither, so they change what the algorithm sees
     * rather than how the result looks. Saturation and blur also exist in the effect chain,
     * where they do something else entirely — see [Adjustments].
     *
     * All neutral by default, so nothing saved before these existed renders differently.
     */
    val saturation: Int = 100,
    val midtones: Int = 50,
    val highlights: Int = 50,
    val hue: Int = 0,
    /** Radius in *cells*, not pixels. 0 is off. */
    val preBlur: Int = 0,
    val denoise: Int = 0,
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
    /**
     * 0..100 — a pattern's second axis, whose meaning belongs to the style.
     *
     * Spokes for a radial burst, the beating frequency for a moiré, line weight for a
     * crosshatch. One field instead of a dozen, each of which would sit dead for every style
     * but one; the panel renames the slider so it never reads as "density".
     */
    val patternDensity: Int = 50,
    /** Shape of an individual orb — only the orb and beehive modes read these. */
    val orbCount: Int = 1,
    val orbSize: Int = 100,
    val orbIntensity: Int = 0,
    val orbRandom: Int = 0,
    val orbOffset: Int = 0,
    val orbDirection: Int = 0,
    val orbSeed: Int = 1,
    /**
     * An imported dither screen, stored inline as the ranked cells `ScreenImport` produces.
     *
     * Inline for the reason `paletteOverride` is: a reference to a file is a reference that can
     * go missing, and a preset that renders differently on somebody else's device is worse than
     * one that is a few kilobytes larger. Empty means none, and `CUSTOM_SCREEN` falls back to a
     * Bayer tile rather than to a flat field.
     *
     * The size is the screen's own length — 256 or 1024 — rather than a second field that can
     * disagree with it.
     */
    val screenOverride: List<Int> = emptyList(),
    /** Let strong edges pick a directional glyph instead of a brightness-matched one. */
    val edgeEnabled: Boolean = false,
    /** 0..100 — gradient magnitude a cell needs before it counts as an edge. */
    val edgeThreshold: Int = 25,
    val edgeSetId: String = "ascii",
    /** Draw only the edges and leave flat areas blank — line art instead of tone. */
    val edgeOnly: Boolean = false,
    val colorMode: ColorMode = ColorMode.SINGLE,
    val inkColor: Int = DEFAULT_INK,
    val paletteId: String = "palette.grayscale",
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
    /**
     * Which metric decides "nearest colour" when the pixel path reduces an image to a palette.
     *
     * Only read in [RenderMode.PurePixel] with [ColorMode.SOURCE] — the glyph path samples a
     * palette by luminance and never asks the question. OKLAB by default because it is the one
     * of the three that agrees with the eye about which of two entries is closer.
     */
    val colorDistance: ColorDistance = ColorDistance.OKLAB,
    /**
     * Output size of one dithered cell, in pixels. 0 lets the render decide.
     *
     * Splits resolution from magnification, which used to be one number: [cellSize] says how
     * coarsely the image is *sampled*, and this says how large each of those cells is *drawn*.
     * Fused, a chunky dither could only ever be a small image. Zero keeps the old behaviour —
     * blocks grow to fill whatever the preview or export budget allows — so no stored preset
     * changes.
     */
    val pixelBlock: Int = 0,
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
    /**
     * Extra renderings stacked over this one. Empty by default, so nothing changes for
     * anything that has never touched them.
     *
     * A layer carries a whole [RenderSettings] and therefore a `layers` list of its own; that
     * one is deliberately not recursed into. See [Layer].
     */
    val layers: List<Layer> = emptyList(),
) {
    /** Depth actually reachable with the current set — what the UI shows as `n/64`. */
    val effectiveDepth: Int get() = depth.coerceIn(1, MAX_DEPTH)

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
        /**
         * The settings a new session opens with.
         *
         * Exists because the constructor default cannot be it. That default is the value a
         * preset falls back to when it names no render mode, and it has to stay Glyph Art or
         * every preset written before the field existed would change appearance. This is the
         * other default — what a session with nothing loaded starts in — and that is Pixel
         * Dither, the general-purpose mode.
         */
        fun newSession(): RenderSettings = RenderSettings(renderMode = RenderMode.DEFAULT)

        const val MAX_INJECTION = 10
        const val MAX_DEPTH = 64
        /**
         * White, not the phosphor green the interface wears.
         *
         * The theme is a choice about the app; the ink is part of the artwork. Starting a
         * new image already tinted green meant every export carried a decision nobody had
         * made, and undoing it took two taps before you could even see the picture straight.
         */
        const val DEFAULT_INK = 0xFFFFFFFF.toInt()
        /**
         * Neutral, not the faintly green black that used to sit under the green ink. It was
         * chosen to sit under a colour that is no longer the default, and a green cast under
         * white ink is visible on a large flat area.
         */
        const val DEFAULT_BACKGROUND = 0xFF0A0A0A.toInt()
        val CELL_SIZE_RANGE = 2..48
        val FONT_SIZE_RANGE = 6..48
        val CANVAS_SIZE_RANGE = 64..8192
        val DITHER_SCALE_RANGE = 25..400
        val PALETTE_DEPTH_RANGE = 2..32
        val SATURATION_RANGE = 0..200
        val TONE_RANGE = 0..100
        val HUE_RANGE = 0..359
        val NEIGHBOURHOOD_RANGE = 0..6
        val MOD_SCALE_RANGE = 2..64
        val MOD_ANGLE_RANGE = 0..359
    }
}
