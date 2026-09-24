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
    fun everyCropCategory_hasDistinctLabelResource() {
        val labels = CropCategory.entries.map { it.labelRes() }
        assertEquals("分类标签资源不能重复，否则图鉴筛选会出现同名项", labels.size, labels.distinct().size)
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
    fun cropCategory_preferredWeather_mapsCategoriesToWeather() {
        assertEquals(FarmWeather.RAINY, CropCategory.GRAIN.preferredWeather())
        assertEquals(FarmWeather.RAINY, CropCategory.HERB.preferredWeather())
        assertEquals(FarmWeather.SUNNY, CropCategory.VEGETABLE.preferredWeather())
        assertEquals(FarmWeather.SUNNY, CropCategory.FRUIT.preferredWeather())
        assertEquals(FarmWeather.CLOUDY, CropCategory.FLOWER.preferredWeather())
        assertEquals(FarmWeather.FOGGY, CropCategory.MUSHROOM.preferredWeather())
        assertNull("其他类别无特别偏好", CropCategory.OTHER.preferredWeather())
        assertEquals(FarmWeather.SUNNY, requireNotNull(cropById("radish")).preferredWeather)
        assertTrue(requireNotNull(cropById("mushroom")).prefers(FarmWeather.FOGGY))
    }
}
