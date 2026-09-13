package com.xiaoxiong.weather.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.upcomingFromCurrent

/**
 * 基于体感、降水、紫外线生成一句穿衣 / 出行建议。
 */
object WeatherAdviceGenerator {
    @StringRes
    fun clothingRes(feelsC: Double?): Int =
        when {
            feelsC == null -> R.string.advice_clothing_unknown
            feelsC >= 32 -> R.string.advice_clothing_hot
            feelsC >= 26 -> R.string.advice_clothing_warm
            feelsC >= 18 -> R.string.advice_clothing_mild
            feelsC >= 10 -> R.string.advice_clothing_cool
            feelsC >= 0 -> R.string.advice_clothing_cold
            else -> R.string.advice_clothing_freezing
        }

    /**
     * 穿衣建议的体感口径：与生活指数共用 [LifeIndexGenerator.effectiveClothingFeels]，
     * 缺体感温度且高湿时按湿冷下调 3°C，避免同屏建议与生活指数穿衣结论矛盾。
     */
    @StringRes
    internal fun clothingResFor(current: CurrentWeather): Int =
        clothingRes(
            LifeIndexGenerator.effectiveClothingFeels(
                feelsC = current.apparentTemperatureC ?: current.temperatureC,
                humidityPercent = current.humidityPercent,
                isApparentTemperature = current.apparentTemperatureC != null,
            ),
        )

    @StringRes
    fun rainTipRes(
        weatherCode: Int?,
        precipitationMm: Double?,
        upcomingPrecip: Boolean,
    ): Int? {
        val precipNow = WeatherCodes.isPrecipitatingNow(precipitationMm)
        val snowOrIceNow = WeatherCodes.isSnowCode(weatherCode) || WeatherCodes.isFreezingRainCode(weatherCode)
        return when {
            snowOrIceNow -> R.string.advice_rain_snow
            precipNow -> R.string.advice_rain_now
            upcomingPrecip -> R.string.advice_rain_soon
            else -> null
        }
    }

    @StringRes
    fun uvTipRes(
        uv: Double?,
        isDay: Boolean? = true,
    ): Int? {
        // 夜间或昼夜未知（无 is_day 数据）都不提示 UV
        if (isDay != true) return null
        return when {
            uv == null -> null
            uv >= WeatherCodes.UV_VERY_HIGH -> R.string.advice_uv_strong
            uv >= WeatherCodes.UV_HIGH -> R.string.advice_uv_moderate
            else -> null
        }
    }

    @StringRes
    fun windTipRes(windKmh: Double?): Int? =
        if (windKmh != null && windKmh >= WeatherCodes.WIND_WATCH_KMH) {
            R.string.advice_wind_strong
        } else {
            null
        }

    fun generate(
        resources: Resources,
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        airQuality: AirQuality?,
    ): String {
        val upcoming = upcomingFromCurrent(hourly, { it.time }, current.time, count = 12)
        // 「未来有雨」不含当前小时，避免已在下雨仍提示即将降雨
        val upcomingPrecip =
            upcoming.drop(1).any {
                (it.precipitationMm ?: 0.0) >= 0.2 || (it.precipProbability ?: 0) >= 60
            }
        // 与预警/生活指数同一口径：白天看当日最大 UV，夜间不提示
        val uv = WeatherCodes.uvForDaytime(current.isDay, airQuality, daily.firstOrNull())
        val wind = current.windGustsKmh ?: current.windSpeedKmh

        val parts =
            listOfNotNull(
                resources.getString(clothingResFor(current)),
                rainTipRes(
                    current.weatherCode,
                    WeatherCodes.intensityPrecipMmPerHour(current),
                    upcomingPrecip,
                )?.let { resources.getString(it) },
                uvTipRes(uv, current.isDay)?.let { resources.getString(it) },
                windTipRes(wind)?.let { resources.getString(it) },
            )
        val body = parts.joinToString(resources.getString(R.string.advice_separator))
        return resources.getString(R.string.advice_sentence, body)
    }
}
