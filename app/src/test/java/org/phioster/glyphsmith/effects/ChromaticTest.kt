package org.phioster.glyphsmith.effects

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class ChromaticTest {

    private fun stripe(width: Int = 32, height: Int = 4, color: Int = 0xFFFFFFFF.toInt()) =
        Pixels(
            IntArray(width * height) { if ((it % width) in 12..16) color else 0xFF000000.toInt() },
            width,
            height,
        )

    private val on = ChromaticParams(enabled = true, maxDisplace = 6)

    @Test
    fun `disabled leaves the pixels alone`() {
        val source = stripe()
        val before = source.data.toList()
        assertEquals(before, Chromatic.apply(source, on.copy(enabled = false)).data.toList())
    }

    /**
     * The channel positions were added to an effect that already had saved presets. Their
     * defaults have to reproduce the old symmetric split exactly — red forward, green still,
     * blue back — or every preset using chromatic would quietly render differently.
     */
    @Test
    fun `the default positions reproduce the old symmetric split`() {
        val out = Chromatic.apply(stripe(), on)
        // Red leads the stripe, blue trails it: the two must not sit on the same columns.
        val reds = out.data.indices.filter { PixelOps.redOf(out.data[it]) > 128 }
        val blues = out.data.indices.filter { PixelOps.blueOf(out.data[it]) > 128 }
        assertTrue("no red anywhere", reds.isNotEmpty())
        assertTrue("no blue anywhere", blues.isNotEmpty())
        assertTrue("red and blue landed on the same columns", reds != blues)
        assertTrue("red does not lead blue", reds.first() < blues.first())
    }

    @Test
    fun `max displace gates the channels entirely`() {
        val moved = Chromatic.apply(stripe(), on.copy(maxDisplace = 0, redChannel = 0, blueChannel = 100))
        val untouched = stripe()
        assertEquals(untouched.data.toList(), moved.data.toList())
    }

    @Test
    fun `all channels at the middle is no split`() {
        val aligned = Chromatic.apply(
            stripe(),
            on.copy(redChannel = 50, greenChannel = 50, blueChannel = 50),
        )
        assertEquals(stripe().data.toList(), aligned.data.toList())
    }

    /**
     * The property the tutorial makes a point of: this is a real channel split, so moving a
     * channel that the picture does not contain does nothing at all.
     */
    @Test
    fun `a channel absent from the image does not move`() {
        val blueOnly = stripe(color = 0xFF0000FF.toInt())
        val movedRed = Chromatic.apply(blueOnly, on.copy(redChannel = 0))
        val movedRedFurther = Chromatic.apply(stripe(color = 0xFF0000FF.toInt()), on.copy(redChannel = 100))
        assertEquals(
            "the red slider moved a picture with no red in it",
            movedRed.data.toList(),
            movedRedFurther.data.toList(),
        )
    }

    @Test
    fun `moving the blue channel does change a blue image`() {
        val a = Chromatic.apply(stripe(color = 0xFF0000FF.toInt()), on.copy(blueChannel = 0))
        val b = Chromatic.apply(stripe(color = 0xFF0000FF.toInt()), on.copy(blueChannel = 100))
        assertTrue("the blue slider did nothing", a.data.toList() != b.data.toList())
    }

    /** Two channels together and one away is how yellow or cyan fringing is made. */
    @Test
    fun `aligning two channels puts them on the same columns`() {
        val out = Chromatic.apply(
            stripe(),
            on.copy(redChannel = 100, greenChannel = 100, blueChannel = 0),
        )
        val reds = out.data.indices.filter { PixelOps.redOf(out.data[it]) > 128 }
        val greens = out.data.indices.filter { PixelOps.greenOf(out.data[it]) > 128 }
        assertEquals("red and green were meant to coincide", reds, greens)
    }

    /** A preset written before the rename stores the value under `offset`. */
    @Test
    fun `the old key still loads`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<ChromaticParams>("""{"enabled":true,"offset":9}""")
        assertEquals(9, decoded.maxDisplace)
        assertEquals(100, decoded.redChannel)
        assertEquals(50, decoded.greenChannel)
        assertEquals(0, decoded.blueChannel)
    }
}
