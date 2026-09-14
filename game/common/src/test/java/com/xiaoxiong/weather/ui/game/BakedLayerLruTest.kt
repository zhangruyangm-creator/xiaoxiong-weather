package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [BakedLayerLru] 的 JVM 单测：命中提升、近似复用、先淘汰再分配、容量边界。 */
class BakedLayerLruTest {
    @Test
    fun `getOrPut bakes once per key and caches result`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)
        var bakes = 0

        val first =
            cache.getOrPut(1) {
                bakes++
                "a"
            }
        val second =
            cache.getOrPut(1) {
                bakes++
                "b"
            }

        assertEquals("a", first)
        assertEquals("a", second)
        assertEquals(1, bakes)
        assertEquals(1, cache.size)
    }

    @Test
    fun `exact hit promotes entry to most recent`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)
        cache.put(1, "a")
        cache.put(2, "b")

        assertEquals("a", cache.get(1))
        assertEquals(listOf(2, 1), cache.keys)

        // A 刚被访问过，超容量时被淘汰的是 B
        cache.getOrPut(3) { "c" }
        assertEquals(listOf(1, 3), cache.keys)
        assertNull(cache.get(2))
    }

    @Test
    fun `reusable hit reuses cached value without baking`() {
        val cache =
            BakedLayerLru<Int, String>(
                maxEntries = 2,
                reusable = { cached, target -> cached == target - 100 },
            )
        cache.put(10, "cached")

        var bakes = 0
        val reused =
            cache.getOrPut(110) {
                bakes++
                "fresh"
            }
        assertEquals("cached", reused)
        assertEquals(0, bakes)
        assertEquals(listOf(10), cache.keys)
    }

    @Test
    fun `reusable hit does not refresh recency`() {
        val cache =
            BakedLayerLru<Int, String>(
                maxEntries = 2,
                reusable = { cached, target -> cached == target - 100 },
            )
        cache.put(10, "a")
        cache.put(20, "b")

        assertEquals("a", cache.get(110))
        // 近似命中不提升，顺序仍是旧的在前
        assertEquals(listOf(10, 20), cache.keys)

        cache.getOrPut(30) { "c" }
        assertEquals(listOf(20, 30), cache.keys)
    }

    @Test
    fun `evicts oldest before baking to bound peak memory`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)
        cache.put(1, "a")
        cache.put(2, "b")

        val sizeAtBake = mutableListOf<Int>()
        val result =
            cache.getOrPut(3) {
                sizeAtBake += cache.size
                "c"
            }

        assertEquals("c", result)
        assertEquals(listOf(1), sizeAtBake)
        assertEquals(listOf(2, 3), cache.keys)
    }

    @Test
    fun `zero capacity bakes without caching`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 0)
        var bakes = 0

        cache.getOrPut(1) {
            bakes++
            "a"
        }
        cache.getOrPut(1) {
            bakes++
            "b"
        }

        assertEquals(2, bakes)
        assertEquals(0, cache.size)
    }

    @Test
    fun `failed bake is not cached`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)

        assertNull(cache.getOrPut(1) { null })
        assertEquals(0, cache.size)
        assertNull(cache.get(1))
    }

    @Test
    fun `put overwrites same key and promotes it`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)
        cache.put(1, "a")
        cache.put(2, "b")
        cache.put(1, "a2")

        assertEquals(listOf(2, 1), cache.keys)
        assertEquals("a2", cache.get(1))
    }

    @Test
    fun `clear empties cache`() {
        val cache = BakedLayerLru<Int, String>(maxEntries = 2)
        cache.put(1, "a")
        cache.put(2, "b")

        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache.get(1))
    }
}
