package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import kotlin.math.abs

class FilmGrainTest {

    private fun flat(level: Int) = Pixels(IntArray(64 * 64) { PixelOps.argb(255, level, level, level) }, 64, 64)

    private fun spread(level: Int): Float {
        val out = PostProcessing.apply(
            flat(level),
            PostProcessingParams(enabled = true, grain = 100, grainKind = GrainKind.FILM),
        )
        return out.data.map { abs(PixelOps.redOf(it) - level) }.average().toFloat()
    }

    @Test
    fun `film grain lives in the mid-tones`() {
        assertTrue(spread(128) > spread(15) * 1.8f)
        assertTrue(spread(128) > spread(240) * 1.8f)
    }

    @Test
    fun `film grain is the same every time`() {
        val p = PostProcessingParams(enabled = true, grain = 60, grainKind = GrainKind.FILM, seed = 4)
        assertArrayEquals(PostProcessing.apply(flat(100), p).data, PostProcessing.apply(flat(100), p).data)
    }

    @Test
    fun `uniform grain is unchanged by the new kind`() {
        val p = PostProcessingParams(enabled = true, grain = 50, seed = 9)
        val expected = flat(100).also { px ->
            val random = kotlin.random.Random(9)
            for (i in px.data.indices) {
                val n = ((random.nextFloat() - 0.5f) * 255f * 0.5f)
                val v = (100f + n).toInt().coerceIn(0, 255)
                px.data[i] = PixelOps.argb(255, v, v, v)
            }
        }
        assertArrayEquals(expected.data, PostProcessing.apply(flat(100), p).data)
    }
}
