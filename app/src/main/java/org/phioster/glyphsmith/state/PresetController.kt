package org.phioster.glyphsmith.state

import android.content.Context
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The saved library and every operation on it.
 *
 * Second slice of splitting the central ViewModel, and the first that Robolectric makes worth
 * doing: [PresetStore] is now covered, so what sits on top of it can be too.
 *
 * The ViewModel held five near-identical blocks — call the store, copy the returned list into
 * the state, set a status line, rebuild the thumbnails — which is four steps repeated five
 * times, and the sort of thing where one of them quietly loses a step. Here each operation is
 * one call that returns the line to show; the ViewModel keeps the two jobs that are genuinely
 * its own, publishing the state and rendering thumbnails.
 *
 * ## The invariant
 *
 * [presets] is the library as it is on disk. Every operation goes through the store and takes
 * the list the store hands back rather than editing a local copy, so the two cannot drift —
 * which is the failure that would show up as work reappearing after a restart, or a deletion
 * that did not stick. A test holds each operation to it.
 */
class PresetController(context: Context) {

    private val store = PresetStore(context)

    /** The library, as stored. Replaced wholesale by every operation. */
    var presets: List<Preset> = store.load()
        private set

    /** Saves [params] under [name], overwriting a preset of that name if there is one. */
    fun save(name: String, params: RenderSettings, description: String = ""): String {
        presets = store.upsert(name, params, description)
        return "preset saved"
    }

    fun rename(from: String, to: String, description: String): String {
        presets = store.rename(from, to, description)
        return "preset renamed"
    }

    fun delete(name: String): String {
        presets = store.delete(name)
        return "preset deleted"
    }

    /** Flips the star. Silent on purpose — the star itself is the feedback. */
    fun toggleFavourite(name: String) {
        presets = store.toggleFavourite(name)
    }

    /** Puts the shipped library back, discarding anything saved on top of it. */
    fun reset(): String {
        presets = store.reset()
        return "presets reset to the built-in library"
    }

    fun exportJson(): String = store.exportJson()

    /**
     * Merges an exported document, and says what it could not read.
     *
     * Takes the text rather than a `Uri`: opening one needs a `ContentResolver` and belongs to
     * whoever has the Android plumbing, while what a merge *does* is the part worth testing.
     */
    fun import(text: String): String {
        val imported = store.importJson(text) ?: return "that file isn't a preset export"
        presets = imported.presets
        return imported.summary()
    }
}
