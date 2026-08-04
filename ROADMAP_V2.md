# ROADMAP_V2.md

## Phase 2: product work

The migration is complete. The roadmap now starts from a stable provider-driven architecture.

## Phase 0: keep the baseline correct

- sync docs with the current codebase
- keep headline counts accurate
- keep stable IDs and preset compatibility intact
- keep boundary tests green
- keep the provider model intact

## Phase 1: documentation and project clarity

- keep `CLAUDE.md` aligned with the actual project state
- keep `PROJECT_STATE.md` as the first read for any future Claude session
- keep `ARCHITECTURE.md` aligned with the real package and provider layout
- add or update product-facing documentation only when it reduces confusion

## Phase 2: feature matrix and comparison work

- build a feature matrix for the current app
- compare Glyphsmith with the public Dither Boy feature set
- classify features as present, partial, missing, or intentionally different
- identify candidate signature features for Glyphsmith

## Phase 3: decide the first feature slices

Good candidates for the first post-migration slices are:

- capability-driven UI cleanup
- effect catalog organization
- export workflow polish
- preset browsing and discovery improvements
- signature effect additions
- small workflow improvements that make the app faster to use

## Phase 4: ship small PRs only

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
