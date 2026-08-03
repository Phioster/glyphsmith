# Glyphsmith Architecture

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

Glyph Art is an integrated render module. Shared rendering, palettes, effects,
animation, layers, and image export must not depend on Glyph Art.

## Processing Pipeline

Media Source  
-> Source Adjustments  
-> Sampling  
-> Dithering or Quantisation  
-> Render Module  
-> Effect Pipeline  
-> Layers  
-> Preview or Export

Preview, image export, video export, animation, and preset thumbnails should
use the same pipeline with different quality limits.

## Render Modules

The initial render modules are:

- Pixel Dither
- Glyph Art

Pixel Dither is the default for new sessions.

Glyph Art may additionally produce:

- a glyph grid
- plain text
- ANSI
- HTML
- text SVG
- outline SVG

Shared code must not assume that every render module provides glyph output.

## Dependency Direction

Allowed dependencies:

- Glyph Art depends on Shared Render Core
- Effects depend on the Effect API
- Dither algorithms depend on the Dither API

Forbidden dependencies:

- Shared Render Core must not depend on Glyph Art
- Effect API must not depend on individual effects
- Dither API must not depend on individual algorithms

Code shared by Pixel Dither and Glyph Art must use neutral names and live
outside Glyph-specific packages.

## Internal Plugin Model

Plugins are initially internal and compiled into the APK.

Do not load arbitrary executable third-party code during the first migration.

Plugin categories are:

- Render Modules
- Dither Providers
- Effect Providers
- Palette Providers
- Export Providers

Each provider should eventually expose:

- stable ID
- display name
- category
- description
- default parameters
- capabilities
- implementation

Example stable IDs:

- render.pixel-dither
- render.glyph-art
- dither.floyd-steinberg
- dither.bayer-8
- effect.epsilon-glow
- effect.pixel-sort
- export.png
- export.svg-outline

Stable IDs must not depend on enum names, class names, package names, or
translated display labels.

## Dither Providers

A Dither Provider converts sampled values into levels, palette indexes, or
colors.

The architecture must support:

- direct quantisation
- error diffusion
- ordered matrices
- modulation patterns
- precomputed traversal
- region-based methods
- color diffusion

Existing algorithms must initially be adapted, not rewritten.

## Effect Providers

Effects operate on the bitmap produced by a Render Module.

Effects:

- are order-dependent
- have serializable parameters
- are deterministic for the same input, seed, and time
- preserve existing output during migration
- do not modify the logical Glyph Art text grid

Existing effects must initially be adapted, not rewritten.

## Presets

A preset is a saved configuration of available providers.

A preset is not executable code and is not a plugin implementation.

A preset may contain:

- Render Module ID
- Dither Provider ID
- palette configuration
- effect order and parameters
- animation
- layers
- output preferences

The curated preset library should contain approximately:

- 80 to 90 percent Pixel Dither presets
- 10 to 20 percent Glyph Art presets

Algorithm-comparison presets should remain in a separate LAB section.

## Compatibility

Existing presets must retain their appearance.

Legacy presets without a stored render mode must load as Glyph Art.

New sessions and new general presets must eventually default to Pixel Dither.

Before changing the default, implement:

- preset schema versions
- migration logic
- stable serialized IDs
- compatibility tests

Changing only a Kotlin constructor default is not sufficient.

## Naming Direction

Preferred future names:

- AsciiParams becomes RenderSettings
- ascii.Pipeline becomes RenderPipeline
- ASCII becomes Glyph Art in the UI
- Pure Pixel becomes Pixel Dither in the UI

Naming refactors and behavior changes must be separate tasks.

Do not combine package moves, serialization changes, and default changes in
one large commit.

## UI Direction

The intended navigation is:

- Source
- Style
- Dither
- Color
- Effects
- Layers
- Motion
- Glyph Art
- Output
- Presets

When Pixel Dither is active:

- Glyph controls are hidden or inactive
- text exports are unavailable
- no glyph ramp is required

When Glyph Art is active:

- Glyph controls and text exports are available
- shared Dither, Color, Effects, Layers, and Motion controls remain available

## State Ownership

The central ViewModel may remain temporarily.

The future direction is to separate responsibilities into components such as:

- SourceController
- RenderCoordinator
- HistoryController
- PresetController
- AnimationController
- ExportCoordinator

Image-processing algorithms must not live in the ViewModel.

## Testing Rules

Architectural changes must preserve existing tests and add tests for:

- stable ID uniqueness
- provider registration
- legacy preset migration
- current-schema round trips
- unknown providers
- deterministic rendering
- effect order
- render-module capabilities
- export compatibility

Tests must not be removed or weakened to make a migration pass.

## Migration Order

1. Add documentation
2. Add preset schema version and migrations
3. Add stable serialized provider IDs
4. Change new-session default to Pixel Dither
5. Replace the main preset library with Pixel-first presets
6. Introduce neutral names
7. Add internal provider registries
8. Adapt existing algorithms and effects
9. Move shared code out of Glyph-specific packages
10. Split the central ViewModel

## Non-Goals

The initial migration does not include:

- deleting Glyph Art
- deleting existing algorithms or effects
- copying proprietary Dither Boy behavior
- loading arbitrary downloaded code
- rewriting all algorithms
- dynamically generating the entire UI
- moving every package in one commit
- breaking old presets

## Definition of Success

The migration is successful when:

- new sessions start in Pixel Dither
- old Glyph Art presets retain their appearance
- Pixel Dither works without constructing Glyph-specific state
- Glyph Art remains fully functional
- shared render code has no dependency on Glyph Art
- algorithms and effects use stable provider IDs
- presets have schema versions and migrations
- the UI uses provider capabilities
- existing rendering tests continue to pass
