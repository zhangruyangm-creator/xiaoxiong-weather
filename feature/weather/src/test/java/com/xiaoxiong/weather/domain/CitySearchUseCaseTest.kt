package com.xiaoxiong.weather.domain

import com.xiaoxiong.weather.data.AppError
import com.xiaoxiong.weather.data.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CitySearchUseCaseTest {
    private fun city(
        name: String,
        lat: Double = 36.65,
        lon: Double = 117.12,
    ) = City(id = name.hashCode().toLong(), name = name, latitude = lat, longitude = lon)

    @Test
    fun networkFailure_emptyCatalog_throwsInsteadOfEmptyList() {
        assertThrows(AppError.NetworkUnavailable::class.java) {
            mergeRemoteCitySearch(
                query = "London",
                catalog = emptyList(),
                remote = Result.failure(AppError.NetworkUnavailable()),
                geocoder = emptyList(),
            )
        }
    }

    @Test
    fun timeout_emptyCatalog_throwsNetworkUnavailable() {
        assertThrows(AppError.NetworkUnavailable::class.java) {
            mergeRemoteCitySearch(
                query = "London",
                catalog = emptyList(),
                remote = Result.failure(AppError.NetworkUnavailable()),
                geocoder = emptyList(),
                timedOut = true,
            )
        }
    }

    @Test
    fun networkFailure_keepsLocalCatalog() {
        val result =
            mergeRemoteCitySearch(
                query = "济南",
                catalog = listOf(city("济南")),
                remote = Result.failure(AppError.Http(503)),
                geocoder = emptyList(),
            )
        assertEquals("济南", result.single().name)
    }

    @Test
    fun emptyRemoteSuccess_emptyCatalog_isGenuineNotFound() {
        val result =
            mergeRemoteCitySearch(
                query = "zzzzzz",
                catalog = emptyList(),
                remote = Result.success(emptyList()),
                geocoder = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun geocoderHits_coverRemoteFailure() {
        val result =
            mergeRemoteCitySearch(
                query = "纽约",
                catalog = emptyList(),
                remote = Result.failure(AppError.NetworkUnavailable()),
                geocoder = listOf(city("New York", 40.7, -74.0)),
            )
        assertEquals("New York", result.single().name)
    }
}
