#!/usr/bin/env python3
"""游戏模块字符串资源重名检查。

所有 game/* 模块共用同一个 namespace，values/values-en 中同名资源在 APK 资源合并时
会相互覆盖。本脚本扫描 game/*/src/main/res/values{,-en}/strings.xml，同一资源名
在同一语言下若被多个模块定义了不同的值，合并结果不可控（静默覆盖），直接失败；
同名同值只打印提示。

用法:
    python3 tools/check_duplicate_game_strings.py
    python3 tools/check_duplicate_game_strings.py --root .

退出码: 0 无冲突；1 存在值不一致的重名资源。
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

LANGS = {"zh": "values", "en": "values-en"}


def collect(root: pathlib.Path) -> dict[str, dict[str, list[tuple[str, str]]]]:
    resources: dict[str, dict[str, list[tuple[str, str]]]] = defaultdict(lambda: defaultdict(list))
    for module_dir in sorted(root.glob("game/*/src/main/res")):
        module = module_dir.parents[2].name
        for lang, folder in LANGS.items():
            path = module_dir / folder / "strings.xml"
            if not path.exists():
                continue
            for string in ET.parse(path).getroot().findall("string"):
                name = string.get("name")
                if not name:
                    continue
                resources[name][lang].append((module, "".join(string.itertext())))
    return resources


def main() -> None:
    parser = argparse.ArgumentParser(description="检查 game/* 模块间值不一致的重名字符串资源")
    parser.add_argument("--root", default=None, help="项目根目录（默认脚本上一级）")
    args = parser.parse_args()
    root = pathlib.Path(args.root).resolve() if args.root else pathlib.Path(__file__).resolve().parents[1]

    resources = collect(root)
    notices: list[tuple[str, list[str]]] = []
    conflicts: list[tuple[str, list[tuple[str, list[tuple[str, str]]]]]] = []
    for name, langs in sorted(resources.items()):
        modules = sorted({module for entries in langs.values() for module, _ in entries})
        if len(modules) < 2:
            continue
        differing = [
            (lang, entries) for lang, entries in sorted(langs.items()) if len({value for _, value in entries}) > 1
        ]
        if differing:
            conflicts.append((name, differing))
        else:
            notices.append((name, modules))

    for name, modules in notices:
        print(f"提示: 重名同值资源 `{name}`（{', '.join(modules)}），暂不处理")
    for name, differing in conflicts:
        print(f"冲突: 资源 `{name}` 在多个模块定义了不同的值，资源合并会静默覆盖", file=sys.stderr)
        for lang, entries in differing:
            for module, value in entries:
                print(f"    [{lang}] {module}: {value!r}", file=sys.stderr)

    if conflicts:
        print(f"错误: 发现 {len(conflicts)} 组值不一致的重名资源", file=sys.stderr)
        sys.exit(1)
    print(f"✔ 检查通过：{len(resources)} 个资源名，{len(notices)} 组重名同值，0 组冲突")


if __name__ == "__main__":
    main()
