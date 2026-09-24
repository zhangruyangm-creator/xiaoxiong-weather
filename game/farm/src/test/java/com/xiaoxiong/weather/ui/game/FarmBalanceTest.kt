package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.roundToInt

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
    fun everyCrop_hasDescription() {
        FARM_CROPS.forEach { crop ->
            assertTrue(
                "${crop.id} 缺少中文介绍",
                cropDisplayDescription(crop, Locale.CHINA).isNotBlank(),
            )
        }
    }

    @Test
    fun everyCrop_hasEnglishNameAndDescription() {
        FARM_CROPS.forEach { crop ->
            val enName = cropDisplayName(crop, Locale.ENGLISH)
            assertTrue("${crop.id} 缺少英文名", enName.isNotBlank())
            assertTrue(
                "${crop.id} 英文名与中文名相同，疑似漏翻译",
                enName != crop.nameZh,
            )
            assertTrue(
                "${crop.id} 缺少英文介绍",
                cropDisplayDescription(crop, Locale.ENGLISH).isNotBlank(),
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
        // 无喜好天气的作物（bamboo 属「其他」）只吃通用加成
        val plain = FARM_CROPS.first { it.preferredWeather == null }
        assertEquals(plain.sellPrice, sellPriceOf(plain, FarmWeather.CLOUDY))
        assertEquals((plain.sellPrice * 1.15f).roundToInt(), sellPriceOf(plain, FarmWeather.SUNNY))
        assertEquals((plain.sellPrice * 1.05f).roundToInt(), sellPriceOf(plain, FarmWeather.RAINY))
    }

    @Test
    fun sellPrice_preferredWeatherAddsBonus() {
        // 谷物（小麦）喜好雨天：1.25 高于通用 1.05，雨天售价确有喜好收益
        val wheat = requireNotNull(cropById("wheat"))
        val cloudy = sellPriceOf(wheat, FarmWeather.CLOUDY)
        val rainy = sellPriceOf(wheat, FarmWeather.RAINY)
        assertTrue("rainy $rainy should exceed cloudy $cloudy", rainy > cloudy)
        assertEquals((wheat.sellPrice * 1.25f).roundToInt(), rainy)

        // 喜晴的萝卜在晴天：1.25 > 通用 1.15，不再被通用加成吞掉
        val radish = requireNotNull(cropById("radish"))
        val sunny = sellPriceOf(radish, FarmWeather.SUNNY)
        assertEquals((radish.sellPrice * 1.25f).roundToInt(), sunny)
        assertTrue(
            "sunny $sunny should exceed cloudy ${sellPriceOf(radish, FarmWeather.CLOUDY)}",
            sunny > sellPriceOf(radish, FarmWeather.CLOUDY),
        )
    }

    @Test
    fun bestSellBonusOf_matchesInventoryPreferredCrops() {
        // 空背包只吃通用加成
        assertEquals(1f, bestSellBonusOf(emptyList(), FarmWeather.CLOUDY), 1e-4f)
        assertEquals(1.15f, bestSellBonusOf(emptyList(), FarmWeather.SUNNY), 1e-4f)
        // 任一作物喜当前天气 → 顶部显示喜好加成（与卡片单价一致）
        assertEquals(1f + CROP_PREFERRED_SELL_BONUS, bestSellBonusOf(listOf("radish"), FarmWeather.SUNNY), 1e-4f)
        assertEquals(1f + CROP_PREFERRED_SELL_BONUS, bestSellBonusOf(listOf("moss", "wheat"), FarmWeather.RAINY), 1e-4f)
        // 作物不喜欢当前天气则不加成
        assertEquals(1f, bestSellBonusOf(listOf("radish"), FarmWeather.CLOUDY), 1e-4f)
        assertEquals(1.05f, bestSellBonusOf(listOf("moss"), FarmWeather.RAINY), 1e-4f)
    }
}
