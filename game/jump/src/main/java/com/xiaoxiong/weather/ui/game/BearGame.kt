package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 跑酷场景：跟随当前天气。阴/雨（按强度分档）/雪/雷暴/冻雨/冰雹渲染对应粒子与灰调背景。 */
enum class RunScene { SUNNY, CLOUDY, RAIN_LIGHT, RAIN, RAIN_HEAVY, SNOW, STORM, FREEZING_RAIN, HAIL }

/** 跑酷快照键：对局进行中落盘，结束/重开/退出时清除。 */
private const val JUMP_SNAPSHOT_KEY = "jump_snapshot_v1"

/** 快照 meta 段的字段数，用于坏档校验。 */
private const val JUMP_SNAPSHOT_META_FIELDS = 22

enum class ObstacleType { PUDDLE, SNOWMAN, STORM_CLOUD, ICE_WALL }

class Obstacle(
    val type: ObstacleType,
    var x: Float,
    val w: Float,
    val h: Float,
)

class Coin(
    var x: Float,
    val y: Float,
    val r: Float,
)

class BgCloud(
    var x: Float,
    var y: Float,
    val scale: Float,
    val layer: Float,
)

class FloatText(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float = 0.8f,
)

/**
 * 小熊跑酷全部可变状态。物理量在普通字段里（每帧变化不走快照），
 * 只有阶段/最高分用 Compose state 驱动 HUD 重组。
 */
class BearGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)

    /** HUD 实时分数：仅在整数值变化时写入，避免组合期读取帧计数器导致每帧整屏重组。 */
    var hudScore by mutableIntStateOf(0)

    /** 连续吃金币、无漏接的连击数；仅供 HUD 展示（漏接/死亡/重开归零）。 */
    var combo by mutableIntStateOf(0)

    /** 当前天气场景；由 [BearGameScreen] 在天气变化时刷新。 */
    var scene by mutableStateOf(RunScene.SUNNY)

    var size = IntSize.Zero
    var time = 0f
    var distance = 0f
    var speed = 0f
    var coinCount = 0
    var bearY = 0f
    var bearVY = 0f
    var jumpsLeft = 2
    var grounded = true
    var nextGap = Float.MAX_VALUE

    /** 视差山丘 Path 缓存：key = (heightRatio, s)，避免每帧重建 Path 引发 GC。 */
    val hillPathCache = mutableMapOf<Pair<Float, Float>, Path>()
    var bob = 0f
    var runPhase = 0f
    var deadT = 0f

    /** 眨眼倒计时；< 0 的 0.12s 内为闭眼。 */
    var blinkT = 2.5f

    /** 落地挤压计时；< 0 表示未触发。 */
    var squashT = -1f

    /** 二段跳空翻进度计时；< 0 表示未触发。 */
    var flipT = -1f

    /** 死亡屏幕震动剩余时间。 */
    var shakeT = 0f
    var dustT = 0f

    /** 离地时长（秒）：土狼时间内仍可补第一段跳。 */
    var airTime = 0f

    /** 跳跃缓冲剩余时间（秒）：落地前点按会缓存，落地时立即消费。 */
    var jumpBufferT = 0f

    /** 下一个里程碑分数阈值：每 500 分飘一个字。 */
    var nextMilestone = 500

    val obstacles = mutableListOf<Obstacle>()
    val coins = mutableListOf<Coin>()
    val clouds = mutableListOf<BgCloud>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<FloatText>()

    /** 主动放弃/结束/退出后置位：阻止 ON_STOP/onDispose 的兜底落盘把已清理的对局写回复活。 */
    private var snapshotDiscarded = false

    /** 恢复出的快照尺寸：首次 resize 若与之一致则保留恢复状态，避免被整局回准备态。 */
    private var restoredSize: IntSize? = null

    val s get() = size.height / 820f
    val groundY get() = size.height * 0.80f
    val bearSize get() = size.height * 0.115f
    val bearX get() = size.width * 0.18f
    private val floorY get() = groundY - bearSize
    private val jumpV get() = BearGameLogic.jumpV(s)
    private val jumpV2 get() = BearGameLogic.jumpV2(s)
    private val maxSpeed get() = BearGameLogic.maxSpeed(s)

    // 速度曲线由纯距离分数驱动（详见 BearGameLogic.speedAt）
    private fun speedAt(distanceScore: Float): Float = BearGameLogic.speedAt(distanceScore, s)

    // 步频随速度渐快并封顶，避免高速抽帧（详见 BearGameLogic.runPhaseRate）
    private fun runPhaseRate(): Float = BearGameLogic.runPhaseRate(speed, s)

    fun score(): Int = BearGameLogic.score(distance, coinCount, s)

    fun resize(
        newSize: IntSize,
        preserveRunning: Boolean = false,
    ) {
        if (newSize == size || newSize.height <= 0) return
        val oldS = s
        // 恢复快照后首次拿到与存档一致的尺寸：保留恢复出的对局，不能整局回准备态；
        // 真实旋转导致尺寸变化时 restoredSize 不匹配，仍按原逻辑重置。
        // preserveRunning：暂停中改窗口尺寸（分屏折叠等）保留对局，避免暂停菜单分数失效。
        val keepRestored = phase == GamePhase.RUNNING && (newSize == restoredSize || preserveRunning)
        size = newSize
        // s 随尺寸变化，旧的 Path 不再命中且会占内存，直接清空
        hillPathCache.clear()
        restoredSize = null
        if (keepRestored || phase == GamePhase.OVER) {
            // 保留对局/结算展示：按新缩放折算距离，resize 前后 score() 数值保持一致
            if (oldS > 0f) distance *= s / oldS
        }
        if (!keepRestored) {
            when (phase) {
                // 结算界面改窗口（旋转/分屏）：只更新尺寸，保留本局分数（对齐接雨雪）
                GamePhase.OVER -> Unit
                // 对局中改尺寸会让障碍像素与碰撞错位，直接整局回准备态；
                // 同时标记快照失效，避免停在 READY 被兜底落盘写回复活。
                GamePhase.RUNNING -> {
                    snapshotDiscarded = true
                    phase = GamePhase.READY
                    reset()
                }
                else -> {
                    snapshotDiscarded = true
                    reset()
                }
            }
        }
        bearY = bearY.coerceIn(-size.height.toFloat(), floorY)
        if (clouds.isEmpty()) {
            repeat(5) {
                clouds +=
                    BgCloud(
                        x = Random.nextFloat() * size.width,
                        y = size.height * (0.06f + Random.nextFloat() * 0.22f),
                        scale = 0.6f + Random.nextFloat() * 0.9f,
                        layer = if (it % 2 == 0) 0.15f else 0.3f,
                    )
            }
        }
    }

    fun reset() {
        time = 0f
        distance = 0f
        speed = 0f
        coinCount = 0
        hudScore = 0
        bearY = floorY
        bearVY = 0f
        jumpsLeft = 2
        grounded = true
        nextGap = 560f * s
        runPhase = 0f
        deadT = 0f
        bob = 0f
        blinkT = 2.5f
        squashT = -1f
        flipT = -1f
        shakeT = 0f
        dustT = 0f
        airTime = 0f
        jumpBufferT = 0f
        combo = 0
        nextMilestone = 500
        obstacles.clear()
        coins.clear()
        particles.clear()
        texts.clear()
    }

    fun start() {
        snapshotDiscarded = false
        reset()
        phase = GamePhase.RUNNING
        speed = 340f * s
    }

    fun tap(onJump: () -> Unit = {}) {
        when (phase) {
            GamePhase.READY -> start()
            GamePhase.RUNNING ->
                when (BearGameLogic.resolveJumpIntent(grounded, airTime, jumpsLeft)) {
                    BearGameLogic.JumpIntent.FIRST -> {
                        bearVY = jumpV
                        jumpsLeft = 1
                        grounded = false
                        airTime = 0f
                        jumpBufferT = 0f
                        burst(bearX + bearSize / 2, groundY, count = 3, spread = 140f * s)
                        onJump()
                    }
                    BearGameLogic.JumpIntent.SECOND -> {
                        bearVY = jumpV2
                        jumpsLeft = 0
                        grounded = false
                        airTime = 0f
                        jumpBufferT = 0f
                        flipT = 0f
                        burst(bearX + bearSize / 2, groundY, count = 3, spread = 140f * s)
                        onJump()
                    }
                    // 空中且没有剩余跳跃：缓存这次点按，落地时立即消费（跳跃缓冲）
                    BearGameLogic.JumpIntent.NONE -> jumpBufferT = BearGameLogic.JUMP_BUFFER_SEC
                }
            GamePhase.OVER -> Unit
        }
    }

    fun die(onDeath: (score: Int) -> Unit) {
        phase = GamePhase.OVER
        deadT = 0f
        shakeT = 0.35f
        flipT = -1f
        burst(bearX + bearSize / 2, bearY + bearSize / 2, count = 16, spread = 540f * s)
        onDeath(score())
    }

    private fun onLand() {
        squashT = 0f
        flipT = -1f
        burst(
            bearX + bearSize / 2,
            groundY,
            count = 6,
            spread = 170f * s,
            color = Color(0xFFD7CCC8),
        )
    }

    fun burst(
        cx: Float,
        cy: Float,
        count: Int,
        spread: Float,
        color: Color = Color.White,
    ) {
        repeat(count) {
            val a = Random.nextFloat() * (Math.PI * 2).toFloat()
            val v = (0.3f + Random.nextFloat() * 0.7f) * spread
            val life = 0.5f + Random.nextFloat() * 0.4f
            particles +=
                Particle(
                    x = cx,
                    y = cy,
                    vx = kotlin.math.cos(a) * v,
                    vy = kotlin.math.sin(a) * v - 120f * s,
                    life = life,
                    maxLife = life,
                    color = color,
                )
        }
    }

    /**
     * 场景 → 障碍概率映射（纯函数，可单测）。r ∈ [0,1)。
     * 雷暴/冰雹天雷云占主导，普通雨天水坑占主导，雪天雪人占主导，晴天较均衡。
     * 雪/冻雨才出现冰墙（与冰主题一致）。
     */
    fun obstacleTypeFor(
        scene: RunScene,
        r: Float,
    ): ObstacleType =
        when (scene) {
            RunScene.SNOW ->
                when {
                    r < 0.14f -> ObstacleType.PUDDLE
                    r < 0.52f -> ObstacleType.SNOWMAN
                    r < 0.76f -> ObstacleType.ICE_WALL
                    else -> ObstacleType.STORM_CLOUD
                }
            RunScene.FREEZING_RAIN ->
                when {
                    r < 0.42f -> ObstacleType.PUDDLE
                    r < 0.60f -> ObstacleType.SNOWMAN
                    r < 0.78f -> ObstacleType.ICE_WALL
                    else -> ObstacleType.STORM_CLOUD
                }
            // 雷暴/冰雹：雷暴云占主导（50%+），而不是和晴天里一样只是偶尔出现
            RunScene.STORM, RunScene.HAIL ->
                when {
                    r < 0.30f -> ObstacleType.PUDDLE
                    r < 0.45f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
            // 普通雨：水坑变多，雪人减少
            RunScene.RAIN, RunScene.RAIN_LIGHT, RunScene.RAIN_HEAVY ->
                when {
                    r < 0.62f -> ObstacleType.PUDDLE
                    r < 0.80f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
            RunScene.SUNNY, RunScene.CLOUDY ->
                when {
                    r < 0.40f -> ObstacleType.PUDDLE
                    r < 0.72f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
        }

    /**
     * 冰墙高度：单跳（~184·s）够不到、二段跳（~322·s）稳过；
     * 雪天比冻雨略矮，手感更宽松。
     */
    private fun iceWallHeight(scene: RunScene): Float = BearGameLogic.iceWallHeight(scene, s)

    private fun iceWallMinScore(scene: RunScene): Int = BearGameLogic.iceWallMinScore(scene)

    /** 出生障碍类型：冰墙在低分段降级为雪人。 */
    fun spawnType(
        scene: RunScene,
        r: Float,
        score: Int,
    ): ObstacleType {
        val type = obstacleTypeFor(scene, r)
        return if (type == ObstacleType.ICE_WALL && score < iceWallMinScore(scene)) {
            ObstacleType.SNOWMAN
        } else {
            type
        }
    }

    private fun spawn() {
        val r = Random.nextFloat()
        val type = spawnType(scene, r, score())
        val (w, h) =
            when (type) {
                ObstacleType.PUDDLE -> 78f * s to 20f * s
                ObstacleType.SNOWMAN -> 56f * s to 84f * s
                ObstacleType.STORM_CLOUD -> 68f * s to 50f * s
                ObstacleType.ICE_WALL -> 46f * s to iceWallHeight(scene)
            }
        val x = size.width + 48f * s
        obstacles += Obstacle(type, x, w, h)
        // 障碍上方一道金币弧线，鼓励踩着节奏跳；冰墙不生成（会落进墙体变陷阱）
        val arcYs =
            BearGameLogic.coinArcYs(
                type = type,
                roll = Random.nextFloat(),
                lift = Random.nextFloat(),
                groundY = groundY,
                bearSize = bearSize,
                s = s,
            )
        for ((idx, cy) in arcYs.withIndex()) {
            coins += Coin(x + w / 2 + (idx - 2) * 34f * s, cy, 11f * s)
        }
    }

    fun tick(
        dt: Float,
        onCoin: () -> Unit,
        onDeath: (score: Int) -> Unit,
        onJump: () -> Unit = {},
    ) {
        if (size == IntSize.Zero) return
        time += dt
        if (shakeT > 0f) shakeT -= dt
        if (squashT >= 0f) squashT += dt
        if (phase != GamePhase.OVER) {
            blinkT -= dt
            if (blinkT < -0.12f) blinkT = 2.2f + Random.nextFloat() * 2.2f
        }

        // 粒子任何阶段都继续飞
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.vy += 900f * s * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
        // 飘分
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= 70f * s * dt
            if (t.life <= 0f) ti.remove()
        }

        if (phase == GamePhase.READY) bob = sin(time * 2.5f) * 1.5f * s
        if (phase == GamePhase.OVER) deadT += dt
        if (phase != GamePhase.RUNNING) return

        speed = min(maxSpeed, speedAt(distance / (60f * s)))
        val dx = speed * dt
        distance += dx
        if (flipT >= 0f) flipT += dt
        if (jumpBufferT > 0f) jumpBufferT = (jumpBufferT - dt).coerceAtLeast(0f)

        // 小熊物理（核心推进委托给引擎层，保证帧行为可测）
        val ph = BearGameLogic.stepBearPhysics(bearY, bearVY, jumpsLeft, grounded, floorY, s, dt)
        bearY = ph.bearY
        bearVY = ph.bearVY
        jumpsLeft = ph.jumpsLeft
        grounded = ph.grounded
        if (ph.grounded) {
            airTime = 0f
            if (ph.landed) onLand()
            // 跳跃缓冲：落地前 ~110ms 的点按在此消费，直接补一段跳
            if (jumpBufferT > 0f) {
                jumpBufferT = 0f
                bearVY = jumpV
                jumpsLeft = 1
                grounded = false
                burst(bearX + bearSize / 2, groundY, count = 3, spread = 140f * s)
                onJump()
            }
        }
        if (grounded) {
            // 步频跟速度走：步幅相位驱动四肢摆动与身体起伏
            runPhase += runPhaseRate() * dt
            bob = -abs(sin(runPhase)) * 3f * s
            // 高速跑动脚下扬尘
            if (speed > 520f * s) {
                dustT += dt
                if (dustT > 0.09f) {
                    dustT = 0f
                    val life = 0.3f + Random.nextFloat() * 0.2f
                    particles +=
                        Particle(
                            x = bearX + bearSize * 0.25f,
                            y = groundY - 5f * s,
                            vx = -(70f + Random.nextFloat() * 90f) * s,
                            vy = -(20f + Random.nextFloat() * 60f) * s,
                            life = life,
                            maxLife = life,
                            color = Color(0xFFD7CCC8),
                        )
                }
            }
        } else {
            airTime += dt
            bob = 0f
        }

        // 世界推进
        obstacles.forEach { it.x -= dx }
        obstacles.removeAll { it.x < -it.w - 20f * s }
        val coinsBefore = coins.size
        coins.forEach { it.x -= dx }
        // 漏接金币（飞出左边界）断连击
        coins.removeAll { it.x < -it.r - 20f * s }
        if (coins.size < coinsBefore) combo = 0
        clouds.forEach {
            it.x -= dx * it.layer + 10f * s * dt
            if (it.x < -140f * s * it.scale) {
                it.x = size.width + Random.nextFloat() * 160f * s
            }
        }

        nextGap -= dx
        if (nextGap <= 0f) {
            spawn()
            nextGap = (460f + Random.nextFloat() * 340f) * s
        }

        // 碰撞（判定盒内缩，手感宽松一点）
        val insetX = bearSize * 0.20f
        val insetY = bearSize * 0.14f
        val bearRect =
            Rect(
                bearX + insetX,
                bearY + insetY,
                bearX + bearSize - insetX,
                bearY + bearSize - insetY * 0.5f,
            )
        val overlapping =
            coins.filter { c ->
                val dxC = c.x - (bearX + bearSize / 2)
                val dyC = c.y - (bearY + bearSize / 2)
                val reach = c.r + bearSize * 0.52f
                dxC * dxC + dyC * dyC < reach * reach
            }
        if (overlapping.isNotEmpty()) {
            for (coinHit in overlapping) {
                coins.remove(coinHit)
                coinCount++
                combo++
                texts += FloatText(coinHit.x, coinHit.y - 24f * s, "+10")
                burst(coinHit.x, coinHit.y, count = 4, spread = 150f * s, color = Color(0xFFFFCA28))
                onCoin()
            }
        }
        val dead =
            obstacles.any { ob ->
                val obTop =
                    when (ob.type) {
                        ObstacleType.PUDDLE -> groundY - ob.h
                        ObstacleType.SNOWMAN -> groundY - ob.h
                        ObstacleType.STORM_CLOUD -> groundY - ob.h - 4f * s
                        ObstacleType.ICE_WALL -> groundY - ob.h
                    }
                val shrinkX = ob.w * 0.16f
                val shrinkTop = ob.h * 0.14f
                bearRect.overlaps(
                    Rect(ob.x + shrinkX, obTop + shrinkTop, ob.x + ob.w - shrinkX, obTop + ob.h),
                )
            }
        if (dead) die(onDeath)
        val v = score()
        if (hudScore != v) hudScore = v
        // 每 500 分里程碑飘字
        if (v >= nextMilestone) {
            texts +=
                FloatText(
                    bearX + bearSize / 2,
                    groundY - bearSize * 2.2f,
                    "$nextMilestone!",
                )
            nextMilestone += 500
        }
    }

    /** 把足以续玩的一局编码落盘；仅在对局进行中写入，READY/OVER 不写。 */
    fun saveSnapshot(prefs: SharedPreferences) {
        // 已放弃/改窗口重开：兜底落盘也不得把陈旧档写回复活
        if (snapshotDiscarded) {
            GameSnapshot.clear(prefs, JUMP_SNAPSHOT_KEY)
            return
        }
        if (phase != GamePhase.RUNNING) return
        val meta =
            SnapshotCodec.encode(
                phase.ordinal,
                time,
                distance,
                speed,
                coinCount,
                bearY,
                bearVY,
                jumpsLeft,
                if (grounded) 1 else 0,
                runPhase,
                bob,
                deadT,
                blinkT,
                squashT,
                flipT,
                shakeT,
                dustT,
                nextGap,
                best,
                scene.ordinal,
                size.width,
                size.height,
            )
        val obstacleGroups = obstacles.map { listOf<Number>(it.type.ordinal, it.x, it.w, it.h) }
        val coinGroups = coins.map { listOf<Number>(it.x, it.y, it.r) }
        val cloudGroups = clouds.map { listOf<Number>(it.x, it.y, it.scale, it.layer) }
        val payload =
            listOf(
                meta,
                SnapshotCodec.encodeGroups(obstacleGroups),
                SnapshotCodec.encodeGroups(coinGroups),
                SnapshotCodec.encodeGroups(cloudGroups),
            ).joinToString("\n")
        GameSnapshot.save(prefs, JUMP_SNAPSHOT_KEY, payload)
    }

    /** 读取并恢复一局；无档/坏档返回 false，保持 READY。恢复成功后保留快照供再次落盘。 */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        val restored = restoreSnapshotOrNull(prefs)
        // 校验失败一律清档：坏档留在 prefs 里会被每次进入重复解析、反复失败，
        // 还会让「继续」入口的可用性判断与实际不符
        if (!restored) clearSnapshot(prefs)
        return restored
    }

    @Suppress("ReturnCount")
    private fun restoreSnapshotOrNull(prefs: SharedPreferences): Boolean {
        val raw = GameSnapshot.load(prefs, JUMP_SNAPSHOT_KEY) ?: return false
        val sections = raw.split('\n')
        val meta = SnapshotCodec.decodeFloatArray(sections.getOrNull(0)) ?: return false
        if (meta.size < JUMP_SNAPSHOT_META_FIELDS) return false
        // 只接受 RUNNING 档：旧档/标题档恢复后会造成「标题态却续着残缺对局」的错乱
        if (GamePhase.entries.getOrNull(meta[0].toInt()) != GamePhase.RUNNING) return false
        val obstacleGroups = decodeSnapshotSection(sections.getOrNull(1)) ?: return false
        val coinGroups = decodeSnapshotSection(sections.getOrNull(2)) ?: return false
        val cloudGroups = decodeSnapshotSection(sections.getOrNull(3)) ?: return false
        // 先整体校验再落状态，避免坏档半途写入把对局留在 RUNNING 的残缺状态。
        val newObstacles =
            obstacleGroups.map { g ->
                if (g.size < 4) return false
                val type = ObstacleType.values().getOrNull(g[0].toInt()) ?: return false
                Obstacle(type, g[1], g[2], g[3])
            }
        val newCoins =
            coinGroups.map { g ->
                if (g.size < 3) return false
                Coin(g[0], g[1], g[2])
            }
        val newClouds =
            cloudGroups.map { g ->
                if (g.size < 4) return false
                BgCloud(g[0], g[1], g[2], g[3])
            }

        phase = GamePhase.RUNNING
        time = meta[1]
        distance = meta[2]
        speed = meta[3]
        coinCount = meta[4].toInt()
        bearY = meta[5]
        bearVY = meta[6]
        jumpsLeft = meta[7].toInt()
        grounded = meta[8] >= 0.5f
        runPhase = meta[9]
        bob = meta[10]
        deadT = meta[11]
        blinkT = meta[12]
        squashT = meta[13]
        flipT = meta[14]
        shakeT = meta[15]
        dustT = meta[16]
        nextGap = meta[17]
        // 与现有最高分合并取大：旧快照不得把当前已提升的最高分改小
        best = max(best, meta[18].toInt())
        scene = RunScene.values().getOrElse(meta[19].toInt()) { RunScene.SUNNY }
        val savedW = meta[20].toInt()
        val savedH = meta[21].toInt()
        // 恢复可能早于首次 onSizeChanged：用存档尺寸兜底，否则 score() 以零尺寸计算
        // 会把 hudScore 归零，随后连续重播 500/1000… 里程碑飘字
        if (size == IntSize.Zero && savedW > 0 && savedH > 0) {
            size = IntSize(savedW, savedH)
        }
        restoredSize = IntSize(savedW, savedH)
        hudScore = score()
        nextMilestone = (hudScore / 500 + 1) * 500
        airTime = 0f
        jumpBufferT = 0f
        combo = 0
        snapshotDiscarded = false
        obstacles.clear()
        obstacles += newObstacles
        coins.clear()
        coins += newCoins
        clouds.clear()
        clouds += newClouds
        return true
    }

    /** 清除落盘快照并抑制后续兜底落盘（结束/重开/退出时调用）。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        snapshotDiscarded = true
        GameSnapshot.clear(prefs, JUMP_SNAPSHOT_KEY)
    }
}

/** 解析快照的一个列表段；空白段视为空列表，非空但解析失败返回 null（坏档）。 */
private fun decodeSnapshotSection(text: String?): List<FloatArray>? =
    if (text.isNullOrBlank()) emptyList() else SnapshotCodec.decodeGroups(text)
