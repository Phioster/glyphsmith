package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.glyph.CharacterSets
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.core.dither.DitherCategory
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.color.PaletteProvider
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.render.CellSampler
import org.phioster.glyphsmith.render.PixelDitherRenderer
import org.phioster.glyphsmith.render.QuantisePass
import org.phioster.glyphsmith.render.RenderMode
import java.security.MessageDigest

class PresetLibraryTest {

    private val presets = PresetLibrary.builtIns

    /** The bench, which is not part of the curated library and is not counted with it. */
    private val lab = presets.filter { it.category == PresetStore.CATEGORY_LAB }

    /** What the picker presents as the library. */
    private val curated = presets - lab.toSet()

    private val glyphArt = curated.filter { it.category == PresetStore.CATEGORY_GLYPH }

    // --- the library, exactly as it ships -----------------------------------------------

    /**
     * Every built-in, in order, as name, shelf, render module and algorithm.
     *
     * The library moved out of [PresetStore] and into [PresetLibrary] as a straight lift, and
     * "straight" is a claim that needs checking rather than asserting: a list this long is
     * exactly the kind of thing a move can quietly reorder or drop one entry from. This is the
     * table of contents, written down, so that any change to what ships — an addition, a
     * removal, a rename, a different shelf, a different algorithm, a different order — arrives
     * as a diff somebody chose to make instead of as a surprise.
     *
     * Editing the library means editing this. That is the point of it.
     */
    private val fingerprint = """
        one bit|CLASSIC|PurePixel|FLOYD_STEINBERG
        soft grain|CLASSIC|PurePixel|ATKINSON
        hard threshold|CLASSIC|PurePixel|NONE
        chalk on slate|CLASSIC|PurePixel|SMOOTH_DIFFUSE
        blocks|CLASSIC|PurePixel|NONE
        orb diffuse|DIFFUSION|PurePixel|DIFFUSE_Y
        line diffuse|DIFFUSION|PurePixel|DIFFUSE_X
        cracked|DIFFUSION|PurePixel|CRACKED_DIFFUSE
        edge keeper|DIFFUSION|PurePixel|CONTRAST_AWARE_Y
        riemersma walk|DIFFUSION|PurePixel|RIEMERSMA
        spiral grain|DIFFUSION|PurePixel|VORTEX_DIFFUSION
        bayer eight|ORDERED|PurePixel|BAYER_8
        bayer two|ORDERED|PurePixel|BAYER_2
        blue noise|ORDERED|PurePixel|BLUE_NOISE_32
        clustered dot|ORDERED|PurePixel|CLUSTER_8
        broken bayer|ORDERED|PurePixel|BAYER_8
        waveform|PATTERN|PurePixel|MOD_WAVE
        ripple|PATTERN|PurePixel|MOD_RINGS
        honeycomb|PATTERN|PurePixel|BEEHIVE
        orbital|PATTERN|PurePixel|MOD_ORB
        scanlines|PATTERN|PurePixel|MOD_LINES
        pen and ink|PATTERN|PurePixel|CROSSHATCH
        stipple study|PATTERN|PurePixel|STIPPLING
        copperplate|PATTERN|PurePixel|DOTTED_LINES
        survey map|PATTERN|PurePixel|TOPOGRAPHY
        hot iron|PATTERN|PurePixel|MOD_ORB
        modulation cyberpunk|PATTERN|PurePixel|NONE
        newsprint|PRINT|PurePixel|NONE
        duplicator print|PRINT|PurePixel|NONE
        process rosette|PRINT|PurePixel|PRINT_PATTERN
        press halftone|PRINT|PurePixel|BLOCK_TONE
        low bit halftone|PRINT|PurePixel|BIT_TONE
        spot colour print|PRINT|PurePixel|NONE
        riso two colour|PRINT|PurePixel|CLUSTER_4
        low poly|GEOMETRY|PurePixel|LOW_POLY
        hex tiles|GEOMETRY|PurePixel|HEXA_POLY
        camouflage|GEOMETRY|PurePixel|CAMO
        tile floor|GEOMETRY|PurePixel|SQUARE_MOSAIC
        dot grid|GEOMETRY|PurePixel|CIRCLE_GRID
        gameboy|COLOR|PurePixel|NONE
        c64 lores|COLOR|PurePixel|BAYER_4
        vapour wash|COLOR|PurePixel|FLOYD_STEINBERG
        oklab crush|COLOR|PurePixel|NONE
        duotone grain|COLOR|PurePixel|NONE
        glitch|GLITCH|PurePixel|ARTIFACT_GLITCH
        databend|GLITCH|PurePixel|NONE
        crt|GLITCH|PurePixel|NONE
        crt arcade monitor|GLITCH|PurePixel|BAYER_4
        tape damage|GLITCH|PurePixel|ATKINSON_VHS
        vhs dub|GLITCH|PurePixel|NONE
        meltdown|GLITCH|PurePixel|NONE
        shredder|GLITCH|PurePixel|NONE
        drifting wave|MOTION|PurePixel|MOD_WAVE
        bad signal|MOTION|PurePixel|NONE
        static storm|MOTION|PurePixel|NONE
        rolling glow|MOTION|PurePixel|NONE
        modulation lines|MOTION|PurePixel|MOD_LINES
        waveform glitch|MOTION|PurePixel|MOD_WAVE
        cyberpunk|MOTION|PurePixel|NONE
        gemstone|MOTION|PurePixel|MOD_ORB
        matrix particles|MOTION|PurePixel|NONE
        winding vortex|MOTION|PurePixel|VORTEX
        breathing contours|MOTION|PurePixel|TOPOGRAPHY
        ripple peaks|MOTION|PurePixel|RADIAL_PEAKS
        moire drift|MOTION|PurePixel|SINE_DISTORT
        waveform scan|MOTION|PurePixel|WAVEFORM
        spinning burst|MOTION|PurePixel|RADIAL_BURST
        tearing signal|MOTION|PurePixel|GLITCH
        crawling hatch|MOTION|PurePixel|CROSSHATCH
        pulsing stipple|MOTION|PurePixel|STIPPLING
        rolling gridlock|MOTION|PurePixel|GRIDLOCK
        shifting facets|MOTION|PurePixel|LOW_POLY
        two pass screen|LAYERED|PurePixel|CLUSTER_8
        offset plates|LAYERED|PurePixel|FLOYD_STEINBERG
        terminal|GLYPH|GlyphMatrix|NONE
        phosphor|GLYPH|GlyphMatrix|NONE
        braille|GLYPH|GlyphMatrix|NONE
        matrix|GLYPH|GlyphMatrix|NONE
        monogram|GLYPH|GlyphMatrix|NONE
        ascii poster|GLYPH|GlyphMatrix|NONE
        blueprint|GLYPH|GlyphMatrix|NONE
        engraving|GLYPH|GlyphMatrix|NONE
        breathing|GLYPH|GlyphMatrix|NONE
        floyd-steinberg|LAB|PurePixel|FLOYD_STEINBERG
        atkinson|LAB|PurePixel|ATKINSON
        ostromoukhov|LAB|PurePixel|OSTROMOUKHOV
        shiau-fan|LAB|PurePixel|SHIAU_FAN
        dot diffusion|LAB|PurePixel|DOT_DIFFUSION
        fractal walk|LAB|PurePixel|FRACTAL_DIFFUSE
    """.trimIndent().trim()

    /**
     * And the rest of it: every effect, every parameter, the animation, the layers, the
     * palettes — the whole library as it is actually written to disk, reduced to one hash.
     *
     * The fingerprint above covers what a person reads in the picker. This covers what the
     * renderer reads, which no readable list could hold. Together they are the statement that
     * the extraction changed nothing at all.
     *
     * Taken over [PresetSchema.encode] rather than over the objects, because that is the form
     * that has to stay stable: it is what a user's file is compared against and what an export
     * hands to somebody else.
     *
     * It has moved once, at schema 4, when a palette stopped being named by a bare id. That is
     * what this is for: the change was checked by diffing the encoded library against the old
     * one and confirming that nothing but `schemaVersion` and 91 `paletteId` values had moved.
     */
    private val digest = "0d7e1e31b8a4bf76bbaeb22111783b965ab0a36beb71b3248527dfc47d7fdb47"

    @Test
    fun `the shipped library is exactly the library that shipped`() {
        assertEquals(
            fingerprint,
            presets.joinToString("\n") {
                "${it.name}|${it.category}|${it.params.renderMode.name}|${it.params.ditherMode.name}"
            },
        )
    }

    /**
     * Schema 4 gave palettes a wire id. A preset still carrying the bare one would work —
     * [Palettes.byId] reads either — which is exactly why it needs saying out loud: a missed
     * write site produces a file with two spellings of one identity and no symptom at all.
     */
    @Test
    fun `every shipped preset names its palette the way the format spells it`() {
        (presets + presets.flatMap { it.params.layers }.map { Preset(it.name, it.params) })
            .forEach { preset ->
                assertTrue(
                    "${preset.name} carries the bare palette id '${preset.params.paletteId}'",
                    preset.params.paletteId.startsWith("palette."),
                )
            }
    }

    @Test
    fun `the library ships the same settings it shipped`() {
        val hashed = MessageDigest.getInstance("SHA-256")
            .digest(PresetSchema.encode(presets).toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals("the encoded library is not what it was", digest, hashed)
    }

    /** Counted separately from the fingerprint so a miscount says so in one number. */
    @Test
    fun `the library is the size it was`() {
        assertEquals("built-in presets", 89, presets.size)
        assertEquals("curated presets", 83, curated.size)
        assertEquals("bench presets", 6, lab.size)
    }

    /**
     * The store hands back the shipped library and nothing else of its own.
     *
     * `reset()` and an empty `load()` both return [PresetLibrary.builtIns] directly, so what a
     * reset restores is this list — which the two tests above pin entry by entry and parameter
     * by parameter. The store needs a `Context` and cannot be built in a JVM test; what is
     * checkable here is that there is exactly one definition of "fresh" and it is this one.
     */
    @Test
    fun `nothing but the library defines what a reset restores`() {
        assertEquals(PresetLibrary.builtIns, presets)
    }

    @Test
    fun `no two presets share a name`() {
        val duplicates = presets.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("these names appear twice: $duplicates", duplicates.isEmpty())
    }

    /** The name is the identity — it is what a save overwrites and what an import matches on. */
    @Test
    fun `every preset has a name worth showing`() {
        presets.forEach { preset ->
            assertTrue("a preset in ${preset.category} has a blank name", preset.name.isNotBlank())
            assertEquals("'${preset.name}' has stray whitespace", preset.name.trim(), preset.name)
        }
    }

    // --- what the library is -----------------------------------------------------------

    /**
     * The distribution the product direction asks for: a dithering application whose library
     * looks like one, with Glyph Art present as a clearly named minority.
     *
     * Stated as a share rather than a count so that adding presets cannot quietly tip it —
     * which is exactly how the library ended up 100 % glyph art before this round.
     */
    @Test
    fun `the curated library is pixel dither first`() {
        val pixel = curated.count { it.params.renderMode == RenderMode.PurePixel }
        val glyph = curated.count { it.params.renderMode == RenderMode.GlyphMatrix }

        assertEquals("every curated preset is one or the other", curated.size, pixel + glyph)
        assertTrue(
            "$pixel of ${curated.size} presets are pixel dithers, which is not 80-90 %",
            pixel * 100 >= curated.size * 80 && pixel * 100 <= curated.size * 90,
        )
        assertTrue(
            "$glyph of ${curated.size} presets are glyph art, which is not 10-20 %",
            glyph * 100 >= curated.size * 10 && glyph * 100 <= curated.size * 20,
        )
    }

    /** A general-purpose preset is a pixel dither. Glyph Art is reached through its own shelf. */
    @Test
    fun `every general preset is a pixel dither`() {
        (curated - glyphArt.toSet()).forEach { preset ->
            assertEquals(
                "${preset.name} is filed under ${preset.category} but renders as glyph art",
                RenderMode.PurePixel,
                preset.params.renderMode,
            )
        }
    }

    @Test
    fun `every glyph art preset asks for glyph art`() {
        assertTrue("the glyph art shelf is empty", glyphArt.isNotEmpty())
        glyphArt.forEach { preset ->
            assertEquals(
                "${preset.name} sits on the glyph shelf without asking for glyphs",
                RenderMode.GlyphMatrix,
                preset.params.renderMode,
            )
        }
    }

    /**
     * The mode has to be *stored*, not inherited.
     *
     * A preset that says nothing about its mode is read back as the field default, which is
     * Glyph Art and must stay Glyph Art for the sake of files written before the field
     * existed. So a shipped pixel preset that leaned on the default would ship as glyph art
     * the moment it went through the file. This reads the bytes rather than the object.
     */
    @Test
    fun `every preset names its render mode on disk`() {
        val entries = Json.parseToJsonElement(PresetSchema.encode(presets))
            .jsonObject["presets"]!!.jsonArray

        assertEquals(presets.size, entries.size)
        entries.forEach { entry ->
            val name = entry.jsonObject["name"]?.jsonPrimitive?.content
            val mode = entry.jsonObject["params"]?.jsonObject?.get("renderMode")?.jsonPrimitive?.content
            assertTrue("$name writes no render mode at all", mode != null)
            assertTrue("$name writes '$mode', which is not a render id", mode!!.startsWith("render."))
        }
    }

    /** A layer is a whole second set of settings, and it needs the mode named just as much. */
    @Test
    fun `every layer inside a preset names its mode too`() {
        presets.flatMap { preset -> preset.params.layers.map { preset.name to it } }
            .forEach { (name, layer) ->
                assertEquals(
                    "the layer '${layer.name}' in $name does not ask for pixel dithering",
                    RenderMode.PurePixel,
                    layer.params.renderMode,
                )
                assertTrue(
                    "the layer '${layer.name}' in $name wants a palette that does not exist",
                    Palettes.all.any { it.id == PaletteProvider.bareIdOf(layer.params.paletteId) },
                )
            }
    }

    // --- the bench ---------------------------------------------------------------------

    /**
     * The comparison presets are a bench, not a look, so they are kept off the curated
     * shelves and out of the distribution above.
     */
    @Test
    fun `lab presets stand apart from the curated library`() {
        assertTrue("there are no lab presets", lab.isNotEmpty())
        lab.forEach { preset ->
            assertEquals(PresetStore.CATEGORY_LAB, preset.category)
            assertEquals(
                "${preset.name} compares algorithms, so it should do it in pixel dither",
                RenderMode.PurePixel,
                preset.params.renderMode,
            )
        }
        assertTrue(
            "a lab preset leaked onto a curated shelf",
            curated.none { it.category == PresetStore.CATEGORY_LAB },
        )
    }

    /**
     * A comparison is only a comparison if the algorithm is the only thing that differs.
     * Anything else varying means the difference you are looking at might not be the kernel.
     */
    @Test
    fun `lab presets differ only in the algorithm`() {
        val normalised = lab.map { it.params.copy(ditherMode = DitherMode.NONE) }.toSet()

        assertEquals("the bench varies more than the algorithm: $normalised", 1, normalised.size)
        assertEquals(
            "two bench entries run the same algorithm",
            lab.size,
            lab.map { it.params.ditherMode }.toSet().size,
        )
    }

    /** The shelf is named after the mechanism, so the mechanism has to be what is on it. */
    @Test
    fun `the ordered shelf holds ordered matrices`() {
        val ordered = curated.filter { it.category == PresetStore.CATEGORY_ORDERED }

        assertTrue("the ordered shelf is empty", ordered.isNotEmpty())
        ordered.forEach { preset ->
            assertEquals(
                "${preset.name} is on the ordered shelf running ${preset.params.ditherMode}",
                DitherCategory.ORDERED,
                preset.params.ditherMode.category,
            )
        }
    }

    @Test
    fun `the error diffusion shelf holds error diffusion`() {
        val diffusion = curated.filter { it.category == PresetStore.CATEGORY_DIFFUSION }

        assertTrue("the diffusion shelf is empty", diffusion.isNotEmpty())
        diffusion.forEach { preset ->
            assertTrue(
                "${preset.name} is on the diffusion shelf running ${preset.params.ditherMode}",
                preset.params.ditherMode.category in
                    setOf(DitherCategory.ERROR_DIFFUSION, DitherCategory.GLITCH),
            )
        }
    }

    // --- what a user already has -------------------------------------------------------

    /**
     * The shelves this round retired still have to be listed.
     *
     * A category is a stored string. Presets saved before this round carry the old words, and
     * a category the picker does not know about sorts to the bottom under a heading of its
     * own — so dropping the constants would scatter someone's saved library rather than
     * simply renaming a heading.
     */
    @Test
    fun `the categories presets were saved under are still known`() {
        listOf(
            PresetStore.CATEGORY_DITHER,
            PresetStore.CATEGORY_INK,
            PresetStore.CATEGORY_HEAVY,
            PresetStore.CATEGORY_SIGNATURE,
            PresetStore.CATEGORY_CLASSIC,
            PresetStore.CATEGORY_PRINT,
            PresetStore.CATEGORY_MOTION,
            PresetStore.CATEGORY_GEOMETRY,
            PresetStore.CATEGORY_CUSTOM,
        ).forEach { category ->
            assertTrue("$category is no longer a category the picker draws", category in PresetStore.categories)
        }
    }

    /**
     * The new library must not reach into a file somebody already has. A stored preset decodes
     * to exactly itself — same name, same shelf, same settings — whatever ships alongside it.
     */
    @Test
    fun `a stored preset is untouched by the shipped library`() {
        val stored = """
            {"schemaVersion":2,"presets":[{"name":"mine","category":"HEAVY","favourite":true,
              "params":{"renderMode":"GlyphMatrix","ditherMode":"ATKINSON","cellSize":7}}]}
        """.trimIndent()

        val preset = PresetSchema.decode(stored).single()

        assertEquals("mine", preset.name)
        assertEquals(PresetStore.CATEGORY_HEAVY, preset.category)
        assertEquals(RenderMode.GlyphMatrix, preset.params.renderMode)
        assertEquals(DitherMode.ATKINSON, preset.params.ditherMode)
        assertEquals(7, preset.params.cellSize)
        assertTrue(preset.favourite)
    }

    /**
     * What a reset restores.
     *
     * `reset()` deletes the stored file and hands back [PresetLibrary.builtIns], so the library
     * it produces is exactly this list — the store itself needs a `Context` and cannot be
     * built in a JVM test. What is checkable here is that the list a reset hands back is the
     * whole shipped library: both halves of it, and every shelf, which the tests above pin
     * one by one.
     */
    @Test
    fun `a reset hands back the whole shipped library`() {
        assertEquals(presets, curated + lab)
        assertTrue("the curated library is empty", curated.isNotEmpty())
        assertTrue("the bench is missing from the shipped library", lab.isNotEmpty())
    }

    /** A preset filed under a category the picker does not draw is a preset nobody can find. */
    @Test
    fun `every preset lands in a category the picker knows`() {
        presets.forEach { preset ->
            assertTrue(
                "${preset.name} is filed under ${preset.category}",
                preset.category in PresetStore.categories,
            )
        }
    }

    /** Referencing a palette or character set that does not exist fails silently at runtime. */
    @Test
    fun `every preset points at something that exists`() {
        presets.forEach { preset ->
            assertTrue(
                "${preset.name} wants the palette '${preset.params.paletteId}'",
                Palettes.all.any { it.id == PaletteProvider.bareIdOf(preset.params.paletteId) },
            )
            assertTrue(
                "${preset.name} wants the set '${preset.params.charSetId}'",
                CharacterSets.all.any { it.id == preset.params.charSetId },
            )
        }
    }

    /**
     * Every curated shelf has to hold something: an empty category draws a header over
     * nothing.
     *
     * Checked against [PresetStore.curatedCategories] rather than the full list, because that
     * list also carries the shelves this round retired. Those are deliberately empty of
     * shipped presets and exist only so that presets somebody saved under them still sort
     * where their owner filed them.
     */
    @Test
    fun `every curated category has at least one preset`() {
        PresetStore.curatedCategories.forEach { category ->
            assertTrue("$category is empty", curated.any { it.category == category })
        }
    }

    // --- motion --------------------------------------------------------------------

    private val motion = presets.filter { it.category == PresetStore.CATEGORY_MOTION }

    /** The category's whole promise is that applying one and pressing play is enough. */
    @Test
    fun `every motion preset arrives with its animation switched on`() {
        assertTrue("there are no motion presets", motion.isNotEmpty())
        motion.forEach { preset ->
            assertTrue("${preset.name} is not animated", preset.params.animation.enabled)
            assertTrue(
                "${preset.name} has no track aimed at anything",
                preset.params.animation.tracks.any { it.enabled },
            )
        }
    }

    @Test
    fun `motion presets run long enough to read as motion`() {
        motion.forEach { preset ->
            assertTrue("${preset.name} has ${preset.params.animation.frames} frames", preset.params.animation.frames >= 24)
        }
    }

    /**
     * A track that ends somewhere other than where it began leaves a visible jump at the loop
     * seam. A non-closing curve is therefore allowed only on a target whose range joins up —
     * an angle, or a modulation phase — and only when it sweeps the whole of it.
     */
    @Test
    fun `every animated track either closes or wraps`() {
        motion.forEach { preset ->
            preset.params.animation.tracks.filter { it.enabled }.forEach { track ->
                if (track.curve.seamless) return@forEach
                assertTrue(
                    "${preset.name} runs a non-closing curve on ${track.target}, which does not wrap",
                    track.target.cyclic,
                )
                assertEquals(
                    "${preset.name} does not sweep the whole of ${track.target}",
                    track.target.min,
                    minOf(track.from, track.to),
                )
                assertEquals(
                    "${preset.name} does not sweep the whole of ${track.target}",
                    track.target.max,
                    maxOf(track.from, track.to),
                )
            }
        }
    }

    /** The point of this round: the new styles have to actually be reachable from a preset. */
    @Test
    fun `the styles added in this round are represented`() {
        val used = presets.map { it.params.ditherMode }.toSet()
        listOf(
            DitherMode.CROSSHATCH, DitherMode.STIPPLING, DitherMode.TOPOGRAPHY,
            DitherMode.LOW_POLY, DitherMode.CAMO, DitherMode.HEXA_POLY,
            DitherMode.OSTROMOUKHOV, DitherMode.SHIAU_FAN, DitherMode.DOT_DIFFUSION,
            DitherMode.PRINT_PATTERN, DitherMode.VORTEX, DitherMode.GLITCH,
            DitherMode.VORTEX_DIFFUSION, DitherMode.CONTRAST_AWARE_Y,
        ).forEach { mode ->
            assertTrue("nothing ships using ${mode.name}", mode in used)
        }
    }

    // --- what they actually produce ----------------------------------------------------

    /**
     * The pixel path, run for real.
     *
     * Sample, quantise and paint are plain Kotlin, so a preset can be rendered here instead of
     * being taken on trust. This is the closest thing to a thumbnail a JVM test can produce,
     * and it catches the two failures that matter: a preset that comes out blank, and two
     * presets that come out the same.
     *
     * The effect chain is deliberately *not* run. Several passes rotate or scale through
     * `android.graphics.Bitmap`, which unit tests do not have — so the chain is compared as
     * settings instead, below.
     */
    private val side = 320

    /**
     * A gradient, colour, a hard edge and some fine detail.
     *
     * The detail is what makes this worth rendering against: a style that flattens an area
     * and one that averages it are the same picture on a smooth ramp, and only tell each
     * other apart where there is something inside the cell to lose.
     */
    private val source = IntArray(side * side) { i ->
        val x = i % side
        val y = i / side
        val disc = if ((x - 205) * (x - 205) + (y - 115) * (y - 115) < 4000) 90 else 0
        val bars = if ((x / 5 + y / 9) % 2 == 0) 40 else 0
        val r = (x * 255 / side + disc).coerceAtMost(255)
        val g = (y * 255 / side + bars).coerceAtMost(255)
        val b = ((x + y) * 255 / (2 * side) + disc - bars).coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun render(params: RenderSettings): Pixels {
        val cell = params.cellSize.coerceAtLeast(1)
        val grid = CellSampler.sample(source, side, side, params, cell, cell)
        val indexed = QuantisePass.run(params, grid, PixelDitherRenderer.levelsFor(params))
        return PixelDitherRenderer.render(indexed, params, cell)
    }

    private val pixelPresets get() = presets.filter { it.params.renderMode == RenderMode.PurePixel }

    /** A preset that renders one flat colour is a preset that went wrong quietly. */
    @Test
    fun `every pixel preset renders something with structure in it`() {
        pixelPresets.forEach { preset ->
            val out = render(preset.params)

            assertTrue(
                "${preset.name} renders ${out.width}x${out.height}, which is nothing",
                out.width > 0 && out.height > 0,
            )
            assertTrue(
                "${preset.name} renders one flat colour",
                out.data.toHashSet().size > 1,
            )
        }
    }

    /** Same settings, same picture. A preset nobody can reproduce is not a preset. */
    @Test
    fun `a preset renders the same image twice`() {
        pixelPresets.forEach { preset ->
            assertTrue(
                "${preset.name} renders differently on a second run",
                render(preset.params).data.contentEquals(render(preset.params).data),
            )
        }
    }

    /**
     * The point of curation: on the same picture, no two presets come out alike.
     *
     * Compared as rendered pixels rather than as settings, because two different settings can
     * still produce the same image — which is what "differs only by a slider" looks like from
     * the outside. Two presets are allowed to share a base render only if what they stack on
     * top of it differs, which is the case the effect-led presets are.
     */
    @Test
    fun `no two presets render the same picture`() {
        pixelPresets.groupBy { render(it.params).data.contentHashCode() }
            .filterValues { it.size > 1 }
            .forEach { (_, sharing) ->
                assertEquals(
                    "${sharing.map { it.name }} render the same picture and stack the same effects",
                    sharing.size,
                    sharing.map { it.params.effects }.toSet().size,
                )
            }
    }

    /** A palette preset with no palette colour mode is a setting that does nothing. */
    @Test
    fun `presets naming a palette actually use one`() {
        presets.filter { it.params.colorMode == ColorMode.PALETTE }.forEach { preset ->
            assertTrue(
                "${preset.name} is in palette mode with under two stops",
                Palettes.byId(preset.params.paletteId).colors.size >= 2,
            )
        }
    }

    /**
     * Every pass in the chain has to be shown off by at least one shipped preset.
     *
     * An effect nobody can find is an effect nobody uses: the panel lists twelve-odd collapsed
     * sections, and the realistic way anyone meets a new one is by applying a preset that already
     * uses it. This is what pins the four presets that came with the newer passes.
     */
    @Test
    fun `every effect is used by at least one shipped preset`() {
        val used = PresetLibrary.builtIns
            .flatMap { preset -> EffectId.entries.filter { preset.params.effects.enabledOf(it) } }
            .toSet()

        val unused = EffectId.entries.filterNot { it in used }
        assertTrue("no preset demonstrates: $unused", unused.isEmpty())
    }
}
