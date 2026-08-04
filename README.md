# Glyphsmith

An Android dithering and retro-effect tool. Load an image, a video or the camera, reduce it —
to a palette as pixels, or onto a character grid as glyphs — and export the render as a PNG,
JPG, WEBP, GIF, MP4 or SVG, or the grid itself as a `.txt`.

Three render modes share one engine. The 79 dither algorithms quantise into *levels*, and a
level becomes a colour or a character depending on the mode; nothing below that seam knows
which one it is feeding. Pixel dithering is the default; glyph art is the optional half; the
third mode chains them, dithering to a palette first and reading the *result* as glyphs, which
is the only way to get a paletted dither and a `.txt` out of one render.

It began by rebuilding the feature set of **Script Slayer** — the glyph module inside Studio
AAA's Dither Boy — and has since grown past it into the dithering half: the same vocabulary
where it fits, an entirely separate implementation throughout. Script Slayer is a reference,
not the product definition; pixel dithering is.

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

**Render modes** — three. The mode decides what a quantised level *becomes*; nothing that
produced the level knows which mode asked for it. *Pixel dither* turns each level into a
colour and is the default.
*Glyph art* turns it into a character from a ramp. *Pixel dither → glyphs* runs a palette
dither to completion first and then reads that bitmap as glyphs — the other two fork at the
quantised level, so a paletted dither could not be turned into characters at all. The panels
that do not apply to the active mode disappear from the tab row rather than sitting there
inert.

**Tone** — gamma → contrast → brightness, plus hue, saturation, midtones, highlights,
pre-blur and denoise, applied to the sampled grid before anything quantises it. Without it a
flat photo only ever reaches the middle third of the range.

**Dithering** — 79 algorithms in seven families. Error diffusion with serpentine scanning: Floyd–Steinberg,
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
driven until it visibly breaks down. Beyond those come the patterned, polygon, glitch and
special families — tiles, tessellations, region fills and deliberate signal faults — each filed
by what it does to the image rather than by how loud it looks.

The seven shelves and their sizes: error diffusion 21, patterned 18, special 16, glitch 12,
ordered 8, polygon 4, and basic — which holds only *no dithering at all*. A style's shelf is
where someone browsing will look for it; how it actually decides a cell is a separate axis,
and a glitch and a halftone can share a mechanism.

**Colour** — single ink colour, source-sampled colour, or a palette. 44 palettes in 11
categories, with individually editable stops, per-stop locks, a shuffle that respects them,
a palette depth independent of the glyph depth, and extraction straight from the loaded
image. Three distance metrics decide which palette entry a colour is nearest, OKLab by
default. Transparent or hex-picked background.

**Effects** — seventeen stackable passes over the rendered image, in an order you can change:
post processing, blur/sharpen, tint, chromatic aberration, JPEG databending, pixel sort,
slice shift, interlace, modulation lines, diffraction stars, subtexture, spot-colour print,
CMYK halftone, colour depth, blue-noise dither, Epsilon Glow, and CRT warp. The order is not
cosmetic — glitch before glow blooms, glitch after glow cuts the bloom apart, and the warp
belongs last because the glass is the outermost thing a tube has.

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

**Camera** — a photo through the system camera app, or a live preview rendered from the
camera stream. The live path asks CameraX for RGBA frames directly rather than converting
YUV per pixel, and drops frames while one is still rendering, which is the difference between
a preview and a slideshow that lags reality.

**Animation** — a still image animated by moving the parameters, not the picture. Eleven
tracks, eight curves (four of them one-way ramps, marked as such because they do not close
a loop), and **Temporal Variation**: nine animated noise patterns that shift the dither
threshold itself. Every pattern is periodic over the loop, so the last frame lands back on
the first.

A track spans the whole loop and repeats. A *segment* is the other shape: one property moving
across a slice of the loop and then stopping, which is the only way to say "fade in, hold,
fade out" — three segments on one property, the middle one going from a value to itself.
Outside every segment the base value applies, so holding is stated rather than inherited from
a neighbour, and two segments on one property may not overlap.

**Presets** — 89 shipped starting points, plus whatever you save. 83 of them are the curated
library, shelved by mechanism across eleven categories — Classic Dither, Error Diffusion,
Ordered Dither, Pattern, Print, Geometry, Color, Glitch, Motion, Layered and Glyph Art — and
the split is deliberately 80–90 % pixel dithers, with glyph art present as a clearly named
minority. A preset holds the *complete* state, effects and animation included, so applying one
is a single tap. The Motion set arrives with animation already aimed: apply, press play. Each
one renders a thumbnail from your loaded image, favourites sort to the top, and `surprise me`
rolls a look within ranges chosen to actually produce something.

The remaining 6 are the Algorithm Lab, kept off the curated shelves and counted separately: one
preset per kernel at otherwise identical settings, so the algorithm is the only thing that
differs. A library that opened on six versions of the same picture would describe a test bench
rather than an application.

**Layers** — extra renderings of the same picture stacked over the first, each with its own
complete settings plus blend mode, opacity, offset, scale, rotation and flip. A layer is
captured from the settings in force rather than edited separately: build a look, capture it,
change the settings, capture again. Layers render at the output size and are scaled
afterwards, so scaling one up enlarges its glyphs rather than resampling a smaller picture
of them.

**Batch** — pick a set of images and run them all through the current settings. Your loaded
image and its framing come back afterwards.

**Export** — PNG, JPG or WEBP to `Pictures/Glyphsmith`; the character grid, GIF, MP4 and SVG
to `Download/Glyphsmith`; clipboard; or the system share sheet. JPEG has no alpha, so the
format picker says which formats a transparent background survives. Presets are saved as
JSON, and palettes can be exported on their own — a name and a list of hex colours, editable
by hand.

Every export goes through one place that decides what happened and says so, rather than each
button spelling out its own status line — which is why `saved to Download/Glyphsmith` reads
identically from four different paths and a refused write reports a failure instead of a
silence.

**Preview** — Live halves the still preview's resolution for a heavy effect chain; the export
is untouched. Playback has its own pair: Quick halves the resolution *and* renders every
second frame, because sixty frames at half size is still sixty renders. Playback can loop or
stop on the last frame.

**Themes** — seven interface looks: Matrix, Amber CRT, Ice, Handheld and Rose are dark;
Parchment and Medieval are light. The theme is an app setting rather than part of a preset:
loading someone else's preset should not repaint your interface.

## Glyph Art

The optional half. Everything above applies to it — the same dither algorithms, palettes,
effects, layers and animation — and everything below applies only to it. Its controls leave
the tab row when the active mode has no characters in it.

**Character sets** — 48 built-in ramps across 11 categories: ASCII, numbers, symbols,
blocks, braille, geometric, languages, cards, unicode, lines, misc. Every ramp is ordered by
ink coverage, and the panel shows the live ramp — narrowed, injected and inverted exactly as
the engine will use it.

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
makes those sets sit unevenly in their cells. DejaVu Sans Mono covers most of the 48 sets in
four real styles; GNU Unifont covers all 48 in one. `AUTO` picks the first face that can
draw every glyph in the current ramp and says which one it used — and falls back to the
system face for anything typed into Inject Characters, since the subsets don't contain it.
Both together are under 170 KB. See `app/src/main/assets/fonts/NOTICE.md`.

**Ramp order** — the sets are ordered by ink coverage because the engine maps luminance
straight onto a ramp index. That order used to be a hand-made guess. Now each glyph can be
measured — drawn and counted, in the face that will actually render it — and the ramp sorted
by the result, injected characters included. The panel shows every glyph's measured coverage
and lets the order be changed by hand.

**Edges** — Sobel over the cell grid; a cell whose gradient clears the threshold takes a
glyph matching the edge's direction rather than its brightness. Four edge sets, plus an
edges-only mode that gives line art.

**Text output** — the character grid as `.txt`, and two formats that keep the colour a `.txt`
drops: a self-contained HTML page, and ANSI escapes a terminal renders straight from `cat`.

SVG comes in two modes. *Text* keeps the glyphs editable as type but needs the font at the
other end. *Outlines* flattens each glyph to a real path — no font dependency, which is what
print and embroidery want.

## Building

No Gradle wrapper is committed; CI provisions Gradle 8.11.1.

```
gradle testDebugUnitTest    # engine, ramps, character sets, layering, provider ids
gradle detekt               # config/detekt/detekt.yml
gradle lintDebug
gradle assembleDebug
```

CI runs all four on every push.

Release builds are signed with a key that never enters the repository: `release.yml` decodes
it from a secret into the runner for one job, and the build fails rather than publishes if the
resulting APK is not signed by it. Without that secret a local `assembleRelease` falls back to
debug signing, so it still works.

A `workflow_dispatch` run builds an APK and uploads it as a run artifact, publishing nothing;
pushing a `vX.Y.Z` tag is what cuts a GitHub Release.

## Layout

```
core/      The parts that know nothing about any render module: dither/ (79 algorithms,
           matrices, screens, the provider registry), color/ (palettes, three distance
           metrics, nearest-colour), image/ (Pixels, Adjustments), pipeline/ (the node
           abstraction, buffer pool, row parallelism), provider/ (Provider, Registry),
           serial/ (the stable wire ids everything is stored as)
render/    Shared render infrastructure: RenderSettings, RenderMode, Layers, EdgeDetect,
           CellSampler and QuantisePass, PixelDitherRenderer and ColorDiffusionPass, the
           pixel module, and the RenderModule / ModuleRender / RenderModuleSet seam
glyph/     The glyph module: CharacterSets, GlyphEngine (pure Kotlin), GlyphRenderer
           (Canvas), Fonts, the ramp helpers, and its own outputs — TextExporters, SvgExporter
pipeline/  RenderPipeline, LayerCompositor, Providers — runs whatever module it is handed,
           without knowing which modules exist
anim/      Animation tracks, segments and curves, Temporal, GifEncoder, Mp4Encoder,
           ColorQuantizer
effects/   The seventeen passes plus PixelOps and the node-based EffectPipeline
data/      Source (still / video), ImageLoader (decode + EXIF), the camera, PresetLibrary,
           PresetStore, PresetSchema, PaletteFile, Settings
state/     The slices lifted out of the view model: SourceController, HistoryController,
           PresetController, ExportCoordinator, PlaybackPlan, RandomLook
export/    The byte sink — MediaStore, clipboard, share sheet. What produced the bytes is
           not its business
ui/        terminal-styled Compose panels
```

Which module renders which mode is bound in `AppRenderModules`, at the top level beside the
view model — the one place that names glyph art and the pixel module in the same breath.
Everything below it is either shared and knows no module, or is a module and knows no other.
That is not a convention: `LayeringTest` reads the imports and fails on a dependency pointing
the wrong way.

The engine is deliberately free of Android types, so cell averaging, the tone curve, the
dither path and ramp mapping are all unit-tested on the JVM — about 589 tests across 60
classes, three of which need Robolectric.
