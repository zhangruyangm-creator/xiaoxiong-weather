package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 进行中对局快照的 prefs key（区别于回放用的 `pool_last_replay_v2`）。 */
internal const val POOL_SNAPSHOT_KEY = "pool_snapshot_v2"

/** 旧版本（1.9.0 前）的快照 / 回放 key：载入与清档时显式清理，避免孤儿数据。 */
internal const val POOL_LEGACY_SNAPSHOT_KEY = "pool_snapshot_v1"
internal const val POOL_LEGACY_REPLAY_KEY = "pool_last_replay_v1"

/** 赛制局数范围：设置里 1/2/3 快捷档 + 自定义（1..15）。 */
internal const val POOL_RACE_TO_MIN = 1
internal const val POOL_RACE_TO_MAX = 15

/**
 * 小熊台球运行时：把「固定步长物理内核 + 八球规则 + AI + 输入/瞄准/杆法 + 计分」收口。
 *
 * 坐标系：物理用球桌空间（0..宽, 0..高），屏幕输入经 [toTableX]/[toTableY] 映射。
 */
class PoolGame {
    enum class Phase { READY, AIMING, SHOOTING, FRAME_OVER, MATCH_OVER }

    /** 瞄准辅助等级。 */
    enum class Aids { FULL, GHOST, OFF }

    /** 瞄准杆法：侧旋（-1..1，左右塞）与高低杆（-1 缩杆 / +1 跟杆）。 */
    data class Spin(
        val side: Float = 0f,
        val vertical: Float = 0f,
    )

    /** 一杆的完整输入快照，用于确定性回放（只记玩家/AI 的意图，不记物理结果）。 */
    data class RecordedShot(
        val angle: Float,
        val power: Float,
        val spinSide: Float,
        val spinVertical: Float,
        val elevation: Float,
        /** 出杆前白球位置（自由球/落袋重摆后每次不同，回放需据此复位）。 */
        val cueX: Float = 0f,
        val cueY: Float = 0f,
    )

    var phase by mutableStateOf(Phase.READY)
        private set

    /** 本局（自 [startFrame] 起）每一杆的输入记录，供回放按序重放。 */
    val replayLog: MutableList<RecordedShot> = mutableListOf()

    /** 本局重摆种子与开球方（回放据此重建完全相同的初始球局）。 */
    var frameSeed: Long = 0L
        private set
    var breaker: PoolSide = PoolSide.PLAYER
        private set

    /** 减弱动画：抑制粒子、落袋动画直达、冻结时间脉冲；不改变物理。 */
    var reduceMotion = false

    /** 是否处于回放态（回放期间禁用玩家输入）。 */
    var replaying by mutableStateOf(false)
        private set

    /** 已回放杆数 / 总杆数。 */
    var replayProgress by mutableIntStateOf(0)
        private set
    var replayTotal by mutableIntStateOf(0)
        private set

    private var replayIndex = 0
    private var replayAccumulator = 0f
    private var replayApplied = false
    private var savedPhase: Phase? = null
    private var savedMatch: PoolMatchState? = null
    private var savedFrameWinner: PoolSide? = null

    /** 回放前的完整快照：停止回放时原样还原球局与输入态。 */
    private var savedSnapshotForReplay: PoolSnapshot? = null

    var match by mutableStateOf(PoolMatchState())
        private set

    var difficulty by mutableStateOf(PoolAi.Difficulty.NORMAL)
    var aids by mutableStateOf(Aids.FULL)
    var mode by mutableStateOf(PoolMode.EIGHT_BALL)
    var raceTo by mutableIntStateOf(2)
    var best by mutableIntStateOf(0)
    var resultSubmitted = false

    /** 当前正在进行关卡/每日挑战；普通对局为 null。 */
    var challenge: PoolChallenge? = null
        private set

    /** 当前每日挑战对应的日期（epoch day，0 = 非每日挑战）；快照保存/恢复。 */
    var challengeDate: Long = 0L
        private set

    /** 当前正在进行新手教学；普通对局/关卡为 null。 */
    var lesson by mutableStateOf<PoolLesson?>(null)
        private set

    /** 教学课程是否已达成目标（达成即结束本局）。 */
    var lessonPassed by mutableStateOf(false)
        private set

    /**
     * 台呢主题索引（0..3）。当前仅承载设置与未来渲染接入，渲染层是否读取由同事的
     * [PoolRender] 决定（本字段已按约定暴露在 [PoolGame] 上）。
     */
    var clothTheme by mutableIntStateOf(0)

    /** 最近若干杆摘要（只读），供击球历史面板展示。 */
    private val shotHistoryList = mutableListOf<ShotSummary>()

    val table: PoolTable = PoolTable.standard()

    var balls: MutableList<PoolBall> = mutableListOf()
        private set

    /** 视图变换（球桌 → 画布）。 */
    var viewScale = 1f
        private set
    var viewOffsetX = 0f
        private set
    var viewOffsetY = 0f
        private set
    private var canvasW = 0f
    private var canvasH = 0f

    // 输入/瞄准
    var aimAngle by mutableFloatStateOf(-(Math.PI / 2).toFloat())
        private set
    var power by mutableFloatStateOf(0f)
        private set
    var spin by mutableStateOf(Spin())

    /** 抬杆角度 0..1（0 = 水平；越大越容易出现弧线/masse，与侧旋耦合）。 */
    var elevation by mutableFloatStateOf(0f)
        private set
    var dragging = false
        private set
    var ballInHand by mutableStateOf(false)
        private set
    var placingCue = false
        private set

    // 动画/反馈
    var time by mutableFloatStateOf(0f)
        private set
    var message by mutableStateOf(PoolMessage.NONE)
        private set
    var messageT by mutableFloatStateOf(0f)
        private set
    var frameWinner by mutableStateOf<PoolSide?>(null)
        private set
    var pendingHapticCue = false
    var pendingHapticHit = false
    var pendingHapticPot = false
    var pendingHapticRail = false
    var pendingHapticFoul = false
    val effects = mutableListOf<PoolEffect>()

    private var accumulator = 0f
    private var aiTimer = 0f
    private var shotCount = 0
    private var isBreak = true
    private var shooterClearedBefore = false

    /** 本场累计进球数（不含白球）。 */
    private var potsThisMatch = 0

    /** 本场单杆最多进球数（用于成就「一杆三球」与最高单杆）。 */
    private var bestShotPots = 0

    /** 最近一杆进球数。 */
    var lastShotPots = 0
        private set

    /** 当前这局的开球方与 AI 是否出过杆（判定 break & run）。 */
    private var frameBreaker = PoolSide.PLAYER
    private var aiTouchedInFrame = false

    /** 最近一局结束时玩家是否完成「开球清台」。 */
    var breakAndRunFrame = false
        private set

    /** 本场累计「开球清台」次数（跨局累计，供成就/统计使用）。 */
    var breakRunsThisMatch = 0
        private set

    /** 上一局胜者，用于「胜者开球」。 */
    private var lastFrameWinner: PoolSide? = null

    /**
     * 清掉提示消息（新局/重摆时调用）。不含 pendingHaptic*：这些标志是「本杆待消费」
     * 反馈，UI 在 tick 返回后才会消费；rerack 重摆发生在同一 tick 内，清掉会丢失开球
     * 的落袋/首碰反馈。统一在出杆 [shoot] 时清理，开局/恢复快照另由 [clearPendingFeedback] 兜底。
     */
    private fun resetTransientIndicators() {
        message = PoolMessage.NONE
        messageT = 0f
    }

    /** 清掉尚未被 UI 消费的本杆反馈标志（开局/恢复快照/下一杆出杆前调用）。 */
    private fun clearPendingFeedback() {
        pendingHapticCue = false
        pendingHapticHit = false
        pendingHapticPot = false
        pendingHapticRail = false
        pendingHapticFoul = false
    }

    private var shotFirstContact: PoolBallKind? = null
    private var shotFirstContactNumber: Int? = null
    private var shotContactHappened = false
    private var shotRailAfter = false
    private val shotPotted = mutableListOf<PoolBallKind>()
    private val shotPottedNumbers = mutableListOf<Int>()
    private var shotLowestBefore: Int? = null
    private var shotCuePotted = false
    private var shotCueStartY = 0f

    /** 本杆碰过库的目标球编号（不含白球），翻袋判定用。 */
    private val shotRailedBalls = mutableSetOf<Int>()

    private var aiRng: Random = Random(1)

    private val listener =
        object : PoolPhysics.Listener {
            override fun onBallBall(
                a: PoolBall,
                b: PoolBall,
            ) {
                if (shotFirstContact == null && (a.kind == PoolBallKind.CUE || b.kind == PoolBallKind.CUE)) {
                    val other = if (a.kind == PoolBallKind.CUE) b else a
                    shotFirstContact = other.kind
                    shotFirstContactNumber = other.number
                    shotContactHappened = true
                    // 球-球碰撞：只在每杆的首次白球接触时响一次，避免开球连击刷屏
                    pendingHapticHit = true
                    spawnBurst((a.x + b.x) / 2f, (a.y + b.y) / 2f, 6, 0xFFFFF3C4.toInt())
                } else if (shotContactHappened) {
                    spawnBurst((a.x + b.x) / 2f, (a.y + b.y) / 2f, 4, 0xFFE8EAF6.toInt())
                }
            }

            override fun onRail(ball: PoolBall) {
                if (shotContactHappened) shotRailAfter = true
                if (ball.kind != PoolBallKind.CUE) shotRailedBalls += ball.id
                val speed = ball.speed
                if (speed > 0.35f) {
                    pendingHapticRail = true
                    spawnBurst(ball.x, ball.y, 3, 0xFFD7CCC8.toInt())
                }
            }

            override fun onPocket(ball: PoolBall) {
                ball.pocketAnim = 0.001f
                if (ball.kind == PoolBallKind.CUE) {
                    shotCuePotted = true
                } else {
                    shotPotted += ball.kind
                    shotPottedNumbers += ball.number
                    spawnBurst(ball.x, ball.y, 14, 0xFFFFD54F.toInt())
                }
                pendingHapticPot = true
            }
        }

    // ---------------------------------------------------------------------
    // 视图/输入映射
    // ---------------------------------------------------------------------

    fun resize(
        width: Float,
        height: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        canvasW = width
        canvasH = height
        val pad = 26f
        val usableW = width - pad * 2f
        val usableH = height - pad * 2f
        val scale = min(usableW / table.width, usableH / table.height)
        viewScale = scale
        viewOffsetX = (width - table.width * scale) * 0.5f
        viewOffsetY = (height - table.height * scale) * 0.5f
        clampBallsAfterResize()
    }

    /** 尺寸变化后把球重新夹在台面内，避免拉伸时越界。 */
    private fun clampBallsAfterResize() {
        for (b in balls) {
            if (!b.onTable || b.airborne) continue
            b.x = b.x.coerceIn(table.left + b.radius, table.right - b.radius)
            b.y = b.y.coerceIn(table.top + b.radius, table.bottom - b.radius)
        }
    }

    fun toTableX(canvasX: Float): Float = (canvasX - viewOffsetX) / viewScale

    fun toTableY(canvasY: Float): Float = (canvasY - viewOffsetY) / viewScale

    fun toCanvasX(tableX: Float): Float = viewOffsetX + tableX * viewScale

    fun toCanvasY(tableY: Float): Float = viewOffsetY + tableY * viewScale

    fun cueBall(): PoolBall? = balls.firstOrNull { it.kind == PoolBallKind.CUE }

    // ---------------------------------------------------------------------
    // 开局
    // ---------------------------------------------------------------------

    fun startMatch(
        breaker: PoolSide = PoolSide.PLAYER,
        seed: Long = System.nanoTime(),
    ) {
        challenge = null
        challengeDate = 0L
        lesson = null
        lessonPassed = false
        // 关卡模式不会走普通开局；若误留则退回练习模式
        if (mode == PoolMode.CHALLENGE) mode = PoolMode.PRACTICE
        match =
            PoolMatchState(
                openTable = true,
                turn = breaker,
                mode = mode,
            )
        resultSubmitted = false
        frameWinner = null
        lastFrameWinner = null
        breakRunsThisMatch = 0
        potsThisMatch = 0
        bestShotPots = 0
        resetTransientIndicators()
        clearPendingFeedback()
        aiRng = Random(seed)
        startFrame(breaker, seed)
    }

    /**
     * 开始关卡/每日挑战：按 [challenge] 的 [PoolBallSetup] 指定位置摆球，
     * 采用练习规则（不换人、无犯规、白球落袋自动回台），清台即结束。
     *
     * @param dateEpochDay 每日挑战的日期（epoch day）；普通关卡传 0。
     */
    fun startChallenge(
        challenge: PoolChallenge,
        dateEpochDay: Long = 0L,
    ) {
        this.challenge = challenge
        this.challengeDate = dateEpochDay
        this.lesson = null
        enterPractice(challenge.balls)
    }

    /**
     * 开始一节新手教学：按 [lesson] 的 [PoolBallSetup] 摆球，采用练习规则
     * （不换人、无犯规、白球落袋自动回台）；达到 [PoolLesson.goal] 即判定通过并结束本局。
     */
    fun startLesson(lesson: PoolLesson) {
        this.lesson = lesson
        this.challenge = null
        this.challengeDate = 0L
        enterPractice(lesson.setup)
    }

    /** 教学/关卡共用的摆球与状态复位（练习规则）。 */
    private fun enterPractice(setup: List<PoolBallSetup>) {
        // game.mode 保留玩家上次选择的普通模式，关卡/教学由 match.mode 承载，避免污染模式选择器
        match = PoolMatchState(openTable = true, turn = PoolSide.PLAYER, mode = PoolMode.CHALLENGE)
        replayLog.clear()
        frameSeed = 0L
        breaker = PoolSide.PLAYER
        savedPhase = null
        savedMatch = null
        savedFrameWinner = null
        frameWinner = null
        resultSubmitted = false
        potsThisMatch = 0
        bestShotPots = 0
        lastShotPots = 0
        frameBreaker = PoolSide.PLAYER
        aiTouchedInFrame = false
        breakAndRunFrame = false
        isBreak = false
        ballInHand = false
        placingCue = false
        shotCount = 0
        shotHistoryList.clear()
        lessonPassed = false
        resetTransientIndicators()
        clearPendingFeedback()
        aimAngle = -(Math.PI / 2).toFloat()
        power = 0f
        spin = Spin()
        elevation = 0f
        effects.clear()
        balls = setup.map { PoolBall(it.id, it.kind, it.x, it.y) }.toMutableList()
        // 容错：布局缺白球时补在开球点
        if (cueBall() == null) {
            balls += PoolBall(0, PoolBallKind.CUE, table.centerX, table.headStringY)
        }
        phase = Phase.AIMING
    }

    /** 退出关卡/教学返回主菜单。 */
    fun returnToReady() {
        challenge = null
        challengeDate = 0L
        lesson = null
        lessonPassed = false
        phase = Phase.READY
    }

    private fun startFrame(
        breaker: PoolSide,
        seed: Long,
    ) {
        // 新的一局：清空上一局的回放记录（回放不会走到这里）
        replayLog.clear()
        shotHistoryList.clear()
        frameSeed = seed
        this.breaker = breaker
        savedPhase = null
        savedMatch = null
        savedFrameWinner = null
        rackFrame(breaker, seed, scheduleAi = true)
    }

    /**
     * 按 [seed] 重新摆球并把球局状态复位。回放与正常开局共用，保证初始条件一致。
     * [scheduleAi] 为 false 时（回放）不安排 AI 出杆。
     */
    private fun rackFrame(
        breaker: PoolSide,
        seed: Long,
        scheduleAi: Boolean,
    ) {
        balls = PoolRack.rack(table, PoolRack.standardOrder(seed)).toMutableList()
        match = match.copy(turn = breaker)
        frameBreaker = breaker
        aiTouchedInFrame = false
        breakAndRunFrame = false
        isBreak = true
        ballInHand = false
        placingCue = false
        shotCount = 0
        resetTransientIndicators()
        aimAngle = -(Math.PI / 2).toFloat()
        power = 0f
        spin = Spin()
        elevation = 0f
        effects.clear()
        phase = Phase.AIMING
        if (scheduleAi) scheduleAiIfNeeded()
    }

    /** 进入下一局（或整场结束）。 */
    fun nextFrame() {
        if (mode == PoolMode.PRACTICE || mode == PoolMode.CHALLENGE) {
            phase = Phase.MATCH_OVER
            resultSubmitted = false
            return
        }
        val framesPlayed = match.playerFrames + match.aiFrames
        if (match.playerFrames >= raceTo || match.aiFrames >= raceTo) {
            phase = Phase.MATCH_OVER
            resultSubmitted = false
            return
        }
        frameWinner = null
        // 上一局未消费完的反馈不应串到新一局
        clearPendingFeedback()
        // 上一局赢家开球（无记录时玩家开球）
        val breaker = lastFrameWinner ?: PoolSide.PLAYER
        startFrame(breaker, System.nanoTime() xor (framesPlayed * 7919L))
    }

    fun matchOver(): Boolean =
        phase == Phase.MATCH_OVER ||
            match.playerFrames >= raceTo ||
            match.aiFrames >= raceTo

    // ---------------------------------------------------------------------
    // 输入
    // ---------------------------------------------------------------------

    fun onDown(
        canvasX: Float,
        canvasY: Float,
    ) {
        if (replaying || phase != Phase.AIMING || match.turn != PoolSide.PLAYER) return
        val tx = toTableX(canvasX)
        val ty = toTableY(canvasY)
        if (placingCue) {
            tryPlaceCue(tx, ty)
            return
        }
        dragging = true
        updateAimFrom(tx, ty)
    }

    fun onMove(
        canvasX: Float,
        canvasY: Float,
    ) {
        if (replaying || !dragging || phase != Phase.AIMING || match.turn != PoolSide.PLAYER) return
        updateAimFrom(toTableX(canvasX), toTableY(canvasY))
    }

    fun onUp() {
        if (!dragging) return
        dragging = false
        if (!replaying && !placingCue && power > 0.06f) {
            shoot(aimAngle, power, spin)
        }
        power = 0f
    }

    private fun updateAimFrom(
        tx: Float,
        ty: Float,
    ) {
        val cue = cueBall() ?: return
        val dx = tx - cue.x
        val dy = ty - cue.y
        if (hypot(dx, dy) < cue.radius * 1.2f) return
        aimAngle = atan2(dy, dx)
        // 拖拽距离映射力度：约 0.06m 起步，0.70m 满力
        power = ((hypot(dx, dy) - 0.06f) / 0.64f).coerceIn(0f, 1f)
    }

    /**
     * 自由球时把白球放到台面坐标：台内、不与其他球重叠、不在袋口捕获圈内。
     * 合法点判定与 AI [PoolAi.placeCueBall] 共用 [PoolPhysics.isLegalCuePlacement]。
     */
    fun tryPlaceCue(
        tx: Float,
        ty: Float,
    ): Boolean {
        val cue = cueBall() ?: return false
        if (!PoolPhysics.isLegalCuePlacement(balls, table, cue, tx, ty)) return false
        cue.x = tx
        cue.y = ty
        cue.onTable = true
        cue.potted = false
        cue.stop()
        placingCue = false
        ballInHand = false
        return true
    }

    fun beginPlacing() {
        if (replaying) return
        if (phase == Phase.AIMING && match.turn == PoolSide.PLAYER && ballInHand) {
            placingCue = true
        }
    }

    fun updateSpin(newSpin: Spin) {
        spin = Spin(newSpin.side.coerceIn(-1f, 1f), newSpin.vertical.coerceIn(-1f, 1f))
    }

    fun updateElevation(value: Float) {
        elevation = value.coerceIn(0f, 1f)
    }

    /**
     * 精瞄微调：在当前 [aimAngle] 上叠加 [deltaRadians]，并把结果归一化到 (-π, π]。
     * 仅玩家瞄准阶段生效（回放/非玩家回合/自由球放置时忽略）。供 Screen 的左右微调按钮使用。
     */
    fun nudgeAim(deltaRadians: Float) {
        if (replaying || phase != Phase.AIMING || match.turn != PoolSide.PLAYER || placingCue) return
        aimAngle = PoolMath.normalizeAngle(aimAngle + deltaRadians)
    }

    // ---------------------------------------------------------------------
    // 开杆
    // ---------------------------------------------------------------------

    fun shoot(
        angle: Float,
        shotPower: Float,
        shotSpin: Spin = Spin(),
    ) {
        if (phase != Phase.AIMING || replaying) return
        val cue = cueBall() ?: return
        if (cue.potted || !cue.onTable) return
        val p = shotPower.coerceIn(0.05f, 1f)
        val elev = elevation.coerceIn(0f, 1f)
        val side = shotSpin.side.coerceIn(-1f, 1f)
        val vertical = shotSpin.vertical.coerceIn(-1f, 1f)
        applyShotToCue(angle, p, Spin(side, vertical), elev)
        // 记录实际击球输入（含自由球摆位）供回放确定性重放
        replayLog += RecordedShot(angle, p, side, vertical, elev, cue.x, cue.y)
        resetShotTracking()
        if (match.turn == PoolSide.AI) aiTouchedInFrame = true
        val group = match.groupOf(match.turn)
        shooterClearedBefore = group != null && PoolRules.remaining(balls).remainingFor(group) == 0
        accumulator = 0f
        phase = Phase.SHOOTING
        shotCount++
        // 上一杆未消费完的反馈不应串到本杆；本杆反馈从击球音开始重新累积
        clearPendingFeedback()
        pendingHapticCue = true
    }

    /** 把一杆的物理参数施加到白球（[shoot] 与回放共用，保证推演一致）。 */
    private fun applyShotToCue(
        angle: Float,
        shotPower: Float,
        shotSpin: Spin,
        shotElevation: Float,
    ) {
        val cue = cueBall() ?: return
        val p = shotPower.coerceIn(0.05f, 1f)
        val speed = PoolConfig.shotSpeedMps(p)
        val r = cue.radius
        // 侧旋「偏杆」：右塞白球向左偏（squirt），力度越大越明显
        val elev = shotElevation.coerceIn(0f, 1f)
        val squirt = (-shotSpin.side) * PoolMath.radians(0.85f) * (0.35f + 0.65f * p)
        val dir = angle + squirt
        cue.vx = cos(dir) * speed
        cue.vy = sin(dir) * speed
        when {
            shotSpin.vertical > 0f -> {
                // 跟杆：额外的正向滚动
                val k = shotSpin.vertical
                cue.wx = -cue.vy / r * k
                cue.wy = cue.vx / r * k
            }
            shotSpin.vertical < 0f -> {
                // 缩杆：反向旋转
                val k = -shotSpin.vertical
                cue.wx = cue.vy / r * k
                cue.wy = -cue.vx / r * k
            }
        }
        cue.wz = shotSpin.side * (12f * p + 4f)
        // 弧线：抬杆 + 侧旋耦合；水平杆不产生弧线
        cue.curve = shotSpin.side * elev * 3.2f
        // 跳球：抬杆与力度同时达标时给白球起跳竖直速度（越高越大力跳得越高越远）
        cue.z = 0f
        cue.vz =
            if (elev >= PoolConfig.JUMP_ELEVATION_MIN && p >= PoolConfig.JUMP_POWER_MIN) {
                PoolConfig.GRAVITY * PoolConfig.JUMP_TAKEOFF * elev * (0.5f + p)
            } else {
                0f
            }
    }

    /** 复位一杆的判定采集字段（[shoot] / 回放每杆开始时调用）。 */
    private fun resetShotTracking() {
        shotFirstContact = null
        shotFirstContactNumber = null
        shotContactHappened = false
        shotRailAfter = false
        shotPotted.clear()
        shotPottedNumbers.clear()
        shotLowestBefore = PoolRules.lowestBall(balls)?.number
        shotCuePotted = false
        shotCueStartY = cueBall()?.y ?: 0f
        shotRailedBalls.clear()
    }

    // ---------------------------------------------------------------------
    // 帧推进
    // ---------------------------------------------------------------------

    fun tick(dt: Float) {
        // 减弱动画时冻结时间：瞄准脉冲 / 天气粒子静止，物理仍按 dt 推进
        if (!reduceMotion) time += dt
        if (messageT > 0f) messageT = max(0f, messageT - dt)
        updateEffects(dt)
        updatePocketAnims(dt)

        if (replaying) {
            replayTick(dt)
            return
        }

        when (phase) {
            Phase.SHOOTING -> tickShooting(dt)
            Phase.AIMING -> tickAiming(dt)
            Phase.READY, Phase.FRAME_OVER, Phase.MATCH_OVER -> Unit
        }
    }

    private fun tickAiming(dt: Float) {
        if (match.turn != PoolSide.AI) return
        aiTimer -= dt
        if (aiTimer > 0f) return
        aiTakeTurn()
    }

    private fun tickShooting(dt: Float) {
        accumulator += dt
        var steps = 0
        while (accumulator >= PoolConfig.SIM_DT && steps < 60) {
            PoolPhysics.step(balls, table, PoolConfig.SIM_DT, listener)
            accumulator -= PoolConfig.SIM_DT
            steps++
            if (PoolPhysics.settled(balls)) break
        }
        if (accumulator > PoolConfig.SIM_DT * 120f) accumulator = PoolConfig.SIM_DT * 120f
        if (PoolPhysics.settled(balls)) {
            accumulator = 0f
            finalizeShot()
        }
    }

    private fun updateEffects(dt: Float) {
        val iter = effects.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.life -= dt
            e.vx *= 0.98f
            e.vy *= 0.98f
            e.x += e.vx * dt
            e.y += e.vy * dt
            if (e.life <= 0f) iter.remove()
        }
    }

    private fun updatePocketAnims(dt: Float) {
        for (b in balls) {
            if (b.potted && b.pocketAnim < 1f) {
                // 减弱动画：落袋直接到位，省略吞咽过程
                b.pocketAnim = if (reduceMotion) 1f else min(1f, b.pocketAnim + dt * 3f)
            }
        }
    }

    private fun spawnBurst(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
    ) {
        if (reduceMotion) return
        if (effects.size > 220) return
        for (i in 0 until count) {
            val a = (i.toFloat() / count) * (2f * Math.PI.toFloat()) + (time * 3f)
            val sp = 0.15f + (i % 3) * 0.12f
            effects +=
                PoolEffect(
                    x = x,
                    y = y,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp,
                    life = 0.35f + (i % 4) * 0.05f,
                    color = color,
                )
        }
    }

    // ---------------------------------------------------------------------
    // 结算
    // ---------------------------------------------------------------------

    private fun finalizeShot() {
        val shooter = match.turn
        val remaining = PoolRules.remaining(balls)
        val pottedThisShot = shotPotted.size
        lastShotPots = pottedThisShot
        if (pottedThisShot > 0) {
            potsThisMatch += pottedThisShot
            if (pottedThisShot > bestShotPots) bestShotPots = pottedThisShot
        }
        val outcome =
            PoolShotOutcome(
                firstContact = shotFirstContact,
                railAfterContact = shotRailAfter,
                potted = shotPotted.toList(),
                cuePotted = shotCuePotted,
                shooterGroupClearedBefore = shooterClearedBefore,
                firstContactNumber = shotFirstContactNumber,
                pottedNumbers = shotPottedNumbers.toList(),
                lowestBefore = shotLowestBefore,
            )
        val update = PoolRules.resolve(match, outcome, remaining, isBreak)
        match = update.state
        message = update.message
        messageT = 2.4f
        // 犯规提示音：本杆判定为犯规时给一声（结算后由 Screen 消费）
        if (update.foul) pendingHapticFoul = true
        recordShotSummary(shooter, update)

        // 教学：达成目标即结束本局（通过），不再走普通胜负/犯规流程
        val activeLesson = lesson
        if (activeLesson != null && lessonGoalAchieved(activeLesson, update)) {
            lessonPassed = true
            frameWinner = PoolSide.PLAYER
            phase = Phase.MATCH_OVER
            resultSubmitted = false
            return
        }

        if (update.rerack) {
            startFrame(match.turn, System.nanoTime())
            return
        }
        isBreak = false

        val winner = update.frameWinner
        if (winner != null) {
            frameWinner = winner
            lastFrameWinner = winner
            // 开球清台：玩家开球且 AI 未出杆，本局由玩家拿下
            breakAndRunFrame = winner == PoolSide.PLAYER && frameBreaker == PoolSide.PLAYER && !aiTouchedInFrame
            if (breakAndRunFrame) breakRunsThisMatch++
            val singleFrame =
                match.mode == PoolMode.PRACTICE ||
                    match.mode == PoolMode.CHALLENGE ||
                    match.playerFrames >= raceTo ||
                    match.aiFrames >= raceTo
            phase =
                if (singleFrame) {
                    Phase.MATCH_OVER
                } else {
                    Phase.FRAME_OVER
                }
            if (phase == Phase.MATCH_OVER) resultSubmitted = false
            return
        }

        // 白球落袋 / 自由球处理
        if (shotCuePotted) respawnCue()
        ballInHand = update.ballInHand
        placingCue = ballInHand && match.turn == PoolSide.PLAYER
        phase = Phase.AIMING
        scheduleAiIfNeeded()
    }

    /** 记录一杆摘要到 [shotHistory]（超出上限时丢弃最旧的一条）。 */
    private fun recordShotSummary(
        shooter: PoolSide,
        update: PoolMatchUpdate,
    ) {
        shotHistoryList +=
            ShotSummary(
                index = shotCount,
                turn = shooter,
                pottedNumbers = shotPottedNumbers.toList(),
                foul = update.foul,
                scratch = shotCuePotted,
            )
        while (shotHistoryList.size > MAX_SHOT_HISTORY) shotHistoryList.removeAt(0)
    }

    /** 判断当前这杆是否达成教学 [lesson] 的目标。 */
    private fun lessonGoalAchieved(
        lesson: PoolLesson,
        update: PoolMatchUpdate,
    ): Boolean =
        when (lesson.goal) {
            Goal.POT_ANY -> shotPottedNumbers.isNotEmpty()
            Goal.POT_ONE -> lesson.targetNumber?.let { it in shotPottedNumbers } == true
            Goal.CLEAR -> update.frameWinner == PoolSide.PLAYER && PoolRules.remaining(balls).total == 0
            Goal.DRAW_BACK ->
                !shotCuePotted &&
                    cueBall()?.let { it.y < shotCueStartY - PoolConfig.BALL_RADIUS } == true
            Goal.BANK_SHOT -> shotPottedNumbers.any { it in shotRailedBalls }
        }

    private fun respawnCue() {
        val cue = cueBall() ?: return
        cue.onTable = true
        cue.potted = false
        cue.pocketAnim = 0f
        cue.stop()
        // 优先开球点，被占则找附近空位
        val spot = findFreeCueSpot(table.centerX, table.headStringY)
        cue.x = spot.first
        cue.y = spot.second
    }

    private fun findFreeCueSpot(
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val cue = cueBall() ?: return x to y
        if (freeSpotAt(cue, x, y)) return x to y
        for (ring in 1..6) {
            val r = ring * cue.radius * 2.2f
            for (i in 0 until 12) {
                val a = i * (2f * Math.PI.toFloat() / 12f)
                val px = (x + cos(a) * r).coerceIn(table.left + cue.radius, table.right - cue.radius)
                val py = (y + sin(a) * r).coerceIn(table.top + cue.radius, table.bottom - cue.radius)
                if (freeSpotAt(cue, px, py)) return px to py
            }
        }
        return x to y
    }

    private fun freeSpotAt(
        cue: PoolBall,
        x: Float,
        y: Float,
    ): Boolean {
        for (b in balls) {
            if (!b.onTable || b === cue) continue
            if (hypot(b.x - x, b.y - y) < b.radius + cue.radius + 1e-3f) return false
        }
        return true
    }

    private fun scheduleAiIfNeeded() {
        if (match.turn == PoolSide.AI && phase == Phase.AIMING) {
            aiTimer = 0.9f
        }
    }

    private fun aiTakeTurn() {
        aiRng = Random(aiRng.nextLong())
        if (ballInHand) {
            val (px, py) = PoolAi.placeCueBall(balls, table, match, aiRng)
            cueBall()?.let {
                it.x = px
                it.y = py
                it.onTable = true
                it.potted = false
                it.stop()
            }
            ballInHand = false
        }
        val plan = PoolAi.plan(balls, table, match, difficulty, aiRng) ?: return
        // AI 不使用抬杆：重置为水平，避免继承玩家上一次设置的抬杆导致误起跳/弧线
        elevation = 0f
        // 可视化一帧瞄准
        aimAngle = plan.angle
        power = plan.power
        spin = Spin(plan.spinX, plan.spinY)
        shoot(plan.angle, plan.power, Spin(plan.spinX, plan.spinY))
    }

    // ---------------------------------------------------------------------
    // 回放
    // ---------------------------------------------------------------------

    /** 只读暴露已记录的一杆输入列表。 */
    fun replayShots(): List<RecordedShot> = replayLog.toList()

    /**
     * 装载一条持久化的回放（冷启动「回放上一场」用）：写入 mode/seed/开球方与杆序。
     * 不清空当前对局，直到调用 [startReplay]。
     */
    fun loadReplay(
        mode: PoolMode,
        seed: Long,
        breaker: PoolSide,
        shots: List<RecordedShot>,
    ) {
        if (replaying) return
        replayLog.clear()
        replayLog.addAll(shots)
        this.mode = mode
        frameSeed = seed
        this.breaker = breaker
    }

    /**
     * 从当前 [replayLog] 重建球局并进入回放态：用 [frameSeed] 重新摆球，
     * 然后按记录顺序把每一杆直接施加到白球，由物理内核确定性推演。
     * 无记录或已在回放返回 false。
     */
    fun startReplay(): Boolean {
        if (replaying || replayLog.isEmpty()) return false
        savedPhase = phase
        savedMatch = match
        savedFrameWinner = frameWinner
        savedSnapshotForReplay = captureSnapshot()
        replaying = true
        replayIndex = 0
        replayProgress = 0
        replayTotal = replayLog.size
        replayAccumulator = 0f
        replayApplied = false
        match = PoolMatchState(openTable = true, turn = breaker, mode = mode)
        rackFrame(breaker, frameSeed, scheduleAi = false)
        phase = Phase.SHOOTING
        return true
    }

    /**
     * 以固定步长推进当前回放杆的物理；settle 后进入下一杆，全部完成后退出回放并
     * 回到原本的结算态（不清空 [replayLog]）。
     */
    fun replayTick(dt: Float) {
        if (!replaying) return
        if (replayIndex >= replayLog.size) {
            stopReplay()
            return
        }
        if (!replayApplied) {
            applyShotInternal(replayLog[replayIndex])
            replayApplied = true
        }
        replayAccumulator += dt
        var steps = 0
        while (replayAccumulator >= PoolConfig.SIM_DT && steps < 60) {
            PoolPhysics.step(balls, table, PoolConfig.SIM_DT, listener)
            replayAccumulator -= PoolConfig.SIM_DT
            steps++
            if (PoolPhysics.settled(balls)) break
        }
        if (replayAccumulator > PoolConfig.SIM_DT * 120f) {
            replayAccumulator = PoolConfig.SIM_DT * 120f
        }
        if (PoolPhysics.settled(balls)) {
            replayAccumulator = 0f
            replayIndex++
            replayProgress = replayIndex
            replayApplied = false
            if (replayIndex >= replayLog.size) stopReplay()
        }
    }

    /** 随时停止回放并恢复进入前的结算态（记录保留）。 */
    fun stopReplay() {
        if (!replaying) return
        replaying = false
        replayApplied = false
        // 用回放前的完整快照还原球局/比分/输入态，避免停在回放残局
        savedSnapshotForReplay?.let { applySnapshot(it) }
        savedSnapshotForReplay = null
        savedPhase = null
        savedMatch = null
        savedFrameWinner = null
    }

    /** 回放单杆：确保白球在台后按记录参数击球，不写入 [replayLog]。 */
    private fun applyShotInternal(shot: RecordedShot) {
        val cue = cueBall() ?: return
        if (cue.potted || !cue.onTable) respawnCue()
        // 自由球摆位也记录在白球坐标里：回放到该杆时先复位到真实位置
        if (shot.cueX != 0f || shot.cueY != 0f) {
            cue.x = shot.cueX
            cue.y = shot.cueY
        }
        applyShotToCue(shot.angle, shot.power, Spin(shot.spinSide, shot.spinVertical), shot.elevation)
        resetShotTracking()
    }

    // ---------------------------------------------------------------------
    // 对局快照（进程被回收 / 旋转后恢复当前对局，与持久化回放是两件事）
    // ---------------------------------------------------------------------

    /**
     * 落盘当前对局快照：[Phase.AIMING] / [Phase.SHOOTING] / [Phase.FRAME_OVER] 有可续玩
     * 的对局；FRAME_OVER 允许被杀后回到「本局结算」界面继续点下一局。
     * 其它阶段（READY / MATCH_OVER）视为无对局，直接清档，避免陈旧快照被续玩。
     *
     * 快照保存球的完整运动状态与一杆判定采集中间态，因此 SHOOTING 阶段的球会
     * 在恢复后继续飞行并正常结算（不是回放，也不丢弃正在飞行的球）。
     */
    fun saveSnapshot(prefs: SharedPreferences) {
        // 回放不是「进行中对局」：保留回放前的续玩快照，不用回放中的球局覆盖它
        if (replaying) return
        clearLegacySnapshotKeys(prefs)
        if (phase != Phase.AIMING && phase != Phase.SHOOTING && phase != Phase.FRAME_OVER) {
            clearSnapshot(prefs)
            return
        }
        val payload = runCatching { PoolSnapshotCodec.encode(captureSnapshot()) }.getOrNull() ?: return
        GameSnapshot.save(prefs, POOL_SNAPSHOT_KEY, payload)
    }

    /**
     * 恢复当前对局快照；无快照 / 解析失败 / 当前已非初始 [Phase.READY] 返回 false。
     * 坏档会被清掉且不抛异常，调用方保持 READY 即可。载入时一并清理旧版本 key。
     */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        if (phase != Phase.READY) return false
        clearLegacySnapshotKeys(prefs)
        val raw = GameSnapshot.load(prefs, POOL_SNAPSHOT_KEY) ?: return false
        val snapshot = runCatching { PoolSnapshotCodec.decode(raw) }.getOrNull()
        if (snapshot == null) {
            clearSnapshot(prefs)
            return false
        }
        return runCatching { applySnapshot(snapshot) }.isSuccess
    }

    /** 清掉对局快照（重开 / 对局结束 / 主动退出），并清理旧版本遗留 key。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        GameSnapshot.clear(prefs, POOL_SNAPSHOT_KEY)
        clearLegacySnapshotKeys(prefs)
    }

    /** 清掉 1.9.0 前遗留的快照 / 回放 key，避免旧格式孤儿数据常驻。 */
    private fun clearLegacySnapshotKeys(prefs: SharedPreferences) {
        GameSnapshot.clear(prefs, POOL_LEGACY_SNAPSHOT_KEY)
        GameSnapshot.clear(prefs, POOL_LEGACY_REPLAY_KEY)
    }

    private fun captureSnapshot(): PoolSnapshot =
        PoolSnapshot(
            phase = phase,
            mode = mode,
            openTable = match.openTable,
            playerGroup = match.playerGroup,
            aiGroup = match.aiGroup,
            turn = match.turn,
            playerFrames = match.playerFrames,
            aiFrames = match.aiFrames,
            matchMode = match.mode,
            raceTo = raceTo,
            best = best,
            challengeId = challenge?.id,
            challengePar = challenge?.par ?: -1,
            challengeDate = challengeDate,
            lessonId = lesson?.id,
            shotCount = shotCount,
            isBreak = isBreak,
            shooterClearedBefore = shooterClearedBefore,
            ballInHand = ballInHand,
            placingCue = placingCue,
            aimAngle = aimAngle,
            elevation = elevation,
            spinSide = spin.side,
            spinVertical = spin.vertical,
            frameSeed = frameSeed,
            breaker = breaker,
            frameWinner = frameWinner,
            lastFrameWinner = lastFrameWinner,
            breakRunsThisMatch = breakRunsThisMatch,
            potsThisMatch = potsThisMatch,
            bestShotPots = bestShotPots,
            lastShotPots = lastShotPots,
            frameBreaker = frameBreaker,
            aiTouchedInFrame = aiTouchedInFrame,
            breakAndRunFrame = breakAndRunFrame,
            balls = balls.map { it.toSnapshot() },
            tracking =
                PoolShotTrackingSnapshot(
                    firstContact = shotFirstContact,
                    firstContactNumber = shotFirstContactNumber,
                    contactHappened = shotContactHappened,
                    railAfter = shotRailAfter,
                    potted = shotPotted.toList(),
                    pottedNumbers = shotPottedNumbers.toList(),
                    lowestBefore = shotLowestBefore,
                    cuePotted = shotCuePotted,
                    cueStartY = shotCueStartY,
                    railedBalls = shotRailedBalls.toSet(),
                ),
            history =
                shotHistoryList.map {
                    ShotSummarySnapshot(it.index, it.turn, it.pottedNumbers, it.foul, it.scratch)
                },
        )

    private fun applySnapshot(snapshot: PoolSnapshot) {
        phase = snapshot.phase
        mode = snapshot.mode
        match =
            PoolMatchState(
                openTable = snapshot.openTable,
                playerGroup = snapshot.playerGroup,
                aiGroup = snapshot.aiGroup,
                turn = snapshot.turn,
                playerFrames = snapshot.playerFrames,
                aiFrames = snapshot.aiFrames,
                mode = snapshot.matchMode,
            )
        raceTo = snapshot.raceTo
        best = snapshot.best
        lesson = snapshot.lessonId?.let { id -> PoolTutorial.lessons().firstOrNull { it.id == id } }
        challenge = if (lesson == null) resolveChallenge(snapshot) else null
        challengeDate = if (challenge != null) snapshot.challengeDate else 0L
        lessonPassed = false
        resultSubmitted = false
        frameWinner = snapshot.frameWinner
        lastFrameWinner = snapshot.lastFrameWinner
        breakRunsThisMatch = snapshot.breakRunsThisMatch
        potsThisMatch = snapshot.potsThisMatch
        bestShotPots = snapshot.bestShotPots
        lastShotPots = snapshot.lastShotPots

        shotCount = snapshot.shotCount
        isBreak = snapshot.isBreak
        shooterClearedBefore = snapshot.shooterClearedBefore
        ballInHand = snapshot.ballInHand
        placingCue = snapshot.placingCue
        aimAngle = snapshot.aimAngle
        elevation = snapshot.elevation
        spin = Spin(snapshot.spinSide, snapshot.spinVertical)
        frameSeed = snapshot.frameSeed
        breaker = snapshot.breaker
        frameBreaker = snapshot.frameBreaker
        aiTouchedInFrame = snapshot.aiTouchedInFrame
        breakAndRunFrame = snapshot.breakAndRunFrame

        balls = snapshot.balls.map { it.toBall() }.toMutableList()

        val tracking = snapshot.tracking
        shotFirstContact = tracking.firstContact
        shotFirstContactNumber = tracking.firstContactNumber
        shotContactHappened = tracking.contactHappened
        shotRailAfter = tracking.railAfter
        shotPotted.clear()
        shotPotted += tracking.potted
        shotPottedNumbers.clear()
        shotPottedNumbers += tracking.pottedNumbers
        shotLowestBefore = tracking.lowestBefore
        shotCuePotted = tracking.cuePotted
        shotCueStartY = tracking.cueStartY
        shotRailedBalls.clear()
        shotRailedBalls += tracking.railedBalls

        shotHistoryList.clear()
        shotHistoryList +=
            snapshot.history.map { ShotSummary(it.index, it.turn, it.pottedNumbers, it.foul, it.scratch) }

        accumulator = 0f
        aiTimer = 0f
        dragging = false
        replaying = false
        savedPhase = null
        savedMatch = null
        savedFrameWinner = null
        effects.clear()
        resetTransientIndicators()
        clearPendingFeedback()
        // AI 回合恢复后稍作停顿再出杆，避免恢复瞬间立刻出杆
        if (phase == Phase.AIMING) scheduleAiIfNeeded()
    }

    /** 挑战按 id 关联；每日挑战（id=0）无静态定义，用存档里的 par 与当前球重建。 */
    private fun resolveChallenge(snapshot: PoolSnapshot): PoolChallenge? {
        val id = snapshot.challengeId ?: return null
        PoolChallenges.campaigns().firstOrNull { it.id == id }?.let { return it }
        if (id != PoolChallenges.DAILY_ID) return null
        return PoolChallenge(
            id = id,
            nameZh = "每日挑战",
            nameEn = "Daily Challenge",
            par = snapshot.challengePar.coerceAtLeast(1),
            balls = snapshot.balls.map { PoolBallSetup(it.id, it.kind, it.x, it.y) },
        )
    }

    // ---------------------------------------------------------------------
    // 查询/辅助
    // ---------------------------------------------------------------------

    fun targetKind(): PoolBallKind? =
        when (match.mode) {
            PoolMode.NINE_BALL -> targetBall()?.kind
            PoolMode.PRACTICE, PoolMode.CHALLENGE -> null
            PoolMode.EIGHT_BALL -> PoolRules.targetKindFor(match, PoolRules.remaining(balls))
        }

    /** 当前目标球（九球为最低号球；八球为本方花色最小号；练习为 null）。 */
    fun targetBall(): PoolBall? = PoolRules.targetBallFor(match, balls)

    /** 本局已出杆数（只读）。 */
    val shots: Int get() = shotCount

    /** 最近若干杆摘要（只读），最多 [MAX_SHOT_HISTORY] 条，最新的在末尾。 */
    val shotHistory: List<ShotSummary> get() = shotHistoryList

    /** 本场累计进球数（不含白球，只读）。 */
    val matchPots: Int get() = potsThisMatch

    /** 本场单杆最多进球数（只读）。 */
    val bestShot: Int get() = bestShotPots

    /** 练习模式得分：杆数越少分越高。 */
    fun practiceScore(): Int = max(1, 120 - shots * 2)

    /** 关卡/每日挑战得分：`max(10, par*100 - shots*8)`，杆数越少分越高。 */
    fun challengeScore(): Int = challenge?.let { PoolChallenges.scoreFor(it.par, shots) } ?: 0

    fun isPlayerTurn(): Boolean = match.turn == PoolSide.PLAYER

    fun canPlayerShoot(): Boolean = !replaying && phase == Phase.AIMING && match.turn == PoolSide.PLAYER && !placingCue
}

/** 击球历史最多保留的杆数。 */
private const val MAX_SHOT_HISTORY = 30

/** 一杆结束后的摘要，供击球历史面板展示。 */
data class ShotSummary(
    /** 本场第几杆（从 1 开始）。 */
    val index: Int,
    /** 击球方。 */
    val turn: PoolSide,
    /** 本杆落袋的球号（不含白球），按落袋先后。 */
    val pottedNumbers: List<Int>,
    /** 是否犯规。 */
    val foul: Boolean,
    /** 白球是否落袋。 */
    val scratch: Boolean,
)

/** 台面火花/落袋粒子。 */
class PoolEffect(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val size: Float = 0.006f,
)

/** 单颗球的快照（位置 / 线速度 / 角速度 / 朝向 / 落袋状态）。 */
private data class PoolBallSnapshot(
    val id: Int,
    val kind: PoolBallKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val wx: Float,
    val wy: Float,
    val wz: Float,
    val z: Float,
    val vz: Float,
    val curve: Float,
    val qw: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val onTable: Boolean,
    val potted: Boolean,
    val pocketIndex: Int,
    val pocketAnim: Float,
)

/** 一杆判定采集中间态：恢复到 SHOOTING 后据此继续飞行并正常结算。 */
private data class PoolShotTrackingSnapshot(
    val firstContact: PoolBallKind?,
    val firstContactNumber: Int?,
    val contactHappened: Boolean,
    val railAfter: Boolean,
    val potted: List<PoolBallKind>,
    val pottedNumbers: List<Int>,
    val lowestBefore: Int?,
    val cuePotted: Boolean,
    val cueStartY: Float,
    val railedBalls: Set<Int>,
)

/** 击球历史条目快照。 */
private data class ShotSummarySnapshot(
    val index: Int,
    val turn: PoolSide,
    val pottedNumbers: List<Int>,
    val foul: Boolean,
    val scratch: Boolean,
)

/** 整个进行中对局的快照（球桌几何固定为 `PoolTable.standard()`，无需存储）。 */
private data class PoolSnapshot(
    val phase: PoolGame.Phase,
    val mode: PoolMode,
    val openTable: Boolean,
    val playerGroup: PoolGroup?,
    val aiGroup: PoolGroup?,
    val turn: PoolSide,
    val playerFrames: Int,
    val aiFrames: Int,
    val matchMode: PoolMode,
    val raceTo: Int,
    val best: Int,
    val challengeId: Int?,
    val challengePar: Int,
    val challengeDate: Long,
    val lessonId: Int?,
    val shotCount: Int,
    val isBreak: Boolean,
    val shooterClearedBefore: Boolean,
    val ballInHand: Boolean,
    val placingCue: Boolean,
    val aimAngle: Float,
    val elevation: Float,
    val spinSide: Float,
    val spinVertical: Float,
    val frameSeed: Long,
    val breaker: PoolSide,
    val frameWinner: PoolSide?,
    val lastFrameWinner: PoolSide?,
    val breakRunsThisMatch: Int,
    val potsThisMatch: Int,
    val bestShotPots: Int,
    val lastShotPots: Int,
    val frameBreaker: PoolSide,
    val aiTouchedInFrame: Boolean,
    val breakAndRunFrame: Boolean,
    val balls: List<PoolBallSnapshot>,
    val tracking: PoolShotTrackingSnapshot,
    val history: List<ShotSummarySnapshot>,
)

private fun PoolBall.toSnapshot(): PoolBallSnapshot =
    PoolBallSnapshot(
        id = id,
        kind = kind,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        wx = wx,
        wy = wy,
        wz = wz,
        z = z,
        vz = vz,
        curve = curve,
        qw = qw,
        qx = qx,
        qy = qy,
        qz = qz,
        onTable = onTable,
        potted = potted,
        pocketIndex = pocketIndex,
        pocketAnim = pocketAnim,
    )

private fun PoolBallSnapshot.toBall(): PoolBall =
    PoolBall(id = id, kind = kind, x = x, y = y, vx = vx, vy = vy, wx = wx, wy = wy, wz = wz).also {
        it.z = z
        it.vz = vz
        it.curve = curve
        it.qw = qw
        it.qx = qx
        it.qy = qy
        it.qz = qz
        it.onTable = onTable
        it.potted = potted
        it.pocketIndex = pocketIndex
        it.pocketAnim = pocketAnim
    }

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name.isNullOrBlank()) null else runCatching { enumValueOf<T>(name) }.getOrNull()

/**
 * 对局快照的紧凑文本编解码（每行 `key=value`，球/历史各占一行）。
 * 不使用 kotlinx.serialization，读写均容错；任何字段异常都让 [decode] 返回 null。
 *
 * 字段：`v` 版本；`phase`/`mode`/`match`/`flags`/`aim`/`frame`/`stats`/`challenge`/
 * `challengeDate`/`lesson`/`raceTo`/`best`/`shotCount`；`ball` 每球一行；`track` 一杆采集中间态；
 * `hist` 击球历史每行一条。
 *
 * 版本历史：v1 = 1.9.0 前的旧袋口/台面几何（中袋位置与 `pocketIndex` 含义已变），
 * 升 v2 后旧 payload 一律拒绝，避免续玩时球落入错误袋口。
 */
private object PoolSnapshotCodec {
    private const val VERSION = 2

    fun encode(snapshot: PoolSnapshot): String {
        val lines = ArrayList<String>()
        lines += "v=$VERSION"
        lines += "phase=${snapshot.phase.name}"
        lines += "mode=${snapshot.mode.name}"
        lines +=
            "match=${bool(snapshot.openTable)},${snapshot.playerGroup?.name.orEmpty()}," +
            "${snapshot.aiGroup?.name.orEmpty()},${snapshot.turn.name},${snapshot.playerFrames}," +
            "${snapshot.aiFrames},${snapshot.matchMode.name}"
        lines += "raceTo=${snapshot.raceTo}"
        lines += "best=${snapshot.best}"
        lines += "challenge=${snapshot.challengeId ?: ""},${snapshot.challengePar}"
        lines += "challengeDate=${snapshot.challengeDate}"
        lines += "lesson=${snapshot.lessonId ?: ""}"
        lines += "shotCount=${snapshot.shotCount}"
        lines +=
            "flags=${bool(snapshot.isBreak)},${bool(snapshot.shooterClearedBefore)}," +
            "${bool(snapshot.ballInHand)},${bool(snapshot.placingCue)}"
        lines += "aim=${snapshot.aimAngle},${snapshot.elevation},${snapshot.spinSide},${snapshot.spinVertical}"
        lines +=
            "frame=${snapshot.frameSeed},${snapshot.breaker.name}," +
            "${snapshot.frameWinner?.name.orEmpty()},${snapshot.lastFrameWinner?.name.orEmpty()}"
        lines +=
            "stats=${snapshot.breakRunsThisMatch},${snapshot.potsThisMatch},${snapshot.bestShotPots}," +
            "${snapshot.lastShotPots},${snapshot.frameBreaker.name}," +
            "${bool(snapshot.aiTouchedInFrame)},${bool(snapshot.breakAndRunFrame)}"
        for (b in snapshot.balls) {
            lines +=
                "ball=${b.id},${b.kind.name},${b.x},${b.y},${b.vx},${b.vy},${b.wx},${b.wy},${b.wz}," +
                "${b.z},${b.vz},${b.curve},${b.qw},${b.qx},${b.qy},${b.qz}," +
                "${bool(b.onTable)},${bool(b.potted)},${b.pocketIndex},${b.pocketAnim}"
        }
        val t = snapshot.tracking
        lines +=
            "track=${t.firstContact?.name.orEmpty()},${t.firstContactNumber ?: ""}," +
            "${bool(t.contactHappened)},${bool(t.railAfter)},${t.lowestBefore ?: ""}," +
            "${bool(t.cuePotted)},${t.cueStartY},${t.potted.joinToString(":") { it.name }}," +
            "${t.pottedNumbers.joinToString(":")},${t.railedBalls.joinToString(":")}"
        for (h in snapshot.history) {
            lines += "hist=${h.index},${h.turn.name},${h.pottedNumbers.joinToString(":")},${bool(h.foul)},${bool(h.scratch)}"
        }
        return lines.joinToString("\n")
    }

    fun decode(raw: String): PoolSnapshot? {
        if (raw.isBlank()) return null
        val values = HashMap<String, String>()
        val ballLines = ArrayList<String>()
        val histLines = ArrayList<String>()
        var trackLine: String? = null
        for (line in raw.split('\n')) {
            if (line.isBlank()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) return null
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            when (key) {
                "ball" -> ballLines += value
                "hist" -> histLines += value
                "track" -> trackLine = value
                else -> values[key] = value
            }
        }
        if (values["v"]?.toIntOrNull() != VERSION) return null

        val phase = enumOrNull<PoolGame.Phase>(values["phase"]) ?: return null
        val mode = enumOrNull<PoolMode>(values["mode"]) ?: return null
        val raceTo =
            (values["raceTo"]?.toIntOrNull() ?: return null)
                .coerceIn(POOL_RACE_TO_MIN, POOL_RACE_TO_MAX)
        val best = values["best"]?.toIntOrNull() ?: return null
        val shotCount = values["shotCount"]?.toIntOrNull() ?: return null

        val matchParts = values["match"]?.split(',') ?: return null
        if (matchParts.size != 7) return null
        val turn = enumOrNull<PoolSide>(matchParts[3]) ?: return null
        val matchMode = enumOrNull<PoolMode>(matchParts[6]) ?: return null
        val playerFrames = matchParts[4].toIntOrNull() ?: return null
        val aiFrames = matchParts[5].toIntOrNull() ?: return null

        val flags = values["flags"]?.split(',') ?: return null
        if (flags.size != 4) return null

        val aim = values["aim"]?.split(',') ?: return null
        if (aim.size != 4) return null

        val frame = values["frame"]?.split(',') ?: return null
        if (frame.size != 4) return null
        val frameSeed = frame[0].toLongOrNull() ?: return null
        val breaker = enumOrNull<PoolSide>(frame[1]) ?: return null

        val stats = values["stats"]?.split(',') ?: return null
        if (stats.size != 7) return null
        val frameBreaker = enumOrNull<PoolSide>(stats[4]) ?: return null

        val challengeParts = values["challenge"]?.split(',') ?: return null
        if (challengeParts.size != 2) return null
        val challengePar = challengeParts[1].toIntOrNull() ?: return null

        val balls = ballLines.mapNotNull { parseBall(it) }
        if (balls.size != ballLines.size) return null
        val tracking = parseTracking(trackLine ?: return null) ?: return null
        val history = histLines.mapNotNull { parseHistory(it) }
        if (history.size != histLines.size) return null

        return PoolSnapshot(
            phase = phase,
            mode = mode,
            openTable = matchParts[0].toBool(),
            playerGroup = enumOrNull<PoolGroup>(matchParts[1]),
            aiGroup = enumOrNull<PoolGroup>(matchParts[2]),
            turn = turn,
            playerFrames = playerFrames,
            aiFrames = aiFrames,
            matchMode = matchMode,
            raceTo = raceTo,
            best = best,
            challengeId = challengeParts[0].toIntOrNull(),
            challengePar = challengePar,
            challengeDate = values["challengeDate"]?.toLongOrNull() ?: 0L,
            lessonId = values["lesson"]?.toIntOrNull(),
            shotCount = shotCount,
            isBreak = flags[0].toBool(),
            shooterClearedBefore = flags[1].toBool(),
            ballInHand = flags[2].toBool(),
            placingCue = flags[3].toBool(),
            aimAngle = aim[0].toFloatOrNull() ?: return null,
            elevation = aim[1].toFloatOrNull() ?: return null,
            spinSide = aim[2].toFloatOrNull() ?: return null,
            spinVertical = aim[3].toFloatOrNull() ?: return null,
            frameSeed = frameSeed,
            breaker = breaker,
            frameWinner = enumOrNull<PoolSide>(frame[2]),
            lastFrameWinner = enumOrNull<PoolSide>(frame[3]),
            breakRunsThisMatch = stats[0].toIntOrNull() ?: return null,
            potsThisMatch = stats[1].toIntOrNull() ?: return null,
            bestShotPots = stats[2].toIntOrNull() ?: return null,
            lastShotPots = stats[3].toIntOrNull() ?: return null,
            frameBreaker = frameBreaker,
            aiTouchedInFrame = stats[5].toBool(),
            breakAndRunFrame = stats[6].toBool(),
            balls = balls,
            tracking = tracking,
            history = history,
        )
    }

    private fun parseBall(line: String): PoolBallSnapshot? {
        val f = line.split(',')
        if (f.size != 20) return null
        return PoolBallSnapshot(
            id = f[0].toIntOrNull() ?: return null,
            kind = enumOrNull<PoolBallKind>(f[1]) ?: return null,
            x = f[2].toFloatOrNull() ?: return null,
            y = f[3].toFloatOrNull() ?: return null,
            vx = f[4].toFloatOrNull() ?: return null,
            vy = f[5].toFloatOrNull() ?: return null,
            wx = f[6].toFloatOrNull() ?: return null,
            wy = f[7].toFloatOrNull() ?: return null,
            wz = f[8].toFloatOrNull() ?: return null,
            z = f[9].toFloatOrNull() ?: return null,
            vz = f[10].toFloatOrNull() ?: return null,
            curve = f[11].toFloatOrNull() ?: return null,
            qw = f[12].toFloatOrNull() ?: return null,
            qx = f[13].toFloatOrNull() ?: return null,
            qy = f[14].toFloatOrNull() ?: return null,
            qz = f[15].toFloatOrNull() ?: return null,
            onTable = f[16].toBool(),
            potted = f[17].toBool(),
            pocketIndex = f[18].toIntOrNull() ?: return null,
            pocketAnim = f[19].toFloatOrNull() ?: return null,
        )
    }

    private fun parseTracking(line: String): PoolShotTrackingSnapshot? {
        val f = line.split(',')
        if (f.size != 10) return null
        return PoolShotTrackingSnapshot(
            firstContact = enumOrNull<PoolBallKind>(f[0]),
            firstContactNumber = f[1].toIntOrNull(),
            contactHappened = f[2].toBool(),
            railAfter = f[3].toBool(),
            potted = kindList(f[7]),
            pottedNumbers = intList(f[8]),
            lowestBefore = f[4].toIntOrNull(),
            cuePotted = f[5].toBool(),
            cueStartY = f[6].toFloatOrNull() ?: return null,
            railedBalls = intList(f[9]).toSet(),
        )
    }

    private fun parseHistory(line: String): ShotSummarySnapshot? {
        val f = line.split(',')
        if (f.size != 5) return null
        return ShotSummarySnapshot(
            index = f[0].toIntOrNull() ?: return null,
            turn = enumOrNull<PoolSide>(f[1]) ?: return null,
            pottedNumbers = intList(f[2]),
            foul = f[3].toBool(),
            scratch = f[4].toBool(),
        )
    }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private fun String.toBool(): Boolean = this == "1"

    private fun intList(value: String): List<Int> = if (value.isBlank()) emptyList() else value.split(':').mapNotNull { it.toIntOrNull() }

    private fun kindList(value: String): List<PoolBallKind> =
        if (value.isBlank()) emptyList() else value.split(':').mapNotNull { enumOrNull<PoolBallKind>(it) }
}
