package org.phioster.glyphsmith.core.image

import kotlin.math.pow

/**
 * The adjustments that happen *before* the dither reads the image.
 *
 * This is the distinction the whole file exists for. Saturation and blur also exist in the
 * effect chain, where they work on the rendered glyphs and change how the picture *looks*.
 * Here they change what the dithering algorithm *sees*, and therefore which glyph each cell
 * picks — the pattern itself moves. Turning saturation down here and turning it down in FX
 * produce completely different images, and that is not a bug to be reconciled.
 *
 * Everything is pure and works on the cell grid rather than on source pixels: a cell is the
 * smallest thing this app can draw, so there is nothing to be gained from finer resolution
 * and a great deal of speed to be lost.
 */
object Adjustments {

    /**
     * Hue rotation and saturation, applied to a cell's averaged colour before its luminance
     * is taken. Returns the adjusted colour packed as RGB.
     *
     * The rotation is the standard YIQ-style matrix rather than a round trip through HSL —
     * it is one pass of arithmetic instead of two conversions, and at one call per cell that
     * difference is worth having.
     */
    fun colorAdjust(r: Int, g: Int, b: Int, hueDegrees: Int, saturation: Int): Int {
        val sat = (saturation / 100f).coerceIn(0f, 2f)
        val hue = Math.floorMod(hueDegrees, 360)
        if (hue == 0 && sat == 1f) return pack(r, g, b)

        var rf = r / 255f
        var gf = g / 255f
        var bf = b / 255f

        if (hue != 0) {
            val radians = hue * Math.PI / 180.0
            val cos = kotlin.math.cos(radians).toFloat()
            val sin = kotlin.math.sin(radians).toFloat()
            val third = 1f / 3f
            val sqrt = kotlin.math.sqrt(1f / 3f)
            val a = cos + (1f - cos) * third
            val bb = third * (1f - cos) - sqrt * sin
            val c = third * (1f - cos) + sqrt * sin
            val nr = rf * a + gf * bb + bf * c
            val ng = rf * c + gf * a + bf * bb
            val nb = rf * bb + gf * c + bf * a
            rf = nr
            gf = ng
            bf = nb
        }

        if (sat != 1f) {
            // Towards the cell's own grey, so desaturating never shifts its brightness.
            val grey = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
            rf = grey + (rf - grey) * sat
            gf = grey + (gf - grey) * sat
            bf = grey + (bf - grey) * sat
        }

        return pack(to255(rf), to255(gf), to255(bf))
    }

    /**
     * Midtones and highlights, on top of the existing gamma / contrast / brightness curve.
     *
     * Both are 0..100 with 50 neutral, matching the panel they are modelled on. What each
     * does at a given value is this app's own choice — the original documents neither.
     *
     * Midtones is a gamma pivot: it moves the middle of the range without touching either
     * end, which is what keeps black black while the mid grey opens up. Highlights is a
     * shoulder applied only above the midpoint, weighted so it fades to nothing at 0.5 and
     * reaches full strength at white — otherwise it would just be a second contrast slider.
     */
    fun tone(value: Float, midtones: Int, highlights: Int): Float {
        var v = value.coerceIn(0f, 1f)

        if (midtones != 50) {
            // 50 → exponent 1; 0 → 2 (darker mids); 100 → 0.5 (brighter mids).
            val exponent = 2f.pow((50 - midtones) / 50f)
            v = v.pow(exponent)
        }

        if (highlights != 50 && v > 0.5f) {
            val amount = (highlights - 50) / 50f
            // 0 at the midpoint, 1 at white — the shoulder has to arrive gradually, or it
            // shows up as a hard kink halfway up the ramp.
            val weight = (v - 0.5f) * 2f
            // Lifting heads for white, pulling heads for the midpoint. Writing both as a
            // move towards a fixed end keeps the result inside 0..1 without a clamp doing
            // the work, and keeps the two directions symmetric.
            v += if (amount > 0f) {
                amount * weight * (1f - v)
            } else {
                amount * weight * (v - 0.5f)
            }
        }

        return v.coerceIn(0f, 1f)
    }

    /**
     * Separable box blur over the cell grid.
     *
     * A box rather than a Gaussian because at these radii — one to a handful of *cells* —
     * the two are visually indistinguishable and the box is two passes of running sums.
     */
    fun blur(grid: FloatArray, cols: Int, rows: Int, radius: Int): FloatArray {
        if (radius <= 0) return grid
        val horizontal = FloatArray(grid.size)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var sum = 0f
                var n = 0
                for (dx in -radius..radius) {
                    val sx = (x + dx).coerceIn(0, cols - 1)
                    sum += grid[y * cols + sx]
                    n++
                }
                horizontal[y * cols + x] = sum / n
            }
        }
        val out = FloatArray(grid.size)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var sum = 0f
                var n = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, rows - 1)
                    sum += horizontal[sy * cols + x]
                    n++
                }
                out[y * cols + x] = sum / n
            }
        }
        return out
    }

    /**
     * Median filter over the cell grid.
     *
     * The whole point of having this *and* blur: a median throws away a lone outlier without
     * touching an edge, because the middle of a sorted neighbourhood on one side of an edge
     * is still a value from that side. A blur averages across the edge and softens it. On a
     * grainy photo that is the difference between cleaning it up and smearing it.
     */
    fun denoise(grid: FloatArray, cols: Int, rows: Int, radius: Int): FloatArray {
        if (radius <= 0) return grid
        val out = FloatArray(grid.size)
        val window = FloatArray((radius * 2 + 1) * (radius * 2 + 1))
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var n = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, rows - 1)
                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, cols - 1)
                        window[n++] = grid[sy * cols + sx]
                    }
                }
                val slice = window.copyOf(n)
                slice.sort()
                out[y * cols + x] = slice[n / 2]
            }
        }
        return out
    }

    /** The same neighbourhood passes over the colour grid, so SOURCE colour follows suit. */
    fun blurColors(grid: IntArray, cols: Int, rows: Int, radius: Int): IntArray =
        channelwise(grid, cols, rows) { channel -> blur(channel, cols, rows, radius) }

    fun denoiseColors(grid: IntArray, cols: Int, rows: Int, radius: Int): IntArray =
        channelwise(grid, cols, rows) { channel -> denoise(channel, cols, rows, radius) }

    private fun channelwise(
        grid: IntArray,
        cols: Int,
        rows: Int,
        pass: (FloatArray) -> FloatArray,
    ): IntArray {
        val r = FloatArray(grid.size) { ((grid[it] shr 16) and 0xFF) / 255f }
        val g = FloatArray(grid.size) { ((grid[it] shr 8) and 0xFF) / 255f }
        val b = FloatArray(grid.size) { (grid[it] and 0xFF) / 255f }
        val pr = pass(r)
        val pg = pass(g)
        val pb = pass(b)
        return IntArray(grid.size) { pack(to255(pr[it]), to255(pg[it]), to255(pb[it])) }
    }

    private fun pack(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun to255(v: Float): Int = (v * 255f).toInt().coerceIn(0, 255)
}
