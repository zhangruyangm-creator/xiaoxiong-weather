package com.xiaoxiong.weather.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityStoreTimezoneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun favoriteDto_decodesLegacyJsonWithoutTimezone() {
        val raw = """{"id":1,"name":"青岛市","latitude":36.06,"longitude":120.38}"""
        val dto = json.decodeFromString(FavoriteCityStore.FavoriteCityDto.serializer(), raw)

        assertNull(dto.timezone)
        assertNull(dto.toCity().timezone)
    }

    @Test
    fun favoriteDto_roundTripsTimezone() {
        val city = City(id = 1L, name = "青岛市", latitude = 36.06, longitude = 120.38, timezone = "Asia/Shanghai")
        val encoded =
            json.encodeToString(
                FavoriteCityStore.FavoriteCityDto.serializer(),
                FavoriteCityStore.FavoriteCityDto.from(city),
            )

        assertEquals(
            "Asia/Shanghai",
            json.decodeFromString(FavoriteCityStore.FavoriteCityDto.serializer(), encoded).toCity().timezone,
        )
    }

    @Test
    fun recentDto_decodesLegacyJsonWithoutTimezone() {
        val raw = """{"id":2,"name":"济南市","latitude":36.65,"longitude":117.12}"""
        val dto = json.decodeFromString(RecentCityStore.RecentCityDto.serializer(), raw)

        assertNull(dto.timezone)
        assertNull(dto.toCity().timezone)
    }

    @Test
    fun recentDto_roundTripsTimezone() {
        val city = City(id = 2L, name = "济南市", latitude = 36.65, longitude = 117.12, timezone = "Asia/Shanghai")
        val encoded =
            json.encodeToString(
                RecentCityStore.RecentCityDto.serializer(),
                RecentCityStore.RecentCityDto.from(city),
            )

        assertEquals(
            "Asia/Shanghai",
            json.decodeFromString(RecentCityStore.RecentCityDto.serializer(), encoded).toCity().timezone,
        )
    }
}
