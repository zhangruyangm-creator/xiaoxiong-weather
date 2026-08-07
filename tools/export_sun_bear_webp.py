#!/usr/bin/env python3
"""Resize generated PNG to 384x384 WebP into drawable-nodpi."""
from __future__ import annotations
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/drawable-nodpi"
SIZE = 384

def export_one(src: Path) -> Path:
    name = src.stem
    if name.endswith("_rect"):
        name = name[: -len("_rect")]
    dst = OUT / f"{name}.webp"
    im = Image.open(src).convert("RGB")
    im = im.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    im.save(dst, "WEBP", quality=90, method=6)
    print(f"OK {src.name} -> {dst.relative_to(ROOT)}")
    return dst

def main(argv: list[str]) -> None:
    paths = [Path(a) for a in argv] if argv else list((ROOT / "tools/art_rect_out").glob("*.png"))
    if not paths:
        raise SystemExit("no inputs")
    for p in paths:
        export_one(p)

if __name__ == "__main__":
    main(sys.argv[1:])
