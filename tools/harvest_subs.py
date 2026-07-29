#!/usr/bin/env python3
"""
Pull subtitles — and only subtitles — for a YouTube channel or playlist, then flag the
transcripts that mention something we don't already know about.

Written because the Dither Boy beginners guide showed that essentially all the useful
information was in the *spoken* content, not the pictures. Frames confirmed panel layouts;
the transcripts carried the semantics. A subtitle track is ~66 KB against ~60 MB for the
video, so fetching every tutorial's subtitles costs less than fetching one video.

    python3 harvest_subs.py <playlist-or-channel-url> [outdir]

Downloads nothing but .vtt, cleans each into plain timestamped text, and prints a ranked
list of which transcripts are worth reading — scored by how many terms they contain that
are not already in KNOWN.
"""

import html
import re
import subprocess
import sys
from pathlib import Path

# Everything the beginners-guide series already established. A transcript that only repeats
# these is not worth reading again; the score below counts what is *missing* from this list.
KNOWN = {
    "signal chain", "temporal variation", "adjustments", "dither", "post processing",
    "tint", "epsilon glow", "jpeg glitch", "chromatic", "brightness", "contrast",
    "saturation", "midtones", "highlights", "hue", "blur", "denoise", "threshold",
    "threshold smoothing", "radius", "radius compensation", "intensity", "aspect ratio",
    "direction", "falloff", "epsilon", "distance scale", "max displace", "red channel",
    "green channel", "blue channel", "orb count", "orb size", "orb intensity",
    "orb offset", "orb random", "orb direction", "line scale", "luminance threshold",
    "scale dpi", "color depth", "ordered cycling", "preset", "palette", "library",
    "looped", "rendered", "standard", "quick", "timeline", "animation layer",
    "floyd-steinberg", "sierra", "bayer", "bayer void", "atkinson", "jarvis", "stucki",
}

# The two shapes in which a spoken tutorial actually names a thing: "the max displace
# slider", and "an algorithm called Sierra". Matching the *shape* rather than scanning for
# unknown words is the difference between a list of controls and a list of sentence
# fragments — an earlier version scored "and then we" as a discovery.
NAMED_BEFORE = re.compile(
    r"\b((?:[a-z]+[ -]){0,2}[a-z]+)\s+"
    r"(?:slider|toggle|button|control|parameter|setting|option|effect|effects|"
    r"algorithm|mode|panel|tab|section|checkbox|dropdown)\b"
)
NAMED_AFTER = re.compile(
    r"\b(?:called|named|which is|known as)\s+((?:[a-z]+[ -]){0,2}[a-z]+)\b"
)

# Words that never appear inside a control's name. A candidate containing any of them
# anywhere is filler that happened to sit in front of the cue word — "hover over the
# slider" is not a control called "hover over the".
FILLER = {
    "the", "a", "an", "this", "that", "these", "those", "my", "your", "our", "its",
    "some", "any", "each", "every", "other", "another", "one", "first", "second", "next",
    "last", "same", "whole", "entire", "just", "only", "very", "really", "quite", "here",
    "there", "now", "then", "and", "but", "or", "so", "if", "when", "what", "which",
    "is", "was", "are", "be", "get", "got", "have", "has", "in", "on", "at", "of", "to",
    "for", "with", "from", "by", "as", "it", "you", "we", "i", "like", "kind", "sort",
    "not", "no", "more", "less", "why", "how", "well", "also", "even", "still", "much",
    "going", "want", "need", "make", "made", "take", "look", "see", "use", "using",
    "add", "move", "set", "put", "turn", "click", "choose", "pick", "load", "save",
    "give", "gives", "let", "lets", "can", "will", "would", "should", "do", "does",
    "up", "down", "out", "over", "into", "onto", "about", "than", "because", "cause",
}


def fetch(url: str, outdir: Path) -> None:
    """Subtitles only. --skip-download is what keeps this cheap."""
    outdir.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            sys.executable, "-m", "yt_dlp",
            "--skip-download",
            "--write-subs", "--write-auto-subs",
            "--sub-langs", "en.*",
            "--sub-format", "vtt",
            "--ignore-errors",
            "-o", str(outdir / "%(playlist_index)03d-%(title)s.%(ext)s"),
            url,
        ],
        check=False,
    )


def clean(path: Path) -> str:
    """VTT to timestamped plain text, with the rolling duplicates auto-captions produce
    collapsed — they inflate a 6-minute talk to three times its real length."""
    raw = path.read_text(encoding="utf-8", errors="replace")
    out, seen, stamp = [], set(), None
    for line in raw.split("\n"):
        if "-->" in line:
            stamp = line.split("-->")[0].strip().split(".")[0]
            continue
        text = re.sub(r"<[^>]+>", "", line).strip()
        if not text or text.startswith(("WEBVTT", "Kind:", "Language:", "NOTE")):
            continue
        text = html.unescape(text)
        if text in seen:
            continue
        seen.add(text)
        out.append(f"[{stamp}] {text}")
    return "\n".join(out)


def trim(phrase: str) -> str:
    """The name inside a captured phrase, or "" when the phrase is only filler.

    Leading filler is stripped; filler anywhere *after* that disqualifies the candidate
    outright, because a real control name does not contain "you" or "the" in the middle.
    """
    words = phrase.split()
    while words and words[0] in FILLER:
        words.pop(0)
    if not words or any(w in FILLER for w in words):
        return ""
    return " ".join(words)


# Studio AAA publish tutorials for several products. A Photoshop or After Effects video is
# full of terms this project has never heard of and scores beautifully on novelty alone,
# which is exactly the wrong answer — novelty has to be gated on the video being about the
# software we are studying at all.
SUBJECT = re.compile(r"\bdither ?boy\b", re.I)
OTHER = re.compile(
    r"\b(after effects|photoshop|glitch machine|flareware|illustrator|blender|audacity|"
    r"premiere|figma|procreate)\b",
    re.I,
)


def relevance(text: str) -> float:
    """0..1 — how much this transcript is about the subject rather than a sibling product."""
    ours = len(SUBJECT.findall(text))
    theirs = len(OTHER.findall(text))
    if ours == 0:
        return 0.0
    return ours / (ours + theirs)


def score(text: str) -> tuple[int, list[str]]:
    """Named controls and features in this transcript that KNOWN does not already hold."""
    lower = re.sub(r"\[[^\]]*\]", " ", text.lower())
    found = set()
    for pattern in (NAMED_BEFORE, NAMED_AFTER):
        for match in pattern.finditer(lower):
            name = trim(match.group(1))
            if not name or len(name) < 3:
                continue
            if name in KNOWN or any(name == k or name in k for k in KNOWN):
                continue
            found.add(name)
    ranked = sorted(found)
    return len(ranked), ranked[:14]


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    outdir = Path(sys.argv[2] if len(sys.argv) > 2 else "subs")
    fetch(sys.argv[1], outdir)

    results = []
    for vtt in sorted(outdir.glob("*.vtt")):
        # yt-dlp writes en, en-orig and sometimes en-GB for the same video.
        if ".en-orig." in vtt.name:
            continue
        text = clean(vtt)
        target = vtt.with_suffix(".txt")
        target.write_text(text, encoding="utf-8")
        n, sample = score(text)
        rel = relevance(text)
        # Novelty alone ranks a Photoshop tutorial first. Weighting by relevance is what
        # makes the list answer "which of these should I read", not "which is most unlike
        # what I know".
        results.append((round(n * rel, 1), n, round(rel, 2), target.name, sample))

    results.sort(reverse=True)
    kept = [r for r in results if r[0] > 0]
    print(f"\n{len(results)} transcripts, {len(kept)} about the subject. Ranked:\n")
    for weighted, n, rel, name, sample in kept:
        print(f"  {weighted:6.1f}  (novel {n}, relevance {rel})  {name}")
        if sample:
            print(f"          {', '.join(sample)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
