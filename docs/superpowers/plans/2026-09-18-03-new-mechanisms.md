# Four Algorithms With New Mechanisms — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the four dithering methods from the survey whose *mechanism* Glyphsmith does not
already have — and no others.

**Architecture:** `DitherAlgorithm` is explicitly a taxonomy of mechanisms, not of looks: "Two
styles that draw nothing alike are the same kind if they decide a cell the same way." Three of
the four here fit an existing kind — `ErrorDiffusion` already carries a `perValue` hook for
kernels that change with the value being quantised, which is how Ostromoukhov already works. The
fourth, DBS, fits no existing kind and needs a new one.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Gradle. No new dependencies.

**Spec:** `docs/superpowers/plans/2026-09-18-reference-survey-spec.md` §2.3

## Why this plan is last, and short

`CLAUDE.md` ranks *additional algorithm count* **10th of 10** and says outright: "Do not add
algorithms merely to increase the advertised number." This plan is therefore filtered hard. Every
candidate from the survey that was only a variation on something shipped was **rejected**, and the
rejections are recorded in the spec §2.3 so they are not proposed again: Bayer 32×32, Sierra 2-4A,
Posterize, Woodcut and Stipple.

What survives is four methods that decide a cell in a way nothing in the app currently does.

## Global Constraints

From the spec §4.

- Wire ids never change once written; shape `category.name`.
- `PresetSchema.CURRENT_VERSION` is **4**. Adding enum constants with new ids does **not** raise
  it; adding a params field with a default does not either. **Do not raise the version.**
- An unknown id is refused, not remapped.
- No migration may change how anything renders.
- `LayeringTest` enforces import direction. Everything here is in `core/dither`.
- Do not weaken tests to make an implementation pass.
- No new dependencies.
- **Primary sources only.** Where a construction is needed it comes from the paper, not from a
  reimplementation. The precedent is concrete: dither-guy's Ostromoukhov table diverges from the
  paper from row 8 (`22,6,11` in the paper against `22,5,10` there) and would have been inherited
  silently by anyone copying it.

## File Structure

| File | Responsibility |
| --- | --- |
| `core/dither/AdaptiveDiffusion.kt` | **new** — content-adaptive Floyd–Steinberg, 3×3 and 7×7 |
| `core/dither/ZhouFang.kt` | **new** — variable error diffusion with a modulated threshold |
| `core/dither/KackerAllebach.kt` | **new** — dot-profile screening |
| `core/dither/DirectBinarySearch.kt` | **new** — iterative whole-grid optimisation, and a new `DitherAlgorithm` kind |
| `core/dither/DitherAlgorithm.kt` | **modified** — one new sealed subclass, `WholeGrid` |
| `core/dither/DitherMode.kt`, `DitherModeIds.kt`, `DitherAlgorithms.kt` | **modified** — five constants, five ids, five declarations |

---

### Task 1: Adaptive Floyd–Steinberg

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/dither/AdaptiveDiffusion.kt`
- Modify: `core/dither/DitherMode.kt`, `DitherModeIds.kt`, `DitherAlgorithms.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/AdaptiveDiffusionTest.kt`

**Interfaces:**
- Produces: `object AdaptiveDiffusion { val THREE: ErrorDiffusion; val SEVEN: ErrorDiffusion }`
- Produces: `DitherMode.ADAPTIVE_FS_3`, `ADAPTIVE_FS_7`; ids `dither.adaptive-fs-3`,
  `dither.adaptive-fs-7`.

The new mechanism: the kernel's weights shift with the value being quantised, pushing error
forward in flat areas and sideways near extremes, which suppresses the worm artefacts plain
Floyd–Steinberg leaves in smooth gradients. This is `ErrorDiffusion`'s existing `perValue` hook —
the same one Ostromoukhov uses — so no new kind is needed.

**The allocation trap, stated because the existing code states it:** `kernelFor` is documented as
"Returns a cached list, never a fresh one. This is asked once per cell, and a megapixel image is
a million allocations if it is answered carelessly." Build a table of kernels once, indexed by
quantised value, and return entries from it. Test 3 below is aimed at exactly this.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDiffusionTest {

    @Test
    fun `the kernel varies with the value`() {
        val d = AdaptiveDiffusion.THREE
        assertTrue("must declare itself variable", d.varies)
        assertNotEquals(d.kernelFor(0.05f), d.kernelFor(0.5f))
    }

    @Test
    fun `every kernel it can return conserves the error`() {
        for (d in listOf(AdaptiveDiffusion.THREE, AdaptiveDiffusion.SEVEN)) {
            for (step in 0..100) {
                val taps = d.kernelFor(step / 100f)!!
                val sum = taps.sumOf { it.weight.toDouble() }.toFloat()
                assertEquals("value ${step / 100f}", 1f, sum, 1e-4f)
            }
        }
    }

    /** Asked once per cell. It must hand back a cached list, not build one. */
    @Test
    fun `the same value returns the identical list object`() {
        val d = AdaptiveDiffusion.SEVEN
        assertSame(d.kernelFor(0.42f), d.kernelFor(0.42f))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AdaptiveDiffusionTest*'`
Expected: FAIL — `Unresolved reference: AdaptiveDiffusion`.

- [ ] **Step 3: Implement it**

Build a `Array<List<DiffusionTap>>` of 256 precomputed kernels at construction, interpolating the
weight redistribution across the value range, and have `perValue` index into it. Declare the
representative `taps` as plain Floyd–Steinberg so `depth` sizes the buffer correctly. Register
both modes under the error-diffusion family.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*AdaptiveDiffusionTest*' --tests '*Dither*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/AdaptiveDiffusion.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/AdaptiveDiffusionTest.kt
git commit -m "Adaptives Floyd-Steinberg, 3x3 und 7x7"
```

---

### Task 2: Zhou–Fang

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/dither/ZhouFang.kt`
- Modify: `core/dither/DitherMode.kt`, `DitherModeIds.kt`, `DitherAlgorithms.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/ZhouFangTest.kt`

**Interfaces:**
- Produces: `object ZhouFang { val KERNEL: ErrorDiffusion }`
- Produces: `DitherMode.ZHOU_FANG`; id `dither.zhou-fang`.

Primary source: Bingfeng Zhou and Xifeng Fang, *Improving Mid-tone Quality of Variable-Coefficient
Error Diffusion Using Threshold Modulation*, SIGGRAPH 2003. The second published member of the
family Ostromoukhov started, and the only other one with a construction rather than a table of
taste.

Two parts, and **both** are needed or the result is just Ostromoukhov with extra steps: variable
coefficients per input level, *and* a threshold modulated by a noise field. The mid-tone quality
the title refers to comes from the second part.

`core/dither/Ostromoukhov.kt` is the model to follow for shape. Read it first.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhouFangTest {

    @Test
    fun `coefficients vary with the input level and always conserve the error`() {
        val d = ZhouFang.KERNEL
        assertTrue(d.varies)
        for (step in 0..255) {
            val taps = d.kernelFor(step / 255f)!!
            assertEquals("level $step", 1f, taps.sumOf { it.weight.toDouble() }.toFloat(), 1e-4f)
        }
        assertNotEquals(d.kernelFor(0.25f), d.kernelFor(0.5f))
    }

    /** The paper's symmetry: level i and level 255-i are mirror images. */
    @Test
    fun `the coefficient table is symmetric about the mid-tone`() {
        val d = ZhouFang.KERNEL
        for (step in 0..127) {
            val low = d.kernelFor(step / 255f)!!.map { it.weight }
            val high = d.kernelFor((255 - step) / 255f)!!.map { it.weight }
            assertEquals("level $step against ${255 - step}", low, high)
        }
    }

    @Test
    fun `threshold modulation is strongest in the mid-tones`() {
        assertTrue(ZhouFang.modulationAt(0.5f) > ZhouFang.modulationAt(0.02f))
        assertTrue(ZhouFang.modulationAt(0.5f) > ZhouFang.modulationAt(0.98f))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ZhouFangTest*'`
Expected: FAIL — `Unresolved reference: ZhouFang`.

- [ ] **Step 3: Implement it from the paper**

Coefficient table built from the paper's construction, mirrored about the mid-tone exactly as
Ostromoukhov's is (`D(i) == D(255-i)`, already relied on in `Ostromoukhov.kt`). Add
`fun modulationAt(value: Float): Float` for the threshold-modulation strength and apply it in the
render path where the threshold is formed.

**If the paper cannot be obtained, stop and report.** Do not reconstruct a coefficient table by
eye from a picture of the output. A wrong table renders plausibly and is nearly impossible to
spot later — which is precisely how dither-guy's Ostromoukhov table went wrong.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ZhouFang*' --tests '*Dither*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/ZhouFang.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/ZhouFangTest.kt
git commit -m "Zhou-Fang mit variablen Koeffizienten und Schwellenmodulation"
```

---

### Task 3: A kind for algorithms that resolve the whole grid

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithm.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/WholeGridTest.kt`

**Interfaces:**
- Produces: `abstract class WholeGrid(periodLabel, densityLabel) : DitherAlgorithm` with
  `abstract fun resolve(values: FloatArray, width: Int, height: Int, levels: Int): IntArray`.

`DitherAlgorithm`'s own documentation already anticipates this kind — it names three ways a style
can decide a cell, the third being "to stand aside and let the algorithm resolve the whole grid by
itself" — but no subclass expresses it yet. DBS is the first, and giving it a kind before giving
it an implementation keeps Task 4 from smuggling a special case into the render loop.

Doing this as its own task also means the render path's new branch gets reviewed separately from
the search maths, which are unrelated risks.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeGridTest {

    /** A stand-in that rounds, so the kind can be exercised without DBS existing yet. */
    private object Rounding : WholeGrid(DEFAULT_PERIOD_LABEL, null) {
        override fun resolve(values: FloatArray, width: Int, height: Int, levels: Int): IntArray =
            IntArray(values.size) { Dither.quantise(values[it], levels) }
    }

    @Test
    fun `a whole-grid algorithm returns one level per cell`() {
        val values = floatArrayOf(0f, 0.5f, 1f, 0.25f)
        val out = Rounding.resolve(values, 2, 2, 2)
        assertEquals(4, out.size)
        assertEquals(0, out[0])
        assertEquals(1, out[2])
        assertTrue(out.all { it in 0..1 })
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*WholeGridTest*'`
Expected: FAIL — `Unresolved reference: WholeGrid`.

- [ ] **Step 3: Add the kind and the render branch**

Add the subclass to `DitherAlgorithm.kt`. In the render path, add the branch that hands the whole
sampled grid over and takes levels back, beside the existing threshold and diffusion branches.
Leave `isOrdered` and `surfaceOf` answering false/null for it.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*WholeGridTest*' --tests '*Render*'`
Expected: PASS. No shipped style is a `WholeGrid` yet, so nothing renders differently.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithm.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/WholeGridTest.kt
git commit -m "Eine Art fuer Algorithmen, die das ganze Raster aufloesen"
```

---

### Task 4: Direct Binary Search, with a budget

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/dither/DirectBinarySearch.kt`
- Modify: `core/dither/DitherMode.kt`, `DitherModeIds.kt`, `DitherAlgorithms.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/DirectBinarySearchTest.kt`

**Interfaces:**
- Consumes: `WholeGrid` from Task 3.
- Produces: `class DirectBinarySearch(val passes: Int) : WholeGrid`
- Produces: `RenderSettings.dbsPasses: Int = 3`
- Produces: `DitherMode.DBS`; id `dither.dbs`.

Primary source: Analoui and Allebach, *Model-based halftoning using direct binary search*, SPIE
1992. Start from a plain threshold, then repeatedly consider toggling each cell and swapping it
with a neighbour, keeping any change that lowers the perceived error — the error being the
difference between the halftone and the original **after** a human visual filter, which is what
makes it "model-based" and is the part that must not be skipped.

**This is the slow one and the plan says so.** libdither's own README warns DBS "can take a few
minutes or longer" on desktop hardware. On a phone it is not a default and must never become one:

- `passes` is capped, default 3, and the control says what it costs.
- A grid above a cell count the device cannot finish is **refused with a message**, not attempted.
  Follow the export path's rule — one place decides what happened and says so — rather than
  letting the preview hang.
- DBS stays out of `surprise me` and out of the Motion preset set.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DirectBinarySearchTest {

    private fun meanError(values: FloatArray, levels: IntArray, levelCount: Int): Float {
        var sum = 0f
        for (i in values.indices) sum += abs(values[i] - levels[i].toFloat() / (levelCount - 1))
        return sum / values.size
    }

    @Test
    fun `it returns one level per cell, all in range`() {
        val values = FloatArray(64) { it / 64f }
        val out = DirectBinarySearch(passes = 1).resolve(values, 8, 8, 2)
        assertEquals(64, out.size)
        assertTrue(out.all { it in 0..1 })
    }

    /** The whole claim of DBS: it beats a plain threshold on the same picture. */
    @Test
    fun `it lands closer to the original than a bare threshold does`() {
        val values = FloatArray(256) { 0.5f + 0.2f * kotlin.math.sin(it / 8f) }
        val threshold = IntArray(256) { Dither.quantise(values[it], 2) }
        val dbs = DirectBinarySearch(passes = 3).resolve(values, 16, 16, 2)
        assertTrue(
            "DBS must improve on the threshold",
            meanError(values, dbs, 2) <= meanError(values, threshold, 2),
        )
    }

    @Test
    fun `more passes never make it worse`() {
        val values = FloatArray(256) { (it % 16) / 16f }
        val one = DirectBinarySearch(passes = 1).resolve(values, 16, 16, 2)
        val four = DirectBinarySearch(passes = 4).resolve(values, 16, 16, 2)
        assertTrue(meanError(values, four, 2) <= meanError(values, one, 2) + 1e-6f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DirectBinarySearchTest*'`
Expected: FAIL — `Unresolved reference: DirectBinarySearch`.

- [ ] **Step 3: Implement it**

Gaussian human-visual filter, an error metric over the filtered difference, and the toggle/swap
search with early exit when a pass changes nothing. Keep the filtered error incremental — a
recomputation of the whole error per candidate change is what turns minutes into hours.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*DirectBinarySearch*' --tests '*Dither*'`
Expected: PASS, all three. The middle test is the one that means anything.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/DirectBinarySearch.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt \
        app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/DirectBinarySearchTest.kt
git commit -m "Direct Binary Search mit gedeckeltem Durchlaufbudget"
```

---

### Task 5: Kacker–Allebach, and the documentation

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/dither/KackerAllebach.kt`
- Modify: `core/dither/DitherMode.kt`, `DitherModeIds.kt`, `DitherAlgorithms.kt`
- Modify: `README.md`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/dither/KackerAllebachTest.kt`

**Interfaces:**
- Produces: `DitherMode.KACKER_ALLEBACH`; id `dither.kacker-allebach`.

Primary source: Kacker and Allebach, *Joint halftoning and watermarking*, and the dot-profile
screening described alongside DBS in the same group's work. The mechanism is DBS's quality
argument applied directly rather than iteratively: a screen built from optimised dot profiles, so
a cell is decided by a lookup rather than by a search. It belongs to the ordered family.

Finish by updating the counts in `README.md`. The shelf sizes there — "error diffusion 21,
patterned 18, special 16, glitch 12, ordered 8, polygon 4" — are now wrong, and the total is no
longer 79. Recount from `DitherMode.entries` rather than adding by hand.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KackerAllebachTest {

    @Test
    fun `it is an ordered screen with a stable id`() {
        assertEquals("dither.kacker-allebach", DitherModeIds.idOf(DitherMode.KACKER_ALLEBACH))
        assertTrue(Dither.isOrdered(DitherMode.KACKER_ALLEBACH))
    }

    /** A screen is a permutation of its cells, or it is not a screen. */
    @Test
    fun `the screen ranks every cell exactly once`() {
        val algorithm = DitherProviders.of(DitherMode.KACKER_ALLEBACH).algorithm as OrderedMatrix
        val ranks = algorithm.matrix.flatMap { it.toList() }.sorted()
        assertEquals(ranks.distinct().size, ranks.size)
        assertEquals(0, ranks.first())
        assertEquals(ranks.size - 1, ranks.last())
    }
}
```

`OrderedMatrix.matrix` is a `by lazy` `Array<IntArray>` — the tile is built on first use, so this
test is also the first thing that forces the screen to be generated at all.

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*KackerAllebachTest*'`
Expected: FAIL — `Unresolved reference: KACKER_ALLEBACH`.

- [ ] **Step 3: Implement it and recount the README**

Generate the screen from its construction rules, the way the blue-noise masks already are — the
existing comment gives the reason and it applies here too: "a rule can be tested and a 1024-entry
table cannot."

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — the whole suite, this being the last task.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/dither/KackerAllebach.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherMode.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/core/dither/DitherAlgorithms.kt \
        app/src/test/java/org/phioster/glyphsmith/core/dither/KackerAllebachTest.kt \
        README.md
git commit -m "Kacker-Allebach als Raster, Stilzahlen im README nachgezogen"
```

---

## Notes for the reviewer

- **Any task here may be dropped without harming the others.** They share only the registration
  files. If a paper cannot be obtained, drop that task — an unverified dither table is worse than
  a missing style, because it looks fine.
- **DBS is the one to be suspicious of.** If the middle test in Task 4 passes only with a large
  pass count, or the incremental error bookkeeping is not actually incremental, it will be
  unusable on a phone regardless of what the test says. Ask for a timing on a real grid before
  approving it.
- **Resist re-adding the rejected styles.** Spec §2.3 lists them and why. If one is wanted anyway,
  that is a product decision to be argued on its own, not a line added quietly to this plan.
