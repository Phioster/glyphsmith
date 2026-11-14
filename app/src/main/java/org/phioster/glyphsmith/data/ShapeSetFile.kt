package org.phioster.glyphsmith.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import org.phioster.glyphsmith.render.ShapeBand
import org.phioster.glyphsmith.render.ShapeBands
import org.phioster.glyphsmith.render.ShapeKind

/**
 * A shape shifter's bands on their own, as a shareable file: which shape stands for which
 * tones, how large, how wide, and in what colour. Shapes travel by name and colours as hex, so
 * the file can be read and edited by hand.
 */
@Serializable
data class ShapeSetFile(
    val name: String = "shape set",
    val bands: List<Band> = emptyList(),
) {
    @Serializable
    data class Band(
        val shape: String,
        val size: Int = 100,
        val width: Int = 50,
        /** Empty for a band that takes its colour from the picture or the palette. */
        val color: String = "",
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun of(name: String, bands: List<ShapeBand>) = ShapeSetFile(
            name = name,
            bands = bands.map { band ->
                Band(
                    shape = band.shape.name.lowercase(Locale.US),
                    size = band.size,
                    width = band.width,
                    color = if (band.color == -1) "" else String.format(Locale.US, "#%06X", band.color and 0xFFFFFF),
                )
            },
        )

        fun encode(name: String, bands: List<ShapeBand>): String = json.encodeToString(of(name, bands))

        /** The shelf of kept sets as one document, and back. Text that is not one reads as empty. */
        fun encodeAll(sets: List<ShapeSetFile>): String = json.encodeToString(sets)

        fun decodeAll(text: String): List<ShapeSetFile> =
            runCatching { json.decodeFromString<List<ShapeSetFile>>(text) }.getOrDefault(emptyList())

        /** One file as it was written, or null when the text is not a shape set with bands. */
        fun read(text: String): ShapeSetFile? =
            runCatching { json.decodeFromString<ShapeSetFile>(text) }.getOrNull()?.takeIf { bandsOf(it) != null }

        /** [kept] with [set] on it; a set of the same name is replaced, and the new one goes last. */
        fun keeping(kept: List<ShapeSetFile>, set: ShapeSetFile): List<ShapeSetFile> =
            kept.filterNot { it.name.equals(set.name, ignoreCase = true) } + set

        /** The first of "my set 1", "my set 2"… that is not on [kept] yet. */
        fun freeName(kept: List<ShapeSetFile>): String =
            generateSequence(1) { it + 1 }.map { "my set $it" }.first { name -> kept.none { it.name.equals(name, ignoreCase = true) } }

        /**
         * Reads a shape set back, or null when the text is not one. A band naming a shape this
         * version does not know is dropped rather than failing the file; values are brought
         * into range and the list is cut at the most bands the editor holds.
         */
        fun decode(text: String): List<ShapeBand>? {
            val parsed = runCatching { json.decodeFromString<ShapeSetFile>(text) }.getOrNull() ?: return null
            return bandsOf(parsed)
        }

        /** The bands [parsed] describes, by the rules of [decode]. */
        fun bandsOf(parsed: ShapeSetFile): List<ShapeBand>? {
            val bands = parsed.bands.mapNotNull { band ->
                val kind = ShapeKind.entries.firstOrNull { it.name.equals(band.shape.trim(), ignoreCase = true) }
                    ?: return@mapNotNull null
                ShapeBand(kind, band.size.coerceIn(0, 200), band.width.coerceIn(1, 100), parseHex(band.color))
            }
            return bands.take(ShapeBands.MAX_BANDS).ifEmpty { null }
        }

        private fun parseHex(text: String): Int {
            val digits = text.trim().removePrefix("#")
            if (digits.length != 6) return -1
            val rgb = digits.toIntOrNull(16) ?: return -1
            return 0xFF000000.toInt() or rgb
        }
    }
}
