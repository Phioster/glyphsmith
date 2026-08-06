#!/usr/bin/env python3
"""Fetch the screenshots the walkthrough took and lay them out on one sheet.

The emulator test drives the app and photographs every screen; this is the other
half of that, and the half a person or a model actually looks at. Twenty-five
separate PNGs are twenty-five things to open. One sheet, in order, with each
screen labelled, is a thing you can scan in a few seconds and point at.

    python3 tools/screens.py            # the newest run, successful or not
    python3 tools/screens.py 31092372409

Writes ``build/screens/sheet.png`` and leaves the individual frames beside it.
Needs the GitHub CLI, logged in, and Pillow.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys

REPO = "Phioster/glyphsmith"
WORKFLOW = "instrumented.yml"
OUT = os.path.join("build", "screens")
COLUMNS = 5
WIDTH = 320


def run(*args: str) -> str:
    done = subprocess.run(args, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit(f"{' '.join(args)}\n{done.stderr.strip()}")
    return done.stdout


def latest_run() -> str:
    listing = run(
        "gh", "run", "list", "-R", REPO, "--workflow", WORKFLOW,
        "--limit", "1", "--json", "databaseId,conclusion,headBranch",
    )
    runs = json.loads(listing)
    if not runs:
        sys.exit("no walkthrough has run yet")
    first = runs[0]
    print(f"run {first['databaseId']} on {first['headBranch']}: {first['conclusion']}")
    return str(first["databaseId"])


def frames(directory: str) -> list[str]:
    """Every PNG under the artifact, which nests them per device."""
    found = []
    for root, _, files in os.walk(directory):
        found += [os.path.join(root, f) for f in files if f.endswith(".png")]
    return sorted(found, key=os.path.basename)


def sheet(paths: list[str], target: str) -> None:
    from PIL import Image, ImageDraw

    label = 18
    first = Image.open(paths[0])
    height = round(WIDTH * first.height / first.width)
    rows = -(-len(paths) // COLUMNS)
    canvas = Image.new("RGB", (COLUMNS * WIDTH, rows * (height + label)), "black")
    pen = ImageDraw.Draw(canvas)

    for i, path in enumerate(paths):
        x = (i % COLUMNS) * WIDTH
        y = (i // COLUMNS) * (height + label)
        canvas.paste(Image.open(path).convert("RGB").resize((WIDTH, height)), (x, y + label))
        pen.text((x + 4, y + 4), os.path.basename(path)[:-4], fill="#7CFC7C")

    canvas.save(target)


def main() -> None:
    run_id = sys.argv[1] if len(sys.argv) > 1 else latest_run()

    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT)
    run("gh", "run", "download", run_id, "-R", REPO, "-n", "screens", "-D", OUT)

    paths = frames(OUT)
    if not paths:
        sys.exit("the run uploaded no screenshots")

    target = os.path.join(OUT, "sheet.png")
    sheet(paths, target)
    print(f"{len(paths)} screens -> {target}")


if __name__ == "__main__":
    main()
