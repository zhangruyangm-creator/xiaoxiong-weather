package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAlertGeneratorTest {

    private fun kindsOf(
        current: CurrentWeather = WeatherFixtures.current(),
        hourly: List<HourlyWeather> = emptyList(),
        daily: List<DailyWeather> = emptyList(),
        air: AirQuality? = null
    ): List<AlertKind> =
        WeatherAlertGenerator.specs(current, hourly, daily, air).map { it.kind }

    private fun first(
        current: CurrentWeather = WeatherFixtures.current(),
        hourly: List<HourlyWeather> = emptyList(),
        daily: List<DailyWeather> = emptyList(),
        air: AirQuality? = null
    ): WeatherAlertGenerator.Spec =
        WeatherAlertGenerator.specs(current, hourly, daily, air).first()

    @Test
    fun clearWeather_emitsClearInfo() {
        val alert = first(WeatherFixtures.current(weatherCode = 0, temperatureC = 22.0))
        assertEquals(AlertKind.CLEAR, alert.kind)
        assertEquals(AlertSeverity.INFO, alert.severity)
    }

    @Test
    fun weatherCode_mapsToKinds() {
        assertTrue(kindsOf(WeatherFixtures.current(weatherCode = 95)).contains(AlertKind.STORM))
        assertTrue(kindsOf(WeatherFixtures.current(weatherCode = 96)).contains(AlertKind.HAIL))
        assertTrue(
            kindsOf(
                WeatherFixtures.current(weatherCode = 65, precipitationRateMmPerHour = 8.0)
            ).contains(AlertKind.HEAVY_RAIN)
        )
        assertTrue(kindsOf(WeatherFixtures.current(weatherCode = 75)).contains(AlertKind.SNOW))
        assertEquals(
            AlertSeverity.WATCH,
            first(WeatherFixtures.current(weatherCode = 75)).severity
        )
        assertEquals(
            AlertSeverity.WARNING,
            first(WeatherFixtures.current(weatherCode = 86)).severity
        )
        assertTrue(kindsOf(WeatherFixtures.current(weatherCode = 66)).contains(AlertKind.FREEZING_RAIN))
        assertTrue(kindsOf(WeatherFixtures.current(weatherCode = 45)).contains(AlertKind.FOG))
    }

    @Test
    fun wind_thresholds() {
        val watch = first(WeatherFixtures.current(windSpeedKmh = 45.0))
        assertEquals(AlertKind.WIND, watch.kind)
        assertEquals(AlertSeverity.WATCH, watch.severity)

        val warning = first(WeatherFixtures.current(windGustsKmh = 70.0))
        assertEquals(AlertKind.WIND, warning.kind)
        assertEquals(AlertSeverity.WARNING, warning.severity)
    }

    @Test
    fun heatAndCold_useApparentTempPreferred() {
        val heat = first(
            WeatherFixtures.current(temperatureC = 30.0, apparentTemperatureC = 38.0)
        )
        assertEquals(AlertKind.HEAT, heat.kind)
        assertEquals(AlertSeverity.WARNING, heat.severity)

        val cold = first(
            WeatherFixtures.current(temperatureC = 0.0, apparentTemperatureC = -12.0)
        )
        assertEquals(AlertKind.COLD, cold.kind)
    }

    @Test
    fun lowVisibility() {
        val alert = first(WeatherFixtures.current(visibilityMeters = 500.0))
        assertEquals(AlertKind.LOW_VISIBILITY, alert.kind)
        assertEquals(AlertSeverity.WATCH, alert.severity)
    }

    @Test
    fun rainSoon_fromUpcomingHourly() {
        val current = WeatherFixtures.current(time = "2026-08-02T12:00", weatherCode = 0)
        val hourly = WeatherFixtures.hourlyAroundNow(
            currentTime = "2026-08-02T12:00",
            futurePrecipProb = 85
        )
        assertTrue(kindsOf(current, hourly).contains(AlertKind.RAIN_SOON))
    }

    @Test
    fun rainSoon_skippedWhenAlreadyHeavyRainCode() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 65,
            precipitationRateMmPerHour = 8.0
        )
        val hourly = WeatherFixtures.hourlyAroundNow(
            currentTime = "2026-08-02T12:00",
            futurePrecipProb = 90
        )
        assertFalse(kindsOf(current, hourly).contains(AlertKind.RAIN_SOON))
        assertTrue(kindsOf(current, hourly).contains(AlertKind.HEAVY_RAIN))
    }

    @Test
    fun rainSoon_skippedWhileLightRainNow_emitsRaining() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 61,
            precipitationRateMmPerHour = 0.4
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.4, precipProbability = 80),
            WeatherFixtures.hourly("2026-08-02T13:00", precipitationMm = 1.2, precipProbability = 85)
        )
        val kinds = kindsOf(current, hourly)
        assertFalse(kinds.contains(AlertKind.RAIN_SOON))
        assertTrue(kinds.contains(AlertKind.RAINING))
        assertFalse(kinds.contains(AlertKind.CLEAR))
    }

    @Test
    fun moderateRainNow_emitsRainingNotClear() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 63,
            precipitationRateMmPerHour = 2.2
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 2.2)
        )
        val alert = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.RAINING }
        assertEquals(AlertSeverity.WATCH, alert.severity)
        assertEquals(R.string.alert_raining_title, alert.titleRes)
        assertFalse(kindsOf(current, hourly).contains(AlertKind.CLEAR))
    }

    @Test
    fun lightSnowNow_emitsSnowing() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T12:00",
            weatherCode = 71,
            temperatureC = -2.0,
            precipitationRateMmPerHour = 0.4
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T12:00", precipitationMm = 0.4, weatherCode = 71)
        )
        assertTrue(kindsOf(current, hourly).contains(AlertKind.SNOWING))
        assertFalse(kindsOf(current, hourly).contains(AlertKind.SNOW))
        assertFalse(kindsOf(current, hourly).contains(AlertKind.CLEAR))
    }

    @Test
    fun heavySnowNow_watch_blizzardNow_warning() {
        val heavy = WeatherAlertGenerator.specs(
            WeatherFixtures.current(
                weatherCode = 71,
                precipitationRateMmPerHour = 3.0,
                temperatureC = -5.0
            ),
            emptyList(),
            emptyList(),
            null
        ).first { it.kind == AlertKind.SNOW }
        assertEquals(AlertSeverity.WATCH, heavy.severity)
        assertEquals(R.string.alert_heavy_snow_title, heavy.titleRes)

        val blizzard = WeatherAlertGenerator.specs(
            WeatherFixtures.current(
                weatherCode = 71,
                precipitationRateMmPerHour = 5.5,
                temperatureC = -8.0
            ),
            emptyList(),
            emptyList(),
            null
        ).first { it.kind == AlertKind.SNOW }
        assertEquals(AlertSeverity.WARNING, blizzard.severity)
        assertEquals(R.string.alert_blizzard_warning_title, blizzard.titleRes)
    }

    @Test
    fun heavySnowSoon_fromUpcomingSnowHours() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T12:00",
            weatherCode = 0,
            temperatureC = -3.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T12:00", precipitationMm = 0.0, weatherCode = 0),
            WeatherFixtures.hourly("2026-01-10T13:00", precipitationMm = 3.2, weatherCode = 75),
            WeatherFixtures.hourly("2026-01-10T14:00", precipitationMm = 1.0, weatherCode = 71)
        )
        val soon = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.SNOW_SOON }
        assertEquals(AlertSeverity.WATCH, soon.severity)
        assertEquals(R.string.alert_heavy_snow_soon_title, soon.titleRes)
    }

    @Test
    fun blizzardSoon_warningFromUpcomingPeak() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T12:00",
            weatherCode = 0,
            temperatureC = -6.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T12:00", precipitationMm = 0.0, weatherCode = 0),
            WeatherFixtures.hourly("2026-01-10T13:00", precipitationMm = 6.0, weatherCode = 86)
        )
        val soon = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.SNOW_SOON }
        assertEquals(AlertSeverity.WARNING, soon.severity)
        assertEquals(R.string.alert_blizzard_soon_warning_title, soon.titleRes)
    }

    @Test
    fun heavyRain_fromCurrentHourBucket() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 51,
            precipitationMm = 0.2,
            precipitationRateMmPerHour = 0.2
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 10.0, precipProbability = 90),
            WeatherFixtures.hourly("2026-08-02T13:00", precipitationMm = 9.0, precipProbability = 90)
        )
        // 提醒优先用 hourly 当前整点桶 → 10 mm → 橙色强降雨提醒（未达 16）
        val heavy = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.HEAVY_RAIN }
        assertEquals(R.string.alert_heavy_rain_title, heavy.titleRes)
        assertEquals(AlertSeverity.WATCH, heavy.severity)
        assertFalse(kindsOf(current, hourly).contains(AlertKind.RAIN_SOON))
    }

    @Test
    fun heavyRain_warningWhenCurrentBucketTorrential() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 65,
            precipitationRateMmPerHour = 18.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 18.0, precipProbability = 95)
        )
        val heavy = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.HEAVY_RAIN }
        assertEquals(R.string.alert_heavy_rain_warning_title, heavy.titleRes)
        assertEquals(R.string.alert_heavy_rain_warning_msg, heavy.messageRes)
        assertEquals(AlertSeverity.WARNING, heavy.severity)
    }

    @Test
    fun heavyRainSoon_whenPeakIsLaterNotNow() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 3,
            precipitationMm = 0.1,
            precipitationRateMmPerHour = 0.1
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.1, precipProbability = 70),
            WeatherFixtures.hourly("2026-08-02T13:00", precipitationMm = 1.0, precipProbability = 80),
            WeatherFixtures.hourly("2026-08-02T14:00", precipitationMm = 1.5, precipProbability = 85),
            WeatherFixtures.hourly("2026-08-02T15:00", precipitationMm = 10.6, precipProbability = 97)
        )
        val specs = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
        assertFalse(specs.any { it.kind == AlertKind.HEAVY_RAIN })
        val soon = specs.first { it.kind == AlertKind.RAIN_SOON }
        assertEquals(R.string.alert_heavy_rain_soon_title, soon.titleRes)
        assertEquals(R.string.alert_heavy_rain_soon_msg, soon.messageRes)
        assertEquals(AlertSeverity.WATCH, soon.severity)
        assertEquals(10.6, soon.messageArg)
        assertEquals(10.6, soon.precipIconMm)
    }

    @Test
    fun heavyRainSoon_warningWhenPeakVeryHigh() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T12:00",
            weatherCode = 3,
            precipitationRateMmPerHour = 0.1
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-02T12:00", precipitationMm = 0.1),
            WeatherFixtures.hourly("2026-08-02T13:00", precipitationMm = 2.0),
            WeatherFixtures.hourly("2026-08-02T14:00", precipitationMm = 22.0, precipProbability = 95)
        )
        val soon = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
            .first { it.kind == AlertKind.RAIN_SOON }
        assertEquals(R.string.alert_heavy_rain_soon_warning_title, soon.titleRes)
        assertEquals(R.string.alert_heavy_rain_soon_warning_msg, soon.messageRes)
        assertEquals(AlertSeverity.WARNING, soon.severity)
        assertEquals(22.0, soon.messageArg)
    }

    @Test
    fun heavyRain_fromDailyTorrentUsesDailyCopyNotCurrentStrong() {
        val current = WeatherFixtures.current(
            time = "2026-08-02T18:00",
            weatherCode = 53,
            precipitationMm = 0.5,
            precipitationRateMmPerHour = 0.5
        )
        // 全日预报 sum 可能很大，但提醒只用截至当前的小时累计
        val hourly = (0..18).map { hour ->
            val t = "2026-08-02T%02d:00".format(hour)
            WeatherFixtures.hourly(t, precipitationMm = if (hour <= 18) 3.0 else 0.0)
        }
        val daily = listOf(WeatherFixtures.daily().copy(precipSumMm = 90.0))
        val specs = WeatherAlertGenerator.specs(current, hourly, daily, null)
        val heavy = specs.first { it.kind == AlertKind.HEAVY_RAIN }
        assertEquals(R.string.alert_heavy_rain_daily_title, heavy.titleRes)
        assertEquals(R.string.alert_heavy_rain_daily_msg, heavy.messageRes)
        assertEquals(AlertSeverity.WATCH, heavy.severity)
        assertEquals(57.0, heavy.messageArg) // 0..18 点共 19 小时 × 3mm（含当前整点桶）
    }

    @Test
    fun uv_fallsBackToDailyMaxOnlyInDaytime() {
        val day = WeatherFixtures.current(isDay = true)
        val alert = WeatherAlertGenerator.specs(
            day,
            emptyList(),
            listOf(WeatherFixtures.daily(uvIndexMax = 9.0)),
            null
        ).first { it.kind == AlertKind.UV }
        assertEquals(AlertKind.UV, alert.kind)

        val night = WeatherFixtures.current(isDay = false)
        assertFalse(
            WeatherAlertGenerator.specs(
                night,
                emptyList(),
                listOf(WeatherFixtures.daily(uvIndexMax = 9.0)),
                null
            ).any { it.kind == AlertKind.UV }
        )
    }

    @Test
    fun uvAndAqi_severityByThreshold() {
        val uvWatch = first(air = WeatherFixtures.air(uvIndex = 8.5))
        assertEquals(AlertKind.UV, uvWatch.kind)
        assertEquals(AlertSeverity.WATCH, uvWatch.severity)

        val uvWarn = first(air = WeatherFixtures.air(uvIndex = 11.0))
        assertEquals(AlertSeverity.WARNING, uvWarn.severity)

        val aqiWatch = first(air = WeatherFixtures.air(europeanAqi = 70))
        assertEquals(AlertKind.AIR_QUALITY, aqiWatch.kind)
        assertEquals(AlertSeverity.WATCH, aqiWatch.severity)

        val aqiWarn = first(air = WeatherFixtures.air(europeanAqi = 90))
        assertEquals(AlertSeverity.WARNING, aqiWarn.severity)

        val dustWatch = first(air = WeatherFixtures.air(dustUgM3 = 60.0))
        assertEquals(AlertKind.DUST, dustWatch.kind)
        assertEquals(AlertSeverity.WATCH, dustWatch.severity)

        val dustWarn = first(air = WeatherFixtures.air(dustUgM3 = 160.0))
        assertEquals(AlertKind.DUST, dustWarn.kind)
        assertEquals(AlertSeverity.WARNING, dustWarn.severity)
    }

    @Test
    fun distinctByTitle() {
        val alerts = WeatherAlertGenerator.specs(
            WeatherFixtures.current(weatherCode = 45, visibilityMeters = 500.0),
            emptyList(),
            emptyList(),
            null
        )
        assertTrue(alerts.any { it.kind == AlertKind.FOG })
        assertTrue(alerts.any { it.kind == AlertKind.LOW_VISIBILITY })
    }

    @Test
    fun snowyRoad_afterModerateSnowWithinWindow() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T12:00",
            weatherCode = 0,
            temperatureC = -2.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T08:00", weatherCode = 73, precipitationMm = 1.2),
            WeatherFixtures.hourly("2026-01-10T09:00", weatherCode = 73, precipitationMm = 1.0),
            WeatherFixtures.hourly("2026-01-10T10:00", weatherCode = 3, precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-01-10T11:00", weatherCode = 3, precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-01-10T12:00", weatherCode = 0, precipitationMm = 0.0)
        )
        // 中雪后约 3 小时，持续 6 小时内 → 应提醒
        assertTrue(kindsOf(current, hourly).contains(AlertKind.SNOWY_ROAD))
    }

    @Test
    fun snowyRoad_skippedWhenModerateSnowWindowExpired() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T18:00",
            weatherCode = 0,
            temperatureC = -3.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T08:00", weatherCode = 73, precipitationMm = 1.5),
            WeatherFixtures.hourly("2026-01-10T09:00", weatherCode = 3, precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-01-10T18:00", weatherCode = 0, precipitationMm = 0.0)
        )
        // 中雪结束已约 9 小时 > 6 小时 → 不提醒
        assertFalse(kindsOf(current, hourly).contains(AlertKind.SNOWY_ROAD))
    }

    @Test
    fun snowyRoad_heavySnowLastsLongerThanModerate() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T18:00",
            weatherCode = 0,
            temperatureC = -4.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T08:00", weatherCode = 75, precipitationMm = 3.0),
            WeatherFixtures.hourly("2026-01-10T09:00", weatherCode = 3, precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-01-10T18:00", weatherCode = 0, precipitationMm = 0.0)
        )
        // 大雪后约 9 小时，持续 12 小时内 → 仍提醒
        assertTrue(kindsOf(current, hourly).contains(AlertKind.SNOWY_ROAD))
    }

    @Test
    fun snowyRoad_skippedWhenTooWarm() {
        val current = WeatherFixtures.current(
            time = "2026-01-10T12:00",
            weatherCode = 0,
            temperatureC = 5.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-01-10T10:00", weatherCode = 73, precipitationMm = 1.2),
            WeatherFixtures.hourly("2026-01-10T12:00", weatherCode = 0, precipitationMm = 0.0)
        )
        assertFalse(kindsOf(current, hourly).contains(AlertKind.SNOWY_ROAD))
    }

    @Test
    fun ponding_fromThreeHourAccumulation() {
        val current = WeatherFixtures.current(
            time = "2026-08-04T15:00",
            weatherCode = 63,
            precipitationMm = 2.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-04T13:00", precipitationMm = 10.0),
            WeatherFixtures.hourly("2026-08-04T14:00", precipitationMm = 9.0),
            WeatherFixtures.hourly("2026-08-04T15:00", precipitationMm = 8.0)
        )
        val specs = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
        val ponding = specs.first { it.kind == AlertKind.PONDING }
        assertEquals(AlertSeverity.WATCH, ponding.severity)
        assertEquals(27.0, ponding.messageArg as Double, 0.01)
    }

    @Test
    fun ponding_warningWhenSixHourHeavy() {
        val current = WeatherFixtures.current(
            time = "2026-08-04T18:00",
            weatherCode = 65,
            precipitationMm = 5.0
        )
        val hourly = (13..18).map { hour ->
            WeatherFixtures.hourly(
                "2026-08-04T%02d:00".format(hour),
                precipitationMm = 9.0
            )
        }
        val specs = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
        val ponding = specs.first { it.kind == AlertKind.PONDING }
        assertEquals(AlertSeverity.WARNING, ponding.severity)
    }

    @Test
    fun ponding_twelveHourAccumulation_watchBeforeWarningThreshold() {
        // 12h 累计 60mm：刚达触发档，应仍是 WATCH（60 < 75 预警档）
        val current = WeatherFixtures.current(
            time = "2026-08-04T21:00",
            weatherCode = 63,
            precipitationMm = 5.0
        )
        val hourly = (10..21).map { hour ->
            WeatherFixtures.hourly(
                "2026-08-04T%02d:00".format(hour),
                precipitationMm = 5.0
            )
        }
        val specs = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
        val ponding = specs.first { it.kind == AlertKind.PONDING }
        assertEquals(AlertSeverity.WATCH, ponding.severity)
        assertEquals(60.0, ponding.messageArg as Double, 0.01)
    }

    @Test
    fun ponding_twelveHourAccumulation_warningAtSevereThreshold() {
        // 12h 累计 78mm：3h/6h 均未达预警档，只有 12h 档升 WARNING
        val current = WeatherFixtures.current(
            time = "2026-08-04T21:00",
            weatherCode = 65,
            precipitationMm = 6.5
        )
        val hourly = (10..21).map { hour ->
            WeatherFixtures.hourly(
                "2026-08-04T%02d:00".format(hour),
                precipitationMm = 6.5
            )
        }
        val specs = WeatherAlertGenerator.specs(current, hourly, emptyList(), null)
        val ponding = specs.first { it.kind == AlertKind.PONDING }
        assertEquals(AlertSeverity.WARNING, ponding.severity)
        assertEquals(78.0, ponding.messageArg as Double, 0.01)
    }

    @Test
    fun ponding_skippedWhenAccumulationLow() {
        val current = WeatherFixtures.current(
            time = "2026-08-04T12:00",
            weatherCode = 61,
            precipitationMm = 1.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-04T10:00", precipitationMm = 2.0),
            WeatherFixtures.hourly("2026-08-04T11:00", precipitationMm = 2.0),
            WeatherFixtures.hourly("2026-08-04T12:00", precipitationMm = 2.0)
        )
        assertFalse(kindsOf(current, hourly).contains(AlertKind.PONDING))
    }

    @Test
    fun ponding_skippedAfterRainLongGone() {
        val current = WeatherFixtures.current(
            time = "2026-08-04T18:00",
            weatherCode = 0,
            precipitationMm = 0.0
        )
        val hourly = listOf(
            WeatherFixtures.hourly("2026-08-04T10:00", precipitationMm = 15.0),
            WeatherFixtures.hourly("2026-08-04T11:00", precipitationMm = 15.0),
            WeatherFixtures.hourly("2026-08-04T12:00", precipitationMm = 12.0),
            WeatherFixtures.hourly("2026-08-04T16:00", precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-08-04T17:00", precipitationMm = 0.0),
            WeatherFixtures.hourly("2026-08-04T18:00", precipitationMm = 0.0)
        )
        assertFalse(kindsOf(current, hourly).contains(AlertKind.PONDING))
    }

    @Test
    fun coldWave_ignoresNormalDiurnalSwing() {
        // 白天 18°C、夜里 9°C 只是昼夜温差，日际几乎不降温 → 不应报警
        val current = WeatherFixtures.current(time = "2026-11-10T14:00", temperatureC = 18.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-11-10", tempMaxC = 18.0, tempMinC = 9.0),
            WeatherFixtures.daily("2026-11-11", tempMaxC = 17.0, tempMinC = 8.0)
        )
        assertFalse(
            WeatherAlertGenerator.specs(current, emptyList(), daily, null)
                .any { it.kind == AlertKind.COLD_WAVE }
        )
    }

    @Test
    fun coldWave_watchWhenDayOverDayDropAbout10CIntoCold() {
        val current = WeatherFixtures.current(time = "2026-11-10T14:00", temperatureC = 20.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-11-10", tempMaxC = 20.0, tempMinC = 10.0),
            WeatherFixtures.daily("2026-11-11", tempMaxC = 9.0, tempMinC = 2.0)
        )
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.COLD_WAVE }
        assertEquals(AlertSeverity.WATCH, wave.severity)
        assertEquals(R.string.alert_cold_wave_title, wave.titleRes)
        assertEquals(11.0, wave.messageArg) // max 20→9
    }

    @Test
    fun coldWave_warningWhenDayOverDayDropAbout14CIntoCold() {
        val current = WeatherFixtures.current(time = "2026-11-10T14:00", temperatureC = 18.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-11-10", tempMaxC = 18.0, tempMinC = 8.0),
            WeatherFixtures.daily("2026-11-11", tempMaxC = 3.0, tempMinC = -2.0)
        )
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.COLD_WAVE }
        assertEquals(AlertSeverity.WARNING, wave.severity)
        assertEquals(R.string.alert_cold_wave_warning_title, wave.titleRes)
        assertEquals(15.0, wave.messageArg) // max 18→3
    }

    @Test
    fun heatWave_watchWhenThreeHotDays() {
        val current = WeatherFixtures.current(time = "2026-07-10T12:00", temperatureC = 33.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-07-10", tempMaxC = 35.0, tempMinC = 26.0),
            WeatherFixtures.daily("2026-07-11", tempMaxC = 36.0, tempMinC = 27.0),
            WeatherFixtures.daily("2026-07-12", tempMaxC = 35.5, tempMinC = 26.5),
            WeatherFixtures.daily("2026-07-13", tempMaxC = 30.0, tempMinC = 24.0)
        )
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.HEAT_WAVE }
        assertEquals(AlertSeverity.WATCH, wave.severity)
        assertEquals(R.string.alert_heat_wave_title, wave.titleRes)
        assertEquals(3, wave.messageArg)
    }

    @Test
    fun heatWave_watchWhenFourHotDaysStillOrange() {
        val current = WeatherFixtures.current(time = "2026-07-10T12:00", temperatureC = 34.0)
        val daily = (0 until 4).map { d ->
            WeatherFixtures.daily(
                "2026-07-%02d".format(10 + d),
                tempMaxC = 36.0,
                tempMinC = 27.0
            )
        }
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.HEAT_WAVE }
        assertEquals(AlertSeverity.WATCH, wave.severity)
        assertEquals(4, wave.messageArg)
    }

    @Test
    fun heatWave_warningWhenFiveHotDays() {
        val current = WeatherFixtures.current(time = "2026-07-10T12:00", temperatureC = 34.0)
        val daily = (0 until 5).map { d ->
            WeatherFixtures.daily(
                "2026-07-%02d".format(10 + d),
                tempMaxC = 36.0,
                tempMinC = 27.0
            )
        }
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.HEAT_WAVE }
        assertEquals(AlertSeverity.WARNING, wave.severity)
        assertEquals(R.string.alert_heat_wave_warning_title, wave.titleRes)
        assertEquals(5, wave.messageArg)
    }

    @Test
    fun heatWave_warningWhenThreeSevereHotDays() {
        val current = WeatherFixtures.current(time = "2026-07-10T12:00", temperatureC = 36.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-07-10", tempMaxC = 37.0, tempMinC = 28.0),
            WeatherFixtures.daily("2026-07-11", tempMaxC = 38.0, tempMinC = 29.0),
            WeatherFixtures.daily("2026-07-12", tempMaxC = 37.5, tempMinC = 28.5)
        )
        val wave = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
            .first { it.kind == AlertKind.HEAT_WAVE }
        assertEquals(AlertSeverity.WARNING, wave.severity)
        assertEquals(3, wave.messageArg)
    }

    @Test
    fun heatWave_skippedWhenFewerThanThreeHotDays() {
        val current = WeatherFixtures.current(time = "2026-07-10T12:00", temperatureC = 33.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-07-10", tempMaxC = 36.0, tempMinC = 27.0),
            WeatherFixtures.daily("2026-07-11", tempMaxC = 35.0, tempMinC = 26.0),
            WeatherFixtures.daily("2026-07-12", tempMaxC = 32.0, tempMinC = 24.0)
        )
        assertFalse(
            WeatherAlertGenerator.specs(current, emptyList(), daily, null)
                .any { it.kind == AlertKind.HEAT_WAVE }
        )
    }

    @Test
    fun cooldown_warmDestinationUsesCooldownKindNotSnowBear() {
        // 37→26：降幅大，但明天仍偏暖 → 剧烈降温 + 添衣小熊，不是寒潮雪天熊
        val current = WeatherFixtures.current(time = "2026-08-10T14:00", temperatureC = 37.0)
        val daily = listOf(
            WeatherFixtures.daily("2026-08-10", tempMaxC = 37.0, tempMinC = 28.0),
            WeatherFixtures.daily("2026-08-11", tempMaxC = 26.0, tempMinC = 20.0)
        )
        val specs = WeatherAlertGenerator.specs(current, emptyList(), daily, null)
        assertFalse(specs.any { it.kind == AlertKind.COLD_WAVE })
        val cool = specs.first { it.kind == AlertKind.COOLDOWN }
        assertEquals(R.string.alert_cold_wave_title, cool.titleRes)
        assertEquals(11.0, cool.messageArg)
        assertEquals(AlertSeverity.WATCH, cool.severity)
    }
}
