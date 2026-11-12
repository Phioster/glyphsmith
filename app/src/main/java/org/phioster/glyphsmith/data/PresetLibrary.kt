package org.phioster.glyphsmith.data

import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.anim.AnimCurve
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.anim.AnimTrack
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.anim.TemporalPattern
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.Layer
import org.phioster.glyphsmith.render.LayerBlend
import org.phioster.glyphsmith.render.OscilloscopeParams
import org.phioster.glyphsmith.render.Phosphor
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.effects.BlendMode
import org.phioster.glyphsmith.effects.BlurSharpenParams
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.CmykHalftoneParams
import org.phioster.glyphsmith.effects.DiffractionStarsParams
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.PixelSortParams
import org.phioster.glyphsmith.effects.SliceShiftParams
import org.phioster.glyphsmith.effects.SortAxis
import org.phioster.glyphsmith.effects.SubtextureParams
import org.phioster.glyphsmith.effects.TextureKind
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.effects.JpegGlitchParams
import org.phioster.glyphsmith.effects.PostProcessingParams
import org.phioster.glyphsmith.effects.GrainKind
import org.phioster.glyphsmith.effects.NeoGlowParams
import org.phioster.glyphsmith.effects.PaintStrokesParams
import org.phioster.glyphsmith.effects.StressReliefParams
import org.phioster.glyphsmith.render.LightOrbsParams
import org.phioster.glyphsmith.render.FeatherPattern
import org.phioster.glyphsmith.render.ShapeLayout
import org.phioster.glyphsmith.render.ShapeSets
import org.phioster.glyphsmith.render.FlowAsciiParams
import org.phioster.glyphsmith.render.FlowStyle
import org.phioster.glyphsmith.render.ShapeShifterParams
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.effects.CrtWarpParams
import org.phioster.glyphsmith.effects.ColorDepthParams
import org.phioster.glyphsmith.effects.SpotColorPrintParams
import org.phioster.glyphsmith.effects.ModulationColorMode
import org.phioster.glyphsmith.effects.LineAxis
import org.phioster.glyphsmith.effects.ModulationLinesParams
import org.phioster.glyphsmith.effects.InterlaceParams
import org.phioster.glyphsmith.effects.BlueNoiseDitherParams
import org.phioster.glyphsmith.effects.TintMode
import org.phioster.glyphsmith.effects.TintParams

/**
 * The shipped preset library: what every entry is, and in what order they arrive.
 *
 * Split out of [PresetStore] because the two answer different questions. This one is a
 * *catalogue* — a long, flat list of settings with nothing to decide — while the store is the
 * machinery around it: the file, the migrations, what a user saved, and how the two lists are
 * merged. Keeping the catalogue here means a preset can be added or read without scrolling
 * past the persistence, and the persistence can be changed without scrolling past 1500 lines
 * of parameters.
 *
 * Nothing here has state. The store owns everything mutable; this object only describes.
 */
object PresetLibrary {

    /**
     * A general-purpose preset: a pixel dither.
     *
     * The mode is written here rather than left to the constructor, and that is the whole
     * point of this helper. The field default is [RenderMode.GlyphMatrix] and has to stay
     * that way — it is what a preset written before the field existed is read as — so a
     * shipped preset that said nothing would ship as glyph art by accident. Every entry
     * below goes through one of these two functions, so no built-in inherits a mode.
     */
    private fun pixel(name: String, category: String, params: RenderSettings) =
        Preset(name, params.copy(renderMode = RenderMode.PurePixel), category)

    /** A Glyph Art preset: the render module, chosen deliberately. */
    private fun glyph(name: String, category: String, params: RenderSettings) =
        Preset(name, params.copy(renderMode = RenderMode.GlyphMatrix), category)

    /**
     * Thirty-two pigments. The first row is pure tube colours: an error-diffusion dither can only
     * hold a colour that lies between its entries, so a box of muted earths alone turns a bright
     * red into orange. The rest are earths, skin, greys and white.
     */
    private val PAINT_BOX = listOf(
        0xFFE0181F, 0xFFF26A1B, 0xFFF7C91E, 0xFF2FB24A, 0xFF1560D8, 0xFF7B2FA8, 0xFFE8509C, 0xFF000000,
        0xFF0B0A0C, 0xFF1F1A1C, 0xFF2B1D14, 0xFF16263A, 0xFF1F3324, 0xFF4A2A1A, 0xFF5E1F1C, 0xFF3A3F47,
        0xFF2F5078, 0xFF3F6B3A, 0xFF7A4A2A, 0xFFA3352C, 0xFF6E6A66, 0xFF8C6A4A, 0xFF5E8FB8, 0xFF7FA05A,
        0xFFC07A4A, 0xFFD9A441, 0xFFA8A29A, 0xFFDDA98A, 0xFFE8C9A8, 0xFFB8D4E8, 0xFFE9E2D6, 0xFFFFFFFF,
    ).map { it.toInt() }

    /**
     * The colours of a glossy 2000s desktop: sky, water and grass with clean whites, and just
     * enough warm and neutral entries that a face or a sunset is not forced blue.
     */
    private val AERO_BOX = listOf(
        0xFFFFFFFF, 0xFFE8F8FF, 0xFFB8E8FF, 0xFF7FD4FF, 0xFF38B6FF, 0xFF0A8CF0, 0xFF0A5CC8, 0xFF083A8C,
        0xFF06204E, 0xFF02101E, 0xFF5CF0E8, 0xFF18C8C0, 0xFF0C8C90, 0xFFC8F890, 0xFF7CE04C, 0xFF38B02C,
        0xFF1C7A24, 0xFF0C4A1C, 0xFFFFF4B0, 0xFFFFD86A, 0xFFF0A8C8, 0xFFD06A4A, 0xFF8C5A3C, 0xFF6C7884,
    ).map { it.toInt() }

    /**
     * Chrome as a run of tones: it does not rise evenly from dark to light but breaks back to a
     * mid blue after its first white, which is the horizon mirrored in polished metal.
     */
    private val CHROME = listOf(
        0xFF02040C, 0xFF14245C, 0xFF3C6CC8, 0xFF5C7CB8, 0xFF8CB0E8, 0xFFF0F8FF, 0xFF4C6CB0, 0xFF9CB8E0, 0xFFFFFFFF,
    ).map { it.toInt() }

    /**
     * Sweets and rhinestones: pinks from deep to pale, lilac, baby blue, white. No gold or brown:
     * with either in the box, skin came out mustard instead of rosy.
     */
    private val BONBON = listOf(
        0xFF1E0820, 0xFF5A1050, 0xFFA0187A, 0xFFE0389C, 0xFFFF6FB8, 0xFFFFA4D4, 0xFFFFD4EC, 0xFFFFFFFF,
        0xFF7A4CC8, 0xFFB894F0, 0xFF8CD4FF, 0xFFD8F0FF, 0xFFE89CA8, 0xFFFFC4B8, 0xFF7A3A5A, 0xFF3A2A34,
    ).map { it.toInt() }

    /** Pink into turquoise, on night violet: the two colours a 1990s mall rendered everything in. */
    private val VAPOR = listOf(
        0xFF1A0B3A, 0xFF5A2A9C, 0xFFF05CB8, 0xFF4CE0D8, 0xFFFFC8E8, 0xFFFFFFFF,
    ).map { it.toInt() }

    /**
     * Twenty-four of the 216 web-safe colours (every channel one of 00 33 66 99 CC FF): the
     * sixteen a 1990s browser named, moved onto that grid, and eight for skin and pastel. Not all
     * 216, because the palette editor lists every colour as a row.
     */
    private val WEB_SAFE = listOf(
        0xFF000000, 0xFF990000, 0xFF009900, 0xFF999900, 0xFF000099, 0xFF990099, 0xFF009999, 0xFFCCCCCC,
        0xFF666666, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF0000FF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF,
        0xFFFFCC99, 0xFFCC9966, 0xFF996633, 0xFF663300, 0xFFFF99CC, 0xFF99CCFF, 0xFFFFFF99, 0xFF333333,
    ).map { it.toInt() }

    /**
     * Foil on a trading card: under the shadows, the pastel rainbow runs through twice, so a
     * small change in brightness is a change of colour and a surface shimmers as it turns. It
     * ends in pale pink, not white: white left skin as a blank. Same number of stops as before,
     * because the stops are spread evenly and one more would have moved every colour.
     */
    private val HOLO = listOf(
        0xFF0A0A1E, 0xFF3A2A6A, 0xFFE87AC8, 0xFFF8D878, 0xFF7CE8B0, 0xFF6CC8F8,
        0xFFB894F8, 0xFFF8A0D0, 0xFFF8F0B0, 0xFF8CE0F8, 0xFFFFB0E0,
    ).map { it.toInt() }

    /** A wet street at night: a row of dull blues and violets, and a few signs' worth of neon. */
    private val NEON_DUSK = listOf(
        0xFF05060C, 0xFF0E1424, 0xFF16263A, 0xFF1E3A52, 0xFF2A2440, 0xFF3A1E4A, 0xFF4A5A6A, 0xFF6E7E8E,
        0xFF12A8D8, 0xFF4CE0FF, 0xFFE0218A, 0xFFFF6FC0, 0xFF7A3AD8, 0xFFFFD84A, 0xFFB8D8F0, 0xFFFFFFFF,
    ).map { it.toInt() }

    /**
     * Old wallpaper under strip lights, as a ramp from dark to light: damp brown through a green
     * that is off into mustard and pale yellow. A ramp, not a box of colours to match: matched by
     * colour, a red and a skin tone both landed on the same brown and the picture went flat.
     */
    private val STALE = listOf(
        0xFF080702, 0xFF1E1A0A, 0xFF3A3414, 0xFF5A5222, 0xFF7E7230, 0xFFA8943E, 0xFFCCB656, 0xFFE8D67E, 0xFFFAF2BC,
    ).map { it.toInt() }

    /**
     * Akahana's own colours, taken from the game: the field's greys, the poppies' reds, dry grass,
     * and the amber of its lanterns.
     */
    private val AKAHANA = listOf(
        0xFF1A1618, 0xFF2E2829, 0xFF575052, 0xFF8E888A, 0xFFC3BFBA, 0xFFE8E4DC, 0xFF5E1418, 0xFF93282D,
        0xFFD8363C, 0xFFE8504A, 0xFF3D3424, 0xFF5A4A30, 0xFF7C6844, 0xFF9C875B, 0xFFF0A050, 0xFFFFD8A0,
    ).map { it.toInt() }

    /** Black into blood red, with only the very brightest left pale: a ramp, mapped by brightness. */
    private val BLOOD = listOf(
        0xFF000000, 0xFF120003, 0xFF3A0508, 0xFF7A0A10, 0xFFC8181C, 0xFFFF6A50, 0xFFFFE8D8,
    ).map { it.toInt() }

    /** What a 1998 console had for a foggy town: grey-greens, browns, rust, and nothing bright. */
    private val MURK = listOf(
        0xFF050605, 0xFF12140F, 0xFF1E221C, 0xFF2C302A, 0xFF3C423C, 0xFF50564E, 0xFF686E64, 0xFF848A7E,
        0xFFA0A498, 0xFFBEC0B4, 0xFFDCDCD0, 0xFF2A1A12, 0xFF4A2A1A, 0xFF6E3E24, 0xFF8E5A3A, 0xFFB08862,
        0xFFD0B08C, 0xFF5A1410, 0xFF8E2018, 0xFFB83A28, 0xFF1E2E3A, 0xFF34485A, 0xFF2A3A22, 0xFF4A5A34,
    ).map { it.toInt() }

    /** A sunset behind a grid: night violet through magenta and orange to pale yellow. */
    private val OUTRUN = listOf(
        0xFF0A0420, 0xFF2A0A58, 0xFF7A1090, 0xFFE0207A, 0xFFFF5A3C, 0xFFFF9A2A, 0xFFFFE89A,
    ).map { it.toInt() }

    /** A flow ASCII preset: rows of characters or hairs that follow the picture's forms. */
    private fun flow(name: String, params: RenderSettings) =
        Preset(name, params.copy(renderMode = RenderMode.FlowAscii), PresetStore.CATEGORY_GLYPH)

    /** An oscilloscope preset: the picture as traces on a tube. */
    private fun scope(name: String, params: RenderSettings) =
        Preset(name, params.copy(renderMode = RenderMode.Oscilloscope), PresetStore.CATEGORY_GEOMETRY)

    /** A Shape Shifter preset: tones drawn as vector shapes on a grid. */
    private fun shapes(name: String, category: String, params: RenderSettings) =
        Preset(name, params.copy(renderMode = RenderMode.ShapeShifter), category)

    /** A track that sweeps one parameter across a whole loop and closes seamlessly. */
    private fun sweep(
        target: AnimTarget,
        from: Int,
        to: Int,
        curve: AnimCurve = AnimCurve.SINE,
        cycles: Int = 1,
    ) = AnimTrack(target = target, enabled = true, curve = curve, from = from, to = to, cycles = cycles)

    private fun animation(vararg tracks: AnimTrack, frames: Int = 30, fps: Int = 15) =
        AnimationParams(
            enabled = true,
            frames = frames,
            fps = fps,
            // Every target needs an entry, so the enabled ones are laid over the defaults.
            tracks = AnimTarget.entries.map { target ->
                tracks.firstOrNull { it.target == target }
                    ?: AnimTrack(target, from = target.min, to = target.max)
            },
        )

    /**
     * The curated library.
     *
     * Pixel dithering is what this application is, and the shelves are ordered the way the
     * work is: the canonical dithers first, then the two families that produce them —
     * error diffusion and ordered matrices — then patterns, print, geometry, colour,
     * damage, motion and layers. Glyph Art is one shelf near the end rather than the
     * premise of the list.
     *
     * Every entry names its render mode through [pixel] or [glyph]. Nothing here relies on
     * the constructor default, because that default answers a different question — what an
     * old file with no mode in it should be read as.
     *
     * Algorithm-comparison presets are not in here. They are [lab], and they are counted
     * separately, because a library that opens on eight versions of the same picture
     * describes a test bench rather than a product.
     */
    private val curated: List<Preset> = listOf(
        // --- classic dither ------------------------------------------------------
        // The canonical monochrome looks, each one a different answer to the same
        // question: two levels, and what to do with the error.
        pixel(
            "one bit",
            PresetStore.CATEGORY_CLASSIC,
            // Floyd-Steinberg at two levels and one cell per two source pixels: the
            // default meaning of "dithered" for forty years.
            RenderSettings(
                cellSize = 2,
                depth = 2,
                contrast = 1.15f,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFFFFFFF.toInt(),
                backgroundColor = 0xFF000000.toInt(),
            ),
        ),
        pixel(
            "soft grain",
            PresetStore.CATEGORY_CLASSIC,
            // Atkinson throws away a quarter of the error, which is why it keeps highlights
            // open and reads lighter than Floyd-Steinberg on the same picture.
            RenderSettings(
                cellSize = 2,
                depth = 3,
                ditherMode = DitherMode.ATKINSON,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFF3EDE2.toInt(),
                backgroundColor = 0xFF12100E.toInt(),
            ),
        ),
        pixel(
            "hard threshold",
            PresetStore.CATEGORY_CLASSIC,
            // No dither at all — the control case. Worth shipping because it is the thing
            // every other preset in this category is an improvement on.
            RenderSettings(
                cellSize = 3,
                depth = 2,
                contrast = 1.35f,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFFFFFFF.toInt(),
                backgroundColor = 0xFF000000.toInt(),
            ),
        ),
        pixel(
            "chalk on slate",
            PresetStore.CATEGORY_CLASSIC,
            RenderSettings(
                cellSize = 3,
                depth = 4,
                brightness = 0.05f,
                ditherMode = DitherMode.SMOOTH_DIFFUSE,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFE8E4D8.toInt(),
                backgroundColor = 0xFF23282B.toInt(),
            ),
        ),
        pixel(
            "blocks",
            PresetStore.CATEGORY_CLASSIC,
            // The coarse reduction with no algorithm in the way: each cell keeps its own
            // averaged colour. In pixel mode the cell size *is* the block size.
            RenderSettings(cellSize = 6, colorMode = ColorMode.SOURCE),
        ),

        // --- error diffusion -----------------------------------------------------
        pixel(
            "orb diffuse",
            PresetStore.CATEGORY_DIFFUSION,
            // Error pushed almost entirely downward travels in a line, which is what turns
            // grain into wavering vertical columns.
            RenderSettings(
                cellSize = 4,
                depth = 5,
                contrast = 1.55f,
                gamma = 1.15f,
                ditherMode = DitherMode.DIFFUSE_Y,
                ditherStrength = 100,
                serpentine = false,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
                backgroundColor = 0xFF000206.toInt(),
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.VALUE_NOISE,
                    scale = 9,
                    amount = 30,
                ),
                effects = EffectStack(
                    glow = GlowParams(
                        enabled = true,
                        threshold = 42,
                        thresholdSmoothing = 40,
                        radius = 110,
                        intensity = 700,
                        falloff = 14,
                    ),
                ),
                animation = animation(sweep(AnimTarget.DITHER_STRENGTH, 70, 100), frames = 36),
            ),
        ),
        pixel(
            "line diffuse",
            PresetStore.CATEGORY_DIFFUSION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                contrast = 1.45f,
                ditherMode = DitherMode.DIFFUSE_X,
                serpentine = false,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.amber",
                backgroundColor = 0xFF060200.toInt(),
                effects = EffectStack(
                    glow = GlowParams(enabled = true, radius = 70, intensity = 480, aspectRatio = 320),
                ),
                animation = animation(sweep(AnimTarget.DEPTH, 3, 9), frames = 30),
            ),
        ),
        pixel(
            "cracked",
            PresetStore.CATEGORY_DIFFUSION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                contrast = 1.3f,
                ditherMode = DitherMode.CRACKED_DIFFUSE,
                serpentine = false,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.acid",
            ),
        ),
        pixel(
            "edge keeper",
            PresetStore.CATEGORY_DIFFUSION,
            RenderSettings(
                cellSize = 3,
                depth = 5,
                contrast = 1.25f,
                ditherMode = DitherMode.CONTRAST_AWARE_Y,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
            ),
        ),
        pixel(
            "riemersma walk",
            PresetStore.CATEGORY_DIFFUSION,
            // Diffusion along a space-filling curve rather than along rows: the grain has
            // no direction at all, which is the one thing row-order diffusion cannot do.
            RenderSettings(
                cellSize = 2,
                depth = 4,
                contrast = 1.1f,
                ditherMode = DitherMode.RIEMERSMA,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFEDEDED.toInt(),
                backgroundColor = 0xFF0B0B0B.toInt(),
            ),
        ),
        pixel(
            "spiral grain",
            PresetStore.CATEGORY_DIFFUSION,
            RenderSettings(
                cellSize = 3,
                depth = 8,
                ditherMode = DitherMode.VORTEX_DIFFUSION,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                animation = animation(sweep(AnimTarget.DITHER_STRENGTH, 40, 100), frames = 34),
            ),
        ),

        // --- ordered -------------------------------------------------------------
        // A matrix decides the threshold, so the pattern is fixed and the picture moves
        // through it. Everything on this shelf is a matrix; nothing here diffuses error.
        pixel(
            "ash and mauve",
            PresetStore.CATEGORY_ORDERED,
            // The look of O EMPIRE! WARD OFF THY ROT: a Bayer screen over four dusty greys
            // sampled from the game, softened afterwards so the dots bloom, with a dark rim.
            RenderSettings(
                cellSize = 3,
                // Pinned because the default fills the 8192 px budget: a portrait photo then
                // exported at ~5700x7600 and the blur and vignette copies ran out of heap.
                pixelBlock = 4,
                depth = 6,
                contrast = 1.1f,
                ditherMode = DitherMode.BAYER_8,
                colorMode = ColorMode.PALETTE,
                // Four greys from the game's grey scenes, plus the two poppy reds of its
                // field scene: red source pixels come out red, everything else stays ash.
                paletteOverride = listOf(
                    0xFF2B272A.toInt(), 0xFF7A6A6E.toInt(), 0xFFB1AEAB.toInt(), 0xFFE8E8DA.toInt(),
                    0xFFE0242E.toInt(), 0xFFA8202A.toInt(),
                ),
                backgroundColor = 0xFF2B272A.toInt(),
                effects = EffectStack(
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 35, radius = 2),
                    postProcessing = PostProcessingParams(enabled = true, vignette = 60, grain = 8),
                ),
            ),
        ),
        pixel(
            "bayer eight",
            PresetStore.CATEGORY_ORDERED,
            RenderSettings(
                cellSize = 3,
                depth = 4,
                ditherMode = DitherMode.BAYER_8,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFFFFFFF.toInt(),
                backgroundColor = 0xFF07090C.toInt(),
            ),
        ),
        pixel(
            "bayer two",
            PresetStore.CATEGORY_ORDERED,
            // Four thresholds and two levels: the coarsest ordered dither there is, and the
            // one whose weave you can count.
            RenderSettings(
                cellSize = 4,
                depth = 2,
                contrast = 1.25f,
                ditherMode = DitherMode.BAYER_2,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFFFFFFF.toInt(),
                backgroundColor = 0xFF000000.toInt(),
            ),
        ),
        pixel(
            "blue noise",
            PresetStore.CATEGORY_ORDERED,
            // A matrix with no periodicity to find: the grain reads as texture rather than
            // as a screen, which is what makes it survive being printed or scaled.
            RenderSettings(
                cellSize = 2,
                depth = 3,
                ditherMode = DitherMode.BLUE_NOISE_32,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFFF2F2F2.toInt(),
                backgroundColor = 0xFF101010.toInt(),
            ),
        ),
        pixel(
            "clustered dot",
            PresetStore.CATEGORY_ORDERED,
            // Thresholds ordered outward from the centre of the tile, so ink lands in
            // growing blobs — the ordered matrix that behaves like a printing screen.
            RenderSettings(
                cellSize = 3,
                depth = 4,
                ditherMode = DitherMode.CLUSTER_8,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF14110E.toInt(),
                backgroundColor = 0xFFF1EADA.toInt(),
            ),
        ),
        pixel(
            "broken bayer",
            PresetStore.CATEGORY_ORDERED,
            // The pattern scale is pulled far past the cell size on purpose: this is the
            // documented way to drive an ordered dither until it visibly falls apart.
            RenderSettings(
                cellSize = 4,
                depth = 3,
                ditherMode = DitherMode.BAYER_8,
                ditherScale = 340,
                contrast = 1.4f,
                colorMode = ColorMode.SINGLE,
            ),
        ),

        // --- pattern -------------------------------------------------------------
        // A shape, repeated, carrying the tone: waves, rings, hatching, stipple. The
        // pattern is the subject as much as the picture is.
        pixel(
            "waveform",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 5,
                depth = 6,
                ditherMode = DitherMode.MOD_WAVE,
                modScale = 10,
                modAngle = 25,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
            ),
        ),
        pixel(
            "ripple",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 6,
                depth = 5,
                ditherMode = DitherMode.MOD_RINGS,
                modScale = 7,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.amber",
            ),
        ),
        pixel(
            "honeycomb",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.BEEHIVE,
                modScale = 6,
                contrast = 1.3f,
                colorMode = ColorMode.SINGLE,
            ),
        ),
        pixel(
            "orbital",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 5,
                depth = 4,
                ditherMode = DitherMode.MOD_ORB,
                modScale = 5,
                colorMode = ColorMode.SOURCE,
            ),
        ),
        pixel(
            "scanlines",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 5,
                depth = 4,
                ditherMode = DitherMode.MOD_LINES,
                modScale = 4,
                modAngle = 90,
                colorMode = ColorMode.SINGLE,
            ),
        ),
        // Pen, brush and burin. Few tones on purpose: these styles put their marks where
        // the picture is dark rather than shading it.
        pixel(
            "pen and ink",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 3,
                depth = 3,
                contrast = 1.3f,
                ditherMode = DitherMode.CROSSHATCH,
                modScale = 4,
                patternDensity = 35,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF1A1712.toInt(),
                backgroundColor = 0xFFE8DFC8.toInt(),
            ),
        ),
        pixel(
            "stipple study",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 3,
                depth = 3,
                contrast = 1.25f,
                ditherMode = DitherMode.STIPPLING,
                modScale = 3,
                patternDensity = 55,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF141414.toInt(),
                backgroundColor = 0xFFF0EBDD.toInt(),
            ),
        ),
        pixel(
            "copperplate",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                contrast = 1.4f,
                ditherMode = DitherMode.DOTTED_LINES,
                modScale = 5,
                modAngle = 20,
                patternDensity = 60,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ink",
            ),
        ),
        pixel(
            "survey map",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.TOPOGRAPHY,
                modScale = 10,
                patternDensity = 30,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF23331F.toInt(),
                backgroundColor = 0xFFEDE7D2.toInt(),
            ),
        ),
        pixel(
            "hot iron",
            PresetStore.CATEGORY_PATTERN,
            RenderSettings(
                cellSize = 5,
                depth = 8,
                ditherMode = DitherMode.MOD_ORB,
                modScale = 7,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                effects = EffectStack(
                    stars = DiffractionStarsParams(enabled = true, rays = 6, length = 80, threshold = 60),
                    glow = GlowParams(enabled = true, intensity = 600, radius = 140),
                ),
            ),
        ),
        pixel(
            "modulation cyberpunk",
            PresetStore.CATEGORY_PATTERN,
            // The plot is the picture here: lines carry the image and the glow gives the
            // dots their bloom, which is what stops a line drawing reading as a diagram.
            RenderSettings(
                cellSize = 3,
                depth = 4,
                contrast = 1.3f,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.neon",
                backgroundColor = 0xFF05000E.toInt(),
                effects = EffectStack(
                    modulationLines = ModulationLinesParams(
                        enabled = true,
                        lineSpacing = 7,
                        amplitude = 9,
                        dotSize = 2,
                        waveMix = 35,
                        waveSpeed = 40,
                        colorMode = ModulationColorMode.SOURCE,
                        backgroundColor = 0xFF05000E.toInt(),
                    ),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 6),
                    glow = GlowParams(
                        enabled = true,
                        threshold = 38,
                        radius = 90,
                        intensity = 520,
                    ),
                ),
            ),
        ),

        /*
         * The three styles that shipped without anything to find them by.
         *
         * A capability nobody can reach is not a feature, and in this app a preset is how one
         * becomes reachable. Each of these was chosen by rendering the candidates and comparing
         * them, not by picking plausible numbers.
         */
        pixel(
            "wave weave",
            PresetStore.CATEGORY_PATTERN,
            // Modulated diffusion: the surface bends the decision and the residue still spreads,
            // so the wave runs through the background and *around* the subject rather than over
            // it. A fine cell against a short period is what keeps the lines reading as lines.
            RenderSettings(
                cellSize = 2,
                depth = 3,
                ditherMode = DitherMode.WAVE_DIFFUSE,
                modScale = 6,
                patternDensity = 40,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
            ),
        ),
        pixel(
            "orb lattice",
            PresetStore.CATEGORY_PATTERN,
            /*
             * The same mechanism with the orb surface, and it needs a *coarse* period to work.
             * At a scale near the cell size the diffusion dissolves the lattice into grain —
             * pleasant, but it shows nothing. Well above it the orbs survive in the flat areas
             * while the subject keeps the smooth gradation the plain orb style cannot give it.
             */
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.ORB_DIFFUSE,
                modScale = 22,
                patternDensity = 40,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
            ),
        ),
        pixel(
            "loose weave",
            PresetStore.CATEGORY_PATTERN,
            // The imported-screen style on the woven screen it ships with, at two levels on
            // toned stock: the clearest way to see what a screen *is* before replacing it with
            // one of your own.
            RenderSettings(
                cellSize = 3,
                depth = 2,
                ditherMode = DitherMode.CUSTOM_SCREEN,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF101418.toInt(),
                backgroundColor = 0xFFEDE7DA.toInt(),
            ),
        ),
        pixel(
            "standing signal",
            PresetStore.CATEGORY_PATTERN,
            // The modulation lines drawn down the frame rather than across it. Brightness pushes
            // each column sideways, so the subject is read out of the displacement — the plot
            // needs the space between the columns, which is why nothing is dithered underneath.
            RenderSettings(
                cellSize = 2,
                depth = 6,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    modulationLines = ModulationLinesParams(
                        enabled = true,
                        axis = LineAxis.VERTICAL,
                        lineSpacing = 7,
                        amplitude = 14,
                        waveMix = 30,
                        dotSize = 2,
                        colorMode = ModulationColorMode.PHOSPHOR,
                    ),
                ),
            ),
        ),

        // --- print ---------------------------------------------------------------
        pixel(
            "newsprint",
            PresetStore.CATEGORY_PRINT,
            RenderSettings(
                cellSize = 4,
                colorMode = ColorMode.SOURCE,
                backgroundColor = 0xFFF2EDE0.toInt(),
                effects = EffectStack(
                    cmyk = CmykHalftoneParams(enabled = true, frequency = 5, blackInk = 90),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.PAPER_GRAIN,
                        blend = BlendMode.MULTIPLY,
                        intensity = 22,
                        scale = 2,
                    ),
                ),
            ),
        ),
        pixel(
            // Named for the process, not the machine: a coarse halftone screen on warm
            // uncoated stock. "spot colour print" is the same family done with real plate
            // misregistration rather than with a screen.
            "duplicator print",
            PresetStore.CATEGORY_PRINT,
            RenderSettings(
                cellSize = 5,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.sunset",
                backgroundColor = 0xFFF6F1E4.toInt(),
                effects = EffectStack(
                    cmyk = CmykHalftoneParams(enabled = true, frequency = 9, blackInk = 40, angle = 20),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.PAPER_FIBRE,
                        blend = BlendMode.MULTIPLY,
                        intensity = 30,
                    ),
                ),
            ),
        ),
        pixel(
            "process rosette",
            PresetStore.CATEGORY_PRINT,
            RenderSettings(
                cellSize = 4,
                depth = 5,
                ditherMode = DitherMode.PRINT_PATTERN,
                modScale = 8,
                colorMode = ColorMode.SOURCE,
            ),
        ),
        pixel(
            "press halftone",
            PresetStore.CATEGORY_PRINT,
            RenderSettings(
                cellSize = 3,
                depth = 4,
                contrast = 1.35f,
                ditherMode = DitherMode.BLOCK_TONE,
                modScale = 5,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF15130F.toInt(),
                backgroundColor = 0xFFE4DFD2.toInt(),
            ),
        ),
        pixel(
            "low bit halftone",
            PresetStore.CATEGORY_PRINT,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.BIT_TONE,
                modScale = 6,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.cga",
            ),
        ),
        pixel(
            "spot colour print",
            PresetStore.CATEGORY_PRINT,
            // Three plates, badly registered, on toned stock. The subtexture pass adds the
            // fibre the ink sits in; the node's own grain is the ink, not the paper.
            RenderSettings(
                cellSize = 5,
                depth = 4,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    spotPrint = SpotColorPrintParams(
                        enabled = true,
                        misalignment = 38,
                        inkOpacity = 82,
                        inkBleed = 28,
                        paperTextureBlend = 30,
                        inkCount = 3,
                        paperTone = 8,
                    ),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.PAPER_FIBRE,
                        intensity = 22,
                    ),
                ),
            ),
        ),
        pixel(
            "riso two colour",
            PresetStore.CATEGORY_PRINT,
            // Two inks and a clustered screen, which is what a duplicator can actually
            // lay down. The palette is the whole colour budget, so the dither is doing
            // the mixing.
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.CLUSTER_4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.riso-blue-red",
                backgroundColor = 0xFFF7F3E8.toInt(),
                effects = EffectStack(
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.PAPER_FIBRE,
                        blend = BlendMode.MULTIPLY,
                        intensity = 25,
                    ),
                ),
            ),
        ),

        // --- geometry ------------------------------------------------------------
        // Large cells and short ramps, because these flatten areas: a tessellation with
        // sixty tones is a photograph with visible seams, not a low-poly picture.
        pixel(
            "low poly",
            PresetStore.CATEGORY_GEOMETRY,
            // Palette rather than source colour, and that is not a taste decision: a
            // region style has no error to carry, so the colour path reduces each cell to
            // its nearest entry independently and the facets never appear at all. The
            // level a region averages to is what has to pick the colour.
            //
            // Coarser facets than "shifting facets", which animates the cut: at rest the
            // two would otherwise be one preset with a slider in a different place.
            RenderSettings(
                cellSize = 8,
                depth = 5,
                ditherMode = DitherMode.LOW_POLY,
                modScale = 16,
                patternDensity = 0,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ocean",
            ),
        ),
        pixel(
            "hex tiles",
            PresetStore.CATEGORY_GEOMETRY,
            RenderSettings(
                cellSize = 5,
                depth = 6,
                ditherMode = DitherMode.HEXA_POLY,
                modScale = 8,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.sunset",
            ),
        ),
        pixel(
            "camouflage",
            PresetStore.CATEGORY_GEOMETRY,
            RenderSettings(
                cellSize = 5,
                depth = 4,
                contrast = 1.2f,
                ditherMode = DitherMode.CAMO,
                modScale = 7,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.saltmarsh",
            ),
        ),
        pixel(
            "tile floor",
            PresetStore.CATEGORY_GEOMETRY,
            // Palette for the same reason as "low poly": a region style needs the level
            // to choose the colour, or the tiles it draws are thrown away.
            RenderSettings(
                cellSize = 5,
                depth = 6,
                ditherMode = DitherMode.SQUARE_MOSAIC,
                modScale = 6,
                patternDensity = 30,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.sepia",
            ),
        ),
        pixel(
            "dot grid",
            PresetStore.CATEGORY_GEOMETRY,
            RenderSettings(
                cellSize = 4,
                depth = 5,
                ditherMode = DitherMode.CIRCLE_GRID,
                modScale = 6,
                patternDensity = 70,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
            ),
        ),
        shapes(
            "bubble grid",
            PresetStore.CATEGORY_GEOMETRY,
            // Circles in the picture's own colours, larger where it is brighter and big enough
            // to overlap — bright shapes are drawn last, so highlights pile up on top.
            RenderSettings(
                cellSize = 16,
                colorMode = ColorMode.SOURCE,
                backgroundColor = 0xFF000000.toInt(),
                shapeShifter = ShapeShifterParams(bands = ShapeSets.named("circles"), shapeSize = 160, feather = 40),
            ),
        ),
        shapes(
            "star field",
            PresetStore.CATEGORY_GEOMETRY,
            // Plus signs in the shadows opening into four-point stars, with a glow over them.
            RenderSettings(
                cellSize = 14,
                colorMode = ColorMode.SOURCE,
                backgroundColor = 0xFF000000.toInt(),
                shapeShifter = ShapeShifterParams(bands = ShapeSets.named("4-point stars"), shapeSize = 110),
                effects = EffectStack(
                    glow = GlowParams(enabled = true, threshold = 40, radius = 60, intensity = 300),
                ),
            ),
        ),
        shapes(
            "cross stitch",
            PresetStore.CATEGORY_GEOMETRY,
            // Crosses for the dark half, pluses for the bright one, scattered where they meet
            // and coloured by tone from the palette.
            RenderSettings(
                cellSize = 14,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                shapeShifter = ShapeShifterParams(
                    bands = ShapeSets.named("plus and cross"),
                    shapeSize = 100,
                    feather = 60,
                    featherPattern = FeatherPattern.SCATTER,
                ),
            ),
        ),
        shapes(
            "ring halftone",
            PresetStore.CATEGORY_GEOMETRY,
            // Ink on paper: inverted so the dark tones get the big shapes, on an offset grid
            // like a printed screen.
            RenderSettings(
                cellSize = 12,
                invert = true,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF14110F.toInt(),
                backgroundColor = 0xFFF2EEE3.toInt(),
                shapeShifter = ShapeShifterParams(
                    bands = ShapeSets.named("rings"),
                    shapeSize = 105,
                    layout = ShapeLayout.OFFSET,
                ),
            ),
        ),
        shapes(
            "turning stars",
            PresetStore.CATEGORY_GEOMETRY,
            // Five-point stars in the picture's colours, all turning together; a full turn
            // lands on the frame it started from.
            RenderSettings(
                cellSize = 16,
                colorMode = ColorMode.SOURCE,
                backgroundColor = 0xFF000000.toInt(),
                shapeShifter = ShapeShifterParams(bands = ShapeSets.named("stars"), shapeSize = 105),
                animation = animation(
                    sweep(AnimTarget.SHAPE_ROTATION, 0, 359, AnimCurve.SAWTOOTH),
                    frames = 48,
                ),
            ),
        ),
        shapes(
            "custom bayer",
            PresetStore.CATEGORY_GEOMETRY,
            // A dither assembled from bands: Bayer tiles of rising density in one ink, so the
            // steps read as tone. Crisp, because a dither's pixels should not be smoothed.
            RenderSettings(
                cellSize = 8,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF46FF7A.toInt(),
                backgroundColor = 0xFF050B06.toInt(),
                shapeShifter = ShapeShifterParams(bands = ShapeSets.named("bayer tiles"), crispEdges = true),
            ),
        ),
        shapes(
            "red hearts",
            PresetStore.CATEGORY_GEOMETRY,
            // Each band in a colour of its own: grey dots growing into pale ones, and the
            // brightest tones turned into red pixel hearts.
            RenderSettings(
                cellSize = 14,
                backgroundColor = 0xFF000000.toInt(),
                shapeShifter = ShapeShifterParams(
                    bands = ShapeSets.named("red hearts"),
                    perBandColor = true,
                    feather = 35,
                ),
            ),
        ),

        // --- colour --------------------------------------------------------------
        // Palette work: a fixed set of inks, and the dither doing the mixing. In pixel
        // mode a palette *is* the level count, so the palette is the whole decision.
        pixel(
            "gameboy",
            PresetStore.CATEGORY_COLOR,
            RenderSettings(
                cellSize = 7,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.gameboy",
                backgroundColor = 0xFF0F380F.toInt(),
            ),
        ),
        pixel(
            "c64 lores",
            PresetStore.CATEGORY_COLOR,
            RenderSettings(
                cellSize = 5,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.c64",
            ),
        ),
        pixel(
            "vapour wash",
            PresetStore.CATEGORY_COLOR,
            // Source colour reduced to a palette through the diffusion pass rather than
            // indexed by luminance: the metric picks the entry, so the same picture in
            // OKLAB and in RGB comes back different.
            RenderSettings(
                cellSize = 3,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.vapor",
                colorDistance = ColorDistance.OKLAB,
            ),
        ),
        pixel(
            "oklab crush",
            PresetStore.CATEGORY_COLOR,
            // The colour-depth pass instead of a palette: levels per channel, chosen in a
            // perceptual space, with its own dither to hide the banding.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    colorDepth = ColorDepthParams(
                        enabled = true,
                        colorLevels = 4,
                        colorSpace = ColorDistance.OKLAB,
                        dithered = true,
                    ),
                ),
            ),
        ),
        pixel(
            "duotone grain",
            PresetStore.CATEGORY_COLOR,
            // Two colours and nothing between them, held together by grain rather than by
            // tone. The blue-noise pass is what makes that readable: the same look with
            // ordered dithering reads as a printed pattern, which is a different thing.
            RenderSettings(
                cellSize = 4,
                depth = 4,
                contrast = 1.25f,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    tint = TintParams(
                        enabled = true,
                        mode = TintMode.DUOTONE,
                        shadowColor = 0xFF120A2E.toInt(),
                        highlightColor = 0xFFFFD9A0.toInt(),
                        amount = 90,
                    ),
                    blueNoise = BlueNoiseDitherParams(
                        enabled = true,
                        levels = 3,
                        noiseScale = 2,
                        monochrome = true,
                    ),
                ),
            ),
        ),

        // --- glitch --------------------------------------------------------------
        // Damage, in the order it happens to a signal: in the transmission, on the tape,
        // in the file, on the glass.
        pixel(
            "glitch",
            PresetStore.CATEGORY_GLITCH,
            // The artefact-glitch style is the dither *being* the fault, rather than a
            // clean dither with damage applied afterwards.
            RenderSettings(
                cellSize = 4,
                depth = 4,
                contrast = 1.6f,
                ditherMode = DitherMode.ARTIFACT_GLITCH,
                colorMode = ColorMode.SINGLE,
                effects = EffectStack(
                    jpegGlitch = JpegGlitchParams(enabled = true, quality = 30, corruption = 45),
                ),
            ),
        ),
        pixel(
            "databend",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 5,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    chromatic = ChromaticParams(
                        enabled = true,
                        maxDisplace = 9,
                        waveAmplitude = 14,
                        waveFrequency = 20,
                        waveNoise = 35,
                    ),
                    jpegGlitch = JpegGlitchParams(enabled = true, quality = 18, corruption = 90),
                ),
            ),
        ),
        pixel(
            "crt",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 5,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                contrast = 1.25f,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true,
                        scanlines = 45,
                        scanlineSpacing = 3,
                        vignette = 35,
                        grain = 8,
                    ),
                    glow = GlowParams(enabled = true, intensity = 420, radius = 90),
                ),
            ),
        ),
        pixel(
            "crt arcade monitor",
            PresetStore.CATEGORY_GLITCH,
            // The order matters more than the values: interlace and the colour crush belong
            // to the signal, the warp belongs to the glass, so the warp has to come last or
            // the scan lines curve the wrong way — flat lines on curved glass.
            RenderSettings(
                cellSize = 4,
                depth = 6,
                contrast = 1.2f,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    interlace = InterlaceParams(enabled = true, shift = 3, density = 45),
                    colorDepth = ColorDepthParams(
                        enabled = true,
                        colorLevels = 5,
                        colorSpace = ColorDistance.OKLAB,
                        dithered = true,
                    ),
                    crtWarp = CrtWarpParams(
                        enabled = true,
                        warpCurvature = 30,
                        vignetteIntensity = 45,
                    ),
                ),
            ),
        ),
        pixel(
            "tape damage",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 4,
                depth = 5,
                ditherMode = DitherMode.ATKINSON_VHS,
                serpentine = false,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 9),
                ),
            ),
        ),
        pixel(
            "vhs dub",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 5,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 7, waveAmplitude = 9, waveNoise = 40),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.VHS_BANDS,
                        blend = BlendMode.SCREEN,
                        intensity = 35,
                        scale = 20,
                    ),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = -45, radius = 3),
                    postProcessing = PostProcessingParams(enabled = true, grain = 18, saturation = 130),
                ),
            ),
        ),
        pixel(
            "meltdown",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 4,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    pixelSort = PixelSortParams(
                        enabled = true,
                        thresholdLow = 18,
                        thresholdHigh = 72,
                        maxRun = 120,
                    ),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 5),
                    glow = GlowParams(enabled = true, intensity = 300, radius = 70),
                ),
            ),
        ),
        pixel(
            "shredder",
            PresetStore.CATEGORY_GLITCH,
            RenderSettings(
                cellSize = 4,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    sliceShift = SliceShiftParams(
                        enabled = true,
                        slices = 40,
                        maxOffset = 18,
                        density = 70,
                        colorShift = 45,
                    ),
                    pixelSort = PixelSortParams(enabled = true, axis = SortAxis.VERTICAL),
                    // Sorting after the slicing rather than before: the displaced edges
                    // become new run boundaries, which is what makes the tear read.
                    order = listOf(
                        EffectId.POST, EffectId.BLUR, EffectId.TINT, EffectId.CHROMATIC,
                        EffectId.GLITCH, EffectId.SLICE, EffectId.SORT, EffectId.STARS,
                        EffectId.SUBTEXTURE, EffectId.CMYK, EffectId.GLOW,
                    ),
                ),
            ),
        ),

        // --- motion --------------------------------------------------------------
        /*
         * These arrive with the animation already switched on and the track already
         * aimed, so applying one and pressing play is the whole interaction.
         *
         * Nearly all of them move a *pattern* rather than the picture, which is why they
         * loop cleanly: a sawtooth over the modulation phase travels exactly one period
         * and arrives where it began. The ones driving the second axis use a sine
         * instead, because that axis has no period to complete — a sawtooth there would
         * snap back visibly at the seam.
         */
        pixel(
            "drifting wave",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 5,
                depth = 5,
                ditherMode = DitherMode.MOD_WAVE,
                modScale = 9,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
                // A sawtooth over a whole period is what makes the pattern travel rather
                // than rock back and forth.
                animation = animation(sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH)),
            ),
        ),
        pixel(
            "bad signal",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 5,
                colorMode = ColorMode.SOURCE,
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.SCANLINE_ROLL,
                    scale = 6,
                    amount = 55,
                ),
                effects = EffectStack(
                    sliceShift = SliceShiftParams(enabled = true, slices = 18, maxOffset = 8, density = 35),
                ),
                animation = animation(sweep(AnimTarget.GLITCH_SEED, 1, 9999, AnimCurve.RANDOM)),
            ),
        ),
        pixel(
            "static storm",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                colorMode = ColorMode.SINGLE,
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.PLASMA,
                    scale = 10,
                    speed = 2,
                    amount = 70,
                ),
                animation = animation(sweep(AnimTarget.DITHER_STRENGTH, 30, 100)),
            ),
        ),
        pixel(
            "rolling glow",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 6,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                effects = EffectStack(
                    glow = GlowParams(enabled = true, intensity = 520, radius = 120, aspectRatio = 240),
                ),
                animation = animation(sweep(AnimTarget.GLOW_DIRECTION, 0, 359, AnimCurve.SAWTOOTH)),
            ),
        ),
        pixel(
            "phosphor relief",
            PresetStore.CATEGORY_MOTION,
            // A tube drawing a face in scan lines: fine modulation lines, the picture held dark so
            // the pattern stays loose, tinted before a strong glow, then phosphor triads and film
            // grain over the light. The order is the point — tint first, so the glow is blue.
            RenderSettings(
                cellSize = 3,
                depth = 2,
                ditherMode = DitherMode.MOD_LINES,
                modScale = 3,
                brightness = -0.3f,
                colorMode = ColorMode.SINGLE,
                effects = EffectStack(
                    tint = TintParams(enabled = true, color = 0xFF0048FF.toInt(), amount = 50),
                    glow = GlowParams(enabled = true, threshold = 47, radius = 200, intensity = 1000),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.RGB_TRIAD,
                        scale = 3,
                        intensity = 50,
                    ),
                    postProcessing = PostProcessingParams(enabled = true, grain = 45),
                ),
                animation = animation(sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH), frames = 36),
            ),
        ),
        pixel(
            "drifting orbs",
            PresetStore.CATEGORY_MOTION,
            // Soft orbs whose rows slide through each other while a faint noise moves under the
            // dither: kept weak on purpose, so the picture stays the subject and the motion is
            // only something you notice.
            RenderSettings(
                cellSize = 4,
                depth = 3,
                ditherMode = DitherMode.MOD_ORB,
                modScale = 7,
                orbCount = 3,
                orbSize = 70,
                orbIntensity = 40,
                colorMode = ColorMode.SINGLE,
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.BANDS,
                    scale = 16,
                    speed = 1,
                    amount = 8,
                ),
                effects = EffectStack(
                    tint = TintParams(enabled = true, color = 0xFF9CFF5A.toInt(), amount = 60),
                    glow = GlowParams(enabled = true, threshold = 19, radius = 78, intensity = 500),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.RGB_TRIAD,
                        scale = 3,
                        intensity = 40,
                    ),
                ),
                animation = animation(sweep(AnimTarget.ORB_OFFSET, -100, 100), frames = 40),
            ),
        ),
        pixel(
            "triad tube",
            PresetStore.CATEGORY_MOTION,
            // Phosphor triads built by the dither itself: a fine triad grid, its channels pulled
            // apart by a small chromatic shift (red out, blue in), glow last. The convergence
            // breathes and a faint random walk keeps the dots alive without moving the picture.
            RenderSettings(
                cellSize = 2,
                depth = 3,
                ditherMode = DitherMode.TRIAD_GRID,
                modScale = 3,
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.RANDOM_WALK,
                    scale = 24,
                    speed = 1,
                    amount = 6,
                ),
                effects = EffectStack(
                    chromatic = ChromaticParams(
                        enabled = true,
                        maxDisplace = 3,
                        redChannel = 100,
                        greenChannel = 30,
                        blueChannel = 0,
                    ),
                    glow = GlowParams(enabled = true, threshold = 10, radius = 60, intensity = 400),
                ),
                animation = animation(sweep(AnimTarget.CHROMATIC_OFFSET, 1, 6), frames = 36),
            ),
        ),
        pixel(
            "floating lights",
            PresetStore.CATEGORY_MOTION,
            // Light added before the dither, so the specks are drawn in the Bayer pattern itself:
            // many small faint ones, like dust in a beam, rising slowly one way and looping.
            RenderSettings(
                cellSize = 3,
                depth = 3,
                ditherMode = DitherMode.BAYER_8,
                brightness = -0.15f,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                lightOrbs = LightOrbsParams(
                    enabled = true,
                    count = 90,
                    size = 6,
                    intensity = 35,
                    random = 70,
                    direction = 250,
                ),
                animation = animation(
                    sweep(AnimTarget.LIGHT_ORB_DRIFT, -100, 100, AnimCurve.SAWTOOTH),
                    frames = 120,
                    fps = 10,
                ),
            ),
        ),
        pixel(
            "diagonal drift",
            PresetStore.CATEGORY_MOTION,
            // Soft slanted stripes slide under an Atkinson dither, kept faint on purpose: the
            // dots only lean as the stripes pass, the picture does not move.
            RenderSettings(
                cellSize = 3,
                depth = 3,
                ditherMode = DitherMode.ATKINSON,
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.DIAGONAL,
                    scale = 12,
                    speed = 1,
                    amount = 14,
                    angle = 35,
                ),
                // The stripes sway a little as they pass, which is the one motion this look needs.
                animation = animation(sweep(AnimTarget.TEMPORAL_ANGLE, 25, 45), frames = 36),
            ),
        ),
        pixel(
            "silver halide",
            PresetStore.CATEGORY_CLASSIC,
            // Floyd-Steinberg at several levels with film grain over it: the clumped kind, which
            // sits in the mid-tones and leaves the blacks and whites clean.
            RenderSettings(
                cellSize = 2,
                depth = 6,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true,
                        contrast = 110,
                        grain = 35,
                        grainKind = GrainKind.FILM,
                        vignette = 25,
                    ),
                ),
            ),
        ),
        pixel(
            "oil study",
            PresetStore.CATEGORY_CLASSIC,
            // A fine colour dither repainted in brush strokes: each dab leans towards one of the
            // dither's colours, turns along the edges and stops at them. A second pass with half
            // the brush goes back over the places that hold detail. Extract a palette from the
            // picture for its exact colours.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                effects = EffectStack(
                    paintStrokes = PaintStrokesParams(enabled = true, passes = 2),
                ),
            ),
        ),
        pixel(
            "flaking fresco",
            PresetStore.CATEGORY_CLASSIC,
            // The oil study left to age: after the brush, the borders between its colours fray
            // and chip, and flecks of the colour next door land where the paint has come off.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                effects = EffectStack(
                    paintStrokes = PaintStrokesParams(enabled = true, passes = 2, brushGrowth = 15),
                    stressRelief = StressReliefParams(enabled = true, edgeWear = 3),
                ),
            ),
        ),
        pixel(
            "y2k chrome",
            PresetStore.CATEGORY_COLOR,
            // Blue chrome off a 2000s CD cover: tones mapped onto polished metal, with a hard
            // four-point star on everything that shines.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = CHROME,
                contrast = 1.15f,
                // The bright band sits above the midtones, so only lit surfaces mirror and a face
                // keeps its drawing. Glow stays faint: more than this washed hands out to a blank.
                effects = EffectStack(
                    stars = DiffractionStarsParams(enabled = true, threshold = 86, rays = 4, length = 45, intensity = 450),
                    glow = GlowParams(enabled = true, threshold = 92, radius = 14, intensity = 110),
                ),
            ),
        ),
        pixel(
            "mcbling",
            PresetStore.CATEGORY_COLOR,
            // Mid-2000s glam: everything in sweet pinks, a star on every stone, soft all over.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = BONBON,
                saturation = 125,
                // Glow kept small: the pale pinks are close to white already.
                effects = EffectStack(
                    stars = DiffractionStarsParams(enabled = true, threshold = 84, rays = 4, length = 34, intensity = 420),
                    glow = GlowParams(enabled = true, threshold = 86, radius = 22, intensity = 110),
                ),
            ),
        ),
        pixel(
            "vaporwave",
            PresetStore.CATEGORY_COLOR,
            // Tones mapped onto pink and turquoise in a coarse ordered screen, on washed scan lines.
            RenderSettings(
                cellSize = 3,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = VAPOR,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, scanlines = 28, scanlineSpacing = 4),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 3),
                ),
            ),
        ),
        pixel(
            "old web",
            PresetStore.CATEGORY_COLOR,
            // A 1998 homepage: web-safe colours in a hard ordered screen, in chunky pixels, with a
            // glitter star on anything bright.
            RenderSettings(
                cellSize = 3,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = WEB_SAFE,
                saturation = 130,
                effects = EffectStack(
                    stars = DiffractionStarsParams(enabled = true, threshold = 82, rays = 4, length = 22, intensity = 480),
                ),
            ),
        ),
        pixel(
            "dreamcore",
            PresetStore.CATEGORY_COLOR,
            // A half-remembered place: too bright, a little too colourful, everything behind a
            // milky haze, with the faint lines and grain of an old tape.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                saturation = 140,
                // The pale tint is what makes the haze: it lifts black off the floor. Saturation is
                // pushed twice because the tint takes colour out again.
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true, exposure = 22, contrast = 88, saturation = 150, grain = 10, grainKind = GrainKind.FILM,
                        scanlines = 10, scanlineSpacing = 3,
                    ),
                    tint = TintParams(enabled = true, color = 0xFFF0D0FF.toInt(), amount = 17),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 2),
                    glow = GlowParams(enabled = true, threshold = 55, radius = 160, intensity = 170),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 35, radius = 2),
                ),
            ),
        ),
        pixel(
            "holo foil",
            PresetStore.CATEGORY_COLOR,
            // Tones mapped through a rainbow that repeats, with small stars where the foil catches.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = HOLO,
                effects = EffectStack(
                    stars = DiffractionStarsParams(enabled = true, threshold = 88, rays = 4, length = 26, intensity = 380),
                ),
            ),
        ),
        pixel(
            "analog film",
            PresetStore.CATEGORY_COLOR,
            // A print from a roll of film: grain, warm and a little soft, dark in the corners. The
            // red halo round the lights comes from Flaresmith's look of the same name.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                saturation = 110,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, grain = 28, grainKind = GrainKind.FILM, vignette = 28),
                    tint = TintParams(enabled = true, color = 0xFFFFB070.toInt(), amount = 10),
                    glow = GlowParams(enabled = true, threshold = 78, radius = 60, intensity = 220),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 15, radius = 2),
                ),
            ),
        ),
        pixel(
            "cyberpunk rain",
            PresetStore.CATEGORY_COLOR,
            // Dull blues in an ordered screen with the neon left bright, and every light pulled
            // sideways into the streak of a cinema lens.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = NEON_DUSK,
                contrast = 1.15f,
                effects = EffectStack(
                    tint = TintParams(enabled = true, color = 0xFF2060FF.toInt(), amount = 12),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 2),
                    glow = GlowParams(enabled = true, threshold = 70, radius = 110, intensity = 380, aspectRatio = 400),
                ),
            ),
        ),
        pixel(
            "ps1 horror",
            PresetStore.CATEGORY_COLOR,
            // Coarse pixels in the console's 4x4 screen, few and murky colours, and fog over all
            // of it: the town you should not have driven into.
            RenderSettings(
                cellSize = 4,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = MURK,
                saturation = 75,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, grain = 12, vignette = 45),
                    tint = TintParams(enabled = true, color = 0xFF8A9088.toInt(), amount = 22),
                ),
            ),
        ),
        pixel(
            "backrooms",
            PresetStore.CATEGORY_COLOR,
            // Found footage from a place with no exit: everything in stale yellows, soft and
            // noisy like a camcorder tape, darker towards the corners.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = STALE,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true, grain = 18, grainKind = GrainKind.FILM, vignette = 38,
                        scanlines = 14, scanlineSpacing = 3,
                    ),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 2),
                    glow = GlowParams(enabled = true, threshold = 72, radius = 70, intensity = 200),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 20, radius = 2),
                    interlace = InterlaceParams(enabled = true, shift = 1, density = 12, tearColor = 20),
                ),
            ),
        ),
        pixel(
            "game boy camera",
            PresetStore.CATEGORY_COLOR,
            // The camera cartridge rather than the console: smaller pixels than `gameboy`, hard
            // contrast, the four greens in an ordered screen, and sensor noise.
            RenderSettings(
                cellSize = 5,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.gameboy",
                contrast = 1.35f,
                backgroundColor = 0xFF0F380F.toInt(),
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, grain = 16),
                ),
            ),
        ),
        pixel(
            "akahana",
            PresetStore.CATEGORY_COLOR,
            // The game's colours in the game's 4x4 raster, with its warm lantern glow.
            RenderSettings(
                cellSize = 3,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = AKAHANA,
                effects = EffectStack(
                    tint = TintParams(enabled = true, color = 0xFFFF9E4D.toInt(), amount = 8),
                    glow = GlowParams(enabled = true, threshold = 70, radius = 80, intensity = 220),
                ),
            ),
        ),
        pixel(
            "analog horror",
            PresetStore.CATEGORY_GLITCH,
            // A recording nobody admits to having made: crushed to black and blood red, coarse,
            // on heavy scan lines, with the picture slipping sideways where the tape is worn.
            RenderSettings(
                cellSize = 3,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = BLOOD,
                contrast = 1.45f,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true, grain = 28, grainKind = GrainKind.FILM, vignette = 50,
                        scanlines = 32, scanlineSpacing = 3,
                    ),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 3),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 22, radius = 2),
                    sliceShift = SliceShiftParams(enabled = true, slices = 22, maxOffset = 7, density = 18),
                    interlace = InterlaceParams(enabled = true, shift = 2, density = 20, tearColor = 30),
                ),
            ),
        ),
        pixel(
            "giallo",
            PresetStore.CATEGORY_COLOR,
            // A 1970s Italian thriller, lit through gels: the shadows go deep blue and the lights
            // hard red, on grainy film, blooming.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                contrast = 1.25f,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, grain = 20, grainKind = GrainKind.FILM, vignette = 32),
                    tint = TintParams(
                        enabled = true, mode = TintMode.DUOTONE,
                        shadowColor = 0xFF06105A.toInt(), highlightColor = 0xFFFF3848.toInt(), amount = 72,
                    ),
                    glow = GlowParams(enabled = true, threshold = 65, radius = 70, intensity = 220),
                ),
            ),
        ),
        pixel(
            "synthwave",
            PresetStore.CATEGORY_COLOR,
            // An outrun sunset: night violet to orange, with the lights blooming like neon tube.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.oxide",
                paletteOverride = OUTRUN,
                contrast = 1.2f,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, scanlines = 18, scanlineSpacing = 3),
                    glow = GlowParams(enabled = true, threshold = 60, radius = 70, intensity = 420),
                ),
            ),
        ),
        pixel(
            "vhs night",
            PresetStore.CATEGORY_GLITCH,
            // A party filmed on tape: soft, grainy, on scan lines, the colours pulled apart, the
            // odd line torn, and every lamp smeared sideways into a trail.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = PAINT_BOX,
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true, saturation = 80, grain = 22, grainKind = GrainKind.FILM,
                        vignette = 30, scanlines = 40, scanlineSpacing = 3,
                    ),
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 5),
                    glow = GlowParams(enabled = true, threshold = 65, radius = 90, intensity = 450, aspectRatio = 320),
                    blurSharpen = BlurSharpenParams(enabled = true, amount = 25, radius = 2),
                    interlace = InterlaceParams(enabled = true, shift = 2, density = 25, tearColor = 40),
                ),
            ),
        ),
        pixel(
            "frutiger aero",
            PresetStore.CATEGORY_COLOR,
            // Glyphsmith's half of the look: saturated colour dithered into sky, water and grass,
            // with a soft bloom on the lights. The bubbles and sun rays come from Flaresmith.
            RenderSettings(
                cellSize = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SOURCE,
                paletteId = "palette.oxide",
                paletteOverride = AERO_BOX,
                saturation = 135,
                highlights = 60,
                effects = EffectStack(
                    glow = GlowParams(enabled = true, threshold = 70, radius = 50, intensity = 400),
                ),
            ),
        ),
        pixel(
            "modulation lines",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 5,
                depth = 5,
                ditherMode = DitherMode.MOD_LINES,
                modScale = 6,
                modAngle = 90,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 36,
                ),
            ),
        ),
        pixel(
            "waveform glitch",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 5,
                depth = 6,
                ditherMode = DitherMode.MOD_WAVE,
                modScale = 12,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    chromatic = ChromaticParams(
                        enabled = true,
                        maxDisplace = 6,
                        waveAmplitude = 18,
                        waveFrequency = 12,
                    ),
                    glow = GlowParams(enabled = true, intensity = 380, radius = 80),
                ),
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    sweep(AnimTarget.CHROMATIC_OFFSET, 2, 14),
                    frames = 36,
                ),
            ),
        ),
        pixel(
            "cyberpunk",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.neon",
                backgroundColor = 0xFF05000C.toInt(),
                temporal = TemporalParams(
                    enabled = true,
                    pattern = TemporalPattern.INTERFERENCE,
                    scale = 12,
                    amount = 40,
                ),
                effects = EffectStack(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 8),
                    sliceShift = SliceShiftParams(enabled = true, slices = 26, maxOffset = 10, density = 40),
                    glow = GlowParams(enabled = true, intensity = 560, radius = 100),
                ),
                animation = animation(sweep(AnimTarget.GLITCH_SEED, 1, 9999, AnimCurve.RANDOM)),
            ),
        ),
        pixel(
            "gemstone",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 5,
                depth = 6,
                ditherMode = DitherMode.MOD_ORB,
                modScale = 6,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.sunset",
                effects = EffectStack(
                    stars = DiffractionStarsParams(
                        enabled = true,
                        rays = 6,
                        length = 70,
                        threshold = 55,
                        intensity = 620,
                    ),
                    glow = GlowParams(enabled = true, intensity = 340, radius = 60),
                ),
                animation = animation(
                    sweep(AnimTarget.STARS_ANGLE, 0, 359, AnimCurve.SAWTOOTH),
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 36,
                ),
            ),
        ),
        pixel(
            "matrix particles",
            PresetStore.CATEGORY_MOTION,
            // The lines are the rain and the glitch is the transmission it is falling
            // through. The phase track is what actually moves it — the node reads the
            // clock, but a track makes the motion editable like every other animated
            // preset in here.
            RenderSettings(
                cellSize = 3,
                depth = 5,
                contrast = 1.35f,
                ditherMode = DitherMode.NONE,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF00FF41.toInt(),
                backgroundColor = 0xFF000000.toInt(),
                effects = EffectStack(
                    modulationLines = ModulationLinesParams(
                        enabled = true,
                        lineSpacing = 5,
                        amplitude = 14,
                        dotSize = 1,
                        waveMix = 70,
                        waveSpeed = 65,
                        colorMode = ModulationColorMode.PHOSPHOR,
                    ),
                    jpegGlitch = JpegGlitchParams(enabled = true, quality = 24, corruption = 55),
                ),
                animation = animation(sweep(AnimTarget.MODULATION_PHASE, 0, 100), frames = 30),
            ),
        ),
        pixel(
            "winding vortex",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 6,
                ditherMode = DitherMode.VORTEX,
                modScale = 9,
                patternDensity = 30,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.twilight",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    sweep(AnimTarget.PATTERN_DENSITY, 15, 55),
                    frames = 40,
                ),
            ),
        ),
        pixel(
            "breathing contours",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.TOPOGRAPHY,
                modScale = 12,
                patternDensity = 20,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.saltmarsh",
                animation = animation(sweep(AnimTarget.PATTERN_DENSITY, 5, 60), frames = 44),
            ),
        ),
        pixel(
            "ripple peaks",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 5,
                ditherMode = DitherMode.RADIAL_PEAKS,
                modScale = 7,
                patternDensity = 40,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 36,
                ),
            ),
        ),
        pixel(
            "moire drift",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 3,
                depth = 3,
                ditherMode = DitherMode.SINE_DISTORT,
                modScale = 5,
                patternDensity = 45,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.acid",
                animation = animation(sweep(AnimTarget.PATTERN_DENSITY, 30, 65), frames = 48),
            ),
        ),
        pixel(
            "waveform scan",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 5,
                ditherMode = DitherMode.WAVEFORM,
                modScale = 8,
                patternDensity = 50,
                modAngle = 90,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 36,
                ),
            ),
        ),
        pixel(
            "spinning burst",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.RADIAL_BURST,
                modScale = 6,
                patternDensity = 35,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ember",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 32,
                ),
            ),
        ),
        pixel(
            "tearing signal",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.GLITCH,
                modScale = 7,
                colorMode = ColorMode.SOURCE,
                effects = EffectStack(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 7),
                ),
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    sweep(AnimTarget.GLITCH_SEED, 1, 400, AnimCurve.RANDOM),
                    frames = 30,
                ),
            ),
        ),
        pixel(
            "crawling hatch",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 3,
                depth = 3,
                contrast = 1.3f,
                ditherMode = DitherMode.CROSSHATCH,
                modScale = 5,
                patternDensity = 40,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF1A1712.toInt(),
                backgroundColor = 0xFFE8DFC8.toInt(),
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 40,
                ),
            ),
        ),
        pixel(
            "pulsing stipple",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 3,
                depth = 3,
                ditherMode = DitherMode.STIPPLING,
                modScale = 4,
                patternDensity = 40,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.bone",
                animation = animation(sweep(AnimTarget.PATTERN_DENSITY, 20, 75), frames = 40),
            ),
        ),
        pixel(
            "rolling gridlock",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 4,
                depth = 4,
                ditherMode = DitherMode.GRIDLOCK,
                modScale = 7,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.neon-district",
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 34,
                ),
            ),
        ),
        /*
         * The one that animates a *choice* rather than a quantity. The second axis picks
         * which of the six triangle cuts is used, so a sawtooth across it steps through
         * every tessellation in turn and the whole surface re-facets at each step.
         */
        pixel(
            "shifting facets",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 6,
                depth = 5,
                ditherMode = DitherMode.LOW_POLY,
                modScale = 10,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.arctic",
                // A triangle rather than a sawtooth: the axis picks one of six cuts and
                // does not wrap, so it rides up through them and back down instead of
                // snapping from the last to the first in view.
                animation = animation(
                    sweep(AnimTarget.PATTERN_DENSITY, 0, 100, AnimCurve.TRIANGLE),
                    frames = 36,
                    fps = 8,
                ),
            ),
        ),

        /*
         * The one that animates the *glass* rather than the picture.
         *
         * Every other motion preset moves something inside the image — a phase, a density, a
         * seed. This one bends the whole frame: the curvature runs from flat to a full bulge
         * and back, so a straight edge bows outwards, the corners pull in, and the shadow in
         * them deepens as they go. Starting at zero is the point of it. The first frame is the
         * picture the user already has, and the second is visibly not, which is what makes the
         * control legible without reading anything.
         *
         * A sine rather than a ramp: a tube settling is a there-and-back movement, and it is
         * also the only shape that closes the loop, so an exported GIF does not snap at the
         * seam.
         *
         * The scanlines and the phosphor palette are held still on purpose. They set the scene
         * in the first frame; if they moved too there would be three things changing at once
         * and no way to tell which control did what.
         */
        pixel(
            "warming tube",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 3,
                depth = 6,
                ditherMode = DitherMode.BAYER_4,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                effects = EffectStack(
                    postProcessing = PostProcessingParams(
                        enabled = true,
                        scanlines = 55,
                        scanlineSpacing = 3,
                        vignette = 20,
                    ),
                    // Curvature starts flat and is driven; the corner shadow is held, so the
                    // darkening tracks the bulge instead of competing with it.
                    crtWarp = CrtWarpParams(
                        enabled = true,
                        warpCurvature = 0,
                        vignetteIntensity = 45,
                    ),
                ),
                animation = animation(sweep(AnimTarget.WARP_CURVATURE, 0, 85), frames = 36),
            ),
        ),

        /*
         * The lit reading of a modulation dither.
         *
         * `drifting wave` a few entries up is the same algorithm, the same palette and the same
         * swept phase, and looks nothing like this: at cell size 5 with no glow it is a coarse
         * matte pattern. Here the cell is fine enough that the wave reads as line work, and the
         * glow turns the lit parts into a source of light with the wave running through them.
         * Two presets earn their place when the mechanism is shared but the reading is not.
         *
         * A modulation dither decides each cell against a moving surface rather than a fixed
         * matrix, so the surface itself is visible wherever the image is dark: the background
         * fills with the wave instead of going empty, and the body is shaded by the same wave
         * it is drawn with. Sweeping the phase makes the whole field travel.
         *
         * A sawtooth, which is only safe because the phase is one of the cyclic targets — it
         * wraps inside the pattern, so running off the end of the range is the same as
         * starting again and the loop closes with no seam. Any non-cyclic target driven this
         * way would snap in plain sight.
         *
         * The glow is what makes it read as light rather than as a texture: with the
         * threshold low and the radius wide, the lit parts bloom into the dark ones and the
         * wave in between survives. Circular, so it stays cheap — a glow with a direction
         * rotates its buffer twice.
         */
        pixel(
            "signal bloom",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 2,
                depth = 6,
                ditherMode = DitherMode.MOD_WAVE,
                modScale = 9,
                patternDensity = 35,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.ice",
                effects = EffectStack(
                    glow = GlowParams(
                        enabled = true,
                        threshold = 20,
                        thresholdSmoothing = 30,
                        radius = 90,
                        intensity = 620,
                    ),
                ),
                animation = animation(
                    sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                    frames = 32,
                ),
            ),
        ),

        /*
         * The one that animates what the algorithm *sees*.
         *
         * Every other motion preset moves something the renderer draws — a phase, a density, a
         * curvature. This drives the source's own brightness, before the dither reads it, and
         * the difference is the whole point: the picture does not fade, it stops feeding the
         * algorithm. The first frame is empty, the figure assembles out of nothing, and on the
         * way back it collapses into the field it came from.
         *
         * It sweeps to +20 rather than to 0 so the peak lifts the background over the first
         * threshold too — at the top of the loop the empty field itself comes alive with grain,
         * which is what makes the collapse read as the dither switching off rather than as a
         * light being dimmed.
         *
         * Error diffusion on purpose. A flat field gives it no error to pass on, so "nothing"
         * really is nothing; an ordered matrix would keep drawing its own texture into the dark.
         */
        pixel(
            "forming",
            PresetStore.CATEGORY_MOTION,
            RenderSettings(
                cellSize = 3,
                depth = 4,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF33FF66.toInt(),
                backgroundColor = 0xFF04140A.toInt(),
                animation = animation(
                    sweep(AnimTarget.SOURCE_BRIGHTNESS, -100, 20),
                    frames = 36,
                ),
            ),
        ),

        // --- layered -------------------------------------------------------------
        // One image, two treatments, blended. A layer is a complete second set of
        // settings rendered at the same size, so what these demonstrate is the thing you
        // cannot get from any single dither: two screens at once.
        pixel(
            "two pass screen",
            PresetStore.CATEGORY_LAYERED,
            RenderSettings(
                cellSize = 6,
                depth = 3,
                ditherMode = DitherMode.CLUSTER_8,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF161310.toInt(),
                backgroundColor = 0xFFF2ECDD.toInt(),
                layers = listOf(
                    Layer(
                        name = "fine screen",
                        params = RenderSettings(
                            renderMode = RenderMode.PurePixel,
                            cellSize = 3,
                            depth = 3,
                            ditherMode = DitherMode.BAYER_8,
                            colorMode = ColorMode.SINGLE,
                            inkColor = 0xFF161310.toInt(),
                            backgroundColor = 0xFFF2ECDD.toInt(),
                        ),
                        blend = LayerBlend.MULTIPLY,
                        opacity = 60,
                    ),
                ),
            ),
        ),
        pixel(
            "offset plates",
            PresetStore.CATEGORY_LAYERED,
            // Two inks laid down slightly out of register — the misalignment is the
            // picture, so the layer offset carries the whole effect.
            RenderSettings(
                cellSize = 4,
                depth = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG,
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF1B3F8F.toInt(),
                backgroundColor = 0xFFF7F3E8.toInt(),
                layers = listOf(
                    Layer(
                        name = "second plate",
                        params = RenderSettings(
                            renderMode = RenderMode.PurePixel,
                            cellSize = 4,
                            depth = 2,
                            ditherMode = DitherMode.FLOYD_STEINBERG,
                            colorMode = ColorMode.SINGLE,
                            inkColor = 0xFFD2263F.toInt(),
                            backgroundColor = 0xFFF7F3E8.toInt(),
                        ),
                        blend = LayerBlend.MULTIPLY,
                        opacity = 70,
                        offsetX = 2,
                        offsetY = -2,
                    ),
                ),
            ),
        ),

        // --- glyph art -----------------------------------------------------------
        // The render module, not the premise. Each of these demonstrates a different
        // glyph capability rather than a different look: the ramp, its length, a
        // non-Latin set, the edge pass, injection, and colour on characters.
        scope(
            "oscilloscope",
            // The picture as a relief of green traces, nearer ones hiding the ones behind.
            RenderSettings(contrast = 1.3f, oscilloscope = OscilloscopeParams()),
        ),
        scope(
            "amber scope",
            // An older tube: amber, the traces closer and lower, no grid on the glass.
            RenderSettings(
                contrast = 1.3f,
                oscilloscope = OscilloscopeParams(phosphor = Phosphor.AMBER, spacing = 6, height = 22, graticule = false),
            ),
        ),
        scope(
            "pulsar",
            // White lines on black with hardly any glow: a plot of a signal rather than a tube.
            RenderSettings(
                contrast = 1.4f,
                oscilloscope = OscilloscopeParams(phosphor = Phosphor.WHITE, spacing = 10, height = 48, glow = 25, graticule = false),
            ),
        ),
        flow(
            "flow ascii",
            // Characters that lie along fingers, folds and faces, in the picture's own colours.
            RenderSettings(colorMode = ColorMode.SOURCE, contrast = 1.15f, flowAscii = FlowAsciiParams()),
        ),
        flow(
            "ascii fur",
            // Everything out of short hairs laid along the forms; made for animals and hair.
            RenderSettings(
                colorMode = ColorMode.SOURCE,
                contrast = 1.15f,
                // Lifted, so the coat of a dark animal is drawn rather than left a black shape.
                flowAscii = FlowAsciiParams(style = FlowStyle.FUR, liftDarks = 40),
            ),
        ),
        flow(
            "moonlit ascii",
            // One cold ink on a night-blue ground, with the strong edges drawn over once more.
            RenderSettings(
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF9CCBFF.toInt(),
                backgroundColor = 0xFF05080F.toInt(),
                contrast = 1.25f,
                flowAscii = FlowAsciiParams(outline = true, blackBelow = 8),
                effects = EffectStack(
                    glow = GlowParams(enabled = true, threshold = 55, radius = 40, intensity = 380),
                ),
            ),
        ),
        flow(
            "terminal hologram",
            // A projection rather than a print: cyan characters that glow, on faint scan lines.
            RenderSettings(
                colorMode = ColorMode.SINGLE,
                inkColor = 0xFF5CF4FF.toInt(),
                backgroundColor = 0xFF020B10.toInt(),
                contrast = 1.2f,
                // Dark areas stay empty: lifted, they filled with rows of noise that made the
                // whole projection restless.
                flowAscii = FlowAsciiParams(blackBelow = 10),
                effects = EffectStack(
                    postProcessing = PostProcessingParams(enabled = true, scanlines = 28, scanlineSpacing = 3),
                    glow = GlowParams(enabled = true, threshold = 45, radius = 50, intensity = 400),
                ),
            ),
        ),
        glyph(
            "terminal",
            PresetStore.CATEGORY_GLYPH,
            RenderSettings(charSetId = "ascii-standard-10", cellSize = 6, contrast = 1.2f),
        ),
        glyph(
            "phosphor",
            PresetStore.CATEGORY_GLYPH,
            // Seventy steps: the long ramp, where the mapping has enough glyphs to render
            // a tonal photograph rather than a poster.
            RenderSettings(
                charSetId = "ascii-standard-70",
                cellSize = 5,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                contrast = 1.3f,
            ),
        ),
        glyph(
            "tube glyphs",
            PresetStore.CATEGORY_GLYPH,
            // The new per-channel texture on characters: every glyph is lit through red, green
            // and blue phosphor stripes, then glows like a terminal on a real tube.
            RenderSettings(
                charSetId = "ascii-standard-10",
                cellSize = 6,
                contrast = 1.2f,
                effects = EffectStack(
                    tint = TintParams(enabled = true, color = 0xFF0048FF.toInt(), amount = 45),
                    glow = GlowParams(enabled = true, threshold = 40, radius = 120, intensity = 700),
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.RGB_TRIAD,
                        scale = 3,
                        intensity = 45,
                    ),
                ),
            ),
        ),
        glyph(
            "matrix reveal",
            PresetStore.CATEGORY_GLYPH,
            // Code rain that uncovers the picture: each column's bright head falls and leaves the
            // katakana portrait standing behind it, then the rain closes over it again.
            RenderSettings(
                charSetId = "lang-katakana",
                cellSize = 8,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                contrast = 1.4f,
                effects = EffectStack(
                    neoGlow = NeoGlowParams(
                        enabled = true,
                        length = 22,
                        stagger = 100,
                        intensity = 320,
                        rest = 30,
                        lane = 8,
                        color = 0xFF9CFF5A.toInt(),
                        runs = 3,
                        vary = 60,
                        falloff = 70,
                        head = 75,
                        gamma = 20,
                        reveal = true,
                    ),
                    glow = GlowParams(enabled = true, threshold = 55, radius = 40, intensity = 350),
                    // Neo glow before glow, so the bright heads are what blooms.
                    order = EffectId.entries.filter { it != EffectId.NEO_GLOW && it != EffectId.GLOW } +
                        EffectId.NEO_GLOW + EffectId.GLOW,
                ),
                animation = animation(sweep(AnimTarget.NEO_GLOW_OFFSET, 0, 100, AnimCurve.SAWTOOTH), frames = 40),
            ),
        ),
        glyph(
            "braille",
            PresetStore.CATEGORY_GLYPH,
            RenderSettings(charSetId = "braille-ramp", cellSize = 4, contrast = 1.4f, gamma = 1.2f),
        ),
        glyph(
            "matrix",
            PresetStore.CATEGORY_GLYPH,
            RenderSettings(
                charSetId = "lang-katakana",
                cellSize = 8,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                contrast = 1.5f,
            ),
        ),
        glyph(
            "monogram",
            PresetStore.CATEGORY_GLYPH,
            // Injection: characters of your own appended to the dense end of the ramp, so
            // the darkest parts of the picture are drawn with them.
            RenderSettings(
                charSetId = "ascii-standard-10",
                cellSize = 6,
                depth = 8,
                offset = 2,
                injection = "@#&%",
                contrast = 1.2f,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.bone",
            ),
        ),
        glyph(
            "ascii poster",
            PresetStore.CATEGORY_GLYPH,
            // Characters carrying the source colour, one hue per cell — the thing the
            // pixel path cannot do, because there is no character to colour.
            RenderSettings(
                charSetId = "ascii-standard-70",
                cellSize = 5,
                contrast = 1.15f,
                colorMode = ColorMode.SOURCE,
            ),
        ),
        glyph(
            "blueprint",
            PresetStore.CATEGORY_GLYPH,
            // Edge glyphs only: the Sobel pass picks a line character per cell and the
            // tonal mapping is switched off entirely.
            RenderSettings(
                charSetId = "line-box",
                cellSize = 6,
                edgeEnabled = true,
                edgeOnly = true,
                edgeThreshold = 18,
                edgeSetId = "box",
                inkColor = 0xFFCFE4FF.toInt(),
                backgroundColor = 0xFF0E2A4A.toInt(),
            ),
        ),
        glyph(
            "engraving",
            PresetStore.CATEGORY_GLYPH,
            // The same edge pass laid *over* a tonal ramp rather than replacing it.
            RenderSettings(
                charSetId = "ascii-standard-70",
                cellSize = 4,
                edgeEnabled = true,
                edgeThreshold = 30,
                edgeSetId = "ascii",
                contrast = 1.5f,
                inkColor = 0xFF1A1410.toInt(),
                backgroundColor = 0xFFEFE3C8.toInt(),
            ),
        ),
        glyph(
            "breathing",
            PresetStore.CATEGORY_GLYPH,
            // Animated glyph art: the ramp length itself is the track, so the picture is
            // redrawn with more and then fewer characters.
            RenderSettings(
                charSetId = "ascii-standard-70",
                cellSize = 5,
                colorMode = ColorMode.PALETTE,
                paletteId = "palette.phosphor",
                animation = animation(sweep(AnimTarget.DEPTH, 3, 40)),
            ),
        ),
    )

    /**
     * The test bench: one picture, one set of settings, and the algorithm as the only
     * variable.
     *
     * Separate from [curated] and counted separately, because these are not looks. They
     * are how you find out what a kernel actually does, and a curated library that opened
     * on six versions of the same grey gradient would describe a laboratory rather than
     * an application.
     *
     * Everything here is deliberately identical apart from [RenderSettings.ditherMode]: same
     * cell size, same level count, same neutral ink, no effects, no palette. That is the
     * whole point — a difference you can see is a difference the algorithm made.
     */
    private fun lab(name: String, mode: DitherMode) = pixel(
        name,
        PresetStore.CATEGORY_LAB,
        RenderSettings(
            cellSize = 2,
            depth = 6,
            ditherMode = mode,
            serpentine = true,
            colorMode = ColorMode.SINGLE,
            inkColor = 0xFFFFFFFF.toInt(),
            backgroundColor = 0xFF000000.toInt(),
        ),
    )

    private val laboratory: List<Preset> = listOf(
        lab("floyd-steinberg", DitherMode.FLOYD_STEINBERG),
        lab("atkinson", DitherMode.ATKINSON),
        lab("ostromoukhov", DitherMode.OSTROMOUKHOV),
        lab("shiau-fan", DitherMode.SHIAU_FAN),
        lab("dot diffusion", DitherMode.DOT_DIFFUSION),
        lab("fractal walk", DitherMode.FRACTAL_DIFFUSE),
    )

    /**
     * Declared last on purpose: a Kotlin object initialises its properties in source
     * order, so reading [curated] from above where it is defined would hand back null.
     */
    val builtIns: List<Preset> = curated + laboratory
}
