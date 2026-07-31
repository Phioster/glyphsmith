# Glyphsmith Development Guide

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

Glyphsmith currently has two output modes:

1. Pixel Dither
2. Glyph Art

Pixel Dither is the default, general-purpose mode.

Glyph Art maps quantised levels onto character ramps. It is a specialized
render module and must not own shared concepts such as palettes, dithering,
effects, animation, layers, sources, or export.

Code used by both modes must use neutral names and live outside the glyph
or ASCII package.

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

Initially, plugins are compiled into the application.

Do not implement downloadable executable third-party code unless a
separate task explicitly requests it.

The initial plugin-style categories are:

- render modules
- dither algorithms
- image effects
- palette providers
- export providers

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
- UI panels must hide or disable controls that do not apply to the active mode.

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

LAB or algorithm-comparison presets may exist separately from the curated
main preset library.

## Compatibility

Existing user presets and saved configurations must not silently change
appearance.

Legacy presets that do not contain a render mode must load as Glyph Art,
because they were created before Pixel Dither became the default.

New presets use Pixel Dither unless another mode is explicitly selected.

Before changing defaults, the preset format must gain:

- an explicit schema version
- migration logic
- stable serialized identifiers
- tests for legacy preset loading

Do not rename serialized enum entries without a migration or alias.

## Naming direction

Shared classes must use neutral product-level names.

Preferred future names:

- RenderSettings instead of AsciiParams
- RenderPipeline instead of ascii.Pipeline
- Glyph Art instead of ASCII in user-facing text
- Pixel Dither instead of Pure Pixel in user-facing text
- GlyphRenderer instead of names implying the entire app is ASCII-based

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
