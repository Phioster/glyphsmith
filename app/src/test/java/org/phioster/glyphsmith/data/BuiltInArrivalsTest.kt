package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

class BuiltInArrivalsTest {

    private fun p(name: String) = Preset(name, RenderSettings())

    @Test
    fun `a library saved before 1_1_0's additions gets them, and only them`() {
        val builtIns = listOf(p("one bit"), p("soft grain"), p("phosphor relief"), p("tube glyphs"))
        val stored = listOf(p("one bit"), p("my look"))           // "soft grain" was deleted
        val r = BuiltInArrivals.merge(stored, null, builtIns)
        assertEquals(listOf("one bit", "my look", "phosphor relief", "tube glyphs"), r.presets.map { it.name })
        assertEquals(2, r.added)
    }

    @Test
    fun `a built-in deleted after it arrived stays deleted`() {
        val builtIns = listOf(p("one bit"), p("phosphor relief"))
        val first = BuiltInArrivals.merge(listOf(p("one bit")), null, builtIns)
        val afterDelete = first.presets.filterNot { it.name == "phosphor relief" }
        val second = BuiltInArrivals.merge(afterDelete, first.offered, builtIns)
        assertEquals(listOf("one bit"), second.presets.map { it.name })
        assertEquals(0, second.added)
    }

    @Test
    fun `a later built-in arrives once, and a same-named user preset is never overwritten`() {
        val offered = setOf("one bit")
        val builtIns = listOf(p("one bit"), p("new thing"), p("Mine"))
        val stored = listOf(p("one bit"), p("mine"))
        val r = BuiltInArrivals.merge(stored, offered, builtIns)
        assertEquals(listOf("one bit", "mine", "new thing"), r.presets.map { it.name })
        assertTrue("new thing" in r.offered && "mine" in r.offered)
    }

    @Test
    fun `the shipped arrivals list names real presets`() {
        val names = PresetLibrary.builtIns.map { it.name }.toSet()
        BuiltInArrivals.SINCE_1_1_0.forEach { assertTrue("$it is not shipped", it in names) }
    }
}
