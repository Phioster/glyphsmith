# Examples

Fifteen of the shipped presets, all run over the same photograph, so what differs between the
images is the preset and nothing else.

They are chosen to differ in **mechanism** rather than in texture. Fifteen threshold styles would
look like fifteen variations of one picture; these are error diffusion, halftone screens, print
separations, palette reductions, modulated diffusion, radial and glitch families.

| File | Preset | What it is |
| --- | --- | --- |
| `01-one-bit.png` | one bit | Floyd–Steinberg at two levels — what "dithered" has meant for forty years |
| `02-clustered-dot.png` | clustered dot | a halftone screen: the dot grows, the grid does not move |
| `03-process-rosette.png` | process rosette | four printing screens at their classic angles |
| `04-riso-two-colour.png` | riso two colour | two inks, and the paper showing between them |
| `05-gameboy.png` | gameboy | four greens, and nothing else available |
| `06-c64-lores.png` | c64 lores | a hard palette over large cells |
| `07-oklab-crush.png` | oklab crush | colour reduced where the eye can see the difference |
| `08-vapour-wash.png` | vapour wash | error diffusion under a colour wash |
| `09-wave-weave.png` | wave weave | a wave surface *and* a diffused error — the lines bend around the subject |
| `10-orb-lattice.png` | orb lattice | the same mechanism on an orb field, at a coarse period |
| `11-low-bit-halftone.png` | low bit halftone | a halftone with almost no colours left to print with |
| `12-spiral-grain.png` | spiral grain | error diffused along a spiral rather than along the rows |
| `13-spinning-burst.png` | spinning burst | a radial threshold — the pattern has a centre |
| `14-crt-arcade-monitor.png` | crt arcade monitor | curved glass, scanlines, a shadow in the corners |
| `15-vhs-dub.png` | vhs dub | a tape that has been copied one time too many |

Rendered at 640 px wide. `00-source.jpg` is the photograph they were all made from, so anything
here can be reproduced by loading it and applying the named preset.

## The photograph

> *"Tribal Dance"* by **BdwayDiva1**, licensed under
> [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/).
> Source: <https://www.flickr.com/photos/19381472@N04/2306372642>

Used and redistributed here under that licence, unmodified as `00-source.jpg`; the fifteen
renders are derivative works of it and carry the same attribution.

Everything else in this folder is output of this repository's own code.
