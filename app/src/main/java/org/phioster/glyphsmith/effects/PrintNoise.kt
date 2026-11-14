package org.phioster.glyphsmith.effects

import kotlin.math.floor

/** Smooth noise for the print effects: the same on every device, and cheap enough per pixel. */
internal object PrintNoise {

    /** 0 up to but not 1 from a lattice point and a seed. */
    fun hash(a: Int, b: Int, seed: Int): Float {
        var n = a * 374761393 + b * 668265263 + seed * 362437
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65536f
    }

    /** 0..1, varying smoothly over about one unit of [x] and [y]. */
    fun value(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val top = hash(x0, y0, seed) * (1f - sx) + hash(x0 + 1, y0, seed) * sx
        val bottom = hash(x0, y0 + 1, seed) * (1f - sx) + hash(x0 + 1, y0 + 1, seed) * sx
        return top * (1f - sy) + bottom * sy
    }

    /**
     * [pixels] with every pixel the mean of the square of side 2·[radius]+1 around it, as a new
     * array; alpha is copied. An effect that reads edges or one colour per cell reads this: the
     * picture reaches it dithered, and a dither is all edges and single pixels.
     */
    fun softened(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius < 1) return pixels.copyOf()
        val across = IntArray(pixels.size)
        pass(pixels, across, w, h, radius, alongRows = true)
        val out = IntArray(pixels.size)
        pass(across, out, w, h, radius, alongRows = false)
        return out
    }

    private fun pass(from: IntArray, to: IntArray, w: Int, h: Int, radius: Int, alongRows: Boolean) {
        val lines = if (alongRows) h else w
        val count = if (alongRows) w else h
        val step = if (alongRows) 1 else w
        val size = 2 * radius + 1
        org.phioster.glyphsmith.core.pipeline.RowParallel.rows(lines) { band ->
            for (line in band) {
                val start = if (alongRows) line * w else line
                var r = 0
                var g = 0
                var b = 0
                for (i in -radius..radius) {
                    val p = from[start + i.coerceIn(0, count - 1) * step]
                    r += p shr 16 and 0xFF
                    g += p shr 8 and 0xFF
                    b += p and 0xFF
                }
                for (i in 0 until count) {
                    val at = start + i * step
                    to[at] = (from[at] and (0xFF shl 24)) or ((r / size) shl 16) or ((g / size) shl 8) or (b / size)
                    val gone = from[start + (i - radius).coerceAtLeast(0) * step]
                    val come = from[start + (i + radius + 1).coerceAtMost(count - 1) * step]
                    r += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
                    g += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
                    b += (come and 0xFF) - (gone and 0xFF)
                }
            }
        }
    }

    /** The colour [t] of the way along [stops], 0..1, as packed RGB. */
    fun ramp(stops: IntArray, t: Float): Int {
        val at = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = at.toInt().coerceAtMost(stops.size - 2)
        val f = at - i
        val a = stops[i]
        val b = stops[i + 1]
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * f).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
