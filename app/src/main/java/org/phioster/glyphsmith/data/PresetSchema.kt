package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.phioster.glyphsmith.render.RenderMode

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

    const val CURRENT_VERSION = 2

    /** The bare array, from before anything wrote a version. */
    private const val VERSION_LEGACY = 1

    private const val KEY_VERSION = "schemaVersion"
    private const val KEY_PRESETS = "presets"
    private const val KEY_PARAMS = "params"
    private const val KEY_RENDER_MODE = "renderMode"

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
     * Entry by entry, and dropping whatever will not read: the effect order serialises as effect
     * *names*, and an unknown name throws no matter what `ignoreUnknownKeys` says. Decoding the
     * array as one value would therefore mean a single preset written by a newer build silently
     * discarding the entire library. Per entry, that costs exactly the preset that cannot be read.
     */
    fun decode(text: String): List<Preset> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val entries = entriesOf(root) ?: return emptyList()
        val steps = migrations.filter { it.from >= versionOf(root) }
        return entries.mapNotNull { entry ->
            val migrated = steps.fold(entry) { carried, step -> step.apply(carried) }
            runCatching { json.decodeFromJsonElement<Preset>(migrated) }.getOrNull()
        }
    }

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
            val patched = JsonObject(params + (KEY_RENDER_MODE to JsonPrimitive(RenderMode.GlyphMatrix.name)))
            return JsonObject(preset + (KEY_PARAMS to patched))
        }
    }

    /** In ascending order of [Migration.from]. */
    private val migrations: List<Migration> = listOf(RenderModeMigration)
}
