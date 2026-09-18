# Custom Error-Diffusion Kernel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let someone build their own error-diffusion kernel on a grid — set the weights, watch
the picture change — instead of choosing from the kernels we happened to ship.

**Architecture:** The mechanism already exists. Every diffusing style in
`core/dither/DiffusionKernels.kt` is an `ErrorDiffusion(listOf(DiffusionTap(dx, dy, weight), …))`,
and the render loop already knows how to run one. What is missing is a route from the interface
to that list. `DitherMode.CUSTOM_SCREEN` and `RenderSettings.screenOverride` already solve the
identical problem for ordered screens, inline in the preset, falling back to Bayer when empty.
This plan copies that pattern exactly: `DitherMode.CUSTOM_KERNEL` plus
`RenderSettings.kernelOverride`, falling back to Floyd–Steinberg when empty.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. No new
dependencies.

**Spec:** `docs/superpowers/plans/2026-09-18-reference-survey-spec.md` §2.2

## Global Constraints

From the spec §4. Every task's requirements implicitly include this section.

- No runtime plugins, no reflection, no dynamic class loading.
- Wire ids never change once written; shape `category.name`. The new id is `dither.custom-kernel`.
- `PresetSchema.CURRENT_VERSION` is **4**. Adding a field with a default does not raise it —
  `kernelOverride` defaults to `emptyList()`, so every existing preset decodes unchanged and
  renders unchanged. **Do not raise the version.**
- An unknown id is refused, not remapped.
- `LayeringTest` enforces import direction. `core/dither` may not import from `render`.
- No new dependencies.

### The one real obstacle, stated up front

`DitherAlgorithms.byMode` is built **once, statically**, and its own comment says so: "Nothing
here is stateful: two renders that ask for the same style get the same object." A custom kernel
is per-settings and therefore cannot live in that array.

`Dither.algorithmOf(mode)` is the single seam where the render path turns a mode into an
algorithm. This plan widens exactly that seam and nothing else: the declared kernel stays the
fallback, and the settings supply an override when one is present. `ErrorDiffusion.depth` — which
sizes the error buffer — is computed from `taps`, so it must be read off the *instance actually
used*, not off the declaration. Task 3 exists to prove that.

## File Structure

| File | Responsibility |
| --- | --- |
| `core/dither/CustomKernel.kt` | **new** — validate, normalise and build an `ErrorDiffusion` from a flat weight grid |
| `core/dither/DitherMode.kt` | **modified** — one constant, `CUSTOM_KERNEL` |
| `core/dither/DitherModeIds.kt` | **modified** — `dither.custom-kernel` |
| `core/dither/DitherAlgorithms.kt` | **modified** — declare the fallback kernel |
| `core/dither/Dither.kt` | **modified** — `algorithmOf` accepts an override |
| `render/RenderSettings.kt` | **modified** — `kernelOverride: List<Float>` |
| `ui/panels/RenderPanel.kt` | **modified** — the grid editor |

---

### Task 1: Turn a weight grid into a kernel

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/dither/CustomKernel.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelTest.kt`

**Interfaces:**
- Produces: `object CustomKernel` with
  - `const val RADIUS = 2` — the grid is 5 wide and 3 deep
  - `const val WIDTH = 5`, `const val DEPTH = 3`, `const val SIZE = 15`
  - `val CENTRE = 2` — index of the current cell within a row
  - `fun taps(weights: List<Float>): List<DiffusionTap>`
  - `fun diffusion(weights: List<Float>): ErrorDiffusion?` — null when the grid carries no
    usable weight, which is the caller's signal to fall back

The grid is row-major, 5 columns by 3 rows: row 0 is the current row (only the cells *after* the
centre are reachable), rows 1 and 2 are below. Cells at or before the centre of row 0 are
structurally unreachable — a serpentine scan has already passed them — and are dropped rather
than rejected, so the editor can show a full rectangle with dead cells greyed out.

Weights are **normalised to sum 1**. Atkinson deliberately loses a quarter of its error and is
not normalised, but that is a property of a shipped declaration; a hand-entered grid summing to
0.3 would silently wash the picture out, and the editor shows the normalised result so what you
set is what you get.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomKernelTest {

    /** Row-major 5x3, centre of row 0 is index 2. */
    private fun grid(vararg pairs: Pair<Int, Float>): List<Float> {
        val w = MutableList(CustomKernel.SIZE) { 0f }
        for ((i, v) in pairs) w[i] = v
        return w
    }

    @Test
    fun `floyd-steinberg entered by hand reproduces floyd-steinberg`() {
        // right 7, then below-left 3, below 5, below-right 1
        val taps = CustomKernel.taps(grid(3 to 7f, 6 to 3f, 7 to 5f, 8 to 1f))
        val expected = mapOf(
            (1 to 0) to 7 / 16f,
            (-1 to 1) to 3 / 16f,
            (0 to 1) to 5 / 16f,
            (1 to 1) to 1 / 16f,
        )
        assertEquals(expected.size, taps.size)
        for (tap in taps) {
            assertEquals("tap ${tap.dx},${tap.dy}", expected[tap.dx to tap.dy]!!, tap.weight, 1e-6f)
        }
    }

    @Test
    fun `weights are normalised whatever scale they are entered at`() {
        val taps = CustomKernel.taps(grid(3 to 700f, 7 to 300f))
        assertEquals(1f, taps.sumOf { it.weight.toDouble() }.toFloat(), 1e-5f)
    }

    @Test
    fun `cells the scan has already passed are dropped`() {
        // indices 0,1,2 are left-of and at the centre on the current row
        val taps = CustomKernel.taps(grid(0 to 5f, 1 to 5f, 2 to 5f, 3 to 1f))
        assertEquals(1, taps.size)
        assertEquals(1, taps[0].dx)
        assertEquals(0, taps[0].dy)
    }

    @Test
    fun `an empty or unreachable grid has no kernel at all`() {
        assertNull(CustomKernel.diffusion(grid()))
        assertNull(CustomKernel.diffusion(grid(0 to 9f)))
        assertTrue(CustomKernel.diffusion(grid(3 to 1f)) != null)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CustomKernelTest*'`
Expected: FAIL — `Unresolved reference: CustomKernel`.

- [ ] **Step 3: Implement it**

```kotlin
package org.phioster.glyphsmith.core.dither

/**
 * A kernel somebody typed in, rather than one we shipped.
 *
 * The grid is row-major, [WIDTH] across and [DEPTH] down, with the current cell at [CENTRE] of
 * row 0. Cells at or before that centre are unreachable — a serpentine scan has already been
 * there — and are dropped rather than refused, so the editor can draw an honest rectangle with
 * the dead cells greyed instead of a ragged shape nobody can read.
 *
 * Weights are normalised to sum 1. The shipped Atkinson kernel deliberately throws a quarter of
 * its error away and is *not* normalised, but that is a decision made once, in a declaration,
 * with a comment next to it. A hand-entered grid summing to 0.3 is an accident, and it would
 * show up as a washed-out picture rather than as a mistake.
 */
object CustomKernel {

    const val WIDTH = 5
    const val DEPTH = 3
    const val SIZE = WIDTH * DEPTH
    const val CENTRE = WIDTH / 2

    /** True when the grid cell at [index] is somewhere the error can still be sent. */
    fun isReachable(index: Int): Boolean {
        val dy = index / WIDTH
        val dx = index % WIDTH - CENTRE
        return dy > 0 || dx > 0
    }

    fun taps(weights: List<Float>): List<DiffusionTap> {
        val usable = (0 until minOf(SIZE, weights.size))
            .filter { isReachable(it) && weights[it] > 0f }
        val total = usable.sumOf { weights[it].toDouble() }.toFloat()
        if (total <= 0f) return emptyList()
        return usable.map { i ->
            DiffusionTap(i % WIDTH - CENTRE, i / WIDTH, weights[i] / total)
        }
    }

    /** The kernel, or null when the grid says nothing — the caller's signal to fall back. */
    fun diffusion(weights: List<Float>): ErrorDiffusion? =
        taps(weights).takeIf { it.isNotEmpty() }?.let { ErrorDiffusion(it) }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*CustomKernelTest*'`
Expected: PASS, all four.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/CustomKernel.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelTest.kt
git commit -m "Ein Gewichtsraster wird zu einem Fehlerdiffusions-Kernel"
```

---

### Task 2: Register the mode

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelModeTest.kt`

**Interfaces:**
- Consumes: `CustomKernel` from Task 1.
- Produces: `DitherMode.CUSTOM_KERNEL`, wire id `dither.custom-kernel`, filed under the error
  diffusion family, declaring Floyd–Steinberg as its fallback.

Declaring the fallback in `DitherAlgorithms` is what keeps the static array honest: asked with no
settings in hand, the mode is a real, working kernel rather than a hole. Follow whatever
`CUSTOM_SCREEN` does for its id and its family — this mode is its sibling and should read like it.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomKernelModeTest {

    @Test
    fun `the mode has a stable id and a real fallback kernel`() {
        assertEquals("dither.custom-kernel", DitherModeIds.idOf(DitherMode.CUSTOM_KERNEL))
        val algorithm = DitherProviders.of(DitherMode.CUSTOM_KERNEL).algorithm
        assertTrue("must fall back to a working kernel", algorithm is ErrorDiffusion)
        assertEquals(
            "the fallback is Floyd-Steinberg",
            4,
            (algorithm as ErrorDiffusion).taps.size,
        )
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CustomKernelModeTest*'`
Expected: FAIL — `Unresolved reference: CUSTOM_KERNEL`.

- [ ] **Step 3: Add the constant, the id and the declaration**

Add `CUSTOM_KERNEL` to `DitherMode` with the label `custom kernel` and the error-diffusion
category. Add `dither.custom-kernel` to `DitherModeIds`. In `DitherAlgorithms.declare`:

```kotlin
        // The fallback only. The real kernel comes from RenderSettings.kernelOverride, because
        // this table is built once and shared between renders — see the comment on byMode.
        DitherMode.CUSTOM_KERNEL -> DiffusionKernels.FLOYD_STEINBERG
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Dither*'`
Expected: PASS. Any test asserting a mode count will fail loudly — update the count, that is the
test doing its job.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelModeTest.kt
git commit -m "Custom Kernel als eigener Dither-Modus mit Floyd-Steinberg als Rueckfall"
```

---

### Task 3: Carry the kernel in the settings and run it

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/dither/Dither.kt:113-114`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelRenderTest.kt`

**Interfaces:**
- Consumes: `CustomKernel.diffusion`, `DitherMode.CUSTOM_KERNEL`.
- Produces: `RenderSettings.kernelOverride: List<Float> = emptyList()`.
- Produces: `Dither.algorithmOf(mode: DitherMode, kernelOverride: List<Float> = emptyList())`.

The error buffer's depth must come from the instance in use. A custom kernel reaching two rows
down while the buffer was sized from a one-row declaration writes past the end or silently drops
the deepest taps; the test below is aimed straight at it.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomKernelRenderTest {

    private fun grid(vararg pairs: Pair<Int, Float>): List<Float> {
        val w = MutableList(CustomKernel.SIZE) { 0f }
        for ((i, v) in pairs) w[i] = v
        return w
    }

    @Test
    fun `the override replaces the declared kernel`() {
        val onlyRight = grid(3 to 1f)
        val algorithm = Dither.algorithmOf(DitherMode.CUSTOM_KERNEL, onlyRight) as ErrorDiffusion
        assertEquals(1, algorithm.taps.size)
        assertEquals(1f, algorithm.taps[0].weight, 1e-6f)
    }

    @Test
    fun `an empty override leaves the declared kernel in place`() {
        val algorithm = Dither.algorithmOf(DitherMode.CUSTOM_KERNEL, emptyList()) as ErrorDiffusion
        assertEquals(4, algorithm.taps.size)
    }

    /** The error buffer is sized from depth. A two-row kernel must report two rows. */
    @Test
    fun `depth follows the override, not the declaration`() {
        val twoRowsDown = grid(12 to 1f) // row 2, left of centre
        val algorithm = Dither.algorithmOf(DitherMode.CUSTOM_KERNEL, twoRowsDown) as ErrorDiffusion
        assertEquals(3, algorithm.depth)
    }

    @Test
    fun `other modes ignore the override entirely`() {
        val algorithm = Dither.algorithmOf(DitherMode.FLOYD_STEINBERG, grid(3 to 1f))
        assertEquals(4, (algorithm as ErrorDiffusion).taps.size)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CustomKernelRenderTest*'`
Expected: FAIL — `algorithmOf` is private and takes one argument.

- [ ] **Step 3: Widen the seam**

In `RenderSettings`, beside `screenOverride` and worded to match it:

```kotlin
    /**
     * A hand-built error-diffusion kernel, stored inline as [CustomKernel]'s row-major grid.
     *
     * Inline for the reason `screenOverride` is: a preset that renders differently on somebody
     * else's device is worse than one that is a few bytes larger. Empty means none, and
     * `CUSTOM_KERNEL` falls back to Floyd-Steinberg rather than to no diffusion at all.
     */
    val kernelOverride: List<Float> = emptyList(),
```

In `Dither`, make the seam public and give it the override:

```kotlin
    fun algorithmOf(
        mode: DitherMode,
        kernelOverride: List<Float> = emptyList(),
    ): DitherAlgorithm {
        if (mode == DitherMode.CUSTOM_KERNEL) {
            CustomKernel.diffusion(kernelOverride)?.let { return it }
        }
        return DitherProviders.of(mode).algorithm
    }
```

Then pass `settings.kernelOverride` down from each render-path caller of `algorithmOf`. Leave
`isOrdered` and `surfaceOf` calling the one-argument form — a custom kernel is neither ordered
nor modulated, so they are correct as they stand.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Dither*' --tests '*Render*' --tests '*Preset*'`
Expected: PASS. The preset tests prove `kernelOverride`'s default keeps old documents decoding.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/Dither.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelRenderTest.kt
git commit -m "Der Kernel kommt aus den Einstellungen, der Fehlerpuffer folgt ihm"
```

---

### Task 4: The grid editor

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt`
- Modify: `README.md`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelPresetTest.kt`

**Interfaces:**
- Consumes: `CustomKernel.WIDTH`, `DEPTH`, `SIZE`, `CENTRE`, `isReachable`, `taps`.

A 5×3 grid of number fields, shown only when the mode is `CUSTOM_KERNEL`, following the existing
rule that controls which do not apply to the active mode disappear rather than sit inert. The
current cell is marked, unreachable cells are greyed and not editable, and the normalised weight
is shown beneath each field so the sum-to-1 behaviour is visible rather than surprising.

Offer the four shipped kernels as starting points — Floyd–Steinberg, Atkinson, Jarvis, Burkes —
because an empty grid is a worse invitation than a familiar one to deform. Read them from
`DiffusionKernels`; do not retype the weights.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

class CustomKernelPresetTest {

    @Test
    fun `a kernel survives a round trip through a preset`() {
        val weights = MutableList(CustomKernel.SIZE) { 0f }
        weights[3] = 7f
        weights[7] = 9f
        val saved = Json.encodeToString(
            RenderSettings.serializer(),
            RenderSettings(ditherMode = DitherMode.CUSTOM_KERNEL, kernelOverride = weights),
        )
        val back = Json.decodeFromString(RenderSettings.serializer(), saved)
        assertEquals(weights, back.kernelOverride)
    }

    @Test
    fun `a preset written before this feature still decodes`() {
        val old = """{"ditherMode":"FLOYD_STEINBERG"}"""
        val back = Json { ignoreUnknownKeys = true }
            .decodeFromString(RenderSettings.serializer(), old)
        assertEquals(emptyList<Float>(), back.kernelOverride)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CustomKernelPresetTest*'`
Expected: FAIL if `kernelOverride` is missing or mis-defaulted; this test is the guard on Task 3's
serialisation promise as much as on the editor.

- [ ] **Step 3: Build the editor**

Add the section to `RenderPanel.kt`, gated on `settings.ditherMode == DitherMode.CUSTOM_KERNEL`.
In `README.md`, add one paragraph under **Dithering** naming the mode and saying what it is for:
the point is not an eightieth algorithm but the ability to deform one until it breaks.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — the whole suite.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/CustomKernelPresetTest.kt \
        README.md
git commit -m "Kernel-Editor im Bedienfeld, mit den vier bekannten als Startpunkt"
```

---

## Notes for the reviewer

- **The grid size is a product decision, not a technical limit.** 5×3 covers Floyd–Steinberg,
  Atkinson, Burkes and Sierra Lite exactly; Jarvis and Stucki are 5×3 too. Stevenson–Arce is not
  and is deliberately out of reach. If it should be, that is a separate change to three constants
  and the editor, and the serialised list grows with a default — no migration.
- **Normalisation is deliberate and Atkinson is the reason it is worth arguing about.** If
  someone wants the Atkinson error loss from a hand-built kernel, that is a second control
  ("keep the lost error"), not a reason to drop normalisation.
