package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.render.RenderMode

/**
 * The preset file format: what it writes, and what it can still read.
 *
 * The point of the version is the render mode. A preset written before that field existed has
 * to keep rendering as Glyph Art, and it cannot rely on the Kotlin default to say so — the
 * default is going to become Pixel Dither.
 */
class PresetSchemaTest {

    @Test
    fun `the written format carries an explicit schema version`() {
        val text = PresetSchema.encode(listOf(Preset("one", AsciiParams())))

        val version = Json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.int
        assertEquals(PresetSchema.CURRENT_VERSION, version)
    }

    @Test
    fun `a library survives being written and read back`() {
        val presets = listOf(
            Preset(
                name = "loaded",
                params = AsciiParams(
                    renderMode = RenderMode.PurePixel,
                    charSetId = "block-shade",
                    cellSize = 5,
                    depth = 6,
                    ditherMode = DitherMode.MOD_WAVE,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "ice",
                    effects = EffectStack(chromatic = ChromaticParams(enabled = true, maxDisplace = 9)),
                ),
                category = PresetStore.CATEGORY_DITHER,
                favourite = true,
                description = "why this look",
            ),
            Preset("plain", AsciiParams()),
        )

        assertEquals(presets, PresetSchema.decode(PresetSchema.encode(presets)))
    }

    @Test
    fun `the written format can be read by the version it declares`() {
        val text = PresetSchema.encode(listOf(Preset("one", AsciiParams())))

        assertTrue(
            "$text does not read back as version ${PresetSchema.CURRENT_VERSION}",
            PresetSchema.decode(text).isNotEmpty(),
        )
    }

    // --- version 1: the bare array written before there was a version ----------------

    /**
     * The whole reason the version exists. Version 1 predates the render mode, so a preset
     * from it is a glyph preset whatever the current default happens to be.
     */
    @Test
    fun `a version 1 preset without a render mode loads as Glyph Art`() {
        val legacy = """[{"name":"terminal","params":{"charSetId":"ascii-standard-10","cellSize":6}}]"""

        val loaded = PresetSchema.decode(legacy)

        assertEquals(1, loaded.size)
        assertEquals(RenderMode.GlyphMatrix, loaded.single().params.renderMode)
    }

    /** A mode the file actually names is the author's choice and outranks the migration. */
    @Test
    fun `a version 1 preset that names its render mode keeps it`() {
        val legacy = """[{"name":"dithered","params":{"renderMode":"PurePixel","cellSize":4}}]"""

        assertEquals(RenderMode.PurePixel, PresetSchema.decode(legacy).single().params.renderMode)
    }

    /** Migrating one field must not cost the rest of the entry. */
    @Test
    fun `a version 1 preset keeps everything else it carried`() {
        val legacy = """
            [{"name":"kept","category":"DITHER","favourite":true,"description":"from before",
              "params":{"cellSize":6,"depth":7,"paletteId":"ice"}}]
        """.trimIndent()

        val preset = PresetSchema.decode(legacy).single()

        assertEquals("kept", preset.name)
        assertEquals(PresetStore.CATEGORY_DITHER, preset.category)
        assertTrue(preset.favourite)
        assertEquals("from before", preset.description)
        assertEquals(6, preset.params.cellSize)
        assertEquals(7, preset.params.depth)
        assertEquals("ice", preset.params.paletteId)
    }

    // --- entries that will not read --------------------------------------------------

    /**
     * One preset a build cannot read must not cost the library. The effect order serialises as
     * effect names, so a preset carrying an effect added after this build is exactly this case.
     */
    @Test
    fun `an entry that will not read costs only itself`() {
        val document = """
            {"schemaVersion":2,"presets":[
              {"name":"before","params":{"cellSize":4}},
              {"name":"unreadable","params":{"effects":{"order":["A_PASS_FROM_LATER"]}}},
              {"name":"after","params":{"cellSize":5}}
            ]}
        """.trimIndent()

        assertEquals(listOf("before", "after"), PresetSchema.decode(document).map { it.name })
    }

    @Test
    fun `an entry missing its params is dropped and the rest survive`() {
        val document = """
            {"schemaVersion":2,"presets":[{"name":"nameless"},{"name":"whole","params":{"cellSize":4}}]}
        """.trimIndent()

        assertEquals(listOf("whole"), PresetSchema.decode(document).map { it.name })
    }

    @Test
    fun `an entry that is not an object at all is dropped and the rest survive`() {
        val document = """{"schemaVersion":2,"presets":["nonsense",{"name":"whole","params":{}}]}"""

        assertEquals(listOf("whole"), PresetSchema.decode(document).map { it.name })
    }

    /** Keys from a later build are ignored, on the entry and on the document. */
    @Test
    fun `keys this build does not know are ignored`() {
        val document = """
            {"schemaVersion":2,"somethingLater":true,"presets":[
              {"name":"forward","params":{"cellSize":4,"aFieldFromLater":9},"alsoLater":"x"}
            ]}
        """.trimIndent()

        val preset = PresetSchema.decode(document).single()

        assertEquals("forward", preset.name)
        assertEquals(4, preset.params.cellSize)
    }

    /**
     * A file from a newer schema is read rather than refused. Refusing it would cost the whole
     * library for one field this build has never heard of; per entry, it costs only the entries
     * that genuinely will not read.
     */
    @Test
    fun `a document from a newer schema version still yields its readable presets`() {
        val document = """{"schemaVersion":99,"presets":[{"name":"future","params":{"cellSize":4}}]}"""

        assertEquals(listOf("future"), PresetSchema.decode(document).map { it.name })
    }

    /** An entry may leave out anything that has a default, and gets the default. */
    @Test
    fun `an entry that leaves out the optional fields falls back to the defaults`() {
        val document = """{"schemaVersion":2,"presets":[{"name":"bare","params":{}}]}"""

        val preset = PresetSchema.decode(document).single()

        assertEquals("bare", preset.name)
        assertEquals(PresetStore.CATEGORY_CUSTOM, preset.category)
        assertEquals(false, preset.favourite)
        assertEquals("", preset.description)
        assertEquals(AsciiParams(), preset.params)
    }

    // --- documents that are not preset documents -------------------------------------

    @Test
    fun `text that is not JSON reads as nothing`() {
        assertEquals(emptyList<Preset>(), PresetSchema.decode("not a preset file at all"))
    }

    @Test
    fun `JSON that is not a preset document reads as nothing`() {
        assertEquals(emptyList<Preset>(), PresetSchema.decode("""{"settings":{"theme":"dark"}}"""))
    }

    @Test
    fun `an empty document reads as nothing`() {
        assertEquals(emptyList<Preset>(), PresetSchema.decode(""))
        assertEquals(emptyList<Preset>(), PresetSchema.decode("[]"))
        assertEquals(emptyList<Preset>(), PresetSchema.decode("""{"schemaVersion":2,"presets":[]}"""))
    }

    // --- the shipped library ---------------------------------------------------------

    /** What actually ships has to survive the trip, or a reset would change what people see. */
    @Test
    fun `the built-in library survives being written and read back`() {
        assertEquals(PresetStore.builtIns, PresetSchema.decode(PresetSchema.encode(PresetStore.builtIns)))
    }
}
