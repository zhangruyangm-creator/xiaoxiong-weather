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
- **彩蛋游戏**（仅 `full` flavor）：搜索框输入口令可进入「小熊跳跳 / 小熊摘星 / 小熊迷宫 / 小熊农场」
- **调试模式**：隐藏入口可覆盖天气数据用于测试预警/图标
- **Baseline Profile**：`:baselineprofile` module 通过 macrobenchmark 生成启动性能基线

## 构建命令

```bash
# Debug
./gradlew assembleFullDebug
./gradlew assembleLiteDebug

# Release（需要 keystore.properties + app/release.keystore）
./gradlew assembleFullRelease
./gradlew assembleLiteRelease

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
| `full` | ✅ | 默认版本，含小熊彩蛋游戏 |
| `lite` | ❌ | 游戏编译为空实现，R8 内联消除，包体零痕迹 |

`lite` 的 `BearGameScreen.kt` 只保留符号签名，release 构建下会被 R8 直接内联消除。

## 签名配置

Release 构建需要在项目根目录放 `keystore.properties`：

```properties
storeFile=release.keystore
storePassword=******
keyAlias=******
keyPassword=******
```

`app/release.keystore` 是实际密钥库文件（已在 `.gitignore` 中排除）。

## Firebase Crashlytics（可选）

把 `google-services.json` 放进 `app/` 目录后，`app/build.gradle.kts` 会自动应用 `google-services` 与 `crashlytics` 插件。不放则不启用，无任何 Firebase 依赖。

## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml      # 含 20 个 activity-alias（按天气切换图标）
│   │   ├── assets/
│   │   │   ├── china_cities.json     # 中国城市目录（搜索结果重写/排序）
│   │   │   ├── foreign_city_aliases.json  # 中文→英文别名（纽约等）
│   │   │   └── privacy_policy.txt
│   │   ├── java/com/xiaoxiong/weather/
│   │   │   ├── WeatherApp.kt        # Application，隐私同意后才初始化
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/                # Repository / Store / Ports / Generator
│   │   │   │   ├── WeatherRepository.kt
│   │   │   │   ├── WeatherModels.kt
│   │   │   │   ├── WeatherAlertGenerator.kt
│   │   │   │   ├── LifeIndexGenerator.kt
│   │   │   │   ├── WeatherAdviceGenerator.kt
│   │   │   │   ├── SeasonTipGenerator.kt
│   │   │   │   ├── ChinaCityCatalog.kt
│   │   │   │   ├── LocationHelper.kt
│   │   │   │   ├── AppSettingsStore.kt
│   │   │   │   ├── WeatherCacheStore.kt
│   │   │   │   ├── SelectedCityStore.kt
│   │   │   │   ├── FavoriteCityStore.kt
│   │   │   │   ├── RecentCityStore.kt
│   │   │   │   ├── DebugWeatherStore.kt
│   │   │   │   ├── WeatherPorts.kt   # 可测试接口
│   │   │   │   ├── WeatherRefreshPolicy.kt
│   │   │   │   ├── AppError.kt       # 类型化错误
│   │   │   │   ├── RadarMapRepository.kt
│   │   │   │   └── remote/WeatherDtos.kt
│   │   │   ├── domain/CitySearchUseCase.kt
│   │   │   ├── di/AppContainer.kt   # 手动 DI
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   ├── weather/
│   │   │   │   │   ├── WeatherScreen.kt
│   │   │   │   │   ├── WeatherViewModel.kt
│   │   │   │   │   ├── WeatherTodayPage.kt
│   │   │   │   │   ├── WeatherHourlyPage.kt
│   │   │   │   │   ├── WeatherDailyPage.kt
│   │   │   │   │   ├── WeatherSearchHeader.kt
│   │   │   │   │   ├── WeatherAtmosphere.kt   # 雨/雪/雾粒子动画
│   │   │   │   │   ├── HourlyChartSection.kt
│   │   │   │   │   ├── PrecipitationBarChart.kt
│   │   │   │   │   ├── DailyTempRange.kt
│   │   │   │   │   ├── SunPathArc.kt
│   │   │   │   │   ├── LifeIndicesSection.kt
│   │   │   │   │   ├── SeasonTipCard.kt
│   │   │   │   │   ├── LiveRadarPane.kt
│   │   │   │   │   ├── RadarCloudMapSection.kt
│   │   │   │   │   ├── RadarPrecipOutlook.kt
│   │   │   │   │   ├── SatelliteCloudPane.kt
│   │   │   │   │   ├── RadarScanLoadingOverlay.kt
│   │   │   │   │   ├── ShareWeatherCard.kt
│   │   │   │   │   ├── BearHoliday.kt       # 节日小熊图标
│   │   │   │   │   ├── LocationWeatherCoordinator.kt
│   │   │   │   │   ├── ForegroundAutoRefresh.kt
│   │   │   │   │   ├── CitySearchController.kt
│   │   │   │   │   ├── DebugModeController.kt
│   │   │   │   │   └── WeatherViewModelFactory.kt
│   │   │   │   ├── settings/
│   │   │   │   ├── debug/
│   │   │   │   └── game/              # full flavor：4 个彩蛋游戏
│   │   │   ├── widget/                # Glance 桌面小组件
│   │   │   ├── worker/WeatherRefreshWorker.kt
│   │   │   └── util/
│   │   │       ├── WeatherCodes.kt    # WMO 码 + 降水强度分档
│   │   │       ├── WeatherLauncherIcon.kt
│   │   │       ├── ChineseLunarCalendar.kt
│   │   │       ├── HourlyUtils.kt
│   │   │       └── AlertBearIcon.kt
│   │   └── res/
│   │       ├── drawable-nodpi/        # 181 张小熊 webp（天气/预警/游戏）
│   │       ├── mipmap-*/              # 按天气切换的启动图标
│   │       └── values/                # strings.xml（中文为主）
│   ├── full/                          # full flavor 源集
│   ├── lite/                          # lite flavor：游戏空实现
│   ├── test/                          # 24 个单元测试
│   └── androidTest/                   # 仪器测试
└── build.gradle.kts

baselineprofile/                       # macrobenchmark 生成 Baseline Profile
```

## 数据源

| 用途 | 接口 | 说明 |
|------|------|------|
| 天气预报 | `api.open-meteo.com/v1/forecast` | 含 current/hourly/daily |
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
- **隐私合规**：`WeatherApp.initializeAfterConsent()` 用 `AtomicBoolean` 保证幂等，未同意前不初始化 Crashlytics / 不入队 WorkManager

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
