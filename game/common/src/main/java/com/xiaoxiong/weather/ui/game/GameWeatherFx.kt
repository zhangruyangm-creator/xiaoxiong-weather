package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/** 雷暴/冰雹闪电：两条随机亮线周期性闪现。跑酷/摘星/迷宫共用。 */
fun DrawScope.drawStormLightning(
    time: Float,
    s: Float,
    width: Float,
    height: Float,
) {
    if (sin(time * 6f) > 0.90f) {
        val bolt = Color(0xFFFFF59D).copy(alpha = 0.8f)
        drawLine(
            color = bolt,
            start = Offset(width * 0.70f, 0f),
            end = Offset(width * 0.58f, height * 0.18f),
            strokeWidth = 2.2f * s,
        )
        drawLine(
            color = bolt,
            start = Offset(width * 0.62f, height * 0.06f),
            end = Offset(width * 0.50f, height * 0.20f),
            strokeWidth = 1.4f * s,
        )
    }
}

/** 冻雨：冷白雨滴 + 稀疏冰晶圆点，比普通雨更细更透亮。跑酷/摘星/迷宫共用。 */
fun DrawScope.drawFreezingRain(
    time: Float,
    s: Float,
    width: Float,
    height: Float,
    yOff: Float = 0f,
    splash: Boolean = true,
) {
    drawRainLayered(
        fgCount = 34,
        bgCount = 16,
        time = time,
        width = width,
        height = height,
        scale = s,
        lenMul = 0.95f,
        alpha = 0.7f,
        yOff = yOff,
        fgColor = Color(0xFFC4D9F4),
        bgColor = Color(0xFFE3EEFA),
        splash = splash,
    )
    val crystal = Color.White.copy(alpha = 0.75f)
    for (i in 0 until 18) {
        val phase = ((i * 857) % 47) / 47f
        val x = ((i * 137 + time * 90f * (0.9f + phase * 0.4f)) % (width + 60)) - 30
        val y = ((i * 59 + time * 620f * s) % (height + 60)) - 30 + yOff
        val twinkle = 0.65f + 0.25f * (1f + sin(time * 2.6f + i * 1.7f)) * 0.5f
        drawCircle(
            crystal.copy(alpha = 0.75f * twinkle),
            (1.2f + phase * 1.1f) * s,
            Offset(x, y),
        )
    }
}

/** 冰雹：白色/半透明圆点密集砸落，下落快，部分带浅灰。跑酷/摘星/迷宫共用。 */
fun DrawScope.drawHailPellets(
    time: Float,
    s: Float,
    width: Float,
    height: Float,
    yOff: Float = 0f,
    count: Int = 52,
) {
    val light = Color.White.copy(alpha = 0.68f)
    val gray = Color(0xFFE4EAF1).copy(alpha = 0.7f)
    for (i in 0 until count) {
        val speed = 1500f * s + (i % 5) * 140f * s
        val x = ((i * 113 + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = ((i * 47 + time * speed) % (height + 90) + (height + 90)) % (height + 90) - 45 + yOff
        val size = (1.8f + (i % 4) * 0.6f) * s
        drawCircle(
            color = if (i % 3 == 0) gray else light,
            radius = size,
            center = Offset(x, y),
        )
    }
}

/**
 * 双层雨丝：远层细淡、近层「柔边 + 亮芯 + 滴头」，斜向有风感。
 *
 * [splash]：落地水花。竖屏滚动玩法（摘星跳）建议关掉，避免底部假地面水花。
 */
fun DrawScope.drawRainLayered(
    fgCount: Int,
    bgCount: Int,
    time: Float,
    width: Float,
    height: Float,
    scale: Float,
    lenMul: Float = 1f,
    alpha: Float = 0.55f,
    yOff: Float = 0f,
    fgColor: Color = Color(0xFFB8D6F5),
    bgColor: Color = Color(0xFFD5E8FA),
    splash: Boolean = true,
) {
    val a = alpha.coerceIn(0f, 1f)
    val bgCol = bgColor.copy(alpha = a * 0.40f)
    val fgSoft = fgColor.copy(alpha = a * 0.58f)
    val fgCore = Color.White.copy(alpha = a * 0.48f)
    val tipCol = Color.White.copy(alpha = a * 0.62f)
    val splashCol = fgColor.copy(alpha = a * 0.36f)
    val splashThreshold = height * 0.88f + yOff
    val wind = 6.2f * scale

    // 远雨：更长、更斜、轻摆，营造纵深
    for (i in 0 until bgCount) {
        val phase = ((i * 733) % 83) / 83f
        val lenFactor = 0.75f + ((i * 397) % 5) / 5f * 0.55f
        val speed = 700f * scale * (0.85f + phase * 0.45f)
        val x = ((i * 131 + time * (speed * 0.42f)) % (width + 70)) - 35
        val y = ((i * 71 + time * speed) % (height + 100) + (height + 100)) % (height + 100) - 50 + yOff
        val sway = sin(time * 1.4f + i * 0.55f) * 1.4f * scale
        val dropLen = 20f * scale * lenMul * lenFactor
        val tilt = wind * (0.7f + phase * 0.35f)
        drawLine(
            color = bgCol,
            start = Offset(x + sway, y),
            end = Offset(x + sway - tilt, y + dropLen),
            strokeWidth = 1.05f * scale,
            cap = StrokeCap.Round,
        )
    }

    // 近雨：柔边宽线 + 亮芯 + 滴头，读起来才像「雨」而不是噪点
    for (i in 0 until fgCount) {
        val phase = ((i * 1049) % 97) / 97f
        val speed = 1180f * scale * (0.88f + phase * 0.55f)
        val lenFactor = 0.85f + ((i * 593) % 7) / 7f * 0.5f
        val x = ((i * 97 + time * (speed * 0.48f)) % (width + 70)) - 35
        val y = ((i * 53 + time * speed) % (height + 100) + (height + 100)) % (height + 100) - 50 + yOff
        val sway = sin(time * 1.8f + i * 0.9f) * 1.8f * scale
        val dropLen = 26f * scale * lenMul * lenFactor
        val tilt = wind * (0.95f + phase * 0.4f)
        val tailX = x + sway
        val headX = tailX - tilt
        val headY = y + dropLen

        drawLine(
            color = fgSoft,
            start = Offset(tailX, y),
            end = Offset(headX, headY),
            strokeWidth = 2.35f * scale,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = fgCore,
            start = Offset(tailX - tilt * 0.08f, y + dropLen * 0.08f),
            end = Offset(headX, headY),
            strokeWidth = 1.05f * scale,
            cap = StrokeCap.Round,
        )
        drawCircle(tipCol, 1.25f * scale, Offset(headX, headY))

        if (splash && y > splashThreshold && i % 3 == 0) {
            val splashY = splashThreshold + 1.5f * scale
            val spread = (2.2f + phase * 2.8f) * scale
            drawCircle(splashCol, 1.1f * scale, Offset(headX, splashY))
            drawCircle(splashCol, 0.85f * scale, Offset(headX - spread, splashY - 1.2f * scale))
            drawCircle(splashCol, 0.8f * scale, Offset(headX + spread * 0.85f, splashY - 0.8f * scale))
        }
    }
}
