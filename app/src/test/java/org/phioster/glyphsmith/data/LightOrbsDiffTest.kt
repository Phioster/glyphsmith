package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import java.security.MessageDigest

/** One-off: this PR changes the library only by the lightOrbs block and one disabled track. */
class LightOrbsDiffTest {
    private fun strip(p: RenderSettings): RenderSettings = p.copy(
        animation = p.animation.copy(tracks = p.animation.tracks.filter { it.target != AnimTarget.LIGHT_ORB_DRIFT }),
        layers = p.layers.map { it.copy(params = strip(it.params)) },
    )

    @Test
    fun `without light orbs the library hashes as before`() {
        val text = PresetSchema.encode(PresetLibrary.builtIns.map { it.copy(params = strip(it.params)) })
            .replace(Regex("\"lightOrbs\":\\s*\\{[^}]*\\},(\\s*)"), "")
        val hashed = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("7c12bc879de2c8ad7c1c9dc1bb1cdc3e9b4ab9d8fc70ee61ff2f7796f601b505", hashed)
    }
}
