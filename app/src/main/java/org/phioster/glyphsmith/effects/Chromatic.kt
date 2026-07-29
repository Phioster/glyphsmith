package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.effects.PixelOps.alphaOf
import org.phioster.glyphsmith.effects.PixelOps.argb
import org.phioster.glyphsmith.effects.PixelOps.blueOf
import org.phioster.glyphsmith.effects.PixelOps.greenOf
import org.phioster.glyphsmith.effects.PixelOps.redOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Channel separation plus row displacement.
 *
 * Each channel is sampled from its own position along [ChromaticParams.angle], scaled by
 * [ChromaticParams.maxDisplace]. That the three are independent is the point: pulling two of
 * them together and the third away gives yellow or cyan fringing, where a symmetric split
 * can only ever give red and blue.
 *
 * It is a real channel split, so a channel that is not present in the image does not move —
 * a pure blue picture ignores the red slider entirely, and that is correct rather than a
 * missing feature.
 *
 * The row displacement runs on top, either as a clean sine or as per-row noise.
 */
object Chromatic {

    fun apply(source: Pixels, params: ChromaticParams): Pixels {
        if (!params.enabled) return source
        if (params.maxDisplace == 0 && params.waveAmplitude == 0) return source

        val radians = params.angle * PI / 180.0
        val axisX = cos(radians).toFloat()
        val axisY = sin(radians).toFloat()

        // 50 is the middle, so a channel left there does not move at all.
        fun displacement(position: Int) = (position.coerceIn(0, 100) - 50) / 50f * params.maxDisplace
        val red = displacement(params.redChannel)
        val green = displacement(params.greenChannel)
        val blue = displacement(params.blueChannel)

        val random = Random(params.seed)
        val noiseAmount = params.waveNoise / 100f
        // One displacement per row, computed up front so every pixel in the row agrees.
        val rowShift = IntArray(source.height) { y ->
            if (params.waveAmplitude == 0) {
                0
            } else {
                val phase = 2.0 * PI * params.waveFrequency * y / source.height
                val wave = sin(phase).toFloat()
                val noise = (random.nextFloat() * 2f - 1f)
                val mixed = wave * (1f - noiseAmount) + noise * noiseAmount
                (mixed * params.waveAmplitude).roundToInt()
            }
        }

        val out = source.buffer()
        for (y in 0 until source.height) {
            val shift = rowShift[y]
            for (x in 0 until source.width) {
                val baseX = x + shift
                val r = sample(source, baseX + axisX * red, y + axisY * red)
                val g = sample(source, baseX + axisX * green, y + axisY * green)
                val b = sample(source, baseX + axisX * blue, y + axisY * blue)
                out[y * source.width + x] = argb(
                    alphaOf(g),
                    redOf(r),
                    greenOf(g),
                    blueOf(b),
                )
            }
        }
        return source.derive(out)
    }

    /** Nearest-neighbour on purpose: interpolation softens exactly the hard edges we want. */
    private fun sample(source: Pixels, x: Float, y: Float): Int {
        val sx = x.roundToInt().coerceIn(0, source.width - 1)
        val sy = y.roundToInt().coerceIn(0, source.height - 1)
        return source.data[sy * source.width + sx]
    }
}
