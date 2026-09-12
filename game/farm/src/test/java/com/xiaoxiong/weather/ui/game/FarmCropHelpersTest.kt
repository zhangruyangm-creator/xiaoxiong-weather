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
}
