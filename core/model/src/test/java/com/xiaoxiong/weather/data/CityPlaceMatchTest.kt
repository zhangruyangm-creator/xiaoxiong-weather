package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityPlaceMatchTest {
    private fun city(
        latitude: Double,
        longitude: Double,
        name: String = "测试",
    ): City = City(id = name.hashCode().toLong(), name = name, latitude = latitude, longitude = longitude)

    @Test
    fun samePlace_trueWhenBothAxesWithinEpsilon() {
        val a = city(36.65, 117.12)
        assertTrue(a.isSamePlace(city(36.659, 117.129)))
    }

    @Test
    fun samePlace_falseWhenEitherAxisBeyondEpsilon() {
        val a = city(36.65, 117.12)
        // 0.0101 > 0.01
        assertFalse(a.isSamePlace(city(36.6601, 117.12)))
        assertFalse(a.isSamePlace(city(36.65, 117.1301)))
        // 一轴命中、另一轴超阈值
        assertFalse(a.isSamePlace(city(36.659, 117.1301)))
    }

    @Test
    fun samePlace_epsilonIsStrictlyLessThan() {
        val a = city(1.0, 1.0)
        assertTrue(a.isSamePlace(city(1.25, 1.25), epsilon = 0.5))
        assertFalse(a.isSamePlace(city(1.5, 1.5), epsilon = 0.5))
    }

    @Test
    fun samePlace_handlesNegativeCoordinates() {
        val a = city(-33.86, -151.21)
        assertTrue(a.isSamePlace(city(-33.869, -151.219)))
        assertFalse(a.isSamePlace(city(-33.871, -151.21)))
    }

    @Test
    fun samePlace_missingCoordinatesUseUpstreamZeroWithoutNameOrIdFallback() {
        // 上游 DTO 缺坐标时落为默认 0.0：两个缺坐标城市按 (0,0) 判为同城，与替换前一致。
        val a = city(0.0, 0.0, name = "甲").copy(id = 1L)
        val b = city(0.0, 0.0, name = "乙").copy(id = 2L)
        assertTrue(a.isSamePlace(b))
        // 名称/ID 相同但坐标不同：仍按坐标判为不同城，不做名称/ID 回退。
        val c = city(10.0, 10.0, name = "甲").copy(id = 1L)
        assertFalse(a.isSamePlace(c))
        // NaN 由严格小于自然判为不同城。
        assertFalse(a.isSamePlace(city(Double.NaN, Double.NaN)))
    }

    @Test
    fun samePlace_doesNotWrapAcrossAntimeridian() {
        // 明确不处理跨 ±180° 经线：按普通数值差比较，判为不同城。
        val a = city(0.0, 179.99)
        assertFalse(a.isSamePlace(city(0.0, -179.99)))
    }

    @Test
    fun nearSamePlace_usesWiderDefaultEpsilon() {
        val a = city(36.65, 117.12)
        assertTrue(a.isNearSamePlace(city(36.72, 117.15)))
        assertFalse(a.isNearSamePlace(city(36.7401, 117.12)))
        // 显式收窄到 0.01 时与严格同城一致
        assertTrue(a.isNearSamePlace(city(36.659, 117.129), epsilon = 0.01))
        assertFalse(a.isNearSamePlace(city(36.6601, 117.12), epsilon = 0.01))
    }
}
