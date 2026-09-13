package com.xiaoxiong.weather.ui.game

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmSaveTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun oldSave_withoutNewFields_decodesWithDefaults() {
        val old = """{"coins":120,"plots":[],"unlockedCropIds":["radish"],"harvestedTotal":5}"""
        val s = json.decodeFromString(FarmSave.serializer(), old)
        assertEquals(120, s.coins)
        assertEquals(5, s.harvestedTotal)
        assertTrue(s.harvestCounts.isEmpty())
        assertEquals(0, s.completedSeasons)
        assertEquals(0, s.lifetimeHarvests)
        assertEquals(1, s.schemaVersion)
        assertTrue(s.inventory.isEmpty())
        assertTrue("旧存档无本季收集字段应默认空集", s.seasonCollected.isEmpty())
    }

    @Test
    fun roundTrip_preservesCatalogAndSeasonFields() {
        val s =
            FarmSave(
                coins = 88,
                plots = listOf(FarmPlotState(cropId = "radish", stage = 3)),
                unlockedCropIds = listOf("radish"),
                harvestedTotal = 9,
                harvestCounts = mapOf("radish" to 6, "tomato" to 3),
                completedSeasons = 2,
                lifetimeHarvests = 300,
                schemaVersion = 2,
                inventory = mapOf("radish" to 4, "bamboo" to 1),
                seasonCollected = setOf("radish", "tomato"),
            )
        val decoded =
            json.decodeFromString(
                FarmSave.serializer(),
                json.encodeToString(FarmSave.serializer(), s),
            )
        assertEquals(mapOf("radish" to 6, "tomato" to 3), decoded.harvestCounts)
        assertEquals(2, decoded.completedSeasons)
        assertEquals(300, decoded.lifetimeHarvests)
        assertEquals(2, decoded.schemaVersion)
        assertEquals(mapOf("radish" to 4, "bamboo" to 1), decoded.inventory)
        assertEquals(setOf("radish", "tomato"), decoded.seasonCollected)
        assertEquals("radish", decoded.plots.single().cropId)
    }

    @Test
    fun seasonGrowthBonus_capsAtTwentyFivePercent() {
        assertEquals(0, seasonGrowthPercent(0))
        assertEquals(5, seasonGrowthPercent(1))
        assertEquals(25, seasonGrowthPercent(5))
        assertEquals(25, seasonGrowthPercent(9))
        assertEquals(1f, seasonGrowthMul(0))
        assertEquals(1.05f, seasonGrowthMul(1))
        assertEquals(1.25f, seasonGrowthMul(5))
        assertEquals(1.25f, seasonGrowthMul(9))
    }

    @Test
    fun oldFullyCollectedSave_backfillsSeasonCollected() {
        val all = FARM_CROPS.map { it.id }
        val save =
            FarmSave(
                harvestCounts = all.associateWith { 1 },
                unlockedCropIds = all,
                harvestedTotal = 500,
                schemaVersion = 1,
            )

        val state = FarmState.fromSave(save, backfillUnlockedAsCollected = true)

        assertTrue("全收集旧档应能立即开新一季", state.seasonReady)
        assertEquals(all.toSet(), state.seasonCollected)
        assertEquals(all.toSet(), state.harvestCounts.keys)
    }

    @Test
    fun oldSaveUnlockedAllWithoutCounts_alsoBackfillsSeasonCollected() {
        // pre-图鉴版本（harvestCounts 尚未记录）只有 unlocked 可判断收集进度
        val all = FARM_CROPS.map { it.id }
        val save = FarmSave(unlockedCropIds = all, harvestCounts = emptyMap(), schemaVersion = 1)

        val state = FarmState.fromSave(save, backfillUnlockedAsCollected = true)

        assertTrue(state.seasonReady)
        assertEquals(all.toSet(), state.seasonCollected)
    }

    @Test
    fun oldPartiallyCollectedSave_doesNotFastForwardSeason() {
        val ids = FARM_CROPS.map { it.id }
        val save =
            FarmSave(
                harvestCounts = ids.take(8).associateWith { 1 },
                unlockedCropIds = ids.take(8),
                harvestedTotal = 40,
                schemaVersion = 1,
            )

        val state = FarmState.fromSave(save, backfillUnlockedAsCollected = true)

        assertTrue("普通老档不应被错误加速", state.seasonCollected.isEmpty())
        assertFalse(state.seasonReady)
        // 图鉴仍按既有逻辑回填
        assertEquals(8, state.harvestCounts.size)
    }

    @Test
    fun currentSchemaSave_keepsSeasonCollectedUntouched() {
        val save =
            FarmSave(
                harvestCounts = FARM_CROPS.associate { it.id to 1 },
                seasonCollected = setOf("radish"),
                schemaVersion = 2,
            )

        val state = FarmState.fromSave(save, backfillUnlockedAsCollected = false)

        assertEquals(setOf("radish"), state.seasonCollected)
    }

    // ── 回档：瞬态 UI 清空 + 选中种子归位 ───────────────────────

    @Test
    fun seedIdAfterLoad_keepsValidSelectionOrFallsBackToUnlocked() {
        // 仍在已解锁集合内：保留原选择
        assertEquals("tomato", seedIdAfterLoad(setOf("radish", "tomato"), "tomato"))
        // 已失效：回落到默认种子
        assertEquals("radish", seedIdAfterLoad(setOf("radish", "tomato"), "bamboo"))
        // 默认种子也没解锁：取任一已解锁作物
        assertEquals("tomato", seedIdAfterLoad(setOf("tomato"), "bamboo"))
        // 极端空集合：仍返回默认种子，避免空串
        assertEquals(DEFAULT_SEED_ID, seedIdAfterLoad(emptySet(), "bamboo"))
    }

    @Test
    fun loadFarmFromSave_clearsTransientUiAndFixesLockedSeed() {
        val save =
            FarmSave(
                coins = 10,
                unlockedCropIds = listOf("radish"),
                plots = listOf(FarmPlotState(cropId = "radish", stage = 2)),
            )

        val loaded =
            loadFarmFromSave(
                save = save,
                currentSelectedSeedId = "bamboo",
                backfillUnlockedAsCollected = false,
            )

        assertNull("回档后 toast 应清空", loaded.ui.toast)
        assertTrue("回档后飘字应清空", loaded.ui.floatingRewards.isEmpty())
        assertNull("回档后解锁卡应清空", loaded.ui.unlockCard)
        assertTrue("回档后解锁队列应清空", loaded.ui.unlockQueue.isEmpty())
        assertEquals("失效种子应归位到已解锁作物", "radish", loaded.ui.selectedSeedId)
        assertEquals(10, loaded.state.coins)
        assertEquals(FARM_PLOT_COUNT, loaded.state.plots.size)
        val firstPlot = loaded.state.plots.first()
        assertEquals("radish", firstPlot.cropId)
    }
}
