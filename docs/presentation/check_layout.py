#!/usr/bin/env python3
"""Heuristic layout checker: estimates wrapped text height for every shape and
flags shapes whose text likely overflows its box or leaves the slide."""

import sys
from pathlib import Path

from PIL import ImageFont
from pptx import Presentation
from pptx.util import Emu

ROOT = Path(__file__).resolve().parents[2]
PPTX = ROOT / "docs" / "presentation" / "ScreenDrop-presentation.pptx"

FONTS = {
    ("Noto Sans", False): "/usr/share/fonts/noto/NotoSans-Regular.ttf",
    ("Noto Sans", True): "/usr/share/fonts/noto/NotoSans-Bold.ttf",
    ("JetBrains Mono", False): "/home/ikar/.local/share/fonts/deck/JetBrainsMono-Regular.ttf",
    ("JetBrains Mono", True): "/home/ikar/.local/share/fonts/deck/JetBrainsMono-Bold.ttf",
}
_cache = {}


def font_for(name, bold, size):
    key = (name, bold, int(size * 4))
    if key not in _cache:
        _cache[key] = ImageFont.truetype(
            FONTS.get((name, bold), FONTS[("Noto Sans", bold)]), int(size * 4)
        )
    return _cache[key]


def text_width_pt(value, name, bold, size):
    return font_for(name, bold, size).getlength(value) / 4.0


def measure_runs(p, width_pt):
    """Greedy word-wrap across runs with individual font sizes -> (lines, height)."""
    tokens = []  # (word, size, bold, name)
    for r in p.runs:
        size = r.font.size.pt if r.font.size else 18
        name = r.font.name or "Noto Sans"
        bold = bool(r.font.bold)
        for word in r.text.replace("\n", " ").split(" "):
            tokens.append((word, size, bold, name))
    lines = []
    cur_w = 0.0
    cur_size = 0.0
    for word, size, bold, name in tokens:
        if word == "":
            continue
        w = text_width_pt(word, name, bold, size)
        space = text_width_pt(" ", name, bold, size)
        if cur_size and cur_w + space + w > width_pt:
            lines.append(cur_size)
            cur_w, cur_size = w, size
        else:
            cur_w += (space if cur_size else 0) + w
            cur_size = max(cur_size, size)
    if cur_size:
        lines.append(cur_size)
    if not lines:
        return 0, 0.0
    height = sum(s * 1.21 for s in lines)
    return len(lines), height


def main():
    prs = Presentation(str(PPTX))
    slide_w, slide_h = prs.slide_width, prs.slide_height
    problems = []
    for idx, slide in enumerate(prs.slides, start=1):
        for shp in slide.shapes:
            if not shp.has_text_frame or not shp.text_frame.text.strip():
                continue
            tf = shp.text_frame
            if shp.left + shp.width > slide_w + Emu(10000) or shp.top + shp.height > slide_h + Emu(10000):
                problems.append((idx, "OUT-OF-SLIDE", tf.text[:40]))
            inner_w = (shp.width - tf.margin_left - tf.margin_right) / 12700.0
            total = 0.0
            for p in tf.paragraphs:
                if not p.runs:
                    continue
                _, h = measure_runs(p, inner_w)
                total += h
                total += (p.space_before.pt if p.space_before else 0) + (
                    p.space_after.pt if p.space_after else 0
                )
            box_pt = shp.height / 12700.0
            if total > box_pt * 1.08 + 2:
                problems.append(
                    (idx, "OVERFLOW", tf.text[:48].replace("\n", " / "), round(total), round(box_pt))
                )
    if not problems:
        print("layout: no obvious problems")
        return 0
    for p in problems:
        print(p)
    print(f"\n{len(problems)} potential issue(s)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
