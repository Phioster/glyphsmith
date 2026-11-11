package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.ShapeBand
import org.phioster.glyphsmith.render.ShapeKind
import org.phioster.glyphsmith.render.ShapeSets

class ShapeSetFileTest {

    @Test
    fun `every built-in set survives the round trip`() {
        ShapeSets.all.forEach { set ->
            assertEquals(set.name, set.bands, ShapeSetFile.decode(ShapeSetFile.encode(set.name, set.bands)))
        }
    }

    @Test
    fun `shapes go out by name and colours as hex`() {
        val text = ShapeSetFile.encode("mine", listOf(ShapeBand(ShapeKind.PIXEL_HEART, 80, 40, 0xFFE0181F.toInt()), ShapeBand()))
        assertTrue(text, text.contains("\"pixel_heart\""))
        assertTrue(text, text.contains("\"#E0181F\""))
        assertTrue(text, text.contains("\"color\": \"\""))
    }

    @Test
    fun `a hand-written file is read with its gaps filled and its values brought into range`() {
        val bands = ShapeSetFile.decode(
            """{"bands":[{"shape":"Star5","size":900,"width":0,"color":"e0181f"},{"shape":"ring"},{"shape":"no such shape"}]}""",
        )
        assertEquals(
            listOf(ShapeBand(ShapeKind.STAR5, 200, 1, 0xFFE0181F.toInt()), ShapeBand(ShapeKind.RING, 100, 50, -1)),
            bands,
        )
    }

    @Test
    fun `more bands than the editor holds are cut off`() {
        val many = List(12) { ShapeBand(ShapeKind.CIRCLE, 10 * it, 50) }
        assertEquals(8, ShapeSetFile.decode(ShapeSetFile.encode("many", many))?.size)
    }

    @Test
    fun `text that is no shape set is refused`() {
        assertNull(ShapeSetFile.decode("not json"))
        assertNull(ShapeSetFile.decode("""{"name":"empty","bands":[]}"""))
        assertNull(ShapeSetFile.decode("""{"name":"a palette","colors":["#112233"]}"""))
    }
}
