package com.xiaoxiong.weather.ui.weather

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 对比度回归（纯 Kotlin，不依赖 Android 资源）：
 *
 * 浅色场景（CLEAR/PARTLY_CLOUDY/DRIZZLE 白天 + FOG/SNOW 浅色）恢复白字后，
 * 卡片在动画层之上、内容之下叠加 [WeatherSceneStyle.scrimAlpha] 纯黑暗角：
 *
 * - 背景按「渐变每个 stop 复合 scrim」计算，模拟卡片实际绘制顺序（渐变 → 动画 → 暗角）；
 * - 断言口径：[WeatherSceneStyle.contentColor]（城市/温度/描述，displaySmall/titleLarge
 *   等大号排版）按 WCAG 大号文本 3:1；[WeatherSceneStyle.mutedContentColor]（次级/标签
 *   小字号）在浅色场景按小号文本 AA 4.5:1，深色场景因 muted 为有意着色的次色
 *   （如 #BBDEFB），沿用大号 3:1 底线。
 *
 * 修复前（最低值）：CLEAR 白天白字 1.67:1、PARTLY 白天 1.67:1、DRIZZLE 2.20:1；
 * 上一版加深 + scrim 后最亮 stop 的 content/muted：CLEAR 4.80/4.23、PARTLY 4.88/4.30、
 * DRIZZLE 5.26/4.62、FOG 4.56/4.04、SNOW 3.97/3.55 —— FOG/SNOW 以及 CLEAR/PARTLY 的
 * muted 小字号均低于 4.5:1。
 *
 * 本次调整（scrim 与 muted alpha 微调，色相/渐变不变）后最亮 stop 的 content/muted：
 * CLEAR 5.16/4.64、PARTLY 5.24/4.72、DRIZZLE 5.26/4.62、FOG 5.55/4.86、SNOW 5.25/4.62，
 * 浅色场景 content/muted 均 ≥4.5:1（content 超出大号口径，仍统一实测）。
 */
class WeatherSceneContrastTest {
    private data class Case(
        val label: String,
        val code: Int,
        val isDay: Boolean,
        val appDark: Boolean,
        /** 浅色场景：应有 scrimAlpha 暗角，背景按复合 scrim 校验。 */
        val lightScene: Boolean,
    )

    private val cases =
        listOf(
            Case("CLEAR_DAY", 0, isDay = true, appDark = false, lightScene = true),
            Case("CLEAR_NIGHT", 0, isDay = false, appDark = false, lightScene = false),
            Case("MAINLY_CLEAR_DAY", 1, isDay = true, appDark = false, lightScene = true),
            Case("MAINLY_CLEAR_NIGHT", 1, isDay = false, appDark = false, lightScene = false),
            Case("PARTLY_CLOUDY_DAY", 2, isDay = true, appDark = false, lightScene = true),
            Case("PARTLY_CLOUDY_NIGHT", 2, isDay = false, appDark = false, lightScene = false),
            Case("OVERCAST", 3, isDay = true, appDark = false, lightScene = false),
            Case("FOG_LIGHT", 45, isDay = true, appDark = false, lightScene = true),
            Case("FOG_DARK", 45, isDay = true, appDark = true, lightScene = false),
            Case("DRIZZLE", 51, isDay = true, appDark = false, lightScene = true),
            Case("RAIN", 61, isDay = true, appDark = false, lightScene = false),
            Case("HEAVY_RAIN", 65, isDay = true, appDark = false, lightScene = false),
            Case("THUNDER", 95, isDay = true, appDark = false, lightScene = false),
            Case("HAIL", 99, isDay = true, appDark = false, lightScene = false),
            Case("FREEZING_RAIN", 66, isDay = true, appDark = false, lightScene = false),
            Case("SNOW_LIGHT", 71, isDay = true, appDark = false, lightScene = true),
            Case("SNOW_DARK", 71, isDay = true, appDark = true, lightScene = false),
            Case("HEAVY_SNOW", 75, isDay = true, appDark = false, lightScene = false),
            Case("UNKNOWN", 999, isDay = true, appDark = false, lightScene = false),
        )

    /** content 为大号/粗体排版：所有场景对复合后每个渐变 stop ≥3:1（大号 AA）。 */
    @Test
    fun allScenes_content_meetLargeTextFloor() {
        cases.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            style.gradient.forEach { stop ->
                val background = scrimmedBackground(stop, style.scrimAlpha)
                assertContrast(case, "content", style.contentColor, background, MIN_LARGE_TEXT)
            }
        }
    }

    /** 浅色场景 muted 为白字小号标签：对复合 scrim 后每个渐变 stop ≥4.5:1（小号 AA）。 */
    @Test
    fun lightScenes_muted_meetSmallTextFloor() {
        cases.filter { it.lightScene }.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            style.gradient.forEach { stop ->
                val background = scrimmedBackground(stop, style.scrimAlpha)
                assertContrast(case, "muted", style.mutedContentColor, background, MIN_SMALL_TEXT)
            }
        }
    }

    /** 深色场景 muted 为有意着色的次色，沿用既有 3:1 底线（不在浅色白字修复口径内）。 */
    @Test
    fun darkScenes_muted_meetLargeTextFloor() {
        cases.filterNot { it.lightScene }.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            style.gradient.forEach { stop ->
                val background = scrimmedBackground(stop, style.scrimAlpha)
                assertContrast(case, "muted", style.mutedContentColor, background, MIN_LARGE_TEXT)
            }
        }
    }

    @Test
    fun lightScenes_useScrim_darkScenes_skipScrim() {
        cases.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            if (case.lightScene) {
                assertTrue(
                    "${case.label}: light scene scrimAlpha=${style.scrimAlpha} not in 0.25..0.5",
                    style.scrimAlpha in 0.25f..0.5f,
                )
            } else {
                assertEquals("${case.label}: dark scene must skip scrim", 0f, style.scrimAlpha, 0f)
            }
        }
    }

    @Test
    fun allScenes_keepLightText() {
        cases.forEach { case ->
            val style = weatherSceneStyle(case.code, case.isDay, case.appDark)
            assertTrue(
                "${case.label}: contentColor luminance ${style.contentColor.luminance()} < 0.5",
                style.contentColor.luminance() >= 0.5f,
            )
        }
    }

    /** 卡片绘制顺序：渐变 stop 在下，scrimAlpha 纯黑在上。 */
    private fun scrimmedBackground(
        stop: Color,
        scrimAlpha: Float,
    ): Color = Color.Black.copy(alpha = scrimAlpha).compositeOver(stop)

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
        const val MIN_LARGE_TEXT = 3.0
        const val MIN_SMALL_TEXT = 4.5
        const val EPSILON = 1e-9
    }
}
