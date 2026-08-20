#!/usr/bin/env python3
"""Generate sun-bear illustrations via MiniMax API and write to res-illustrations.

Usage:
  # 单张
  python tools/gen_illustration.py --scene home_balcony

  # 批量（按场景键展开）
  python tools/gen_illustration.py --scenes home_balcony,home_kitchen,commute_bike

  # 自定义 prompt
  python tools/gen_illustration.py --prompt "小熊在做早餐" --out ic_sun_bear_home_pancake

  # 用 reference 图锁定小熊外观
  python tools/gen_illustration.py --scene home_balcony \\
      --ref core/res-illustrations/src/main/res/drawable-nodpi/ic_sun_bear_work_farm.webp

  # 走 IMAGE-01-LIVE + 风格
  python tools/gen_illustration.py --scene home_balcony --model image-01-live \\
      --style '{"style_name":"Storybook"}'

环境变量:
  MINIMAX_API_KEY            必填
  MINIMAX_IMAGE_ENDPOINT     可选，默认国内 api.minimaxi.com

依赖:
  pip install requests Pillow
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("缺少依赖 Pillow，请先 pip install Pillow")

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from sun_bear_prompt import build_prompt, SCENE_PRESETS  # noqa: E402

ENDPOINT = os.environ.get(
    "MINIMAX_IMAGE_ENDPOINT",
    "https://api.minimaxi.com/v1/image_generation",
)
DEFAULT_OUT_DIR = ROOT / "core/res-illustrations/src/main/res/drawable-nodpi"
DEFAULT_SIZE = 384
DEFAULT_QUALITY = 90


def post(payload: dict) -> dict:
    api_key = os.environ.get("MINIMAX_API_KEY")
    if not api_key:
        sys.exit("缺少 MINIMAX_API_KEY 环境变量")
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT,
        data=data,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        # 友好化错误信息
        friendly = {
            401: "❌ 401: key 无效或过期",
            402: "❌ 402: 余额不足，需要充值",
            403: "❌ 403: 套餐不含图像 API 权限",
            429: "❌ 429: 触发限流",
            500: "❌ 500: 服务端错误",
            503: "❌ 503: 服务不可用",
        }.get(e.code, f"❌ HTTP {e.code}")
        sys.exit(f"{friendly}\n   响应: {body[:500]}")
    except urllib.error.URLError as e:
        sys.exit(f"❌ 网络错误: {e.reason}")
    except TimeoutError:
        sys.exit("❌ 请求超时（>120s）")


def save_image(raw: bytes, out_path: Path, size: int, quality: int) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    import io
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    if im.size != (size, size):
        im = im.resize((size, size), Image.Resampling.LANCZOS)
    im.save(out_path, "WEBP", quality=quality, method=6)


def url_to_local_path(url: str, tmp_dir: Path) -> Path:
    """把 URL 缓存到本地 tmp 目录（API 的 URL 24h 过期）。"""
    tmp_dir.mkdir(parents=True, exist_ok=True)
    name = base64.urlsafe_b64encode(url.encode()).decode().rstrip("=")[:16] + ".bin"
    p = tmp_dir / name
    if not p.exists():
        with urllib.request.urlopen(url, timeout=60) as r:
            p.write_bytes(r.read())
    return p


def generate(
    prompt: str,
    out_name: str,
    *,
    aspect_ratio: str = "1:1",
    model: str = "image-01",
    n: int = 1,
    ref_image: str | None = None,
    style: dict | None = None,
    size: int = DEFAULT_SIZE,
    quality: int = DEFAULT_QUALITY,
    out_dir: Path = DEFAULT_OUT_DIR,
    dry_run: bool = False,
) -> Path | None:
    payload: dict = {
        "model": model,
        "prompt": prompt,
        "aspect_ratio": aspect_ratio,
        "n": max(1, min(n, 9)),
        "response_format": "base64",
    }
    if style and model == "image-01-live":
        payload["style"] = style
    if ref_image:
        # MiniMax API 拒绝 localhost / 私有地址：只接受公网 http(s) URL
        # 路径 / file:// 都会触发 "disallowed image url"
        is_url = ref_image.startswith("http://") or ref_image.startswith("https://")
        is_local = ref_image.startswith("/") or ref_image.startswith("./") or ref_image.startswith("~")
        if is_local:
            ref_path = Path(ref_image).expanduser()
            if not ref_path.exists():
                sys.exit(f"--ref 指定的图不存在: {ref_image}")
            print(f"⚠️  --ref 是本地路径，API 不支持，跳过 subject_reference")
            print(f"   路径: {ref_path.resolve()}")
            print(f"   原因: MiniMax 拒绝 localhost / 私有地址")
            print(f"   提示: 风格一致性靠 SUN_BEAR_STYLE_BLOCK 维持")
            print(f"   高级:  把图传到公网 OSS 后用 https URL")
        elif is_url:
            payload["subject_reference"] = [
                {"type": "character", "image_file": ref_image}
            ]
        else:
            print(f"⚠️  --ref 无法识别为 URL 或路径，跳过: {ref_image}")

    if dry_run:
        print(f"[dry-run] POST {ENDPOINT}")
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return None

    resp = post(payload)
    target = out_dir / f"{out_name}.webp"

    # MiniMax image-01 实际响应：image_base64 / image_urls（带 s）
    data = resp.get("data") or {}
    b64_list = (data.get("image_base64") if data else None) or []
    url_list = (data.get("image_urls") if data else None) or (data.get("image_url") if data else None) or []
    if isinstance(url_list, str):
        url_list = [url_list]

    if not data:
        print(f"响应里没有 data 字段: {json.dumps(resp, ensure_ascii=False)[:500]}")
        return None

    if b64_list:
        raw = base64.b64decode(b64_list[0])
        save_image(raw, target, size, quality)
        return target
    if url_list:
        cache_path = url_to_local_path(url_list[0], ROOT / "tools/.tmp_image_cache")
        save_image(cache_path.read_bytes(), target, size, quality)
        return target
    print(f"响应里没有 image_base64 / image_urls: {json.dumps(resp, ensure_ascii=False)[:500]}")
    return None


def main() -> None:
    p = argparse.ArgumentParser(description="为小熊天气生成插画")
    p.add_argument("--scene", type=str, help="场景键，如 home_balcony")
    p.add_argument("--scenes", type=str, help="批量场景，逗号分隔")
    p.add_argument("--prompt", type=str, help="自定义 prompt（覆盖 --scene）")
    p.add_argument("--out", type=str, help="输出文件名（不含扩展名）")
    p.add_argument("--out-dir", type=str, default=str(DEFAULT_OUT_DIR))
    p.add_argument("--aspect", type=str, default="1:1")
    p.add_argument("--model", type=str, default="image-01",
                   choices=["image-01", "image-01-live"])
    p.add_argument("--n", type=int, default=1)
    p.add_argument("--ref", type=str, help="参考图路径（锁定小熊外观）")
    p.add_argument("--style", type=str, help="image-01-live 风格 JSON 字符串")
    p.add_argument("--size", type=int, default=DEFAULT_SIZE)
    p.add_argument("--quality", type=int, default=DEFAULT_QUALITY)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--list-scenes", action="store_true")
    args = p.parse_args()

    if args.list_scenes:
        print("可用 scene 键：")
        for k, v in SCENE_PRESETS.items():
            print(f"  {k:24s}  {v['action']}")
        return

    out_dir = Path(args.out_dir)
    style = json.loads(args.style) if args.style else None

    jobs: list[dict] = []
    if args.prompt:
        jobs.append({
            "prompt": args.prompt,
            "out": args.out or "ic_sun_bear_custom",
        })
    elif args.scenes:
        for s in args.scenes.split(","):
            s = s.strip()
            if not s:
                continue
            if s not in SCENE_PRESETS:
                print(f"⚠️  未在 SCENE_PRESETS 找到 {s}，改用自由 prompt")
                prompt = f"小熊在 {s} 场景"
            else:
                prompt = build_prompt(s)
            jobs.append({
                "prompt": prompt,
                "out": args.out or f"ic_sun_bear_{s}",
            })
    elif args.scene:
        prompt = build_prompt(args.scene)
        jobs.append({
            "prompt": prompt,
            "out": args.out or f"ic_sun_bear_{args.scene}",
        })
    else:
        p.error("需要 --scene / --scenes / --prompt 之一")

    for job in jobs:
        print(f"→ 生成 {job['out']}.webp")
        result = generate(
            prompt=job["prompt"],
            out_name=job["out"],
            aspect_ratio=args.aspect,
            model=args.model,
            n=args.n,
            ref_image=args.ref,
            style=style,
            size=args.size,
            quality=args.quality,
            out_dir=out_dir,
            dry_run=args.dry_run,
        )
        if result:
            print(f"  ✓ {result.relative_to(ROOT)}")
        else:
            print(f"  ✗ 失败")


if __name__ == "__main__":
    main()
