# CLAUDE.md

## Role in this repository

Claude Code is used here as the implementation agent for Glyphsmith. The migration is complete, so the current phase is product development and controlled feature work, not architectural recovery.

## Current project state

Glyphsmith is now a provider-driven Android rendering app with optional Glyph Art support.

Current headline numbers:

- Render modes: 3
- Dither algorithms: 80
- Effects: 17
- Palettes: 44
- Character sets: 48
- Themes: 7
- Built-in presets: 89
- Latest reported CI result: 572 tests passing

## Current architecture summary

The codebase is organized around neutral render infrastructure:

- `render/` contains neutral render-module abstractions and render budget logic
- `glyph/` contains Glyph Art specific rendering code
- `effects/` owns effect execution and effect passes
- `core/dither/` owns dither algorithms and dither providers
- `core/color/` owns palette-related code
- `pipeline/` owns the render pipeline and shared pipeline nodes
- `state/` owns controllers and orchestration that used to sit in the big ViewModel
- `data/` owns preset schema, preset storage, preset library, and import/export data handling
- `ui/` owns the Compose UI and screens

Provider registries now carry both metadata and execution. Execution must stay inside the provider model instead of drifting back into large switch statements.

## Rules for Claude Code

1. Prefer small, reviewable PRs.
2. Do not change stable IDs unless explicitly requested.
3. Do not change preset compatibility unless the task is about compatibility.
4. Do not change algorithms or effects unless the task is about those behaviors.
5. Do not reintroduce Glyph Art assumptions into shared code.
6. Keep shared code neutral.
7. Keep tests strong. If a new behavior matters, add a test that locks it down.
8. Treat CI as required, not optional.
9. Prefer adapters over incompatible refactors.
10. If a task is ambiguous, analyze the current code first and report the actual state before coding.

## Workflow

When given a task:

1. Read this file first.
2. Read `PROJECT_STATE.md`.
3. Read `ARCHITECTURE.md`.
4. Read `ROADMAP_V2.md`.
5. Read `CLAUDE_TASKS.md` if the task is feature or roadmap related.
6. Inspect the actual code.
7. Make the smallest correct change.
8. Run tests.
9. Report exactly what changed and what remains open.

## What is no longer the focus

The ASCII-first migration is complete. Do not spend time reopening completed migration steps unless the task explicitly asks for a postmortem or documentation update.

## What should happen next

The next phase is product work:

- docs sync
- feature matrix completion
- Dither Boy comparison
- first post-migration feature slices
- UI/capability cleanup where it improves the product
- future effect and workflow improvements

## Good task shape

Good tasks are narrow:

- one feature
- one architectural seam
- one document update
- one testable change

Bad tasks are broad:

- "improve the app"
- "refactor everything"
- "make it like Dither Boy"
- "clean up the architecture"

## Stability boundaries

The following should stay stable unless a task explicitly says otherwise:

- wire IDs
- preset compatibility
- provider contracts
- the current render-module split
- the package boundary rules
- the current test posture

## Notes

If a requested task would require reopening completed migration work, stop and explain the conflict instead of making unrelated changes.
