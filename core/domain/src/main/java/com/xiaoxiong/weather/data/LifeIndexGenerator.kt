package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.upcomingFromCurrent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 基于 Open-Meteo 已有字段在本地估算生活指数（非官方发布）。
 */
object LifeIndexGenerator {
    fun generate(
        resources: Resources,
        current: CurrentWeather,
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        airQuality: AirQuality?,
    ): List<LifeIndex> {
        val upcoming6 = upcomingFromCurrent(hourly, { it.time }, current.time, count = 6)
        val upcoming12 = upcomingFromCurrent(hourly, { it.time }, current.time, count = 12)
        val upcoming18 = upcomingFromCurrent(hourly, { it.time }, current.time, count = 18)
        val today = daily.firstOrNull()
        val feels = current.apparentTemperatureC ?: current.temperatureC
        val wind = current.windGustsKmh ?: current.windSpeedKmh
        val aqi = unifiedUsAqi(airQuality)
        val uv = uvForIndex(current.isDay, airQuality, today)
        val maxPrecipProb =
            upcoming18.mapNotNull { it.precipProbability }.maxOrNull()
                ?: today?.precipProbabilityMax
        val precipSum = upcoming18.sumOf { it.precipitationMm ?: 0.0 }
        val rainingNow =
            WeatherCodes.isPrecipitatingNow(
                WeatherCodes.intensityPrecipMmPerHour(current),
            )

        return listOf(
            comfortIndex(resources, feels, current.humidityPercent),
            clothingIndex(
                resources,
                feels,
                current.humidityPercent,
                isApparentTemperature = current.apparentTemperatureC != null,
            ),
            umbrellaIndex(resources, rainingNow, current.weatherCode, upcoming6, upcoming12),
            uvIndex(resources, uv, current.isDay),
            carWashIndex(resources, rainingNow, maxPrecipProb, precipSum, wind),
            laundryIndex(
                resources,
                rainingNow,
                maxPrecipProb,
                current.humidityPercent,
                current.cloudCoverPercent,
                current.isDay,
            ),
            sportIndex(resources, feels, rainingNow, wind, aqi, uv),
            coldIndex(resources, feels, wind, current.humidityPercent, upcoming12),
        )
    }

    /**
     * 统一 UV 口径，与预警/建议一致：白天以当日最大 UV 为准（缺失回退当前值），夜间返回 null。
     */
    internal fun uvForIndex(
        isDay: Boolean?,
        airQuality: AirQuality?,
        today: DailyWeather?,
    ): Double? = WeatherCodes.uvForDaytime(isDay, airQuality, today)

    /**
     * 统一到 US AQI 口径，供运动指数等判断使用。
     * US AQI 与欧洲 EAQI 数值不可比：US AQI 100 ≈ PM2.5 35.4µg/m³，
     * EAQI 100 ≈ PM2.5 50µg/m³，故只有 EAQI 时按 50/35.4≈1.41 近似放大，
     * 避免同一污染水平用 EAQI 判断时偏乐观。
     */
    internal fun unifiedUsAqi(airQuality: AirQuality?): Int? {
        val us = airQuality?.usAqi
        if (us != null) return us
        val eu = airQuality?.europeanAqi ?: return null
        return (eu * 50.0 / 35.4).roundToInt().coerceAtLeast(0)
    }

    /**
     * 体感分档：结合体感温度与湿度。
     * 偏冷高湿 →「湿冷」；高湿但不热 →「闷湿」；偏热高湿 →「闷热」；
     * 温度尚可但湿度过低 →「干燥 / 很干燥」（不算舒适）。
     */
    internal fun comfortBand(
        feelsC: Double?,
        humidityPercent: Int?,
    ): ComfortBand {
        if (feelsC == null) return ComfortBand.UNKNOWN
        val humid = humidityPercent ?: 50
        return when {
            // 更冷再分档：&lt; -30°C 极寒，&lt; -15°C 酷寒，&lt; 0°C 严寒
            feelsC < -30 -> ComfortBand.EXTREME_COLD
            feelsC < -15 -> ComfortBand.BITTER
            feelsC < 0 -> ComfortBand.FREEZING
            // 0–10°C：高湿偏冷为湿冷，否则寒冷（极干时仍偏冷感，不强行改干燥）
            feelsC < 10 -> if (humid >= 75) ComfortBand.DAMP_COLD else ComfortBand.COLD
            // 10–18°C：湿冷 / 很干燥 / 干燥 / 凉爽
            feelsC < 18 ->
                when {
                    humid >= 80 -> ComfortBand.DAMP_COLD
                    humid < 13 -> ComfortBand.VERY_DRY
                    humid < 25 -> ComfortBand.DRY
                    else -> ComfortBand.COOL
                }
            // 约 18–26°C：闷湿 / 很干燥 / 干燥 / 舒适
            feelsC < 26 ->
                when {
                    humid >= 85 -> ComfortBand.HUMID
                    humid < 13 -> ComfortBand.VERY_DRY
                    humid < 25 -> ComfortBand.DRY
                    else -> ComfortBand.MILD
                }
            feelsC < 30 ->
                when {
                    humid >= 70 -> ComfortBand.MUGGY
                    humid < 13 -> ComfortBand.VERY_DRY
                    humid < 25 -> ComfortBand.DRY
                    else -> ComfortBand.WARM
                }
            feelsC < 35 ->
                when {
                    humid >= 65 -> ComfortBand.MUGGY
                    humid < 13 -> ComfortBand.VERY_DRY
                    humid < 25 -> ComfortBand.DRY
                    else -> ComfortBand.HOT
                }
            // ≥35°C 酷热；再高 ≥45°C 极热
            feelsC < 45 -> ComfortBand.SCORCHING
            else -> ComfortBand.EXTREME_HEAT
        }
    }

    enum class ComfortBand {
        UNKNOWN,
        EXTREME_COLD,
        BITTER,
        FREEZING,
        COLD,
        DAMP_COLD,
        COOL,
        MILD,
        DRY,
        VERY_DRY,
        HUMID,
        WARM,
        MUGGY,
        HOT,
        SCORCHING,
        EXTREME_HEAT,
    }

    /**
     * 带伞档位汇总。调用方需自行判断两档降水信号并合并进入参：
     * - 正在下雨 / 下雪，或未来 6 小时内较强降水（≥0.5mm 或概率 ≥70%）→ 必带（POOR）；
     * - 仅未来 12 小时内毛毛雨（≥0.2mm 或概率 ≥60%）→ 可带可不带（FAIR）。
     */
    internal fun umbrellaLevel(
        rainingNow: Boolean,
        weatherCode: Int?,
        upcomingPrecipSoon: Boolean,
    ): LifeIndexLevel {
        val snow = WeatherCodes.isSnowCode(weatherCode) || WeatherCodes.isFreezingRainCode(weatherCode)
        return when {
            snow || rainingNow -> LifeIndexLevel.POOR
            upcomingPrecipSoon -> LifeIndexLevel.FAIR
            else -> LifeIndexLevel.GOOD
        }
    }

    /**
     * 未来降水对带伞的分档信号：
     * - 返回 [Pair]：first = 未来 6 小时内较强降水（≥0.5mm/h 或概率 ≥70%）→「必带」；
     * - second = 未来 12 小时内毛毛雨（≥0.2mm/h 或概率 ≥60%）→「可带可不带」。
     * 避免 12 小时窗口里任一小毛毛雨就长期提醒带伞。
     */
    internal fun umbrellaPrecip(
        upcoming6: List<HourlyWeather>,
        upcoming12: List<HourlyWeather>,
    ): Pair<Boolean, Boolean> {
        val heavySoon =
            upcoming6.any {
                (it.precipitationMm ?: 0.0) >= 0.5 || (it.precipProbability ?: 0) >= 70
            }
        val lightSoon =
            upcoming12.any {
                (it.precipitationMm ?: 0.0) >= 0.2 || (it.precipProbability ?: 0) >= 60
            }
        return heavySoon to lightSoon
    }

    internal fun carWashLevel(
        rainingNow: Boolean,
        maxPrecipProb: Int?,
        precipSum: Double,
        windKmh: Double?,
    ): LifeIndexLevel =
        when {
            rainingNow || precipSum >= 1.0 || (maxPrecipProb ?: 0) >= 60 -> LifeIndexLevel.POOR
            (maxPrecipProb ?: 0) >= 35 || (windKmh ?: 0.0) >= WeatherCodes.WIND_WATCH_KMH -> LifeIndexLevel.FAIR
            else -> LifeIndexLevel.GOOD
        }

    private fun comfortIndex(
        resources: Resources,
        feelsC: Double?,
        humidityPercent: Int?,
    ): LifeIndex {
        val band = comfortBand(feelsC, humidityPercent)
        val (level, labelRes, summaryRes) =
            when (band) {
                ComfortBand.MILD ->
                    Triple(
                        LifeIndexLevel.GOOD,
                        R.string.life_comfort_label_mild,
                        R.string.life_comfort_mild,
                    )
                ComfortBand.COOL ->
                    Triple(
                        LifeIndexLevel.GOOD,
                        R.string.life_comfort_label_cool,
                        R.string.life_comfort_cool,
                    )
                ComfortBand.WARM ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_warm,
                        R.string.life_comfort_warm,
                    )
                ComfortBand.HUMID ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_humid,
                        R.string.life_comfort_humid,
                    )
                ComfortBand.DRY ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_dry,
                        R.string.life_comfort_dry,
                    )
                ComfortBand.VERY_DRY ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_very_dry,
                        R.string.life_comfort_very_dry,
                    )
                // 闷热与炎热同为较差：同温高湿不应比干热更「轻」
                ComfortBand.MUGGY ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_muggy,
                        R.string.life_comfort_muggy,
                    )
                ComfortBand.HOT ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_hot,
                        R.string.life_comfort_hot,
                    )
                ComfortBand.COLD ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_cold,
                        R.string.life_comfort_cold,
                    )
                ComfortBand.DAMP_COLD ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_damp_cold,
                        R.string.life_comfort_damp_cold,
                    )
                ComfortBand.FREEZING ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_freezing,
                        R.string.life_comfort_freezing,
                    )
                ComfortBand.BITTER ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_bitter,
                        R.string.life_comfort_bitter,
                    )
                ComfortBand.EXTREME_COLD ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_extreme_cold,
                        R.string.life_comfort_extreme_cold,
                    )
                ComfortBand.SCORCHING ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_scorching,
                        R.string.life_comfort_scorching,
                    )
                ComfortBand.EXTREME_HEAT ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_comfort_label_extreme_heat,
                        R.string.life_comfort_extreme_heat,
                    )
                ComfortBand.UNKNOWN ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_comfort_label_unknown,
                        R.string.life_comfort_unknown,
                    )
            }
        return LifeIndex(
            kind = LifeIndexKind.COMFORT,
            level = level,
            title = resources.getString(R.string.life_comfort),
            summary = resources.getString(summaryRes),
            levelLabel = resources.getString(labelRes),
        )
    }

    /**
     * 穿衣指数的等效体感温度：体感温度缺失、退回原始气温时，
     * 高湿（≥75%）会放大寒冷感（湿冷），等效体感下调 3°C；
     * 已有体感温度（已含湿度修正）则原样使用。
     */
    internal fun effectiveClothingFeels(
        feelsC: Double?,
        humidityPercent: Int?,
        isApparentTemperature: Boolean,
    ): Double? =
        if (!isApparentTemperature && feelsC != null && (humidityPercent ?: 0) >= 75) {
            feelsC - 3.0
        } else {
            feelsC
        }

    private fun clothingIndex(
        resources: Resources,
        feelsC: Double?,
        humidityPercent: Int?,
        isApparentTemperature: Boolean,
    ): LifeIndex {
        val effectiveFeels =
            effectiveClothingFeels(feelsC, humidityPercent, isApparentTemperature)
        val level =
            when {
                effectiveFeels == null -> LifeIndexLevel.FAIR
                effectiveFeels in 18.0..26.0 -> LifeIndexLevel.GOOD
                effectiveFeels in 10.0..32.0 -> LifeIndexLevel.FAIR
                else -> LifeIndexLevel.POOR
            }
        val (labelRes, summaryRes) =
            when {
                effectiveFeels == null ->
                    R.string.life_clothing_label_fair to R.string.life_clothing_unknown
                effectiveFeels >= 32 ->
                    R.string.life_clothing_label_guard to R.string.life_clothing_hot
                effectiveFeels >= 26 ->
                    R.string.life_clothing_label_fair to R.string.life_clothing_warm
                effectiveFeels >= 18 ->
                    R.string.life_clothing_label_good to R.string.life_clothing_mild
                effectiveFeels >= 10 ->
                    R.string.life_clothing_label_fair to R.string.life_clothing_cool
                effectiveFeels >= 0 ->
                    R.string.life_clothing_label_guard to R.string.life_clothing_cold
                else ->
                    R.string.life_clothing_label_guard to R.string.life_clothing_freezing
            }
        return LifeIndex(
            kind = LifeIndexKind.CLOTHING,
            level = level,
            title = resources.getString(R.string.life_clothing),
            summary = resources.getString(summaryRes),
            levelLabel = resources.getString(labelRes),
        )
    }

    private fun umbrellaIndex(
        resources: Resources,
        rainingNow: Boolean,
        weatherCode: Int?,
        upcoming6: List<HourlyWeather>,
        upcoming12: List<HourlyWeather>,
    ): LifeIndex {
        val (heavySoon, lightSoon) = umbrellaPrecip(upcoming6, upcoming12)
        val level = umbrellaLevel(rainingNow || heavySoon, weatherCode, lightSoon)
        val (labelRes, summaryRes) =
            when (level) {
                LifeIndexLevel.POOR ->
                    R.string.life_umbrella_label_need to R.string.life_umbrella_need
                LifeIndexLevel.FAIR ->
                    R.string.life_umbrella_label_maybe to R.string.life_umbrella_maybe
                LifeIndexLevel.GOOD ->
                    R.string.life_umbrella_label_ok to R.string.life_umbrella_ok
            }
        return LifeIndex(
            kind = LifeIndexKind.UMBRELLA,
            level = level,
            title = resources.getString(R.string.life_umbrella),
            summary = resources.getString(summaryRes),
            levelLabel = resources.getString(labelRes),
        )
    }

    private fun uvIndex(
        resources: Resources,
        uv: Double?,
        isDay: Boolean?,
    ): LifeIndex {
        val daytime = isDay != false
        val (level, labelRes, summaryRes) =
            when {
                !daytime || uv == null || uv < WeatherCodes.UV_WATCH ->
                    Triple(
                        LifeIndexLevel.GOOD,
                        R.string.life_uv_label_low,
                        R.string.life_uv_low,
                    )
                uv < WeatherCodes.UV_HIGH ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_uv_label_mid,
                        R.string.life_uv_mid,
                    )
                else ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_uv_label_high,
                        R.string.life_uv_high,
                    )
            }
        return LifeIndex(
            kind = LifeIndexKind.UV,
            level = level,
            title = resources.getString(R.string.life_uv),
            summary = resources.getString(summaryRes),
            levelLabel = resources.getString(labelRes),
        )
    }

    private fun carWashIndex(
        resources: Resources,
        rainingNow: Boolean,
        maxPrecipProb: Int?,
        precipSum: Double,
        windKmh: Double?,
    ): LifeIndex {
        val level = carWashLevel(rainingNow, maxPrecipProb, precipSum, windKmh)
        val summaryRes =
            when (level) {
                LifeIndexLevel.POOR -> R.string.life_carwash_bad
                LifeIndexLevel.FAIR -> R.string.life_carwash_fair
                LifeIndexLevel.GOOD -> R.string.life_carwash_good
            }
        return LifeIndex(
            kind = LifeIndexKind.CAR_WASH,
            level = level,
            title = resources.getString(R.string.life_carwash),
            summary = resources.getString(summaryRes),
        )
    }

    private fun laundryIndex(
        resources: Resources,
        rainingNow: Boolean,
        maxPrecipProb: Int?,
        humidity: Int?,
        cloud: Int?,
        isDay: Boolean?,
    ): LifeIndex {
        val (level, summaryRes) =
            when {
                rainingNow || (maxPrecipProb ?: 0) >= 60 ->
                    LifeIndexLevel.POOR to R.string.life_laundry_bad
                (maxPrecipProb ?: 0) >= 35 ||
                    (humidity ?: 0) >= 85 ||
                    (cloud ?: 0) >= 85 ||
                    isDay == false ->
                    LifeIndexLevel.FAIR to R.string.life_laundry_fair
                else -> LifeIndexLevel.GOOD to R.string.life_laundry_good
            }
        return LifeIndex(
            kind = LifeIndexKind.LAUNDRY,
            level = level,
            title = resources.getString(R.string.life_laundry),
            summary = resources.getString(summaryRes),
        )
    }

    private fun sportIndex(
        resources: Resources,
        feelsC: Double?,
        rainingNow: Boolean,
        windKmh: Double?,
        aqi: Int?,
        uv: Double?,
    ): LifeIndex {
        val hotOrCold = feelsC != null && (feelsC >= 33 || feelsC <= 0)
        val badAir = aqi != null && aqi >= 100
        val strongWind = (windKmh ?: 0.0) >= 45
        val (level, summaryRes) =
            when {
                rainingNow || hotOrCold || badAir || strongWind ->
                    LifeIndexLevel.POOR to R.string.life_sport_bad
                (aqi != null && aqi >= 60) ||
                    (uv != null && uv >= WeatherCodes.UV_VERY_HIGH) ||
                    (feelsC != null && (feelsC >= 28 || feelsC <= 8)) ->
                    LifeIndexLevel.FAIR to R.string.life_sport_fair
                else -> LifeIndexLevel.GOOD to R.string.life_sport_good
            }
        return LifeIndex(
            kind = LifeIndexKind.SPORT,
            level = level,
            title = resources.getString(R.string.life_sport),
            summary = resources.getString(summaryRes),
        )
    }

    private fun coldIndex(
        resources: Resources,
        feelsC: Double?,
        windKmh: Double?,
        humidity: Int?,
        upcoming12: List<HourlyWeather>,
    ): LifeIndex {
        val temps = upcoming12.mapNotNull { it.temperatureC }
        val swing = if (temps.size >= 2) abs(temps.max() - temps.min()) else 0.0
        val chilly = feelsC != null && feelsC <= 10
        val dampCold = chilly && (humidity ?: 0) >= 75
        val windChill = chilly && (windKmh ?: 0.0) >= 25
        val (level, labelRes, summaryRes) =
            when {
                dampCold || windChill || swing >= 10 || (feelsC != null && feelsC <= 0) ->
                    Triple(
                        LifeIndexLevel.POOR,
                        R.string.life_cold_label_high,
                        R.string.life_cold_high,
                    )
                chilly || swing >= 6 ->
                    Triple(
                        LifeIndexLevel.FAIR,
                        R.string.life_cold_label_mid,
                        R.string.life_cold_mid,
                    )
                else ->
                    Triple(
                        LifeIndexLevel.GOOD,
                        R.string.life_cold_label_low,
                        R.string.life_cold_low,
                    )
            }
        return LifeIndex(
            kind = LifeIndexKind.COLD,
            level = level,
            title = resources.getString(R.string.life_cold),
            summary = resources.getString(summaryRes),
            levelLabel = resources.getString(labelRes),
        )
    }
}
