package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 静态层缓存「近似复用」纯函数回归：球桌几何（left/top/width/height）不一致必须重建，
 * 位图尺寸与球门宽只在容差内可近似复用。
 */
class HockeyLayerKeyTest {
    private val dayStyle = weatherSceneStyle(0, isDay = true, appDark = true)
    private val nightStyle = weatherSceneStyle(0, isDay = false, appDark = true)

    private fun key(
        width: Int = 400,
        height: Int = 800,
        left: Float = 20f,
        top: Float = 40f,
        tableWidth: Float = 360f,
        tableHeight: Float = 720f,
        goalWidth: Float = 120f,
        theme: RinkTheme = RinkTheme.ICE,
        style: WeatherSceneStyle = dayStyle,
        isDay: Boolean = true,
    ) = HockeyLayerKey(width, height, left, top, tableWidth, tableHeight, goalWidth, theme, style, isDay)

    @Test
    fun identicalKey_reusable() {
        assertTrue(hockeyLayerKeyReusable(key(), key()))
    }

    @Test
    fun tableGeometryShift_beyondTolerance_notReusable() {
        assertFalse(hockeyLayerKeyReusable(key(), key(left = 24f)))
        assertFalse(hockeyLayerKeyReusable(key(), key(top = 44f)))
        assertFalse(hockeyLayerKeyReusable(key(), key(tableWidth = 364f)))
        assertFalse(hockeyLayerKeyReusable(key(), key(tableHeight = 724f)))
    }

    @Test
    fun tableGeometryShift_withinOnePixel_reusable() {
        assertTrue(hockeyLayerKeyReusable(key(), key(left = 20.5f, top = 39.6f, tableWidth = 359.4f, tableHeight = 720.4f)))
    }

    @Test
    fun bitmapSize_withinResizeTolerance_reusable() {
        assertTrue(hockeyLayerKeyReusable(key(), key(width = 420, height = 810)))
        assertFalse(hockeyLayerKeyReusable(key(), key(width = 431)))
        assertFalse(hockeyLayerKeyReusable(key(), key(height = 831)))
    }

    @Test
    fun goalWidthChange_beyondTolerance_notReusable() {
        assertTrue(hockeyLayerKeyReusable(key(), key(goalWidth = 121.5f)))
        assertFalse(hockeyLayerKeyReusable(key(), key(goalWidth = 124f)))
    }

    @Test
    fun styleOrDayChange_notReusable() {
        assertFalse(hockeyLayerKeyReusable(key(), key(style = nightStyle)))
        assertFalse(hockeyLayerKeyReusable(key(), key(isDay = false)))
    }

    /**
     * 场地主题必须参与缓存键：否则切主题时几何 / 样式 / 昼夜、位图尺寸都没变，
     * 会错误复用旧主题的静态层，画面不更新。
     */
    @Test
    fun rinkThemeChange_notReusable() {
        RinkTheme.entries.forEach { theme ->
            if (theme != RinkTheme.ICE) {
                assertFalse(hockeyLayerKeyReusable(key(), key(theme = theme)))
            }
        }
        assertTrue(hockeyLayerKeyReusable(key(), key(theme = RinkTheme.ICE)))
    }
}
