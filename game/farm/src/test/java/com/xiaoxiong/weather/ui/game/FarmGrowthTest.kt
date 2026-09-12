package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmGrowthTest {
    private val sunnyFx = farmWeatherEffect(FarmWeather.SUNNY)
    private val radish = requireNotNull(cropById("radish"))

    @Test
    fun advanceGrowth_addsProgressWithinStage() {
        val plot = FarmPlotState(cropId = "radish", stage = 1, moisture = 1f)
        // 萝卜第一阶段 2h；晴天、湿润、无周目加成：2h → 2.5 倍进度 → 升入发芽并余 0.25
        advanceGrowth(plot, radish, hours = 2f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(2, plot.stage)
        assertEquals(0.25f, plot.stageProgress, 1e-4f)
    }

    @Test
    fun advanceGrowth_canJumpMultipleStages() {
        val plot = FarmPlotState(cropId = "radish", stage = 1, moisture = 1f)
        advanceGrowth(plot, radish, hours = 4f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(3, plot.stage)
        assertEquals(0.5f, plot.stageProgress, 1e-4f)
    }

    @Test
    fun advanceGrowth_isBlockedByWeedBugOrDrySoil() {
        val weedy = FarmPlotState(cropId = "radish", stage = 2, moisture = 1f, weed = true)
        advanceGrowth(weedy, radish, hours = 10f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(2, weedy.stage)
        assertEquals(0f, weedy.stageProgress, 1e-4f)

        val buggy = FarmPlotState(cropId = "radish", stage = 2, moisture = 1f, bug = true)
        advanceGrowth(buggy, radish, hours = 10f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(0f, buggy.stageProgress, 1e-4f)

        val dry = FarmPlotState(cropId = "radish", stage = 2, moisture = 0.2f)
        advanceGrowth(dry, radish, hours = 10f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(0f, dry.stageProgress, 1e-4f)
    }

    @Test
    fun advanceGrowth_ignoresEmptyAndWilted() {
        val empty = FarmPlotState(cropId = null, stage = 0)
        advanceGrowth(empty, radish, hours = 10f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(0, empty.stage)

        val wilted = FarmPlotState(cropId = "radish", stage = 5)
        advanceGrowth(wilted, radish, hours = 10f, fx = sunnyFx, seasonMul = 1f)
        assertEquals(5, wilted.stage)
    }

    @Test
    fun advanceGrowth_preferredWeatherGrowsFaster() {
        val base = FarmPlotState(cropId = "radish", stage = 1, moisture = 1f)
        val preferred = FarmPlotState(cropId = "radish", stage = 1, moisture = 1f)
        // 萝卜喜好晴天
        advanceGrowth(base, radish, hours = 1f, fx = sunnyFx, seasonMul = 1f, preferred = false)
        advanceGrowth(preferred, radish, hours = 1f, fx = sunnyFx, seasonMul = 1f, preferred = true)
        assertTrue(
            "喜好天气进度 ${preferred.stageProgress} 应高于普通 ${base.stageProgress}",
            preferred.stageProgress > base.stageProgress,
        )
    }
}
