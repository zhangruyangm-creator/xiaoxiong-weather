package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FarmSimulatorTest {
    /** 始终返回接近 1 的随机数：杂草/害虫/枯萎概率都不会触发，便于确定性断言。 */
    private val noEvents =
        object : Random() {
            override fun nextBits(bitCount: Int): Int = (1 shl bitCount) - 1
        }

    private val hourMs = 3_600_000L
    private val now = 1_700_000_000_000L

    // ---- tick：水分变化是确定性的 ----

    @Test
    fun tick_sunnyEvaporatesMoisture() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now - hourMs) })
        val plot = state.plots.first()
        plot.moisture = 1f
        plot.cropId = "radish"
        plot.stage = 1

        FarmSimulator.tick(state, now, FarmWeather.SUNNY, noEvents)

        // 晴天 1 小时：1.0 - 0.22
        assertEquals(0.78f, state.plots.first().moisture, 1e-3f)
        // 生长确实发生（未长草/虫，未干旱）
        assertTrue(state.plots.first().stageProgress > 0.3f)
    }

    @Test
    fun tick_rainyReplenishesMoisture() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now - hourMs) })
        state.plots.first().apply {
            moisture = 0.5f
            cropId = "radish"
            stage = 1
        }

        FarmSimulator.tick(state, now, FarmWeather.RAINY, noEvents)

        // 雨天 1 小时：0.5 - 0.02 + 0.35
        assertEquals(0.83f, state.plots.first().moisture, 1e-3f)
    }

    @Test
    fun tick_offlineCatchUpIsCappedAtEightHours() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now - 3L * 24 * hourMs) })
        state.plots.first().apply {
            moisture = 1f
            cropId = "radish"
            stage = 1
        }

        FarmSimulator.tick(state, now, FarmWeather.SUNNY, noEvents)

        // 只补 8 小时：1.0 - 0.22 * 8 < 0 → 收敛到 0；lastTickMs 归位到 now
        assertEquals(0f, state.plots.first().moisture, 1e-3f)
        assertEquals(now, state.plots.first().lastTickMs)
    }

    @Test
    fun tick_clockRollbackConvergesLastTick() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now + 100_000L) })

        FarmSimulator.tick(state, now, FarmWeather.SUNNY, noEvents)

        assertEquals(now, state.plots.first().lastTickMs)
    }

    @Test
    fun tick_weedBlocksGrowth() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = now - hourMs) })
        state.plots.first().apply {
            moisture = 1f
            cropId = "radish"
            stage = 2
            weed = true
        }

        FarmSimulator.tick(state, now, FarmWeather.SUNNY, noEvents)

        assertEquals(2, state.plots.first().stage)
        assertEquals(0f, state.plots.first().stageProgress, 1e-4f)
    }

    // ---- 种植 / 收获 ----

    @Test
    fun plant_deductsCoinsAndSeedsPlot() {
        val state = FarmState(coins = 100)

        val effect = FarmSimulator.plant(state, 0, "radish")

        assertEquals(R.string.bear_farm_toast_planted, effect.toastRes)
        assertEquals(92, state.coins)
        assertEquals("radish", state.plots[0].cropId)
        assertEquals(1, state.plots[0].stage)
    }

    @Test
    fun plant_insufficientCoinsReturnsError() {
        val state = FarmState(coins = 5)

        val effect = FarmSimulator.plant(state, 0, "radish")

        assertEquals(R.string.bear_farm_toast_need_coins, effect.toastRes)
        assertEquals(5, state.coins)
        assertNull(state.plots[0].cropId)
    }

    @Test
    fun harvest_putsCropIntoInventoryAndUnlocksNext() {
        val state =
            FarmState(
                coins = 50,
                harvestedTotal = 1, // 差一次收获到解锁阈值（need=2）
                plots = List(FARM_PLOT_COUNT) { FarmPlotState() },
            )
        state.plots = state.plots.mapIndexed { i, p -> if (i == 0) p.copy(cropId = "radish", stage = 4) else p }

        val effect = FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)

        assertEquals(R.string.bear_farm_toast_harvest, effect.toastRes)
        assertEquals(listOf("cucumber"), effect.unlockCropIds)
        assertTrue("cucumber" in state.unlocked)
        assertEquals(1, state.inventory["radish"])
        assertEquals(1, state.harvestCounts["radish"])
        assertTrue("收获应记入本季已收集", "radish" in state.seasonCollected)
        assertEquals(2, state.harvestedTotal)
        assertEquals(1, state.lifetimeHarvests)
        assertEquals(1, effect.rewards.size)
        // 金币不因收获变化（卖出才得币）
        assertEquals(50, state.coins)
        assertNull(state.plots[0].cropId)
        assertEquals(0, state.plots[0].stage)
    }

    @Test
    fun harvest_notRipeIsRejected() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(cropId = "radish", stage = 2) })

        val effect = FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)

        assertEquals(R.string.bear_farm_toast_not_ripe, effect.toastRes)
        assertEquals(0, state.inventory.size)
    }

    // ---- 出售 ----

    @Test
    fun sellAll_appliesWeatherBonusAndClearsItem() {
        val state =
            FarmState(
                coins = 0,
                inventory = mapOf("radish" to 3),
            )

        val effect = FarmSimulator.sellAll(state, "radish", FarmWeather.SUNNY)

        // 萝卜喜晴：18 × 1.25 = 22.5 → 23；×3 = 69
        assertEquals(R.string.bear_farm_toast_sell, effect.toastRes)
        assertEquals(69, state.coins)
        assertFalse("radish" in state.inventory)
    }

    @Test
    fun sellAllInventory_sumsEverything() {
        val state =
            FarmState(
                coins = 0,
                inventory = mapOf("radish" to 2, "bamboo" to 1),
            )

        val effect = FarmSimulator.sellAllInventory(state, FarmWeather.CLOUDY)

        // 萝卜 18×2 + 竹子 242 = 278
        assertEquals(R.string.bear_farm_toast_sell_all, effect.toastRes)
        assertEquals(278, state.coins)
        assertTrue(state.inventory.isEmpty())
    }

    // ---- 一键照料 / 新一季 ----

    @Test
    fun careAll_followsPriorityAndSummarizes() {
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState() })
        state.plots =
            state.plots.mapIndexed { i, p ->
                when (i) {
                    0 -> p.copy(cropId = "radish", stage = 2, bug = true)
                    1 -> p.copy(cropId = "radish", stage = 2, weed = true)
                    2 -> p.copy(cropId = "radish", stage = 4)
                    3 -> p.copy(cropId = "radish", stage = 2, moisture = 0.2f)
                    4 -> p.copy(cropId = "radish", stage = 5)
                    else -> p
                }
            }

        val effect = FarmSimulator.careAll(state, FarmWeather.SUNNY)

        assertEquals(R.string.bear_farm_care_all_summary, effect.toastRes)
        assertEquals(listOf<Any>(1, 1, 1, 1, 1), effect.toastArgs)
        assertEquals(1, effect.rewards.size)
        assertFalse(state.plots[0].bug)
        assertFalse(state.plots[1].weed)
        assertEquals(0, state.plots[2].stage)
        assertTrue(state.plots[3].moisture > 0.5f)
        assertEquals(0, state.plots[4].stage)
    }

    @Test
    fun careAll_collectsEveryUnlockedCrop() {
        val state = FarmState(harvestedTotal = 1, plots = List(FARM_PLOT_COUNT) { FarmPlotState() })
        // 三块熟地：收获计数 2/3/4，分别在 unlocked=4 的阈值 2、5 的阈值 4 各解锁一种
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i < 3) p.copy(cropId = "radish", stage = 4) else p
            }

        val effect = FarmSimulator.careAll(state, FarmWeather.SUNNY)

        assertEquals(3, effect.rewards.size)
        assertEquals(listOf("cucumber", "carrot"), effect.unlockCropIds)
        assertTrue("cucumber" in state.unlocked)
        assertTrue("carrot" in state.unlocked)
    }

    @Test
    fun lifetimeHarvests_countsAcrossSeasons() {
        val state = FarmState(coins = 100)
        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == 0) p.copy(cropId = "radish", stage = 4) else p
            }
        FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)
        assertEquals(1, state.harvestedTotal)
        assertEquals(1, state.lifetimeHarvests)

        // 达成开季门槛后开新一季：本季计数清零，累计保留
        state.seasonCollected = FARM_CROPS.take(12).map { it.id }.toSet()
        FarmSimulator.startNewSeason(state)
        assertEquals(0, state.harvestedTotal)
        assertEquals(1, state.lifetimeHarvests)

        state.plots =
            state.plots.mapIndexed { i, p ->
                if (i == 0) p.copy(cropId = "wheat", stage = 4) else p
            }
        FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)
        assertEquals(1, state.harvestedTotal)
        assertEquals(2, state.lifetimeHarvests)
    }

    @Test
    fun startNewSeason_resetsButKeepsCollectionAndBag() {
        val inventory = mapOf("radish" to 5)
        val counts = FARM_CROPS.associate { it.id to 1 }
        val state =
            FarmState(
                coins = 999,
                inventory = inventory,
                harvestCounts = counts,
                harvestedTotal = 500,
                lifetimeHarvests = 600,
                completedSeasons = 1,
                seasonCollected = counts.keys,
            )

        val effect = FarmSimulator.startNewSeason(state)

        assertEquals(R.string.bear_farm_new_season_started, effect.toastRes)
        assertEquals(80, state.coins)
        assertEquals(0, state.harvestedTotal)
        assertEquals(2, state.completedSeasons)
        assertEquals(setOf("radish", "cabbage", "wheat", "tomato"), state.unlocked)
        assertEquals(FARM_PLOT_COUNT, state.plots.size)
        assertEquals(inventory, state.inventory)
        assertEquals(counts, state.harvestCounts)
        assertEquals(600, state.lifetimeHarvests)
        assertTrue("新一季应清空本季收集", state.seasonCollected.isEmpty())
        assertEquals(0, state.seasonCollectedCount)
    }

    @Test
    fun startNewSeason_secondCallIsRejectedUntilRecollected() {
        val all = FARM_CROPS.map { it.id }.toSet()
        val state =
            FarmState(
                harvestCounts = all.associateWith { 1 },
                seasonCollected = all,
            )

        FarmSimulator.startNewSeason(state)
        assertEquals(1, state.completedSeasons)

        // 图鉴仍齐全，但本季尚未收获 → 不能连续刷赛季加成
        val second = FarmSimulator.startNewSeason(state)
        assertNull(second.toastRes)
        assertEquals(1, state.completedSeasons)
    }

    @Test
    fun startNewSeason_requiresFullCollection() {
        val state = FarmState(coins = 500)

        val effect = FarmSimulator.startNewSeason(state)

        assertNull(effect.toastRes)
        assertEquals(500, state.coins)
        assertEquals(0, state.completedSeasons)
    }

    @Test
    fun startNewSeason_usesInjectedClockForFreshPlots() {
        val ids = FARM_CROPS.map { it.id }
        val state = FarmState(seasonCollected = ids.take(12).toSet(), completedSeasons = 0)

        val effect = FarmSimulator.startNewSeason(state, now = 1_234_567L)

        assertEquals(R.string.bear_farm_new_season_started, effect.toastRes)
        assertEquals(FARM_PLOT_COUNT, state.plots.size)
        assertTrue("新一季田地时间基准应使用注入时钟", state.plots.all { it.lastTickMs == 1_234_567L })
    }

    @Test
    fun seasonGoal_growsWithSeasonsAndCaps() {
        assertEquals(12, FarmState(completedSeasons = 0).seasonGoal)
        assertEquals(16, FarmState(completedSeasons = 1).seasonGoal)
        assertEquals(28, FarmState(completedSeasons = 4).seasonGoal)
        assertEquals(40, FarmState(completedSeasons = 7).seasonGoal)
        assertEquals(40, FarmState(completedSeasons = 99).seasonGoal)
    }

    @Test
    fun startNewSeason_requiresSeasonGoalNotFullCollection() {
        val ids = FARM_CROPS.map { it.id }
        val state = FarmState(seasonCollected = ids.take(12).toSet())

        assertTrue("12 种应达成本季目标", state.seasonReady)
        assertEquals(12, state.seasonGoal)

        val effect = FarmSimulator.startNewSeason(state)

        assertEquals(R.string.bear_farm_new_season_started, effect.toastRes)
        assertEquals(1, state.completedSeasons)
        assertTrue(state.seasonCollected.isEmpty())
        assertEquals(16, state.seasonGoal)
    }

    @Test
    fun harvest_lastMissingCropMakesAllCollected() {
        val crops = FARM_CROPS.map { it.id }
        val last = crops.last()
        val state =
            FarmState(
                plots = List(FARM_PLOT_COUNT) { FarmPlotState() },
                seasonCollected = (crops - last).toSet(),
            )
        state.plots = state.plots.mapIndexed { i, p -> if (i == 0) p.copy(cropId = last, stage = 4) else p }

        assertFalse(state.allCollected)
        assertEquals(crops.size - 1, state.seasonCollectedCount)
        FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)
        assertTrue("收齐本季全部作物后 allCollected 应为 true", state.allCollected)
        assertEquals(crops.size, state.seasonCollectedCount)
    }

    @Test
    fun tick_forecastSlicesUsePastWeatherNotCurrent() {
        val t0 = now - 2 * hourMs
        val state = FarmState(plots = List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = t0) })
        state.plots.first().apply {
            moisture = 0.5f
            cropId = "radish"
            stage = 1
        }
        val forecast =
            listOf(
                FarmWeatherSlice(t0, FarmWeather.SUNNY),
                FarmWeatherSlice(t0 + hourMs, FarmWeather.RAINY),
            )
        FarmSimulator.tick(state, now, FarmWeather.SUNNY, noEvents, forecast)
        // 1h 晴 -0.22，1h 雨 -0.02+0.35 → 0.61；若整段按当前晴天则 0.5-0.44=0.06
        assertEquals(0.61f, state.plots.first().moisture, 1e-3f)
    }

    @Test
    fun ensureWeatherOrders_rainAddsGrainAndHerb() {
        val state = FarmState()
        assertTrue(FarmSimulator.ensureWeatherOrders(state, FarmWeather.RAINY))
        assertEquals(2, state.weatherOrders.size)
        assertEquals(setOf("GRAIN", "HERB"), state.weatherOrders.map { it.category }.toSet())
        assertFalse("同一天气不重复生成", FarmSimulator.ensureWeatherOrders(state, FarmWeather.RAINY))
    }

    @Test
    fun harvest_matchingOrderPaysAndRemoves() {
        val state =
            FarmState(
                coins = 80,
                plots = List(FARM_PLOT_COUNT) { FarmPlotState() },
                weatherOrders =
                    listOf(
                        FarmWeatherOrder(
                            weather = FarmWeather.SUNNY.name,
                            category = CropCategory.VEGETABLE.name,
                            target = 1,
                            progress = 0,
                            rewardCoins = 20,
                        ),
                    ),
            )
        state.plots = state.plots.mapIndexed { i, p -> if (i == 0) p.copy(cropId = "radish", stage = 4) else p }
        val effect = FarmSimulator.harvest(state, 0, FarmWeather.SUNNY)
        assertEquals(20, effect.orderCoins)
        assertEquals(100, state.coins)
        assertTrue(state.weatherOrders.isEmpty())
    }

    @Test
    fun startNewSeason_clearsWeatherOrders() {
        val state =
            FarmState(
                seasonCollected = FARM_CROPS.take(12).map { it.id }.toSet(),
                weatherOrders =
                    listOf(
                        FarmWeatherOrder(
                            FarmWeather.RAINY.name,
                            CropCategory.GRAIN.name,
                            target = 3,
                            rewardCoins = 30,
                        ),
                    ),
            )
        val effect = FarmSimulator.startNewSeason(state)
        assertEquals(R.string.bear_farm_new_season_started, effect.toastRes)
        assertTrue(state.weatherOrders.isEmpty())
    }
}
