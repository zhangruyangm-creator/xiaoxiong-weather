package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 台面静态层缓存「近似复用」纯函数回归：台呢主题必须参与缓存键（否则切主题时
 * 几何/样式/尺寸都没变会错误复用旧主题的静态层）；球桌几何不一致必须重建；
 * 位图尺寸只在容差内可近似复用。
 */
class PoolLayerKeyTest {
    private val dayStyle = weatherSceneStyle(0, isDay = true, appDark = true)
    private val nightStyle = weatherSceneStyle(0, isDay = false, appDark = true)

    private fun key(
        width: Int = 400,
        height: Int = 800,
        viewScale: Float = 320f,
        offsetX: Float = 12f,
        offsetY: Float = 24f,
        tableLeft: Float = 0f,
        tableTop: Float = 0f,
        tableWidth: Float = 1.27f,
        tableHeight: Float = 2.54f,
        style: WeatherSceneStyle = dayStyle,
        isDay: Boolean = true,
        clothTheme: Int = 0,
    ) = PoolLayerKey(width, height, viewScale, offsetX, offsetY, tableLeft, tableTop, tableWidth, tableHeight, style, isDay, clothTheme)

    @Test
    fun identicalKey_reusable() {
        assertTrue(poolLayerKeyReusable(key(), key()))
    }

    @Test
    fun clothThemeChange_notReusable() {
        for (theme in 1 until CLOTH_THEME_COUNT) {
            assertFalse(
                "主题 0 → $theme 必须重建静态层",
                poolLayerKeyReusable(key(), key(clothTheme = theme)),
            )
        }
        assertTrue(poolLayerKeyReusable(key(), key(clothTheme = 0)))
    }

    @Test
    fun styleOrDayChange_notReusable() {
        assertFalse(poolLayerKeyReusable(key(), key(style = nightStyle)))
        assertFalse(poolLayerKeyReusable(key(), key(isDay = false)))
    }

    @Test
    fun weatherSceneChange_notReusable() {
        // style 里含天气场景：雨/雪/晴的静态层氛围不同，不得互相复用
        val rain = weatherSceneStyle(61, isDay = true, appDark = true)
        val snow = weatherSceneStyle(71, isDay = true, appDark = true)
        assertFalse(poolLayerKeyReusable(key(style = dayStyle), key(style = rain)))
        assertFalse(poolLayerKeyReusable(key(style = rain), key(style = snow)))
        assertTrue(poolLayerKeyReusable(key(style = rain), key(style = rain)))
    }

    @Test
    fun tableGeometryShift_notReusable() {
        // 球桌几何是固定常量，换台（尺寸变化）必须整层重建，避免错位残留
        assertFalse(poolLayerKeyReusable(key(), key(tableLeft = 2f)))
        assertFalse(poolLayerKeyReusable(key(), key(tableTop = 2f)))
        assertFalse(poolLayerKeyReusable(key(), key(tableWidth = 1.28f)))
        assertFalse(poolLayerKeyReusable(key(), key(tableHeight = 2.55f)))
    }

    @Test
    fun bitmapSize_withinResizeTolerance_reusable() {
        assertTrue(poolLayerKeyReusable(key(), key(width = 420, height = 810)))
        assertFalse(poolLayerKeyReusable(key(), key(width = 431)))
        assertFalse(poolLayerKeyReusable(key(), key(height = 831)))
    }

    @Test
    fun exactHit_requiresSameViewTransform() {
        // 精确命中走 data class 相等：视图缩放/偏移不同即视为不同键，由尺寸容差路径兜底
        assertFalse(key() == key(viewScale = 999f))
        assertFalse(key() == key(offsetX = 999f))
        assertFalse(key() == key(offsetY = 999f))
    }
}
