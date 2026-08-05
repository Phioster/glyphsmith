# Animation Targets as a Plugin Category — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an effect declare that one of its parameters is animatable, and have it appear in
the animation panel, in tracks, in segments and in saved presets — without `anim/` being edited.

**Architecture:** The same shape the effect category reached: the thing that knows a parameter
declares everything about it, and one compiler-checked registry collects the declarations.
`AnimTarget` stops being a hand-written enum in `anim/` and becomes a registry of
`AnimTargetProvider`s contributed by whoever owns the parameter. The wire format is prepared
first (Task 2) so the Kotlin type can change later (Task 4) without the preset format moving at
all.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. No new
dependencies.

## Why this milestone, and not the palette one

The milestone as given was the palette category. **It was audited first and needs no work**, and
Task 1 records that rather than inventing code for it. The audit, in full:

- Adding a built-in palette is **already one line** in `core/color/Palettes.kt`. From that line
  the palette reaches `PaletteProviders` (built by `Palettes.all.map(::PaletteProvider)`), its
  wire id (`PaletteProvider.wireIdOf`, derived — never hand-written), the picker
  (`Palettes.categories` / `inCategory`, both derived), presets (`paletteId` is that id),
  Surprise Me (`Palettes.all.random`) and the schema migration.
- There is **no per-palette behaviour to dispatch.** A palette is data — id, name, category,
  colours. The failure mode the effect work removed (a `when` mapping a constant to code, kept
  by hand in several places) cannot arise here, and never did: `ARCHITECTURE.md` already records
  that `PaletteProvider` "carries data" while `EffectProvider` "carries execution".
- A **new category** is free text on the palette. The `const val` names in `Palettes` are a
  convention, not a registry.
- Imported palettes are **not** a second-class registered palette and do not need to become one.
  `GlyphsmithViewModel.importPalette` applies a file as `paletteOverride` — a colour list stored
  inside the preset — so an imported palette survives saving, sharing and reloading. It has no
  name and no shelf, which is a product question, not an architectural one.

The one imperfection found: **`PaletteProviders` is not on any read path.** The UI reads
`Palettes.categories` / `Palettes.inCategory`, `RandomLook` reads `Palettes.all`, the renderers
read `Palettes.byId`; only `pipeline/Providers` reads the registry, to check ids. Routing the UI
through the registry would change nothing observable and is exactly the artificial refactor the
brief forbids. **Decision: leave it, and write down why** — that is Task 1.

So the next category is the one already identified in `CLAUDE_TASKS.md`, task 7: animation
targets. It is also the one with actual user value behind it — five effects shipped since the
animation system was written and not one of their parameters can be animated.

## Global Constraints

Copied from `CLAUDE.md`, `ROADMAP_V2.md` and the standing goal. Every task's requirements
implicitly include this section.

- No runtime plugins, no reflection, no `ServiceLoader`, no dynamic class loading, no script
  engines. Everything stays typed and compiled in.
- Stable wire ids are never changed once written. Raising `PresetSchema.CURRENT_VERSION` means
  adding a `Migration` **and** a test that decodes a literal document of the old version.
- An unknown id is **refused**, not remapped. The entry carrying it is dropped; the rest of the
  library survives.
- No migration may change how anything renders. Existing animations must look identical.
- `RenderSettings.renderMode` stays `GlyphMatrix`; `RenderMode.DEFAULT` stays `PurePixel`.
- Do not weaken an existing test. New guardrails state a rule no existing test states.
- Layering (`LayeringTest`): `core` may import nothing else; `effects` may not import `render`,
  `glyph`, `pipeline`, `state`, `ui`, `data`, `export` or **`anim`**. `anim` is unrestricted and
  may import `render` and `effects` — the collection direction is `anim` → `effects`, never back.
- One behaviour axis, one visible change, one testable outcome per PR.
- Commit identity: no `Co-Authored-By` and no session trailers.
- Local gate: `T=~/glyphsmith-research/local-test-harness ./run.sh <test files>` (~20 s, no
  Compose, no Robolectric). Authoritative gate: CI (`gradle testDebugUnitTest`, `detekt`,
  `lintDebug`, `assembleDebug`). Add every new non-Compose source file to the harness's
  `srcfiles.txt` or the main compile fails and every symbol in the test reads as unresolved.

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `PROJECT_STATE.md`, `ARCHITECTURE.md`, `CLAUDE_TASKS.md`, `ROADMAP_V2.md` | record the palette audit and retarget the milestone | 1 |
| `anim/AnimTargetIds.kt` *(new)* | the stable id table for animation targets | 2 |
| `anim/Animation.kt` | `AnimTarget` gains `wireId`; serialises as its id | 2 |
| `data/PresetSchema.kt` | schema 5 and the `AnimTargetMigration` | 2 |
| `core/anim/AnimatableParam.kt` *(new)* | what "this parameter can be driven" is, as a value a module can hold. In `core/` because `effects/` must be able to declare one and may not import `anim/` | 4 |
| `anim/AnimTargets.kt` *(new)* | the registry: every target this build offers, collected from its owners | 3, 4 |
| `anim/Animation.kt` | `Animator.apply`'s `when` dies; `AnimTrack.target` becomes an id | 3, 4 |
| `effects/EffectPass.kt` | a pass may declare animatable parameters | 4 |
| `effects/*.kt` | the five effect-owned targets move home; new ones are added | 4, 5 |
| `ui/panels/AnimPanel.kt` | reads the registry instead of the enum | 3, 4 |

---

### Task 1: Record the palette audit and retarget the milestone

**Files:**
- Modify: `PROJECT_STATE.md` — the *Execution model* section, after the effect-category paragraph
- Modify: `ARCHITECTURE.md:276` — the *Palette Providers* section
- Modify: `CLAUDE_TASKS.md` — add task 8, amend task 7's framing
- Modify: `ROADMAP_V2.md` — phase 3

**Interfaces:**
- Consumes: nothing.
- Produces: the written decision that later tasks point at. No code.

**Why:** the brief says that if a category is already modular enough, document it and move on
rather than generate code. This is that document. Without it the next session re-audits palettes
from scratch, which is the cost this file exists to prevent.

- [ ] **Step 1: Add the audit to `ARCHITECTURE.md`, *Palette Providers***

Append to that section:

```markdown
**The palette category is complete as it stands, and was audited on 2026-08-05 rather than
rebuilt.** Adding a built-in palette is one line in `Palettes.all`; from there the registry, the
wire id, the picker's categories, presets, the schema migration and Surprise Me all follow
without another edit. There is no per-palette behaviour, so the failure the effect category was
opened up to remove — a constant mapped to code by hand in several places — has no counterpart
here.

`PaletteProviders` is deliberately **not** on the read path. The picker reads
`Palettes.categories` and `Palettes.inCategory`, `RandomLook` reads `Palettes.all`, and the
renderers read `Palettes.byId`; the registry is read by `pipeline/Providers` to hold the ids to
their rules. Routing the UI through the registry instead would change nothing a user or a test
can observe, so it is not done.

Imported palettes stay out of the registry on purpose. `importPalette` applies a file as
`paletteOverride` — a colour list carried inside the preset — so an imported palette already
survives saving and sharing. Giving it a name and a shelf is a product decision, not an
architectural gap.
```

- [ ] **Step 2: Add the same conclusion to `PROJECT_STATE.md`**

In *Execution model*, after the paragraph beginning "The effect category is the one that is
fully plugin-shaped":

```markdown
**The palette category needs no such work and was audited, not rebuilt** (2026-08-05). A palette
is data, adding one is a single line, and everything downstream is derived from it. The registry
is not on the read path and is deliberately left that way — `ARCHITECTURE.md`, *Palette
Providers*, says why. Do not re-open this.
```

- [ ] **Step 3: Amend `CLAUDE_TASKS.md`**

Retitle task 7 to *the next plugin category — animation targets* if it is not already, and add
below it:

```markdown
## Task 8: palette category audit — **done, no code** (2026-08-05)

Goal: bring the palette category to the maturity the effect category reached.

Result: **it is already there, by a different route.** A palette is data and adding one is a
single line in `Palettes.all`; the registry, the wire id, the picker, presets, the migration and
Surprise Me are all derived from it. There is no execution to scatter, so there was no `when` to
remove. The finding, including why `PaletteProviders` is deliberately not on the read path and
why imported palettes stay out of the registry, is in `ARCHITECTURE.md`, *Palette Providers*.

No code was written. The next category is task 7.
```

- [ ] **Step 4: Point `ROADMAP_V2.md` phase 3 at animation targets**

Under *Still good candidates*, replace nothing and add:

```markdown
- **animation targets as the next plugin category** (`CLAUDE_TASKS.md` task 7). The palette
  category was audited and needs no work (task 8).
```

- [ ] **Step 5: Verify no code changed**

Run: `git diff --stat -- app/`
Expected: empty output.

- [ ] **Step 6: Commit and open the PR**

```bash
git checkout -b palette-audit
git add ARCHITECTURE.md PROJECT_STATE.md CLAUDE_TASKS.md ROADMAP_V2.md
git commit -m "Record that the palette category needs no plugin work"
git push -u origin palette-audit
gh pr create --title "Record that the palette category needs no plugin work" --body "..."
```

---

### Task 2: Stable wire ids for animation targets

> **Executed 2026-08-05 with one deviation, found by the tests.** Steps 6–8 below plan a schema
> version 5 and an `AnimTargetMigration`. Both were **dropped**: with the serialiser in place the
> migration provably rewrites nothing, because `WireIdSerializer` already accepts a legacy
> constant name as an alias and encoding always writes the id. The literal-version-4 tests from
> step 6 were written first and passed without any migration at all, which is the evidence. A
> version bump whose migration is empty is ceremony, and the brief forbids artificial changes —
> so the version stays 4 and `PresetSchema`'s KDoc records when the spelling changed and why the
> number did not move. Everything else in this task was executed as written.

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/anim/AnimTargetIds.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/anim/Animation.kt:18-49` (the `AnimTarget`
  declaration)
- Modify: `app/src/main/java/org/phioster/glyphsmith/data/PresetSchema.kt:65` (version), `:289-291`
  (the migration list), and add `AnimTargetMigration`
- Test: `app/src/test/java/org/phioster/glyphsmith/core/serial/WireIdTest.kt:24`
- Test: `app/src/test/java/org/phioster/glyphsmith/data/PresetSchemaTest.kt`

**Interfaces:**
- Consumes: `core/serial/WireIdSerializer`, `PresetSchema.Migration`.
- Produces: `AnimTargetIds.idOf(target): String`, `AnimTargetIds.migrated(raw): String?`,
  `AnimTarget.wireId: String`, `PresetSchema.CURRENT_VERSION == 5`.

**Why:** `AnimTarget` is the last identity in the preset format written as a Kotlin constant
name — `AnimTrack.target` and `AnimSegment.target` serialise `GLOW_DIRECTION`, so renaming the
constant silently changes what an existing animation drives. That is worth fixing on its own.
It is also what makes Task 4 cheap: once the file holds an id string, the Kotlin type behind it
can change without the format moving.

- [ ] **Step 1: Write the failing id tests**

In `WireIdTest`, add `AnimTargetIds` to the shared table list and one explicit assertion:

```kotlin
private val tables = listOf(RenderModeIds, DitherModeIds, EffectIds, AnimTargetIds)

@Test
fun `every animation target id is unique`() = assertUnique(AnimTargetIds.ids)
```

and extend the existing `the enums serialise as their ids`:

```kotlin
assertEquals("\"anim.glow-direction\"", json.encodeToString(AnimTarget.serializer(), AnimTarget.GLOW_DIRECTION))
```

The shared tests over `tables` then also cover format, category prefix, round trip, legacy-name
resolution and refusal of an unknown id, for free.

- [ ] **Step 2: Run and watch it fail**

Run: `T=~/glyphsmith-research/local-test-harness ~/glyphsmith-research/local-test-harness/run.sh core/serial/WireIdTest.kt`
Expected: FAIL — `AnimTargetIds` unresolved.

- [ ] **Step 3: Give `AnimTarget` its ids**

In `anim/Animation.kt`, the constants keep their spelling and their order — the order is a
rendering input, see Task 3 — and gain an id as the first argument:

```kotlin
@Serializable(with = AnimTargetIds::class)
enum class AnimTarget(
    val wireId: String,
    val label: String,
    val min: Int,
    val max: Int,
    val cyclic: Boolean = false,
) {
    DEPTH("anim.depth", "Depth", 1, RenderSettings.MAX_DEPTH),
    CHARACTER_OFFSET("anim.character-offset", "Character Offset", 0, 64),
    DITHER_STRENGTH("anim.dither-strength", "Dither Strength", 0, 100),
    MOD_PHASE("anim.pattern-phase", "Modulation Phase", 0, 100, cyclic = true),
    PATTERN_DENSITY("anim.pattern-density", "Pattern Density", 0, 100),
    EDGE_THRESHOLD("anim.edge-threshold", "Edge Threshold", 0, 100),
    GLITCH_SEED("anim.glitch-seed", "Glitch Seed", 1, 9999),
    CHROMATIC_OFFSET("anim.chromatic-offset", "Chromatic Offset", 0, 50),
    GLOW_DIRECTION("anim.glow-direction", "Glow Direction", 0, 359, cyclic = true),
    STARS_ANGLE("anim.stars-angle", "Stars Angle", 0, 359, cyclic = true),
    MODULATION_PHASE("anim.modulation-lines-phase", "Modulation Phase (lines)", 0, 100, cyclic = true),
}
```

The two phase ids are deliberately *not* mechanical renderings of the constants. `MOD_PHASE`
drives the dither pattern and `MODULATION_PHASE` drives the modulation-lines effect; the labels
have always been nearly identical and the ids are the chance to say which is which. This also
satisfies `ids are not merely the enum names in lower case`, which a fully mechanical table
would fail.

- [ ] **Step 4: Add the id table**

Create `anim/AnimTargetIds.kt`:

```kotlin
package org.phioster.glyphsmith.anim

import org.phioster.glyphsmith.core.serial.WireIdSerializer

/**
 * What each animation target is called in a saved preset.
 *
 * The last identity in the format that was a Kotlin constant name: an `AnimTrack` stored
 * `GLOW_DIRECTION`, so renaming the constant would have changed what an existing animation
 * drives, silently and on somebody else's device.
 */
object AnimTargetIds : WireIdSerializer<AnimTarget>(
    category = "anim",
    values = AnimTarget.entries.toList(),
    idOf = AnimTarget::wireId,
)
```

- [ ] **Step 5: Run the id tests**

Run: `T=~/glyphsmith-research/local-test-harness ~/glyphsmith-research/local-test-harness/run.sh core/serial/WireIdTest.kt`
Expected: PASS. (First add `anim/AnimTargetIds.kt` to `srcfiles.txt`.)

- [ ] **Step 6: Write the failing migration test**

In `PresetSchemaTest`:

```kotlin
/** A version 4 document names its animation targets with the Kotlin constants. */
private val version4 = """
    {"schemaVersion": 4, "presets": [
      {"name": "old", "category": "MOTION", "params": {
        "renderMode": "render.glyph-art",
        "animation": {"enabled": true, "frames": 24, "fps": 12,
          "tracks": [{"target": "GLOW_DIRECTION", "enabled": true, "from": 0, "to": 359}],
          "segments": [{"target": "GLITCH_SEED", "from": 1, "to": 9999, "start": 0, "end": 50}]}
      }}
    ]}
""".trimIndent()

@Test
fun `a version 4 animation target still drives the same parameter`() {
    val preset = PresetSchema.decode(version4).single()

    assertEquals(AnimTarget.GLOW_DIRECTION, preset.params.animation.tracks.single().target)
    assertEquals(AnimTarget.GLITCH_SEED, preset.params.animation.segments.single().target)
}

@Test
fun `a re-encoded preset names its targets by id`() {
    val text = PresetSchema.encode(PresetSchema.decode(version4))

    assertTrue(text.contains("anim.glow-direction"))
    assertTrue(!text.contains("GLOW_DIRECTION"))
}
```

- [ ] **Step 7: Run and watch it fail**

Run: `... run.sh data/PresetSchemaTest.kt`
Expected: FAIL on the second test — the first already passes, because `WireIdSerializer`
accepts a legacy constant name on read. That asymmetry is the point: reading is already safe,
and the migration is what stops the old spelling from being carried forward for ever.

- [ ] **Step 8: Add the migration**

In `PresetSchema`, raise the version and add the step. It follows `WireIdMigration`'s rules
exactly — rewrite only what this build recognises, recurse into layers, because a layer carries
a full set of params and therefore an animation of its own:

```kotlin
const val CURRENT_VERSION = 5

private const val KEY_ANIMATION = "animation"
private const val KEY_TRACKS = "tracks"
private const val KEY_SEGMENTS = "segments"
private const val KEY_TARGET = "target"

/**
 * 4 → 5: an animation target's constant name becomes its stable id.
 *
 * The last identity in the format still spelled as Kotlin. Reading a version 4 file already
 * worked — the serialiser accepts legacy names — so this changes nothing that can be seen; what
 * it does is stop the old spelling being written back out for ever, which is what would have
 * kept the constants load-bearing.
 */
private object AnimTargetMigration : Migration {
    override val from = 4

    override fun apply(entry: JsonElement): JsonElement {
        val preset = entry as? JsonObject ?: return entry
        val params = preset[KEY_PARAMS] as? JsonObject ?: return entry
        return JsonObject(preset + (KEY_PARAMS to params(params)))
    }

    private fun params(params: JsonObject): JsonObject {
        val animation = params[KEY_ANIMATION] as? JsonObject
        var patched = params
        if (animation != null) {
            val rewritten = targets(targets(animation, KEY_TRACKS), KEY_SEGMENTS)
            patched = JsonObject(patched + (KEY_ANIMATION to rewritten))
        }
        return layers(patched)
    }

    private fun targets(animation: JsonObject, key: String): JsonObject {
        val list = animation[key] as? JsonArray ?: return animation
        val renamed = JsonArray(
            list.map { element ->
                val item = element as? JsonObject ?: return@map element
                val raw = (item[KEY_TARGET] as? JsonPrimitive)?.contentOrNull ?: return@map element
                val stable = AnimTargetIds.migrated(raw) ?: return@map element
                JsonObject(item + (KEY_TARGET to JsonPrimitive(stable)))
            },
        )
        return JsonObject(animation + (key to renamed))
    }

    private fun layers(params: JsonObject): JsonObject {
        val layers = params[KEY_LAYERS] as? JsonArray ?: return params
        val patched = JsonArray(
            layers.map { layer ->
                val entry = layer as? JsonObject ?: return@map layer
                val nested = entry[KEY_PARAMS] as? JsonObject ?: return@map layer
                JsonObject(entry + (KEY_PARAMS to params(nested)))
            },
        )
        return JsonObject(params + (KEY_LAYERS to patched))
    }
}

private val migrations: List<Migration> =
    listOf(RenderModeMigration, WireIdMigration, PaletteIdMigration, AnimTargetMigration)
```

Also extend the *Versions* KDoc with a `5` entry, in the style of the four above it.

- [ ] **Step 9: Run the schema and animation tests**

Run: `... run.sh data/PresetSchemaTest.kt data/PresetLibraryTest.kt anim/AnimationTest.kt anim/AnimSegmentTest.kt core/serial/WireIdTest.kt`
Expected: PASS, all of them.

- [ ] **Step 10: Commit and open the PR**

```bash
git checkout -b anim-target-wire-ids
git add app/src/main/java/org/phioster/glyphsmith/anim/AnimTargetIds.kt \
        app/src/main/java/org/phioster/glyphsmith/anim/Animation.kt \
        app/src/main/java/org/phioster/glyphsmith/data/PresetSchema.kt \
        app/src/test/java/org/phioster/glyphsmith/core/serial/WireIdTest.kt \
        app/src/test/java/org/phioster/glyphsmith/data/PresetSchemaTest.kt
git commit -m "Give animation targets stable ids"
```

Update `PROJECT_STATE.md`'s "Preset schema version: 4" to 5 and the test count in the same
commit — phase 0 of the roadmap makes those the first numbers to rot.

---

### Task 3: The target carries what it writes

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/anim/AnimTargets.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/anim/Animation.kt:274-313` (delete
  `Animator.apply`'s `when`), `:154`, `:201`, `:269` (the ordinal uses)
- Test: `app/src/test/java/org/phioster/glyphsmith/anim/AnimTargetsTest.kt` *(new)*

**Interfaces:**
- Consumes: `AnimTarget`, `RenderSettings`.
- Produces: `AnimTargetProvider(target, salt, write)` with
  `write: (RenderSettings, Int) -> RenderSettings`; `AnimTargets.all: List<AnimTargetProvider>`,
  `AnimTargets.of(target): AnimTargetProvider`, `AnimTargetProvider.salt: Int`.

**Why:** `Animator.apply` is an eleven-branch `when` over `AnimTarget` mapping a target to the
field it writes — the same shape `state/RandomLook` had for effects. It is what makes Task 4
impossible while it stands, because a target contributed by an effect has no branch there to be
written in.

**The trap, and it is a real one:** `track.target.ordinal + 1` is the salt of the `RANDOM` curve
(`Animation.kt:201` and `:269`) and `target.ordinal` is the track sort key (`:154`). The ordinal
is therefore a **rendering input**. Once targets stop being an enum their ordinals are gone, so
the salt has to become an explicit number — and it must be seeded with the values the eleven
existing targets have today, or every saved animation using a `RANDOM` curve changes appearance.
That is the one thing this task must not get wrong.

- [ ] **Step 1: Write the failing salt test**

`anim/AnimTargetsTest.kt`:

```kotlin
package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimTargetsTest {

    /**
     * The salt of the RANDOM curve, frozen at the values the enum ordinals produced.
     *
     * This is not a tidiness test. `Animator.sample` hashes `frame` against the salt, so a salt
     * that moves is a saved animation that renders differently — and the only symptom is an
     * exported GIF nobody compares against last week's.
     */
    @Test
    fun `the historic targets keep the salt their ordinal gave them`() {
        val expected = mapOf(
            AnimTarget.DEPTH to 1,
            AnimTarget.CHARACTER_OFFSET to 2,
            AnimTarget.DITHER_STRENGTH to 3,
            AnimTarget.MOD_PHASE to 4,
            AnimTarget.PATTERN_DENSITY to 5,
            AnimTarget.EDGE_THRESHOLD to 6,
            AnimTarget.GLITCH_SEED to 7,
            AnimTarget.CHROMATIC_OFFSET to 8,
            AnimTarget.GLOW_DIRECTION to 9,
            AnimTarget.STARS_ANGLE to 10,
            AnimTarget.MODULATION_PHASE to 11,
        )

        expected.forEach { (target, salt) ->
            assertEquals("${target.name} changed salt", salt, AnimTargets.of(target).salt)
        }
    }

    @Test
    fun `no two targets share a salt`() {
        val salts = AnimTargets.all.map { it.salt }
        assertEquals("two targets hash alike", salts.size, salts.distinct().size)
    }

    @Test
    fun `every target is registered exactly once`() {
        assertEquals(AnimTarget.entries.toList(), AnimTargets.all.map { it.target })
    }

    /** Each target writes its own field and leaves the rest of the settings alone. */
    @Test
    fun `a target writes only what it names`() {
        val base = org.phioster.glyphsmith.render.RenderSettings()

        AnimTargets.all.forEach { provider ->
            val moved = provider.write(base, provider.target.max)
            assertTrue("${provider.target.name} wrote nothing", moved != base)
        }
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `... run.sh anim/AnimTargetsTest.kt`
Expected: FAIL — `AnimTargets` unresolved.

- [ ] **Step 3: Write `anim/AnimTargets.kt`**

Each provider is the branch that used to live in `Animator.apply`, moved next to the target it
describes. The clamps and the two deliberate non-clamps come across verbatim — `MOD_PHASE` and
`CHARACTER_OFFSET` are unclamped on purpose and say so in `Animation.kt` today.

```kotlin
package org.phioster.glyphsmith.anim

import org.phioster.glyphsmith.render.RenderSettings

/**
 * One animation target: what it is called, how its random curve is seeded, and the field it
 * writes.
 *
 * [salt] is not the position in a list. It seeds the RANDOM curve's hash, so it is a rendering
 * input and is stated explicitly rather than derived — it used to be `ordinal + 1`, and the
 * numbers below are exactly those, kept so that no saved animation changes.
 */
class AnimTargetProvider(
    val target: AnimTarget,
    val salt: Int,
    val write: (RenderSettings, Int) -> RenderSettings,
)

object AnimTargets {

    val all: List<AnimTargetProvider> = listOf(
        AnimTargetProvider(AnimTarget.DEPTH, 1) { p, v ->
            p.copy(depth = v.coerceIn(1, RenderSettings.MAX_DEPTH))
        },
        // The offset wraps anyway, so it is never clamped to the ramp length here.
        AnimTargetProvider(AnimTarget.CHARACTER_OFFSET, 2) { p, v -> p.copy(offset = v) },
        AnimTargetProvider(AnimTarget.DITHER_STRENGTH, 3) { p, v ->
            p.copy(ditherStrength = v.coerceIn(0, 100))
        },
        // Left unclamped: the phase wraps inside the pattern, so a track that runs past 100
        // keeps travelling instead of stalling at the end of its range.
        AnimTargetProvider(AnimTarget.MOD_PHASE, 4) { p, v -> p.copy(modPhase = v) },
        AnimTargetProvider(AnimTarget.PATTERN_DENSITY, 5) { p, v ->
            p.copy(patternDensity = v.coerceIn(0, 100))
        },
        AnimTargetProvider(AnimTarget.EDGE_THRESHOLD, 6) { p, v ->
            p.copy(edgeThreshold = v.coerceIn(0, 100))
        },
        AnimTargetProvider(AnimTarget.GLITCH_SEED, 7) { p, v ->
            p.copy(effects = p.effects.copy(jpegGlitch = p.effects.jpegGlitch.copy(seed = v)))
        },
        AnimTargetProvider(AnimTarget.CHROMATIC_OFFSET, 8) { p, v ->
            p.copy(
                effects = p.effects.copy(
                    chromatic = p.effects.chromatic.copy(maxDisplace = v.coerceIn(0, 50)),
                ),
            )
        },
        AnimTargetProvider(AnimTarget.GLOW_DIRECTION, 9) { p, v ->
            p.copy(effects = p.effects.copy(glow = p.effects.glow.copy(direction = v)))
        },
        AnimTargetProvider(AnimTarget.STARS_ANGLE, 10) { p, v ->
            p.copy(effects = p.effects.copy(stars = p.effects.stars.copy(angle = v)))
        },
        AnimTargetProvider(AnimTarget.MODULATION_PHASE, 11) { p, v ->
            p.copy(
                effects = p.effects.copy(
                    modulationLines = p.effects.modulationLines.copy(phase = v),
                ),
            )
        },
    )

    private val bySlot: Array<AnimTargetProvider> =
        AnimTarget.entries.map { t -> all.first { it.target == t } }.toTypedArray()

    fun of(target: AnimTarget): AnimTargetProvider = bySlot[target.ordinal]
}
```

- [ ] **Step 4: Point `Animator` at the registry**

Delete `Animator.apply` entirely and replace its three call sites:

```kotlin
// in paramsAt
params = AnimTargets.of(track.target).write(params, value)
...
params = AnimTargets.of(segment.target).write(params, value)

// in valueAt
val curve = sample(track.curve, u, frame, AnimTargets.of(track.target).salt)

// in valueIn
val shaped = shape(segment.curve, local, AnimTargets.of(segment.target).salt)
```

`AnimationParams.withTrack`'s `sortedBy { it.target.ordinal }` stays as it is for now; Task 4
replaces it with the registry order.

- [ ] **Step 5: Run the animation tests**

Run: `... run.sh anim/AnimTargetsTest.kt anim/AnimationTest.kt anim/AnimSegmentTest.kt data/PresetLibraryTest.kt`
Expected: PASS. `AnimationTest` is the one that matters — it exercises the curves and the
per-frame values, so an altered salt shows up there.

- [ ] **Step 6: Commit**

```bash
git checkout -b anim-target-write
git add app/src/main/java/org/phioster/glyphsmith/anim/ app/src/test/java/org/phioster/glyphsmith/anim/AnimTargetsTest.kt
git commit -m "Let an animation target carry the field it writes"
```

---

### Task 4: Targets are contributed by whoever owns the parameter

**Files:**
- Create: `app/src/main/java/org/phioster/glyphsmith/core/anim/AnimatableParam.kt`
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/EffectPass.kt` (a pass may declare
  animatable parameters)
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/JpegGlitch.kt`,
  `Chromatic.kt`, `EpsilonGlow.kt`, `DiffractionStars.kt`, `ModulationLines.kt` (the five
  effect-owned targets move home)
- Modify: `app/src/main/java/org/phioster/glyphsmith/anim/AnimTargets.kt` (collects them),
  `anim/Animation.kt` (`AnimTarget` is deleted; `target` becomes an id)
- Modify: `app/src/main/java/org/phioster/glyphsmith/ui/panels/AnimPanel.kt:129,257-259,295-297`
- Test: `app/src/test/java/org/phioster/glyphsmith/anim/AnimTargetsTest.kt`,
  `app/src/test/java/org/phioster/glyphsmith/effects/EffectCatalogTest.kt`

**Interfaces:**
- Consumes: `AnimTargets` and `AnimTargetProvider` from Task 3, `AnimTargetIds` from Task 2,
  `EffectPass.write` (shipped in the effect-plugin PR).
- Produces: `core/anim/AnimatableParam<P>(id, label, min, max, cyclic, salt, write: (P, Int) -> P)`;
  `EffectPass.animatable: List<AnimatableParam<*>>` erased to
  `List<AnimTargetProvider>`-compatible declarations; `AnimTrack.target: String`.

**Why:** this is the milestone. Until it lands, an effect's animatable parameter has to be
written down in `anim/`, which is the defect the effect category was opened up to remove.

**Where `AnimatableParam` lives, and why it is not in `anim/`:** `effects/` must declare one,
and `LayeringTest` forbids `effects` → `anim`. The declaration is engine vocabulary — an int
parameter with a range and a way to write it — so it belongs in `core/`. `core/anim/` reads as
what it is and satisfies the layering regex, which keys on the first package segment.

- [ ] **Step 1: Write the failing test — an effect contributes a target**

In `AnimTargetsTest`:

```kotlin
/**
 * The point of the whole category: a target that belongs to an effect is declared by the
 * effect, and reaches the registry without `anim/` naming it.
 */
@Test
fun `an effect contributes its own animatable parameters`() {
    val ids = AnimTargets.all.map { it.id }

    assertTrue("the glow's direction is missing", "anim.glow-direction" in ids)
    assertTrue("the glitch seed is missing", "anim.glitch-seed" in ids)
}

@Test
fun `no two targets share an id`() {
    val ids = AnimTargets.all.map { it.id }
    assertEquals(ids.size, ids.distinct().size)
}

@Test
fun `every registered target has a valid wire id in the anim category`() {
    AnimTargets.all.forEach { provider ->
        assertTrue(provider.id, WireId.isValid(provider.id))
        assertTrue(provider.id, provider.id.startsWith("anim."))
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `... run.sh anim/AnimTargetsTest.kt`
Expected: FAIL — `AnimTargetProvider` has no `id`.

- [ ] **Step 3: Write `core/anim/AnimatableParam.kt`**

```kotlin
package org.phioster.glyphsmith.core.anim

/**
 * One parameter a module declares as drivable by an animation.
 *
 * It is written where the parameter lives — inside the effect, beside the slider it moves —
 * because that is the only place that knows what the sensible range is and what the value
 * means. `anim/` collects these; it does not keep a list of what is animatable.
 *
 * [salt] seeds the RANDOM curve's hash and is therefore a rendering input, not a position:
 * a value that moves is a saved animation that renders differently. Take the next free number
 * and never reuse one.
 */
class AnimatableParam<P : Any>(
    val id: String,
    val label: String,
    val min: Int,
    val max: Int,
    val cyclic: Boolean,
    val salt: Int,
    private val set: (P, Int) -> P,
) {
    fun write(params: P, value: Int): P = set(params, value)
}
```

- [ ] **Step 4: Let a pass declare them**

`EffectPass` gains a parameter and one erased accessor. `P` stays private, exactly as with
`randomise`:

```kotlin
    private val animatable: List<AnimatableParam<P>> = emptyList(),
```

and, beside `rolled`:

```kotlin
    /**
     * This pass's animatable parameters, each already able to write itself into a whole
     * [EffectStack] — so `anim/` can drive an effect it cannot name.
     */
    fun animatableIn(): List<AnimatableTarget> = animatable.map { param ->
        AnimatableTarget(param.id, param.label, param.min, param.max, param.cyclic, param.salt) { stack, value ->
            write(stack, param.write(select(stack), value))
        }
    }
```

where `AnimatableTarget` is a small non-generic carrier in `effects/`:

```kotlin
class AnimatableTarget(
    val id: String,
    val label: String,
    val min: Int,
    val max: Int,
    val cyclic: Boolean,
    val salt: Int,
    val write: (EffectStack, Int) -> EffectStack,
)
```

- [ ] **Step 5: Move the five effect-owned targets home**

For example in `EpsilonGlow`, the pass gains:

```kotlin
        animatable = listOf(
            AnimatableParam("anim.glow-direction", "Glow Direction", 0, 359, cyclic = true, salt = 9) { p, v ->
                p.copy(direction = v)
            },
        ),
```

and the same for `JpegGlitch` (`anim.glitch-seed`, 1..9999, salt 7), `Chromatic`
(`anim.chromatic-offset`, 0..50, salt 8, clamped), `DiffractionStars` (`anim.stars-angle`,
0..359, cyclic, salt 10) and `ModulationLines` (`anim.modulation-lines-phase`, 0..100, cyclic,
salt 11). The salts are the ones frozen in Task 3 and must be carried over exactly.

- [ ] **Step 6: `AnimTargets` collects instead of listing**

The six render-owned targets stay declared in `anim/AnimTargets.kt`; the effect-owned ones come
from the registry:

```kotlin
    val all: List<AnimTargetProvider> = renderOwned + EffectProviders.all.flatMap { provider ->
        provider.pass.animatableIn().map { target ->
            AnimTargetProvider(target.id, target.label, target.min, target.max, target.cyclic, target.salt) { p, v ->
                p.copy(effects = target.write(p.effects, v))
            }
        }
    }
```

- [ ] **Step 7: `AnimTrack.target` becomes the id**

`AnimTarget` is deleted. `AnimTrack` and `AnimSegment` hold `val target: String`, which is what
the file already contains after Task 2 — so the preset format does not change at all, and
`AnimTargetIds` is no longer needed as a serialiser (keep the ids; delete the table only if
nothing else reads it). `AnimationParams.track`, `withTrack`, `segmentAt` and the default
`tracks` list key on the registry:

```kotlin
    val tracks: List<AnimTrack> = AnimTargets.all.map { AnimTrack(it.id, from = it.min, to = it.max) },
```

and `withTrack`'s sort becomes `sortedBy { track -> AnimTargets.indexOf(track.target) }`.

**An unknown target id must be dropped, not resolved**, on the same rule as every other
identity: a track naming a target this build does not have is skipped by `paramsAt` and left in
the list, so a preset written by a build with more effects survives a round trip intact.

- [ ] **Step 8: Point the panel at the registry**

`AnimPanel.kt` currently reads `AnimTarget.entries` in three places. All three become
`AnimTargets.all`, and each already uses only `label`, `min` and `max`, so no other change is
needed.

**Open product question, to raise rather than decide here:** the tracks section renders one
block per target. Eleven is already a long panel and Task 5 makes it longer. Grouping by owner —
render parameters, then one group per effect — is the obvious answer and belongs in its own PR.

- [ ] **Step 9: Run everything that touches animation**

Run: `... run.sh anim/AnimTargetsTest.kt anim/AnimationTest.kt anim/AnimSegmentTest.kt data/PresetSchemaTest.kt data/PresetLibraryTest.kt effects/EffectCatalogTest.kt LayeringTest.kt`
Expected: PASS. Then push and let CI compile the Compose half — the harness does not.

- [ ] **Step 10: Commit**

```bash
git checkout -b anim-target-providers
git commit -m "Let an effect declare its own animatable parameters"
```

---

### Task 5: The parameters that were never animatable

**Files:**
- Modify: `app/src/main/java/org/phioster/glyphsmith/effects/Interlace.kt`, `PixelSort.kt`,
  `SliceShift.kt`, `CrtWarp.kt`, `CmykHalftone.kt`
- Test: `app/src/test/java/org/phioster/glyphsmith/effects/EffectCatalogTest.kt`

**Interfaces:**
- Consumes: `AnimatableParam` and `EffectPass.animatable` from Task 4.
- Produces: no new API. Five new entries in `AnimTargets.all`.

**Why:** this is the user-facing payoff, and the proof that the category works. Five effects have
shipped since the animation system was written and not one of their parameters can be driven.
Each of these is one declaration inside the effect and nothing at all in `anim/`.

The parameters, chosen because each *moves* rather than merely changes:

| Effect | Parameter | Range | Cyclic | Salt |
| --- | --- | --- | --- | --- |
| `Interlace` | `shift` | 0..100 | no | 12 |
| `PixelSort` | `thresholdHigh` | 0..100 | no | 13 |
| `SliceShift` | `maxOffset` | 0..100 | no | 14 |
| `CrtWarp` | `warpCurvature` | 0..100 | no | 15 |
| `CmykHalftone` | `angle` | 0..90 | no | 16 |

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `the effects that ship animatable parameters declare them`() {
    val ids = EffectProviders.all.flatMap { it.pass.animatableIn() }.map { it.id }

    listOf(
        "anim.interlace-shift",
        "anim.pixel-sort-band",
        "anim.slice-offset",
        "anim.warp-curvature",
        "anim.halftone-angle",
    ).forEach { assertTrue("$it is not declared", it in ids) }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `... run.sh effects/EffectCatalogTest.kt`
Expected: FAIL on the first id.

- [ ] **Step 3: Declare them**

One per effect, for example in `Interlace`:

```kotlin
        animatable = listOf(
            AnimatableParam("anim.interlace-shift", "Interlace Shift", 0, 100, cyclic = false, salt = 12) { p, v ->
                p.copy(shift = v.coerceIn(0, 100))
            },
        ),
```

- [ ] **Step 4: Run the tests**

Run: `... run.sh effects/EffectCatalogTest.kt anim/AnimTargetsTest.kt anim/AnimationTest.kt`
Expected: PASS — including `no two targets share a salt` and `every registered target has a
valid wire id`, which now cover the five new ones without being edited. That is the whole claim
of the category, tested.

- [ ] **Step 5: Add a preset that uses one**

A capability nobody can find is not a feature. Add one MOTION preset to `data/PresetLibrary.kt`
driving `anim.warp-curvature` — a CRT that breathes — and let `PresetLibraryTest` prove it is
non-blank and reproducible like every other built-in.

- [ ] **Step 6: Commit**

```bash
git checkout -b anim-effect-parameters
git commit -m "Make the effects that were never animatable animatable"
```

---

## Self-review

**Spec coverage.** The brief asked for the palette category first — Task 1 answers it with the
audit and the decision not to write code, which is what the brief asks for in that case. It then
asks to move to the next category, which Tasks 2–5 do. Small independent PRs: five of them, each
with its own branch, its own test and its own reason to exist. Stable ids and preset
compatibility: Task 2 is entirely about that, and Task 4 is designed so the format does not move
when the Kotlin type does.

**No placeholders.** Every step names a file, and every code step carries the code. The three
`"..."` are PR body text, not implementation.

**Type consistency.** `AnimatableParam` (Task 4) is generic over the params type and lives in
`core/anim/`; `AnimatableTarget` (Task 4) is its erased form over `EffectStack` and lives in
`effects/`; `AnimTargetProvider` (Task 3, extended in Task 4) is the registry entry over
`RenderSettings`. Three types, three layers, each named after what it is. `salt` is an `Int`
everywhere and is frozen at 1–11 for the historic targets, continuing at 12 in Task 5.

**Known risk, stated rather than hidden.** Task 4 deletes an enum that four files use and
changes the type of a serialized field. It is the largest step here and the only one that cannot
be verified locally, because `AnimPanel` is Compose and the harness compiles no Compose. Push
early and let CI compile it.
