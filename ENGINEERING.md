# 工程化配置说明

本文档汇总项目已接入的工程化能力及需要手动配置的部分。

## CI（GitHub Actions）

`.github/workflows/ci.yml` 已配置：push 到 `main` 或开 PR 时自动运行：

- `lintKotlin`（ktlint 静态检查）
- `test`（全模块单元测试：core / feature / game / app）
- `lintFullDebug`（Android Lint）
- `python3 tools/check_duplicate_game_strings.py`（游戏模块重名资源）
- `python3 tools/check_r8_dex_keep.py`（full release APK 的 R8 keep 守卫，见下）
- ServiceLoader provider / `-Pgames` 接线与 dex 构造器校验

无需额外配置即可生效（私有仓库免费额度内）。

## R8 收窄与 dex keep 守卫

`app/proguard-rules.pro` 已删除 `com.xiaoxiong.weather.data.**`、`.data.remote.**` 两条宽泛
keep，仅保留通用 kotlinx.serialization 规则（`**$$serializer` / `Companion` / `serializer(...)`）
与最小的 ServiceLoader / WebView 规则；R8 seeds 从 6631 降到 2162，APK 约 28.4 MB。

收窄后由 `tools/check_r8_dex_keep.py` 在 full release 构建产物上做兜底守卫（纯 Python 解析
dex，无需 dexdump），失败即 CI 变红：

- 源码每个 `@Serializable` 非枚举类都有 `X` 与 `X$$serializer`（当前 51 类 = 47 数据类 + 4 枚举，
  枚举不生成 `$$serializer`，改为校验其常量名仍在 dex 字符串池）；
- 9 个 `GameEntryProvider` 类、`<init>()V` 与 `META-INF/services` 注册一一对应；
- WorkManager 按类名反射实例化的 `WeatherRefreshWorker` 保持原名与 `(Context, WorkerParameters)` 构造器；
- 持久化枚举（`TemperatureUnit` / `ThemeMode` / `AppFontScale` / 游戏难度等）常量名未被裁剪；
- 配置了 `app/google-services.json` 时，`Class.forName` + `getMethod` 反射的
  `FirebaseApp.initializeApp(Context)` 与 `FirebaseCrashlytics.getInstance()/setCrashlyticsCollectionEnabled/
  recordException` 保名存在（firebase-crashlytics 19.4.4 不带 consumer keep 规则，故在
  `proguard-rules.pro` 中以 `-if` 条件规则精确保留；未接入 Firebase 时规则自动失效）。

本地用法：

```bash
./gradlew :app:assembleFullRelease
python3 tools/check_r8_dex_keep.py                    # 默认读 app/build/outputs/apk/full/release/app-full-release.apk
python3 tools/check_r8_dex_keep.py --expect-firebase  # 强制校验 Firebase 反射面
```

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

`release.sh` 与 `release.yml` 现在都会在构建签名 APK 之前先运行 `./gradlew test lintKotlin`
（全模块单元测试 + ktlint），失败即中止、不产出 Release；本地应急发布可用
`SKIP_TESTS=1 ./release.sh <tag>` 跳过，CI 无跳过开关。

`release.yml` 的 `build-and-publish` 超时已从 40 分钟提高到 60 分钟，覆盖冷缓存下
test + lintKotlin + R8 + 签名 APK 全量构建。tag push 不触发 `ci.yml`，因此发布流程里额外串了
`tools/check_duplicate_game_strings.py`（构建前）与 `tools/check_r8_dex_keep.py`（APK 构建后），
保证「被发布的 APK 自己」经过资源重名与 R8 keep 守卫，而不是只看过 main 分支的 CI 结果。

## 游戏音频（只有音效，无 BGM）

**当前只有 24 个程序合成音效，没有背景音乐。** 这是刻意的体积决策，不是待补的缺口：

- 音效（`tools/generate_sfx.py`，24 个，合计约 240KB）承担全部玩法反馈；
- 曾尝试为 7 款游戏各配一首循环 BGM，共 9.3MB，**占 APK 的 26%**（WAV 是 PCM，
  deflate 压不动，压缩率 100%），换来的"每款游戏都有音乐"并不改善任何一款的可玩性；
- 故整体下线。玩法反馈靠音效已足够，体积留给更值得的内容。

音效生成器的两条约定：

- **按音效名派生独立随机种子**（`cue_rng`），不用"一条共享 rng 顺序喂给所有音效"——
  否则新增任何音效都会改变其后所有音效的波形，历史音频无法复现；
- 音效是短采样（几十到几百毫秒），继续用 WAV：SoundPool 对解码后 PCM 有约 1MB 上限，
  WAV 可直接解码，且总量极小。

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
