package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/*
 * 天气特效共享层（跑酷/摘星/接雨雪/迷宫/台球共用）。
 *
 * 设计原则：**确定性伪随机**（同一 seed 每帧结果一致，不闪烁、不掉帧）+ **拒绝均匀**
 * —— 用阵风、雨势起伏、逐滴随机把雨丝打散；闪电做成「事件」而非固定直线。
 */

/** 确定性伪随机 0..1（同一 (i,salt) 永远同值），避免逐帧抖动与规律感。 */
private fun hash01(
    i: Int,
    salt: Int = 0,
): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}

/**
 * 正模运算：结果恒落在 `[0, span)`（与 `%` 对负数的语义不同）。
 * 雨/雹的纵向位置要把 [yOff]（如摘星的高空视差）一并纳入取模，
 * 否则整体偏移会把粒子移出屏幕。
 */
internal fun positiveModulo(
    value: Float,
    span: Float,
): Float {
    if (span <= 0f) return 0f
    val r = value % span
    return if (r < 0f) r + span else r
}

/**
 * 云底辉光笔刷缓存：以原点为中心缓存，半径不变时复用；
 * 亮度包络 [env] 通过 drawCircle 的 alpha 施加，不再每帧重建带 alpha 的 Brush。
 */
private class StormGlowBrushCache {
    private var radius = Float.NaN
    private var brush: Brush? = null

    fun get(radius: Float): Brush {
        val cached = brush
        if (cached != null && this.radius == radius) return cached
        return Brush
            .radialGradient(
                colors = listOf(Color(0xFFFFF59D).copy(alpha = 0.55f), Color(0x00FFF59D)),
                center = Offset.Zero,
                radius = radius,
            ).also {
                brush = it
                this.radius = radius
            }
    }
}

private val stormGlowBrushCache = StormGlowBrushCache()

/**
 * 雷暴闪电：按「事件」触发（每约 3.6s 一次、持续约 0.55s，含 2–3 次子闪），
 * 每次用更早的种子生成**分叉折线**，并叠加全屏环境闪光与云底辉光。
 * 跑酷/摘星/迷宫共用；不用状态，全部由 [time] 推导，确定性。
 */
fun DrawScope.drawStormLightning(
    time: Float,
    s: Float,
    width: Float,
    height: Float,
    /** 减弱动画：跳过闪电与环境闪光，只保留静态天空底色。 */
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) return
    val cycle = 3.6f
    val idx = floor(time / cycle).toInt()
    val t = time - idx * cycle
    val env = lightningEnvelope(t)
    if (env < 0.012f) return

    val baseX = (0.16f + 0.68f * hash01(idx, 5)).coerceIn(0.12f, 0.88f)

    // 全屏环境闪光：短促、指数衰减，而不是整段时间常亮
    drawRect(Color(0xFFE8F0FF).copy(alpha = 0.11f * env))
    drawRect(Color(0xFFFFFDE7).copy(alpha = 0.055f * env))

    // 云底辉光（缓存笔刷以原点为中心，平移到云底中心；亮度用 drawCircle alpha 施加）
    val glowCenter = Offset(width * baseX, height * 0.03f)
    val glowRadius = width * 0.46f
    translate(glowCenter.x, glowCenter.y) {
        drawCircle(
            brush = stormGlowBrushCache.get(glowRadius),
            radius = glowRadius,
            center = Offset.Zero,
            alpha = env,
        )
    }

    // 主干
    drawBoltChain(
        startX = width * baseX,
        startY = -height * 0.02f,
        length = height * (0.5f + 0.22f * hash01(idx, 9)),
        s = s,
        width = width,
        seed = idx,
        env = env,
        salt = 0,
    )
    // 两根分叉
    for (b in 0 until 2) {
        val bx = width * (0.10f + 0.80f * hash01(idx, 40 + b))
        val by = height * (0.02f + 0.12f * hash01(idx, 60 + b))
        drawBoltChain(
            startX = bx,
            startY = by,
            length = height * (0.18f + 0.18f * hash01(idx, 80 + b)),
            s = s,
            width = width,
            seed = idx,
            env = env,
            salt = 100 + b * 19,
        )
    }
}

/** 三次子闪叠加的亮度包络（0..1），t 为本次事件内的秒数。 */
private fun lightningEnvelope(t: Float): Float {
    if (t < 0f || t > 0.55f) return 0f
    var f = exp(-t * 24f)
    if (t >= 0.07f) f += 0.75f * exp(-(t - 0.07f) * 42f)
    if (t >= 0.19f) f += 0.5f * exp(-(t - 0.19f) * 30f)
    return f.coerceIn(0f, 1f)
}

/** 画一条锯齿状闪电（外发光 + 亮芯）；几何全部由 [seed]/[salt] 决定，稳定不闪。 */
private fun DrawScope.drawBoltChain(
    startX: Float,
    startY: Float,
    length: Float,
    s: Float,
    width: Float,
    seed: Int,
    env: Float,
    salt: Int,
) {
    val segs = 7
    val glowColor = Color(0xFFFFF59D).copy(alpha = 0.32f * env)
    val coreColor = Color(0xFFFFFDE7).copy(alpha = 0.95f * env)
    val bias = (hash01(seed, salt + 1) - 0.5f) * 2f
    var x = startX
    var y = startY
    for (k in 0 until segs) {
        val segLen = (length / segs) * (0.65f + 0.7f * hash01(seed, salt + k))
        val nx = x + (hash01(seed, salt + 10 + k) - 0.5f) * width * 0.065f + bias * width * 0.008f
        val ny = y + segLen
        drawLine(glowColor, Offset(x, y), Offset(nx, ny), strokeWidth = 3.4f * s, cap = StrokeCap.Round)
        drawLine(coreColor, Offset(x, y), Offset(nx, ny), strokeWidth = 1.25f * s, cap = StrokeCap.Round)
        x = nx
        y = ny
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
    /** 减弱动画：跳过冻雨粒子。 */
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) return
    drawRainLayered(
        fgCount = 30,
        bgCount = 14,
        time = time,
        width = width,
        height = height,
        scale = s,
        lenMul = 0.95f,
        alpha = 0.55f,
        yOff = yOff,
        fgColor = Color(0xFFC4D9F4),
        bgColor = Color(0xFFE3EEFA),
        splash = splash,
    )
    val crystal = Color.White.copy(alpha = 0.7f)
    for (i in 0 until 18) {
        val h = hash01(i, 7)
        val phase = ((i * 857) % 47) / 47f
        val x = ((h * (width + 60) + time * 90f * (0.9f + phase * 0.4f)) % (width + 60)) - 30
        val y = positiveModulo(hash01(i, 31) * (height + 60) + time * 620f * s + yOff, height + 60) - 30
        val twinkle = 0.65f + 0.25f * (1f + sin(time * 2.6f + i * 1.7f)) * 0.5f
        drawCircle(
            crystal.copy(alpha = 0.7f * twinkle),
            (1.1f + phase * 1.0f) * s,
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
    /** 减弱动画：跳过冰雹粒子。 */
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) return
    for (i in 0 until count) {
        val h = hash01(i, 17)
        if (hash01(i, 43) > 0.55f + 0.45f * h) continue
        val speed = 1450f * s + (i % 5) * 140f * s
        val x = ((h * (width + 60) + time * (speed * 0.5f)) % (width + 60)) - 30
        val y = positiveModulo(hash01(i, 53) * (height + 90) + time * speed + yOff, height + 90) - 45
        val size = (1.6f + (i % 4) * 0.55f) * s
        val alpha = 0.45f + 0.3f * h
        drawCircle(
            color = (if (i % 3 == 0) Color(0xFFE4EAF1) else Color.White).copy(alpha = alpha),
            radius = size,
            center = Offset(x, y),
        )
    }
}

/**
 * 双层雨丝：远层细淡、近层「柔边 + 亮芯 + 滴头」，斜向有风感。
 *
 * 为避免「太显眼、太均匀」：
 *  - 整体更淡更细（远层 alpha 更低、近层柔边收细、亮芯只给一部分雨滴）；
 *  - **阵风**：[time] 驱动的风速起伏让倾角缓慢变化；
 *  - **雨势起伏**：[lull] 让部分雨丝随呼吸式强弱被剔除，形成疏密不匀；
 *  - **逐滴随机**：每滴的位置、速度、长度、亮度、倾角都由 [hash01] 决定，消除平铺规律。
 *
 * [splash]：落地水花；[ripples]：落地涟漪（更少、更大，只有少数雨滴触发）。
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
    ripples: Boolean = true,
    /** 减弱动画：跳过雨丝粒子（静态天空底色仍保留）。 */
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) return
    val a = alpha.coerceIn(0f, 1f)
    val wind = 6.0f * scale * (0.62f + 0.30f * sin(time * 0.19f) + 0.14f * sin(time * 0.53f + 1.1f)).coerceIn(0.2f, 1.1f)
    val lull = 0.68f + 0.32f * (0.5f + 0.5f * sin(time * 0.11f + 2.3f))
    // y 已把 yOff 纳入取模并回绕到屏幕坐标系，落地线不再叠加 yOff
    val splashThreshold = height * 0.88f
    val splashCol = fgColor.copy(alpha = a * 0.30f)

    // 远雨：更长更斜更淡，纵深;逐滴随机避免平铺
    for (i in 0 until bgCount) {
        val h = hash01(i, 11)
        if (hash01(i, 29) > lull * (0.6f + 0.4f * h)) continue
        val speed = 620f * scale * (0.7f + 0.7f * h)
        val x = ((h * (width + 70) + time * (speed * 0.45f)) % (width + 70)) - 35
        val y = positiveModulo(hash01(i, 7) * (height + 100) + time * speed + yOff, height + 100) - 50
        val sway = sin(time * 1.3f + h * 6.28f) * 1.2f * scale
        val dropLen = (15f + 11f * h) * scale * lenMul
        val tilt = wind * (0.6f + h * 0.7f)
        drawLine(
            color = bgColor.copy(alpha = a * (0.16f + 0.16f * h)),
            start = Offset(x + sway, y),
            end = Offset(x + sway - tilt, y + dropLen),
            strokeWidth = 0.95f * scale,
            cap = StrokeCap.Round,
        )
    }

    // 近雨：柔边 + 亮芯（部分）+ 滴头
    for (i in 0 until fgCount) {
        val h = hash01(i, 101)
        if (hash01(i, 131) > lull * (0.55f + 0.45f * h)) continue
        val speed = 1050f * scale * (0.75f + 0.7f * h)
        val x = ((h * (width + 70) + time * (speed * 0.5f)) % (width + 70)) - 35
        val y = positiveModulo(hash01(i, 97) * (height + 100) + time * speed + yOff, height + 100) - 50
        val sway = sin(time * 1.7f + h * 6.28f) * 1.6f * scale
        val dropLen = (19f + 16f * h) * scale * lenMul
        val tilt = wind * (0.85f + h * 0.5f)
        val tailX = x + sway
        val headX = tailX - tilt
        val headY = y + dropLen
        val dropA = a * (0.30f + 0.42f * h)

        drawLine(
            color = fgColor.copy(alpha = dropA * 0.5f),
            start = Offset(tailX, y),
            end = Offset(headX, headY),
            strokeWidth = 1.75f * scale,
            cap = StrokeCap.Round,
        )
        if (h < 0.5f) {
            drawLine(
                color = Color.White.copy(alpha = dropA * 0.45f),
                start = Offset(tailX - tilt * 0.08f, y + dropLen * 0.08f),
                end = Offset(headX, headY),
                strokeWidth = 0.85f * scale,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Color.White.copy(alpha = dropA * 0.55f), 0.9f * scale, Offset(headX, headY))

        if (splash && y > splashThreshold && h < 0.4f) {
            val splashY = splashThreshold + 1.5f * scale
            val spread = (1.8f + h * 3.2f) * scale
            drawCircle(splashCol, 0.9f * scale, Offset(headX, splashY))
            drawCircle(splashCol, 0.7f * scale, Offset(headX - spread, splashY - 1.1f * scale))
            drawCircle(splashCol, 0.65f * scale, Offset(headX + spread * 0.85f, splashY - 0.8f * scale))
            if (ripples && h < 0.12f) {
                drawCircle(
                    color = fgColor.copy(alpha = a * 0.22f),
                    radius = (2.6f + h * 6f) * scale,
                    center = Offset(headX, splashY),
                    style = Stroke(width = 0.8f * scale),
                )
            }
        }
    }
}

/**
 * 装饰性运动时钟：减弱动画开启时冻结为 0，让待机呼吸、闪烁、漂移、金币旋转这类
 * **纯装饰**动画停下；关闭时原样返回 [time]，默认路径与行为完全不变。
 *
 * 玩法必需的运动（角色/障碍/掉落物的实际坐标、相机跟随）不经过本函数，
 * 因此减弱动画不会影响可玩性，只去掉晃动。
 */
fun decorativeTime(
    time: Float,
    reduceMotion: Boolean,
): Float = if (reduceMotion) 0f else time
