package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

/** The per-channel textures: they bring red, green and blue into a grey picture. */
class SubtextureRgbTest {

    private fun grey(w: Int, h: Int): Pixels =
        Pixels(IntArray(w * h) { PixelOps.argb(255, 128, 128, 128) }, w, h)

    @Test
    fun `a triad lights one channel per third of its width`() {
        val lit = (0 until 3).map { ch -> Subtexture.rgbTexture(TextureKind.RGB_TRIAD, 0.1f, 0f, 0, 0, 1, ch) }
        assertTrue(lit[0] > 0.5f && lit[1] < 0.5f && lit[2] < 0.5f)
        val blue = Subtexture.rgbTexture(TextureKind.RGB_TRIAD, 0.9f, 0f, 0, 0, 1, 2)
        assertTrue(blue > 0.5f)
    }

    @Test
    fun `the triad turns a grey picture into coloured stripes`() {
        val out = Subtexture.apply(
            grey(12, 4),
            SubtextureParams(enabled = true, kind = TextureKind.RGB_TRIAD, scale = 6, intensity = 100),
        )
        val pixel = out.data[0]
        val r = PixelOps.redOf(pixel)
        val g = PixelOps.greenOf(pixel)
        val b = PixelOps.blueOf(pixel)
        assertTrue("one channel stands out: $r $g $b", maxOf(r, g, b) - minOf(r, g, b) > 80)
        val colours = out.data.map { maxOf(PixelOps.redOf(it), PixelOps.greenOf(it), PixelOps.blueOf(it)) }
        assertTrue(colours.all { it > 0 })
    }

    @Test
    fun `the slot mask has dark gaps between its slots`() {
        val inGap = (0 until 3).map { Subtexture.rgbTexture(TextureKind.RGB_SLOT_MASK, 0.1f, 0.9f, 0, 0, 1, it) }
        assertTrue(inGap.all { it < 0.5f })
    }

    @Test
    fun `the old kinds still render the same grey for every channel`() {
        val out = Subtexture.apply(
            grey(8, 8),
            SubtextureParams(enabled = true, kind = TextureKind.CRT_STRIPES, scale = 4, intensity = 100),
        )
        out.data.forEach { assertEquals(PixelOps.redOf(it), PixelOps.blueOf(it)) }
    }
}
