package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimCurve
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.anim.AnimTrack
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.effects.NeoGlowParams
import org.phioster.glyphsmith.render.LightOrbsParams
import java.security.MessageDigest

/** One-off: rebuilds the two presets as they were and strips the new neo glow fields. */
class NeoRainDiffTest {
    @Test
    fun `with the old two presets and without the new fields the library hashes as before`() {
        val old = PresetLibrary.builtIns.map { preset ->
            when (preset.name) {
                "floating lights" -> preset.copy(
                    params = preset.params.copy(
                        lightOrbs = LightOrbsParams(enabled = true, count = 35, size = 25, intensity = 55, random = 50),
                        animation = preset.params.animation.copy(
                            frames = 48,
                            fps = 15,
                            tracks = preset.params.animation.tracks.map {
                                if (it.target == AnimTarget.LIGHT_ORB_DRIFT) {
                                    AnimTrack(AnimTarget.LIGHT_ORB_DRIFT, enabled = true, curve = AnimCurve.SINE, from = -100, to = 100)
                                } else {
                                    it
                                }
                            },
                        ),
                    ),
                )
                "matrix reveal" -> preset.copy(
                    params = preset.params.copy(
                        effects = EffectStack(
                            neoGlow = NeoGlowParams(
                                enabled = true,
                                length = 35,
                                stagger = 90,
                                intensity = 250,
                                rest = 45,
                                lane = 8,
                                color = 0xFF9CFF5A.toInt(),
                            ),
                        ),
                    ),
                )
                else -> preset
            }
        }
        val inner = Regex(",\\s*\"(runs|vary|falloff|head|gamma|mirror)\":\\s*[^,}\\s]+")
        val text = Regex("\"neoGlow\":\\s*\\{[^}]*\\}")
            .replace(PresetSchema.encode(old)) { it.value.replace(inner, "") }
        val hashed = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("c340751c75553f5c672725d694763ffc42c88ded88a15c837aaf717f76b0b964", hashed)
    }
}
