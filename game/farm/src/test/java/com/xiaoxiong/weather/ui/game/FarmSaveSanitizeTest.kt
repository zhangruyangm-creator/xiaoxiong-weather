package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FarmSave.sanitized] 的守卫测试。
 *
 * 背景：JSON 解析层拒掉 NaN/Infinity，但**有限但越界**的值能进来——
 * 手改档、跨版本删种、写入中断都会留下负数金币、stage=99、moisture=50
 * 以及已不存在的作物 id。
 */
class FarmSaveSanitizeTest {
    private fun plot(
        cropId: String? = "radish",
        stage: Int = 2,
        stageProgress: Float = 0.5f,
        moisture: Float = 0.5f,
    ) = FarmPlotState(
        cropId = cropId,
        stage = stage,
        stageProgress = stageProgress,
        moisture = moisture,
    )

    @Test
    fun negativeCoins_areClampedToZero() {
        assertEquals(0, FarmSave(coins = -999).sanitized().coins)
    }

    @Test
    fun negativeCounters_areClampedToZero() {
        val clean =
            FarmSave(
                harvestedTotal = -5,
                completedSeasons = -2,
                lifetimeHarvests = -7,
            ).sanitized()
        assertEquals(0, clean.harvestedTotal)
        assertEquals(0, clean.completedSeasons)
        assertEquals(0, clean.lifetimeHarvests)
    }

    @Test
    fun outOfRangeStage_isClamped() {
        val clean = FarmSave(plots = listOf(plot(stage = 99))).sanitized()
        assertEquals(5, clean.plots.first().stage)
    }

    @Test
    fun negativeStage_isClamped() {
        val clean = FarmSave(plots = listOf(plot(stage = -3))).sanitized()
        assertEquals(0, clean.plots.first().stage)
    }

    @Test
    fun outOfRangeMoistureAndProgress_areClamped() {
        val clean =
            FarmSave(plots = listOf(plot(moisture = 50f, stageProgress = -2f))).sanitized()
        assertEquals(1f, clean.plots.first().moisture, 1e-6f)
        assertEquals(0f, clean.plots.first().stageProgress, 1e-6f)
    }

    @Test
    fun nonFiniteMoisture_fallsBackToDefault() {
        val clean = FarmSave(plots = listOf(plot(moisture = Float.NaN))).sanitized()
        assertEquals(0.5f, clean.plots.first().moisture, 1e-6f)
    }

    @Test
    fun unknownCropId_becomesEmptyPlot() {
        val clean = FarmSave(plots = listOf(plot(cropId = "deleted_crop", stage = 3))).sanitized()
        val restored = clean.plots.first()
        assertNull(restored.cropId)
        assertEquals("无作物的地应是空地", 0, restored.stage)
    }

    @Test
    fun plotCount_isPaddedToGridSize() {
        val clean = FarmSave(plots = listOf(plot(), plot())).sanitized()
        assertEquals(FARM_PLOT_COUNT, clean.plots.size)
    }

    @Test
    fun extraPlots_areTruncatedToGridSize() {
        val clean = FarmSave(plots = List(FARM_PLOT_COUNT + 5) { plot() }).sanitized()
        assertEquals(FARM_PLOT_COUNT, clean.plots.size)
    }

    @Test
    fun unknownUnlockedCropIds_areDropped() {
        val clean =
            FarmSave(unlockedCropIds = listOf("radish", "gone_forever", "wheat")).sanitized()
        assertEquals(listOf("radish", "wheat"), clean.unlockedCropIds)
    }

    @Test
    fun allUnknownUnlockedIds_fallBackToDefaults() {
        val clean = FarmSave(unlockedCropIds = listOf("a", "b")).sanitized()
        assertEquals(FARM_DEFAULT_UNLOCKED, clean.unlockedCropIds)
    }

    @Test
    fun unknownIdsInCountsAndInventory_areDropped() {
        val clean =
            FarmSave(
                harvestCounts = mapOf("radish" to 3, "ghost" to 9),
                inventory = mapOf("wheat" to 2, "ghost" to 5),
            ).sanitized()
        assertEquals(mapOf("radish" to 3), clean.harvestCounts)
        assertEquals(mapOf("wheat" to 2), clean.inventory)
    }

    @Test
    fun nonPositiveCounts_areDropped() {
        val clean =
            FarmSave(
                harvestCounts = mapOf("radish" to 0, "wheat" to -4),
                inventory = mapOf("tomato" to -1),
            ).sanitized()
        assertTrue(clean.harvestCounts.isEmpty())
        assertTrue(clean.inventory.isEmpty())
    }

    @Test
    fun unknownIdsInSeasonCollected_areDropped() {
        val clean = FarmSave(seasonCollected = setOf("radish", "ghost")).sanitized()
        assertEquals(setOf("radish"), clean.seasonCollected)
    }

    @Test
    fun saneSave_isUnchanged() {
        val save =
            FarmSave(
                coins = 1234,
                plots = List(FARM_PLOT_COUNT) { plot(stage = 3, stageProgress = 0.4f, moisture = 0.8f) },
                unlockedCropIds = listOf("radish", "wheat"),
                harvestedTotal = 42,
                harvestCounts = mapOf("radish" to 7),
                completedSeasons = 2,
                lifetimeHarvests = 99,
                schemaVersion = 2,
                inventory = mapOf("radish" to 5),
                seasonCollected = setOf("radish", "wheat"),
            )
        val clean = save.sanitized()
        assertEquals(save.coins, clean.coins)
        assertEquals(save.plots.map { it.stage }, clean.plots.map { it.stage })
        assertEquals(save.harvestCounts, clean.harvestCounts)
        assertEquals(save.inventory, clean.inventory)
        assertEquals(save.seasonCollected, clean.seasonCollected)
        assertEquals(save.schemaVersion, clean.schemaVersion)
    }

    @Test
    fun absurdCompletedSeasons_isClampedBeforeAnyMultiplication() {
        val clean = FarmSave(completedSeasons = 536_870_912).sanitized()
        assertEquals(MAX_FARM_SEASONS_STORED, clean.completedSeasons)
    }

    @Test
    fun seasonGoal_staysPositiveForOverflowingSeasonsCount() {
        val state = FarmState(completedSeasons = 536_870_912)
        assertTrue(
            "季节目标为负会让 seasonCollectedCount >= seasonGoal 恒真，可无成本连开新季",
            state.seasonGoal > 0,
        )
        assertEquals(40, state.seasonGoal)
    }

    @Test
    fun seasonGrowthPercent_neverGoesNegative() {
        assertEquals(MAX_SEASON_BONUS_PERCENT, seasonGrowthPercent(536_870_912))
        assertEquals(MAX_SEASON_BONUS_PERCENT, seasonGrowthPercent(Int.MAX_VALUE))
        assertEquals(0, seasonGrowthPercent(-5))
    }

    @Test
    fun fromSave_appliesSanitizing() {
        val state =
            FarmState.fromSave(
                save =
                    FarmSave(
                        coins = -100,
                        plots = listOf(plot(cropId = "ghost", stage = 42)),
                        unlockedCropIds = listOf("ghost_only"),
                    ),
                backfillUnlockedAsCollected = false,
            )
        assertEquals(0, state.coins)
        assertNull(state.plots.first().cropId)
        assertNotNull(state.unlocked)
        assertTrue(state.unlocked.contains("radish"))
    }
}
