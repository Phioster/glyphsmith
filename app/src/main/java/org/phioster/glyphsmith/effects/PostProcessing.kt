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
        PostProcessingParams::enabled,
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
                    val noise = (random.nextFloat() - 0.5f) * 255f * grain
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
}
