package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.R
import kotlin.random.Random

/**
 * 农场的纯数据状态：不依赖 Compose，可直接由 [FarmSave] 构造/导出。
 * 所有规则都通过 [FarmSimulator] 修改它，UI 层只负责展示与持久化。
 */
data class FarmState(
    var coins: Int = 80,
    var plots: List<FarmPlotState> = List(FARM_PLOT_COUNT) { FarmPlotState() },
    var unlocked: Set<String> = setOf("radish", "cabbage", "wheat", "tomato"),
    var harvestedTotal: Int = 0,
    var harvestCounts: Map<String, Int> = emptyMap(),
    var inventory: Map<String, Int> = emptyMap(),
    var completedSeasons: Int = 0,
    var lifetimeHarvests: Int = 0,
    /** 本季已收获的作物种类；开启新一季后清空（判断能否开启新一季用）。 */
    var seasonCollected: Set<String> = emptySet(),
    /** 本季天气订单（未完成最多 2 条）；换季清空。 */
    var weatherOrders: List<FarmWeatherOrder> = emptyList(),
) {
    val currentSeason: Int get() = completedSeasons + 1

    /** 已收获过的作物种类数（图鉴收藏数，跨周目累计）。 */
    val collectedCount: Int get() = harvestCounts.size

    /** 本季已收获的作物种类数（开启新一季的进度）。 */
    val seasonCollectedCount: Int get() = seasonCollected.size

    /**
     * 本季开启新一季所需的作物种类数：随周目递增（12/16/20…），封顶 40。
     * 不再要求集齐全部 128 种，降低周目门槛。
     */
    val seasonGoal: Int get() = (12 + completedSeasons.coerceIn(0, MAX_FARM_SEASONS_STORED) * 4).coerceAtMost(40)

    /** 本季是否已达成开季门槛。 */
    val seasonReady: Boolean get() = seasonCollectedCount >= seasonGoal

    /** 是否已集齐本季全部作物（开季门槛之外的额外成就）。 */
    val allCollected: Boolean get() = FARM_CROP_ORDER.all { it in seasonCollected }

    /**
     * 导出可安全交给后台线程编码的快照。
     *
     * [FarmPlotState] 是带 `var` 的可变对象，而 60fps 帧循环里的 `tick` /
     * `advanceGrowth` 会**原地**改它们。这里必须逐块 `copy()`：只复制列表的话，
     * 快照与活状态共享元素，后果有两个——后台编码时读到撕裂的数据；更隐蔽的是
     * 存盘去重基准 `lastSaved` 也会跟着被改，于是「只有地块变化」（作物生长）时
     * `toSave() == lastSaved` 恒为真，磁盘永远停在旧进度，静默丢存档。
     */
    fun toSave(): FarmSave =
        FarmSave(
            coins = coins,
            plots = plots.map { it.copy() },
            unlockedCropIds = unlocked.toList(),
            harvestedTotal = harvestedTotal,
            harvestCounts = harvestCounts,
            completedSeasons = completedSeasons,
            lifetimeHarvests = lifetimeHarvests,
            schemaVersion = FARM_SAVE_SCHEMA_VERSION,
            inventory = inventory,
            seasonCollected = seasonCollected,
            weatherOrders = weatherOrders.map { it.copy() },
        )

    companion object {
        /**
         * 从存档构造。旧存档（1.x，无图鉴/本季收集字段）一次性回填：
         * 已解锁的作物视为收集过，避免老玩家迁移后图鉴从零开始；
         * 若旧档已集齐全部作物（图鉴或解锁覆盖全部 128 种），本季收集也一并回填，
         * 让老玩家能像旧版「集齐开新季」一样立即开启新一季，而不是从 0/12 重新攒进度。
         */
        fun fromSave(
            save: FarmSave,
            backfillUnlockedAsCollected: Boolean,
        ): FarmState {
            // 存档先过一遍清洗：负数金币、越界阶段/湿度、已删除的作物 id 都在这里被夹回去
            val clean = save.sanitized()
            val backfilledHarvestCounts =
                if (backfillUnlockedAsCollected) {
                    clean.unlockedCropIds.toSet().associateWith { 1 }
                } else {
                    clean.harvestCounts
                }
            val oldSaveFullyCollected =
                clean.schemaVersion < 2 && backfilledHarvestCounts.size >= FARM_CROPS.size
            return FarmState(
                coins = clean.coins,
                plots = clean.plots.map { it.copy() },
                unlocked =
                    clean.unlockedCropIds.toSet().ifEmpty {
                        FARM_DEFAULT_UNLOCKED.toSet()
                    },
                harvestedTotal = clean.harvestedTotal,
                harvestCounts = backfilledHarvestCounts,
                inventory = clean.inventory,
                completedSeasons = clean.completedSeasons,
                lifetimeHarvests = clean.lifetimeHarvests,
                seasonCollected =
                    if (oldSaveFullyCollected) backfilledHarvestCounts.keys else clean.seasonCollected,
                weatherOrders = clean.weatherOrders,
            )
        }
    }
}

/** 一次操作产生的 UI 反馈（纯数据，由 UI 层翻译成 toast / 飘字 / 弹卡）。 */
data class FarmRewardSpec(
    val plotIndex: Int,
    val label: String,
)

data class FarmEffect(
    val toastRes: Int? = null,
    val toastArgs: List<Any> = emptyList(),
    val rewards: List<FarmRewardSpec> = emptyList(),
    /** 本次操作新解锁的作物（一次操作可能解锁多个，如 [FarmSimulator.careAll]）。 */
    val unlockCropIds: List<String> = emptyList(),
    /** 天气订单结算的金币（已写入 [FarmState.coins]）。 */
    val orderCoins: Int = 0,
)

/**
 * 农场规则引擎：所有状态流转都在这层，纯 Kotlin 可单测。
 * 天气/随机数通过参数注入，便于确定性测试。
 */
object FarmSimulator {
    /**
     * 按真实时间推进：浇水蒸发、下雨补水、杂草害虫、生长。
     * 离线追赶按分钟切片（单次最多追 8 小时），避免一口气跳阶段/枯萎。
     */
    fun tick(
        state: FarmState,
        now: Long,
        weather: FarmWeather,
        random: Random = Random.Default,
        forecast: List<FarmWeatherSlice> = emptyList(),
    ) {
        val maxCatchUp = 8L * 60L * 60L * 1000L
        val minStepMs = 200L
        val sliceMs = 60_000L // 离线追赶按 1 分钟切片

        // 快速路径：没有任何格子需要推进时跳过整段 map/拷贝（在线时大多数帧都如此）
        val needsWork =
            state.plots.any { p ->
                val last = p.lastTickMs
                last <= 0L || last > now || now - last >= minStepMs
            }
        if (!needsWork) return

        val wiltPPerMinute = farmChanceAtLeastOnce(0.25f / 24f, 1f / 60f)
        val seasonMul = seasonGrowthMul(state.completedSeasons)
        val fxByWeather = mutableMapOf<FarmWeather, FarmWeatherEffect>()
        val weedPByWeather = mutableMapOf<FarmWeather, Float>()
        val bugPByWeather = mutableMapOf<FarmWeather, Float>()

        var dirty = false
        val next =
            state.plots.map { p ->
                val plot = p.copy()
                if (plot.lastTickMs <= 0L) {
                    plot.lastTickMs = now
                    dirty = true
                }
                var elapsed = now - plot.lastTickMs
                if (elapsed < 0L) {
                    // 时钟回拨（NTP 校时/手动改时间）：把 lastTickMs 收敛到 now，
                    // 否则 elapsed 恒为 0 且 lastTickMs 停在未来，农场会冻结到墙钟追平
                    plot.lastTickMs = now
                    elapsed = 0L
                    dirty = true
                }
                if (elapsed > maxCatchUp) elapsed = maxCatchUp
                // 未满 200ms 不推进；但 lastTickMs 初值修正仍要写回
                if (elapsed < minStepMs) return@map plot
                dirty = true

                val crop = cropById(plot.cropId)
                var t = plot.lastTickMs
                while (elapsed >= minStepMs) {
                    val step = if (elapsed > sliceMs) sliceMs else elapsed
                    elapsed -= step
                    val w = farmWeatherAt(t, weather, forecast)
                    val fx = fxByWeather.getOrPut(w) { farmWeatherEffect(w) }
                    val fullMinute = step >= sliceMs
                    val hours = step / 3_600_000f
                    val weedPMin = weedPByWeather.getOrPut(w) { farmChanceAtLeastOnce(fx.weedChancePerHour, 1f / 60f) }
                    val bugPMin = bugPByWeather.getOrPut(w) { farmChanceAtLeastOnce(fx.bugChancePerHour, 1f / 60f) }
                    val weedP = if (fullMinute) weedPMin else fx.weedChancePerHour * hours
                    val bugP = if (fullMinute) bugPMin else fx.bugChancePerHour * hours
                    val wiltP = if (fullMinute) wiltPPerMinute else (0.25f / 24f) * hours

                    plot.moisture =
                        (plot.moisture - fx.dryPerHour * hours + fx.rainPerHour * hours)
                            .coerceIn(0f, 1f)

                    if (plot.stage in 1..4) {
                        if (!plot.weed && random.nextFloat() < weedP) plot.weed = true
                        if (!plot.bug && random.nextFloat() < bugP) plot.bug = true
                    }

                    if (plot.stage in 1..4 && plot.moisture < 0.08f && random.nextFloat() < wiltP) {
                        plot.stage = 5
                        plot.stageProgress = 0f
                    }

                    if (crop != null) {
                        advanceGrowth(
                            plot,
                            crop,
                            hours,
                            fx,
                            seasonMul,
                            preferred = crop.prefers(w),
                        )
                    }
                    t += step
                }
                plot.lastTickMs = now
                plot
            }
        if (dirty) state.plots = next
    }

    fun plant(
        state: FarmState,
        index: Int,
        seedId: String,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (plot.stage != 0) return FarmEffect(toastRes = R.string.bear_farm_toast_occupied)
        val crop = cropById(seedId) ?: return FarmEffect()
        if (crop.id !in state.unlocked) return FarmEffect(toastRes = R.string.bear_farm_toast_locked_seed)
        if (state.coins < crop.seedCost) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_need_coins, toastArgs = listOf(crop.seedCost))
        }
        state.coins -= crop.seedCost
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == index) {
                    p.copy(
                        cropId = crop.id,
                        stage = 1,
                        stageProgress = 0f,
                        moisture = 0.55f,
                        weed = false,
                        bug = false,
                    )
                } else {
                    p
                }
            }
        return FarmEffect(toastRes = R.string.bear_farm_toast_planted, toastArgs = listOf(crop.emoji, crop.nameZh))
    }

    fun water(
        state: FarmState,
        index: Int,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (plot.stage == 0 || plot.stage == 5) return FarmEffect(toastRes = R.string.bear_farm_toast_nothing_water)
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == index) p.copy(moisture = (p.moisture + 0.45f).coerceAtMost(1f)) else p
            }
        return FarmEffect(toastRes = R.string.bear_farm_toast_watered)
    }

    fun weed(
        state: FarmState,
        index: Int,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (!plot.weed) return FarmEffect(toastRes = R.string.bear_farm_toast_no_weed)
        state.plots = state.plots.mapIndexed { i, p -> if (i == index) p.copy(weed = false) else p }
        return FarmEffect(toastRes = R.string.bear_farm_toast_weeded)
    }

    fun bug(
        state: FarmState,
        index: Int,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (!plot.bug) return FarmEffect(toastRes = R.string.bear_farm_toast_no_bug)
        state.plots = state.plots.mapIndexed { i, p -> if (i == index) p.copy(bug = false) else p }
        return FarmEffect(toastRes = R.string.bear_farm_toast_debugged)
    }

    fun clear(
        state: FarmState,
        index: Int,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (plot.stage == 0) return FarmEffect(toastRes = R.string.bear_farm_toast_already_empty)
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == index) {
                    p.copy(
                        cropId = null,
                        stage = 0,
                        stageProgress = 0f,
                        weed = false,
                        bug = false,
                    )
                } else {
                    p
                }
            }
        return FarmEffect(toastRes = R.string.bear_farm_toast_cleared)
    }

    /** 收获：作物进背包，图鉴/计数更新；满足进度时解锁下一种作物。 */
    fun harvest(
        state: FarmState,
        index: Int,
        weather: FarmWeather,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (plot.stage != 4) return FarmEffect(toastRes = R.string.bear_farm_toast_not_ripe)
        val crop = cropById(plot.cropId) ?: return FarmEffect()
        state.harvestedTotal++
        state.lifetimeHarvests++
        state.harvestCounts = state.harvestCounts + (crop.id to (state.harvestCounts[crop.id] ?: 0) + 1)
        state.seasonCollected = state.seasonCollected + crop.id
        state.inventory = state.inventory + (crop.id to (state.inventory[crop.id] ?: 0) + 1)
        val unlockedCropId = unlockByProgress(state)
        val orderCoins = applyHarvestToOrders(state, crop, weather)
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == index) {
                    p.copy(
                        cropId = null,
                        stage = 0,
                        stageProgress = 0f,
                        weed = false,
                        bug = false,
                        moisture = 0.4f,
                    )
                } else {
                    p
                }
            }
        return FarmEffect(
            toastRes = R.string.bear_farm_toast_harvest,
            toastArgs = listOf(crop.emoji, crop.nameZh),
            rewards = listOf(FarmRewardSpec(index, "+1 ${crop.emoji}")),
            unlockCropIds = listOfNotNull(unlockedCropId),
            orderCoins = orderCoins,
        )
    }

    /** 金肥料：付费让一块生长中的作物立刻推进 [FERTILIZE_HOURS] 小时。 */
    fun fertilize(
        state: FarmState,
        index: Int,
        weather: FarmWeather,
    ): FarmEffect {
        val plot = state.plots.getOrNull(index) ?: return FarmEffect()
        if (plot.stage !in 1..3) return FarmEffect(toastRes = R.string.bear_farm_toast_fertilize_target)
        if (plot.weed || plot.bug || plot.moisture < 0.25f) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_fertilize_blocked)
        }
        if (state.coins < FERTILIZE_COST) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_need_coins, toastArgs = listOf(FERTILIZE_COST))
        }
        val crop = cropById(plot.cropId) ?: return FarmEffect()
        state.coins -= FERTILIZE_COST
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == index) {
                    p.copy().also {
                        advanceGrowth(
                            it,
                            crop,
                            FERTILIZE_HOURS,
                            farmWeatherEffect(weather),
                            seasonGrowthMul(state.completedSeasons),
                            preferred = crop.prefers(weather),
                        )
                    }
                } else {
                    p
                }
            }
        return FarmEffect(toastRes = R.string.bear_farm_toast_fertilize, toastArgs = listOf(crop.emoji, crop.nameZh))
    }

    /** 小雨壶：全田浇水（一次性道具）。 */
    fun rainAll(state: FarmState): FarmEffect {
        val watered = state.plots.count { p -> p.stage in 1..4 && p.moisture < 1f }
        if (watered == 0) return FarmEffect(toastRes = R.string.bear_farm_care_all_nothing)
        if (state.coins < RAIN_CAN_COST) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_need_coins, toastArgs = listOf(RAIN_CAN_COST))
        }
        state.coins -= RAIN_CAN_COST
        state.plots =
            state.plots.map { p ->
                if (p.stage in 1..4) p.copy(moisture = (p.moisture + 0.45f).coerceAtMost(1f)) else p
            }
        return FarmEffect(toastRes = R.string.bear_farm_toast_item_rain, toastArgs = listOf(watered))
    }

    /** 除虫喷雾：清除全田害虫（一次性道具）。 */
    fun bugAll(state: FarmState): FarmEffect {
        val count = state.plots.count { it.bug }
        if (count == 0) return FarmEffect(toastRes = R.string.bear_farm_care_all_nothing)
        if (state.coins < BUG_SPRAY_COST) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_need_coins, toastArgs = listOf(BUG_SPRAY_COST))
        }
        state.coins -= BUG_SPRAY_COST
        state.plots = state.plots.map { if (it.bug) it.copy(bug = false) else it }
        return FarmEffect(toastRes = R.string.bear_farm_toast_item_bug, toastArgs = listOf(count))
    }

    /** 除草剂：清除全田杂草（一次性道具）。 */
    fun weedAll(state: FarmState): FarmEffect {
        val count = state.plots.count { it.weed }
        if (count == 0) return FarmEffect(toastRes = R.string.bear_farm_care_all_nothing)
        if (state.coins < WEED_KILLER_COST) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_need_coins, toastArgs = listOf(WEED_KILLER_COST))
        }
        state.coins -= WEED_KILLER_COST
        state.plots = state.plots.map { if (it.weed) it.copy(weed = false) else it }
        return FarmEffect(toastRes = R.string.bear_farm_toast_item_weed, toastArgs = listOf(count))
    }

    /** 一键照料：按智能照料优先级处理全部格子，收尾汇总成一条提示。 */
    fun careAll(
        state: FarmState,
        weather: FarmWeather,
    ): FarmEffect {
        var harvested = 0
        var watered = 0
        var weeded = 0
        var debugged = 0
        var cleared = 0
        var orderCoins = 0
        val rewards = mutableListOf<FarmRewardSpec>()
        val unlockCropIds = mutableListOf<String>()
        state.plots.indices.forEach { index ->
            val plot = state.plots[index]
            val effect =
                when {
                    plot.bug -> {
                        debugged++
                        bug(state, index)
                    }
                    plot.weed -> {
                        weeded++
                        weed(state, index)
                    }
                    plot.stage == 4 -> {
                        harvested++
                        harvest(state, index, weather)
                    }
                    plot.stage in 1..4 && plot.moisture < 0.35f -> {
                        watered++
                        water(state, index)
                    }
                    plot.stage == 5 -> {
                        cleared++
                        clear(state, index)
                    }
                    else -> null
                }
            if (effect != null) {
                rewards += effect.rewards
                unlockCropIds += effect.unlockCropIds
                orderCoins += effect.orderCoins
            }
        }
        val done = harvested + watered + weeded + debugged + cleared
        return FarmEffect(
            toastRes = if (done == 0) R.string.bear_farm_care_all_nothing else R.string.bear_farm_care_all_summary,
            toastArgs = if (done == 0) emptyList() else listOf(harvested, watered, weeded, debugged, cleared),
            rewards = rewards,
            unlockCropIds = unlockCropIds,
            orderCoins = orderCoins,
        )
    }

    /**
     * 开启新一季：重置金币/田地/解锁，保留图鉴与累计记录，获得永久生长加成。
     * [now] 由调用方注入（默认墙钟），便于测试与复用同一时间基准。
     */
    fun startNewSeason(
        state: FarmState,
        now: Long = System.currentTimeMillis(),
    ): FarmEffect {
        if (!state.seasonReady) return FarmEffect()
        state.coins = 80
        state.plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now) }
        state.unlocked = setOf("radish", "cabbage", "wheat", "tomato")
        state.harvestedTotal = 0
        state.seasonCollected = emptySet()
        state.weatherOrders = emptyList()
        state.completedSeasons++
        return FarmEffect(
            toastRes = R.string.bear_farm_new_season_started,
            toastArgs = listOf(seasonGrowthPercent(state.completedSeasons)),
        )
    }

    /** 商店直接解锁作物（金币）。 */
    fun buyUnlock(
        state: FarmState,
        cropId: String,
    ): FarmEffect {
        val crop = cropById(cropId) ?: return FarmEffect()
        if (cropId in state.unlocked) return FarmEffect()
        val price = crop.seedCost * 5
        if (state.coins < price) {
            return FarmEffect(toastRes = R.string.bear_farm_toast_unlock_need, toastArgs = listOf(price))
        }
        state.coins -= price
        state.unlocked = state.unlocked + cropId
        return FarmEffect(toastRes = R.string.bear_farm_toast_unlocked, toastArgs = listOf(crop.emoji, crop.nameZh))
    }

    /** 卖出背包里某作物的全部；售价按当前天气加成。 */
    fun sellAll(
        state: FarmState,
        cropId: String,
        weather: FarmWeather,
    ): FarmEffect {
        val count = state.inventory[cropId] ?: 0
        if (count <= 0) return FarmEffect(toastRes = R.string.bear_farm_toast_sell_nothing)
        val crop = cropById(cropId) ?: return FarmEffect(toastRes = R.string.bear_farm_toast_sell_nothing)
        val unit = sellPriceOf(crop, weather)
        val gain = unit * count
        state.coins += gain
        state.inventory = state.inventory - cropId
        return FarmEffect(
            toastRes = R.string.bear_farm_toast_sell,
            toastArgs = listOf(count, crop.emoji, crop.nameZh, gain),
        )
    }

    /** 一键卖出背包全部；售价按当前天气加成。 */
    fun sellAllInventory(
        state: FarmState,
        weather: FarmWeather,
    ): FarmEffect {
        if (state.inventory.isEmpty()) return FarmEffect(toastRes = R.string.bear_farm_toast_sell_nothing)
        var total = 0
        state.inventory.forEach { (cropId, count) ->
            val crop = cropById(cropId) ?: return@forEach
            total += sellPriceOf(crop, weather) * count
        }
        state.coins += total
        state.inventory = emptyMap()
        return FarmEffect(toastRes = R.string.bear_farm_toast_sell_all, toastArgs = listOf(total))
    }

    /** 满足解锁进度时解锁下一种作物；未解锁返回 null。 */
    private fun unlockByProgress(state: FarmState): String? {
        val next = FARM_CROP_ORDER.firstOrNull { it !in state.unlocked } ?: return null
        val need = farmUnlockNeed(state.unlocked.size)
        if (state.harvestedTotal < need) return null
        state.unlocked = state.unlocked + next
        return next
    }

    /**
     * 当前天气若有喜好类别，且本季未满 [FARM_ORDER_MAX] 条未完成订单、也还没有该天气的订单，则补 1～2 条。
     * 已有其它天气的订单会保留（下次该天气再继续）。
     */
    fun ensureWeatherOrders(
        state: FarmState,
        weather: FarmWeather,
    ): Boolean {
        val open = state.weatherOrders.filter { it.progress < it.target }.sanitizedOpen()
        val preferring = categoriesPreferring(weather)
        if (preferring.isEmpty()) {
            if (open.size == state.weatherOrders.size) return false
            state.weatherOrders = open
            return true
        }
        if (open.any { it.weatherEnum() == weather }) {
            if (open.size == state.weatherOrders.size) return false
            state.weatherOrders = open
            return true
        }
        val slots = FARM_ORDER_MAX - open.size
        if (slots <= 0) {
            if (open.size == state.weatherOrders.size) return false
            state.weatherOrders = open
            return true
        }
        val target =
            (FARM_ORDER_TARGET_MIN + state.completedSeasons)
                .coerceIn(FARM_ORDER_TARGET_MIN, FARM_ORDER_TARGET_MAX)
        val reward = 15 + target * 5
        val added =
            preferring.take(slots).map { cat ->
                FarmWeatherOrder(
                    weather = weather.name,
                    category = cat.name,
                    target = target,
                    progress = 0,
                    rewardCoins = reward,
                )
            }
        state.weatherOrders = open + added
        return true
    }

    private fun List<FarmWeatherOrder>.sanitizedOpen(): List<FarmWeatherOrder> {
        val seen = mutableSetOf<String>()
        return mapNotNull { order ->
            val w = order.weatherEnum() ?: return@mapNotNull null
            val c = order.categoryEnum() ?: return@mapNotNull null
            val key = "${w.name}:${c.name}"
            if (!seen.add(key)) return@mapNotNull null
            order
        }
    }
}

internal fun categoriesPreferring(weather: FarmWeather): List<CropCategory> =
    CropCategory.entries.filter { it.preferredWeather() == weather }

/** 收获推进匹配天气的订单；完成后发奖并移除。返回本次发放金币。 */
internal fun applyHarvestToOrders(
    state: FarmState,
    crop: CropDef,
    weather: FarmWeather,
): Int {
    if (state.weatherOrders.isEmpty()) return 0
    var coins = 0
    val next = mutableListOf<FarmWeatherOrder>()
    for (order in state.weatherOrders) {
        val match = order.weatherEnum() == weather && order.categoryEnum() == crop.category
        if (!match || order.progress >= order.target) {
            if (order.progress < order.target) next += order
            continue
        }
        val progress = (order.progress + 1).coerceAtMost(order.target)
        if (progress >= order.target) {
            coins += order.rewardCoins
        } else {
            next += order.copy(progress = progress)
        }
    }
    if (next != state.weatherOrders || coins > 0) {
        state.weatherOrders = next
        if (coins > 0) state.coins += coins
    }
    return coins
}
