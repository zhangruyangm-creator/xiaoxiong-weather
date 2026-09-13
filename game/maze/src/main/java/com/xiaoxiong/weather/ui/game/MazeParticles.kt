package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 迷宫事件特效宿主：拾取/开锁/传送/通关的爆发粒子 + 小熊演出标记 + 撞墙屏幕轻抖。
 *
 * 约束：
 *  - 粒子用并行 FloatArray 环形池存储，不产生每帧大对象；超出容量覆盖最旧。
 *  - 粒子位置用「格坐标」（(0,0) 为 0 行 0 列中心），与画布解耦。
 *  - 所有随机量由 [mazeHash01] 从自增序号派生，事件确定可复现。
 *  - 状态观测（[observe]）只读 [MazeGame]，在绘制阶段调用；不改玩法状态。
 */
internal class MazeFxHost {
    private companion object {
        const val CAP = 192
        const val KIND_DOT = 0
        const val KIND_STAR = 1
        const val KIND_SHARD = 2
        const val KIND_DEBRIS = 3
        const val KIND_CONFETTI = 4
        const val KIND_RING = 5
        const val KIND_DUST = 6
    }

    private val pX = FloatArray(CAP)
    private val pY = FloatArray(CAP)
    private val pVx = FloatArray(CAP)
    private val pVy = FloatArray(CAP)
    private val pG = FloatArray(CAP)
    private val pBorn = FloatArray(CAP)
    private val pLife = FloatArray(CAP)
    private val pSize = FloatArray(CAP)
    private val pKind = IntArray(CAP)
    private val pColor = IntArray(CAP)
    private var count = 0
    private var cursor = 0
    private var serial = 1

    /** 撞墙回弹：时间点与方向（格坐标单位向量）。 */
    var bumpAt = -10f
        private set
    var bumpDr = 0f
        private set
    var bumpDc = 0f
        private set

    /** 落地挤压 / 拾取上跳 / 传送旋入 / 开锁 / 通关。 */
    var landAt = -10f
        private set
    var hopAt = -10f
        private set
    var portalAt = -10f
        private set
    var portalFromR = 0
        private set
    var portalFromC = 0
        private set
    var unlockAt = -10f
        private set
    var unlockR = 0
        private set
    var unlockC = 0
        private set
    var clearAt = -10f
        private set

    private var prevGrid: Array<Array<MazeCell>>? = null
    private var prevHoney = -1
    private var prevKeys = -1
    private var prevR = -1
    private var prevC = -1
    private var prevMoves = -1
    private var prevMoving = false
    private var prevCleared = false
    private var lastTrailAt = -10f

    /** 通关庆祝是否已被 [observe] 观测到（绘制阶段才派发），避免帧循环抢跑暂停。 */
    private var clearObserved = false

    private fun spawn(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        gravity: Float,
        life: Float,
        size: Float,
        kind: Int,
        color: Color,
    ) {
        val i = cursor
        pX[i] = x
        pY[i] = y
        pVx[i] = vx
        pVy[i] = vy
        pG[i] = gravity
        pBorn[i] = lastEventTime
        pLife[i] = life
        pSize[i] = size
        pKind[i] = kind
        pColor[i] = color.toArgb()
        cursor = (cursor + 1) % CAP
        if (count < CAP) count++
        serial++
    }

    private var lastEventTime = 0f

    /** 撞墙/被锁挡住：回弹 + 尘土 + 屏幕轻抖（由 UI 回调触发，非每帧）。 */
    fun bump(
        time: Float,
        dr: Int,
        dc: Int,
        r: Int,
        c: Int,
    ) {
        lastEventTime = time
        bumpAt = time
        bumpDr = dr.toFloat()
        bumpDc = dc.toFloat()
        val bx = c + dc * 0.38f
        val by = r + dr * 0.38f
        for (i in 0 until 6) {
            val a = mazeHash01(serial * 7 + i, 501) * Math.PI.toFloat() * 2f
            val sp = 0.6f + 0.9f * mazeHash01(serial + i, 502)
            spawn(
                x = bx,
                y = by,
                vx = cos(a) * sp - dc * 0.5f,
                vy = sin(a) * sp - dr * 0.5f,
                gravity = 1.6f,
                life = 0.32f + 0.20f * mazeHash01(serial + i, 503),
                size = 0.035f + 0.03f * mazeHash01(serial + i, 504),
                kind = KIND_DUST,
                color = Color(0xFFD8C8A8),
            )
        }
    }

    /** 每帧观测对局状态变化并派发特效；不修改对局状态。 */
    fun observe(
        game: MazeGame,
        style: MazeThemeStyle,
    ) {
        if (game.rows <= 0 || game.cols <= 0) return
        lastEventTime = game.time
        if (prevGrid !== game.grid) {
            prevGrid = game.grid
            count = 0
            cursor = 0
            // 宿主跨关卡复用时清掉上一局的演出时间点，效果等同新建实例
            bumpAt = -10f
            landAt = -10f
            hopAt = -10f
            portalAt = -10f
            unlockAt = -10f
            clearAt = -10f
            clearObserved = false
            prevHoney = game.honeyLeft
            prevKeys = game.keys
            prevR = game.bearR
            prevC = game.bearC
            prevMoves = game.moves
            prevMoving = game.moving
            prevCleared = game.cleared
            return
        }
        val t = game.time
        val br = game.bearR.toFloat()
        val bc = game.bearC.toFloat()

        // 落地：从移动到静止
        if (prevMoving && !game.moving && game.phase == MazePhase.PLAYING) landAt = t

        // 拾取蜂蜜
        if (game.honeyLeft < prevHoney) {
            hopAt = t
            val honey = Color(0xFFFFB300)
            val light = Color(0xFFFFE082)
            for (i in 0 until 12) {
                val a = Math.PI.toFloat() * 2f * i / 12f + mazeHash01(serial, 511) * 0.4f
                val sp = 0.8f + 1.1f * mazeHash01(serial + i, 512)
                spawn(
                    x = bc,
                    y = br,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp - 0.5f,
                    gravity = 2.6f,
                    life = 0.42f + 0.24f * mazeHash01(serial + i, 513),
                    size = 0.045f + 0.04f * mazeHash01(serial + i, 514),
                    kind = if (i % 3 == 0) KIND_STAR else KIND_DOT,
                    color = if (i % 2 == 0) honey else light,
                )
            }
            spawn(bc, br, 0f, 0f, 0f, 0.34f, 0.10f, KIND_RING, light.copy(alpha = 0.9f))
        }

        // 钥匙 / 开锁
        if (game.keys > prevKeys) {
            hopAt = t
            for (i in 0 until 9) {
                val a = Math.PI.toFloat() * 2f * i / 9f
                spawn(
                    x = bc,
                    y = br,
                    vx = cos(a) * 0.9f,
                    vy = sin(a) * 0.9f - 0.7f,
                    gravity = 2.4f,
                    life = 0.40f + 0.22f * mazeHash01(serial + i, 521),
                    size = 0.04f + 0.03f * mazeHash01(serial + i, 522),
                    kind = KIND_STAR,
                    color = Color(0xFFFFD54F),
                )
            }
        } else if (game.keys < prevKeys) {
            unlockAt = t
            unlockR = game.bearR
            unlockC = game.bearC
            val wood = Color(0xFF8D6E63)
            val iron = Color(0xFFB0BEC5)
            for (i in 0 until 14) {
                val a = mazeHash01(serial + i, 531) * Math.PI.toFloat() * 2f
                val sp = 0.7f + 1.0f * mazeHash01(serial + i, 532)
                spawn(
                    x = bc,
                    y = br,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp - 0.4f,
                    gravity = 3.4f,
                    life = 0.38f + 0.26f * mazeHash01(serial + i, 533),
                    size = 0.03f + 0.035f * mazeHash01(serial + i, 534),
                    kind = if (i % 4 == 3) KIND_DOT else KIND_DEBRIS,
                    color = if (i % 3 == 0) iron else wood,
                )
            }
        }

        // 传送：非移动状态下的位置跳变（撤销会让 moves 变小，排除）
        val teleported =
            game.phase == MazePhase.PLAYING &&
                !game.moving &&
                (game.bearR != prevR || game.bearC != prevC) &&
                game.moves >= prevMoves
        if (teleported) {
            portalAt = t
            portalFromR = prevR
            portalFromC = prevC
            val color = style.fx
            for (i in 0 until 14) {
                val a = Math.PI.toFloat() * 2f * i / 14f + mazeHash01(serial, 541)
                val sp = 0.5f + 0.9f * mazeHash01(serial + i, 542)
                spawn(
                    x = bc,
                    y = br,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp,
                    gravity = 0f,
                    life = 0.30f + 0.30f * mazeHash01(serial + i, 543),
                    size = 0.035f + 0.03f * mazeHash01(serial + i, 544),
                    kind = if (i % 2 == 0) KIND_DOT else KIND_SHARD,
                    color = color,
                )
            }
            // 出发点：被吸入的收束粒子
            val fx0 = prevC.toFloat()
            val fy0 = prevR.toFloat()
            for (i in 0 until 10) {
                val a = Math.PI.toFloat() * 2f * i / 10f
                val sp = 0.4f + 0.7f * mazeHash01(serial + i, 545)
                spawn(
                    x = fx0 + cos(a) * 0.45f,
                    y = fy0 + sin(a) * 0.45f,
                    vx = -cos(a) * sp,
                    vy = -sin(a) * sp,
                    gravity = 0f,
                    life = 0.28f + 0.22f * mazeHash01(serial + i, 546),
                    size = 0.03f + 0.025f * mazeHash01(serial + i, 547),
                    kind = KIND_DOT,
                    color = color,
                )
            }
        }

        // 通关庆祝
        if (game.cleared && !prevCleared) {
            clearAt = t
            clearObserved = true
            for (i in 0 until 36) {
                val a = mazeHash01(serial + i, 551) * Math.PI.toFloat() * 2f
                val sp = 0.8f + 1.8f * mazeHash01(serial + i, 552)
                spawn(
                    x = bc + (mazeHash01(serial + i, 553) - 0.5f) * 2.4f,
                    y = br - 0.6f,
                    vx = cos(a) * sp * 0.6f,
                    vy = sin(a) * sp - 1.4f,
                    gravity = 2.2f,
                    life = 0.8f + 0.7f * mazeHash01(serial + i, 554),
                    size = 0.05f + 0.05f * mazeHash01(serial + i, 555),
                    kind = KIND_CONFETTI,
                    color = mazeConfetti[i % mazeConfetti.size],
                )
            }
        }

        // 移动拖尾：冰面冰屑 / 普通地面扬尘
        if (game.moving && t - lastTrailAt > (if (game.sliding) 0.045f else 0.085f)) {
            lastTrailAt = t
            val dr = trailDr
            val dc = trailDc
            val tx = bc - dc * 0.35f
            val ty = br - dr * 0.35f
            if (game.sliding) {
                for (i in 0 until 3) {
                    spawn(
                        x = tx,
                        y = ty,
                        vx = (mazeHash01(serial + i, 561) - 0.5f) * 1.2f,
                        vy = (mazeHash01(serial + i, 562) - 0.5f) * 1.2f,
                        gravity = 0.6f,
                        life = 0.26f + 0.16f * mazeHash01(serial + i, 563),
                        size = 0.03f + 0.025f * mazeHash01(serial + i, 564),
                        kind = KIND_SHARD,
                        color = Color(0xFFE1F5FE),
                    )
                }
            } else if (style.fx.alpha > 0f) {
                spawn(
                    x = tx,
                    y = ty,
                    vx = (mazeHash01(serial, 571) - 0.5f) * 0.5f,
                    vy = -0.25f * mazeHash01(serial, 572),
                    gravity = 1.2f,
                    life = 0.30f,
                    size = 0.035f,
                    kind = KIND_DUST,
                    color = style.fx.copy(alpha = 0.55f),
                )
            }
        }

        prevHoney = game.honeyLeft
        prevKeys = game.keys
        prevR = game.bearR
        prevC = game.bearC
        prevMoves = game.moves
        prevMoving = game.moving
        prevCleared = game.cleared
    }

    private var trailDr = 1
    private var trailDc = 0

    /** 由 [MazeRender] 在绘制小熊前写入当前朝向，供拖尾反向推导。 */
    fun setTrailDir(
        dr: Int,
        dc: Int,
    ) {
        trailDr = dr
        trailDc = dc
    }

    /** 撞墙回弹强度 0..1（约 0.35s 衰减）。 */
    fun bumpDecay(time: Float): Float {
        val age = time - bumpAt
        if (age < 0f || age > 0.35f) return 0f
        val k = 1f - age / 0.35f
        return k * k
    }

    /** 落地挤压强度 0..1（约 0.22s 衰减）。 */
    fun landDecay(time: Float): Float {
        val age = time - landAt
        if (age < 0f || age > 0.22f) return 0f
        return 1f - age / 0.22f
    }

    /** 拾取上跳高度 0..1（单次弧线）。 */
    fun hopLift(time: Float): Float {
        val age = time - hopAt
        if (age < 0f || age > 0.34f) return 0f
        return sin(Math.PI.toFloat() * age / 0.34f)
    }

    /** 传送旋入进度 0..1（0=刚出现，1=完成）。 */
    fun portalSpin(time: Float): Float {
        val age = time - portalAt
        if (age < 0f || age > 0.55f) return 1f
        val k = age / 0.55f
        return k * k * (3f - 2f * k)
    }

    /** 传送是否正在播放。 */
    fun portalActive(time: Float): Boolean {
        val age = time - portalAt
        return age in 0f..0.55f
    }

    /** 通关庆祝已过去的时间（秒），未通关返回 -1。 */
    fun celebrateAge(time: Float): Float {
        val age = time - clearAt
        return if (age < 0f || age > 6f) -1f else age
    }

    /** 屏幕轻抖的像素偏移量（已按格大小缩放）；reduceMotion 下由调用方置零。 */
    fun shakeAmp(
        time: Float,
        cell: Float,
    ): Float = bumpDecay(time) * cell * 0.10f

    /**
     * 原地压缩环形池：把仍存活的粒子按相对顺序搬到数组前段，
     * 并把写入游标同步到存活区间末尾（`count % CAP`），
     * 否则环形回绕会覆盖仍存活的粒子。返回存活数量。
     */
    fun compact(time: Float): Int {
        var write = 0
        for (read in 0 until count) {
            val life = pLife[read]
            val age = time - pBorn[read]
            if (age < 0f || age >= life) continue
            if (write != read) {
                pX[write] = pX[read]
                pY[write] = pY[read]
                pVx[write] = pVx[read]
                pVy[write] = pVy[read]
                pG[write] = pG[read]
                pBorn[write] = pBorn[read]
                pLife[write] = pLife[read]
                pSize[write] = pSize[read]
                pKind[write] = pKind[read]
                pColor[write] = pColor[read]
            }
            write++
        }
        count = write
        cursor = count % CAP
        return count
    }

    /** 当前时间下仍存活的粒子数（绘制区间 `[0, count)` 内）。 */
    fun liveCount(time: Float): Int {
        var live = 0
        for (i in 0 until count) {
            val age = time - pBorn[i]
            if (age >= 0f && age < pLife[i]) live++
        }
        return live
    }

    /**
     * 对局是否已完全静止：通关庆祝已结束且没有任何存活粒子。
     * 通关但庆祝尚未被 [observe] 观测时返回 false，避免帧循环抢在首帧绘制前暂停。
     */
    fun isIdle(game: MazeGame): Boolean {
        if (game.cleared && !clearObserved) return false
        if (celebrateAge(game.time) >= 0f) return false
        return liveCount(game.time) == 0
    }

    /** 事件粒子绘制（reduceMotion 时由调用方跳过）。 */
    fun DrawScope.drawParticles(
        layout: MazeBoardLayout,
        time: Float,
    ) {
        if (count <= 0 || layout.cell <= 0f) return
        val live = compact(time)
        if (live == 0) return
        for (i in 0 until live) {
            drawParticle(i, time - pBorn[i], pLife[i], layout, time)
        }
    }

    private fun DrawScope.drawParticle(
        i: Int,
        age: Float,
        life: Float,
        layout: MazeBoardLayout,
        time: Float,
    ) {
        val cell = layout.cell
        val k = (age / life).coerceIn(0f, 1f)
        val x = layout.ox + (pX[i] + pVx[i] * age + 0.5f) * cell
        val y = layout.oy + (pY[i] + pVy[i] * age + 0.5f * pG[i] * age * age + 0.5f) * cell
        val alpha = (1f - k) * (1f - k)
        val size = max(0.5f, pSize[i] * cell * (0.6f + 0.6f * (1f - k)))
        val color = Color(pColor[i])
        when (pKind[i]) {
            KIND_DOT -> {
                drawCircle(color.copy(alpha = 0.20f * alpha), size * 1.9f, Offset(x, y))
                drawCircle(color.copy(alpha = 0.92f * alpha), size, Offset(x, y))
            }
            KIND_STAR -> {
                drawLine(
                    color.copy(alpha = 0.95f * alpha),
                    Offset(x - size * 1.8f, y),
                    Offset(x + size * 1.8f, y),
                    strokeWidth = max(1f, size * 0.5f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color.copy(alpha = 0.95f * alpha),
                    Offset(x, y - size * 1.8f),
                    Offset(x, y + size * 1.8f),
                    strokeWidth = max(1f, size * 0.5f),
                    cap = StrokeCap.Round,
                )
                drawCircle(Color.White.copy(alpha = 0.85f * alpha), size * 0.5f, Offset(x, y))
            }
            KIND_SHARD -> {
                val spin = time * 5f + i
                val ux = cos(spin)
                val uy = sin(spin)
                drawLine(
                    color.copy(alpha = 0.85f * alpha),
                    Offset(x - ux * size * 1.6f, y - uy * size * 1.6f),
                    Offset(x + ux * size * 1.6f, y + uy * size * 1.6f),
                    strokeWidth = max(1f, size * 0.6f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White.copy(alpha = 0.55f * alpha),
                    Offset(x - uy * size * 1.1f, y + ux * size * 1.1f),
                    Offset(x + uy * size * 1.1f, y - ux * size * 1.1f),
                    strokeWidth = max(1f, size * 0.4f),
                    cap = StrokeCap.Round,
                )
            }
            KIND_DEBRIS -> {
                drawLine(
                    color.copy(alpha = 0.90f * alpha),
                    Offset(x - size * 1.2f, y - size * 0.4f),
                    Offset(x + size * 1.2f, y + size * 0.4f),
                    strokeWidth = max(1f, size * 0.9f),
                    cap = StrokeCap.Round,
                )
            }
            KIND_CONFETTI -> {
                val phase = sin(time * 6f + i * 1.7f)
                drawRect(
                    color.copy(alpha = 0.92f * alpha),
                    topLeft = Offset(x - size, y - size * 0.6f),
                    size = Size(size * 2f, size * 1.2f * (0.4f + 0.6f * abs(phase))),
                )
            }
            KIND_RING -> {
                drawCircle(
                    color.copy(alpha = 0.75f * alpha),
                    size * (1f + 2.6f * k),
                    Offset(x, y),
                    style = mazeStroke(max(1f, cell * 0.02f)),
                )
            }
            else -> {
                drawCircle(color.copy(alpha = 0.45f * alpha), size * 1.4f, Offset(x, y))
                drawCircle(color.copy(alpha = 0.7f * alpha), size * 0.7f, Offset(x, y))
            }
        }
    }
}

private val mazeConfetti =
    arrayOf(
        Color(0xFFFF5252),
        Color(0xFFFFD54F),
        Color(0xFF69F0AE),
        Color(0xFF40C4FF),
        Color(0xFFFF80AB),
    )

/** 主题氛围微粒：确定性时间驱动，数量受 reduceMotion 控制。 */
internal fun DrawScope.drawThemeMotes(
    theme: MazeTheme,
    style: MazeThemeStyle,
    time: Float,
    reduceMotion: Boolean,
) {
    if (reduceMotion || size.width <= 0f || size.height <= 0f) return
    when (theme) {
        MazeTheme.GARDEN -> {
            for (i in 0 until 14) {
                val speed = 10f + 14f * mazeHash01(i, 601)
                val x = (mazeHash01(i, 602) * size.width + sin(time * 0.6f + i) * 18f)
                val y = ((mazeHash01(i, 603) * size.height + time * speed) % (size.height + 30f)) - 15f
                drawCircle(
                    Color(0xFFFFF59D).copy(alpha = 0.35f + 0.25f * mazeHash01(i, 604)),
                    2f + 1.6f * mazeHash01(i, 605),
                    Offset(x, y),
                )
            }
        }
        MazeTheme.SNOW -> {
            for (i in 0 until 12) {
                val speed = 14f + 18f * mazeHash01(i, 611)
                val x = (mazeHash01(i, 612) * size.width + sin(time * 0.8f + i) * 14f) % size.width
                val y = ((mazeHash01(i, 613) * size.height + time * speed) % (size.height + 24f)) - 12f
                drawCircle(Color.White.copy(alpha = 0.5f), 1.6f + 1.4f * mazeHash01(i, 614), Offset(x, y))
            }
        }
        MazeTheme.NIGHT -> {
            for (i in 0 until 10) {
                val x = (mazeHash01(i, 621) * size.width + sin(time * 0.5f + i * 2.1f) * 26f)
                val y = (mazeHash01(i, 622) * size.height * 0.7f + cos(time * 0.4f + i) * 18f)
                val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 2.4f + i * 1.3f))
                drawCircle(style.fx.copy(alpha = tw), 2.2f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.6f * tw), 1f, Offset(x, y))
            }
        }
        MazeTheme.FESTIVAL -> {
            for (i in 0 until 16) {
                val speed = 16f + 22f * mazeHash01(i, 631)
                val x = (mazeHash01(i, 632) * size.width + sin(time * 1.1f + i) * 20f) % size.width
                val y = ((mazeHash01(i, 633) * size.height + time * speed) % (size.height + 24f)) - 12f
                drawCircle(
                    style.fx.copy(alpha = 0.4f + 0.35f * mazeHash01(i, 634)),
                    1.8f + 1.6f * mazeHash01(i, 635),
                    Offset(x, y),
                )
            }
        }
        MazeTheme.CLOUD -> {
            for (i in 0 until 6) {
                val span = size.width + 220f
                val x = ((mazeHash01(i, 641) * span + time * (7f + 5f * mazeHash01(i, 642))) % span) - 110f
                val y = size.height * (0.08f + 0.16f * mazeHash01(i, 643))
                drawCircle(Color.White.copy(alpha = 0.10f), 60f + 50f * mazeHash01(i, 644), Offset(x, y))
            }
        }
        MazeTheme.RAIN -> {
            for (i in 0 until 10) {
                val speed = 90f + 60f * mazeHash01(i, 651)
                val x = (mazeHash01(i, 652) * size.width + time * 12f) % size.width
                val y = ((mazeHash01(i, 653) * size.height + time * speed) % (size.height + 40f)) - 20f
                drawLine(
                    Color(0xFFB3D4EE).copy(alpha = 0.30f),
                    Offset(x, y),
                    Offset(x - 3f, y + 12f + 8f * mazeHash01(i, 654)),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 冰面滑行拖尾：沿运动反方向的两道高光，增强滑行速度感；减弱动画时冻结不画。 */
internal fun DrawScope.drawIceTrail(
    game: MazeGame,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    if (!game.moving || !game.sliding) return
    val cell = layout.cell
    if (cell <= 0f) return
    val (br, bc) = game.bearDrawPos()
    val cx = layout.ox + (bc + 0.5f) * cell
    val cy = layout.oy + (br + 0.5f) * cell
    val dir =
        when (game.facing) {
            MazeDir.UP -> -1f to 0f
            MazeDir.DOWN -> 1f to 0f
            MazeDir.LEFT -> 0f to -1f
            MazeDir.RIGHT -> 0f to 1f
        }
    val len = cell * 0.85f
    val taper = cell * 0.07f
    val nx = -dir.second
    val ny = -dir.first
    for (side in -1..1 step 2) {
        val ox = cx - dir.second * len * 0.45f + nx * taper * side
        val oy = cy - dir.first * len * 0.45f + ny * taper * side
        val ex = cx - dir.second * len * 1.35f + nx * taper * 0.3f * side
        val ey = cy - dir.first * len * 1.35f + ny * taper * 0.3f * side
        drawLine(
            Color(0xFFE1F5FE).copy(alpha = 0.42f),
            Offset(ox, oy),
            Offset(ex, ey),
            strokeWidth = max(1f, cell * 0.045f),
            cap = StrokeCap.Round,
        )
    }
}

/** 传送门附近的吸入/吐出微粒（确定性时间驱动）。 */
internal fun DrawScope.drawPortalMotes(
    game: MazeGame,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val cell = layout.cell
    if (cell <= 0f) return
    val time = game.time
    for (r in 0 until game.rows) {
        for (c in 0 until game.cols) {
            if (game.grid[r][c].tile != MazeTile.PORTAL) continue
            val cx = layout.ox + (c + 0.5f) * cell
            val cy = layout.oy + (r + 0.5f) * cell
            for (i in 0 until 6) {
                val phase = (time * 0.7f + i / 6f + mazeHash01(r * 31 + c * 17 + i, 661)) % 1f
                val radius = cell * (0.42f - 0.24f * phase)
                val a = time * 1.6f + i * 1.047f
                val px = cx + cos(a) * radius
                val py = cy + sin(a) * radius * 0.8f
                drawCircle(
                    style.fx.copy(alpha = 0.65f * (1f - phase)),
                    max(1f, cell * 0.035f * (1f - phase * 0.5f)),
                    Offset(px, py),
                )
            }
        }
    }
}

/** 出口光晕呼吸（开门后增强）。 */
internal fun DrawScope.drawExitGlow(
    game: MazeGame,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (game.exitR !in 0 until game.rows || game.exitC !in 0 until game.cols) return
    val cell = layout.cell
    if (cell <= 0f) return
    val open = game.honeyLeft == 0
    val cx = layout.ox + (game.exitC + 0.5f) * cell
    val cy = layout.oy + (game.exitR + 0.5f) * cell
    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(game.time * 2.6f)
    val base = if (open) 0.30f + 0.22f * pulse else 0.10f + 0.05f * pulse
    drawCircle(style.accent.copy(alpha = base * 0.45f), cell * 0.62f, Offset(cx, cy))
    drawCircle(style.accent.copy(alpha = base * 0.35f), cell * 0.44f, Offset(cx, cy))
    if (!reduceMotion && open) {
        val rise = (game.time * 0.55f) % 1f
        for (i in 0 until 5) {
            val p = (rise + i / 5f) % 1f
            val px = cx + sin((p + i) * 6.28f) * cell * 0.16f
            val py = cy - p * cell * 0.5f
            drawCircle(
                Color.White.copy(alpha = 0.55f * (1f - p)),
                max(1f, cell * 0.03f),
                Offset(px, py),
            )
        }
    }
    // 无解提示浮标由 UI 层负责，这里仅画出口的光。
    if (game.needHoneyHintT > 0f && !reduceMotion) {
        val a = (game.needHoneyHintT / 2.2f).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.30f * a), cell * (0.5f + 0.1f * (1f - a)), Offset(cx, cy))
    }
}
