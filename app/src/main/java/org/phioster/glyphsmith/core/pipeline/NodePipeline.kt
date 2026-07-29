package org.phioster.glyphsmith.core.pipeline

import org.phioster.glyphsmith.effects.Pixels

/**
 * Runs an ordered list of [ImageProcessorNode]s over one buffer.
 *
 * The chain is data, not a sequence of calls: what runs, and in what order, comes from the
 * list it is handed. That is what makes a step optional — a node that is not in the list, or
 * whose [ImageProcessorNode.enabled] is false, does not exist as far as this loop is
 * concerned.
 *
 * Each node either mutates the buffer it was given and returns it, or returns a new one. The
 * loop reassigns either way, so a node is free to choose whichever it can do — the ones that
 * cannot work in place (channel separation, anything that reads a neighbourhood) return a new
 * buffer, and the rest do not pay for a copy.
 */
object NodePipeline {

    fun run(input: Pixels, nodes: List<ImageProcessorNode>, ctx: RenderContext): Pixels {
        var pixels = input
        for (node in nodes) {
            if (!node.enabled) continue
            val out = node.process(pixels, ctx)
            // The one place a buffer may be recycled. A step that returned a *different* buffer
            // has made the one it was given unreachable — the chain is linear, so nothing else
            // can still be holding it. A step that mutated in place returns the same array, and
            // that array is the live image; identity is what tells the two apart.
            if (out.data !== pixels.data) ctx.pool.give(pixels.data)
            pixels = out
        }
        return pixels
    }

    /** The nodes that would actually run, in order — what a test or a UI readout wants. */
    fun activeOf(nodes: List<ImageProcessorNode>): List<String> =
        nodes.filter { it.enabled }.map { it.id }
}
