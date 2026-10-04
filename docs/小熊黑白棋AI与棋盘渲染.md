# 小熊黑白棋：AI 与棋盘渲染技术说明

> 对应模块：`:game:reversi`（小熊黑白棋）
> 核心文件：`ReversiLogic.kt`（纯逻辑：落子/翻转/过手/评估/搜索）、`ReversiGame.kt`（对局状态机）、`ReversiBoardArt.kt`（棋盘渲染）、`BearReversiScreen.kt`（帧循环与装配接线）、`BearReversiEntry.kt` / `BearReversiEntryProvider.kt`（ServiceLoader 入口）
> 测试文件：`ReversiLogicTest`、`ReversiAiTest`、`BearReversiEntryProviderTest`
> 摘要：本文说明小熊黑白棋（8×8 奥赛罗）如何在「纯 Kotlin + Compose Canvas」下实现：夹吃/过手/终局的规则引擎、白方视角的 minimax + α-β 搜索（**单盘原地 apply/undo**、无每节点分配）、经自对弈验证的评估函数、后台线程搜索与主线程校验回写，以及七档难度与确定性强度回归测试。

---

## 1. 背景与玩法

标准奥赛罗：8×8 棋盘，玩家执**黑先手**，AI 执**白**。落子必须能**夹吃**对方至少一枚棋子（沿八个方向把被夹住的对方子翻成本方色）；**无子可下则过手**，双方都无子可下时终局，**子数多者胜**。

棋局三要素：`ReversiPhase`（READY / PLAYING / OVER）、`turn`（黑/白）、`outcome`（BLACK_WIN / WHITE_WIN / TIE / NONE）。玩家的成绩取「终局黑子数」：胜→黑子数、和→32、负→黑子数（上限 32），因此打赢越多的子分越高。

---

## 2. 棋盘模型与规则引擎（`ReversiLogic.kt`）

棋盘为扁平 `IntArray(64)`，`index = row * 8 + col`，取值为 `REVERSI_EMPTY / BLACK / WHITE`。

| 函数 | 职责 |
|------|------|
| `reversiInitialBoard()` | 标准开局：中心四子（对角同色） |
| `reversiLegalMoves(board, color)` | 单方向 `reversiLineFlips` 累计翻转数，>0 即成合法着，返回 `ReversiMove(index, flips)` |
| `reversiFlipsIfMove(board, index, color)` | 试算某点可翻转的子数（带越界保护，越界/非空返回 0） |
| `reversiApplyMove(board, index, color)` | 落子并 `reversiFlipLine` 逐方向翻子，返回翻转总数 |
| `reversiAdvanceTurn(board, turn)` | 判定过手：对手有子可下→换手；仅己方有子→己方续下（`skipped=true`）；都无→终局 |
| `reversiIsGameOver(board)` | 双方均无合法着即终局（经 `reversiHasLegalMove` 判定，不分配列表） |
| `reversiOutcome(board)` | 数子定胜负 |

**热路径无分配**：`reversiHasLegalMove`（存在性短路）与 `reversiCountLegalMoves`（计数）都不构造列表，供终局判定与评估的机动性计算使用；列表版 `reversiLegalMoves` 只在真正需要候选时调用。

---

## 3. 评估函数（`reversiEvaluate`）

评估始终以**某一方视角**给出分值（搜索中固定用白方视角），为「零和」：

```kotlin
score = (mine − theirs) * discWeight + mobility * 8 + corners + dangerPenalty
```

| 项 | 含义与权重 |
|----|------------|
| `discWeight` | 子数差权重：**残局**（空位 ≤ 12）取 3，其余取 1 —— 中局不再贪子 |
| `mobility * 8` | 机动性差（本方合法着数 − 对方合法着数）× 8 |
| `corners` | 角权重 `reversiCornerBonus`：四角 100、边非角 8；本方加、对方减 |
| `dangerPenalty` | **空角相邻格危险度**：四角为空时，占据其相邻的 **X 格**（对角，+40/−40）与 **C 格**（正交，+20/−20）会被惩罚，避免「送角」 |

> 为什么要 X/C 惩罚：占据空角旁边的格子，等于把角拱手让给对手。四角已被占据时对应邻格不再计罚。

**评估升级是经自对弈验证的**：把「角 + X/C + 机动性×8 + 残局计数」与旧版「数子×2 + 机动性×5 + 角」在同深度下自对弈，新版**两种先后手都以 61–2 取胜**，因此采用。

---

## 4. 搜索（`reversiChooseAiMove` + `reversiMinimax`）

固定深度 **minimax + α-β 剪枝**，白方极大、黑方极小，叶子返回白方视角评估值。

- **根节点不取负**：`reversiChooseAiMove` 直接取 `reversiMinimax(..., BLACK)` 的返回值（白方视角）并**择大**。这与 `gomoku` 的写法一致；早期版本此处误加了取负号（见 §7 教训）。
- **单盘原地 apply/undo**：搜索在**一份工作棋盘副本**上原地落子/回退，不再每个节点 `board.copyOf()`：
  - `reversiApplyMoveLogged` 落子并把被翻格子压入复用的 `IntArray` 翻转栈（容量 `REVERSI_CELLS * 8`）；
  - `reversiUndoMove` 按栈把翻过的格子还原为对手色、清空落子点。
  - 因为 α-β 提前返回时也要能正确回退，撤销与落子严格配对。
- **未加走法排序**：实测本评估 + α-β 的搜索树本就很小（深度 6 约 3.6k 节点），加排序收益不明显甚至因排序开销变慢，故不引入。
- **平手打破**：分数相同时优先角位（`reversiCornerBonus` 更大者）。

### 4.1 难度档位（`ReversiDifficulty`）

| 档位 | id | 搜索深度 | 说明 |
|------|----|----------|------|
| 入门 | `beginner` | —（`greedyOnly`） | 贪心：取「翻子最少 + 角加成」者，故意弱 |
| 轻松 | `easy` | 2 | |
| 普通 | `normal` | 3 | 默认档（`fromId(null)`） |
| 进阶 | `advanced` | 4 | |
| 挑战 | `hard` | 5 | |
| 大师 | `master` | 6 | |
| 宗师 | `expert` | 7 | |

兼容旧 id：`casual → BEGINNER`、`challenge → HARD`。

---

## 5. 线程与对局装配（`BearReversiScreen.kt` / `ReversiGame.kt`）

**搜索在后台线程执行**，避免深度 7 的主线程卡帧/ANR：

```kotlin
// 主线程：判断轮到白方并等待"思考"延迟
delay(320)
// 抓取快照 → 后台搜索 → 回主线程校验 → 落子
val boardSnapshot = game.board.copyOf()
val index = withContext(Dispatchers.Default) { reversiChooseAiMove(boardSnapshot, diff) }
if (game.phase != PLAYING || game.turn != WHITE) return@LaunchedEffect  // 局面已变则丢弃
if (index != null) game.applyAiMove(index) else game.passTurnIfNoLegalMove()
```

- `ReversiGame.applyAiMove(index)` 应用后台算好的落子（再校验 turn/phase 与合法性）；
- 过手处理：`while (game.passTurnIfNoLegalMove()) {…}` 在 AI 落子前统一结算双方过手/终局；
- 交互：落子/过手带触感反馈（`CLOCK_TICK`/`REJECT`）、音效（WIN/LOSE/KEY/UI_CLICK）；
- **状态机不落盘**：黑白棋不保存对局快照，仅持久化最高分与排行榜（见 §8）。

---

## 6. 渲染（`ReversiBoardArt.kt`）

纯 Canvas 绘制，坐标由 `reversiBoardLayout(width, height)` 给出：取 `side = min(w,h)` 居中，`cell = side / 8`，`(left/top)` 为左上角。

| 元素 | 画法 |
|------|------|
| 外框 | 圆角矩形 + 木纹线性渐变（`FRAME_LIGHT/MID/DARK`）+ 内阴影 |
| 棋盘面 | 竖向渐变（`FELT_TOP/FELT_BOTTOM` 绿呢） |
| 网格 | 0..8 共 9 条横竖线，边线更粗更深；星位 4 个（`(2,2)/(2,5)/(5,2)/(5,5)` 格中心） |
| 棋子 | 阴影 + 径向渐变（黑：暗色高光；白：白→米色）；半径 `cell * 0.42` |
| 合法提示 | 绿点：`LEGAL_GHOST` 半透明大圆 + 亮心小圆（仅黑方回合显示） |
| 末手标记 | 琥珀色圆环 `LAST_MOVE_RING`（`cell * 0.47`，描边） |

棋子画在**格心**（不同于五子棋的交叉点）。绘制不受帧循环影响（对局无逐帧动画），状态变化即重绘。

---

## 7. 工程决策与教训

- **白方视角 vs 根节点取负**：本项目两个棋类曾各自出现同一处错误——评估按白方（AI）视角给出，搜索内部也按白/黑显式 max/min，**根节点却又取负**，等价于让 AI「专挑对自己最差的点」。用等价 Python 模拟复现：按原码，白方 0–19 惨败于贪心黑、且对空角视而不见；去掉负号后 55–9 大胜。修复即删掉根节点多余的一元负号。
- **强度回归**：`ReversiAiTest.search_takesAvailableCorner` 断言「有空角时必取角」（对符号错误极敏感）；`search_beatsGreedyBaseline` 用一盘确定性完整对局断言「白方搜索胜过贪心黑」，作为符号/评估/深度的综合回归。
- **评估调参靠自对弈**：任何评估改动都先用同深度自对弈对比新旧（两种先后手）再决定采用，避免「凭感觉调权重」。

---

## 8. 成绩与榜单

- 最高分 key `reversi_best`；排行榜 key `leaderboard_reversi_v1`，复用公共 `GameLeaderboardStore`；
- 仅黑方胜或和棋时入榜（`reversiPlayerScore`：胜=黑子数、和=32、负=黑子数≤32）；榜单按分数排序；
- 清榜同时重置 `best` 与 prefs。

---

## 9. 测试与回归

| 测试文件 | 关键用例 |
|----------|----------|
| `ReversiLogicTest` | 开局四子与黑方合法着；`applyMove_flipsOpponentLine`；`countAndHasLegalMove_matchListVersion`（无分配版与列表版等价）；难度映射（含旧 id）；`difficulty_searchDepth_increasesWithTier` 深度阶梯 `[0,2,3,4,5,6,7]` |
| `ReversiAiTest` | `search_takesAvailableCorner`（必取角）；`search_beatsGreedyBaseline`（强度回归）；`chooseAiMove_returnsNullWhenWhiteHasNoMoves`；`flipsIfMove_outOfRangeIndexIsNotLegal`（越界保护） |
| `BearReversiEntryProviderTest` | ServiceLoader 入口反射可实例化 |

---

## 10. 已知取舍与后续改进

| 取舍 / 方向 | 现状与可改进点 |
|-------------|----------------|
| **无终局精确解** | 目前固定深度评估搜索；标准强引擎会在空位 ≤ ~10 时切换到**精确终局求解**（配合奇偶/稳定度），这是最大的强度提升点 |
| **无置换表 / 迭代加深** | 未做 Zobrist 哈希置换表与时间预算控制；加上后可用「固定时间、自适应深度」替代「固定深度」 |
| **评估仍偏简** | 仅角 + X/C + 机动性 + 残局子数；可补**稳定子 / 前沿子（frontier）/ 奇偶（parity）**与更细的边权 |
| **无走法排序** | 实测当前深度下收益不明显；若上终局精确解/更深搜索，需配合走法排序 |
| **无开局库** | 前几手可查表，避免开局走弱 |
| **主线程** | 搜索已在 `Dispatchers.Default`；深度/耗时若继续增大，建议加时间上限保护 |

*本文档配套代码见 `game/reversi/src/main/java/com/xiaoxiong/weather/ui/game/`，测试见同模块 `src/test/java/.../ui/game/`。*
