# Glyphsmith Migration Plan — completed

**Status: complete.** All ten steps are merged into `main`. This file is kept as the record of
what the migration set out to do and what each step actually became; the architecture it
produced is described in `ARCHITECTURE.md`, and the rules that still bind new work are in
`CLAUDE.md`.

Anything further is ordinary work, not a migration step.

## Goal

Move Glyphsmith from an ASCII-first application to a Pixel-Dither-first host application,
with Glyph Art fully supported as an integrated render module.

## The Steps, and What They Became

| # | Step | Landed as |
| --- | --- | --- |
| 1 | Preset schema versioning | `data/PresetSchema`, `CURRENT_VERSION` (PR #23) |
| 2 | Migration tests for legacy presets | `data/PresetSchemaTest` (PR #23) |
| 3 | Stable serialized provider IDs | `core/serial/WireId`, `RenderModeIds`, `DitherModeIds`, `EffectIds` (PR #25) |
| 4 | New-session default is Pixel Dither | `RenderMode.DEFAULT`, `RenderSettings.newSession()` (PR #24, tests PR #26) |
| 5 | Surprise Me defaults to Pixel Dither | `state/RandomLook` (PR #24) |
| 6 | Pixel-first preset library | 83 curated presets, 74 pixel / 9 glyph, filed by mechanism (PRs #27, #28, #33) |
| 7 | Rename shared ASCII-specific architecture | `AsciiParams` → `RenderSettings`, `ascii.Pipeline` → `RenderPipeline` (PR #29) |
| 8 | Internal provider registries | `core/provider/Provider` + `Registry`, four registries, `pipeline/Providers` (PRs #34, #40, #42, #44) |
| 9 | Shared code out of Glyph-specific packages | the `ascii` package dissolved into `render` / `glyph` / `pipeline`; `RenderModule`, `ModuleRender`, `RenderModuleSet`, `AppRenderModules` (PRs #30, #45) |
| 10 | Split the central ViewModel | `state/` — `HistoryController` (#35), `PresetController` (#38), `ExportCoordinator` (#39, #47), `PlaybackPlan` (#41), `SourceController` (#46) |

## Step 10 Was Cut Down On Purpose

Two components the plan named were examined and **deliberately not built**:

- **`RenderCoordinator`** — after step 9, `rebuild` is one call to `RenderPipeline.run`
  wrapped in `_state.value.copy`. The class would either take `_state` with it, and then it is
  the view model renamed, or need a callback for every field it sets.
- **`AnimationController`** — `PlaybackPlan` already took the testable half.

A `previewBudget()` helper was struck for the same reason: there is exactly one budget
*decision* in the app, and the remaining constants belong to different owners.

The criterion used throughout: **extract what has an invariant worth testing, not what is
long.** `GlyphsmithViewModel` staying a ~915-line coordinator is the intended end state.

## What Was Not Built, And Why

**Export providers.** `ARCHITECTURE.md` names them as a plugin category and
`ProviderCategory` has four entries rather than five. An exporter is chosen from a menu rather
than named in a preset, so it has nothing to be identified *by* yet. `export/Exports` is the
seam in the shape one would take, minus the ids and the registry.

## Compatibility Rules — All Held

- Existing presets retained their appearance. No migration changes how anything renders.
- Legacy presets without a render mode load as Glyph Art. `RenderSettings.renderMode` still
  defaults to `GlyphMatrix` for exactly this reason, while `RenderMode.DEFAULT` is `PurePixel`.
- New sessions and new general-purpose presets are Pixel Dither.
- Glyph Art presets select Glyph Art explicitly, via the `glyph()` helper.
- No existing algorithm or effect was rewritten during the migration; they were adapted into
  providers.
- No test was weakened or deleted.

## Commit Discipline — As Applied

Each step was a separate pull request, and schema migration, default changes, preset
replacement, renaming, package moves and the provider architecture were never combined in one.
Every step compiled and passed the tests before the next began.

That discipline is no longer a migration rule but a standing one; it is stated for future work
in `CLAUDE.md` under *Change discipline*.

## Verification

The migration's own definition of success is now checked by tests rather than by reading:

| Claim | Test |
| --- | --- |
| new sessions start in Pixel Dither | `render/SessionDefaultTest`, `UiStateDefaultTest` |
| old Glyph Art presets retain their appearance | `data/PresetSchemaTest` |
| Pixel Dither works without Glyph-specific state | `render/PurePixelTest`, `AppRenderModulesTest` |
| shared render code has no dependency on Glyph Art | `LayeringTest` |
| algorithms and effects use stable provider IDs | `core/serial/WireIdTest`, `pipeline/ProviderRegistryTest` |
| presets have schema versions and migrations | `data/PresetSchemaTest` |
| the UI uses provider capabilities | `pipeline/ProviderRegistryTest` |
