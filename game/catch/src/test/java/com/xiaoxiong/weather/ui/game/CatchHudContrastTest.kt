package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 接雨雪 HUD 对比度回归（纯 Kotlin，不依赖 Android 资源）：
 *
 * 浅色天空下 HUD 纯白主文字直接压在天空渐变上时对比度不足（晴天顶色 #4FC3F7 2.00:1、
 * 雪天 #BEC7D3 1.71:1、多云 #92A1AD 2.65:1）。修复方式与 jump 一致：天空/云层之上、
 * 玩法内容之下叠一层顶部黑色暗角 [CatchSkyStyle.hudScrimAlpha]，并按「HUD 文本区间
 * （0..[CATCH_HUD_TEXT_BAND] 屏高）内最亮的天空色」反推 alpha。
 *
 * 断言口径：HUD 主文字（纯白）对复合暗角后的最亮天空色 ≥4.5:1（WCAG 小号文本 AA）。
 * 天空底部更亮的 stop 不在 HUD 之下、不受顶部暗角影响，不在本口径内。
 */
class CatchHudContrastTest {
    @Test
    fun hudPrimaryText_meetsSmallTextFloor_overBrightestSkyInHudBand() {
        CatchScene.entries.forEach { scene ->
            listOf(true, false).forEach { isDay ->
                val style = catchSceneSky(scene, isDay)
                assertTrue(
                    "${scene.name}(day=$isDay) scrim alpha ${style.hudScrimAlpha} out of 0..0.6",
                    style.hudScrimAlpha in 0f..0.6f,
                )
                val sky = brightestInHudBand(style)
                val background = Color.Black.copy(alpha = style.hudScrimAlpha).compositeOver(sky)
                val ratio = contrastRatio(Color.White, background)
                assertTrue(
                    "${scene.name}(day=$isDay) 白字 on $background = ${"%.2f".format(ratio)} < 4.5",
                    ratio >= MIN_SMALL_TEXT - EPSILON,
                )
            }
        }
    }

    /** 暗角全强度段必须完整覆盖 HUD 文本区间，否则反推 alpha 会被顶部渐隐削弱。 */
    @Test
    fun hudScrimPlateau_coversHudTextBand() {
        assertTrue(
            "plateau ${CATCH_HUD_SCRIM_BAND * HUD_SCRIM_PLATEAU} < band $CATCH_HUD_TEXT_BAND",
            CATCH_HUD_SCRIM_BAND * HUD_SCRIM_PLATEAU >= CATCH_HUD_TEXT_BAND,
        )
    }

    /** HUD 文本区间内的最亮天空色：沿天空三色渐变按 1% 采样取最大亮度。 */
    private fun brightestInHudBand(style: CatchSkyStyle): Color {
        var brightest = style.top
        var y = 0f
        while (y <= CATCH_HUD_TEXT_BAND + 1e-6f) {
            val color = skyColorAt(style.top, style.bottom, y)
            if (color.luminance() > brightest.luminance()) brightest = color
            y += 0.01f
        }
        return brightest
    }

    /** 与渲染层 [skyGradient]（顶/中/底三色等距 stop）一致的取色。 */
    private fun skyColorAt(
        top: Color,
        bottom: Color,
        y: Float,
    ): Color {
        val stops = skyGradient(top, bottom)
        val pos = y.coerceIn(0f, 1f) * (stops.size - 1)
        val i = pos.toInt().coerceAtMost(stops.size - 2)
        return lerp(stops[i], stops[i + 1], pos - i)
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
