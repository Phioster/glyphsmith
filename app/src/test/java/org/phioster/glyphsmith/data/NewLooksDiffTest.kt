package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/** One-off: the library diff of this PR is exactly the three new presets. */
class NewLooksDiffTest {
    @Test
    fun `without the three new presets the library hashes as before`() {
        val new = setOf("phosphor relief", "drifting orbs", "tube glyphs")
        val rest = PresetLibrary.builtIns.filter { it.name !in new }
        assertEquals(PresetLibrary.builtIns.size - 3, rest.size)
        val hashed = MessageDigest.getInstance("SHA-256")
            .digest(PresetSchema.encode(rest).toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("503bc03e1f6fe8a12de0830a2da7e8ef703785b5972eecd67a37be814a5a21a3", hashed)
    }
}
