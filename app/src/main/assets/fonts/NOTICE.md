# Bundled fonts

Both faces are shipped **subsetted** to the 482 code points the built-in character sets use,
which is why they are a few dozen KB each rather than a few MB. Subsetting removes glyphs
only; it changes nothing about the licence terms.

## DejaVu Sans Mono 2.37

`dejavu_mono.ttf`, `dejavu_mono_bold.ttf`, `dejavu_mono_italic.ttf`,
`dejavu_mono_bold_italic.ttf`

Copyright © 2003 Bitstream, Inc. (Bitstream Vera Fonts) and © 2006 Tavmjong Bah (Arev
Fonts); DejaVu changes are in the public domain. Full terms in `LICENSE-DejaVu.txt`.
Source: https://dejavu-fonts.github.io/

## GNU Unifont 16.0.04

`unifont.ttf`

Copyright © Roman Czyborra, Paul Hardy and contributors. Dual-licensed under the SIL Open
Font License 1.1 and the GNU GPL v2 or later **with the GNU font embedding exception**,
which explicitly permits embedding the font in a document or application without the
embedding document falling under the GPL.

- OFL 1.1: https://openfontlicense.org/
- Source and full terms: https://unifoundry.com/unifont/

Used here as the coverage fallback: it is the only one of the two that can draw braille,
kana, runic, Hebrew and circled numerals — 12 of the 48 built-in sets.
