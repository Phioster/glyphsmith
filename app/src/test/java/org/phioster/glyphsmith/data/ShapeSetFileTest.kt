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

    @Test
    fun `a kept set comes back from the shelf with its name and bands`() {
        val bands = ShapeSets.all.first().bands
        val shelf = ShapeSetFile.keeping(emptyList(), ShapeSetFile.of("mine", bands))
        val back = ShapeSetFile.decodeAll(ShapeSetFile.encodeAll(shelf))
        assertEquals(listOf("mine"), back.map { it.name })
        assertEquals(bands, ShapeSetFile.bandsOf(back.single()))
    }

    @Test
    fun `keeping a name again replaces it and puts it last`() {
        val a = ShapeSetFile.of("a", ShapeSets.all[0].bands)
        val b = ShapeSetFile.of("b", ShapeSets.all[1].bands)
        val again = ShapeSetFile.of("A", ShapeSets.all[2].bands)
        assertEquals(listOf(b, again), ShapeSetFile.keeping(listOf(a, b), again))
    }

    @Test
    fun `a new set gets the first free name`() {
        assertEquals("my set 1", ShapeSetFile.freeName(emptyList()))
        val kept = listOf(ShapeSetFile(name = "My Set 1"), ShapeSetFile(name = "my set 3"))
        assertEquals("my set 2", ShapeSetFile.freeName(kept))
    }

    @Test
    fun `text that is not a shape set is not read as one`() {
        assertEquals(null, ShapeSetFile.read("{\"name\":\"empty\"}"))
        assertEquals(null, ShapeSetFile.read("not json"))
        assertEquals(emptyList<ShapeSetFile>(), ShapeSetFile.decodeAll("not json"))
    }
}
