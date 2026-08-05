package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.Layer
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.core.color.Palettes

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
        val text = PresetSchema.encode(listOf(Preset("one", RenderSettings())))

        val version = Json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.int
        assertEquals(PresetSchema.CURRENT_VERSION, version)
    }

    @Test
    fun `a library survives being written and read back`() {
        val presets = listOf(
            Preset(
                name = "loaded",
                params = RenderSettings(
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
            Preset("plain", RenderSettings()),
        )

        assertEquals(presets, PresetSchema.decode(PresetSchema.encode(presets)))
    }

    @Test
    fun `the written format can be read by the version it declares`() {
        val text = PresetSchema.encode(listOf(Preset("one", RenderSettings())))

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
        // Carried through every migration in turn, so a version 1 document arrives spelled
        // the way version 4 spells a palette.
        assertEquals("palette.ice", preset.params.paletteId)
    }

    // --- version 3: palettes named by a bare id ------------------------------------------

    /**
     * The spelling change schema 4 makes, on a document that predates it.
     *
     * The danger here is not that it fails — [Palettes.byId] reads either spelling, so a preset
     * whose palette was never migrated renders correctly and says nothing. The danger is that a
     * library ends up holding both spellings of one identity, with no symptom to notice.
     */
    @Test
    fun `a version 3 palette id is respelled`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"kept","params":{
              "renderMode":"render.pixel-dither","ditherMode":"dither.atkinson","paletteId":"ice"}}]}
        """.trimIndent()

        val preset = PresetSchema.decode(document).single()

        assertEquals("palette.ice", preset.params.paletteId)
        assertEquals("the palette itself must not have changed", Palettes.byId("ice"), preset.params.activePalette())
    }

    /** A version 4 document names its animation targets with the Kotlin constants. */
    private val version4 = """
        {"schemaVersion":4,"presets":[{"name":"old","params":{
          "renderMode":"render.glyph-art","paletteId":"palette.ice",
          "animation":{"enabled":true,"frames":24,"fps":12,
            "tracks":[{"target":"GLOW_DIRECTION","enabled":true,"from":0,"to":359}],
            "segments":[{"target":"GLITCH_SEED","from":1,"to":9999,"start":0,"end":50}]}}}]}
    """.trimIndent()

    /**
     * Reading already worked — the serialiser accepts a legacy constant name — and this says so
     * rather than assuming it. What a preset *drives* is the thing a respelling could break, and
     * it is not visible in the file: it is visible only in which field moves when the animation
     * runs.
     */
    @Test
    fun `a version 4 animation target still drives the same parameter`() {
        val preset = PresetSchema.decode(version4).single()

        assertEquals(AnimTarget.GLOW_DIRECTION, preset.params.animation.tracks.single().target)
        assertEquals(AnimTarget.GLITCH_SEED, preset.params.animation.segments.single().target)
    }

    /**
     * And the migration's own job: the old spelling does not survive the round trip.
     *
     * Without it a preset saved by this build would still contain `GLOW_DIRECTION`, and the
     * constant would stay load-bearing for ever — which is the whole thing the ids exist to end.
     */
    @Test
    fun `a re-encoded version 4 preset names its targets by id`() {
        val text = PresetSchema.encode(PresetSchema.decode(version4))

        assertTrue("the track kept its constant name", text.contains("anim.glow-direction"))
        assertTrue("the segment kept its constant name", text.contains("anim.glitch-seed"))
        assertTrue("a Kotlin constant is still in the file", !text.contains("GLOW_DIRECTION"))
        assertTrue("a Kotlin constant is still in the file", !text.contains("GLITCH_SEED"))
    }

    /** A layer carries an animation of its own, and it is the one a rewrite forgets. */
    @Test
    fun `an animation target inside a layer is respelled too`() {
        val document = """
            {"schemaVersion":4,"presets":[{"name":"stacked","params":{
              "renderMode":"render.pixel-dither",
              "layers":[{"params":{"renderMode":"render.pixel-dither",
                "animation":{"tracks":[{"target":"STARS_ANGLE","enabled":true}]}}}]}}]}
        """.trimIndent()

        val preset = PresetSchema.decode(document).single()
        val layer = preset.params.layers.single()

        assertEquals(AnimTarget.STARS_ANGLE, layer.params.animation.tracks.single().target)
        assertTrue(PresetSchema.encode(listOf(preset)).contains("anim.stars-angle"))
    }

    /** A layer carries a palette of its own, and it is the one a rewrite forgets. */
    @Test
    fun `a palette inside a layer is respelled too`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"stacked","params":{
              "renderMode":"render.pixel-dither","paletteId":"ice",
              "layers":[{"name":"over","params":{"renderMode":"render.pixel-dither","paletteId":"bone"}}]}}]}
        """.trimIndent()

        val preset = PresetSchema.decode(document).single()

        assertEquals("palette.ice", preset.params.paletteId)
        assertEquals("palette.bone", preset.params.layers.single().params.paletteId)
    }

    /**
     * A palette this build has never heard of is left exactly as it was, on the same rule the
     * id migration follows: a migration that guessed would be the thing all of this exists to
     * prevent.
     */
    @Test
    fun `a palette this build does not know is carried through untouched`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"newer","params":{
              "renderMode":"render.pixel-dither","paletteId":"invented-tomorrow"}}]}
        """.trimIndent()

        val preset = PresetSchema.decode(document).single()

        assertEquals("invented-tomorrow", preset.params.paletteId)
    }

    /** Respelling twice would produce `palette.palette.ice`. */
    @Test
    fun `a palette already spelled the new way is left alone`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"early","params":{
              "renderMode":"render.pixel-dither","paletteId":"palette.ice"}}]}
        """.trimIndent()

        assertEquals("palette.ice", PresetSchema.decode(document).single().params.paletteId)
    }

    @Test
    fun `both spellings name the same palette`() {
        assertEquals(Palettes.byId("ice"), Palettes.byId("palette.ice"))
    }

    // --- version 2: enum constants, before the stable ids ----------------------------

    /**
     * What a version 2 file contains is Kotlin constant names. They still have to read, and
     * they have to come back as exactly the same style — a preset is only worth keeping if it
     * keeps looking like itself.
     */
    @Test
    fun `a version 2 preset named by enum constants keeps every identity`() {
        val legacy = """
            {"schemaVersion":2,"presets":[{"name":"old","params":{
              "renderMode":"PurePixel","ditherMode":"FLOYD_STEINBERG","cellSize":5,"depth":6,
              "effects":{"order":["GLOW","POST","CHROMATIC"]}}}]}
        """.trimIndent()

        val params = PresetSchema.decode(legacy).single().params

        assertEquals(RenderMode.PurePixel, params.renderMode)
        assertEquals(DitherMode.FLOYD_STEINBERG, params.ditherMode)
        assertEquals(listOf(EffectId.GLOW, EffectId.POST, EffectId.CHROMATIC), params.effects.order)
        assertEquals(5, params.cellSize)
        assertEquals(6, params.depth)
    }

    /** A layer carries a whole second set of params, and a second dither style with it. */
    @Test
    fun `a version 2 preset carries its layers across too`() {
        val legacy = """
            {"schemaVersion":2,"presets":[{"name":"stacked","params":{
              "renderMode":"GlyphMatrix","ditherMode":"ATKINSON",
              "layers":[{"name":"over","params":{"renderMode":"PurePixel","ditherMode":"BAYER_8"}}]}}]}
        """.trimIndent()

        val params = PresetSchema.decode(legacy).single().params
        val layer = params.layers.single().params

        assertEquals(DitherMode.ATKINSON, params.ditherMode)
        assertEquals(RenderMode.PurePixel, layer.renderMode)
        assertEquals(DitherMode.BAYER_8, layer.ditherMode)
    }

    /** The two spellings are the same preset, so they must render as the same preset. */
    @Test
    fun `a preset named by enum constants and one named by ids read alike`() {
        val legacy = """{"schemaVersion":2,"presets":[{"name":"same","params":
            {"renderMode":"PurePixel","ditherMode":"MOD_WAVE","effects":{"order":["GLOW","POST"]}}}]}"""
        val current = """{"schemaVersion":3,"presets":[{"name":"same","params":
            {"renderMode":"render.pixel-dither","ditherMode":"dither.modulation-wave",
             "effects":{"order":["effect.glow","effect.post-processing"]}}}]}"""

        assertEquals(PresetSchema.decode(legacy), PresetSchema.decode(current))
    }

    // --- version 3: what is actually written -----------------------------------------

    @Test
    fun `the written format names things by their stable ids`() {
        val preset = Preset(
            "written",
            RenderSettings(renderMode = RenderMode.PurePixel, ditherMode = DitherMode.ATKINSON),
        )

        val params = Json.parseToJsonElement(PresetSchema.encode(listOf(preset)))
            .jsonObject["presets"]!!.jsonArray.single().jsonObject["params"]!!.jsonObject

        assertEquals("render.pixel-dither", params["renderMode"]?.jsonPrimitive?.content)
        assertEquals("dither.atkinson", params["ditherMode"]?.jsonPrimitive?.content)
        assertEquals(
            "effect.post-processing",
            params["effects"]?.jsonObject?.get("order")?.jsonArray?.first()?.jsonPrimitive?.content,
        )
    }

    /**
     * The mode a preset was saved in is the mode it comes back in — for every mode, not just
     * the two that happen to appear in the round trips above. Saving is where the new default
     * could leak into the library: a mode dropped on the way out would be read back as the
     * field default and the preset would change appearance.
     */
    @Test
    fun `every render mode survives being saved and reloaded`() {
        RenderMode.entries.forEach { mode ->
            val preset = Preset("saved in $mode", RenderSettings(renderMode = mode))

            val reloaded = PresetSchema.decode(PresetSchema.encode(listOf(preset))).single()

            assertEquals(mode, reloaded.params.renderMode)
        }
    }

    /** A preset saved today, reloaded, has to be the same preset down to the effect order. */
    @Test
    fun `a preset written now survives being read back whole`() {
        val preset = Preset(
            name = "everything",
            params = RenderSettings(
                renderMode = RenderMode.PixelThenGlyph,
                ditherMode = DitherMode.OSTROMOUKHOV,
                cellSize = 5,
                depth = 7,
                effects = EffectStack(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = 9),
                    order = listOf(EffectId.GLOW, EffectId.WARP) + (EffectId.entries - EffectId.GLOW - EffectId.WARP),
                ),
                layers = listOf(Layer(name = "over", params = RenderSettings(ditherMode = DitherMode.BAYER_8))),
            ),
            category = PresetStore.CATEGORY_DITHER,
        )

        assertEquals(listOf(preset), PresetSchema.decode(PresetSchema.encode(listOf(preset))))
    }

    // --- identities this build does not know ------------------------------------------

    /**
     * The failure that matters. An unknown style must never come back as some *other* style —
     * a preset that silently renders with the wrong algorithm is worse than one that is
     * reported missing, because nothing tells you it happened.
     */
    @Test
    fun `an unknown dither id is refused rather than swapped for another style`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"from later","params":{"ditherMode":"dither.not-invented-yet"}}]}
        """.trimIndent()

        assertEquals(emptyList<Preset>(), PresetSchema.decode(document))
    }

    @Test
    fun `an unknown render mode id is refused rather than falling back to a default`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"from later","params":{"renderMode":"render.not-invented-yet"}}]}
        """.trimIndent()

        assertEquals(emptyList<Preset>(), PresetSchema.decode(document))
    }

    @Test
    fun `an unknown effect id is refused rather than dropped from the chain`() {
        val document = """
            {"schemaVersion":3,"presets":[{"name":"from later","params":{"effects":{"order":["effect.not-invented-yet"]}}}]}
        """.trimIndent()

        assertEquals(emptyList<Preset>(), PresetSchema.decode(document))
    }

    /** One preset from a newer build must not cost the ones around it. */
    @Test
    fun `a library holding an unknown id keeps every preset that does read`() {
        val document = """
            {"schemaVersion":3,"presets":[
              {"name":"before","params":{"ditherMode":"dither.atkinson"}},
              {"name":"from later","params":{"ditherMode":"dither.not-invented-yet"}},
              {"name":"after","params":{"cellSize":5}}
            ]}
        """.trimIndent()

        val reading = PresetSchema.read(document)

        assertEquals(listOf("before", "after"), reading.presets.map { it.name })
        assertEquals(listOf("from later"), reading.skipped.map { it.name })
        assertTrue(
            "the reason should name the id it could not read: ${reading.skipped.single().reason}",
            reading.skipped.single().reason.contains("dither.not-invented-yet"),
        )
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
        assertEquals(RenderSettings(), preset.params)
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
        assertEquals(PresetLibrary.builtIns, PresetSchema.decode(PresetSchema.encode(PresetLibrary.builtIns)))
    }
}
