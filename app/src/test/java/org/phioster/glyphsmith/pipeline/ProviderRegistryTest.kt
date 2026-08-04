package org.phioster.glyphsmith.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.DitherModeIds
import org.phioster.glyphsmith.core.dither.DitherProviders
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.serial.UnknownWireIdException
import org.phioster.glyphsmith.core.serial.WireId
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectIds
import org.phioster.glyphsmith.effects.EffectProviders
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModeIds
import org.phioster.glyphsmith.render.RenderModules

class ProviderRegistryTest {

    // --- the gap this closes ------------------------------------------------------------

    /**
     * The reason the registries exist.
     *
     * The id rules were already tested, over a list of three serialisers written out by hand in
     * `WireIdTest`. A fourth category would have compiled, shipped and been covered by nothing —
     * the test would still have passed, over the three it happened to know about. Here the
     * enumeration is the production one, so a category that nobody registers fails here instead
     * of going quietly untested.
     */
    @Test
    fun `every provider category is registered`() {
        val registered = Providers.all.map { it.category }.toSet()

        assertEquals(
            "a provider category exists that no registry covers",
            ProviderCategory.entries.toSet(),
            registered,
        )
        assertEquals("a category is registered twice", ProviderCategory.entries.size, Providers.all.size)
    }

    // --- ids ----------------------------------------------------------------------------

    @Test
    fun `every id is well formed and carries its own category`() {
        Providers.all.forEach { registry ->
            registry.all.forEach { provider ->
                assertTrue(
                    "${provider.displayName} has a malformed id: \"${provider.id}\"",
                    WireId.isValid(provider.id),
                )
                assertTrue(
                    "${provider.id} is registered as a ${registry.category}",
                    provider.id.startsWith("${registry.category.prefix}."),
                )
            }
        }
    }

    /**
     * Unique across *all* categories, not merely within one.
     *
     * The per-category rule is what correctness strictly needs, since an id is only ever
     * resolved inside its own registry. This is the stronger claim, and it is the one that lets
     * [Providers.find] take an id and nothing else: a build where two categories shared one
     * would make that lookup depend on which registry was consulted first.
     */
    @Test
    fun `no two providers anywhere share an id`() {
        val duplicates = Providers.everything
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys

        assertTrue("these ids appear more than once: $duplicates", duplicates.isEmpty())
    }

    /**
     * The registries describe what the serialisers write, so the two may not drift. If they
     * did, a picker would offer an id that no preset could ever contain.
     */
    @Test
    fun `provider ids are the ids presets are written with`() {
        RenderMode.entries.forEach {
            assertEquals(RenderModeIds.idOf(it), RenderModules.of(it).id)
        }
        DitherMode.entries.forEach {
            assertEquals(DitherModeIds.idOf(it), DitherProviders.of(it).id)
        }
        EffectId.entries.forEach {
            assertEquals(EffectIds.idOf(it), EffectProviders.of(it).id)
        }
    }

    // --- lookup -------------------------------------------------------------------------

    @Test
    fun `a provider is found by the id it is registered under`() {
        Providers.everything.forEach { provider ->
            assertEquals(provider, Providers.find(provider.id))
            assertEquals(provider, Providers.registryFor(provider.category).find(provider.id))
        }
    }

    @Test
    fun `an unknown id is refused rather than resolved to something else`() {
        assertNull(Providers.find("dither.no-such-algorithm"))
        assertNull(DitherProviders.find("dither.no-such-algorithm"))

        try {
            DitherProviders.require("dither.no-such-algorithm")
            fail("an unknown id was resolved to something")
        } catch (e: UnknownWireIdException) {
            assertEquals("dither", e.category)
            assertEquals("dither.no-such-algorithm", e.id)
        }
    }

    /** An id from the wrong category is as unknown as one nobody ever minted. */
    @Test
    fun `a registry does not answer for another category`() {
        assertNull(DitherProviders.find(RenderModeIds.idOf(RenderMode.PurePixel)))
        assertNotNull(Providers.find(RenderModeIds.idOf(RenderMode.PurePixel)))
    }

    // --- coverage and metadata ----------------------------------------------------------

    /** A provider missing from its registry is a thing the picker cannot offer at all. */
    @Test
    fun `every algorithm, effect and module has a provider`() {
        assertEquals(RenderMode.entries.size, RenderModules.all.size)
        assertEquals(DitherMode.entries.size, DitherProviders.all.size)
        assertEquals(EffectId.entries.size, EffectProviders.all.size)
    }

    @Test
    fun `every provider has a name worth showing`() {
        Providers.everything.forEach { provider ->
            assertTrue("${provider.id} has no name", provider.displayName.isNotBlank())
            assertTrue(
                "${provider.id} is shown as an identifier: ${provider.displayName}",
                provider.displayName != provider.displayName.uppercase() ||
                    provider.displayName.length <= 4,
            )
        }
    }

    /**
     * The registry is what the mode dropdown lists, so its order is the order on screen.
     * Reordering it may be worth doing — pixel dither is the default — but as a decision about
     * the product, not as a side effect of describing the modules.
     */
    @Test
    fun `the render modules are listed in the order they are declared in`() {
        assertEquals(RenderMode.entries.toList(), RenderModules.all.map { it.mode })
    }

    @Test
    fun `the effect registry follows the chain's default order`() {
        assertEquals(EffectId.entries.toList(), EffectProviders.all.map { it.effect })
    }

    // --- capabilities -------------------------------------------------------------------

    /**
     * Capabilities are what the UI actually branches on — whether the glyph controls are worth
     * drawing, whether a `.txt` export exists at all — so they have to agree with the modes.
     */
    @Test
    fun `the render modules declare what they can do`() {
        val glyph = RenderModules.of(RenderMode.GlyphMatrix)
        val pixel = RenderModules.of(RenderMode.PurePixel)
        val both = RenderModules.of(RenderMode.PixelThenGlyph)

        assertTrue("glyph art produces a character grid", glyph.producesGlyphs)
        assertTrue("pixel dither produces no character grid", !pixel.producesGlyphs)
        assertTrue("the combined mode produces a character grid", both.producesGlyphs)

        assertTrue("pixel dither dithers first", pixel.ditherFirst)
        assertTrue("the combined mode dithers first", both.ditherFirst)
        assertTrue("glyph art does not dither to a palette first", !glyph.ditherFirst)

        RenderMode.entries.forEach {
            assertEquals(it.isGlyph, RenderModules.of(it).producesGlyphs)
            assertEquals(it.ditherFirst, RenderModules.of(it).ditherFirst)
        }
    }

    /**
     * The capabilities used to be negations — `isGlyph` was `this != PurePixel` and
     * `ditherFirst` was `this != GlyphMatrix`. A negation answers for modes that do not exist
     * yet: a fourth would have been glyph art the moment it was declared, and the first sign
     * would have been text exports offered for a render with no characters in it.
     *
     * They are exhaustive now, so the compiler refuses a new module that has not said what it
     * produces. This is the same claim from the outside: every module answers both questions,
     * and no two modules answer both the same way, which is what makes them worth asking.
     */
    @Test
    fun `each render module is told apart by what it can do`() {
        val answers = RenderModules.all.map { it.producesGlyphs to it.ditherFirst }

        assertEquals("two modules are indistinguishable by capability", answers.size, answers.toSet().size)
    }

    /** The dither families are what the picker groups by, so every algorithm needs the right one. */
    @Test
    fun `every dither provider carries its family`() {
        DitherMode.entries.forEach {
            assertEquals(it.category, DitherProviders.of(it).family)
        }
        val counted = org.phioster.glyphsmith.core.dither.DitherCategory.entries
            .sumOf { DitherProviders.inFamily(it).size }
        assertEquals("the families do not partition the algorithms", DitherProviders.all.size, counted)
    }
}
