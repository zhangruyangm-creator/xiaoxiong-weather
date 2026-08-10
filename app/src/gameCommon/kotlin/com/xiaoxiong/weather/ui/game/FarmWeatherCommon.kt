package com.xiaoxiong.weather.ui.game

/** 游戏天气分类（由 App 天气码映射），4 个小游戏共用。 */
enum class FarmWeather {
    SUNNY,
    CLOUDY,
    RAINY,
    STORMY,
    SNOWY,
    FOGGY,
}

/** 降水强度：按 App 的 displayCode（已含雨强归一化）细分，用于控制游戏雨点密度。 */
enum class RainStrength { NONE, LIGHT, MODERATE, HEAVY }

/**
 * 由 sceneCode 推断降水强度。sceneCode 来自 [com.xiaoxiong.weather.util.WeatherCodes.displayCode]，
 * 已把雨量归一化到细分码：51/53/80 小雨，55/61/63/81 中雨，65/82 大雨，95+ 雷暴/冰雹按大雨算。
 */
fun rainStrengthOf(sceneCode: Int?): RainStrength =
    when (sceneCode) {
        51, 53, 80 -> RainStrength.LIGHT
        55, 61, 63, 81 -> RainStrength.MODERATE
        65, 82, in 95..99 -> RainStrength.HEAVY
        else -> RainStrength.NONE
    }

/** 冻雨码：56/57 冻毛毛雨，66/67 冻雨。 */
fun isFreezingRainCode(sceneCode: Int?): Boolean = sceneCode in setOf(56, 57, 66, 67)

/** 冰雹码：96/99 雷暴伴冰雹（95 为普通雷暴）。 */
fun isHailCode(sceneCode: Int?): Boolean = sceneCode in setOf(96, 99)

fun farmWeatherFromScene(
    sceneCode: Int?,
    isDay: Boolean?,
): FarmWeather {
    val code = sceneCode ?: return if (isDay == false) FarmWeather.CLOUDY else FarmWeather.SUNNY
    return when (code) {
        in 95..99 -> FarmWeather.STORMY
        in 71..86, 77 -> FarmWeather.SNOWY
        in 45..48 -> FarmWeather.FOGGY
        in 51..67, 80, 81, 82 -> FarmWeather.RAINY
        0, 1 -> if (isDay == false) FarmWeather.CLOUDY else FarmWeather.SUNNY
        2, 3 -> FarmWeather.CLOUDY
        else -> FarmWeather.CLOUDY
    }
}
