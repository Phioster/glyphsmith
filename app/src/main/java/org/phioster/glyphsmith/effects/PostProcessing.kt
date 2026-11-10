package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.effects.PixelOps.argb
import org.phioster.glyphsmith.effects.PixelOps.blueOf
import org.phioster.glyphsmith.effects.PixelOps.greenOf
import org.phioster.glyphsmith.effects.PixelOps.redOf
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import org.phioster.glyphsmith.core.image.Pixels

/** Grading and film-y damage, all in one pass over the pixels. */
object PostProcessing {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::postProcessing,
        { copy(postProcessing = it) },
        PostProcessingParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                grain = roll.random.nextInt(0, 40),
                vignette = roll.random.nextInt(0, 60),
                scanlines = roll.random.nextInt(0, 60),
            )
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    fun apply(source: Pixels, params: PostProcessingParams): Pixels {
        if (!params.enabled) return source

        val exposure = params.exposure / 100f * 255f
        val contrast = params.contrast / 100f
        val saturation = params.saturation / 100f
        val grain = params.grain / 100f
        val vignette = params.vignette / 100f
        val scanline = params.scanlines / 100f
        val spacing = params.scanlineSpacing.coerceAtLeast(1)
        val random = Random(params.seed)

        val centreX = source.width / 2f
        val centreY = source.height / 2f
        val maxDistance = sqrt(centreX * centreX + centreY * centreY).coerceAtLeast(1f)

        for (y in 0 until source.height) {
            // Scanlines darken whole rows, so the factor is computed once per row.
            val scanlineFactor = if (scanline > 0f && y % spacing == 0) 1f - scanline else 1f
            for (x in 0 until source.width) {
                val index = y * source.width + x
                val pixel = source.data[index]

                var r = redOf(pixel).toFloat()
                var g = greenOf(pixel).toFloat()
                var b = blueOf(pixel).toFloat()

                r += exposure
                g += exposure
                b += exposure

                r = (r - 128f) * contrast + 128f
                g = (g - 128f) * contrast + 128f
                b = (b - 128f) * contrast + 128f

                if (saturation != 1f) {
                    val grey = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    r = grey + (r - grey) * saturation
                    g = grey + (g - grey) * saturation
                    b = grey + (b - grey) * saturation
                }

                if (grain > 0f) {
                    // The uniform draw stays first and unconditional, so an existing render
                    // keeps its exact noise sequence.
                    val uniform = random.nextFloat() - 0.5f
                    val shape = if (params.grainKind == GrainKind.FILM) {
                        filmGrain(x, y, params.seed, r, g, b)
                    } else {
                        uniform
                    }
                    val noise = shape * 255f * grain
                    r += noise
                    g += noise
                    b += noise
                }

                var factor = scanlineFactor
                if (vignette > 0f) {
                    val dx = x - centreX
                    val dy = y - centreY
                    val distance = sqrt(dx * dx + dy * dy) / maxDistance
                    factor *= 1f - vignette * min(1f, distance * distance)
                }
                if (factor != 1f) {
                    r *= factor
                    g *= factor
                    b *= factor
                }

                source.data[index] = argb(PixelOps.alphaOf(pixel), r.toInt(), g.toInt(), b.toInt())
            }
        }
        return source
    }

    /**
     * Film grain in -0.5..0.5 for one pixel: fine noise mixed with a coarser clump, so it reads
     * as silver grains rather than as sensor noise, and weighted towards the mid-tones — film
     * has little grain left in a crushed black or a blown highlight.
     */
    @Suppress("LongParameterList")
    internal fun filmGrain(x: Int, y: Int, seed: Int, r: Float, g: Float, b: Float): Float {
        val fine = hash(x, y, seed) - 0.5f
        val clump = hash(x shr 1, y shr 1, seed + 101) - 0.5f
        val lum = ((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f).coerceIn(0f, 1f)
        val fromMid = 2f * lum - 1f
        val weight = 1f - 0.8f * fromMid * fromMid
        return (fine * 0.55f + clump * 0.75f).coerceIn(-0.5f, 0.5f) * weight
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
