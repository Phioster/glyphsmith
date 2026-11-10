package org.phioster.glyphsmith.data

/**
 * Brings presets that a newer build ships into a library the user has already saved.
 *
 * A stored library is read instead of the shipped one, so without this a preset added in an
 * update only ever reached people who had never saved anything — and the one way to get it,
 * a reset, deletes their own presets. What is added is decided by what the user has *been
 * offered*, not by what is in their list: a built-in they deleted on purpose stays deleted.
 */
object BuiltInArrivals {

    /**
     * Built-ins that shipped after 1.1.0, before anything recorded what had been offered.
     * A library saved before then has seen every other built-in, deleted or not.
     */
    val SINCE_1_1_0 = setOf("phosphor relief", "drifting orbs", "tube glyphs")

    /** The library to show, and the names offered so far, after this build's arrivals. */
    data class Result(val presets: List<Preset>, val offered: Set<String>, val added: Int)

    fun merge(stored: List<Preset>, offered: Set<String>?, builtIns: List<Preset>): Result {
        val seen = offered ?: (builtIns.map { key(it.name) }.toSet() - SINCE_1_1_0.map(::key).toSet())
        val have = stored.map { key(it.name) }.toSet()
        val arriving = builtIns.filter { key(it.name) !in seen && key(it.name) !in have }
        return Result(
            presets = stored + arriving,
            offered = seen + builtIns.map { key(it.name) },
            added = arriving.size,
        )
    }

    private fun key(name: String) = name.trim().lowercase()
}
