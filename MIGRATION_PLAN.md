# Glyphsmith Migration Plan

## Goal

Move Glyphsmith from an ASCII-first application to a Pixel-Dither-first host
application.

Glyph Art remains fully supported as an integrated render module.

## Required Order

1. Add preset schema versioning
2. Add migration tests for legacy presets
3. Add stable serialized provider IDs
4. Change new-session default to Pixel Dither
5. Update Surprise Me to default to Pixel Dither
6. Replace the main preset library with Pixel-first presets
7. Rename shared ASCII-specific architecture
8. Introduce internal provider registries
9. Move shared code out of Glyph-specific packages
10. Split the central ViewModel

## Compatibility Rules

- Existing presets must retain their appearance.
- Legacy presets without a render mode must load as Glyph Art.
- New sessions default to Pixel Dither.
- New general-purpose presets use Pixel Dither.
- Glyph Art presets explicitly select Glyph Art.
- Existing algorithms and effects must not be rewritten during migration.
- Existing rendering output must remain unchanged unless explicitly requested.
- Tests must not be weakened or deleted.

## Commit Discipline

Each migration step must be a separate commit.

Do not combine:

- schema migration
- default changes
- preset replacement
- broad renaming
- package moves
- provider architecture

Every step must compile and pass tests before the next step begins.

## First Implementation Task

The first code task is preset compatibility only:

- inspect the current preset serialization format
- add an explicit schema version
- add migration infrastructure
- ensure legacy presets without a render mode load as Glyph Art
- add tests for legacy and current preset loading
- do not change the new-session default yet
- do not replace presets
- do not rename classes
- do not introduce plugin interfaces yet
- 
