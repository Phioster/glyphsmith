package org.phioster.glyphsmith.data

/**
 * What an import did.
 *
 * It exists because the interesting half of an import was being thrown away. [PresetSchema.read]
 * has always collected every entry it could not decode, and the reason for each; both
 * [PresetStore.load] and [PresetStore.importJson] went through `decode`, which drops that list
 * on the floor. So importing a file exported from a newer build lost the presets naming a style
 * this build has never heard of, and said nothing at all — the count simply came back lower than
 * the file had entries, if anyone was counting.
 *
 * Kept separate from [PresetStore] and free of Android on purpose: what gets *said* about an
 * import is a decision worth testing, and the store needs a `Context` that a JVM test has not
 * got.
 */
data class Import(
    /** The whole library after the merge — what the picker should show. */
    val presets: List<Preset>,
    /** How many entries in the offered file actually read. */
    val added: Int,
    /** The entries that did not, and why. */
    val skipped: List<PresetSchema.Skipped>,
) {

    /**
     * One line for the status bar, which is the only surface an import has.
     *
     * The reasons are summarised by *kind* rather than quoted, because the distinction that
     * matters to somebody staring at a preset that has gone missing is only ever one of two
     * things: this build is older than the file, or the file is damaged. Quoting
     * `unknown dither id: "dither.something"` at them says the same thing in more characters
     * than the line has.
     */
    fun summary(): String = buildString {
        append(if (added == 0) "nothing imported" else "$added imported · ${presets.size} presets")
        if (skipped.isEmpty()) return@buildString

        append(" · ${skipped.size} skipped, ${reasonKind()}: ")
        append(skipped.take(NAMES_SHOWN).joinToString { it.name })
        if (skipped.size > NAMES_SHOWN) append(" +${skipped.size - NAMES_SHOWN}")
    }

    /**
     * An entry naming something this build does not know is not corrupt — it is from a build
     * that knows more, and that is a different thing to tell someone.
     */
    private fun reasonKind(): String {
        val unknown = skipped.count { it.reason.contains(UNKNOWN, ignoreCase = true) }
        return when (unknown) {
            skipped.size -> "not known to this build"
            0 -> "unreadable"
            else -> "$unknown not known to this build, ${skipped.size - unknown} unreadable"
        }
    }

    private companion object {
        const val NAMES_SHOWN = 3

        /**
         * What [org.phioster.glyphsmith.core.serial.UnknownWireIdException] says. Matched on the
         * word rather than on the type because the reason has already been flattened to a string
         * by the time it reaches here — the decode failure is caught per entry so that one bad
         * preset cannot cost the file.
         */
        const val UNKNOWN = "unknown"
    }
}
