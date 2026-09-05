package com.xiaoxiong.weather.ui.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.dailyVariantIndex
import java.time.LocalDate
import kotlin.math.sin
import com.xiaoxiong.weather.resillustrations.R as IllustR

/**
 * 阳台观景：插画主视觉 + 天气旁白；雨雪雷暴叠加轻粒子，不再用剪影城市。
 */
@Composable
internal fun HomeBalcony(
    sceneCode: Int?,
    isDay: Boolean?,
    date: LocalDate = LocalDate.now(),
    onGoStreet: () -> Unit = {},
) {
    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val night = isDay == false
    val artRes = remember(weather, night, date) { balconySceneArt(weather, night, date) }
    val labelRes = balconyWeatherLabel(weather, night)
    val tipRes = balconyWeatherTip(weather, night)

    val timeState = remember { mutableFloatStateOf(0f) }
    val needsFx =
        weather == FarmWeather.RAINY ||
            weather == FarmWeather.STORMY ||
            weather == FarmWeather.SNOWY
    LaunchedEffect(needsFx) {
        if (!needsFx) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                val dt =
                    if (last == Long.MIN_VALUE) {
                        1f / 60f
                    } else {
                        ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    }
                last = now
                // 只在 draw 里读 floatValue，避免带动 Image/文案每帧重组
                timeState.floatValue += dt
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF6E8))) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(artRes),
                    contentDescription = stringResource(R.string.bear_home_room_balcony),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                if (needsFx) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val time = timeState.floatValue.toDouble()
                        when (weather) {
                            FarmWeather.RAINY ->
                                drawBalconyRain(time, size.width, size.height, 28, 0.85f, 0.35f)
                            FarmWeather.STORMY -> {
                                drawBalconyRain(time, size.width, size.height, 40, 1.1f, 0.4f)
                                drawBalconyLightning(time, size.width, size.height)
                            }
                            FarmWeather.SNOWY ->
                                drawBalconySnow(time, size.width, size.height, 26)
                            else -> Unit
                        }
                    }
                }
                // 天气标签：浅底，避免压死插画
                Text(
                    text = stringResource(labelRes),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 72.dp, start = 16.dp)
                            .background(Color(0x99FFF8E7), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4E342E),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(tipRes),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5D4037),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onGoStreet) {
                Text(stringResource(R.string.bear_home_street_downstairs))
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        // 底部淡出，和小屋其它房间一致
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x00FFF6E8), Color(0xFFFFF6E8)),
                        ),
                    ),
        )
    }
}

/**
 * 按天气/昼夜选阳台主图：白天与恶劣天气用新绘本图；晴夜/多云夜轮换既有晚间阳台池。
 */
@DrawableRes
internal fun balconySceneArt(
    weather: FarmWeather,
    isNight: Boolean,
    date: LocalDate,
): Int =
    when (weather) {
        FarmWeather.SUNNY ->
            if (isNight) {
                pick(date, "balcony_clear_night", BALCONY_CLEAR_NIGHT)
            } else {
                R.drawable.ic_bear_home_balcony_sunny_day
            }
        FarmWeather.CLOUDY ->
            if (isNight) {
                pick(date, "balcony_cloudy_night", BALCONY_CLOUDY_NIGHT)
            } else {
                R.drawable.ic_bear_home_balcony_cloudy
            }
        FarmWeather.RAINY -> R.drawable.ic_bear_home_balcony_rain
        FarmWeather.STORMY -> R.drawable.ic_bear_home_balcony_storm
        FarmWeather.SNOWY ->
            if (isNight) {
                IllustR.drawable.ic_sun_bear_evening_snowwindow
            } else {
                R.drawable.ic_bear_home_balcony_snow
            }
        FarmWeather.FOGGY -> R.drawable.ic_bear_home_balcony_fog
    }

@StringRes
internal fun balconyWeatherLabel(
    weather: FarmWeather,
    isNight: Boolean,
): Int =
    when (weather) {
        FarmWeather.SUNNY ->
            if (isNight) R.string.balcony_weather_clear_night else R.string.balcony_weather_sunny
        FarmWeather.CLOUDY -> R.string.balcony_weather_cloudy
        FarmWeather.RAINY -> R.string.balcony_weather_rain_light
        FarmWeather.STORMY -> R.string.balcony_weather_storm
        FarmWeather.SNOWY -> R.string.balcony_weather_snow
        FarmWeather.FOGGY -> R.string.balcony_weather_fog
    }

@StringRes
internal fun balconyWeatherTip(
    weather: FarmWeather,
    isNight: Boolean,
): Int =
    when (weather) {
        FarmWeather.SUNNY ->
            if (isNight) {
                R.string.bear_home_balcony_tip_clear_night
            } else {
                R.string.bear_home_balcony_tip_sunny
            }
        FarmWeather.CLOUDY -> R.string.bear_home_balcony_tip_cloudy
        FarmWeather.RAINY -> R.string.bear_home_balcony_tip_rain
        FarmWeather.STORMY -> R.string.bear_home_balcony_tip_storm
        FarmWeather.SNOWY -> R.string.bear_home_balcony_tip_snow
        FarmWeather.FOGGY -> R.string.bear_home_balcony_tip_fog
    }

private fun pick(
    date: LocalDate,
    salt: String,
    pool: IntArray,
): Int = pool[dailyVariantIndex(date, salt, pool.size)]

private val BALCONY_CLEAR_NIGHT =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_evening_balconybreeze,
        IllustR.drawable.ic_sun_bear_evening_balconymoon,
        IllustR.drawable.ic_sun_bear_evening_balconynight,
        IllustR.drawable.ic_sun_bear_evening_balconyapartment,
        IllustR.drawable.ic_sun_bear_evening_balconysunset,
        IllustR.drawable.ic_sun_bear_evening_balconyphoto,
    )

private val BALCONY_CLOUDY_NIGHT =
    intArrayOf(
        IllustR.drawable.ic_sun_bear_evening_balcony,
        IllustR.drawable.ic_sun_bear_evening_balconyhang,
        IllustR.drawable.ic_sun_bear_evening_balconystreet,
        IllustR.drawable.ic_sun_bear_evening_balconyroom,
    )

private fun DrawScope.drawBalconyRain(
    time: Double,
    w: Float,
    h: Float,
    count: Int,
    lenMul: Float,
    alpha: Float,
) {
    val wd = w.toDouble()
    val hd = h.toDouble()
    for (i in 0 until count) {
        val speed = 900.0 + (i % 4) * 150.0
        val x = (((i * 97.0 + time * speed * 0.55) % (wd + 40.0)) - 20.0).toFloat()
        val y = ((((i * 53.0 + time * speed) % (hd + 60.0) + (hd + 60.0)) % (hd + 60.0)) - 30.0).toFloat()
        val wobble = (sin(time * 3.0 + i * 1.1) * 2.5).toFloat()
        val dropLen = 14f * lenMul
        drawLine(
            color = Color(0xFFB4D2F0).copy(alpha = alpha),
            start = Offset(x + wobble, y),
            end = Offset(x + wobble - 3f, y + dropLen),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBalconyLightning(
    time: Double,
    w: Float,
    h: Float,
) {
    if (sin(time * 6.0) > 0.88) {
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.7f),
            start = Offset(w * 0.72f, 0f),
            end = Offset(w * 0.60f, h * 0.20f),
            strokeWidth = 2.5f,
        )
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.7f),
            start = Offset(w * 0.64f, h * 0.06f),
            end = Offset(w * 0.52f, h * 0.22f),
            strokeWidth = 1.6f,
        )
    }
}

private fun DrawScope.drawBalconySnow(
    time: Double,
    w: Float,
    h: Float,
    count: Int,
) {
    val wd = w.toDouble()
    val hd = h.toDouble()
    for (i in 0 until count) {
        val x = ((i * 83.0 + time * 20.0 + sin(time * 1.2 + i) * 15.0) % (wd + 30.0)).toFloat()
        val y = ((i * 61.0 + time * 45.0) % (hd + 30.0)).toFloat()
        val size = 2f + (i % 3) * 0.8f
        drawCircle(Color.White.copy(alpha = 0.75f), size, Offset(x, y))
    }
}
