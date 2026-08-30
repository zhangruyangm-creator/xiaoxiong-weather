#!/usr/bin/env python3
"""Remove baked-in white canvas / sticker halo from farm crop webp assets."""

from __future__ import annotations

import argparse
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image


def edge_white_ratio(img: Image.Image, threshold: int = 240, band: int = 3) -> float:
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    edge: list[tuple[int, int, int]] = []
    for x in range(w):
        for y in range(h):
            if x < band or y < band or x >= w - band or y >= h - band:
                r, g, b, a = px[x, y]
                if a > 10:
                    edge.append((r, g, b))
    if not edge:
        return 0.0
    white = sum(1 for r, g, b in edge if r >= threshold and g >= threshold and b >= threshold)
    return white / len(edge)


def floodfill_thresh(arr: np.ndarray, sx: int, sy: int, thresh: int = 45) -> None:
    h, w = arr.shape[:2]
    target = arr[sy, sx, :3].astype(np.int16)
    visited = np.zeros((h, w), dtype=bool)
    stack = [(sx, sy)]
    while stack:
        x, y = stack.pop()
        if visited[y, x]:
            continue
        visited[y, x] = True
        if arr[y, x, 3] == 0:
            continue
        cur = arr[y, x, :3].astype(np.int16)
        if np.max(np.abs(cur - target)) > thresh:
            continue
        arr[y, x] = [0, 0, 0, 0]
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[ny, nx]:
                stack.append((nx, ny))


def near_white(r: int, g: int, b: int, a: int) -> bool:
    return a > 0 and r >= 222 and g >= 220 and b >= 220


def peel_near_white(arr: np.ndarray, iterations: int = 40) -> None:
    h, w = arr.shape[:2]
    for _ in range(iterations):
        clear: list[tuple[int, int]] = []
        for y in range(h):
            for x in range(w):
                if arr[y, x, 3] == 0:
                    continue
                r, g, b, a = (int(v) for v in arr[y, x])
                if not near_white(r, g, b, a):
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and arr[ny, nx, 3] == 0:
                        clear.append((x, y))
                        break
        if not clear:
            break
        for x, y in clear:
            arr[y, x, 3] = 0


def peel_light_fringe(arr: np.ndarray, iterations: int = 16) -> None:
    h, w = arr.shape[:2]
    for _ in range(iterations):
        clear: list[tuple[int, int]] = []
        for y in range(h):
            for x in range(w):
                a = int(arr[y, x, 3])
                if a == 0 or a == 255:
                    continue
                r, g, b = (int(arr[y, x, i]) for i in range(3))
                if min(r, g, b) < 150:
                    continue
                if max(r, g, b) - min(r, g, b) > 35:
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and arr[ny, nx, 3] == 0:
                        clear.append((x, y))
                        break
        if not clear:
            break
        for x, y in clear:
            arr[y, x, 3] = 0


def remove_white_border(img: Image.Image) -> Image.Image:
    arr = np.array(img.convert("RGBA"), copy=True)
    h, w = arr.shape[:2]
    for sx, sy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        floodfill_thresh(arr, sx, sy, 45)
    peel_near_white(arr)
    peel_light_fringe(arr)
    return Image.fromarray(arr)


def should_process(path: Path) -> bool:
    with Image.open(path) as img:
        return edge_white_ratio(img) > 0.55


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dir",
        type=Path,
        default=Path("game/farm/src/main/res/drawable-nodpi"),
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    files = sorted(args.dir.glob("farm_*.webp"))
    todo = [f for f in files if should_process(f)]
    print(f"found {len(files)} farm assets, {len(todo)} need white-border cleanup")
    for path in todo:
        if args.dry_run:
            print("would process", path.name)
            continue
        out = remove_white_border(Image.open(path))
        tmp = path.with_suffix(".png")
        out.save(tmp)
        path.unlink()
        import subprocess

        subprocess.run(
            ["cwebp", "-q", "85", str(tmp), "-o", str(path)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        tmp.unlink()
        print("processed", path.name)


if __name__ == "__main__":
    main()
