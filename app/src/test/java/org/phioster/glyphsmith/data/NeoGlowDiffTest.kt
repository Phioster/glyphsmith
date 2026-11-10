package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import java.security.MessageDigest

/** One-off: this PR changes the library only by the neo glow slot, its params and one track. */
class NeoGlowDiffTest {
    private fun strip(p: RenderSettings): RenderSettings = p.copy(
        animation = p.animation.copy(tracks = p.animation.tracks.filter { it.target != AnimTarget.NEO_GLOW_OFFSET }),
        layers = p.layers.map { it.copy(params = strip(it.params)) },
    )

    @Test
    fun `without neo glow the library hashes as before`() {
        val text = PresetSchema.encode(PresetLibrary.builtIns.map { it.copy(params = strip(it.params)) })
            .replace(Regex("\"neoGlow\":\\s*\\{[^}]*\\},(\\s*)"), "")
            .replace(Regex(",\\s*\"effect\\.neo-glow\""), "")
        val hashed = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("8a42557288483dae5b184f31a2e1017cb1d6269120a21d2b063488bda4edd4f2", hashed)
    }
}
