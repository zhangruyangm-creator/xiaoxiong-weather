#!/usr/bin/env python3
"""发版前版本号一致性校验：versionName / CHANGELOG / git tag 三处必须一致。

用法:
    python3 tools/verify_release_version.py v1.7.19
    python3 tools/verify_release_version.py 1.7.19 --root .

校验内容:
1. tag 名（可带或不带 v）符合 vX.Y.Z
2. app/build.gradle.kts 的 versionName 与版本号一致
3. CHANGELOG.md 存在 `## [X.Y.Z] - YYYY-MM-DD` 段落且至少有一条变更
4. versionCode 严格大于上一个版本 tag 的 versionCode（拿不到历史时仅告警）

退出码: 0 通过；1 校验失败。
"""

from __future__ import annotations

import argparse
import datetime
import pathlib
import re
import subprocess
import sys

TAG_RE = re.compile(r"^v?(\d+\.\d+\.\d+)$")
VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"([^"]+)"')
VERSION_CODE_RE = re.compile(r"versionCode\s*=\s*(\d+)")


def fail(message: str) -> None:
    print(f"错误: {message}", file=sys.stderr)
    sys.exit(1)


def parse_gradle(text: str, label: str) -> tuple[str, int]:
    name = VERSION_NAME_RE.search(text)
    code = VERSION_CODE_RE.search(text)
    if name is None or code is None:
        fail(f"{label} 中未找到 versionName/versionCode")
    return name.group(1), int(code.group(1))


def check_changelog(text: str, version: str) -> str:
    header = re.search(rf"^## \[{re.escape(version)}\] - (\d{{4}}-\d{{2}}-\d{{2}})\s*$", text, re.M)
    if header is None:
        fail(f"CHANGELOG.md 中没有 `## [{version}] - YYYY-MM-DD` 段落")
    date = header.group(1)
    try:
        datetime.date.fromisoformat(date)
    except ValueError:
        fail(f"CHANGELOG.md 中 {version} 的日期 `{date}` 不是合法日期")

    body_start = header.end()
    next_header = re.search(r"^## ", text[body_start:], re.M)
    body = text[body_start : body_start + next_header.start()] if next_header else text[body_start:]
    entries = [line for line in body.splitlines() if line.strip().startswith("- ")]
    if not entries:
        fail(f"CHANGELOG.md 中 {version} 段落没有任何 `- ` 变更条目")
    return date


def git(root: pathlib.Path, *args: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout
    except (OSError, subprocess.CalledProcessError):
        return None


def previous_release_tag(root: pathlib.Path, tag: str) -> str | None:
    output = git(root, "tag", "--list", "v*", "--sort=-v:refname")
    if output is None:
        return None
    for line in output.splitlines():
        candidate = line.strip()
        if candidate and candidate != tag:
            return candidate
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description="版本号一致性校验（versionName / CHANGELOG / tag）")
    parser.add_argument("version", help="待发布版本，如 v1.7.19 或 1.7.19")
    parser.add_argument("--root", default=None, help="项目根目录（默认脚本上一级）")
    args = parser.parse_args()

    matched = TAG_RE.match(args.version)
    if matched is None:
        fail(f"版本号 `{args.version}` 不符合 vX.Y.Z 格式")
    version = matched.group(1)
    tag = f"v{version}"
    root = pathlib.Path(args.root).resolve() if args.root else pathlib.Path(__file__).resolve().parents[1]

    print(f"==> 版本一致性校验：{tag}")

    gradle_file = root / "app" / "build.gradle.kts"
    changelog_file = root / "CHANGELOG.md"
    if not gradle_file.exists():
        fail(f"找不到 {gradle_file}")
    if not changelog_file.exists():
        fail(f"找不到 {changelog_file}")

    version_name, version_code = parse_gradle(gradle_file.read_text(encoding="utf-8"), "app/build.gradle.kts")
    if version_name != version:
        fail(f"versionName = {version_name}，与待发布版本 {version} 不一致（请先改版本号并提交）")
    print(f"  versionName = {version_name}  ✓")

    date = check_changelog(changelog_file.read_text(encoding="utf-8"), version)
    print(f"  CHANGELOG 段落 [{version}] - {date}  ✓")

    previous = previous_release_tag(root, tag)
    if previous is None:
        print("  警告: 未找到上一个版本 tag，跳过 versionCode 递增检查（CI 需 checkout fetch-depth: 0）")
    else:
        previous_text = git(root, "show", f"{previous}:app/build.gradle.kts")
        if previous_text is None:
            print(f"  警告: 读不到 {previous} 的 build.gradle.kts，跳过 versionCode 递增检查")
        else:
            _, previous_code = parse_gradle(previous_text, f"{previous}:app/build.gradle.kts")
            if version_code <= previous_code:
                fail(
                    f"versionCode = {version_code} 未大于 {previous} 的 {previous_code}，"
                    "安装包无法覆盖升级（请 +1 后重试）"
                )
            print(f"  versionCode = {version_code} > {previous} 的 {previous_code}  ✓")

    print("✔ 校验通过")


if __name__ == "__main__":
    main()
