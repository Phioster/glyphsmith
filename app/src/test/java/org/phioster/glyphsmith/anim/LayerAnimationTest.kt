package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.data.PresetLibrary
import org.phioster.glyphsmith.render.Layer
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.render.animationShown
import org.phioster.glyphsmith.render.withAnimationEdited

class LayerAnimationTest {

    private val still = AnimTarget.entries.map { AnimTrack(it, enabled = false) }

    private fun moving(from: Int, to: Int) = AnimationParams(
        enabled = true,
        frames = 99,
        tracks = still,
        segments = listOf(AnimSegment(AnimTarget.DEPTH, from = from, to = to, start = 0, end = 100, curve = AnimCurve.SAWTOOTH)),
    )

    private val clock = AnimationParams(enabled = true, frames = 20, tracks = still)

    @Test
    fun `a layer moves by its own tracks on the picture's clock`() {
        val layer = Layer(name = "top", params = RenderSettings(depth = 12, animation = moving(4, 40)))
        val picture = RenderSettings(depth = 7, animation = clock, layers = listOf(layer))
        val early = Animator.paramsAt(picture, clock, 0)
        val late = Animator.paramsAt(picture, clock, 19)
        // The picture has no tracks of its own and stands still.
        assertEquals(7, early.depth)
        assertEquals(7, late.depth)
        // The layer runs the whole of its segment in the picture's twenty frames, not its own 99.
        assertEquals(4, early.layers[0].params.depth)
        assertTrue("depth ${late.layers[0].params.depth}", late.layers[0].params.depth > 36)
    }

    @Test
    fun `a layer with its animation off stays as it was`() {
        val layer = Layer(name = "top", params = RenderSettings(depth = 12, animation = moving(4, 40).copy(enabled = false)))
        val picture = RenderSettings(animation = clock, layers = listOf(layer))
        assertEquals(picture, Animator.paramsAt(picture, clock, 10))
    }

    @Test
    fun `the clock reaches the picture and every layer`() {
        val picture = RenderSettings(layers = listOf(Layer(name = "a"), Layer(name = "b")))
        val at = Animator.clocked(picture, 0.25f)
        assertEquals(0.25f, at.temporal.time, 0f)
        at.layers.forEach { assertEquals(0.25f, it.params.temporal.time, 0f) }
    }

    @Test
    fun `the panel shows the picture's clock with the layer's tracks, and takes them apart again`() {
        val layer = Layer(name = "top", params = RenderSettings(animation = moving(4, 40).copy(enabled = false)))
        val picture = RenderSettings(animation = clock, layers = listOf(layer))

        val shown = picture.animationShown(0)
        assertEquals(20, shown.frames)
        assertEquals(1, shown.segments.size)
        assertEquals(clock, picture.animationShown(-1))

        val changed = picture.withAnimationEdited(0, shown.copy(frames = 30, segments = emptyList()))
        assertEquals(30, changed.animation.frames)
        assertEquals(clock.segments, changed.animation.segments)
        assertEquals(emptyList<AnimSegment>(), changed.layers[0].params.animation.segments)
        assertTrue(changed.layers[0].params.animation.enabled)
    }

    /** Layers used to stand still in a loop; none of the shipped looks may start moving unasked. */
    @Test
    fun `no shipped preset's layers change by this`() {
        PresetLibrary.builtIns.forEach { preset ->
            preset.params.layers.forEach { layer ->
                val own = layer.params.animation
                val moves = own.enabled && (own.tracks.any { it.enabled } || own.segments.isNotEmpty())
                assertTrue("${preset.name} · ${layer.name} would start moving", !moves)
            }
        }
    }
}
