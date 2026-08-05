package org.phioster.glyphsmith.effects

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * The effect category as a plugin catalogue.
 *
 * An effect is meant to be addable by writing one file and naming it twice — once in
 * [EffectPasses] to say what runs, once in `ui/panels/EffectControls` to say what it looks like.
 * Everything else is supposed to follow on its own: the id, the label, the chain, the toggle, the
 * saved preset, Surprise Me.
 *
 * "Follows on its own" is a claim about lists, and a claim about lists is only true while
 * something checks that they all still hold every effect. That is what this file is. The
 * individual passes are tested by their own files; this one tests the *registration*, and it is
 * written so that adding an effect to the enum and forgetting a step fails here rather than in a
 * build somebody ships.
 */
class EffectCatalogTest {

    private val roll get() = EffectRoll(Random(20260805), Palettes.all.first())

    /** One provider per slot, and no slot with two of them. */
    @Test
    fun `every effect has exactly one provider`() {
        assertEquals(EffectId.entries.size, EffectProviders.all.size)

        EffectId.entries.forEach { effect ->
            val matching = EffectProviders.all.filter { it.effect == effect }
            assertEquals("${effect.name} is not registered exactly once", 1, matching.size)
            assertSame("${effect.name} resolves to a different provider", matching.single(), EffectProviders.of(effect))
        }
    }

    /**
     * One pass per provider, and no two providers sharing one.
     *
     * Sharing is the failure worth naming: two slots pointing at the same pass compiles, renders
     * something, and makes one toggle switch on the other effect.
     */
    @Test
    fun `every provider carries exactly one pass, and no two share it`() {
        val passes = EffectProviders.all.map { it.pass }

        assertEquals(EffectId.entries.size, passes.size)
        assertEquals("two providers share a pass", passes.size, passes.distinct().size)
    }

    /** The registry is in the chain's default order, which is also the enum's. */
    @Test
    fun `the registry is the enum, once each, in order`() {
        assertEquals(EffectId.entries.toList(), EffectProviders.all.map { it.effect })
    }

    /**
     * Every list an effect has to appear in, checked against the enum rather than against a
     * hand-written expectation.
     *
     * This is the assertion that makes "a new effect appears everywhere" a fact instead of a
     * hope. A slot added to [EffectId] and left out of any of these fails here.
     */
    @Test
    fun `a registered effect appears in every list that has to hold it`() {
        val stack = EffectStack()
        val slots = EffectId.entries.toSet()

        assertEquals("the provider registry", slots, EffectProviders.all.map { it.effect }.toSet())
        assertEquals("the wire-id table", slots, EffectIds.ids.keys)
        assertEquals("the default chain order", slots, stack.effectiveOrder().toSet())
        assertEquals("the built node list", slots.map { it.name }.toSet(), EffectNodes.of(stack).map { it.id }.toSet())
        assertEquals("the pass table", slots, slots.filter { EffectPasses.of(it) === EffectProviders.of(it).pass }.toSet())
    }

    /**
     * The chain's order survives an effect being added at the end.
     *
     * A preset written before an effect existed carries an order that does not name it, and the
     * effect still has to run — that is what `effectiveOrder` is for, and it is the mechanism a
     * new effect relies on to appear in every preset ever saved.
     */
    @Test
    fun `an order written before an effect existed still runs it`() {
        val old = EffectStack(order = listOf(EffectId.GLOW, EffectId.TINT))

        assertEquals(EffectId.entries.size, old.effectiveOrder().size)
        assertEquals(listOf(EffectId.GLOW, EffectId.TINT), old.effectiveOrder().take(2))
        assertEquals(EffectId.entries.toSet(), old.effectiveOrder().toSet())
    }

    /**
     * Ids and labels are stated on the constant, so neither can be missing.
     *
     * The format and uniqueness rules belong to `WireIdTest`; what is checked here is that the
     * serialiser is reading the constant rather than keeping a second table beside it, because a
     * second table is exactly what this change removed.
     */
    @Test
    fun `a slot states its own id and label`() {
        EffectId.entries.forEach { effect ->
            assertEquals(effect.wireId, EffectIds.idOf(effect))
            assertEquals(effect.wireId, EffectProviders.of(effect).id)
            assertEquals(effect.label, EffectProviders.of(effect).displayName)
            assertTrue("${effect.name} has no label", effect.label.isNotBlank())
        }
    }

    /**
     * A duplicate registration is refused at construction, not discovered later.
     *
     * The registry rule exists so that a build with two providers under one id fails on its first
     * launch rather than on the first preset somebody saves with it. Stated here as a fact about
     * *effect* providers, because that is the category being opened up to new entries.
     */
    @Test
    fun `two providers for one effect cannot be registered`() {
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            Registry(
                ProviderCategory.EFFECT,
                listOf(EffectProvider(EffectId.GLOW), EffectProvider(EffectId.GLOW)),
            )
        }

        assertTrue(duplicate.message.orEmpty().contains("effect.glow"))
    }

    /**
     * A roll switches its own effect on and touches nothing else.
     *
     * Both halves matter. The first is the contract Surprise Me depends on — a roll that left the
     * effect disabled would spend one of the two picks on nothing. The second is what proves an
     * effect's read and write lenses address the same field: a pass that read `tint` and wrote
     * `glow` would leave two effects on, or the wrong one.
     */
    @Test
    fun `a rolled effect is switched on, and only that effect`() {
        EffectProviders.randomisable.forEach { provider ->
            val rolled = provider.pass.rolled(EffectStack(), roll)

            requireNotNull(rolled) { "${provider.effect.name} declares a roll but returned nothing" }
            assertTrue("${provider.effect.name} rolled itself off", rolled.enabledOf(provider.effect))
            assertEquals("${provider.effect.name} rolled another effect too", 1, rolled.activeCount)
        }
    }

    /** A pass that declares no roll says so, and is left out rather than rolled as a no-op. */
    @Test
    fun `a pass without a roll is not offered to one`() {
        EffectProviders.all.forEach { provider ->
            assertEquals(
                "${provider.effect.name} disagrees with the randomisable list",
                provider.pass.isRandomisable,
                provider in EffectProviders.randomisable,
            )
            if (!provider.pass.isRandomisable) {
                assertEquals(null, provider.pass.rolled(EffectStack(), roll))
            }
        }
    }

    /**
     * Every effect this build ships is reachable by Surprise Me.
     *
     * Not a rule about the mechanism — an effect is allowed to declare no roll — but a statement
     * about *this* catalogue, so that dropping a roll becomes a deliberate edit to this line
     * rather than an effect that silently stops being offered.
     */
    @Test
    fun `every shipped effect offers a random roll`() {
        assertEquals(EffectId.entries.size, EffectProviders.randomisable.size)
    }

    /** Rolls are values: the same seed gives the same settings, a different one does not. */
    @Test
    fun `a roll is reproducible from its seed`() {
        val provider = EffectProviders.of(EffectId.SLICE)
        val palette = Palettes.all.first()

        assertEquals(
            provider.pass.rolled(EffectStack(), EffectRoll(Random(7), palette)),
            provider.pass.rolled(EffectStack(), EffectRoll(Random(7), palette)),
        )
        assertNotEquals(
            provider.pass.rolled(EffectStack(), EffectRoll(Random(7), palette)),
            provider.pass.rolled(EffectStack(), EffectRoll(Random(8), palette)),
        )
    }

    /** Rolling one effect on top of another keeps both, which is how two picks land. */
    @Test
    fun `rolls stack rather than replace`() {
        val first = EffectProviders.of(EffectId.GLOW).pass.rolled(EffectStack(), roll)!!
        val second = EffectProviders.of(EffectId.TINT).pass.rolled(first, roll)!!

        assertTrue(second.enabledOf(EffectId.GLOW))
        assertTrue(second.enabledOf(EffectId.TINT))
        assertEquals(2, second.activeCount)
        assertFalse(second.enabledOf(EffectId.WARP))
    }
}
