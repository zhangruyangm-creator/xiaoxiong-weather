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

    /** 天气 → 掉落物权重的确定性映射（下表与 [itemKindFor] 对齐）。 */
    fun itemKindFor(
        scene: CatchScene,
        r: Float,
    ): ItemKind =
        when (scene) {
            CatchScene.SUNNY ->
                when {
                    r < 0.13f -> ItemKind.COIN
                    r < 0.55f -> ItemKind.PETAL
                    r < 0.74f -> ItemKind.RAINDROP
                    r < 0.88f -> ItemKind.CLOUD
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.CLOUDY ->
                when {
                    r < 0.08f -> ItemKind.COIN
                    r < 0.74f -> ItemKind.RAINDROP
                    r < 0.88f -> ItemKind.CLOUD
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.RAIN_LIGHT, CatchScene.RAIN ->
                when {
                    r < 0.10f -> ItemKind.COIN
                    r < 0.80f -> ItemKind.RAINDROP
                    r < 0.90f -> ItemKind.CLOUD
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.RAIN_HEAVY ->
                when {
                    r < 0.04f -> ItemKind.COIN
                    r < 0.76f -> ItemKind.RAINDROP
                    r < 0.88f -> ItemKind.CLOUD
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.SNOW ->
                when {
                    r < 0.07f -> ItemKind.COIN
                    r < 0.86f -> ItemKind.SNOWFLAKE
                    r < 0.96f -> ItemKind.CLOUD
                    else -> ItemKind.GIFT
                }
            CatchScene.STORM ->
                when {
                    r < 0.66f -> ItemKind.RAINDROP
                    r < 0.85f -> ItemKind.CLOUD
                    r < 0.93f -> ItemKind.LIGHTNING
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.FREEZING_RAIN ->
                when {
                    r < 0.62f -> ItemKind.RAINDROP
                    r < 0.81f -> ItemKind.CLOUD
                    r < 0.90f -> ItemKind.HAIL
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
            CatchScene.HAIL ->
                when {
                    r < 0.50f -> ItemKind.RAINDROP
                    r < 0.76f -> ItemKind.HAIL
                    r < 0.88f -> ItemKind.CLOUD
                    r < 0.98f -> ItemKind.GIFT
                    else -> ItemKind.BUBBLE
                }
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
    const val SHIELD_COOLDOWN_SEC = 18.0f

    /** 护盾仅作为一次性「抵挡」使用：出现后进入 CD，不能累积。 */
    const val MAX_SHIELD = 1

    /** 双倍得分持续时间（秒）。 */
    const val DOUBLE_TIME_SEC = 8.0f

    /** 生成间隔（秒），随分数缩短、先快后缓，最低 0.26s；雷暴/冰雹基础更密。 */
    fun spawnGapSeconds(
        score: Int,
        scene: CatchScene = CatchScene.RAIN,
    ): Float {
        val t = (score / 500f).coerceIn(0f, 1f)
        val base =
            when (scene) {
                CatchScene.SNOW -> 0.78f
                CatchScene.SUNNY, CatchScene.CLOUDY -> 0.68f
                CatchScene.RAIN_LIGHT, CatchScene.FREEZING_RAIN -> 0.60f
                CatchScene.RAIN -> 0.56f
                CatchScene.RAIN_HEAVY -> 0.48f
                CatchScene.HAIL -> 0.44f
                CatchScene.STORM -> 0.42f
            }
        return base - 0.30f * t
    }

    /**
     * 下落速度（px/s，已乘 [s]）：分数越高越快；雪天最慢最宽松，冰雹/雷暴最快。
     * 受击后短暂无敌期间下落略放缓，给玩家喘息。
     * [burstWaveActive] 为真时额外提速，制造风暴爆发波次压迫感。
     */
    fun fallSpeed(
        scene: CatchScene,
        score: Int,
        s: Float,
        burstWaveActive: Boolean = false,
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
        val t = (score / 500f).coerceIn(0f, 1f)
        var speed = base * (1f + 0.9f * t) * s
        if (burstWaveActive) speed *= 1.25f
        return speed
    }

    /** 风暴爆发波次时长（秒）。 */
    const val BURST_WAVE_SEC = 4.0f

    /** 风暴爆发波次间隔（秒）：在 [BURST_WAVE_SEC] 与 [BURST_PERIOD_SEC] 之间循环。 */
    const val BURST_PERIOD_SEC = 22.0f

    /** 该时刻是否处于风暴爆发波次：每 22s 循环，前 4s 为波次。 */
    fun inBurstWave(time: Float): Boolean {
        val cycle = time % BURST_PERIOD_SEC
        return cycle < BURST_WAVE_SEC
    }

    /**
     * 横风横向偏移系数（像素/秒）：雨天/雷暴越强越明显，雪天轻微，晴天无。
     * 正为中心偏右、负为偏左。掉落物整体被风带着斜飘，玩家需预判落点。
     */
    fun windDrift(
        scene: CatchScene,
        score: Int,
        s: Float,
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
        val t = (score / 500f).coerceIn(0f, 1f)
        return base * (1f + 0.6f * t) * s
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
    ): FrameEvents {
        var accum = spawnAccum + dt
        val burstWave = scene == CatchScene.STORM || scene == CatchScene.HAIL
        val burstActive = burstWave && inBurstWave(time)
        val gap = spawnGapSeconds(score, scene)
        val wind = windDrift(scene, score, s)
        var spawned = 0
        // 长帧最多补足 8 次生成，避免一帧塞满全场；每帧最多 1 个已够，这里仅防御性兜底
        while (accum >= gap && items.size < MAX_ITEMS && spawned < 8) {
            val kind = itemKindFor(scene, random.nextFloat())
            val r = itemRadius(kind, s)
            val x = r + random.nextFloat() * (width - 2f * r).coerceAtLeast(1f)
            items.add(
                Item(
                    kind = kind,
                    x = x,
                    y = -r,
                    r = r,
                    phase = random.nextFloat() * 6.28f,
                ).also { it.vy = fallSpeed(scene, score, s, burstActive) },
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
        )

    const val MAX_ITEMS = 40
}
