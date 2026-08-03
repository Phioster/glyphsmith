package org.phioster.glyphsmith.ascii

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderMode

/**
 * Surprise Me, as a value rather than a button.
 *
 * The roll is general-purpose, so it rolls the general-purpose mode: a Pixel Dither look,
 * whatever the session happened to be in when the button was pressed. Glyph Art is reached
 * deliberately, and the mode is a parameter so that a glyph-flavoured roll stays possible
 * without the button having to change.
 */
class RandomLookTest {

    private val seeded get() = Random(20260803)

    @Test
    fun `a rolled look is a pixel dither`() {
        assertEquals(RenderMode.PurePixel, RandomLook.roll(RenderSettings(), seeded).renderMode)
    }

    /** Even from a session that was sitting in Glyph Art: the roll decides, the session does not. */
    @Test
    fun `a roll from a glyph session still rolls a pixel dither`() {
        val glyph = RenderSettings(renderMode = RenderMode.GlyphMatrix)

        assertEquals(RenderMode.PurePixel, RandomLook.roll(glyph, seeded).renderMode)
    }

    /**
     * Pressing the button again is the normal way to use it, and every press has to land in
     * the same mode — a roll that walked out of Pixel Dither after the first press would make
     * the general-purpose button a way to end up in Glyph Art by accident.
     */
    @Test
    fun `rolling again stays in pixel dither`() {
        var look = RenderSettings()

        repeat(5) { press ->
            look = RandomLook.roll(look, Random(press))

            assertEquals(RenderMode.PurePixel, look.renderMode)
        }
    }

    @Test
    fun `the rolled mode is the one the mode names as default`() {
        assertEquals(RenderMode.DEFAULT, RandomLook.roll(RenderSettings(), seeded).renderMode)
    }

    /** The door Glyph Art gets reached through, rather than a second copy of the roll. */
    @Test
    fun `a roll asked for a glyph look gets one`() {
        val rolled = RandomLook.roll(RenderSettings(), seeded, RenderMode.GlyphMatrix)

        assertEquals(RenderMode.GlyphMatrix, rolled.renderMode)
    }

    /**
     * The roll is deliberately narrow — it moves the look, not every knob. Anything it does
     * not name is the user's and has to survive.
     */
    @Test
    fun `a roll leaves the settings it does not touch alone`() {
        val tuned = RenderSettings(
            brightness = 0.4f,
            contrast = 1.6f,
            gamma = 0.8f,
            saturation = 130,
            offset = 3,
            injection = "@#",
        )

        val rolled = RandomLook.roll(tuned, seeded)

        assertEquals(0.4f, rolled.brightness, 0f)
        assertEquals(1.6f, rolled.contrast, 0f)
        assertEquals(0.8f, rolled.gamma, 0f)
        assertEquals(130, rolled.saturation)
        assertEquals(3, rolled.offset)
        assertEquals("@#", rolled.injection)
    }

    /** A hand-arranged ramp and a hand-picked palette are looks in their own right; a roll replaces them. */
    @Test
    fun `a roll clears the overrides it is replacing`() {
        val overridden = RenderSettings(
            rampOverride = "..::##",
            paletteOverride = listOf(0xFF102030.toInt()),
            paletteLocks = listOf(true),
        )

        val rolled = RandomLook.roll(overridden, seeded)

        assertEquals("", rolled.rampOverride)
        assertEquals(emptyList<Int>(), rolled.paletteOverride)
        assertEquals(emptyList<Boolean>(), rolled.paletteLocks)
    }

    @Test
    fun `the same seed rolls the same look`() {
        assertEquals(RandomLook.roll(RenderSettings(), Random(7)), RandomLook.roll(RenderSettings(), Random(7)))
    }

    @Test
    fun `different seeds roll different looks`() {
        assertNotEquals(RandomLook.roll(RenderSettings(), Random(7)), RandomLook.roll(RenderSettings(), Random(8)))
    }

    /**
     * The point of the narrowness: a uniform roll over every knob produces an unreadable image
     * far more often than an interesting one.
     */
    @Test
    fun `a roll stays inside the ranges that produce a readable image`() {
        repeat(200) { seed ->
            val rolled = RandomLook.roll(RenderSettings(), Random(seed))

            assertTrue("cell size ${rolled.cellSize}", rolled.cellSize in 4..12)
            assertTrue("depth ${rolled.depth}", rolled.depth in 3..23)
            assertTrue("strength ${rolled.ditherStrength}", rolled.ditherStrength in 50..100)
            assertTrue("colour mode", rolled.colorMode in ColorMode.entries)
        }
    }
}
