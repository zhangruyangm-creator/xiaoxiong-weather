package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.sin

/**
 * 农场背景的静态远景层：远山剪影 + 丘陵 + 树线 + 暗角。
 *
 * 与冰球/台球同一思路——这些内容只依赖「天气配色 + 时段 + 画布尺寸」，
 * 因此用公共 [BakedLayerCache] 烘焙成一张位图，之后每帧只 drawImage；
 * 雨雪/星空/萤火虫等动态氛围仍逐帧绘制。
 *
 * 视觉目标是补上"地平线以下的层次"：原来天空渐变之下直接就是田地，
 * 画面缺少远近对比；加入三层剪影后田格像坐落在真实的乡野里。
 */
internal class FarmBackdropKey(
    val widthPx: Int,
    val heightPx: Int,
    val phase: FarmSkyPhase,
    val weather: FarmWeather,
    val skyTop: Long,
    val skyBottom: Long,
) {
    fun matches(other: FarmBackdropKey): Boolean =
        widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            phase == other.phase &&
            weather == other.weather &&
            skyTop == other.skyTop &&
            skyBottom == other.skyBottom
}

/** 远景层相对画布的纵向锚点：地平线落在 62% 高度，农田在其下方。 */
private const val HORIZON_RATIO = 0.62f

/** 烘焙农场静态远景：远山 + 丘陵 + 树线 + 暗角。 */
internal fun DrawScope.drawFarmBackdropStatic(
    phase: FarmSkyPhase,
    weather: FarmWeather,
    skyTop: Long,
    skyBottom: Long,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val horizon = h * HORIZON_RATIO
    val isNight = phase == FarmSkyPhase.NIGHT
    val isDusk = phase == FarmSkyPhase.DUSK

    // 三层剪影由远及近逐层变暗，做出空气透视
    val farTint = mixTone(Color(skyTop), Color(skyBottom), 0.45f, isNight, isDusk)
    val midTint = mixTone(Color(skyTop), Color(skyBottom), 0.72f, isNight, isDusk)
    val nearTint = mixTone(Color(skyTop), Color(skyBottom), 0.92f, isNight, isDusk)

    // 远山：低频起伏，最高不过地平线上方 12%
    val farPeak = horizon - h * 0.12f
    drawPath(
        buildRidgePath(
            width = w,
            baseY = horizon + h * 0.004f,
            amplitude = h * 0.075f,
            segments = 5,
            phaseOffset = 0.13f,
        ),
        brush = Brush.verticalGradient(listOf(farTint.copy(alpha = 0.55f), farTint.copy(alpha = 0.32f))),
    )

    // 丘陵：中频起伏，遮住远山底部
    val midPeak = horizon - h * 0.05f
    drawPath(
        buildRidgePath(
            width = w,
            baseY = horizon + h * 0.012f,
            amplitude = h * 0.045f,
            segments = 8,
            phaseOffset = 0.61f,
        ),
        brush = Brush.verticalGradient(listOf(midTint.copy(alpha = 0.78f), midTint.copy(alpha = 0.55f))),
    )

    // 树线：地平线上的一排小树剪影，晴天/多云更密
    val treeAlpha =
        when (weather) {
            FarmWeather.SUNNY -> 0.85f
            FarmWeather.CLOUDY -> 0.7f
            FarmWeather.FOGGY -> 0.45f
            else -> 0.55f
        }
    drawTreeLine(
        baseY = horizon + h * 0.016f,
        width = w,
        treeColor = nearTint.copy(alpha = treeAlpha),
        density =
            when (weather) {
                FarmWeather.SUNNY, FarmWeather.CLOUDY -> 26
                else -> 18
            },
    )

    // 地面基色：地平线以下压一层暖土色，让田格不至于浮在天空上
    drawRect(
        brush =
            Brush.verticalGradient(
                listOf(
                    nearTint.copy(alpha = 0.55f),
                    Color(0xFF6B4A32).copy(alpha = if (isNight) 0.72f else 0.45f),
                ),
                startY = horizon,
                endY = h,
            ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    // 暗角：四边压暗，把视线收进中间的田地
    drawRect(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (isNight) 0.34f else 0.18f)),
                center = Offset(w / 2f, h * 0.45f),
                radius = maxOf(w, h) * 0.78f,
            ),
        size = Size(w, h),
    )
}

/** 起伏山脊：正弦 + 确定性扰动，保证同一 seed 每次形状一致。 */
private fun buildRidgePath(
    width: Float,
    baseY: Float,
    amplitude: Float,
    segments: Int,
    phaseOffset: Float,
): Path {
    val step = width / segments
    return Path().apply {
        moveTo(0f, baseY)
        var x = 0f
        var i = 0
        while (i <= segments) {
            val t = i.toFloat() / segments
            // 两个不同频率叠加：避免规则正弦的机械感
            val jitter = abs(gameHash01(i * 31 + segments)) * amplitude * 0.5f
            val y =
                baseY -
                    sin(t * Math.PI.toFloat() * 1.6f + phaseOffset * 6f) * amplitude * 0.5f -
                    sin(t * Math.PI.toFloat() * 4.3f + phaseOffset * 11f) * amplitude * 0.22f -
                    jitter
            lineTo(x, y)
            x += step
            i++
        }
        lineTo(width, baseY)
        close()
    }
}

/** 地平线上的树线剪影：三角树冠 + 细树干，按确定性 hash 分布大小与间距。 */
private fun DrawScope.drawTreeLine(
    baseY: Float,
    width: Float,
    treeColor: Color,
    density: Int,
) {
    val spacing = width / density
    for (i in 0 until density) {
        val r1 = gameHash01(i * 17 + 3)
        val r2 = gameHash01(i * 29 + 11)
        val r3 = gameHash01(i * 41 + 5)
        val cx = i * spacing + r1 * spacing * 0.6f
        val treeH = spacing * (0.55f + r2 * 0.5f)
        val treeW = treeH * (0.42f + r3 * 0.16f)
        // 树干
        drawRect(
            color = treeColor,
            topLeft = Offset(cx - treeW * 0.06f, baseY - treeH * 0.28f),
            size = Size(treeW * 0.12f, treeH * 0.28f),
        )
        // 树冠：两层三角
        val crown =
            Path().apply {
                moveTo(cx, baseY - treeH)
                lineTo(cx - treeW * 0.5f, baseY - treeH * 0.30f)
                lineTo(cx + treeW * 0.5f, baseY - treeH * 0.30f)
                close()
            }
        drawPath(crown, treeColor)
        val upper =
            Path().apply {
                moveTo(cx, baseY - treeH * 1.18f)
                lineTo(cx - treeW * 0.36f, baseY - treeH * 0.62f)
                lineTo(cx + treeW * 0.36f, baseY - treeH * 0.62f)
                close()
            }
        drawPath(upper, treeColor)
    }
}

/** 夜色/黄昏下把天空色往暗处压，黄昏保留暖调。 */
private fun mixTone(
    top: Color,
    bottom: Color,
    towardBottom: Float,
    isNight: Boolean,
    isDusk: Boolean,
): Color {
    val base = lerp(top, bottom, towardBottom)
    return when {
        isNight -> base.copy(alpha = 1f).darken(0.62f)
        isDusk -> base.darken(0.22f)
        else -> base
    }
}

/** 按比例压暗颜色（保持 alpha）。 */
private fun Color.darken(factor: Float): Color =
    Color(
        red = red * (1f - factor),
        green = green * (1f - factor),
        blue = blue * (1f - factor),
        alpha = alpha,
    )
