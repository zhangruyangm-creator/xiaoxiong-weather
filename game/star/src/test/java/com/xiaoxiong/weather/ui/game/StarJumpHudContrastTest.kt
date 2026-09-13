package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 摘星 HUD 对比度回归（纯 Kotlin，不依赖 Android 资源）：
 *
 * HUD 副文字为 0.85 白（labelMedium），直接压在白天天空顶色 #2E6FB8 上约 4.20:1，
 * 略低于小号文本 AA。修复：天空/背景之上、内容之下叠一层顶部黑色暗角
 * [JUMP_HUD_SCRIM_ALPHA]（顶部全强度后向下渐隐），断言 0.85 白字对复合暗角后的
 * 各高度天空顶色 ≥4.5:1。更高空 / 雨雪场景的顶色更暗，只会更安全。
 */
class StarJumpHudContrastTest {
    @Test
    fun hudMutedText_meetsSmallTextFloor_overScrimmedSkyTops() {
        assertTrue(
            "scrim alpha $JUMP_HUD_SCRIM_ALPHA not in 0.10..0.15",
            JUMP_HUD_SCRIM_ALPHA in 0.10f..0.15f,
        )
        assertTrue(
            "plateau ${JUMP_HUD_SCRIM_BAND * JUMP_HUD_SCRIM_PLATEAU} < HUD 行高",
            JUMP_HUD_SCRIM_BAND * JUMP_HUD_SCRIM_PLATEAU >= 0.15f,
        )
        skyStops.forEach { stop ->
            val background = Color.Black.copy(alpha = JUMP_HUD_SCRIM_ALPHA).compositeOver(stop.top)
            val muted = Color.White.copy(alpha = 0.85f)
            val ratio = contrastRatio(muted, background)
            assertTrue(
                "alt=${stop.alt} top=${stop.top} 0.85 白 on $background = ${"%.2f".format(ratio)} < 4.5",
                ratio >= MIN_SMALL_TEXT - EPSILON,
            )
        }
    }

    @Test
    fun hudPrimaryText_meetsSmallTextFloor_overScrimmedSkyTops() {
        skyStops.forEach { stop ->
            val background = Color.Black.copy(alpha = JUMP_HUD_SCRIM_ALPHA).compositeOver(stop.top)
            val ratio = contrastRatio(Color.White, background)
            assertTrue(
                "alt=${stop.alt} 白字 on $background = ${"%.2f".format(ratio)} < 4.5",
                ratio >= MIN_SMALL_TEXT - EPSILON,
            )
        }
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val fg = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
        val l1 = fg.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Color.compositeOver(background: Color): Color {
        val a = alpha
        return Color(
            red = red * a + background.red * (1f - a),
            green = green * a + background.green * (1f - a),
            blue = blue * a + background.blue * (1f - a),
            alpha = 1f,
        )
    }

    private companion object {
        const val MIN_SMALL_TEXT = 4.5
        const val EPSILON = 1e-9
    }
}
