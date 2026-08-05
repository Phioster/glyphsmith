package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge, which is the whole of this export.
 *
 * Writing rectangles is trivial; the reason the file is usable at all is that adjacent cells of
 * one colour became one shape first. The property that must hold whatever the merge does is
 * that it still describes the same picture — every cell covered once, by a block of its own
 * colour. An export that quietly drops a row is one nobody notices is wrong until it has been
 * cut into something.
 */
class PixelVectorTest {

    private fun grid(cols: Int, rows: Int, f: (Int, Int) -> Int) =
        IntArray(cols * rows) { f(it % cols, it / cols) }

    /** Rebuilds the grid from the blocks, so coverage and colour are checked in one pass. */
    private fun rebuild(blocks: List<PixelVector.Block>, cols: Int, rows: Int): IntArray {
        val out = IntArray(cols * rows) { -1 }
        blocks.forEach { b ->
            for (r in b.row until b.row + b.rows) {
                for (c in b.col until b.col + b.cols) {
                    assertEquals("cell $c,$r is covered twice", -1, out[r * cols + c])
                    out[r * cols + c] = b.colour
                }
            }
        }
        return out
    }

    // --- the invariant --------------------------------------------------------------------

    @Test
    fun `the blocks tile the grid exactly, whatever is in it`() {
        val patterns = listOf<Pair<String, (Int, Int) -> Int>>(
            "flat" to { _, _ -> 0x101010 },
            "checker" to { x, y -> if ((x + y) % 2 == 0) 0xFFFFFF else 0x000000 },
            "rows" to { _, y -> y * 7 },
            "columns" to { x, _ -> x * 5 },
            "diagonal" to { x, y -> (x + y) / 3 },
            "noise" to { x, y -> (x * 2654435761L.toInt() + y * 40503) and 0xFF },
        )
        listOf(1 to 1, 1 to 9, 9 to 1, 8 to 5, 17 to 13).forEach { (cols, rows) ->
            patterns.forEach { (name, f) ->
                val cells = grid(cols, rows, f)
                val blocks = PixelVector.merge(cells, cols, rows)

                assertTrue("$name ${cols}x$rows produced nothing", blocks.isNotEmpty())
                assertEquals("$name ${cols}x$rows does not rebuild", cells.toList(), rebuild(blocks, cols, rows).toList())
            }
        }
    }

    @Test
    fun `an empty grid produces nothing rather than an empty rectangle`() {
        assertTrue(PixelVector.merge(IntArray(0), 0, 0).isEmpty())
    }

    @Test
    fun `a grid that does not match its size is refused`() {
        val threw = runCatching { PixelVector.merge(IntArray(5), 3, 3) }
        assertTrue("a mismatched grid was accepted", threw.isFailure)
    }

    // --- what the merge is for ------------------------------------------------------------

    @Test
    fun `a flat area is one block, not one per cell`() {
        val blocks = PixelVector.merge(grid(20, 20) { _, _ -> 0x223344 }, 20, 20)

        assertEquals(1, blocks.size)
        assertEquals(PixelVector.Block(0, 0, 20, 20, 0x223344), blocks.single())
    }

    @Test
    fun `stripes merge along their own direction`() {
        val rows = PixelVector.merge(grid(16, 4) { _, y -> y }, 16, 4)
        val cols = PixelVector.merge(grid(4, 16) { x, _ -> x }, 4, 16)

        assertEquals("four rows should be four blocks", 4, rows.size)
        assertEquals("four columns should be four blocks", 4, cols.size)
    }

    /** The worst case is real and worth pinning: a checkerboard cannot be merged at all. */
    @Test
    fun `a checkerboard is the worst case and costs one block per cell`() {
        val cols = 12
        val rows = 12
        val blocks = PixelVector.merge(grid(cols, rows) { x, y -> (x + y) % 2 }, cols, rows)

        assertEquals(cols * rows, blocks.size)
    }

    /**
     * And the case the export exists for: a real two-level dither is mostly flat runs, so the
     * merge has to take a large bite out of the count rather than a token one.
     */
    @Test
    fun `a two-level image merges to a fraction of its cells`() {
        val cols = 200
        val rows = 200
        // A disc on a ground: large flat areas, one long boundary — a dithered photograph in
        // miniature.
        val cells = grid(cols, rows) { x, y ->
            val dx = x - 100
            val dy = y - 100
            if (dx * dx + dy * dy < 3600) 0xF0F0F0 else 0x101010
        }

        val blocks = PixelVector.merge(cells, cols, rows)

        assertEquals(cells.toList(), rebuild(blocks, cols, rows).toList())
        assertTrue(
            "merged to ${blocks.size} of ${cells.size} cells, which is no saving at all",
            blocks.size < cells.size / 50,
        )
    }

    // --- the document ---------------------------------------------------------------------

    @Test
    fun `the svg is a ground plus the blocks that are not on it`() {
        val blocks = PixelVector.merge(grid(4, 2) { x, _ -> x / 2 }, 4, 2)
        val svg = PixelVector.svg(blocks, 4, 2, 8)

        // One rectangle for the ground, then only the blocks of the other colours.
        val ground = blocks.groupBy { it.colour }.maxByOrNull { (_, g) -> g.sumOf { it.cols * it.rows } }!!.key
        val drawn = blocks.count { it.colour != ground }

        assertEquals(drawn + 1, Regex("<rect ").findAll(svg).count())
        assertTrue(svg.startsWith("<svg "))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        assertTrue("the canvas is not in cell units", svg.contains("width=\"32\""))
        assertTrue(svg.contains("height=\"16\""))
        assertTrue("the viewBox is missing", svg.contains("viewBox=\"0 0 32 16\""))
    }

    /**
     * The ground is the colour covering the most *cells*, not the one with the most blocks.
     *
     * Choosing by block count would pick the fragmented colour, and painting that across the
     * canvas saves nothing — the compact colour then has to be drawn over almost all of it.
     *
     * Built by hand rather than through [PixelVector.merge], because a merge cannot produce the
     * case: scattered cells of one colour fragment the other colour just as much, so no real
     * grid has many blocks of one and few of the other. The rule still has to be right.
     */
    @Test
    fun `the ground is the colour that covers the most area, not the most blocks`() {
        val compact = 0x111111
        val scattered = 0x222222
        val blocks = listOf(PixelVector.Block(0, 0, 10, 8, compact)) +
            (0 until 10).map { PixelVector.Block(it, 8, 1, 1, scattered) } +
            (0 until 10).map { PixelVector.Block(it, 9, 1, 1, scattered) }

        assertTrue("the fixture does not set the trap", blocks.count { it.colour == scattered } > 1)

        val svg = PixelVector.svg(blocks, 10, 10, 1)

        assertTrue("the ground should be the compact colour, in $svg", svg.contains("fill=\"#111111\"/>"))
        assertEquals("only the scattered blocks are drawn, plus the ground", 21, Regex("<rect ").findAll(svg).count())
    }

    /** One `fill` per remaining colour rather than per rectangle. */
    @Test
    fun `blocks are grouped by colour`() {
        val blocks = PixelVector.merge(grid(8, 8) { x, y -> (x + y) % 2 }, 8, 8)
        val svg = PixelVector.svg(blocks, 8, 8, 4)

        assertEquals("the ground needs no group of its own", 1, Regex("<g fill=").findAll(svg).count())
        assertEquals("32 drawn plus one ground", 33, Regex("<rect ").findAll(svg).count())
    }

    /**
     * The document, read back and painted, is the picture the blocks describe.
     *
     * Everything above tests the merge or one attribute of the output. This tests the *file* —
     * that what a viewer will draw from it, in the order the file gives, is the same image. It
     * is the assertion that covers the ground trick: paint the ground last, or scale one
     * coordinate and not another, and every other test here still passes.
     */
    @Test
    fun `painting the document back gives the picture the blocks describe`() {
        val cols = 23
        val rows = 17
        val cell = 3
        val cells = grid(cols, rows) { x, y ->
            when {
                (x - 11) * (x - 11) + (y - 8) * (y - 8) < 20 -> 0xE0D0C0
                (x + y) % 3 == 0 -> 0x203040
                else -> 0x102030
            }
        }
        val blocks = PixelVector.merge(cells, cols, rows)
        val svg = PixelVector.svg(blocks, cols, rows, cell)

        // Paint the document: every rect in file order, later ones over earlier ones.
        val painted = IntArray(cols * cell * rows * cell) { -1 }
        var fill = 0
        Regex("""<g fill="#([0-9A-F]{6})">|<rect x="(\d+)" y="(\d+)" width="(\d+)" height="(\d+)"/>|<rect width="(\d+)" height="(\d+)" fill="#([0-9A-F]{6})"/>""")
            .findAll(svg)
            .forEach { m ->
                when {
                    m.groupValues[1].isNotEmpty() -> fill = m.groupValues[1].toInt(16)
                    m.groupValues[6].isNotEmpty() -> {
                        val colour = m.groupValues[8].toInt(16)
                        painted.fill(colour)
                    }
                    else -> {
                        val x = m.groupValues[2].toInt()
                        val y = m.groupValues[3].toInt()
                        val w = m.groupValues[4].toInt()
                        val h = m.groupValues[5].toInt()
                        for (py in y until y + h) {
                            for (px in x until x + w) painted[py * cols * cell + px] = fill
                        }
                    }
                }
            }

        // And the same picture, expanded straight from the cells.
        for (y in 0 until rows * cell) {
            for (x in 0 until cols * cell) {
                val expected = cells[(y / cell) * cols + (x / cell)]
                assertEquals(
                    "the document disagrees at $x,$y",
                    expected,
                    painted[y * cols * cell + x],
                )
            }
        }
    }

    @Test
    fun `a colour is written as six hex digits with the alpha dropped`() {
        val svg = PixelVector.svg(
            listOf(PixelVector.Block(0, 0, 1, 1, 0xFF3366CC.toInt())),
            1, 1, 10,
        )

        assertTrue("expected #3366CC in $svg", svg.contains("fill=\"#3366CC\""))
    }
}
