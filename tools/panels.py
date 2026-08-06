#!/usr/bin/env python3
"""Read a reference app's control panel off a public tutorial video.

Companion to `harvest_subs.py`, which established that the *spoken* content carries
the semantics. This covers the other half: the numbers. A tutorial that shows the
panel while a look is being built states, in one frame, what a paragraph of
description cannot — which style, and every slider value that produced it.

    python3 tools/panels.py <youtube-url> 92 116 140 176

Downloads the video once at 1080p (a panel is unreadable below that — 480p was
tried and the labels are mush), takes one frame at each timestamp in seconds,
crops the right-hand column where the controls live, and writes the crops plus a
contact sheet into `build/panels/`.

Why crop rather than look at the whole frame: the panel is about a sixth of the
width, so a full frame scaled to something viewable throws away exactly the pixels
that carry the numbers.

This reads a public interface as a feature reference, which is what CLAUDE.md
permits. It is not a way to copy presets, assets or names — see *Reference
products*.
"""

from __future__ import annotations

import os
import subprocess
import sys

OUT = os.path.join("build", "panels")
VIDEO = os.path.join(OUT, "source.webm")

# The reference app's controls sit in a column down the right. Fractions of the
# frame rather than pixels, so a 1080p and a 1440p capture crop the same place.
LEFT = 0.815
WIDTH = 0.180
TOP = 0.035
HEIGHT = 0.940


def run(*args: str) -> None:
    done = subprocess.run(args, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit(f"{' '.join(args[:3])}…\n{done.stderr.strip()[-600:]}")


def fetch(url: str) -> None:
    if os.path.exists(VIDEO):
        print(f"using the copy already in {VIDEO}")
        return
    print("fetching at 1080p — a panel below that is unreadable")
    run("yt-dlp", "-f", "bv*[height<=1080]", "-o", VIDEO, url)


def size() -> tuple[int, int]:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v",
         "-show_entries", "stream=width,height", "-of", "csv=p=0", VIDEO],
        capture_output=True, text=True,
    ).stdout.strip().split(",")
    return int(out[0]), int(out[1])


def crop(seconds: list[int]) -> list[str]:
    width, height = size()
    box = "%d:%d:%d:%d" % (
        round(width * WIDTH), round(height * HEIGHT),
        round(width * LEFT), round(height * TOP),
    )
    written = []
    for at in seconds:
        path = os.path.join(OUT, f"panel-{at:04d}.png")
        run("ffmpeg", "-v", "error", "-y", "-ss", str(at), "-i", VIDEO,
            "-frames:v", "1", "-vf", f"crop={box},scale=680:-1", path)
        written.append(path)
        print(f"  {at:>5}s -> {path}")
    return written


def sheet(paths: list[str]) -> None:
    from PIL import Image

    ims = [Image.open(p).convert("RGB") for p in paths]
    w, h = ims[0].size
    canvas = Image.new("RGB", (w * len(ims), h), "black")
    for i, im in enumerate(ims):
        canvas.paste(im, (i * w, 0))
    target = os.path.join(OUT, "sheet.png")
    canvas.save(target)
    print(f"{len(ims)} panels -> {target}")


def main() -> None:
    if len(sys.argv) < 3:
        sys.exit(__doc__)

    os.makedirs(OUT, exist_ok=True)
    fetch(sys.argv[1])
    paths = crop([int(a) for a in sys.argv[2:]])
    if paths:
        sheet(paths)


if __name__ == "__main__":
    main()
