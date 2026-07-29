package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Bands of the image displaced sideways — the other half of the glitch vocabulary.
 *
 * [PixelSort] reorders pixels within a line; this moves whole lines without touching their
 * contents. The two do different damage and read very differently stacked in either order,
 * which is a good reason for them to be separate passes rather than one.
 */
@Serializable
data class SliceShiftParams(
    val enabled: Boolean = false,
    /** How many bands the image is cut into. */
    val slices: Int = 24,
    /** 0..100 — largest displacement, as a percentage of the width. */
    val maxOffset: Int = 12,
    /** 0..100 — chance that any given band is displaced at all. */
    val density: Int = 50,
    /** 0..100 — pulls the red and blue channels apart by a further fraction of the offset. */
    val colorShift: Int = 0,
    /** Bands run down the image instead of across it. */
    val vertical: Boolean = false,
    val seed: Int = 1,
)

object SliceShift {

    fun apply(source: Pixels, params: SliceShiftParams): Pixels {
        if (!params.enabled || params.slices <= 0 || params.maxOffset == 0) return source

        val random = Random(params.seed)
        val out = source.data.copyOf()
        val lines = if (params.vertical) source.width else source.height
        val length = if (params.vertical) source.height else source.width
        val maxOffset = (length * params.maxOffset / 100f).toInt().coerceAtLeast(1)
        val chance = params.density / 100f
        val colorShift = params.colorShift / 100f

        var line = 0
        while (line < lines) {
            // Bands of uneven height: a fixed slice height reads as a regular pattern, and
            // regularity is the one thing a glitch must not look like.
            val height = (lines / params.slices).coerceAtLeast(1)
            val band = (height / 2 + random.nextInt(height + 1)).coerceAtLeast(1)
            val end = minOf(line + band, lines)

            if (random.nextFloat() < chance) {
                val offset = random.nextInt(-maxOffset, maxOffset + 1)
                val redOffset = (offset * (1f + colorShift)).toInt()
                val blueOffset = (offset * (1f - colorShift)).toInt()
                for (index in line until end) {
                    shiftLine(source, out, params.vertical, index, length, offset, redOffset, blueOffset)
                }
            }
            line = end
        }

        System.arraycopy(out, 0, source.data, 0, out.size)
        return source
    }

    @Suppress("LongParameterList")
    private fun shiftLine(
        source: Pixels,
        out: IntArray,
        vertical: Boolean,
        index: Int,
        length: Int,
        offset: Int,
        redOffset: Int,
        blueOffset: Int,
    ) {
        for (position in 0 until length) {
            val base = sampleAt(source, vertical, index, position - offset, length)
            val pixel = if (redOffset == blueOffset) {
                base
            } else {
                // Each channel is fetched from its own displacement, so a shifted band also
                // tears its colour apart rather than sliding as one clean block.
                val red = sampleAt(source, vertical, index, position - redOffset, length)
                val blue = sampleAt(source, vertical, index, position - blueOffset, length)
                PixelOps.argb(
                    PixelOps.alphaOf(base),
                    PixelOps.redOf(red),
                    PixelOps.greenOf(base),
                    PixelOps.blueOf(blue),
                )
            }
            val target = if (vertical) position * source.width + index else index * source.width + position
            out[target] = pixel
        }
    }

    /** Reads along the band, wrapping — a displaced band stays full rather than trailing off. */
    private fun sampleAt(
        source: Pixels,
        vertical: Boolean,
        index: Int,
        position: Int,
        length: Int,
    ): Int {
        val wrapped = Math.floorMod(position, length)
        return if (vertical) {
            source.data[wrapped * source.width + index]
        } else {
            source.data[index * source.width + wrapped]
        }
    }
}
