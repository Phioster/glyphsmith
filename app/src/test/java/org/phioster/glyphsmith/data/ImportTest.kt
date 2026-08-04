package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

/**
 * What an import says it did.
 *
 * The information was always there — [PresetSchema.read] has collected every entry it could not
 * decode, with a reason, since the schema gained versions — and both paths into the store went
 * through `decode`, which drops it. A preset from a newer build vanished on import and nothing
 * said so.
 */
class ImportTest {

    private fun preset(name: String) = Preset(name, RenderSettings.newSession())
    private fun unknown(name: String) =
        PresetSchema.Skipped(name, "unknown dither id: \"dither.something-newer\"")

    private fun damaged(name: String) = PresetSchema.Skipped(name, "unreadable")

    private fun import(added: Int, skipped: List<PresetSchema.Skipped> = emptyList()) =
        Import(
            presets = (1..added + 5).map { preset("preset $it") },
            added = added,
            skipped = skipped,
        )

    // --- the ordinary case --------------------------------------------------------------

    @Test
    fun `an import with nothing to report says what it added`() {
        val summary = import(added = 3).summary()

        assertTrue(summary, summary.startsWith("3 imported"))
        assertTrue("nothing was skipped, so nothing should be mentioned", "skipped" !in summary)
    }

    @Test
    fun `the summary says how large the library now is`() {
        assertTrue(import(added = 3).summary().contains("8 presets"))
    }

    // --- what it had to leave behind ----------------------------------------------------

    @Test
    fun `skipped entries are counted and named`() {
        val summary = import(added = 2, skipped = listOf(unknown("aurora"), unknown("neon"))).summary()

        assertTrue(summary, "2 skipped" in summary)
        assertTrue(summary, "aurora" in summary)
        assertTrue(summary, "neon" in summary)
    }

    /**
     * The distinction the reasons exist for: a build that knows fewer styles than the file is
     * not a damaged file, and telling someone the wrong one of those sends them looking in the
     * wrong place.
     */
    @Test
    fun `an entry this build does not know is not called damaged`() {
        val summary = import(added = 1, skipped = listOf(unknown("aurora"))).summary()

        assertTrue(summary, "not known to this build" in summary)
        assertTrue(summary, "unreadable" !in summary)
    }

    @Test
    fun `a damaged entry is called damaged`() {
        val summary = import(added = 1, skipped = listOf(damaged("aurora"))).summary()

        assertTrue(summary, "unreadable" in summary)
        assertTrue(summary, "not known to this build" !in summary)
    }

    @Test
    fun `a mixture is reported as one of each`() {
        val summary = import(
            added = 1,
            skipped = listOf(unknown("aurora"), damaged("neon"), unknown("vhs")),
        ).summary()

        assertTrue(summary, "2 not known to this build" in summary)
        assertTrue(summary, "1 unreadable" in summary)
    }

    /** The status bar is one line, so a file full of unreadable entries may not fill it. */
    @Test
    fun `a long list of skipped names is cut short`() {
        val many = (1..9).map { unknown("preset $it") }

        val summary = import(added = 0, skipped = many).summary()

        assertTrue(summary, "preset 3" in summary)
        assertTrue("the fourth name should not be spelled out: $summary", "preset 4" !in summary)
        assertTrue("the rest should still be counted: $summary", "+6" in summary)
    }

    // --- the case that used to be reported wrongly --------------------------------------

    /**
     * A file this build cannot read a single entry of *is* a preset export, and saying "that
     * file isn't a preset export" sends its author looking for a problem with the wrong thing.
     */
    @Test
    fun `a file nothing could be read from still reports as an import`() {
        val summary = import(added = 0, skipped = listOf(unknown("aurora"), unknown("neon"))).summary()

        assertTrue(summary, summary.startsWith("nothing imported"))
        assertTrue(summary, "2 skipped" in summary)
        assertTrue(summary, "not known to this build" in summary)
    }

    /** Read against the real reason string rather than one written to match. */
    @Test
    fun `the reason an unknown id actually produces is recognised`() {
        val document = """
            {"schemaVersion":2,"presets":[
              {"name":"from tomorrow","category":"CUSTOM",
               "params":{"renderMode":"render.pixel-dither","ditherMode":"dither.not-invented-yet"}}
            ]}
        """.trimIndent()

        val reading = PresetSchema.read(document)

        assertEquals("the entry should not have decoded", 0, reading.presets.size)
        assertEquals(1, reading.skipped.size)
        assertEquals("from tomorrow", reading.skipped.single().name)

        val summary = Import(emptyList(), added = 0, skipped = reading.skipped).summary()
        assertTrue(
            "the real reason was not recognised as an unknown id: $summary",
            "not known to this build" in summary,
        )
    }
}
