# Ridgeline (Joyplot) Render Mode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A fourth render mode that reads each row of the sampled grid as a curve and stacks the
curves up the frame — the *Unknown Pleasures* figure — with hidden-line removal, and exports as
real vector rather than as a picture of lines.

**Architecture:** The first three modes all turn a quantised *level* into something: a colour, a
character, or a colour then a character. This one does not quantise at all. It reads row
brightness as a **height** and emits polylines. That makes it the first mode whose natural output
is vector, and the first that produces an `.svg` without producing a `.txt` — which is why Task 2
splits a capability the provider currently conflates.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. No new
dependencies.

**Spec:** `docs/superpowers/plans/2026-09-18-reference-survey-spec.md` §2.6

## Global Constraints

From the spec §4.

- Wire ids never change once written. The new id is `render.ridgeline`.
- `PresetSchema.CURRENT_VERSION` is **4**. A new enum constant with a new id and new settings
  fields with defaults do **not** raise it. **Do not raise the version.**
- An unknown id is refused, not remapped. A preset naming `render.ridgeline` on an older build is
  dropped, which is correct.
- No migration may change how anything renders. The three existing modes must be untouched.
- `LayeringTest` enforces import direction: **shared render infrastructure must not depend on
  glyph-specific classes**, and this mode is shared infrastructure, not glyph art. It must not
  import from `glyph`.
- Do not weaken tests to make an implementation pass.
- No new dependencies.

### What the compiler will do for you

`RenderMode.isGlyph` and `ditherFirst` are deliberately written as exhaustive `when`s rather than
as negations. `CLAUDE.md` explains why: "a fourth one would have been glyph art the moment it was
declared, without anybody deciding that, and the first sign would have been text exports offered
for a render that has no characters in it."

So adding the constant in Task 3 **will not compile** until every such `when` states what the new
mode produces. That is the design working. Do not add an `else` branch to make it build.

## File Structure

| File | Responsibility |
| --- | --- |
| `render/ridgeline/Ridgeline.kt` | **new** — pure geometry: a grid of brightnesses to a list of polylines |
| `render/ridgeline/RidgelineRenderer.kt` | **new** — draws those polylines to `Pixels` |
| `render/RenderMode.kt` | **modified** — the constant, plus the two exhaustive `when`s |
| `render/RenderModeIds.kt` | **modified** — `render.ridgeline` |
| `render/RenderModuleProviders.kt` | **modified** — the provider, and the capability split |
| `export/Exports.kt` | **modified** — SVG for a vector mode, `.txt`/`.ansi` still glyph-only |
| `render/RenderSettings.kt` | **modified** — five ridgeline fields |
| `ui/panels/RenderPanel.kt` | **modified** — the controls |

---

### Task 1: The geometry, with nothing Android in it

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/render/ridgeline/Ridgeline.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/render/ridgeline/RidgelineTest.kt`

**Interfaces:**
- Produces: `data class RidgePoint(val x: Float, val y: Float)`
- Produces: `data class Ridge(val row: Int, val points: List<RidgePoint>)`
- Produces:
  ```kotlin
  object Ridgeline {
      fun build(
          values: FloatArray, width: Int, height: Int,
          rows: Int, amplitude: Float, overlap: Float, baseline: Boolean,
      ): List<Ridge>
  }
  ```
  `values` is row-major brightness in 0..1. Output is in a 0..1 × 0..1 unit square, top-left
  origin, so the renderer and the SVG writer scale it independently and cannot disagree.

Design points, each with a test below:

- **`rows` is independent of `height`.** A 1080-row image drawn as 1080 curves is a grey smear;
  40 is a picture. Rows are averaged into bands.
- **`amplitude` may exceed the band spacing, and should.** Curves overlapping their neighbours is
  the whole look. `overlap` says how far a curve may reach into the bands above it.
- **Hidden-line removal is what makes it read as depth.** A curve is drawn front-to-back and
  clipped against the silhouette accumulated from the rows already drawn — the nearest (bottom)
  row first. Without it the image is a tangle.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.render.ridgeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidgelineTest {

    private fun flat(value: Float, w: Int, h: Int) = FloatArray(w * h) { value }

    @Test
    fun `it emits exactly the number of rows asked for`() {
        val ridges = Ridgeline.build(flat(0.5f, 64, 64), 64, 64, rows = 8, 0.5f, 1f, false)
        assertEquals(8, ridges.size)
        assertEquals(0, ridges.first().row)
        assertEquals(7, ridges.last().row)
    }

    @Test
    fun `every point stays inside the unit square`() {
        val values = FloatArray(64 * 64) { (it % 64) / 63f }
        for (ridge in Ridgeline.build(values, 64, 64, 12, 2f, 1f, false)) {
            for (p in ridge.points) {
                assertTrue("x=${p.x}", p.x in 0f..1f)
                assertTrue("y=${p.y}", p.y in 0f..1f)
            }
        }
    }

    @Test
    fun `a brighter row rises further than a darker one`() {
        val values = FloatArray(16 * 4)
        for (i in 0 until 16) values[i] = 0.1f              // row 0, dark
        for (i in 48 until 64) values[i] = 0.9f             // row 3, bright
        val ridges = Ridgeline.build(values, 16, 4, rows = 4, amplitude = 1f, 1f, false)
        val darkRise = ridges[0].points.minOf { it.y }
        val brightRise = ridges[3].points.minOf { it.y }
        // y grows downward, so a bigger rise is a smaller y relative to its own baseline.
        val darkBase = ridges[0].points.maxOf { it.y }
        val brightBase = ridges[3].points.maxOf { it.y }
        assertTrue(
            "bright row must depart further from its baseline",
            (brightBase - brightRise) > (darkBase - darkRise),
        )
    }

    @Test
    fun `hidden line removal shortens a row standing behind a taller one`() {
        val values = FloatArray(16 * 2)
        for (i in 0 until 16) values[i] = 0.2f    // back row, low
        for (i in 16 until 32) values[i] = 1f     // front row, tall
        val clipped = Ridgeline.build(values, 16, 2, rows = 2, amplitude = 2f, 1f, false)
        val unclipped = Ridgeline.build(values, 16, 2, rows = 2, amplitude = 0.05f, 1f, false)
        val backClipped = clipped.first { it.row == 0 }.points.size
        val backWhole = unclipped.first { it.row == 0 }.points.size
        assertTrue(
            "a tall front row must hide part of the row behind it ($backClipped vs $backWhole)",
            backClipped < backWhole,
        )
    }

    @Test
    fun `a flat image gives flat curves`() {
        val ridges = Ridgeline.build(flat(0.5f, 32, 32), 32, 32, 4, 1f, 1f, false)
        for (ridge in ridges) {
            val ys = ridge.points.map { it.y }
            assertEquals("row ${ridge.row} must be level", ys.min(), ys.max(), 1e-5f)
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RidgelineTest*'`
Expected: FAIL — `Unresolved reference: Ridgeline`.

- [ ] **Step 3: Implement it**

Average the source rows into `rows` bands; map each band's brightness to a y-offset from that
band's baseline; walk the rows **bottom-first**, keeping a per-column running minimum y as the
silhouette, and drop points at or below it, splitting a ridge into several `Ridge` entries where
it disappears and reappears. `baseline = true` closes each curve down to its own baseline, which
is the record-sleeve look; `false` leaves open curves.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RidgelineTest*'`
Expected: PASS, all five.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/render/ridgeline/Ridgeline.kt \
        app/src/test/java/org/phioster/glyphsmith/render/ridgeline/RidgelineTest.kt
git commit -m "Zeilen werden zu gestapelten Kurven, mit verdeckten Linien"
```

---

### Task 2: Split "makes glyphs" from "makes vector"

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderModuleProviders.kt:19`
- Modify: `app/src/main/java/org/phioster/glyphsmith/export/Exports.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/render/RenderCapabilityTest.kt`

**Interfaces:**
- Produces: `RenderModuleProvider.producesVector: Boolean`, alongside the existing
  `producesGlyphs`.

`producesGlyphs` is currently documented as gating "a `.txt`, `.svg` or `.ansi`" — three formats
behind one capability, because until now every vector export happened to come from characters.
Ridgeline breaks that: it produces `.svg` and has no characters at all. This is the same shape as
the colour split in Plan 01, and for the same reason — two ideas sharing one flag until something
arrived that needed only one of them.

Do this **before** adding the mode, so the capability change is reviewed on its own and the three
existing modes demonstrably keep every export they have.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.render

import org.junit.Assert.assertTrue
import org.junit.Test

class RenderCapabilityTest {

    @Test
    fun `every glyph mode keeps all three text-shaped exports`() {
        for (provider in RenderModules.all.filter { it.producesGlyphs }) {
            assertTrue("${provider.id} lost its vector export", provider.producesVector)
        }
    }

    @Test
    fun `pixel dither offers neither`() {
        val pixel = RenderModules.of(RenderMode.PurePixel)
        assertTrue(!pixel.producesGlyphs)
        assertTrue(!pixel.producesVector)
    }
}
```

`RenderModules.of(mode)` is a map lookup and `RenderModules.all` comes from `Registry`, so both
calls above are as they stand in `RenderModuleProviders.kt`.

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RenderCapabilityTest*'`
Expected: FAIL — `Unresolved reference: producesVector`.

- [ ] **Step 3: Add the capability and re-gate SVG**

```kotlin
    /** True when the render produces a character grid, and so a `.txt` or `.ansi`. */
    val producesGlyphs: Boolean get() = mode.isGlyph

    /**
     * True when the render is made of lines and can be written as `.svg` without being drawn
     * first.
     *
     * Separate from [producesGlyphs] because the two were the same flag only for as long as every
     * vector output happened to be made of characters. A ridgeline is vector and has no
     * characters in it; a mode could equally be the other way round.
     */
    val producesVector: Boolean get() = mode.isGlyph || mode == RenderMode.Ridgeline
```

In `Exports.kt`, gate `.txt` and `.ansi` on `producesGlyphs` and `.svg` on `producesVector`.

**Note:** the `RenderMode.Ridgeline` reference above does not exist until Task 3. Add the constant
first if the compiler objects — the two tasks are adjacent for that reason, and splitting them is
about review focus, not about compilability in isolation.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RenderCapability*' --tests '*Export*'`
Expected: PASS. No existing mode changes which exports it offers.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/render/RenderModuleProviders.kt \
        app/src/main/java/org/phioster/glyphsmith/export/Exports.kt \
        app/src/test/java/org/phioster/glyphsmith/render/RenderCapabilityTest.kt
git commit -m "Vektorausgabe ist nicht dasselbe wie Zeichenausgabe"
```

---

### Task 3: Declare the mode

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderMode.kt:25-46`
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderModeIds.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderModuleProviders.kt:38-42`
- Test: `app/src/test/java/org/phioster/glyphsmith/render/RidgelineModeTest.kt`

**Interfaces:**
- Produces: `RenderMode.Ridgeline`, id `render.ridgeline`, display name `ridgeline`.

Every exhaustive `when` over `RenderMode` in the codebase will now fail to compile. Work through
them and state the answer in each; do not add `else`. Expect at least `isGlyph`, `ditherFirst`,
and whatever the render dispatcher uses.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RidgelineModeTest {

    @Test
    fun `it has a stable id and is listed last`() {
        assertEquals("render.ridgeline", RenderModeIds.idOf(RenderMode.Ridgeline))
        assertEquals(RenderMode.Ridgeline, RenderModules.all.last().mode)
    }

    @Test
    fun `it makes vector but not characters and does not dither first`() {
        val provider = RenderModules.of(RenderMode.Ridgeline)
        assertFalse("a ridgeline has no characters in it", provider.producesGlyphs)
        assertTrue("a ridgeline is made of lines", provider.producesVector)
        assertFalse(RenderMode.Ridgeline.ditherFirst)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RidgelineModeTest*'`
Expected: FAIL — `Unresolved reference: Ridgeline`.

- [ ] **Step 3: Add the constant and answer every `when`**

```kotlin
    /**
     * Rows read as heights and stacked up the frame — the ridgeline, or joyplot, figure.
     *
     * The other three modes all turn a quantised *level* into something. This one does not
     * quantise at all: a row's brightness becomes a displacement, and what comes out is a set of
     * curves rather than a grid of cells. That is why it produces vector natively and why it is
     * the first mode to offer `.svg` without offering `.txt`.
     */
    Ridgeline,
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Render*' --tests '*Layering*'`
Expected: PASS. `LayeringTest` matters here: the new package must not import from `glyph`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/render/RenderMode.kt \
        app/src/main/java/org/phioster/glyphsmith/render/RenderModeIds.kt \
        app/src/main/java/org/phioster/glyphsmith/render/RenderModuleProviders.kt \
        app/src/test/java/org/phioster/glyphsmith/render/RidgelineModeTest.kt
git commit -m "Ridgeline als vierter Render-Modus"
```

---

### Task 4: Draw it

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/render/ridgeline/RidgelineRenderer.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/render/ridgeline/RidgelineSettingsTest.kt`

**Interfaces:**
- Consumes: `Ridgeline.build` from Task 1.
- Produces: `RidgelineRenderer.render(ridges, width, height, settings): Pixels`
- Produces on `RenderSettings`: `ridgeRows: Int = 40`, `ridgeAmplitude: Int = 150`,
  `ridgeOverlap: Int = 100`, `ridgeBaseline: Boolean = true`, `ridgeStroke: Int = 2`.

The renderer draws to `Pixels` and so cannot be unit-tested here — same constraint as Plan 04.
The test therefore covers the settings' serialisation and their mapping onto `Ridgeline.build`'s
float arguments, which is where the off-by-a-factor-of-100 mistakes live. The picture is checked
by eye in Task 6.

Colour follows the existing rules rather than inventing its own: ink colour, source-sampled or
palette, with the background from the same setting everything else uses.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.render.ridgeline

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderSettings

class RidgelineSettingsTest {

    @Test
    fun `a preset written before this mode existed keeps every default`() {
        val old = """{"ditherMode":"FLOYD_STEINBERG"}"""
        val back = Json { ignoreUnknownKeys = true }
            .decodeFromString(RenderSettings.serializer(), old)
        assertEquals(40, back.ridgeRows)
        assertEquals(150, back.ridgeAmplitude)
        assertEquals(true, back.ridgeBaseline)
    }

    @Test
    fun `the percentage settings map onto the geometry's floats`() {
        val settings = RenderSettings(
            renderMode = RenderMode.Ridgeline,
            ridgeAmplitude = 150,
            ridgeOverlap = 200,
        )
        assertEquals(1.5f, RidgelineRenderer.amplitudeOf(settings), 1e-6f)
        assertEquals(2.0f, RidgelineRenderer.overlapOf(settings), 1e-6f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RidgelineSettingsTest*'`
Expected: FAIL — `Unresolved reference: ridgeRows`.

- [ ] **Step 3: Add the settings and the renderer**

Add the five fields to `RenderSettings` with the defaults above and doc comments in the house
style. Write `RidgelineRenderer` with the two public `amplitudeOf`/`overlapOf` helpers the test
names, and stroke the polylines with the settings' ink colour.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Ridgeline*' --tests '*Preset*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/render/ridgeline/RidgelineRenderer.kt \
        app/src/main/java/org/phioster/glyphsmith/render/RenderSettings.kt \
        app/src/test/java/org/phioster/glyphsmith/render/ridgeline/RidgelineSettingsTest.kt
git commit -m "Ridgeline wird gezeichnet, Einstellungen im Preset"
```

---

### Task 5: Export it as real vector

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/export/Exports.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/export/RidgelineSvgTest.kt`

**Interfaces:**
- Consumes: `Ridge`, `RidgePoint` from Task 1; `producesVector` from Task 2.
- Produces: `fun ridgelineSvg(ridges: List<Ridge>, width: Int, height: Int, stroke: Int, ink: Int, background: Int?): String`

This is the payoff. The glyph SVG export writes one `<text>` per cell; a ridgeline writes one
`<polyline>` per visible run, which is both smaller and genuinely resolution-independent —
the first export in the app that loses nothing at all.

Two rules the test enforces: a transparent background writes **no** background rectangle (the
format picker already promises which formats survive transparency), and coordinates are written
with bounded precision so a 40-row plot does not produce a megabyte of decimals.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.phioster.glyphsmith.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.ridgeline.Ridge
import org.phioster.glyphsmith.render.ridgeline.RidgePoint

class RidgelineSvgTest {

    private val ridges = listOf(
        Ridge(0, listOf(RidgePoint(0f, 0.5f), RidgePoint(0.5f, 0.25f), RidgePoint(1f, 0.5f))),
        Ridge(1, listOf(RidgePoint(0f, 0.9f), RidgePoint(1f, 0.9f))),
    )

    @Test
    fun `each visible run becomes one polyline`() {
        val svg = Exports.ridgelineSvg(ridges, 800, 600, 2, 0xFF000000.toInt(), null)
        assertEquals(2, Regex("<polyline").findAll(svg).count())
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("viewBox=\"0 0 800 600\""))
    }

    @Test
    fun `unit coordinates are scaled into the viewbox`() {
        val svg = Exports.ridgelineSvg(ridges, 800, 600, 2, 0xFF000000.toInt(), null)
        assertTrue("x=0.5 must land at 400", svg.contains("400"))
        assertTrue("y=0.9 must land at 540", svg.contains("540"))
    }

    @Test
    fun `a transparent background writes no rectangle`() {
        val svg = Exports.ridgelineSvg(ridges, 800, 600, 2, 0xFF000000.toInt(), null)
        assertFalse(svg.contains("<rect"))
        val opaque = Exports.ridgelineSvg(ridges, 800, 600, 2, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertTrue(opaque.contains("<rect"))
    }

    @Test
    fun `coordinates do not run to full float precision`() {
        val awkward = listOf(Ridge(0, listOf(RidgePoint(1f / 3f, 1f / 7f))))
        val svg = Exports.ridgelineSvg(awkward, 1000, 1000, 1, 0xFF000000.toInt(), null)
        assertFalse("no runaway decimals", Regex("\\d\\.\\d{4,}").containsMatchIn(svg))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RidgelineSvgTest*'`
Expected: FAIL — `Unresolved reference: ridgelineSvg`.

- [ ] **Step 3: Implement it**

Emit the header, an optional background `<rect>`, then one `<polyline>` per `Ridge` with `fill`
set from `ridgeBaseline` and `stroke` from the ink colour. Round coordinates to two decimals.
Route the export through the same place that already decides what happened and says so, so
`saved to Download/Glyphsmith` reads identically from this path too.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Svg*' --tests '*Export*'`
Expected: PASS, all four, and the glyph SVG tests unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/export/Exports.kt \
        app/src/test/java/org/phioster/glyphsmith/export/RidgelineSvgTest.kt
git commit -m "Ridgeline als echtes SVG, eine Polylinie je sichtbarem Zug"
```

---

### Task 6: Controls, presets and the documentation

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/data/PresetLibrary.kt`
- Modify: `README.md`, `ARCHITECTURE.md`

**Interfaces:**
- Consumes: everything above.

Five controls, shown only in this mode, following the standing rule that controls which do not
apply disappear rather than sit inert. Add **two** shipped presets — one the record-sleeve look
(baseline on, high overlap, many rows), one open curves on a light ground — because a mode with
no preset is a mode nobody finds.

`README.md` needs more than a line: it currently says there are three render modes, in the second
paragraph, and repeats it under **Render modes**. `ARCHITECTURE.md` needs the capability split
from Task 2 recorded next to the existing provider table.

- [ ] **Step 1: Add the controls and the two presets**

- [ ] **Step 2: Build and install**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Check by eye**

1. A portrait at 40 rows, baseline on — should read as the record sleeve, with faces still legible
   in the silhouette.
2. Amplitude past 200% — curves should tangle into each other without tearing or leaving gaps at
   the frame edge.
3. Export `.svg` and open it — line count should match what is on screen, and `.txt` must **not**
   be offered.
4. Switch to pixel dither and back — the other three modes must be exactly as they were.

- [ ] **Step 4: Update both documents**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/phioster/glyphsmith/ui/panels/RenderPanel.kt \
        app/src/main/java/org/phioster/glyphsmith/data/PresetLibrary.kt \
        README.md ARCHITECTURE.md
git commit -m "Ridgeline im Bedienfeld, zwei Presets, Doku nachgezogen"
```

---

## Notes for the reviewer

- **This is the largest plan of the five and the least certain.** Tasks 1–3 are safe; Task 4's
  look is a matter of taste that no test can settle. If the picture is disappointing at Task 6,
  the fault is almost certainly in the brightness-to-height mapping being linear — a gamma on that
  mapping is the first thing to try, and it is one more setting, not a redesign.
- **Hidden-line removal is the difference between the figure and a mess.** If Task 1's fourth test
  is made to pass by some means other than an actual silhouette, the mode will look wrong and
  nobody will be able to say why.
- **Do not let this mode touch the glyph package.** It is shared render infrastructure. If it
  starts wanting something from `glyph`, that thing is in the wrong place, and `LayeringTest` will
  say so before a reviewer does.
