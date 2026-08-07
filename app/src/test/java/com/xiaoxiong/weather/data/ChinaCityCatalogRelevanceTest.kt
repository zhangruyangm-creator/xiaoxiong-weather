package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.fixtures.WeatherFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinaCityCatalogRelevanceTest {

    @Test
    fun relevanceScore_nameMatchLevels() {
        val city = WeatherFixtures.city("济南", admin1 = "山东", admin2 = "济南市")
        assertEquals(100, ChinaCityCatalog.relevanceScore(city, "济南"))
        assertEquals(100, ChinaCityCatalog.relevanceScore(city, "济南市")) // normalize
        assertEquals(90, ChinaCityCatalog.relevanceScore(city, "济"))
        assertEquals(0, ChinaCityCatalog.relevanceScore(city, ""))
    }

    @Test
    fun relevanceScore_adminFallback() {
        val city = WeatherFixtures.city("章丘", admin1 = "山东", admin2 = "济南市")
        assertEquals(40, ChinaCityCatalog.relevanceScore(city, "济南"))
        assertEquals(20, ChinaCityCatalog.relevanceScore(city, "山东"))
    }

    @Test
    fun relevanceScore_pinyinMatch() {
        val city = WeatherFixtures.city("北京")
        assertEquals(85, ChinaCityCatalog.relevanceScore(city, "beijing"))
    }

    @Test
    fun hasStrongMatch() {
        val cities = listOf(
            WeatherFixtures.city("潍坊"),
            WeatherFixtures.city("诸城", admin2 = "潍坊市")
        )
        assertTrue(ChinaCityCatalog.hasStrongMatch("诸城", cities))
        assertFalse(ChinaCityCatalog.hasStrongMatch("xyz", cities))
    }
}
