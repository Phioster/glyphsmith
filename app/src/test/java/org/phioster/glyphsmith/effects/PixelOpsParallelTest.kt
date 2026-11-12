package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

/**
 * The blur and the light it feeds run their rows on several threads once a picture is tall
 * enough. Sharing the rows out must not change one pixel, so each is held against the plain
 * loop it replaced, on a picture tall enough to be shared out and with a height that does not
 * divide evenly.
 */
class PixelOpsParallelTest {

    private val width = 61
    private val height = 601

    private fun picture(seed: Int) = Pixels(
        IntArray(width * height) { i ->
            val h = (i * 374761393 + seed * 668265263).let { (it xor (it ushr 13)) * 1274126177 }
            (h and 0x00FFFFFF) or (((h ushr 24) or 0x40) shl 24)
        },
        width,
        height,
    )

    private fun naiveConvolve(source: Pixels, weights: FloatArray, horizontal: Boolean): IntArray {
        val radius = weights.size / 2
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in weights.indices) {
                val sx = if (horizontal) (x + k - radius).coerceIn(0, width - 1) else x
                val sy = if (horizontal) y else (y + k - radius).coerceIn(0, height - 1)
                val pixel = source.data[sy * width + sx]
                r += PixelOps.redOf(pixel) * weights[k]
                g += PixelOps.greenOf(pixel) * weights[k]
                b += PixelOps.blueOf(pixel) * weights[k]
            }
            PixelOps.argb(255, r.toInt(), g.toInt(), b.toInt())
        }
    }

    @Test
    fun `a blur shared out over threads is the blur it was`() {
        val source = picture(1)
        val weights = PixelOps.inversePowerKernel(9, 1.6f, 0.2f, 0.4f, normalise = true)
        assertArrayEquals(naiveConvolve(source, weights, true), PixelOps.convolve(source, weights, true).data)
        assertArrayEquals(naiveConvolve(source, weights, false), PixelOps.convolve(source, weights, false).data)
    }

    @Test
    fun `light added over threads is the light it was`() {
        val base = picture(2)
        val light = picture(3)
        val expected = IntArray(width * height) { i ->
            val b = base.data[i]
            val l = light.data[i]
            val r = PixelOps.redOf(l) * 0.7f
            val g = PixelOps.greenOf(l) * 0.7f
            val bl = PixelOps.blueOf(l) * 0.7f
            PixelOps.argb(
                maxOf(PixelOps.alphaOf(b), ((r + g + bl) / 3f).toInt().coerceIn(0, 255)),
                (PixelOps.redOf(b) + r).toInt(),
                (PixelOps.greenOf(b) + g).toInt(),
                (PixelOps.blueOf(b) + bl).toInt(),
            )
        }
        PixelOps.addLight(base, light, 0.7f)
        assertArrayEquals(expected, base.data)
    }

    @Test
    fun `the soft blur gives the same picture every time, however its rows fall`() {
        val first = PixelOps.blurRgba(picture(4), 5).data
        repeat(3) { assertArrayEquals(first, PixelOps.blurRgba(picture(4), 5).data) }
    }

    @Test
    fun `halftone over threads is the halftone of each strip by itself`() {
        // A strip too short to be shared out is worked on one thread; the dots depend on where
        // a pixel sits, so the strip has to be the top of the picture to compare.
        val params = CmykHalftoneParams(enabled = true)
        val whole = CmykHalftone.apply(picture(5), params).data
        val strip = 200
        val top = CmykHalftone.apply(Pixels(picture(5).data.copyOf(width * strip), width, strip), params).data
        assertArrayEquals(top, whole.copyOf(width * strip))
    }
}
