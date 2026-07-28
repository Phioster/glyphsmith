package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable

/**
 * Controls for the directional bloom, named and ranged after Script Slayer's Epsilon Glow
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
    /** 0..50 px — how far red and blue are pulled apart. */
    val offset: Int = 6,
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
 * The effect chain, applied to the *rendered glyphs* in this fixed order:
 *
 * post processing → tint → chromatic → JPEG glitch → diffraction stars → glow
 *
 * The order is deliberate rather than configurable: grading before damage, damage before
 * light. Putting the glow last is what makes flares and glitch edges bloom instead of being
 * smeared by later passes. None of it touches the character grid, so `.txt` exports are
 * unaffected by anything in here.
 */
@Serializable
data class EffectStack(
    val postProcessing: PostProcessingParams = PostProcessingParams(),
    val tint: TintParams = TintParams(),
    val chromatic: ChromaticParams = ChromaticParams(),
    val jpegGlitch: JpegGlitchParams = JpegGlitchParams(),
    val stars: DiffractionStarsParams = DiffractionStarsParams(),
    val glow: GlowParams = GlowParams(),
) {
    val activeCount: Int
        get() = listOf(
            postProcessing.enabled,
            tint.enabled,
            chromatic.enabled,
            jpegGlitch.enabled,
            stars.enabled,
            glow.enabled,
        ).count { it }
}
