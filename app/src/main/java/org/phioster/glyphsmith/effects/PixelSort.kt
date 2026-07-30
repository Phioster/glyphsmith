package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.abs
import org.phioster.glyphsmith.core.image.Pixels

enum class SortAxis(val label: String) {
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical"),
}

enum class SortKey(val label: String) {
    LUMINANCE("Luminance"),
    HUE("Hue"),
    SATURATION("Saturation"),
}

/**
 * Pixel sorting: contiguous runs of pixels are reordered in place along one axis.
 *
 * The threshold band is the whole effect, not a refinement of it. Sorting *everything*
 * turns the image into gradient mush; sorting only the pixels whose brightness falls inside
 * a band leaves the darks and the lights where they are, and the sorted mid-tones appear to
 * bleed out of the edges between them. Narrowing the band is how the effect is aimed.
 */
@Serializable
data class PixelSortParams(
    val enabled: Boolean = false,
    val axis: SortAxis = SortAxis.HORIZONTAL,
    val key: SortKey = SortKey.LUMINANCE,
    /** 0..100 — a pixel joins a run only while its luminance is between these. */
    val thresholdLow: Int = 25,
    val thresholdHigh: Int = 80,
    /** 0 is unlimited; otherwise a run is cut off after this many pixels. */
    val maxRun: Int = 0,
    val reverse: Boolean = false,
)

object PixelSort {

    fun apply(source: Pixels, params: PixelSortParams): Pixels {
        if (!params.enabled) return source

        // A band given the wrong way round would sort nothing at all, which reads as a
        // broken effect rather than as a mistake; swapping is what the user meant.
        val low = minOf(params.thresholdLow, params.thresholdHigh) / 100f
        val high = maxOf(params.thresholdLow, params.thresholdHigh) / 100f
        val limit = if (params.maxRun <= 0) Int.MAX_VALUE else params.maxRun

        val lines = if (params.axis == SortAxis.HORIZONTAL) source.height else source.width
        val length = if (params.axis == SortAxis.HORIZONTAL) source.width else source.height
        val line = IntArray(length)

        for (index in 0 until lines) {
            readLine(source, params.axis, index, line)
            sortRuns(line, low, high, limit, params.key, params.reverse)
            writeLine(source, params.axis, index, line)
        }
        return source
    }

    private fun readLine(source: Pixels, axis: SortAxis, index: Int, out: IntArray) {
        if (axis == SortAxis.HORIZONTAL) {
            System.arraycopy(source.data, index * source.width, out, 0, source.width)
        } else {
            for (y in 0 until source.height) out[y] = source.data[y * source.width + index]
        }
    }

    private fun writeLine(source: Pixels, axis: SortAxis, index: Int, line: IntArray) {
        if (axis == SortAxis.HORIZONTAL) {
            System.arraycopy(line, 0, source.data, index * source.width, source.width)
        } else {
            for (y in 0 until source.height) source.data[y * source.width + index] = line[y]
        }
    }

    /**
     * Walks the line, and every time it finds a stretch inside the band, sorts exactly that
     * stretch. Pixels outside the band are never read into a run, so they cannot move — that
     * is what keeps the untouched parts of the picture recognisable.
     */
    @Suppress("LongParameterList")
    private fun sortRuns(
        line: IntArray,
        low: Float,
        high: Float,
        limit: Int,
        key: SortKey,
        reverse: Boolean,
    ) {
        var start = 0
        while (start < line.size) {
            if (!inBand(line[start], low, high)) {
                start++
                continue
            }
            var end = start
            while (end < line.size && end - start < limit && inBand(line[end], low, high)) end++

            if (end - start > 1) {
                val run = line.copyOfRange(start, end).sortedBy { keyOf(it, key) }
                for (i in run.indices) {
                    line[start + i] = if (reverse) run[run.size - 1 - i] else run[i]
                }
            }
            start = end
        }
    }

    private fun inBand(pixel: Int, low: Float, high: Float): Boolean {
        val luminance = PixelOps.luminance(pixel)
        return luminance in low..high
    }

    private fun keyOf(pixel: Int, key: SortKey): Float = when (key) {
        SortKey.LUMINANCE -> PixelOps.luminance(pixel)
        SortKey.HUE -> hueOf(pixel)
        SortKey.SATURATION -> saturationOf(pixel)
    }

    /** Hue in 0..1. Grey has no hue at all, so it is parked at 0 rather than left random. */
    private fun hueOf(pixel: Int): Float {
        val r = PixelOps.redOf(pixel) / 255f
        val g = PixelOps.greenOf(pixel) / 255f
        val b = PixelOps.blueOf(pixel) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta < 1e-5f) return 0f
        val hue = when (max) {
            r -> ((g - b) / delta + if (g < b) 6f else 0f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        return (hue / 6f).let { it - kotlin.math.floor(it) }
    }

    private fun saturationOf(pixel: Int): Float {
        val r = PixelOps.redOf(pixel) / 255f
        val g = PixelOps.greenOf(pixel) / 255f
        val b = PixelOps.blueOf(pixel) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max <= 0f) 0f else abs(max - min) / max
    }
}
