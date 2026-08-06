package org.phioster.glyphsmith.data

import android.content.Context
import java.io.File

/**
 * Imported palettes as one JSON file in app-private storage.
 *
 * Until this existed, importing a palette applied its colours and forgot them: they went into
 * `paletteOverride`, which lives inside whichever preset the user went on to save. A set of
 * colours could be used and could be shared, but it could not be *kept*, so the second time you
 * wanted it you went looking for the file again.
 *
 * **Imported palettes still get no stable identity, and that is the decision rather than an
 * omission.** A provider id is a promise the build makes about something compiled into it; a
 * palette that arrived this morning cannot make that promise, and a preset naming one would be a
 * preset that breaks on any device where the file was never opened. So presets keep carrying the
 * colours themselves, exactly as they do today — the same answer `importScreen` gives for an
 * imported dither screen, and for the same reason. What this adds is a shelf to take one off,
 * not a new kind of thing for a preset to point at.
 *
 * Shaped after [PresetStore], down to the read-modify-write: the file is small enough that
 * anything cleverer would be cost without benefit, and two stores that behave the same way are
 * two fewer behaviours to remember.
 */
class PaletteStore(context: Context) {

    private val file = File(context.filesDir, "palettes.json")

    /** Everything kept, oldest first. Empty when nothing has been imported, which is the norm. */
    fun load(): List<PaletteFile> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return PaletteFile.decodeAll(text)
    }

    fun save(palettes: List<PaletteFile>) {
        runCatching { file.writeText(PaletteFile.encodeAll(palettes)) }
    }

    /**
     * Keeps [palettes], replacing any already held under the same name.
     *
     * Matched case-insensitively and by name alone, like a preset: importing a corrected version
     * of a file should update what is on the shelf rather than leave two entries a letter apart.
     * The replacement goes at the end, so the most recently touched palette is the last one — a
     * list that reorders itself on every save would be a list nobody can find anything in twice,
     * but a name that comes back is a deliberate act and belongs where new things go.
     */
    fun add(palettes: List<PaletteFile>): List<PaletteFile> {
        val incoming = palettes.filter { it.colors.isNotEmpty() }
        if (incoming.isEmpty()) return load()

        // Within one pack the last entry of a name wins, so a file that repeats a name does not
        // put two of it on the shelf.
        val deduped = incoming.associateBy { it.name.lowercase() }.values.toList()
        val taken = deduped.map { it.name.lowercase() }.toSet()
        val kept = load().filterNot { it.name.lowercase() in taken } + deduped
        save(kept)
        return kept
    }

    /** Forgets one by name. Unknown names are not an error — the shelf ends up as asked. */
    fun remove(name: String): List<PaletteFile> {
        val kept = load().filterNot { it.name.equals(name, ignoreCase = true) }
        save(kept)
        return kept
    }
}
