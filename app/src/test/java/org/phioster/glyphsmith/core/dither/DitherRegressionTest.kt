package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import org.phioster.glyphsmith.core.dither.PatternOptions
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.Dither
import org.phioster.glyphsmith.glyph.GlyphEngine
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The net under the algorithm round.
 *
 * Roughly fifty styles are about to be added, and every one of them touches shared machinery:
 * the threshold function, the kernel lookup, the loop that walks the grid. The danger is not
 * that a new style is wrong — that gets noticed immediately, because someone went looking for
 * it. The danger is that adding it quietly changes an old one that nobody thought to check.
 */
class DitherRegressionTest {

    /**
     * Small cells on a large image, giving a 64×32 grid.
     *
     * The size is load-bearing, which is not obvious. A 16×16 Bayer tile needs sixteen rows
     * before it differs from the 8×8 one it contains, and a staggered orb lattice needs two
     * lattice rows before the stagger exists at all. On a coarser grid those pairs render
     * identically — correctly so — and the test below would call it a collision.
     */
    private val params = RenderSettings(charSetId = "ascii-standard-10", depth = 10, cellSize = 2)

    /** A gradient with a hard diagonal through it — flat fields alone hide most mistakes. */
    private fun testImage(width: Int = SIDE, height: Int = SIDE): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val ramp = (x * 255 / (width - 1))
        val v = if (x + y > width) (255 - ramp) else ramp
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    private fun render(mode: DitherMode): String =
        GlyphEngine.convert(testImage(), SIDE, SIDE, params.copy(ditherMode = mode)).toText()


    // --- what each of them actually draws -------------------------------------------------

    /**
     * Every algorithm's output, pinned.
     *
     * The tests above check *properties* — that a style fills the grid, ignores brightness,
     * renders the same thing twice. All of them would still pass if a kernel moved by one tap
     * and every picture the app has ever made changed. Nothing held the algorithms to what they
     * actually draw, which is the one thing a dithering application is.
     *
     * That gap matters most now. The plugin work ahead has to take twelve `when (mode)`
     * dispatches apart and give each algorithm its own declaration, touching all eighty on the
     * way. A refactor of that shape is exactly where a kernel silently swaps places with its
     * neighbour, and where determinism and grid size both keep saying everything is fine.
     *
     * Truncated to 16 hex characters: enough that a collision is not a thing that happens, short
     * enough that the table can be read.
     *
     * **When one of these changes**, the question is not how to update the number. It is which
     * algorithm changed and whether that was the intention. If it was, the new digest goes in
     * with a note saying why.
     */
    private val golden: Map<DitherMode, String> = mapOf(
        DitherMode.NONE to "4d4bba66e994f47e",
        DitherMode.FLOYD_STEINBERG to "68fa7c8a31e5f8c7",
        DitherMode.ATKINSON to "f456c71aa525a9d7",
        DitherMode.JARVIS to "7499fe7870cfb0a0",
        DitherMode.SIERRA_LITE to "4ee61833a366e555",
        DitherMode.STUCKI to "12dd843f311dc4a1",
        DitherMode.BURKES to "8941b133f0f18f72",
        DitherMode.SIERRA to "f1ecc8f7ddfb74be",
        DitherMode.SIERRA_TWO_ROW to "9c2d6b3daeb6b36f",
        DitherMode.FALSE_FLOYD to "13b654c11e4f92ce",
        DitherMode.STEVENSON_ARCE to "4ace42ef54ec98e5",
        DitherMode.SMOOTH_DIFFUSE to "a6cde635fd282236",
        DitherMode.RIEMERSMA to "830cfe236c125f63",
        DitherMode.DIFFUSE_Y to "baf58defd3138b0c",
        DitherMode.DIFFUSE_X to "78633c120ccf4b94",
        DitherMode.BAYER_2 to "ba54a9e642b71b4a",
        DitherMode.BAYER_4 to "e7bd2d02a2ba4aed",
        DitherMode.BAYER_8 to "7bc2df5e5f7d3b6d",
        DitherMode.BAYER_16 to "0dc174a8f5dec2e2",
        DitherMode.CLUSTER_4 to "1a73c188835506aa",
        DitherMode.CLUSTER_8 to "40440e3555c21f3e",
        DitherMode.BLUE_NOISE_16 to "525fb2a8233add45",
        DitherMode.BLUE_NOISE_32 to "a417b7c90b6237b2",
        DitherMode.MOD_LINES to "077f6941a0f04a84",
        DitherMode.MOD_WAVE to "92e1453dc525040c",
        DitherMode.MOD_RINGS to "f3f887e249990eb5",
        DitherMode.MOD_ORB to "f2611f37b3d3aa51",
        DitherMode.BEEHIVE to "62886cb2b94010fc",
        DitherMode.UNIFORM_MODULATION to "270a1e452156f951",
        DitherMode.HEART_GRID to "70ee386124ad6be7",
        DitherMode.POP_TONE to "c8c13b25db014082",
        DitherMode.CHECKERS to "60e2f2331cbf059b",
        DitherMode.DIAMOND to "92036e1df08f3d25",
        DitherMode.CROSSHATCH to "411c062b6ade729c",
        DitherMode.STIPPLING to "1aae5b87dc2090ad",
        DitherMode.BIT_TONE to "b845dc008aa50d04",
        DitherMode.BLOCK_TONE to "df240f6793d8d8b7",
        DitherMode.PRINT_PATTERN to "7eb90d820997e2a1",
        DitherMode.GRIDLOCK to "d502ce0d3225764a",
        DitherMode.RANDOM_ORDERED to "dca49afb94c8cee2",
        DitherMode.NOISE to "a94004410de635cf",
        DitherMode.WAVE to "75a59b097a11d2fd",
        DitherMode.RADIAL_BURST to "240994333b78a2ed",
        DitherMode.SINE_DISTORT to "67259e8c8803657f",
        DitherMode.MOSAIC to "daa45d7ae318dd3c",
        DitherMode.SQUARE_MOSAIC to "987e150635381ab5",
        DitherMode.CIRCLE_GRID to "9b71553e7a4f951e",
        DitherMode.DIAMOND_GRID to "8a2ab7a281199f0c",
        DitherMode.TRI_POLY to "cc823169dd806505",
        DitherMode.HEXA_POLY to "80d5ceef2eb3eca6",
        DitherMode.PENTA_POLY to "ccaa58856916547e",
        DitherMode.LOW_POLY to "57cbfbb6ee61c03e",
        DitherMode.CAMO to "faa4c7250ecdda3b",
        DitherMode.OSTROMOUKHOV to "f6a3405da43fa33b",
        DitherMode.SHIAU_FAN to "1eb44900cca3432d",
        DitherMode.DOT_DIFFUSION to "541a04030d9f3a0e",
        DitherMode.GAUSSIAN to "2d10b709284cead2",
        DitherMode.ARTIFACT_MODULATION to "06376cc9109cc439",
        DitherMode.VARIABLE_ERROR to "1e61747e40ddb66c",
        DitherMode.FRACTAL_DIFFUSE to "c7a0b70194a2fbdf",
        DitherMode.WAVEFORM to "0b89d42d646525f0",
        DitherMode.WAVEFORM_ALT to "5b68d91595cc104f",
        DitherMode.THRESHOLDER to "1ae80b3b3f7166d2",
        DitherMode.SINE_WAVE_MOD to "325c95b9f467e756",
        DitherMode.TOPOGRAPHY to "37fd74dac6417b92",
        DitherMode.VORTEX to "a8a4f088f499d39e",
        DitherMode.RADIAL_PEAKS to "1193603904dc6874",
        DitherMode.DOTTED_LINES to "02f343cc05b91f81",
        DitherMode.DISPLACE_CONTOUR to "b2e535fe8b93c339",
        DitherMode.ORB_MATRIX to "c72471d92c495d35",
        DitherMode.ORDERED_MODULATION to "ac8410b3bbf3a8a4",
        DitherMode.GLITCH to "bd5736a6a00a7e4a",
        DitherMode.CRACKED_DIFFUSE to "d1545f2fda668990",
        DitherMode.ARTIFACT_GLITCH to "894515e01f23aa49",
        DitherMode.ATKINSON_VHS to "dd0fceef1538fc72",
        DitherMode.ATKINSON_LINE_MOD to "d77ae212711aa7b9",
        DitherMode.STUCKI_LINES to "dfedbc3688287d65",
        DitherMode.CONTRAST_AWARE_X to "60e683c0cdb0e692",
        DitherMode.CONTRAST_AWARE_Y to "0569d1820cc45314",
        DitherMode.VORTEX_DIFFUSION to "2fb8a78bdf09d84a",

        // Added with the modulated-diffusion kind. Their pins were taken on the first build that
        // had them; the other eighty were confirmed unchanged in the same run, which is the
        // whole reason this table exists.
        // Its default screen, not an imported one — that is what a fresh document renders, and
        // pinning it is what would catch the default changing by accident.
        DitherMode.CUSTOM_SCREEN to "8c09f44764b5c186",
        DitherMode.WAVE_DIFFUSE to "f92721283ea89bd0",
        DitherMode.ORB_DIFFUSE to "8eb16b35408f32fd",
    )

    @Test
    fun `every algorithm draws exactly what it drew`() {
        val moved = DitherMode.entries.mapNotNull { mode ->
            val expected = golden[mode] ?: return@mapNotNull "$mode is not pinned at all"
            val actual = digestOf(mode)
            if (actual == expected) null else "$mode: expected $expected, drew $actual"
        }

        assertTrue("these algorithms no longer draw what they drew:\n${moved.joinToString("\n")}", moved.isEmpty())
    }

    /** A style added without a digest would otherwise be covered by nothing here. */
    @Test
    fun `every algorithm is pinned`() {
        assertEquals(DitherMode.entries.toSet(), golden.keys)
    }

    /**
     * Eighty algorithms and eighty pictures. Two that agree on this image are two the picker
     * offers as separate choices while drawing the same thing — worth knowing either way.
     */
    @Test
    fun `no two algorithms draw the same picture`() {
        val shared = golden.entries.groupBy { it.value }.filterValues { it.size > 1 }

        assertTrue(
            "these draw identically: ${shared.values.map { g -> g.map { it.key } }}",
            shared.isEmpty(),
        )
    }

    private fun digestOf(mode: DitherMode): String =
        MessageDigest.getInstance("SHA-256").digest(render(mode).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(DIGEST_CHARS)


    /**
     * The one property this phase can actually prove about itself.
     *
     * `threshold` grew a brightness argument so that later styles can read the picture as
     * well as the position. Every style that existed before must ignore it completely — if
     * one starts listening, its texture changes with the image content and no saved preset
     * looks the way it did.
     */
    @Test
    fun `every style that is not content aware ignores brightness`() {
        val options = PatternOptions()
        DitherMode.entries
            .filter { Dither.isThresholdBased(it) && !Dither.isContentAware(it) }
            .forEach { mode ->
            for (x in 0 until 17) {
                for (y in 0 until 17) {
                    val dark = Dither.threshold(mode, x, y, 0f, options)
                    val mid = Dither.threshold(mode, x, y, 0.5f, options)
                    val light = Dither.threshold(mode, x, y, 1f, options)
                    assertEquals("$mode reacted to brightness at ($x,$y)", dark, mid, 0f)
                    assertEquals("$mode reacted to brightness at ($x,$y)", dark, light, 0f)
                }
            }
        }
    }

    /**
     * And the other half of that split: a style that claims to read the picture must actually
     * do so. A crosshatch that ignores brightness is a grid of lines.
     */
    @Test
    fun `every content aware style reacts to brightness`() {
        val options = PatternOptions()
        val aware = DitherMode.entries.filter { Dither.isContentAware(it) }
        assertTrue("nothing claims to be content aware", aware.isNotEmpty())
        aware.forEach { mode ->
            val differs = (0 until 24).any { x ->
                (0 until 24).any { y ->
                    Dither.threshold(mode, x, y, 0.05f, options) !=
                        Dither.threshold(mode, x, y, 0.95f, options)
                }
            }
            assertTrue("$mode ignores the brightness it asked for", differs)
        }
    }

    @Test
    fun `every style produces a full grid of glyphs`() {
        DitherMode.entries.forEach { mode ->
            val art = GlyphEngine.convert(testImage(), SIDE, SIDE, params.copy(ditherMode = mode))
            assertEquals("$mode returned the wrong size", art.cols * art.rows, art.glyphs.size)
            assertTrue("$mode produced no glyphs", art.glyphs.isNotEmpty())
        }
    }

    /** A one-cell image is the smallest thing that can go wrong at an array boundary. */
    @Test
    fun `every style survives a single cell`() {
        DitherMode.entries.forEach { mode ->
            val art = GlyphEngine.convert(IntArray(4) { -1 }, 2, 2, params.copy(ditherMode = mode))
            assertTrue("$mode failed on a 2x2 image", art.glyphs.isNotEmpty())
        }
    }

    /**
     * Rendering is deterministic — twice through the same style gives the same picture.
     *
     * Sounds trivial; it is not. The generated matrices are built lazily and cached, and the
     * orb lattice hashes its own coordinates. Either could pick up state between runs.
     */
    @Test
    fun `rendering twice gives the same result`() {
        DitherMode.entries.forEach { mode ->
            assertEquals("$mode is not deterministic", render(mode), render(mode))
        }
    }

    /**
     * Two different styles must not render identically.
     *
     * This is what catches a new style wired to the wrong branch — it compiles, it appears in
     * the picker, and it silently draws what Floyd–Steinberg drew. No pair is exempt: if two
     * styles agree on this image, either one of them is misrouted or the grid is too coarse
     * to tell them apart, and both are worth stopping for.
     */
    @Test
    fun `styles are distinguishable from one another`() {
        val rendered = DitherMode.entries.associateWith { render(it) }
        val collisions = rendered.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .map { group -> group.value.map { it.key.name } }
        assertTrue("these styles render identically: $collisions", collisions.isEmpty())
    }

    private companion object {
        const val SIDE = 128

        /** Enough that a collision does not happen; short enough that the table reads. */
        const val DIGEST_CHARS = 16
    }
}
