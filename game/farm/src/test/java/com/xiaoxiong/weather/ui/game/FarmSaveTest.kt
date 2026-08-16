package com.xiaoxiong.weather.ui.game

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
