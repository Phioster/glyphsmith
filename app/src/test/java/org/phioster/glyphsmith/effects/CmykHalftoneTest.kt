package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class CmykHalftoneTest {

    private fun flat(color: Int, width: Int = 48, height: Int = 48) =
        Pixels(IntArray(width * height) { color }, width, height)

    private fun meanLuminance(p: Pixels): Float =
        p.data.map { PixelOps.luminance(it) }.average().toFloat()

    private val on = CmykHalftoneParams(enabled = true, frequency = 6)

    @Test
    fun `disabled leaves every pixel untouched`() {
        val source = flat(0xFF4080C0.toInt())
        val before = source.data.toList()
        CmykHalftone.apply(source, on.copy(enabled = false))
        assertEquals(before, source.data.toList())
    }

    @Test
    fun `white paper stays white`() {
        // No ink is asked for anywhere, so no dot may be laid down.
        val out = CmykHalftone.apply(flat(0xFFFFFFFF.toInt()), on)
        assertTrue(out.data.all { PixelOps.redOf(it) == 255 })
    }

    /**
     * A flat mid-grey must break into dots. If every pixel comes out the same, the screen is
     * not being applied at all and the effect has quietly become a colour transform.
     */
    @Test
    fun `a flat tone is broken into a screen`() {
        val out = CmykHalftone.apply(flat(0xFF808080.toInt()), on)
        assertTrue("the screen produced a flat field", out.data.toSet().size > 1)
    }

    /** Darker in, more ink down — the one monotonic relationship the effect must keep. */
    @Test
    fun `darker input lays down more ink`() {
        val light = meanLuminance(CmykHalftone.apply(flat(0xFFC0C0C0.toInt()), on))
        val mid = meanLuminance(CmykHalftone.apply(flat(0xFF808080.toInt()), on))
        val dark = meanLuminance(CmykHalftone.apply(flat(0xFF303030.toInt()), on))

        assertTrue("mid is not darker than light ($mid vs $light)", mid < light)
        assertTrue("dark is not darker than mid ($dark vs $mid)", dark < mid)
    }

    /**
     * Grey component replacement is the whole reason a K plate exists: with it, a neutral is
     * carried by black, and the result stays neutral. Without it the same grey is printed by
     * cyan, magenta and yellow together and drifts off-colour.
     */
    @Test
    fun `black ink keeps a neutral neutral`() {
        val out = CmykHalftone.apply(flat(0xFF808080.toInt()), on.copy(blackInk = 100))
        out.data.forEach { pixel ->
            val r = PixelOps.redOf(pixel)
            val g = PixelOps.greenOf(pixel)
            val b = PixelOps.blueOf(pixel)
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            assertTrue("neutral drifted to ($r,$g,$b)", spread <= 2)
        }
    }

    @Test
    fun `dropping the black ink pushes the same grey off-neutral`() {
        val withK = CmykHalftone.apply(flat(0xFF808080.toInt()), on.copy(blackInk = 100))
        val withoutK = CmykHalftone.apply(flat(0xFF808080.toInt()), on.copy(blackInk = 0))
        assertTrue(
            "black ink made no difference",
            withK.data.toList() != withoutK.data.toList(),
        )
    }

    @Test
    fun `a finer screen puts more dots in the same area`() {
        fun edges(p: Pixels): Int =
            (1 until p.data.size).count { p.data[it] != p.data[it - 1] }

        val coarse = edges(CmykHalftone.apply(flat(0xFF808080.toInt()), on.copy(frequency = 16)))
        val fine = edges(CmykHalftone.apply(flat(0xFF808080.toInt()), on.copy(frequency = 4)))
        assertTrue("a finer screen produced no more dots ($fine vs $coarse)", fine > coarse)
    }

    @Test
    fun `mid-tone gain moves the ink coverage`() {
        val neutral = meanLuminance(CmykHalftone.apply(flat(0xFF909090.toInt()), on))
        val lifted = meanLuminance(
            CmykHalftone.apply(flat(0xFF909090.toInt()), on.copy(midtoneGain = 200)),
        )
        val crushed = meanLuminance(
            CmykHalftone.apply(flat(0xFF909090.toInt()), on.copy(midtoneGain = 40)),
        )
        assertTrue("gain did nothing", lifted != neutral || crushed != neutral)
        // Dot gain darkens the mid-tones: more gain is more ink, less gain is less. Getting
        // this backwards would still "do something", which is why the direction is asserted.
        assertTrue("more gain did not add ink ($lifted vs $crushed)", lifted < crushed)
    }

    @Test
    fun `alpha is carried through`() {
        val translucent = Pixels(IntArray(16 * 16) { 0x80404040.toInt() }, 16, 16)
        val out = CmykHalftone.apply(translucent, on)
        assertTrue(out.data.all { PixelOps.alphaOf(it) == 0x80 })
    }
}
