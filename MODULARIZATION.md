# 多模块化重构设计文档

> 状态：**阶段 1~6 全部完成**　|　适用版本：1.6.5+
> 目标：在不破坏 `-Pgames` 游戏裁剪、隐私合规门、性能基线的前提下，把单模块 app 拆成可独立构建的模块体系，并完成游戏模块化（阶段 5）。
>
> 阶段 5 已落地：`-Pgames` 源码集机制退役，改为 `:app` 对 `:game:*` 的条件动态依赖 + `ServiceLoader` 装配；未选自游戏模块不编译，包内零字节码。

---

## 1. 背景与动机

当前 `:app` 单模块承载全部代码（约 39k 行 Kotlin、514 张插画、316 个测试），已在单模块内通过源码集、控制器拆分、手动 DI 做到了较好的组织。但仍存在：

- **无编译期边界**：`ui.weather` 可以直接触碰 `data` 内部，无依赖方向约束
- **widget / worker 反向依赖 app**（引用 `MainActivity`），是循环依赖隐患
- **构建缓存粒度粗**：任何改动触发全模块重编译（依赖源码集切换的现状）
- **学习价值**：模块化 + 接口反转 + 资源隔离是简历级架构实践

**决策原则**：每个阶段独立可验证、可回滚；阶段 5（游戏模块化）已完成，`-Pgames` 源码集机制退役、由条件动态依赖 + ServiceLoader 接棒。

---

## 2. 现状依赖分析

### 2.1 依赖耦合点

| 耦合 | 位置 | 性质 | 对策 |
|---|---|---|---|
| R 资源引用 | 60 个文件 `import com.xiaoxiong.weather.R` | 字符串/插画绑定 | 资源随代码迁移 |
| gameHome → `ui.weather.SunPathMath` | `SunPathPhase`/`sunPathBearMoment`/`SunPathBearMoment`/`isWeekend` | UI 反向被游戏依赖 | SunPathMath 下沉 `core:designsystem` |
| gameCommon → `util.WeatherCodes` | 天气码转降水强度 | 领域逻辑 | 迁入 `core:common` |
| widget → `MainActivity` | `actionStartActivity(Intent(...))` | 循环依赖隐患 | `getLaunchIntentForPackage`（已改造 ✓） |
| worker → `MainActivity` + `widget.WeatherWidgetUpdater` | PendingIntent / 刷新 | 循环依赖隐患 | `getLaunchIntentForPackage`（已改造 ✓） |
| `WeatherGameOverlay` → 全部游戏 | `main/ui/weather/WeatherGameOverlay.kt` | 编译期硬引用 | 阶段 5 接口反转为 `GameEntry` |
| 游戏源码集裁剪 | `app/build.gradle.kts` sourceSet 动态切换 | 单模块专属机制 | 阶段 5 改为动态依赖，机制退役 |

### 2.2 硬约束

1. **R 迁移是体力活**：strings.xml 全在 `app/src/main/res`，拆模块时字符串必须跟着代码走。
2. **`-Pgames` 与游戏模块化互斥**：多模块后无法用源码集切空实现。已决策接受——阶段 5 将 `-Pgames` 改为动态依赖，未选中的游戏模块直接不编译，裁剪能力保留且更彻底。
3. **Store 层依赖 Context**：DataStore / SharedPreferences 需要 Context 初始化，模块内须暴露 `init(context)` 或带 context 构造。

---

## 3. 目标架构

### 3.1 模块树

```
xiaoxiong-weather/
├── :app                     # 薄壳：Application、MainActivity、manifest、DI 组装、游戏装配
├── :core:model              # 纯 Kotlin 数据模型 + 温度单位（无 Android 依赖）
├── :core:network            # OkHttp 基础设施 + 雷达/卫星 DTO + 城市别名 assets
├── :core:domain             # 纯逻辑生成器（Android Library，自带 res）
├── :core:data               # Store、缓存、Repository 门面、fetch 聚合器、城市目录、卡片偏好协议
├── :core:designsystem       # Compose 主题 + SunPathMath + WeatherScene + 生活片刻枚举 + 预警/图标（插画资源在 res-illustrations）
├── :core:res-illustrations  # 纯资源模块：456 张 ic_sun_bear_* 场景插画（三餐/作息/节日…），单一数据源，无代码
├── :core:common             # WeatherCodes、AppError、格式化工具（Android Library，自带 res，无 Compose 依赖）
├── :core:test-fixtures      # 测试共享 fixture（WeatherFixtures）
├── :feature:weather         # WeatherViewModel/各页面/雷达/分享（WeatherScreen 由 :app 装配）
├── :feature:settings        # 设置/隐私/卡片设置
├── :game:api                # GameEntry 接口 + GameEntryProvider（ServiceLoader 发现）+ 口令常量
├── :game:common             # 共享精灵表/排行榜/天气特效（GameBear/GameScaffold/GameLeaderboard/GameWeatherFx…）
├── :game:jump|catch|star|maze|farm|home|hockey|pool   # 各游戏独立模块（各持 res/strings，provider 注册自身）
├── :baselineprofile         # 保留不动
└── (阶段 5 已移除) app/src/gameXxx* 源码集、Empty 门面、-Pgames 源码集机制
```

### 3.2 依赖方向（严格单向）

```
:core:model        ← 无依赖（基石）
:core:common       ← :core:model
:core:network      ← :core:model, :core:common
:core:domain       ← :core:model, :core:common
:core:data         ← :core:model, :core:common, :core:domain, :core:network
:core:designsystem ← :core:model, :core:common, :core:domain, :core:res-illustrations（api，资源透传）
:core:res-illustrations ← 无依赖（纯 res）
:core:test-fixtures← :core:model
:feature:weather   ← core:{model,network,domain,data,designsystem,common,res-illustrations}（不依赖 :game:api，游戏装配由 :app 承载）
:feature:settings  ← core:{model,data,designsystem,common}
:game:common       ← :game:api, core:{designsystem,common,model}
:game:xxx          ← :game:api, :game:common, core:{designsystem,common}
:game:home         ← 另显式依赖 :core:res-illustrations（三餐插画直接用）
:app               ← 全部 feature / game / widget* / worker*（游戏装配经 ServiceLoader 发现，:app 仅依赖 :game:api 接口）
```

**铁律**：`feature:*` 不得互相依赖；`game:*` 不得依赖 `feature:*`；任何模块不得依赖 `:app`。

### 3.3 天气—游戏联动契约

游戏随天气联动（雨雪粒子、雷暴闪电、冻雨冰晶、冰雹、昼夜、排行榜天气文本）**不依赖天气 UI 模块**，通过运行期值传递 + 共享契约模块实现：

```
数据流
WeatherViewModel 刷新
  → WeatherCodes.displayCode(...)   // :core:common 天气码归一化 → sceneCode: Int?
  → bgCurrent.isDay                 // 昼夜
  → cityName                        // 城市名
  → BearGameOverlay(sceneCode, isDay, cityName)   // :app 游戏装配
  → game Content(sceneCode, isDay, cityName)
```

| 联动契约 | 下沉位置 | 用途 |
|---|---|---|
| `WeatherCodes`（`displayCode`/`description`） | `:core:common` | 场景归一化 + 排行榜天气文本（`GameLeaderboard.description`） |
| `SunPathPhase`/`SunPathBearMoment`/`sunPathBearMoment` | `:core:designsystem` | 天气页 + `game:home` 共用生活片刻 |
| `sceneCode: Int?` / `isDay: Boolean?` / `cityName: String?` | 参数（无 import） | 游戏侧纯函数自分类为 `FarmWeather`/`RainStrength` |

**关键约束**：
1. 游戏侧不 import 任何 `feature:weather` 类型；只消费 `sceneCode`/`isDay`/`cityName` 三个值
2. 天气码映射逻辑（`FarmWeather.kt` 的 `farmWeatherFromScene`/`rainStrengthOf`）留在 `:game:common`，随游戏走——新增游戏类型改映射只影响游戏模块
3. `WeatherCodes` 自带的 `R.string`（排行榜天气文本）随代码迁入 `:core:common`
4. 天气层只负责"把复杂天气归一化成场景码"，不关心游戏如何渲染——分工不变，只是固化为编译边界

---

## 4. 核心难点与对策

### 4.1 `-Pgames` 裁剪 → 接口反转（阶段 5，已确认执行）

游戏模块化后 `WeatherGameOverlay` 不能静态 import 游戏类。阶段 4 已先把接口下沉到 `:game:api`（过渡态）:

```kotlin
// :game:api（已落地，2026-08）
interface GameEntry {
    val password: String          // "#小熊跳跳" / "#小熊回家"
    @get:StringRes val titleRes: Int   // 标题资源（Home 枢纽、排行榜共用）
    @Composable fun Content(
        sceneCode: Int?,
        isDay: Boolean?,
        cityName: String?,
        onExit: () -> Unit,
        onOpenGame: ((password: String) -> Unit)? = null,   // 小熊的家枢纽内跳其它游戏
        otherGames: List<GameEntry> = emptyList(),          // 装配方注入的其它已启用游戏（随 -Pgames 裁剪增减）
    )
}
```

- `WeatherGameOverlay` 已改造为接收 `List<GameEntry>` 按口令匹配，同一时刻单游戏激活（保持历史互斥行为）
- 阶段 5 落定：门面全部删除；各游戏模块实现 `object XxxEntry : GameEntry` + `class XxxEntryProvider : GameEntryProvider`（provider 必须是普通 class 而非 object，ServiceLoader 需反射调用 public 无参构造器），经 `META-INF/services` 注册；`MainActivity` 用 `loadGameEntryProviders()`（ServiceLoader）装配，未选中模块根本不编译，裁剪效果优于状态前
- 口令常量集中 `:game:api`（`EasterEggPasswords.kt`）：home 枢纽引用其它游戏口令依赖接口层

### 4.2 SunPathMath 下沉（阶段 3）

`SunPathMath.kt` 含两个职责：日弧数学（天气页）+ 小熊生活片刻枚举（200+ 插画，gameHome 依赖）。整体迁入 `:core:designsystem`；其专属插画收在 `:core:res-illustrations`，SunPathMath 经 `IllustR` 引用。

### 4.3 widget/worker 循环依赖（阶段 4）

去掉对 `MainActivity` 类的直接引用：

```kotlin
val intent = context.packageManager
    .getLaunchIntentForPackage(context.packageName)
    ?.apply { flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP }
```

worker 的 `PendingIntent` 同理。改造后 widget/worker 仅依赖 core 层，`:app` 不再是其编译依赖。

### 4.4 字符串迁移策略

- **跟随代码**：generator 引用的 `R.string.*` 与 strings 块一起迁入所属模块
- **共享字符串**：阶段 3 建立 `:core:res` 模块（`resourcePrefix` 约束）收编跨模块公共字符串
- **迁移顺序**：strings 迁移在代码迁移**同一步**完成，避免拆完编译不过

---

## 5. 分阶段实施计划

> 每阶段 = 独立提交 + 验收通过才进入下一阶段；失败可整体 revert。

### 阶段 0：冻结基线（0.5 天）

- 提交当前未提交改动（小熊的家 + 三餐/职业插画 + 1.6.4 版本号）
- 验收：`./gradlew test lintKotlin lintFullDebug` 全绿

### 阶段 1：`:core:model` + `:core:common`（1~2 天）

**策略**：**物理迁移 + 保留包名**——新模块保持 `com.xiaoxiong.weather.data` / `.util` 包路径，30+ 消费文件的 import 零改动；统一包名整理留到阶段 6 收尾。

**迁移清单（已完成）**：

| 目标模块 | 文件 |
|---|---|
| `:core:model` | `data/WeatherModels.kt`、`data/remote/WeatherDtos.kt` |
| `:core:common` | `util/HourlyUtils.kt`（含从 WeatherCodes 迁入的 `formatHourLabel`）、`util/ChineseLunarCalendar.kt` |

**已处理依赖**：
- `HourlyUtils.formatHourDetailLabel` 依赖 `formatHourLabel`（原在 WeatherCodes），已将 `formatHourLabel` 迁入 `:core:common` 的 `HourlyUtils.kt`（同包纯函数，消费端 `import ...util.formatHourLabel` 零改动）
- app 测试对 core:model 类型的跨模块 nullable smart cast 失效（`summary.hottest!!.value`），改局部 val 消除
- `ChineseLunarCalendarTest` / `HourlyUtilsTest` 随迁至 `:core:common`

**未迁移（留在 app）**：`WeatherCodes`（含 Compose `stringResource` UI 部分）、`AppError`（依赖 `Resources`/`R`，属 Android 层，随 core:domain 阶段走）、`CardLayoutModel`（含 `StringRes`，随 designsystem 阶段走）。

**验收**：`./gradlew test lintKotlin lintFullDebug` 全绿 ✓

### 阶段 2：`:core:network` + `:core:domain`（2~3 天）

| 目标模块 | 文件（帷幕） |
|---|---|
| `:core:domain` | `data/WeatherAlertGenerator.kt`、`SeasonTipGenerator.kt`、`LifeIndexGenerator.kt`、`WeatherAdviceGenerator.kt`、`GrowingDegreeDays.kt`、`WeatherRefreshPolicy.kt` + 对应 6 个测试 |
| `:core:network` | `data/RetryInterceptor.kt`、`RadarMapRepository.kt`、`ForeignCityAliases.kt`、`data/remote/OpenMeteoHttp.kt`、`data/remote/RainViewerDtos.kt` + `foreign_city_aliases.json`（assets 随迁） |
| 迁至 `:core:common` | `util/WeatherCodes.kt`、`data/AppError.kt` + WeatherCodes 的 44 个专用字符串 + AppError 的 3 个 error 字符串（中英） + 对应测试 |
| 迁至 `:core:model` | `data/TemperatureUnit.kt`（从 `AppSettingsStore.kt` 拆出，领域概念归 model） |
| 新增 `:core:test-fixtures` | `fixtures/WeatherFixtures.kt`（JVM 模块，app/domain 测试共用） |

**关键决策（偏离初稿）**：

1. **`:core:domain` 用 Android Library 而非纯 Kotlin**——generator 大量依赖 `R.string`（`LifeIndexGenerator` 直接 `resources.getString`），强行纯 Kotlin 化需大改 API 与测试，违背低风险原则。模块自持 res，`R.string` 引用随字符串迁移自洽。
2. **`:core:common` 由纯 Kotlin 升级为 Android Library**——`WeatherCodes` 被 generator 强依赖且含 Compose `stringResource`，整体迁入还需 res。三处 Android Library 统一 `namespace = "com.xiaoxiong.weather"`，保留 `import com.xiaoxiong.weather.R` 引用路径。
3. **`:core:network` 范围收窄**——`CitySearchFetcher`/`HistoryFetcher` 依赖 `ChinaCityCatalog`（app data 层）与 `GrowingDegreeDays`（domain），强行迁移会破坏 `network→domain` 或 `network→app` 单向性。**保留在 app data 层，阶段 3 随 `:core:data` 迁移**。network 只收录纯 HTTP 基础设施。
4. **`AppError` 归 `:core:common`**——network 的 `OpenMeteoHttp` 与 data 层都要用它做错误映射，common 是唯一无环公共位置。放 domain 会迫使 network 反向依赖 domain。
5. **`TemperatureUnit` 拆到 `:core:model`**——被 WeatherCodes/widget/AppSettingsStore 共用，是纯领域概念。
6. **移除 `BuildConfig.VERSION_NAME` 依赖**——network 内 User-Agent 改用常量 `"XiaoxiongWeather (Android; personal use)"`，避免跨模块 BuildConfig 版本不一致。
7. **新增 `:core:test-fixtures`**——`WeatherFixtures` 被 app 与 domain 测试共用，不可随 domain 独走。

**风险与缓解**：

- **字符串迁移漏项**：按「代码引用 → 同步迁移」脚本提取（zh + en 双份），从 app 删除避免资源合并冲突。
- **跨模块 smart cast 失效**（阶段 1 已见）：domain 测试用局部 `val` 解包。

**验收**：`./gradlew test lintKotlin lintFullDebug` 全绿 ✓
**已实测**：`core:{common,domain,network}:testDebugUnitTest`、`:app:test{Full,Lite}DebugUnitTest`、`:core:model:test`、`:core:test-fixtures:test`、`lintKotlin`、`lintFullDebug` 全部通过。

**待办（阶段 3）**：`ForecastFetcher`/`CitySearchFetcher`/`HistoryFetcher`（依赖 app data 层）随 `:core:data` 迁移；届时 `:core:data → :core:domain, :core:network`。

### 阶段 3：`:core:data` + `:core:designsystem`（2~3 天）

| 目标模块 | 文件（帷幕） |
|---|---|
| `:core:data` | Store 层 8 个（`AppSettingsStore`/`FavoriteCityStore`/`RecentCityStore`/`SelectedCityStore`/`WeatherCacheStore`/`PhenologyCacheStore`/`DebugWeatherStore`/`WeatherNotifyDedupStore`）+ `LocationHelper`/`NetworkMonitor`/`ChinaCityCatalog`（`china_cities.json` 随迁）+ `CitySearchFetcher`/`ForecastFetcher`/`HistoryFetcher` + `WeatherRepository` 门面 + `WeatherPorts` 数据接口 + `CardLayoutModel`/`TodayCardLayout`/`HourlyCardLayout`/`DailyCardLayout`（卡片偏好协议，含 12 个 card 字符串）+ 对应 7 个测试 |
| `:core:designsystem` | `ui/theme/*`（Color/Theme/Type）、`ui/weather/SunPathMath.kt`、`ui/weather/WeatherIcon.kt`、`util/AlertBearIcon.kt`、`util/WeatherLauncherIcon.kt`、新增 `ui/weather/WeatherScene.kt`（从 `WeatherAtmosphere.kt` 抽出 `WeatherScene`enum + `weatherSceneOf`）+ 286 张 `ic_sun_bear_*`/`ic_alert_bear_*`/`ic_launcher_bear_*` 插画 + 101 个生活片刻字符串 + `SunPathMathTest`（注：`ic_sun_bear_*` 场景插画后续再抽出至 `:core:res-illustrations`，见阶段 6） |

**关键决策（偏离初稿）**：

1. **卡片布局归 `:core:data`**——`CardLayoutModel`/三个 CardLayout 是卡片偏好协议（含 `@StringRes` 但纯 Kotlin，无 Compose UI），被 `AppSettingsStore` 依赖，放 data 避免 data→designsystem 反向依赖。
2. **`WeatherPorts` 拆文件**——数据端口（WeatherFetcher/Store 源/定位会话等）迁 data；`WeatherPresentationEffects`（呈现副作用，依赖 `WeatherLauncherIcon`/`WeatherWidgetUpdater`）留在 app 独立文件，阶段 4 处理。
3. **`WeatherScene`+`weatherSceneOf` 从 `WeatherAtmosphere` 抽出下沉**——`WeatherIcon`（designsystem）强依赖场景分类，动画保留 app。
4. **`:core:designsystem` 必须带 `kotlin.compose` 插件**——缺它 `@Composable` 默认参数编译报 `ERROR_EXPR 'Default Stub'`。`SunPathMath.dailyVariantIndex` 从 internal 改 public（跨模块可见）。
5. **插画 286 张全部随迁**——生活片刻/预警/启动图标插画是 SunPathMath/AlertBearIcon/WeatherLauncherIcon 的资源，gameHome 也依赖 SunPathMath，必须全量迁移。app 剩 26 张（游戏/天气页专属）。

**已处理依赖**：
- data 层需 `tinypinyin`、`play-services-location`、`datastore-preferences`、okhttp
- designsystem 需 compose BOM + foundation + material3 + `kotlin.compose`
- 卡片/字体/定位/天气空错字符串随模块迁移（zh+en）

**验收**：`./gradlew test lintKotlin lintFullDebug` 全绿 ✓
**已实测**：全量 `test`（含 `core:data`/`core:designsystem` 测试）、`lintKotlin`、`lintFullDebug`、`:app:compile{Lite,Full}{Debug,Release}Kotlin` 全部通过。

**待办（阶段 4）**：`WeatherPresentationEffects` 移到 feature 层；widget/worker 改造（去掉 `MainActivity` 引用）；`WeatherAtmosphere` 等 UI 迁移到 features。

### 阶段 4：`:feature:weather` + `:feature:settings` + widget/worker 改造（3~4 天）

| 目标模块 | 文件 |
|---|---|
| `:feature:weather` | `ui/weather/*` 全部（ViewModel/控制器/雷达/分享/历史/Atmosphere）、`ui/debug/*`、`domain/CitySearchUseCase` + 8 个测试 |
| `:feature:settings` | `ui/settings/*` 全部 |
| widget/worker（驻留 `:app`） | `widget/*`、`worker/*`；MainActivity 引用改 `getLaunchIntentForPackage` |

**已完成的阶段 4 前置（为阶段 5 铺路）**：
- 建立 `:game:api` 模块（已编译接入），定义 `GameEntry` 接口（口令/titleRes/Content/onOpenGame）
- `WeatherGameOverlay` 改造为 `List<GameEntry>` 驱动、单游戏互斥激活；`WeatherScreen`/`MainActivity` 从游戏源码集装配 5 个 entry
- full/empty/lite 三种组合均可编译：full 走真实实现，`:games` 子集或 lite 走 DISABLED 门面

**阶段 4 主体（已完成）**：

1. **`:feature:weather`**——`WeatherViewModel`/`WeatherViewModelDeps`（`presentationEffects` 默认改为 `NoOp`）/`CitySearchUseCase`/全部 `ui.weather` 组件（Atmosphere、Today/Hourly/Daily/History 页、雷达、分享、SunPathArc、PrecipitationBarChart 等 34 文件）迁移；`WeatherPresentationEffects` 接口下沉 `:core:data`（`NoOp` 驻 data），`Default` 实现驻 `:app` 绑定 `WeatherLauncherIcon`/`WeatherWidgetUpdater`；`ui/debug/*` 随迁。测试源集随迁（`BearHolidayTest` 等 8 个）。
2. **`:feature:settings`**——`ui/settings/*` 8 文件迁移；`SettingsScreen` 用 `onBackgroundRefreshIntervalChanged` 回调替换对 `WeatherRefreshWorker` 的直接依赖（保持 feature 无 worker 依赖）。
3. **字符串/插画随迁**——按「代码引用 → 同步提取」脚本迁移各 feature 所需 `R.string`/`R.array`/`R.drawable`（含 `humidity_short`、`debug_*` 等 60+ 项，zh+en）。
4. **widget/worker 去 `MainActivity` 引用**——小组件与刷新通知均改用 `getLaunchIntentForPackage`，不再 import `MainActivity`；`WeatherAppWidget` 保留 `R` 资源。

**关键决策（偏离初稿）**：

1. **`WeatherScreen` 驻留 `:app` 作装配壳**——`WeatherScreen` 同时编排天气（feature:weather）、设置（feature:settings）、隐私门、debug、游戏入口，跨 3 个 feature。按依赖方向规则 feature 间不允许互相引用，故由 `:app` 装配层组合。feature:weather 提供天气主体（Welcome 页/今天/小时/天/历史/雷达）+ `WeatherViewModel`；`:app` 的 `WeatherViewModelFactory` 注入 `DefaultWeatherPresentationEffects` 与 DI 容器。
2. **`:game:api` 的 `GameEntry.Content` 增设可选 `onOpenGame`**——`BearHomeEntry` 枢纽内切换其它游戏口令所需，由装配方统一路由。
3. **widget/worker 不拆独立模块（精简）**——拆 `:feature:widget`/`:feature:worker` 收益有限（widget 强依赖 app 资源与 DI 容器），阶段 6 薄壳再评估；本阶段将 `MainActivity` 反向引用全部消除，解除循环依赖隐患。
4. **内部可见性放宽**——feature:weather 的 key composable（`HistoryWeatherScreen`、`TodayPage`、`HourlyPage` 等）因 WeatherScreen（app）跨模块调用改 public；收紧封装留待阶段 6 整理 API。

**验收**：全量单测（`test`）+ `lintKotlin` + `lintFullDebug` + `:app:compile{Lite,Full}{Debug,Release}Kotlin` 全部通过 ✓；真机回归（图标切换/小组件/通知）待验证。

### 阶段 5：游戏模块化（3~5 天，已确认执行）

| 目标模块 | 文件 |
|---|---|
| `:game:api`（前置完成） | `GameEntry` 接口 + `GameEntryProvider`（ServiceLoader 发现）+ 口令常量 `EasterEggPasswords` |
| `:game:common` | `app/src/gameCommon/*`（GameBear/GameScaffold/GameLeaderboard/GameWeatherFx/GameSkyFx/GameTypes/FarmWeatherCommon）+ 11 张精灵表 + 排行榜字符串 |
| `:game:jump` | `gameJump/*`（BearGameScreen/GameRunRender/BearGameEntry）+ 3 测试 |
| `:game:star` | `gameStar/*`（BearStarJumpScreen） |
| `:game:maze` | `gameMaze/*`（BearMazeScreen/Maze{Generator,Levels,Solvability}）+ 2 测试 |
| `:game:farm` | `gameFarm/*`（Farm{…} 11 文件）+ 156 张插画 + 5 测试 |
| `:game:home` | `gameHome/*`（BearHomeScreen/HomeRoutine/HomeMealDex）+ 90 张生活片刻插画 + 1 测试 |

> 后续新增的 `:game:catch`（小熊接雨雪）、`:game:hockey`（小熊冰球）、`:game:pool`（小熊台球）按同一模式下沉为独立模块（各持 res/strings + `GameEntryProvider` + 测试），当前游戏模块共 8 个。

**核心机制——`-Pgames` 退役，改动态依赖 + ServiceLoader 装配**：

1. `:app` 依 `-Pgames`（默认 all、`none`、逗号列表）对八个游戏模块做 `fullImplementation` **条件动态依赖**；未选中的模块不参与编译，classpath 上不存在其字节码。
2. 各游戏模块通过 `META-INF/services/com.xiaoxiong.weather.game.GameEntryProvider` 注册 provider（`GameEntryProvider.entry` 暴露各自的 `GameEntry` object）。provider 必须是**普通 class**（public 无参构造器），不能用 Kotlin `object`——`ServiceLoader` 靠反射 `newInstance()` 实例化，`object` 构造器是 private 的，启动即抛 `ServiceConfigurationError`。
3. `MainActivity` 用 `:game:api` 的 `loadGameEntryProviders()`（`ServiceLoader`）装配 `gameEntries`——比旧源码集机制更彻底：未选中的游戏**连字节码都不存在**，无需 Empty 门面。
4. `gameXxxEmpty` 门面、`app/src/lite` 的游戏禁用 facade、`src/gameXxxTest` 测试源集全部删除；游戏测试随各模块走。

**关键决策（偏离初稿）**：

1. **口令常量集中到 `:game:api`**——`EASTER_EGG_BEAR_{GAME,STAR,MAZE,FARM,HOME}` 从各游戏 Screen 文件上移至 `EasterEggPasswords.kt`，home 枢纽引用其它游戏口令从此依赖接口层而非实现对。
2. **`GameWeatherFx` 的 DrawScope 特效改 public**——`drawStormLightning`/`drawRainLayered` 等被 jump/star 跨模块调用，从 internal 放开。
3. **HomePlayroom 不再判 `DISABLED` 哨兵**——动态依赖下口令恒为真实值，游乐间四个入口恒展示；某游戏未装配时其入口无效由装配层（`onOpenGame` → overlay 注册表）兜底。
4. ~~**生活片刻插画 85 张随 home 拷贝**~~（阶段 6 已取消）——`ic_sun_bear_{breakfast,lunch,dinner}_*` 已在 `:core:designsystem`，但 Android Library 编译期 `R` 只含自持资源，曾把 gameHome 所需插画按「代码引用 → 同步迁移」拷入 `:game:home` res；阶段 6 起统一收至 `:core:res-illustrations`，home 直接 `api` 依赖，插画处处只打一份。
5. **lite 版零游戏实现**——动态游戏依赖仅挂 `fullImplementation`，lite 不含任何游戏实现模块（jump/star/catch/maze/farm/home/hockey/pool 字节码为零）。注意 `:game:api` 接口契约层仍由 `:app` 无条件依赖（MainActivity/WeatherGameOverlay 需引用 `GameEntry` 类型），lite 包保留约 1-2KB 接口字节码，属预期——「零痕迹」指零游戏实现，非零接口契约。

**验收（已全部通过）**：
- `-Pgames=all` / `jump` / `none` 三档编译 ✓
- APK 实测：默认 all 45.7MB（含全部游戏）→ `-Pgames=none` 33.5MB（**零游戏类**）→ `-Pgames=jump` 仅注册 jump 入口 ✓
- 全量 `test`（含 8 个游戏模块测试）+ `lintKotlin` + `:app:lint{Full,Lite}Debug` + `assemble{Lite,Full}{Debug,Release}` 全部通过 ✓

### 阶段 6（可选）：`:app` 薄壳收尾（已完成）

- `WeatherApp`/`MainActivity`/`AppContainer` 保留 `:app`；保留手动 DI（不引入 Hilt，体量小收益低）
- 清理：
  - 删除空源码目录（`app/src/{full,lite,testFull}`、`core/network/src/main/res`）与 app 内空包目录
  - app 残留 3 个测试（`WeatherCodesTest`/`WeatherLauncherIconMappingTest`/`AlertBearIconTest`）随代码分别迁入 `core:common` / `core:designsystem`；`app/src/test` 删除（app 不再有单元测试，装配层留薄）
  - `mock-maker-inline` 配置删除（Mockito 5.14 默认内联，冗余）
- 文档：README 项目结构/游戏裁剪改为动态依赖描述（8 个游戏 + ServiceLoader）；ENGINEERING CI 说明改为全量 `test`；CHANGELOG 新增「未发布」多模块重构记录；CI workflow 用 `./gradlew test` 覆盖全模块并把测试报告路径扩到 core/feature/game
- 资源去重（`ic_sun_bear_*` 场景插画单一数据源）：
  - 新建 `:core:res-illustrations` 纯资源模块，收纳全部 266 张 `ic_sun_bear_*`（designsystem 247 + 节日 19），删除 `game:home` 重复的 85 张与 `app`/`feature:weather` 的 fest 副本
  - `core:designsystem` 与 `game:home`/`feature:weather` 以 `api(project(":core:res-illustrations"))` 依赖；`nonTransitiveRClass=true` 下跨模块引用用 `import ...R as IllustR` 别名（ktlint 要求 alias 在 import 区末尾）
  - 验证：全量 `test`/`lintKotlin`/`assembleFullRelease` 通过，APK 内 `ic_sun_bear_*` 仅 266 个唯一条目（每张只打一份）

---

## 6. 工作量与风险

| 指标 | 估计 |
|---|---|
| 总工作量 | 15~20 天全职 |
| 资源/字符串迁移占比 | ~55%（游戏资源随模块迁移，比预想更规整） |
| 最高风险阶段 | 阶段 5（游戏模块化，触碰 `-Pgames`） |
| 阶段 5 收益 | 游戏独立编译缓存 / 独立测试源集 / 未来可独立发布 |

**风险清单**：

| 风险 | 缓解 |
|---|---|
| strings 迁移漏项导致编译失败 | 字符串与代码同步迁移；阶段验收依赖全量编译 |
| Store Context 初始化时序变化 | 保留单例 + `init(context)`，`WeatherApp` 在隐私同意后初始化 |
| widget/worker 行为回归 | 阶段 4 专项真机回归 + 现有仪器测试 |
| `-Pgames` 行为变化 | 阶段 5 三档编译矩阵 + 游戏测试随模块迁移（已完成 ✓） |
| R8/R 资源混淆 | 各模块自持 res 后按原 proguard 规则，逐模块验证 release 构建 |

---

## 7. 决策记录

| 编号 | 决策 | 理由 |
|---|---|---|
| D1 | 游戏模块化（阶段 5）确认执行 | 用户决策：换取游戏独立编译/测试/发布能力与长期更干净的入口架构；`-Pgames` 裁剪能力由动态依赖接棒，未选中模块直接不编译 |
| D2 | SunPathMath 整体迁入 designsystem | 同时被 weather 与 game:home 依赖的唯一无环位置 |
| D3 | 引入 `:core:res` 共享资源模块 | 收编跨模块公共字符串，避免 string 散落各模块 |
| D4 | widget/worker 用 packageManager 启动入口 | 打破对 `MainActivity` 的循环依赖，代价可忽略 |
| D5 | 手动 DI 保留（阶段 6 再评估 Hilt） | 先拆分再换框架，避免两件事同时变 |

---

## 8. 附录：迁移前准备清单（全部已完成）

- [x] 阶段 0 提交 + 基线测试绿
- [x] 建立 `:core:model` 骨架（空模块 + 依赖空置）跑通构建链路
- [x] 确认 `-Pgames` 各档当前行为（记录 APK 大小/游戏入口可达性）作为对照
- [x] 记录各游戏测试源集（`gameJumpTest`/`gameStarTest`/`gameMazeTest`/`gameFarmTest`/`gameHomeTest` 等）文件清单，供阶段 5 随模块迁移
- [x] 统计全部 `R.string` 引用按模块归属（脚本辅助）
- [x] 统计 `R.drawable` 引用按模块归属（脚本辅助）
