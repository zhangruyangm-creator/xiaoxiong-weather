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
    MASTER("master", 7),
    EXPERT("expert", 8),
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
): Int {
    var n = 0
    for (i in 0 until REVERSI_CELLS) if (board[i] == color) n++
    return n
}

internal fun reversiEmptyCount(board: IntArray): Int {
    var n = 0
    for (i in 0 until REVERSI_CELLS) if (board[i] == REVERSI_EMPTY) n++
    return n
}

/** 空位少于此值时改走精确终局（数子胜负），不再截断评估。 */
internal const val REVERSI_ENDGAME_EMPTIES = 12

/** 大师及以上更早切换精确终局，残局少靠启发式。 */
internal const val REVERSI_MASTER_ENDGAME_EMPTIES = 16

private const val REVERSI_EXACT_NODE_BUDGET = 250_000
private const val REVERSI_MASTER_EXACT_NODE_BUDGET = 400_000

internal fun reversiEndgameEmpties(difficulty: ReversiDifficulty): Int =
    if (difficulty.ordinal >= ReversiDifficulty.MASTER.ordinal) {
        REVERSI_MASTER_ENDGAME_EMPTIES
    } else {
        REVERSI_ENDGAME_EMPTIES
    }

internal fun reversiExactNodeBudget(difficulty: ReversiDifficulty): Int =
    if (difficulty.ordinal >= ReversiDifficulty.MASTER.ordinal) {
        REVERSI_MASTER_EXACT_NODE_BUDGET
    } else {
        REVERSI_EXACT_NODE_BUDGET
    }

/**
 * 位置权重：角最高、边优于中腹；X/C 的「送角」仍由 [reversiDangerPenalty] 在角空时额外扣。
 */
private fun reversiSquareWeight(index: Int): Int {
    val r = minOf(reversiRow(index), REVERSI_SIZE - 1 - reversiRow(index))
    val c = minOf(reversiCol(index), REVERSI_SIZE - 1 - reversiCol(index))
    val inner = minOf(r, c)
    val outer = maxOf(r, c)
    return when {
        inner == 0 && outer == 0 -> 80
        inner == 0 && outer == 1 -> -4
        inner == 1 && outer == 1 -> -8
        inner == 0 && outer == 2 -> 8
        inner == 0 -> 6
        inner == 1 -> -2
        inner == 2 -> 2
        else -> 0
    }
}

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
    var run = 0
    while (r in 0 until REVERSI_SIZE && c in 0 until REVERSI_SIZE) {
        when (board[reversiIndex(r, c)]) {
            opp -> run++
            color -> {
                if (run > 0) {
                    var fr = row + dr
                    var fc = col + dc
                    repeat(run) {
                        board[reversiIndex(fr, fc)] = color
                        fr += dr
                        fc += dc
                    }
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
    if (reversiHasLegalMove(board, opp)) return opp to false
    if (reversiHasLegalMove(board, turn)) return turn to true
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
    var mine = 0
    var theirs = 0
    var position = 0
    var myFrontier = 0
    var oppFrontier = 0
    var empties = 0
    for (i in 0 until REVERSI_CELLS) {
        when (board[i]) {
            REVERSI_EMPTY -> empties++
            forColor -> {
                mine++
                position += reversiSquareWeight(i)
                if (reversiTouchesEmpty(board, i)) myFrontier++
            }
            else -> {
                theirs++
                position -= reversiSquareWeight(i)
                if (reversiTouchesEmpty(board, i)) oppFrontier++
            }
        }
    }
    val mobility =
        reversiCountLegalMoves(board, forColor) - reversiCountLegalMoves(board, opp)
    val discWeight = if (empties <= REVERSI_ENDGAME_EMPTIES) 3 else 1
    return (mine - theirs) * discWeight +
        mobility * 8 +
        position +
        (oppFrontier - myFrontier) * 4 +
        reversiDangerPenalty(board, forColor)
}

private fun reversiTouchesEmpty(
    board: IntArray,
    index: Int,
): Boolean {
    val row = reversiRow(index)
    val col = reversiCol(index)
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = row + dr
            val c = col + dc
            if (r !in 0 until REVERSI_SIZE || c !in 0 until REVERSI_SIZE) continue
            if (board[reversiIndex(r, c)] == REVERSI_EMPTY) return true
        }
    }
    return false
}

private fun reversiSortedMoves(
    board: IntArray,
    color: Int,
): List<ReversiMove> {
    val moves = reversiLegalMoves(board, color)
    if (moves.size <= 1) return moves
    return moves.sortedByDescending { reversiCornerBonus(it.index) * 4 + reversiSquareWeight(it.index) }
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
    val moves = reversiSortedMoves(board, REVERSI_WHITE)
    if (moves.isEmpty()) return null
    if (difficulty.greedyOnly) {
        return moves.minByOrNull { it.flips + reversiCornerBonus(it.index) }?.index
    }
    val work = board.copyOf()
    val flipLog = IntArray(REVERSI_FLIP_LOG_CAPACITY)
    val exactThreshold = reversiEndgameEmpties(difficulty)
    val exactBudget = intArrayOf(reversiExactNodeBudget(difficulty))
    val exactRoot = reversiEmptyCount(board) <= exactThreshold
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
                exactRoot,
                exactBudget,
                exactThreshold,
            )
        reversiUndoMove(work, move.index, REVERSI_WHITE, flipLog, 0, newTop)
        if (score > bestScore ||
            (score == bestScore && best != null && reversiCornerBonus(move.index) > reversiCornerBonus(best))
        ) {
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
    exact: Boolean,
    exactBudget: IntArray,
    exactThreshold: Int,
): Int {
    val inExact = exact || reversiEmptyCount(board) <= exactThreshold
    if (reversiIsGameOver(board)) {
        return reversiCount(board, REVERSI_WHITE) - reversiCount(board, REVERSI_BLACK)
    }
    if (inExact) {
        if (exactBudget[0]-- <= 0) {
            return reversiEvaluate(board, REVERSI_WHITE)
        }
    } else if (depth <= 0) {
        return reversiEvaluate(board, REVERSI_WHITE)
    }
    val moves = reversiSortedMoves(board, turn)
    val nextDepth = if (inExact) depth else depth - 1
    if (moves.isEmpty()) {
        return reversiMinimax(
            board,
            nextDepth,
            reversiOpponent(turn),
            alpha,
            beta,
            flipLog,
            flipTop,
            inExact,
            exactBudget,
            exactThreshold,
        )
    }
    var a = alpha
    var b = beta
    if (turn == REVERSI_WHITE) {
        var best = Int.MIN_VALUE / 2
        for (move in moves) {
            val newTop = reversiApplyMoveLogged(board, move.index, REVERSI_WHITE, flipLog, flipTop)
            val score =
                reversiMinimax(
                    board,
                    nextDepth,
                    REVERSI_BLACK,
                    a,
                    b,
                    flipLog,
                    newTop,
                    inExact,
                    exactBudget,
                    exactThreshold,
                )
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
        val score =
            reversiMinimax(
                board,
                nextDepth,
                REVERSI_WHITE,
                a,
                b,
                flipLog,
                newTop,
                inExact,
                exactBudget,
                exactThreshold,
            )
        reversiUndoMove(board, move.index, REVERSI_BLACK, flipLog, flipTop, newTop)
        best = minOf(best, score)
        b = minOf(b, score)
        if (a >= b) break
    }
    return best
}
