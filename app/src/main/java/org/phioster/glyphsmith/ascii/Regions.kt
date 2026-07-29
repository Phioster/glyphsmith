package org.phioster.glyphsmith.ascii

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Styles that flatten an area instead of thresholding a cell.
 *
 * Everything else in this app decides one cell at a time: a threshold is compared, a glyph is
 * chosen, the error moves on. These do the opposite — they carve the grid into tiles,
 * triangles or blobs, take the average brightness of each, and give every cell in it the same
 * glyph. What comes out is not a texture laid over the picture but a coarser picture, which
 * is why a mosaic reads as a mosaic and not as a pattern of squares.
 *
 * That means they cannot run inside the row loop at all, and they do not try. Each returns a
 * finished grid of glyph indices through the same door Riemersma uses.
 *
 * **Gaps are a region too.** Circles on a grid leave the corners uncovered, and a mosaic with
 * visible grout leaves its lines bare. Those cells are marked [GAP] and come back as the
 * emptiest glyph, which is what makes the tiles read as separate objects rather than as a
 * quilt.
 */
object Regions {

    /** A cell that belongs to no region — the space between tiles. */
    private const val GAP = Int.MIN_VALUE

    /**
     * Every cell's glyph index, decided by the region it falls in.
     *
     * Two passes: the first works out which region each cell belongs to and accumulates that
     * region's brightness, the second reads the average back out. A map rather than an array
     * because a Voronoi lattice does not number its cells contiguously and the block styles
     * should not pay for a special case.
     */
    fun quantise(
        mode: DitherMode,
        luma: FloatArray,
        cols: Int,
        rows: Int,
        levels: Int,
        options: PatternOptions,
    ): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out

        val keys = IntArray(cols * rows)
        val sums = HashMap<Int, Float>()
        val counts = HashMap<Int, Int>()

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val cell = y * cols + x
                val key = regionOf(mode, x, y, options)
                keys[cell] = key
                if (key == GAP) continue
                sums[key] = (sums[key] ?: 0f) + luma[cell]
                counts[key] = (counts[key] ?: 0) + 1
            }
        }

        val top = levels - 1
        for (cell in out.indices) {
            val key = keys[cell]
            if (key == GAP) continue
            val count = counts[key] ?: continue
            val average = (sums[key] ?: 0f) / count
            out[cell] = (average.coerceIn(0f, 1f) * top).roundToInt().coerceIn(0, top)
        }
        return out
    }

    /**
     * Which region a cell belongs to, as a key that is only ever compared for equality.
     *
     * Packed from the region's own coordinates and, where a tile is subdivided, which part of
     * it the cell is in. The packing has to survive negative coordinates — the grid does not
     * start at the origin once a pattern is rotated — so it is shifted rather than multiplied
     * into a corner.
     */
    private fun regionOf(mode: DitherMode, x: Int, y: Int, options: PatternOptions): Int {
        val size = options.period.coerceAtLeast(1).toFloat()
        val radians = options.angle * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val u = (x * c + y * s) / size
        val v = (-x * s + y * c) / size

        val tileX = floor(u).toInt()
        val tileY = floor(v).toInt()
        val fx = u - tileX
        val fy = v - tileY

        return when (mode) {
            // Plain blocks. The simplest of the family and the yardstick for the rest.
            DitherMode.MOSAIC -> pack(tileX, tileY, 0)

            // Blocks with the outer band left bare, so the tiles read as tiles. The band
            // widens with the second axis, which is the grout.
            DitherMode.SQUARE_MOSAIC -> {
                val grout = 0.05f + options.density / 100f * 0.35f
                val inside = fx > grout && fx < 1f - grout && fy > grout && fy < 1f - grout
                if (inside) pack(tileX, tileY, 0) else GAP
            }

            DitherMode.CIRCLE_GRID -> {
                val radius = 0.15f + options.density / 100f * 0.35f
                val d = hypot(fx - 0.5f, fy - 0.5f)
                if (d <= radius) pack(tileX, tileY, 0) else GAP
            }

            // The same idea under Manhattan distance, which is all that separates a diamond
            // from a circle.
            DitherMode.DIAMOND_GRID -> {
                val radius = 0.15f + options.density / 100f * 0.35f
                val d = abs(fx - 0.5f) + abs(fy - 0.5f)
                if (d <= radius) pack(tileX, tileY, 0) else GAP
            }

            // A square cut corner to corner. Which side of the diagonal a cell falls on is
            // the entire tessellation.
            DitherMode.TRI_POLY -> pack(tileX, tileY, if (fx + fy < 1f) 0 else 1)

            /*
             * Six ways to cut a square into two triangles, each tessellating perfectly and
             * each looking quite different. The slope is what changes: a gentle one gives
             * long shallow wedges, a steep one gives spikes, and the diagonal in the other
             * direction mirrors the whole field.
             */
            DitherMode.LOW_POLY -> {
                val slope = TRIANGLE_SLOPES[
                    (options.density * TRIANGLE_SLOPES.size / 101).coerceIn(0, TRIANGLE_SLOPES.size - 1),
                ]
                val above = if (slope >= 0f) fy < fx * slope else fy < 1f + fx * slope
                pack(tileX, tileY, if (above) 0 else 1)
            }

            // A honeycomb. Offsetting every other row by half a tile is what turns a grid of
            // boxes into hexagons — the same trick the beehive threshold uses, one dimension
            // up, and here it decides ownership rather than a threshold.
            DitherMode.HEXA_POLY -> hexCell(u, v, 0)

            // Hexagons halved. The split runs one way or the other depending on the second
            // axis, and the two readings tile quite differently.
            DitherMode.PENTA_POLY -> {
                val vertical = options.density >= 50
                val half = if (vertical) {
                    if (fx < 0.5f) 0 else 1
                } else {
                    if (fy < 0.5f) 0 else 1
                }
                hexCell(u, v, half)
            }

            /*
             * Camo. A Voronoi diagram built the cheap way: one seed per lattice cell, pushed
             * off centre by a hash of its own coordinates, and each cell joins whichever of
             * the nine nearest seeds it is closest to.
             *
             * A true Voronoi would search every seed; at this grid size the two are
             * indistinguishable, and only one of them is affordable while a camera is running.
             */
            DitherMode.CAMO -> {
                var bestX = tileX
                var bestY = tileY
                var best = Float.MAX_VALUE
                for (oy in -1..1) {
                    for (ox in -1..1) {
                        val sx = tileX + ox
                        val sy = tileY + oy
                        val jx = sx + hash(sx, sy, options.orb.seed)
                        val jy = sy + hash(sy, sx, options.orb.seed + 31)
                        val d = hypot(u - jx, v - jy)
                        if (d < best) {
                            best = d
                            bestX = sx
                            bestY = sy
                        }
                    }
                }
                pack(bestX, bestY, 0)
            }

            else -> pack(tileX, tileY, 0)
        }
    }

    /**
     * A honeycomb cell, as the offset-row trick.
     *
     * Rows are two-thirds of a tile tall so neighbours interlock, and every other row is
     * pushed half a tile sideways. That is not a mathematically exact hexagon, and it does
     * not need to be: on a grid of character cells the difference is smaller than one glyph.
     */
    private fun hexCell(u: Float, v: Float, part: Int): Int {
        val row = floor(v / 0.75f).toInt()
        val shift = if (Math.floorMod(row, 2) == 0) 0f else 0.5f
        val col = floor(u - shift).toInt()
        return pack(col, row, part)
    }

    /** Slopes for [DitherMode.LOW_POLY]'s six cuts, from a shallow wedge to a mirrored spike. */
    private val TRIANGLE_SLOPES = floatArrayOf(1f, 0.5f, 2f, -1f, -0.5f, -2f)

    /**
     * Region coordinates into one comparable int.
     *
     * Shifted into the positive half before packing: a rotated pattern produces negative tile
     * coordinates, and two distinct regions colliding on one key would silently merge them
     * into a single blotch.
     */
    private fun pack(x: Int, y: Int, part: Int): Int {
        val px = (x + BIAS) and 0xFFF
        val py = (y + BIAS) and 0xFFF
        return (px shl 20) or (py shl 8) or (part and 0xFF)
    }

    private const val BIAS = 1 shl 11

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
