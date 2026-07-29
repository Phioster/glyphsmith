package org.phioster.glyphsmith.core.color

/**
 * Maps arbitrary colours onto the nearest entry of a fixed palette.
 *
 * This is what the pixel path needs and the glyph path never did. Sampling a palette by
 * luminance — which is what the glyph renderer does — only has to answer "how bright is this
 * cell"; reducing an image *to* a palette has to answer "which of these colours is this one",
 * and those are different questions. A blue and a brown of equal luminance are interchangeable
 * to the first and unrelated to the second.
 *
 * The palette is converted into the metric's space once, at construction. Per-colour lookups
 * are then three subtractions and a comparison per entry, with no transcendental maths in the
 * loop — the cube roots all happen up front.
 *
 * Results are memoised. Dithered output revisits the same source colours constantly, so the
 * cache turns most lookups into a map hit; the same trick the GIF quantiser has always used.
 * Not thread-safe by itself — build one per render pass, which is how the render path uses it.
 */
class PaletteQuantizer(
    private val palette: IntArray,
    private val metric: ColorDistance = ColorDistance.OKLAB,
) {

    private val coords: Array<FloatArray> = Array(palette.size) { metric.coordsOf(palette[it]) }
    private val cache = HashMap<Int, Int>()

    val size: Int get() = palette.size

    /** The palette entry closest to [color], or [color] itself for an empty palette. */
    fun nearest(color: Int): Int {
        if (palette.isEmpty()) return color
        return palette[indexOf(color)]
    }

    /** Index of the closest palette entry — what a dither needs, since it works in indices. */
    fun indexOf(color: Int): Int {
        if (palette.size <= 1) return 0
        val key = color and 0x00FFFFFF
        cache[key]?.let { return it }

        val target = metric.coordsOf(color)
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in coords.indices) {
            val d = ColorDistance.squaredBetween(target, coords[i])
            if (d < bestDistance) {
                bestDistance = d
                best = i
                // An exact match cannot be beaten, and palettes contain the source colours
                // often enough — flat backgrounds, anything already posterised — for this to
                // pay for itself.
                if (d == 0f) break
            }
        }
        cache[key] = best
        return best
    }

    /** The entry at [index], clamped. Used when a dither has chosen a level rather than a colour. */
    fun colorAt(index: Int): Int {
        if (palette.isEmpty()) return 0
        return palette[index.coerceIn(0, palette.size - 1)]
    }
}
