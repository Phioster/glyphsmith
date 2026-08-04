package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.random.Random
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Interlace corruption — damage that follows the scan pattern of an interlaced signal.
 *
 * Named in their tutorials but not demonstrated; the behaviour here is this app's own.
 *
 * The distinguishing idea, and the reason this is not [SliceShift] again: an interlaced
 * frame is two *fields*, the even lines and the odd lines, transmitted separately. When one
 * arrives damaged the other is untouched, so the corruption lands on every other row rather
 * than on a band. That comb pattern is what makes it read as a signal fault instead of a
 * cut — and it is why the field, not the band, is the unit everything here works on.
 */
@Serializable
data class InterlaceParams(
    val enabled: Boolean = false,
    /** Which field takes the damage. Corrupting both just looks like noise. */
    val oddField: Boolean = true,
    /** 0..100 % of the width the damaged field is pushed sideways. */
    val shift: Int = 4,
    /** 0..100 — chance a given line of the field is hit at all. */
    val density: Int = 60,
    /** 0..100 — how far the damaged lines drift in hue. */
    val tearColor: Int = 30,
    /** Lines are held from the row above instead of shifted — a frozen field. */
    val freeze: Boolean = false,
    val seed: Int = 1,
)

object Interlace {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(EffectStack::interlace, InterlaceParams::enabled) { pixels, params, _ ->
        apply(pixels, params)
    }

    fun apply(source: Pixels, params: InterlaceParams): Pixels {
        if (!params.enabled) return source
        if (params.shift == 0 && params.tearColor == 0 && !params.freeze) return source

        val random = Random(params.seed)
        val out = source.data.copyOf()
        val width = source.width
        val maxShift = (width * params.shift / 100f).toInt()
        val chance = params.density / 100f
        val tint = params.tearColor / 100f
        val parity = if (params.oddField) 1 else 0

        for (y in 0 until source.height) {
            if (y % 2 != parity) continue
            // Rolled per line rather than per field, so the damage is ragged down the image
            // instead of arriving as one solid block of every-other-row.
            if (random.nextFloat() >= chance) continue

            val offset = if (maxShift == 0) 0 else random.nextInt(-maxShift, maxShift + 1)
            val hue = (random.nextFloat() - 0.5f) * 2f * tint

            for (x in 0 until width) {
                val pixel = if (params.freeze && y > 0) {
                    // A frozen field repeats its neighbour, which is what a dropped field
                    // actually looks like: the previous line stands in for the missing one.
                    source.data[(y - 1) * width + x]
                } else {
                    source.data[y * width + Math.floorMod(x - offset, width)]
                }
                out[y * width + x] = if (hue == 0f) pixel else skew(pixel, hue)
            }
        }

        System.arraycopy(out, 0, source.data, 0, out.size)
        return source
    }

    /**
     * Pushes red and blue apart by [amount] without touching green.
     *
     * Cheaper than a hue rotation and closer to the artefact being imitated: analogue colour
     * damage shows up as the chroma carriers drifting, and green carries most of the
     * luminance, so leaving it alone keeps the line's brightness where it was.
     */
    private fun skew(pixel: Int, amount: Float): Int {
        val r = PixelOps.redOf(pixel) + (amount * 90f).toInt()
        val b = PixelOps.blueOf(pixel) - (amount * 90f).toInt()
        return PixelOps.argb(
            PixelOps.alphaOf(pixel),
            r.coerceIn(0, 255),
            PixelOps.greenOf(pixel),
            b.coerceIn(0, 255),
        )
    }
}
