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

        val effect = FarmSimulator.harvest(state, 0)

        assertEquals(R.string.bear_farm_toast_harvest, effect.toastRes)
        assertEquals("cucumber", effect.unlockCropId)
        assertTrue("cucumber" in state.unlocked)
        assertEquals(1, state.inventory["radish"])
        assertEquals(1, state.harvestCounts["radish"])
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

        val effect = FarmSimulator.harvest(state, 0)

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

        // 18 × 1.15 = 20.7 → 21；×3 = 63
        assertEquals(R.string.bear_farm_toast_sell, effect.toastRes)
        assertEquals(63, state.coins)
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

        val effect = FarmSimulator.careAll(state)

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
    }

    @Test
    fun startNewSeason_requiresFullCollection() {
        val state = FarmState(coins = 500)

        val effect = FarmSimulator.startNewSeason(state)

        assertNull(effect.toastRes)
        assertEquals(500, state.coins)
        assertEquals(0, state.completedSeasons)
    }
}
