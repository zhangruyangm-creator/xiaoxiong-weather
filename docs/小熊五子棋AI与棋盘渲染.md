# 小熊五子棋：AI 与棋盘渲染技术说明

> 对应模块：`:game:gomoku`（小熊五子棋）
> 核心文件：`GomokuLogic.kt`（纯逻辑：候选/连五/评估/搜索）、`GomokuGame.kt`（对局状态机）、`GomokuBoardArt.kt`（棋盘渲染）、`BearGomokuScreen.kt`（帧循环与装配接线）、`BearGomokuEntry.kt` / `BearGomokuEntryProvider.kt`（ServiceLoader 入口）
> 测试文件：`GomokuLogicTest`、`BearGomokuEntryProviderTest`
> 摘要：本文说明小熊五子棋（15×15 连五）如何在「纯 Kotlin + Compose Canvas」下实现：候选点裁剪、全局杀着/必防捷径、按「连子数 × 开度」打分的评估函数、带走法排序的 α-β 搜索、后台线程搜索与主线程校验回写、七档难度（顶档 5 层 + VCF）与确定性强度回归测试。

---

## 1. 背景与玩法

15×15 棋盘，玩家执**黑先手**，AI 执**白**，先连成五子（横/竖/斜，≥5 即胜，自由规则）者胜；棋盘下满为和。**任意空点均可落子**（不限制在已有棋子附近），绿色圆点仅为推荐落点提示。

棋局三要素：`GomokuPhase`（READY / PLAYING / OVER）、`turn`、`outcome`（BLACK_WIN / WHITE_WIN / DRAW / NONE）。玩家成绩：胜 100、和 50、负 0。

---

## 2. 棋盘模型与候选点裁剪（`GomokuLogic.kt`）

棋盘为扁平 `IntArray(225)`，`index = row * 15 + col`。

| 函数 | 职责 |
|------|------|
| `gomokuInitialBoard()` | 空盘 |
| `gomokuCandidateMoves(board, radius)` | **候选点裁剪**：已有棋子切比雪夫距离 ≤ `radius`（默认 2，顶档搜索用 3）范围内的空点；空盘只给中心；极端情况下回退为全盘空点 |
| `gomokuWinAt(board, index, color)` | 以落子点为中心，四个轴向（横/竖/两斜）各向两侧数同色，任一方向 ≥5 即胜 |
| `gomokuWouldWin(board, index, color)` | 试落一子判断是否成五（落/收复原状） |
| `gomokuFindImmediateWin(board, color)` | **全盘扫描**一步成五的点（含候选集外的点） |

候选裁剪 + 全局成五扫描是这套 AI 的两个关键工程点：前者把搜索分支压到局部（15×15→每步约几十个候选），后者保证「能赢就赢、必挡则挡」不受候选集限制。

---

## 3. 评估函数（`gomokuMoveHeuristic` / `gomokuEvaluateBoard`）

### 3.1 单点启发式 `gomokuMoveHeuristic(board, index, color)`

对某空点，分别从**进攻**与**防守**两个角度打分（临时落子/复原）：

- **进攻**：先判是否一步成五（`gomokuWinAt`）→ 直接给 `500_000`；否则对四个轴向用 `gomokuAnalyzeLine` 取「连子数 `count` 与开度 `openEnds`」，查 `gomokuLineScore`。
- **防守**：把该点当作对手的子落子——若对手在此成五 → 加 `400_000`；否则同样按 `gomokuLineScore` 计入对手线的威胁，再乘 `9/10` 折扣。

`gomokuLineScore(count, openEnds)`（越小越弱）：

| count | 开度 2 | 开度 1 | 开度 0 |
|-------|--------|--------|--------|
| ≥5 | 200000 | 200000 | 200000 |
| 4 | 12000 | 1200 | 60 |
| 3 | 800 | 80 | 10 |
| 2 | 40 | 8 | 2 |
| 1 | 2 | 2 | 0 |

### 3.2 全局评估 `gomokuEvaluateBoard(board, forColor)`

对**每个候选点**求和：

```kotlin
sum += gomokuMoveHeuristic(board, i, forColor)
sum -= gomokuMoveHeuristic(board, i, opponent(forColor)) * 95 / 100
```

即「本方所有候选点的机会 − 对手候选点的机会（略作折扣）」，是一个零和、简洁但有效的局面分。

### 3.3 走法排序 `gomokuRankedMoves`

按 `gomokuMoveHeuristic` 对候选点**降序排序**并取前 `top` 个。这既用于根节点挑选，也用于 minimax 内部——**让 α-β 真正剪枝**（这是五子棋能跑到 5 层的关键）。

---

## 4. 搜索（`gomokuChooseAiMove` + `gomokuMinimax`）

### 4.1 根节点决策顺序

```
1. 白方一步成五？→ 直接下（取胜）
2. 黑方一步成五？→ 直接堵（防杀）
3. 顶档（usesVcf）：VCF 连续冲四强制取胜搜索，命中即下（§4.3）
4. BEGINNER(weak)：在"较弱的一半"候选里确定性取点
5. searchDepth ≤ 0：取排序第 1
6. 否则：minimax + α-β（白方极大 / 黑方极小）
```

1、2 步的全盘扫描保证「不放过任何成五/被杀点」，即使该点不在候选集内。

### 4.2 minimax

- 深度受限、α-β 剪枝；**原地 apply/undo**（`board[move] = color` → 递归 → `board[move] = EMPTY`），无每节点棋盘复制；
- 子节点用 `gomokuRankedMoves(board, turn, topMoves)` 排序后展开；
- 白方分支若发现己方成五直接返回 `+500000`，黑方分支若成五返回 `-500000`（早停）。

### 4.3 连续冲四（VCF）强制取胜

评估搜索只能看有限层数，对「步步成四」的多步杀看不远。顶档在此之上叠加 **VCF（Victory by Continuous Fours）威胁空间搜索**：只在「成四/成五」的强制着上展开，对手对单点四必须封堵，从而把分支压到极小、深度拉长。

- **候选**：近邻（切比雪夫半径 1）空点中，能让白方**成五或成四**的点；按启发式降序取前 `GOMOKU_VCF_MAX_MOVES = 16` 个。
- **成五点计数** `gomokuFiveThreatsThrough`：落子后沿过该点的四条线、只扫局部 ±4 范围内能「下一手成五」的空点（无需全盘）。
- **递归**（`gomokuVcfSearch`）：
  1. 若该着直接成五 → 取胜；
  2. 若形成**活四/双四**（成五点 ≥ 2）→ 对手无法同时封堵，取胜；
  3. 若仅一个成五点 → 对手被**迫封堵**该点，白方续走，递归下一层；
  4. 每层开始前先判「对手是否已能成五」——能则本线作废（保守放弃，交给评估搜索）。
- **健全性**：该递归不会产生假杀——对手的封堵若反向形成自己的四，会在下一层开头的「对手成五」检查中被拒；封闭四只有一个封堵点，故「被迫封堵」成立。
- **可复现**：纯确定性，无随机。
- **预算可控**：深度上限 `12`、展开节点上限 `6000`，超限即放弃（回退评估搜索）；实测顶档单步仍在毫秒～百毫秒级。

VCF 让 AI 真正具备「算杀」能力（对手稍一松懈即被连续冲四带走）。但它够不到「叉杀」（首着不是四）。顶档在其之后**再叠加一个有界的威胁空间搜索**（`gomokuVctForcedWin`）：先在副本上判一步杀（成五 / 造双四 = `gomokuWinsInTwoMove`），再对每个候选着 m 核验「对手所有**相关**应手之后，白方仍能一步杀」——相关应手 = 过 m 的四条轴 ±8 内空点 ∪ 对手造四着（成五点必与 m 同线，故该集合覆盖全部封堵点）。它覆盖**四三 / 双三**这类 VCF 看不到的叉杀；健全性由等价 Python 对**全部**空点核验（150 随机局面 + 构造双三，0 假阳性）与 Kotlin `vct_findsSoundForcedWinOnDoubleThree`（对全部应手核验）双重保证；有界（候选 ≤8、轴半径 8），找不到就退回评估搜索，绝不误判。

### 4.4 难度档位（`GomokuDifficulty`）

| 档位 | id | 深度 | 候选数 | VCF | 说明 |
|------|----|------|--------|-----|------|
| 入门 | `beginner` | 0 | 24 | — | `randomWeak`：取较弱一半，**按棋盘内容哈希确定性**选点 |
| 轻松 | `easy` | 1 | 8 | — | |
| 普通 | `normal` | 2 | 10 | — | 默认档（`fromId` 兜底） |
| 进阶 | `advanced` | 2 | 14 | — | |
| 挑战 | `hard` | 3 | 10 | — | |
| 大师 | `master` | **5** | 12 | ✓ | 邻域半径 3 |
| 宗师 | `expert` | **5** | 16 | ✓ | 邻域半径 3，候选更宽 |

**顶两档（大师/宗师）加深到 5 层并启用 VCF**：邻域从切比雪夫 2 扩到 3，避免隔一路叉杀落在候选外；VCF/VCT 预算同步加大。低档位（入门～挑战）保持不变。搜索在后台线程执行。

### 4.5 入门档的确定性

早期入门档用未播种的 `random()` 取点，导致同局面结果不可复现、难以测试。现改为：

```kotlin
val weak = ranked.takeLast((ranked.size / 2).coerceAtLeast(1))
return weak[Math.floorMod(board.contentHashCode(), weak.size)]
```

既有变化（随局面不同），又可复现（同局面同结果）。

---

## 5. 线程与对局装配（`BearGomokuScreen.kt` / `GomokuGame.kt`）

**搜索在后台线程执行**（`Dispatchers.Default`），主线程只做「抓快照、等思考延迟、校验回写、落子」：

```kotlin
val boardSnapshot = game.board.copyOf()
val index = withContext(Dispatchers.Default) { gomokuChooseAiMove(boardSnapshot, diff) }
if (game.phase != PLAYING || game.turn != WHITE || pause.paused) return@LaunchedEffect
val played = if (index != null) game.applyAiMove(index) else game.playAiMove()
```

- 思考延迟：顶档（深度 ≥3）480ms，其余 320ms；
- `GomokuGame.applyAiMove(index)` 应用后台结果并再校验；`playAiMove()` 作为兜底（内部现算）；
- 交互：合法落子 `CLOCK_TICK`、非法 `REJECT` 触感；音效 KEY/WIN/LOSE。
- **状态机不落盘**：五子棋不保存对局快照，仅持久化最高分与排行榜（见 §8）。

---

## 6. 渲染（`GomokuBoardArt.kt`）

坐标由 `gomokuBoardLayout(width, height)` 给出：`side = min(w,h)` 居中，`cell = side / 14`（15 条线 → 14 个间隔），棋子在**交叉点**上。

| 元素 | 画法 |
|------|------|
| 外框 | 圆角矩形 + 木纹线性渐变 + 边框宽度 `cell * 0.55` |
| 棋盘面 | 竖向渐变（米黄木色 `FELT_TOP/BOTTOM`）居中外扩半格 |
| 星位 `GOMOKU_HOSHI` | 5 个：`(3,3)/(3,11)/(7,7)/(11,3)/(11,11)` |
| 网格 | 15 条横竖线（`lineW` 0.8–2.2px 自适应） |
| 棋子 | 阴影 + 径向渐变；黑（`DISC_BLACK` 系）、白（`DISC_WHITE` 系）；半径 `cell * 0.44` |
| 合法提示 | 绿点（`LEGAL_GHOST`）：半透明大圆 + 亮心小圆（仅黑方回合） |
| 末手标记 | 橙色圆环 `LAST_MOVE_RING`（`cell * 0.48` 描边） |

绘制在 Compose 绘制阶段直接读「棋盘 / 末手 / 阶段」等 state，无需逐帧动画。

---

## 7. 工程决策与教训

- **白方视角 vs 根节点取负**：与黑白棋同源的一处错误——评估按白方视角、搜索内部白极大/黑极小，根节点却误加取负号，导致 AI「专挑对自己最差的点」。等价 Python 模拟验证：原码下 AI 对「黑方活三」视而不见、落到无关点；去掉负号后正确封堵（`GomokuLogicTest.chooseAiMove_blocksOpenThree` 即锁死此回归）。
- **强度回归**：`GomokuLogicTest.search_beatsGreedyBaseline` 用一盘确定性完整对局（白=宗师 4 层、黑=贪心 1 层）断言白胜，综合锁死符号/评估/深度；`difficulty_strengthIsMonotonic` 断言高阶档的深度与候选数不降。
- **确定性优先**：入门档由 `random()` 改为棋盘哈希定选，使整棵决策可复现、可测试。

---

## 8. 成绩与榜单

- 最高分 key `gomoku_best`、难度 key `gomoku_difficulty`、排行榜 key `leaderboard_gomoku_v1`；
- 仅黑方胜或和棋时入榜（`gomokuPlayerScore`：胜 100、和 50、负 0）；
- 复用公共 `GameLeaderboardStore`；清榜同时重置 `best` 与 prefs。

---

## 9. 测试与回归

| 测试文件 | 关键用例 |
|----------|----------|
| `GomokuLogicTest` | `win_detectsFiveInRow`；`candidateMoves_startAtCenter`；`chooseAiMove_returnsWinWhenAvailable`（能赢就赢）；`findImmediateWin_detectsMustBlock`；**`chooseAiMove_blocksOpenThree`**（封堵黑方活三，覆盖搜索根分支——符号回归）；`beginner_isDeterministicForSameBoard`（入门档确定性）；`difficulty_strengthIsMonotonic`（难度单调）；**`search_beatsGreedyBaseline`**（白=宗师胜贪心黑的确定性对局，强度回归）；`vcf_findsForcedWinWhenOpenThreeAvailable` / `vcf_nullWhenNoThreat` / `vcf_nullWhenOpponentCanWinFirst`（VCF 正例/无威胁/对手先胜 三类）；**`vct_findsSoundForcedWinOnDoubleThree`**（构造双三杀 + 对**全部**应手核验无假杀）/ `vct_nullWhenNoThreat`（VCT 双三正例与无威胁） |
| `BearGomokuEntryProviderTest` | ServiceLoader 入口反射可实例化 |

---

## 10. 已知取舍与后续改进

| 取舍 / 方向 | 现状与可改进点 |
|-------------|----------------|
| **VCT（威胁空间）已实现，有界** | 见 §4.3 末：覆盖四三 / 双三这类叉杀；健全性经等价 Python fuzz（对全部空点核验，0 假阳性）验证。**有界**：候选着 ≤6、轴半径 8、只查"相关"应手,不是完备的全局威胁搜索;不保证找到所有杀（**漏杀可接受，绝不假杀**） |
| **防守 VCF/VCT（已加强）** | 顶档对**全部**根候选检查：落子后若黑方获得 VCF 或 VCT 则剔除（全被剔除时回退原列表）。仍非完备证明 |
| **叉杀启发式** | 单点评估对「一手造双威胁」加分，中心格有轻微位置权 |
| **无置换表 / 迭代加深** | 未做 Zobrist 置换表与时间预算 |
| **候选裁剪固定半径 2** | 远点杀依赖全盘成五扫描；威胁空间搜索仍有界 |

*本文档配套代码见 `game/gomoku/src/main/java/com/xiaoxiong/weather/ui/game/`，测试见同模块 `src/test/java/.../ui/game/`。*
