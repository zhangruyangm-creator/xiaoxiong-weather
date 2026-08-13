package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmBalanceTest {
    @Test
    fun earlyCrops_areNotScaled() {
        val radish = requireNotNull(cropById("radish"))
        assertEquals(8, radish.seedCost)
        assertEquals(18, radish.sellPrice)
        assertEquals(7.5f, radish.totalHours, 1e-4f)
    }

    @Test
    fun lateCrops_costMoreAndGrowSlower() {
        // 中后期水果段（index 90+）：种子变贵、生长变慢
        val mango = requireNotNull(cropById("mango"))
        assertTrue("mango cost should rise, got ${mango.seedCost}", mango.seedCost > 34)
        assertTrue("mango hours should rise, got ${mango.totalHours}", mango.totalHours > 30f)

        // 茶/咖啡段（index 104+）
        val coffee = requireNotNull(cropById("coffee"))
        assertTrue("coffee cost should rise, got ${coffee.seedCost}", coffee.seedCost > 36)
        assertTrue("coffee hours should rise, got ${coffee.totalHours}", coffee.totalHours > 31f)

        // 末尾段（index 118+，竹子等）
        val bamboo = requireNotNull(cropById("bamboo"))
        assertTrue("bamboo cost should rise, got ${bamboo.seedCost}", bamboo.seedCost > 40)
        assertTrue("bamboo hours should rise, got ${bamboo.totalHours}", bamboo.totalHours > 37.5f)
    }

    @Test
    fun allCrops_keepPositiveMargin() {
        FARM_CROPS.forEach { crop ->
            assertTrue(
                "${crop.id}: sell ${crop.sellPrice} should exceed seed cost ${crop.seedCost}",
                crop.sellPrice > crop.seedCost,
            )
        }
    }

    @Test
    fun sellBonus_isHigherOnSunnyAndRainy() {
        assertEquals(1.15f, farmSellBonus(FarmWeather.SUNNY), 1e-4f)
        assertEquals(1.05f, farmSellBonus(FarmWeather.RAINY), 1e-4f)
        assertEquals(1f, farmSellBonus(FarmWeather.CLOUDY), 1e-4f)
        assertEquals(1f, farmSellBonus(FarmWeather.SNOWY), 1e-4f)
    }

    @Test
    fun sellPrice_appliesWeatherBonus() {
        val radish = requireNotNull(cropById("radish"))
        assertEquals(18, sellPriceOf(radish, FarmWeather.CLOUDY))
        // 18 × 1.15 = 20.7 → 21
        assertEquals(21, sellPriceOf(radish, FarmWeather.SUNNY))
        // 18 × 1.05 = 18.9 → 19
        assertEquals(19, sellPriceOf(radish, FarmWeather.RAINY))
    }
}
