# Glyphsmith Architecture

Describes the application as it is built today. The migration that produced this shape is
recorded in `MIGRATION_LEGACY.md`; the rules that still bind new work are in `CLAUDE.md`.

## Product Direction

Glyphsmith is a native Android host application for:

- pixel dithering
- palette mapping
- stackable effects
- layers
- animation
- image and video export
- optional Glyph Art

Pixel Dither is the primary workflow.

Glyph Art is an integrated render module. Shared rendering, palettes, effects, animation,
layers, and image export do not depend on Glyph Art, and a test enforces it — see
*Layering Rules*.

## Processing Pipeline

Media Source  
-> Source Adjustments  
-> Sampling  
-> Dithering or Quantisation  
-> Render Module  
-> Effect Pipeline  
-> Layers  
-> Preview or Export

`pipeline/RenderPipeline.run` is the single entry point. The live preview, the still export,
every animation frame and every preset thumbnail go through it and differ only in `maxSide`
and in the `isScrubbing` flag, so a frame can never be produced by a slightly different code
path than the preview that sold it to the user.

The effect chain and the layer compositor run *in* the pipeline, once, on whatever the render
module produced. A module does not apply effects itself — three modules applying them
identically was three chances to drift on the order.

## Render Modules

Three modules ship, one per `RenderMode`:

| Mode constant | Stable id | Display name | Produces glyphs | Dithers first |
| --- | --- | --- | --- | --- |
| `GlyphMatrix` | `render.glyph-art` | glyph art | yes | no |
| `PurePixel` | `render.pixel-dither` | pixel dither | no | yes |
| `PixelThenGlyph` | `render.pixel-then-glyph` | pixel dither → glyphs | yes | yes |

`PurePixel` is the default for new sessions. `PixelThenGlyph` is the chained mode: the image
is dithered to a palette first and the glyph stage then reads the result as an ordinary
image, which is the only way to get a paletted dither *and* a `.txt` out of one render.

`RenderMode.isGlyph` and `RenderMode.ditherFirst` are exhaustive `when` expressions rather
than negations, so a fourth mode does not compile until somebody has said what it produces.

The glyph modes may additionally produce:

- a glyph grid
- plain text
- ANSI
- HTML
- text SVG
- outline SVG

Shared code does not assume that every render module provides glyph output — see
*RenderModule System*.

### RenderModule System

The seam has four parts, and they are deliberately separate:

- **`render/RenderModule`** — the execution half. `render(pixels, sourceWidth, sourceHeight,
  params, maxSide): ModuleRender`. Nothing else.
- **`render/ModuleRender`** — a render before effects and layers: the bitmap, the grid the
  module worked on (`cols`, `rows`), the size the same settings would produce at full export
  scale (`outputWidth`, `outputHeight`), and an optional `RenderModuleOutput`.
- **`render/RenderModuleOutput`** — an empty marker interface. The pipeline carries this value
  from the module that made it to the caller that asked for the render and cannot know what is
  inside it. Glyph Art's implementation is `glyph/GlyphRenderOutput`, which holds the character
  grid and the typeface it was drawn with. A caller that wants the glyph half asks for it by
  type; the pipeline never mentions either.
- **`render/RenderModuleSet`** — a `fun interface` mapping a mode to a module. The pipeline
  takes one as an argument.

The binding itself is `AppRenderModules`, at the top level of the package beside the view
model and the activity, because it is the one place that names Glyph Art and the pixel module
in the same breath. It is a `when` over the enum, not a map and not a registration list filled
in at startup: a map throws at the first frame in whichever mode nobody tried, and a startup
registration is missing in every test, quietly.

`RenderModuleProvider` is the declarative half — id, display name, `producesGlyphs`,
`ditherFirst` — and is read by the UI and by tests that run without an Android runtime.

A panel asking that question hides the controls the answer governs and nothing wider. The
render, mapping and output panels each hide a *section*; the tab row hides nothing at all.
`ui/panels/MappingSections` is that rule written down for the mapping panel, kept out of the
composable and free of Compose so it can be tested without a UI host.

## Package Layout

```
core/       Knows no render module at all.
  color/    Palettes, PaletteProviders, ColorDistance, PaletteQuantizer
  dither/   DitherMode, DitherAlgorithm(s), DitherProviders, the kernels, matrices,
            screens, modulation surfaces, region and polygon methods
  image/    Pixels, Adjustments
  pipeline/ ImageProcessorNode, NodePipeline, BufferPool, RowParallel, RenderContext
  provider/ Provider, ProviderCategory, Registry
  serial/   WireId, WireIdSerializer
render/     Shared render infrastructure: RenderSettings, RenderMode(+Ids), CellGrid,
            CellSampler, QuantisePass, EdgeDetect, Layers, RenderBudget,
            PixelDitherRenderer, ColorDiffusionPass, PixelDitherModule,
            RenderModule/ModuleRender/RenderModuleSet, RenderModuleProviders
glyph/      The Glyph Art module: CharacterSets, GlyphEngine (pure Kotlin), GlyphRenderer
            (Canvas), Fonts, GlyphCoverage, GlyphRamp, EdgeGlyphs, GlyphFromBitmap,
            GlyphRenderModules, and Glyph Art's own outputs — TextExporters, SvgExporter
effects/    EffectId/EffectIds, EffectParams, EffectStack, EffectPass, EffectPasses,
            EffectNodes, EffectProviders, EffectPipeline, PixelOps and the passes
pipeline/   RenderPipeline, LayerCompositor, Providers
anim/       Animation tracks and segments, Temporal, GifEncoder, Mp4Encoder, ColorQuantizer
data/       Source (still/video), ImageLoader, LiveCamera, CameraCapture, PresetLibrary,
            PresetStore, PresetSchema, PaletteFile, Import, Settings
state/      SourceController, HistoryController, PresetController, ExportCoordinator,
            PlaybackPlan, RandomLook
export/     Exports (the sink interface) and Exporter (MediaStore/FileProvider)
ui/         Terminal-styled Compose panels and the theme
```

The composition root — `AppRenderModules`, `GlyphsmithViewModel`, `MainActivity`,
`GlyphsmithApp` — sits at the top level.

## Layering Rules

Stated as prohibitions, and checked by `LayeringTest` reading the actual `import` lines. A new
*allowed* dependency is ordinary work; a dependency pointing the wrong way fails the build.

| Layer | May not depend on |
| --- | --- |
| `core` | `render`, `glyph`, `pipeline`, `state`, `ui`, `data`, `export`, `anim`, `effects` |
| `render` | `glyph`, `pipeline`, `state`, `ui`, `data`, `export` |
| `glyph` | `pipeline`, `state`, `ui`, `data`, `export` |
| `effects` | `render`, `glyph`, `pipeline`, `state`, `ui`, `data`, `export`, `anim` |
| `pipeline` | `glyph`, `state`, `ui`, `data`, `export` |
| `export` | `glyph`, `pipeline`, `state`, `ui` |

Three consequences worth naming:

- The pipeline runs Glyph Art without knowing that Glyph Art exists. The mode→module binding
  is the application's and arrives as an argument.
- `export/` is a byte sink. The text, ANSI, HTML and SVG writers are Glyph Art's own output
  and live in `glyph/`.
- `effects/` reads pixels and knows the engine, and nothing else — not what rendered them, not
  where they are going, and not Compose. An effect is the thing this codebase adds most often,
  so the category is kept closed on purpose: adding one stays inside it.

The same test asserts that no `org.phioster.glyphsmith.ascii` package exists and that no
source names it. Its shared half became `render`, its glyph half `glyph`, and the part that
picks between them `pipeline`.

## Internal Plugin Model

Plugins are internal and compiled into the APK. No arbitrary executable third-party code is
loaded.

`core/provider/Provider` is metadata and nothing else: a stable `id`, a `displayName`, and a
`ProviderCategory`. The enums stay where they are and keep doing the work; a provider says
what a thing is *called* and what it *can do*, so the picker, the tests and the UI ask one
uniform question instead of each switching over a different enum.

`Registry<P : Provider>` checks its rules at construction rather than in a test that has to
remember to look — the registry is non-empty, no two providers share an id, every id is
well-formed, every id carries its own category prefix. A broken build therefore fails on its
first launch, not on the first preset somebody saves with it. `find` returns null for an
unknown id; `require` throws `UnknownWireIdException` rather than substituting a default, for
the same reason the serialisers do: an unknown dither is not "no dither".

### Provider Registries

Four categories are registered. Each registry lives with the code it describes, so `core` need
not know what a render module is and `render` need not know what a glyph is:

| Category | Prefix | Registry | Contents |
| --- | --- | --- | --- |
| `RENDER` | `render` | `render/RenderModules` | 3 render modules, in enum order |
| `DITHER` | `dither` | `core/dither/DitherProviders` | 80 `DitherMode` entries (79 algorithms + `NONE`) |
| `EFFECT` | `effect` | `effects/EffectProviders` | 17 passes, in the chain's default order |
| `PALETTE` | `palette` | `core/color/PaletteProviders` | 44 built-in palettes |

`pipeline/Providers` gathers all four — the only place allowed to see them at once — so the
rules about ids can be stated once over everything rather than over a list somebody has to
remember to extend. A category that is not registered fails `ProviderRegistryTest` rather than
going quietly uncovered.

**Export providers are named in the plugin model and are still absent, by decision.** An
exporter is chosen from a menu rather than named in a preset, so it has nothing to be
identified *by* yet. `ProviderCategory` has four entries, not five.

Stable ids do not depend on enum names, class names, package names or translated labels.
Actual examples from the build:

- `render.pixel-dither`, `render.glyph-art`, `render.pixel-then-glyph`
- `dither.floyd-steinberg`, `dither.bayer-8`
- `effect.glow`, `effect.pixel-sort`
- `palette.grayscale`

`WireIdTest` holds them to their format, asserts they are not merely the enum names in lower
case, and checks that every legacy enum name still resolves to the same value.

### Dither Providers

A `DitherProvider` carries the mode, its `family` (`DitherCategory` — the shelf a picker
groups it under) and its `algorithm`. The provider carries the *implementation*; it began as
description only, while a dozen `when (mode)` dispatches did the work.

`DitherAlgorithm` is a sealed class describing how a style decides a cell — a mechanism, not a
look. Two styles that draw nothing alike are the same kind if they resolve a cell the same
way, which is the distinction the render loop actually needs. The kinds are:

- `NoDither` — the level is whatever brightness rounds to
- `OrderedMatrix` — a threshold read off a fixed repeating tile (Bayer, clustered dot,
  blue noise; the matrices are generated from their construction rules)
- `Modulation` and `ModulationCell` — a threshold that is a continuous function of position
- `ErrorDiffusion` — error passed to neighbours, optionally serpentine
- `Precomputed` — the algorithm resolves the whole grid itself (Riemersma's Hilbert walk, dot
  diffusion, the region and polygon methods)

This axis is deliberately *not* `DitherCategory`. A glitch and a halftone can share a
mechanism, and two ordered matrices can sit on different shelves.

The families and their sizes:

| `DitherCategory` | Count |
| --- | --- |
| Basic (`NONE` only) | 1 |
| Error Diffusion | 21 |
| Ordered | 8 |
| Patterned | 18 |
| Polygon | 4 |
| Glitch | 12 |
| Special | 16 |

`DitherMode.category` is an exhaustive `when` rather than a constructor argument, so the
compiler refuses to build once a style is added without saying where it belongs.

Lookup is by ordinal, not by hash: asking a style for its threshold means asking the provider
for its algorithm first, and that is on the per-cell path.

### Effect Providers

Effects operate on the bitmap a render module produced. They are order-dependent, have
serializable parameters, are deterministic for the same input, seed and time, and do not
touch the logical glyph grid — so `.txt`, `.ansi`, `.html` and `.svg` are unaffected by them.

`EffectPass<P>` declares everything a pass needs as one declaration inside the effect's own
object: the slice of `EffectStack` it is configured by, how that slice is written back, the
flag in it that switches it on, how it is rolled at random, and the code. Those used to be
separate `when (EffectId)` blocks a long way from the effect, so a slot could run one effect
and read another's toggle and still compile. `P` is erased at the interface, because the chain
runs seventeen passes with seventeen unrelated params types and must not know one of them —
which is also what lets a caller *change* an effect it cannot name.

#### Adding an effect

The effect category is the worked example of the internal plugin model. A new effect is one
new file plus two lines, and everything else follows on its own:

| Written | Where |
| --- | --- |
| params `data class` | `effects/EffectParams.kt` |
| an `EffectId` constant, with its wire id and label | `effects/EffectParams.kt` |
| a field on `EffectStack` | `effects/EffectParams.kt` |
| the implementation and its `EffectPass` | `effects/YourEffect.kt` |
| the panel | `ui/panels/EffectSections.kt` |
| *slot → pass* | `effects/EffectPasses.kt` |
| *slot → controls* | `ui/panels/EffectPanels.kt` |

Nothing else. The chain, the toggle, the reorder buttons, the FX panel, the preset format, the
saved file, the export and Surprise Me all read the registry or the stack and pick a new effect
up without being edited.

**Four things are deliberately not automatic**, and each is a different reason:

- The **`EffectId` constant** and its **wire id** are the effect's stable identity. A preset's
  effect *order* is a list of these, so every preset ever saved carries all of them. An
  identity that is generated is an identity that can change.
- The **`EffectStack` field** keeps the params typed and serializable. A map of params keyed by
  id would take the compiler out of the wiring and change the preset format for nothing.
- **`EffectPasses`** binds a slot to its implementation, and **`ui/panels/EffectPanels`** binds
  it to its controls. Both are exhaustive `when`s for the reason `AppRenderModules` is one: a
  map is missing an entry until somebody opens that panel in the one build nobody tried,
  whereas a `when` stops the build. The panel binding additionally *cannot* move into
  `effects/` — a pass carrying its own `@Composable` would put Compose on the render path and
  make every effect need a UI toolkit to be unit-tested.

`EffectCatalogTest` states the claim as tests: one provider per effect, one pass per provider,
each registered exactly once, a duplicate registration refused at construction, every effect
present in every list that has to hold it, and every roll switching on its own effect and
nothing else.

Execution is a data pipeline, not a call sequence:

`EffectPipeline.apply` converts the bitmap to `Pixels` → `EffectNodes.of(stack)` builds the
ordered node list from `EffectStack.order` → `NodePipeline.run` walks it, skipping disabled
nodes → the result is converted back. Each node either mutates its buffer and returns it or
returns a new one; the loop reassigns either way and returns the old buffer to the
`BufferPool` when identity says a new one was produced. Keeping the Android conversion at the
edges is what leaves every effect unit-testable.

The seventeen passes, in the chain's default order: post processing, blur/sharpen, tint,
chromatic aberration, JPEG databending, pixel sort, slice shift, interlace, modulation lines,
diffraction stars, subtexture, spot-colour print, CMYK halftone, colour depth, blue-noise
dither, glow, CRT warp.

### Palette Providers

A `Palette` keeps its own short id — `grayscale` — which the picker, the favourites and the
category listings key on. The *wire id* is `palette.grayscale`, and that is what a saved
preset carries. Both spellings resolve, because files written by older builds contain the bare
one. Imported palettes are not registered: they live in a file.

**The palette category is complete as it stands, and was audited on 2026-08-05 rather than
rebuilt.** Adding a built-in palette is one line in `Palettes.all`; from there the registry, the
wire id, the picker's categories, presets, the schema migration and Surprise Me all follow
without another edit. There is no per-palette behaviour, so the failure the effect category was
opened up to remove — a constant mapped to code by hand in several places, the copies drifting
apart — has no counterpart here and never did. A palette is data.

`PaletteProviders` is deliberately **not** on the read path. The picker reads
`Palettes.categories` and `Palettes.inCategory`, `RandomLook` reads `Palettes.all`, and the
renderers read `Palettes.byId`; the registry is read by `pipeline/Providers`, which holds the
ids to their rules. Routing the UI through the registry instead would change nothing a user or
a test can observe, so it is not done.

Imported palettes stay out of the registry on purpose. `GlyphsmithViewModel.importPalette`
applies a file as `paletteOverride` — a colour list carried inside the preset — so an imported
palette already survives saving, sharing and reloading. Giving it a name and a shelf of its own
is a product decision, not an architectural gap.

A new palette *category* is free text on the palette. The `const val` names in `Palettes` are a
convention that keeps the built-ins consistent, not a registry that has to be extended.

## Presets

A preset is a saved configuration of available providers — not executable code, not a plugin
implementation. It always stores the *complete* `RenderSettings`, effects, animation, temporal
and layers included, so applying one is a single action.

The shipped library is **92 presets**:

- **86 curated**, filed by mechanism across 11 categories — Classic Dither (5), Error
  Diffusion (6), Ordered Dither (5), Pattern (11), Print (7), Geometry (5), Color (5), Glitch
  (8), Motion (23), Layered (2), Glyph Art (9).
- **6 Algorithm Lab**, kept off the curated shelves and counted separately: one preset per
  kernel at otherwise identical settings.

The curated split is **77 Pixel Dither to 9 Glyph Art** — 89 % to 11 %, inside the 80–90 / 10–20
target. Built-in presets name their mode explicitly via the `pixel()` and `glyph()` helpers,
so they depend on neither of the two defaults below.

## Compatibility

The stored schema is at **version 4**. `data/PresetSchema` reads any known version; migrations
run per entry and in order, so a version 1 file is carried through every step in turn.

- **1** — a bare JSON array, no version anywhere. Predates `RenderMode`.
- **2** — `{"schemaVersion": 2, "presets": [...]}`, so the version has somewhere to live.
- **3** — render modes, dither styles and effects named by stable ids rather than by Kotlin
  enum constants.
- **4** — a palette named the same way: `palette.grayscale` rather than `grayscale`, inside
  layers too. Both spellings are still read.

No migration changes a preset's appearance; the same things are being named, in a spelling
that no longer moves when the source does.

A version 1 preset without a stored render mode loads as Glyph Art, because it was created
before Pixel Dither became the default. A file claiming a *newer* version is still read entry
by entry, and an entry that will not read costs only itself.

An unknown dither, render mode or effect id is **refused**, not remapped — the preset holding
it is dropped and the rest of the library survives.

**Two defaults that must stay different values.** `RenderSettings.renderMode` defaults to
`GlyphMatrix`: that is what a preset naming no mode is read as, and every preset written
before the field existed is that case. `RenderMode.DEFAULT` is `PurePixel` and is what new
sessions and `RandomLook` start from. Collapsing them turns the saved library into pixel
dithers on first open.

## Naming

The renames the migration called for are done. `AsciiParams` is `RenderSettings`,
`ascii.Pipeline` is `pipeline.RenderPipeline`, the `ascii` package no longer exists, and the
UI says *glyph art* and *pixel dither* — the display names live in `RenderModules` rather than
in a table each panel keeps.

The enum constants `GlyphMatrix` and `PurePixel` deliberately still carry their original
spelling. Renaming them changes no stored byte, because `RenderModeIds` writes the id.

Naming refactors and behaviour changes stay separate tasks.

## UI

The tab row is eight entries:

`SET` · `MAP` · `COLOUR` · `FX` · `LYR` · `ANIM` · `OUT` · `PRE`

`MAP` — the glyph mapping panel — is filtered out of the row entirely when the active mode
does not produce glyphs, and the selection falls back to `SET` if it was showing. Whether a
mode produces glyphs is asked of `RenderModules`, not derived from the enum at the call site.

When Pixel Dither is active, glyph controls are hidden, text exports are unavailable and no
glyph ramp is required. When a glyph mode is active, glyph controls and text exports are
available and the shared dither, colour, effects, layers and motion controls remain so.

## State Ownership

`GlyphsmithViewModel` is a ~915-line coordinator, and that is the intended end state rather
than a leftover. What was extracted from it is what had an invariant worth testing:

| Component | Owns |
| --- | --- |
| `state/SourceController` | the loaded source, the preview position, `frame()`, closing the old source |
| `state/HistoryController` | undo/redo |
| `state/PresetController` | applying, saving and deleting presets |
| `state/ExportCoordinator` | what happens to a finished render and what is said about it |
| `state/PlaybackPlan` | the testable half of animation playback — frame counts and budgets |
| `state/RandomLook` | Surprise Me, within ranges that produce something — the *effect* ranges belong to the effects, which declare their own rolls |

Two components named in the original plan were **deliberately not built**, and should not be:

- **`RenderCoordinator`** — after the render-module split, `rebuild` is one call to
  `RenderPipeline.run` wrapped in `_state.value.copy`. The class would either take `_state`
  with it, and then it is the view model renamed, or need a callback for every field it sets.
- **`AnimationController`** — `PlaybackPlan` already took the testable half and says so in its
  own KDoc.

A `previewBudget()` helper was considered and struck for the same reason: there is exactly one
budget *decision* in the app, and the other constants belong to different owners —
`RenderBudget.MAX_OUTPUT_SIDE` is a platform ceiling, `LiveCamera.MAX_SIDE` is a callback-rate
limit, `PlaybackPlan.budgetSide` comes from a memory budget.

The criterion throughout: **extract what has an invariant worth testing, not what is long.**

Image-processing algorithms do not live in the ViewModel.

## Testing

61 JVM test classes, ~595 test methods, run by `gradle testDebugUnitTest`. The engine is free
of Android types, so sampling, the tone curve, the dither path and ramp mapping are all
testable on the JVM; three classes need Robolectric.

The architectural rules each have a test that states them:

| Rule | Test |
| --- | --- |
| dependency direction, read off the imports | `LayeringTest` |
| every mode has a module, and only the glyph modules produce glyphs | `AppRenderModulesTest` |
| every provider category is registered; no two providers anywhere share an id | `pipeline/ProviderRegistryTest` |
| id format, uniqueness, legacy-name resolution, refusal of unknown ids | `core/serial/WireIdTest` |
| legacy preset migration, current-schema round trips, unknown providers | `data/PresetSchemaTest` |
| new sessions start in pixel dither; the field default stays glyph art | `render/SessionDefaultTest`, `UiStateDefaultTest` |
| presets render non-blank, reproducibly and mutually distinct | `data/PresetLibraryTest` |
| a pass runs its own effect with its own settings, in the stored order | `effects/EffectPassTest`, `effects/EffectNodeContractTest` |
| the chained mode | `render/ChainedModeTest` |
| which half of the mapping panel a mode shows, and what its reset writes | `ui/panels/MappingSectionsTest` |
| kernels against the published literature | `core/dither/LiteratureTest`, `DitherRegressionTest` |

Note what a JVM test cannot reach: several effect passes go through
`android.graphics.Bitmap`/`Matrix` and can only be compared as settings. The dither path
(`CellSampler` → `QuantisePass` → `PixelDitherRenderer`) is pure Kotlin and *is* rendered in
tests.

CI additionally runs `gradle detekt` (config in `config/detekt/detekt.yml`) and
`gradle lintDebug`.

Tests are not removed or weakened to make an implementation pass.

## Non-Goals

Still out of scope:

- deleting Glyph Art
- deleting existing algorithms or effects
- copying proprietary Dither Boy behaviour
- loading arbitrary downloaded code
- dynamically generating the entire UI
- breaking old presets
