# Glyphsmith

An Android ASCII-art forge: load an image or a video, map it onto a character grid, export
the render as a PNG, GIF, MP4 or SVG — or the grid itself as a `.txt`.

It rebuilds the feature set of **Script Slayer** — the ASCII module inside Studio AAA's
Dither Boy — for the phone: the same controls, the same vocabulary, an entirely separate
implementation.

## Relationship to Dither Boy

This project is **not affiliated with, endorsed by, or connected to Studio AAA** in any way.
Dither Boy and Script Slayer are their products, and the names are used here only to say
truthfully what this app is modelled on.

Everything here was written from the publicly published feature list and from screenshots of
the interface. No code, assets, fonts or resources from Dither Boy were used, examined or
decompiled, and the app has never been run against it. Where behaviour could not be observed
from the outside — how the glow kernel is actually computed, where injected characters land
in the ramp — this app makes its own choice and says so in the source.

## Licence

None. All rights reserved — deliberately, not by oversight. The source is public so the
builds can run; no permission to use, modify or redistribute it is granted.

The two bundled typefaces are a separate matter and keep their own terms; see
`app/src/main/assets/fonts/NOTICE.md`.

## What it does

**Character sets** — 48 built-in ramps across 11 categories: ASCII, numbers, symbols,
blocks, braille, geometric, languages, cards, unicode, lines, misc. Every ramp is ordered by
ink coverage, and the panel shows the live ramp — narrowed, injected and inverted exactly as
the engine will use it.

**ASCII settings**

| Control | Range | Effect |
| --- | --- | --- |
| Depth | 1–64 | how many glyph levels of the set are used |
| Character category / set | 11 + All / 48 | which ramp, with `<` `>` stepping |
| Inject characters | up to 10 | appended to the dense end of the ramp |
| Character offset | 0–ramp length | rotates the luminance→glyph mapping, wrapping |
| Font style | regular / bold / italic / bold-italic | |
| Cell size | 2–48 px | source pixels per glyph cell — sets the grid resolution |
| Invert ramp | | reverses light and dark |

**Typefaces** — two subsetted faces ship with the app, because the device monospace font
silently falls back to a *proportional* face for braille, kana, runic and friends, which
makes those sets sit unevenly in their cells. DejaVu Sans Mono covers 36 of the 48 sets in
four real styles; GNU Unifont covers all 48 in one. `AUTO` picks the first face that can
draw every glyph in the current ramp and says which one it used — and falls back to the
system face for anything typed into Inject Characters, since the subsets don't contain it.
Both together are under 170 KB. See `app/src/main/assets/fonts/NOTICE.md`.

**Tone** — gamma → contrast → brightness, applied to each cell's luminance before it picks a
glyph. Without it a flat photo only ever reaches the middle third of the ramp.

**Dithering** — 26 algorithms. Error diffusion with serpentine scanning: Floyd–Steinberg,
False Floyd–Steinberg, Jarvis–Judice–Ninke, Stucki, Burkes, Sierra, Sierra Two-Row, Sierra
Lite, Atkinson and the hexagonal Stevenson–Arce from the halftoning literature, Riemersma
walking a Hilbert curve with a decaying error queue instead of a kernel at all, plus axis-dominant Diffuse Y and Diffuse X
that send the error down one axis so grain becomes streaks. Ordered: Bayer 2/4/8/16,
clustered-dot screens that grow a dot from the centre the way a printing screen does, and
blue-noise masks built by Ulichney's void-and-cluster method — the matrices are generated
from their construction rules rather than transcribed, because a rule can be tested and a
1024-entry table cannot. And a modulation family whose threshold is a
continuous function of position: lines, wave, rings, orb and beehive. A separate **pattern
scale** sizes the pattern independently of the cell, which is what lets an algorithm be
driven until it visibly breaks down.

**Edges** — Sobel over the cell grid; a cell whose gradient clears the threshold takes a
glyph matching the edge's direction rather than its brightness. Four edge sets, plus an
edges-only mode that gives line art.

**Ramp order** — the sets are ordered by ink coverage because the engine maps luminance
straight onto a ramp index. That order used to be a hand-made guess. Now each glyph can be
measured — drawn and counted, in the face that will actually render it — and the ramp sorted
by the result, injected characters included. The panel shows every glyph's measured coverage
and lets the order be changed by hand.

**Colour** — single ink colour, source-sampled colour, or a palette. 19 palettes in 6
categories, with individually editable stops, per-stop locks, a shuffle that respects them,
a palette depth independent of the glyph depth, and extraction straight from the loaded
image. Transparent or hex-picked background.

**Effects** — eleven stackable passes over the rendered glyphs, in an order you can change:
post processing, blur/sharpen, tint, chromatic aberration, JPEG databending, pixel sort,
slice shift, diffraction stars, subtexture, CMYK halftone, and Epsilon Glow. The order is not cosmetic — glitch before glow blooms,
glitch after glow cuts the bloom apart.

*Epsilon Glow* is a directional bloom: threshold with soft knee, radius with optional
compensation, intensity, aspect ratio, direction, and an inverse-power falloff
`w(d) = 1 / ((d·scale)ⁿ + ε)`.

*Subtexture* generates its own textures rather than shipping any — CRT stripes and aperture,
halftone, paper grain and fibre, scanner streaks, static, VHS bands — with four blend modes
and an option to derive the texture from the picture's own local detail so it bites where
there is structure and fades over flat areas.

*Pixel sort* reorders runs of pixels along one axis, but only where their brightness falls
inside a threshold band. The band is the whole effect: sorting everything gives gradient
mush, sorting only the mid-tones leaves the darks and lights anchored and makes the colour
appear to bleed out of the edges between them. *Slice shift* is the other half — whole bands
displaced sideways, wrapping, with uneven heights so the result never reads as a pattern.

*CMYK halftone* screens the image the way process printing does: four separations at the
classic angles (yellow 0°, cyan 15°, black 45°, magenta 75°), with grey component
replacement on a black-ink slider and a mid-tone gain for dot spread.

None of the effects touch the character grid, so `.txt` and `.svg` exports are unaffected.

**Video** — load a clip instead of a still and the frames are sampled evenly across it,
each one going through the same pipeline. Frames are decoded on demand rather than held, so
a long clip costs about what a single image does. Parameter tracks and temporal noise still
apply on top.

**Animation** — a still image animated by moving the parameters, not the picture. Nine
tracks, eight curves (three of them one-way ramps, marked as such because they do not close
a loop), and **Temporal Variation**: nine animated noise patterns that shift the dither
threshold itself. Every pattern is periodic over the loop, so the last frame lands back on
the first.

**Presets** — 56 shipped starting points in seven categories, plus whatever you save. A
preset holds the *complete* state, effects and animation included, so applying one is a
single tap. The MOTION and SIGNATURE sets arrive with animation already aimed: apply, press play.
SIGNATURE is this app's own approximation of the looks Studio AAA show in their public
preview clips — not their presets, which aren't published. Each one
renders a thumbnail from your loaded image, favourites sort to the top, and `surprise me`
rolls a look within ranges chosen to actually produce something. The LAB category is a
comparison bench: one preset per dithering algorithm at otherwise identical settings,
generated from the enum so an algorithm added later cannot be left without one.

**Export** — PNG to `Pictures/Glyphsmith`; the character grid, GIF, MP4 and SVG to
`Download/Glyphsmith`; clipboard; or the system share sheet. Presets are saved as JSON.

**Themes** — six interface looks (Matrix, Amber CRT, Ice, Handheld, Rose, and the one light
theme, Parchment). The theme is an app setting rather than part of a preset: loading someone
else's preset should not repaint your interface.

SVG comes in two modes. *Text* keeps the glyphs editable as type but needs the font at the
other end. *Outlines* flattens each glyph to a real path — no font dependency, which is what
print and embroidery want.

## Building

No Gradle wrapper is committed; CI provisions Gradle 8.11.1.

```
gradle testDebugUnitTest    # engine, ramps, character sets
gradle assembleDebug
```

Release builds are signed with the committed debug key, so every build installs over the
previous one. `release.yml` on `workflow_dispatch` publishes the APK to a rolling `dev`
release.

## Layout

```
ascii/     CharacterSets, AsciiParams, AsciiEngine (pure Kotlin), Dither, EdgeDetect,
           Palettes, Fonts, AsciiRenderer (Canvas), Pipeline
anim/      Animation tracks and curves, Temporal, GifEncoder, Mp4Encoder, ColorQuantizer
effects/   The nine passes plus PixelOps and the ordered EffectPipeline
data/      Source (still / video), ImageLoader (decode + EXIF), PresetStore, Settings
export/    PNG / TXT / SVG / clipboard / share
ui/        terminal-styled Compose panels
```

The engine is deliberately free of Android types, so cell averaging, the tone curve and
ramp mapping are all unit-tested on the JVM.
