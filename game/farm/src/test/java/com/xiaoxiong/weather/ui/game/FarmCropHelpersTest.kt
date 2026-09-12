package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmCropHelpersTest {
    @Test
    fun farmCropCategories_startsWithAllOption() {
        val categories = farmCropCategories()
        assertNull(categories.first())
    }

    @Test
    fun farmCropCategories_hasNoDuplicateEntries() {
        val categories = farmCropCategories()
        assertEquals(categories.size, categories.distinct().size)
    }

    @Test
    fun farmCropCategories_coversEveryCropCategory() {
        val categories = farmCropCategories().filterNotNull().toSet()
        assertTrue(categories.containsAll(FARM_CROPS.map { it.category }.toSet()))
    }

    @Test
    fun farmUnlockPrice_isFiveTimesSeedCost() {
        val radish = requireNotNull(cropById("radish"))
        assertEquals(40, farmUnlockPrice(radish))
    }

    @Test
    fun farmUnlockPrice_matchesEveryCropSeedCost() {
        FARM_CROPS.forEach { crop ->
            assertEquals("${crop.id} unlock price mismatch", crop.seedCost * 5, farmUnlockPrice(crop))
        }
    }

    @Test
    fun farmCrops_hasExactly128Entries() {
        assertEquals("作物数量应为 128（与注释/图鉴一致）", 128, FARM_CROPS.size)
    }

    @Test
    fun preferredWeatherOf_mapsCategoriesToWeather() {
        assertEquals(FarmWeather.RAINY, preferredWeatherOf("谷物"))
        assertEquals(FarmWeather.RAINY, preferredWeatherOf("香草"))
        assertEquals(FarmWeather.SUNNY, preferredWeatherOf("蔬菜"))
        assertEquals(FarmWeather.SUNNY, preferredWeatherOf("水果"))
        assertEquals(FarmWeather.CLOUDY, preferredWeatherOf("花卉"))
        assertEquals(FarmWeather.FOGGY, preferredWeatherOf("菇菌"))
        assertNull("其他类别无特别偏好", preferredWeatherOf("其他"))
        assertEquals(FarmWeather.SUNNY, requireNotNull(cropById("radish")).preferredWeather)
        assertTrue(requireNotNull(cropById("mushroom")).prefers(FarmWeather.FOGGY))
    }
}
