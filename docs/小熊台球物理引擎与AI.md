# 小熊台球：物理引擎与 AI 技术说明

> 对应模块：`:game:pool`（小熊台球）
> 核心文件：`PoolModel.kt`（几何/常量）、`PoolPhysics.kt`（物理内核）、`PoolRules.kt`（规则引擎）、`PoolGame.kt`（对局状态机/回放）、`PoolSnapshot.kt`（快照编解码）、`PoolAi.kt`（AI）、`PoolRender.kt`（静态层入口/粒子特效）、`PoolTableStatic.kt`（台桌静态绘制/台呢主题）、`PoolBalls.kt`（球体绘制）、`PoolAim.kt`（瞄准辅助线）、`PoolWeather.kt`（天气氛围）、`PoolRenderCache.kt`（图层/渐变缓存与调色板）、`PoolHud.kt`（HUD）、`PoolDialogs.kt`（设置与结算弹窗）、`PoolPrefsStore.kt`（prefs/进度/排行榜）、`BearPoolScreen.kt`（帧循环与装配接线）
> 摘要：本文说明小熊台球如何在「纯 Kotlin + Compose Canvas」下实现一套确定性台球模拟：600Hz 固定步长与 TOI 连续碰撞、台呢摩擦/旋转/塞/弧线/跳球、与绘制一致的袋口捕获与钳口几何，以及其上的八球/九球规则引擎、带真实物理推演与预算控制的 AI、可回放可续玩的对局工程。

---

## 1. 分层与坐标系

代码刻意分为纯逻辑与表现两层，纯逻辑层不依赖 Compose/Android，可直接用普通 JUnit 验证。

| 层 | 核心文件 | 职责 |
|----|----------|------|
| 几何与常量 | `PoolModel.kt` | `PoolConfig` 常量、`PoolBall` 刚体、`PoolTable.standard()` 球桌/袋口/库边、`PoolRack` 摆球、`PoolMath` 角度工具 |
| 物理内核 | `PoolPhysics.kt` | 固定步长积分、摩擦、TOI 碰撞、袋口捕获、瞄准预测 |
| 规则引擎 | `PoolRules.kt` | 以 `PoolShotOutcome` 为输入的纯函数结算（八球/九球/练习/关卡） |
| 对局状态机 | `PoolGame.kt` | 回合与自由球、输入瞄准、杆法、结算、回放 |
| 快照编解码 | `PoolSnapshot.kt` | 快照模型与 `PoolSnapshotCodec` 编解码/版本校验 |
| AI | `PoolAi.kt` | 候选生成 + 真实物理推演验证 + 走位/安全球/一库解球 |
| 渲染入口 | `PoolRender.kt` | 静态层缓存查询/回退与对局粒子特效 |
| 台桌静态层 | `PoolTableStatic.kt` | 台桌/袋口烘焙、台呢主题与织纹 |
| 球体与辅助线 | `PoolBalls.kt`、`PoolAim.kt` | 球体渐变/贴花、瞄准辅助线与杆身 |
| 天气氛围 | `PoolWeather.kt` | 雨/雪/雾/阳光与云层 |
| 渲染缓存 | `PoolRenderCache.kt` | 静态图层、渐变与调色板 |
| 界面装配 | `BearPoolScreen.kt` | 帧循环与对局装配接线 |
| HUD | `PoolHud.kt` | 记分板、杆法控件、回放/历史浮层 |
| 弹窗 | `PoolDialogs.kt` | 设置、教学、关卡/每日、成就弹窗 |
| 进度与榜单 | `PoolPrefsStore.kt` | prefs、进度、回放存取与排行榜 |

坐标系为俯视：X 向右、Y 向下，单位为米；球桌长轴沿 Y（竖屏友好），左上角为 `(0, 0)`。屏幕输入经 `PoolGame.toTableX/toTableY` 映射到台面空间，渲染再经 `toCanvasX/toCanvasY` 映射回画布。

### 1.1 常量表（`PoolConfig`）

| 常量 | 值 | 说明 |
|------|-----|------|
| `PLAY_WIDTH` / `PLAY_HEIGHT` | `1.27` / `2.54` m | 9 尺台面（1 英寸 = 25.4mm） |
| `BALL_RADIUS` | `0.028575` m | 标准美式台球直径 57.15mm |
| `BALL_MASS` | `0.17` kg | 球质量（等质量双球） |
| `GRAVITY` | `9.80665` | 重力加速度 |
| `MU_SLIDE` / `MU_ROLL` / `MU_SPIN` | `0.20` / `0.05` / `0.022` | 打滑摩擦 / 滚动阻力 / 自旋衰减 |
| `NAP_ANISOTROPY` | `0.15` | 台呢顺逆毛各向异性（+Y 为顺毛） |
| `CUSHION_RESTITUTION` / `CUSHION_RESTITUTION_FAST_LOSS` / `CUSHION_FRICTION` | `0.78` / `0.12` / `0.20` | 库边法向弹性 / 高速衰减 / 切向摩擦 |
| `BALL_RESTITUTION` / `BALL_FRICTION` | `0.95` / `0.06` | 球-球法向弹性、切向摩擦（决定甩球） |
| `SIM_DT` | `1/600` s | 物理固定步长 |
| `MAX_TOI_ITERATIONS` | `32` | 单步内 TOI 求解迭代上限 |
| `MAX_SPEED` / `MIN_SHOT_SPEED` | `12.0` / `0.7` m/s | 速度上限、力度 0 的白球初速 |
| `SLEEP_SPEED` / `SLEEP_SPIN` | `0.0055` / `0.06` | 判定静止的线速度 / 角速度阈值 |
| `CORNER_MOUTH` / `SIDE_MOUTH` | `3.2r` / `3.4r` | 角袋 / 中袋口宽（`r` 为球半径） |
| `CORNER_POCKET_R` / `SIDE_POCKET_R` | `2.9r` / `2.4r` | 角袋 / 中袋捕获半径（与绘制洞口同量级） |
| `JAW_CORNER_CHAMFER` / `JAW_SIDE_DEPTH` / `JAW_SIDE_INSET` | `0.007` / `0.018` / `0.002` m | 角袋倒角 / 中袋漏斗进深 / 中袋收口量 |
| `CURVE_DECAY_RATE` | `3` /s | 弧线横向加速度衰减速率 |
| `JUMP_ELEVATION_MIN` / `JUMP_POWER_MIN` / `JUMP_TAKEOFF` | `0.7` / `0.7` / `0.16` | 跳球阈值与起跳速度系数 |
| `AIRBORNE_EPS` / `LANDING_KEEP` | `1e-4` / `0.985` | 腾空高度阈值 / 落地水平速度保留 |

`shotSpeedMps(power)` 统一力度到速度的映射：`0.7 + power·(12 − 0.7)`，真实出杆与 AI 推演共用，避免两套标定。

---

## 2. 物理内核

### 2.1 固定步长与台呢摩擦

`PoolPhysics.step(balls, table, dt = PoolConfig.SIM_DT, listener)` 是唯一推进入口。`PoolGame.tickShooting` 用累加器把帧时间切成 600Hz 子步，单帧最多 60 步、累加器钳制到 `SIM_DT × 120`，掉帧时表现为「慢动作」而不是穿模。

台呢摩擦 `applyClothFriction` 用接触点速度 `u = v + ω × (-r ẑ)` 区分两种状态：

- `|u| > 1e-4`：打滑。库仑摩擦冲量一次把接触点速度解到零（受 `μ_slide·m·g·dt` 上限约束，避免阈值附近过冲抖动），球体切向等效质量取 `2/7 m`；
- 否则：纯滚动。按 `MU_ROLL·g·dt` 减速并强制维持 `ω = v/r`。

顺逆毛 `nap` 按运动方向与 +Y 的夹角乘到摩擦系数上（下限 0.5），+Y 顺毛滚得更远。竖直自旋 `wz` 单独按 `2.5·MU_SPIN·g/r·dt` 衰减。`clampSpeed` 把线速度钳到 `MAX_SPEED`、滚动角速度钳到 `MAX_SPEED/r`。

### 2.2 TOI 连续碰撞

单步内的推进是一个「找最早碰撞 → 推进到接触点 → 解算 → 继续」的循环，上限 `MAX_TOI_ITERATIONS`：

```kotlin
while (remaining > 1e-7f && iterations < PoolConfig.MAX_TOI_ITERATIONS) {
    val hit = findEarliestHit(balls, table, remaining)
    if (hit == null) { advance(balls, remaining); remaining = 0f }
    else { advance(balls, hit.time); remaining -= hit.time; /* 解算 hit */ }
}
```

- `ballBallToi`：解相对运动的二次方程。已重叠时只有「仍在相互接近」才返回 0，静止相切（三角架摆放）不会占满迭代；
- `sweptCircleSegment`：扫掠圆 vs 线段，同时检测线段端点 `considerEndpoint`，返回 `(time, normal)`；
- `firstRailHit`：对外暴露同一条扫掠逻辑给预测层复用，保证「物理真撞到的库/钳口」和「辅助线预测的库/钳口」是同一个。

**法线取接触时刻的球心连线**，而不是 TOI 求解前的旧方向：

```kotlin
advance(balls, hit.time)   // 先推进到接触点
val nx = dx / dist         // 再按推进后的球心连线重算
```

`resolveBallBall` 的注释记录了历史 bug：用 TOI 前法线时，切球偏差可达数十度，会出现「瞄准线对准袋口却打不进」。现在只在两球心几乎重合时才回退 `fallbackNx/fallbackNy`。

### 2.3 冲量、旋转与杆法

- `resolveRail`：法向弹性 `cushionRestitution(|vn|) = (0.78 − 0.12·(|vn|/6).coerceIn(0,1))`，下限 `0.45`；切向摩擦叠加侧旋改变反弹角，冲量经 `applyTangentialSpin` 只改 `wz`（τ = −r·j·ẑ）；
- `resolveBallBall`：法向 `j = −(1+0.95)·vn/(1/mₐ+1/m_b)`；切向摩擦上限 `0.06·|j|`，双球切向等效质量 `m/7`，产生甩球与旋转传递；位置纠偏只做少量重叠分离；
- 杆法统一在 `PoolGame.applyShotToCue` 与 `PoolAi.simulate` 两处实现同一套公式：侧旋偏杆 `squirt = (-side)·0.85°·(0.35 + 0.65·power)`；跟杆/缩杆写 `wx/wy = ∓v/r·k`；竖直自旋 `wz = side·(12·power + 4)`；弧线 `curve = side·elevation·3.2`（水平杆不产生弧线），由 `applyCurve` 施加垂直于速度的横向加速度并随 `CURVE_DECAY_RATE` 衰减；
- 跳球：抬杆 ≥ `JUMP_ELEVATION_MIN` 且力度 ≥ `JUMP_POWER_MIN` 时给竖直初速 `GRAVITY·JUMP_TAKEOFF·elevation·(0.5 + power)`，`integrateVertical` 做抛物线积分与落地前滚修正；腾空高于球半径的球不参与球-球/球-库碰撞。

### 2.4 袋口几何、钳口与预测一致性

`PoolTable.standard()` 生成 4 个角袋 + 左右长库中点的 2 个中袋，以及避开袋口的主库线段：

- **捕获**：`captureInPocket` 判定球心到袋心距离 ≤ `pocketCaptureRadius`。角袋为 `pocket.radius − 0.05r`、中袋为 `pocket.radius × 0.85`，与绘制洞口同量级，因此「贴库滚过洞口」会被自然捕获。腾空高于球半径时从袋口上方飞过，不判落袋；
- **钳口**：`PoolTable.jaws` 给每个袋口生成两段短斜边——角袋沿 45° 向袋内倒角 `JAW_CORNER_CHAMFER`，中袋向台面外延伸成漏斗（`JAW_SIDE_DEPTH`/`JAW_SIDE_INSET`），不侵入台面，避免贴库滚过时误碰。`PoolPocketGeometry.isPocketJaw` 以「长度 ≤ 2r 且两端点都在袋口半径的 1.5 倍范围内」从 `table.rails` 中挑出 12 段钳口，渲染层据此画钳口而非主库；
- **预测一致性**：`predictObjectPath` 与 `pocketCaptureRadius` 完全共用口径，并对贴边路径取保守系数 `POCKET_PREDICTION_MARGIN = 0.82`；库边距离同时考虑中心射线 `raycastRail` 与球半径扫掠 `firstRailHit` 的较小值；浅擦钳口时 `rescuePocketAfterRail` 沿反射方向做一次短程二次判定（最远 `POCKET_RESCUE_DISTANCE = 0.35m`，命中点须在袋口边缘 `POCKET_RESCUE_RANGE = 6r` 内），修复「蹭口进袋被预测成碰库」与「擦边被谎报进袋」两类偏差；
- **兜底**：极端角度逸出台面的球（超出 `4r`）按落袋处理并标记 `pocketIndex`；跳球飞出边界（`jumpMargin = 1.5r`）同理会回调 `onPocket`，避免台外无限滑行。

### 2.5 对渲染/AI 暴露的预测接口

| 接口 | 用途 |
|------|------|
| `predictFirstContact` | 白球沿瞄角最先碰到的球与命中点（幽灵球） |
| `predictObjectPath` | 目标球路径：返回 `ObjectPath(endX, endY, distance, pocket, pocketIndex, blocker, railDistance)` |
| `raycastRail` / `railInwardNormal` | 瞄准线被库遮挡判断 / 库边内侧法线 |
| `isLegalCuePlacement` | 自由球摆位合法性（玩家 `tryPlaceCue` 与 AI `placeCueBall` 共用） |
| `simulateUntilSettled` | 连续推演到静止，供测试与 AI 使用 |

---

## 3. 规则引擎

`PoolRules.resolve(state, outcome, remainingAfter, isBreak)` 按 `PoolMode` 分派，输入是 `PoolGame` 在物理过程中采集的 `PoolShotOutcome`（首碰类型/球号、接触后是否碰库、落袋列表、白球是否落袋、击球前是否已清台等），输出 `PoolMatchUpdate(state, foul, ballInHand, frameWinner, message, rerack)`。

### 3.1 八球

- 开球误进 8（且白球未落袋）→ `BREAK_RERACK` 重摆，不计犯规；
- 8 号落袋 → 直接判胜负：`firstContact == EIGHT && shooterGroupClearedBefore && !cuePotted` 为胜，否则负；
- `legalFirst`：开放台任意非 8；本方已清台时必须 8；否则必须为本方花色；`railOk = railAfterContact || potted.isNotEmpty()`；
- `foul = cuePotted || !legalFirst || !railOk` → 对手自由球；提示按「白球落袋 → 先碰错球 → 空杆 → 无库无袋」优先级分类；
- 开放台首个合法进球按第一颗落袋的非 8 球定组（`GROUP_ASSIGNED`），之后本方进球继续回合（`CONTINUE`），否则换人（`TURN_PASS`）。

### 3.2 九球

- 首碰必须是台面剩余编号最小的球（`lowestBefore == firstContactNumber`）；
- 接触后需有球碰库或有球落袋，否则犯规；
- 9 号落袋：首碰合法且白球未落袋 → `NINE_WIN`；否则 `NINE_LOSE`（简化处理，对手直接获胜）；
- 合法进球（含组合球）继续回合，无进球换人；犯规提示区分「碰对了最低号但无库无袋」（`FOUL_NO_RAIL`）与「先碰错球」（`FOUL_WRONG_BALL`）。

### 3.3 练习、关卡与教学

练习与关卡共用 `resolvePractice`：不换人、无犯规、白球落袋自动回台（`respawnCue`），台面清空（`remainingAfter.total == 0`）即 `PRACTICE_CLEAR` 并结束。关卡/每日挑战由 `PoolChallenges` 提供 12 关静态布局与按 epoch day 确定性生成的每日挑战，计分统一为 `max(10, par×100 − shots×8)`；教学由 `PoolTutorial` 的 `Goal`（如 `DRAW_BACK`、`BANK_SHOT`）判定通过。

### 3.4 `PoolMessage` 分类

| 消息 | 触发条件 |
|------|----------|
| `GROUP_ASSIGNED` / `CONTINUE` / `TURN_PASS` | 定组 / 本方进球续杆 / 合法无进球换人 |
| `FOUL_CUE` | 白球落袋 |
| `FOUL_MISS` | 空杆 |
| `FOUL_WRONG_BALL` | 首碰不合法（八球先碰错花色、开放台先碰 8；九球先碰错号） |
| `FOUL_NO_RAIL` | 首碰合法但接触后无球碰库且无球落袋 |
| `BREAK_RERACK` / `EIGHT_WIN` / `EIGHT_LOSE` | 开球误进 8 / 合法打进 8 / 非法打进 8 |
| `NINE_WIN` / `NINE_LOSE` | 九球 9 号合法 / 非法落袋 |
| `PRACTICE_CLEAR` | 练习/关卡清台 |

---

## 4. AI

AI 的主流程是「解析法枚举候选 → 按分数排序 → 对最有希望的候选做真实物理推演 → 在干净进球中按走位加权选择」，全部随机数来自传入的 `Random`，保证同一 seed 复现同一杆。

### 4.1 候选生成

`eligibleTargets` 按模式筛出可打目标；`evaluateCandidate` 对「目标球 × 袋口」：

1. 幽灵球位 `ghost = ball + 2r·unit(ball → pocket)`，要求台内；
2. 白球到幽灵球距离 `≥ 2.2r`，切角 `≤ 1.35` rad（否则直接丢弃）；
3. `pathClear` 检查白球→幽灵、目标球→袋口两条路径的净空（球半径 + 余量）；
4. 评分 `score = cutFactor / (1 + 1.6·cueDistance + 1.1·ballToPocket)`，其中 `cutFactor = 1 − (cut/1.35)·0.7`，`ballToPocket` 按捕获圈边缘算。

`plan` 先抽定 `aimErrRad` 与 `powerErr`，之后短名单取 `difficulty.candidates`，推演与最终出杆都用含误差的角度/力度，保证「推演干净」等价于「实打干净」。`estimatePower = (0.28 + (cueDistance + ballToPocket)·0.55 + cut/1.35·0.22)`，钳制到 `0.15..1`。

### 4.2 真实物理推演与 `SimBudget`

`simulate` 克隆整桌球，用比游戏更粗的 `SIM_DT = 1/180`、最长 `SIM_MAX_SECONDS = 2`（`SIM_MAX_STEPS = 360` 步）推演，出杆参数与 `PoolGame.applyShotToCue` 一致（squirt / 高低杆 / 侧旋）。结果 `SimResult` 包含 `targetPotted`、`cueScratched`、`truncated`、`budgetExhausted`、`railAfterContact` 与推演后的整桌球，用于判断走位。

单次 `plan` 的推演步数由 `SimBudget` 统一发放：

| 阶段 | 默认配额 | 用途 |
|------|----------|------|
| `VERIFY` | 总预算 × 70% | 短名单干净进球验证与补验 |
| `KICK` | 总预算 × 20% | 一库解球候选推演 |
| `SAFETY` | 剩余全部 | 安全球与兜底补验 |

`tryConsume(stage)` 按阶段独立限流，`exhausted` 后 `simulate` 返回 `budgetExhausted`——调用方必须停止该阶段而不是把「未验证」当成「没进球」。每档难度的总预算 `simulationBudget` 为 `NOVICE 360 / EASY 600 / NORMAL、HARD、MASTER 1500`，可经 `worstCaseSimulationSteps` 在测试里回归。

### 4.3 走位、安全球与一库解球

- **走位评估**：仅 `HARD/MASTER` 启用 `positionCandidates`（3/4 个已验证候选）。把推演后的白球停点 + 剩余台面作为下一杆局面，复用 `evaluateCandidate` 得到 `nextShotQuality`，总分 `score + positionPlay × pos`；
- **验证失败兜底**：先用 VERIFY 剩余额度补验短名单其它候选，再用 `SOFTER_POWER_FACTORS = [0.8, 0.62, 0.45]` 降力度重试（跟袋/打薄时轻推仍可能进球），仍无干净进球则走 `safetyPlan`；`safestCandidate` 按 `cueScratchRank` 排序（进球且不落袋 > 接触后有库/袋 > 无库无袋 > 未推演 > 已确认白球落袋），并用 `cuePocketClearance` 优先白球远离袋口；
- **安全球**：固定 `SAFETY_POWER = 0.32`，对最多 `SAFETY_MAX_TARGETS = 5` 个目标各尝试 `[0, ±0.045]` rad 三个瞄点，总推演不超过 `SAFETY_MAX_SIMS = 6`；过滤白球落袋、首碰错球与「接触后无库无袋」，选 `opponentOpportunity` 最小的一杆；找不到时退化为最近目标轻推，并给后续兜底留 `SAFETY_FALLBACK_RESERVE = 240` 步；
- **一库解球**：直接路径全被挡时 `kickPlan` 用 `kickCandidates` 对 6 段主库（`mainRails`，排除钳口）做镜像点几何，按「库 → 目标」轮转取 `KICK_MAX_SHOTS = 18` 个候选，只有推演真正进球且首碰正确才采用；
- **自由球摆位**：`placeCueBall` 枚举「目标球 × 袋口」的直球摆位，用 `placementQuality`（假想白球评分）取最优合法点，全被挡时 `findLegalCueSpot` 网格扫描兜底，合法性始终走 `isLegalCuePlacement`。

### 4.4 难度标定

| 难度 | `aimErrorDeg` | `powerError` | `candidates` | `verifyShots` | `positionPlay` | `positionCandidates` |
|------|---------------|--------------|--------------|---------------|----------------|----------------------|
| `NOVICE` | 3.2 | 0.20 | 4 | 0 | 0 | 0 |
| `EASY` | 2.2 | 0.14 | 6 | 0 | 0 | 0 |
| `NORMAL` | 1.35 | 0.08 | 10 | 2 | 0 | 0 |
| `HARD` | 0.5 | 0.045 | 12 | 4 | 0.40 | 3 |
| `MASTER` | 0.15 | 0.02 | 16 | 6 | 0.70 | 4 |

难度不是「拍脑袋」而是有回归测试的：`difficultyHitRates_fallInCalibratedBands` 用 6 袋 × 4 距离 × 4 切角 × 2 白球距离约 190 个固定场景、固定 `CALIBRATION_SEED = 20_250_913` 做蒙特卡洛，按与真实出杆一致的参数执行 plan 并检验命中率落在标定区间（0.45–0.65 / 0.60–0.75 / 0.75–0.87 / 0.88–1.0 / 0.93–1.0）；`perfectBot_fixedSeedMatch_keepsFoulsLow` 则让 MASTER 打满 20 杆并要求犯规 ≤ 4。

---

## 5. 对局工程

### 5.1 状态机、回合与自由球

`PoolGame.Phase` 为 `READY / AIMING / SHOOTING / FRAME_OVER / MATCH_OVER`。`finalizeShot` 在物理静止后采集 `PoolShotOutcome` 调用规则引擎，再处理：教学达成、rerack、本局胜负、白球回位（`respawnCue`：优先开球点，被占则环形搜索空位）、自由球（`ballInHand` + `placingCue`，玩家 `tryPlaceCue`，AI `placeCueBall`），最后 `scheduleAiIfNeeded`（AI 延迟 `aiTimer = 0.9s` 出杆）。

`matchDifficulty` 在本场 `startMatch` 时锁定，局中改难度只影响之后的 AI 行为，结算与成就仍按锁定值记账（与冰球语义一致），防止打到一半切 `MASTER` 蹭成就。

### 5.2 回放

回放只记录意图、不记录物理结果：`RecordedShot(angle, power, spinSide, spinVertical, elevation, cueX, cueY)`。`startReplay` 保存当前快照并用 `frameSeed` 重新摆球（`rackFrame(scheduleAi = false)`），`replayTick` 以 600Hz 确定性重演，逐杆 settle 后推进；`stopReplay` 用 `savedSnapshotForReplay` 还原球局。`savedResultSubmitted` 专门保存回放前的结算标记——`applySnapshot` 会把 `resultSubmitted` 复位，不还原就会在 `MATCH_OVER` 回放结束后重复入榜。新开对局/教学摆球会走 `resetReplaySession`，否则残留的回放态会把旧局恢复回来。最近一场还会以文本格式持久化到 `pool_last_replay_v2`（`PoolPrefsStore.kt` 的 `saveReplay`/`loadReplay`）供冷启动回放。

### 5.3 快照 `_v2`

进行中对局经 `PoolSnapshot.kt` 的 `PoolSnapshotCodec` 编解码后存入 `pool_snapshot_v2`：

- **版本校验**：`VERSION = 2`，旧 v1 payload（中袋位置与 `pocketIndex` 含义已变）一律拒绝，避免续玩时球落入错误袋口；载入/清档时顺便清理 `pool_snapshot_v1`、`pool_last_replay_v1` 孤儿 key；
- **保存时机**：仅 `AIMING / SHOOTING / FRAME_OVER` 落盘（`SHOOTING` 会保存飞行中的球与一杆采集中间态，恢复后继续飞并正常结算）；`READY / MATCH_OVER` 视为无对局直接清档；回放中 `saveSnapshot` 直接跳过，不用回放残局覆盖续玩快照；
- **`raceTo` 钳制**：解码时经 `clampRaceTo` 收敛到 `1..15`，越界旧档不会引入非法赛制；
- **难度兼容**：`matchDiff` 为可选字段，旧档缺失时回退为当前难度；
- **幂等**：`applySnapshot` 全面重置瞬时状态（累加器、拖拽、粒子、回放态、AI 计时），`resultSubmitted = false`；快照与 prefs 赛制不一致时 `pendingRulesAfterSnapshotRestore` 重建「下一场生效」的暂存，避免设置界面显示旧值。

快照生命周期（进入恢复、`ON_STOP`/`onDispose` 兜底落盘、主动退出抑制、`MATCH_OVER` 清档）已统一由 `:game:common` 的 `GameSnapshotLifecycle`（`GameSnapshotSession.kt`）驱动；公共层另有 `GameCards.kt`（公共 READY/结算卡与文案）与 `GameRenderPrimitives.kt`（`BakedLayerLru`/`strokeOf`/`gameHash01` 渲染原语）供各游戏复用。

### 5.4 赛制、自定义局数与主题

- `POOL_RACE_TO_MIN = 1`、`POOL_RACE_TO_MAX = 15`，所有入口（设置保存 / prefs 读取 / 快照解码）都走 `clampRaceTo`；设置弹窗有 1/2/3 快捷档与 1–15 自定义；
- 局中改赛制/模式只 `stageRuleChange` 暂存，下一场 `startMatch` 生效，`displayRaceTo/displayMode` 负责展示暂存的「下一场值」；暂停菜单「新一场」调用 `startFreshMatch`，从 prefs 同步赛制/模式并清暂存，避免快照恢复后的旧内存值覆盖自定义设置；
- 台呢主题 `clothTheme` 取 `0..3`（经典湖蓝/蜂蜜棕/午夜墨绿/樱花粉），`PoolPrefsStore.kt` 的 `clampClothTheme`/`loadClothTheme`/`saveClothTheme` 统一钳制与持久化（台呢色函数与静态层绘制在 `PoolTableStatic.kt`）。

---

## 6. 排行榜

对局类榜单按「净胜」而不是「赢下的总局数」排序：

- `PoolPrefsStore.kt` 的 `poolRaceScore(playerFrames, aiFrames) = playerFrames − aiFrames`；
- `poolLeaderboardEntry`：对局类 `score = 净胜`、`tieBreak = 失局`（同净胜时 3:0 优于 4:1）；练习类 `score = practiceScore()`，无 `tieBreak`；
- 通用排序 `leaderboardEntryOrder`：`score` 降序 → `tieBreak` 升序 → 时间新者靠前；
- 名次展示：`leaderboardDisplayRank(score, rank)` 只在净胜 > 0 时返回名次，负/零净胜返回 0——但对局类榜单 `retainRecent = true`，保留最近 20 条，惨败条目不会被挤出（练习榜仍按高分淘汰）；
- **旧 v1 榜延迟清理**：`POOL_LEGACY_LEADERBOARD_KEYS`（`leaderboard_pool_*_v1` 与旧 `best_pool_*`）不随版本升级立刻删除，而是在用户首次成功进榜（`GameLeaderboardStore.submit` 返回非空排名）或主动清榜时 `purgeLegacyKeys`，避免一进游戏旧记录就永久消失。每日挑战榜（`leaderboard_pool_daily_*`）与每日最佳是杆数口径，不在清理列表内。

---

## 7. 渲染

渲染的目标是「静态的不重复画、动态的少分配」。台桌空间（米）经 `PoolGame` 的视图变换映射到画布。

- **静态层烘焙**：`PoolRender.kt` 的 `drawPoolTable` 先问 `PoolRenderCache.kt` 的 `PoolTableLayerCache.bitmap`。缓存键 `PoolLayerKey` 包含位图尺寸、视图变换、球桌几何、天气样式、昼夜与 `clothTheme`；`poolLayerKeyReusable` 要求主题/样式/几何严格一致、尺寸容差 `POOL_LAYER_RESIZE_TOLERANCE_PX = 24px`，主题不同必须重建（否则切换台呢会错误复用旧静态层）。缓存最多 `MAX_ENTRIES = 2` 张做 LRU，`clearPoolTableLayerCache` 在退出对局时释放全屏 Bitmap；
- **球体渐变缓存**：`PoolRenderCache.kt` 的 `BrushCache` 是固定容量的小型 LRU，`sphereBaseBrushes` / `sphereShadeBrushes` / `sphereRimBrushes` / `sphereAmbientBrushes` / `sphereHighlightBrushes` 缓存球体基础/暗部/菲涅尔/环境/高光径向渐变（key 为半径 + 颜色），绘制时 `translate` 到球心复用；`PoolBalls.kt` 中花色球 `drawStripeBand` 按 `PoolBall.markingNormal()` 的投影角旋转贴花，数字用复用的 `numberPaint`；
- **辅助线**（`PoolAim.kt`）：`drawPoolAim` 按 `Aids.FULL/GHOST/OFF` 分级。`drawAimAssist` 用 `predictFirstContact` 画幽灵球与首碰线，用 `predictObjectPath` 画目标球路径：预测进袋画绿色实线 + 袋口高亮圈（`potLineColor`/`potRingColor`），首碰非法时幽灵球、引导线与目标线统一切换警示色 `aimWarnColor`；`cueSeparationDirection` 给出白球碰后切线方向，若该方向的白球路径预测终点是袋口，则画红圈预警「这一杆白球会落袋」；无有效目标时退化为库边反射线；
- **性能与 `reduceMotion`**：`reduceMotion` 冻结时间脉冲、关闭球体拖影与天气粒子（`spawnBurst` 直接返回）、落袋动画直达终点，但物理推进不受影响；粒子总数超过 220 时停止新增。

---

## 8. 测试与回归

| 测试文件 | 关键用例 |
|----------|----------|
| `PoolPhysicsTest` | `noTunneling_highSpeedBreakHitsRack`、`railReflection_reversesNormalVelocityAndStaysInside`、`pocketCapture_ballAtCornerPocketIsPotted`、`railRollOverSidePocket_isPotted`、`passingBesideSideMouth_isNotPotted`、`escapeNet_marksPocketIndexAndNotifies`、`determinism_sameInputSameOutput`、`predictFirstContact_findsStraightAheadBall` |
| `PoolPocketJawTest` | `everyPocket_hasTwoJawSegments`、`pocketEntrances_areWiderThanBallDiameter`、`pocketCapture_acceptsBallsFromMultipleAngles`、`randomBreakShots_neverEscapeTableUnpotted`、`touchedRestingBalls_doNotLetFastBallTunnelThroughRail` |
| `PoolPocketAcceptanceTest` | `straightShots_potOnEveryPocket`、`cutShots_potOnEveryPocket`、`guidePrediction_matchesPocket`、`physicallyPottedGhostBallShots_arePredictedAsPocket`、`predictedPocketPaths_actuallyPotInRealPhysics` |
| `PoolPocketMouthTest` | 角袋/中袋鼻点位置、口线位于袋心与台心之间、外法线方向、`jaws_areShortSegmentsNearPocketMouths`、`mainCushionRails_areNotTreatedAsJaws` |
| `PoolRulesTest` | 八球分组/续杆/各类犯规/黑八胜负、`eightOnBreak_reracks`、九球最低号与 9 号胜负、`nineBall_legalFirstButNoRail_isNoRailFoul_notWrongBall`、练习与关卡清台 |
| `PoolAiTest` | `plan_findsStraightShotToBottomPocket`、`simBudget_stageQuotas_areIndependent`、`simulate_budgetExhausted_marksUnverifiedNotFailed`、`kickCandidates_coverMultipleTargetsWithinShotLimit`、`masterWorstCaseSimulation_isBounded`、`master_avoidsScratchingPotLine`、`difficultyHitRates_fallInCalibratedBands`、`perfectBot_fixedSeedMatch_keepsFoulsLow` |
| `PoolSnapshotTest` | `saveRestore_inProgressMatch_keepsBallsPhaseAndScore`、`saveRestore_shooting_resumesFlyingBallAndSettles`、`restoreSnapshot_outOfRangeRaceTo_isClamped`、`restoreSnapshot_oldGeometryVersion_rejectedAndCleared`、`restoreSnapshot_onlyLegacyV1Key_returnsFalseAndPurges`、`saveSnapshot_duringReplay_keepsExistingSnapshot` |
| `PoolGameTest` | `customRaceTo_sevenFrames_sixKeepPlaying_seventhEnds`、`raceToOne_firstFrameEndsMatch`、`raceToFifteen_fourteenthFrameDoesNotEndMatch`、`replay_isDeterministicForSameLog`、`stopReplay_afterSettledMatch_keepsResultSubmitted`、`pendingRulesAfterSnapshotRestore_differencesRebuildPendingAndClamp`、`matchDifficulty_lockKeepsAchievementAccountingStable`、`poolLeaderboardEntry_netAndTieBreakConventions`、`legacyPurgeList_doesNotTouchDailyLeaderboardKeys` |
| `PoolClothThemeTest` | `clothThemes_distinctInDayAndNight`、`clothThemes_distinctUnderRainAndSnow`、`outOfRangeTheme_fallsBackToDefaultFelt`、`coldOverlay_blendsEachThemeByExactBoundedAmount`、`prefs_corruptOrLegacyValue_isClamped` |
| `PoolLayerKeyTest` | `clothThemeChange_notReusable`、`styleOrDayChange_notReusable`、`tableGeometryShift_notReusable`、`bitmapSize_withinResizeTolerance_reusable`、`exactHit_requiresSameViewTransform` |
| `PoolProgressTest` | `customRaceToShutout_countsOncePerMatchRegardlessOfFrameCount`、`competitiveLossResetsStreak_butPracticeKeepsIt`、`practiceClear_doesNotUnlockMatchAchievements` |
| `PoolSpinTest` / `PoolJumpTest` / `PoolNapTest` | 侧旋衰减与弧线有界、跳球越过阻挡与飞出边界按落袋、顺逆毛滚动距离与确定性 |
| `PoolTutorialTest` / `PoolChallengesTest` | 教学布局合法且目标可用物理达成、12 关布局合法、每日挑战同日确定性 |

袋口相关测试刻意做「双向一致性」：既要求物理真进的球预测必须提示进袋，也要求所有提示进袋的路径按同方向推演必须真的落袋，把辅助线的可信度当成可回归的约束。

---

## 9. 已知取舍与后续演进

| 取舍 | 现状与代价 |
|------|-----------|
| AI 推演在主线程 | 用 `SimBudget` 把单次规划最坏步数限制在 1500 步（NOVICE/EASY 更低），最坏耗时可控；后续可移到后台协程或预计算以进一步降低主线程卡顿 |
| `kickPlan` 仅一库 | 只对 6 段主库做镜像点，无两库解球、无跳球/弧线解斯诺克，复杂斯诺克下仍退回安全球 |
| 袋口预测是保守启发式 | `POCKET_PREDICTION_MARGIN = 0.82` + 钳口二次救援不能保证与 600Hz 物理 100% 一致，靠双向回归测试兜底 |
| 负净胜榜单仍保留条目 | `retainRecent = true` 保留最近 20 条，只是 `leaderboardDisplayRank` 不显示名次；避免了「赢了进不了榜、输了被清空」的体验问题 |
| 快照 v2 拒绝 v1 | 旧档直接清掉不迁移（中袋位置/pocketIndex 语义已变），代价是跨版本丢续玩进度 |
| 回放只存意图 | `RecordedShot` 不含物理结果，跨版本修改物理常量会让旧回放重演结果不同；白球自由球摆位用 `cueX/cueY` 记录来保证复位 |
| 规则做了简化 | 九球 9 号非法落袋直接判负、八球犯规统一自由球、开球误进 8 重摆；AI 不模拟跳球与弧线，保证玩法与 AI 口径一致 |
| 悬空依赖外部状态 | 快照/回放依赖 `SharedPreferences`，回放文本格式为手写分隔符（未引入序列化运行时），字段少、容错读，但扩展字段需同步改编解码两侧 |

*本文档配套代码见 `game/pool/src/main/java/com/xiaoxiong/weather/ui/game/`，测试见同模块 `src/test/java/.../ui/game/`。*
