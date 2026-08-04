package org.phioster.glyphsmith.data

import android.content.Context
import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.render.RenderSettings
import java.io.File
import org.phioster.glyphsmith.core.dither.DitherCategory
import org.phioster.glyphsmith.core.dither.DitherMode

@Serializable
data class Preset(
    val name: String,
    val params: RenderSettings,
    /** Grouping in the picker. Defaulted so presets saved before this still load. */
    val category: String = PresetStore.CATEGORY_CUSTOM,
    val favourite: Boolean = false,
    /** Free text the author leaves for whoever loads it — why this look, or where from. */
    val description: String = "",
)

/**
 * Presets as one JSON file in app-private storage. Small enough that read-modify-write on
 * every change is cheaper than any incremental scheme.
 *
 * The format itself — its version, what older versions looked like, and how they are carried
 * forward — is [PresetSchema]. What ships in the box is [PresetLibrary]. This holds the file
 * and the operations on the library; it does not know how a preset is spelled, nor what any
 * particular one contains.
 */
class PresetStore(context: Context) {

    private val file = File(context.filesDir, "presets.json")

    fun load(): List<Preset> {
        if (!file.exists()) return PresetLibrary.builtIns
        val text = runCatching { file.readText() }.getOrNull() ?: return PresetLibrary.builtIns
        return PresetSchema.decode(text).ifEmpty { PresetLibrary.builtIns }
    }

    fun save(presets: List<Preset>) {
        runCatching { file.writeText(PresetSchema.encode(presets)) }
    }

    fun exportJson(): String = PresetSchema.encode(load())

    /**
     * Merges an exported file into the stored presets, matched by name — importing an
     * edited export updates those presets instead of producing duplicates.
     *
     * Returns null only when the text is not a preset document at all. A document whose
     * entries this build cannot read is *not* that case, and used to be treated as one: it
     * came back empty and was reported as "not a preset export", which is both wrong and
     * unhelpful to whoever exported it from a newer build. It now returns an [Import] that
     * imported nothing and says which entries it had to leave behind.
     *
     * Only the entries that read are merged, so an unreadable one is never written into the
     * stored library — it stays a problem with the file that was offered, not with the file
     * that is kept.
     */
    fun importJson(text: String): Import? {
        val reading = PresetSchema.read(text)
        if (reading.presets.isEmpty() && reading.skipped.isEmpty()) return null

        val names = reading.presets.map { it.name.lowercase() }.toSet()
        val merged = load().filterNot { it.name.lowercase() in names } + reading.presets
        if (reading.presets.isNotEmpty()) save(merged)
        return Import(merged, reading.presets.size, reading.skipped)
    }

    fun upsert(name: String, params: RenderSettings, description: String = ""): List<Preset> {
        val trimmed = name.trim().ifEmpty { "untitled" }
        val existing = load().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val updated = load().filterNot { it.name.equals(trimmed, ignoreCase = true) } +
            Preset(
                name = trimmed,
                params = params,
                // Overwriting a preset keeps where it sits and whether it was starred;
                // re-saving a favourite should not quietly demote it.
                // A category the user already chose wins; otherwise it is derived, because
                // everything landing in one CUSTOM heap is what made the picker unusable
                // once there were more than a handful.
                category = existing?.category ?: familyOf(params),
                favourite = existing?.favourite ?: false,
                description = description.ifEmpty { existing?.description ?: "" },
            )
        save(updated)
        return updated
    }

    /** Renames in place and keeps the preset's position, star and settings. */
    fun rename(from: String, to: String, description: String): List<Preset> {
        val trimmed = to.trim().ifEmpty { from }
        val updated = load().map {
            if (it.name == from) it.copy(name = trimmed, description = description) else it
        }
        save(updated)
        return updated
    }

    /** Flips the star on [name]. Favourites sort to the top of the picker. */
    fun toggleFavourite(name: String): List<Preset> {
        val updated = load().map {
            if (it.name == name) it.copy(favourite = !it.favourite) else it
        }
        save(updated)
        return updated
    }

    fun delete(name: String): List<Preset> {
        val updated = load().filterNot { it.name == name }
        save(updated)
        return updated
    }

    /**
     * Throws away the stored file so the shipped library comes back.
     *
     * Deleting rather than overwriting: [load] already falls back to [PresetLibrary.builtIns]
     * when there is no file, so there is only one place that decides what "fresh" means. This
     * also picks up any presets added by a later version, which writing today's list would not.
     */
    fun reset(): List<Preset> {
        runCatching { file.delete() }
        return PresetLibrary.builtIns
    }

    companion object {
        /**
         * The shelves of the curated library, named after what the algorithm *is* rather than
         * after how loud it looks — a picker sorted by mechanism is one you can predict.
         */
        const val CATEGORY_CLASSIC = "CLASSIC"
        const val CATEGORY_DIFFUSION = "DIFFUSION"
        const val CATEGORY_ORDERED = "ORDERED"
        const val CATEGORY_PATTERN = "PATTERN"
        const val CATEGORY_PRINT = "PRINT"
        const val CATEGORY_GEOMETRY = "GEOMETRY"
        const val CATEGORY_COLOR = "COLOR"
        const val CATEGORY_GLITCH = "GLITCH"
        const val CATEGORY_MOTION = "MOTION"
        const val CATEGORY_LAYERED = "LAYERED"
        const val CATEGORY_GLYPH = "GLYPH"

        /** The bench, kept off the curated shelves — see the laboratory in [PresetLibrary]. */
        const val CATEGORY_LAB = "LAB"

        const val CATEGORY_CUSTOM = "CUSTOM"

        /**
         * Shelves nothing ships in any more.
         *
         * They stay declared, and stay in [categories], because a category is a stored string:
         * presets people saved before this round carry these words, and a category the picker
         * does not know sorts to the bottom under a heading of its own. Keeping them listed
         * keeps those presets where their owner filed them — and keeps them named, since
         * [label] has to have something to draw over the shelf.
         *
         * Nothing new is filed onto any of them. DITHER used to take every threshold-based
         * save; those now go to the shelf their family is actually called after.
         */
        const val CATEGORY_DITHER = "DITHER"
        const val CATEGORY_HEAVY = "HEAVY"
        const val CATEGORY_SIGNATURE = "SIGNATURE"
        const val CATEGORY_INK = "INK"

        /** Picker order. Anything the user saves lands in CUSTOM, which sits last. */
        val categories = listOf(
            CATEGORY_CLASSIC,
            CATEGORY_DIFFUSION,
            CATEGORY_ORDERED,
            CATEGORY_PATTERN,
            CATEGORY_PRINT,
            CATEGORY_GEOMETRY,
            CATEGORY_COLOR,
            CATEGORY_GLITCH,
            CATEGORY_MOTION,
            CATEGORY_LAYERED,
            CATEGORY_GLYPH,
            CATEGORY_LAB,
            CATEGORY_DITHER,
            CATEGORY_INK,
            CATEGORY_SIGNATURE,
            CATEGORY_HEAVY,
            CATEGORY_CUSTOM,
        )

        /** The shelves the curated library actually ships on — [categories] minus the bench,
         * the historical ones and the drawer the user's own saves land in. */
        val curatedCategories = listOf(
            CATEGORY_CLASSIC,
            CATEGORY_DIFFUSION,
            CATEGORY_ORDERED,
            CATEGORY_PATTERN,
            CATEGORY_PRINT,
            CATEGORY_GEOMETRY,
            CATEGORY_COLOR,
            CATEGORY_GLITCH,
            CATEGORY_MOTION,
            CATEGORY_LAYERED,
            CATEGORY_GLYPH,
        )

        /**
         * Which shelf a newly saved preset lands on, read off the settings themselves.
         *
         * Derived rather than typed, because the settings are a fact about the preset while a
         * typed category is a second thing to keep consistent. Only ever consulted for a save
         * that has no category yet: [upsert] hands back an existing preset's own category
         * untouched, so nothing already filed is moved by a change in here.
         *
         * The order of the questions is the point. A preset is first *what it renders as* —
         * glyph art is a shelf of its own however it was dithered — then *how it is composed*,
         * layers and motion being structural facts that outrank the algorithm, and only then
         * the dither family.
         *
         * Three curated shelves are deliberately unreachable from here. CLASSIC is a
         * judgement about a look rather than a property of the settings, PRINT has no dither
         * family behind it, and COLOR would swallow nearly everything, since most presets name
         * a palette. Those shelves are curated by hand; nothing is filed onto them by guess.
         */
        fun familyOf(params: RenderSettings): String = when {
            // Whatever produced the levels, a render that ends in characters is glyph art, and
            // that is the shelf someone will look on for it.
            params.renderMode.isGlyph -> CATEGORY_GLYPH

            // Stacked renders and animation are what the preset *is*, and both are visible in
            // the picker in a way the kernel is not. Layers first: an animated stack is still
            // most usefully found as a stack.
            params.layers.isNotEmpty() -> CATEGORY_LAYERED
            params.animation.enabled || params.temporal.enabled -> CATEGORY_MOTION

            else -> familyOf(params.ditherMode)
        }

        /**
         * The shelf a dither family belongs on.
         *
         * Split out so the taxonomy is one readable table rather than a tail on [familyOf],
         * and exhaustive over [DitherCategory] on purpose: adding a family should not compile
         * until someone has said where its presets go.
         */
        private fun familyOf(mode: DitherMode): String = when (mode.category) {
            DitherCategory.ERROR_DIFFUSION -> CATEGORY_DIFFUSION
            DitherCategory.ORDERED -> CATEGORY_ORDERED

            // Patterned tiles an area and special modulates a threshold, and both arrive as a
            // repeating figure over the picture. The library shelves them together as PATTERN
            // for the same reason.
            DitherCategory.PATTERNED,
            DitherCategory.SPECIAL,
            -> CATEGORY_PATTERN

            DitherCategory.POLYGON -> CATEGORY_GEOMETRY
            DitherCategory.GLITCH -> CATEGORY_GLITCH

            // BASIC is [DitherMode.NONE] alone — no dithering at all. There is no dither family
            // to file it under, so it falls to the drawer a save lands in when nothing else
            // describes it.
            DitherCategory.BASIC -> CATEGORY_CUSTOM
        }

        /**
         * What a category is called on screen.
         *
         * The stored string and the visible one are deliberately two different things. A
         * category travels in the file and in every export, so it must never be retitled;
         * the words over the shelf are a product decision that can be reworded or translated
         * whenever, and used to be neither — the picker drew the stored token, and headings
         * read DIFFUSION and LAYERED at people.
         *
         * Unlisted categories are the ones only a stored preset carries: someone's own save
         * from a version that shelved things differently, or a file from a later one. Those
         * get their token tidied rather than shown raw, so an unknown shelf still reads as
         * words instead of as an identifier.
         */
        fun label(category: String): String = labels[category] ?: titleCase(category)

        private val labels = mapOf(
            CATEGORY_CLASSIC to "Classic Dither",
            CATEGORY_DIFFUSION to "Error Diffusion",
            CATEGORY_ORDERED to "Ordered Dither",
            CATEGORY_PATTERN to "Pattern",
            CATEGORY_PRINT to "Print",
            CATEGORY_GEOMETRY to "Geometry",
            CATEGORY_COLOR to "Color",
            CATEGORY_GLITCH to "Glitch",
            CATEGORY_MOTION to "Motion",
            CATEGORY_LAYERED to "Layered",
            CATEGORY_GLYPH to "Glyph Art",
            CATEGORY_LAB to "Algorithm Lab",
            CATEGORY_CUSTOM to "Custom",
            // Retired shelves. Listed rather than left to the fallback because they are still
            // real headings for anyone whose library predates this round.
            CATEGORY_DITHER to "Dither",
            CATEGORY_INK to "Ink",
            CATEGORY_SIGNATURE to "Signature",
            CATEGORY_HEAVY to "Heavy",
        )

        private fun titleCase(category: String): String = category
            .split('_', ' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
