# PROJECT_STATE.md

Where the project stands. `ARCHITECTURE.md` describes how it is built, `CLAUDE.md` states
the rules that bind new work, `MIGRATION_LEGACY.md` records the migration that produced this
shape. This file is the snapshot, and every number in it was read off the code rather than
carried forward from the last time somebody wrote one down.

Verified against the tree on 2026-08-05.

`MIGRATION_LEGACY.md` is the only document filed as history. `ARCHITECTURE.md` briefly carried
a `_LEGACY` suffix too, which was wrong about its contents: it describes the architecture the
app has today and is still where the layering rules are written down.

## Current status

Glyphsmith has completed the architecture migration. All ten steps are merged; the app is in
the product phase.

The old ASCII-first shape has been replaced by a provider-driven render architecture with
optional Glyph Art support. There is no `ascii` package, and `LayeringTest` keeps it that way.

## Current headline numbers

- **Render modes: 3.** `GlyphMatrix` (`render.glyph-art`), `PurePixel` (`render.pixel-dither`)
  and `PixelThenGlyph` (`render.pixel-then-glyph`). `PurePixel` is what new sessions start in;
  `PixelThenGlyph` is the chained mode, dithering to a palette first and reading the result as
  glyphs.
- **Dither styles: 80 `DitherMode` entries — 79 algorithms plus `NONE`.** `NONE` is not an
  algorithm. It is *no dithering at all*, sits alone in `DitherCategory.BASIC`, and is
  registered like the rest so that a picker and a preset can name it. Quoting 80 as an
  algorithm count overstates the app by one.
- **Effects: 17** passes, in the chain's default order.
- **Palettes: 44** built-in, across 11 categories. Imported palettes are not registered; they
  live in a file.
- **Character sets: 48** across 11 categories (Glyph Art only).
- **Themes: 7** — Matrix, Amber CRT, Ice, Handheld and Rose are dark; Parchment and Medieval
  are light.
- **Built-in presets: 89** — 83 curated (74 Pixel Dither to 9 Glyph Art) plus 6 Algorithm Lab
  presets, which are counted separately.
- **Tests: 612 test methods across 62 JVM test classes**, three of which need Robolectric. Run
  by `gradle testDebugUnitTest`; CI additionally runs `detekt`, `lintDebug` and
  `assembleDebug`.
- **Preset schema version: 4.**

The dither families, which are the shelves a picker groups styles under, are: error diffusion
21, patterned 18, special 16, glitch 12, ordered 8, polygon 4, basic 1 (`NONE`).

## Current architectural shape

### Packages

| Package | Owns |
| --- | --- |
| `core/` | The engine. Knows no render module at all. |
| `core/color/` | Palettes, `PaletteProviders`, `ColorDistance`, `PaletteQuantizer` |
| `core/dither/` | `DitherMode`, the algorithms and kernels, matrices, screens, modulation surfaces, region and polygon methods, `DitherProviders` |
| `core/image/` | `Pixels`, `Adjustments` |
| `core/pipeline/` | The node infrastructure: `ImageProcessorNode`, `NodePipeline`, `BufferPool`, `RowParallel` |
| `core/provider/` | `Provider`, `ProviderCategory`, `Registry` |
| `core/serial/` | `WireId` and its serialiser — the stable ids everything is stored as |
| `render/` | Shared render infrastructure: `RenderSettings`, `RenderMode`, `CellSampler`, `QuantisePass`, `PixelDitherRenderer`, `Layers`, `RenderBudget`, the `RenderModule` / `ModuleRender` / `RenderModuleSet` seam, `RenderModules` |
| `glyph/` | The Glyph Art module and its own outputs: `CharacterSets`, `GlyphEngine`, `GlyphRenderer`, `Fonts`, `TextExporters`, `SvgExporter` |
| `effects/` | Effect execution: `EffectId`, `EffectPass`, `EffectNodes`, `EffectPipeline`, `EffectProviders` and the 17 passes |
| `pipeline/` | `RenderPipeline`, `LayerCompositor`, `Providers` — runs whatever module it is handed |
| `anim/` | Animation tracks and segments, `Temporal`, `GifEncoder`, `Mp4Encoder`, `ColorQuantizer` |
| `data/` | `Source`, `ImageLoader`, `LiveCamera`, `CameraCapture`, `PresetLibrary`, `PresetStore`, `PresetSchema`, `PaletteFile`, `Import`, `Settings` |
| `state/` | `SourceController`, `HistoryController`, `PresetController`, `ExportCoordinator`, `PlaybackPlan`, `RandomLook` |
| `export/` | The byte sink only: `Exports` (the interface) and `Exporter` (MediaStore / FileProvider) |
| `ui/` | Terminal-styled Compose panels and the theme |

`AppRenderModules`, `GlyphsmithViewModel`, `MainActivity` and `GlyphsmithApp` sit at the top
level as the composition root.

Two boundaries are easy to state wrongly and worth stating twice:

- **`export/` does not orchestrate exports.** It writes bytes and does not know what produced
  them. Deciding what happens to a finished render and what is said about it is
  `state/ExportCoordinator`. Glyph Art's `.txt`, `.ansi`, `.html` and `.svg` writers live in
  `glyph/`, not here.
- **The node infrastructure is `core/pipeline/`, not `pipeline/`.** `NodePipeline`,
  `BufferPool`, `ImageProcessorNode` and `RowParallel` are engine parts that know no render
  module. `pipeline/` is the render pipeline that composes modules, effects and layers.

The dependency direction is enforced by `LayeringTest`, which reads the actual `import` lines
and fails the build on a dependency pointing the wrong way. The prohibition table is in
`ARCHITECTURE.md`.

### Execution model

Four provider categories are registered — `ProviderCategory` has four entries, not five:

| Category | Registry | What the provider carries |
| --- | --- | --- |
| `RENDER` | `render/RenderModules` | metadata and **capabilities** (`producesGlyphs`, `ditherFirst`) |
| `DITHER` | `core/dither/DitherProviders` | metadata and **execution** (`algorithm`) |
| `EFFECT` | `effects/EffectProviders` | metadata, **execution** (`pass`) and everything else an effect is |
| `PALETTE` | `core/color/PaletteProviders` | metadata and **data** (the `Palette` and its family) |

`pipeline/Providers` gathers all four — the only place allowed to see them at once — so the
rules about ids can be stated once over everything.

**Which module renders which mode is bound in `AppRenderModules`, deliberately not in a
registry.** It is a `when` over `RenderMode` at the composition root, and the reasoning is in
its KDoc: a map throws at the first frame in whichever mode nobody tried, and a startup
registration list is missing in every test, quietly. The `when` does not compile until a fourth
mode says what renders it. This is the intended end state — it is not a switch statement left
over from before the provider model, and it should not be "moved into the registry".

`RenderModuleProvider` is the declarative half of the same seam: it carries the id, the display
name and the capabilities the UI branches on, and is readable without an Android runtime.

**The effect category is the one that is fully plugin-shaped.** An `EffectPass` carries its
params slice, how that slice is written back, its toggle, its random roll and its code, all
declared inside the effect's own object; the id and the label sit on the `EffectId` constant.
Adding an effect therefore touches its own file plus two binding lines — `effects/EffectPasses`
(what runs) and `ui/panels/EffectPanels` (what it looks like) — and it appears in the chain, the
panel, presets, exports and Surprise Me on its own. The four couplings that are kept on purpose,
and why, are in `ARCHITECTURE.md`, *Adding an effect*. `effects/EffectCatalogTest` is what holds
the "appears on its own" claim to the lists it is a claim about.

**The palette category needs no such work, and was audited rather than rebuilt** (2026-08-05).
A palette is data: adding one is a single line in `Palettes.all`, and the registry, the wire id,
the picker's categories, presets, the migration and Surprise Me are all derived from it. There
is no execution to scatter, so there was no `when` to remove. `PaletteProviders` is not on the
read path and is deliberately left that way, and imported palettes stay out of the registry
because they already travel inside the preset as `paletteOverride`. `ARCHITECTURE.md`, *Palette
Providers*, has the full finding. Do not re-open it.

**Export providers are the fifth category named in the plugin model and do not exist, by
decision.** An exporter is chosen from a menu rather than named in a preset, so it has nothing
to be identified by yet. `export/Exports` is the seam in the shape one would take, minus the
ids and the registry.

### What the UI branches on

Panels ask `RenderModules.of(mode)` for a capability and hide the controls that capability
governs. Three places do it, and each hides a *section*, never a whole page:

| Place | Hidden when the module produces no glyphs |
| --- | --- |
| `ui/panels/RenderPanel` | the glyph settings — depth, character set, ramp, font |
| `ui/panels/MappingPanel` | the edge mapping only, via `ui/panels/MappingSections` |
| `ui/panels/OutputPanel` | the text and glyph-grid exports |

The tab row itself branches on nothing: every tab is offered in every mode. It used to hide the
whole MAP tab outside glyph art, which took the dither picker, the tone curve and the
pre-dither adjustments with it — in the default mode there was then no way to choose a dither
algorithm at all. That was fixed by splitting the panel rather than by relaxing the tab rule,
and `ui/panels/MappingSectionsTest` states which half is which.

## Compatibility invariants

These are the things a change can break silently, and each has a test.

**The two render-mode defaults are different values on purpose.**

- `RenderSettings.renderMode` defaults to `RenderMode.GlyphMatrix`. That is what a preset
  naming *no* mode is read as, and every preset written before the field existed is that case.
- `RenderMode.DEFAULT` is `RenderMode.PurePixel`. That is what new sessions, new general
  presets and `RandomLook` start from — `RenderSettings.newSession()` is the one that reads it.

Collapsing them into one value turns the saved library into pixel dithers on first open. Every
built-in preset states its mode through the `pixel()` or `glyph()` helper, so no shipped preset
depends on either default. Covered by `render/SessionDefaultTest`, `UiStateDefaultTest` and
`data/PresetSchemaTest`.

The rest:

- Stable wire ids (`core/serial/WireId`) are what presets store — never enum names, class
  names, package names or translated labels. Renaming the Kotlin enum constants is safe;
  changing an id is not. Four tables: `RenderModeIds`, `DitherModeIds`, `EffectIds` and
  `AnimTargetIds`. The last one arrived on 2026-08-05 and closed the final identity in the
  format that was still a Kotlin constant name; it did **not** raise the schema version, and
  `PresetSchema`'s KDoc says why.
- Preset schema version 4, with per-entry migrations that run in order. Raising the version
  means adding a migration *and* a test that decodes a literal document of the old version.
- An unknown dither, render mode, effect or palette id is **refused**, not remapped: the preset
  holding it is dropped and the rest of the library survives.
- No migration changes how anything renders.

## What the migration finished

Kept as project history. The full record, including what each step became, is in
`MIGRATION_LEGACY.md`.

- preset schema versioning
- migration tests for legacy presets
- stable wire IDs
- new-session default and Surprise Me moved to Pixel Dither
- pixel-first preset library
- rename of the shared ASCII-specific architecture
- internal provider registries
- shared render code moved out of Glyph Art dependencies
- the `ascii` package dissolved, package boundaries enforced by tests
- view model responsibilities split into smaller units

Two components the plan named were examined and **deliberately not built**, and should not be
built now: `RenderCoordinator` and `AnimationController`. A `previewBudget()` helper was struck
for the same reason. The criterion was: extract what has an invariant worth testing, not what
is long. `GlyphsmithViewModel` staying a ~915-line coordinator is the intended end state.

## What is no longer the main focus

The migration is not the active work and should not be reopened.

Documentation synchronisation is also done: the documents now describe the application that
exists rather than the one the migration set out to build.

The remaining work is product-phase work, and two slices of it have shipped: the mapping panel
now offers its render-neutral half in every mode (PR #50), so pixel dither can reach the dither
controls, and the effect category has been closed into a plugin shape (task 6) so that adding an
effect no longer means editing files outside it.

What is still open:

- feature matrix creation
- public Dither Boy comparison
- feature slice planning
- UX cleanup where it improves the product

## Risks to keep in mind

- docs can drift away from code again — the headline numbers above are the part that rots
  first
- shared code can accidentally regain Glyph Art assumptions
- UI can drift back to ID-based branching instead of capability-based branching, or hide a
  whole page where only a section is mode-specific — that is what made the default mode
  unusable once already
- new effects can become messy if they bypass the provider model
- the two render-mode defaults can be "tidied" into one value by somebody who reads only one
  of them
- `AppRenderModules` can be mistaken for a leftover switch statement and moved into a registry

## Practical summary

Glyphsmith is in a stable architecture state. Future work should treat the current structure as
the baseline, not as something to reopen casually.
