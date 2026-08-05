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

## Task 2: feature matrix — **open**

Goal: document the current product feature set.

Prompt:
> Build a feature matrix for Glyphsmith using the current codebase. Separate render modes, dither algorithms, effects, palettes, export paths, and workflow features. Do not implement anything new.

Acceptance criteria:

- matrix is specific and factual
- no speculative claims
- no code changes unless a generated source of truth is required
- counts agree with `PROJECT_STATE.md`, or `PROJECT_STATE.md` is corrected in the same PR

## Task 3: Dither Boy comparison — **open**

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

## Working rules

- one branch per task
- one PR per task
- run tests before merge
- keep changes minimal
- prefer adapters over rewrites
- if a task conflicts with the finished migration, stop and report the conflict
