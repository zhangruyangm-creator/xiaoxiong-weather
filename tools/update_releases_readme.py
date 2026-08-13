#!/usr/bin/env python3
"""更新公开下载仓库 README：插入新版本行 + 版本记录行（体积取真实 APK 大小）。

用法: python3 tools/update_releases_readme.py <token> <版本号如 1.6.0> <apk 路径>
需要当前目录是主仓库根目录（读取 CHANGELOG.md 提取版本摘要）。
"""

import base64
import datetime
import json
import os
import re
import sys
import urllib.request


def main() -> None:
    if len(sys.argv) != 4:
        sys.exit("用法: update_releases_readme.py <token> <version> <apk_path>")
    token, version, apk_path = sys.argv[1], sys.argv[2], sys.argv[3]
    repo = "zhangruyangm-creator/xiaoxiong-weather-releases"
    headers = {"Authorization": f"token {token}", "Accept": "application/vnd.github+json"}

    req = urllib.request.Request(f"https://api.github.com/repos/{repo}/contents/README.md", headers=headers)
    r = json.load(urllib.request.urlopen(req))
    sha, old = r["sha"], base64.b64decode(r["content"]).decode()

    size_mb = max(1, os.path.getsize(apk_path) // (1024 * 1024))
    row = (
        f"| {version} | [app-full-release.apk]"
        f"(https://github.com/{repo}/releases/download/v{version}/app-full-release.apk) | 约 {size_mb} MB |\n"
    )
    ver_header = "| 版本 | 文件 | 大小 |\n|------|------|------|\n"
    if f"| {version} |" in old:
        print(f"README 已含 {version}，跳过版本表插入")
    else:
        assert ver_header in old, "README 版本表头未找到"
        old = old.replace(ver_header, ver_header + row, 1)

    changelog = open("CHANGELOG.md", encoding="utf-8").read()
    m = re.search(rf"## \[{re.escape(version)}\].*?(?=\n## |\Z)", changelog, re.S)
    bullets = re.findall(r"^\- (.*)$", m.group(0), re.M) if m else []
    summary = "；".join(bullets) if bullets else "见 GitHub Release 说明"
    # CI runner 是 UTC，发布记录按北京时间（UTC+8）落日期
    today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date().isoformat()
    log_header = "| 版本 | 日期 | 主要更新 |\n|------|------|----------|\n"
    log_row = f"| {version} | {today} | {summary} |\n"
    if log_row in old:
        print(f"README 已含 {version} 的记录行，跳过")
    else:
        assert log_header in old, "README 记录表头未找到"
        old = old.replace(log_header, log_header + log_row, 1)

    payload = json.dumps(
        {
            "message": f"update README for v{version}",
            "content": base64.b64encode(old.encode()).decode(),
            "sha": sha,
        }
    ).encode()
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/contents/README.md",
        data=payload,
        method="PUT",
        headers=headers,
    )
    json.load(urllib.request.urlopen(req))
    print("README 已更新")


if __name__ == "__main__":
    main()
