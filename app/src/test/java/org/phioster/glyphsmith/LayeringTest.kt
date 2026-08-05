package org.phioster.glyphsmith

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The dependency direction, read off the imports.
 *
 * ARCHITECTURE.md states the rule as a prohibition — "Shared Render Core must not depend on
 * Glyph Art" — and until this existed the only thing enforcing it was whoever happened to read
 * the document. It was in fact broken: `render/` imported `AsciiParams`, `ColorMode` and
 * `EdgeDetect` out of the glyph package, because those types were shared but had never moved
 * out of the package they were first written in.
 *
 * Stated as prohibitions rather than as an exact list of what each layer may import. A new
 * *allowed* dependency is ordinary work and should not fail a test; a new dependency pointing
 * the wrong way is the thing worth stopping, and it is stopped here rather than in review.
 *
 * Reading the sources rather than the classes is deliberate: compiled Kotlin does not keep its
 * import list, and an import is exactly what the rule is about.
 */
class LayeringTest {

    private val forbidden = mapOf(
        // The engine. Knows about no render module at all, not even indirectly.
        "core" to setOf("render", "glyph", "pipeline", "state", "ui", "data", "export", "anim", "effects"),
        // Shared render infrastructure: sampling, quantisation, settings, layers, the pixel
        // module. Both modules are built on it, so it may not know either of them.
        "render" to setOf("glyph", "pipeline", "state", "ui", "data", "export"),
        // The glyph module. Sits on top of the shared core and is free to use it; it must not
        // reach up into what composes the two modules, nor sideways into storage or the UI.
        "glyph" to setOf("pipeline", "state", "ui", "data", "export"),
        // The pipeline: sampling to effects to layers, for whatever module was handed to it.
        // It runs Glyph Art without knowing that Glyph Art exists — the binding of a mode to a
        // module is the application's, and arrives as an argument. This is the rule the render
        // pipeline used to break by having the branch for all three modes inside it.
        "pipeline" to setOf("glyph", "state", "ui", "data", "export"),
        // The effect category. An effect is the thing this codebase adds most often, and the
        // point of its plugin shape is that adding one stays inside `effects/` — so the category
        // must not acquire a reason to be edited from outside. It reads pixels and knows the
        // engine; it does not know what rendered them, where they are going, or what the
        // controls look like. Compose in particular: a pass that carried its own panel would
        // need a UI toolkit to be unit-tested, which is why the panel binding lives in `ui`.
        "effects" to setOf("render", "glyph", "pipeline", "state", "ui", "data", "export", "anim"),
        // The export sink: bytes to a file. What produced them is not its business, and a
        // glyph grid is certainly not — the text, ANSI, HTML and SVG writers are Glyph Art's
        // own output and live with it.
        "export" to setOf("glyph", "pipeline", "state", "ui"),
    )

    private val sourceRoot: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/java", "app/src/main/java")) {
                val root = File(dir, "$candidate/org/phioster/glyphsmith")
                if (root.isDirectory) return@lazy root
            }
            dir = dir.parentFile
        }
        error("cannot find the sources from ${System.getProperty("user.dir")}")
    }

    private fun sourcesIn(layer: String): List<File> =
        File(sourceRoot, layer).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun importsIn(layer: String): List<Pair<File, String>> =
        sourcesIn(layer).flatMap { file ->
            file.readLines()
                .mapNotNull { PREFIX.find(it.trim())?.groupValues?.get(1) }
                .map { file to it }
        }

    @Test
    fun `no layer depends on one that is meant to sit above it`() {
        forbidden.forEach { (layer, banned) ->
            // The canary is the *files*, not the imports: a layer with nothing to import from
            // anywhere else is the healthiest thing on this list, and `export` is now exactly
            // that. Asserting on imports would have read a leaf package as a missing one.
            assertTrue("$layer has no sources at all — did a package move?", sourcesIn(layer).isNotEmpty())

            val violations = importsIn(layer)
                .filter { (_, target) -> target in banned }
                .map { (file, target) -> "${file.name} -> $target" }

            assertTrue(
                "$layer must not depend on $banned, but does: ${violations.distinct()}",
                violations.isEmpty(),
            )
        }
    }

    /**
     * The package the migration set out to dissolve. Its shared half became `render`, its glyph
     * half `glyph`, and the part that picks between them `pipeline`.
     */
    @Test
    fun `there is no ascii package left to put shared code back into`() {
        assertTrue(
            "org.phioster.glyphsmith.ascii is back",
            !File(sourceRoot, "ascii").exists(),
        )
        val strays = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("org.phioster.glyphsmith.ascii") }
            .map { it.name }
            .toList()
        assertTrue("these still name the old package: $strays", strays.isEmpty())
    }

    private companion object {
        val PREFIX = Regex("""^import\s+org\.phioster\.glyphsmith\.([a-z]+)[.\w]*""")
    }
}
