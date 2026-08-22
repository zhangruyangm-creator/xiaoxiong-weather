package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryHelpersTest {
    @Test
    fun normalizePlaceQuery_stripsSuffixes() {
        assertEquals("北京", CitySearchFetcher.normalizePlaceQuery("北京市"))
        assertEquals("历下", CitySearchFetcher.normalizePlaceQuery("历下区"))
        assertEquals("济南", CitySearchFetcher.normalizePlaceQuery(" 济南市 "))
        // “西安区”会被剥成“西安”，与陕西省会“西安”同名；
        // 热门城市选择因此必须精确全名优先，否则“西安区”可能顶掉“西安”。
        assertEquals("西安", CitySearchFetcher.normalizePlaceQuery("西安区"))
    }

    @Test
    fun normalizePlaceQuery_keepsShortNames() {
        // 剥掉后不足 2 字则保留原串
        assertEquals("市", CitySearchFetcher.normalizePlaceQuery("市"))
    }

    @Test
    fun containsHan_andPinyin() {
        assertTrue(CitySearchFetcher.containsHan("北京"))
        assertFalse(CitySearchFetcher.containsHan("Beijing"))
        assertEquals("beijing", CitySearchFetcher.toPinyinQuery("北京"))
    }

    @Test
    fun nearSamePlace_threshold() {
        val a = WeatherFixtures.city("A", lat = 36.65, lon = 117.12)
        val near = WeatherFixtures.city("B", lat = 36.70, lon = 117.15)
        val far = WeatherFixtures.city("C", lat = 37.00, lon = 117.12)
        assertTrue(CitySearchFetcher.nearSamePlace(a, near))
        assertFalse(CitySearchFetcher.nearSamePlace(a, far))
    }

    @Test
    fun mergeCityResults_prefersFirstAndDedups() {
        val preferred = listOf(WeatherFixtures.city("济南", lat = 36.65, lon = 117.12))
        val extras =
            listOf(
                WeatherFixtures.city("济南市", lat = 36.66, lon = 117.13), // near-dup
                WeatherFixtures.city("青岛", lat = 36.07, lon = 120.38),
            )
        val merged = CitySearchFetcher.mergeCityResults(preferred, extras)
        assertEquals(2, merged.size)
        assertEquals("济南", merged[0].name)
        assertEquals("青岛", merged[1].name)
    }
}
