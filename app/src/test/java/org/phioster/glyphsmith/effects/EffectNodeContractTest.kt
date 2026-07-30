package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

/**
 * The invariants every one of the new passes has to hold, checked against all five at once.
 *
 * Written as one file rather than five near-identical ones because the point is the *contract*, and
 * a contract stated in one place cannot drift between implementations. Anything specific to a pass
 * lives in its own test.
 */
class EffectNodeContractTest {

    private val side = 300

    private fun ctx(time: Float = 0f) = RenderContext(maxSide = 1024, time = time)

    /** A gradient with some colour in it, so a pass that only moves luminance still shows up. */
    private fun image(): Pixels {
        val data = IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            (0xFF shl 24) or ((x * 255 / side) shl 16) or ((y * 255 / side) shl 8) or 0x60
        }
        return Pixels(data, side, side)
    }

    /** Each pass, applied with a setting that plainly does something. */
    private val active: List<Pair<String, (Pixels, RenderContext) -> Pixels>> = listOf(
        "modulation lines" to { p, c ->
            ModulationLines.apply(p, ModulationLinesParams(enabled = true), c)
        },
        "crt warp" to { p, c -> CrtWarp.apply(p, CrtWarpParams(enabled = true), c) },
        "blue noise" to { p, c -> BlueNoiseDither.apply(p, BlueNoiseDitherParams(enabled = true), c) },
        "spot print" to { p, c -> SpotColorPrint.apply(p, SpotColorPrintParams(enabled = true), c) },
        "colour depth" to { p, c -> ColorDepth.apply(p, ColorDepthParams(enabled = true), c) },
    )

    /** The same passes, disabled. */
    private val inactive: List<Pair<String, (Pixels, RenderContext) -> Pixels>> = listOf(
        "modulation lines" to { p, c -> ModulationLines.apply(p, ModulationLinesParams(), c) },
        "crt warp" to { p, c -> CrtWarp.apply(p, CrtWarpParams(), c) },
        "blue noise" to { p, c -> BlueNoiseDither.apply(p, BlueNoiseDitherParams(), c) },
        "spot print" to { p, c -> SpotColorPrint.apply(p, SpotColorPrintParams(), c) },
        "colour depth" to { p, c -> ColorDepth.apply(p, ColorDepthParams(), c) },
    )

    /**
     * Disabled must return the very same buffer, not an equal one.
     *
     * Identity, because that is what the chain reads: `NodePipeline` recycles the input only when a
     * pass hands back a *different* buffer, so a disabled pass that copied would quietly cost a
     * full allocation per frame for nothing.
     */
    @Test
    fun `a disabled pass returns the identical buffer`() {
        for ((name, pass) in inactive) {
            val input = image()
            val out = pass(input, ctx())
            assertSame("$name changed the buffer while disabled", input.data, out.data)
        }
    }

    @Test
    fun `an enabled pass changes the image`() {
        for ((name, pass) in active) {
            val input = image()
            val before = input.data.copyOf()
            val out = pass(input, ctx())
            assertTrue("$name did nothing when enabled", !out.data.contentEquals(before))
        }
    }

    @Test
    fun `every pass keeps the image size`() {
        for ((name, pass) in active) {
            val out = pass(image(), ctx())
            assertEquals("$name changed the width", side, out.width)
            assertEquals("$name changed the height", side, out.height)
            assertEquals("$name changed the buffer length", side * side, out.data.size)
        }
    }

    /**
     * Deterministic, which is stronger here than it looks: these passes run their rows in parallel,
     * so a shared random generator or any dependence on which band finished first would show up as
     * a different image on a second run. That is precisely the bug this catches.
     */
    @Test
    fun `every pass is reproducible`() {
        for ((name, pass) in active) {
            val first = pass(image(), ctx()).data
            val second = pass(image(), ctx()).data
            assertArrayEquals("$name is not reproducible", first, second)
        }
    }

    @Test
    fun `every pass leaves alpha alone`() {
        for ((name, pass) in active) {
            val out = pass(image(), ctx())
            assertTrue(
                "$name lost the alpha channel",
                out.data.all { ((it ushr 24) and 0xFF) == 0xFF },
            )
        }
    }

    /** A one-pixel image is a real case — a preset thumbnail of a solid colour reaches this. */
    @Test
    fun `every pass survives a one pixel image`() {
        for ((name, pass) in active) {
            val tiny = Pixels(intArrayOf(0xFF804020.toInt()), 1, 1)
            val out = pass(tiny, ctx())
            assertEquals("$name broke on a 1×1 image", 1, out.data.size)
        }
    }

    /**
     * Only the modulation pass reads the clock, and it must actually read it — that path exists
     * solely so an animation moves, and nothing else in the test suite can prove it does.
     *
     * The time is 0.3 rather than a half or a quarter on purpose. Phase advances by
     * `time * speed / 10` cycles, so at a speed of 80 a time of 0.5 is *exactly four whole cycles*
     * and the wave is back where it started — correct behaviour, and it closes an animation loop
     * seamlessly, but it would make this assertion fail against a sampler that was working.
     */
    @Test
    fun `the modulation pass moves with the clock`() {
        val params = ModulationLinesParams(enabled = true, waveSpeed = 80, waveMix = 90)
        val atStart = ModulationLines.apply(image(), params, ctx(time = 0f)).data
        val laterOn = ModulationLines.apply(image(), params, ctx(time = 0.3f)).data

        assertTrue("the wave did not move between frames", !atStart.contentEquals(laterOn))
    }

    /**
     * The other half of that: a whole number of cycles *must* land back on the same image, which is
     * what lets an animated loop close without a visible seam.
     */
    @Test
    fun `a whole number of cycles returns the wave to its start`() {
        val params = ModulationLinesParams(enabled = true, waveSpeed = 80, waveMix = 90)
        val atStart = ModulationLines.apply(image(), params, ctx(time = 0f)).data
        val fourCycles = ModulationLines.apply(image(), params, ctx(time = 0.5f)).data

        assertArrayEquals("the loop does not close", atStart, fourCycles)
    }

    @Test
    fun `the modulation pass holds still when its speed is zero`() {
        val params = ModulationLinesParams(enabled = true, waveSpeed = 0)
        val atStart = ModulationLines.apply(image(), params, ctx(time = 0f)).data
        val laterOn = ModulationLines.apply(image(), params, ctx(time = 0.5f)).data

        assertArrayEquals("a speed of zero must not move", atStart, laterOn)
    }
}
