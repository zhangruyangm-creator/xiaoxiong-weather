package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LifeIndexGeneratorTest {
    @Test
    fun comfortBand_coolMildHotAndMuggy() {
        assertEquals(
            LifeIndexGenerator.ComfortBand.COOL,
            LifeIndexGenerator.comfortBand(15.0, 50),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DAMP_COLD,
            LifeIndexGenerator.comfortBand(15.0, 90),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DAMP_COLD,
            LifeIndexGenerator.comfortBand(8.0, 80),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MILD,
            LifeIndexGenerator.comfortBand(22.0, 60),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.DRY,
            LifeIndexGenerator.comfortBand(22.0, 20),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.VERY_DRY,
            LifeIndexGenerator.comfortBand(22.0, 12),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MILD,
            LifeIndexGenerator.comfortBand(22.0, 30),
        )
        // 体感不高、湿度很高：闷湿，不算闷热
        assertEquals(
            LifeIndexGenerator.ComfortBand.HUMID,
            LifeIndexGenerator.comfortBand(23.5, 98),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.MUGGY,
            LifeIndexGenerator.comfortBand(28.0, 80),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.SCORCHING,
            LifeIndexGenerator.comfortBand(36.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.SCORCHING,
            LifeIndexGenerator.comfortBand(41.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.EXTREME_HEAT,
            LifeIndexGenerator.comfortBand(46.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.FREEZING,
            LifeIndexGenerator.comfortBand(-3.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.FREEZING,
            LifeIndexGenerator.comfortBand(-12.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.BITTER,
            LifeIndexGenerator.comfortBand(-16.0, 40),
        )
        assertEquals(
            LifeIndexGenerator.ComfortBand.EXTREME_COLD,
            LifeIndexGenerator.comfortBand(-32.0, 40),
        )
    }

    @Test
    fun umbrellaLevel_bands() {
        assertEquals(
            LifeIndexLevel.GOOD,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 0,
                upcomingPrecipSoon = false,
            ),
        )
        assertEquals(
            LifeIndexLevel.FAIR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 0,
                upcomingPrecipSoon = true,
            ),
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = true,
                weatherCode = 61,
                upcomingPrecipSoon = false,
            ),
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.umbrellaLevel(
                rainingNow = false,
                weatherCode = 71,
                upcomingPrecipSoon = false,
            ),
        )
    }

    @Test
    fun carWashLevel_bands() {
        assertEquals(
            LifeIndexLevel.GOOD,
            LifeIndexGenerator.carWashLevel(
                rainingNow = false,
                maxPrecipProb = 10,
                precipSum = 0.0,
                windKmh = 12.0,
            ),
        )
        assertEquals(
            LifeIndexLevel.FAIR,
            LifeIndexGenerator.carWashLevel(
                rainingNow = false,
                maxPrecipProb = 40,
                precipSum = 0.0,
                windKmh = 12.0,
            ),
        )
        assertEquals(
            LifeIndexLevel.POOR,
            LifeIndexGenerator.carWashLevel(
                rainingNow = true,
                maxPrecipProb = 10,
                precipSum = 0.0,
                windKmh = 12.0,
            ),
        )
    }

    @Test
    fun unifiedUsAqi_prefersUsAqi_andScalesEuropean() {
        // US AQI 直接采用
        assertEquals(
            90,
            LifeIndexGenerator.unifiedUsAqi(AirQuality(usAqi = 90, europeanAqi = 100)),
        )
        // 只有欧洲 EAQI 时按 50/35.4 放大（EAQI 100 ≈ US 141）
        assertEquals(
            141,
            LifeIndexGenerator.unifiedUsAqi(AirQuality(europeanAqi = 100)),
        )
        // 均缺失
        assertNull(LifeIndexGenerator.unifiedUsAqi(AirQuality()))
    }

    @Test
    fun effectiveClothingFeels_appliesDampColdCorrectionWhenRawTemp() {
        // 已有体感温度：原样使用
        assertEquals(
            10.0,
            LifeIndexGenerator.effectiveClothingFeels(10.0, 90, isApparentTemperature = true),
        )
        // 退回原始气温 + 高湿：湿冷下调 3°C
        assertEquals(
            7.0,
            LifeIndexGenerator.effectiveClothingFeels(10.0, 90, isApparentTemperature = false),
        )
        // 退回原始气温 + 低湿：不下调
        assertEquals(
            10.0,
            LifeIndexGenerator.effectiveClothingFeels(10.0, 40, isApparentTemperature = false),
        )
        // 湿度缺失按低湿处理，不下调
        assertEquals(
            10.0,
            LifeIndexGenerator.effectiveClothingFeels(10.0, null, isApparentTemperature = false),
        )
    }

    @Test
    fun uvForIndex_daytimeUsesDailyMax_nightUsesCurrent() {
        val today = DailyWeather(date = "2026-08-11", uvIndexMax = 8.0)
        val air = AirQuality(uvIndex = 2.0)
        // 白天优先当日最大，避免早晚用当前低值误报
        assertEquals(8.0, LifeIndexGenerator.uvForIndex(true, air, today))
        // 当日缺失时回退当前值
        assertEquals(
            2.0,
            LifeIndexGenerator.uvForIndex(true, air, DailyWeather(date = "2026-08-11")),
        )
        // 夜间用当前值（夜里几乎为 0）
        assertEquals(2.0, LifeIndexGenerator.uvForIndex(false, air, today))
        // 夜间且当前值缺失
        assertNull(
            LifeIndexGenerator.uvForIndex(
                false,
                AirQuality(),
                DailyWeather(date = "2026-08-11", uvIndexMax = 8.0),
            ),
        )
    }

    @Test
    fun umbrellaPrecip_heavySoonNeedsStrongRain_prob70Within6h() {
        fun hour(
            precip: Double? = null,
            prob: Int? = null,
        ) = HourlyWeather(time = "2026-08-11T12:00", precipitationMm = precip, precipProbability = prob)

        // 6 小时内 ≥0.5mm 才触发「必带」；0.2mm 毛毛雨不触发
        assertEquals(
            true to false,
            LifeIndexGenerator.umbrellaPrecip(
                listOf(hour(precip = 0.5), hour(precip = 0.0)),
                listOf(hour()),
            ),
        )
        assertEquals(
            true to true,
            LifeIndexGenerator.umbrellaPrecip(
                listOf(hour(precip = 0.2, prob = 75)),
                listOf(hour(precip = 0.2)),
            ),
        )
        // 6 小时内 0.4mm/60% 不足以必带；12 小时内 0.2mm 仍算「可带」
        assertEquals(
            false to true,
            LifeIndexGenerator.umbrellaPrecip(
                listOf(hour(precip = 0.4, prob = 60)),
                listOf(hour(precip = 0.2)),
            ),
        )
        // 均无降水
        assertEquals(
            false to false,
            LifeIndexGenerator.umbrellaPrecip(
                listOf(hour()),
                listOf(hour()),
            ),
        )
    }
}
