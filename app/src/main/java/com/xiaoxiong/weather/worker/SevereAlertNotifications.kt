package com.xiaoxiong.weather.worker

import com.xiaoxiong.weather.data.AlertKind
import com.xiaoxiong.weather.data.AlertSeverity
import com.xiaoxiong.weather.data.WeatherAlertGenerator

/**
 * 除临近雨雪外，即使证据只到 WATCH 也值得后台提醒的严重天气类型。
 * 这些预警（高温/寒潮/雾/沙尘/大风/UV/积水等）此前被 Worker 只挑
 * [AlertKind.RAIN_SOON]/[AlertKind.SNOW_SOON] 的逻辑整类漏掉。
 */
internal val SEVERE_NOTIFY_KINDS: Set<AlertKind> =
    setOf(
        AlertKind.STORM,
        AlertKind.HAIL,
        AlertKind.HEAVY_RAIN,
        AlertKind.PONDING,
        AlertKind.SNOW,
        AlertKind.SNOWY_ROAD,
        AlertKind.FREEZING_RAIN,
        AlertKind.FOG,
        AlertKind.LOW_VISIBILITY,
        AlertKind.WIND,
        AlertKind.HEAT,
        AlertKind.HEAT_WAVE,
        AlertKind.COLD,
        AlertKind.COLD_WAVE,
        AlertKind.COOLDOWN,
        AlertKind.UV,
        AlertKind.AIR_QUALITY,
        AlertKind.DUST,
    )

/**
 * 后台通知候选：所有 WARNING 级预警 + [SEVERE_NOTIFY_KINDS] 的 WATCH/INFO 提醒，
 * 并保留历史行为中会推送的临近降雨/降雪。普通「正在下雨/下雪」与 CLEAR 不在此列。
 */
internal fun selectSevereNotificationSpecs(specs: List<WeatherAlertGenerator.Spec>): List<WeatherAlertGenerator.Spec> =
    specs
        .filter { spec ->
            spec.severity == AlertSeverity.WARNING ||
                spec.kind in SEVERE_NOTIFY_KINDS ||
                spec.kind == AlertKind.RAIN_SOON ||
                spec.kind == AlertKind.SNOW_SOON
        }.distinctBy { it.kind to it.titleRes }

/** 推送去重签名：城市 + 类型 + 文案；不同预警互不误伤，同名预警 6 小时内只发一次。 */
internal fun severeNotificationSignature(
    cityName: String,
    spec: WeatherAlertGenerator.Spec,
): String = "$cityName|${spec.kind}|${spec.titleRes}"
