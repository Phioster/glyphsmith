package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.phioster.glyphsmith.core.dither.DitherModeIds
import org.phioster.glyphsmith.core.serial.WireId
import org.phioster.glyphsmith.effects.EffectIds
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModeIds

/**
 * The preset file format: what gets written, and what can still be read.
 *
 * Lives apart from [PresetStore] because the store needs a `Context` and this does not — the
 * format is the part worth testing, and it is testable only if reading it does not require a
 * device.
 *
 * ## Versions
 *
 * - **1** — a bare JSON array of presets, with no version anywhere. Everything written before
 *   this object existed. Predates [RenderMode], so an entry that does not name one is a glyph
 *   preset: it was written when that was the only thing a preset could be.
 * - **2** — `{"schemaVersion": 2, "presets": [ ... ]}`. Same entries, wrapped so the version
 *   has somewhere to live.
 * - **3** — render modes, dither styles and effects are named by stable ids
 *   (`render.pixel-dither`, `dither.floyd-steinberg`, `effect.glow`) instead of by Kotlin enum
 *   constants. See [WireId]. Nothing else about an entry changed, and no preset changes
 *   appearance: the same things are being named, in a spelling that no longer moves when the
 *   source does.
 *
 * ## Adding a version
 *
 * 1. Raise [CURRENT_VERSION].
 * 2. Add a [Migration] with `from` set to the version it upgrades *out of*, and register it in
 *    [migrations] in ascending order. It rewrites one entry's JSON before anything tries to
 *    turn it into a [Preset], which is what lets it reach fields that no longer exist on the
 *    class.
 * 3. Add a test that decodes a literal document of the old version.
 *
 * Migrations run per entry and in order, so a version 1 file is carried through every step in
 * turn rather than needing one migration per version pair.
 *
 * A file claiming a *newer* version than this build knows is still read entry by entry: unknown
 * keys are ignored, so the readable presets in it survive. That is deliberately the same
 * treatment a damaged file gets, because the alternative — refusing the file — costs the user
 * their whole library for one field this build has never heard of.
 */
object PresetSchema {

    const val CURRENT_VERSION = 3

    /** The bare array, from before anything wrote a version. */
    private const val VERSION_LEGACY = 1

    private const val KEY_VERSION = "schemaVersion"
    private const val KEY_PRESETS = "presets"
    private const val KEY_PARAMS = "params"
    private const val KEY_NAME = "name"
    private const val KEY_RENDER_MODE = "renderMode"
    private const val KEY_DITHER_MODE = "ditherMode"
    private const val KEY_EFFECTS = "effects"
    private const val KEY_ORDER = "order"
    private const val KEY_LAYERS = "layers"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(presets: List<Preset>): String {
        val document = buildJsonObject {
            put(KEY_VERSION, CURRENT_VERSION)
            put(KEY_PRESETS, json.encodeToJsonElement(presets))
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    /**
     * Reads a preset document of any known version.
     *
     * Entry by entry, and dropping whatever will not read: an id this build does not know throws
     * no matter what `ignoreUnknownKeys` says, because there is no honest way to guess what it
     * meant. Decoding the array as one value would therefore mean a single preset written by a
     * newer build silently discarding the entire library. Per entry, that costs exactly the
     * preset that cannot be read.
     */
    fun decode(text: String): List<Preset> = read(text).presets

    /**
     * [decode] plus what it had to drop and why.
     *
     * The reasons are worth carrying rather than swallowing: an entry naming
     * `dither.something-newer` is not corrupt, it is from a build that knows more styles than
     * this one, and that is a different message to whoever is looking at a preset that has gone
     * missing. Nothing shows them yet — that is a UI change and belongs to its own task — so
     * for now this is what a caller can ask for.
     */
    fun read(text: String): Reading {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return Reading()
        val entries = entriesOf(root) ?: return Reading()
        val steps = migrations.filter { it.from >= versionOf(root) }
        val presets = mutableListOf<Preset>()
        val skipped = mutableListOf<Skipped>()
        entries.forEach { entry ->
            val migrated = steps.fold(entry) { carried, step -> step.apply(carried) }
            runCatching { json.decodeFromJsonElement<Preset>(migrated) }.fold(
                onSuccess = { presets += it },
                onFailure = { skipped += Skipped(nameOf(entry), it.message ?: "unreadable") },
            )
        }
        return Reading(presets, skipped)
    }

    /** What a document yielded: the presets that read, and the entries that did not. */
    data class Reading(
        val presets: List<Preset> = emptyList(),
        val skipped: List<Skipped> = emptyList(),
    )

    /** One entry that could not be read, and the reason it could not. */
    data class Skipped(val name: String, val reason: String)

    private fun nameOf(entry: JsonElement): String =
        ((entry as? JsonObject)?.get(KEY_NAME) as? JsonPrimitive)?.contentOrNull ?: "untitled"

    /** A bare array is version 1; anything else has to say, and is treated as 1 if it doesn't. */
    private fun versionOf(root: JsonElement): Int = when (root) {
        is JsonArray -> VERSION_LEGACY
        is JsonObject -> (root[KEY_VERSION] as? JsonPrimitive)?.intOrNull ?: VERSION_LEGACY
        else -> VERSION_LEGACY
    }

    private fun entriesOf(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> root[KEY_PRESETS] as? JsonArray
        else -> null
    }

    /** One step between two adjacent schema versions, applied to a single preset entry. */
    private interface Migration {
        /** The version this upgrades out of. It runs on any document at or below it. */
        val from: Int

        fun apply(entry: JsonElement): JsonElement
    }

    /**
     * 1 → 2: write the render mode a version 1 preset was rendered with.
     *
     * Making it explicit rather than leaving it to the field's default is the entire point of
     * this step. The default is going to become Pixel Dither, and on that day an old preset
     * falling through to it would quietly change how it looks.
     */
    private object RenderModeMigration : Migration {
        override val from = 1

        override fun apply(entry: JsonElement): JsonElement {
            val preset = entry as? JsonObject ?: return entry
            val params = preset[KEY_PARAMS] as? JsonObject ?: return entry
            if (KEY_RENDER_MODE in params) return entry
            val glyphArt = JsonPrimitive(RenderModeIds.idOf(RenderMode.GlyphMatrix))
            val patched = JsonObject(params + (KEY_RENDER_MODE to glyphArt))
            return JsonObject(preset + (KEY_PARAMS to patched))
        }
    }

    /**
     * 2 → 3: enum constant names become stable ids.
     *
     * Only names this build recognises are rewritten. Anything else — an id already, or a style
     * from a build that knows more than this one — is carried through untouched, so that the
     * reader is the single place that decides an identity is unknown. A migration that guessed
     * here would be the one thing this whole step exists to prevent: a preset quietly coming
     * back as a different algorithm.
     *
     * Layers carry a full set of params of their own, so the rewrite recurses into them. It has
     * to: a layer is where a second dither style lives.
     */
    private object WireIdMigration : Migration {
        override val from = 2

        override fun apply(entry: JsonElement): JsonElement {
            val preset = entry as? JsonObject ?: return entry
            val params = preset[KEY_PARAMS] as? JsonObject ?: return entry
            return JsonObject(preset + (KEY_PARAMS to params(params)))
        }

        private fun params(params: JsonObject): JsonObject {
            var patched = params
            patched = rename(patched, KEY_RENDER_MODE) { RenderModeIds.migrated(it) }
            patched = rename(patched, KEY_DITHER_MODE) { DitherModeIds.migrated(it) }
            patched = effects(patched)
            return layers(patched)
        }

        /** The effect chain's order, which is a list of effect identities. */
        private fun effects(params: JsonObject): JsonObject {
            val effects = params[KEY_EFFECTS] as? JsonObject ?: return params
            val order = effects[KEY_ORDER] as? JsonArray ?: return params
            val renamed = JsonArray(
                order.map { id ->
                    val raw = (id as? JsonPrimitive)?.contentOrNull ?: return@map id
                    EffectIds.migrated(raw)?.let { JsonPrimitive(it) } ?: id
                },
            )
            return JsonObject(params + (KEY_EFFECTS to JsonObject(effects + (KEY_ORDER to renamed))))
        }

        private fun layers(params: JsonObject): JsonObject {
            val layers = params[KEY_LAYERS] as? JsonArray ?: return params
            val renamed = JsonArray(
                layers.map { layer ->
                    val entry = layer as? JsonObject ?: return@map layer
                    val nested = entry[KEY_PARAMS] as? JsonObject ?: return@map layer
                    JsonObject(entry + (KEY_PARAMS to params(nested)))
                },
            )
            return JsonObject(params + (KEY_LAYERS to renamed))
        }

        private fun rename(params: JsonObject, key: String, id: (String) -> String?): JsonObject {
            val raw = (params[key] as? JsonPrimitive)?.contentOrNull ?: return params
            val stable = id(raw) ?: return params
            return JsonObject(params + (key to JsonPrimitive(stable)))
        }
    }

    /** In ascending order of [Migration.from]. */
    private val migrations: List<Migration> = listOf(RenderModeMigration, WireIdMigration)
}
