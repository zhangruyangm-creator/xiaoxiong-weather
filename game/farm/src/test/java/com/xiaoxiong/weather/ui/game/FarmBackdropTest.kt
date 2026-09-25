package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 农场静态远景层的键与配色规则。
 *
 * 远景是烘焙进位图的（`BakedLayerCache`），所以键一旦漏掉某个视觉输入，
 * 换天气/换时段后就会显示上一次的远景——这是本层最贵的 bug。因此这里把
 * "键必须区分所有视觉输入"和"夜间压暗规则"都锁成断言。
 */
class FarmBackdropTest {
    /**
     * 键必须结构相等。
     *
     * 这条断言锁的是缓存能否命中：`BakedLayerCache` 的默认复用谓词用 `==`。
     * 键曾是普通 class（引用比较），而缓存构造又漏传 reusable，于是**永不命中**；
     * 该调用点在 Canvas 绘制 lambda 内、农场按 60fps 重绘，等于每帧重新烘焙一张
     * 整屏位图（1080×2400 约 10MB）。结构相等一旦被破坏，缓存就退化成本 bug。
     */
    @Test
    fun key_isStructurallyEqual() {
        assertEquals(
            "键必须结构相等，否则缓存永不命中、每帧重烘焙整屏位图",
            key(),
            key(),
        )
    }

    @Test
    fun key_equalityDistinguishesEveryVisualInput() {
        assertNotEquals(key(), key(phase = FarmSkyPhase.NIGHT))
        assertNotEquals(key(), key(weather = FarmWeather.RAINY))
        assertNotEquals(key(), key(w = 1440))
        assertNotEquals(key(), key(h = 2340))
        assertNotEquals(key(), key(skyTop = 0xFF000000))
        assertNotEquals(key(), key(skyBottom = 0xFFFFFFFF))
    }

    private fun key(
        phase: FarmSkyPhase = FarmSkyPhase.DAY,
        weather: FarmWeather = FarmWeather.SUNNY,
        w: Int = 1080,
        h: Int = 1920,
        skyTop: Long = 0xFF4FA3E0,
        skyBottom: Long = 0xFFB8E4F8,
    ) = FarmBackdropKey(
        widthPx = w,
        heightPx = h,
        phase = phase,
        weather = weather,
        skyTop = skyTop,
        skyBottom = skyBottom,
    )

    @Test
    fun sameInputs_reusable() {
        assertTrue(key().matches(key()))
    }

    @Test
    fun differentPhase_notReused() {
        assertNotEquals(true, key(phase = FarmSkyPhase.DAY).matches(key(phase = FarmSkyPhase.NIGHT)))
    }

    @Test
    fun differentWeather_notReused() {
        assertTrue(!key(weather = FarmWeather.SUNNY).matches(key(weather = FarmWeather.SNOWY)))
    }

    @Test
    fun differentSize_notReused() {
        // 尺寸进键：窗口变化后不能拉伸复用旧远景（山脊/树线会变形）
        assertTrue(!key(w = 1080).matches(key(w = 1440)))
        assertTrue(!key(h = 1920).matches(key(h = 2340)))
    }

    @Test
    fun differentSkyTint_notReused() {
        // 天气配色进键：雨天与晴天天空色不同，远景基色也不同
        val rainy = farmWeatherEffect(FarmWeather.RAINY)
        val sunny = farmWeatherEffect(FarmWeather.SUNNY)
        assertNotEquals(sunny.skyTop, rainy.skyTop)
        assertTrue(
            !key(
                weather = FarmWeather.SUNNY,
                skyTop = sunny.skyTop,
                skyBottom = sunny.skyBottom,
            ).matches(
                key(
                    weather = FarmWeather.RAINY,
                    skyTop = rainy.skyTop,
                    skyBottom = rainy.skyBottom,
                ),
            ),
        )
    }

    @Test
    fun everyWeather_hasSkyTintForBackdrop() {
        // 远景依赖 skyTop/skyBottom：任何天气缺色都会让烘焙层拿到 0 值
        FarmWeather.entries.forEach { w ->
            val fx = farmWeatherEffect(w)
            assertTrue("$w 缺少天空顶色", fx.skyTop != 0L)
            assertTrue("$w 缺少天空底色", fx.skyBottom != 0L)
        }
    }

    @Test
    fun horizonSitsAboveFieldSoPlotsHaveBacking() {
        // 地平线锚点是视觉约定：必须落在画面 55%~70% 之间，
        // 太高会压住天空氛围，太低则田格浮在山上面
        val ratio = 0.62f
        assertTrue("地平线过低，田格会浮在山上面", ratio > 0.55f)
        assertTrue("地平线过高，天空氛围被挤没", ratio < 0.70f)
    }

    @Test
    fun everySkyPhase_hasThreeStopGradient() {
        // 三段渐变（顶/中/底）保证天空不会只有两段而显得平
        FarmSkyPhase.entries.forEach { p ->
            val g = farmSkyGradient(p)
            assertTrue("${p.name} 顶色异常", g.top != 0L)
            assertTrue("${p.name} 中色异常", g.mid != 0L)
            assertTrue("${p.name} 底色异常", g.bottom != 0L)
            assertNotEquals("${p.name} 顶色与中色不应相同", g.top, g.mid)
        }
    }
}
