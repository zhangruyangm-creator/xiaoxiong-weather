# 小熊天气（XiaoxiongWeather）

一个以"小熊"为主题的 Android 天气 App。基于 Open-Meteo 免费 API，无需密钥；针对中国大陆做了拼音搜索、县级市正向地理编码、农历、桌面图标随天气切换等本地化适配。

## 功能概览

- **实况 + 18h 预报 + 7 天日报**：温度、体感、湿度、风、能见度、气压、云量、露点、降水
- **空气质量**：欧洲 AQI / 美国 AQI / PM2.5 / PM10 / 沙尘 / UV / NO₂ / O₃
- **智能预警**：基于实况与预报阈值生成（强降雨、暴雪、寒潮、高温、沙尘、积水、雷暴、冰雹、冻雨、雾、大风、紫外线、空气污染等），并匹配专属小熊图标
- **生活指数**：穿衣、雨伞、洗车、晾晒、运动、感冒、舒适度
- **季节贴士 + 农历**：按节气与农历日期给出当日提示
- **桌面图标随天气切换**：通过 20 个 `activity-alias` 实现，前台排队、退后台再 flush，避免部分机型杀进程
- **Glance 桌面小组件**：响应式尺寸（小/中/宽/高），含今明概况与小时走势
- **通知提醒**：仅在天气码变化、气温明显变化或临近降水时提醒，避免刷屏
- **分享卡片**：把当前天气渲染成图片分享
- **隐私合规门**：未同意前不初始化 Crashlytics / 不入队 WorkManager
- **彩蛋游戏**（仅 `full` flavor）：搜索框输入口令可进入「小熊跳跳 / 小熊摘星 / 小熊迷宫 / 小熊农场 / 小熊的家」
- **小熊的家**：客厅枢纽 + 卧室作息 / 厨房三餐图鉴 / 游乐间游戏入口 / 储物间
- **调试模式**：隐藏入口可覆盖天气数据用于测试预警/图标
- **Baseline Profile**：`:baselineprofile` module 通过 macrobenchmark 生成启动性能基线
- **性能监控**（Debug 构建）：冷启动 / 网络请求 / 内存指标采集，环形缓冲区 + 守护线程汇总；Release 包零开销

## 构建命令

```bash
# Debug
./gradlew assembleFullDebug
./gradlew assembleLiteDebug

# Release（需要 keystore.properties + app/release.keystore）
./gradlew assembleFullRelease
./gradlew assembleLiteRelease

# 彩蛋游戏可选编译（full 版，默认全部 5 个都编译进去）
./gradlew assembleFullRelease -Pgames=jump,star          # 只编译跳跳 + 摘星
./gradlew assembleFullRelease -Pgames=none               # 不含任何游戏

# 单元测试
./gradlew test

# Android 仪器测试（需连接设备/模拟器）
./gradlew connectedAndroidTest

# 生成 Baseline Profile
./gradlew :baselineprofile:generateBaselineProfile
```

## Flavor 区别

| Flavor | 彩蛋游戏 | 说明 |
|--------|---------|------|
| `full` | ✅（可选） | 默认版本，含小熊彩蛋游戏，可裁剪 |
| `lite` | ❌ | 不依赖任何游戏实现模块（字节码为零；仅保留 `:game:api` 接口契约层约 1-2KB） |

### full 版游戏可选编译

游戏代码分散在独立模块 `:game:jump / :game:star / :game:maze / :game:farm / :game:home`（共享部分在 `:game:common`），`full` 版默认全部编译进去；需要裁剪时用 `-Pgames` 让 `:app` 只对选中模块建立动态依赖：

```bash
./gradlew assembleFullRelease -Pgames=jump,maze          # 只编译跳跳 + 迷宫
./gradlew assembleFullRelease -Pgames=none               # 不含任何游戏
```

- 游戏 id：`jump`（小熊跳跳）、`star`（小熊摘星）、`maze`（小熊迷宫）、`farm`（小熊农场）、`home`（小熊的家）
- 未选中的游戏模块**不参与编译**（不存在字节码），其代码、插画、单元测试都不进包——`-Pgames=none` 的 full 包实测比默认小约 12MB
- 入口装配用 `ServiceLoader`（`GameEntryProvider`）：`MainActivity` 发现 `:game:*` 在 classpath 的 provider 组装游戏列表，未选模块自然不出现
- 缺省（不传 `-Pgames`）等价于 `all`

## 签名配置

Release 构建需要在项目根目录放 `keystore.properties`：

```properties
storeFile=release.keystore
storePassword=******
keyAlias=******
keyPassword=******
```

`app/release.keystore` 是实际密钥库文件（已在 `.gitignore` 中排除）。

## 发版流程

新版本发布 = **推送代码提交 + 打同名版本 tag** 两步，缺一不可：

```bash
# 1. 假设 CHANGELOG.md 已新增版本条目、app/build.gradle.kts 已升 versionName
git commit -am "release: 1.7.9（版本号 63、CHANGELOG 更新）"
git push origin main

# 2. 打标签并推送——这一步触发 GitHub Actions 自动发版：
#    没有 v1.7.9 这个 tag，xiaoxiong-weather-releases 仓库就不会更新！
git tag v1.7.9
git push origin v1.7.9
```

> 注意：**tag 名称必须与 CHANGELOG 条目、versionName 三者一致**（如 `v1.7.9`）。只 push 代码不打 tag，发布仓库会一直停留在旧版本。

push tag 后，CI 自动完成：

| 步骤 | 说明 |
|---|---|
| 还原签名密钥 | 从仓库 Secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 注入，不落盘 |
| 构建签名 APK | `./gradlew assembleFullRelease` |
| 提取更新说明 | 从 `CHANGELOG.md` 自动摘出对应版本条目作为 Release notes |
| 发布 GitHub Release | APK 发布到 [`xiaoxiong-weather-releases`](https://github.com/zhangruyangm-creator/xiaoxiong-weather-releases) 仓库的 Releases 页 |
| 更新下载站 | `tools/update_releases_readme.py` 刷新的 README 版本表 |

前置条件（CI 缺失会直接报错退出）：

- 仓库 Secrets 已配置 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
- 已配置 `GH_TOKEN`（需要 `xiaoxiong-weather-releases` 仓库的写权限）

可选：push 前先在本地跑 `./gradlew assembleFullRelease` 验证构建与签名配置无误，减少 CI 失败重跑。发版进度可在仓库 Actions 页查看。

## Firebase Crashlytics（可选）

把 `google-services.json` 放进 `app/` 目录后，`app/build.gradle.kts` 会自动应用 `google-services` 与 `crashlytics` 插件。不放则不启用，无任何 Firebase 依赖。

## 项目结构

多模块构建（阶段 1~5 已落地）：

```
xiaoxiong-weather/
├── app/                       # :app 薄壳：Application、MainActivity、DI、Widget、Worker、WeatherScreen 装配
│   └── src/main/java/com/xiaoxiong/weather/
│       ├── WeatherApp.kt     # Application，隐私同意后才初始化
│       ├── MainActivity.kt   # 单 Activity，ServiceLoader 装配游戏入口
│       ├── data/DefaultWeatherPresentationEffects.kt
│       ├── di/AppContainer.kt  # 手动 DI
│       ├── ui/weather/       # WeatherScreen（跨 feature 装配壳）+ WeatherViewModelFactory
│       ├── widget/           # Glance 桌面小组件（4 文件）
│       ├── worker/WeatherRefreshWorker.kt
│       └── util/CrashReporting.kt
├── core/
│   ├── model/                # 纯 Kotlin 数据模型 + 温度单位
│   ├── common/               # WeatherCodes、AppError、格式化工具、性能监控（带 res）
│   ├── network/              # OkHttp + DTO + 城市别名 assets
│   ├── domain/               # 纯逻辑生成器（LifeIndex/预警/季节贴士等）
│   ├── data/                 # Store、Repository、城市目录、区域进 assets
│   ├── test-fixtures/        # 测试夹具（WeatherFixtures），仅测试依赖
│   ├── designsystem/         # Compose 主题 + SunPathMath + WeatherScene + 预警/图标
│   └── res-illustrations/    # 纯资源模块：456 张 ic_sun_bear_* 场景插画（单一数据源）
├── feature/
│   ├── weather/              # WeatherViewModel / 天气页 / 雷达 / 分享 / 调试
│   └── settings/             # 设置 / 隐私门 / 卡片设置
├── game/
│   ├── api/                  # GameEntry + GameEntryProvider（ServiceLoader） + 口令常量
│   ├── common/               # 共享精灵表 / 排行榜 / 天气特效
│   ├── jump/ catch/ star/ maze/ farm/ home/ hockey/   # 各游戏模块（代码 + 插画 + 字符串 + 测试）
├── baselineprofile/          # macrobenchmark 生成 Baseline Profile
└── build.gradle.kts          # 根构建脚本
```

- **资源归属**：各模块自持所需 `R.string` / `R.drawable`（Android Library 编译期只认自己 res）；`ic_sun_bear_*` 场景插画统一收在 `:core:res-illustrations`，消费方经 `R as IllustR` 别名引用
- **依赖方向铁律**：`feature:*` 不得互依；`game:*` 不得依赖 `feature:*`；任何模块不得依赖 `:app`
- **游戏裁剪**：`:app` 依 `-Pgames` 对 `:game:*` 建条件动态依赖，未选模块不编译

## 数据源

| 用途 | 接口 | 说明 |
|------|------|------|
| 天气预报 | `api.open-meteo.com/v1/forecast` | 含 current/hourly/daily |
| 天气预报降级 | `api.met.no/weatherapi/locationforecast/2.0/compact` | Open-Meteo 断网/5xx/坏包时自动回退（免密钥、全球覆盖；不含 AQI/日出日落/紫外线） |
| 空气质量 | `air-quality-api.open-meteo.com/v1/air-quality` | CAMS 数据 |
| 城市搜索 | `geocoding-api.open-meteo.com/v1/search` | 配合拼音 + 别名 |
| 中国县级市 | 系统 `Geocoder` | Open-Meteo 搜不到时正向地理编码兜底 |
| 逆地理 | 系统 `Geocoder` | 定位后反查区/县名，带 1km 网格缓存 |

所有接口均无需 API Key。

## 关键技术决策

- **手动 DI 而非 Hilt**：体量较小，编译快、依赖少；`AppContainer` 用 `by lazy` 共享 OkHttp 连接池与磁盘缓存
- **Ports 接口**（`WeatherFetcher` / `WeatherCache` / `LocationSession` / `WeatherPresentationEffects`）：让 ViewModel 可在单元测试中替换实现
- **类型化错误**（`AppError` sealed class）：UI 不解析 `"HTTP 503"` 这类技术字符串
- **请求竞态处理**：`AtomicInteger weatherRequestId` 在 `selectCity` 入口与 `publishWeather` 续体两次校验，快速切城时旧快照不会盖回新城市
- **时区对齐**：小时序列按城市本地墙钟时间对齐，不用 `ZoneId.systemDefault()`，避免跨时区偏一小时甚至整天
- **降水强度分档**：中国气象局小时暴雨 ≥16mm、英式 violent >32mm、毛毛雨 <0.5mm；`displayCode` 在天气码与实测雨量不一致时只升不降（不降级雷暴/冰雹/冻雨）
- **前台整点对齐刷新**：`:00 / :15 / :30 / :45` 对齐，退后台停轮询，留给 WorkManager
- **多源降级**：Open-Meteo 预报 5xx / 网络不可达 / 响应格式异常时自动切换 MET Norway Locationforecast（免密钥）；4xx 不降级；降级源缺失字段按「无数据」处理
- **隐私合规**：`WeatherApp.initializeAfterConsent()` 用 `AtomicBoolean` 保证幂等，未同意前不初始化 Crashlytics / 不入队 WorkManager / 不启用性能监控

## 最低要求

- minSdk 26（Android 8.0）
- compileSdk 37
- Java 17
- Kotlin 2.2.20

## 依赖

主要依赖见 `gradle/libs.versions.toml`：

- Jetpack Compose（BOM 2025.10.01）
- Material3
- DataStore Preferences
- WorkManager
- OkHttp 4.12
- kotlinx.serialization / coroutines
- Glance（桌面小组件）
- tinypinyin（中文转拼音搜索）
- Firebase Crashlytics（可选）
- Baseline Profile / macrobenchmark

## 自定义 Scheme

App 注册了 `xiaoxiongweather://open` 用于外部入口拉起。`MainActivity` 配置为 `singleTop`，会通过 `onNewIntent` 接收后续 intent。
