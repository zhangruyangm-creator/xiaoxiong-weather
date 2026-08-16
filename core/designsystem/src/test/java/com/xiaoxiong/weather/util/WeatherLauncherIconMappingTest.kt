package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherLauncherIconMappingTest {
    @Test
    fun fromWeatherCode_clearAndCloud() {
        assertEquals(WeatherLauncherIcon.Style.SUNNY, WeatherLauncherIcon.fromWeatherCode(0))
        assertEquals(WeatherLauncherIcon.Style.FEW_CLOUDS, WeatherLauncherIcon.fromWeatherCode(1))
        assertEquals(WeatherLauncherIcon.Style.PARTLY_CLOUDY, WeatherLauncherIcon.fromWeatherCode(2))
        assertEquals(WeatherLauncherIcon.Style.CLOUDY, WeatherLauncherIcon.fromWeatherCode(3))
        assertEquals(
            WeatherLauncherIcon.Style.CLEAR_NIGHT,
            WeatherLauncherIcon.fromWeatherCode(0, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.FEW_CLOUDS_NIGHT,
            WeatherLauncherIcon.fromWeatherCode(1, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.PARTLY_CLOUDY_NIGHT,
            WeatherLauncherIcon.fromWeatherCode(2, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.CLOUDY,
            WeatherLauncherIcon.fromWeatherCode(3, isDay = false),
        )
    }

    @Test
    fun fromWeather_nightClearUsesMoon() {
        assertEquals(
            WeatherLauncherIcon.Style.CLEAR_NIGHT,
            WeatherLauncherIcon.fromWeather(0, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.FEW_CLOUDS_NIGHT,
            WeatherLauncherIcon.fromWeather(1, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.PARTLY_CLOUDY_NIGHT,
            WeatherLauncherIcon.fromWeather(2, isDay = false),
        )
        assertEquals(
            WeatherLauncherIcon.Style.SUNNY,
            WeatherLauncherIcon.fromWeather(0, isDay = true),
        )
        assertEquals(
            WeatherLauncherIcon.Style.FEW_CLOUDS,
            WeatherLauncherIcon.fromWeather(1, isDay = true),
        )
        assertEquals(
            WeatherLauncherIcon.Style.RAINY,
            WeatherLauncherIcon.fromWeather(3, 1.0, isDay = true),
        )
        assertEquals(
            WeatherLauncherIcon.Style.CLOUDY,
            WeatherLauncherIcon.fromWeather(3, 0.0, isDay = true),
        )
    }

    @Test
    fun fromWeather_usesPrecipMmForFiveRainIcons() {
        assertEquals(
            WeatherLauncherIcon.Style.DRIZZLE,
            WeatherLauncherIcon.fromWeather(51, 0.4),
        )
        assertEquals(
            WeatherLauncherIcon.Style.RAINY,
            WeatherLauncherIcon.fromWeather(51, 0.6),
        )
        assertEquals(
            WeatherLauncherIcon.Style.MODERATE_RAIN,
            WeatherLauncherIcon.fromWeather(51, 1.2),
        )
        assertEquals(
            WeatherLauncherIcon.Style.RAINY,
            WeatherLauncherIcon.fromWeather(61, 0.4),
        )
        assertEquals(
            WeatherLauncherIcon.Style.HEAVY_RAIN,
            WeatherLauncherIcon.fromWeather(61, 5.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.HEAVY_RAIN,
            WeatherLauncherIcon.fromWeather(51, 8.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.HEAVY_RAIN,
            WeatherLauncherIcon.fromWeather(61, 12.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.VIOLENT_RAIN,
            WeatherLauncherIcon.fromWeather(61, 16.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.EXTREME_RAIN,
            WeatherLauncherIcon.fromWeather(61, 40.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.EXTREME_RAIN,
            WeatherLauncherIcon.fromWeather(61, 55.0),
        )
        // 雷暴常见，红伞/黑伞暴雨量级更稀少：雨强达标时盖过雷暴图标
        assertEquals(
            WeatherLauncherIcon.Style.STORM,
            WeatherLauncherIcon.fromWeather(95, 5.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.VIOLENT_RAIN,
            WeatherLauncherIcon.fromWeather(95, 16.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.VIOLENT_RAIN,
            WeatherLauncherIcon.fromWeather(95, 30.0),
        )
        assertEquals(
            WeatherLauncherIcon.Style.EXTREME_RAIN,
            WeatherLauncherIcon.fromWeather(95, 40.0),
        )
    }

    @Test
    fun fromWeatherCode_precipBuckets() {
        assertEquals(WeatherLauncherIcon.Style.FOGGY, WeatherLauncherIcon.fromWeatherCode(45))
        assertEquals(WeatherLauncherIcon.Style.DRIZZLE, WeatherLauncherIcon.fromWeatherCode(51))
        assertEquals(WeatherLauncherIcon.Style.RAINY, WeatherLauncherIcon.fromWeatherCode(61))
        assertEquals(WeatherLauncherIcon.Style.MODERATE_RAIN, WeatherLauncherIcon.fromWeatherCode(63))
        assertEquals(WeatherLauncherIcon.Style.HEAVY_RAIN, WeatherLauncherIcon.fromWeatherCode(65))
        assertEquals(WeatherLauncherIcon.Style.FREEZING_RAIN, WeatherLauncherIcon.fromWeatherCode(66))
        assertEquals(WeatherLauncherIcon.Style.SNOWY, WeatherLauncherIcon.fromWeatherCode(71))
        assertEquals(WeatherLauncherIcon.Style.MODERATE_SNOW, WeatherLauncherIcon.fromWeatherCode(73))
        assertEquals(WeatherLauncherIcon.Style.HEAVY_SNOW, WeatherLauncherIcon.fromWeatherCode(75))
        assertEquals(WeatherLauncherIcon.Style.BLIZZARD, WeatherLauncherIcon.fromWeatherCode(86))
        assertEquals(WeatherLauncherIcon.Style.STORM, WeatherLauncherIcon.fromWeatherCode(95))
        assertEquals(WeatherLauncherIcon.Style.STORM, WeatherLauncherIcon.fromWeatherCode(96))
        assertEquals(WeatherLauncherIcon.Style.HAIL, WeatherLauncherIcon.fromWeatherCode(99))
    }

    @Test
    fun fromWeather_usesPrecipMmForFourSnowIcons() {
        assertEquals(WeatherLauncherIcon.Style.SNOWY, WeatherLauncherIcon.fromWeather(71, 0.4))
        assertEquals(WeatherLauncherIcon.Style.MODERATE_SNOW, WeatherLauncherIcon.fromWeather(71, 1.2))
        assertEquals(WeatherLauncherIcon.Style.HEAVY_SNOW, WeatherLauncherIcon.fromWeather(71, 3.0))
        assertEquals(WeatherLauncherIcon.Style.BLIZZARD, WeatherLauncherIcon.fromWeather(71, 6.0))
        assertEquals(WeatherLauncherIcon.Style.HEAVY_SNOW, WeatherLauncherIcon.fromWeather(75, 0.2))
        assertEquals(WeatherLauncherIcon.Style.BLIZZARD, WeatherLauncherIcon.fromWeather(86, 0.2))
    }

    @Test
    fun fromWeatherCode_nullAndUnknownFallbackCloudy() {
        assertEquals(WeatherLauncherIcon.Style.CLOUDY, WeatherLauncherIcon.fromWeatherCode(null))
        assertEquals(WeatherLauncherIcon.Style.CLOUDY, WeatherLauncherIcon.fromWeatherCode(999))
    }

    @Test
    fun styleFromAlias() {
        assertEquals(WeatherLauncherIcon.Style.RAINY, WeatherLauncherIcon.Style.fromAlias("Rainy"))
        assertEquals(
            WeatherLauncherIcon.Style.FEW_CLOUDS,
            WeatherLauncherIcon.Style.fromAlias("FewClouds"),
        )
        assertEquals(
            WeatherLauncherIcon.Style.FEW_CLOUDS_NIGHT,
            WeatherLauncherIcon.Style.fromAlias("FewCloudsNight"),
        )
        assertEquals(WeatherLauncherIcon.Style.SUNNY, WeatherLauncherIcon.Style.fromAlias(null))
        assertEquals(WeatherLauncherIcon.Style.SUNNY, WeatherLauncherIcon.Style.fromAlias("Nope"))
    }

    @Test
    fun drawableRes_followsWeatherCodeStyle() {
        assertEquals(
            com.xiaoxiong.weather.R.drawable.ic_launcher_bear_sunny,
            WeatherLauncherIcon.drawableRes(0),
        )
        assertEquals(
            com.xiaoxiong.weather.R.drawable.ic_launcher_bear_few_clouds,
            WeatherLauncherIcon.drawableRes(1),
        )
        assertEquals(
            com.xiaoxiong.weather.R.drawable.ic_launcher_bear_few_clouds_night,
            WeatherLauncherIcon.drawableRes(1, isDay = false),
        )
        assertEquals(
            com.xiaoxiong.weather.R.drawable.ic_launcher_bear_storm,
            WeatherLauncherIcon.drawableRes(95),
        )
    }
}
