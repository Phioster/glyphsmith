package org.phioster.glyphsmith.data

import android.content.Context
import java.io.File

/**
 * Shape sets the user kept, as one JSON file in app-private storage. Shaped after
 * [PaletteStore], and for its reason: a preset carries the bands themselves, so this is a shelf
 * to take a set off, not something a preset points at.
 */
class ShapeSetStore(context: Context) {

    private val file = File(context.filesDir, "shape-sets.json")

    fun load(): List<ShapeSetFile> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return ShapeSetFile.decodeAll(text)
    }

    fun add(set: ShapeSetFile): List<ShapeSetFile> = ShapeSetFile.keeping(load(), set).also(::save)

    fun remove(name: String): List<ShapeSetFile> =
        load().filterNot { it.name.equals(name, ignoreCase = true) }.also(::save)

    private fun save(sets: List<ShapeSetFile>) {
        runCatching { file.writeText(ShapeSetFile.encodeAll(sets)) }
    }
}
