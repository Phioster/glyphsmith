package org.phioster.glyphsmith.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

/**
 * Undo and redo, which until it was pulled out of the ViewModel had no tests at all — reaching
 * it meant standing up an Application, a coroutine scope and a debounce.
 */
class HistoryControllerTest {

    private val start = RenderSettings.newSession()
    private fun at(cell: Int) = start.copy(cellSize = cell)

    private fun history(initial: RenderSettings = start, limit: Int = 50) =
        HistoryController(initial, limit)

    // --- the empty case -----------------------------------------------------------------

    @Test
    fun `a fresh session has nothing to undo or redo`() {
        val history = history()

        assertTrue(!history.canUndo)
        assertTrue(!history.canRedo)
        assertNull(history.undo())
        assertNull(history.redo())
    }

    /**
     * Seeded with the settings the session opens on. Otherwise the first edit would record a
     * step back to a state the user never saw — which, since the field default is Glyph Art and
     * a new session is Pixel Dither, would mean one undo landing in the wrong render mode.
     */
    @Test
    fun `the first edit goes back to what the session opened on`() {
        val history = history()

        history.commit(at(4))

        assertEquals(start, history.undo())
    }

    // --- what counts as a step ----------------------------------------------------------

    @Test
    fun `a commit that changes nothing is not a step`() {
        val history = history()

        history.commit(start)
        history.commit(start)

        assertTrue("settling on the value you started from is not an edit", !history.canUndo)
    }

    /** A drag that wanders and comes back has settled once, on the value it started from. */
    @Test
    fun `only settled values are recorded`() {
        val history = history()

        history.commit(at(4))
        history.commit(at(9))

        assertEquals(2, history.depth)
        assertEquals(at(4), history.undo())
        assertEquals(start, history.undo())
        assertNull(history.undo())
    }

    // --- undo and redo ------------------------------------------------------------------

    @Test
    fun `redo returns what undo took away`() {
        val history = history()
        history.commit(at(4))

        assertEquals(start, history.undo())
        assertTrue(history.canRedo)
        assertEquals(at(4), history.redo())
        assertTrue(!history.canRedo)
    }

    @Test
    fun `undo and redo walk the whole chain in both directions`() {
        val history = history()
        listOf(4, 9, 16).forEach { history.commit(at(it)) }

        assertEquals(at(9), history.undo())
        assertEquals(at(4), history.undo())
        assertEquals(start, history.undo())
        assertNull(history.undo())

        assertEquals(at(4), history.redo())
        assertEquals(at(9), history.redo())
        assertEquals(at(16), history.redo())
        assertNull(history.redo())
    }

    /**
     * The bug the suppress flag exists for.
     *
     * Restoring feeds the restored settings back through the same debounce, which commits them
     * again. Recorded as an ordinary edit, that commit would push what was just undone straight
     * back on — and undo would get you precisely nowhere, once.
     */
    @Test
    fun `the commit an undo causes is not recorded as an edit`() {
        val history = history()
        history.commit(at(4))

        val restored = history.undo()!!
        history.commit(restored)

        assertTrue("undoing left something to undo again", !history.canUndo)
        assertEquals("redo was lost", at(4), history.redo())
    }

    @Test
    fun `the commit a redo causes is not recorded either`() {
        val history = history()
        history.commit(at(4))
        history.commit(history.undo()!!)

        val forward = history.redo()!!
        history.commit(forward)

        assertEquals("redo did not leave one step back", 1, history.depth)
        assertEquals(start, history.undo())
    }

    /** Suppression covers the one commit the restore caused, and not the edit after it. */
    @Test
    fun `an edit made after an undo is recorded normally`() {
        val history = history()
        history.commit(at(4))
        history.commit(history.undo()!!)

        history.commit(at(25))

        assertEquals(start, history.undo())
    }

    // --- branching ----------------------------------------------------------------------

    /** Editing after an undo abandons the branch, because it no longer follows from here. */
    @Test
    fun `an edit after an undo drops what could have been redone`() {
        val history = history()
        history.commit(at(4))
        history.commit(history.undo()!!)
        assertTrue(history.canRedo)

        history.commit(at(25))

        assertTrue("a redo survived an edit that replaced it", !history.canRedo)
        assertEquals(start, history.undo())
    }

    // --- the cap ------------------------------------------------------------------------

    @Test
    fun `the oldest steps fall off once the cap is reached`() {
        val history = history(limit = 3)

        listOf(1, 2, 3, 4, 5).forEach { history.commit(at(it)) }

        assertEquals(3, history.depth)
        assertEquals(at(4), history.undo())
        assertEquals(at(3), history.undo())
        assertEquals(at(2), history.undo())
        assertNull("a step older than the cap survived", history.undo())
    }

    @Test
    fun `the shipped cap is what it always was`() {
        assertEquals(50, HistoryController.MAX_HISTORY)
    }
}
