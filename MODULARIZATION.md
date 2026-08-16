# 多模块化重构设计文档

> 状态：**设计定稿（阶段 1~5 确认执行）**　|　适用版本：1.6.4+
> 目标：在不破坏 `-Pgames` 游戏裁剪、隐私合规门、性能基线的前提下，把单模块 app 拆成可独立构建的模块体系，并完成游戏模块化（阶段 5）。

---

## 1. 背景与动机

当前 `:app` 单模块承载全部代码（约 39k 行 Kotlin、508 张插画、316 个测试），已在单模块内通过源码集、控制器拆分、手动 DI 做到了较好的组织。但仍存在：

- **无编译期边界**：`ui.weather` 可以直接触碰 `data` 内部，无依赖方向约束
- **widget / worker 反向依赖 app**（引用 `MainActivity`），是循环依赖隐患
- **构建缓存粒度粗**：任何改动触发全模块重编译（依赖源码集切换的现状）
- **学习价值**：模块化 + 接口反转 + 资源隔离是简历级架构实践

**决策原则**：每个阶段独立可验证、可回滚；已确认执行到阶段 5（游戏模块化），`-Pgames` 源码集机制届时退役、由动态依赖接棒。

---

## 2. 现状依赖分析

### 2.1 依赖耦合点

| 耦合 | 位置 | 性质 | 对策 |
|---|---|---|---|
| R 资源引用 | 60 个文件 `import com.xiaoxiong.weather.R` | 字符串/插画绑定 | 资源随代码迁移 |
| gameHome → `ui.weather.SunPathMath` | `SunPathPhase`/`sunPathBearMoment`/`SunPathBearMoment`/`isWeekend` | UI 反向被游戏依赖 | SunPathMath 下沉 `core:designsystem` |
| gameCommon → `util.WeatherCodes` | 天气码转降水强度 | 领域逻辑 | 迁入 `core:common` |
| widget → `MainActivity` | `actionStartActivity(Intent(...))` | 循环依赖隐患 | packageManager 启动入口 |
| worker → `MainActivity` + `widget.WeatherWidgetUpdater` | PendingIntent / 刷新 | 循环依赖隐患 | 同上 + 依赖 `feature:widget` |
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
├── :core:model              # 纯 Kotlin 数据模型（无 Android 依赖）
├── :core:network            # OkHttp fetcher + 序列化 DTO
├── :core:domain             # 纯逻辑生成器 + 城市目录 + 类型化错误
├── :core:data               # DataStore/SharedPreferences Store、缓存、Repository 门面
├── :core:designsystem       # Compose 主题 + SunPathMath + 生活片刻插画 + 通用卡片布局
├── :core:common             # WeatherCodes、格式化工具等纯 util（含自带少量 res）
├── :feature:weather         # WeatherScreen/ViewModel/各页面/雷达/分享
├── :feature:settings        # 设置/隐私/卡片设置
├── :feature:widget          # Glance 小组件（无 MainActivity 引用）
├── :feature:worker          # WorkManager 通知/刷新
├── :game:api                # GameEntry 接口（口令 → Composable 注册表）
├── :game:common             # 共享精灵表/排行榜/天气特效
├── :game:jump|star|maze|farm|home   # 各游戏独立模块（已确认执行）
├── :baselineprofile         # 保留不动
└── (阶段 5 移除) app/src/gameXxx* 源码集与 Empty 门面
```

### 3.2 依赖方向（严格单向）

```
:core:model        ← 无依赖（基石）
:core:common       ← :core:model
:core:network      ← :core:model
:core:domain       ← :core:model, :core:common, :core:network
:core:data         ← :core:model, :core:common, :core:domain, :core:network
:core:designsystem ← :core:model, :core:common
:feature:weather   ← core:{model,network,domain,data,designsystem,common}
:feature:settings  ← core:{model,data,designsystem,common}
:feature:widget    ← core:{model,data,common}, core:designsystem（图标）
:feature:worker    ← core:{model,data,common}, feature:widget
:game:common       ← :game:api, core:{designsystem,common,model}
:game:xxx          ← :game:api, :game:common
:app               ← 全部 feature / game
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

游戏模块化后 `WeatherGameOverlay` 不能静态 import 游戏类。对策：

```kotlin
// :game:api
interface GameEntry {
    val password: String          // "#小熊跳跳" / "#小熊回家"
    val titleRes: Int
    @Composable fun Content(sceneCode: Int?, isDay: Boolean?, onExit: () -> Unit)
}
```

- `WeatherGameOverlay` 只遍历注入的 `List<GameEntry>` 匹配口令
- `:app` 的 `AppContainer` 按 `-Pgames` 动态 `implementation(project(":game:xxx"))` 并组装列表
- 未选中的游戏模块**根本不编译**，裁剪效果优于现状
- 现有 `app/src/gameXxxEmpty/*` 空实现门面可全部删除

### 4.2 SunPathMath 下沉（阶段 3）

`SunPathMath.kt` 含两个职责：日弧数学（天气页）+ 小熊生活片刻枚举（200+ 插画，gameHome 依赖）。整体迁入 `:core:designsystem`，附带其专属插画。

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

| 目标模块 | 文件 |
|---|---|
| `:core:network` | `data/CitySearchFetcher.kt`、`ForecastFetcher.kt`、`HistoryFetcher.kt`、`RadarMapRepository.kt`、`RetryInterceptor.kt`、`ForeignCityAliases.kt`（assets 随迁） |
| `:core:domain` | `data/WeatherAlertGenerator.kt`、`SeasonTipGenerator.kt`、`LifeIndexGenerator.kt`、`WeatherAdviceGenerator.kt`、`GrowingDegreeDays.kt`、`WeatherRefreshPolicy.kt`、`data/remote/*` |

**风险**：generator 大量 `R.string`，每个生成器专属 strings 块随迁；`ForecastFetcher` 也引 R。
**验收**：`app/src/test` 中 generator 测试迁入对应模块并全绿。

### 阶段 3：`:core:data` + `:core:designsystem`（2~3 天）

| 目标模块 | 文件 |
|---|---|
| `:core:data` | `FavoriteCityStore`、`SelectedCityStore`、`RecentCityStore`、`WeatherCacheStore`、`PhenologyCacheStore`、`AppSettingsStore`、`DebugWeatherStore`、`WeatherNotifyDedupStore`、`LocationHelper`、`NetworkMonitor`、`ChinaCityCatalog`（assets 随迁）、`WeatherPorts`、`WeatherRepository` |
| `:core:designsystem` | `ui/theme/*`（Theme/Color/Type）、`ui/weather/SunPathMath.kt`、`util/WeatherLauncherIcon.kt`、`util/AlertBearIcon.kt`、`ui/weather/WeatherIcon.kt`、卡片布局 `TodayCardLayout`/`HourlyCardLayout`/`DailyCardLayout`（若含 R 字符串随迁）+ 全部生活片刻/天气/预警插画 |

**注意**：Store 层在模块内提供 `fun init(context)` 或 `class XStore(context)`；`WeatherRepository` 门面依赖 network+domain，收编进 `:core:data`。
**验收**：单测全绿；`:app` 装配改为从各模块实例化。

### 阶段 4：`:feature:weather` + `:feature:settings` + widget/worker 改造（3~4 天）

| 目标模块 | 文件 |
|---|---|
| `:feature:weather` | `ui/weather/*` 全部（含 WeatherScreen、WeatherViewModel、控制器、雷达、分享、历史）、`ui/debug/*` |
| `:feature:settings` | `ui/settings/*` 全部 |
| `:feature:widget` | `widget/*` 全部；MainActivity 引用改 packageManager |
| `:feature:worker` | `worker/WeatherRefreshWorker.kt`；依赖 `feature:widget` 的 `WeatherWidgetUpdater` |

**前置（阶段 4 完成，为阶段 5 铺路）**：先抽出 `:game:api`（仅接口，无实现），`WeatherGameOverlay` 改为 `List<GameEntry>` 驱动；游戏实现仍留 `:app` 源码集（过渡态）。阶段 5 只做"搬实现"，不改入口结构。
**验收**：全量单测 + `connectedAndroidTest` + 真机手工回归（图标切换/小组件/通知）。

### 阶段 5：游戏模块化（3~5 天，已确认执行）

| 目标模块 | 文件 |
|---|---|
| `:game:api` | `GameEntry` 接口 |
| `:game:common` | `app/src/gameCommon/*`（精灵表、排行榜、天气特效）+ `gameCommon/res` |
| `:game:jump` | `gameJump/*` |
| `:game:star` | `gameStar/*` |
| `:game:maze` | `gameMaze/*` |
| `:game:farm` | `gameFarm/*` |
| `:game:home` | `gameHome/*`（含 HomeRoutine/HomeMealDex） |

- `-Pgames` 改为 `:app` 动态依赖；删除 `gameXxxEmpty` 门面与源码集逻辑
- 游戏测试源集跟随各模块
- **验收**：`-Pgames=jump` / `-Pgames=none` / `-Pgames=all` 三档编译 + 全量测试

### 阶段 6（可选）：`:app` 薄壳收尾

- `WeatherApp`/`MainActivity`/`AppContainer` 保留 `:app`；可评估引入 Hilt 或保留手动 DI
- 清理：删除空目录、更新 README/ENGINEERING/CHANGELOG

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
| `-Pgames` 行为变化 | 阶段 5 三档编译矩阵 + `gameXxxTest` 随模块迁移 |
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

## 8. 附录：迁移前准备清单

- [ ] 阶段 0 提交 + 基线测试绿
- [ ] 建立 `:core:model` 骨架（空模块 + 依赖空置）跑通构建链路
- [ ] 确认 `-Pgames` 各档当前行为（记录 APK 大小/游戏入口可达性）作为对照
- [ ] 记录 5 个游戏各自的测试源集（`gameJumpTest`/`gameStarTest`/`gameMazeTest`/`gameFarmTest`/`gameHomeTest`）文件清单，供阶段 5 随模块迁移
- [ ] 统计全部 `R.string` 引用按模块归属（脚本辅助）
- [ ] 统计 `R.drawable` 引用按模块归属（脚本辅助）
