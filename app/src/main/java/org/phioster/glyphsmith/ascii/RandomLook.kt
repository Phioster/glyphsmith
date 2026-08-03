package org.phioster.glyphsmith.ascii

import kotlin.random.Random
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.effects.BlueNoiseDitherParams
import org.phioster.glyphsmith.effects.BlurSharpenParams
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.CmykHalftoneParams
import org.phioster.glyphsmith.effects.ColorDepthParams
import org.phioster.glyphsmith.effects.CrtWarpParams
import org.phioster.glyphsmith.effects.DiffractionStarsParams
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.effects.InterlaceParams
import org.phioster.glyphsmith.effects.JpegGlitchParams
import org.phioster.glyphsmith.effects.ModulationColorMode
import org.phioster.glyphsmith.effects.ModulationLinesParams
import org.phioster.glyphsmith.effects.PixelSortParams
import org.phioster.glyphsmith.effects.PostProcessingParams
import org.phioster.glyphsmith.effects.SliceShiftParams
import org.phioster.glyphsmith.effects.SpotColorPrintParams
import org.phioster.glyphsmith.effects.SubtextureParams
import org.phioster.glyphsmith.effects.TextureKind
import org.phioster.glyphsmith.effects.TintParams
import org.phioster.glyphsmith.render.RenderMode

/**
 * Surprise Me: a look rolled at random.
 *
 * A plain function of a base, a source of randomness and a mode, rather than something the
 * view model does to itself — the ranges below are the whole feature, and they are only worth
 * anything if they can be tested without a device.
 *
 * Deliberately narrow: a genuinely uniform roll over every parameter produces an empty or
 * unreadable image far more often than an interesting one, which makes the button useless.
 * Cell size, depth and the effect count are all kept inside the range that reliably yields
 * something worth looking at, and the effects are picked one at a time rather than all rolled
 * independently.
 *
 * Anything not named in the final copy is left as the caller had it. The roll moves the look,
 * not every knob in the app.
 */
object RandomLook {

    /**
     * Rolls a look on top of [base].
     *
     * [renderMode] defaults to [RenderMode.DEFAULT] — Pixel Dither — because this is the
     * general-purpose roll, and general-purpose means the general-purpose mode whatever the
     * session was sitting in. It stays a parameter so a glyph-flavoured roll is reachable
     * without a second copy of everything below.
     */
    fun roll(
        base: AsciiParams,
        random: Random = Random.Default,
        renderMode: RenderMode = RenderMode.DEFAULT,
    ): AsciiParams {
        val set = CharacterSets.all.random(random)
        val palette = Palettes.all.random(random)
        val dither = DitherMode.entries.random(random)

        var effects = EffectStack()
        repeat(random.nextInt(0, 3)) {
            effects = when (EffectId.entries.random(random)) {
                EffectId.POST -> effects.copy(
                    postProcessing = PostProcessingParams(
                        enabled = true,
                        grain = random.nextInt(0, 40),
                        vignette = random.nextInt(0, 60),
                        scanlines = random.nextInt(0, 60),
                    ),
                )

                EffectId.BLUR -> effects.copy(
                    blurSharpen = BlurSharpenParams(enabled = true, amount = random.nextInt(-70, 70)),
                )

                EffectId.TINT -> effects.copy(tint = TintParams(enabled = true, color = palette.colors.last()))
                EffectId.CHROMATIC -> effects.copy(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = random.nextInt(2, 18)),
                )

                EffectId.GLITCH -> effects.copy(
                    jpegGlitch = JpegGlitchParams(enabled = true, corruption = random.nextInt(20, 140)),
                )

                EffectId.SORT -> effects.copy(
                    pixelSort = PixelSortParams(
                        enabled = true,
                        thresholdLow = random.nextInt(10, 40),
                        thresholdHigh = random.nextInt(55, 90),
                    ),
                )

                EffectId.SLICE -> effects.copy(
                    sliceShift = SliceShiftParams(enabled = true, maxOffset = random.nextInt(4, 20)),
                )

                EffectId.INTERLACE -> effects.copy(
                    interlace = InterlaceParams(
                        enabled = true,
                        shift = random.nextInt(2, 18),
                        density = random.nextInt(30, 90),
                        tearColor = random.nextInt(0, 60),
                    ),
                )

                EffectId.STARS -> effects.copy(stars = DiffractionStarsParams(enabled = true))
                EffectId.SUBTEXTURE -> effects.copy(
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.entries.random(random),
                        intensity = random.nextInt(20, 60),
                    ),
                )

                EffectId.CMYK -> effects.copy(
                    cmyk = CmykHalftoneParams(enabled = true, frequency = random.nextInt(4, 14)),
                )

                EffectId.GLOW -> effects.copy(
                    glow = GlowParams(enabled = true, intensity = random.nextInt(200, 600)),
                )

                EffectId.MODULATION -> effects.copy(
                    modulationLines = ModulationLinesParams(
                        enabled = true,
                        lineSpacing = random.nextInt(4, 16),
                        amplitude = random.nextInt(3, 20),
                        colorMode = ModulationColorMode.entries.random(random),
                    ),
                )

                EffectId.PRINT -> effects.copy(
                    spotPrint = SpotColorPrintParams(
                        enabled = true,
                        misalignment = random.nextInt(10, 60),
                        inkCount = random.nextInt(2, 5),
                        seed = random.nextInt(1, 999),
                    ),
                )

                EffectId.DEPTH -> effects.copy(
                    colorDepth = ColorDepthParams(
                        enabled = true,
                        colorLevels = random.nextInt(2, 10),
                        colorSpace = ColorDistance.entries.random(random),
                    ),
                )

                EffectId.DITHER -> effects.copy(
                    blueNoise = BlueNoiseDitherParams(
                        enabled = true,
                        levels = random.nextInt(2, 6),
                        noiseScale = random.nextInt(1, 4),
                    ),
                )

                EffectId.WARP -> effects.copy(
                    crtWarp = CrtWarpParams(
                        enabled = true,
                        warpCurvature = random.nextInt(10, 60),
                        vignetteIntensity = random.nextInt(15, 60),
                    ),
                )
            }
        }

        return base.copy(
            renderMode = renderMode,
            charSetId = set.id,
            cellSize = random.nextInt(4, 13),
            depth = random.nextInt(3, 24),
            invert = random.nextBoolean(),
            ditherMode = dither,
            ditherStrength = random.nextInt(50, 101),
            modScale = random.nextInt(4, 16),
            modAngle = random.nextInt(0, 360),
            colorMode = ColorMode.entries.random(random),
            paletteId = palette.id,
            paletteOverride = emptyList(),
            paletteLocks = emptyList(),
            rampOverride = "",
            effects = effects,
        )
    }
}
