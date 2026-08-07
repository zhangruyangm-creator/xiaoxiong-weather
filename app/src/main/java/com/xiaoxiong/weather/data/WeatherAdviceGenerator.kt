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
    val precipCodes = setOf(
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99
    )
    private val snowOrIceCodes = setOf(56, 57, 66, 67, 71, 73, 75, 77, 85, 86)

    @StringRes
    fun clothingRes(feelsC: Double?): Int = when {
        feelsC == null -> R.string.advice_clothing_unknown
        feelsC >= 32 -> R.string.advice_clothing_hot
        feelsC >= 26 -> R.string.advice_clothing_warm
        feelsC >= 18 -> R.string.advice_clothing_mild
        feelsC >= 10 -> R.string.advice_clothing_cool
        feelsC >= 0 -> R.string.advice_clothing_cold
        else -> R.string.advice_clothing_freezing
    }

    @StringRes
    fun rainTipRes(
        weatherCode: Int?,
        precipitationMm: Double?,
        upcomingPrecip: Boolean
    ): Int? {
        val precipNow = WeatherCodes.isPrecipitatingNow(precipitationMm)
        val snowOrIceNow = weatherCode in snowOrIceCodes
        return when {
            snowOrIceNow -> R.string.advice_rain_snow
            precipNow -> R.string.advice_rain_now
            upcomingPrecip -> R.string.advice_rain_soon
            else -> null
        }
    }

    @StringRes
    fun uvTipRes(uv: Double?, isDay: Boolean? = true): Int? {
        if (isDay == false) return null
        return when {
            uv == null -> null
            uv >= 8 -> R.string.advice_uv_strong
            uv >= 5 -> R.string.advice_uv_moderate
            else -> null
        }
    }

    @StringRes
    fun windTipRes(windKmh: Double?): Int? =
        if (windKmh != null && windKmh >= 40) R.string.advice_wind_strong else null

    fun generate(
        resources: Resources,
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        airQuality: AirQuality?
    ): String {
        val feels = current.apparentTemperatureC ?: current.temperatureC
        val upcoming = upcomingFromCurrent(hourly, { it.time }, current.time, count = 12)
        // 「未来有雨」不含当前小时，避免已在下雨仍提示即将降雨
        val upcomingPrecip = upcoming.drop(1).any {
            (it.precipitationMm ?: 0.0) >= 0.2 || (it.precipProbability ?: 0) >= 60
        }
        val uv = when {
            current.isDay == false -> airQuality?.uvIndex
            else -> airQuality?.uvIndex ?: daily.firstOrNull()?.uvIndexMax
        }
        val wind = current.windGustsKmh ?: current.windSpeedKmh

        val parts = listOfNotNull(
            resources.getString(clothingRes(feels)),
            rainTipRes(
                current.weatherCode,
                WeatherCodes.intensityPrecipMmPerHour(current),
                upcomingPrecip
            )
                ?.let { resources.getString(it) },
            uvTipRes(uv, current.isDay)?.let { resources.getString(it) },
            windTipRes(wind)?.let { resources.getString(it) }
        )
        val body = parts.joinToString(resources.getString(R.string.advice_separator))
        return resources.getString(R.string.advice_sentence, body)
    }
}
