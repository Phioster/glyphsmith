package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class PrintEffectsTest {

    private val size = 240

    private fun flat(value: Int, alpha: Int = 0xFF) = Pixels(IntArray(size * size) { PixelOps.argb(alpha, value, value, value) }, size, size)

    /** A white disc on black. */
    private fun disc() = Pixels(
        IntArray(size * size) {
            val dx = it % size - size / 2
            val dy = it / size - size / 2
            val v = if (dx * dx + dy * dy < size * size / 16) 255 else 0
            PixelOps.argb(0xFF, v, v, v)
        },
        size, size,
    )

    private val all: List<(Pixels, Boolean) -> Pixels> = listOf(
        { p, on -> Cyanotype.apply(p, CyanotypeParams(enabled = on)) },
        { p, on -> Blueprint.apply(p, BlueprintParams(enabled = on)) },
        { p, on -> HoneycombScreen.apply(p, HoneycombScreenParams(enabled = on)) },
        { p, on -> PetrolSheen.apply(p, PetrolSheenParams(enabled = on)) },
        { p, on -> CrumpledPrint.apply(p, CrumpledPrintParams(enabled = on)) },
    )

    @Test
    fun `switched off, each leaves the picture untouched`() {
        all.forEach { effect -> assertTrue(disc().data.contentEquals(effect(disc(), false).data)) }
    }

    @Test
    fun `each changes the picture, keeps its transparency and gives the same result twice`() {
        all.forEachIndexed { i, effect ->
            val once = effect(disc(), true)
            assertFalse("effect $i", disc().data.contentEquals(once.data))
            assertTrue("effect $i", once.data.contentEquals(effect(disc(), true).data))
            assertTrue("effect $i", effect(flat(140, alpha = 0x80), true).data.all { it ushr 24 == 0x80 })
        }
    }

    @Test
    fun `a cyanotype is blue where it is coated and paper at the bare edge`() {
        val whole = Cyanotype.apply(flat(90), CyanotypeParams(enabled = true, border = 0, grain = 0))
        assertTrue(whole.data.all { PixelOps.blueOf(it) > PixelOps.redOf(it) + 30 })

        val edged = Cyanotype.apply(flat(90), CyanotypeParams(enabled = true, border = 100, grain = 0))
        val corner = edged.data[0]
        assertTrue(PixelOps.redOf(corner) > 200 && PixelOps.blueOf(corner) > 190)
        val middle = edged.data[size / 2 * size + size / 2]
        assertTrue(PixelOps.blueOf(middle) > PixelOps.redOf(middle) + 30)
    }

    @Test
    fun `a blueprint draws a line at an edge and none on an even field`() {
        val out = Blueprint.apply(disc(), BlueprintParams(enabled = true, grid = 0, fill = 0))
        val row = size / 2
        val rim = size / 2 + size / 4
        assertTrue((rim - 3..rim + 3).any { PixelOps.redOf(out.data[row * size + it]) > 180 })
        val far = out.data[row * size + size - 6]
        assertTrue(PixelOps.redOf(far) < 60 && PixelOps.blueOf(far) > 90)
    }

    @Test
    fun `honeycomb cells are large where the picture is bright and gone where it is black`() {
        val bright = HoneycombScreen.apply(flat(255), HoneycombScreenParams(enabled = true)).data.count { PixelOps.greenOf(it) > 128 }
        val dark = HoneycombScreen.apply(flat(0), HoneycombScreenParams(enabled = true)).data.count { PixelOps.greenOf(it) > 128 }
        assertTrue("$bright of ${size * size}", bright > size * size * 6 / 10)
        assertEquals(0, dark)
    }

    @Test
    fun `crumpled paper is lit on one side of a fold and shaded on the other`() {
        val out = CrumpledPrint.apply(flat(128), CrumpledPrintParams(enabled = true, paper = 0)).data
        assertTrue(out.count { PixelOps.greenOf(it) > 140 } > size * size / 20)
        assertTrue(out.count { PixelOps.greenOf(it) < 116 } > size * size / 20)
    }

    @Test
    fun `the sheen runs through over a loop, closes on itself and holds when told to`() {
        val on = PetrolSheenParams(enabled = true)
        val start = PetrolSheen.apply(flat(40), on, 0f).data
        assertFalse(start.contentEquals(PetrolSheen.apply(flat(40), on, 0.5f).data))
        assertTrue(start.contentEquals(PetrolSheen.apply(flat(40), on, 1f).data))
        val held = on.copy(flow = 0)
        assertTrue(PetrolSheen.apply(flat(40), held, 0f).data.contentEquals(PetrolSheen.apply(flat(40), held, 0.5f).data))
    }
}
