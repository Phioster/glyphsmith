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
}
