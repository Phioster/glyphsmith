package org.phioster.glyphsmith.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.anim.AnimCurve
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.anim.AnimTrack
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.anim.TemporalPattern
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.DitherMode
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
import java.io.File

@Serializable
data class Preset(
    val name: String,
    val params: AsciiParams,
    /** Grouping in the picker. Defaulted so presets saved before this still load. */
    val category: String = PresetStore.CATEGORY_CUSTOM,
    val favourite: Boolean = false,
)

/**
 * Presets as one JSON file in app-private storage. Small enough that read-modify-write on
 * every change is cheaper than any incremental scheme, and it survives a schema change
 * because unknown keys are ignored and missing ones fall back to the defaults.
 */
class PresetStore(context: Context) {

    private val file = File(context.filesDir, "presets.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(): List<Preset> {
        if (!file.exists()) return builtIns
        return runCatching { json.decodeFromString<List<Preset>>(file.readText()) }
            .getOrElse { builtIns }
    }

    fun save(presets: List<Preset>) {
        runCatching { file.writeText(json.encodeToString(presets)) }
    }

    fun exportJson(): String = json.encodeToString(load())

    /**
     * Merges an exported file into the stored presets, matched by name — importing an
     * edited export updates those presets instead of producing duplicates. Returns null
     * when the text isn't a preset export at all.
     */
    fun importJson(text: String): List<Preset>? {
        val imported = runCatching { json.decodeFromString<List<Preset>>(text) }.getOrNull()
            ?: return null
        val names = imported.map { it.name.lowercase() }.toSet()
        val merged = load().filterNot { it.name.lowercase() in names } + imported
        save(merged)
        return merged
    }

    fun upsert(name: String, params: AsciiParams): List<Preset> {
        val trimmed = name.trim().ifEmpty { "untitled" }
        val existing = load().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val updated = load().filterNot { it.name.equals(trimmed, ignoreCase = true) } +
            Preset(
                name = trimmed,
                params = params,
                // Overwriting a preset keeps where it sits and whether it was starred;
                // re-saving a favourite should not quietly demote it.
                category = existing?.category ?: CATEGORY_CUSTOM,
                favourite = existing?.favourite ?: false,
            )
        save(updated)
        return updated
    }

    /** Flips the star on [name]. Favourites sort to the top of the picker. */
    fun toggleFavourite(name: String): List<Preset> {
        val updated = load().map {
            if (it.name == name) it.copy(favourite = !it.favourite) else it
        }
        save(updated)
        return updated
    }

    fun delete(name: String): List<Preset> {
        val updated = load().filterNot { it.name == name }
        save(updated)
        return updated
    }

    /**
     * Throws away the stored file so the shipped library comes back.
     *
     * Deleting rather than overwriting: [load] already falls back to [builtIns] when there
     * is no file, so there is only one place that decides what "fresh" means. This also
     * picks up any presets added by a later version, which writing today's list would not.
     */
    fun reset(): List<Preset> {
        runCatching { file.delete() }
        return builtIns
    }

    companion object {
        const val CATEGORY_CLASSIC = "CLASSIC"
        const val CATEGORY_DITHER = "DITHER"
        const val CATEGORY_PRINT = "PRINT"
        const val CATEGORY_MOTION = "MOTION"
        const val CATEGORY_HEAVY = "HEAVY"
        const val CATEGORY_SIGNATURE = "SIGNATURE"
        const val CATEGORY_CUSTOM = "CUSTOM"

        /** Picker order. Anything the user saves lands in CUSTOM, which sits last. */
        val categories = listOf(
            CATEGORY_CLASSIC,
            CATEGORY_DITHER,
            CATEGORY_PRINT,
            CATEGORY_SIGNATURE,
            CATEGORY_MOTION,
            CATEGORY_HEAVY,
            CATEGORY_CUSTOM,
        )

        private fun preset(name: String, category: String, params: AsciiParams) =
            Preset(name, params, category)

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
         * Shipped starting points.
         *
         * The CLASSIC nine are the originals, unchanged. Everything after them exists
         * because the features added since — modulation, temporal noise, subtexture, CMYK,
         * edges, the reorderable chain — had no starting point at all, so a look had to be
         * built from scratch every time. The MOTION set in particular is the point: they
         * arrive with animation already switched on and tracks already aimed, so applying
         * one and pressing play is the whole interaction.
         */
        val builtIns: List<Preset> = listOf(
            // --- classic ------------------------------------------------------------
            preset(
                "terminal", CATEGORY_CLASSIC,
                AsciiParams(charSetId = "ascii-standard-10", cellSize = 6, contrast = 1.2f),
            ),
            preset(
                "phosphor", CATEGORY_CLASSIC,
                AsciiParams(
                    charSetId = "ascii-standard-70",
                    cellSize = 5,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    contrast = 1.3f,
                ),
            ),
            preset(
                "blocks", CATEGORY_CLASSIC,
                AsciiParams(charSetId = "block-shade", cellSize = 6, colorMode = ColorMode.SOURCE),
            ),
            preset(
                "braille", CATEGORY_CLASSIC,
                AsciiParams(charSetId = "braille-ramp", cellSize = 4, contrast = 1.4f, gamma = 1.2f),
            ),
            preset(
                "matrix", CATEGORY_CLASSIC,
                AsciiParams(
                    charSetId = "lang-katakana",
                    cellSize = 8,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    contrast = 1.5f,
                ),
            ),
            preset(
                "gameboy", CATEGORY_CLASSIC,
                AsciiParams(
                    charSetId = "geo-squares",
                    cellSize = 7,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "gameboy",
                    backgroundColor = 0xFF0F380F.toInt(),
                ),
            ),
            preset(
                "glitch", CATEGORY_CLASSIC,
                AsciiParams(charSetId = "block-quadrant", cellSize = 5, offset = 4, contrast = 1.6f),
            ),
            preset(
                "databend", CATEGORY_CLASSIC,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    colorMode = ColorMode.SOURCE,
                    effects = EffectStack(
                        chromatic = ChromaticParams(
                            enabled = true,
                            offset = 9,
                            waveAmplitude = 14,
                            waveFrequency = 20,
                            waveNoise = 35,
                        ),
                        jpegGlitch = JpegGlitchParams(enabled = true, quality = 18, corruption = 90),
                    ),
                ),
            ),
            preset(
                "crt", CATEGORY_CLASSIC,
                AsciiParams(
                    charSetId = "ascii-standard-70",
                    cellSize = 5,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
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

            // --- dither -------------------------------------------------------------
            preset(
                "waveform", CATEGORY_DITHER,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    depth = 6,
                    ditherMode = DitherMode.MOD_WAVE,
                    modScale = 10,
                    modAngle = 25,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ice",
                ),
            ),
            preset(
                "ripple", CATEGORY_DITHER,
                AsciiParams(
                    charSetId = "geo-circles",
                    cellSize = 6,
                    depth = 5,
                    ditherMode = DitherMode.MOD_RINGS,
                    modScale = 7,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "amber",
                ),
            ),
            preset(
                "honeycomb", CATEGORY_DITHER,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 4,
                    depth = 4,
                    ditherMode = DitherMode.BEEHIVE,
                    modScale = 6,
                    contrast = 1.3f,
                ),
            ),
            preset(
                "orbital", CATEGORY_DITHER,
                AsciiParams(
                    charSetId = "geo-circles",
                    cellSize = 5,
                    depth = 4,
                    ditherMode = DitherMode.MOD_ORB,
                    modScale = 5,
                    colorMode = ColorMode.SOURCE,
                ),
            ),
            preset(
                "broken bayer", CATEGORY_DITHER,
                // The pattern scale is pulled far past the cell size on purpose: this is the
                // documented way to drive an ordered dither until it visibly falls apart.
                AsciiParams(
                    charSetId = "ascii-standard-10",
                    cellSize = 4,
                    depth = 3,
                    ditherMode = DitherMode.BAYER_8,
                    ditherScale = 340,
                    contrast = 1.4f,
                ),
            ),
            preset(
                "scanlines", CATEGORY_DITHER,
                AsciiParams(
                    charSetId = "block-horizontal",
                    cellSize = 5,
                    depth = 4,
                    ditherMode = DitherMode.MOD_LINES,
                    modScale = 4,
                    modAngle = 90,
                ),
            ),

            // --- print --------------------------------------------------------------
            preset(
                "newsprint", CATEGORY_PRINT,
                AsciiParams(
                    charSetId = "ascii-standard-10",
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
            preset(
                "risograph", CATEGORY_PRINT,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "sunset",
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
            preset(
                "blueprint", CATEGORY_PRINT,
                AsciiParams(
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
            preset(
                "engraving", CATEGORY_PRINT,
                AsciiParams(
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

            // --- motion -------------------------------------------------------------
            preset(
                "drifting wave", CATEGORY_MOTION,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    depth = 5,
                    ditherMode = DitherMode.MOD_WAVE,
                    modScale = 9,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ice",
                    // A sawtooth over a whole period is what makes the pattern travel rather
                    // than rock back and forth.
                    animation = animation(sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH)),
                ),
            ),
            preset(
                "bad signal", CATEGORY_MOTION,
                AsciiParams(
                    charSetId = "block-quadrant",
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
            preset(
                "breathing", CATEGORY_MOTION,
                AsciiParams(
                    charSetId = "ascii-standard-70",
                    cellSize = 5,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    animation = animation(sweep(AnimTarget.DEPTH, 3, 40)),
                ),
            ),
            preset(
                "static storm", CATEGORY_MOTION,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 4,
                    depth = 4,
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
            preset(
                "rolling glow", CATEGORY_MOTION,
                AsciiParams(
                    charSetId = "geo-circles",
                    cellSize = 6,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ember",
                    effects = EffectStack(
                        glow = GlowParams(enabled = true, intensity = 520, radius = 120, aspectRatio = 240),
                    ),
                    animation = animation(sweep(AnimTarget.GLOW_DIRECTION, 0, 359, AnimCurve.SAWTOOTH)),
                ),
            ),

            // --- signature ----------------------------------------------------------
            // Modelled on the looks Studio AAA show in their public preview clips, not on
            // their presets — those are not published, and neither is the maths behind the
            // styles. Each of these is this app's own approximation of a look, built from
            // controls that already existed, and each one animates so that applying it and
            // pressing play is the whole interaction.
            preset(
                "orb diffuse", CATEGORY_SIGNATURE,
                // Their "Orb Diffuse Y": a figure resolved into wavering vertical columns of
                // dots with bright blooming points scattered through it. The vertical run is
                // DIFFUSE_Y — error pushed almost entirely downward travels in a line, which
                // is what turns grain into streaks.
                AsciiParams(
                    charSetId = "geo-circles",
                    cellSize = 4,
                    depth = 5,
                    contrast = 1.55f,
                    gamma = 1.15f,
                    ditherMode = DitherMode.DIFFUSE_Y,
                    ditherStrength = 100,
                    serpentine = false,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ice",
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
            preset(
                "line diffuse", CATEGORY_SIGNATURE,
                AsciiParams(
                    charSetId = "misc-dots",
                    cellSize = 4,
                    depth = 4,
                    contrast = 1.45f,
                    ditherMode = DitherMode.DIFFUSE_X,
                    serpentine = false,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "amber",
                    backgroundColor = 0xFF060200.toInt(),
                    effects = EffectStack(
                        glow = GlowParams(enabled = true, radius = 70, intensity = 480, aspectRatio = 320),
                    ),
                    animation = animation(sweep(AnimTarget.DEPTH, 3, 9), frames = 30),
                ),
            ),
            preset(
                "modulation lines", CATEGORY_SIGNATURE,
                AsciiParams(
                    charSetId = "block-horizontal",
                    cellSize = 5,
                    depth = 5,
                    ditherMode = DitherMode.MOD_LINES,
                    modScale = 6,
                    modAngle = 90,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    animation = animation(
                        sweep(AnimTarget.MOD_PHASE, 0, 100, AnimCurve.SAWTOOTH),
                        frames = 36,
                    ),
                ),
            ),
            preset(
                "waveform", CATEGORY_SIGNATURE,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    depth = 6,
                    ditherMode = DitherMode.MOD_WAVE,
                    modScale = 12,
                    colorMode = ColorMode.SOURCE,
                    effects = EffectStack(
                        chromatic = ChromaticParams(
                            enabled = true,
                            offset = 6,
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
            preset(
                "cyberpunk", CATEGORY_SIGNATURE,
                AsciiParams(
                    charSetId = "block-quadrant",
                    cellSize = 4,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "neon",
                    backgroundColor = 0xFF05000C.toInt(),
                    temporal = TemporalParams(
                        enabled = true,
                        pattern = TemporalPattern.INTERFERENCE,
                        scale = 12,
                        amount = 40,
                    ),
                    effects = EffectStack(
                        chromatic = ChromaticParams(enabled = true, offset = 8),
                        sliceShift = SliceShiftParams(enabled = true, slices = 26, maxOffset = 10, density = 40),
                        glow = GlowParams(enabled = true, intensity = 560, radius = 100),
                    ),
                    animation = animation(sweep(AnimTarget.GLITCH_SEED, 1, 9999, AnimCurve.RANDOM)),
                ),
            ),
            preset(
                "gemstone", CATEGORY_SIGNATURE,
                AsciiParams(
                    charSetId = "geo-diamonds",
                    cellSize = 5,
                    depth = 6,
                    ditherMode = DitherMode.MOD_ORB,
                    modScale = 6,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "sunset",
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

            // --- heavy --------------------------------------------------------------
            preset(
                "meltdown", CATEGORY_HEAVY,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 4,
                    colorMode = ColorMode.SOURCE,
                    effects = EffectStack(
                        pixelSort = PixelSortParams(
                            enabled = true,
                            thresholdLow = 18,
                            thresholdHigh = 72,
                            maxRun = 120,
                        ),
                        chromatic = ChromaticParams(enabled = true, offset = 5),
                        glow = GlowParams(enabled = true, intensity = 300, radius = 70),
                    ),
                ),
            ),
            preset(
                "shredder", CATEGORY_HEAVY,
                AsciiParams(
                    charSetId = "block-quadrant",
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
            preset(
                "vhs dub", CATEGORY_HEAVY,
                AsciiParams(
                    charSetId = "ascii-standard-10",
                    cellSize = 5,
                    colorMode = ColorMode.SOURCE,
                    effects = EffectStack(
                        chromatic = ChromaticParams(enabled = true, offset = 7, waveAmplitude = 9, waveNoise = 40),
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
            preset(
                "hot iron", CATEGORY_HEAVY,
                AsciiParams(
                    charSetId = "block-shade",
                    cellSize = 5,
                    depth = 8,
                    ditherMode = DitherMode.MOD_ORB,
                    modScale = 7,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ember",
                    effects = EffectStack(
                        stars = DiffractionStarsParams(enabled = true, rays = 6, length = 80, threshold = 60),
                        glow = GlowParams(enabled = true, intensity = 600, radius = 140),
                    ),
                ),
            ),
        )
    }
}
