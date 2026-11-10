package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import java.security.MessageDigest

/** One-off: the library diff of PR #98 is exactly the two new, disabled orb tracks. */
class OrbTracksDiffTest {

    private val new = setOf(AnimTarget.ORB_OFFSET, AnimTarget.ORB_DIRECTION)

    private fun strip(p: RenderSettings): RenderSettings = p.copy(
        animation = p.animation.copy(tracks = p.animation.tracks.filter { it.target !in new }),
        layers = p.layers.map { it.copy(params = strip(it.params)) },
    )

    @Test
    fun `without the two orb tracks the library hashes as before`() {
        val stripped = PresetLibrary.builtIns.map { it.copy(params = strip(it.params)) }
        val hashed = MessageDigest.getInstance("SHA-256")
            .digest(PresetSchema.encode(stripped).toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("8e81701da4a64464cc83c22738100b130128f18e042d97e24e462ac97cf0beff", hashed)
        PresetLibrary.builtIns.forEach { preset ->
            preset.params.animation.tracks.filter { it.target in new }.forEach {
                assertEquals("${preset.name}: a new track is switched on", false, it.enabled)
            }
        }
    }
}
