package org.phioster.glyphsmith.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.data.Source

/**
 * What owning the loaded image or video actually commits the app to.
 *
 * Every one of these was a rule the view model followed by hand at four call sites, and none of
 * them could be checked: a leaked [Source] shows up as a decoder the system never gets back,
 * not as a failing assertion, and the position rule shows up as a video that opens somewhere in
 * the middle. The fake below is why `Source` is no longer sealed — closing can only be observed
 * by an implementation that records it.
 */
class SourceControllerTest {

    private class RecordingSource(
        private val pixels: IntArray = IntArray(4),
        override val width: Int = 2,
        override val height: Int = 2,
        override val isMoving: Boolean = false,
    ) : Source {
        var closeCount = 0
            private set

        /** Every position [pixelsAt] was asked for, in order. */
        val positions = mutableListOf<Float>()

        override fun pixelsAt(position: Float): IntArray {
            positions += position
            return pixels
        }

        override fun close() {
            closeCount++
        }
    }

    private val controller = SourceController()

    @Test
    fun `adopting a source closes the one it replaces, exactly once`() {
        val first = RecordingSource()
        val second = RecordingSource()

        controller.adopt(first)
        controller.adopt(second)

        assertEquals(1, first.closeCount)
        assertEquals(0, second.closeCount)
        assertSame(second, controller.source)
    }

    /**
     * The one case where closing the previous source would close the *current* one. Nothing
     * calls it today; the guard is here because the alternative failure — a live source closed
     * under the renderer — is silent and instant.
     */
    @Test
    fun `adopting the source already held changes nothing`() {
        val only = RecordingSource()
        controller.adopt(only)

        controller.adopt(only)

        assertEquals(0, only.closeCount)
        assertSame(only, controller.source)
    }

    @Test
    fun `releasing closes the source and forgets it`() {
        val source = RecordingSource()
        controller.adopt(source)

        controller.release()

        assertEquals(1, source.closeCount)
        assertNull(controller.source)
        assertNull(controller.frame())
    }

    /** `onCleared` can arrive after a release; closing a decoder twice is not free. */
    @Test
    fun `releasing twice closes once`() {
        val source = RecordingSource()
        controller.adopt(source)

        controller.release()
        controller.release()

        assertEquals(1, source.closeCount)
    }

    /**
     * The rule three loaders each spelled out for themselves. A new image opening at wherever
     * the last video was scrubbed to is the bug this prevents.
     */
    @Test
    fun `a new source starts at the beginning`() {
        controller.adopt(RecordingSource(isMoving = true))
        controller.seek(0.75f)

        controller.adopt(RecordingSource(isMoving = true))

        assertEquals(0f, controller.position, 0f)
    }

    @Test
    fun `a still refuses to be scrubbed`() {
        controller.adopt(RecordingSource(isMoving = false))

        assertFalse(controller.seek(0.5f))
        assertEquals(0f, controller.position, 0f)
    }

    @Test
    fun `scrubbing with nothing loaded is refused`() {
        assertFalse(controller.seek(0.5f))
        assertEquals(0f, controller.position, 0f)
    }

    @Test
    fun `a video accepts a position and clamps it`() {
        controller.adopt(RecordingSource(isMoving = true))

        assertTrue(controller.seek(0.25f))
        assertEquals(0.25f, controller.position, 0f)

        controller.seek(4f)
        assertEquals(1f, controller.position, 0f)

        controller.seek(-4f)
        assertEquals(0f, controller.position, 0f)
    }

    @Test
    fun `a frame is read at the position the controller holds`() {
        val source = RecordingSource(isMoving = true)
        controller.adopt(source)
        controller.seek(0.4f)

        controller.frame()

        assertEquals(listOf(0.4f), source.positions.toList())
    }

    @Test
    fun `there is no frame without a source`() {
        assertNull(controller.frame())
        assertFalse(controller.isMoving)
    }

    /**
     * The reason this is one call and not three reads. A render is handed the buffer and the
     * size separately; taken from two different sources they describe an image that does not
     * exist, and the sampler rejects it several layers further down.
     */
    @Test
    fun `a frame's pixels and size come from the same source`() {
        val pixels = IntArray(21)
        controller.adopt(RecordingSource(pixels = pixels, width = 7, height = 3))

        val frame = controller.frame()!!

        assertSame(pixels, frame.pixels)
        assertEquals(7, frame.width)
        assertEquals(3, frame.height)
    }
}
