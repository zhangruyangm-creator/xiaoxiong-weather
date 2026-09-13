package com.xiaoxiong.weather.util

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AirQuality
import com.xiaoxiong.weather.data.DailyWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WeatherCodesTest {
    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun displayCode_upgradesDrizzleWhenHeavyMm() {
        assertEquals(65, WeatherCodes.displayCode(51, 9.0))
        assertEquals(63, WeatherCodes.displayCode(51, 3.0))
        assertEquals(51, WeatherCodes.displayCode(51, 0.4)) // 仍属毛毛雨量级
        assertEquals(61, WeatherCodes.displayCode(51, 0.6)) // 超毛毛雨上限 → 小雨
        assertEquals(63, WeatherCodes.displayCode(51, 1.5)) // ≥1 mm/h → 中雨档展示码
        assertEquals(95, WeatherCodes.displayCode(95, 20.0)) // 雷暴不改写
    }

    @Test
    fun isStillDrizzle_threshold() {
        assertTrue(WeatherCodes.isStillDrizzle(51, null))
        assertTrue(WeatherCodes.isStillDrizzle(53, 0.4))
        assertFalse(WeatherCodes.isStillDrizzle(51, 0.5)) // ≥0.5 → 不再当毛毛雨
        assertFalse(WeatherCodes.isStillDrizzle(61, 0.2))
    }

    @Test
    fun rainIntensity_ukStyleBands() {
        assertEquals(WeatherCodes.RainIntensity.LIGHT, WeatherCodes.rainIntensity(1.0))
        assertEquals(WeatherCodes.RainIntensity.MODERATE, WeatherCodes.rainIntensity(1.1))
        assertEquals(WeatherCodes.RainIntensity.MODERATE, WeatherCodes.rainIntensity(4.0))
        assertEquals(WeatherCodes.RainIntensity.HEAVY, WeatherCodes.rainIntensity(4.1))
        assertEquals(WeatherCodes.RainIntensity.HEAVY, WeatherCodes.rainIntensity(15.9))
        assertEquals(WeatherCodes.RainIntensity.TORRENTIAL, WeatherCodes.rainIntensity(16.0))
        assertEquals(WeatherCodes.RainIntensity.TORRENTIAL, WeatherCodes.rainIntensity(32.0))
        assertEquals(WeatherCodes.RainIntensity.VIOLENT, WeatherCodes.rainIntensity(32.1))
    }

    @Test
    fun intensityPrecipMmPerHour_usesStoredHourlyBucket() {
        val current =
            com.xiaoxiong.weather.data.CurrentWeather(
                time = "2026-08-04T12:00",
                precipitationMm = 0.5, // 短窗口原始值，分档忽略
                precipitationIntervalSeconds = 900,
                precipitationRateMmPerHour = 7.0, // 整点小时桶
            )
        assertEquals(7.0, WeatherCodes.intensityPrecipMmPerHour(current)!!, 1e-6)
        assertNull(
            WeatherCodes.intensityPrecipMmPerHour(
                com.xiaoxiong.weather.data
                    .CurrentWeather(time = "2026-08-04T12:00"),
            ),
        )
    }

    @Test
    fun isHeavyRainIntensity_thresholds() {
        assertFalse(WeatherCodes.isHeavyRainIntensity(65, 0.0)) // 大雨码但雨强为 0 → 不算当前强降雨
        assertTrue(WeatherCodes.isHeavyRainIntensity(51, 4.1))
        assertFalse(WeatherCodes.isHeavyRainIntensity(51, 0.2))
        assertTrue(WeatherCodes.isHeavyRainForecastPeak(4.1))
        assertFalse(WeatherCodes.isHeavyRainForecastPeak(4.0))
        assertTrue(WeatherCodes.isHeavyRainDailyAccumulation(1.0, 55.0))
        assertFalse(WeatherCodes.isHeavyRainDailyAccumulation(0.3, 55.0))
        assertFalse(WeatherCodes.isHeavyRainIntensity(61, 1.0))
        assertFalse(WeatherCodes.isHeavyRainIntensity(61, 4.0)) // ≤4 仍为中雨
        assertTrue(WeatherCodes.isHeavySnowIntensity(75, 0.0))
        assertTrue(WeatherCodes.isBlizzardWarningIntensity(86, 0.0))
        assertTrue(WeatherCodes.isHeavySnowIntensity(71, 3.0))
        assertTrue(WeatherCodes.isBlizzardWarningIntensity(71, 5.5))
        assertFalse(WeatherCodes.isHeavySnowIntensity(71, 0.4))
        assertTrue(WeatherCodes.isHeavySnowForecastPeak(3.0, 71))
        assertTrue(WeatherCodes.isBlizzardForecastWarning(5.5, 71))
        assertTrue(WeatherCodes.isHeavySnowDailyAccumulation(71, 0.2, 6.0))
        assertFalse(WeatherCodes.isHeavySnowDailyAccumulation(71, 0.0, 6.0))
    }

    @Test
    fun displayCode_upgradesDrySkyWhenRaining() {
        assertEquals(61, WeatherCodes.displayCode(3, 0.1)) // 阴 + 小雨量 → 小雨码
        assertEquals(61, WeatherCodes.displayCode(0, 1.0))
        assertEquals(63, WeatherCodes.displayCode(2, 3.0))
        assertEquals(65, WeatherCodes.displayCode(3, 8.0))
        assertEquals(3, WeatherCodes.displayCode(3, 0.0))
    }

    @Test
    fun displayCode_upgradesSnowByIntensity() {
        assertEquals(75, WeatherCodes.displayCode(71, 3.0))
        assertEquals(86, WeatherCodes.displayCode(71, 6.0))
        assertEquals(73, WeatherCodes.displayCode(71, 1.5))
        assertEquals(71, WeatherCodes.displayCode(71, 0.4))
        assertEquals(95, WeatherCodes.displayCode(95, 20.0))
    }

    @Test
    fun snowIntensity_fourBands() {
        assertEquals(WeatherCodes.SnowIntensity.LIGHT, WeatherCodes.snowIntensity(71, 0.0))
        assertEquals(WeatherCodes.SnowIntensity.MODERATE, WeatherCodes.snowIntensity(73, 0.0))
        assertEquals(WeatherCodes.SnowIntensity.HEAVY, WeatherCodes.snowIntensity(75, 0.0))
        assertEquals(WeatherCodes.SnowIntensity.BLIZZARD, WeatherCodes.snowIntensity(86, 0.0))
        assertEquals(WeatherCodes.SnowIntensity.LIGHT, WeatherCodes.snowIntensity(71, 0.5))
        assertEquals(WeatherCodes.SnowIntensity.MODERATE, WeatherCodes.snowIntensity(71, 1.5))
        assertEquals(WeatherCodes.SnowIntensity.HEAVY, WeatherCodes.snowIntensity(71, 3.0))
        assertEquals(WeatherCodes.SnowIntensity.BLIZZARD, WeatherCodes.snowIntensity(71, 5.5))
        assertEquals(
            WeatherCodes.SnowIntensity.BLIZZARD,
            WeatherCodes.snowIntensity(71, 0.2, dailyPrecipSumMm = 10.0),
        )
        assertEquals(WeatherCodes.SnowIntensity.NONE, WeatherCodes.snowIntensity(61, 3.0))
    }

    @Test
    fun descriptionRes_commonCodes() {
        assertEquals(R.string.wx_clear, WeatherCodes.descriptionRes(0))
        assertEquals(R.string.wx_fog, WeatherCodes.descriptionRes(45))
        assertEquals(R.string.wx_heavy_rain, WeatherCodes.descriptionRes(65))
        assertEquals(R.string.wx_thunderstorm, WeatherCodes.descriptionRes(95))
        assertEquals(R.string.wx_thunderstorm, WeatherCodes.descriptionRes(96))
        assertEquals(R.string.wx_hail, WeatherCodes.descriptionRes(99))
        assertEquals(R.string.label_unknown, WeatherCodes.descriptionRes(null))
        assertEquals(R.string.wx_code_fallback, WeatherCodes.descriptionRes(999))
    }

    @Test
    fun description_matchesDisplayCode_whenDrySkyRains() {
        // 阴 + 小雨量：文案、展示码、emoji 都是雨，不是阴
        assertEquals(61, WeatherCodes.displayCode(3, 0.5))
        assertEquals(R.string.wx_light_rain, WeatherCodes.descriptionRes(3, 0.5))
        assertEquals("🌧️", WeatherCodes.emoji(WeatherCodes.displayCode(3, 0.5)))

        assertEquals(63, WeatherCodes.displayCode(0, 2.0))
        assertEquals(R.string.wx_moderate_rain, WeatherCodes.descriptionRes(0, 2.0))

        assertEquals(51, WeatherCodes.displayCode(51, 0.3))
        assertEquals(R.string.wx_drizzle, WeatherCodes.descriptionRes(51, 0.3))
    }

    @Test
    fun sleetCodes_keepSleetWhenPrecipPresent() {
        // 68/69 雨夹雪：有雨量时不得被降级成中雨/大雨
        assertEquals(R.string.wx_sleet, WeatherCodes.descriptionRes(68))
        assertEquals(R.string.wx_sleet, WeatherCodes.descriptionRes(69))
        assertEquals(68, WeatherCodes.displayCode(68, 3.0))
        assertEquals(69, WeatherCodes.displayCode(69, 6.0))
        assertEquals(R.string.wx_sleet, WeatherCodes.descriptionRes(68, 3.0))
        assertEquals(R.string.wx_sleet, WeatherCodes.descriptionRes(69, 6.0))
        assertEquals("🌨️", WeatherCodes.emoji(68))
        assertEquals("🌨️", WeatherCodes.emoji(69))
        assertFalse(WeatherCodes.isSnowCode(68))
        assertFalse(WeatherCodes.isSnowCode(69))
    }

    @Test
    fun emoji_buckets() {
        assertEquals("☀️", WeatherCodes.emoji(0))
        assertEquals("🌫️", WeatherCodes.emoji(48))
        assertEquals("⛈️", WeatherCodes.emoji(95))
        assertEquals("⛈️", WeatherCodes.emoji(96))
        assertEquals("🧊", WeatherCodes.emoji(99))
        assertEquals("🌡️", WeatherCodes.emoji(999))
    }

    @Test
    fun emoji_nightOverload_avoidsSun() {
        // 夜间晴朗/大体晴 → 月亮；少云 → 云
        assertEquals("🌙", WeatherCodes.emoji(0, isDay = false))
        assertEquals("🌙", WeatherCodes.emoji(1, isDay = false))
        assertEquals("☁️", WeatherCodes.emoji(2, isDay = false))
        // 原无 isDay 重载等价白天口径
        assertEquals(WeatherCodes.emoji(0), WeatherCodes.emoji(0, isDay = true))
        assertEquals(WeatherCodes.emoji(2), WeatherCodes.emoji(2, isDay = true))
        // 雨雪等昼夜一致
        assertEquals("🌧️", WeatherCodes.emoji(61, isDay = false))
        assertEquals("❄️", WeatherCodes.emoji(71, isDay = false))
        assertEquals("🌨️", WeatherCodes.emoji(68, isDay = false))
        assertEquals("⛈️", WeatherCodes.emoji(95, isDay = false))
    }

    @Test
    fun uvForDaytime_unifiedAcrossGenerators() {
        val today = DailyWeather(date = "2026-08-11", uvIndexMax = 9.0)
        val currentUv = AirQuality(uvIndex = 2.0)
        // 白天：优先当日最大 UV（当前值偏低也不误报）
        assertEquals(9.0, WeatherCodes.uvForDaytime(true, currentUv, today))
        // 当日最大缺失：回退当前值
        assertEquals(
            2.0,
            WeatherCodes.uvForDaytime(true, currentUv, DailyWeather(date = "2026-08-11")),
        )
        // 夜间：不取任何口径，返回 null
        assertNull(WeatherCodes.uvForDaytime(false, currentUv, today))
        // 昼夜未知（无 is_day 数据）：同样不提示 UV，避免误报
        assertNull(WeatherCodes.uvForDaytime(null, currentUv, today))
        assertNull(WeatherCodes.uvForDaytime(null, AirQuality(), DailyWeather(date = "2026-08-11")))
    }

    @Test
    fun codeSets_areCentralized() {
        assertEquals(setOf(56, 57, 66, 67), WeatherCodes.freezingCodes)
        assertTrue(WeatherCodes.isFreezingRainCode(56))
        assertTrue(WeatherCodes.isFreezingRainCode(67))
        assertFalse(WeatherCodes.isFreezingRainCode(71))
        assertFalse(WeatherCodes.isFreezingRainCode(null))
        // 降水码覆盖毛毛雨/雨/冻雨/雨夹雪/雪/阵雨/雷暴/冰雹
        assertTrue(
            listOf(51, 61, 66, 68, 71, 80, 86, 95, 99).all { it in WeatherCodes.precipCodes },
        )
        assertFalse(0 in WeatherCodes.precipCodes)
        // 雪码与冻结码互不重叠，且都是降水码子集
        assertTrue(WeatherCodes.snowCodes.intersect(WeatherCodes.freezingCodes).isEmpty())
        assertTrue(WeatherCodes.precipCodes.containsAll(WeatherCodes.snowCodes))
        assertTrue(WeatherCodes.precipCodes.containsAll(WeatherCodes.freezingCodes))
    }

    @Test
    fun uvThresholds_matchLabels() {
        assertEquals(3.0, WeatherCodes.UV_WATCH, 1e-6)
        assertEquals(6.0, WeatherCodes.UV_HIGH, 1e-6)
        assertEquals(8.0, WeatherCodes.UV_VERY_HIGH, 1e-6)
        assertEquals(11.0, WeatherCodes.UV_EXTREME, 1e-6)
        assertEquals(R.string.uv_moderate, uvLabelRes(WeatherCodes.UV_WATCH))
        assertEquals(R.string.uv_high, uvLabelRes(WeatherCodes.UV_HIGH))
        assertEquals(R.string.uv_very_high, uvLabelRes(WeatherCodes.UV_VERY_HIGH))
        assertEquals(R.string.uv_extreme, uvLabelRes(WeatherCodes.UV_EXTREME))
    }

    @Test
    fun aqiLabelRes_boundaries() {
        assertEquals(R.string.label_unknown, aqiLabelRes(null))
        assertEquals(R.string.aqi_good, aqiLabelRes(20))
        assertEquals(R.string.aqi_fair, aqiLabelRes(40))
        assertEquals(R.string.aqi_moderate, aqiLabelRes(60))
        assertEquals(R.string.aqi_poor, aqiLabelRes(80))
        assertEquals(R.string.aqi_very_poor, aqiLabelRes(100))
        assertEquals(R.string.aqi_extremely_poor, aqiLabelRes(101))
    }

    @Test
    fun uvLabelRes_boundaries() {
        assertEquals(R.string.label_unknown, uvLabelRes(null))
        assertEquals(R.string.uv_low, uvLabelRes(2.9))
        assertEquals(R.string.uv_moderate, uvLabelRes(3.0))
        assertEquals(R.string.uv_high, uvLabelRes(6.0))
        assertEquals(R.string.uv_very_high, uvLabelRes(8.0))
        assertEquals(R.string.uv_extreme, uvLabelRes(11.0))
    }

    @Test
    fun windDirectionRes_sectors() {
        assertEquals(R.string.wind_n, windDirectionRes(0))
        assertEquals(R.string.wind_ne, windDirectionRes(45))
        assertEquals(R.string.wind_e, windDirectionRes(90))
        assertEquals(R.string.wind_n, windDirectionRes(360))
        assertEquals(R.string.wind_n, windDirectionRes(-10))
        assertNull(windDirectionRes(null))
    }

    @Test
    fun temperatureAndVisibilityFormatting() {
        assertEquals(32.0, celsiusToFahrenheit(0.0), 0.001)
        assertEquals("--", formatTemperature(null))
        assertEquals("22.0\u00B0C", formatTemperature(22.0, TemperatureUnit.CELSIUS))
        assertEquals("22.4\u00B0C", formatTemperature(22.4, TemperatureUnit.CELSIUS))
        assertEquals("71.6\u00B0F", formatTemperature(22.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("1.5 km", formatVisibility(1500.0))
        assertEquals("800 m", formatVisibility(800.0))
        assertEquals("--", formatVisibility(null))
    }

    @Test
    fun formatHourAndDateLabels() {
        assertEquals("14:30", formatHourLabel("2026-08-02T14:30"))
        assertEquals("08/01", formatDateLabel("2026-08-01"))
    }
}
