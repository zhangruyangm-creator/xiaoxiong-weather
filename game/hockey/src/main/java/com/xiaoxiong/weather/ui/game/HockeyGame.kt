package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import com.xiaoxiong.weather.ui.game.HockeyLogic.Table
import kotlin.math.hypot
import kotlin.random.Random

/** 冰面纵向渐变：颜色固定，仅 startY/endY 随球桌尺寸变化，resize 时重建缓存。 */
private val TABLE_ICE_COLORS = listOf(Color(0xFFE8F6FF), Color(0xFFD2EAF8), Color(0xFFC5E0F2))

/** 击球震动 / 火花的最小间隔（秒）：球被顶在墙角持续接触时，避免震动连发。 */
private const val HIT_FEEDBACK_INTERVAL = 0.12f

/**
 * 墙角死锁判定窗口（秒）：窗口内球相对起点的净位移 < 2*puckR 且持续接触挡板才算卡住。
 * 单帧位移阈值会被「每帧 2–5px 的往复抖动」反复清零而永不触发。
 */
private const val STUCK_WINDOW_SECONDS = 0.3f

/** 角落区域挡板接触限速（相对短边/秒）：避免 1.85 恢复系数向被顶在墙角的球持续泵能。 */
private const val CORNER_CONTACT_SPEED_MUL = 1.1f

/** 对局快照 KEY：紧凑文本，仅存对局进行中的状态。 */
private const val HOCKEY_SNAPSHOT_KEY = "hockey_snapshot_v1"

/**
 * 快照表头固定字段数（其后紧跟难度名称的字符码，再跟可选拖尾字段：
 * paddleScaleBoth、matchDifficulty、5 个局中暂存设置 pending* 与场地主题 id；
 * 缺省时按兼容值回退）。
 */
private const val HOCKEY_HEADER_FIELDS = 21

/** 撞击火花。 */
class HockeySpark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Color,
)

/**
 * 小熊冰球运行时状态：帧推进、输入、计分、粒子。
 */
class HockeyGame {
    var phase by mutableStateOf(Phase.READY)
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    /** 自定义难度等级 1…10（仅 [Difficulty.CUSTOM] 生效）。 */
    var customLevel by mutableIntStateOf(5)
    var winScore by mutableIntStateOf(5)

    /** [startMatch] 时锁定的难度：对局中改难度只影响 AI，结算 / 成就仍按本局难度记账。 */
    var matchDifficulty by mutableStateOf(Difficulty.NORMAL)

    private var paramsCacheKey: Long? = null
    private var paramsCache: HockeyLogic.DifficultyParams? = null

    /** 难度参数按 difficulty/customLevel 缓存，避免 CUSTOM 每帧 lerpParams 新建对象。 */
    fun effectiveParams(): HockeyLogic.DifficultyParams {
        val key = difficulty.ordinal.toLong() shl 32 or customLevel.toLong()
        val cached = paramsCache
        if (cached != null && paramsCacheKey == key) return cached
        return HockeyLogic.params(difficulty, customLevel).also {
            paramsCache = it
            paramsCacheKey = key
        }
    }

    /** 设置保存 / 重开时主动失效参数缓存。 */
    fun invalidateParamsCache() {
        paramsCache = null
        paramsCacheKey = null
    }

    var playerScore by mutableIntStateOf(0)
    var aiScore by mutableIntStateOf(0)
    var bestWins by mutableIntStateOf(0)
    var resultSubmitted = false

    var table: Table? = null
    var tableGradient: Brush? = null
        private set
    var player = Body(0f, 0f, r = 1f)
    var ai = Body(0f, 0f, r = 1f)
    var puck = Body(0f, 0f, r = 1f)

    /** 挡板尺寸倍率（设置可调）。 */
    var paddleScale by mutableFloatStateOf(HockeyLogic.PADDLE_SCALE_DEFAULT)

    /** 尺寸倍率生效对象：true 双方挡板都变大；false 仅玩家挡板变大（AI 保持标准）。 */
    var paddleScaleBoth by mutableStateOf(true)

    /** 球（蜜罐）尺寸倍率。 */
    var puckScale by mutableFloatStateOf(HockeyLogic.PUCK_SCALE_DEFAULT)

    /** 球门宽度倍率。 */
    var goalScale by mutableFloatStateOf(HockeyLogic.GOAL_SCALE_DEFAULT)

    /** 场地主题（当前生效）：决定地面摩擦与静态外观。 */
    var rinkTheme by mutableStateOf(RinkTheme.DEFAULT)

    // 最近一次画布尺寸：设置变更后重建球桌用（onSizeChanged 不会主动触发）
    private var lastCanvasW = 0f
    private var lastCanvasH = 0f
    private var lastPadH = 0f
    private var lastPadV = 0f

    var playerPrevX = 0f
    var playerPrevY = 0f
    var touchX: Float? = null
    var touchY: Float? = null

    var serveTimer by mutableFloatStateOf(0f)
    var scoreFlash by mutableFloatStateOf(0f)
    var lastScorer: Scorer? = null
    var matchWinner: Scorer? = null

    /** 本次发球飞向玩家半场？null=首球方向随机未定（供发球提示显示）。 */
    val serveTowardPlayer: Boolean?
        get() =
            when (lastScorer) {
                Scorer.AI -> true
                Scorer.PLAYER -> false
                null -> null
            }
    var hitFlash by mutableFloatStateOf(0f)
    var time by mutableFloatStateOf(0f)

    /**
     * 「减弱动态效果」：冻结装饰用 [time]（蜜罐滚动 / 发球脉冲 / 场外天气），
     * 物理与计时仍按 dt 推进（与 PoolGame 一致）。
     */
    var reduceMotion = false

    /** 击球震动 / 火花的节流计时。 */
    private var hitFeedbackCooldown = 0f

    /** 墙角死锁检测窗口：窗口起点、累计时间与是否在窗口内。 */
    private var stuckWindowTime = 0f
    private var stuckWindowStartX = 0f
    private var stuckWindowStartY = 0f
    private var stuckWindowActive = false

    /** 已触发的墙角解脱次数（诊断 / 回归测试用）。 */
    var cornerEscapeCount = 0
        private set

    var aiTargetX = 0f
    var aiTargetY = 0f
    var aiReactionAge = 0f

    /** 对局进行中（非 READY/MATCH_OVER）：获胜分数与尺寸改动不即时生效。 */
    val rulesLocked: Boolean
        get() = phase != Phase.READY && phase != Phase.MATCH_OVER

    // 对局进行中被延迟到下一局生效的设置（[rulesLocked] 时暂存，[startMatch] 时套用）。
    private var pendingWinScore: Int? = null
    private var pendingPaddleScale: Float? = null
    private var pendingPaddleScaleBoth: Boolean? = null
    private var pendingPuckScale: Float? = null
    private var pendingGoalScale: Float? = null
    private var pendingRinkTheme: RinkTheme? = null

    /** 是否有等待下一局生效的设置（供 UI 提示）。 */
    val hasPendingRules: Boolean
        get() =
            pendingWinScore != null ||
                pendingPaddleScale != null ||
                pendingPaddleScaleBoth != null ||
                pendingPuckScale != null ||
                pendingGoalScale != null ||
                pendingRinkTheme != null

    // 打开设置时展示的值：优先展示已暂存的「下一局」值。
    fun displayWinScore(): Int = pendingWinScore ?: winScore

    fun displayPaddleScale(): Float = pendingPaddleScale ?: paddleScale

    fun displayPaddleScaleBoth(): Boolean = pendingPaddleScaleBoth ?: paddleScaleBoth

    fun displayPuckScale(): Float = pendingPuckScale ?: puckScale

    fun displayGoalScale(): Float = pendingGoalScale ?: goalScale

    fun displayRinkTheme(): RinkTheme = pendingRinkTheme ?: rinkTheme

    val trail = ArrayDeque<Pair<Float, Float>>(16)
    val sparks = mutableListOf<HockeySpark>()

    var pendingHapticHit = false
    var pendingHapticGoal = false

    fun resize(
        w: Float,
        h: Float,
        padH: Float,
        padV: Float,
    ) {
        lastCanvasW = w
        lastCanvasH = h
        lastPadH = padH
        lastPadV = padV
        val base = HockeyLogic.layoutTable(w, h, padH, padV)
        // 分屏/浮窗把窗口压得过小（如高度不足 192dp，上下 padding 就占满）时球桌尺寸为负，
        // 后续 clampPaddle 的 coerceIn 会构成空区间抛异常；忽略本次尺寸，
        // 保留上一张球桌（首次则为 null，tick/绘制各自兜底），窗口恢复后 onSizeChanged 会再触发。
        if (base.width <= 0f || base.height <= 0f) return
        val puckR =
            (base.puckR * puckScale.coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX))
                .coerceAtMost(base.paddleR * 0.95f)
        val goalW =
            (base.goalWidth * goalScale.coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX))
                .coerceIn(base.shortSide * 0.18f, base.width * 0.88f)
        val built = base.copy(puckR = puckR, goalWidth = goalW)
        table = built
        tableGradient =
            Brush.verticalGradient(
                colors = TABLE_ICE_COLORS,
                startY = built.top,
                endY = built.bottom,
            )
        puck.r = puckR
        // 倍率只加在挡板 Body 上；钳制 / AI 走位显式传各自半径，支持「仅玩家变大」
        player.r = base.paddleR * paddleScale
        ai.r = base.paddleR * (if (paddleScaleBoth) paddleScale else 1f)
        if (phase == Phase.READY || phase == Phase.MATCH_OVER) {
            resetPositions(serve = false)
        } else {
            // 保持相对位置比例粗略可用
            val (px, py) = HockeyLogic.clampPaddle(base, player.x, player.y, isPlayer = true, paddleR = player.r)
            player.x = px
            player.y = py
            val (ax, ay) = HockeyLogic.clampPaddle(base, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
            ai.x = ax
            ai.y = ay
            // 桌/球尺寸变化后球可能落到桌外：钳回新桌内
            clampPuckToTable()
        }
    }

    /** 设置里改了挡板尺寸后，按最近的画布尺寸重建球桌。 */
    fun relayout() {
        if (lastCanvasW <= 0f || lastCanvasH <= 0f) return
        resize(lastCanvasW, lastCanvasH, lastPadH, lastPadV)
    }

    /** 把球钳回当前球桌内（尺寸变化后调用，避免球落桌外）。 */
    private fun clampPuckToTable() {
        val t = table ?: return
        if (t.width <= puck.r * 2f || t.height <= puck.r * 2f) return
        puck.x = puck.x.coerceIn(t.left + puck.r, t.right - puck.r)
        puck.y = puck.y.coerceIn(t.top + puck.r, t.bottom - puck.r)
    }

    /**
     * 应用设置：
     * - 难度 / 自定义等级随时生效（仅影响之后的 AI 行为）；
     * - 对局进行中（[rulesLocked]）获胜分数、场地主题与挡板 / 球 / 门尺寸改动暂存，
     *   下一局 [startMatch] 生效；
     * - 非对局期间立即生效；最后重建球桌并把球钳回新桌内。
     */
    fun applySettings(
        difficulty: Difficulty,
        customLevel: Int,
        winScore: Int,
        paddleScale: Float,
        paddleScaleBoth: Boolean,
        puckScale: Float,
        goalScale: Float,
        rinkTheme: RinkTheme = RinkTheme.DEFAULT,
    ) {
        this.difficulty = difficulty
        this.customLevel = customLevel
        invalidateParamsCache()
        val w = winScore.coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
        val ps = paddleScale.coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX)
        val pu = puckScale.coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX)
        val go = goalScale.coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX)
        if (rulesLocked) {
            pendingWinScore = w
            pendingPaddleScale = ps
            pendingPaddleScaleBoth = paddleScaleBoth
            pendingPuckScale = pu
            pendingGoalScale = go
            pendingRinkTheme = rinkTheme
        } else {
            this.winScore = w
            this.paddleScale = ps
            this.paddleScaleBoth = paddleScaleBoth
            this.puckScale = pu
            this.goalScale = go
            this.rinkTheme = rinkTheme
            // 非对局期直接生效：清掉上一局遗留的暂存值，避免下一局又套用旧值
            clearPendingRules()
        }
        relayout()
        clampPuckToTable()
    }

    private fun clearPendingRules() {
        pendingWinScore = null
        pendingPaddleScale = null
        pendingPaddleScaleBoth = null
        pendingPuckScale = null
        pendingGoalScale = null
        pendingRinkTheme = null
    }

    /** 下一局开始时套用被延迟的设置。 */
    private fun consumePendingRules() {
        pendingWinScore?.let { winScore = it }
        pendingPaddleScale?.let { paddleScale = it }
        pendingPaddleScaleBoth?.let { paddleScaleBoth = it }
        pendingPuckScale?.let { puckScale = it }
        pendingGoalScale?.let { goalScale = it }
        pendingRinkTheme?.let { rinkTheme = it }
        clearPendingRules()
    }

    fun startMatch() {
        // 本局难度开局锁定：局中改难度只影响 AI，结算按锁定值记账
        matchDifficulty = difficulty
        // 套用上一局期间暂存的设置，并按其重建球桌
        consumePendingRules()
        playerScore = 0
        aiScore = 0
        matchWinner = null
        lastScorer = null
        scoreFlash = 0f
        resultSubmitted = false
        sparks.clear()
        trail.clear()
        stuckWindowTime = 0f
        stuckWindowActive = false
        cornerEscapeCount = 0
        hitFeedbackCooldown = 0f
        time = 0f
        pendingHapticHit = false
        pendingHapticGoal = false
        invalidateParamsCache()
        // phase 仍是旧值（READY/MATCH_OVER）：relayout 内部会复位，随后再按发球态摆位
        relayout()
        resetPositions(serve = true)
        // 清掉上一局残留的触摸点（对局结束时手指可能还按着，手势协程已被取消），
        // 否则新一局发球阶段挡板会瞬移到旧坐标
        touchX = null
        touchY = null
        phase = Phase.SERVE
        // 2s = 0.5s「预备」+ 1.5s 倒数 3、2、1（与界面倒数常量对应）
        serveTimer = 2.0f
    }

    fun resetPositions(serve: Boolean) {
        val t = table ?: return
        val (px, py) = HockeyLogic.playerHome(t)
        val (ax, ay) = HockeyLogic.aiHome(t)
        val (cx, cy) = HockeyLogic.puckCenter(t)
        player.x = px
        player.y = py
        player.vx = 0f
        player.vy = 0f
        playerPrevX = px
        playerPrevY = py
        ai.x = ax
        ai.y = ay
        ai.vx = 0f
        ai.vy = 0f
        aiTargetX = ax
        aiTargetY = ay
        aiReactionAge = 99f
        puck.x = cx
        puck.y = cy
        puck.vx = 0f
        puck.vy = 0f
        if (!serve) {
            touchX = null
            touchY = null
        }
    }

    /**
     * 局中落盘：进程被回收（或窗口重建）后由 [restoreSnapshot] 续玩。
     * 仅对局进行中（SERVE/PLAYING/SCORED）保存；标题/结算不写。
     *
     * 编码：第 0 组为表头（含难度名称字符码），第 1/2/3 组为 player/ai/puck，
     * 其后每两字段为一个拖尾点；球桌不落盘，恢复时按画布尺寸重建后套用坐标。
     */
    fun saveSnapshot(prefs: SharedPreferences) {
        if (phase == Phase.READY || phase == Phase.MATCH_OVER) return
        runCatching {
            val name = difficulty.name
            val header = mutableListOf<Number>()
            header += phase.ordinal
            header += playerScore
            header += aiScore
            header += serveTimer
            header += scoreFlash
            header += hitFlash
            header += customLevel
            header += winScore
            header += paddleScale
            header += puckScale
            header += goalScale
            header += bestWins
            header += matchWinner?.ordinal ?: -1
            header += if (resultSubmitted) 1 else 0
            header += lastScorer?.ordinal ?: -1
            header += time
            header += lastCanvasW
            header += lastCanvasH
            header += lastPadH
            header += lastPadV
            header += name.length
            name.forEach { header += it.code }
            // 拖尾字段：放在难度名之后以兼容旧档解析（读时缺省回退）
            header += if (paddleScaleBoth) 1 else 0
            header += matchDifficulty.ordinal
            // 局中暂存、待下一局生效的设置：不落盘会在恢复后被旧值覆盖导致永不生效。
            // -1 表示「无暂存」，旧档缺这些字段时同样回退为无暂存并保持旧行为。
            header += pendingWinScore ?: -1
            header += pendingPaddleScale ?: -1f
            header += pendingPaddleScaleBoth?.let { if (it) 1f else 0f } ?: -1f
            header += pendingPuckScale ?: -1f
            header += pendingGoalScale ?: -1f
            // 场地主题：按稳定 id 字符串落盘（长度 + 字符码）。旧档缺这两段时
            // 读回默认 ICE / 无暂存，保持旧档可恢复。
            header += rinkTheme.id.length
            rinkTheme.id.forEach { header += it.code }
            header += pendingRinkTheme?.id?.length ?: -1
            pendingRinkTheme?.id?.forEach { header += it.code }
            val groups = mutableListOf<List<Number>>()
            groups += header
            groups += listOf(player.x, player.y, player.vx, player.vy, player.r)
            groups += listOf(ai.x, ai.y, ai.vx, ai.vy, ai.r)
            groups += listOf(puck.x, puck.y, puck.vx, puck.vy, puck.r)
            trail.forEach { (x, y) -> groups += listOf(x, y) }
            GameSnapshot.save(prefs, HOCKEY_SNAPSHOT_KEY, SnapshotCodec.encodeGroups(groups))
        }
    }

    /** 恢复局中快照；无档 / 坏档返回 false（坏档会被清除，当作无档）。 */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        val groups = SnapshotCodec.decodeGroups(GameSnapshot.load(prefs, HOCKEY_SNAPSHOT_KEY)) ?: return false
        return runCatching { applySnapshot(groups) }.getOrElse {
            clearSnapshot(prefs)
            false
        }
    }

    /** 清档：结算、暂停重开、退出时调用，避免陈旧续玩。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        GameSnapshot.clear(prefs, HOCKEY_SNAPSHOT_KEY)
    }

    private fun applySnapshot(groups: List<FloatArray>): Boolean {
        val header = groups.firstOrNull() ?: return false
        if (header.size < HOCKEY_HEADER_FIELDS) return false
        val savedPhase = Phase.entries.getOrNull(header[0].toInt()) ?: return false
        if (savedPhase == Phase.READY || savedPhase == Phase.MATCH_OVER) return false
        val nameLen = header[20].toInt()
        if (nameLen < 0 || header.size < HOCKEY_HEADER_FIELDS + nameLen) return false
        val name = buildString { for (i in 0 until nameLen) append(header[HOCKEY_HEADER_FIELDS + i].toInt().toChar()) }
        val savedDifficulty = Difficulty.entries.firstOrNull { it.name == name } ?: return false
        // 旧档没有拖尾字段：paddleScaleBoth 回退为 true，matchDifficulty 回退为存档难度
        val trailing = HOCKEY_HEADER_FIELDS + nameLen
        val savedPaddleScaleBoth = header.getOrNull(trailing)?.let { it != 0f } ?: true
        val savedMatchDifficulty =
            header
                .getOrNull(trailing + 1)
                ?.toInt()
                ?.let { Difficulty.entries.getOrNull(it) }
                ?: savedDifficulty
        // 局中暂存设置：旧档（无这些字段）读回 null，保持「无暂存」的旧行为
        val pendingBase = trailing + 2
        pendingWinScore =
            header
                .getOrNull(pendingBase)
                ?.takeIf { it >= 0f }
                ?.toInt()
                ?.coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
        pendingPaddleScale =
            header
                .getOrNull(pendingBase + 1)
                ?.takeIf { it >= 0f }
                ?.coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX)
        pendingPaddleScaleBoth = header.getOrNull(pendingBase + 2)?.let { if (it < 0f) null else it != 0f }
        pendingPuckScale =
            header
                .getOrNull(pendingBase + 3)
                ?.takeIf { it >= 0f }
                ?.coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX)
        pendingGoalScale =
            header
                .getOrNull(pendingBase + 4)
                ?.takeIf { it >= 0f }
                ?.coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX)
        // 场地主题（可选拖尾）：旧档没有该字段时回退默认 ICE / 无暂存
        val (savedRinkTheme, afterRinkTheme) = readRinkThemeId(header, pendingBase + 5)
        rinkTheme = savedRinkTheme ?: RinkTheme.DEFAULT
        val (savedPendingRinkTheme, _) = readRinkThemeId(header, afterRinkTheme)
        pendingRinkTheme = savedPendingRinkTheme
        val playerG = groups.getOrNull(1)?.takeIf { it.size >= 5 } ?: return false
        val aiG = groups.getOrNull(2)?.takeIf { it.size >= 5 } ?: return false
        val puckG = groups.getOrNull(3)?.takeIf { it.size >= 5 } ?: return false

        difficulty = savedDifficulty
        matchDifficulty = savedMatchDifficulty
        customLevel = header[6].toInt().coerceIn(HockeyLogic.CUSTOM_LEVEL_MIN, HockeyLogic.CUSTOM_LEVEL_MAX)
        winScore = header[7].toInt().coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
        paddleScale = header[8].coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX)
        paddleScaleBoth = savedPaddleScaleBoth
        puckScale = header[9].coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX)
        goalScale = header[10].coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX)
        invalidateParamsCache()
        // 球桌可能已由 Screen 的 resize 先建好，也可能尚未建立（恢复早于首次 onSizeChanged）：
        // 前者按存档倍率重建，后者用存档画布尺寸 layout，之后再套用坐标。
        val canvasW = header[16]
        val canvasH = header[17]
        if (table == null && canvasW > 0f && canvasH > 0f) {
            resize(canvasW, canvasH, header[18], header[19])
        } else {
            relayout()
        }

        // 半径按当前球桌与倍率重算，绝不用存档里的旧半径（画布尺寸可能已变，
        // 旧半径过大会让 clampPaddle 区间反转并导致整档静默丢失）。
        val t = table
        val playerR = t?.paddleR?.times(paddleScale) ?: playerG[4]
        val aiR = t?.let { it.paddleR * (if (paddleScaleBoth) paddleScale else 1f) } ?: aiG[4]
        player = Body(playerG[0], playerG[1], playerG[2], playerG[3], playerR)
        ai = Body(aiG[0], aiG[1], aiG[2], aiG[3], aiR)
        puck = Body(puckG[0], puckG[1], puckG[2], puckG[3], t?.puckR ?: puckG[4])
        playerPrevX = player.x
        playerPrevY = player.y
        // 触摸点是瞬时输入，不落盘；恢复时清空，避免挡板瞬移到旧手指位置
        touchX = null
        touchY = null
        if (t != null) {
            val (px, py) = HockeyLogic.clampPaddle(t, player.x, player.y, isPlayer = true, paddleR = player.r)
            player.x = px
            player.y = py
            val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
            ai.x = ax
            ai.y = ay
            clampPuckToTable()
        }
        // aiTargetX/Y 初值为 (0,0)：恢复后若不修正，AI 会朝原点猛冲（冲角落）。
        // 同步到恢复位置并标记为「已过反应延迟」，下一帧立即重算目标。
        aiTargetX = ai.x
        aiTargetY = ai.y
        aiReactionAge = 99f
        playerScore = header[1].toInt()
        aiScore = header[2].toInt()
        serveTimer = header[3]
        scoreFlash = header[4]
        hitFlash = header[5]
        bestWins = header[11].toInt().coerceAtLeast(0)
        matchWinner = Scorer.entries.getOrNull(header[12].toInt())
        resultSubmitted = header[13] != 0f
        lastScorer = Scorer.entries.getOrNull(header[14].toInt())
        time = header[15]
        trail.clear()
        for (i in 4 until groups.size) {
            val g = groups[i]
            if (g.size >= 2) trail.addLast(g[0] to g[1])
        }
        phase = savedPhase
        return true
    }

    /**
     * 解析表头拖尾的场地主题 id（长度 + 字符码）：缺省 / 空段 / 越界返回 null，
     * 由调用方回退默认主题；第二返回值是下一个待读字段的位置，便于继续读 pending 主题。
     * 长度 -1 表示「无暂存」。
     */
    private fun readRinkThemeId(
        header: FloatArray,
        offset: Int,
    ): Pair<RinkTheme?, Int> {
        val len = header.getOrNull(offset)?.toInt() ?: return null to offset
        if (len < 0) return null to offset + 1
        val end = offset + len + 1
        if (len == 0 || end > header.size) return null to offset + 1
        val id = buildString { for (i in offset + 1 until end) append(header[i].toInt().toChar()) }
        return RinkTheme.fromId(id) to end
    }

    fun tick(dt: Float) {
        val t = table ?: return
        // 减弱动画：冻结装饰用时间轴，物理 / 计时（发球倒数、进球横幅）不受影响
        if (!reduceMotion) time += dt
        if (hitFlash > 0f) hitFlash = (hitFlash - dt * 3.2f).coerceAtLeast(0f)
        if (scoreFlash > 0f) scoreFlash = (scoreFlash - dt * 1.1f).coerceAtLeast(0f)
        if (hitFeedbackCooldown > 0f) hitFeedbackCooldown -= dt

        // 火花
        if (sparks.isNotEmpty()) {
            val it = sparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.life -= dt
                if (s.life <= 0f) {
                    it.remove()
                    continue
                }
                s.x += s.vx * dt
                s.y += s.vy * dt
                s.vy += 220f * dt
                s.vx *= 0.98f
            }
        }

        when (phase) {
            Phase.READY, Phase.MATCH_OVER -> return
            Phase.SERVE -> {
                followPlayer(t, dt)
                holdAiNearHome(t, dt)
                serveTimer -= dt
                if (serveTimer <= 0f) {
                    // 失球方接发球：球飞向失球一侧
                    val launchTowardPlayer =
                        when (lastScorer) {
                            Scorer.AI -> true
                            Scorer.PLAYER -> false
                            null -> Random.nextBoolean()
                        }
                    val (vx, vy) = HockeyLogic.serveVelocity(t, towardPlayer = launchTowardPlayer)
                    puck.vx = vx
                    puck.vy = vy
                    phase = Phase.PLAYING
                }
            }
            Phase.SCORED -> {
                followPlayer(t, dt)
                holdAiNearHome(t, dt)
                serveTimer -= dt
                if (serveTimer <= 0f) {
                    if (HockeyLogic.matchOver(playerScore, aiScore, winScore)) {
                        // 幂等结算：横幅期间界面可能已提前补结算（退出 / 提交榜单），
                        // 这里只负责推相位，胜场不会被重复累加
                        settleDecidedMatch()
                        phase = Phase.MATCH_OVER
                    } else {
                        resetPositions(serve = true)
                        phase = Phase.SERVE
                        // 进球横幅已充当「预备」节拍，直接 3、2、1 倒数
                        serveTimer = 1.5f
                    }
                }
            }
            Phase.PLAYING -> {
                stepPlaying(t, dt)
            }
        }
    }

    /**
     * 幂等结算：达到决胜分时记录胜者并累加胜场，返回本局是否已决胜。
     * 只结算数据、不推 [phase]——SCORED 进球横幅由 [tick] 播完再转 MATCH_OVER，
     * 提前退出时界面调用本方法补结算后直接提交榜单。
     */
    fun settleDecidedMatch(): Boolean {
        if (!HockeyLogic.matchOver(playerScore, aiScore, winScore)) return false
        if (matchWinner == null) {
            matchWinner = HockeyLogic.winner(playerScore, aiScore)
            if (matchWinner == Scorer.PLAYER) {
                bestWins += 1
            }
        }
        return true
    }

    private fun stepPlaying(
        t: Table,
        dt: Float,
    ) {
        val params = effectiveParams()
        followPlayer(t, dt)

        // AI：未到反应延迟时 updateAiTarget 只会原样返回缓存，直接跳过以省 Triple 分配
        aiReactionAge += dt
        if (aiReactionAge >= params.reactionDelay) {
            val (tx, ty) =
                HockeyLogic.updateAiTarget(
                    table = t,
                    puck = puck,
                    params = params,
                    reactionAge = aiReactionAge,
                    cachedTargetX = aiTargetX,
                    cachedTargetY = aiTargetY,
                    paddleR = ai.r,
                    ai = ai,
                )
            aiTargetX = tx
            aiTargetY = ty
            aiReactionAge = 0f
        }
        HockeyLogic.stepAiVelocity(ai, aiTargetX, aiTargetY, t, params, dt)
        ai.x += ai.vx * dt
        ai.y += ai.vy * dt
        // 门线解围期间才放开 AI 的 y 下界（允许压到 top+r/2），其余时刻保持原有走位手感
        val goalLineHug = HockeyLogic.aiGoalLineClearanceActive(t, puck, ai.r)
        val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r, aiGoalLineHug = goalLineHug)
        ai.x = ax
        ai.y = ay

        val pvx = player.vx
        val pvy = player.vy

        // 多子步防穿模
        val steps = 3
        val sub = dt / steps
        var scored: Scorer? = null
        var paddleContact = false
        var wallContact = false
        val frameStartX = puck.x
        val frameStartY = puck.y
        repeat(steps) {
            if (scored != null) return@repeat
            // 场地主题统一决定球面摩擦：AI 对局与玩家共用同一值
            val r = HockeyLogic.integratePuck(puck, t, sub, friction = rinkTheme.friction)
            if (r.scored != null) {
                scored = r.scored
                return@repeat
            }
            if (r.hitWall) wallContact = true
            val hitP =
                HockeyLogic.collidePaddle(puck, player, pvx, pvy, t) ||
                    HockeyLogic.collidePaddle(puck, ai, ai.vx, ai.vy, t)
            if (hitP) paddleContact = true
        }

        // 击球反馈按最小间隔节流：球被顶在墙角持续接触时，震动 / 火花不会连发
        if (paddleContact) {
            hitFlash = 1f
            if (hitFeedbackCooldown <= 0f) {
                hitFeedbackCooldown = HIT_FEEDBACK_INTERVAL
                pendingHapticHit = true
                spawnHitSparks(puck.x, puck.y)
            }
        } else if (wallContact && hitFeedbackCooldown <= 0f) {
            hitFeedbackCooldown = HIT_FEEDBACK_INTERVAL
            // 碰库：轻短反馈（与击球共用节流）
            pendingHapticHit = true
            spawnHitSparks(puck.x, puck.y, count = 4, muted = true)
        }

        // 角落区域挡板接触限速：1.85 恢复系数叠加墙面反弹会向被顶在墙角的球持续泵能，
        // 先压住速度上限，避免球在解脱前就加速到上限乱飞。
        if (paddleContact && HockeyLogic.inCornerZone(t, puck)) {
            HockeyLogic.clampSpeed(puck, 0f, t.shortSide * CORNER_CONTACT_SPEED_MUL)
        }
        // 门线解围同样限速：扫球是解围不是大力抽射，避免球被抽到全场乱飞后 AI 失位
        if (paddleContact && goalLineHug) {
            HockeyLogic.clampSpeed(puck, 0f, t.shortSide * CORNER_CONTACT_SPEED_MUL)
        }

        // 墙角死锁检测：单帧位移阈值会被「每帧 2–5px 的往复抖动」反复清零，
        // 改为 0.3s 窗口内看相对起点的净位移：几乎没离开原地 + 持续接触挡板即解脱。
        // 另外要求球处于墙角区域：沿直库（非角落）慢速推球同样「持续接触 + 净位移小」，
        // 但球会自行沿库弹开，不属于死锁，不应瞬移解脱。
        val nearWall =
            puck.x - puck.r < t.left + t.puckR * 2f ||
                puck.x + puck.r > t.right - t.puckR * 2f ||
                puck.y - puck.r < t.top + t.puckR * 2f ||
                puck.y + puck.r > t.bottom - t.puckR * 2f
        if (paddleContact && nearWall && HockeyLogic.inCornerZone(t, puck)) {
            if (!stuckWindowActive) {
                stuckWindowActive = true
                stuckWindowTime = 0f
                stuckWindowStartX = frameStartX
                stuckWindowStartY = frameStartY
            }
            stuckWindowTime += dt
            if (stuckWindowTime >= STUCK_WINDOW_SECONDS) {
                val netMoved = hypot(puck.x - stuckWindowStartX, puck.y - stuckWindowStartY)
                if (netMoved < t.puckR * 2f) {
                    hitFlash = 1f
                    pendingHapticHit = true
                    cornerEscapeCount += 1
                    HockeyLogic.cornerEscapeKick(t, puck)
                    spawnHitSparks(puck.x, puck.y, count = 12)
                }
                stuckWindowActive = false
                stuckWindowTime = 0f
            }
        } else {
            stuckWindowActive = false
            stuckWindowTime = 0f
        }

        // 轨迹
        trail.addFirst(puck.x to puck.y)
        while (trail.size > 12) trail.removeLast()

        scored?.let { onGoal(it) }
    }

    private fun onGoal(scorer: Scorer) {
        lastScorer = scorer
        when (scorer) {
            Scorer.PLAYER -> playerScore += 1
            Scorer.AI -> aiScore += 1
        }
        scoreFlash = 1f
        pendingHapticGoal = true
        puck.vx = 0f
        puck.vy = 0f
        phase = Phase.SCORED
        serveTimer = 1.15f
        spawnGoalBurst(scorer)
    }

    private fun followPlayer(
        t: Table,
        dt: Float,
    ) {
        val tx = touchX
        val ty = touchY
        playerPrevX = player.x
        playerPrevY = player.y
        if (tx != null && ty != null) {
            var (cx, cy) = HockeyLogic.clampPaddle(t, tx, ty, isPlayer = true, paddleR = player.r)
            if (phase == Phase.SERVE) {
                // 发球前球停在中点：挡板与球保持一圈距离，避免视觉压球、开球瞬间多吃一次碰撞冲量
                val clearY = t.midY + player.r + t.puckR + 4f
                if (clearY <= t.bottom - player.r) {
                    cy = cy.coerceAtLeast(clearY)
                }
            }
            // 跟手：直接贴坐标，无缓动
            player.x = cx
            player.y = cy
        }
        val inv = if (dt > 1e-5f) 1f / dt else 0f
        player.vx = (player.x - playerPrevX) * inv
        player.vy = (player.y - playerPrevY) * inv
        // 限一下极端甩拍
        val maxP = t.shortSide * 3.2f
        val sp = hypot(player.vx, player.vy)
        if (sp > maxP) {
            player.vx *= maxP / sp
            player.vy *= maxP / sp
        }
    }

    private fun holdAiNearHome(
        t: Table,
        dt: Float,
    ) {
        val (hx, hy) = HockeyLogic.aiHome(t)
        aiTargetX = hx
        aiTargetY = hy
        val params = effectiveParams()
        HockeyLogic.stepAiVelocity(ai, hx, hy, t, params, dt)
        ai.x += ai.vx * dt
        ai.y += ai.vy * dt
        val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
        ai.x = ax
        ai.y = ay
    }

    private fun spawnHitSparks(
        x: Float,
        y: Float,
        count: Int = 8,
        muted: Boolean = false,
    ) {
        val base = if (muted) 90f else 160f
        repeat(count) {
            val a = Random.nextFloat() * Math.PI.toFloat() * 2f
            val sp = base + Random.nextFloat() * base
            sparks +=
                HockeySpark(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(a) * sp,
                    vy = kotlin.math.sin(a) * sp,
                    life = 0.25f + Random.nextFloat() * 0.25f,
                    color =
                        if (muted) {
                            Color(0xFFB3E5FC)
                        } else {
                            Color(0xFFFFE082)
                        },
                )
        }
    }

    private fun spawnGoalBurst(scorer: Scorer) {
        val t = table ?: return
        val x = t.midX
        val y = if (scorer == Scorer.PLAYER) t.top + 8f else t.bottom - 8f
        val col = if (scorer == Scorer.PLAYER) Color(0xFFFFCA28) else Color(0xFF80DEEA)
        repeat(18) {
            val a = Random.nextFloat() * Math.PI.toFloat() * 2f
            val sp = 120f + Random.nextFloat() * 220f
            sparks +=
                HockeySpark(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(a) * sp,
                    vy = kotlin.math.sin(a) * sp,
                    life = 0.45f + Random.nextFloat() * 0.35f,
                    color = col,
                )
        }
    }
}
