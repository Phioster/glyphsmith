package org.phioster.glyphsmith.render

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode

/**
 * The compositing itself needs a Canvas and cannot be unit-tested here. What can be — and
 * what actually goes wrong — is the shape of the data: a layer that carries the stack it
 * belongs to would try to render everything beneath itself again, once per frame.
 */
class LayersTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `params start with no layers`() {
        assertTrue(RenderSettings().layers.isEmpty())
    }

    @Test
    fun `a layer survives a round trip`() {
        val params = RenderSettings(
            layers = listOf(
                Layer(
                    name = "coarse",
                    params = RenderSettings(cellSize = 12, ditherMode = DitherMode.BAYER_4),
                    blend = LayerBlend.MULTIPLY,
                    opacity = 60,
                    offsetX = -20,
                    rotation = 45,
                    flipVertical = true,
                ),
            ),
        )
        val restored = json.decodeFromString<RenderSettings>(json.encodeToString(params))

        assertEquals(1, restored.layers.size)
        val layer = restored.layers.first()
        assertEquals("coarse", layer.name)
        assertEquals(12, layer.params.cellSize)
        assertEquals(LayerBlend.MULTIPLY, layer.blend)
        assertEquals(60, layer.opacity)
        assertEquals(-20, layer.offsetX)
        assertEquals(45, layer.rotation)
        assertTrue(layer.flipVertical)
    }

    /** A preset written before layers existed has no such field and must still load. */
    @Test
    fun `params without the field decode to an empty stack`() {
        val restored = json.decodeFromString<RenderSettings>("""{"charSetId":"ascii-standard-10"}""")
        assertTrue(restored.layers.isEmpty())
    }

    /**
     * The capture in the panel strips the stack before storing it. If it ever stopped doing
     * that, every render would recurse one level further for each layer added — the cost
     * doubles rather than growing by one.
     */
    @Test
    fun `a captured layer does not carry the stack it belongs to`() {
        val existing = RenderSettings(layers = listOf(Layer(name = "first")))
        val captured = Layer(name = "second", params = existing.copy(layers = emptyList()))

        assertTrue("the capture kept the stack", captured.params.layers.isEmpty())
    }

    @Test
    fun `layer ranges are the ones the panel offers`() {
        assertEquals(0..100, Layer.OPACITY_RANGE)
        assertEquals(10..400, Layer.SCALE_RANGE)
        assertEquals(-100..100, Layer.OFFSET_RANGE)
    }

    @Test
    fun `every blend mode is named`() {
        LayerBlend.entries.forEach { assertTrue(it.label.isNotBlank()) }
        assertEquals(LayerBlend.entries.size, LayerBlend.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `the panels edit the chosen layer and nothing else`() {
        val stack = RenderSettings(cellSize = 4, layers = listOf(Layer(name = "a"), Layer(name = "b", params = RenderSettings(cellSize = 9))))
        assertEquals(9, stack.edited(1).cellSize)
        assertEquals(4, stack.edited(-1).cellSize)
        assertEquals(4, stack.edited(7).cellSize)

        val changed = stack.withEdited(1, RenderSettings(cellSize = 2, layers = listOf(Layer(name = "stowaway"))))
        assertEquals(4, changed.cellSize)
        assertEquals(stack.layers[0], changed.layers[0])
        assertEquals(2, changed.layers[1].params.cellSize)
        assertEquals("b", changed.layers[1].name)
        assertEquals(emptyList<Layer>(), changed.layers[1].params.layers)
    }

    @Test
    fun `with no layer chosen a change replaces the picture's own settings`() {
        val stack = RenderSettings(cellSize = 4, layers = listOf(Layer(name = "a")))
        val next = stack.copy(cellSize = 6)
        assertEquals(next, stack.withEdited(-1, next))
    }

    @Test
    fun `light handed over again replaces the light from before and nothing else`() {
        val picture = Layer(name = "picture 1", image = "a")
        val settings = Layer(name = "light")
        val first = Layer(name = Layers.HANDED_LIGHT, image = "b")
        val second = Layer(name = Layers.HANDED_LIGHT, image = "c")

        val once = Layers.withHandedLight(listOf(picture, settings), first)
        assertEquals(listOf(picture, settings, first), once)
        assertEquals(listOf(picture, settings, second), Layers.withHandedLight(once, second))
    }

    @Test
    fun `changing a layer's settings ends showing it as it is, and leaving them alone does not`() {
        val light = Layer(name = Layers.HANDED_LIGHT, image = "a", asIs = true)
        val settings = RenderSettings(layers = listOf(light))

        assertTrue(settings.withEdited(0, light.params).layers[0].asIs)
        assertEquals(false, settings.withEdited(0, light.params.copy(cellSize = 3)).layers[0].asIs)
    }

    @Test
    fun `a light loop sets the timing only where nothing is animated yet`() {
        val still = org.phioster.glyphsmith.anim.AnimationParams()
        val timed = Layers.timedFor(still, frames = 48, loopMs = 4000)
        assertEquals(48, timed.frames)
        assertEquals(12, timed.fps)
        assertEquals(still, Layers.timedFor(still, frames = 1, loopMs = 4000))
        assertEquals(still, Layers.timedFor(still, frames = 48, loopMs = 0))
        val own = still.copy(enabled = true, frames = 36, fps = 15)
        assertEquals(own, Layers.timedFor(own, frames = 48, loopMs = 4000))
    }

    @Test
    fun `a picture sent coloured lies under its light, is replaced with it and goes when a look is chosen`() {
        val own = Layer(name = "picture 1", image = "a")
        val sent = Layer(name = Layers.HANDED_PICTURE, image = "b", asIs = true)
        val light = Layer(name = Layers.HANDED_LIGHT, image = "c")

        val handed = Layers.withHandedLight(listOf(own), light, sent)
        assertEquals(listOf(own, sent, light), handed)

        // A second handover without colours takes the first one's picture away with its light.
        val again = Layer(name = Layers.HANDED_LIGHT, image = "d")
        assertEquals(listOf(own, again), Layers.withHandedLight(handed, again))

        assertEquals(listOf(own, light), Layers.rendered(handed))
    }
}
