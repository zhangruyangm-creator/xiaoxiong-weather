package com.xiaoxiong.weather.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 天气文案的 Composable 查表层：把 [WeatherCodes] 的 `*Res` 码表映射为可组合文案。
 * 独立于 `:core:common` 存放（该模块保持纯 Kotlin/无 Compose），由本模块消费。
 */
@Composable
fun weatherCodeDescription(code: Int?): String {
    val res = WeatherCodes.descriptionRes(code)
    return if (WeatherCodes.isFallbackDescription(code, res) && code != null) {
        stringResource(res, code)
    } else {
        stringResource(res)
    }
}

@Composable
fun weatherCodeDescription(
    code: Int?,
    precipMmPerHour: Double?,
): String {
    val res = WeatherCodes.descriptionRes(code, precipMmPerHour)
    return if (WeatherCodes.isFallbackDescription(code, res) && code != null) {
        stringResource(res, code)
    } else {
        stringResource(res)
    }
}

@Composable
fun windDirectionText(deg: Int?): String {
    val res = windDirectionRes(deg) ?: return "--"
    return stringResource(res)
}

@Composable
fun aqiLabelText(europeanAqi: Int?): String = stringResource(aqiLabelRes(europeanAqi))

@Composable
fun uvLabelText(uv: Double?): String = stringResource(uvLabelRes(uv))
