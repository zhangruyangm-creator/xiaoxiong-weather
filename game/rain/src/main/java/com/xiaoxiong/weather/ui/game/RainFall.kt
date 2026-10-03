package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 * 小熊雨幕 · 引擎层（纯 Kotlin，无 Compose / Android 依赖）。
 *
 * ## 为什么这套玩法的时钟要求极低
 * 本作沿用「别踩白块儿」的核心：**判定依据是方块的视觉位置，不是音乐**。
 * 方块自己就是节奏，玩家看着它到线就点。因此：
 *  - 不需要音频时钟、不需要延迟校准设置；
 *  - 音频只是"点到了"的反馈（自演奏音阶），不是判定基准；
 *  - 蓝牙耳机延迟多少都不影响可玩性。
 *
 * 这与"严肃音游"的根本差别在于：后者把音乐当标准答案，音符必须踩在拍上，
 * 一旦视觉时钟与音频时钟错位就全面失准。
 *
 * ## 三个关键设计约束
 * 1. **每块方块以固定速度下落**（出生时锁定速度）。若改用"当前速度"推进，
 *    方块会在半空突然加速，轨迹非线性，玩家无法预判到达时间——可预测性是这类玩法的基础。
 * 2. **只保留在飞的方块**。出怪只增不减会让列表无限增长，导致每帧 O(n) 遍历；
 *    这里在每帧末把"已消费 / 已过线"的方块剪掉，列表长度恒等于视野内方块数。
 * 3. **命中必须发生在判定线附近**。若不限制点击距离，玩家可以隔着半屏把刚出场的
 *    方块点掉，难度与手感都会崩。
 *
 * ## 难度只有两个轴
 * 下落速度 [difficultyAt] 与出怪间隔，外加不可点方块占比。没有关卡、没有难度分档、
 * 没有人工调表——参数就是设计。
 */

/** 一块下落物的类型。区分度是这个玩法的命脉，故在引擎层显式建模。 */
enum class RainKind {
    /** 雨滴：必须点掉。漏接即结束。 */
    DROP,

    /** 冰雹：必须**不**点。误点即结束。视觉上必须与 [DROP] 强烈区分。 */
    HAIL,
}

/** 对局阶段。 */
enum class RainPhase {
    READY,
    PLAYING,
    OVER,
}

/** 结束原因，供结算文案区分「漏接」与「误点冰雹」。 */
enum class RainOverReason {
    /** 漏接雨滴。 */
    MISSED_DROP,

    /** 误点冰雹。 */
    TAPPED_HAIL,
}

/** 点击结果。 */
enum class RainTapResult {
    /** 点中雨滴（加分）。 */
    HIT_DROP,

    /** 误点冰雹（结束）。 */
    TAPPED_HAIL,
}

/** 一块在飞的方块。 */
data class RainTile(
    val id: Long,
    val kind: RainKind,
    /** 出场时刻（秒，相对本局开始）。 */
    val spawnTime: Float,
    /** **出生时锁定**的下落速度（屏高/秒），保证轨迹线性可预判。 */
    val speed: Float,
    /** 是否已被点掉或已判定失败。 */
    val consumed: Boolean = false,
)

/** 难度在某时刻的取值。 */
data class RainDifficulty(
    /** 下落速度（屏高/秒）。 */
    val fallSpeed: Float,
    /** 出怪间隔（秒）。 */
    val spawnInterval: Float,
    /** 冰雹出现概率（0..1）。 */
    val hailChance: Float,
)

/**
 * 引擎层：负责生成、推进与判定，**不持有时间也不读时钟**。
 *
 * 外部（帧循环）按 dt 累加出 [elapsed] 后调用 [tick]；所有难度参数都是纯函数
 * [difficultyAt]，可独立单测。
 */
internal class RainFall {
    /**
     * 对局阶段。
     *
     * 刻意用 Compose 的快照状态而非普通字段：阶段变化必须让界面重组。
     * 之前版本用普通 `var` + 一个由各条路径手动置位的 `showOver` 标志，
     * 结果**漏接（发生在 tick 里）不会弹出结算界面**——因为漏接路径根本不碰那个标志。
     * 改为可观察后，界面直接由本字段派生，不再有"忘记设标志"的可能。
     * 这不影响可测性：快照状态在纯 JVM 单测里读写完全正常。
     */
    var phase by mutableStateOf(RainPhase.READY)
        private set

    /** 本局已进行的秒数（由外部按 dt 累加传入）。 */
    var elapsed: Float = 0f
        private set

    /** 已点掉的雨滴数（= 分数）。 */
    var score: Int = 0
        private set

    /** 当前连续命中数。 */
    var combo: Int = 0
        private set

    /** 历史最高连续命中。 */
    var maxCombo: Int = 0
        private set

    /** 结束原因。 */
    var overReason: RainOverReason? = null
        private set

    /** 上一块被点掉时的类型（表现层做音高与粒子用）。 */
    var lastHitKind: RainKind? = null
        private set

    /** 本局随机种子（同种子必得同一串方块，便于复现与「再来一次」）。 */
    var seed: Long = 0L
        private set

    /** 只保留「未消费且未过线」的方块，长度恒等于视野内方块数。 */
    private val flying = ArrayList<RainTile>()
    private var nextId = 0L
    private var nextSpawnAt = 0f
    private var rng = Random(0)

    fun isPlaying(): Boolean = phase == RainPhase.PLAYING

    fun isOver(): Boolean = phase == RainPhase.OVER

    /** 准备一局。 */
    fun prepare(seed: Long) {
        this.seed = seed
        resetProgress()
        this.rng = Random(seed)
        nextSpawnAt = FIRST_SPAWN_DELAY
    }

    private fun resetProgress() {
        phase = RainPhase.READY
        elapsed = 0f
        score = 0
        combo = 0
        maxCombo = 0
        overReason = null
        lastHitKind = null
        flying.clear()
        nextId = 0L
    }

    /** 开始（READY → PLAYING）。 */
    fun start() {
        if (phase != RainPhase.READY) return
        resetProgress()
        this.rng = Random(seed)
        nextSpawnAt = FIRST_SPAWN_DELAY
        phase = RainPhase.PLAYING
    }

    /** 重开同一局（同种子 → 同一串方块）。 */
    fun restart() {
        prepare(seed)
        start()
    }

    /**
     * 推进 [dt] 秒：出怪 → 判定漏接 → 剪枝。
     *
     * 出怪用 `while` 追帧，保证低帧率下不漏方块（宁可补出也不丢节奏）。
     */
    fun tick(dt: Float) {
        if (phase != RainPhase.PLAYING) return
        elapsed += dt

        var guard = 0
        while (elapsed >= nextSpawnAt && guard++ < MAX_SPAWNS_PER_TICK) {
            spawnOne()
            nextSpawnAt += spawnIntervalAt(elapsed)
        }

        // 漏接判定：雨滴越过判定线即结束。用位置比较而非下标推进，掉帧也不会漏判。
        val travel = travelDistance()
        var i = 0
        while (i < flying.size) {
            val tile = flying[i]
            if (tilePositionAt(tile, elapsed) > travel && tile.kind == RainKind.DROP) {
                finish(RainOverReason.MISSED_DROP)
                prune()
                return
            }
            i++
        }
        prune()
    }

    private fun spawnOne() {
        val kind = pickKind(elapsed, rng)
        flying +=
            RainTile(
                id = nextId++,
                kind = kind,
                spawnTime = elapsed,
                speed = fallSpeedAt(elapsed),
            )
    }

    /**
     * 点击判定：在判定线附近找**距判定线最近**的那块方块。
     *
     * @param xNorm 归一化横向坐标（0..1）；单列玩法只用其做边界钳制。
     * @param lanes 列数（1 = 单列，经典形态）。
     * @return 命中结果；点到空处返回 null（空敲不罚）。
     */
    fun tap(
        xNorm: Float,
        lanes: Int = 1,
    ): RainTapResult? {
        if (phase != RainPhase.PLAYING) return null
        val travel = travelDistance()
        val lane = laneOf(xNorm, lanes)
        var bestIndex = -1
        var bestDist = Float.MAX_VALUE
        for (i in flying.indices) {
            val tile = flying[i]
            if (tile.consumed) continue
            if (lanes > 1 && laneOf(tile.normX, lanes) != lane) continue
            // 只有进入命中窗的方块才能被点：既防"隔半屏点掉刚出场的方块"，
            // 也让"看到压线再点"成为最优解。判定复用 isHittable，保证与渲染一致。
            if (!isHittable(tile, elapsed)) continue
            val dist = abs(tilePositionAt(tile, elapsed) - travel)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        if (bestIndex < 0) return null
        val tile = flying[bestIndex]
        flying[bestIndex] = tile.copy(consumed = true)
        lastHitKind = tile.kind
        return if (tile.kind == RainKind.DROP) {
            score++
            combo++
            if (combo > maxCombo) maxCombo = combo
            RainTapResult.HIT_DROP
        } else {
            finish(RainOverReason.TAPPED_HAIL)
            prune()
            RainTapResult.TAPPED_HAIL
        }
    }

    /** 剪掉「已消费」与「已过线」的方块，把列表长度压回视野内数量。 */
    private fun prune() {
        val travel = travelDistance()
        val it = flying.iterator()
        while (it.hasNext()) {
            val tile = it.next()
            if (tile.consumed || tilePositionAt(tile, elapsed) > travel + OVERSCAN_MARGIN) {
                it.remove()
            }
        }
    }

    private fun finish(reason: RainOverReason) {
        phase = RainPhase.OVER
        overReason = reason
    }

    /** 某块方块当前纵坐标（判定线 = [travelDistance]）。用出生时锁定的速度，故线性。 */
    fun tilePositionAt(
        tile: RainTile,
        at: Float = elapsed,
    ): Float = (at - tile.spawnTime) * tile.speed

    /** 某块方块到判定线还需多久（秒）。已过线返回 0。 */
    fun secondsToLine(
        tile: RainTile,
        at: Float = elapsed,
    ): Float {
        if (tile.speed <= 0f) return Float.MAX_VALUE
        return max(0f, (travelDistance() - tilePositionAt(tile, at)) / tile.speed)
    }

    /** 某块方块的进度：0 = 刚出场，1 = 恰好到判定线。 */
    fun tileProgress(
        tile: RainTile,
        at: Float = elapsed,
    ): Float {
        val travel = travelDistance()
        if (travel <= 0f) return 0f
        return tilePositionAt(tile, at) / travel
    }

    /**
     * 该方块当前是否处于可点状态（处于命中窗内）。
     *
     * 表现层用它做"可点高亮"，测试用它模拟玩家点击——**必须与 [tap] 用同一判定**，
     * 所以提取为函数而非两处各写一遍，避免"看到的能点、实际点了不中"或反之。
     */
    fun isHittable(
        tile: RainTile,
        at: Float = elapsed,
    ): Boolean {
        if (tile.consumed) return false
        val travel = travelDistance()
        val distToLine = travel - tilePositionAt(tile, at)
        return distToLine in 0f..(travel * HIT_WINDOW_FRACTION)
    }

    /** 视野内方块（表现层遍历用；因已剪枝，长度天然很小）。 */
    fun activeTiles(): List<RainTile> = flying.filter { !it.consumed && secondsToLine(it) <= ACTIVE_HORIZON_SECONDS }

    /** 当前在飞方块总数（测试用：验证列表不会无限增长）。 */
    fun flyingCount(): Int = flying.size

    /** 距离下一块方块出场还有多久（HUD 预告用）。 */
    fun timeToNextSpawn(): Float = max(0f, nextSpawnAt - elapsed)

    /** 归一化横向位置：单列恒为 0.5。 */
    private val RainTile.normX: Float
        get() = 0.5f

    companion object {
        /** 判定线在屏幕高度的比例（表现层与引擎共用，保证"到线"定义一致）。 */
        const val HIT_LINE_RATIO = 0.82f

        /** 从出场到判定线的归一化距离（以屏高为 1）。 */
        fun travelDistance(): Float = HIT_LINE_RATIO

        /** 开场到第一块的延迟（秒），给玩家一个"准备"瞬间。 */
        const val FIRST_SPAWN_DELAY = 0.9f

        /** 单帧最多补出多少块（防止极端卡顿后一次性爆一堆）。 */
        const val MAX_SPAWNS_PER_TICK = 8

        /** 表现层只关心这么多秒内会到线的方块。 */
        const val ACTIVE_HORIZON_SECONDS = 2.2f

        /**
         * 命中窗：方块距判定线不超过「判定线距离的这个比例」才可被点。
         *
         * 用**位置比例**而非秒数，有两个好处：
         *  - 自适应速度：高速时时间窗自动收窄、慢速时自动放宽，"到线才算命中"的
         *    手感在全程一致；
         *  - **保证同一时刻窗内最多只有一块方块**——见 [RAIN_MIN_TILE_SPACING]，
         *    这避免了「想点雨滴却误中冰雹」的公平性问题。
         */
        const val HIT_WINDOW_FRACTION = 0.10f

        /**
         * 相邻方块的最小间距（以判定线距离为 1）。
         *
         * 必须 > 2 × [HIT_WINDOW_FRACTION]，否则两块可能同时处于命中窗内，
         * 玩家无法判断该点哪一块——这是单列玩法在高速下的固有陷阱。
         * 难度曲线的最小出怪间隔已按此约束设计（见 [RAIN_SPAWN_GAP_END]）。
         */
        const val RAIN_MIN_TILE_SPACING = 0.22f

        /** 过线后额外保留一小段再剪枝，避免"刚过线就消失"造成视觉断层。 */
        private const val OVERSCAN_MARGIN = 0.06f
    }
}

/** 计算归一化横坐标落在哪一列。 */
private fun laneOf(
    xNorm: Float,
    lanes: Int,
): Int {
    if (lanes <= 1) return 0
    return min(lanes - 1, max(0, (xNorm * lanes).toInt()))
}

// ---------------------------------------------------------------- 难度曲线

/** 开局宽限期内强制不出冰雹（秒）：让玩家先建立节奏，再引入第二种方块。 */
const val RAIN_GRACE_SECONDS = 6f

/** 速度从初值到终值所需时间（秒）。 */
const val RAIN_SPEED_RAMP_SECONDS = 150f

/** 出怪间隔从初值到终值所需时间（秒）。 */
const val RAIN_DENSITY_RAMP_SECONDS = 150f

/** 冰雹概率爬到峰值所需时间（秒）。 */
const val RAIN_HAIL_RAMP_SECONDS = 150f

/** 出怪间隔初值（秒）——开局舒缓（约 1.2 次/秒）。 */
const val RAIN_SPAWN_GAP_START = 0.85f

/**
 * 出怪间隔终值（秒）——后期密集（约 5.9 次/秒，接近人类连点上限）。
 *
 * 下限受 [RAIN_FALL.HIT_WINDOW_FRACTION] / [RAIN_FALL.RAIN_MIN_TILE_SPACING] 约束：
 * 再密就会出现"窗内两块、无法判断该点哪块"的公平性问题。
 */
const val RAIN_SPAWN_GAP_END = 0.17f

/** 下落速度初值（屏高/秒）。 */
const val RAIN_FALL_SPEED_START = 0.42f

/**
 * 下落速度终值（屏高/秒）——单块约 0.73s 落到线。
 *
 * 注意：难度主要由**出怪间隔**决定，而非速度。速度只决定"留给反应的时间"，
 * 因此这里刻意保持中等——速度拉满会让同屏方块数变少，画面反而不像"更密"。
 */
const val RAIN_FALL_SPEED_END = 1.15f

/** 冰雹概率初值（宽限期结束后才开始爬升）。 */
const val RAIN_HAIL_START = 0.10f

/** 冰雹概率峰值。 */
const val RAIN_HAIL_MAX = 0.38f

/**
 * 取 [t] 秒处的难度。
 *
 * 三条曲线都用**平滑饱和**而非线性：线性会让前期陡然变难、后期又几乎不变，
 * 体感是"突然难起来"；饱和曲线前期平缓、后期趋缓，最接近"手感自然加速"。
 */
fun difficultyAt(t: Float): RainDifficulty =
    RainDifficulty(
        fallSpeed = lerp(RAIN_FALL_SPEED_START, RAIN_FALL_SPEED_END, saturate(t / RAIN_SPEED_RAMP_SECONDS)),
        spawnInterval = lerp(RAIN_SPAWN_GAP_START, RAIN_SPAWN_GAP_END, saturate(t / RAIN_DENSITY_RAMP_SECONDS)),
        hailChance = lerp(RAIN_HAIL_START, RAIN_HAIL_MAX, saturate((t - RAIN_GRACE_SECONDS) / RAIN_HAIL_RAMP_SECONDS)),
    )

/**
 * 归一化平滑饱和：0→0，随 x 单调趋近 1。
 *
 * 系数取 3.0 使 `x = 1`（即达到标称爬坡时间）时进度已达 0.95——
 * 否则"标称 150 秒"实际只走到 80%，玩家会觉得后期迟迟不变难。
 */
private fun saturate(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 2f) return 1f
    return 1f - kotlin.math.exp(-x * 3.0f)
}

/** 线性插值。 */
private fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

/** 下落速度（屏高/秒）。 */
fun fallSpeedAt(t: Float): Float = difficultyAt(t).fallSpeed

/** 出怪间隔（秒）。 */
fun spawnIntervalAt(t: Float): Float = difficultyAt(t).spawnInterval

/** 冰雹出现概率（0..1）；宽限期内恒为 0。 */
fun hailChanceAt(t: Float): Float =
    if (t < RAIN_GRACE_SECONDS) {
        0f
    } else {
        difficultyAt(t).hailChance
    }

/**
 * 按概率决定这一块是雨滴还是冰雹。
 *
 * 宽限期内（[RAIN_GRACE_SECONDS]）**强制雨滴**——这是"区分度"的玩法侧保障：
 * 玩家先在只有一种方块的情况下建立节奏与肌肉记忆，之后才引入第二种方块。
 */
fun pickKind(
    t: Float,
    rng: Random,
): RainKind = if (t < RAIN_GRACE_SECONDS || rng.nextFloat() >= hailChanceAt(t)) RainKind.DROP else RainKind.HAIL

/** 带固定 seed 的便捷重载（测试与复现用；每次调用结果相同）。 */
fun pickKind(
    t: Float,
    seed: Long,
): RainKind = pickKind(t, Random(seed))
