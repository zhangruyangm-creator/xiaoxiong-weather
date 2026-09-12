# 工程化配置说明

本文档汇总项目已接入的工程化能力及需要手动配置的部分。

## CI（GitHub Actions）

`.github/workflows/ci.yml` 已配置：push 到 `main` 或开 PR 时自动运行：

- `lintKotlin`（ktlint 静态检查）
- `test`（全模块单元测试：core / feature / game / app）
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

发布前会由 `python3 tools/verify_release_version.py <tag>` 校验 **tag / versionName / CHANGELOG 三处一致**，
并检查 versionCode 相对上一版本严格递增（本地 `release.sh` 与 CI `release.yml` 均已接入，CI checkout 使用
`fetch-depth: 0` 以读取历史 tag）。任何一项不一致都会在构建前直接失败。

## 崩溃监控（Crashlytics）

代码已就绪（`CrashReporting.kt` 门面 + `build.gradle.kts` 条件启用），放入 `app/google-services.json`
后自动启用，无需改代码。启用步骤：

1. 打开 https://console.firebase.google.com → 创建项目（或复用现有项目）
2. 添加 Android 应用，包名填 `com.xiaoxiong.weather`
3. 下载生成的 `google-services.json`，放到 `app/google-services.json`（此文件已被 `.gitignore` 排除，不会进仓库）
4. 重新构建即可；崩溃会自动上报 Firebase Crashlytics 控制台

未放入配置文件时应用正常运行，仅无崩溃上报。

## 性能监控（Debug 构建）

代码已就绪（`PerformanceMonitor` + `PerformanceInterceptor`），**仅 Debug 构建启用**，Release 包零开销（`enabled=false` 时单次 volatile 读即返回）。

### 监控维度

| 指标 | 采集方式 | 说明 |
|------|---------|------|
| 冷启动 | `WeatherApp.onCreate` 记录耗时 | 超 2s 输出 warn |
| 网络请求 | OkHttp `PerformanceInterceptor` 按路径分组 | 超 1s 计入慢请求 |
| 内存使用 | 守护线程每 30s 采样 | 记录已用 MB |
| 帧渲染 | Compose `SideEffect`（可选接入） | 超 16ms 计入慢帧 |

### 查看报告

在 Logcat 中过滤 `PerfMonitor` 标签，每 60 秒自动汇总输出 avg/p50/p95/max 统计。也可在代码中调用 `PerformanceMonitor.report()` 获取文本报告。

### 数据安全

所有指标仅存储在内存环形缓冲区（固定 64 条），不写磁盘、不上报、不离开设备。

## 数据源降级

主预报源为 Open-Meteo（免密钥）。当 forecast 请求因网络不可达 / 5xx / 响应格式异常失败时，
`ForecastFetcher` 自动回退到 **MET Norway Locationforecast 2.0**（`api.met.no`，免密钥、全球覆盖）：

- 触发条件：`IOException`、5xx、`AppError.DataFormat`；4xx 是请求本身问题，不降级
- 主站同格式备用入口（`forecastBaseFallbacks`）与 MET Norway 可叠加：先换镜像入口，再换数据源
- 降级时空气质量不可用（`airQuality=null`），日出日落/紫外线/体感/阵风等字段为空，UI 按无数据处理
- MET Norway 响应为 UTC，按城市经度近似时区换算（仅降级路径存在偏差，小时序列与 `timezone` 字段自洽）
- 通过 `WeatherRepository(metNorwayBase = null)` 可关闭；单元测试显式传 null，避免访问外网
- User-Agent 按 MET Norway 要求携带应用标识与联系方式（见 `MetNorwayFetcher.MET_USER_AGENT`）

空气质量、历史天气、地理编码仍为 Open-Meteo 单源。若希望接入国内数据源（和风天气/心知天气）作为
fallback，需要注册服务获取 API Key、增加第二适配器并将 Key 通过 `BuildConfig` 注入（不写进仓库）；
这会改变「免密钥」的产品定位，需明确需要后再做。
