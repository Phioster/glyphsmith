package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.render.RenderSettings
import java.security.MessageDigest

/** One-off: this PR changes the library only by the temporal angle field and its disabled track. */
class TemporalAngleDiffTest {
    private fun strip(p: RenderSettings): RenderSettings = p.copy(
        animation = p.animation.copy(tracks = p.animation.tracks.filter { it.target != AnimTarget.TEMPORAL_ANGLE }),
        layers = p.layers.map { it.copy(params = strip(it.params)) },
    )

    @Test
    fun `without the angle the library hashes as before`() {
        val text = PresetSchema.encode(PresetLibrary.builtIns.map { it.copy(params = strip(it.params)) })
            .replace(Regex("\"angle\":\\s*45,(\\s*)(?=\"time\")"), "")
        val hashed = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("61719895be8d1ade6cef3a6e92ee0c8dd4cf8c224c18676de8fcc93e6af3941a", hashed)
    }
}
