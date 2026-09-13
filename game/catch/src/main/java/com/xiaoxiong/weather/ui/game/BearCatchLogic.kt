package com.xiaoxiong.weather.ui.game

/**
 * 小熊接雨雪核心逻辑：与 Compose / Android 完全解耦，可被普通 JUnit 单测。
 *
 * 玩法：小熊在底部左右移动，接住天上落下的得分物（雨滴/雪花/硬币/花瓣），
 * 躲开惩罚物（乌云/冰雹/闪电）。掉落物种类由当前天气决定（天气即玩法）；
 * 分数越高生成间隔越短、下落越快（难度曲线）。
 *
 * 屏幕缩放 [s]（= 屏高 / 820）作为显式参数传入，与 [BearGameLogic] 一致：
 * 同一套规则既能在 Compose 渲染层跑，也能脱离渲染环境被确定性测试。
 */
object BearCatchLogic {
    /** 掉落物种类：天气决定每种出现的权重。 */
    enum class ItemKind {
        /** 雨滴：得分 +10，雨天主力。 */
        RAINDROP,

        /** 雪花：得分 +10，雪天主力（慢速、宽松）。 */
        SNOWFLAKE,

        /** 硬币：得分 +25，晴天/多云稀疏高分。 */
        COIN,

        /** 花瓣：得分 +8，晴天点缀（细小、高频，挑战性）。 */
        PETAL,

        /** 礼物盒：得分 +20，接住后进入 8 秒双倍得分。 */
        GIFT,

        /** 护盾泡泡：接住积一层护盾（最多 3 层），抵挡一次伤害。 */
        BUBBLE,

        /** 乌云：碰到扣 1 心，需躲开。 */
        CLOUD,

        /** 冰雹：碰到扣 1 心，快而致命。 */
        HAIL,

        /** 闪电：碰到扣 2 心，雷暴天一次性高威胁。 */
        LIGHTNING,
    }

    /** 场景：由 [farmWeatherFromScene] + 降水强度 + 冻雨/冰雹特殊码映射。 */
    enum class CatchScene {
        SUNNY,
        CLOUDY,
        RAIN_LIGHT,
        RAIN,
        RAIN_HEAVY,
        SNOW,
        STORM,
        FREEZING_RAIN,
        HAIL,
    }

    /**
     * 难度档位：与天气解耦，决定加压曲线速率与危险物占比。
     * 天气只保留「风味」级微调（各场景基础速度/间隔仍不同），
     * 避免晴天永远最简单、且玩家无法选择。
     */
    enum class CatchDifficulty(
        /** 持久化 id（prefs / 快照兼容）。 */
        val id: String,
        /** 加压速率：分数进度按此倍率累积（>1 更快进入高难）。 */
        val pressureRate: Float,
        /** 危险加成：额外危险物占比按此倍率缩放。 */
        val dangerBonus: Float,
        /** 爆发波次周期（秒）。 */
        val burstPeriodSec: Float,
        /** 爆发波次内下落速度倍率。 */
        val burstSpeedMultiplier: Float,
    ) {
        CASUAL("casual", 0.65f, 0.5f, 22f, 1.20f),
        STANDARD("standard", 1.0f, 1.0f, 18f, 1.30f),
        CHALLENGE("challenge", 1.45f, 1.5f, 15f, 1.40f),
        ;

        companion object {
            /** 从持久化 id 还原；空 / 未知值回退 [STANDARD]（兼容旧档）。 */
            fun fromId(id: String?): CatchDifficulty = entries.firstOrNull { it.id == id } ?: STANDARD
        }
    }

    /** 掉落物状态。 */
    class Item(
        val kind: ItemKind,
        var x: Float,
        var y: Float,
        val r: Float,
        val phase: Float,
    ) {
        var vy: Float = 0f
    }

    /** 一帧推进结果：接住/中招/漏接 + 累计生成时钟 + 本帧新生成个数。 */
    data class FrameEvents(
        val caught: List<ItemKind>,
        val hit: List<ItemKind>,
        val missedGood: Int,
        val spawnAccum: Float,
        val spawnedCount: Int = 0,
        /** 本帧是否处于风暴爆发波次（供渲染层做视觉提示）。 */
        val inBurstWave: Boolean = false,
    )

    /** 基础权重切片：宽度即 roll 区间，按场景固定（与旧版分布一致）。 */
    private class KindSlice(
        val kind: ItemKind,
        val width: Float,
    )

    /** 各场景 0 分 / STANDARD 下的基础分布（与旧版 [itemKindFor] 完全一致）。 */
    private fun baseSlices(scene: CatchScene): List<KindSlice> =
        when (scene) {
            CatchScene.SUNNY ->
                listOf(
                    KindSlice(ItemKind.COIN, 0.13f),
                    KindSlice(ItemKind.PETAL, 0.42f),
                    KindSlice(ItemKind.RAINDROP, 0.19f),
                    KindSlice(ItemKind.CLOUD, 0.14f),
                    KindSlice(ItemKind.GIFT, 0.10f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.CLOUDY ->
                listOf(
                    KindSlice(ItemKind.COIN, 0.08f),
                    KindSlice(ItemKind.RAINDROP, 0.66f),
                    KindSlice(ItemKind.CLOUD, 0.14f),
                    KindSlice(ItemKind.GIFT, 0.10f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.RAIN_LIGHT, CatchScene.RAIN ->
                listOf(
                    KindSlice(ItemKind.COIN, 0.10f),
                    KindSlice(ItemKind.RAINDROP, 0.70f),
                    KindSlice(ItemKind.CLOUD, 0.10f),
                    KindSlice(ItemKind.GIFT, 0.08f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.RAIN_HEAVY ->
                listOf(
                    KindSlice(ItemKind.COIN, 0.04f),
                    KindSlice(ItemKind.RAINDROP, 0.72f),
                    KindSlice(ItemKind.CLOUD, 0.12f),
                    KindSlice(ItemKind.GIFT, 0.10f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.SNOW ->
                listOf(
                    KindSlice(ItemKind.COIN, 0.07f),
                    KindSlice(ItemKind.SNOWFLAKE, 0.79f),
                    KindSlice(ItemKind.CLOUD, 0.08f),
                    KindSlice(ItemKind.GIFT, 0.02f),
                    // 雪天也要能拿到护盾，且其余场景一致放在分布末尾（约 4%）
                    KindSlice(ItemKind.BUBBLE, 0.04f),
                )
            CatchScene.STORM ->
                listOf(
                    KindSlice(ItemKind.RAINDROP, 0.66f),
                    KindSlice(ItemKind.CLOUD, 0.19f),
                    KindSlice(ItemKind.LIGHTNING, 0.08f),
                    KindSlice(ItemKind.GIFT, 0.05f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.FREEZING_RAIN ->
                listOf(
                    KindSlice(ItemKind.RAINDROP, 0.62f),
                    KindSlice(ItemKind.CLOUD, 0.19f),
                    KindSlice(ItemKind.HAIL, 0.09f),
                    KindSlice(ItemKind.GIFT, 0.08f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
            CatchScene.HAIL ->
                listOf(
                    KindSlice(ItemKind.RAINDROP, 0.50f),
                    KindSlice(ItemKind.HAIL, 0.26f),
                    KindSlice(ItemKind.CLOUD, 0.12f),
                    KindSlice(ItemKind.GIFT, 0.10f),
                    KindSlice(ItemKind.BUBBLE, 0.02f),
                )
        }

    /**
     * 天气 + 分数 + 难度 → 掉落物种类。
     *
     * 基础分布（0 分 / STANDARD）与旧版逐区间一致；分数越高额外危险占比越大，
     * 600 分后缺少冰雹的场景混入 HAIL、1200 分后混入 LIGHTNING（重分配 roll 区间），
     * 危险占比上限 [MAX_DANGER_SHARE] 保证好物始终占多数；额外占比按档位缩放。
     */
    fun itemKindFor(
        scene: CatchScene,
        r: Float,
        score: Int = 0,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): ItemKind {
        val base = baseSlices(scene)
        var baseDanger = 0f
        for (slice in base) {
            if (!isGood(slice.kind)) baseDanger += slice.width
        }
        val ramp = (score / DANGER_RAMP_SCORE).coerceIn(0f, 1f)
        val extra =
            (DANGER_MAX_ADD * difficulty.dangerBonus * ramp)
                .coerceAtMost((MAX_DANGER_SHARE - baseDanger).coerceAtLeast(0f))
        if (extra <= 0f) return pickFrom(base, r)
        return pickFrom(evolveSlices(base, baseDanger, score, extra), r)
    }

    /**
     * 危险进化：额外危险占比 [extra] 从好物区间按比例扣除；
     * 缺少 HAIL/LIGHTNING 的场景在阈值后混入（分走一部分额外占比），
     * 剩余按原比例加到该场景既有危险物上。
     */
    private fun evolveSlices(
        base: List<KindSlice>,
        baseDanger: Float,
        score: Int,
        extra: Float,
    ): List<KindSlice> {
        val hasHail = base.any { it.kind == ItemKind.HAIL }
        val hasLightning = base.any { it.kind == ItemKind.LIGHTNING }
        val newHail = if (!hasHail && score >= HAIL_UNLOCK_SCORE) extra * NEW_HAIL_SHARE else 0f
        val newLightning = if (!hasLightning && score >= LIGHTNING_UNLOCK_SCORE) extra * NEW_LIGHTNING_SHARE else 0f
        val baseGain = extra - newHail - newLightning
        val goodScale = (1f - baseDanger - extra) / (1f - baseDanger)
        val out = ArrayList<KindSlice>(base.size + 2)
        var dangerDone = false
        for (slice in base) {
            if (isGood(slice.kind)) {
                out += KindSlice(slice.kind, slice.width * goodScale)
            } else if (!dangerDone) {
                dangerDone = true
                for (d in base) {
                    if (!isGood(d.kind)) {
                        out += KindSlice(d.kind, d.width * (1f + baseGain / baseDanger))
                    }
                }
                if (newHail > 0f) out += KindSlice(ItemKind.HAIL, newHail)
                if (newLightning > 0f) out += KindSlice(ItemKind.LIGHTNING, newLightning)
            }
        }
        return out
    }

    /** 按累计宽度命中切片；浮点误差导致 r 落到末尾时回退最后一个切片。 */
    private fun pickFrom(
        slices: List<KindSlice>,
        r: Float,
    ): ItemKind {
        var acc = 0f
        for (slice in slices) {
            acc += slice.width
            if (r < acc) return slice.kind
        }
        return slices.last().kind
    }

    /** 得分物判定（供渲染/文案复用）。 */
    fun isGood(kind: ItemKind): Boolean =
        kind == ItemKind.RAINDROP ||
            kind == ItemKind.SNOWFLAKE ||
            kind == ItemKind.COIN ||
            kind == ItemKind.PETAL ||
            kind == ItemKind.GIFT ||
            kind == ItemKind.BUBBLE

    /** 得分物分值。 */
    fun scoreOf(kind: ItemKind): Int =
        when (kind) {
            ItemKind.COIN -> 25
            ItemKind.RAINDROP -> 10
            ItemKind.SNOWFLAKE -> 10
            ItemKind.PETAL -> 8
            ItemKind.GIFT -> 20
            ItemKind.BUBBLE -> 0
            ItemKind.CLOUD, ItemKind.HAIL, ItemKind.LIGHTNING -> 0
        }

    /**
     * 连击倍率：HUD 在 combo≥2 显示 ×N；计分与展示对齐，封顶 ×5。
     * [combo] 为本次接住后的连击数（先自增再算分）。
     */
    fun comboScoreMultiplier(combo: Int): Int =
        when {
            combo < 2 -> 1
            else -> minOf(combo, 5)
        }

    /** 惩罚物扣血。 */
    fun damageOf(kind: ItemKind): Int =
        when (kind) {
            ItemKind.LIGHTNING -> 2
            ItemKind.CLOUD, ItemKind.HAIL -> 1
            else -> 0
        }

    /** 护盾单次抵挡时间（秒）；被消耗后重新积累前需要等待这么长。 */
    const val SHIELD_COOLDOWN_SEC = 24.0f

    /** 受击后的无敌时间（秒，闪电与普通伤害统一）。 */
    const val HIT_INVINCIBLE_SEC = 0.6f

    /** 护盾仅作为一次性「抵挡」使用：出现后进入 CD，不能累积。 */
    const val MAX_SHIELD = 1

    /** 双倍得分持续时间（秒）。 */
    const val DOUBLE_TIME_SEC = 8.0f

    /** 生成间隔硬下限（秒）：500 分后继续加压也不低于此值。 */
    const val MIN_SPAWN_GAP_SEC = 0.18f

    /** 加压曲线每档分数跨度：每 [PRESSURE_STEP_SCORE] 分为一档。 */
    private const val PRESSURE_STEP_SCORE = 500f

    /** 危险占比额外加成累计到的分数上限。 */
    private const val DANGER_RAMP_SCORE = 1500f

    /** 满分时额外危险占比（STANDARD；实际按档位 dangerBonus 缩放）。 */
    private const val DANGER_MAX_ADD = 0.12f

    /** 危险占比硬上限：保证好物始终占多数（≥55%）。 */
    private const val MAX_DANGER_SHARE = 0.45f

    /** 高分局混入冰雹的分数阈值。 */
    private const val HAIL_UNLOCK_SCORE = 600

    /** 高分混入闪电的分数阈值。 */
    private const val LIGHTNING_UNLOCK_SCORE = 1200

    /** 新解锁危险物分走的额外危险占比。 */
    private const val NEW_HAIL_SHARE = 0.45f
    private const val NEW_LIGHTNING_SHARE = 0.30f

    /** 分数里程碑（跨过时给一次提示）。 */
    private val MILESTONES = intArrayOf(500, 1000, 2000)

    /**
     * 加压进度：每 [PRESSURE_STEP_SCORE] 分为一档，[t1] 为首档（封顶 1），
     * [t2] 为 500 分之后的继续加压量；两者都按 [CatchDifficulty.pressureRate] 缩放，
     * 使档位决定难度爬升速度（STANDARD 到 ~3000 分仍有增长）。
     */
    private fun pressureOf(
        score: Int,
        difficulty: CatchDifficulty,
    ): Pair<Float, Float> {
        val progress = (score / PRESSURE_STEP_SCORE) * difficulty.pressureRate
        val t1 = progress.coerceAtMost(1f)
        val t2 = (progress - 1f).coerceAtLeast(0f)
        return t1 to t2
    }

    /** 场景基础生成间隔（秒，0 分 / STANDARD）：天气只影响风味底数。 */
    private fun spawnBaseSeconds(scene: CatchScene): Float =
        when (scene) {
            CatchScene.SNOW -> 0.78f
            CatchScene.SUNNY, CatchScene.CLOUDY -> 0.68f
            CatchScene.RAIN_LIGHT, CatchScene.FREEZING_RAIN -> 0.60f
            CatchScene.RAIN -> 0.56f
            CatchScene.RAIN_HEAVY -> 0.48f
            CatchScene.HAIL -> 0.44f
            CatchScene.STORM -> 0.42f
        }

    /**
     * 生成间隔（秒）：500 分内线性缩短，500 分后继续缓慢加压到 ~3000 分，
     * 硬下限 [MIN_SPAWN_GAP_SEC]（修正旧版 500 分封顶且雷暴会低于注释下限的问题）。
     */
    fun spawnGapSeconds(
        score: Int,
        scene: CatchScene = CatchScene.RAIN,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): Float {
        val (t1, t2) = pressureOf(score, difficulty)
        val base = spawnBaseSeconds(scene)
        return (base - 0.30f * t1 - 0.05f * t2).coerceAtLeast(MIN_SPAWN_GAP_SEC)
    }

    /**
     * 下落速度（px/s，已乘 [s]）：分数越高越快；雪天最慢最宽松，冰雹/雷暴最快。
     * 受击后短暂无敌期间下落略放缓，给玩家喘息。
     * [burstWaveActive] 为真时按档位倍率额外提速，制造风暴爆发波次压迫感。
     */
    fun fallSpeed(
        scene: CatchScene,
        score: Int,
        s: Float,
        burstWaveActive: Boolean = false,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): Float {
        val base =
            when (scene) {
                CatchScene.SNOW -> 300f
                CatchScene.SUNNY, CatchScene.CLOUDY -> 390f
                CatchScene.RAIN_LIGHT, CatchScene.FREEZING_RAIN -> 440f
                CatchScene.RAIN -> 490f
                CatchScene.RAIN_HEAVY, CatchScene.STORM -> 570f
                CatchScene.HAIL -> 580f
            }
        val (t1, t2) = pressureOf(score, difficulty)
        var speed = base * (1f + 0.9f * t1 + 0.4f * t2) * s
        if (burstWaveActive) speed *= difficulty.burstSpeedMultiplier
        return speed
    }

    /** 风暴爆发波次时长（秒）。 */
    const val BURST_WAVE_SEC = 4.0f

    /** 高分局爆发周期缩短阈值：1000 分后波次更频繁。 */
    private const val BURST_SPEEDUP_SCORE = 1000

    /** 高分局爆发周期缩放。 */
    private const val BURST_SPEEDUP_SCALE = 0.85f

    /** 该分数 / 档位下的爆发波次周期（秒）：CASUAL 22 / STANDARD 18 / CHALLENGE 15，1000 分后 ×0.85。 */
    fun burstPeriodSeconds(
        score: Int,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): Float {
        val base = difficulty.burstPeriodSec
        return if (score >= BURST_SPEEDUP_SCORE) base * BURST_SPEEDUP_SCALE else base
    }

    /** 该时刻是否处于风暴爆发波次：周期前 [BURST_WAVE_SEC] 秒为波次。 */
    fun inBurstWave(
        time: Float,
        score: Int = 0,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): Boolean {
        val period = burstPeriodSeconds(score, difficulty)
        val cycle = ((time % period) + period) % period
        return cycle < BURST_WAVE_SEC
    }

    /**
     * 横风横向偏移系数（像素/秒）：雨天/雷暴越强越明显，雪天轻微，晴天无。
     * 正为中心偏右、负为偏左。掉落物整体被风带着斜飘，玩家需预判落点。
     * 500 分后继续缓慢加压（旧版封顶）。
     */
    fun windDrift(
        scene: CatchScene,
        score: Int,
        s: Float,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): Float {
        val base =
            when (scene) {
                CatchScene.SUNNY, CatchScene.CLOUDY -> 0f
                CatchScene.SNOW -> 24f
                CatchScene.RAIN_LIGHT, CatchScene.FREEZING_RAIN -> 60f
                CatchScene.RAIN -> 90f
                CatchScene.RAIN_HEAVY -> 130f
                CatchScene.STORM -> 150f
                CatchScene.HAIL -> 120f
            }
        val (t1, t2) = pressureOf(score, difficulty)
        return base * (1f + 0.6f * t1 + 0.25f * t2) * s
    }

    /** 跨过 500 / 1000 / 2000 时返回里程碑值；一次跨多个返回最高值，未跨过返回 null。 */
    fun milestoneFor(
        prevScore: Int,
        newScore: Int,
    ): Int? {
        var reached: Int? = null
        for (milestone in MILESTONES) {
            if (prevScore < milestone && newScore >= milestone) reached = milestone
        }
        return reached
    }

    /** 掉落物半径（px，已乘 [s]）。 */
    fun itemRadius(
        kind: ItemKind,
        s: Float,
    ): Float =
        when (kind) {
            ItemKind.CLOUD -> 30f * s
            ItemKind.LIGHTNING -> 28f * s
            ItemKind.HAIL -> 16f * s
            ItemKind.COIN -> 15f * s
            ItemKind.GIFT -> 18f * s
            ItemKind.BUBBLE -> 17f * s
            else -> 13f * s
        }

    /**
     * 推进一帧：移动所有掉落物、清理出界、按累计钟生成新掉落物、判定接住/中招。
     *
     * 圆接圆碰撞：小熊视为圆心 [catchX]/[catchY]、半径 [catchR] 的圆。
     * 得分物漏接只计数（不含事件）；惩罚物溜出底部即自然消失，不扣血。
     *
     * @return [FrameEvents] 供渲染层做特效，并把 `spawnAccum` 回传继续累计。
     */
    fun stepCatchWorld(
        items: MutableList<Item>,
        score: Int,
        scene: CatchScene,
        s: Float,
        dt: Float,
        width: Float,
        height: Float,
        catchX: Float,
        catchY: Float,
        catchR: Float,
        spawnAccum: Float,
        random: java.util.Random,
        time: Float = 0f,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): FrameEvents {
        var accum = spawnAccum + dt
        val burstWave = scene == CatchScene.STORM || scene == CatchScene.HAIL
        val burstActive = burstWave && inBurstWave(time, score, difficulty)
        val gap = spawnGapSeconds(score, scene, difficulty)
        val wind = windDrift(scene, score, s, difficulty)
        var spawned = 0
        // 长帧最多补足 8 次生成，避免一帧塞满全场；每帧最多 1 个已够，这里仅防御性兜底
        while (accum >= gap && items.size < MAX_ITEMS && spawned < 8) {
            val kind = itemKindFor(scene, random.nextFloat(), score, difficulty)
            val r = itemRadius(kind, s)
            val x = r + random.nextFloat() * (width - 2f * r).coerceAtLeast(1f)
            items.add(
                Item(
                    kind = kind,
                    x = x,
                    y = -r,
                    r = r,
                    phase = random.nextFloat() * 6.28f,
                ).also { it.vy = fallSpeed(scene, score, s, burstActive, difficulty) },
            )
            accum -= gap
            spawned++
        }

        val caught = ArrayList<ItemKind>()
        val hit = ArrayList<ItemKind>()
        var missedGood = 0

        val iter = items.iterator()
        while (iter.hasNext()) {
            val it = iter.next()
            it.y += it.vy * dt
            // 横风：掉落物带横向漂移（雨/雷暴明显、雪轻微、晴天无）
            it.x += wind * dt

            val dx = it.x - catchX
            val dy = it.y - catchY
            val reach = catchR + it.r
            if (dx * dx + dy * dy < reach * reach) {
                iter.remove()
                if (isGood(it.kind)) {
                    caught.add(it.kind)
                } else {
                    hit.add(it.kind)
                }
                continue
            }

            // 横风把物品吹出左右屏外：玩家不可能接到，只移除；
            // 不计 missedGood、不断连击（与纵向漏接区分开）。
            if (it.x < -it.r || it.x > width + it.r) {
                iter.remove()
                continue
            }

            if (it.y - it.r > height) {
                iter.remove()
                // BUBBLE 只是道具，不计入连击得分物：漏接不应断连击
                if (isGood(it.kind) && it.kind != ItemKind.BUBBLE) missedGood++
            }
        }

        return FrameEvents(
            caught = caught,
            hit = hit,
            missedGood = missedGood,
            spawnAccum = accum,
            spawnedCount = spawned,
            inBurstWave = burstActive,
        )
    }

    /** 用默认随机源包装 [stepCatchWorld]。渲染层应传复用的 [random]，避免每帧 new Random。 */
    fun stepCatchWorldDefault(
        items: MutableList<Item>,
        score: Int,
        scene: CatchScene,
        s: Float,
        dt: Float,
        width: Float,
        height: Float,
        catchX: Float,
        catchY: Float,
        catchR: Float,
        spawnAccum: Float,
        random: java.util.Random = java.util.Random(),
        time: Float = 0f,
        difficulty: CatchDifficulty = CatchDifficulty.STANDARD,
    ): FrameEvents =
        stepCatchWorld(
            items = items,
            score = score,
            scene = scene,
            s = s,
            dt = dt,
            width = width,
            height = height,
            catchX = catchX,
            catchY = catchY,
            catchR = catchR,
            spawnAccum = spawnAccum,
            random = random,
            time = time,
            difficulty = difficulty,
        )

    const val MAX_ITEMS = 40
}
