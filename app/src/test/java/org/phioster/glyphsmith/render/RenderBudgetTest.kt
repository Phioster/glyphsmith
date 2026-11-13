package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderBudgetTest {

    private val fill = RenderSettings()

    @Test
    fun `a preview's block is decided by its side, as before`() {
        assertEquals(2, RenderBudget.blockFor(fill, 368, 404, 1080))
        assertEquals(1, RenderBudget.blockFor(fill, 2000, 1500, 1080))
    }

    @Test
    fun `saving a tall photo stays under the pixel ceiling`() {
        // 440 x 587 cells filled an 8192 side with blocks of 13: 5720 x 7631, forty-three megapixels.
        val block = RenderBudget.blockFor(fill, 440, 587, RenderBudget.MAX_OUTPUT_SIDE)
        assertEquals(5, block)
        assertTrue(440L * block * 587 * block <= RenderBudget.MAX_OUTPUT_PIXELS)
    }

    @Test
    fun `a pinned block is held to the same ceiling`() {
        assertEquals(4, RenderBudget.blockFor(fill.copy(pixelBlock = 4), 440, 587, RenderBudget.MAX_OUTPUT_SIDE))
        assertEquals(5, RenderBudget.blockFor(fill.copy(pixelBlock = 12), 440, 587, RenderBudget.MAX_OUTPUT_SIDE))
    }

    @Test
    fun `a grid already past the ceiling still gets one pixel a cell`() {
        assertEquals(1, RenderBudget.blockFor(fill, 4000, 4000, RenderBudget.MAX_OUTPUT_SIDE))
    }

    @Test
    fun `a pixel block may be one pixel where the picture fits the budget`() {
        assertEquals(1, RenderSettings.PIXEL_BLOCK_RANGE.first)
        assertEquals(1, RenderBudget.cellFor(800, 600, RenderSettings.PIXEL_BLOCK_RANGE.first, 1024))
        // A picture larger than the budget still gets the coarser block it needs.
        assertEquals(2, RenderBudget.cellFor(2000, 600, 1, 1024))
    }
}
