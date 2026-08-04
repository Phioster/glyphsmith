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
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.effects.CrtWarpParams
import org.phioster.glyphsmith.effects.ColorDepthParams
import org.phioster.glyphsmith.effects.SpotColorPrintParams
import org.phioster.glyphsmith.effects.ModulationColorMode
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
