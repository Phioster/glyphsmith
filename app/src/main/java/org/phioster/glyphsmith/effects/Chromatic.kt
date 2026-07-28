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
 * Red and blue are sampled from opposite sides along [ChromaticParams.angle] while green
 * stays put — that asymmetry is what reads as a mistuned signal rather than a blur. The row
 * displacement runs on top, either as a clean sine or as per-row noise.
 */
object Chromatic {

    fun apply(source: Pixels, params: ChromaticParams): Pixels {
        if (!params.enabled) return source
        if (params.offset == 0 && params.waveAmplitude == 0) return source

        val radians = params.angle * PI / 180.0
        val dx = (cos(radians) * params.offset).toFloat()
        val dy = (sin(radians) * params.offset).toFloat()

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

        val out = IntArray(source.data.size)
        for (y in 0 until source.height) {
            val shift = rowShift[y]
            for (x in 0 until source.width) {
                val baseX = x + shift
                val red = sample(source, baseX + dx, y + dy)
                val green = sample(source, baseX.toFloat(), y.toFloat())
                val blue = sample(source, baseX - dx, y - dy)
                out[y * source.width + x] = argb(
                    alphaOf(green),
                    redOf(red),
                    greenOf(green),
                    blueOf(blue),
                )
            }
        }
        return Pixels(out, source.width, source.height)
    }

    /** Nearest-neighbour on purpose: interpolation softens exactly the hard edges we want. */
    private fun sample(source: Pixels, x: Float, y: Float): Int {
        val sx = x.roundToInt().coerceIn(0, source.width - 1)
        val sy = y.roundToInt().coerceIn(0, source.height - 1)
        return source.data[sy * source.width + sx]
    }
}
