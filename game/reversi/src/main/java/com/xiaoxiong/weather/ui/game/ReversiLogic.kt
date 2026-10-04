package com.xiaoxiong.weather.ui.game

internal const val REVERSI_SIZE = 8
internal const val REVERSI_CELLS = REVERSI_SIZE * REVERSI_SIZE

internal const val REVERSI_EMPTY = 0
internal const val REVERSI_BLACK = 1
internal const val REVERSI_WHITE = 2

internal const val REVERSI_BEST_KEY = "reversi_best"
internal const val REVERSI_LEADERBOARD_KEY = "leaderboard_reversi_v1"
internal const val REVERSI_PREF_DIFF = "reversi_difficulty"

internal enum class ReversiPhase {
    READY,
    PLAYING,
    OVER,
}

internal enum class ReversiDifficulty(
    val id: String,
    /** Minimax 根节点搜索深度；[greedyOnly] 时忽略。 */
    val searchDepth: Int,
    val greedyOnly: Boolean = false,
) {
    BEGINNER("beginner", 0, greedyOnly = true),
    EASY("easy", 2),
    NORMAL("normal", 3),
    ADVANCED("advanced", 4),
    HARD("hard", 5),
    MASTER("master", 6),
    EXPERT("expert", 7),
    ;

    companion object {
        private val LEGACY_IDS =
            mapOf(
                "casual" to BEGINNER,
                "challenge" to HARD,
            )

        fun fromId(id: String?): ReversiDifficulty {
            if (id == null) return NORMAL
            LEGACY_IDS[id]?.let { return it }
            return entries.firstOrNull { it.id == id } ?: NORMAL
        }
    }
}

internal enum class ReversiOutcome {
    NONE,
    BLACK_WIN,
    WHITE_WIN,
    TIE,
}

internal data class ReversiMove(
    val index: Int,
    val flips: Int,
)

internal fun reversiOpponent(color: Int): Int = if (color == REVERSI_BLACK) REVERSI_WHITE else REVERSI_BLACK

internal fun reversiInitialBoard(): IntArray =
    IntArray(REVERSI_CELLS).apply {
        val m = REVERSI_SIZE / 2 - 1
        this[reversiIndex(m, m)] = REVERSI_WHITE
        this[reversiIndex(m + 1, m + 1)] = REVERSI_WHITE
        this[reversiIndex(m, m + 1)] = REVERSI_BLACK
        this[reversiIndex(m + 1, m)] = REVERSI_BLACK
    }

internal fun reversiIndex(
    row: Int,
    col: Int,
): Int = row * REVERSI_SIZE + col

internal fun reversiRow(index: Int): Int = index / REVERSI_SIZE

internal fun reversiCol(index: Int): Int = index % REVERSI_SIZE

internal fun reversiCount(
    board: IntArray,
    color: Int,
): Int = board.count { it == color }

internal fun reversiLegalMoves(
    board: IntArray,
    color: Int,
): List<ReversiMove> {
    val moves = ArrayList<ReversiMove>(32)
    for (i in 0 until REVERSI_CELLS) {
        if (board[i] != REVERSI_EMPTY) continue
        val flips = reversiFlipsIfMove(board, i, color)
        if (flips > 0) moves.add(ReversiMove(i, flips))
    }
    return moves
}

/** 是否存在合法落子（不分配列表，供终局判定热路径使用）。 */
internal fun reversiHasLegalMove(
    board: IntArray,
    color: Int,
): Boolean {
    for (i in 0 until REVERSI_CELLS) {
        if (board[i] == REVERSI_EMPTY && reversiFlipsIfMove(board, i, color) > 0) return true
    }
    return false
}

/** 合法落子数（不分配列表，供评估函数计算机动性）。 */
internal fun reversiCountLegalMoves(
    board: IntArray,
    color: Int,
): Int {
    var count = 0
    for (i in 0 until REVERSI_CELLS) {
        if (board[i] == REVERSI_EMPTY && reversiFlipsIfMove(board, i, color) > 0) count++
    }
    return count
}

internal fun reversiFlipsIfMove(
    board: IntArray,
    index: Int,
    color: Int,
): Int {
    if (index !in 0 until REVERSI_CELLS || board[index] != REVERSI_EMPTY) return 0
    var total = 0
    val opp = reversiOpponent(color)
    val row = reversiRow(index)
    val col = reversiCol(index)
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            total += reversiLineFlips(board, row, col, dr, dc, color, opp)
        }
    }
    return total
}

private fun reversiLineFlips(
    board: IntArray,
    row: Int,
    col: Int,
    dr: Int,
    dc: Int,
    color: Int,
    opp: Int,
): Int {
    var r = row + dr
    var c = col + dc
    var seenOpp = 0
    while (r in 0 until REVERSI_SIZE && c in 0 until REVERSI_SIZE) {
        when (board[reversiIndex(r, c)]) {
            opp -> seenOpp++
            color -> return if (seenOpp > 0) seenOpp else 0
            else -> return 0
        }
        r += dr
        c += dc
    }
    return 0
}

internal fun reversiApplyMove(
    board: IntArray,
    index: Int,
    color: Int,
): Int {
    val flips = reversiFlipsIfMove(board, index, color)
    if (flips <= 0) return 0
    board[index] = color
    val row = reversiRow(index)
    val col = reversiCol(index)
    val opp = reversiOpponent(color)
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            reversiFlipLine(board, row, col, dr, dc, color, opp)
        }
    }
    return flips
}

private fun reversiFlipLine(
    board: IntArray,
    row: Int,
    col: Int,
    dr: Int,
    dc: Int,
    color: Int,
    opp: Int,
) {
    var r = row + dr
    var c = col + dc
    val toFlip = ArrayList<Int>()
    while (r in 0 until REVERSI_SIZE && c in 0 until REVERSI_SIZE) {
        val idx = reversiIndex(r, c)
        when (board[idx]) {
            opp -> toFlip.add(idx)
            color -> {
                if (toFlip.isNotEmpty()) {
                    toFlip.forEach { board[it] = color }
                }
                return
            }
            else -> return
        }
        r += dr
        c += dc
    }
}

internal fun reversiAdvanceTurn(
    board: IntArray,
    turn: Int,
): Pair<Int, Boolean> {
    val opp = reversiOpponent(turn)
    if (reversiLegalMoves(board, opp).isNotEmpty()) return opp to false
    if (reversiLegalMoves(board, turn).isNotEmpty()) return turn to true
    return turn to true
}

internal fun reversiIsGameOver(board: IntArray): Boolean =
    !reversiHasLegalMove(board, REVERSI_BLACK) && !reversiHasLegalMove(board, REVERSI_WHITE)

internal fun reversiOutcome(board: IntArray): ReversiOutcome {
    val black = reversiCount(board, REVERSI_BLACK)
    val white = reversiCount(board, REVERSI_WHITE)
    return when {
        black > white -> ReversiOutcome.BLACK_WIN
        white > black -> ReversiOutcome.WHITE_WIN
        else -> ReversiOutcome.TIE
    }
}

internal fun reversiCornerBonus(index: Int): Int {
    val r = reversiRow(index)
    val c = reversiCol(index)
    if ((r == 0 || r == REVERSI_SIZE - 1) && (c == 0 || c == REVERSI_SIZE - 1)) return 100
    if (r == 0 || r == REVERSI_SIZE - 1 || c == 0 || c == REVERSI_SIZE - 1) return 8
    return 0
}

/** 四角下标（0,0 / 0,7 / 7,0 / 7,7）。 */
private val REVERSI_CORNER_INDICES = intArrayOf(0, 7, 56, 63)

/**
 * 空角相邻格（X/C 方块）的危险度：占据者会"送角"，故对自己扣分、对对手加分；
 * 对角（X）比正交（C）更危险。四角已占时对应邻格不再惩罚。
 */
private fun reversiDangerPenalty(
    board: IntArray,
    forColor: Int,
): Int {
    val opp = reversiOpponent(forColor)
    var danger = 0
    for (corner in REVERSI_CORNER_INDICES) {
        if (board[corner] != REVERSI_EMPTY) continue
        val cr = reversiRow(corner)
        val cc = reversiCol(corner)
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = cr + dr
                val c = cc + dc
                if (r !in 0 until REVERSI_SIZE || c !in 0 until REVERSI_SIZE) continue
                val weight = if (dr != 0 && dc != 0) 40 else 20
                when (board[reversiIndex(r, c)]) {
                    forColor -> danger -= weight
                    opp -> danger += weight
                }
            }
        }
    }
    return danger
}

internal fun reversiEvaluate(
    board: IntArray,
    forColor: Int,
): Int {
    val opp = reversiOpponent(forColor)
    val mine = reversiCount(board, forColor)
    val theirs = reversiCount(board, opp)
    val mobility =
        reversiCountLegalMoves(board, forColor) - reversiCountLegalMoves(board, opp)
    var corners = 0
    for (i in 0 until REVERSI_CELLS) {
        if (board[i] == forColor) {
            corners += reversiCornerBonus(i)
        } else if (board[i] == opp) {
            corners -= reversiCornerBonus(i)
        }
    }
    // 机动性权重提高、避开空角相邻的"送角"格；子数只在残局（空位 ≤12）才计入，避免中局贪子
    val empties = board.count { it == REVERSI_EMPTY }
    val discWeight = if (empties <= 12) 3 else 1
    return (mine - theirs) * discWeight + mobility * 8 + corners + reversiDangerPenalty(board, forColor)
}

/** 单盘 apply/undo 的翻转栈上限：搜索最深 7 层，每层单步最多翻转 63 格。 */
private const val REVERSI_FLIP_LOG_CAPACITY = REVERSI_CELLS * 8

/**
 * 原地落子，并把被翻转的格子按顺序写入 [flipLog]（自 [top] 起），返回新的栈顶下标，
 * 供 [reversiUndoMove] 回退。翻转语义与 [reversiApplyMove] 完全一致。
 */
private fun reversiApplyMoveLogged(
    board: IntArray,
    index: Int,
    color: Int,
    flipLog: IntArray,
    top: Int,
): Int {
    board[index] = color
    val row = reversiRow(index)
    val col = reversiCol(index)
    val opp = reversiOpponent(color)
    var t = top
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            var r = row + dr
            var c = col + dc
            var run = 0
            while (r in 0 until REVERSI_SIZE && c in 0 until REVERSI_SIZE && board[reversiIndex(r, c)] == opp) {
                run++
                r += dr
                c += dc
            }
            if (run == 0 || r !in 0 until REVERSI_SIZE || c !in 0 until REVERSI_SIZE) continue
            if (board[reversiIndex(r, c)] != color) continue
            var fr = row + dr
            var fc = col + dc
            repeat(run) {
                val fi = reversiIndex(fr, fc)
                board[fi] = color
                flipLog[t++] = fi
                fr += dr
                fc += dc
            }
        }
    }
    return t
}

/** 回退 [reversiApplyMoveLogged]：把 [top, newTop) 记录的格子还原为对手色，并清空落子点。 */
private fun reversiUndoMove(
    board: IntArray,
    index: Int,
    color: Int,
    flipLog: IntArray,
    top: Int,
    newTop: Int,
) {
    val opp = reversiOpponent(color)
    for (i in top until newTop) board[flipLog[i]] = opp
    board[index] = REVERSI_EMPTY
}

internal fun reversiChooseAiMove(
    board: IntArray,
    difficulty: ReversiDifficulty,
): Int? {
    val moves = reversiLegalMoves(board, REVERSI_WHITE)
    if (moves.isEmpty()) return null
    if (difficulty.greedyOnly) {
        return moves.minByOrNull { it.flips + reversiCornerBonus(it.index) }?.index
    }
    // 单盘原地 apply/undo：避免每个搜索节点 copyOf(64) 的分配
    val work = board.copyOf()
    val flipLog = IntArray(REVERSI_FLIP_LOG_CAPACITY)
    var bestScore = Int.MIN_VALUE
    var best: Int? = null
    for (move in moves) {
        val newTop = reversiApplyMoveLogged(work, move.index, REVERSI_WHITE, flipLog, 0)
        val score =
            reversiMinimax(
                work,
                difficulty.searchDepth - 1,
                REVERSI_BLACK,
                Int.MIN_VALUE / 4,
                Int.MAX_VALUE / 4,
                flipLog,
                newTop,
            )
        reversiUndoMove(work, move.index, REVERSI_WHITE, flipLog, 0, newTop)
        if (score > bestScore || (score == bestScore && best != null && reversiCornerBonus(move.index) > reversiCornerBonus(best))) {
            bestScore = score
            best = move.index
        }
    }
    return best ?: moves.first().index
}

private fun reversiMinimax(
    board: IntArray,
    depth: Int,
    turn: Int,
    alpha: Int,
    beta: Int,
    flipLog: IntArray,
    flipTop: Int,
): Int {
    if (depth <= 0 || reversiIsGameOver(board)) {
        return reversiEvaluate(board, REVERSI_WHITE)
    }
    val moves = reversiLegalMoves(board, turn)
    if (moves.isEmpty()) {
        // 该方无子可下（过手）：终局已在函数开头排除，故由对方续走
        return reversiMinimax(board, depth - 1, reversiOpponent(turn), alpha, beta, flipLog, flipTop)
    }
    var a = alpha
    var b = beta
    if (turn == REVERSI_WHITE) {
        var best = Int.MIN_VALUE / 2
        for (move in moves) {
            val newTop = reversiApplyMoveLogged(board, move.index, REVERSI_WHITE, flipLog, flipTop)
            val score = reversiMinimax(board, depth - 1, REVERSI_BLACK, a, b, flipLog, newTop)
            reversiUndoMove(board, move.index, REVERSI_WHITE, flipLog, flipTop, newTop)
            best = maxOf(best, score)
            a = maxOf(a, score)
            if (a >= b) break
        }
        return best
    }
    var best = Int.MAX_VALUE / 2
    for (move in moves) {
        val newTop = reversiApplyMoveLogged(board, move.index, REVERSI_BLACK, flipLog, flipTop)
        val score = reversiMinimax(board, depth - 1, REVERSI_WHITE, a, b, flipLog, newTop)
        reversiUndoMove(board, move.index, REVERSI_BLACK, flipLog, flipTop, newTop)
        best = minOf(best, score)
        b = minOf(b, score)
        if (a >= b) break
    }
    return best
}
