package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertTrue
import org.junit.Test

class FarmCatchUpTest {
    @Test
    fun farmChanceAtLeastOnce_staysBelowOne_forMultiHourCatchUp() {
        val rainy = farmWeatherEffect(FarmWeather.RAINY)
        val stormy = farmWeatherEffect(FarmWeather.STORMY)
        val hours = 8f
        val rainyWeed = farmChanceAtLeastOnce(rainy.weedChancePerHour, hours)
        val stormyBug = farmChanceAtLeastOnce(stormy.bugChancePerHour, hours)
        val wilt = farmChanceAtLeastOnce(0.25f, hours)
        assertTrue("fixed weed p should be < 1: $rainyWeed", rainyWeed < 1f)
        assertTrue("fixed bug p should be < 1: $stormyBug", stormyBug < 1f)
        assertTrue("fixed wilt p should be < 1: $wilt", wilt < 1f)
        assertTrue("legacy p*hours still saturates", rainy.weedChancePerHour * hours >= 1f)
        assertTrue("8h rainy weed should still be high: $rainyWeed", rainyWeed > 0.6f)
    }
}
