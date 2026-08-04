# ROADMAP_V2.md

The migration is complete. The roadmap starts from a stable provider-driven architecture, and
the phases below are what comes after it rather than what finishes it.

The heading numbers are the order the phases were written in, not a queue: phase 0 is standing
maintenance that never closes, and phases 2 to 4 are the open work.

## Phase 0 — keep the baseline correct (standing)

Never "done"; checked whenever something lands.

- keep headline counts accurate — they live in `PROJECT_STATE.md` and are the first thing to
  rot
- keep stable IDs and preset compatibility intact, including the two render-mode defaults
  (`RenderSettings.renderMode` is `GlyphMatrix`, `RenderMode.DEFAULT` is `PurePixel` — see
  `PROJECT_STATE.md`, *Compatibility invariants*)
- keep boundary tests green (`LayeringTest`, `ProviderRegistryTest`, `WireIdTest`,
  `PresetSchemaTest`)
- keep the provider model intact, and leave the `AppRenderModules` binding as the `when` it
  deliberately is

## Phase 1 — documentation and project clarity (done)

Closed by PR #48, *Make the documentation describe the application that exists*, and by the
consolidation that followed it. What changed:

- `ARCHITECTURE.md` describes the architecture that was built rather than the migration that
  was planned — three render modes, four provider categories, the real package layout
- `README.md`, `CLAUDE.md` and `MIGRATION_PLAN.md` follow the same state
- `MIGRATION_PLAN.md` is marked complete and kept as the record of what each step became
- `PROJECT_STATE.md` carries the verified headline numbers, the package table and the
  compatibility invariants

Standing part, which stays open:

- keep `CLAUDE.md` aligned with the actual project state
- keep `PROJECT_STATE.md` as the first read for any future Claude session
- keep `ARCHITECTURE.md` aligned with the real package and provider layout
- add or update product-facing documentation only when it reduces confusion

## Phase 2 — feature matrix and comparison work (open)

- build a feature matrix for the current app
- compare Glyphsmith with the public Dither Boy feature set
- classify features as present, partial, missing, or intentionally different
- identify candidate signature features for Glyphsmith

## Phase 3 — decide the first feature slices (open)

Good candidates for the first post-migration slices are:

- capability-driven UI cleanup
- effect catalog organization
- export workflow polish
- preset browsing and discovery improvements
- signature effect additions
- small workflow improvements that make the app faster to use

## Phase 4 — ship small PRs only (standing)

Rules for future implementation work:

1. one behavior axis per PR
2. one visible change per PR
3. one testable outcome per PR
4. keep shared code neutral
5. do not bypass the provider architecture

## What not to do

- do not reopen finished migration work
- do not rename stable IDs just to improve naming
- do not add features that are not tied to a clear product value
- do not introduce a new architecture without tests
- do not turn roadmap work into a broad refactor
- do not build `RenderCoordinator` or `AnimationController`; both were examined during the
  migration and deliberately not built
- do not collapse the two render-mode defaults into one value
