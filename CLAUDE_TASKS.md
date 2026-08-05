# CLAUDE_TASKS.md

## How to use this file

This file is the task queue for future Claude Code sessions. Each task should be small enough
to fit in a single safe PR.

Tasks carry a status. A task marked **done** stays here as the record of what was found, so the
next session does not repeat it; a task marked **open** is available to pick up.

## Task format

For each task, keep:

- goal
- scope
- files likely to change
- tests required
- acceptance criteria

## Task 1: baseline review — **done** (2026-08-04)

Goal: verify the current codebase state before new work starts.

Prompt:
> Read CLAUDE.md, PROJECT_STATE.md, ARCHITECTURE.md, and ROADMAP_V2.md. Inspect the current codebase and summarize the true current state. Do not change code.

Result: the review was run against the tree and found the documentation, not the code, to be
the thing out of date. The code matched every structural claim — three render modes, four
provider categories, the package layout, the boundary tests, all ten migration steps landed.
What did not match:

- the test count (572 documented, 589 actual, in 60 classes)
- "80 dither algorithms" (80 `DitherMode` entries, of which one is `NONE`)
- "providers carry metadata and execution", which is true of `DitherProvider` and
  `EffectProvider` but not of `RenderModuleProvider` or `PaletteProvider`
- `export/` described as owning export orchestration (it is the byte sink;
  `state/ExportCoordinator` orchestrates)
- `pipeline/` described as holding the shared pipeline nodes (they are in `core/pipeline/`)
- the third render mode `PixelThenGlyph` documented nowhere by name
- the two render-mode defaults documented nowhere at all

Those are corrected in `PROJECT_STATE.md`. Remaining risks are listed there under *Risks to
keep in mind*.

## Tasks 2 and 3: feature matrix and Dither Boy comparison — **done** (2026-08-05)

Both are answered by one audit, because the only honest way to build a feature matrix for a
comparison is to build it *against* the thing being compared.

**Method, so it can be repeated rather than re-derived.** Studio AAA's public material was
surveyed end to end: the free-downloads hub (19 entries), the development blog, ten individual
posts, and **thumbnails for all 118 videos on their channel**, harvested with
`yt-dlp --skip-download --write-thumbnail` and read as contact sheets. Videos 1–83 are Dither
Boy; 84–118 are Photoshop, Glitch Machine and Flareware and were excluded. Every claim below
was then checked against this repository rather than against memory.

**Nothing of theirs was copied.** Preset files were deliberately *not* downloaded or inspected:
a preset is their authored configuration in their format, and `CLAUDE.md` rules out commercial
presets and private implementation details. Where a parameter value mattered, it was read off a
control panel visible in a public tutorial — which is how the Epsilon Glow parity was
established. Their names are not used for Glyphsmith features.

### Present, and not to be rebuilt

Halftone family (CMYK, block, bit, print pattern, spot colour) · CRT curvature and scanlines ·
waveform glitch · video as a source · live camera · SVG and vector export · GIF and MP4 ·
retro handheld palettes and theme · Epsilon Glow (field for field, ranges included) · chromatic
aberration with three channel positions · JPEG databending · temporal variation · texture
derived from the input image · segmented animation timeline · pre-dither adjustments ·
**batch export** · **preset packs** · **palette favourites**.

The last three were listed as gaps in an earlier study and are wrong: `ExportCoordinator`
has `batchImage`/`batchStatus`, `PresetSchema.decode` returns a list so `importPresets` already
reads a pack, and `Settings.favouritePalettes` exists.

### Missing, in the order they are worth doing

| # | Gap | Evidence | Where |
| --- | --- | --- | --- |
| 1 | **No pre-dither value is animatable**, so the "dither in / dither out" transition — the source fading while the dither keeps running, the image dissolving into pure noise and re-forming — cannot be expressed at all. None of the 16 animation targets touches brightness, contrast or gamma. | videos 026, 076, 077; blog *Dither In/Out Animation Trick* | `anim/` |
| 2 | **Modulation and diffusion cannot be combined.** A modulation surface is a threshold and a diffusion kernel spreads error; theirs computes one style that is both. Ours are separate families, so the look is unreachable. | video 073, *Editable Modulation Style Diffusion* | `core/dither/` |
| 3 | **Modulation lines draw horizontally only.** Their demos show vertical. One direction parameter. | blog *Modulation Lines Demo*; videos 072, 073 | `effects/` |
| 4 | **Imported palettes are not kept at all.** Filed first as "a palette file holds one palette", which understated it: `importPalette` applies a file as `paletteOverride` and there is no store, so a pack has nowhere to go. Extending the format alone would ship the smaller half of a feature. See the note below. | their monthly palette packs | `data/` + `ui/` |
| 5 | **No importable threshold screen.** An image used as an ordered-dither matrix — non-executable, so it is not a runtime plugin. | video 060, *Custom Retro Shaders* | `core/dither/` |
| ~~6~~ | ~~Palette extraction is median-cut, not k-means.~~ **Struck: k-means is built.** | — | — |

### Two corrections to this audit, made the day it was written

**Item 6 was never a gap.** `QuantizeMethod.K_MEANS` exists, `ColorQuantizer.kMeans` implements
Lloyd's algorithm weighted by how often each colour occurs, the colour panel offers it in a
dropdown beside median cut, and `ColorQuantizerTest` covers it. It was carried forward from the
earlier transcript study without being checked — the exact failure this audit was written to
correct, repeated inside the audit. Anything sourced from that study and not verified against a
file should be treated as unknown, not as true.

**Item 4 is larger than it was filed as.** There is no palette store of any kind:
`GlyphsmithViewModel.importPalette` drops a file's colours into `paletteOverride`, which lives
inside whichever preset the user goes on to save. So the work is not "decode a pack" — it is a
place for imported palettes to live, a picker that shows them, and a decision about whether they
get stable ids. `ARCHITECTURE.md` records the current answer deliberately: *imported palettes
are not registered, they live in a file.* Reversing that is a product decision and needs its own
task, not a paragraph in this one.

### The finding that is not a gap

Their signature look — a glowing dot matrix on black, appearing across a dozen tutorials — is
reachable today and now ships as the `signal bloom` preset. The gap was never the capability.
It was that the look was a discovery here and a documented workflow there.

**"Force the ordered dither to fail"** (blog *Glitched Bitmap Effect Guide*) is classified as
**intentionally different**. The technique shrinks the cell below the pattern scale until their
beehive cells stop rendering and leave dotted lines. Glyphsmith's beehive was rendered at cell
1–6 against scale 6–26 and does not break: it stays a clean halftone whose dots merely grow.
Theirs draws a cell-sized shape and fails in the sub-pixel case; ours is a threshold surface.
Reproducing that look means implementing the failure deliberately, which is a product decision
and not a preset.

## Task 2: feature matrix — **superseded by the audit above**

Goal: document the current product feature set.

Prompt:
> Build a feature matrix for Glyphsmith using the current codebase. Separate render modes, dither algorithms, effects, palettes, export paths, and workflow features. Do not implement anything new.

Acceptance criteria:

- matrix is specific and factual
- no speculative claims
- no code changes unless a generated source of truth is required
- counts agree with `PROJECT_STATE.md`, or `PROJECT_STATE.md` is corrected in the same PR

## Task 3: Dither Boy comparison — **superseded by the audit above**

Goal: compare Glyphsmith with the public Dither Boy feature set.

Prompt:
> Compare the current Glyphsmith feature set with the public Dither Boy feature set. Categorize each item as present, partial, missing, or intentionally different. Do not implement features yet.

Acceptance criteria:

- comparison is fact-based
- differences are clearly labeled
- no product decisions hidden inside code changes
- public sources only, per `CLAUDE.md`, *Reference products*

## Task 4: first product slice — **done** (2026-08-05, PR #50)

Goal: implement the smallest high-value post-migration improvement.

Prompt:
> Pick one small product-phase improvement that fits the current architecture and implement it in one PR. Keep provider contracts stable and add tests.

The slice taken was the product audit's item 4.1: make the default mode usable. `TabRow` hid
the whole MAP tab unless the module produced glyphs, and no other panel offers the dither
picker — so in pixel dither, the app's primary workflow, there was no way to choose a dither
algorithm, nor to reach the tone curve, the pre-dither adjustments or any algorithm setting.

What it became:

- `ui/panels/MappingPanel` is two halves. `DitherMappingSection` is render-neutral — tone,
  pre-dither adjustments, dither style, strength, serpentine, pattern scale, the modulation
  and orb settings — and shows in every mode. `EdgeMappingSection` is glyph-only.
- Edge detection turned out to be the *only* glyph-specific thing on the page: the edge grid is
  computed in the shared `QuantisePass`, but `glyph/GlyphEngine` is the only thing that reads
  it.
- `ui/panels/MappingSections` holds the rule outside the composable and free of Compose, so it
  is testable without a UI host, and asks the module for `producesGlyphs` rather than comparing
  against a mode.
- `reset mapping` now writes only what the panel is showing, so the edge settings survive a
  reset done from a mode that hides them.

Follow-on slices are listed in `ROADMAP_V2.md`, phase 3.

## Task 4a: finish `reset mapping` — **done** (2026-08-05, PR #52)

Left over from task 4: `patternDensity` and `edgeSetId` were never cleared by `reset mapping`,
although both sit under a control the panel shows — the density slider a modulation style names
itself, and the edge glyph set. Reset a mapping and the pattern's second axis stayed where it
was, which reads as the button being broken rather than selective.

Both are cleared now, the glyph one still only where the glyph half is shown. The values are
taken from a default `RenderSettings` instead of literals repeated in the reset, so the button
cannot drift away from what a new session starts with. `orbSeed` stays untouched — it is the one
stored mapping value with no control on the page.

The test that states it is `after a reset in glyph art no mapping setting is left off its
default`, which compares the whole settings object rather than a list of fields: a forgotten
field is exactly what a hand-written list of assertions cannot catch, because the same hand
wrote the implementation.

## Task 5: maintenance guardrails — **open, partly already in place**

Goal: keep the architecture stable while new work happens.

Prompt:
> Add or update guardrail tests or docs that keep shared code glyph-neutral, keep stable IDs unchanged, and prevent accidental re-coupling of the UI or pipeline.

Already enforced, and not to be rebuilt:

| Rule | Test |
| --- | --- |
| dependency direction, read off the imports; no `ascii` package | `LayeringTest` |
| every mode has a module; only glyph modes produce glyphs | `AppRenderModulesTest` |
| every provider category registered; no two providers share an id | `pipeline/ProviderRegistryTest` |
| id format, uniqueness, legacy-name resolution, refusal of unknown ids | `core/serial/WireIdTest` |
| legacy preset migration and current-schema round trips | `data/PresetSchemaTest` |
| new sessions start in pixel dither; the field default stays glyph art | `render/SessionDefaultTest`, `UiStateDefaultTest` |
| which half of the mapping panel a mode shows, and what its reset writes | `ui/panels/MappingSectionsTest` |

Acceptance criteria:

- boundary rules remain enforced
- stable IDs remain protected
- no weakening of existing tests
- any new guardrail states a rule that no existing test states

## Task 6: the effect category as a compile-time plugin — **done** (2026-08-05)

Goal: make an effect addable without editing anything outside the effect category.

Prompt:
> Extend the existing effect architecture to a fully plugin-shaped, compile-time structure. Remove only unnecessary couplings; keep the necessary ones and document why. No reflection, no ServiceLoader, no dynamic classes.

**What was found.** Adding an effect meant editing nine places in five files, four of which
were `when (EffectId)` blocks:

| Where | What | Verdict |
| --- | --- | --- |
| `effects/EffectParams.kt` | params `data class` | necessary |
| `effects/EffectParams.kt` | the `EffectId` constant | necessary — stable identity |
| `effects/EffectParams.kt` | the `EffectStack` field | necessary — typed, serialized |
| `effects/EffectIds.kt` | `when` → wire id | **removed** |
| `effects/EffectPasses.kt` | `when` → implementation | kept, deliberate |
| `effects/YourEffect.kt` | the effect itself | necessary |
| `ui/panels/EffectSections.kt` | the panel | necessary |
| `ui/panels/EffectsPanel.kt` | `when` → panel | kept, deliberate, **moved** to `EffectPanels.kt` |
| `state/RandomLook.kt` | `when` → random ranges | **removed** |

The one that mattered most was the last: `state/RandomLook` is not part of the effect category,
so every new effect was either an edit to a file in `state/` or an effect Surprise Me could
never roll. Nothing failed when it was forgotten — the effect simply never appeared.

**What changed.**

- `EffectId` states its own `wireId` and `label`. A constructor argument refuses a slot without
  an id sooner than an exhaustive `when` did, and removes a file from the list.
- `EffectPass` gained `write` — the counterpart to `select`, stated beside it so the two cannot
  address different fields — and an optional `randomise`. Both keep `P` private, so a caller
  holding an `EffectPass<*>` can now *change* an effect it cannot name.
- Each effect declares its own Surprise Me ranges next to the sliders they narrow.
  `EffectProviders.randomisable` is the whole of what `RandomLook` knows about effects; the roll
  is bit-identical to the old one, which was checked over 2 000 seeds against a replay of the
  hand-written version before that replay was deleted.
- The panel binding moved out of `EffectsPanel` into `ui/panels/EffectPanels`, so the panel file
  is about the chain and the table is one thing in one place.
- `LayeringTest` gained an `effects` row: the category may not depend on `render`, `glyph`,
  `pipeline`, `state`, `ui`, `data`, `export` or `anim`. That is what keeps the shape true.

**What stayed static, and why.** Four couplings, three different reasons — the identity of a
slot must not be generated, its params must stay typed and serializable, and the two binding
tables are exhaustive `when`s for the `AppRenderModules` reason. The panel one additionally
*cannot* move into `effects/`: a pass carrying a `@Composable` would put Compose on the render
path and make every effect need a UI toolkit to be unit-tested. Written up in
`ARCHITECTURE.md`, *Adding an effect*.

**Tests.** `effects/EffectCatalogTest`, 12 methods: one provider per effect, one pass per
provider and none shared, registered exactly once in enum order, present in every list that has
to hold it, id and label read off the constant, a duplicate registration refused at
construction, a roll that switches on its own effect and nothing else, rolls that stack, and
every shipped effect offering one.

## Task 7: the next plugin category — animation targets — **done in substance** (2026-08-05)

Goal: decide whether `AnimTarget` should become a provider category, and if so, make a new
effect animatable without editing `anim/`.

What is already known, so the next session does not re-derive it:

- A new effect is **not** automatically animatable. `anim/AnimTarget` is a hand-written enum and
  `Animator.apply` is a `when` over it saying which field each target writes. Five of the eleven
  targets write into `RenderSettings.effects`.
- **The blocker is serialization, not design.** `AnimTrack.target` and `AnimSegment.target`
  serialize `AnimTarget` as the *Kotlin constant name* — it is the one identity in the app that
  never got a `WireId`. Making targets pluggable means giving them stable ids first, which is a
  preset-schema change with a migration and a literal-document test, i.e. its own task under the
  compatibility rules. It must not be smuggled in with the feature.
- Not every effect parameter is worth animating, so a plugged-in target list should be opt-in
  per parameter rather than generated from the params class.

Acceptance criteria:

- stable ids for animation targets, with a migration and a test that decodes a document written
  with the old constant names
- no change to how any existing animation renders
- an effect can declare an animatable parameter without `anim/` being edited

### Status, 2026-08-05: two thirds done, the last third deliberately deferred

- **Stable ids — done** (PR #55). `AnimTarget.wireId` and `AnimTargetIds`. No schema bump: the
  migration would provably rewrite nothing, and `PresetSchema`'s KDoc says why.
- **The `when` — done** (PR #56). `anim/AnimTargets` holds one provider per target carrying the
  field it writes. The `ordinal` that silently salted the RANDOM curve is now an explicit,
  tested number.
- **Five new animatable parameters — done** (PR #57). Interlace shift, pixel-sort band, slice
  offset, CRT curvature, halftone angle.
- **"Without `anim/` being edited" — deferred, on purpose.** Adding a target is now two
  compiler-checked lines in two files, both in `anim/`. Removing that last step means deleting
  the `AnimTarget` enum, which costs ~100 call sites in `PresetLibrary` and the tests and turns
  typed constants into string ids — and buys **no correctness**: unlike the effect category,
  where a slot could read another effect's toggle and Surprise Me could silently miss an effect,
  there is no silent failure mode left here. The registry test catches a missing salt and the
  enum makes an unregistered target a compile error.

  Reopen this when an effect genuinely needs to *own* an animatable parameter — a downloadable
  or third-party-authored effect would, and nothing else does. The design, including the
  `core/anim/AnimatableParam` placement that keeps `effects/` from importing `anim/`, is written
  up in `docs/superpowers/plans/2026-08-05-animation-target-plugins.md`, task 4.

## Task 8: palette category audit — **done, no code** (2026-08-05)

Goal: bring the palette category to the maturity the effect category reached.

Prompt:
> Bring the palette architecture to the same maturity as the finished effect plugin architecture. If it is already modular enough, document that and move to the next category instead of producing artificial code.

Result: **it is already there, by a different route, and nothing was written.**

- Adding a built-in palette is one line in `core/color/Palettes.kt`. The registry, the wire id,
  the picker's categories, presets, the schema migration and Surprise Me are all derived from
  that line.
- A palette carries no behaviour. The defect the effect work removed — a constant mapped to code
  by hand in several places — cannot arise where there is no code to map to.
- `PaletteProviders` is not on any read path, and that is deliberate: routing the picker through
  it would change nothing observable. Written down in `ARCHITECTURE.md` so the next session does
  not "fix" it.
- Imported palettes are not second-class. They are applied as `paletteOverride` and therefore
  already survive saving and sharing; naming and shelving them is a product question.

The next category is task 7, and the plan for it is
`docs/superpowers/plans/2026-08-05-animation-target-plugins.md`.

## Tasks 9 to 14: the audit's gaps — **five closed, one open** (2026-08-05)

Numbered in the order of the table in tasks 2/3, which is the order they are worth doing. Each
is small enough for one PR and stays inside one category.

| Task | State |
| --- | --- |
| 9 — a pre-dither value that can be animated | **done**, PR #60, with the `forming` preset |
| 10 — a style that modulates *and* diffuses | **done**, PR #63, two styles |
| 11 — modulation lines with a direction | **done**, PR #61 |
| 12 — somewhere for an imported palette to live | **open**, and the only one |
| 13 — an importable threshold screen | **done**, PR #64 |
| 14 — k-means | **struck**: it was built all along |

Presets for the four mechanisms that shipped without one followed in PR #65, and PR #66 taught
`reset mapping` about the screen — a defect introduced the same day the rule against it was
already written down.

The original wording of each is kept below.

**Task 9 — a pre-dither value that can be animated.** Add an animation target for the source
brightness so a dither-in/out transition is expressible. `brightness` is a `Float` and a target
carries `Int` bounds, so the target has to state its mapping; pick one and write it down.
Acceptance: an animation drives it, nothing else changes, and a preset demonstrates it.

**Task 10 — a style that modulates *and* diffuses.** `DitherAlgorithm` is a sealed class whose
kinds are the mechanisms; this needs a kind that uses a modulation surface as the threshold and
still passes error to neighbours. New ids, no renames. Acceptance: existing styles render
identically, and the new one is in the registry with a test.

**Task 11 — modulation lines with a direction.** One parameter on `ModulationLinesParams`,
defaulting to what it draws today so no preset changes.

**Task 12 — somewhere for an imported palette to live.** The format is the small half; the
missing half is a store, a picker that lists it, and an answer to whether an imported palette
gets a stable id — which `ARCHITECTURE.md` currently answers with a deliberate no. Decide that
first, in writing, then build. A pack format without a store ships half a feature.

**Task 13 — an importable threshold screen.** An image read as an ordered matrix. Not
executable, so it is not a runtime plugin — but it *is* a new identity in the preset format, so
it needs a stable id and a decision about what happens when the screen is missing.

**Task 14 — struck.** k-means is built and always was.

## Working rules

- one branch per task
- one PR per task
- run tests before merge
- keep changes minimal
- prefer adapters over rewrites
- if a task conflicts with the finished migration, stop and report the conflict
