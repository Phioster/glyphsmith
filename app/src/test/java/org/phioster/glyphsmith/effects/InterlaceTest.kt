package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class InterlaceTest {

    /** Every row a distinct flat colour, so a row that moved or was replaced is obvious. */
    private fun rows(width: Int = 16, height: Int = 8) = Pixels(
        IntArray(width * height) { (0xFF shl 24) or ((it / width) * 0x102030) },
        width,
        height,
    )

    private val on = InterlaceParams(enabled = true, shift = 25, density = 100)

    private fun rowOf(p: Pixels, y: Int) = p.data.toList().subList(y * p.width, (y + 1) * p.width)

    @Test
    fun `disabled leaves everything alone`() {
        val source = rows()
        val before = source.data.toList()
        assertEquals(before, Interlace.apply(source, on.copy(enabled = false)).data.toList())
    }

    /**
     * The property that separates this from a band shift: only one field is touched, so the
     * other field's rows come through untouched however heavy the damage.
     */
    @Test
    fun `the untouched field survives intact`() {
        val original = rows()
        val out = Interlace.apply(rows(), on.copy(oddField = true))
        for (y in 0 until out.height step 2) {
            assertEquals("even row $y was damaged", rowOf(original, y), rowOf(out, y))
        }
    }

    @Test
    fun `choosing the other field moves the damage`() {
        val original = rows()
        val out = Interlace.apply(rows(), on.copy(oddField = false))
        for (y in 1 until out.height step 2) {
            assertEquals("odd row $y was damaged", rowOf(original, y), rowOf(out, y))
        }
    }

    @Test
    fun `something in the chosen field actually changes`() {
        val original = rows()
        val out = Interlace.apply(rows(), on)
        val changed = (1 until out.height step 2).any { rowOf(original, it) != rowOf(out, it) }
        assertTrue("the damaged field came through unchanged", changed)
    }

    /** A shifted row wraps, so it keeps exactly the pixels it started with. */
    @Test
    fun `a shifted row keeps its own pixels`() {
        val original = rows()
        val out = Interlace.apply(rows(), on.copy(tearColor = 0))
        for (y in 1 until out.height step 2) {
            assertEquals(
                "row $y gained or lost a pixel",
                rowOf(original, y).sorted(),
                rowOf(out, y).sorted(),
            )
        }
    }

    /** A dropped field shows the line above standing in for the missing one. */
    @Test
    fun `freeze repeats the line above`() {
        val out = Interlace.apply(rows(), on.copy(freeze = true, shift = 0, tearColor = 0))
        for (y in 1 until out.height step 2) {
            assertEquals("row $y did not take the row above", rowOf(out, y - 1), rowOf(out, y))
        }
    }

    @Test
    fun `density zero leaves the image alone`() {
        val source = rows()
        val before = source.data.toList()
        assertEquals(before, Interlace.apply(source, on.copy(density = 0)).data.toList())
    }

    @Test
    fun `the same seed gives the same damage`() {
        val a = Interlace.apply(rows(), on.copy(seed = 5, density = 60))
        val b = Interlace.apply(rows(), on.copy(seed = 5, density = 60))
        assertEquals(a.data.toList(), b.data.toList())
    }

    @Test
    fun `tear colour leaves green alone so the line keeps its brightness`() {
        val original = rows()
        val out = Interlace.apply(rows(), on.copy(shift = 0, tearColor = 100))
        for (y in 1 until out.height step 2) {
            for (x in 0 until out.width) {
                val before = original.data[y * out.width + x]
                val after = out.data[y * out.width + x]
                assertEquals(
                    "green moved on row $y",
                    PixelOps.greenOf(before),
                    PixelOps.greenOf(after),
                )
            }
        }
    }

    @Test
    fun `alpha survives`() {
        val translucent = Pixels(IntArray(16 * 8) { 0x80304050.toInt() }, 16, 8)
        val out = Interlace.apply(translucent, on)
        assertTrue(out.data.all { PixelOps.alphaOf(it) == 0x80 })
    }
}
