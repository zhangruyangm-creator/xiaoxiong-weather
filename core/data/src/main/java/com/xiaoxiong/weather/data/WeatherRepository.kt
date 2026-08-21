package com.xiaoxiong.weather.data

import android.content.res.Resources
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 数据入口门面：按职责委托给搜索/预报/历史三个 fetcher。
 * 构造签名与 WeatherFetcher 接口保持不变，方便 AppContainer 与测试注入。
 */
class WeatherRepository(
    private val resources: Resources,
    private val client: OkHttpClient = defaultClient(),
    private val searchClient: OkHttpClient = defaultSearchClient(),
    private val json: Json = defaultJson(),
    geocodingBase: String = DEFAULT_GEOCODING_BASE,
    forecastBase: String = DEFAULT_FORECAST_BASE,
    airQualityBase: String = DEFAULT_AIR_QUALITY_BASE,
    archiveBase: String = DEFAULT_ARCHIVE_BASE,
) : WeatherFetcher {
    private val citySearch = CitySearchFetcher(resources, searchClient, json, geocodingBase)
    private val forecast = ForecastFetcher(resources, client, json, forecastBase, airQualityBase)
    private val history = HistoryFetcher(client, json, archiveBase)

    override suspend fun fetchWeather(city: City): WeatherSnapshot = forecast.fetchWeather(city)

    override suspend fun fetchPhenologyShiftDays(
        city: City,
        today: LocalDate,
    ): Int? = history.fetchPhenologyShiftDays(city, today)

    override suspend fun fetchHistoryDay(
        city: City,
        date: LocalDate,
    ): HistoryWeather? = history.fetchHistoryDay(city, date)

    override suspend fun fetchHistoryRange(
        city: City,
        start: LocalDate,
        end: LocalDate,
    ): HistoryRangeSummary? = history.fetchHistoryRange(city, start, end)

    suspend fun searchCities(query: String): List<City> = citySearch.searchCities(query)

    companion object {
        private const val DEFAULT_GEOCODING_BASE = "https://geocoding-api.open-meteo.com/v1"
        private const val DEFAULT_FORECAST_BASE = "https://api.open-meteo.com/v1"
        private const val DEFAULT_AIR_QUALITY_BASE = "https://air-quality-api.open-meteo.com/v1"
        private const val DEFAULT_ARCHIVE_BASE = "https://archive-api.open-meteo.com/v1"

        // 搜索辅助函数（实现见 CitySearchFetcher）；ChinaCityCatalog / LocationHelper 仍走门面调用
        fun normalizePlaceQuery(query: String): String = CitySearchFetcher.normalizePlaceQuery(query)

        fun containsHan(text: String): Boolean = CitySearchFetcher.containsHan(text)

        fun nearSamePlace(
            a: City,
            b: City,
        ): Boolean = CitySearchFetcher.nearSamePlace(a, b)

        fun mergeCityResults(
            preferred: List<City>,
            extras: List<City>,
        ): List<City> = CitySearchFetcher.mergeCityResults(preferred, extras)

        fun rankCityResults(
            query: String,
            cities: List<City>,
        ): List<City> = CitySearchFetcher.rankCityResults(query, cities)

        fun createDefaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

        fun createSearchClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .build()

        fun createJson(): Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                // 网络侧 DTO：个别字段类型漂移/缺失时归零值，提升解析健壮性
                coerceInputValues = true
            }

        private fun defaultClient(): OkHttpClient = createDefaultClient()

        private fun defaultSearchClient(): OkHttpClient = createSearchClient()

        private fun defaultJson(): Json = createJson()
    }
}
