package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

class QuickMotionsTest {

    private val plain = RenderSettings()

    @Test
    fun `a motion applied is worn, and applying it again changes nothing`() {
        QuickMotions.all.forEach { motion ->
            val moved = motion.applyTo(plain)
            assertTrue(motion.label, motion.wornBy(moved))
            assertEquals(motion.label, moved, motion.applyTo(moved))
        }
    }

    @Test
    fun `only one motion is worn at a time`() {
        QuickMotions.all.forEach { motion ->
            val moved = motion.applyTo(QuickMotions.all.last().applyTo(plain))
            assertEquals(motion.label, listOf(motion.label), QuickMotions.all.filter { it.wornBy(moved) }.map { it.label })
        }
    }

    @Test
    fun `every motion but still moves something, and still stops it`() {
        QuickMotions.all.drop(1).forEach { motion ->
            val moved = motion.applyTo(plain)
            val moves = (moved.animation.enabled && moved.animation.activeCount > 0) || moved.temporal.enabled
            assertTrue(motion.label, moves)
            val still = QuickMotions.all.first().applyTo(moved)
            assertFalse(motion.label, still.animation.enabled || still.temporal.enabled)
            assertEquals(motion.label, 0, still.animation.activeCount)
        }
    }

    @Test
    fun `a motion keeps the look and the length of the loop`() {
        val look = plain.copy(cellSize = 5, animation = plain.animation.copy(frames = 48, fps = 20))
        val moved = QuickMotions.all[1].applyTo(look)
        assertEquals(5, moved.cellSize)
        assertEquals(48, moved.animation.frames)
        assertEquals(20, moved.animation.fps)
    }
}
