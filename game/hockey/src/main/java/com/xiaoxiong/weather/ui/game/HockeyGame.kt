package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import com.xiaoxiong.weather.ui.game.HockeyLogic.Table
import kotlin.math.hypot
import kotlin.random.Random

/** 击球震动 / 火花的最小间隔（秒）：球被顶在墙角持续接触时，避免震动连发。 */
private const val HIT_FEEDBACK_INTERVAL = 0.12f

/** 墙角死锁判定：持续接触挡板且原地抖动超过该时长（秒），触发解脱。 */
private const val STUCK_ESCAPE_SECONDS = 0.35f

/** 「原地抖动」的每帧净位移阈值（相对短边）。 */
private const val STUCK_MOVE_EPS_MUL = 0.004f

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

    fun effectiveParams() = HockeyLogic.params(difficulty, customLevel)

    var playerScore by mutableIntStateOf(0)
    var aiScore by mutableIntStateOf(0)
    var bestWins by mutableIntStateOf(0)
    var resultSubmitted = false

    var table: Table? = null
    var player = Body(0f, 0f, r = 1f)
    var ai = Body(0f, 0f, r = 1f)
    var puck = Body(0f, 0f, r = 1f)

    /** 挡板尺寸倍率（设置可调）。 */
    var paddleScale by mutableFloatStateOf(HockeyLogic.PADDLE_SCALE_DEFAULT)

    /** 尺寸倍率生效对象：true 双方挡板都变大；false 仅玩家挡板变大（AI 保持标准）。 */
    var paddleScaleBoth by mutableStateOf(true)

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
    var hitFlash by mutableFloatStateOf(0f)
    var time by mutableFloatStateOf(0f)

    /** 击球震动 / 火花的节流计时。 */
    private var hitFeedbackCooldown = 0f

    /** 墙角死锁计时：球持续接触挡板且位移极小时累计，触发解脱后清零。 */
    private var stuckTime = 0f

    var aiTargetX = 0f
    var aiTargetY = 0f
    var aiReactionAge = 0f

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
        table = base
        puck.r = base.puckR
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
        }
    }

    /** 设置里改了挡板尺寸后，按最近的画布尺寸重建球桌。 */
    fun relayout() {
        if (lastCanvasW <= 0f || lastCanvasH <= 0f) return
        resize(lastCanvasW, lastCanvasH, lastPadH, lastPadV)
    }

    fun startMatch() {
        playerScore = 0
        aiScore = 0
        matchWinner = null
        lastScorer = null
        scoreFlash = 0f
        resultSubmitted = false
        sparks.clear()
        trail.clear()
        stuckTime = 0f
        hitFeedbackCooldown = 0f
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

    fun tick(dt: Float) {
        val t = table ?: return
        time += dt
        if (hitFlash > 0f) hitFlash = (hitFlash - dt * 3.2f).coerceAtLeast(0f)
        if (scoreFlash > 0f) scoreFlash = (scoreFlash - dt * 1.1f).coerceAtLeast(0f)
        if (hitFeedbackCooldown > 0f) hitFeedbackCooldown -= dt

        // 火花
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
                        matchWinner = HockeyLogic.winner(playerScore, aiScore)
                        if (matchWinner == Scorer.PLAYER) {
                            bestWins += 1
                        }
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

    private fun stepPlaying(
        t: Table,
        dt: Float,
    ) {
        val params = effectiveParams()
        followPlayer(t, dt)

        // AI
        aiReactionAge += dt
        val (tx, ty, refreshed) =
            HockeyLogic.updateAiTarget(
                table = t,
                puck = puck,
                params = params,
                reactionAge = aiReactionAge,
                cachedTargetX = aiTargetX,
                cachedTargetY = aiTargetY,
                paddleR = ai.r,
            )
        if (refreshed) {
            aiTargetX = tx
            aiTargetY = ty
            aiReactionAge = 0f
        }
        HockeyLogic.stepAiVelocity(ai, aiTargetX, aiTargetY, t, params, dt)
        ai.x += ai.vx * dt
        ai.y += ai.vy * dt
        val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
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
            val r = HockeyLogic.integratePuck(puck, t, sub)
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
            spawnHitSparks(puck.x, puck.y, count = 4, muted = true)
        }

        // 墙角死锁检测：球被挡板顶在墙角时，墙面反弹与挡板碰撞互相抵消，
        // 球原地高频抖动（持续接触 + 净位移极小）永远出不来——朝场内一脚解脱
        val moved = hypot(puck.x - frameStartX, puck.y - frameStartY)
        if (paddleContact && moved < t.shortSide * STUCK_MOVE_EPS_MUL) {
            stuckTime += dt
            if (stuckTime >= STUCK_ESCAPE_SECONDS) {
                stuckTime = 0f
                hitFlash = 1f
                pendingHapticHit = true
                HockeyLogic.cornerEscapeKick(t, puck)
                spawnHitSparks(puck.x, puck.y, count = 12)
            }
        } else {
            stuckTime = (stuckTime - dt * 2f).coerceAtLeast(0f)
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
