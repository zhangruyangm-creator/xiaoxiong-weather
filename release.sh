#!/usr/bin/env bash
#
# 本地一键发布：构建 release APK -> 发布 GitHub Release -> 更新下载站 README -> 打 tag
# 用法: ./release.sh v1.4.6
#        SKIP_TESTS=1 ./release.sh v1.4.6   # 跳过发布前的单元测试与 ktlint（应急发布用）
# 默认先跑 ./gradlew test lintKotlin，失败即退出，不会创建 Release
#
set -euo pipefail

VERSION_ARG="${1:-}"
if [[ ! "$VERSION_ARG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法: ./release.sh v1.4.6   （SKIP_TESTS=1 可跳过发布前的测试与 lint）"
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
echo "==> 校验版本一致性（versionName / CHANGELOG / tag）"
python3 tools/verify_release_version.py "$VERSION_ARG"

# ---------- 发布前校验：单元测试 + ktlint（失败即退出，不创建 Release） ----------
if [[ "${SKIP_TESTS:-0}" == "1" ]]; then
  echo "==> SKIP_TESTS=1，跳过单元测试与 ktlint"
else
  echo "==> 运行单元测试与 ktlint（SKIP_TESTS=1 可跳过）"
  ./gradlew test lintKotlin --console=plain
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
python3 tools/update_releases_readme.py "$TOKEN" "$VERSION" "$APK"

# ---------- 打 tag ----------
echo "==> 打 tag $VERSION_ARG 并推送"
git tag "$VERSION_ARG"
git push origin "$VERSION_ARG"

echo ""
echo "✔ 发布完成: https://github.com/$REPO_PUB/releases/tag/$VERSION_ARG"
