# Glyphsmith Development Guide

## The documents, and what each is for

| File | Read it for |
| --- | --- |
| `CLAUDE.md` | this file — the rules that bind new work |
| `PROJECT_STATE.md` | where the project stands: verified counts, the package table, the compatibility invariants. The first read of a new session |
| `ARCHITECTURE.md` | how the application is built, and why each seam is where it is |
| `ROADMAP_V2.md` | which phases are done and which are open |
| `CLAUDE_TASKS.md` | the task queue, with what each finished task found |
| `MIGRATION_LEGACY.md` | project history: the completed migration, and what was deliberately not built |
| `README.md` | what the app does, for someone who is not going to read the source |

## Product identity

Glyphsmith is a native Android image and video dithering studio.

It is not primarily an ASCII generator.
It is not primarily a Script Slayer clone.
It must not be architected around glyph rendering.

Glyphsmith is a host application for:

- pixel dithering
- palette reduction and color mapping
- modular render styles
- stackable image effects
- layers
- animation
- image and video export

Glyph Art is one optional render module inside Glyphsmith.

## Primary workflow

The primary and default workflow is:

Source
-> preprocessing
-> sampling
-> dithering or quantisation
-> pixel rendering
-> effect pipeline
-> layers
-> animation
-> export

Pure pixel dithering is the default render workflow.

Glyph rendering is selected only through:

- explicit user action
- a Glyph Art preset
- a project that was previously saved in Glyph mode

General-purpose presets must not enable Glyph Art.

## Render modes

Glyphsmith currently has three output modes:

1. Pixel Dither (`render.pixel-dither`) — the default, general-purpose mode
2. Glyph Art (`render.glyph-art`)
3. Pixel Dither → Glyphs (`render.pixel-then-glyph`) — the chained mode: a
   palette dither runs first, and the glyph stage reads its result

Glyph Art maps quantised levels onto character ramps. It is a specialized
render module and must not own shared concepts such as palettes, dithering,
effects, animation, layers, sources, or export.

Code used by both modes must use neutral names and live outside the glyph
package. There is no ASCII package any more, and `LayeringTest` keeps it
that way.

Do not ask whether a mode is glyph-based by negating `PurePixel`. Ask
`RenderMode.isGlyph` or the mode's `RenderModuleProvider`, both of which are
exhaustive over the enum, so a fourth mode has to state what it produces
before it compiles.

## Product architecture

The intended dependency direction is:

Media Source
-> Source Adjustments
-> Sampling
-> Quantisation or Dithering
-> Render Module
-> Effect Pipeline
-> Layer Compositor
-> Export

Shared infrastructure must not depend on Glyph Art.

Glyph-specific code may depend on shared render infrastructure.

Shared render infrastructure must not depend on glyph-specific classes.

This is not advice — `LayeringTest` reads the `import` lines of `core`,
`render`, `glyph`, `pipeline` and `export` and fails on a dependency
pointing the wrong way. The table of prohibitions is in `ARCHITECTURE.md`.
A new *allowed* dependency is ordinary work and needs no change to the test.

## Internal plugin direction

Glyphsmith should use internal plugin-style modules.

A plugin-style module has:

- a stable identifier
- metadata
- a display name
- a category
- serializable parameters
- default parameters
- capability declarations
- an execution implementation
- optional UI controls
- compatibility information

Plugins are compiled into the application.

Do not implement downloadable executable third-party code unless a
separate task explicitly requests it.

Four provider categories are registered today — render modules, dither
algorithms, image effects and palettes. `Registry` checks id uniqueness and
format at construction, and `pipeline/Providers` gathers all four so the
rules can be stated once over everything.

They do not all carry the same thing, and the difference is deliberate:
`DitherProvider` carries its `algorithm` and `EffectProvider` its `pass`, so
the execution sits with the description instead of in a `when` far away from
it. `PaletteProvider` carries data — the palette and the shelf it is filed
under. `RenderModuleProvider` carries capabilities, `producesGlyphs` and
`ditherFirst`, because those are what the UI branches on.

**Export providers are the fifth category and do not exist yet, by
decision.** An exporter is chosen from a menu rather than named in a preset,
so it has nothing to be identified by. Adding one means adding stable ids to
the stored format, which is its own task.

A new provider goes in the registry that lives with its own code. Do not add
a `when` over its enum somewhere else instead.

**Effects are the worked example of that rule.** A new effect is one file plus
two lines — the slot→pass line in `effects/EffectPasses` and the slot→controls
line in `ui/panels/EffectPanels` — and it then appears in the FX chain, in the
panel, in presets, in saved and exported work and in Surprise Me without any
of those being edited. What a pass *is* travels with it: the stable id and the
label on the `EffectId` constant, the params slice it reads, how that slice is
written back, its toggle, its random roll and its code on its `EffectPass`.

Four couplings are kept on purpose, and `ARCHITECTURE.md`, *Adding an effect*,
says why each: the `EffectId` constant and its wire id (a stable identity must
not be generated), the `EffectStack` field (typed, serializable params), and
the two binding tables (exhaustive `when`s, for the `AppRenderModules`
reason — and the panel one cannot move into `effects/` without putting Compose
on the render path). Do not "tidy" any of the four into a map or a registry.

Do not add a `when (EffectId)` anywhere else. There were four; two of them are
gone, and the one that mattered most was in `state/RandomLook` — a file
outside the effect category, which meant a new effect was either an edit there
or an effect Surprise Me could never reach.

The one `when` that is not a violation of that is `AppRenderModules`, which
binds a `RenderMode` to the module that renders it. It is at the composition
root on purpose and is not a leftover: a map throws at the first frame in
whichever mode nobody tried, and a startup registration list is missing in
every test, quietly. The `when` refuses to compile until a fourth mode says
what renders it. Leave it where it is.

Presets are not executable plugins. A preset is a saved configuration of
available modules and effects.

## Default behavior

- New sessions default to Pixel Dither.
- New general presets default to Pixel Dither.
- Surprise Me defaults to Pixel Dither.
- Glyph Art is enabled only deliberately.
- New effects must work with Pixel Dither first.
- New shared features must not assume that a glyph ramp exists.
- Image effects operate on rendered pixels.
- Text and glyph-grid exports are available only when Glyph Art is active.
- UI panels must hide or disable controls that do not apply to the active mode — the
  *controls*, not the page they sit on. Hiding a whole panel for a capability that governs one
  section of it is what once left the default mode with no way to choose a dither algorithm.
  Ask the module for the capability, and hide the narrowest thing that answers to it.

## Preset direction

The built-in preset library must primarily demonstrate:

- classic error diffusion
- ordered dithering
- blue-noise and clustered-dot dithering
- print and halftone effects
- palette workflows
- pattern and modulation styles
- glitch effects
- layers
- animation

Glyph Art presets must remain available as a clearly named minority
category.

The target distribution for built-in presets is approximately:

- 80 to 90 percent Pixel Dither
- 10 to 20 percent Glyph Art

The curated library currently holds 83 presets, 74 Pixel Dither to 9 Glyph
Art, which is 89 percent. Adding glyph presets is the direction with room in
it; adding pixel ones narrows that margin.

Algorithm-comparison presets live in the Algorithm Lab, which is counted
separately from the curated library — 6 presets, one per kernel at otherwise
identical settings.

## Compatibility

Existing user presets and saved configurations must not silently change
appearance.

Legacy presets that do not contain a render mode must load as Glyph Art,
because they were created before Pixel Dither became the default.

New presets use Pixel Dither unless another mode is explicitly selected.

The preset format has all four of the things a default change required: an
explicit schema version (`PresetSchema.CURRENT_VERSION`, currently 4),
per-entry migrations that run in order, stable serialized identifiers
(`core/serial/WireId`), and tests for legacy loading. Any future default
change must keep them working, and raising the version means adding a
migration *and* a test that decodes a literal document of the old version.

`RenderSettings.renderMode` defaults to `GlyphMatrix` and
`RenderMode.DEFAULT` is `PurePixel`. These are two different questions —
what a preset naming no mode is read as, and what new work starts in — and
collapsing them turns the saved library into pixel dithers on first open.

Do not rename serialized enum entries without a migration or alias. Renaming
the enum constants themselves is safe: nothing writes them, the `WireId`
serialisers write the ids.

## Naming direction

Shared classes must use neutral product-level names.

The renames this section used to ask for are done:

- `AsciiParams` is `RenderSettings`
- `ascii.Pipeline` is `pipeline.RenderPipeline`
- the `ascii` package no longer exists
- the UI says *glyph art* and *pixel dither*, from the display names in
  `RenderModules` rather than a table each panel keeps

The enum constants `GlyphMatrix` and `PurePixel` keep their original
spelling deliberately. They are not user-facing and not stored.

Do not perform broad renames and behavioral changes in the same task.

Renames must be separate, testable refactoring steps.

## Reference products

Dither Boy and Script Slayer may be used only as public feature and
workflow references.

Script Slayer is not the primary product definition.

Do not copy:

- proprietary source code
- private implementation details
- commercial presets
- product assets
- fonts
- interface graphics
- proprietary names for Glyphsmith features

Glyphsmith must remain an independent implementation with its own product
identity.

## Development priorities

Prioritize work in this order:

1. deterministic and correct rendering
2. backward-compatible project and preset formats
3. Pixel Dither workflow
4. palette and color quality
5. effect-pipeline usability
6. export reliability
7. video and animation reliability
8. layer workflow
9. Glyph Art
10. additional algorithm count

Do not add algorithms merely to increase the advertised number.

Prefer:

- better organization
- better presets
- better performance
- clearer controls
- reliable export
- compatibility
- test coverage

over adding more modes.

## Change discipline

Every architectural change must:

1. preserve existing rendering unless a behavior change is explicitly requested
2. preserve existing presets through migration
3. include or update tests
4. avoid mixing unrelated refactors
5. explain changed serialization behavior
6. keep Pixel Dither and Glyph Art independently usable
7. avoid moving shared functionality into Glyph-specific packages

Do not delete existing algorithms, effects, presets, or export formats
unless the task explicitly requests deletion.

Do not weaken tests to make an implementation pass.

If existing behavior and this document conflict, report the conflict
before making a destructive or incompatible change.
