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
            color = Color(0xFFFFF59D).copy(alpha = 0.8f),
            start = Offset(width * 0.70f, 0f),
            end = Offset(width * 0.58f, height * 0.18f),
            strokeWidth = 2.2f * s,
        )
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.8f),
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
    // 冰晶：小而淡的白点，下落比雨滴略慢；氛围点缀，不抢戏
    for (i in 0 until 18) {
        val phase = ((i * 857) % 47) / 47f
        val x = ((i * 137 + time * 90f * (0.9f + phase * 0.4f)) % (width + 60)) - 30
        val y = ((i * 59 + time * 620f * s) % (height + 60)) - 30 + yOff
        val twinkle = 0.65f + 0.25f * (1f + sin(time * 2.6f + i * 1.7f)) * 0.5f
        drawCircle(
            Color.White.copy(alpha = 0.75f * twinkle),
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
    for (i in 0 until count) {
        val speed = 1500f * s + (i % 5) * 140f * s
        val x = ((i * 113 + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = ((i * 47 + time * speed) % (height + 90) + (height + 90)) % (height + 90) - 45 + yOff
        val size = (1.8f + (i % 4) * 0.6f) * s
        drawCircle(
            color = if (i % 3 == 0) Color(0xFFE4EAF1).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.68f),
            radius = size,
            center = Offset(x, y),
        )
    }
}

/**
 * 双层雨 + 底部溅射：背景层细淡慢，前景层粗亮快。
 *
 * 雨滴质感：每滴由「细尾线 + 亮头圆点」组成（头圆尾尖），带随风倾斜；
 * 用伪随机相位打破规则网格分布，同一滴既有横向摆动又有下落速度差；
 * 落到画面底部 ~10% 区域时逐滴产生「溅起小竖线 + 左右散射点」的水花。
 *
 * 供跑酷/摘星/迷宫/接雨雪共用；纯函数，行为只由 time/scale 驱动。
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
    val splashThreshold = height * 0.86f + yOff

    // 背景层：远雨，细、淡、慢，随机相位让分布更散
    for (i in 0 until bgCount) {
        val speedBase = 620f * scale
        val phase = ((i * 733) % 83) / 83f // 0..1 伪随机相位
        val lenFactor = 0.7f + ((i * 397) % 5) / 5f
        val speed = speedBase * (0.85f + phase * 0.5f)
        val x = ((i * 131 + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = ((i * 71 + time * speed) % (height + 80) + (height + 80)) % (height + 80) - 40 + yOff
        val wobble = sin(time * 2.2f + i * 0.7f) * 2.2f * scale
        // 远雨：细丝（两端圆帽），向下略倾；远雨该比近雨更淡
        val tailX = x + wobble
        val tailY = y
        val headX = tailX - 2f * scale
        val headY = tailY + (13f * scale * lenMul * lenFactor)
        drawLine(
            color = bgColor.copy(alpha = alpha * 0.32f),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = 0.9f * scale,
            cap = StrokeCap.Round,
        )
    }

    // 前景层：近雨，粗、亮、更快，摆动更明显
    for (i in 0 until fgCount) {
        val phase = ((i * 1049) % 97) / 97f // 0..1 伪随机相位
        val speedBase = 1050f * scale
        val speed = speedBase * (0.9f + phase * 0.6f)
        val lenFactor = 0.8f + ((i * 593) % 7) / 7f
        val tiltX = 3.2f * scale + phase * 2.4f * scale // 随相位微调倾角，形成风感
        val x = ((i * 97 + time * (speed * 0.55f)) % (width + 60)) - 30
        val y = ((i * 53 + time * speed) % (height + 80) + (height + 80)) % (height + 80) - 40 + yOff
        val wobble = sin(time * 3.1f * (0.9f + phase * 0.4f) + i * 1.1f) * 2.6f * scale
        // 雨丝：一根细长线，两端圆帽收住即可。不做水滴头/高光——
        // 雨是背景氛围，主角是游戏本体，太显眼会喧宾夺主。
        val dropLen = 16f * scale * lenMul * lenFactor
        val lineWidth = 1.25f * scale
        val tailX = x + wobble
        val tailY = y
        val headX = tailX - tiltX
        val headY = tailY + dropLen
        drawLine(
            color = fgColor.copy(alpha = alpha * 0.8f),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )

        // 溅射水花：雨滴到达落地带时，在落点画「溅起小竖线 + 左右散射点」
        if (y > splashThreshold && i % 2 == 0) {
            val splashX = headX
            val splashY = splashThreshold + 2f * scale
            val splashH = (2.8f + phase * 2.2f) * scale
            drawLine(
                color = fgColor.copy(alpha = alpha * 0.38f),
                start = Offset(splashX, splashY),
                end = Offset(splashX, splashY - splashH),
                strokeWidth = 1.0f * scale,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = fgColor.copy(alpha = alpha * 0.3f),
                radius = 0.9f * scale,
                center = Offset(splashX - (1.8f + phase * 2.4f) * scale, splashY),
            )
            drawCircle(
                color = fgColor.copy(alpha = alpha * 0.3f),
                radius = 0.8f * scale,
                center = Offset(splashX + (1.4f + phase * 2f) * scale, splashY),
            )
        }
    }
}
