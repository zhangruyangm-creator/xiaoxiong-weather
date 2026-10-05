# 小熊冰球：AI 与场地主题技术说明

> 对应模块：`:game:hockey`（小熊冰球）
> 核心文件：`HockeyLogic.kt`（物理 / AI / 计分纯逻辑）、`HockeyGame.kt`（对局状态机、多子步推进、快照编解码）、`HockeyRender.kt`（世界绘制入口/动态层）、`HockeyRenderCache.kt`（静态层与精灵缓存）、`HockeyTableStatic.kt`（球桌静态层）、`HockeyMarkings.kt`（场地标线/门柱/球门）、`HockeyArt.kt`（挡板/蜜罐美术与拖尾）、`HockeyAmbient.kt`（粒子/灯光/氛围）、`HockeyDialogs.kt`（设置/生涯/成就弹窗）、`HockeyFormControls.kt`（设置表单控件）、`BearHockeyScreen.kt`（HUD / 排行榜 / 帧循环接线）、`HockeyRinkTheme.kt`（场地主题枚举）、`HockeyCareer.kt`（生涯统计与成就）
> 摘要：本文说明一款竖屏空气曲棍球如何把「球桌固定子步 + 主题摩擦 + 门柱圆碰撞」的确定性物理、带延迟/预判/守门混合的 AI（含门线解围与墙角解脱两套兜底）、四档场地主题与静态层缓存，以及可续玩快照、净胜排行榜与生涯成就收在一套「纯 Kotlin + Compose Canvas」实现里。

---

## 1. 分层与坐标系

| 层 | 核心文件 | 职责 |
|----|----------|------|
| 纯逻辑 | `HockeyLogic.kt` | `Table`/`Body` 几何、摩擦积分、门柱碰撞、AI 目标决策、计分判定。无 Compose/Android 依赖，可直接 JUnit |
| 对局状态机 | `HockeyGame.kt` | 阶段流转、输入、3 子步推进、碰撞反馈、墙角死锁检测、快照编解码 |
| 渲染入口 | `HockeyRender.kt` | `drawHockeyWorld` 组装静态层/动态层与动态计分浮字 |
| 渲染缓存 | `HockeyRenderCache.kt` | 静态层烘焙（`HockeyTableLayerCache`）、挡板/蜜罐精灵（`HockeySpriteCache`）、Path/Paint/Stroke 复用 |
| 静态与美术 | `HockeyTableStatic.kt`、`HockeyMarkings.kt`、`HockeyArt.kt` | 球桌静态层、场地标线/门柱/球门、挡板与蜜罐美术 |
| 氛围 | `HockeyAmbient.kt` | 火花/进球灯/彩带/发球提示/场外天气 |
| 界面 | `BearHockeyScreen.kt` | 帧循环接线、HUD、排行榜 |
| 弹窗与表单 | `HockeyDialogs.kt`、`HockeyFormControls.kt` | 设置/生涯/成就弹窗与设置控件 |
| 主题 | `HockeyRinkTheme.kt` | 4 档场地枚举（id / 摩擦 / 标题资源） |
| 生涯 | `HockeyCareer.kt` | `HockeyProgress` 统计、`HockeyAchievement` 成就、prefs 读写 |

坐标系为竖屏俯视：玩家在下方、AI 在上方，Y 向下为正。`Table` 由 `HockeyLogic.layoutTable(canvasW, canvasH, padH, padV)` 在画布内边距内生成，`midX/midY` 为中线。

### 1.1 球桌常量

| 量 | 公式 | 说明 |
|----|------|------|
| `goalWidth` | `shortSide * 0.36f` | 球门宽度（`shortSide = min(width, height)`） |
| `paddleR` | `shortSide * 0.075f` | 挡板半径 |
| `puckR` | `shortSide * 0.038f` | 蜜罐半径 |
| 挡板倍率 | `PADDLE_SCALE_MIN..MAX = 0.8..1.6` | `resize` 时 `player.r = paddleR * paddleScale`，AI 视 `paddleScaleBoth` 是否同步 |
| 蜜罐倍率 | `PUCK_SCALE_MIN..MAX = 0.6..1.5` | `puckR` 另被 `paddleR * 0.95f` 封顶 |
| 球门倍率 | `GOAL_SCALE_MIN..MAX = 0.55..1.45` | 结果再钳到 `shortSide*0.18 .. width*0.88` |
| 获胜分 | `WIN_SCORE_MIN..MAX = 1..99` | 设置支持 3/5/7 快捷档与自定义 |
| 自定义难度 | `CUSTOM_LEVEL_MIN..MAX = 1..15` | 在 BEGINNER 与 MASTER 参数之间线性插值 |

---

## 2. 物理：子步积分、摩擦与挡板

### 2.1 固定子步

`HockeyGame.stepPlaying` 把一帧 `dt` 切成固定 3 个子步，逐子步调用 `integratePuck` 与 `collidePaddle`：

```kotlin
val steps = 3
val sub = dt / steps
repeat(steps) {
    val r = HockeyLogic.integratePuck(puck, t, sub, friction = rinkTheme.friction)
    ...
    HockeyLogic.collidePaddle(puck, player, pvx, pvy, t) ||
        HockeyLogic.collidePaddle(puck, ai, ai.vx, ai.vy, t)
}
```

`dt` 钳制由共享层 `GameFrameLoop` 完成（与其它游戏一致），子步进一步降低高速球单帧穿透挡板/门柱的概率。

### 2.2 `integratePuck`

| 参数 | 默认值 | 语义 |
|------|--------|------|
| `friction` | `0.18f` | 每子步阻尼 `damp = (1 - friction*dt).coerceIn(0.85f, 1f)` |
| `minSpeedMul` | `0.12f` | 速度低于 `shortSide * minSpeedMul` 时按比例抬回（保持球一直在动） |
| `maxSpeedMul` | `2.35f` | 速度上限 `shortSide * maxSpeedMul` |

先位移再摩擦阻尼、再钳速，然后依次处理左右墙反弹、四个门柱圆碰撞、上下门线判定。左右墙用位置夹取 + 法向速度反号；上下方向只有不在门洞横向范围内才弹墙。

### 2.3 `collidePaddle`

圆-圆碰撞（挡板视为无限质量）：先沿法线分离 `overlap + 0.5f`，用相对速度判断接近方向，再以恢复系数 `1.85f` 反射：

```kotlin
puck.vx -= (1.85f) * vn * nx
```

代码注释记录了历史 bug：公式本身已包含挡板沿法线的速度，旧写法额外注入一次挡板速度，会在「球已远离、挡板还在追」时凭空加速甚至把球顶进自家门。碰后钳速到 `shortSide * 0.18f .. 2.35f`，并保证贴死时不致速度为 0。

### 2.4 墙角挤压与解脱

球被挡板顶在墙角时，墙面反弹与挡板碰撞互相抵消，会出现原地高频抖动。处理分三层：

| 机制 | 常量/条件 | 说明 |
|------|-----------|------|
| 接触限速 | `CORNER_CONTACT_SPEED_MUL = 1.1f` | `paddleContact && inCornerZone` 时把球速压到 `shortSide * 1.1`，避免恢复系数向角落球持续泵能 |
| 死锁窗口 | `STUCK_WINDOW_SECONDS = 0.3f` | 窗口内净位移 `< 2*puckR` 且持续接触挡板且位于墙角才判定卡住；单帧位移会被往复抖动清零，故看整段净位移 |
| 解脱踢 | `cornerEscapeKick` | 朝场地中心（`random` 抖动 ±0.25 rad）给球 `ESCAPE_SPEED_MUL = 1.15` 速度，并沿该方向把球搬出 `paddleR * 2.5f`，最后钳回桌内 |

`inCornerZone` 以「球心距最近角点 ≤ `2 * puckR`」为区域；直库中段的慢速贴库推球不触发（`HockeyGameTest.straightWallPress_doesNotTriggerCornerEscape`）。`cornerEscapeCount` 供诊断与回归断言。

---

## 3. 门柱、库边与进球判定的一致性

左右库边即 `table.left/right`，球心被夹到 `left + r / right - r`；球门宽度 `goalWidth`，门洞横向范围是 `midX ± goalWidth/2`。

门柱用**圆-圆碰撞**，半径由 `goalPostRadius(table)` 统一给出：

```kotlin
val frame = min(shortSide * 0.075f, min(table.left, table.top) * 0.86f).coerceAtLeast(2f)
return max(frame * 0.92f * 0.40f, shortSide * 0.012f)
```

`HockeyMarkings.kt:266` 画柱时直接调用同一函数，因此「看到的门柱」和「撞到的门柱」严格一致。

`collideGoalPost` 的关键点：

- **先于进球判定**执行：正对柱心的球被弹回（`vy` 反号）并返回 `hitWall`，不会被计进；
- **跨线法线修正**：快球单帧跨过柱心时，改用移动前位置（`prevX/prevY`）定法线，避免把球继续往门里推；
- **已越过门线的球不再被柱拦截**：`(prevY - lineY) * rinkSign < 0f` 直接跳过，保证「贴柱内侧滑入」仍按进球处理；
- 柱视为固定圆柱，恢复系数 `POST_RESTITUTION = 1.7f`。

进球判定放在上下边界处理里：`inGoalX && puck.vy < 0f` → `Scorer.PLAYER`（进上方 AI 门），`puck.vy > 0f` → `Scorer.AI`；方向条件排除「已在门内但向外飞」的球。

回归用例：`goal_whenPuckEntersTopGoal`、`goalPost_shotAtPostCenter_bouncesWithoutGoal`、`goalPost_insidePostClearance_scores`、`goalPost_grazeInsidePost_stillSlidesIn`、`goalPost_outsidePost_noGoal`。

---

## 4. 场地主题（`RinkTheme`）

4 档主题几何完全一致，只改**地面摩擦**与**静态外观材质**，顺序即摩擦从滑到涩：

| 主题 | `id` | `friction` | 视觉要点 |
|------|------|-----------|----------|
| `ICE` | `"ice"` | `0.18f` | 默认；木框 + 冰面、冷调反光，无实体库边 |
| `BEAR` | `"bear"` | `0.22f` | 奶油/冰蓝画布、缝线织纹与极淡水印 |
| `WOOD` | `"wood"` | `0.28f` | 蜂蜜木纹、清漆高光、深色木库 |
| `CONCRETE` | `"concrete"` | `0.38f` | 灰调水泥、分格缝与骨料斑点 |

- `id` 用于 prefs / 快照持久化，**必须稳定**，不可随枚举顺序变化；`RinkTheme.fromId(id)` 对 null / 空串 / 大小写不符 / 未知 id 一律回退 `DEFAULT = ICE`；
- 选择保存在 `PREF_RINK_THEME = "hockey_rink_theme"`，设置弹窗用 `RinkTheme.entries.chunked(2)` 展示，每档附摩擦手感说明（`rinkThemeDesc`）；
- **主题是即时皮肤**：`applySettings` 中即使 `rulesLocked`（对局进行中）也立即写入 `rinkTheme`，`pendingRinkTheme` 恒为 null；只有获胜分与挡板/蜜罐/球门尺寸会暂存到下一局（`consumePendingRules` 在 `startMatch` 套用）；
- 旧 1.10 快照可能带「下一局生效」的 pending 主题，恢复时折叠成当前主题（`readRinkThemeId`），避免「设置里是木桌、场上还是冰面」。

### 4.1 静态层缓存

`HockeyRenderCache.kt` 的 `HockeyLayerKey` 是纯数据（便于单测）：`width/height + tableLeft/tableTop/tableWidth/tableHeight + goalWidth + theme + style + isDay`。

| 常量 | 值 | 语义 |
|------|----|------|
| `HOCKEY_LAYER_RESIZE_TOLERANCE_PX` | `24` | 位图宽高容差，窗口连续拖动时避免逐像素重建 |
| `HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX` | `1f` | 球桌 left/top/width/height 容差 |
| `HOCKEY_LAYER_GOAL_TOLERANCE` | `2f` | 球门宽容差 |
| `HockeyTableLayerCache.MAX_ENTRIES` | `2` | LRU 张数 |

`hockeyLayerKeyReusable(cached, target)`（同在 `HockeyRenderCache.kt`）要求主题/样式/昼夜严格一致、几何在 1px 容差内、尺寸在 24px 容差内。**主题必须参与缓存键**，否则切主题时其它键都没变会复用旧静态层（`HockeyLayerKeyTest.rinkThemeChange_notReusable`）。

### 4.2 精灵缓存与释放

`HockeyRenderCache.kt` 的 `HockeySpriteCache`（`MAX_ENTRIES = 3`）缓存玩家两种配色与 AI 挡板、蜜罐的烘焙精灵；半径 key 量化到 `round(radius * 2)/2`，挡板烘焙范围 `1.55r`（留投影）、蜜罐 `1.12r`。退出对局时 `BearHockeyScreen` 的 `DisposableEffect.onDispose` 调用 `clearHockeyRenderCaches()`，释放 2 张全屏位图与全部精灵；二次进入重新烘焙。

---

## 5. AI

### 5.1 难度参数

`HockeyLogic.params(difficulty, customLevel)` 返回 `DifficultyParams`（`aiMaxSpeedMul / aiAccelMul / reactionDelay / predict / defenseBias / chase`）：

| 难度 | 最大速度× | 加速度× | 反应延迟 s | 预判 | 守门倾向 | 出击 |
|------|-----------|---------|------------|------|----------|------|
| `BEGINNER` | 0.30 | 1.6 | 0.55 | 0.0 | 0.85 | 0.20 |
| `EASY` | 0.42 | 2.2 | 0.42 | 0.05 | 0.72 | 0.35 |
| `NORMAL` | 0.66 | 3.6 | 0.20 | 0.24 | 0.52 | 0.56 |
| `ADVANCED` | 0.84 | 5.0 | 0.10 | 0.40 | 0.42 | 0.72 |
| `HARD` | 1.45 | 11 | 0.01 | 0.85 | 0.22 | 1.0 |
| `MASTER` | 2.05 | 18 | 0.0 | 1.0 | 0.10 | 1.0 |

`CUSTOM` 用 `lerpParams(BEGINNER, MASTER, (level-1)/14)` 线性插值；`HockeyGame.effectiveParams` 按 `difficulty.ordinal shl 32 or customLevel` 组合键缓存结果，避免 CUSTOM 每帧新建对象（`invalidateParamsCache` 在设置保存/开新局时失效）。速度单位是「相对短边/秒」，`stepAiVelocity` 用 `approach` 做有限加速度逼近并按最大速度钳制。

### 5.2 防守/进攻决策

`updateAiTarget` 的优先级（传入 `ai: Body` 时）：

1. **门线解围**（见 5.3）满足 `aiGoalLineClearanceActive` 时直接走横向扫球；
2. **智能防反**：`smartDefense = predict >= 0.5f`（HARD/MASTER）且来球危险（`vy < -0.10 * shortSide`）时，用 `goalLineInterceptX` 算门线落点，`tx = lerp(puck.x, interceptX, 0.72 + 0.28*predict)`，`ty = puck.y - paddleR*0.9`（挡板必须在来球上方，接触才把球压回去）。若 AI 还在球下方/同一水平带（`ai.y > puck.y - d*0.35`），先侧移到球侧方 `puck.x ± paddleR*2` 再上移绕行——防止从内侧接触把原本会弹库的球蹭进自家门（`defense_fromBelowOutsidePost_neverOwnGoal`）；
3. **进攻走位**：球在 AI 半场且不是快速来球时，先绕到球上方（`aboveContact = puck.y - d*0.55`），再向下推。挑战及以下对准球心；**大师从外侧（相对中轴）击打**，出球带向玩家球门。横向侧别用 `cachedTargetX` **锁存**，避免目标点左右抖动导致原地打转；
4. **守门/回位**：来球危险时继续站门线落点；否则按 `puck.x + puck.vx * predict * 0.28` 预判。挑战及以下：本方半场跟球、对方半场缩回 `homeY`；**大师在对方半场悬在中线附近切射门通道**（`lerp(homeY, midY, 0.78)`，拦截权重 0.92）；
5. **兼容路径**：不传 `ai`（旧调用/单测）时保留原「跟球 + 半场出击 + defenseBias 混合」逻辑。

`clampPaddle` 负责边界：玩家 `y ∈ [midY + r*0.35, bottom - r]`；AI 默认 `y ∈ [top + r, midY - r*0.35]`，仅门线解围时允许 `aiGoalLineHug` 压到 `top + r*0.5`（球的中心不能压到 `top`，否则半个挡板穿出球门框）。

### 5.3 门线解围：为什么需要

当年两个真实 bug：

- **乌龙**：球贴门线、AI 从球下方/侧后方接近时，接触法线带向内分量，会把球直接顶进自家门；
- **死锁**：球停在球侧 `2*paddleR` 处，AI 因挡板 y 下界无法绕到球上方，也够不到球，双方僵住不触球。

修复方式是把「球贴门线且不朝自家门快速飞来」识别为独立状态，允许 AI 压到门线并**横向把球扫离门洞中心**：

```kotlin
fun aiGoalLineClearanceActive(table, puck, paddleR): Boolean {
    if (puck.y >= table.top + paddleR * 2f) return false
    if (puck.vy < -table.shortSide * 0.10f) return false
    val dx = abs(puck.x - table.midX)
    if (puck.vy <= table.shortSide * 0.06f) {
        return dx <= goalHalf + puck.r + paddleR * 1.5f   // 慢球/静止：留 1.5 个挡板余量
    }
    return dx <= goalHalf + puck.r                         // 正在外出的球：只在门洞内卡位
}
```

`aiGoalLineSweep` 的实现要点：

- 按球在左/右半侧决定 `pushDir`，站到球的另一侧 `standX = puck.x - pushDir * d * 1.3`；
- 先「下沉让开通道」（目标 `safeBelowY = puck.y + d*1.05`），再横移，最后扫球目标略高于球心（`puck.y - paddleR*0.15`），保证接触只把球推向侧方/台面内侧；
- 正在外出的球（`vy > 0.06*shortSide`）只卡侧位、不主动扫，避免把球顶回门线或注入额外能量；
- 扫球期间的挡板接触在 `HockeyGame` 里同样限速到 `CORNER_CONTACT_SPEED_MUL`，避免解围变成全场乱飞的抽射。

回归覆盖：`aiBelowPuckNearOwnGoal_neverScoresOwnGoal`、`aiBelowPuckNearOwnGoal_clearsPuckAwayFromGoal`、`aiNearOwnGoalLine_staticPuck_neverKnocksInOwnGoal`（多组逼近位置）、`aiNearOwnGoalLine_exitingPuck_notKnockedBack`、`aiNearOwnGoalLine_puckAtPost_touchesWithinFifteenSeconds`（防死锁），以及反例 `topRailSlowPuckFarFromGoal_usesNormalFollowLogic`（远离门洞的贴顶库慢球不得被扫球接管）。

### 5.4 门线落点与折叠

`goalLineInterceptX` 用 `(goalY - y) / vy` 求时间，`x + vx * t` 求落点，再经 `foldIntoRange` 按 `[left + puck.r, right - puck.r]` 做镜面折叠，模拟侧墙反弹后的落点；球不朝该方向飞时退回当前 X。测试：`goalLineIntercept_tracksAngledShot`、`foldIntoRange_mirrorsWalls`。

---

## 6. 对局工程

### 6.1 阶段与幂等结算

阶段为 `READY / SERVE / PLAYING / SCORED / MATCH_OVER`。`tick` 中：`SERVE` 倒数结束按 `lastScorer` 决定发球方向（失球方接发球，首球随机）；`SCORED` 播完 1.15s 横幅后判定 `matchOver(playerScore, aiScore, winScore)`，达到决胜分走 `settleDecidedMatch()` 再转 `MATCH_OVER`，否则复位再发球。

`settleDecidedMatch` 是幂等的：只在 `matchWinner == null` 时记录胜者并给玩家加 `bestWins`，不推 `phase`。界面在决胜球横幅期间退出/提交榜单时提前补结算，之后 `tick` 再转相位也不会重复计胜场（`flow_decidingGoalBannerPlaysBeforeMatchOver`）。

`startMatch` 会锁定 `matchDifficulty = difficulty`（本局记账难度），并重置分数、火花、轨迹、暂存窗口、触摸点等瞬时状态。

### 6.2 暂停与快照

快照 key `HOCKEY_SNAPSHOT_KEY = "hockey_snapshot_v1"`。保存时机（`ON_STOP`、`onDispose` 兜底与主动退出抑制已统一由 `:game:common` 的 `GameSnapshotLifecycle`（`GameSnapshotSession.kt`）驱动）：`saveSnapshot` 仅在 `SERVE/PLAYING/SCORED` 写入；标题/结算不写。退出与 `MATCH_OVER` 均 `clearSnapshot`。

`:game:common` 的 `GameRenderPrimitives.kt` 提供公共渲染原语（`BakedLayerLru`/`strokeOf`/`gameHash01`），本作当前的 `strokeOf`/`hockeyHash01` 仍定义在 `HockeyRenderCache.kt`；`GameCards.kt` 提供公共 READY/结算卡与文案（`GameReadyCard`/`GameOverCard`/`gameBestLabel`/`gameViewRank`/`gameExit`/`gameRestart`）。

编码结构：

- 表头固定 `HOCKEY_HEADER_FIELDS = 21` 字段：阶段序号、双方比分、发球计时、scoreFlash、hitFlash、自定义等级、获胜分、挡板/蜜罐/球门倍率、胜场、胜者序号、`resultSubmitted`、`lastScorer`、`time`、画布宽高与 padding、难度名长度，其后紧跟难度名逐字符码；
- 拖尾字段（旧档可缺省）：`paddleScaleBoth`、`matchDifficulty`、5 个 pending 设置、当前场地主题 id（长度 + 字符码）、旧 pending 主题（-1 表示无）。

`applySnapshot` 明确分两段：

1. **先解析校验**：表头长度、阶段合法且非 READY/MATCH_OVER、难度名、球体三组（各 ≥5 字段）全部读进局部变量，任何一步失败直接 `return false`，此时未写任何成员；
2. **后提交**：通过后才写 difficulty / 倍率 / pending / 主题，重建或复用球桌，按当前球桌重算半径并钳制位置，最后套用比分、计时与轨迹。

几个容易踩坑的细节：

- 恢复出的坐标**不用存档旧半径**：画布尺寸可能已变，旧半径过大会让 `clampPaddle` 的 `coerceIn` 区间反转并静默丢档（`restore_oversizedSavedRadii_doesNotThrowOrDropSnapshot`）；
- 恢复早于首次 `onSizeChanged` 时，用存档画布尺寸先建球桌；触摸点不恢复（清 `touchX/touchY`）；`aiTargetX/Y` 同步到恢复位置、`aiReactionAge = 99f`，否则 AI 会朝初值 (0,0) 猛冲；
- pending 设置必须随快照往返，否则恢复后被旧值覆盖、设置永不生效；坏档失败时不得污染已有的 pending 与主题（`restore_brokenBodyGroups_failureKeepsExistingPendingRulesAndTheme`）；
- 旧档兼容：无拖尾 → `paddleScaleBoth = true`、`matchDifficulty = 存档难度`、无 pending；无主题段 → 默认 ICE；1.10 的 pending 主题折叠为当前主题。

### 6.3 计分与净胜排行榜

对局按净胜球入榜，`tieBreak` 用失球数（同为 +2 时 3:1 排在 4:2 前）：

```kotlin
LeaderboardEntry(
    score = playerScore - aiScore,
    tieBreak = aiScore,
    label = "$playerScore : $aiScore",
)
```

榜单 key 为 `leaderboard_hockey_v2`、`retainRecent = true`（保留最近记录），只有净胜 > 0 才由 `leaderboardDisplayRank` 显示名次；`best_hockey` 是累计胜场生涯数据，传 `bestKey = null` 不随清榜清零。旧 key `leaderboard_hockey` 延迟到首次成功进榜或用户主动清榜时清理。测试：`HockeyLeaderboardEntryTest.winEntry_isNetGoalsWithGoalsAgainstTieBreak`、`lossEntry_keepsNegativeNetAndFullGoalsAgainst`、`shutoutWin_hasZeroTieBreakAndPositiveRank`。

### 6.4 生涯统计与成就

`HockeyProgress` 记录局数/胜负/进失球/零封/连胜与最佳连胜/单场最多进球/零封单场最多进球/各难度胜场/已提示成就，prefs key 见 `HOCKEY_STAT_KEYS`（前缀 `hockey_`），`advance` 在一局结束时推进，胜场按开局锁定的 `matchDifficulty` 记账。

| 成就 | id | 解锁条件 |
|------|----|----------|
| `FIRST_WIN` | `first_win` | 至少 1 胜 |
| `SHUTOUT` | `shutout` | 至少 1 次零封 |
| `WIN_STREAK_3` | `win_streak_3` | 最佳连胜 ≥ 3 |
| `HARD_WIN` | `hard_win` | HARD 难度至少 1 胜 |
| `MASTER_WIN` | `master_win` | MASTER 难度至少 1 胜 |
| `FIVE_GOALS` | `five_goals` | 单场 ≥ 5 球 |
| `PERFECT_FIVE` | `perfect_five` | 零封且单场 ≥ 5 球 |
| `VETERAN` | `veteran` | 总局数 ≥ 10 |

`unlocked` 只记录「是否弹过窗」，解锁状态始终由统计派生；新解锁批量进 `pendingUnlocks` 弹窗。清空统计同时清 `HOCKEY_STAT_KEYS`。

### 6.5 帧循环与 `reduceMotion`

`BearHockeyScreen` 用共享 `GameFrameLoop`：`READY/MATCH_OVER` 节流到约 12fps（`slowTick % 5`），`dt` 按真实帧累计成 `animDt` 后整笔提交；暂停/设置/排行榜/成就弹窗时不 tick。

`game.reduceMotion = LocalReduceMotion.current` 只冻结装饰用 `time`（蜜罐滚动、发球脉冲、场外天气），物理、发球倒数、进球横幅与闪烁衰减照常；渲染层跳过获胜彩带、球轨迹与火花，但保留静态天空与场地。测试：`HockeyGameTest.reduceMotion_freezesDecorativeTimeOnly`。

---

## 7. 测试与回归

| 测试文件 | 关键用例 |
|----------|----------|
| `HockeyLogicTest` | `layout_midAndGoalsSensible`、`clampPaddle_staysInOwnHalf`、`clampPaddle_goalLineHugLimitsCenterToHalfRadius`、`wallBounce_leftReflects`、`goal_whenPuckEntersTopGoal`、`goalPost_shotAtPostCenter_bouncesWithoutGoal`、`goalPost_insidePostClearance_scores`、`goalPost_grazeInsidePost_stillSlidesIn`、`goalPost_outsidePost_noGoal`、`paddleCollision_transfersImpulse`、`difficultyParams_sixPresetsOrderedByStrength`、`customLevel_interpolatesBetweenBeginnerAndMaster`、`goalLineIntercept_tracksAngledShot`、`foldIntoRange_mirrorsWalls`、`cornerEscapeKick_pointsAwayFromCorner`、`cornerEscapeKick_clearsPressingPaddle` |
| `HockeyAiTest` | `aiBelowPuckNearOwnGoal_neverScoresOwnGoal`、`aiBelowPuckNearOwnGoal_clearsPuckAwayFromGoal`、`aiAbovePuck_drivesPuckTowardPlayerGoal`、`defense_fromBelowOutsidePost_neverOwnGoal`、`aiNearOwnGoalLine_staticPuck_neverKnocksInOwnGoal`、`aiNearOwnGoalLine_exitingPuck_notKnockedBack`、`aiNearOwnGoalLine_puckAtPost_touchesWithinFifteenSeconds`、`topRailSlowPuckFarFromGoal_usesNormalFollowLogic`、`aiDefendsShotLine_withAi` |
| `HockeyGameTest` | `flow_serveToPlayingToScoredToMatchOver`、`flow_decidingGoalBannerPlaysBeforeMatchOver`、`settleDecidedMatch_returnsFalseBeforeDecided`、`startMatch_locksDifficultyForSettlement`、`applySettings_midMatchDefersRuleChanges`、`applySettings_midMatchAppliesRinkThemeImmediately`、`applySettings_readyClearsStalePending`、`startMatch_resetsTransientState`、`cornerPump_paddlePress_doesNotRunawayAndEscapes`、`straightWallPress_doesNotTriggerCornerEscape`、`reduceMotion_freezesDecorativeTimeOnly`、`resize_illegalSizeEarlyReturn` |
| `HockeySnapshotTest` | `saveRestore_keepsRunningState`、`restore_beforeResize_rebuildsTableFromSavedCanvas`、`saveRestore_keepsPaddleScaleBothFalse`、`restore_legacySnapshotWithoutTrailingFields_defaultsBothTrue`、`restore_afterCanvasChange_recomputesRadiiAndClampsPositions`、`restore_oversizedSavedRadii_doesNotThrowOrDropSnapshot`、`restore_corruptPayload_returnsFalseWithoutCrash`、`restore_resetsAiTargetToRestoredPosition`、`restore_keepsMatchDifficultyWhenChangedMidMatch`、`saveRestore_keepsPendingRulesForNextMatch`、`restore_brokenBodyGroups_failureKeepsExistingPendingRulesAndTheme`、`restore_legacySnapshot_hasNoPendingRules`、`saveRestore_keepsRinkTheme`、`restore_legacySnapshotFoldsPendingRinkTheme`、`restore_legacySnapshot_defaultsRinkThemeIce`、`clear_afterMatchOver_thenRestoreFalse` |
| `HockeyLayerKeyTest` | `identicalKey_reusable`、`tableGeometryShift_beyondTolerance_notReusable`、`tableGeometryShift_withinOnePixel_reusable`、`bitmapSize_withinResizeTolerance_reusable`、`goalWidthChange_beyondTolerance_notReusable`、`styleOrDayChange_notReusable`、`rinkThemeChange_notReusable` |
| `HockeyRinkThemeTest` | `fromId_roundTripsAllStableIds`、`fromId_invalidNullOrDifferentCase_fallsBackToIce`、`friction_increasesFromIceToConcrete`、`displacementAfterTwoSeconds_orderedSlipperyFirst`、`gameTick_usesActiveRinkThemeFriction` |
| `HockeyLeaderboardEntryTest` | `winEntry_isNetGoalsWithGoalsAgainstTieBreak`、`lossEntry_keepsNegativeNetAndFullGoalsAgainst`、`shutoutWin_hasZeroTieBreakAndPositiveRank` |
| `HockeyCareerTest` | `advance_updatesGoalsShutoutStreakAndDifficultyWins`、`advance_threeWinsUnlocksStreakAchievement`、`achievements_unlockFromDerivedStats`、`saveLoad_roundTripsStatsAndAchievements`、`clearStats_restoresEmptyProgress` |

---

## 8. 已知取舍与后续演进

| 取舍 | 现状与代价 |
|------|-----------|
| 物理走固定 3 子步而非 TOI | 实现简单、确定性好；极高速贴柱/贴挡板仍靠碰撞后位置纠正，靠限速与回归测试兜底 |
| 门柱视觉/判定统一 | 渲染直接调用 `HockeyLogic.goalPostRadius`，代价是几何公式变动必须同时满足画面与物理两侧测试 |
| 场地主题只改摩擦与静态外观 | 几何完全一致，切换不会影响平衡；代价是 4 档主题更像「皮肤」，对玩法差异有限 |
| 主题局中即时生效、尺寸下一局生效 | 皮肤切换零等待；尺寸类改动延迟避免局中球桌突变，代价是设置弹窗需要 `displayXxx()` 双轨展示 |
| AI 手感优先「不乌龙、不死锁」 | 低难度保留跟球逻辑，高难度才有智能防守与预判；代价是难度跨越时行为差异明显（`predict >= 0.5` 阈值） |
| 门线解围限速 + 墙角限速 | 解围/解脱不会变成大力抽射；代价是极端贴门球可能被扫得较慢，观感不够「爽」 |
| 静态层缓存最多 2 张、精灵 3 张 | 窗口拖动/主题切换有缓存命中；退出即 `clearHockeyRenderCaches` 释放，代价是二次进入需重新烘焙 |
| 快照解析-提交两段式 | 坏档不污染内存；代价是解析代码较长，新增字段必须同时更新编解码与旧档兼容回退 |
| 净胜榜单保留负分条目 | `retainRecent = true` 保留最近记录，只在展示层隐藏非正净胜名次；代价是存储不被高分淘汰 |
| 帧循环收口 `:game:common` | 各游戏共享 dt 钳制/后台停帧；代价是节流与暂停公式只能整体回归 |

后续演进方向：门柱/挡板碰撞可引入扫掠检测替代「位移 + 分离」；AI 可以复用 `goalLineInterceptX` 做传球/反弹线路规划；主题可扩展到几何微差（库边弹性）而不仅是摩擦；快照可抽出版本号字段以便未来主动迁移。

*本文档配套代码见 `game/hockey/src/main/java/com/xiaoxiong/weather/ui/game/`，测试见同模块 `src/test/java/.../ui/game/`。*
