package org.phioster.glyphsmith.state

import org.phioster.glyphsmith.render.RenderSettings

/**
 * Undo and redo over the settings.
 *
 * First slice of splitting the central ViewModel. It was chosen first because it is the one
 * piece that is genuinely self-contained — two stacks and a flag, no Android, no coroutines,
 * no rendering — and because until it was pulled out it had no tests at all: reaching it meant
 * standing up a ViewModel, an Application and a debounce.
 *
 * ## When a step is recorded
 *
 * [commit] is called at the *debounce* point rather than on every change, and that is the whole
 * design. One slider drag emits dozens of values and settles once, so a drag becomes a single
 * undo step without anything having to track gestures. The controller therefore never sees the
 * intermediate values at all.
 *
 * ## Why undo does not record a step
 *
 * Restoring feeds the restored settings back through the same pipeline, which debounces and
 * calls [commit] again. Without [suppress] that arrival would be recorded as a fresh edit, and
 * undo would push what it had just undone straight back onto the stack — a button that cannot
 * get you anywhere. The flag is cleared by the very next commit, so it can only ever swallow
 * the one that the restore caused.
 */
class HistoryController(
    initial: RenderSettings,
    private val limit: Int = MAX_HISTORY,
) {

    private val undoStack = ArrayDeque<RenderSettings>()
    private val redoStack = ArrayDeque<RenderSettings>()

    /**
     * Seeded with the value the session opens on. Without that, the first edit would record a
     * step back to settings the user never saw.
     */
    private var lastCommitted = initial
    private var suppress = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** How many steps back it is currently possible to go. */
    val depth: Int get() = undoStack.size

    /**
     * Records [params] as the settled state.
     *
     * A commit that changes nothing is not a step — a drag that returns to where it started
     * should not cost an undo — and a commit caused by [undo] or [redo] is not one either.
     * Anything else clears the redo branch, because carrying it forward would offer a future
     * that no longer follows from the present.
     */
    fun commit(params: RenderSettings) {
        if (!suppress && params != lastCommitted) {
            undoStack.addLast(lastCommitted)
            if (undoStack.size > limit) undoStack.removeFirst()
            redoStack.clear()
        }
        suppress = false
        lastCommitted = params
    }

    /** The settings to go back to, or null when there is nothing to undo. */
    fun undo(): RenderSettings? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(lastCommitted)
        return restore(previous)
    }

    /** The settings to go forward to, or null when nothing has been undone. */
    fun redo(): RenderSettings? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(lastCommitted)
        return restore(next)
    }

    private fun restore(params: RenderSettings): RenderSettings {
        suppress = true
        lastCommitted = params
        return params
    }

    companion object {
        /**
         * Steps kept. Each one is a whole [RenderSettings], which is small, but a session that
         * ran for an hour should not carry every value a slider ever came to rest on.
         */
        const val MAX_HISTORY = 50
    }
}
