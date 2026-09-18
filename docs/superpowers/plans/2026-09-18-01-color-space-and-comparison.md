# Colour Space and Comparison — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate "a colour space you can travel back out of" from "a way of measuring how far
apart two colours are", then use the freedom that buys to grow three comparison metrics into
seven and add the Wu quantiser.

**Architecture:** `ColorDistance` today is two concepts in one enum: `coordsOf`/`rgbOf` form an
invertible space that `ColorDepth` uses to *modify* colours, while `distance` measures. The
measuring half is hard-wired to a straight line between three coordinates, which is why
luminance (not invertible) and CIE94/CIEDE2000 (not straight lines) cannot be added. Tasks 1–3
split the concepts without changing a single rendered pixel; tasks 4–9 add what the split makes
possible.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. No new
dependencies.

**Spec:** `docs/superpowers/plans/2026-09-18-reference-survey-spec.md`

## Global Constraints

From the spec §4. Every task's requirements implicitly include this section.

- No runtime plugins, no reflection, no `ServiceLoader`, no dynamic class loading.
- Stable wire ids are never changed once written; shape `category.name` (`core/serial/WireId.kt`).
- `PresetSchema.CURRENT_VERSION` is **4**. Raising it needs a `Migration` **and** a test decoding
  a literal old document. **This plan does not raise it** — see the note below.
- No migration may change how anything renders. Existing presets must look identical.
- `LayeringTest` enforces import direction. Everything here lives in `core/color` and `effects`,
  both already permitted.
- Do not weaken tests to make an implementation pass.
- No new dependencies.

### The serialisation rule that makes this plan cheap

`ColorDepthParams.colorSpace` is declared `ColorDistance` and serialises **by constant name** —
kotlinx.serialization's default for enums. Saved presets therefore contain the literal strings
`"EUCLIDEAN"`, `"CIELAB"` and `"OKLAB"`.

**The new `ColorSpace` enum uses those three names verbatim.** The JSON is then byte-identical
before and after, the schema version does not move, and no migration is written. This is the
whole reason the split is affordable.

`EUCLIDEAN` is a poor name for a *space* (it means "sRGB as stored"). Renaming it is deliberately
**not** part of this plan: `CLAUDE.md` requires renames to be separate, testable refactoring
steps, and a rename here would mean `@SerialName("EUCLIDEAN")` plus a migration test for no
visible gain. Task 1 records the wart in a comment instead.

## File Structure

| File | Responsibility |
| --- | --- |
| `core/color/ColorSpace.kt` | **new** — invertible spaces: `coordsOf`, `rgbOf`, and the sRGB/Lab/OKLab maths moved out of `ColorDistance` |
| `core/color/ColorDistance.kt` | **modified** — comparison only; each metric names its space and owns its `squared` |
| `core/color/PaletteQuantizer.kt` | **modified** — asks the metric for the distance instead of assuming a straight line |
| `core/color/WuQuantiser.kt` | **new** — variance-minimising palette extraction |
| `effects/ColorDepth.kt` | **modified** — `colorSpace` becomes a `ColorSpace` |
| `ui/panels/EffectSections.kt` | **modified** — the Color Depth picker lists spaces, the palette picker lists metrics |
| `data/PresetLibrary.kt` | **modified** — two literal `ColorDistance.OKLAB` become `ColorSpace.OKLAB` |

---

### Task 1: Extract the invertible space

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorSpace.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorSpaceTest.kt`

**Interfaces:**
- Produces: `enum class ColorSpace { EUCLIDEAN, CIELAB, OKLAB }` with
  `fun coordsOf(color: Int): FloatArray` and `fun rgbOf(coords: FloatArray): Int`.
- Produces: `ColorSpace.Companion.squaredBetween(a: FloatArray, b: FloatArray): Float` and
  `distanceBetween(a, b)` — moved verbatim from `ColorDistance.Companion`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSpaceTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Every space must be able to say where a colour is and then put it back. */
    @Test
    fun `every space round-trips an in-gamut colour`() {
        val samples = listOf(
            rgb(0, 0, 0), rgb(255, 255, 255), rgb(128, 128, 128),
            rgb(255, 0, 0), rgb(0, 128, 64), rgb(17, 34, 51),
        )
        for (space in ColorSpace.entries) {
            for (color in samples) {
                val back = space.rgbOf(space.coordsOf(color))
                assertEquals("$space on ${Integer.toHexString(color)}", color, back)
            }
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorSpaceTest*'`
Expected: FAIL — `Unresolved reference: ColorSpace`.

- [ ] **Step 3: Create `ColorSpace` by moving the maths across**

Create `core/color/ColorSpace.kt`. Move these members out of `ColorDistance` **unchanged**:
`coordsOf`, `rgbOf`, `squaredBetween`, `distanceBetween`, `linear`, `encoded`, `XN`/`YN`/`ZN`,
`labOf`, `pivot`, `unpivot`, `fromLab`, `okLabOf`, `fromOkLab`, `fromLinear`, `pack`.

```kotlin
package org.phioster.glyphsmith.core.color

import kotlinx.serialization.Serializable
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A set of coordinates a colour can be expressed in **and brought back from**.
 *
 * This is the half of the old `ColorDistance` that anything *modifying* a colour needs:
 * quantising lightness has to travel out into a perceptual space and return. Comparison never
 * needed a way back, and the two were one enum only because the first three metrics happened to
 * be invertible. Luminance is not, and that is what forced them apart.
 *
 * The constant names are the ones saved presets already contain, and they do not change.
 * [EUCLIDEAN] is a poor name for a space — it means "sRGB as stored" and says how it is measured
 * rather than where it lives — but renaming it is a wire-format change for a cosmetic gain, and
 * `CLAUDE.md` wants renames as their own step.
 */
@Serializable
enum class ColorSpace {
    /** sRGB as stored, 0..255 per channel. Cheapest, and perceptually non-uniform. */
    EUCLIDEAN,

    /** CIE L\*a\*b\*: L\* runs 0..100, both chroma axes are signed and roughly ±128. */
    CIELAB,

    /** OKLab: L runs 0..1. Fixes the blue-region and hue-linearity faults of L\*a\*b\*. */
    OKLAB,
    ;

    fun coordsOf(color: Int): FloatArray { /* moved verbatim */ }

    fun rgbOf(coords: FloatArray): Int { /* moved verbatim */ }

    companion object {
        fun squaredBetween(a: FloatArray, b: FloatArray): Float { /* moved verbatim */ }
        fun distanceBetween(a: FloatArray, b: FloatArray): Float = sqrt(squaredBetween(a, b))
        // …all private helpers moved verbatim…
    }
}
```

In `ColorDistance.kt`, delete the moved members and delegate, keeping every public signature that
exists today so nothing else has to change yet:

```kotlin
@Serializable
enum class ColorDistance {
    EUCLIDEAN,
    CIELAB,
    OKLAB,
    ;

    /** The space this metric measures in. */
    val space: ColorSpace
        get() = when (this) {
            EUCLIDEAN -> ColorSpace.EUCLIDEAN
            CIELAB -> ColorSpace.CIELAB
            OKLAB -> ColorSpace.OKLAB
        }

    fun coordsOf(color: Int): FloatArray = space.coordsOf(color)

    fun rgbOf(coords: FloatArray): Int = space.rgbOf(coords)

    fun distance(a: Int, b: Int): Float =
        ColorSpace.distanceBetween(coordsOf(a), coordsOf(b))

    companion object {
        fun squaredBetween(a: FloatArray, b: FloatArray): Float =
            ColorSpace.squaredBetween(a, b)

        fun distanceBetween(a: FloatArray, b: FloatArray): Float =
            ColorSpace.distanceBetween(a, b)
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorSpaceTest*' --tests '*ColorDistanceTest*' --tests '*LabRoundTripTest*' --tests '*ColorDepthTest*'`
Expected: PASS, all four. `ColorDistanceTest` and `LabRoundTripTest` are untouched and must stay
green — that is the proof the move changed nothing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorSpace.kt \
        app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorSpaceTest.kt
git commit -m "Trenne den umkehrbaren Farbraum von der Vergleichsmetrik"
```

---

### Task 2: Point everything that *modifies* a colour at `ColorSpace`

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/ColorDepth.kt:23,55,69,86,91`
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/EffectSections.kt:860-861`
- Modify: `app/src/main/java/org/phioster/glyphsmith/data/PresetLibrary.kt:874,984`
- Test: `app/src/test/java/org/phioster/glyphsmith/effects/ColorDepthPresetCompatTest.kt`

**Interfaces:**
- Consumes: `ColorSpace` from Task 1.
- Produces: `ColorDepthParams.colorSpace: ColorSpace` — same JSON, different Kotlin type.

This is the task that makes the split real. `ColorDepth` already *calls its field* `colorSpace`;
only the type was wrong. Note `ColorDepth.kt:55` — `randomise` currently picks
`ColorDistance.entries.random(roll.random)`. Once metrics outgrow spaces that roll could hand a
non-invertible metric to a pass that must invert. Repointing it at `ColorSpace.entries` closes
that hole before it opens.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.effects

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.core.color.ColorSpace

class ColorDepthPresetCompatTest {

    /**
     * A preset saved before the space/metric split must still decode, and must still name the
     * same space. The literal below is what schema 4 writes today.
     */
    @Test
    fun `a pre-split color depth preset decodes unchanged`() {
        val saved = """{"enabled":true,"colorLevels":8,"colorSpace":"OKLAB"}"""
        val params = Json { ignoreUnknownKeys = true }
            .decodeFromString(ColorDepthParams.serializer(), saved)
        assertEquals(ColorSpace.OKLAB, params.colorSpace)
    }

    @Test
    fun `the three pre-split space names all still decode`() {
        for (name in listOf("EUCLIDEAN", "CIELAB", "OKLAB")) {
            val params = Json { ignoreUnknownKeys = true }.decodeFromString(
                ColorDepthParams.serializer(),
                """{"enabled":true,"colorSpace":"$name"}""",
            )
            assertEquals(name, params.colorSpace.name)
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDepthPresetCompatTest*'`
Expected: FAIL — `colorSpace` is still a `ColorDistance`, so `assertEquals(ColorSpace.OKLAB, …)`
does not compile.

- [ ] **Step 3: Change the type at all six sites**

`effects/ColorDepth.kt`:

```kotlin
    val colorSpace: ColorSpace = ColorSpace.EUCLIDEAN,
```

```kotlin
                colorSpace = ColorSpace.entries.random(roll.random),
```

`axesFor(params.colorSpace)` takes `ColorSpace` now; change its parameter type and its `when`.
`ui/panels/EffectSections.kt:860-861`:

```kotlin
            selectedIndex = ColorSpace.entries.indexOf(params.colorSpace),
            onSelect = { onChange(params.copy(colorSpace = ColorSpace.entries[it])) },
```

`data/PresetLibrary.kt:874` and `:984`: `colorSpace = ColorSpace.OKLAB,`. Fix the imports in all
three files.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDepth*' --tests '*PresetLibrary*' --tests '*Migration*'`
Expected: PASS. The compat test is the one that matters; the preset-library tests prove the
shipped presets still build.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/effects/ColorDepth.kt \
        app/src/main/java/org/phioster/glyphsmith/ui/panels/EffectSections.kt \
        app/src/main/java/org/phioster/glyphsmith/data/PresetLibrary.kt \
        app/src/test/java/org/phioster/glyphsmith/effects/ColorDepthPresetCompatTest.kt
git commit -m "Color Depth arbeitet in einem Farbraum, nicht in einer Metrik"
```

---

### Task 3: Let a metric own its own distance

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/PaletteQuantizer.kt:46`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt` (extend)

**Interfaces:**
- Produces: `ColorDistance.squared(a: FloatArray, b: FloatArray): Float` — an **instance** method.
  Tasks 4–7 override it. Coordinates passed in are always in `this.space`.
- Produces: `ColorDistance.space: ColorSpace` (already added in Task 1).

`PaletteQuantizer` calls the *companion* `ColorDistance.squaredBetween`, which bakes the
straight-line assumption into the caller. Every non-Euclidean metric in tasks 6–7 needs that
decision to belong to the metric.

- [ ] **Step 1: Write the failing test**

```kotlin
    /** A metric must measure with its own rule, not with a straight line chosen by its caller. */
    @Test
    fun `squared agrees with distance in every metric`() {
        for (metric in ColorDistance.entries) {
            val a = metric.coordsOf(red)
            val b = metric.coordsOf(blue)
            val viaSquared = kotlin.math.sqrt(metric.squared(a, b))
            assertEquals(metric.name, metric.distance(red, blue), viaSquared, 1e-3f)
        }
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDistanceTest*'`
Expected: FAIL — `Unresolved reference: squared`.

- [ ] **Step 3: Add the instance method and route the quantiser through it**

In `ColorDistance`:

```kotlin
    /**
     * Squared distance between two coordinate triples **in this metric's space**.
     *
     * Open per constant rather than fixed, because not every metric is a straight line: CIE94
     * weights the chroma and hue terms, and CIEDE2000 rotates them. Squared rather than rooted
     * because a nearest search never needs the root — it changes no ordering — and this runs
     * once per palette entry per distinct source colour.
     */
    fun squared(a: FloatArray, b: FloatArray): Float = when (this) {
        EUCLIDEAN, CIELAB, OKLAB -> ColorSpace.squaredBetween(a, b)
    }

    fun distance(a: Int, b: Int): Float = sqrt(squared(coordsOf(a), coordsOf(b)))
```

In `PaletteQuantizer.kt:46`:

```kotlin
            val d = metric.squared(target, coords[i])
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*core.color*' --tests '*RenderModeTest*'`
Expected: PASS. Nothing renders differently — the `when` returns exactly what the companion did.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/main/java/org/phioster/glyphsmith/core/color/PaletteQuantizer.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt
git commit -m "Die Metrik entscheidet, wie gemessen wird, nicht der Aufrufer"
```

---

### Task 4: Luminance — the metric that proves the split

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt` (extend)

**Interfaces:**
- Produces: `ColorDistance.LUMINANCE`, whose `space` is `ColorSpace.CIELAB` and whose `squared`
  reads only coordinate 0 (L\*).

Luminance is the metric that could not exist before: it collapses three channels onto one and has
no inverse. It reaches into `ColorSpace.CIELAB` for coordinates and simply ignores two of them —
L\* is already a perceptual lightness, so no new maths is needed. libdither's README records that
luminance distance "works best for gradients", which is the case to test.

Note for whoever reviews: `ColorDistanceTest` has three enum-wide invariant tests (self-distance
zero, symmetry, half a grey ramp shorter than all of it). Luminance satisfies all three. Do not
weaken them.

- [ ] **Step 1: Write the failing test**

```kotlin
    /**
     * Luminance must not distinguish two colours of equal lightness, which is the entire point
     * of it — and is exactly what the perceptual metrics must still do.
     */
    @Test
    fun `luminance ignores hue where oklab does not`() {
        val lab = ColorSpace.CIELAB
        val olive = ColorDistance.LUMINANCE.coordsOf(rgb(128, 128, 0))
        // A grey of the same L*, built by zeroing the chroma axes.
        val grey = lab.rgbOf(floatArrayOf(olive[0], 0f, 0f))

        assertEquals(0f, ColorDistance.LUMINANCE.distance(rgb(128, 128, 0), grey), 0.5f)
        assertTrue(
            "oklab must still see the difference luminance is blind to",
            ColorDistance.OKLAB.distance(rgb(128, 128, 0), grey) > 0.01f,
        )
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDistanceTest*'`
Expected: FAIL — `Unresolved reference: LUMINANCE`.

- [ ] **Step 3: Add the constant**

```kotlin
    /**
     * Lightness alone: two colours of equal L\* are the same colour to this metric.
     *
     * It has no space of its own and borrows [ColorSpace.CIELAB]'s, reading coordinate 0 and
     * discarding the chroma axes. That discarding is why it could not be a [ColorSpace]: three
     * numbers go in and one comes out, so there is no way back, and a pass that modifies a
     * colour would have turned the picture grey.
     *
     * Best on gradients, where a palette's job is to preserve the ramp rather than the hue.
     */
    LUMINANCE,
```

with `space` returning `ColorSpace.CIELAB` for it, and:

```kotlin
    fun squared(a: FloatArray, b: FloatArray): Float = when (this) {
        EUCLIDEAN, CIELAB, OKLAB -> ColorSpace.squaredBetween(a, b)
        LUMINANCE -> {
            val d = a[0] - b[0]
            d * d
        }
    }
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*core.color*'`
Expected: PASS, including the three enum-wide invariants now running over four metrics.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt
git commit -m "Luminanz als Metrik, die keinen Rueckweg braucht"
```

---

### Task 5: HSV, with hue as a plane

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorSpace.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorSpaceTest.kt` (extend)

**Interfaces:**
- Produces: `ColorSpace.HSV` — invertible, so it is a real space and Color Depth can use it.
- Produces: `ColorDistance.HSV`, whose `space` is `ColorSpace.HSV` and whose `squared` is the
  plain straight line in that space.

The design point: hue is **circular**. Stored as a linear axis, red at 0° and magenta at 350°
read as maximally distant when they are neighbours. Stored as a plane — `x = s·cos h`,
`y = s·sin h`, `z = v` — the wrap disappears, saturation falls out as the radius, and the
triple stays invertible via `h = atan2(y, x)`, `s = hypot(x, y)`, `v = z`. That fits the existing
three-coordinate contract exactly, which is why HSV can be a space and luminance cannot.

- [ ] **Step 1: Write the failing test**

```kotlin
    /** Hue wraps. A plane encoding is the only way three coordinates can say so. */
    @Test
    fun `hsv puts neighbouring hues next to each other across the wrap`() {
        val red = rgb(255, 0, 0)          // hue 0
        val magentaish = rgb(255, 0, 40)  // hue ~351
        val green = rgb(0, 255, 0)        // hue 120

        val across = ColorDistance.HSV.distance(red, magentaish)
        val far = ColorDistance.HSV.distance(red, green)
        assertTrue("a 9-degree step must beat a 120-degree one, got $across vs $far", across < far)
    }

    @Test
    fun `hsv round-trips like every other space`() {
        for (color in listOf(rgb(255, 0, 0), rgb(10, 200, 90), rgb(0, 0, 0), rgb(255, 255, 255))) {
            assertEquals(color, ColorSpace.HSV.rgbOf(ColorSpace.HSV.coordsOf(color)))
        }
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorSpaceTest*' --tests '*ColorDistanceTest*'`
Expected: FAIL — `Unresolved reference: HSV`.

- [ ] **Step 3: Add the space and the metric**

In `ColorSpace`, add the constant and its two branches:

```kotlin
    /**
     * Hue, saturation and value — with hue carried as a **plane** rather than an axis.
     *
     * `x = s·cos h`, `y = s·sin h`, `z = v`. Hue is circular, and three linear coordinates cannot
     * say so: stored as an axis, a red at 0° and a red-violet at 350° come out maximally far
     * apart though they are neighbours. On the plane the wrap is gone, the radius *is* the
     * saturation, and the triple still inverts, so this is a real space and not merely a metric.
     */
    HSV,
```

```kotlin
        private fun hsvOf(color: Int): FloatArray {
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val span = max - min
            val h = when {
                span == 0f -> 0f
                max == r -> ((g - b) / span).mod(6f)
                max == g -> (b - r) / span + 2f
                else -> (r - g) / span + 4f
            } * (PI.toFloat() / 3f)
            val s = if (max == 0f) 0f else span / max
            return floatArrayOf(s * cos(h), s * sin(h), max)
        }

        private fun fromHsv(coords: FloatArray): Int {
            val s = hypot(coords[0], coords[1]).coerceIn(0f, 1f)
            val v = coords[2].coerceIn(0f, 1f)
            val h = atan2(coords[1], coords[0]).let { if (it < 0f) it + 2f * PI.toFloat() else it }
            val sector = h / (PI.toFloat() / 3f)
            val i = sector.toInt() % 6
            val f = sector - sector.toInt()
            val p = v * (1f - s)
            val q = v * (1f - s * f)
            val t = v * (1f - s * (1f - f))
            val (r, g, b) = when (i) {
                0 -> Triple(v, t, p)
                1 -> Triple(q, v, p)
                2 -> Triple(p, v, t)
                3 -> Triple(p, q, v)
                4 -> Triple(t, p, v)
                else -> Triple(v, p, q)
            }
            return pack(r * 255f, g * 255f, b * 255f)
        }
```

Add `import kotlin.math.PI`, `cos`, `sin`, `atan2`, `hypot`. Wire both into the `when`s in
`coordsOf` and `rgbOf`.

In `ColorDistance`, add `HSV` with `space = ColorSpace.HSV` and fold it into the straight-line
branch of `squared`.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*core.color*' --tests '*ColorDepth*'`
Expected: PASS. `ColorSpaceTest`'s round-trip now covers four spaces and `ColorDepth` gains HSV
in its picker for free, because it reads `ColorSpace.entries`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorSpace.kt \
        app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorSpaceTest.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt
git commit -m "HSV mit dem Farbton als Ebene statt als Achse"
```

---

### Task 6: CIE94

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt` (extend)

**Interfaces:**
- Produces: `ColorDistance.CIE94`, space `ColorSpace.CIELAB`, non-Euclidean `squared`.

Primary source: CIE Publication 116-1995, graphic-arts weights (`kL = 1`, `K1 = 0.045`,
`K2 = 0.015`). The formula, with both colours' L\*a\*b\* in hand:

```
ΔL = L₁ − L₂
C₁ = √(a₁² + b₁²)          C₂ = √(a₂² + b₂²)
ΔC = C₁ − C₂
ΔH² = (a₁−a₂)² + (b₁−b₂)² − ΔC²     (clamped at 0; rounding can push it slightly negative)
ΔE² = (ΔL/kL)² + (ΔC/(1 + K1·C₁))² + (ΔH²/(1 + K2·C₁)²)
```

Note the asymmetry: `C₁` is the *reference* colour's chroma, so `ΔE(a,b) ≠ ΔE(b,a)` in the
standard. `ColorDistanceTest` has an enum-wide symmetry invariant and it must not be weakened —
so this implementation uses `min(C₁, C₂)` as the reference, which is the usual choice for
nearest-colour search and restores symmetry. State that in the comment; it is a deliberate
divergence from the letter of the standard, made for a reason.

- [ ] **Step 1: Write the failing test**

```kotlin
    /**
     * CIE94's whole purpose: at equal raw Lab separation, a difference among saturated colours
     * matters less than the same difference among near-neutrals.
     */
    @Test
    fun `cie94 discounts a chroma step more the more saturated the pair is`() {
        val lab = ColorSpace.CIELAB
        fun pair(chroma: Float): Float {
            val a = lab.rgbOf(floatArrayOf(60f, chroma, 0f))
            val b = lab.rgbOf(floatArrayOf(60f, chroma + 10f, 0f))
            return ColorDistance.CIE94.distance(a, b)
        }
        assertTrue("saturated pairs must read closer than neutral ones", pair(60f) < pair(2f))
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDistanceTest*'`
Expected: FAIL — `Unresolved reference: CIE94`.

- [ ] **Step 3: Implement it**

```kotlin
    CIE94,
```

```kotlin
        CIE94 -> {
            val dl = a[0] - b[0]
            val c1 = hypot(a[1], a[2])
            val c2 = hypot(b[1], b[2])
            val dc = c1 - c2
            val da = a[1] - b[1]
            val db = a[2] - b[2]
            val dh2 = (da * da + db * db - dc * dc).coerceAtLeast(0f)
            // The standard divides by the *reference* colour's chroma, which makes it asymmetric.
            // A nearest search has no reference — either colour could be the palette entry — so
            // the smaller chroma is used and the metric stays symmetric.
            val cRef = minOf(c1, c2)
            val sc = 1f + 0.045f * cRef
            val sh = 1f + 0.015f * cRef
            dl * dl + (dc * dc) / (sc * sc) + dh2 / (sh * sh)
        }
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*core.color*'`
Expected: PASS, symmetry invariant included.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceTest.kt
git commit -m "CIE94, symmetrisch gemacht fuer die Naechste-Farbe-Suche"
```

---

### Task 7: CIEDE2000

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/Ciede2000Test.kt`

**Interfaces:**
- Produces: `ColorDistance.CIEDE2000`, space `ColorSpace.CIELAB`.

Primary source: Sharma, Wu and Dalal, *The CIEDE2000 Color-Difference Formula*, Color Research &
Application 30(1), 2005 — which includes the **34-pair test table** the implementation is checked
against. That table is the reason this task is affordable: CIEDE2000 has a hue-rotation term and
a discontinuity near hue 0/360 that are notoriously easy to get subtly wrong, and guessing is not
an option. Do not implement from memory; work from the paper's equations (1)–(24).

This is the most intricate maths in the plan. Budget accordingly, and if the Sharma table cannot
be obtained, **stop and report** rather than shipping an unverified formula — an unverified
colour metric is worse than no metric, because it fails silently and beautifully.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sharma, Wu & Dalal (2005), Table 1 — the reference pairs the formula is defined against.
 * Six of the thirty-four, chosen for the corners: the hue-rotation term, the wrap near 0/360,
 * and a neutral pair. Add the rest if a failure needs narrowing down.
 */
class Ciede2000Test {

    private fun dE(l1: Float, a1: Float, b1: Float, l2: Float, a2: Float, b2: Float): Float =
        kotlin.math.sqrt(
            ColorDistance.CIEDE2000.squared(
                floatArrayOf(l1, a1, b1),
                floatArrayOf(l2, a2, b2),
            ),
        )

    @Test
    fun `matches the reference pairs from the paper`() {
        assertEquals(2.0425f, dE(50f, 2.6772f, -79.7751f, 50f, 0f, -82.7485f), 1e-3f)
        assertEquals(2.8615f, dE(50f, 3.1571f, -77.2803f, 50f, 0f, -82.7485f), 1e-3f)
        assertEquals(1.0000f, dE(50f, 2.4900f, -0.0010f, 50f, -2.4900f, 0.0009f), 1e-3f)
        assertEquals(2.3669f, dE(50f, -1.3802f, -84.2814f, 50f, 0f, -82.7485f), 1e-3f)
        assertEquals(0.0000f, dE(50f, 0f, 0f, 50f, 0f, 0f), 1e-3f)
        assertEquals(4.8045f, dE(50f, 2.5f, 0f, 61f, -5f, 29f), 1e-3f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*Ciede2000Test*'`
Expected: FAIL — `Unresolved reference: CIEDE2000`.

- [ ] **Step 3: Implement from the paper**

Add the constant, and a private `companion` helper `ciede2000Squared(a, b)` implementing
equations (1)–(24) with `kL = kC = kH = 1`. Route `squared` to it. The three details that break
naïve implementations, all of which the test above targets:

- `h'` must be `atan2(b', a')` normalised into `[0, 360)`, and **defined as 0 when both `a'` and
  `b'` are zero** rather than left to `atan2`.
- `Δh'` selects among `h'₂ − h'₁`, `±360` by which magnitude is `≤ 180`; the mean hue `H̄'` has
  its own separate wrap rule, and they are **not** the same rule.
- `RT` is negative, and it is the term the `(50, 2.49, -0.001)` pair exists to catch.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Ciede2000Test*' --tests '*core.color*'`
Expected: PASS — all six reference pairs to 1e-3, and the enum-wide invariants over seven metrics.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/Ciede2000Test.kt
git commit -m "CIEDE2000 gegen die Referenztabelle aus dem Paper"
```

---

### Task 8: The Wu quantiser

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/color/WuQuantiser.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/WuQuantiserTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks — it extracts a palette, it does not measure.
- Produces: `object WuQuantiser { fun palette(pixels: IntArray, size: Int): IntArray }`, returning
  at most `size` opaque colours.

Primary source: Xiaolin Wu, *Efficient Statistical Computations for Optimal Color Quantization*,
Graphics Gems II, 1991. The method: build a 32×32×32 histogram with cumulative moments, then
repeatedly split the box whose split most reduces total variance. Where median-cut splits at the
median of the longest axis regardless of what that does to the error, Wu splits where the error
actually falls, which is why it usually wins at the same cost class.

Find where the existing median-cut quantiser is invoked and offer Wu alongside it rather than
replacing it — `CLAUDE.md` forbids deleting existing behaviour.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WuQuantiserTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `an image of four colours quantises to those four colours`() {
        val source = intArrayOf(rgb(255, 0, 0), rgb(0, 255, 0), rgb(0, 0, 255), rgb(255, 255, 0))
        val palette = WuQuantiser.palette(source.copyOf(), 4).toSet()
        assertEquals(4, palette.size)
        for (c in source) {
            assertTrue(
                "expected ${Integer.toHexString(c)} in the palette",
                palette.any { ColorDistance.OKLAB.distance(it, c) < 0.05f },
            )
        }
    }

    @Test
    fun `it never returns more colours than asked for`() {
        val noisy = IntArray(4096) { rgb(it % 256, (it / 3) % 256, (it / 7) % 256) }
        assertTrue(WuQuantiser.palette(noisy, 16).size <= 16)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*WuQuantiserTest*'`
Expected: FAIL — `Unresolved reference: WuQuantiser`.

- [ ] **Step 3: Implement it**

Build `WuQuantiser` with a 33×33×33 cumulative-moment array (the extra index is the standard
1-based trick that makes a box sum a signed sum of eight corners rather than a loop), a `Box`
holding its bounds and its weight, and a loop that splits the box with the greatest variance
reduction until `size` boxes exist. Each box's colour is its weighted mean.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*WuQuantiserTest*'`
Expected: PASS, both.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/core/color/WuQuantiser.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/WuQuantiserTest.kt
git commit -m "Wu-Quantisierung neben Median-Cut"
```

---

### Task 9: Surface the metrics and write down what they are for

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceLabelTest.kt`

**Interfaces:**
- Consumes: every metric from tasks 4–7, `ColorSpace` from task 1.
- Produces: `ColorDistance.label: String` — the picker's text, separate from `name`, because
  `name` is the wire value and must never be reworded.

A metric nobody can choose is not a feature. The palette picker in `RenderPanel.kt` currently
lists `ColorDistance.entries`, so the new ones appear automatically — but they appear as
`CIEDE2000` and `LUMINANCE`, shouted in enum case.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertTrue
import org.junit.Test

class ColorDistanceLabelTest {

    @Test
    fun `every metric has a label that is not its wire name`() {
        for (metric in ColorDistance.entries) {
            assertTrue("${metric.name} has no label", metric.label.isNotBlank())
            assertTrue("${metric.name} shows its wire name", metric.label != metric.name)
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorDistanceLabelTest*'`
Expected: FAIL — `Unresolved reference: label`.

- [ ] **Step 3: Add labels and update the two documents**

```kotlin
    val label: String
        get() = when (this) {
            EUCLIDEAN -> "sRGB"
            CIELAB -> "CIE Lab"
            OKLAB -> "OKLab"
            LUMINANCE -> "luminance"
            HSV -> "HSV"
            CIE94 -> "CIE94"
            CIEDE2000 -> "CIEDE2000"
        }
```

Point `RenderPanel.kt`'s picker at `.label`. In `README.md`, replace "Three distance metrics
decide which palette entry a colour is nearest, OKLab by default" with the seven, and say what
each is for in one clause. In `ARCHITECTURE.md`, add the space/comparison split to the table that
already records `PaletteProvider` "carries data" against `EffectProvider` "carries execution".

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — the whole suite, this being the last task.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt \
        app/src/main/java/org/phioster/glyphsmith/core/color/ColorDistance.kt \
        app/src/test/java/org/phioster/glyphsmith/core/color/ColorDistanceLabelTest.kt \
        README.md ARCHITECTURE.md
git commit -m "Sieben Metriken im Bedienfeld, mit lesbaren Namen"
```

---

## Notes for the reviewer

- **Tasks 1–3 must not change a rendered pixel.** If any existing test changes its expected
  value in those three tasks, the refactor went wrong — reject rather than update the test.
- **Task 7 is allowed to stop.** If the Sharma reference table cannot be obtained, CIEDE2000 is
  dropped and the plan still delivers six metrics. An unverified colour-difference formula is
  worse than none.
- The enum-wide invariants in `ColorDistanceTest` are the safety net for every metric added here.
  They are the reason tasks 4–7 are short. Do not weaken them to make a metric pass; a metric
  that cannot satisfy symmetry or self-distance-zero is wrong, not inconvenient.
