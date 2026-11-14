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
