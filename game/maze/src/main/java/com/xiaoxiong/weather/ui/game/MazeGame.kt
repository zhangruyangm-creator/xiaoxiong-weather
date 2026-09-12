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
)

private val sessionJson = Json { ignoreUnknownKeys = true }

private const val MAZE_SESSION_KEY = "maze_session_v1"

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

    /** 是否有可续玩的无尽对局：主动退出/意外中断都会保留，结算后清除。 */
    var resumeEndless = false
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

    fun loadProgress(prefs: android.content.SharedPreferences) {
        unlocked = prefs.getInt("maze_unlocked", 1).coerceIn(1, MAZE_CAMPAIGN_SIZE)
        for (i in stars.indices) {
            stars[i] = prefs.getInt("maze_star_$i", 0).coerceIn(0, 3)
        }
        endlessBest = prefs.getInt("maze_endless_best", 0)
    }

    fun saveProgress(prefs: android.content.SharedPreferences) {
        prefs.edit().apply {
            putInt("maze_unlocked", unlocked)
            stars.forEachIndexed { i, s -> putInt("maze_star_$i", s) }
            putInt("maze_endless_best", endlessBest)
            apply()
        }
    }

    /** 局中会话落盘：旋转屏幕等进程重建后由 [restoreSession] 恢复；选关阶段只清理/保留旧会话，不写新快照。 */
    fun saveSession(prefs: android.content.SharedPreferences) {
        if (phase == MazePhase.CLEAR) {
            prefs.edit { remove(MAZE_SESSION_KEY) }
            return
        }
        // 选关界面：战役对局视为放弃（删除）；无尽对局保留已有的落盘会话，
        // 不能在这里另写一份（openSelect 已把 endless/levelIndex 清成战役值，会覆盖正确存档）
        if (phase == MazePhase.SELECT) {
            if (!resumeEndless) prefs.edit { remove(MAZE_SESSION_KEY) }
            return
        }
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
            )
        prefs.edit { putString(MAZE_SESSION_KEY, sessionJson.encodeToString(MazeSession.serializer(), session)) }
    }

    /** 恢复局中会话；无会话或解析失败返回 false。恢复后保留快照，供 ON_STOP/onDispose 再次落盘。 */
    fun restoreSession(prefs: android.content.SharedPreferences): Boolean {
        val raw = prefs.getString(MAZE_SESSION_KEY, null) ?: return false
        val session =
            runCatching { sessionJson.decodeFromString(MazeSession.serializer(), raw) }.getOrNull()
                ?: return false
        // 先按关卡/种子重建初始棋盘（含全部蜂蜜/钥匙/锁），再覆盖为已消耗状态
        if (session.endless) {
            endlessSessionSeed = session.seed
            resumeEndless = true
            startEndless(session.endlessWave, freshSeed = false)
        } else {
            resumeEndless = false
            startLevel(session.levelIndex.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1))
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
        moveT = 1f
        sliding = false
        phase = MazePhase.PLAYING
        return true
    }

    fun discardSavedSession(prefs: android.content.SharedPreferences) {
        prefs.edit { remove(MAZE_SESSION_KEY) }
    }

    fun openSelect() {
        phase = MazePhase.SELECT
        cleared = false
        endless = false
        // 若在移动/冰面滑行动画中退出，立即结束动画，
        // 否则帧循环继续推进 moveT，落地事件可能在选关界面触发 finishLevel 弹结算
        sliding = false
        moveT = 1f
    }

    fun startLevel(index: Int) {
        endless = false
        resumeEndless = false
        levelIndex = index.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1)
        loadLevel(mazeCampaignLevel(levelIndex))
    }

    /** 无尽随机：每波用新种子生成，难度随波次上升。 */
    fun startEndless(
        wave: Int = 1,
        freshSeed: Boolean = true,
    ) {
        endless = true
        resumeEndless = true
        endlessWave = wave.coerceAtLeast(1)
        // 仅在开新局时掷会话种子；重开（freshSeed=false）复用当前种子，保证同一张图
        if (freshSeed) endlessSessionSeed = Random.nextLong()
        levelIndex = -1
        val diff = MazeGameLogic.endlessDifficulty(endlessWave)
        val seed = endlessSessionSeed xor (endlessWave.toLong() * 0x9E3779B9L)
        loadLevel(
            generateMazeLevel(
                seed = seed,
                id = endlessWave,
                difficulty = diff,
            ).copy(
                titleZh = "无尽 · 第${endlessWave}波",
                titleEn = "Endless #$endlessWave",
            ),
        )
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
    }

    fun restart() {
        if (endless) {
            // 重开复用当前会话种子，保持与战役「重开同一关」语义一致
            startEndless(endlessWave, freshSeed = false)
        } else {
            startLevel(levelIndex)
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

    /** 步进动画结束时结算格子事件；若在冰上则继续滑。 */
    fun onStepLanded(): Boolean {
        val cell = grid[bearR][bearC]
        var haptic = false
        when (cell.tile) {
            MazeTile.HONEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                honeyLeft = (honeyLeft - 1).coerceAtLeast(0)
                // 收齐最后一罐：记录此刻时间，出口门从这一秒开始播开启动画
                if (honeyLeft == 0) lastHoneyAt = time
                haptic = true
            }
            MazeTile.KEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                keys++
                haptic = true
            }
            MazeTile.LOCK -> {
                if (keys > 0) {
                    keys--
                    grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                    haptic = true
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
                            haptic = true
                            break@outer
                        }
                    }
                }
            }
            MazeTile.EXIT -> {
                if (honeyLeft == 0) {
                    finishLevel()
                    return true
                } else {
                    needHoneyHintT = 2.2f
                    haptic = true
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
        return haptic
    }

    private fun finishLevel() {
        cleared = true
        phase = MazePhase.CLEAR
        // 结算后不再续玩（死亡/通关后下次从新局开始）
        resumeEndless = false
        val budget = level.moveBudget
        // 仅在蜂蜜收齐后踩出口才会到这里，直接按步数定星级
        clearStars = MazeGameLogic.starsFor(moves, budget)
        if (endless) {
            if (endlessWave > endlessBest) endlessBest = endlessWave
        } else if (levelIndex in stars.indices) {
            if (clearStars > stars[levelIndex]) stars[levelIndex] = clearStars
            val next = levelIndex + 2 // 1-based unlock count
            if (next > unlocked) unlocked = next.coerceAtMost(MAZE_CAMPAIGN_SIZE)
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

    fun tick(dt: Float) {
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
                    onStepLanded()
                }
            }
        }
    }

    /** 无尽超步失败：结算界面显示失败，不更新最高波次、不可续玩。 */
    fun failEndless() {
        if (phase != MazePhase.PLAYING) return
        failed = true
        cleared = false
        resumeEndless = false
        phase = MazePhase.CLEAR
    }
}
