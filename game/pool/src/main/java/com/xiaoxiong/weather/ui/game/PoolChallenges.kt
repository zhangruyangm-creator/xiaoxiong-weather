package com.xiaoxiong.weather.ui.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** 关卡/每日挑战里一颗球的初始摆位（含白球）。 */
data class PoolBallSetup(
    val id: Int,
    val kind: PoolBallKind,
    val x: Float,
    val y: Float,
)

/**
 * 一关（或每日挑战）的完整定义。
 *
 * [balls] 已包含白球（[PoolBallKind.CUE]，id 0）；[par] 是设计者给出的目标杆数，
 * 用于结算计分与关卡网格展示。
 */
data class PoolChallenge(
    val id: Int,
    val nameZh: String,
    val nameEn: String,
    val par: Int,
    val balls: List<PoolBallSetup>,
) {
    /** 白球摆位（约定每关恰有一颗）。 */
    val cue: PoolBallSetup?
        get() = balls.firstOrNull { it.kind == PoolBallKind.CUE }

    /** 需要清掉的目标球数量（不含白球）。 */
    val targetCount: Int
        get() = balls.count { it.kind != PoolBallKind.CUE }
}

/**
 * 关卡与每日挑战的静态数据/生成器（纯 Kotlin，无 Compose / R 依赖，可裸 JVM 单测）。
 *
 * - [campaigns]：12 个手工设计的关卡，由易到难（目标球 3 → 9 颗）；
 * - [daily]：按日期确定性生成的 6–8 颗球布局，同一天多次进入完全一致；
 * - [scoreFor]：统一的清台计分公式 `max(10, par*100 - shots*8)`，杆数越少分越高。
 */
object PoolChallenges {
    /** 每日挑战在 [PoolChallenge.id] 中的占位（真实关卡为 1..12）。 */
    const val DAILY_ID = 0

    private val table = PoolTable.standard()
    private val radius = PoolConfig.BALL_RADIUS

    /** 目标球编号池：混合全色 / 黑八 / 花色，保证一手信息量。 */
    private val ID_POOL = listOf(1, 9, 2, 8, 10, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15)

    /** 每日挑战候选网格（间距 ≥ 0.2m，天然满足 2.2r 球距且远离袋口）。 */
    private val GRID_X = listOf(0.22f, 0.42f, 0.63f, 0.84f, 1.05f)
    private val GRID_Y = listOf(0.30f, 0.55f, 0.80f, 1.05f, 1.30f, 1.55f, 1.80f, 2.05f, 2.30f)

    /** 统一的清台计分：par 越高、杆数越少得分越高，保底 10 分。 */
    fun scoreFor(
        par: Int,
        shots: Int,
    ): Int = max(10, par * 100 - shots * 8)

    /** 12 关（由易到难）。返回的列表不可变。 */
    fun campaigns(): List<PoolChallenge> = CAMPAIGNS

    /**
     * 每日挑战：以 [dateEpochDay] 为随机种子，确定性生成 6–8 颗目标球的布局；
     * 同一天多次调用结果完全一致，不同日期几乎必然不同。
     */
    fun daily(dateEpochDay: Long): PoolChallenge {
        val rng = Random(dateEpochDay)
        val count = 6 + rng.nextInt(3)

        val all = ArrayList<Pair<Float, Float>>(GRID_X.size * GRID_Y.size)
        for (x in GRID_X) {
            for (y in GRID_Y) all += x to y
        }
        all.shuffle(rng)

        // 白球放在开球区（低 Y 带），其余从剩余网格里按顺序取
        val cue =
            all.firstOrNull { it.second <= 0.56f }
                ?: (table.centerX to table.headStringY)
        val chosen = all.filter { it != cue }.take(count)

        val objs = objectSet(count)
        val balls = ArrayList<PoolBallSetup>(count + 1)
        for (i in chosen.indices) {
            val (x, y) = chosen[i]
            val (id, kind) = objs[i]
            balls += PoolBallSetup(id, kind, x, y)
        }
        balls += PoolBallSetup(0, PoolBallKind.CUE, cue.first, cue.second)
        val par = max(2, (count * 0.7f).toInt() + 1)
        return PoolChallenge(
            id = DAILY_ID,
            nameZh = "每日挑战",
            nameEn = "Daily Challenge",
            par = par,
            balls = balls,
        )
    }

    /** 布局合法性：恰一颗白球、编号唯一、在台内、不压袋口、球间距 ≥ 2.2r。 */
    fun isLayoutValid(challenge: PoolChallenge): Boolean {
        val balls = challenge.balls
        val cues = balls.count { it.kind == PoolBallKind.CUE }
        if (cues != 1) return false
        if (balls.map { it.id }.toSet().size != balls.size) return false
        val minGap = radius * 2.2f
        for (b in balls) {
            // margin = -radius 表示要求球心距库边至少一个半径（整颗球在台内）
            if (!table.contains(b.x, b.y, margin = -radius)) return false
            for (p in table.pockets) {
                if (hypot(b.x - p.x, b.y - p.y) <= p.radius + radius * 0.5f) return false
            }
        }
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val a = balls[i]
                val c = balls[j]
                if (hypot(a.x - c.x, a.y - c.y) < minGap) return false
            }
        }
        return true
    }

    private fun objectSet(count: Int): List<Pair<Int, PoolBallKind>> =
        (0 until count).map { i ->
            val id = ID_POOL[i % ID_POOL.size]
            id to kindOf(id)
        }

    private fun kindOf(id: Int): PoolBallKind =
        when {
            id == 8 -> PoolBallKind.EIGHT
            id <= 7 -> PoolBallKind.SOLID
            else -> PoolBallKind.STRIPE
        }

    private fun campaign(
        id: Int,
        nameZh: String,
        nameEn: String,
        par: Int,
        cue: Pair<Float, Float>,
        targets: List<Pair<Float, Float>>,
    ): PoolChallenge {
        val objs = objectSet(targets.size)
        val balls = ArrayList<PoolBallSetup>(targets.size + 1)
        targets.forEachIndexed { i, (x, y) ->
            val (ballId, kind) = objs[i]
            balls += PoolBallSetup(ballId, kind, x, y)
        }
        balls += PoolBallSetup(0, PoolBallKind.CUE, cue.first, cue.second)
        return PoolChallenge(id, nameZh, nameEn, par, balls)
    }

    private val CAMPAIGNS: List<PoolChallenge> =
        listOf(
            campaign(
                1,
                "热身",
                "Warm-up",
                3,
                cue = 0.635f to 0.55f,
                targets = listOf(0.40f to 1.40f, 0.635f to 1.70f, 0.85f to 1.40f),
            ),
            campaign(
                2,
                "直线",
                "Straight Lines",
                3,
                cue = 0.635f to 0.50f,
                targets = listOf(0.30f to 1.20f, 0.635f to 1.50f, 0.95f to 1.20f),
            ),
            campaign(
                3,
                "三角",
                "Triangle",
                3,
                cue = 0.635f to 0.50f,
                targets = listOf(0.30f to 1.20f, 0.635f to 1.50f, 0.95f to 1.20f, 0.635f to 2.00f),
            ),
            campaign(
                4,
                "黑八点",
                "Eight Spot",
                4,
                cue = 0.635f to 0.45f,
                targets = listOf(0.35f to 1.10f, 0.70f to 1.60f, 1.00f to 1.30f, 0.50f to 2.10f),
            ),
            campaign(
                5,
                "双排",
                "Twin Rows",
                4,
                cue = 0.635f to 0.45f,
                targets =
                    listOf(
                        0.30f to 1.00f,
                        0.635f to 1.30f,
                        0.95f to 1.00f,
                        0.40f to 1.80f,
                        0.85f to 1.80f,
                    ),
            ),
            campaign(
                6,
                "散花",
                "Scatter",
                4,
                cue = 0.635f to 0.45f,
                targets =
                    listOf(
                        0.30f to 1.20f,
                        0.635f to 0.95f,
                        1.00f to 1.20f,
                        0.45f to 1.75f,
                        0.85f to 2.10f,
                    ),
            ),
            campaign(
                7,
                "阶梯",
                "Ladder",
                5,
                cue = 0.635f to 0.40f,
                targets =
                    listOf(
                        0.30f to 0.85f,
                        0.635f to 1.10f,
                        0.95f to 0.85f,
                        0.35f to 1.55f,
                        0.70f to 1.85f,
                        1.00f to 2.15f,
                    ),
            ),
            campaign(
                8,
                "回字",
                "Loop",
                5,
                cue = 0.635f to 0.40f,
                targets =
                    listOf(
                        0.25f to 1.10f,
                        0.55f to 0.80f,
                        0.95f to 1.10f,
                        0.635f to 1.55f,
                        0.35f to 2.00f,
                        0.95f to 2.00f,
                    ),
            ),
            campaign(
                9,
                "七分",
                "Seven Pack",
                6,
                cue = 0.635f to 0.35f,
                targets =
                    listOf(
                        0.25f to 0.80f,
                        0.55f to 0.55f,
                        0.90f to 0.80f,
                        1.05f to 1.20f,
                        0.65f to 1.40f,
                        0.30f to 1.70f,
                        0.85f to 2.05f,
                    ),
            ),
            campaign(
                10,
                "交错",
                "Weave",
                6,
                cue = 0.635f to 0.35f,
                targets =
                    listOf(
                        0.30f to 0.70f,
                        0.635f to 0.90f,
                        0.95f to 0.70f,
                        0.25f to 1.30f,
                        0.635f to 1.55f,
                        1.00f to 1.30f,
                        0.50f to 2.10f,
                    ),
            ),
            campaign(
                11,
                "风车",
                "Pinwheel",
                7,
                cue = 0.635f to 0.35f,
                targets =
                    listOf(
                        0.25f to 0.75f,
                        0.55f to 0.50f,
                        0.90f to 0.75f,
                        1.05f to 1.15f,
                        0.75f to 1.30f,
                        0.30f to 1.60f,
                        0.65f to 1.95f,
                        1.00f to 2.20f,
                    ),
            ),
            campaign(
                12,
                "满台",
                "Full Rack",
                8,
                cue = 0.635f to 0.30f,
                targets =
                    listOf(
                        0.25f to 0.70f,
                        0.55f to 0.45f,
                        0.90f to 0.70f,
                        1.05f to 1.05f,
                        0.35f to 1.25f,
                        0.70f to 1.45f,
                        0.30f to 1.85f,
                        0.90f to 1.95f,
                        0.60f to 2.25f,
                    ),
            ),
        )
}
