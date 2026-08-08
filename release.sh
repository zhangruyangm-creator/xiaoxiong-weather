#!/usr/bin/env bash
#
# 本地一键发布：构建 release APK -> 发布 GitHub Release -> 更新下载站 README -> 打 tag
# 用法: ./release.sh v1.4.6
#
set -euo pipefail

VERSION_ARG="${1:-}"
if [[ ! "$VERSION_ARG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法: ./release.sh v1.4.6"
  exit 1
fi
VERSION="${VERSION_ARG#v}"
REPO_PUB="zhangruyangm-creator/xiaoxiong-weather-releases"

# ---------- 前置校验 ----------
echo "==> 校验 git 状态"
if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "错误: 必须在 main 分支发布"; exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "错误: 工作区有未提交改动，先提交再发布"; exit 1
fi
if git tag -l "$VERSION_ARG" | grep -q .; then
  echo "错误: tag $VERSION_ARG 已存在"; exit 1
fi
if ! grep -q "versionName = \"$VERSION\"" app/build.gradle.kts; then
  echo "错误: app/build.gradle.kts 中 versionName 不是 $VERSION"
  echo "      请先修改版本号、更新 CHANGELOG.md 并提交"
  exit 1
fi

# ---------- 构建 ----------
echo "==> 构建 full release APK（本地缓存，约 2-3 分钟）"
./gradlew assembleFullRelease
APK="app/build/outputs/apk/full/release/app-full-release.apk"
if [[ ! -f "$APK" ]]; then
  echo "错误: APK 未生成"; exit 1
fi

# ---------- GitHub 凭据 ----------
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null | sed -n 's/^password=//p')
if [[ -z "$TOKEN" ]]; then
  echo "错误: 无法从钥匙串获取 GitHub 凭据（先执行一次 git push 保存凭据）"; exit 1
fi

# ---------- 发布说明（从 CHANGELOG 提取） ----------
NOTES=$(awk -v ver="[$VERSION]" '
  BEGIN { RS = "\n## " }
  index($0, ver) == 1 {
    sub(/^\[[0-9.]+\] - [0-9-]+[[:space:]]*/, "")
    print; exit
  }
' CHANGELOG.md)
if [[ -z "$NOTES" ]]; then
  echo "警告: CHANGELOG.md 中没有 $VERSION 段落，使用默认说明"
  NOTES="自动发布 v$VERSION（由 release.sh 构建签名）"
fi

# ---------- 创建 Release ----------
echo "==> 创建 GitHub Release $VERSION_ARG"
RELEASE_JSON=$(curl -sS -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$REPO_PUB/releases" \
  -d "$(python3 -c 'import json,sys; print(json.dumps({"tag_name": sys.argv[1], "name": "Xiaoxiong Weather " + sys.argv[1][1:], "body": sys.argv[2]}))' "$VERSION_ARG" "$NOTES")")
RELEASE_ID=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
if [[ -z "$RELEASE_ID" ]]; then
  echo "错误: 创建 Release 失败: $RELEASE_JSON"; exit 1
fi

# ---------- 上传 APK ----------
echo "==> 上传 APK"
ASSET_JSON=$(curl -sS -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK" \
  "https://uploads.github.com/repos/$REPO_PUB/releases/$RELEASE_ID/assets?name=app-full-release.apk")
echo "$ASSET_JSON" | python3 -c "import sys,json; r=json.load(sys.stdin); print('APK 已上传:', r.get('size'), 'bytes')"

# ---------- 更新下载站 README ----------
echo "==> 更新公开下载站 README"
python3 - "$TOKEN" "$VERSION" <<'PYEOF'
import json, base64, os, re, sys, urllib.request

token, version = sys.argv[1], sys.argv[2]
repo = "zhangruyangm-creator/xiaoxiong-weather-releases"
h = {"Authorization": f"token {token}", "Accept": "application/vnd.github+json"}

req = urllib.request.Request(f"https://api.github.com/repos/{repo}/contents/README.md", headers=h)
r = json.load(urllib.request.urlopen(req))
sha, old = r["sha"], base64.b64decode(r["content"]).decode()

row = f"| {version} | [app-full-release.apk](https://github.com/{repo}/releases/download/v{version}/app-full-release.apk) | 约 12 MB |\n"
if f"| {version} |" in old:
    print("README 已含该版本，跳过")
    sys.exit(0)

# 版本表插入：锚定表头，永远插到第一行（不再硬编码历史版本号）
ver_header = "| 版本 | 文件 | 大小 |\n|------|------|------|\n"
new = old.replace(ver_header, ver_header + row, 1)

# 从本地 CHANGELOG 提取该版本的 bullet 摘要
changelog = open("CHANGELOG.md", encoding="utf-8").read()
m = re.search(rf"## \[{re.escape(version)}\].*?(?=\n## |\Z)", changelog, re.S)
bullets = re.findall(r"^\- (.*)$", m.group(0), re.M) if m else []
summary = "；".join(bullets) if bullets else "见 GitHub Release 说明"
today = os.popen("date +%F").read().strip()
log_row = f"| {version} | {today} | {summary} |\n"
log_header = "| 版本 | 日期 | 主要更新 |\n|------|------|----------|\n"
new = new.replace(log_header, log_header + log_row, 1)

payload = json.dumps({
    "message": f"update README for v{version}",
    "content": base64.b64encode(new.encode()).decode(),
    "sha": sha,
}).encode()
req = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/contents/README.md",
    data=payload, method="PUT", headers=h,
)
json.load(urllib.request.urlopen(req))
print("README 已更新")
PYEOF

# ---------- 打 tag ----------
echo "==> 打 tag $VERSION_ARG 并推送"
git tag "$VERSION_ARG"
git push origin "$VERSION_ARG"

echo ""
echo "✔ 发布完成: https://github.com/$REPO_PUB/releases/tag/$VERSION_ARG"
