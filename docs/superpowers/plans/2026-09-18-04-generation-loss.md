# Generation Loss — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the JPEG pass run its encode–damage–decode cycle more than once, so the image decays
across generations the way a tape copied from a tape does — and, with corruption at zero, produce
pure recompression decay, which the app cannot currently make at all.

**Architecture:** No new effect and no new pass. `effects/JpegGlitch.kt` already performs the
entire cycle: compress to JPEG, corrupt bytes after the start-of-scan marker, decode the wreckage,
restore alpha. Generation loss is that cycle in a loop, each pass feeding the next. One integer
field on `JpegGlitchParams`, defaulting to 1, which is exactly today's behaviour.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. No new
dependencies.

**Spec:** `docs/superpowers/plans/2026-09-18-reference-survey-spec.md` §2.5

## Global Constraints

From the spec §4.

- `PresetSchema.CURRENT_VERSION` is **4**. A new field with a default does not raise it.
  `generations = 1` reproduces current output byte for byte. **Do not raise the version.**
- No migration may change how anything renders. A preset saved yesterday must look identical.
- Wire ids never change. This adds none — `effect.jpeg-glitch` stays as it is.
- Do not weaken tests to make an implementation pass.
- No new dependencies.

### The testing constraint that shapes this plan

`JpegGlitch` uses `Bitmap` and `BitmapFactory`, and **effects that touch `Bitmap` do not run
under the JVM test source set** — a known trap in this project. So there is no unit test that can
assert what the pixels look like after three generations.

Task 1 therefore extracts the *schedule* — how many cycles at what quality — as a pure function,
and tests that. The pixel behaviour is verified by eye on device, which Task 3 spells out. This is
deliberate: a test that cannot run is worse than an honest manual check, because it reads as
coverage.

## File Structure

| File | Responsibility |
| --- | --- |
| `effects/GenerationSchedule.kt` | **new** — pure: how many cycles, at what quality each |
| `effects/EffectParams.kt` | **modified** — `generations` on `JpegGlitchParams` |
| `effects/JpegGlitch.kt` | **modified** — loop the existing cycle |
| `ui/panels/EffectSections.kt` | **modified** — the generations slider |

---

### Task 1: The schedule, as a pure function

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/effects/GenerationSchedule.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/effects/GenerationScheduleTest.kt`

**Interfaces:**
- Produces: `object GenerationSchedule { fun qualities(generations: Int, quality: Int, decay: Int): IntArray }`
  — one entry per cycle, each the JPEG quality for that cycle, every value in 1..100.

Real generation loss does not re-encode at a constant quality: each copy is made from an already
damaged original, and a falling quality models a chain of lossier copies. `decay` is the drop per
generation in quality points, 0 meaning a constant-quality chain.

Quality is clamped at 1, never 0 — `Bitmap.compress` takes 1..100 and the existing code already
coerces into that range.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationScheduleTest {

    @Test
    fun `one generation at no decay is exactly today's behaviour`() {
        assertArrayEquals(intArrayOf(30), GenerationSchedule.qualities(1, 30, 0))
    }

    @Test
    fun `no decay keeps every generation at the same quality`() {
        assertArrayEquals(intArrayOf(40, 40, 40), GenerationSchedule.qualities(3, 40, 0))
    }

    @Test
    fun `decay lowers the quality once per generation`() {
        assertArrayEquals(intArrayOf(50, 45, 40, 35), GenerationSchedule.qualities(4, 50, 5))
    }

    @Test
    fun `quality never falls below one however long the chain`() {
        val schedule = GenerationSchedule.qualities(50, 20, 10)
        assertEquals(50, schedule.size)
        assertTrue("every quality must stay encodable", schedule.all { it in 1..100 })
        assertEquals(1, schedule.last())
    }

    @Test
    fun `a nonsensical generation count still yields one cycle`() {
        assertEquals(1, GenerationSchedule.qualities(0, 30, 0).size)
        assertEquals(1, GenerationSchedule.qualities(-4, 30, 0).size)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*GenerationScheduleTest*'`
Expected: FAIL — `Unresolved reference: GenerationSchedule`.

- [ ] **Step 3: Implement it**

```kotlin
package org.phioster.glyphsmith.effects

/**
 * How a chain of JPEG re-encodings is paced.
 *
 * Pulled out of [JpegGlitch] so that it can be tested at all: the pass itself touches `Bitmap`,
 * which does not exist under the JVM test source set, and a loop whose only proof is a screenshot
 * is a loop nobody will dare change later.
 *
 * A falling quality is what makes the chain read as *generations* rather than as one hard
 * compression: each copy is taken from an already damaged copy, and the damage compounds.
 */
object GenerationSchedule {

    const val MAX_GENERATIONS = 24

    fun qualities(generations: Int, quality: Int, decay: Int): IntArray {
        val count = generations.coerceIn(1, MAX_GENERATIONS)
        val base = quality.coerceIn(1, 100)
        val step = decay.coerceAtLeast(0)
        return IntArray(count) { (base - step * it).coerceIn(1, 100) }
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*GenerationScheduleTest*'`
Expected: PASS, all five.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/effects/GenerationSchedule.kt \
        app/src/test/java/org/phioster/glyphsmith/effects/GenerationScheduleTest.kt
git commit -m "Der Ablaufplan einer Kopierkette, als pruefbare Funktion"
```

---

### Task 2: Loop the cycle

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/EffectParams.kt:113-121`
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/JpegGlitch.kt:30-55`
- Test: `app/src/test/java/org/phioster/glyphsmith/effects/JpegGlitchParamsTest.kt`

**Interfaces:**
- Consumes: `GenerationSchedule.qualities` from Task 1.
- Produces: `JpegGlitchParams.generations: Int = 1` and `JpegGlitchParams.decay: Int = 0`.

Two things must stay true, and both are easy to break:

1. **`corruption = 0` must still do something.** Today `apply` returns the source untouched when
   `corruption <= 0`, because with no corruption there was nothing to see. With generations that
   is no longer true — a zero-corruption chain is the pure recompression look, and it is the main
   reason to build this. The guard has to become `corruption <= 0 && generations <= 1`.
2. **The retry-on-failure logic is per generation.** `apply` already halves `corruption` up to
   `MAX_ATTEMPTS` times when the decode returns null. Each generation gets its own attempts, and a
   generation that cannot be decoded at all ends the chain and returns what it has — not the
   original.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.effects

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class JpegGlitchParamsTest {

    @Test
    fun `a preset saved before generations existed decodes to a single generation`() {
        val old = """{"enabled":true,"quality":30,"corruption":40,"startOffset":10,"seed":1}"""
        val params = Json { ignoreUnknownKeys = true }
            .decodeFromString(JpegGlitchParams.serializer(), old)
        assertEquals(1, params.generations)
        assertEquals(0, params.decay)
    }

    @Test
    fun `the default params are one generation, which is today's behaviour`() {
        assertEquals(1, JpegGlitchParams().generations)
        assertEquals(0, JpegGlitchParams().decay)
    }

    @Test
    fun `a chain with no corruption still has work to do`() {
        val params = JpegGlitchParams(enabled = true, corruption = 0, generations = 6)
        assertEquals(6, GenerationSchedule.qualities(params.generations, params.quality, params.decay).size)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*JpegGlitchParamsTest*'`
Expected: FAIL — `Unresolved reference: generations`.

- [ ] **Step 3: Add the fields and wrap the cycle in a loop**

In `EffectParams.kt`, on `JpegGlitchParams`:

```kotlin
    /** 1..24 — how many times the image is re-encoded, each pass feeding the next. */
    val generations: Int = 1,
    /** 0..20 — quality points lost per generation. 0 keeps the whole chain at one quality. */
    val decay: Int = 0,
```

In `JpegGlitch.apply`, change the early return to
`if (!params.enabled || (params.corruption <= 0 && params.generations <= 1)) return source`,
then wrap the existing compress/corrupt/decode block in a loop over
`GenerationSchedule.qualities(params.generations, params.quality, params.decay)`, feeding each
result into the next iteration and recycling intermediate bitmaps. Keep `restoreAlpha` at the
**end** of the chain only — running it per generation would keep re-introducing the original
alpha into an image that is meant to be decaying.

Update `randomise` to roll a short chain sometimes, e.g.
`generations = if (roll.random.nextInt(4) == 0) roll.random.nextInt(2, 6) else 1`.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*JpegGlitch*' --tests '*Effect*' --tests '*Preset*'`
Expected: PASS. The preset tests are the guard that old documents still render identically.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/effects/EffectParams.kt \
        app/src/main/java/org/phioster/glyphsmith/effects/JpegGlitch.kt \
        app/src/test/java/org/phioster/glyphsmith/effects/JpegGlitchParamsTest.kt
git commit -m "Die JPEG-Kette laeuft ueber mehrere Generationen"
```

---

### Task 3: The controls, and an honest check on device

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/EffectSections.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: `JpegGlitchParams.generations`, `.decay`.

Two sliders in the existing JPEG section: *generations* 1–24 and *decay* 0–20. Name the pass's
section so the chain is discoverable — someone looking for a VHS-copy look will not find it under
"JPEG databending".

Because no automated test can see the pixels (see the constraint above), this task ends with a
check by eye, and the check is part of the task rather than an afterthought:

- [ ] **Step 1: Add the two sliders**

Follow the shape of the existing quality and corruption sliders in the same section.

- [ ] **Step 2: Build and install**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Check the three cases by eye**

1. `generations = 1`, any corruption — must look **exactly** as it did before this plan. This is
   the regression check, and it is the one that matters.
2. `generations = 8`, `corruption = 0`, `decay = 4` — blocking and colour bleed should build up
   visibly with no byte corruption at all. This is the look the plan exists for.
3. `generations = 8`, `corruption = 60` — should be heavier than one generation at the same
   corruption, and must not hang or return a black frame.

- [ ] **Step 4: Update the README**

Under **Effects**, extend the JPEG databending sentence to say the pass can run as a chain, and
that with corruption at zero it is pure recompression decay.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/ui/panels/EffectSections.kt README.md
git commit -m "Regler fuer Generationen und Qualitaetsverfall"
```

---

## Notes for the reviewer

- **This plan must not change any existing output.** `generations = 1, decay = 0` is the default
  and is today's behaviour exactly. If case 1 in Task 3 looks different, something is wrong with
  the loop, not with the eye.
- **Watch the bitmap recycling.** The existing code recycles carefully because it is on the
  preview path. A 24-generation chain that leaks a bitmap per generation will be found by a user
  on a long video before it is found by a test.
- **24 is a guess.** It is a cap chosen to stop a slider producing a minute-long stall, not a
  number from anywhere. If it turns out to be too low to reach the look, raise it — the field is
  an `Int` and the schedule clamps.
