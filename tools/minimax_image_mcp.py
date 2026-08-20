#!/usr/bin/env python3
"""MCP server: wrap MiniMax image_generation API as tools for Cursor.

Tools exposed:
  - generate_image(prompt, *, aspect_ratio, model, n, ref_image_url, style)
  - generate_sun_bear_illustration(scene, *, action, setting, props, out_name,
                                   aspect_ratio, model, ref_image, style)
  - save_generated_image(b64_or_url, out_name, *, size, quality)

Setup:
  1. export MINIMAX_API_KEY=...
  2. pip install --user mcp requests Pillow
  3. Add to ~/.cursor/mcp.json:
     {
       "mcpServers": {
         "minimax-image": {
           "command": "python3",
           "args": ["/abs/path/to/tools/minimax_image_mcp.py"],
           "env": {"MINIMAX_API_KEY": "..."}
         }
       }
     }
  4. Restart Cursor.
"""
from __future__ import annotations

import base64
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path
from typing import Any

from mcp.server.fastmcp import FastMCP
from PIL import Image

# 国内大陆：api.minimaxi.com；海外：api.minimax.io
ENDPOINT = os.environ.get(
    "MINIMAX_IMAGE_ENDPOINT",
    "https://api.minimaxi.com/v1/image_generation",
)
API_KEY = os.environ.get("MINIMAX_API_KEY", "")

# 项目惯例：插画统一收在 core/res-illustrations 的 drawable-nodpi
ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT_DIR = ROOT / "core/res-illustrations/src/main/res/drawable-nodpi"
DEFAULT_SIZE = 384  # 对齐 export_sun_bear_webp.py
DEFAULT_QUALITY = 90

# 小熊风格锚定 prompt（与 tools/sun_bear_prompt.py 中的 SUN_BEAR_STYLE_BLOCK 同步）
SUN_BEAR_STYLE_BLOCK = (
    "A cute 3D rendered cartoon sun bear character with soft plush toy-like texture: "
    "round chubby body, ginger / honey-orange fur (NOT light brown), "
    "large cream-colored muzzle area around the mouth, "
    "small black oval nose with a curved smile line below, "
    "BIG round black button eyes with single small white highlight dot, "
    "TWO LARGE ROUND EARS prominent on top of head with darker brown inner ear, "
    "large round soft pink blush on cheeks. "
    "Soft 3D volumetric rendered look, slightly rounded plastic-like forms, "
    "mild subsurface scattering on ears and cheeks, "
    "soft cel-shading only as subtle ambient occlusion, NOT flat 2D. "
    "Warm pastel palette: ginger orange, caramel brown, cream, soft blue, soft green. "
    "Children's picture book aesthetic with modern 3D rendering, "
    "Pixar-like soft plush toy style. "
    "Character fills 70-80% of frame, centered composition, "
    "single isolated scene with meaningful background, "
    "no text, no watermark, no floating decorative halos, "
    "square or 3:4 framing, soft warm lighting from upper-left."
)

mcp = FastMCP("minimax-image")


# ---------- internal helpers ----------

def _post(payload: dict[str, Any]) -> dict[str, Any]:
    if not API_KEY:
        raise RuntimeError(
            "MINIMAX_API_KEY not set. export MINIMAX_API_KEY=... or set env in mcp.json"
        )
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT,
        data=data,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code}: {body}") from e


def _normalize_subject_reference(ref: str | list[str] | None) -> list[dict] | None:
    """URL → subject_reference array. 本地路径会被跳过（API 拒绝 localhost/私有地址）。"""
    if not ref:
        return None
    refs = [ref] if isinstance(ref, str) else ref
    out: list[dict] = []
    for r in refs:
        if r.startswith("http://") or r.startswith("https://"):
            out.append({"type": "character", "image_file": r})
        else:
            import warnings
            warnings.warn(
                f"subject_reference 仅支持公网 URL，跳过本地路径: {r}",
                stacklevel=2,
            )
    return out if out else None


def _save_image_bytes(raw: bytes, out_path: Path, size: int, quality: int) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    # 假设是 jpeg/png 原始字节 → 解码 → 缩放到 384x384 → 转 webp
    import io
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    if im.size != (size, size):
        im = im.resize((size, size), Image.Resampling.LANCZOS)
    im.save(out_path, "WEBP", quality=quality, method=6)


def _decode_response(resp: dict, out_dir: Path, size: int, quality: int) -> list[Path]:
    """Decode response (base64 or url) into webp files, return saved paths."""
    out_dir.mkdir(parents=True, exist_ok=True)
    data = resp.get("data", {})
    saved: list[Path] = []

    # base64 路径
    if "image_base64" in data:
        for i, b64 in enumerate(data["image_base64"]):
            raw = base64.b64decode(b64)
            # 文件名由调用方提供，这里用 _<i> 占位
            p = out_dir / f"generated_{i}.webp"
            _save_image_bytes(raw, p, size, quality)
            saved.append(p)

    # url 路径（24h 过期，本地缓存；字段名实测为 image_urls，文档示例为 image_url）
    url_list = data.get("image_urls") or data.get("image_url")
    if url_list:
        urls = url_list
        if isinstance(urls, str):
            urls = [urls]
        for i, url in enumerate(urls):
            with urllib.request.urlopen(url, timeout=60) as r:
                raw = r.read()
            p = out_dir / f"generated_{i}.webp"
            _save_image_bytes(raw, p, size, quality)
            saved.append(p)

    return saved


# ---------- MCP tools ----------

@mcp.tool()
def generate_image(
    prompt: str,
    aspect_ratio: str = "1:1",
    model: str = "image-01",
    n: int = 1,
    ref_image_url: str | None = None,
    style: str | None = None,
    out_dir: str | None = None,
    size: int = DEFAULT_SIZE,
    quality: int = DEFAULT_QUALITY,
) -> dict:
    """Generate image from text prompt via MiniMax image_generation API.

    Args:
        prompt: Text description, max 1500 chars.
        aspect_ratio: One of 1:1 16:9 4:3 3:2 2:3 3:4 9:16 21:9.
        model: image-01 or image-01-live (live supports `style`).
        n: 1-9 images.
        ref_image_url: Optional URL or local file path for subject reference (character consistency).
        style: Only for image-01-live. MiniMax-themed style name or JSON string.
        out_dir: Override default output dir (defaults to
                 core/res-illustrations/src/main/res/drawable-nodpi).
        size: Final webp size (square). 384 matches existing project convention.
        quality: webp quality 1-100.

    Returns:
        dict with keys: ok (bool), saved_paths (list[str]), prompt (echoed),
        model, aspect_ratio, ref_used (bool).
    """
    payload: dict[str, Any] = {
        "model": model,
        "prompt": prompt,
        "aspect_ratio": aspect_ratio,
        "n": max(1, min(n, 9)),
        "response_format": "base64",
    }
    if style and model == "image-01-live":
        payload["style"] = style
    sr = _normalize_subject_reference(ref_image_url)
    if sr:
        payload["subject_reference"] = sr

    resp = _post(payload)
    out_path = Path(out_dir) if out_dir else DEFAULT_OUT_DIR
    saved = _decode_response(resp, out_path, size, quality)
    return {
        "ok": True,
        "saved_paths": [str(p.relative_to(ROOT)) for p in saved],
        "prompt": prompt,
        "model": model,
        "aspect_ratio": aspect_ratio,
        "ref_used": bool(sr),
    }


@mcp.tool()
def generate_sun_bear_illustration(
    scene: str,
    action: str = "",
    setting: str = "",
    props: str = "",
    out_name: str = "",
    aspect_ratio: str = "1:1",
    model: str = "image-01",
    ref_image: str | None = None,
    style: str | None = None,
    size: int = DEFAULT_SIZE,
    quality: int = DEFAULT_QUALITY,
) -> dict:
    """Generate a sun-bear-themed illustration matching the existing project style.

    The MiniMax prompt is auto-constructed with the project's style anchor block
    (SUN_BEAR_STYLE_BLOCK), so the result stays consistent with the 330 existing
    ic_sun_bear_*.webp files.

    Args:
        scene: Scene category like 'work_farm', 'home_balcony', 'breakfast_coffee',
                'commute_bus', 'bedtime_book'. Used for style and (optionally) filename.
        action: What the bear is doing, e.g. 'hanging laundry', 'reading book'.
        setting: Background, e.g. 'sunny balcony with plants', 'kitchen counter'.
        props: Optional props/objects, e.g. 'wooden basket, colorful clothes'.
        out_name: Filename without extension, e.g. 'ic_sun_bear_home_balcony'.
                 If empty, auto-derived from scene.
        ref_image: Path/URL to a reference sun bear image for subject consistency.
        aspect_ratio: Default 1:1. Use 4:3 or 3:4 for wider scenes.
        model: image-01 (recommended) or image-01-live.
        style: Only for image-01-live.
        size: Final webp size.
        quality: webp quality.

    Returns:
        dict with saved_paths, full_prompt, model, etc.
    """
    parts = [SUN_BEAR_STYLE_BLOCK]
    parts.append(f"Scene: {scene}.")
    if action:
        parts.append(f"Action: {action}.")
    if setting:
        parts.append(f"Setting: {setting}.")
    if props:
        parts.append(f"Props: {props}.")
    parts.append("Keep the bear's appearance identical to the reference image.")
    prompt = " ".join(parts)

    if not out_name:
        safe = scene.replace(" ", "_").replace("/", "_").lower()
        out_name = f"ic_sun_bear_{safe}"

    # 临时输出到 filename 命名，但后续可改名
    target = DEFAULT_OUT_DIR / f"{out_name}.webp"
    tmp_dir = ROOT / "tools/.tmp_image_gen"
    payload_result = generate_image(
        prompt=prompt,
        aspect_ratio=aspect_ratio,
        model=model,
        n=1,
        ref_image_url=ref_image,
        style=style,
        out_dir=str(tmp_dir),
        size=size,
        quality=quality,
    )
    if not payload_result["ok"] or not payload_result["saved_paths"]:
        return {"ok": False, "error": "no image returned"}

    # 把第一张图重命名到目标位置
    tmp_path = ROOT / payload_result["saved_paths"][0]
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp_path.replace(target)
    return {
        "ok": True,
        "saved_paths": [str(target.relative_to(ROOT))],
        "full_prompt": prompt,
        "model": model,
        "aspect_ratio": aspect_ratio,
        "ref_used": payload_result["ref_used"],
    }


@mcp.tool()
def save_generated_image(
    image_url_or_b64: str,
    out_name: str,
    is_base64: bool = False,
    size: int = DEFAULT_SIZE,
    quality: int = DEFAULT_QUALITY,
    out_dir: str | None = None,
) -> dict:
    """Save a previously generated image (URL or base64) as webp into the project.

    Use after generate_image if you want full control over the output name.
    """
    out_path = Path(out_dir) if out_dir else DEFAULT_OUT_DIR
    out_path.mkdir(parents=True, exist_ok=True)
    target = out_path / f"{out_name}.webp"
    if is_base64:
        raw = base64.b64decode(image_url_or_b64)
    else:
        with urllib.request.urlopen(image_url_or_b64, timeout=60) as r:
            raw = r.read()
    _save_image_bytes(raw, target, size, quality)
    return {
        "ok": True,
        "saved_path": str(target.relative_to(ROOT)),
    }


@mcp.tool()
def list_existing_illustrations(prefix: str = "ic_sun_bear_") -> dict:
    """List existing webp illustrations matching prefix (default 'ic_sun_bear_').

    Useful before generating to avoid duplicates and understand naming conventions.
    """
    if not DEFAULT_OUT_DIR.exists():
        return {"count": 0, "names": []}
    names = sorted(
        p.name for p in DEFAULT_OUT_DIR.glob(f"{prefix}*.webp")
    )
    return {"count": len(names), "names": names}


if __name__ == "__main__":
    mcp.run()
