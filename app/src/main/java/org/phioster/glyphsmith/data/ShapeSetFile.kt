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

        fun encode(name: String, bands: List<ShapeBand>): String = json.encodeToString(
            ShapeSetFile(
                name = name,
                bands = bands.map { band ->
                    Band(
                        shape = band.shape.name.lowercase(Locale.US),
                        size = band.size,
                        width = band.width,
                        color = if (band.color == -1) "" else String.format(Locale.US, "#%06X", band.color and 0xFFFFFF),
                    )
                },
            ),
        )

        /**
         * Reads a shape set back, or null when the text is not one. A band naming a shape this
         * version does not know is dropped rather than failing the file; values are brought
         * into range and the list is cut at the most bands the editor holds.
         */
        fun decode(text: String): List<ShapeBand>? {
            val parsed = runCatching { json.decodeFromString<ShapeSetFile>(text) }.getOrNull() ?: return null
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
