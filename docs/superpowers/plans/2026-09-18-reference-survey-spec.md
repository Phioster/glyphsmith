# Reference Survey, September 2026 — Spec

**Status:** decided, not implemented. Five plans argue from this document; each names it as its
spec. Written 2026-09-18.

This is the source of truth for *why* the five plans exist and what they may and may not do. It
records a survey of four outside projects, what was already known, what is new, and the three
decisions taken before any plan was written.

---

## 1. What was surveyed

Four sources. One was already known; three were not.

| Source | Kind | Licence | Status before this survey |
| --- | --- | --- | --- |
| [dither-guy](https://github.com/manoelpiovesan/dither-guy) | Python app | **GPL-3.0** | already surveyed (July 2026) |
| [ditherista](https://github.com/robertkist/ditherista) | C++/Qt desktop app | **MIT** | new |
| └ [libdither](https://github.com/robertkist/libdither) | C99 library, 90+ methods | BSD-2-clause-like | new |
| [dithering-studio](https://github.com/Oslonline/dithering-studio) | TypeScript web app | **Apache-2.0** | new |
| [Decaying Sun](https://www.patreon.com/DecayingSun) | TouchDesigner `.tox` components, paid | proprietary | new |

### The licence situation, stated precisely

dither-guy is GPL-3.0 and remains off limits as a source of code; that was already recorded in
`~/glyphsmith-research/README.md` §7 and has not changed.

libdither is the first permissively-licensed body of dithering work found. Note a discrepancy
worth carrying forward: **its README says "MIT licensed" while its `LICENSE` file contains a
BSD-2-clause text** with two added conditions (the kdtree and uthash copyright notices must
travel with any redistribution). Both are permissive and neither is copyleft, but the two
statements are not the same licence, and anyone intending to copy from it must resolve that
first. **This project does not intend to** — see decision 3.

Decaying Sun's components are paid and closed. Nothing was bought, and nothing may be bought for
the purpose of opening it.

---

## 2. What is genuinely missing from Glyphsmith

Checked against the source tree, not against `README.md`. Already shipped and **not** a gap:
Ostromoukhov, Shiau–Fan, Stevenson–Arce, Knuth's dot diffusion, Riemersma, median-cut.

### 2.1 Colour — the largest gap, and the one that matters most

`core/color/ColorDistance.kt` offers three metrics: `EUCLIDEAN`, `CIELAB`, `OKLAB`. libdither
offers eight comparison modes: LAB76, LAB94, LAB2000, sRGB, linear, HSV, luminance, Tetrapal.

This is a gap in *palette and colour quality*, which `CLAUDE.md` ranks **4th** in its priority
list, against *additional algorithm count* at **10th** with the explicit rule: "Do not add
algorithms merely to increase the advertised number." The colour work is therefore the head of
the queue and the algorithm work is the tail, and the plans are numbered accordingly.

**The structural obstacle.** The three metrics cannot simply become eight, because
`ColorDistance` currently conflates two different things:

1. **A space you can travel back out of.** `rgbOf(coords)` is documented as "the exact inverse of
   `coordsOf`", and `effects/ColorDepth.kt` depends on it: quantising lightness means modifying a
   colour in a perceptual space and coming back.
2. **A way of measuring.** `distance(a, b)` is hard-wired to `distanceBetween`, a straight line
   between three coordinates.

Both assumptions break on the metrics we want to add:

- **Luminance** collapses three channels onto one. It has no inverse. Added naïvely it would
  silently turn Color Depth into a greyscale machine.
- **CIE94 and CIEDE2000** are not colour spaces at all. They are weighted formulas over L\*a\*b\*
  coordinates, with hue-rotation and chroma-dependent terms. Neither is a straight line, so
  neither fits `squaredBetween`.
- **HSV** *is* invertible, but only if hue is encoded as a plane (`x = s·cos h`, `y = s·sin h`,
  `z = v`) rather than as a linear axis. As a linear axis, red at h=0 and magenta at h=350 read
  as far apart when they are neighbours.

So the concepts must be separated before the metrics can be added. That separation is Plan 01,
and it is the reason Plan 01 is a refactor before it is a feature.

### 2.2 Custom Kernel — the strongest single idea found

From dithering-studio: rather than shipping algorithm number 80, let the user build their own
error-diffusion kernel.

Glyphsmith is unusually ready for this. `core/dither/DiffusionKernels.kt` already expresses every
diffusing style as `ErrorDiffusion(listOf(DiffusionTap(dx, dy, weight), …))`. A custom kernel is
that same list, supplied from settings instead of from a declaration. The mechanism exists; only
the route from the UI to it does not.

It also fits the product line better than any new algorithm does. `README.md` already describes
pattern scale as "what lets an algorithm be driven until it visibly breaks down". A kernel editor
is that sentence, made general.

### 2.3 Algorithms genuinely absent

Filtered hard against the "do not add algorithms merely to increase the number" rule. Kept only
where the **mechanism** is new, since `DitherAlgorithm` is explicitly a taxonomy of mechanisms:

- **Adaptive Floyd–Steinberg (3×3, 7×7)** — kernel weights vary with local image content. New
  mechanism: no current style varies its kernel per cell.
- **Zhou–Fang** — variable error diffusion with a noise-modulated threshold. A second member of
  the family Ostromoukhov started, and the only other one with a published construction.
- **Direct Binary Search (DBS)** — iterative optimisation of the whole grid against a human
  visual model. Genuinely a different kind of thing; also genuinely slow (libdither's own README
  warns of minutes), which is a real problem on a phone and is treated as one in Plan 03.
- **Kacker–Allebach** — dot-profile-based, related to DBS but direct.

Explicitly **rejected** as number-padding, with the reason recorded so it is not re-proposed:
Bayer 32×32 (we stop at 16; a fifth power-of-two tile is more of the same), Sierra 2-4A (a
fourth Sierra variant), Posterize (that is `NoDither` with a level count), Woodcut and Stipple
(`README.md` records stippling as already covered).

### 2.4 Quantisers

libdither offers median-cut, Wu and KD-tree. Glyphsmith has median-cut. **Wu** is a genuine
improvement — a variance-minimising split that usually beats median-cut at the same cost class.
KD-tree is a *search structure*, not a quantiser: it speeds up nearest-colour lookups rather than
choosing better colours, and is out of scope until profiling says lookup is the bottleneck.

### 2.5 Generation Loss

From Decaying Sun's catalogue. `effects/JpegGlitch.kt` already performs the whole cycle —
encode to JPEG, damage the scan bytes, decode the wreckage. Generation loss is that cycle run
*n* times, and with `corruption = 0` it becomes pure recompression decay, which is the classic
look and which we currently cannot produce at all.

This is one integer field on an existing params class. It is the cheapest real gain in the
entire survey.

### 2.6 Joyplot

Also from Decaying Sun. Row brightness read as a stacked ridgeline — the *Unknown Pleasures*
figure. This is not an effect; it is a fourth render mode, and it is the only item here that
produces vector output natively, which makes it an unusually good fit for the existing SVG
export. It is also the largest piece of work in the survey, and is planned last.

The image is a 1979 record sleeve and the technique is general; nothing about it is taken from
Decaying Sun beyond the reminder that it exists.

---

## 3. Decisions

Taken 2026-09-18, before any plan was written.

### Decision 1 — Scope: everything, as five separate plans

Each plan produces working, testable software on its own and can be executed, reviewed and
merged without the others. Order is by `CLAUDE.md` priority, not by appeal:

| Plan | Subsystem | `CLAUDE.md` priority |
| --- | --- | --- |
| 01 | Colour space / comparison split, then five new metrics and Wu | 4 — palette and colour quality |
| 02 | Custom error-diffusion kernel | 5 — effect-pipeline usability / clearer controls |
| 03 | Four algorithms with genuinely new mechanisms | 10 — additional algorithm count |
| 04 | Generation loss on the JPEG pass | 5 — effect-pipeline usability |
| 05 | Joyplot render mode | 3 — new top-level workflow |

Plan 03 sits last deliberately despite being the most conventionally exciting.

### Decision 2 — The cut: space ≠ comparison

`ColorDistance` is split in two rather than extended in place:

- **`ColorSpace`** — invertible. Owns `coordsOf` and `rgbOf`. This is what anything that
  *modifies* a colour uses, `ColorDepth` above all.
- **`ColorDistance`** — comparison only. Keeps its name and its three existing constant names,
  because presets already contain them. Gains the freedom to measure however it likes, including
  formulas that are not straight lines and spaces that cannot be inverted.

The alternative considered and rejected was adding only the two metrics that fit the existing
contract (HSV and a CCIR-weighted sRGB) and leaving the rest impossible. That caps us at five
metrics permanently and leaves the conflation in place for whoever next tries to add one.

### Decision 3 — No outside code. Read it, write our own.

Even though ditherista, libdither and dithering-studio are permissively licensed and copying
would be *allowed*, nothing is copied. Verbatim from `README.md`:

> the same vocabulary where it fits, an entirely separate implementation throughout

Reasons, in order: the project is `All rights reserved` and shipping third-party licence texts
inside it complicates a position that is currently simple; libdither's own licence statement is
self-contradictory (§1) and resolving it is work with no payoff; and the existing precedent with
Dither Boy already established that published behaviour is a legitimate reference while
implementation is ours.

What may be taken: **which** algorithms exist, **what** their parameters are called, **what**
their published construction is, and **where** the primary literature sits. Everything that
appears in a Glyphsmith source file is written here.

Where a plan needs a published construction it cites the primary source, not the reimplementation
— exactly as `~/glyphsmith-research/README.md` did for Ostromoukhov, Shiau–Fan and Knuth, and for
the same reason: a rule can be tested, a transcribed table cannot. The precedent is not
theoretical. dither-guy's Ostromoukhov table was found to diverge from the paper from row 8
onward (`22,6,11` in the paper against `22,5,10` there), which would have been inherited silently
by anyone who copied it.

---

## 4. Global constraints

These bind every plan. Copied from `CLAUDE.md` and `ARCHITECTURE.md`; exact values verbatim.

- **No runtime plugins.** No reflection, no `ServiceLoader`, no dynamic class loading, no script
  engines. Everything stays typed and compiled in.
- **Stable wire ids are never changed once written.** The shape is `category.name`, lowercase
  ASCII, hyphen-joined, one dot (`core/serial/WireId.kt`).
- Raising `PresetSchema.CURRENT_VERSION` (currently **4**) requires a `Migration` **and** a test
  that decodes a literal document of the old version.
- **An unknown id is refused, not remapped.** The entry carrying it is dropped; the rest of the
  library survives.
- **No migration may change how anything renders.** Existing presets must look identical.
- **Layering is enforced by test.** `LayeringTest` reads the `import` lines of `core`, `render`,
  `glyph`, `pipeline` and `export` and fails on a dependency pointing the wrong way. Shared
  infrastructure must not depend on Glyph Art.
- Do not weaken tests to make an implementation pass.
- Do not delete existing algorithms, effects, presets or export formats.
- Do not mix unrelated refactors into one change.
- Kotlin, kotlinx.serialization, JUnit 4, Compose (UI only), Gradle. **No new dependencies** in
  any of the five plans.

### Testing

Pure-Kotlin tests run on-device through the kotlinc harness in
`~/glyphsmith-research/local-test-harness` in roughly 20 seconds, against roughly 2 minutes for
CI. Effects that touch `Bitmap` do **not** run under the JVM test source set — that is a known
trap recorded in memory, and it is why Plan 04's test targets the iteration count rather than
the decoded pixels.

---

## 5. What this survey does not propose

Recorded so it is not re-proposed later:

- **No NDK/JNI binding of libdither.** The project has no native build at all — no
  `externalNativeBuild`, no CMake, no `ndk` block in `app/build.gradle.kts`. Introducing one for
  this would add a toolchain to a pure-Kotlin build and route the algorithms around the JVM test
  harness that makes the 20-second cycle possible.
- **No KD-tree.** A search structure, not a quantiser. Revisit only if profiling shows
  nearest-colour lookup dominating.
- **No Tetrapal.** Tetrahedral interpolation over a pre-composed palette is interesting, but it
  changes what "nearest" means structurally rather than adding a metric, and it wants its own
  brainstorm.
- **Nothing from Decaying Sun's paid components is bought, downloaded or opened.** Their
  catalogue supplied two words — "generation loss" and "joyplot" — and both name techniques that
  predate them by decades.
