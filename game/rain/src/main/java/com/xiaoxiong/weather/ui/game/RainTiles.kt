package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaoxiong.weather.game.booleanOr
import com.xiaoxiong.weather.game.intOr
import kotlin.math.ceil

/** 默认列数；一屏四行：和「别踩白块儿」同一套格子。 */
internal const val TILES_COLUMNS_DEFAULT = 4
internal const val TILES_COLUMNS_MIN = 2
internal const val TILES_COLUMNS_MAX = 4
internal const val RAIN_ROWS_ON_SCREEN = 4

/** @deprecated 测试与文档沿用；运行期列数以 [RainTiles.columnCount] 为准。 */
internal const val RAIN_COLUMNS = TILES_COLUMNS_DEFAULT

internal const val RAIN_BEST_KEY = "tiles_best"
internal const val RAIN_LEADERBOARD_KEY = "leaderboard_tiles_v1"

/** 是否生成「露耳朵、连点两次」的黑块；默认开。 */
internal const val TILES_PREF_DOUBLE = "tiles_double_tap"

/** 棋盘列数 2 / 3 / 4；默认 4。 */
internal const val TILES_PREF_COLUMNS = "tiles_columns"

internal fun tilesDoubleTapEnabled(prefs: SharedPreferences): Boolean = prefs.booleanOr(TILES_PREF_DOUBLE, default = true)

internal fun tilesColumnCount(prefs: SharedPreferences): Int =
    prefs.intOr(TILES_PREF_COLUMNS, TILES_COLUMNS_DEFAULT).coerceIn(TILES_COLUMNS_MIN, TILES_COLUMNS_MAX)

internal enum class RainPhase {
    READY,
    PLAYING,
    OVER,
}

internal enum class RainBlackKind {
    /** 普通黑块，点一次过关。 */
    PLAIN,

    /** 带小熊脸，点一次过关，表现层加特效。 */
    BEAR,

    /** 先点一次露出小熊，再点同一格才计分。 */
    DOUBLE,
}

internal enum class RainTapResult {
    HIT,
    HIT_BEAR,
    HIT_DOUBLE_PRIMED,
    HIT_DOUBLE_CLEAR,
    WHITE,

    /** 开局未滚前点白：否定反馈，不结束本局。 */
    WHITE_WARMUP,
    IGNORE,
}

internal enum class RainFail {
    NONE,
    WHITE,
    MISS,
}

/**
 * 下落速度（行 / 秒）。点中第一块之后才开始动。
 *
 * 曲线分三段，避免一上来就逼手速、后期又无限变快：
 * - 0–10 分：几乎不涨（练熟列数与整块判定）
 * - 11–40 分：正常线性加速
 * - 40 分后：边际递减，软顶 [MAX]
 *
 * 一屏 4 行：在 [START] 下约 2.2 秒滚过一整屏；[MAX] 时整屏约 0.67 秒。
 */
internal object RainSpeed {
    const val START = 1.85f
    const val PER_HIT = 0.032f

    /** 逻辑行/秒软顶（再高分也不再加快）。 */
    const val MAX = 6.0f

    private const val WARMUP_SCORE = 10
    private const val WARMUP_PER_HIT = 0.012f
    private const val MID_SCORE = 40

    fun rowsPerSecond(score: Int): Float {
        val s = score.coerceAtLeast(0)
        val bonus =
            when {
                s <= WARMUP_SCORE -> s * WARMUP_PER_HIT
                s <= MID_SCORE -> WARMUP_SCORE * WARMUP_PER_HIT + (s - WARMUP_SCORE) * PER_HIT
                else -> {
                    val mid =
                        WARMUP_SCORE * WARMUP_PER_HIT +
                            (MID_SCORE - WARMUP_SCORE) * PER_HIT
                    mid + (s - MID_SCORE) * PER_HIT * 0.55f
                }
            }
        return (START + bonus).coerceAtMost(MAX)
    }

    /** 该分数下滚过一整屏（4 行）约需秒数，便于调参与文档。 */
    fun secondsPerScreen(score: Int): Float = RAIN_ROWS_ON_SCREEN / rowsPerSecond(score)
}

internal fun rainColAt(
    nx: Float,
    columns: Int = TILES_COLUMNS_DEFAULT,
): Int {
    if (!nx.isFinite()) return 0
    val cols = columns.coerceIn(TILES_COLUMNS_MIN, TILES_COLUMNS_MAX)
    return (nx.coerceIn(0f, 0.9999f) * cols).toInt()
}

internal fun rainRowAt(
    scroll: Float,
    ny: Float,
): Int {
    if (!scroll.isFinite() || !ny.isFinite()) return Int.MIN_VALUE
    val t = ny.coerceIn(0f, 0.9999f) * RAIN_ROWS_ON_SCREEN
    return ceil((scroll - t).toDouble()).toInt()
}

internal fun rainCellTopPx(
    scroll: Float,
    row: Int,
    canvasH: Float,
): Float = (scroll - row) * (canvasH / RAIN_ROWS_ON_SCREEN)

internal fun rainCellLeftPx(
    col: Int,
    canvasW: Float,
    columns: Int = TILES_COLUMNS_DEFAULT,
): Float {
    val cols = columns.coerceIn(TILES_COLUMNS_MIN, TILES_COLUMNS_MAX)
    return col * (canvasW / cols)
}

internal fun rainBlackCol(
    row: Int,
    seed: Long,
    columns: Int = TILES_COLUMNS_DEFAULT,
): Int {
    val cols = columns.coerceIn(TILES_COLUMNS_MIN, TILES_COLUMNS_MAX)
    val mixed = seed xor (row.toLong() * -7046029254386353131L)
    return (mixed ushr 32).toInt().mod(cols)
}

/** 黑块玩法类型：由 [row] + [seed] 决定，档位看已解锁的行号（不用分数，避免双连点第一击后变型）。 */
internal fun rainBlackKind(
    row: Int,
    seed: Long,
    allowDouble: Boolean = true,
): RainBlackKind {
    val mixed = (seed xor (row.toLong() * 6364136223846793005L)).ushr(8).toInt()
    val bucket = (mixed and 0x7FFF_FFFF) % 10
    val kind =
        when {
            row < 8 ->
                when {
                    bucket <= 2 -> RainBlackKind.BEAR
                    else -> RainBlackKind.PLAIN
                }
            row < 35 ->
                when {
                    bucket == 0 -> RainBlackKind.DOUBLE
                    bucket <= 2 -> RainBlackKind.BEAR
                    else -> RainBlackKind.PLAIN
                }
            else ->
                when {
                    bucket <= 1 -> RainBlackKind.DOUBLE
                    bucket <= 3 -> RainBlackKind.BEAR
                    else -> RainBlackKind.PLAIN
                }
        }
    if (!allowDouble && kind == RainBlackKind.DOUBLE) return RainBlackKind.PLAIN
    return kind
}

/**
 * 小熊白块引擎：别踩白块儿 + 小熊块 / 双连点块。
 *
 * 坐标全是行/列，不含像素。画布换算只走 [rainCellTopPx] / [rainRowAt]。
 */
internal class RainTiles {
    var phase by mutableStateOf(RainPhase.READY)
        private set
    var scroll: Float = 0f
        private set
    var score: Int = 0
        private set
    var seed: Long = 1L
        private set
    var moving: Boolean = false
        private set
    var fail: RainFail = RainFail.NONE
        private set
    var failRow: Int = 0
        private set
    var failCol: Int = 0
        private set
    var columnCount: Int = TILES_COLUMNS_DEFAULT
        private set

    private val cleared = HashSet<Int>()
    private val primed = HashSet<Int>()
    private var nextMissRow: Int = 0
    var allowDoubleTap: Boolean = true
        private set

    fun isCleared(row: Int): Boolean = row in cleared

    fun isPrimed(row: Int): Boolean = row in primed

    fun kindAt(row: Int): RainBlackKind = rainBlackKind(row, seed, allowDoubleTap)

    fun start(
        seed: Long = 1L,
        allowDouble: Boolean = true,
        columns: Int = TILES_COLUMNS_DEFAULT,
    ) {
        this.seed = seed
        allowDoubleTap = allowDouble
        columnCount = columns.coerceIn(TILES_COLUMNS_MIN, TILES_COLUMNS_MAX)
        phase = RainPhase.PLAYING
        scroll = (RAIN_ROWS_ON_SCREEN - 1).toFloat()
        score = 0
        moving = false
        fail = RainFail.NONE
        failRow = 0
        failCol = 0
        cleared.clear()
        primed.clear()
        nextMissRow = 0
    }

    fun tick(dt: Float) {
        if (phase != RainPhase.PLAYING || !moving) return
        if (!dt.isFinite()) return
        scroll += RainSpeed.rowsPerSecond(score) * dt.coerceIn(0f, 0.05f)
        drainMissed()
    }

    fun tap(
        nx: Float,
        ny: Float,
    ): RainTapResult {
        if (phase != RainPhase.PLAYING) return RainTapResult.IGNORE
        if (!nx.isFinite() || !ny.isFinite()) return RainTapResult.IGNORE
        val col = rainColAt(nx, columnCount)
        val row = rainRowAt(scroll, ny)
        if (row == Int.MIN_VALUE || row < 0) {
            if (!moving) return RainTapResult.WHITE_WARMUP
            lose(RainFail.WHITE, row.coerceAtLeast(0), col)
            return RainTapResult.WHITE
        }
        if (row in cleared) return RainTapResult.IGNORE
        if (rainBlackCol(row, seed, columnCount) != col) {
            if (!moving) return RainTapResult.WHITE_WARMUP
            lose(RainFail.WHITE, row, col)
            return RainTapResult.WHITE
        }
        moving = true
        return when (rainBlackKind(row, seed, allowDoubleTap)) {
            RainBlackKind.DOUBLE ->
                if (row in primed) {
                    primed.remove(row)
                    cleared.add(row)
                    score++
                    RainTapResult.HIT_DOUBLE_CLEAR
                } else {
                    primed.add(row)
                    RainTapResult.HIT_DOUBLE_PRIMED
                }
            RainBlackKind.BEAR -> {
                cleared.add(row)
                score++
                RainTapResult.HIT_BEAR
            }
            RainBlackKind.PLAIN -> {
                cleared.add(row)
                score++
                RainTapResult.HIT
            }
        }
    }

    private fun drainMissed() {
        while (nextMissRow + RAIN_ROWS_ON_SCREEN <= scroll) {
            if (nextMissRow !in cleared) {
                lose(RainFail.MISS, nextMissRow, rainBlackCol(nextMissRow, seed, columnCount))
                return
            }
            primed.remove(nextMissRow)
            nextMissRow++
        }
    }

    private fun lose(
        reason: RainFail,
        row: Int,
        col: Int,
    ) {
        if (phase != RainPhase.PLAYING) return
        phase = RainPhase.OVER
        fail = reason
        failRow = row
        failCol = col
        moving = false
    }

    /** 单测用：把 [row] 对齐到屏底并跳过此前行的漏接判定。 */
    internal fun testingAlignRowAtBottom(row: Int) {
        for (r in 0 until row) cleared.add(r)
        nextMissRow = row
        scroll = row + (RAIN_ROWS_ON_SCREEN - 1).toFloat()
        moving = true
    }
}
