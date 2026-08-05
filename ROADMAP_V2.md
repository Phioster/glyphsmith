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
  deliberately is — the same holds for `EffectPasses` and `ui/panels/EffectPanels`, which are
  binding tables for the same reason and not leftovers

## Phase 1 — documentation and project clarity (done)

Closed by PR #48, *Make the documentation describe the application that exists*, and by the
consolidation that followed it. What changed:

- `ARCHITECTURE.md` describes the architecture that was built rather than the migration
  that was planned — three render modes, four provider categories, the real package layout
- `README.md`, `CLAUDE.md` and `MIGRATION_LEGACY.md` follow the same state
- `MIGRATION_LEGACY.md` is marked complete and kept as the record of what each step became
- `PROJECT_STATE.md` carries the verified headline numbers, the package table and the
  compatibility invariants

Standing part, which stays open:

- keep `CLAUDE.md` aligned with the actual project state
- keep `PROJECT_STATE.md` as the first read for any future Claude session
- keep `ARCHITECTURE.md` aligned with the real package and provider layout
- add or update product-facing documentation only when it reduces confusion

## Phase 2 — feature matrix and comparison work (done)

Closed 2026-08-05 by the audit in `CLAUDE_TASKS.md`, tasks 2 and 3. Studio AAA's public
material was surveyed end to end — the downloads hub, the blog, and thumbnails for all 118
videos on their channel — and every claim checked against this repository.

The result: **six gaps**, filed as tasks 9 to 14 — of which one was struck the same day, because
k-means palette extraction is built and the audit had carried the claim forward from an older
study without checking it. Four items an earlier study listed as missing turned out to be built.
One technique is classified *intentionally different* and is not to be reproduced.

Their signature look was already reachable and now ships as a preset, which is the sharper
version of "identify candidate signature features": the gap was discoverability, not capability.

## Phase 3 — feature slices (open, first one shipped)

Shipped:

- **capability-driven UI cleanup, first pass** (PR #50, product audit item 4.1). The mapping
  panel is split into a render-neutral dither half and a glyph-only edge half, and the tab row
  no longer hides a whole page for a capability that governs one section of it. Pixel dither —
  the default mode — can reach the dither controls. Recorded in `CLAUDE_TASKS.md`, task 4.
- **the effect category as a compile-time plugin**. An effect now carries its id, label, params
  slice, toggle, random roll and code itself; adding one touches its own file plus two
  compiler-enforced binding lines and nothing outside `effects/` and `ui/panels/`. Recorded in
  `CLAUDE_TASKS.md`, task 6, with the four deliberate couplings and the next category to take.

Also shipped, 2026-08-05:

- **animation targets**, three quarters of `CLAUDE_TASKS.md` task 7: stable ids, the target
  carrying the field it writes, and five effects made animatable. The fourth quarter — deleting
  the `AnimTarget` enum so an effect can own its parameter — is deferred with a written trigger,
  because it costs ~100 typed call sites and buys no correctness.
- **five of the six audit gaps** (tasks 9–14), and four presets so the mechanisms can be found.

The palette category was audited and needs no work at all (task 8).

Still good candidates, in the order they look worth doing:

- **preset browsing and discovery.** The library is 96 presets across 11 categories and the
  picker is one sorted list with headings — favourites first, then category, then name. There is
  no search and no filter. The recurring lesson of the audit was that the gap is discoverability
  rather than capability; at this size that now applies to the library itself.
- **effect catalog organization.** Seventeen effects in one chain list, same shape of problem.
- somewhere for an imported palette to live (task 12) — convenience, not a defect
- export workflow polish
- signature effect additions
- ~~the rest of the capability-driven UI cleanup~~ — done: the three places that branch on
  `producesGlyphs` each hide a section, and the tab row branches on nothing
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
