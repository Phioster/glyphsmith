package org.phioster.glyphsmith.core.provider

import org.phioster.glyphsmith.core.serial.UnknownWireIdException
import org.phioster.glyphsmith.core.serial.WireId

/**
 * The kinds of internal module the application is assembled from.
 *
 * [prefix] is the first half of every id in the category, which is what ties a registry to the
 * ids already being written into presets — see [WireId].
 *
 * Palette and export providers are named in ARCHITECTURE.md and are deliberately absent: neither
 * has a stable id yet, palettes are identified in saved presets by a bare `paletteId` string,
 * and minting ids for them is a change to the stored format rather than a registry.
 */
enum class ProviderCategory(val prefix: String) {
    RENDER("render"),
    DITHER("dither"),
    EFFECT("effect"),
}

/**
 * One internal, compiled-in module: a render module, a dither algorithm, an effect pass.
 *
 * Deliberately metadata and nothing else. The enums stay exactly where they are and keep doing
 * the work — a provider says what a thing *is called* and what it *can do*, so that the picker,
 * the tests and eventually the UI can ask a uniform question instead of each switching over a
 * different enum in its own way.
 *
 * [id] is the stable wire id, the same one the serialisers write. It is never derived from the
 * enum name, the class name or [displayName], which is free to be reworded or translated.
 */
interface Provider {
    val id: String
    val displayName: String
    val category: ProviderCategory
}

/**
 * Every provider in one category, indexed by its stable id.
 *
 * The rules are checked when the registry is built rather than by a test that has to remember
 * to look: a duplicate id means one of two providers is unreachable, and a malformed or
 * mis-prefixed one means an id has drifted from the format presets are written in. Failing at
 * construction means failing on the first launch of a broken build, not on the first preset
 * somebody saves with it.
 */
open class Registry<P : Provider>(
    val category: ProviderCategory,
    val all: List<P>,
) {

    private val byId: Map<String, P> = all.associateBy { it.id }

    init {
        require(all.isNotEmpty()) { "the ${category.prefix} registry is empty" }
        require(byId.size == all.size) {
            val duplicates = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
            "two ${category.prefix} providers share an id: $duplicates"
        }
        all.forEach {
            require(WireId.isValid(it.id)) { "${category.prefix} provider has a malformed id: \"${it.id}\"" }
            require(it.id.startsWith("${category.prefix}.")) {
                "a ${category.prefix} provider is registered as \"${it.id}\""
            }
            require(it.category == category) { "${it.id} is a ${it.category} in the $category registry" }
        }
    }

    /** The provider [id] names, or null when this build has never heard of it. */
    fun find(id: String): P? = byId[id]

    /**
     * The provider [id] names.
     *
     * Throws rather than substituting a default, for the same reason the serialisers do: an
     * unknown dither is not "no dither" and an unknown render module is not the default one, and
     * quietly resolving either would hand back something that is not what was asked for.
     */
    fun require(id: String): P =
        byId[id] ?: throw UnknownWireIdException(category.prefix, id)
}
