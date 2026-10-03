package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 区分度契约测试。
 *
 * 用户明确要求「能点的和不能点的区分度要大一些，省得看得不明显容易点错」。
 * 这条要求不能只靠肉眼判断，故在此把它拆成**可断言的数值条件**：
 *
 *  1. 两类方块**形状**不同（绘制常量声明级契约）；
 *  2. **色相**差异足够大（冷暖对立）；
 *  3. 两类方块**各自与背景**都有足够对比度（在暗背景上都能看清）；
 *  4. 冰雹的**描边**与本体有足够对比（否则"描边强化"是空话）。
 *
 * 另含色觉辅助检查：即使把色相全部抹平（模拟色弱/灰度屏），
 * 两者的**亮度**也必须可区分——这是"四重冗余"里不依赖颜色的那一重。
 */
class RainPaletteTest {
    private val scenes = RainScene.entries

    @Test
    fun `drop and hail use different shapes`() {
        assertNotEquals(
            "雨滴与冰雹绝不能同形状——形状是最强的前注意信号",
            RainRenderMetrics.DROP_SHAPE,
            RainRenderMetrics.HAIL_SHAPE,
        )
        assertEquals("circle", RainRenderMetrics.DROP_SHAPE)
        assertEquals("hexagon", RainRenderMetrics.HAIL_SHAPE)
    }

    @Test
    fun `hail outline stays in the band that reads as an outline`() {
        // 下限：太细看不出"禁击"边界；上限：太粗会与斜杠糊成一片、
        // 把冰蓝填充压成两条缝，冰雹就读作"黑色六边形"而不是"冰块"。
        // 0.13 是看图校准后的值（描边+斜杠同粗）。
        assertTrue(
            "冰雹描边过细，看不出「禁击」边界",
            RainRenderMetrics.HAIL_STROKE_RATIO >= 0.10f,
        )
        assertTrue(
            "冰雹描边过粗，会吞掉填充让冰雹读作黑块",
            RainRenderMetrics.HAIL_STROKE_RATIO <= 0.16f,
        )
    }

    @Test
    fun `hail fill stays visually dominant over its markings`() {
        // 把"填充必须占主导"变成可算的断言：描边向内吃 w/2，
        // 填充的等效面积比例约为 (1 - w/2)²；要求剩余填充 ≥ 80%。
        val inner = 1f - RainRenderMetrics.HAIL_STROKE_RATIO / 2f
        val fillRatio = inner * inner
        assertTrue(
            "描边吃掉了过多填充（剩余 ${(fillRatio * 100).toInt()}%）",
            fillRatio >= 0.80f,
        )
    }

    @Test
    fun `slash matches the outline weight so they cannot merge`() {
        // 斜杠比描边粗时，两者交叠处会连成一片黑，"禁击"就变成了"一个黑块"
        assertEquals(
            "斜杠与描边应同粗",
            RainRenderMetrics.HAIL_STROKE_RATIO,
            RAIN_HAIL_SLASH_RATIO,
            1e-6f,
        )
    }

    @Test
    fun `drop highlight is a water glint rather than a second blob`() {
        // 高光过大会让雨滴读作"带白心的蛋黄"；且必须小于半径，否则高光盖住主体
        assertTrue(
            "雨滴高光过大，雨滴会读作荷包蛋",
            RAIN_DROP_HIGHLIGHT_RATIO <= 0.30f,
        )
        assertTrue(
            "雨滴高光过小则失去水润感",
            RAIN_DROP_HIGHLIGHT_RATIO >= 0.18f,
        )
    }

    @Test
    fun `hail is slightly smaller than drop`() {
        // 大小也是信号：读作「不是目标」
        assertTrue(
            "冰雹应略小于雨滴",
            RAIN_HAIL_RADIUS_SCALE < 1f && RAIN_HAIL_RADIUS_SCALE > 0.7f,
        )
    }

    @Test
    fun `drop and hail hues are far apart in every scene`() {
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val d = hueDistance(hueOf(p.dropFill), hueOf(p.hailFill))
            assertTrue(
                "$scene 色相差异过小（$d°），高速下会看混",
                d >= 100f,
            )
        }
    }

    @Test
    fun `hues land on opposite sides of the wheel`() {
        // 暖 vs 冷：雨滴应在红/黄侧，冰雹应在蓝/青侧
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val dropHue = hueOf(p.dropFill)
            val hailHue = hueOf(p.hailFill)
            val warm = dropHue <= 60f || dropHue >= 330f
            val cool = hailHue >= 170f && hailHue <= 260f
            assertTrue("$scene 雨滴应为暖色（当前 $dropHue°）", warm)
            assertTrue("$scene 冰雹应为冷色（当前 $hailHue°）", cool)
        }
    }

    @Test
    fun `both tile types stand out against the play field they actually sit on`() {
        // 注意：衬在方块下面的是「底板叠在天空上」的有效底色，不是天空本身。
        // 早期版本直接拿天空色算，晴天场景下冰雹对比度只有 1.04（几乎隐形）。
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val backdrop = effectiveBackdrop(p)
            val dropC = contrastRatio(p.dropFill, backdrop)
            val hailC = contrastRatio(p.hailFill, backdrop)
            assertTrue("$scene 雨滴与底板对比不足（$dropC）", dropC >= 1.6f)
            assertTrue("$scene 冰雹与底板对比不足（$hailC）", hailC >= 1.6f)
        }
    }

    @Test
    fun `play field actually darkens the backdrop in every scene`() {
        // 底板必须真的把有效底色压暗，否则它只是个装饰
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            assertTrue("$scene 底板应压暗背景", relativeLuminance(effectiveBackdrop(p)) < relativeLuminance(p.skyBottom))
            assertTrue("$scene 底板不透明度应在 0.4~0.8", p.playFieldAlpha in 0.4f..0.8f)
        }
    }

    @Test
    fun `hail outline contrasts with the hail fill`() {
        // 描边是「禁击」的物理边界，描边与填充不分离就等于没画
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val c = contrastRatio(p.hailFill, p.hailEdge)
            assertTrue("$scene 冰雹描边与本体对比不足（$c）", c >= 3.0f)
        }
    }

    @Test
    fun `the tap target is brighter than the must-not-tap tile`() {
        // 早期版本冰雹比雨滴更亮，等于把"不能点"的做成最显眼的——方向完全反了
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            assertTrue(
                "$scene 雨滴应比冰雹亮（该点的必须更显眼）",
                relativeLuminance(p.dropFill) > relativeLuminance(p.hailFill),
            )
        }
    }

    @Test
    fun `tile types remain distinguishable without hue`() {
        // 色觉辅助/灰度场景：即使色相信息全丢，亮度也必须不同。
        // 这是"四重冗余"不依赖颜色的那一重，也是最容易被忽略的可达性底线。
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val la = relativeLuminance(p.dropFill)
            val lb = relativeLuminance(p.hailFill)
            assertNotEquals(
                "$scene 两类方块灰度相同，灰度屏或色弱下会看混",
                la,
                lb,
            )
            val ratio = maxOf(la, lb) / minOf(la, lb)
            assertTrue("$scene 灰度差异过小（$ratio）", ratio >= 1.25f)
        }
    }

    @Test
    fun `hittable ring is brighter than both tile fills`() {
        // 可点光环要在两类方块之上都能被看见，否则「现在该点哪块」这条动态信号失效
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val ring = relativeLuminance(p.hittableRing)
            assertTrue("$scene 光环不够亮（对雨滴）", ring > relativeLuminance(p.dropFill))
            assertTrue("$scene 光环不够亮（对冰雹）", ring > relativeLuminance(p.hailFill))
        }
    }

    @Test
    fun `judge line is visible against the play field`() {
        scenes.forEach { scene ->
            val p = rainPaletteOf(scene)
            val c = contrastRatio(p.judgeLine, effectiveBackdrop(p))
            assertTrue("$scene 判定线对比不足（$c）", c >= 3.0f)
        }
    }

    @Test
    fun `hail ratio stays within a share that keeps the board readable`() {
        // 区分度的另一侧：冰雹占比不能高到画面变成"一半不能点"的噪声
        for (t in 0..600 step 13) {
            assertTrue(
                "t=$t 冰雹占比过高",
                hailChanceAt(t.toFloat()) <= RAIN_HAIL_MAX,
            )
        }
        // 同时不能低到玩法退化
        assertTrue("峰值冰雹占比过低，机制无法被感知", RAIN_HAIL_MAX >= 0.30f)
        assertEquals(RAIN_HAIL_MAX, difficultyAt(600f).hailChance, 1e-3f)
    }
}
