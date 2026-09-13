package com.xiaoxiong.weather.ui.weather

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 对比度回归（纯 Kotlin，不依赖 Android 资源）：
 *
 * - [WeatherSceneStyle.contentColor]（正文/图标）与 [WeatherSceneStyle.mutedContentColor]（次级/标签）
 *   对渐变每一个 stop 至少 [MIN_LARGE_TEXT]（大号/图标 3:1）；
 * - 浅色场景（深色文字：CLEAR/PARTLY_CLOUDY/DRIZZLE 白天 + FOG/SNOW 浅色）
 *   对最亮 stop 还须达到 [MIN_BODY_TEXT]（正文 4.5:1）；
 * - 深色场景（浅色文字）最亮的 stop 只按 3:1 放宽：白字在中等亮度渐变
 *   （如 HAIL #6B8A99、HEAVY_SNOW #78909C）上无法达到 4.5:1，此处按大号/图标口径约束，
 *   在测试注释中说明（需求允许个别 stop 放宽到 3:1）；
 * - CLEAR 白天品牌蓝 stop #2F80ED 对深色文字为 3.32:1，也属于上述放宽口径，
 *   但深字在浅色场景的最亮 stop 上均 ≥4.5:1，正文可读性有保障。
 *
 * 修复前（最低值）：CLEAR 白天白字 1.67:1、PARTLY 白天 1.67:1、DRIZZLE 2.20:1；
 * 修复后：三者 contentColor 对各自最亮 stop 分别为 7.66:1 / 7.92:1 / 5.76:1。
 */
class WeatherSceneContrastTest {
    private data class Case(
        val label: String,
        val code: Int,
        val isDay: Boolean,
        val appDark: Boolean,
        /** contentColor 是否为浅色（深色场景保持浅色/白字）。 */
        val lightText: Boolean,
    )

    private val cases =
        listOf(
            Case("CLEAR_DAY", 0, isDay = true, appDark = false, lightText = false),
            Case("CLEAR_NIGHT", 0, isDay = false, appDark = false, lightText = true),
            Case("PARTLY_CLOUDY_DAY", 2, isDay = true, appDark = false, lightText = false),
            Case("PARTLY_CLOUDY_NIGHT", 2, isDay = false, appDark = false, lightText = true),
            Case("OVERCAST", 3, isDay = true, appDark = false, lightText = true),
            Case("FOG_LIGHT", 45, isDay = true, appDark = false, lightText = false),
            Case("FOG_DARK", 45, isDay = true, appDark = true, lightText = true),
            Case("DRIZZLE", 51, isDay = true, appDark = false, lightText = false),
            Case("RAIN", 61, isDay = true, appDark = false, lightText = true),
            Case("HEAVY_RAIN", 65, isDay = true, appDark = false, lightText = true),
            Case("THUNDER", 95, isDay = true, appDark = false, lightText = true),
            Case("HAIL", 99, isDay = true, appDark = false, lightText = true),
            Case("FREEZING_RAIN", 66, isDay = true, appDark = false, lightText = true),
            Case("SNOW_LIGHT", 71, isDay = true, appDark = false, lightText = false),
            Case("SNOW_DARK", 71, isDay = true, appDark = true, lightText = true),
            Case("HEAVY_SNOW", 75, isDay = true, appDark = false, lightText = true),
            Case("UNKNOWN", 999, isDay = true, appDark = false, lightText = true),
        )

    @Test
    fun contentAndMuted_meetLargeTextFloorOnEveryGradientStop() {
        cases.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            style.gradient.forEach { stop ->
                assertContrast(case, "content", style.contentColor, stop, MIN_LARGE_TEXT)
                assertContrast(case, "muted", style.mutedContentColor, stop, MIN_LARGE_TEXT)
            }
        }
    }

    @Test
    fun lightScenes_meetBodyTextFloorOnBrightestStop() {
        cases.filterNot { it.lightText }.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            val brightest = style.gradient.maxByOrNull { it.luminance() } ?: error("empty gradient")
            assertContrast(case, "content", style.contentColor, brightest, MIN_BODY_TEXT)
            assertContrast(case, "muted", style.mutedContentColor, brightest, MIN_BODY_TEXT)
        }
    }

    @Test
    fun darkScenes_keepLightText_lightScenes_keepDarkText() {
        cases.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            val lightText = style.contentColor.luminance() >= 0.5f
            assertEquals("${case.label}: contentColor luminance", case.lightText, lightText)
        }
    }

    private fun assertContrast(
        case: Case,
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "${case.label} $name $foreground on $background = ${"%.2f".format(ratio)} < $minimum",
            ratio >= minimum - EPSILON,
        )
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
        const val MIN_BODY_TEXT = 4.5
        const val MIN_LARGE_TEXT = 3.0
        const val EPSILON = 1e-9
    }
}
