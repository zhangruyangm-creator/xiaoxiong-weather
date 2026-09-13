package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.random.Random

internal enum class MazeDir(
    val dr: Int,
    val dc: Int,
) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1),
}

internal enum class MazePhase { SELECT, PLAYING, CLEAR }

/** 一次落地的格子事件类型，供 UI 映射不同触感反馈。 */
internal enum class MazeStepEvent { NONE, HONEY, KEY, LOCK, PORTAL, EXIT_LOCKED, CLEAR }

/** [MazeGame.hint] 的结果：方向 / 确认无解 / 状态空间过大暂不支持提示。 */
internal sealed interface MazeHint {
    data class Direction(
        val dir: MazeDir,
    ) : MazeHint

    data object NoSolution : MazeHint

    data object TooLarge : MazeHint
}

/**
 * 提示求解请求：主线程抓取的不可变局面快照，交给后台线程做纯 BFS。
 * [MazeGame.applyHint] 回写前会校验快照是否仍与当前局面一致；
 * 内容相同即视为同一局面（data class 相等性），供 [MazeHintSession] 缓存复用。
 */
internal data class MazeHintRequest(
    val map: List<String>,
    val bearR: Int,
    val bearC: Int,
    val keys: Int,
)

/**
 * 局中会话快照：进程重建（如旋转屏幕）时恢复当前对局。
 * gridRows 复用 [MazeLevel.parse] 的字符集编码，传送门用 '1'..'9' 表示。
 */
@Serializable
private data class MazeSession(
    val endless: Boolean,
    val levelIndex: Int,
    val endlessWave: Int,
    val seed: Long,
    val gridRows: List<String>,
    val keys: Int,
    val honeyLeft: Int,
    val honeyTotal: Int,
    val moves: Int,
    val facing: String,
    val bearR: Int,
    val bearC: Int,
    val startR: Int,
    val startC: Int,
    val exitR: Int,
    val exitC: Int,
    val cleared: Boolean,
    val clearStars: Int,
    val needHoneyHintT: Float,
    val lastHoneyAt: Float,
    val time: Float,
    val hintsUsed: Int = 0,
    val daily: Boolean = false,
    val dailyDate: Long = 0,
)

/** 落盘会话的类型化摘要：选关页据此展示对应模式的「继续」入口。 */
internal sealed interface SavedSessionInfo {
    /** 战役第 [levelIndex] 关（0 基）。 */
    data class Campaign(
        val levelIndex: Int,
    ) : SavedSessionInfo

    /** 无尽第 [wave] 波。 */
    data class Endless(
        val wave: Int,
    ) : SavedSessionInfo

    /** 每日迷宫（[dateEpochDay] 为 `LocalDate.toEpochDay()`）。 */
    data class Daily(
        val dateEpochDay: Long,
    ) : SavedSessionInfo
}

private val sessionJson = Json { ignoreUnknownKeys = true }

private const val MAZE_SESSION_KEY = "maze_session_v1"

/** 撤销历史上限。 */
private const val UNDO_LIMIT = 30

/**
 * 一步移动前的完整对局快照。棋盘按值深拷贝（[MazeCell] 不可变，浅拷即可），
 * 这样被消耗的蜂蜜/钥匙、被打开的锁、踩过的传送门是否已触发都能精确回滚。
 */
private class MazeSnapshot(
    val grid: Array<Array<MazeCell>>,
    val bearR: Int,
    val bearC: Int,
    val fromR: Int,
    val fromC: Int,
    val facing: MazeDir,
    val moves: Int,
    val honeyLeft: Int,
    val keys: Int,
    val cleared: Boolean,
    val lastHoneyAt: Float,
    val needHoneyHintT: Float,
)

private fun tileToChar(cell: MazeCell): Char =
    when (cell.tile) {
        MazeTile.FLOOR -> '.'
        MazeTile.WALL -> '#'
        MazeTile.START -> 'S'
        MazeTile.EXIT -> 'E'
        MazeTile.HONEY -> 'H'
        MazeTile.ICE -> 'I'
        MazeTile.KEY -> 'K'
        MazeTile.LOCK -> 'L'
        MazeTile.PORTAL -> '0' + cell.portalId
    }

private fun charToCell(ch: Char): MazeCell =
    when (ch) {
        '#' -> MazeCell(MazeTile.WALL)
        'S' -> MazeCell(MazeTile.START)
        'E' -> MazeCell(MazeTile.EXIT)
        'H' -> MazeCell(MazeTile.HONEY)
        'I' -> MazeCell(MazeTile.ICE)
        'K' -> MazeCell(MazeTile.KEY)
        'L' -> MazeCell(MazeTile.LOCK)
        in '1'..'9' -> MazeCell(MazeTile.PORTAL, portalId = ch - '0')
        else -> MazeCell(MazeTile.FLOOR)
    }

internal class MazeGame {
    var phase by mutableStateOf(MazePhase.SELECT)
    var levelIndex by mutableIntStateOf(0)
    var unlocked by mutableIntStateOf(1)

    /** 战役每关最高星级 0..3。 */
    var stars = IntArray(MAZE_CAMPAIGN_SIZE)

    /** 无尽模式：当前波次 / 历史最高。 */
    var endless by mutableStateOf(false)
    var endlessWave by mutableIntStateOf(1)
    var endlessBest by mutableIntStateOf(0)

    /** 进度版本号：星级/解锁变化时自增，供选关页做 remember key，避免逐帧复制 stars。 */
    var progressVersion by mutableIntStateOf(0)
        private set

    /** 棋盘代次：换关 / 重开 / 撤销导致棋盘或位置重置时自增，供对局页失效提示缓存与在途求解。 */
    var boardVersion by mutableIntStateOf(0)
        private set

    private var endlessSessionSeed = Random.nextLong()

    var rows = 0
    var cols = 0
    lateinit var grid: Array<Array<MazeCell>>
    lateinit var currentLevel: MazeLevel
    var startR = 0
    var startC = 0
    var exitR = 0
    var exitC = 0

    var bearR by mutableIntStateOf(0)
    var bearC by mutableIntStateOf(0)
    var keys by mutableIntStateOf(0)
    var honeyLeft by mutableIntStateOf(0)
    var honeyTotal = 0
    var moves by mutableIntStateOf(0)
    var facing by mutableStateOf(MazeDir.RIGHT)
    var cleared by mutableStateOf(false)

    /** 无尽超步失败标记：结算界面显示失败，不更新最高波次、不可续玩。 */
    var failed by mutableStateOf(false)
    var clearStars by mutableIntStateOf(0)

    /** 本局使用提示的次数（结算展示）。 */
    var hintsUsed by mutableIntStateOf(0)

    /** 每日迷宫：按本地日期派生的确定性布局，[dailyDate] 为 `LocalDate.toEpochDay()`。 */
    var daily by mutableStateOf(false)
    var dailyDate = 0L

    /** 站在出口但蜂蜜未收齐时的提示剩余时间（秒）。 */
    var needHoneyHintT by mutableFloatStateOf(0f)

    /** 最后一罐蜂蜜被收集时的 [time]（秒）；-1 表示本关还没有记录（出口直接视为全开）。 */
    var lastHoneyAt = -1f

    // 平滑移动插值：从 (fromR,fromC) → (bearR,bearC)
    var fromR = 0
    var fromC = 0
    var moveT by mutableFloatStateOf(1f) // 1 = 静止
    var sliding = false
    var time = 0f

    val level: MazeLevel get() = currentLevel
    val moving get() = moveT < 1f

    /** 撤销历史（上限 [UNDO_LIMIT]）：每次真正开始移动前压入快照，结算/换关时清空。 */
    private val history = ArrayDeque<MazeSnapshot>()

    /** 当前是否可撤销：仅对局中、不在移动动画中且有历史。 */
    val canUndo: Boolean get() = history.isNotEmpty() && phase == MazePhase.PLAYING && !moving && !cleared

    fun loadProgress(prefs: android.content.SharedPreferences) {
        unlocked = prefs.getInt("maze_unlocked", 1).coerceIn(1, MAZE_CAMPAIGN_SIZE)
        for (i in stars.indices) {
            stars[i] = prefs.getInt("maze_star_$i", 0).coerceIn(0, 3)
        }
        endlessBest = prefs.getInt("maze_endless_best", 0)
        progressVersion++
    }

    fun saveProgress(prefs: android.content.SharedPreferences) {
        prefs.edit().apply {
            putInt("maze_unlocked", unlocked)
            stars.forEachIndexed { i, s -> putInt("maze_star_$i", s) }
            putInt("maze_endless_best", endlessBest)
            apply()
        }
    }

    /**
     * 局中会话落盘：旋转屏幕等进程重建后由 [restoreSession] 恢复。
     * 选关阶段不动落盘会话——选关页可能正展示「继续」入口，只有玩家主动
     * 开新局/放弃（[abandonSession]）或对局结算（CLEAR）才会清档。
     */
    fun saveSession(prefs: android.content.SharedPreferences) {
        if (phase == MazePhase.CLEAR) {
            prefs.edit { remove(MAZE_SESSION_KEY) }
            return
        }
        if (phase == MazePhase.SELECT) return
        if (!::grid.isInitialized) return
        val rowsEncoded = grid.map { row -> row.joinToString("") { tileToChar(it).toString() } }
        val session =
            MazeSession(
                endless = endless,
                levelIndex = levelIndex,
                endlessWave = endlessWave,
                seed = endlessSessionSeed,
                gridRows = rowsEncoded,
                keys = keys,
                honeyLeft = honeyLeft,
                honeyTotal = honeyTotal,
                moves = moves,
                facing = facing.name,
                bearR = bearR,
                bearC = bearC,
                startR = startR,
                startC = startC,
                exitR = exitR,
                exitC = exitC,
                cleared = cleared,
                clearStars = clearStars,
                needHoneyHintT = needHoneyHintT,
                lastHoneyAt = lastHoneyAt,
                time = time,
                hintsUsed = hintsUsed,
                daily = daily,
                dailyDate = dailyDate,
            )
        prefs.edit { putString(MAZE_SESSION_KEY, sessionJson.encodeToString(MazeSession.serializer(), session)) }
    }

    /** 恢复局中会话；无会话或解析失败返回 false。恢复后保留快照，供 ON_STOP/onDispose 再次落盘。 */
    fun restoreSession(prefs: android.content.SharedPreferences): Boolean {
        val raw = prefs.getString(MAZE_SESSION_KEY, null) ?: return false
        val session =
            runCatching { sessionJson.decodeFromString(MazeSession.serializer(), raw) }.getOrNull()
                ?: return false
        // 先按类型重建初始棋盘（含全部蜂蜜/钥匙/锁），再覆盖为已消耗状态
        when {
            session.endless -> {
                endlessSessionSeed = session.seed
                startEndless(session.endlessWave, freshSeed = false)
            }
            session.daily -> startDaily(session.dailyDate)
            else -> startLevel(session.levelIndex.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1))
        }
        session.gridRows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                if (r < rows && c < cols) grid[r][c] = charToCell(ch)
            }
        }
        // 存档坐标必须落在当前重建的棋盘内；否则说明会话来自不同版本/已损坏，
        // 直接丢弃（不恢复），避免后续 grid[bearR][bearC] 越界崩溃。
        val inGrid =
            session.bearR in 0 until rows &&
                session.bearC in 0 until cols &&
                session.startR in 0 until rows &&
                session.startC in 0 until cols &&
                session.exitR in 0 until rows &&
                session.exitC in 0 until cols
        if (!inGrid) {
            prefs.edit { remove(MAZE_SESSION_KEY) }
            return false
        }
        keys = session.keys
        honeyLeft = session.honeyLeft
        honeyTotal = session.honeyTotal
        moves = session.moves
        facing = runCatching { MazeDir.valueOf(session.facing) }.getOrDefault(MazeDir.RIGHT)
        bearR = session.bearR
        bearC = session.bearC
        fromR = session.bearR
        fromC = session.bearC
        startR = session.startR
        startC = session.startC
        exitR = session.exitR
        exitC = session.exitC
        cleared = session.cleared
        clearStars = session.clearStars
        needHoneyHintT = session.needHoneyHintT
        lastHoneyAt = session.lastHoneyAt
        time = session.time
        hintsUsed = session.hintsUsed
        history.clear()
        moveT = 1f
        sliding = false
        phase = MazePhase.PLAYING
        return true
    }

    /**
     * 读取落盘会话的类型化摘要用于「继续」入口，不改变当前对局。
     * 无会话或解析失败时返回 null。
     */
    fun peekSavedSession(prefs: android.content.SharedPreferences): SavedSessionInfo? {
        val raw = prefs.getString(MAZE_SESSION_KEY, null) ?: return null
        val session =
            runCatching { sessionJson.decodeFromString(MazeSession.serializer(), raw) }.getOrNull()
                ?: return null
        return when {
            session.endless -> SavedSessionInfo.Endless(session.endlessWave.coerceAtLeast(1))
            session.daily -> SavedSessionInfo.Daily(session.dailyDate)
            else -> SavedSessionInfo.Campaign(session.levelIndex.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1))
        }
    }

    /** 玩家主动开始新局/放弃时清除落盘会话；saveSession 的选关阶段不再做清理。 */
    fun abandonSession(prefs: android.content.SharedPreferences) {
        prefs.edit { remove(MAZE_SESSION_KEY) }
    }

    fun openSelect() {
        phase = MazePhase.SELECT
        cleared = false
        endless = false
        daily = false
        history.clear()
        // 若在移动/冰面滑行动画中退出，立即结束动画，
        // 否则帧循环继续推进 moveT，落地事件可能在选关界面触发 finishLevel 弹结算
        sliding = false
        moveT = 1f
    }

    fun startLevel(index: Int) {
        endless = false
        daily = false
        levelIndex = index.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1)
        loadLevel(mazeCampaignLevel(levelIndex))
    }

    /** 无尽随机：每波用新种子生成，难度随波次上升。 */
    fun startEndless(
        wave: Int = 1,
        freshSeed: Boolean = true,
    ) {
        endless = true
        daily = false
        endlessWave = wave.coerceAtLeast(1)
        // 仅在开新局时掷会话种子；重开（freshSeed=false）复用当前种子，保证同一张图
        if (freshSeed) endlessSessionSeed = Random.nextLong()
        levelIndex = -1
        val diff = MazeGameLogic.endlessDifficulty(endlessWave)
        loadLevel(
            cachedEndlessLevel(
                sessionSeed = endlessSessionSeed,
                wave = endlessWave,
                difficulty = diff,
            ),
        )
    }

    /** 每日迷宫：按 [dateEpochDay]（本地日 `LocalDate.toEpochDay()`）派生确定性布局。 */
    fun startDaily(dateEpochDay: Long) {
        endless = false
        daily = true
        dailyDate = dateEpochDay
        levelIndex = -1
        loadLevel(dailyMazeLevel(dateEpochDay))
    }

    private fun loadLevel(lv: MazeLevel) {
        currentLevel = lv
        grid = lv.parse()
        rows = lv.rows
        cols = lv.cols
        honeyTotal = 0
        honeyLeft = 0
        keys = 0
        moves = 0
        cleared = false
        failed = false
        clearStars = 0
        needHoneyHintT = 0f
        lastHoneyAt = -1f
        hintsUsed = 0
        history.clear()
        sliding = false
        moveT = 1f
        facing = MazeDir.RIGHT
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (grid[r][c].tile) {
                    MazeTile.START -> {
                        startR = r
                        startC = c
                        bearR = r
                        bearC = c
                        fromR = r
                        fromC = c
                    }
                    MazeTile.EXIT -> {
                        exitR = r
                        exitC = c
                    }
                    MazeTile.HONEY -> {
                        honeyTotal++
                        honeyLeft++
                    }
                    else -> Unit
                }
            }
        }
        phase = MazePhase.PLAYING
        boardVersion++
    }

    fun restart() {
        when {
            daily -> startDaily(dailyDate)
            endless -> {
                // 重开复用当前会话种子，保持与战役「重开同一关」语义一致
                startEndless(endlessWave, freshSeed = false)
            }
            else -> startLevel(levelIndex)
        }
    }

    private fun inBounds(
        r: Int,
        c: Int,
    ) = r in 0 until rows && c in 0 until cols

    private fun walkable(
        r: Int,
        c: Int,
    ): Boolean {
        if (!inBounds(r, c)) return false
        return when (grid[r][c].tile) {
            MazeTile.WALL -> false
            MazeTile.LOCK -> keys > 0
            else -> true
        }
    }

    /** 尝试朝方向迈一步；成功返回 true。冰面会进入滑动。 */
    fun tryMove(dir: MazeDir): Boolean {
        if (phase != MazePhase.PLAYING || moving || cleared) return false
        val nr = bearR + dir.dr
        val nc = bearC + dir.dc
        if (!walkable(nr, nc)) return false
        pushHistory()
        facing = dir
        beginStep(nr, nc)
        return true
    }

    private fun beginStep(
        nr: Int,
        nc: Int,
    ) {
        fromR = bearR
        fromC = bearC
        bearR = nr
        bearC = nc
        moveT = 0f
        moves++
        sliding = grid[nr][nc].tile == MazeTile.ICE
    }

    /** 步进动画结束时结算格子事件；若在冰上则继续滑。返回本次落地的事件类型。 */
    fun onStepLanded(): MazeStepEvent {
        val cell = grid[bearR][bearC]
        var event = MazeStepEvent.NONE
        when (cell.tile) {
            MazeTile.HONEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                honeyLeft = (honeyLeft - 1).coerceAtLeast(0)
                // 收齐最后一罐：记录此刻时间，出口门从这一秒开始播开启动画
                if (honeyLeft == 0) lastHoneyAt = time
                event = MazeStepEvent.HONEY
            }
            MazeTile.KEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                keys++
                event = MazeStepEvent.KEY
            }
            MazeTile.LOCK -> {
                if (keys > 0) {
                    keys--
                    grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                    event = MazeStepEvent.LOCK
                }
            }
            MazeTile.PORTAL -> {
                val id = cell.portalId
                // 找另一端
                outer@ for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if ((r != bearR || c != bearC) &&
                            grid[r][c].tile == MazeTile.PORTAL &&
                            grid[r][c].portalId == id
                        ) {
                            bearR = r
                            bearC = c
                            fromR = r
                            fromC = c
                            moveT = 1f
                            sliding = false
                            event = MazeStepEvent.PORTAL
                            break@outer
                        }
                    }
                }
            }
            MazeTile.EXIT -> {
                if (honeyLeft == 0) {
                    finishLevel()
                    return MazeStepEvent.CLEAR
                } else {
                    needHoneyHintT = 2.2f
                    event = MazeStepEvent.EXIT_LOCKED
                }
            }
            else -> Unit
        }
        // 冰面继续滑
        if (sliding && phase == MazePhase.PLAYING) {
            val nr = bearR + facing.dr
            val nc = bearC + facing.dc
            if (walkable(nr, nc)) {
                fromR = bearR
                fromC = bearC
                bearR = nr
                bearC = nc
                moveT = 0f
                // 滑行不额外计步
                sliding = grid[nr][nc].tile == MazeTile.ICE
            } else {
                sliding = false
            }
        }
        return event
    }

    /** 撤销上一次移动；不可撤销时返回 false。 */
    fun undo(): Boolean {
        if (!canUndo) return false
        val s = history.removeLast()
        grid = s.grid
        bearR = s.bearR
        bearC = s.bearC
        fromR = s.fromR
        fromC = s.fromC
        facing = s.facing
        moves = s.moves
        honeyLeft = s.honeyLeft
        keys = s.keys
        cleared = s.cleared
        lastHoneyAt = s.lastHoneyAt
        needHoneyHintT = s.needHoneyHintT
        moveT = 1f
        sliding = false
        boardVersion++
        return true
    }

    /**
     * 提示：从当前状态求「收齐剩余蜂蜜并到达出口」的下一步方向。
     * 对局已结束/移动中返回 [MazeHint.NoSolution]；状态空间过大返回 [MazeHint.TooLarge]。
     * 成功给出方向时 [hintsUsed] + 1。
     */
    fun hint(): MazeHint {
        if (phase != MazePhase.PLAYING || moving || cleared) return MazeHint.NoSolution
        val map = grid.map { row -> row.joinToString("") { tileToChar(it).toString() } }
        return when (val outcome = mazeSolve(map, bearR, bearC, keys)) {
            is MazeSolveOutcome.Solved -> {
                val dir = outcome.firstDir
                if (dir == null) {
                    MazeHint.NoSolution
                } else {
                    hintsUsed++
                    MazeHint.Direction(dir)
                }
            }
            MazeSolveOutcome.Unsolvable -> MazeHint.NoSolution
            MazeSolveOutcome.TooLarge -> MazeHint.TooLarge
        }
    }

    /**
     * 抓取当前局面的提示求解请求（不可变快照）；对局不在正常操作态时返回 null。
     * BFS 本身可在后台线程执行，结果由 [applyHint] 回主线程校验并写回。
     */
    fun hintRequest(): MazeHintRequest? {
        if (phase != MazePhase.PLAYING || moving || cleared) return null
        return MazeHintRequest(
            map = grid.map { row -> row.joinToString("") { tileToChar(it).toString() } },
            bearR = bearR,
            bearC = bearC,
            keys = keys,
        )
    }

    /** [request] 快照是否仍对应当前局面（棋盘内容 / 位置 / 钥匙 / 阶段一致）。 */
    fun hintRequestMatches(request: MazeHintRequest): Boolean {
        if (phase != MazePhase.PLAYING || moving || cleared) return false
        if (bearR != request.bearR || bearC != request.bearC || keys != request.keys) return false
        if (request.map.size != rows) return false
        for (r in 0 until rows) {
            val row = request.map[r]
            if (row.length != cols) return false
            for (c in 0 until cols) {
                if (row[c] != tileToChar(grid[r][c])) return false
            }
        }
        return true
    }

    /**
     * 回写后台求解结果：仅在快照仍与当前局面一致时计入 [hintsUsed] 并返回提示；
     * 局面已变化（移动 / 撤销 / 换关）时返回 null，由调用方丢弃过期结果。
     *
     * 计数语义：每次成功回写计一次提示。UI 用 [MazeHintSession] 对同一局面缓存
     * 结果、避免重复 BFS 与重复回写，因此正常路径下同一局面至多计一次；
     * 本方法自身不去重（内容相同的 request 重复调用仍各计一次）。
     */
    fun applyHint(
        request: MazeHintRequest,
        outcome: MazeSolveOutcome,
    ): MazeHint? {
        if (!hintRequestMatches(request)) return null
        return when (outcome) {
            is MazeSolveOutcome.Solved -> {
                val dir = outcome.firstDir
                if (dir == null) {
                    MazeHint.NoSolution
                } else {
                    hintsUsed++
                    MazeHint.Direction(dir)
                }
            }
            MazeSolveOutcome.Unsolvable -> MazeHint.NoSolution
            MazeSolveOutcome.TooLarge -> MazeHint.TooLarge
        }
    }

    private fun pushHistory() {
        if (history.size >= UNDO_LIMIT) history.removeFirst()
        history.addLast(
            MazeSnapshot(
                grid = Array(grid.size) { r -> grid[r].copyOf() },
                bearR = bearR,
                bearC = bearC,
                fromR = fromR,
                fromC = fromC,
                facing = facing,
                moves = moves,
                honeyLeft = honeyLeft,
                keys = keys,
                cleared = cleared,
                lastHoneyAt = lastHoneyAt,
                needHoneyHintT = needHoneyHintT,
            ),
        )
    }

    private fun finishLevel() {
        cleared = true
        phase = MazePhase.CLEAR
        history.clear()
        val budget = level.moveBudget
        // 仅在蜂蜜收齐后踩出口才会到这里，直接按步数定星级
        clearStars = MazeGameLogic.starsFor(moves, budget)
        if (endless) {
            if (endlessWave > endlessBest) endlessBest = endlessWave
        } else if (levelIndex in stars.indices) {
            if (clearStars > stars[levelIndex]) stars[levelIndex] = clearStars
            val next = levelIndex + 2 // 1-based unlock count
            if (next > unlocked) unlocked = next.coerceAtMost(MAZE_CAMPAIGN_SIZE)
            progressVersion++
        }
    }

    fun bearDrawPos(): Pair<Float, Float> {
        if (moveT >= 1f) return bearR.toFloat() to bearC.toFloat()
        // easeOutQuad
        val t = moveT
        val e = 1f - (1f - t) * (1f - t)
        val r = fromR + (bearR - fromR) * e
        val c = fromC + (bearC - fromC) * e
        return r to c
    }

    fun tick(dt: Float): MazeStepEvent {
        time += dt
        if (needHoneyHintT > 0f) {
            needHoneyHintT = (needHoneyHintT - dt).coerceAtLeast(0f)
        }
        if (moving && phase == MazePhase.PLAYING) {
            val speed = if (sliding) 7.5f else 5.5f
            moveT = min(1f, moveT + dt * speed)
            if (moveT >= 1f) {
                moveT = 1f
                // 无尽超步：落地前判定，超步即失败（失败优先于拾蜜/踩出口）
                if (endless && moves > level.moveBudget) {
                    failEndless()
                } else {
                    return onStepLanded()
                }
            }
        }
        return MazeStepEvent.NONE
    }

    /** 无尽超步失败：结算界面显示失败，不更新最高波次、不可续玩。 */
    fun failEndless() {
        if (phase != MazePhase.PLAYING) return
        failed = true
        cleared = false
        history.clear()
        phase = MazePhase.CLEAR
    }
}
