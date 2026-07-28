package org.phioster.glyphsmith.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.ColorMode
import java.io.File

@Serializable
data class Preset(val name: String, val params: AsciiParams)

/**
 * Presets as one JSON file in app-private storage. Small enough that read-modify-write on
 * every change is cheaper than any incremental scheme, and it survives a schema change
 * because unknown keys are ignored and missing ones fall back to the defaults.
 */
class PresetStore(context: Context) {

    private val file = File(context.filesDir, "presets.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(): List<Preset> {
        if (!file.exists()) return builtIns
        return runCatching { json.decodeFromString<List<Preset>>(file.readText()) }
            .getOrElse { builtIns }
    }

    fun save(presets: List<Preset>) {
        runCatching { file.writeText(json.encodeToString(presets)) }
    }

    fun upsert(name: String, params: AsciiParams): List<Preset> {
        val trimmed = name.trim().ifEmpty { "untitled" }
        val updated = load().filterNot { it.name.equals(trimmed, ignoreCase = true) } + Preset(trimmed, params)
        save(updated)
        return updated
    }

    fun delete(name: String): List<Preset> {
        val updated = load().filterNot { it.name == name }
        save(updated)
        return updated
    }

    companion object {
        /** Shipped starting points — each one exercises a different corner of the engine. */
        val builtIns: List<Preset> = listOf(
            Preset(
                "terminal",
                AsciiParams(charSetId = "ascii-standard-10", cellSize = 6, contrast = 1.2f),
            ),
            Preset(
                "phosphor",
                AsciiParams(
                    charSetId = "ascii-standard-70",
                    cellSize = 5,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    contrast = 1.3f,
                ),
            ),
            Preset(
                "blocks",
                AsciiParams(charSetId = "block-shade", cellSize = 6, colorMode = ColorMode.SOURCE),
            ),
            Preset(
                "braille",
                AsciiParams(charSetId = "braille-ramp", cellSize = 4, contrast = 1.4f, gamma = 1.2f),
            ),
            Preset(
                "matrix",
                AsciiParams(
                    charSetId = "lang-katakana",
                    cellSize = 8,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "phosphor",
                    contrast = 1.5f,
                ),
            ),
            Preset(
                "gameboy",
                AsciiParams(
                    charSetId = "geo-squares",
                    cellSize = 7,
                    colorMode = ColorMode.PALETTE,
                    paletteId = "gameboy",
                    backgroundColor = 0xFF0F380F.toInt(),
                ),
            ),
            Preset(
                "glitch",
                AsciiParams(charSetId = "block-quadrant", cellSize = 5, offset = 4, contrast = 1.6f),
            ),
        )
    }
}
