package org.phioster.glyphsmith.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.color.Palette

/**
 * A palette on its own, as a shareable file.
 *
 * Palettes already travel inside presets, but only as a passenger: there was no way to hand
 * someone a set of colours without handing them a whole look. This is the smallest format
 * that fixes that — a name and a list of hex strings.
 *
 * Hex rather than packed integers on purpose. A palette file is something a person might
 * open, edit, or paste together from somewhere else, and `"#33FF66"` survives that where
 * `-13369498` does not.
 */
@Serializable
data class PaletteFile(
    val name: String,
    val colors: List<String>,
    /**
     * Where it belongs in the picker. Defaulted so a file written before this existed still
     * loads, and free text rather than a fixed set — a category that nearly matches an
     * existing one simply becomes a new one, which is a nuisance worth knowing about but
     * better than refusing a file outright.
     */
    val category: String = "IMPORTED",
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun encode(palette: Palette): String = json.encodeToString(
            PaletteFile(
                name = palette.name,
                colors = palette.colors.map { String.format(Locale.US, "#%06X", it and 0xFFFFFF) },
                category = palette.category.ifEmpty { "IMPORTED" },
            ),
        )

        /**
         * Reads a palette file back. Returns null when the text is not one, so the caller can
         * say so instead of silently loading an empty palette.
         *
         * Colours run through [Palettes.fromColors], which makes them opaque and sorts them
         * darkest-first — the ordering the engine requires. A hand-edited file therefore does
         * not have to be sorted correctly to be usable.
         */
        fun decode(text: String): PaletteFile? {
            val parsed = runCatching { json.decodeFromString<PaletteFile>(text) }.getOrNull()
                ?: return null
            return if (parsed.colors.isEmpty()) null else parsed
        }

        /** Parsed, opaque and ordered — ready to drop straight into `paletteOverride`. */
        fun colorsOf(file: PaletteFile): List<Int> =
            Palettes.fromColors(file.colors.mapNotNull { parseHex(it) })

        /** Several palettes as one document, which is what a pack is and what the store keeps. */
        fun encodeAll(palettes: List<PaletteFile>): String = json.encodeToString(palettes)

        /**
         * Reads either shape: one palette, or a list of them.
         *
         * A pack and a single palette are the same thing to whoever is handing one over — both
         * arrive as a `.json` and both should just open. Asking the caller to know which it has
         * would push the question out to every call site, and the file itself already answers
         * it: an array starts with `[`.
         *
         * Empty palettes are dropped rather than refused, so a pack with one bad entry still
         * yields the rest. A document with nothing usable in it comes back empty, and the
         * caller says so.
         */
        fun decodeAll(text: String): List<PaletteFile> {
            val single = decode(text)
            if (single != null) return listOf(single)
            val many = runCatching { json.decodeFromString<List<PaletteFile>>(text) }.getOrNull()
                ?: return emptyList()
            return many.filter { it.colors.isNotEmpty() }
        }

        private fun parseHex(value: String): Int? {
            val digits = value.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            // Six digits or eight; an alpha given in the file is dropped, since a palette
            // stop that is partly transparent has no meaning to the renderer.
            val rgb = when (digits.length) {
                6 -> digits
                8 -> digits.substring(2)
                else -> return null
            }
            return runCatching { rgb.toLong(16).toInt() or (0xFF shl 24) }.getOrNull()
        }
    }
}
