# Glyphsmith

An Android ASCII-art forge: load an image, map it onto a character grid, export the render
as a PNG or the grid itself as a `.txt`.

It's a rebuild of the feature set of **Script Slayer** (the ASCII module inside Studio AAA's
Dither Boy) for the phone — same controls, same vocabulary, independent implementation.
Nothing here is derived from Dither Boy's code or assets.

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

**Tone** — gamma → contrast → brightness, applied to each cell's luminance before it picks a
glyph. Without it a flat photo only ever reaches the middle third of the ramp.

**Colour** — single ink colour, source-sampled colour, or a palette. 19 palettes in 6
categories, with individually editable stops. Transparent or hex-picked background.

**Epsilon Glow** — directional bloom over the rendered glyphs: threshold with soft knee,
radius with optional compensation, intensity, aspect ratio, direction, and an inverse-power
falloff `w(d) = 1 / ((d·scale)ⁿ + ε)`. It never touches the character grid, so `.txt` exports
are unaffected.

**Export** — PNG to `Pictures/Glyphsmith`, the character grid to `Download/Glyphsmith`,
clipboard, or the system share sheet. Presets are saved as JSON.

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
ascii/     CharacterSets, AsciiParams, AsciiEngine (pure Kotlin), AsciiRenderer (Canvas)
effects/   EpsilonGlow
data/      ImageLoader (decode + EXIF), PresetStore
export/    PNG / TXT / clipboard / share
ui/        terminal-styled Compose panels
```

The engine is deliberately free of Android types, so cell averaging, the tone curve and
ramp mapping are all unit-tested on the JVM.
