# CLAUDE_TASKS.md

## How to use this file

This file is the task queue for future Claude Code sessions. Each task should be small enough to fit in a single safe PR.

## Task format

For each task, keep:

- goal
- scope
- files likely to change
- tests required
- acceptance criteria

## Task 1: baseline review

Goal: verify the current codebase state before new work starts.

Prompt:
> Read CLAUDE.md, PROJECT_STATE.md, ARCHITECTURE.md, and ROADMAP_V2.md. Inspect the current codebase and summarize the true current state. Do not change code.

Acceptance criteria:

- no code changes
- current architecture is summarized correctly
- any remaining risks are called out

## Task 2: feature matrix

Goal: document the current product feature set.

Prompt:
> Build a feature matrix for Glyphsmith using the current codebase. Separate render modes, dither algorithms, effects, palettes, export paths, and workflow features. Do not implement anything new.

Acceptance criteria:

- matrix is specific and factual
- no speculative claims
- no code changes unless a generated source of truth is required

## Task 3: Dither Boy comparison

Goal: compare Glyphsmith with the public Dither Boy feature set.

Prompt:
> Compare the current Glyphsmith feature set with the public Dither Boy feature set. Categorize each item as present, partial, missing, or intentionally different. Do not implement features yet.

Acceptance criteria:

- comparison is fact-based
- differences are clearly labeled
- no product decisions hidden inside code changes

## Task 4: first product slice

Goal: implement the smallest high-value post-migration improvement.

Prompt:
> Pick one small product-phase improvement that fits the current architecture and implement it in one PR. Keep provider contracts stable and add tests.

Acceptance criteria:

- one narrow change
- tests added or updated
- CI green
- no unrelated refactors

## Task 5: maintenance guardrails

Goal: keep the architecture stable while new work happens.

Prompt:
> Add or update guardrail tests or docs that keep shared code glyph-neutral, keep stable IDs unchanged, and prevent accidental re-coupling of the UI or pipeline.

Acceptance criteria:

- boundary rules remain enforced
- stable IDs remain protected
- no weakening of existing tests

## Working rules

- one branch per task
- one PR per task
- run tests before merge
- keep changes minimal
- prefer adapters over rewrites
- if a task conflicts with the finished migration, stop and report the conflict
