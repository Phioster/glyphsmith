package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

class CrtWarpTest {

    private val side = 301 // odd, so there is an exact centre pixel to check
    private val ctx = RenderContext(maxSide = 1024)

    private fun flat(colour: Int = 0xFF808080.toInt()) =
        Pixels(IntArray(side * side) { colour }, side, side)

    private fun centreOf(pixels: Pixels): Int =
        pixels.data[(side / 2) * side + side / 2]

    private fun luma(pixel: Int): Int =
        ((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)

    /**
     * The middle of the screen is the fixed point of a barrel warp — displacement grows with the
     * square of the radius, and at the centre the radius is zero. If the centre moved, the mapping
     * would be a zoom rather than a curvature.
     */
    @Test
    fun `the centre pixel is untouched at any curvature`() {
        for (curvature in listOf(0, 25, 60, 100)) {
            val out = CrtWarp.apply(
                flat(),
                CrtWarpParams(enabled = true, warpCurvature = curvature, vignetteIntensity = 0),
                ctx,
            )
            assertEquals("curvature $curvature moved the centre", 0xFF808080.toInt(), centreOf(out))
        }
    }

    @Test
    fun `the corner shadow darkens the corners and not the middle`() {
        val out = CrtWarp.apply(
            flat(),
            CrtWarpParams(enabled = true, warpCurvature = 0, vignetteIntensity = 80),
            ctx,
        )
        val corner = out.data[0]
        assertTrue("the corner was not darkened", luma(corner) < luma(0xFF808080.toInt()))
        assertEquals("the centre was darkened", 0xFF808080.toInt(), centreOf(out))
    }

    /**
     * Curvature pushes the sampled point outside the source near the corners. Without bleed that
     * has to be black — a stretched edge pixel there would look like a smear rather than a bezel.
     */
    @Test
    fun `without bleed the area past the glass is black`() {
        val out = CrtWarp.apply(
            flat(0xFFFFFFFF.toInt()),
            CrtWarpParams(
                enabled = true, warpCurvature = 100, vignetteIntensity = 0, bezelBleed = 0,
            ),
            ctx,
        )
        assertEquals("the corner should be off the glass", 0xFF000000.toInt(), out.data[0])
    }

    @Test
    fun `with bleed the corner is filled instead of black`() {
        val out = CrtWarp.apply(
            flat(0xFFFFFFFF.toInt()),
            CrtWarpParams(
                enabled = true, warpCurvature = 100, vignetteIntensity = 0, bezelBleed = 100,
            ),
            ctx,
        )
        assertTrue("the corner should carry the stretched edge", out.data[0] != 0xFF000000.toInt())
    }

    /** Both controls at zero is not a look, it is a flat panel — and must cost nothing. */
    @Test
    fun `no curvature and no shadow is a no-op`() {
        val input = flat()
        val before = input.data.copyOf()
        val out = CrtWarp.apply(
            input,
            CrtWarpParams(enabled = true, warpCurvature = 0, vignetteIntensity = 0),
            ctx,
        )
        assertTrue(out.data.contentEquals(before))
    }

    /**
     * Bilinear sampling is the reason this looks smooth rather than staircased. On a sharp edge it
     * has to produce values that were in neither neighbour — that is what interpolation means, and
     * nearest-neighbour sampling could not.
     */
    /**
     * The edge is deliberately **off** centre.
     *
     * A vertical edge through the middle is the one place a barrel warp does not interpolate
     * horizontally: there `nx` is zero, so the sampled x lands exactly on a pixel centre however
     * much the glass bulges, and the test would fail while the sampler was perfectly correct. Off
     * centre, the radius is non-zero and the sample falls between two pixels, which is where
     * bilinear differs from nearest-neighbour at all.
     */
    @Test
    fun `sampling interpolates rather than snapping to a neighbour`() {
        // A hard vertical split at a quarter width, black on the left and white on the right.
        val data = IntArray(side * side) { i ->
            if (i % side < side / 4) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val out = CrtWarp.apply(
            Pixels(data, side, side),
            CrtWarpParams(enabled = true, warpCurvature = 40, vignetteIntensity = 0),
            ctx,
        )
        val intermediate = out.data.count { pixel ->
            val r = (pixel shr 16) and 0xFF
            r in 40..215
        }
        assertTrue("no interpolated pixels — sampling is not bilinear", intermediate > 0)
    }
}
