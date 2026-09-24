package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 烘焙缓存的**键与容量语义**。
 *
 * 为什么不测真实烘焙：位图烘焙依赖 Android graphics（`ImageBitmap(w,h)` 在纯 JVM
 * 单测里直接抛 not-mocked），像素结果也只能在设备/仪器测试里看。这里只覆盖与
 * 绘制无关、但最容易出错的逻辑——键相等、近似复用、容量淘汰、失败不入缓存——
 * 这些正是"换关/换主题/窗口拖动时会不会错误复用旧图"的实际风险点。
 */
class BakedLayerCacheTest {
    /** 与 [BakedLayerCache] 内部一致：业务标识 + 量化后的位图尺寸。 */
    private data class LayerKey(
        val tag: String,
        val widthPx: Int,
        val heightPx: Int,
    )

    private fun key(
        tag: String,
        w: Int,
        h: Int,
    ) = LayerKey(tag, w, h)

    @Test
    fun sameKey_getDoesNotGrowCache() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 2)
        cache.put(key("a", 8, 8), "img-a")
        cache.get(key("a", 8, 8))
        cache.get(key("a", 8, 8))
        assertEquals(1, cache.size)
    }

    @Test
    fun differentSize_sameTag_notReusedByDefault() {
        // 尺寸必须进 key：窗口变化后不能拿旧尺寸的图拉伸复用（会糊）
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 2)
        cache.put(key("board", 100, 200), "img-old")
        assertNull(cache.get(key("board", 101, 200)))
    }

    @Test
    fun nearReusable_servesOldEntryWithoutGrowing() {
        // 冰球/台球的做法：仅尺寸容差可放宽，主题/几何必须完全一致
        val cache =
            BakedLayerLru<LayerKey, String>(
                maxEntries = 2,
                reusable = { cached, target ->
                    cached.tag == target.tag &&
                        kotlin.math.abs(cached.widthPx - target.widthPx) <= 1
                },
            )
        cache.put(key("board", 100, 200), "img-old")
        assertEquals("img-old", cache.get(key("board", 101, 200)))
        assertEquals(1, cache.size)
    }

    @Test
    fun nearReusable_stillRejectsDifferentTag() {
        val cache =
            BakedLayerLru<LayerKey, String>(
                maxEntries = 2,
                reusable = { cached, target ->
                    cached.tag == target.tag &&
                        kotlin.math.abs(cached.widthPx - target.widthPx) <= 1
                },
            )
        cache.put(key("theme-ice", 100, 200), "img-ice")
        assertNull("换主题不得复用旧主题的图", cache.get(key("theme-wood", 100, 200)))
    }

    @Test
    fun capacityOne_keepsOnlyNewest() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 1)
        cache.put(key("a", 8, 8), "img-a")
        cache.put(key("b", 8, 8), "img-b")
        assertEquals(1, cache.size)
        assertNull(cache.get(key("a", 8, 8)))
        assertEquals("img-b", cache.get(key("b", 8, 8)))
    }

    @Test
    fun getOrPut_evictsBeforeBakeSoPeakStaysAtCapacity() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 1)
        cache.put(key("a", 8, 8), "img-a")
        var sizeDuringBake = -1
        cache.getOrPut(key("b", 8, 8)) {
            sizeDuringBake = cache.size
            "img-b"
        }
        assertEquals("烘焙前应已淘汰最旧条目，避免峰值双份位图", 0, sizeDuringBake)
        assertEquals(1, cache.size)
    }

    @Test
    fun bakeReturningNull_isNotCached() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 2)
        assertNull(cache.getOrPut(key("bad", 8, 8)) { null })
        assertEquals("烘焙失败（OOM）不应入缓存，调用方需回退直接绘制", 0, cache.size)
    }

    @Test
    fun zeroCapacity_bakesWithoutCaching() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 0)
        var bakes = 0
        repeat(2) {
            cache.getOrPut(key("a", 8, 8)) {
                bakes++
                "img"
            }
        }
        assertEquals("容量 0 时每次都烘焙但不入缓存", 2, bakes)
        assertEquals(0, cache.size)
    }

    @Test
    fun clear_releasesAll() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 2)
        cache.put(key("a", 8, 8), "img-a")
        assertTrue(cache.size > 0)
        cache.clear()
        assertEquals(0, cache.size)
        assertFalse(cache.keys.isNotEmpty())
    }

    @Test
    fun exactHitPromotes_lruKeepsRecentlyUsed() {
        val cache = BakedLayerLru<LayerKey, String>(maxEntries = 2)
        cache.put(key("a", 8, 8), "img-a")
        cache.put(key("b", 8, 8), "img-b")
        // 触碰 a 让它变成最新，随后插入 c 应淘汰 b
        cache.get(key("a", 8, 8))
        cache.put(key("c", 8, 8), "img-c")
        assertNotNull(cache.get(key("a", 8, 8)))
        assertNull(cache.get(key("b", 8, 8)))
        assertNotNull(cache.get(key("c", 8, 8)))
    }
}
