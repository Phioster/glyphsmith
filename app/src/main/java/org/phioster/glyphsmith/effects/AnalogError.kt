package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * A colour signal sent down a bad cable and decoded by a receiver that has lost its lock.
 *
 * Every line is turned into what analogue colour television sent: brightness, with the colour
 * riding on a fine carrier wave. Then the two ends are pulled apart on purpose and the line is
 * decoded again. Nothing is painted on: the rainbow bands are the picture's own brightness read
 * as colour, which is why they follow its shapes.
 */
@Serializable
data class AnalogErrorParams(
    val enabled: Boolean = false,
    /** 0..100 — how far the whole fault is turned up. */
    val amount: Int = 80,
    /** 0..100 — how much brightness leaks into colour: the strength of the rainbows. */
    val rainbow: Int = 50,
    /** 0..100 — how far the receiver's carrier runs off; higher means finer bands. */
    val detune: Int = 50,
    /** 0..100 — how far bright parts of a line pull it sideways. */
    val bend: Int = 50,
    /** 0..100 — how hard the lines in the torn band jump. */
    val tear: Int = 50,
    /** 0..100 — how far colour runs along a line. */
    val smear: Int = 50,
    /** 0..100 — how far the fault ebbs away and returns over a loop; 0 holds it steady. */
    val swell: Int = 0,
    val seed: Int = 1,
)

object AnalogError {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::analogError,
        { copy(analogError = it) },
        AnalogErrorParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                amount = roll.random.nextInt(40, 100),
                detune = roll.random.nextInt(20, 90),
                tear = roll.random.nextInt(0, 80),
                seed = roll.random.nextInt(1, 999),
            )
        },
    ) { pixels, params, ctx -> apply(pixels, params, ctx.time) }

    /** The carrier's period is this share of the width, so preview and export show the same bands. */
    private const val CYCLES_ACROSS = 120
    private const val MIN_PERIOD = 3

    /** Lengths below are in quarter periods of the carrier: the unit the look was tuned in. */
    private const val QUARTERS = 4f
    private const val WIDE_QUARTERS = 25f
    private const val SMEAR_QUARTERS = 18f
    private const val TEAR_QUARTERS = 140f
    private const val BEND_QUARTERS = 14f
    private const val TEAR_ROWS = 3
    private const val TEAR_WIDTH = 0.09f
    private const val STEPS_PER_LOOP = 24
    private const val FULL_AMOUNT = 80f
    private const val CALM = 0.04f
    private const val TWO_PI = (2 * PI).toFloat()

    fun apply(source: Pixels, params: AnalogErrorParams, time: Float = 0f): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || params.amount <= 0 || w < MIN_PERIOD * 4 || h < 2) return source

        val period = (w / CYCLES_ACROSS).coerceAtLeast(MIN_PERIOD)
        val quarter = period / QUARTERS
        val lineRows = quarter.roundToInt().coerceAtLeast(1)
        val t = time - floor(time)

        // Loudest at the start of the loop, so a still shows the fault rather than its absence.
        val swell = params.swell.coerceIn(0, 100) / 100f
        val pulse = CALM + (1f - CALM) * (0.5f + 0.5f * cos(TWO_PI * t)).pow(1.5f)
        val env = params.amount / FULL_AMOUNT * (1f - swell + swell * pulse)

        val leak = params.rainbow / 100f * 0.9f * env
        val detune = params.detune / 100f * 0.32f * minOf(1f, (env * 1.3f).pow(2))
        val bend = params.bend / 100f * BEND_QUARTERS * quarter * env
        val tear = params.tear / 100f * TEAR_QUARTERS * quarter * env
        val smear = (params.smear / 100f * SMEAR_QUARTERS * quarter).roundToInt().coerceAtLeast(1)
        val wide = (WIDE_QUARTERS * quarter).roundToInt().coerceAtLeast(1)
        val gain = 1f + 0.6f * env
        val step = (t * STEPS_PER_LOOP).toInt() + params.seed * 7919
        val centre = 0.72f + 0.25f * sin(TWO_PI * t)

        // The carrier at both ends, once per column: a row only adds its own phase to these.
        val sentCos = FloatArray(w) { cos(TWO_PI * it / period) }
        val sentSin = FloatArray(w) { sin(TWO_PI * it / period) }
        val readCos = FloatArray(w) { cos(TWO_PI * it / period * (1f + detune)) }
        val readSin = FloatArray(w) { sin(TWO_PI * it / period * (1f + detune)) }

        val data = source.data
        RowParallel.rows(h) { band ->
            val row = Row(w)
            for (y in band) {
                val line = y / lineRows
                val yn = y.toFloat() / h
                val base = y * w
                for (x in 0 until w) {
                    val p = data[base + x]
                    val r = PixelOps.redOf(p) / 255f
                    val g = PixelOps.greenOf(p) / 255f
                    val b = PixelOps.blueOf(p) / 255f
                    row.y[x] = 0.299f * r + 0.587f * g + 0.114f * b
                    row.i[x] = 0.596f * r - 0.274f * g - 0.322f * b
                    row.q[x] = 0.211f * r - 0.523f * g + 0.312f * b
                }
                row.box(row.i, row.a, smear)
                row.box(row.q, row.b, smear)
                row.box(row.y, row.wide, wide)

                // Colour alternates its sign from line to line, as the real signal does.
                val sign = if (line % 2 == 0) 1f else -1f
                for (x in 0 until w) {
                    row.sent[x] = row.y[x] +
                        sign * ((row.a[x] + leak * row.y[x]) * sentCos[x] + row.b[x] * sentSin[x])
                }

                // Time base: every line slips a little, lines in the torn band a lot.
                val wobble = 1.5f * sin(TWO_PI * (yn * 3 + t)) + 0.8f * sin(TWO_PI * (yn * 11 - 2 * t))
                val inBand = exp(-((yn - centre) / TEAR_WIDTH).pow(2))
                val jump = (noise(line / TEAR_ROWS, step) - 0.5f) * tear * inBand
                val slip = env * wobble * quarter + jump

                // The receiver's carrier drifts against the sender's, differently on every line.
                val drift = env * (1.4f * sin(TWO_PI * (yn * 2 - t)) + 0.9f * sin(TWO_PI * (yn * 7 + 2 * t)))
                val driftCos = sign * cos(drift)
                val driftSin = sign * sin(drift)

                for (x in 0 until w) {
                    val at = (x + slip + bend * (row.wide[x] - 0.5f)).coerceIn(0f, w - 1f)
                    val x0 = at.toInt()
                    val x1 = minOf(x0 + 1, w - 1)
                    val f = at - x0
                    val s = row.sent[x0] * (1f - f) + row.sent[x1] * f
                    row.got[x] = s
                    row.a[x] = 2f * s * (readCos[x] * driftCos - readSin[x] * driftSin)
                    row.b[x] = 2f * s * (readSin[x] * driftCos + readCos[x] * driftSin)
                }
                row.box(row.got, row.y, period)
                row.box(row.a, row.i, period)
                row.box(row.b, row.q, period)

                for (x in 0 until w) {
                    val yl = row.y[x]
                    val ci = row.i[x] * gain
                    val cq = row.q[x] * gain
                    data[base + x] = PixelOps.argb(
                        PixelOps.alphaOf(data[base + x]),
                        channel(yl + 0.956f * ci + 0.621f * cq),
                        channel(yl - 0.272f * ci - 0.647f * cq),
                        channel(yl - 1.106f * ci + 1.703f * cq),
                    )
                }
            }
        }
        return source
    }

    private fun channel(value: Float): Int = (value * 255f + 0.5f).toInt().coerceIn(0, 255)

    /** 0..1 from two integers, the same on every device. */
    private fun noise(a: Int, b: Int): Float {
        var n = a * 374761393 + b * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    /** One worker's scratch lines, so rows share nothing. */
    private class Row(val w: Int) {
        val y = FloatArray(w)
        val i = FloatArray(w)
        val q = FloatArray(w)
        val a = FloatArray(w)
        val b = FloatArray(w)
        val wide = FloatArray(w)
        val sent = FloatArray(w)
        val got = FloatArray(w)
        private val sums = FloatArray(w + 1)

        /**
         * Mean over [n] neighbours. Near an end the window stops at the end instead of running
         * past it: it always holds whole periods, so a carrier of period [n] cancels there too.
         */
        fun box(from: FloatArray, to: FloatArray, n: Int) {
            if (n <= 1 || n >= w) {
                System.arraycopy(from, 0, to, 0, w)
                return
            }
            var sum = 0f
            for (x in 0 until w) {
                sums[x] = sum
                sum += from[x]
            }
            sums[w] = sum
            val before = n / 2
            for (x in 0 until w) {
                val lo = (x - before).coerceIn(0, w - n)
                to[x] = (sums[lo + n] - sums[lo]) / n
            }
        }
    }
}
