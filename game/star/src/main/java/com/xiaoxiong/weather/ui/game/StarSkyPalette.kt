package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 摘星天空高度色标与 HUD 暗角参数。
 *
 * 独立成文件（不依赖 Android 图形类）以便纯 JVM 对比度回归测试直接引用，
 * 避免加载 StarJumpRender.kt 里的 Paint 等 Android 对象。
 */
internal class SkyStop(
    val alt: Float,
    val top: Color,
    val bottom: Color,
)

internal val skyStops =
    listOf(
        SkyStop(0f, Color(0xFF2E6FB8), Color(0xFF9BDBF5)), // 白天
        SkyStop(0.9f, Color(0xFF474A8C), Color(0xFFF79E5F)), // 黄昏
        SkyStop(1.8f, Color(0xFF121B3E), Color(0xFF3D4E85)), // 夜空
        SkyStop(2.8f, Color(0xFF04041A), Color(0xFF20123E)), // 太空
    )

internal fun skyColors(alt: Float): Pair<Color, Color> {
    if (alt <= skyStops.first().alt) {
        return skyStops.first().top to skyStops.first().bottom
    }
    for (i in 0 until skyStops.size - 1) {
        val a = skyStops[i]
        val b = skyStops[i + 1]
        if (alt <= b.alt) {
            val t = (alt - a.alt) / (b.alt - a.alt)
            return lerp(a.top, b.top, t) to lerp(a.bottom, b.bottom, t)
        }
    }
    return skyStops.last().top to skyStops.last().bottom
}

/**
 * HUD 顶部暗角强度：白天天空顶色 #2E6FB8 下 0.85 白字约 4.20:1，
 * 叠 0.15 黑后 ≥4.5:1；只覆盖顶部一条并向下渐隐，不改变整体观感基准。
 */
internal const val JUMP_HUD_SCRIM_ALPHA = 0.15f

/** HUD 暗角覆盖的屏幕高度占比。 */
internal const val JUMP_HUD_SCRIM_BAND = 0.36f

/** 暗角全强度段占暗角层的比例：HUD 两行文字（约 0.12 屏高）不被渐隐削弱。 */
internal const val JUMP_HUD_SCRIM_PLATEAU = 0.62f
