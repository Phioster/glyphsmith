package org.phioster.glyphsmith.anim

/**
 * Median-cut reduction to a GIF-sized palette.
 *
 * Glyphsmith output is unusually kind to this: in single-ink mode a frame holds two
 * colours, and a palette-mapped one rarely holds more than a few dozen. The exact-palette
 * shortcut below therefore covers most real exports, and median cut only runs for
 * source-coloured output.
 */
object ColorQuantizer {

    /** Builds one palette for *all* frames, so colours don't shift between them. */
    fun palette(frames: List<IntArray>, maxColors: Int): IntArray {
        val counts = HashMap<Int, Int>()
        frames.forEach { frame ->
            frame.forEach { pixel ->
                if (isOpaque(pixel)) {
                    val rgb = pixel and 0xFFFFFF
                    counts[rgb] = (counts[rgb] ?: 0) + 1
                }
            }
        }
        if (counts.isEmpty()) return intArrayOf(0)
        if (counts.size <= maxColors) return counts.keys.toIntArray()

        var boxes = listOf(Box(counts.keys.toIntArray(), counts))
        while (boxes.size < maxColors) {
            val target = boxes.filter { it.colors.size > 1 }.maxByOrNull { it.range } ?: break
            val split = target.split() ?: break
            boxes = boxes.filterNot { it === target } + split.toList()
        }
        return boxes.map { it.average() }.toIntArray()
    }

    fun isOpaque(pixel: Int): Boolean = ((pixel ushr 24) and 0xFF) >= 128

    /** Nearest palette entry, cached — the same handful of colours recur constantly. */
    class Mapper(private val palette: IntArray) {
        private val cache = HashMap<Int, Int>()

        fun indexOf(pixel: Int): Int {
            val rgb = pixel and 0xFFFFFF
            cache[rgb]?.let { return it }
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in palette.indices) {
                val candidate = palette[i]
                val dr = ((rgb shr 16) and 0xFF) - ((candidate shr 16) and 0xFF)
                val dg = ((rgb shr 8) and 0xFF) - ((candidate shr 8) and 0xFF)
                val db = (rgb and 0xFF) - (candidate and 0xFF)
                val distance = dr * dr + dg * dg + db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                    if (distance == 0) break
                }
            }
            cache[rgb] = best
            return best
        }
    }

    private class Box(val colors: IntArray, val counts: Map<Int, Int>) {

        private fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF

        private fun spread(shift: Int): Int {
            var min = 255
            var max = 0
            colors.forEach {
                val v = channel(it, shift)
                if (v < min) min = v
                if (v > max) max = v
            }
            return max - min
        }

        val range: Int get() = maxOf(spread(16), spread(8), spread(0))

        fun split(): Pair<Box, Box>? {
            if (colors.size < 2) return null
            val shift = listOf(16, 8, 0).maxByOrNull { spread(it) } ?: 16
            val sorted = colors.sortedBy { channel(it, shift) }
            val middle = sorted.size / 2
            return Box(sorted.take(middle).toIntArray(), counts) to
                Box(sorted.drop(middle).toIntArray(), counts)
        }

        /** Weighted by how often each colour actually occurs, not a flat mean. */
        fun average(): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            var total = 0L
            colors.forEach { color ->
                val weight = (counts[color] ?: 1).toLong()
                r += channel(color, 16) * weight
                g += channel(color, 8) * weight
                b += channel(color, 0) * weight
                total += weight
            }
            if (total == 0L) return colors.firstOrNull() ?: 0
            return (((r / total).toInt() and 0xFF) shl 16) or
                (((g / total).toInt() and 0xFF) shl 8) or
                ((b / total).toInt() and 0xFF)
        }
    }
}
