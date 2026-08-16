package com.xiaoxiong.weather.ui.weather

/** 天气场景分类：驱动天气图标与背景动画的绘制分支。 */
enum class WeatherScene {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDER,
    HAIL,
    FREEZING_RAIN,
    SNOW,
    HEAVY_SNOW,
    UNKNOWN,
}

fun weatherSceneOf(
    code: Int?,
    isDay: Boolean?,
): WeatherScene =
    when (code) {
        0, 1 -> WeatherScene.CLEAR
        2 -> WeatherScene.PARTLY_CLOUDY
        3 -> WeatherScene.OVERCAST
        45, 48 -> WeatherScene.FOG
        51, 53, 80 -> WeatherScene.DRIZZLE
        55, 61, 63, 81 -> WeatherScene.RAIN
        65, 82 -> WeatherScene.HEAVY_RAIN
        56, 57, 66, 67 -> WeatherScene.FREEZING_RAIN
        95, 96 -> WeatherScene.THUNDER
        99 -> WeatherScene.HAIL
        71, 73, 77, 85 -> WeatherScene.SNOW
        75, 86 -> WeatherScene.HEAVY_SNOW
        else -> WeatherScene.UNKNOWN
    }
