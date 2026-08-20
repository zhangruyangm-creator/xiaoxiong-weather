#!/usr/bin/env python3
"""最小验证：检测 MINIMAX_API_KEY 是否能调图像生成 API。

用法:
  export MINIMAX_API_KEY="eyJhbGc..."
  python tools/check_api_key.py

退出码:
  0 = 通过（key 有效、能调图像 API）
  1 = 失败（key 错 / 过期 / 没权限 / 余额不足）
  2 = 请求失败（网络超时 / DNS / 其他）
"""
from __future__ import annotations

import base64
import json
import os
import sys
import urllib.request
import urllib.error

ENDPOINT = os.environ.get(
    "MINIMAX_IMAGE_ENDPOINT",
    "https://api.minimaxi.com/v1/image_generation",
)
# 海外可改: https://api.minimax.io/v1/image_generation


def main() -> int:
    key = os.environ.get("MINIMAX_API_KEY", "").strip()
    if not key:
        print("❌ MINIMAX_API_KEY 环境变量为空")
        print("   export MINIMAX_API_KEY=eyJhbGciOi...")
        return 1

    print(f"endpoint: {ENDPOINT}")
    print(f"key 前 8 位: {key[:8]}...（共 {len(key)} 字符）")
    print("→ 发送最小请求...")

    payload = {
        "model": "image-01",
        "prompt": "a single red apple on a white background",
        "aspect_ratio": "1:1",
        "n": 1,
        # 用 url 模式省流量（API 返回的 URL 24h 过期，这里只是验证）
        "response_format": "url",
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT,
        data=data,
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            resp = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        code = e.code
        print(f"❌ HTTP {code}")
        if code == 401:
            print("   401: key 无效或过期")
            print("   → 检查 platform.minimaxi.com 控制台 API Keys 页面")
        elif code == 402:
            print("   402: 余额不足，需要充值")
            print("   → 单独购买图像 API 额度（订阅 Plan 不一定含图像）")
        elif code == 403:
            print("   403: 套餐不含图像 API 权限")
            print("   → Token Plan 可能只覆盖文本/语音，图像 API 需单独开通")
        elif code == 429:
            print("   429: 触发限流")
        else:
            print(f"   响应: {body[:400]}")
        return 1
    except urllib.error.URLError as e:
        print(f"❌ 网络错误: {e.reason}")
        return 2
    except TimeoutError:
        print("❌ 请求超时")
        return 2

    # 验证响应结构
    data_field = resp.get("data", {})
    # MiniMax 实际响应字段为 image_urls（带 s），文档示例偶有 image_url
    b64_list = data_field.get("image_base64") or []
    url_list = data_field.get("image_urls") or data_field.get("image_url") or []
    if isinstance(url_list, str):
        url_list = [url_list]

    if b64_list:
        print("✅ key 有效，返回 base64 图像")
        size = len(base64.b64decode(b64_list[0]))
        print(f"   图片大小: {size} bytes")
    elif url_list:
        print(f"✅ key 有效，返回 {len(url_list)} 张图 URL")
        print(f"   首图 URL: {url_list[0][:80]}...")
    else:
        print("⚠️  key 有效但响应结构异常")
        print(f"   完整响应: {json.dumps(resp, ensure_ascii=False)[:400]}")
        return 1

    # 检查返回的 metadata（如果有）告诉我用量
    meta = resp.get("metadata", {})
    if meta:
        print(f"   metadata: {meta}")

    print()
    print("🎉 验证通过！可以开始生成插画。")
    print("   下一条命令:")
    print('   python tools/gen_illustration.py --scene home_balcony \\')
    print(
        '       --ref core/res-illustrations/src/main/res/drawable-nodpi/ic_sun_bear_work_farm.webp'
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
