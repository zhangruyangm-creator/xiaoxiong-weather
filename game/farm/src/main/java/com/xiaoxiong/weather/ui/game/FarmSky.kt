package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 农场天空时段：昼夜氛围独立于天气效果，只影响画面，不影响生长数值。 */
enum class FarmSkyPhase { DAY, DUSK, NIGHT }

/**
 * 依据白天/夜晚与本地钟点估算时段；黄昏与清晨共用 DUSK 暖色过渡。
 * 晚上 17–20 点与 5–7 点为黄昏/清晨，其余夜间为夜色。
 */
fun farmSkyPhase(isDay: Boolean?): FarmSkyPhase {
    if (isDay != false) return FarmSkyPhase.DAY
    val hour = LocalTime.now().hour
    return if (hour in 5 until 7 || hour in 17 until 20) {
        FarmSkyPhase.DUSK
    } else {
        FarmSkyPhase.NIGHT
    }
}

/** 各时段的天空三色渐变（顶/中/底）；白天保留天气自己的配色。 */
data class FarmSkyGradient(
    val top: Long,
    val mid: Long,
    val bottom: Long,
)

fun farmSkyGradient(phase: FarmSkyPhase): FarmSkyGradient =
    when (phase) {
        FarmSkyPhase.DAY -> FarmSkyGradient(0xFF3D8FD6, 0xFF8FC6EA, 0xFFD9EFFB)
        FarmSkyPhase.DUSK -> FarmSkyGradient(0xFF43305E, 0xFF8E5F8F, 0xFFF2A65E)
        FarmSkyPhase.NIGHT -> FarmSkyGradient(0xFF070B1E, 0xFF16233F, 0xFF2C3E5E)
    }

internal fun DrawScope.drawFarmAtmosphere(
    weather: FarmWeather,
    time: Float,
    phase: FarmSkyPhase,
    /** 减弱动画：跳过雨雪/雷闪/飘云/萤火虫等动态氛围，保留天空底色与静态日月。 */
    reduceMotion: Boolean = false,
) {
    // 夜晚/黄昏的天空装饰（星星、月亮/暮光、萤火虫），降水时云层遮挡星空
    if (phase != FarmSkyPhase.DAY) {
        drawNightSky(weather, time, phase, reduceMotion)
    }
    when (weather) {
        FarmWeather.RAINY, FarmWeather.STORMY -> {
            if (reduceMotion) return
            for (i in 0 until 50) {
                val x = ((i * 97 + time * 90) % (size.width + 40)) - 20
                val y = ((i * 53 + time * 240) % (size.height + 60)) - 30
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x, y),
                    Offset(x - 3f, y + 12f),
                    strokeWidth = 2f,
                )
            }
            if (weather == FarmWeather.STORMY && sin(time * 8f) > 0.92f) {
                drawRect(Color.White.copy(alpha = 0.15f))
            }
        }
        FarmWeather.SNOWY -> {
            if (reduceMotion) return
            for (i in 0 until 40) {
                val x = ((i * 83 + time * 25) % (size.width + 20))
                val y = ((i * 61 + time * 40) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.75f), 2f + i % 3, Offset(x, y))
            }
        }
        FarmWeather.SUNNY -> {
            if (phase == FarmSkyPhase.DAY) {
                val sun = Offset(size.width * 0.88f, 70f)
                drawSoftGlow(sun.x, sun.y, 90f, Color(0xFFFFE082))
                drawCircle(Color(0xFFFFE082), 26f, sun)
                if (reduceMotion) return
                for (i in 0 until 10) {
                    val a = (Math.PI * 2 * i / 10 + time * 0.3).toFloat()
                    drawLine(
                        Color(0xFFFFD54F),
                        sun + Offset(cos(a) * 32f, sin(a) * 32f),
                        sun + Offset(cos(a) * 42f, sin(a) * 42f),
                        strokeWidth = 3f,
                    )
                }
            }
        }
        FarmWeather.FOGGY -> {
            val fogColor =
                when (phase) {
                    FarmSkyPhase.DAY -> Color.White
                    FarmSkyPhase.DUSK -> Color(0xFFE9C9A3)
                    FarmSkyPhase.NIGHT -> Color(0xFFAFC2E0)
                }
            drawRect(fogColor.copy(alpha = 0.18f))
        }
        FarmWeather.CLOUDY -> {
            if (reduceMotion) return
            val cloudColor =
                when (phase) {
                    FarmSkyPhase.DAY -> Color.White
                    FarmSkyPhase.DUSK -> Color(0xFFE8C9A8)
                    FarmSkyPhase.NIGHT -> Color(0xFF93A9CE)
                }
            for (i in 0 until 4) {
                val x = ((i * 160f + time * 12f) % (size.width + 120f)) - 60f
                val y = 60f + i * 18f
                drawCircle(cloudColor.copy(alpha = 0.45f), 26f, Offset(x, y))
                drawCircle(cloudColor.copy(alpha = 0.45f), 20f, Offset(x + 22f, y + 4f))
            }
        }
    }
}

/** 夜晚/黄昏的天空装饰：星星闪烁、月亮/暮光、萤火虫。 */
internal fun DrawScope.drawNightSky(
    weather: FarmWeather,
    time: Float,
    phase: FarmSkyPhase,
    reduceMotion: Boolean = false,
) {
    // 晴/多云才有星空；雨、雪、雷暴、雾视为云层遮挡
    val starVisible = weather == FarmWeather.SUNNY || weather == FarmWeather.CLOUDY
    if (phase == FarmSkyPhase.NIGHT && starVisible && !reduceMotion) {
        val cell = 72f
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height * 0.55f / cell).toInt() + 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val h1 = abs((c * 73856093) xor (r * 19349663))
                val h2 = abs((c * 83492791) xor (r * 29712151))
                val px = c * cell + (h1 % 1000) / 1000f * cell
                val py = r * cell + (h2 % 1000) / 1000f * cell
                if (py > size.height * 0.55f) continue
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 1.6f + (h1 % 628) / 100f))
                if (h1 % 9 == 0) {
                    // 亮星带十字星芒
                    val cr = 2.2f + (h2 % 30) / 12f
                    drawLine(Color.White, Offset(px - cr, py), Offset(px + cr, py), strokeWidth = 1.1f, alpha = twinkle)
                    drawLine(Color.White, Offset(px, py - cr), Offset(px, py + cr), strokeWidth = 1.1f, alpha = twinkle)
                } else {
                    drawCircle(Color.White, 1f + (h2 % 3) * 0.55f, Offset(px, py), alpha = twinkle)
                }
            }
        }
    }

    val clearSky =
        weather == FarmWeather.SUNNY ||
            weather == FarmWeather.CLOUDY ||
            weather == FarmWeather.FOGGY
    if (phase == FarmSkyPhase.NIGHT && clearSky) {
        // 月亮：柔光 + 本体 + 淡淡的环形山
        val moon = Offset(size.width * 0.80f, size.height * 0.12f)
        drawSoftGlow(moon.x, moon.y, 96f, Color(0xFFFFF8E1))
        drawCircle(Color(0xFFFFF8E1), 24f, moon, alpha = 0.95f)
        drawCircle(Color(0xFFEADFBE), 4f, moon + Offset(-7f, 4f), alpha = 0.7f)
        drawCircle(Color(0xFFEADFBE), 2.6f, moon + Offset(6f, -6f), alpha = 0.6f)
        drawCircle(Color(0xFFEADFBE), 2f, moon + Offset(8f, 7f), alpha = 0.5f)
    }

    if (phase == FarmSkyPhase.DUSK) {
        // 黄昏：低垂的暖阳与柔光，暮色渐起
        val sun = Offset(size.width * 0.5f, size.height * 0.30f)
        drawSoftGlow(sun.x, sun.y, 130f, Color(0xFFFFC078))
        drawCircle(Color(0xFFFFE0B2), 26f, sun, alpha = 0.9f)
        drawCircle(Color(0xFFFFF3E0), 18f, sun, alpha = 0.8f)
    }

    // 萤火虫：晴朗夜色里缓慢飘动 + 呼吸光
    if (phase == FarmSkyPhase.NIGHT && clearSky && !reduceMotion) {
        for (i in 0 until 10) {
            val seed = i * 29
            val baseX = (seed * 47 % (size.width * 0.92f).toInt()) + size.width * 0.04f
            val baseY = size.height * (0.55f + (seed % 12) / 28f)
            val wobX = sin(time * (1.1f + i % 3 * 0.35f) + seed) * 16f
            val wobY = cos(time * (0.8f + i % 2 * 0.2f) + seed) * 10f
            val pulse = 0.5f + 0.5f * sin(time * 2.6f + seed * 1.7f)
            val pos = Offset(baseX + wobX, baseY + wobY)
            drawCircle(Color(0xFFFFE082).copy(alpha = 0.10f + 0.20f * pulse), 7f, pos)
            drawCircle(Color(0xFFFFF59D).copy(alpha = 0.45f + 0.45f * pulse), 1.7f, pos)
        }
    }
}
