# 工程化配置说明

本文档汇总项目已接入的工程化能力及需要手动配置的部分。

## CI（GitHub Actions）

`.github/workflows/ci.yml` 已配置：push 到 `main` 或开 PR 时自动运行：

- `lintKotlin`（ktlint 静态检查）
- `testFullDebugUnitTest`（单元测试）
- `lintFullDebug`（Android Lint）

无需额外配置即可生效（私有仓库免费额度内）。

## 静态检查（ktlint）

- 插件：`io.github.usefulness.ktlint-gradle-plugin`（兼容 Gradle 8.13）
- 规则基于 `.editorconfig`（缩进 4 空格、UTF-8、Compose 组件函数命名豁免）
- 本地常用命令：

```bash
./gradlew lintKotlin    # 只检查
./gradlew formatKotlin  # 自动格式化（新代码建议先跑这个）
```

存量代码已全量格式化过一次（3035 条违规清零）。

## 发版自动化

`.github/workflows/release.yml`：推送 `v*` tag 时自动构建签名 APK 并发布到公开下载仓库
`zhangruyangm-creator/xiaoxiong-weather-releases`。

使用前需在私有仓库 **Settings → Secrets and variables → Actions** 配置 5 个 secret：

| Secret | 内容 |
|--------|------|
| `KEYSTORE_BASE64` | `release.keystore` 的 base64（本地执行 `base64 -i release.keystore` 获取） |
| `KEYSTORE_PASSWORD` | 签名库密码（同 `keystore.properties` 的 `storePassword`） |
| `KEY_ALIAS` | 签名别名（同 `keystore.properties` 的 `keyAlias`） |
| `KEY_PASSWORD` | 签名密钥密码（同 `keystore.properties` 的 `keyPassword`） |
| `GH_TOKEN` | 有 `xiaoxiong-weather-releases` 写权限的 PAT（`repo` scope 即可） |

配置完成后，发版流程简化为：

```bash
git tag v1.5.0
git push origin v1.5.0
```

## 崩溃监控（Crashlytics）

代码已就绪（`CrashReporting.kt` 门面 + `build.gradle.kts` 条件启用），放入 `app/google-services.json`
后自动启用，无需改代码。启用步骤：

1. 打开 https://console.firebase.google.com → 创建项目（或复用现有项目）
2. 添加 Android 应用，包名填 `com.xiaoxiong.weather`
3. 下载生成的 `google-services.json`，放到 `app/google-services.json`（此文件已被 `.gitignore` 排除，不会进仓库）
4. 重新构建即可；崩溃会自动上报 Firebase Crashlytics 控制台

未放入配置文件时应用正常运行，仅无崩溃上报。

## 数据源降级（待决策）

当前全部依赖 Open-Meteo 免费接口，无多源降级。若希望接入国内数据源（和风天气/心知天气）作为
fallback，需要：

- 注册相应服务获取 API Key
- 在 `WeatherRepository` 增加第二数据源 + 超时降级逻辑
- 将 Key 通过 `BuildConfig` 字段注入（不写进仓库）

此改动会改变"免密钥"的产品定位，且工作量为本次工程化中最大的一项，建议明确需要后再做。
