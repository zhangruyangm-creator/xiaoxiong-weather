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

internal fun reversiFlipsIfMove(
    board: IntArray,
    index: Int,
    color: Int,
): Int {
    if (board[index] != REVERSI_EMPTY) return 0
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

internal fun reversiIsGameOver(board: IntArray): Boolean {
    val b = reversiLegalMoves(board, REVERSI_BLACK)
    val w = reversiLegalMoves(board, REVERSI_WHITE)
    return b.isEmpty() && w.isEmpty()
}

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

internal fun reversiEvaluate(
    board: IntArray,
    forColor: Int,
): Int {
    val opp = reversiOpponent(forColor)
    val mine = reversiCount(board, forColor)
    val theirs = reversiCount(board, opp)
    val mobility =
        reversiLegalMoves(board, forColor).size - reversiLegalMoves(board, opp).size
    var corners = 0
    for (i in 0 until REVERSI_CELLS) {
        if (board[i] == forColor) {
            corners += reversiCornerBonus(i)
        } else if (board[i] == opp) {
            corners -= reversiCornerBonus(i)
        }
    }
    return (mine - theirs) * 2 + mobility * 5 + corners
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
    var bestScore = Int.MIN_VALUE
    var best: Int? = null
    for (move in moves) {
        val copy = board.copyOf()
        reversiApplyMove(copy, move.index, REVERSI_WHITE)
        val score = -reversiMinimax(copy, difficulty.searchDepth - 1, REVERSI_BLACK, Int.MIN_VALUE / 4, Int.MAX_VALUE / 4)
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
): Int {
    if (depth <= 0 || reversiIsGameOver(board)) {
        return reversiEvaluate(board, REVERSI_WHITE)
    }
    val moves = reversiLegalMoves(board, turn)
    if (moves.isEmpty()) {
        val (_, skipped) = reversiAdvanceTurn(board, turn)
        if (skipped && reversiIsGameOver(board)) return reversiEvaluate(board, REVERSI_WHITE)
        return reversiMinimax(board, depth - 1, reversiOpponent(turn), alpha, beta)
    }
    var a = alpha
    var b = beta
    if (turn == REVERSI_WHITE) {
        var best = Int.MIN_VALUE / 2
        for (move in moves) {
            val copy = board.copyOf()
            reversiApplyMove(copy, move.index, REVERSI_WHITE)
            val score = reversiMinimax(copy, depth - 1, REVERSI_BLACK, a, b)
            best = maxOf(best, score)
            a = maxOf(a, score)
            if (a >= b) break
        }
        return best
    }
    var best = Int.MAX_VALUE / 2
    for (move in moves) {
        val copy = board.copyOf()
        reversiApplyMove(copy, move.index, REVERSI_BLACK)
        val score = reversiMinimax(copy, depth - 1, REVERSI_WHITE, a, b)
        best = minOf(best, score)
        b = minOf(b, score)
        if (a >= b) break
    }
    return best
}
