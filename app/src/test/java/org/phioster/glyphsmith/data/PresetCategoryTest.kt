package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.DitherMode

class PresetCategoryTest {

    @Test
    fun `every algorithm resolves to a shipped category`() {
        DitherMode.entries.forEach { mode ->
            val family = PresetStore.familyOf(mode)
            assertTrue(
                "$mode resolves to $family, which the picker never shows",
                family in PresetStore.categories,
            )
        }
    }

    @Test
    fun `a preset with no dithering is not filed under a dither family`() {
        assertEquals(PresetStore.CATEGORY_CUSTOM, PresetStore.familyOf(DitherMode.NONE))
    }

    @Test
    fun `threshold-based algorithms are filed together`() {
        listOf(DitherMode.BAYER_4, DitherMode.MOD_ORB, DitherMode.HEART_GRID).forEach { mode ->
            assertEquals("$mode", PresetStore.CATEGORY_DITHER, PresetStore.familyOf(mode))
        }
    }

    /** Quick playback has to be an approximation of the whole loop, not a shorter loop. */
    @Test
    fun `quick playback covers the loop with fewer frames`() {
        assertTrue(PlaybackQuality.QUICK.step > PlaybackQuality.RENDERED.step)
        assertTrue(PlaybackQuality.QUICK.maxSide < PlaybackQuality.RENDERED.maxSide)

        val frames = 60
        val quick = (frames + PlaybackQuality.QUICK.step - 1) / PlaybackQuality.QUICK.step
        val full = (frames + PlaybackQuality.RENDERED.step - 1) / PlaybackQuality.RENDERED.step

        assertTrue("quick renders no fewer frames", quick < full)
        // Each quick frame stands in for `step` of them, so the loop still lasts as long.
        assertEquals(frames, quick * PlaybackQuality.QUICK.step)
    }

    @Test
    fun `every playback quality is named and distinct`() {
        val labels = PlaybackQuality.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }
}
