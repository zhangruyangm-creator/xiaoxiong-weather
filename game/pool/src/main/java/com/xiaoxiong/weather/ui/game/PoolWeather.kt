package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.sin

// ---------------------------------------------------------------------
// 天气氛围（台面外侧）
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolWeather(
    game: PoolGame,
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    style: WeatherSceneStyle,
    isDay: Boolean,
    /** 减弱动画：跳过粒子/闪电，仅保留静态底色与光晕。 */
    reduceMotion: Boolean = false,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val w = size.width
    val h = size.height
    val time = game.time

    // 场景底色：整屏铺但极淡，给台外一点氛围，不抢台面
    sceneBrushes.overlay(style, isDay)?.let {
        drawRect(it, topLeft = Offset.Zero, size = Size(w, h))
    }

    // 冻雨 / 冰雹优先识别
    if (freezingRain) {
        drawRect(weatherFreezingTint)
        drawFreezingRain(time, 1f, w, h, splash = false, reduceMotion = reduceMotion)
        return
    }
    if (hail) {
        drawRect(weatherHailTint)
        drawRainLayered(
            fgCount = 16,
            bgCount = 8,
            time = time,
            width = w,
            height = h,
            scale = 1f,
            lenMul = 1.05f,
            alpha = 0.42f,
            splash = false,
            reduceMotion = reduceMotion,
        )
        drawHailPellets(time, 1f, w, h, count = 52, reduceMotion = reduceMotion)
        drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        return
    }

    when (weather) {
        FarmWeather.RAINY -> {
            drawRect(weatherRainTint)
            drawPoolRain(time, w, h, rain, reduceMotion)
        }
        FarmWeather.STORMY -> {
            drawRect(weatherStormTint)
            drawPoolRain(time, w, h, RainStrength.HEAVY, reduceMotion)
            drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        }
        FarmWeather.SNOWY -> {
            drawRect(weatherSnowTint)
            if (!reduceMotion) drawPoolSnow(time, w, h)
        }
        FarmWeather.FOGGY -> {
            drawRect(weatherFogTint)
            if (!reduceMotion) drawPoolFog(time, w, h)
        }
        FarmWeather.SUNNY -> drawPoolSunGlow(w, h, isDay)
        FarmWeather.CLOUDY -> if (!reduceMotion) drawPoolClouds(time, w, h, isDay)
    }
}

private fun DrawScope.drawPoolRain(
    time: Float,
    w: Float,
    h: Float,
    rain: RainStrength,
    reduceMotion: Boolean,
) {
    val (fg, bg, alpha) =
        when (rain) {
            RainStrength.LIGHT -> Triple(14, 7, 0.38f)
            RainStrength.MODERATE -> Triple(26, 13, 0.48f)
            RainStrength.HEAVY -> Triple(40, 20, 0.58f)
            RainStrength.NONE -> Triple(10, 5, 0.32f)
        }
    drawRainLayered(
        fgCount = fg,
        bgCount = bg,
        time = time,
        width = w,
        height = h,
        scale = 1f,
        lenMul = 1.05f,
        alpha = alpha,
        splash = false,
        reduceMotion = reduceMotion,
    )
}

private fun DrawScope.drawPoolSnow(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 44) {
        val phase = ((i * 67) % 29) / 29f
        val speed = 22f + phase * 30f
        val x = ((i * 83 + time * speed * 0.6f) % (w + 40f)) - 20f
        val y = ((i * 61 + time * speed) % (h + 40f)) - 20f
        val sway = sin(time * 1.2f + i) * 6f
        val r = 1.5f + (i % 4) * 0.7f
        drawCircle(Color.White.copy(alpha = 0.50f), r, Offset(x + sway, y))
    }
}

private fun DrawScope.drawPoolFog(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 5) {
        val y = h * (0.14f + i * 0.17f) + sin(time * 0.4f + i * 1.3f) * 8f
        val x = ((i * 211f + time * 9f) % (w + 260f)) - 130f
        drawOval(
            Color.White.copy(alpha = 0.07f),
            topLeft = Offset(x - w * 0.42f, y - h * 0.05f),
            size = Size(w * 0.84f, h * 0.10f),
        )
    }
}

private fun DrawScope.drawPoolSunGlow(
    w: Float,
    h: Float,
    isDay: Boolean,
) {
    if (!isDay) return
    val radius = w * 0.55f
    drawCircle(
        sceneBrushes.sunGlow(w, h),
        radius = radius,
        center = Offset(w * 0.88f, h * 0.05f),
    )
}

private fun DrawScope.drawPoolClouds(
    time: Float,
    w: Float,
    h: Float,
    isDay: Boolean,
) {
    val cloud = if (isDay) Color.White else Color(0xFF93A9CE)
    for (i in 0 until 4) {
        val x = ((i * 210f + time * 10f) % (w + 180f)) - 90f
        val y = h * (0.04f + i * 0.03f)
        val a = 0.10f + i * 0.015f
        drawCircle(cloud.copy(alpha = a), w * 0.10f, Offset(x, y))
        drawCircle(cloud.copy(alpha = a), w * 0.08f, Offset(x + w * 0.07f, y + h * 0.008f))
        drawCircle(cloud.copy(alpha = a), w * 0.07f, Offset(x - w * 0.07f, y + h * 0.006f))
    }
}
