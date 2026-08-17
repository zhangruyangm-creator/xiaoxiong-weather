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
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.85f),
            start = Offset(width * 0.70f, 0f),
            end = Offset(width * 0.58f, height * 0.18f),
            strokeWidth = 2.5f * s,
        )
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.85f),
            start = Offset(width * 0.62f, height * 0.06f),
            end = Offset(width * 0.50f, height * 0.20f),
            strokeWidth = 1.6f * s,
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
) {
    drawRainLayered(
        fgCount = 34,
        bgCount = 16,
        time = time,
        width = width,
        height = height,
        scale = s,
        lenMul = 0.9f,
        alpha = 0.65f,
        yOff = yOff,
        fgColor = Color(0xFFC4D9F4),
        bgColor = Color(0xFFE3EEFA),
    )
    // 冰晶：小而亮的白点，下落比雨滴略慢
    for (i in 0 until 18) {
        val x = ((i * 137 + time * 90f) % (width + 40)) - 20
        val y = ((i * 59 + time * 620f * s) % (height + 60)) - 30 + yOff
        drawCircle(Color.White.copy(alpha = 0.9f), (1.6f + i % 3) * s, Offset(x, y))
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
    for (i in 0 until count) {
        val speed = 1500f * s + (i % 5) * 140f * s
        val x = ((i * 113 + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = ((i * 47 + time * speed) % (height + 90) + (height + 90)) % (height + 90) - 45 + yOff
        val size = (2.8f + (i % 4) * 0.8f) * s
        drawCircle(
            color = if (i % 3 == 0) Color(0xFFE4EAF1).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.85f),
            radius = size,
            center = Offset(x, y),
        )
    }
}

/**
 * 双层雨 + 底部溅射：背景层细淡慢，前景层粗亮快，雨滴带微摆动；
 * 落到画面底部 10% 区域时产生小水花粒子，增强真实感。
 * 供跑酷/摘星/迷宫共用。
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
    fgColor: Color = Color(0xFFB4D2F0),
    bgColor: Color = Color(0xFFD9E9F8),
) {
    val splashThreshold = height * 0.88f + yOff

    // 背景层：细、淡、稍慢，带微摆动
    for (i in 0 until bgCount) {
        val speed = 700f * scale + (i % 3) * 110f * scale
        val x = ((i * 131 + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = ((i * 71 + time * speed) % (height + 80) + (height + 80)) % (height + 80) - 40 + yOff
        val wobble = sin(time * 2.2f + i * 0.7f) * 1.8f * scale
        val dropLen = 9f * scale * lenMul
        drawLine(
            color = bgColor.copy(alpha = alpha * 0.4f),
            start = Offset(x + wobble, y),
            end = Offset(x + wobble - 2.2f * scale, y + dropLen),
            strokeWidth = 1.2f * scale,
            cap = StrokeCap.Round,
        )
    }

    // 前景层：粗、亮、更快，带更明显的摆动
    for (i in 0 until fgCount) {
        val speed = 1080f * scale + (i % 4) * 160f * scale
        val x = ((i * 97 + time * (speed * 0.55f)) % (width + 60)) - 30
        val y = ((i * 53 + time * speed) % (height + 80) + (height + 80)) % (height + 80) - 40 + yOff
        val wobble = sin(time * 3.1f + i * 1.1f) * 2.8f * scale
        val dropLen = 15f * scale * lenMul
        drawLine(
            color = fgColor.copy(alpha = alpha),
            start = Offset(x + wobble, y),
            end = Offset(x + wobble - 3.5f * scale, y + dropLen),
            strokeWidth = 2.1f * scale,
            cap = StrokeCap.Round,
        )
        // 溅射粒子：雨滴到达底部区域时画一个小圆点水花
        if (y > splashThreshold && i % 2 == 0) {
            drawCircle(
                color = fgColor.copy(alpha = alpha * 0.35f),
                radius = 1.5f * scale,
                center = Offset(x + wobble, splashThreshold + 2f * scale),
            )
        }
    }
}
