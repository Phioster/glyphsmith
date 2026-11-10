package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/** One-off: this PR changes the library only by the new grainKind field at its default. */
class GrainKindDiffTest {
    @Test
    fun `without grainKind the library hashes as before`() {
        val text = PresetSchema.encode(PresetLibrary.builtIns)
            .replace(Regex("\"grainKind\":\\s*\"UNIFORM\",(\\s*)"), "")
        val hashed = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("7fb38e871f641150dcc18fe133c33239da7ea43fd643de71ebf1ad52be421c24", hashed)
    }
}
