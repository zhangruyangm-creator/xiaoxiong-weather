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

/**
 * 天气码 → 场景。分类本身与昼夜无关，昼夜差异在样式层
 * （[weatherSceneStyle] 的 CLEAR / PARTLY_CLOUDY 渐变）与图标层体现，
 * [isDay] 参数保留给调用方共用同一签名，避免各处重复改判。
 *
 * 映射要点：
 * - 51/53/55 均为 WMO 毛毛雨码 → DRIZZLE（55 为浓毛毛雨，量级仍属毛毛雨）；
 * - 80/81/82 为阵雨码 → RAIN / HEAVY_RAIN，与稳定雨分开绘制雨滴密度；
 * - 68/69（雨夹雪）→ SNOW：无独立 SLEET 场景，图标按雪绘制避免落到温度计兜底；
 * - 1（大体晴）与 0 合并为 CLEAR，启动图标另行区分 FEW_CLOUDS（见 WeatherLauncherIcon）。
 */
@Suppress("UNUSED_PARAMETER")
fun weatherSceneOf(
    code: Int?,
    isDay: Boolean?,
): WeatherScene =
    when (code) {
        0, 1 -> WeatherScene.CLEAR
        2 -> WeatherScene.PARTLY_CLOUDY
        3 -> WeatherScene.OVERCAST
        45, 48 -> WeatherScene.FOG
        51, 53, 55 -> WeatherScene.DRIZZLE
        61, 63, 80, 81 -> WeatherScene.RAIN
        65, 82 -> WeatherScene.HEAVY_RAIN
        56, 57, 66, 67 -> WeatherScene.FREEZING_RAIN
        95, 96 -> WeatherScene.THUNDER
        99 -> WeatherScene.HAIL
        68, 69, 71, 73, 77, 85 -> WeatherScene.SNOW
        75, 86 -> WeatherScene.HEAVY_SNOW
        else -> WeatherScene.UNKNOWN
    }
