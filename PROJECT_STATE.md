# PROJECT_STATE.md

## Current status

Glyphsmith has completed the architecture migration. The app is now in the product phase.

The old ASCII-first shape has been replaced by a provider-driven render architecture with optional Glyph Art support.

## Current headline numbers

- Render modes: 3
- Dither algorithms: 80
- Effects: 17
- Palettes: 44
- Character sets: 48
- Themes: 7
- Built-in presets: 89
- Latest reported CI result: 572 tests passing

## Current architectural shape

### Core modules

- `render/` is neutral render infrastructure
- `glyph/` is Glyph Art specific
- `effects/` owns effect execution
- `core/dither/` owns dither execution
- `core/color/` owns palette code
- `pipeline/` owns the render pipeline
- `state/` owns controllers and orchestration
- `export/` owns export orchestration
- `data/` owns preset data, schema, storage, and library
- `ui/` owns the user interface

### Execution model

Execution is provider-driven:

- Render providers
- Dither providers
- Effect providers
- Palette providers

Providers carry metadata and execution instead of being simple lookup tables.

## What the migration finished

- preset schema versioning
- preset migration support
- stable wire IDs
- preset library extraction
- render provider model
- dither provider model
- effect provider model
- shared render code moved out of Glyph Art dependencies
- package boundaries enforced by tests
- view model responsibilities split into smaller units
- stronger test coverage and CI reporting

## What is not the main focus anymore

The migration is no longer the active work.

The remaining work is product-phase work:

- documentation synchronization
- phase-2 planning
- feature matrix creation
- public Dither Boy comparison
- feature slice planning
- UX cleanup where it improves the product

## Risks to keep in mind

- docs can drift away from code again
- shared code can accidentally regain Glyph Art assumptions
- UI can drift back to ID-based branching instead of capability-based branching
- new effects can become messy if they bypass the provider model

## Practical summary

Glyphsmith is now in a stable architecture state. Future work should treat the current structure as the baseline, not as something to reopen casually.
