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

        /** 花瓣：得分 +5，晴天点缀（细小、高频，挑战性）。 */
        PETAL,

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
    )

    /** 天气 → 掉落物权重的确定性映射（下表与 [itemKindFor] 对齐）。 */
    fun itemKindFor(
        scene: CatchScene,
        r: Float,
    ): ItemKind =
        when (scene) {
            CatchScene.SUNNY ->
                when {
                    r < 0.16f -> ItemKind.COIN
                    r < 0.61f -> ItemKind.PETAL
                    r < 0.78f -> ItemKind.RAINDROP
                    r < 0.92f -> ItemKind.CLOUD
                    else -> ItemKind.COIN
                }
            CatchScene.CLOUDY ->
                when {
                    r < 0.10f -> ItemKind.COIN
                    r < 0.78f -> ItemKind.RAINDROP
                    else -> ItemKind.CLOUD
                }
            CatchScene.RAIN_LIGHT, CatchScene.RAIN ->
                when {
                    r < 0.12f -> ItemKind.COIN
                    r < 0.84f -> ItemKind.RAINDROP
                    else -> ItemKind.CLOUD
                }
            CatchScene.RAIN_HEAVY ->
                when {
                    r < 0.05f -> ItemKind.COIN
                    r < 0.80f -> ItemKind.RAINDROP
                    else -> ItemKind.CLOUD
                }
            CatchScene.SNOW ->
                when {
                    r < 0.08f -> ItemKind.COIN
                    r < 0.90f -> ItemKind.SNOWFLAKE
                    else -> ItemKind.CLOUD
                }
            CatchScene.STORM ->
                when {
                    r < 0.72f -> ItemKind.RAINDROP
                    r < 0.90f -> ItemKind.CLOUD
                    else -> ItemKind.LIGHTNING
                }
            CatchScene.FREEZING_RAIN ->
                when {
                    r < 0.68f -> ItemKind.RAINDROP
                    r < 0.86f -> ItemKind.CLOUD
                    else -> ItemKind.HAIL
                }
            CatchScene.HAIL ->
                when {
                    r < 0.55f -> ItemKind.RAINDROP
                    r < 0.80f -> ItemKind.HAIL
                    else -> ItemKind.CLOUD
                }
        }

    /** 得分物判定（供渲染/文案复用）。 */
    fun isGood(kind: ItemKind): Boolean =
        kind == ItemKind.RAINDROP ||
            kind == ItemKind.SNOWFLAKE ||
            kind == ItemKind.COIN ||
            kind == ItemKind.PETAL

    /** 得分物分值。 */
    fun scoreOf(kind: ItemKind): Int =
        when (kind) {
            ItemKind.COIN -> 25
            ItemKind.RAINDROP -> 10
            ItemKind.SNOWFLAKE -> 10
            ItemKind.PETAL -> 8
            ItemKind.CLOUD, ItemKind.HAIL, ItemKind.LIGHTNING -> 0
        }

    /** 惩罚物扣血。 */
    fun damageOf(kind: ItemKind): Int =
        when (kind) {
            ItemKind.LIGHTNING -> 2
            ItemKind.CLOUD, ItemKind.HAIL -> 1
            else -> 0
        }

    /** 生成间隔（秒），随分数缩短、先快后缓，最低 0.38s。 */
    fun spawnGapSeconds(score: Int): Float {
        val t = (score / 300f).coerceIn(0f, 1f)
        return 0.85f - 0.47f * t
    }

    /**
     * 下落速度（px/s，已乘 [s]）：分数越高越快；雪天最慢最宽松，冰雹/雷暴最快。
     * 受击后短暂无敌期间下落略放缓，给玩家喘息。
     */
    fun fallSpeed(
        scene: CatchScene,
        score: Int,
        s: Float,
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
        val t = (score / 300f).coerceIn(0f, 1f)
        return base * (1f + 0.5f * t) * s
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
    ): FrameEvents {
        var accum = spawnAccum + dt
        val gap = spawnGapSeconds(score)
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
                ).also { it.vy = fallSpeed(scene, score, s) },
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
                if (isGood(it.kind)) missedGood++
            }
        }

        return FrameEvents(
            caught = caught,
            hit = hit,
            missedGood = missedGood,
            spawnAccum = accum,
            spawnedCount = spawned,
        )
    }

    /** 用默认随机源包装 [stepCatchWorld]，供渲染层每帧调用。 */
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
            random = java.util.Random(),
        )

    const val MAX_ITEMS = 40
}
