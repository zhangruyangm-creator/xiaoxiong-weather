#!/usr/bin/env bash
# 设置 Python 环境并安装项目插画生成工具的依赖。
#
# 阶段 1（必装）：Pillow + requests  —— 只够跑 gen_illustration.py
# 阶段 2（可选）：mcp 库  —— 让 Cursor 里我能直接调工具
#
# mcp 库要求 Python ≥ 3.10；如果你卡在 3.9，先 brew install python 再跑阶段 2。
#
# 用法:
#   bash tools/setup_python.sh           # 默认只装阶段 1
#   bash tools/setup_python.sh --with-mcp # 同时装阶段 2
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"
cd "$ROOT"

WITH_MCP=0
for arg in "$@"; do
    case "$arg" in
        --with-mcp) WITH_MCP=1 ;;
        --help|-h)
            echo "用法: bash tools/setup_python.sh [--with-mcp]"
            exit 0
            ;;
    esac
done

if [[ -t 1 ]]; then
    GREEN="\033[0;32m"; YELLOW="\033[0;33m"; RED="\033[0;31m"; RESET="\033[0m"
else
    GREEN=""; YELLOW=""; RED=""; RESET=""
fi
ok()   { printf "${GREEN}✓${RESET} %s\n" "$*"; }
warn() { printf "${YELLOW}⚠${RESET}  %s\n" "$*"; }
err()  { printf "${RED}❌${RESET} %s\n" "$*"; }

echo "=== 1. 选择 Python ==="

PY=""
if [[ -x "$ROOT/.venv/bin/python3" ]]; then
    PY="$ROOT/.venv/bin/python3"
    ok "使用项目 venv: $PY"
elif command -v /opt/homebrew/bin/python3 >/dev/null 2>&1; then
    PY=/opt/homebrew/bin/python3
    ok "使用 Homebrew Python (Apple Silicon): $PY"
elif command -v /usr/local/bin/python3 >/dev/null 2>&1 \
     && [[ "$(readlink -f /usr/local/bin/python3 2>/dev/null || realpath /usr/local/bin/python3 2>/dev/null || echo)" != *"Xcode"* ]]; then
    PY=/usr/local/bin/python3
    ok "使用系统 Python (Intel Homebrew): $PY"
elif command -v /opt/local/bin/python3 >/dev/null 2>&1; then
    PY=/opt/local/bin/python3
    ok "使用 MacPorts Python: $PY"
elif command -v /usr/bin/python3 >/dev/null 2>&1; then
    PY=/usr/bin/python3
    warn "使用 Xcode 自带 Python: $PY"
    if [[ ! -x "$ROOT/.venv/bin/python3" ]]; then
        echo "  → 自动创建项目 venv..."
    fi
else
    err "找不到 python3"
    err "  请先安装: brew install python"
    exit 1
fi

echo "   路径: $PY"
echo "   版本: $($PY --version 2>&1)"

echo ""
echo "=== 2. 创建 venv ==="
if [[ ! -x "$ROOT/.venv/bin/python3" ]]; then
    case "$PY" in
        /usr/bin/python3) NEED_VENV=1 ;;
        *) NEED_VENV=0 ;;
    esac
    PY_VERSION=$($PY -c "import sys; print(sys.version_info.major * 10 + sys.version_info.minor)")
    if (( PY_VERSION < 310 )); then
        warn "检测到 Python < 3.10 ($($PY -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")'))"
        NEED_VENV=1
    fi
    if (( NEED_VENV )); then
        echo "正在创建 venv..."
        if ! $PY -m venv "$ROOT/.venv" 2>/dev/null; then
            err "venv 创建失败"
            err "  建议: brew install python  然后重跑"
            exit 1
        fi
        if ! "$ROOT/.venv/bin/python3" -m pip --version >/dev/null 2>&1; then
            echo "  venv 没 pip，引导..."
            curl -sSL https://bootstrap.pypa.io/get-pip.py -o /tmp/get-pip.py
            "$ROOT/.venv/bin/python3" /tmp/get-pip.py --quiet
            rm -f /tmp/get-pip.py
        fi
        PY="$ROOT/.venv/bin/python3"
        ok "venv 就绪: $PY"
    fi
else
    ok "venv 已存在: $PY"
fi

PY_VERSION=$($PY -c "import sys; print(sys.version_info.major * 10 + sys.version_info.minor)")
echo "   当前 venv Python: $($PY -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"

echo ""
echo "=== 3. 阶段 1：装 Pillow + requests（出图必装） ==="
$PY -m pip install --upgrade pip --quiet
$PY -m pip install Pillow requests --quiet
ok "阶段 1 完成"

echo ""
echo "=== 4. 阶段 2：装 mcp（Cursor 集成，可选） ==="
if (( WITH_MCP == 0 )); then
    echo "  跳过（没传 --with-mcp）。要装 mcp 之后跑："
    echo "    bash tools/setup_python.sh --with-mcp"
    echo "  注意：mcp 要求 Python ≥ 3.10"
else
    if (( PY_VERSION < 310 )); then
        err "当前 venv Python $($PY -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")') < 3.10，mcp 装不上"
        err "  解决：brew install python  然后删 .venv 重跑"
        exit 1
    fi
    $PY -m pip install mcp --quiet
    ok "mcp 已装"
fi

echo ""
echo "=== 5. 验证 ==="
$PY -c "
import PIL, requests
print(f'  Pillow:   {PIL.__version__}')
print(f'  requests: {requests.__version__}')
"

echo ""
echo "=== 6. 下一步 ==="
echo "  # 1. 激活 venv"
echo "  source .venv/bin/activate"
echo ""
echo "  # 2. 出图（自动写到 res-illustrations）"
echo "  export MINIMAX_API_KEY=\"sk-cp-JZ...\""
echo "  python tools/gen_illustration.py --scene home_balcony \\"
echo "      --ref core/res-illustrations/src/main/res/drawable-nodpi/ic_sun_bear_work_farm.webp"
echo ""
echo "  # 3. 图出来后发给我，我帮你 review 风格"
