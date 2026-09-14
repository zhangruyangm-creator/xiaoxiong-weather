#!/usr/bin/env python3
"""R8 收窄后的 dex 保留校验（防「按名字/反射使用」的类被裁剪或改名）。

背景：曾用 `-keep class com.xiaoxiong.weather.data.**` 这类宽泛规则保证序列化安全，
收窄后改为通用 kotlinx.serialization 规则（`**$$serializer` / `Companion` / `serializer(...)`）。
本脚本在 full release APK 构建后运行，直接从 dex 解析「已定义类 + 方法 + 字符串」，断言：

1. 源码中每个 `@Serializable` 非枚举类，其 `X$$serializer` 与 `X` 本体都在 dex 中；
   枚举没有 `$$serializer` 类（kotlinx.serialization 用 EnumSerializer），只断言本体存在。
2. ServiceLoader 注册的 GameEntryProvider 类与 `<init>()V` 在 dex 中，
   且 META-INF/services 注册文件与实际类一一对应。provider 清单默认从
   `game/*/src/main/resources/.../GameEntryProvider` 运行时枚举（新增游戏模块自动纳入，
   无需改脚本）；`--expected-providers a,b` 可显式覆盖，扫描为空时退回兜底清单。
3. WorkManager 按类名从数据库实例化的 Worker（`WeatherRefreshWorker`）保持原名与
   `(Context, WorkerParameters)` 构造器。
4. 持久化枚举 `valueOf(String)` 回读：枚举类可被 R8 改名（无影响），但其常量名会作为
   持久化字符串回读，必须仍在 dex 字符串池中（枚举被 unbox/裁剪会失败）。
5. google-services.json 存在（Firebase/Crashlytics 启用）时，CrashReporting 通过
   `Class.forName` + `getMethod` 反射调用的类与方法必须保名存在。

脚本不依赖 dexdump，纯 Python 解析 dex，stdlib 即可运行。

用法:
    python3 tools/check_r8_dex_keep.py [--apk app/build/outputs/apk/full/release/app-full-release.apk]
                                      [--root .]
    python3 tools/check_r8_dex_keep.py --expect-firebase      # 强制校验 Firebase 反射面
    python3 tools/check_r8_dex_keep.py --no-expect-firebase   # 跳过 Firebase 反射面

退出码: 0 全部通过；1 存在缺失（输出缺失清单）；2 参数/环境错误。
"""

from __future__ import annotations

import argparse
import pathlib
import re
import struct
import sys
import zipfile

PROVIDER_SERVICE = "META-INF/services/com.xiaoxiong.weather.game.GameEntryProvider"

# 兜底清单：仅当运行时扫描 game/*/META-INF/services 为空时使用；
# 正常情况下 provider 清单从各游戏模块源码动态枚举，新增游戏无需改本脚本。
FALLBACK_EXPECTED_PROVIDERS = [
    "com.xiaoxiong.weather.ui.game.BearGameEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearStarJumpEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearMazeEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearFarmEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearHomeEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearCatchEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearHockeyEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearPoolEntryProvider",
    "com.xiaoxiong.weather.ui.game.BearPlaneEntryProvider",
]


def discover_expected_providers(root: pathlib.Path) -> list[str]:
    """从 game/*/src/main/resources 下的 ServiceLoader 注册文件枚举 provider（保序去重）。"""
    providers: list[str] = []
    for path in sorted(root.glob(f"game/*/src/main/resources/{PROVIDER_SERVICE}")):
        if "build" in path.parts:
            continue
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            name = line.strip()
            if name and not name.startswith("#") and name not in providers:
                providers.append(name)
    return providers

# WorkManager 默认 WorkerFactory 用 `Class.forName(workerClassName)` + 反射构造器实例化。
WORKER_REFLECTION = [
    (
        "com.xiaoxiong.weather.worker.WeatherRefreshWorker",
        "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V",
    ),
]

# 以字符串持久化、运行期 Enum.valueOf(String) 回读的枚举（类可被改名，常量名必须保留）。
RUNTIME_ENUM_LOOKUPS = [
    "com.xiaoxiong.weather.data.TemperatureUnit",
    "com.xiaoxiong.weather.data.ThemeMode",
    "com.xiaoxiong.weather.data.AppFontScale",
    "com.xiaoxiong.weather.ui.game.MazeDir",
    "com.xiaoxiong.weather.ui.game.BearCharacter",
    "com.xiaoxiong.weather.ui.game.PoolSide",
    "com.xiaoxiong.weather.ui.game.PoolMode",
    "com.xiaoxiong.weather.ui.game.PoolGame$Aids",
    "com.xiaoxiong.weather.ui.game.PoolAi$Difficulty",
]

# CrashReporting.kt 的反射面（Class.forName + getMethod），需与源码保持一致。
FIREBASE_REFLECTION = [
    (
        "com.google.firebase.FirebaseApp",
        [("initializeApp", "(Landroid/content/Context;)Lcom/google/firebase/FirebaseApp;")],
    ),
    (
        "com.google.firebase.crashlytics.FirebaseCrashlytics",
        [
            ("getInstance", "()Lcom/google/firebase/crashlytics/FirebaseCrashlytics;"),
            ("setCrashlyticsCollectionEnabled", "(Z)V"),
            ("recordException", "(Ljava/lang/Throwable;)V"),
        ],
    ),
]

# 只扫描参与 app 打包的模块的 main 源集；测试/基准模块不产物进 release APK。
EXCLUDED_PATH_PARTS = {"build", "test-fixtures", "baselineprofile"}


# ---------------------------------------------------------------- source scan

TYPE_DECL_RE = re.compile(
    r"^(?:(?:public|internal|private|protected|abstract|final|open|sealed|data|value|"
    r"annotation|enum|inline|inner|companion|expect|actual|external|tailrec|operator|"
    r"infix|suspend|const|lateinit|override|vararg|crossinline|noinline|reified)\s+)*"
    r"(?P<kind>class|object|interface)\s+(?P<name>[A-Za-z_]\w*)"
)
COMPANION_RE = re.compile(r"^companion\s+object(?:\s+(?P<name>[A-Za-z_]\w*))?")
ANNOTATION_RE = re.compile(r"^@(?:[A-Za-z_]\w*\.)*Serializable\b")


class SourceClass:
    def __init__(self, fqcn: str, kind: str, location: str) -> None:
        self.fqcn = fqcn
        self.kind = kind
        self.location = location

    @property
    def is_enum(self) -> bool:
        return self.kind == "enum"


def _declaration(text: str):
    """返回 (kind, name)；不是类型声明返回 None。"""
    stripped = text.strip()
    companion = COMPANION_RE.match(stripped)
    if companion:
        return "object", companion.group("name") or "Companion"
    match = TYPE_DECL_RE.match(stripped)
    if not match:
        return None
    kind = match.group("kind")
    if kind == "class" and re.search(r"\benum\s+class\b", stripped):
        kind = "enum"
    return kind, match.group("name")


def _strip_leading_annotation(text: str) -> str:
    """去掉行首 `@Some.Serializable(...)`（含参数），返回剩余声明文本。"""
    match = ANNOTATION_RE.match(text.strip())
    if not match:
        return text
    remainder = text.strip()[match.end() :]
    if remainder.startswith("("):
        depth = 0
        for index, char in enumerate(remainder):
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    return remainder[index + 1 :]
        return remainder
    return remainder


def _main_sources(root: pathlib.Path) -> list[pathlib.Path]:
    paths = []
    for path in sorted(root.glob("**/src/main/**/*.kt")):
        if EXCLUDED_PATH_PARTS & set(path.parts):
            continue
        paths.append(path)
    return paths


def parse_serializable_classes(root: pathlib.Path) -> list[SourceClass]:
    classes: list[SourceClass] = []
    for path in _main_sources(root):
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        package = ""
        candidates: list[tuple[int, str, str]] = []
        for index, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("package "):
                package = stripped[len("package ") :].strip().rstrip(";")
            declaration = _declaration(line)
            if declaration:
                candidates.append((index, declaration[1], declaration[0]))

        def enclosing_names(target_line: int, target_indent: int) -> list[str]:
            chain: list[str] = []
            indent = target_indent
            while True:
                parent = None
                for index, name, _ in candidates:
                    if index >= target_line:
                        break
                    if len(lines[index]) - len(lines[index].lstrip()) < indent:
                        parent = (index, name)
                if parent is None:
                    break
                chain.append(parent[1])
                indent = len(lines[parent[0]]) - len(lines[parent[0]].lstrip())
            chain.reverse()
            return chain

        for index, line in enumerate(lines):
            stripped = line.strip()
            if not stripped.startswith("@") or not ANNOTATION_RE.match(stripped):
                continue
            declaration = _declaration(_strip_leading_annotation(line))
            decl_line = index
            if declaration is None:
                decl_line = index + 1
                while decl_line < len(lines):
                    probe = lines[decl_line].strip()
                    if not probe or probe.startswith("//"):
                        decl_line += 1
                        continue
                    declaration = _declaration(_strip_leading_annotation(lines[decl_line]))
                    if declaration is None and probe.startswith("@"):
                        decl_line += 1
                        continue
                    break
            if declaration is None:
                continue
            indent = len(lines[decl_line]) - len(lines[decl_line].lstrip())
            chain = enclosing_names(decl_line, indent)
            fqcn = ".".join(part for part in [package, "$".join(chain + [declaration[1]])] if part)
            classes.append(SourceClass(fqcn, declaration[0], f"{path}:{index + 1}"))
    return sorted(classes, key=lambda item: item.fqcn)


def parse_enum_declarations(root: pathlib.Path) -> dict[str, list[str]]:
    """枚举 FQCN -> 常量名列表（同类名可能重名，必须按 FQCN 归属，如 PoolAi$Difficulty）。"""
    enums: dict[str, list[str]] = {}
    for path in _main_sources(root):
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        text = "\n".join(lines)
        package = ""
        candidates: list[tuple[int, str]] = []
        for index, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("package "):
                package = stripped[len("package ") :].strip().rstrip(";")
            declaration = _declaration(line)
            if declaration:
                candidates.append((index, declaration[1]))

        def enclosing_names(target_line: int, target_indent: int) -> list[str]:
            chain: list[str] = []
            indent = target_indent
            while True:
                parent = None
                for index, name in candidates:
                    if index >= target_line:
                        break
                    if len(lines[index]) - len(lines[index].lstrip()) < indent:
                        parent = (index, name)
                if parent is None:
                    break
                chain.append(parent[1])
                indent = len(lines[parent[0]]) - len(lines[parent[0]].lstrip())
            chain.reverse()
            return chain

        line_offsets = [0]
        for line in lines:
            line_offsets.append(line_offsets[-1] + len(line) + 1)
        for index, line in enumerate(lines):
            declaration = _declaration(line)
            if declaration is None or declaration[0] != "enum":
                continue
            body_start = text.find("{", line_offsets[index])
            if body_start < 0:
                continue
            indent = len(line) - len(line.lstrip())
            chain = enclosing_names(index, indent)
            fqcn = ".".join(part for part in [package, "$".join(chain + [declaration[1]])] if part)
            enums[fqcn] = _read_enum_body(text, body_start)
    return enums


def _read_enum_body(text: str, body_start: int) -> list[str]:
    constants: list[str] = []
    index = body_start + 1
    depth = 1
    parens = 0
    brackets = 0
    length = len(text)
    while index < length and depth > 0:
        char = text[index]
        if text.startswith("//", index):
            index = text.find("\n", index)
            if index < 0:
                break
            continue
        if text.startswith("/*", index):
            end = text.find("*/", index + 2)
            index = length if end < 0 else end + 2
            continue
        if char in "\"'":
            quote = '"""' if text.startswith('"""', index) else char
            index += len(quote)
            while index < length:
                if text.startswith("\\", index):
                    index += 2
                    continue
                if text.startswith(quote, index):
                    index += len(quote)
                    break
                index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                break
        elif char == "(":
            parens += 1
        elif char == ")":
            parens -= 1
        elif char == "[":
            brackets += 1
        elif char == "]":
            brackets -= 1
        elif char == ";" and depth == 1 and parens == 0 and brackets == 0:
            break
        elif depth == 1 and parens == 0 and brackets == 0 and (char.isalpha() or char == "_"):
            token = re.match(r"[A-Za-z_]\w*", text[index:]).group(0)
            if re.fullmatch(r"[A-Z][A-Z0-9_]*", token):
                constants.append(token)
            index += len(token)
            continue
        index += 1
    return constants


# ------------------------------------------------------------------- dex parse

U32 = struct.Struct("<I")
U16 = struct.Struct("<H")


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte & 0x80 == 0:
            return result, offset
        shift += 7


class DexFile:
    """最小 dex 解析：类描述符、每类方法名/原型、字符串池。"""

    def __init__(self, data: bytes) -> None:
        if data[:4] != b"dex\n":
            raise ValueError("not a dex file")
        if U32.unpack_from(data, 40)[0] != 0x12345678:
            raise ValueError("unsupported dex endianness")
        self.data = data
        self.strings = self._read_strings()
        self.string_set = set(self.strings)
        self.types = [self.strings[index] for index in self._read_u32_array(64, 68)]
        self.protos = self._read_protos()
        self.methods = self._read_methods()
        self.classes: dict[str, dict[str, set[str]]] = self._read_classes()

    def _read_u32_array(self, size_field: int, offset_field: int) -> list[int]:
        size = U32.unpack_from(self.data, size_field)[0]
        offset = U32.unpack_from(self.data, offset_field)[0]
        return list(struct.unpack_from(f"<{size}I", self.data, offset))

    def _read_strings(self) -> list[str]:
        size = U32.unpack_from(self.data, 56)[0]
        offset = U32.unpack_from(self.data, 60)[0]
        result = []
        for index in range(size):
            data_off = U32.unpack_from(self.data, offset + index * 4)[0]
            _, cursor = _uleb128(self.data, data_off)
            end = self.data.index(b"\x00", cursor)
            result.append(self.data[cursor:end].decode("utf-8", errors="replace"))
        return result

    def _read_protos(self) -> list[tuple[str, list[str]]]:
        size = U32.unpack_from(self.data, 72)[0]
        offset = U32.unpack_from(self.data, 76)[0]
        result = []
        for index in range(size):
            _, return_type_idx, parameters_off = struct.unpack_from("<III", self.data, offset + index * 12)
            params: list[str] = []
            if parameters_off:
                count = U32.unpack_from(self.data, parameters_off)[0]
                params = [
                    self.types[U16.unpack_from(self.data, parameters_off + 4 + i * 2)[0]] for i in range(count)
                ]
            result.append((self.types[return_type_idx], params))
        return result

    def _read_methods(self) -> list[tuple[str, str, tuple[str, list[str]]]]:
        size = U32.unpack_from(self.data, 88)[0]
        offset = U32.unpack_from(self.data, 92)[0]
        result = []
        for index in range(size):
            class_idx, proto_idx, name_idx = struct.unpack_from("<HHI", self.data, offset + index * 8)
            result.append((self.types[class_idx], self.strings[name_idx], self.protos[proto_idx]))
        return result

    def _read_classes(self) -> dict[str, dict[str, set[str]]]:
        size = U32.unpack_from(self.data, 96)[0]
        offset = U32.unpack_from(self.data, 100)[0]
        classes: dict[str, dict[str, set[str]]] = {}
        for index in range(size):
            class_idx, _, _, _, _, _, class_data_off, _ = struct.unpack_from(
                "<IIIIIIII", self.data, offset + index * 32
            )
            classes[self.types[class_idx]] = self._read_class_methods(class_data_off)
        return classes

    def _read_class_methods(self, class_data_off: int) -> dict[str, set[str]]:
        methods: dict[str, set[str]] = {}
        if not class_data_off:
            return methods
        cursor = class_data_off
        static_fields, cursor = _uleb128(self.data, cursor)
        instance_fields, cursor = _uleb128(self.data, cursor)
        direct_methods, cursor = _uleb128(self.data, cursor)
        virtual_methods, cursor = _uleb128(self.data, cursor)
        for _ in range(static_fields + instance_fields):
            _, cursor = _uleb128(self.data, cursor)
            _, cursor = _uleb128(self.data, cursor)
        # encoded_method 的 method_idx_diff 在 direct / virtual 两个列表内各自累计。
        for count in (direct_methods, virtual_methods):
            method_idx = 0
            for _ in range(count):
                diff, cursor = _uleb128(self.data, cursor)
                _, cursor = _uleb128(self.data, cursor)
                _, cursor = _uleb128(self.data, cursor)
                method_idx += diff
                _, name, (return_type, params) = self.methods[method_idx]
                methods.setdefault(name, set()).add("(" + "".join(params) + ")" + return_type)
        return methods

    def has_class(self, fqcn: str) -> bool:
        return f"L{fqcn.replace('.', '/')};" in self.classes

    def has_method(self, fqcn: str, name: str, signature: str | None = None) -> bool:
        methods = self.classes.get(f"L{fqcn.replace('.', '/')};")
        if methods is None or name not in methods:
            return False
        return signature is None or signature in methods[name]

    def has_string(self, value: str) -> bool:
        return value in self.string_set


def load_apk(apk: pathlib.Path) -> tuple[DexFile, list[str]]:
    with zipfile.ZipFile(apk) as archive:
        dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        if not dex_names:
            raise ValueError(f"{apk} 中没有 classes*.dex")
        merged = DexFile.__new__(DexFile)
        merged.classes = {}
        merged.strings = []
        for name in dex_names:
            dex = DexFile(archive.read(name))
            merged.classes.update(dex.classes)
            merged.strings.extend(dex.strings)
    merged.string_set = set(merged.strings)
    return merged, dex_names


def read_service_registrations(apk: pathlib.Path) -> list[str]:
    with zipfile.ZipFile(apk) as archive:
        try:
            raw = archive.read(PROVIDER_SERVICE).decode("utf-8")
        except KeyError:
            return []
    return [line.strip() for line in raw.splitlines() if line.strip() and not line.startswith("#")]


# ---------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 R8 收窄后 dex 中反射/序列化关键类仍存在")
    default_apk = "app/build/outputs/apk/full/release/app-full-release.apk"
    parser.add_argument("--apk", default=default_apk, help=f"APK 路径（默认 {default_apk}）")
    parser.add_argument("--root", default=None, help="项目根目录（默认脚本上一级）")
    parser.add_argument(
        "--expected-providers",
        default=None,
        help="显式覆盖 GameEntryProvider 清单（逗号分隔；默认从 game/*/META-INF/services 枚举）",
    )
    parser.add_argument("--list-providers", action="store_true", help="仅打印枚举到的 provider 清单后退出")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--expect-firebase", action="store_true", help="强制校验 Firebase/Crashlytics 反射面")
    group.add_argument("--no-expect-firebase", action="store_true", help="跳过 Firebase/Crashlytics 反射面")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve() if args.root else pathlib.Path(__file__).resolve().parents[1]

    explicit_providers = [name.strip() for name in (args.expected_providers or "").split(",") if name.strip()]
    if explicit_providers:
        expected_providers = list(dict.fromkeys(explicit_providers))
    else:
        expected_providers = discover_expected_providers(root) or list(FALLBACK_EXPECTED_PROVIDERS)

    if args.list_providers:
        for provider in expected_providers:
            print(provider)
        return 0

    apk = pathlib.Path(args.apk)
    if not apk.is_file():
        print(f"错误: APK 不存在: {apk}", file=sys.stderr)
        return 2

    expect_firebase = (root / "app" / "google-services.json").is_file()
    if args.expect_firebase:
        expect_firebase = True
    if args.no_expect_firebase:
        expect_firebase = False

    try:
        dex, dex_names = load_apk(apk)
        source_classes = parse_serializable_classes(root)
        enum_declarations = parse_enum_declarations(root)
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        print(f"错误: {error}", file=sys.stderr)
        return 2

    failures: list[str] = []
    print(f"APK: {apk} ({apk.stat().st_size} bytes, {', '.join(dex_names)})")
    print(f"dex 中定义类: {len(dex.classes)}")

    # 1) @Serializable 覆盖
    serializer_count = 0
    enum_count = 0
    for source_class in source_classes:
        if source_class.is_enum:
            # 枚举没有 $$serializer 类；类本身可被 R8 改名（引用是直接的），
            # 但序列化/回读依赖常量名，必须仍在 dex 字符串池中。
            enum_count += 1
            constants = enum_declarations.get(source_class.fqcn)
            if constants is None:
                failures.append(f"未找到枚举源码: {source_class.fqcn}（{source_class.location}）")
                continue
            missing_constants = [name for name in constants if not dex.has_string(name)]
            if missing_constants:
                failures.append(
                    f"@Serializable 枚举常量名缺失: {source_class.fqcn} -> {', '.join(missing_constants)}"
                    f"（{source_class.location}）"
                )
            continue
        if not dex.has_class(source_class.fqcn):
            failures.append(f"@Serializable 类本体缺失: {source_class.fqcn}（{source_class.location}）")
        serializer = source_class.fqcn + "$$serializer"
        if dex.has_class(serializer):
            serializer_count += 1
        else:
            failures.append(f"serializer 缺失: {serializer}（{source_class.location}）")
    print(
        f"@Serializable: 源码 {len(source_classes)} 类（枚举 {enum_count} 个，不生成 $$serializer），"
        f"dex $$serializer {serializer_count} 个"
    )

    # 2) GameEntryProvider（ServiceLoader）
    registrations = read_service_registrations(apk)
    missing = sorted(set(expected_providers) - set(registrations))
    extra = sorted(set(registrations) - set(expected_providers))
    if missing:
        failures.append(f"META-INF/services 缺少注册: {', '.join(missing)}")
    if extra:
        failures.append(f"META-INF/services 出现未预期注册: {', '.join(extra)}")
    for provider in expected_providers:
        if not dex.has_method(provider, "<init>", "()V"):
            failures.append(f"ServiceLoader provider 类或 <init>()V 缺失: {provider}")
    print(f"GameEntryProvider: {len(expected_providers)} 个 provider 的 dex 构造器校验完成")

    # 3) WorkManager 反射构造
    for fqcn, signature in WORKER_REFLECTION:
        if not dex.has_method(fqcn, "<init>", signature):
            failures.append(f"WorkManager Worker 反射构造器缺失: {fqcn}<init>{signature}")
    print(f"WorkManager Worker: {len(WORKER_REFLECTION)} 个反射构造器校验完成")

    # 4) 持久化枚举 valueOf 回读（类可改名，常量名必须在 dex 字符串池）
    for fqcn in RUNTIME_ENUM_LOOKUPS:
        constants = enum_declarations.get(fqcn)
        if constants is None:
            failures.append(f"未找到枚举源码（列表过期？）: {fqcn}")
            continue
        missing_constants = [name for name in constants if not dex.has_string(name)]
        if missing_constants:
            failures.append(f"valueOf 回读的枚举常量名缺失: {fqcn} -> {', '.join(missing_constants)}")
    print(f"运行时枚举回读: {len(RUNTIME_ENUM_LOOKUPS)} 个枚举的常量名校验完成")

    # 5) Firebase 反射（可选依赖）
    if expect_firebase:
        for fqcn, methods in FIREBASE_REFLECTION:
            if not dex.has_class(fqcn):
                failures.append(f"Firebase 反射类缺失（google-services.json 已启用）: {fqcn}")
                continue
            for name, signature in methods:
                if not dex.has_method(fqcn, name, signature):
                    failures.append(f"Firebase 反射方法缺失: {fqcn}.{name}{signature}")
        print("Firebase 反射面: 已校验（依赖存在）")
    else:
        print("Firebase 反射面: 跳过（未启用 google-services.json；用 --expect-firebase 可强制）")

    if failures:
        print(f"\nR8 keep 校验失败，共 {len(failures)} 项:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("\n✔ R8 keep 校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
