package org.phioster.glyphsmith.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls for the directional bloom, named and ranged after the reference app's glow
 * panel so the numbers mean the same thing in both places.
 */
@Serializable
data class GlowParams(
    val enabled: Boolean = false,
    /** 0..100 — luminance above which a pixel contributes to the glow. */
    val threshold: Int = 14,
    /** 0..100 — width of the soft edge around [threshold]. */
    val thresholdSmoothing: Int = 25,
    /** 0..200 — glow reach, in pixels of the rendered image. */
    val radius: Int = 200,
    /** Normalises the kernel so changing [radius] doesn't change apparent brightness. */
    val radiusCompensation: Boolean = true,
    /** 0..1000, where 500 is 1×. */
    val intensity: Int = 500,
    /** 0..400, where 100 is circular; higher stretches along [direction]. */
    val aspectRatio: Int = 100,
    /** 0..359 degrees. */
    val direction: Int = 0,
    /** 0..50, where 10 is n = 1.0 — the exponent in the 1/(dⁿ+ε) falloff. */
    val falloff: Int = 10,
    /** 0..100 — the ε that keeps the falloff finite at distance 0. */
    val epsilon: Int = 50,
    /** 0..500, where 150 is 1× — scales distance before the falloff is evaluated. */
    val distanceScale: Int = 150,
)

/** Exposure, contrast, saturation and the grain/vignette/scanline trio. */
@Serializable
data class PostProcessingParams(
    val enabled: Boolean = false,
    /** -100..100, where 0 leaves the image alone. */
    val exposure: Int = 0,
    /** 0..200, where 100 is 1×. */
    val contrast: Int = 100,
    /** 0..200, where 100 is 1× and 0 is greyscale. */
    val saturation: Int = 100,
    /** 0..100 — monochrome noise, the cheap way to make a clean render look photographed. */
    val grain: Int = 0,
    /** 0..100 — darkening towards the corners. */
    val vignette: Int = 0,
    /** 0..100 — strength of the CRT line darkening. */
    val scanlines: Int = 0,
    /** 1..16 — pixels between scanlines. */
    val scanlineSpacing: Int = 3,
    val seed: Int = 1,
)

enum class TintMode { TINT, DUOTONE }

/** A single colour wash, or a two-colour map from shadows to highlights. */
@Serializable
data class TintParams(
    val enabled: Boolean = false,
    val mode: TintMode = TintMode.TINT,
    val color: Int = 0xFF33FF66.toInt(),
    val shadowColor: Int = 0xFF12021F.toInt(),
    val highlightColor: Int = 0xFF7CF9FF.toInt(),
    /** 0..100 — how far towards the tint the pixel travels. */
    val amount: Int = 50,
)

/**
 * RGB channel separation plus a sine displacement of whole rows — the two halves of the
 * "broken signal" look, which are almost always used together.
 */
@Serializable
data class ChromaticParams(
    val enabled: Boolean = false,
    /**
     * 0..50 px — the furthest apart the channels can be pulled. It gates the three channel
     * positions entirely: at 0 they do nothing, however far they are moved.
     *
     * Still serialised as `offset`, which is what it was called when it only ever pulled red
     * and blue symmetrically. Renaming the field without renaming the key would have made
     * every saved preset lose its setting.
     */
    @SerialName("offset") val maxDisplace: Int = 6,
    /**
     * Where each channel sits along the displacement axis, 0..100 with 50 in the middle.
     *
     * The defaults are 100 / 50 / 0 rather than the tidier-looking 0 / 50 / 100 because
     * those are the positions that reproduce exactly what this effect did before the
     * channels were separable: red forward, green still, blue back. Aligning two of them
     * deliberately is what produces yellow or cyan fringing instead of a plain RGB split.
     */
    val redChannel: Int = 100,
    val greenChannel: Int = 50,
    val blueChannel: Int = 0,
    /** 0..359 degrees — the axis red and blue separate along. */
    val angle: Int = 0,
    /** 0..100 px — amplitude of the horizontal row displacement. */
    val waveAmplitude: Int = 0,
    /** 1..100 — how many wave cycles fit in the image height. */
    val waveFrequency: Int = 8,
    /** 0..100 — randomises row displacement instead of following the sine. */
    val waveNoise: Int = 0,
    val seed: Int = 1,
)

/**
 * Re-encodes the image as a JPEG, damages the compressed bytes, and decodes the wreckage.
 * The blocky smears this produces are the real thing, not a simulation of it.
 */
@Serializable
data class JpegGlitchParams(
    val enabled: Boolean = false,
    /** 1..100 — JPEG quality. Low values give the effect bigger blocks to chew on. */
    val quality: Int = 30,
    /** 0..500 — how many bytes of the compressed scan get corrupted. */
    val corruption: Int = 40,
    /** 0..100 — how far into the scan data corruption is allowed to start. */
    val startOffset: Int = 10,
    val seed: Int = 1,
)

/** Star flares on the brightest points — the anamorphic-lens look. */
@Serializable
data class DiffractionStarsParams(
    val enabled: Boolean = false,
    /** 0..100 — luminance above which a pixel throws a flare. */
    val threshold: Int = 70,
    /** 0..100 — softness of that threshold. */
    val thresholdSmoothing: Int = 15,
    /** 2..12 — number of rays. */
    val rays: Int = 4,
    /** 0..200 px — how far a ray reaches. */
    val length: Int = 60,
    /** 0..1000, where 500 is 1×. */
    val intensity: Int = 500,
    /** 0..359 degrees — rotation of the whole star. */
    val angle: Int = 0,
    /** 0..50, where 10 is n = 1.0 — falloff exponent along the ray. */
    val falloff: Int = 12,
)

/**
 * Blur and sharpen, on one slider.
 *
 * They are the same operation seen from both ends — sharpening is the blurred image
 * subtracted back out of the original — so splitting them into two effects would mean two
 * controls that can contradict each other.
 */
@Serializable
data class BlurSharpenParams(
    val enabled: Boolean = false,
    /** -100..100: negative sharpens, positive blurs, 0 does nothing. */
    val amount: Int = 0,
    /** Kernel radius in pixels, 1..12. */
    val radius: Int = 2,
)

/**
 * One slot in the effect chain. The enum order is the default order of the chain.
 *
 * On disk a slot is a stable id — see [EffectIds] — never the constant's own name.
 */
@Serializable(with = EffectIds::class)
enum class EffectId(val label: String) {
    POST("post"),
    BLUR("blur / sharpen"),
    TINT("tint"),
    CHROMATIC("chromatic"),
    GLITCH("jpeg glitch"),
    SORT("pixel sort"),
    SLICE("slice shift"),
    INTERLACE("interlace"),
    MODULATION("modulation lines"),
    STARS("stars"),
    SUBTEXTURE("subtexture"),
    PRINT("spot print"),
    CMYK("cmyk halftone"),
    DEPTH("colour depth"),
    DITHER("blue noise"),
    GLOW("glow"),
    WARP("crt warp"),
}

/**
 * The effect chain, applied to the *rendered glyphs*.
 *
 * The default order — grading before damage, damage before light — is still the one worth
 * reaching for, and glow last is what makes flares and glitch edges bloom instead of being
 * smeared by whatever follows. But the order is now [order] rather than the sequence of
 * calls, because which pass runs first changes the result far more than most of the sliders
 * do. A preset saved before this existed simply has no `order` field and deserialises to
 * the old fixed sequence.
 *
 * None of it touches the character grid, so `.txt` and `.svg` exports are unaffected.
 */
@Serializable
data class EffectStack(
    val postProcessing: PostProcessingParams = PostProcessingParams(),
    val tint: TintParams = TintParams(),
    val chromatic: ChromaticParams = ChromaticParams(),
    val jpegGlitch: JpegGlitchParams = JpegGlitchParams(),
    val stars: DiffractionStarsParams = DiffractionStarsParams(),
    val glow: GlowParams = GlowParams(),
    val blurSharpen: BlurSharpenParams = BlurSharpenParams(),
    val subtexture: SubtextureParams = SubtextureParams(),
    val cmyk: CmykHalftoneParams = CmykHalftoneParams(),
    val pixelSort: PixelSortParams = PixelSortParams(),
    val sliceShift: SliceShiftParams = SliceShiftParams(),
    val interlace: InterlaceParams = InterlaceParams(),
    val modulationLines: ModulationLinesParams = ModulationLinesParams(),
    val spotPrint: SpotColorPrintParams = SpotColorPrintParams(),
    val colorDepth: ColorDepthParams = ColorDepthParams(),
    val blueNoise: BlueNoiseDitherParams = BlueNoiseDitherParams(),
    val crtWarp: CrtWarpParams = CrtWarpParams(),
    val order: List<EffectId> = EffectId.entries.toList(),
) {
    fun enabledOf(id: EffectId): Boolean = when (id) {
        EffectId.POST -> postProcessing.enabled
        EffectId.BLUR -> blurSharpen.enabled
        EffectId.TINT -> tint.enabled
        EffectId.CHROMATIC -> chromatic.enabled
        EffectId.GLITCH -> jpegGlitch.enabled
        EffectId.SORT -> pixelSort.enabled
        EffectId.SLICE -> sliceShift.enabled
        EffectId.INTERLACE -> interlace.enabled
        EffectId.STARS -> stars.enabled
        EffectId.SUBTEXTURE -> subtexture.enabled
        EffectId.CMYK -> cmyk.enabled
        EffectId.GLOW -> glow.enabled
        EffectId.MODULATION -> modulationLines.enabled
        EffectId.PRINT -> spotPrint.enabled
        EffectId.DEPTH -> colorDepth.enabled
        EffectId.DITHER -> blueNoise.enabled
        EffectId.WARP -> crtWarp.enabled
    }

    /**
     * [order] with duplicates dropped and anything missing appended, so a preset written
     * before an effect existed still runs that effect rather than silently dropping it.
     */
    fun effectiveOrder(): List<EffectId> {
        val known = order.distinct()
        return known + EffectId.entries.filterNot { it in known }
    }

    val activeCount: Int get() = EffectId.entries.count { enabledOf(it) }

    /** Moves [id] one slot earlier or later; a no-op at either end. */
    fun reorder(id: EffectId, delta: Int): EffectStack {
        val current = effectiveOrder().toMutableList()
        val from = current.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in current.indices) return this
        current.removeAt(from)
        current.add(to, id)
        return copy(order = current)
    }
}
