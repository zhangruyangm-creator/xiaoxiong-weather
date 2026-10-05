package com.xiaoxiong.weather.ui.game

import kotlin.math.abs

internal const val GOMOKU_SIZE = 15
internal const val GOMOKU_CELLS = GOMOKU_SIZE * GOMOKU_SIZE

internal const val GOMOKU_EMPTY = 0
internal const val GOMOKU_BLACK = 1
internal const val GOMOKU_WHITE = 2

internal const val GOMOKU_BEST_KEY = "gomoku_best"
internal const val GOMOKU_LEADERBOARD_KEY = "leaderboard_gomoku_v1"
internal const val GOMOKU_PREF_DIFF = "gomoku_difficulty"

private val GOMOKU_DIRS =
    intArrayOf(
        1,
        0,
        0,
        1,
        1,
        1,
        1,
        -1,
    )

// ── AI 搜索预算：顶档 VCF / VCT / 防守的深度、节点与候选上限 ──
private const val GOMOKU_VCF_MAX_DEPTH = 12
private const val GOMOKU_VCF_MAX_NODES = 6_000
private const val GOMOKU_VCF_MAX_MOVES = 16
private const val GOMOKU_VCT_AXIS_RADIUS = 8
private const val GOMOKU_VCT_MAX_MOVES = 8
private const val GOMOKU_DEFENSE_VCF_NODES = 2_500

internal enum class GomokuPhase {
    READY,
    PLAYING,
    OVER,
}

internal enum class GomokuDifficulty(
    val id: String,
    val searchDepth: Int,
    val topMoves: Int,
    val randomWeak: Boolean = false,
    /** 顶档启用连续冲四（VCF）强制取胜搜索。 */
    val usesVcf: Boolean = false,
    /** 搜索候选邻域半径（切比雪夫）；顶档扩到 3 以免漏掉隔一路的叉杀。 */
    val searchRadius: Int = 2,
) {
    BEGINNER("beginner", 0, 24, randomWeak = true),
    EASY("easy", 1, 8),
    NORMAL("normal", 2, 10),
    ADVANCED("advanced", 2, 14),
    HARD("hard", 3, 10),
    MASTER("master", 5, 12, usesVcf = true, searchRadius = 3),
    EXPERT("expert", 5, 16, usesVcf = true, searchRadius = 3),
    ;

    companion object {
        fun fromId(id: String?): GomokuDifficulty = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

internal enum class GomokuOutcome {
    NONE,
    BLACK_WIN,
    WHITE_WIN,
    DRAW,
}

internal fun gomokuIndex(
    row: Int,
    col: Int,
): Int = row * GOMOKU_SIZE + col

internal fun gomokuRow(index: Int): Int = index / GOMOKU_SIZE

internal fun gomokuCol(index: Int): Int = index % GOMOKU_SIZE

internal fun gomokuInitialBoard(): IntArray = IntArray(GOMOKU_CELLS)

internal fun gomokuOpponent(color: Int): Int = if (color == GOMOKU_BLACK) GOMOKU_WHITE else GOMOKU_BLACK

internal fun gomokuInBounds(
    row: Int,
    col: Int,
): Boolean = row in 0 until GOMOKU_SIZE && col in 0 until GOMOKU_SIZE

internal fun gomokuCountStones(board: IntArray): Int = board.count { it != GOMOKU_EMPTY }

internal fun gomokuIsFull(board: IntArray): Boolean = gomokuCountStones(board) >= GOMOKU_CELLS

internal fun gomokuCandidateMoves(
    board: IntArray,
    radius: Int = 2,
): List<Int> {
    if (gomokuCountStones(board) == 0) {
        val m = GOMOKU_SIZE / 2
        return listOf(gomokuIndex(m, m))
    }
    val r0 = radius.coerceAtLeast(1)
    val seen = BooleanArray(GOMOKU_CELLS)
    val out = ArrayList<Int>(64)
    for (i in 0 until GOMOKU_CELLS) {
        if (board[i] == GOMOKU_EMPTY) continue
        val r = gomokuRow(i)
        val c = gomokuCol(i)
        for (dr in -r0..r0) {
            for (dc in -r0..r0) {
                val nr = r + dr
                val nc = c + dc
                if (!gomokuInBounds(nr, nc)) continue
                val idx = gomokuIndex(nr, nc)
                if (board[idx] != GOMOKU_EMPTY || seen[idx]) continue
                seen[idx] = true
                out.add(idx)
            }
        }
    }
    if (out.isEmpty() && !gomokuIsFull(board)) {
        for (i in 0 until GOMOKU_CELLS) {
            if (board[i] == GOMOKU_EMPTY) out.add(i)
        }
    }
    return out
}

/** 全盘扫描一步成五（含候选集外的杀棋/必防点）。 */
internal fun gomokuFindImmediateWin(
    board: IntArray,
    color: Int,
): Int? {
    for (i in 0 until GOMOKU_CELLS) {
        if (board[i] != GOMOKU_EMPTY) continue
        if (gomokuWouldWin(board, i, color)) return i
    }
    return null
}

internal fun gomokuWinAt(
    board: IntArray,
    index: Int,
    color: Int,
): Boolean {
    val row = gomokuRow(index)
    val col = gomokuCol(index)
    if (board[index] != color) return false
    for (d in GOMOKU_DIRS.indices step 2) {
        val dr = GOMOKU_DIRS[d]
        val dc = GOMOKU_DIRS[d + 1]
        var count = 1
        count += gomokuCountAlong(board, row, col, dr, dc, color)
        count += gomokuCountAlong(board, row, col, -dr, -dc, color)
        if (count >= 5) return true
    }
    return false
}

private fun gomokuCountAlong(
    board: IntArray,
    row: Int,
    col: Int,
    dr: Int,
    dc: Int,
    color: Int,
): Int {
    var r = row + dr
    var c = col + dc
    var n = 0
    while (gomokuInBounds(r, c) && board[gomokuIndex(r, c)] == color) {
        n++
        r += dr
        c += dc
    }
    return n
}

internal fun gomokuWouldWin(
    board: IntArray,
    index: Int,
    color: Int,
): Boolean {
    if (board[index] != GOMOKU_EMPTY) return false
    board[index] = color
    val win = gomokuWinAt(board, index, color)
    board[index] = GOMOKU_EMPTY
    return win
}

private fun gomokuLineScore(
    count: Int,
    openEnds: Int,
): Int {
    if (count >= 5) return 200_000
    if (count == 4) {
        return when (openEnds) {
            2 -> 12_000
            1 -> 1_200
            else -> 60
        }
    }
    if (count == 3) {
        return when (openEnds) {
            2 -> 800
            1 -> 80
            else -> 10
        }
    }
    if (count == 2) {
        return when (openEnds) {
            2 -> 40
            1 -> 8
            else -> 2
        }
    }
    return if (count == 1 && openEnds > 0) 2 else 0
}

private fun gomokuAnalyzeLine(
    board: IntArray,
    row: Int,
    col: Int,
    dr: Int,
    dc: Int,
    color: Int,
): Pair<Int, Int> {
    var count = 1
    var open = 0
    var r = row + dr
    var c = col + dc
    while (gomokuInBounds(r, c) && board[gomokuIndex(r, c)] == color) {
        count++
        r += dr
        c += dc
    }
    if (gomokuInBounds(r, c) && board[gomokuIndex(r, c)] == GOMOKU_EMPTY) open++
    r = row - dr
    c = col - dc
    while (gomokuInBounds(r, c) && board[gomokuIndex(r, c)] == color) {
        count++
        r -= dr
        c -= dc
    }
    if (gomokuInBounds(r, c) && board[gomokuIndex(r, c)] == GOMOKU_EMPTY) open++
    return count to open
}

internal fun gomokuMoveHeuristic(
    board: IntArray,
    index: Int,
    color: Int,
): Int {
    if (board[index] != GOMOKU_EMPTY) return Int.MIN_VALUE / 4
    board[index] = color
    if (gomokuWinAt(board, index, color)) {
        board[index] = GOMOKU_EMPTY
        return 500_000
    }
    val row = gomokuRow(index)
    val col = gomokuCol(index)
    var score = 0
    // 越靠中心越好：15×15 中心(7,7) 约 +14，边角接近 0
    score += (GOMOKU_SIZE - 1 - abs(row - GOMOKU_SIZE / 2) - abs(col - GOMOKU_SIZE / 2))
    if (gomokuFiveThreatsThrough(board, index, color, 2) >= 2) {
        score += 80_000
    }
    for (d in GOMOKU_DIRS.indices step 2) {
        val dr = GOMOKU_DIRS[d]
        val dc = GOMOKU_DIRS[d + 1]
        val (cnt, open) = gomokuAnalyzeLine(board, row, col, dr, dc, color)
        score += gomokuLineScore(cnt, open)
    }
    board[index] = GOMOKU_EMPTY
    val opp = gomokuOpponent(color)
    board[index] = opp
    if (gomokuWinAt(board, index, opp)) {
        score += 400_000
    } else {
        if (gomokuFiveThreatsThrough(board, index, opp, 2) >= 2) {
            score += 70_000
        }
        for (d in GOMOKU_DIRS.indices step 2) {
            val dr = GOMOKU_DIRS[d]
            val dc = GOMOKU_DIRS[d + 1]
            val (cnt, open) = gomokuAnalyzeLine(board, row, col, dr, dc, opp)
            score += gomokuLineScore(cnt, open) * 9 / 10
        }
    }
    board[index] = GOMOKU_EMPTY
    return score
}

internal fun gomokuEvaluateBoard(
    board: IntArray,
    forColor: Int,
    radius: Int = 2,
): Int {
    var sum = 0
    for (idx in gomokuCandidateMoves(board, radius)) {
        sum += gomokuMoveHeuristic(board, idx, forColor)
        sum -= gomokuMoveHeuristic(board, idx, gomokuOpponent(forColor)) * 95 / 100
    }
    return sum
}

internal fun gomokuRankedMoves(
    board: IntArray,
    color: Int,
    top: Int,
    radius: Int = 2,
): List<Int> {
    val moves = gomokuCandidateMoves(board, radius)
    if (moves.isEmpty()) return emptyList()
    return moves
        .map { it to gomokuMoveHeuristic(board, it, color) }
        .sortedByDescending { it.second }
        .take(top)
        .map { it.first }
}

/** 近邻空点（切比雪夫半径 1）：冲四点必然紧邻已有棋子，据此收窄枚举。 */
private fun gomokuNearbyEmptyMoves(board: IntArray): List<Int> {
    val out = LinkedHashSet<Int>()
    for (i in 0 until GOMOKU_CELLS) {
        if (board[i] == GOMOKU_EMPTY) continue
        val r = gomokuRow(i)
        val c = gomokuCol(i)
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (!gomokuInBounds(nr, nc)) continue
                val j = gomokuIndex(nr, nc)
                if (board[j] == GOMOKU_EMPTY) out.add(j)
            }
        }
    }
    return out.toList()
}

/**
 * 落子 [index] 后，[color] 在过 [index] 的四条线上能「下一手成五」的点数（上限 [cap]）。
 * 成五点必与 [index] 同线且距离 ≤4，故只需扫描局部的四条线，无需全盘。
 */
private fun gomokuFiveThreatsThrough(
    board: IntArray,
    index: Int,
    color: Int,
    cap: Int,
): Int {
    val row = gomokuRow(index)
    val col = gomokuCol(index)
    var count = 0
    for (d in GOMOKU_DIRS.indices step 2) {
        val dr = GOMOKU_DIRS[d]
        val dc = GOMOKU_DIRS[d + 1]
        for (step in -4..4) {
            if (step == 0) continue
            val r = row + dr * step
            val c = col + dc * step
            if (!gomokuInBounds(r, c)) continue
            val q = gomokuIndex(r, c)
            if (board[q] != GOMOKU_EMPTY) continue
            if (gomokuWouldWin(board, q, color)) {
                count++
                if (count >= cap) return count
            }
        }
    }
    return count
}

/** VCF 候选：能形成四（或直接成五）的点，按启发式降序取前 [GOMOKU_VCF_MAX_MOVES] 个。 */
private fun gomokuVcfThreatMoves(
    board: IntArray,
    attacker: Int,
): List<Int> {
    val out = ArrayList<Int>()
    for (m in gomokuNearbyEmptyMoves(board)) {
        board[m] = attacker
        val makesFour = gomokuWinAt(board, m, attacker) || gomokuFiveThreatsThrough(board, m, attacker, 1) > 0
        board[m] = GOMOKU_EMPTY
        if (makesFour) out.add(m)
    }
    if (out.size <= 1) return out
    return out.sortedByDescending { gomokuMoveHeuristic(board, it, attacker) }.take(GOMOKU_VCF_MAX_MOVES)
}

/** 连续冲四（VCF）递归：attacker 走强制四，对手被迫封堵单点；双四直接取胜。 */
private fun gomokuVcfSearch(
    board: IntArray,
    attacker: Int,
    depth: Int,
    budget: IntArray,
): Int? {
    if (depth <= 0 || budget[0] <= 0) return null
    val opp = gomokuOpponent(attacker)
    // 对手已能成五：无法用冲四强制取胜（保守放弃，交由评估搜索处理）
    if (gomokuFindImmediateWin(board, opp) != null) return null
    for (move in gomokuVcfThreatMoves(board, attacker)) {
        if (budget[0]-- <= 0) return null
        board[move] = attacker
        if (gomokuWinAt(board, move, attacker)) {
            board[move] = GOMOKU_EMPTY
            return move
        }
        val wins = gomokuFiveThreatsThrough(board, move, attacker, 2)
        if (wins >= 2) {
            // 活四/双四：对手无法同时封堵
            board[move] = GOMOKU_EMPTY
            return move
        }
        if (wins == 1) {
            val block = gomokuFindImmediateWin(board, attacker)
            if (block == null) {
                board[move] = GOMOKU_EMPTY
                continue
            }
            board[block] = opp
            val sub = gomokuVcfSearch(board, attacker, depth - 1, budget)
            board[block] = GOMOKU_EMPTY
            if (sub != null) {
                board[move] = GOMOKU_EMPTY
                return move
            }
        }
        board[move] = GOMOKU_EMPTY
    }
    return null
}

/**
 * 连续冲四（VCF）：[attacker] 是否存在一串「步步成四」的强制取胜着法；命中返回首着，否则 null。
 * 在棋盘副本上搜索，不改动传入棋盘。仅顶档 AI（[GomokuDifficulty.usesVcf]）启用。
 */
internal fun gomokuVcfWin(
    board: IntArray,
    attacker: Int,
): Int? = gomokuVcfSearch(board.copyOf(), attacker, GOMOKU_VCF_MAX_DEPTH, intArrayOf(GOMOKU_VCF_MAX_NODES))

/** 全盘统计 [color] 一步成五的点数（测试/VCF 使用）。 */
internal fun gomokuCountImmediateWins(
    board: IntArray,
    color: Int,
): Int {
    var n = 0
    for (i in 0 until GOMOKU_CELLS) {
        if (board[i] == GOMOKU_EMPTY && gomokuWouldWin(board, i, color)) n++
    }
    return n
}

/** [color] 是否存在"造活四/双四"的着（一手产生 ≥2 个成五点）。 */
private fun gomokuHasDoubleMove(
    board: IntArray,
    color: Int,
): Boolean {
    for (p in gomokuNearbyEmptyMoves(board)) {
        board[p] = color
        val ok = gomokuCountImmediateWins(board, color) >= 2
        board[p] = GOMOKU_EMPTY
        if (ok) return true
    }
    return false
}

/** [attacker] 一步（成五，或造活四/双四）即可强制取胜的着；无则 null。 */
internal fun gomokuWinsInTwoMove(
    board: IntArray,
    attacker: Int,
): Int? {
    gomokuFindImmediateWin(board, attacker)?.let { return it }
    val opp = gomokuOpponent(attacker)
    for (p in gomokuNearbyEmptyMoves(board)) {
        board[p] = attacker
        val good = gomokuCountImmediateWins(board, attacker) >= 2 && gomokuFindImmediateWin(board, opp) == null
        board[p] = GOMOKU_EMPTY
        if (good) return p
    }
    return null
}

/** [color] 是否存在成四着（含成五）。 */
private fun gomokuHasFourMove(
    board: IntArray,
    color: Int,
): Boolean {
    for (m in gomokuNearbyEmptyMoves(board)) {
        board[m] = color
        val makes = gomokuWinAt(board, m, color) || gomokuCountImmediateWins(board, color) > 0
        board[m] = GOMOKU_EMPTY
        if (makes) return true
    }
    return false
}

/**
 * 对手"可能反制"的应手集合：过 [m] 的四条轴 ±[GOMOKU_VCT_AXIS_RADIUS] 内空点 ∪ 对手造四着。
 * 成五点必与 [m] 同线（活四两端再外扩），故该集合覆盖全部可能封堵点；对手造四着捕获"远处反制"。
 */
private fun gomokuRelevantDefenses(
    board: IntArray,
    m: Int,
    defender: Int,
): List<Int> {
    val out = LinkedHashSet<Int>()
    val r = gomokuRow(m)
    val c = gomokuCol(m)
    for (d in GOMOKU_DIRS.indices step 2) {
        val dr = GOMOKU_DIRS[d]
        val dc = GOMOKU_DIRS[d + 1]
        for (step in -GOMOKU_VCT_AXIS_RADIUS..GOMOKU_VCT_AXIS_RADIUS) {
            if (step == 0) continue
            val rr = r + dr * step
            val cc = c + dc * step
            if (!gomokuInBounds(rr, cc)) continue
            val j = gomokuIndex(rr, cc)
            if (board[j] == GOMOKU_EMPTY) out.add(j)
        }
    }
    for (d in gomokuNearbyEmptyMoves(board)) {
        board[d] = defender
        val four = gomokuWinAt(board, d, defender) || gomokuCountImmediateWins(board, defender) > 0
        board[d] = GOMOKU_EMPTY
        if (four) out.add(d)
    }
    return out.toList()
}

/**
 * 有界的"一至两步内强制取胜"搜索（含四三、双三这类 VCF 覆盖不到的叉杀）：
 * 先在副本上判断一步杀，再对所有候选着 m 核验"对手相关应手后仍能一步杀"。
 * 健全性由等价 Python fuzz（对**全部**空点核验）验证为 0 假阳性；仅顶档调用。
 */
internal fun gomokuVctForcedWin(
    board: IntArray,
    attacker: Int,
): Int? {
    val w = board.copyOf()
    val opp = gomokuOpponent(attacker)
    if (gomokuFindImmediateWin(w, opp) != null) return null
    if (gomokuHasFourMove(w, opp)) return null
    gomokuWinsInTwoMove(w, attacker)?.let { return it }
    var considered = 0
    for (m in gomokuNearbyEmptyMoves(w)) {
        if (considered >= GOMOKU_VCT_MAX_MOVES) break
        w[m] = attacker
        if (gomokuWinAt(w, m, attacker)) {
            w[m] = GOMOKU_EMPTY
            return m
        }
        val hasThreat = gomokuCountImmediateWins(w, attacker) >= 1 || gomokuHasDoubleMove(w, attacker)
        var ok = hasThreat && gomokuWinsInTwoMove(w, attacker) != null
        if (ok) {
            considered++
            for (d in gomokuRelevantDefenses(w, m, opp)) {
                w[d] = opp
                if (gomokuWinAt(w, d, opp) || gomokuWinsInTwoMove(w, attacker) == null) ok = false
                w[d] = GOMOKU_EMPTY
                if (!ok) break
            }
        }
        w[m] = GOMOKU_EMPTY
        if (ok) return m
    }
    return null
}

/** 白方在 [move] 落子后，黑方是否获得 VCF / VCT 强杀。 */
private fun gomokuOpponentHasForcedWinAfterWhite(
    board: IntArray,
    move: Int,
): Boolean {
    val copy = board.copyOf()
    copy[move] = GOMOKU_WHITE
    if (gomokuFindImmediateWin(copy, GOMOKU_BLACK) != null) return true
    if (gomokuVcfSearch(copy, GOMOKU_BLACK, GOMOKU_VCF_MAX_DEPTH, intArrayOf(GOMOKU_DEFENSE_VCF_NODES)) != null) {
        return true
    }
    return gomokuVctForcedWin(copy, GOMOKU_BLACK) != null
}

internal fun gomokuChooseAiMove(
    board: IntArray,
    difficulty: GomokuDifficulty,
): Int? {
    gomokuFindImmediateWin(board, GOMOKU_WHITE)?.let { return it }
    gomokuFindImmediateWin(board, GOMOKU_BLACK)?.let { return it }
    // 顶档：连续冲四（VCF）+ 威胁空间（VCT：四三 / 双三）强制取胜搜索
    if (difficulty.usesVcf) {
        gomokuVcfWin(board, GOMOKU_WHITE)?.let { return it }
        gomokuVctForcedWin(board, GOMOKU_WHITE)?.let { return it }
    }
    val radius = difficulty.searchRadius
    val moves = gomokuCandidateMoves(board, radius)
    if (moves.isEmpty()) return null
    if (difficulty.randomWeak) {
        val ranked = gomokuRankedMoves(board, GOMOKU_WHITE, difficulty.topMoves, radius)
        val weak = ranked.takeLast((ranked.size / 2).coerceAtLeast(1))
        // 用棋盘内容的确定性哈希从"较弱的一半"里取一个点：既有变化又可复现（便于测试/回放）
        return weak[Math.floorMod(board.contentHashCode(), weak.size)]
    }
    if (difficulty.searchDepth <= 0) {
        return gomokuRankedMoves(board, GOMOKU_WHITE, 1, radius).firstOrNull()
    }
    val ranked = gomokuRankedMoves(board, GOMOKU_WHITE, difficulty.topMoves, radius)
    // 顶档防守：剔除落子后让黑方获得 VCF/VCT 强杀的候选；全部候选都检查
    val candidates =
        if (difficulty.usesVcf && ranked.size > 1) {
            val safe = ranked.filterNot { gomokuOpponentHasForcedWinAfterWhite(board, it) }
            safe.ifEmpty { ranked }
        } else {
            ranked
        }
    var bestScore = Int.MIN_VALUE / 4
    var best = candidates.first()
    // 单盘原地 apply/undo（与 minimax 一致），避免每个候选复制整盘
    val work = board.copyOf()
    for (move in candidates) {
        work[move] = GOMOKU_WHITE
        if (gomokuWinAt(work, move, GOMOKU_WHITE)) {
            work[move] = GOMOKU_EMPTY
            return move
        }
        val score =
            gomokuMinimax(
                work,
                difficulty.searchDepth - 1,
                GOMOKU_BLACK,
                Int.MIN_VALUE / 4,
                Int.MAX_VALUE / 4,
                difficulty.topMoves,
                radius,
            )
        work[move] = GOMOKU_EMPTY
        if (score > bestScore) {
            bestScore = score
            best = move
        }
    }
    return best
}

private fun gomokuMinimax(
    board: IntArray,
    depth: Int,
    turn: Int,
    alpha: Int,
    beta: Int,
    topMoves: Int,
    radius: Int,
): Int {
    if (depth <= 0) {
        return gomokuEvaluateBoard(board, GOMOKU_WHITE, radius)
    }
    val moves = gomokuRankedMoves(board, turn, topMoves, radius)
    if (moves.isEmpty()) {
        return gomokuEvaluateBoard(board, GOMOKU_WHITE, radius)
    }
    var a = alpha
    var b = beta
    if (turn == GOMOKU_WHITE) {
        var best = Int.MIN_VALUE / 4
        for (move in moves) {
            board[move] = GOMOKU_WHITE
            if (gomokuWinAt(board, move, GOMOKU_WHITE)) {
                board[move] = GOMOKU_EMPTY
                return 500_000
            }
            val score = gomokuMinimax(board, depth - 1, GOMOKU_BLACK, a, b, topMoves, radius)
            board[move] = GOMOKU_EMPTY
            best = maxOf(best, score)
            a = maxOf(a, score)
            if (a >= b) break
        }
        return best
    }
    var best = Int.MAX_VALUE / 4
    for (move in moves) {
        board[move] = GOMOKU_BLACK
        if (gomokuWinAt(board, move, GOMOKU_BLACK)) {
            board[move] = GOMOKU_EMPTY
            return -500_000
        }
        val score = gomokuMinimax(board, depth - 1, GOMOKU_WHITE, a, b, topMoves, radius)
        board[move] = GOMOKU_EMPTY
        best = minOf(best, score)
        b = minOf(b, score)
        if (a >= b) break
    }
    return best
}

internal fun gomokuOutcome(board: IntArray): GomokuOutcome {
    for (i in 0 until GOMOKU_CELLS) {
        when (board[i]) {
            GOMOKU_BLACK -> if (gomokuWinAt(board, i, GOMOKU_BLACK)) return GomokuOutcome.BLACK_WIN
            GOMOKU_WHITE -> if (gomokuWinAt(board, i, GOMOKU_WHITE)) return GomokuOutcome.WHITE_WIN
        }
    }
    return if (gomokuIsFull(board)) GomokuOutcome.DRAW else GomokuOutcome.NONE
}
