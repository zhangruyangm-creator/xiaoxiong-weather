package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.data.remote.ArchiveResponse
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** 历史天气与物候：Open-Meteo Archive（ERA5）+ 积温换算。 */
class HistoryFetcher(
    private val client: OkHttpClient,
    private val json: Json,
    private val archiveBase: String,
) {
    suspend fun fetchPhenologyShiftDays(
        city: City,
        today: java.time.LocalDate,
    ): Int? =
        withContext(Dispatchers.IO) {
            val startYear = today.year - PHENOLOGY_YEARS
            // ERA5 再分析数据延迟约 1 天，end_date 用昨天，否则 archive 接口报
            // "end_date out of allowed range" 直接失败（物候偏移一天延迟无感）。
            val endDate = today.minusDays(1)
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", "$startYear-01-01")
                    .addQueryParameter("end_date", endDate.toString())
                    .addQueryParameter(
                        "daily",
                        "temperature_2m_mean",
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val response =
                try {
                    decodeArchive(OpenMeteoHttp.execute(url.toString(), client))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext null
                }
            val daily = response.daily ?: return@withContext null

            val records =
                daily.time.mapIndexedNotNull { index, time ->
                    val date = runCatching { java.time.LocalDate.parse(time) }.getOrNull()
                    val mean = daily.temperatureMean.getOrNull(index)
                    if (date == null || mean == null) null else date to mean
                }

            val thisYear = today.year
            val thisYearTemps =
                records.filter { it.first.year == thisYear }.map { it.second }
            if (thisYearTemps.size < GrowingDegreeDays.MIN_DAYS) return@withContext null

            val normalByDay = GrowingDegreeDays.averageByDayOfYear(records.filter { it.first.year < thisYear })
            val normalTemps =
                GrowingDegreeDays.normalTempsUntil(normalByDay, today, java.time.LocalDate.of(thisYear, 1, 1))
            if (normalTemps.size < GrowingDegreeDays.MIN_DAYS) return@withContext null

            GrowingDegreeDays.shiftDays(thisYearTemps, normalTemps)
        }

    /**
     * 查询某天的历史天气（Archive / ERA5），失败或无数据返回 null。
     *
     * 用户主动查询单日，不参与主天气加载；date 通常为昨天或更早
     * （ERA5 延迟约 1 天）。单日请求极小，ViewModel 层再按内存缓存去重。
     */
    suspend fun fetchHistoryDay(
        city: City,
        date: java.time.LocalDate,
    ): HistoryWeather? =
        withContext(Dispatchers.IO) {
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", date.toString())
                    .addQueryParameter("end_date", date.toString())
                    .addQueryParameter(
                        "daily",
                        listOf(
                            "weather_code",
                            "temperature_2m_max",
                            "temperature_2m_min",
                            "temperature_2m_mean",
                            "precipitation_sum",
                            "relative_humidity_2m_mean",
                            "wind_speed_10m_max",
                            "sunrise",
                            "sunset",
                        ).joinToString(","),
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val response =
                try {
                    decodeArchive(OpenMeteoHttp.execute(url.toString(), client))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext null
                }
            val daily = response.daily ?: return@withContext null
            val day = daily.time.firstOrNull() ?: return@withContext null
            val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@withContext null
            if (parsed != date) return@withContext null

            HistoryWeather(
                date = day,
                weatherCode = WeatherCodes.normalizeCode(daily.weatherCode.getOrNull(0)),
                tempMaxC = daily.tempMax.getOrNull(0),
                tempMinC = daily.tempMin.getOrNull(0),
                tempMeanC = daily.temperatureMean.getOrNull(0),
                precipSumMm = daily.precipSum.getOrNull(0),
                humidityMeanPercent = daily.humidityMean.getOrNull(0),
                windMaxKmh = daily.windMax.getOrNull(0),
                sunrise = daily.sunrise.getOrNull(0),
                sunset = daily.sunset.getOrNull(0),
            )
        }

    /**
     * 查询一段日期范围的历史天气（Archive / ERA5）并本地聚合三个极值：
     * 最高温（temperature_2m_max 最大）、最低温（temperature_2m_min 最小）、
     * 最多降水（precipitation_sum 最大）。逐日列表也一并返回供 UI 展示。
     */
    suspend fun fetchHistoryRange(
        city: City,
        start: java.time.LocalDate,
        end: java.time.LocalDate,
    ): HistoryRangeSummary? {
        if (end.isBefore(start)) return null
        return withContext(Dispatchers.IO) {
            val url =
                archiveBase
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("archive")
                    .addQueryParameter("latitude", city.latitude.toString())
                    .addQueryParameter("longitude", city.longitude.toString())
                    .addQueryParameter("start_date", start.toString())
                    .addQueryParameter("end_date", end.toString())
                    .addQueryParameter(
                        "daily",
                        listOf(
                            "weather_code",
                            "temperature_2m_max",
                            "temperature_2m_min",
                            "temperature_2m_mean",
                            "precipitation_sum",
                            "relative_humidity_2m_mean",
                            "wind_speed_10m_max",
                            "sunrise",
                            "sunset",
                        ).joinToString(","),
                    ).addQueryParameter("timezone", "auto")
                    .build()

            val daily =
                try {
                    decodeArchive(OpenMeteoHttp.execute(url.toString(), client)).daily
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@withContext null

            val days =
                daily.time.mapIndexedNotNull { index, day ->
                    val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@mapIndexedNotNull null
                    if (parsed < start || parsed > end) return@mapIndexedNotNull null
                    HistoryWeather(
                        date = day,
                        weatherCode = WeatherCodes.normalizeCode(daily.weatherCode.getOrNull(index)),
                        tempMaxC = daily.tempMax.getOrNull(index),
                        tempMinC = daily.tempMin.getOrNull(index),
                        tempMeanC = daily.temperatureMean.getOrNull(index),
                        precipSumMm = daily.precipSum.getOrNull(index),
                        humidityMeanPercent = daily.humidityMean.getOrNull(index),
                        windMaxKmh = daily.windMax.getOrNull(index),
                        sunrise = daily.sunrise.getOrNull(index),
                        sunset = daily.sunset.getOrNull(index),
                    )
                }
            if (days.isEmpty()) return@withContext null

            fun extreme(
                select: (HistoryWeather) -> Double?,
                preferMax: Boolean,
            ): HistoryExtreme? {
                val best =
                    days.fold(null as HistoryWeather?) { acc, day ->
                        val v = select(day) ?: return@fold acc
                        if (acc == null) {
                            day
                        } else {
                            val accV = select(acc) ?: return@fold acc
                            if (if (preferMax) v > accV else v < accV) day else acc
                        }
                    } ?: return null
                return HistoryExtreme(
                    value = select(best) ?: return null,
                    date = best.date,
                    weatherCode = best.weatherCode,
                )
            }

            HistoryRangeSummary(
                startDate = start.toString(),
                endDate = end.toString(),
                days = days,
                hottest = extreme({ it.tempMaxC }, preferMax = true),
                coldest = extreme({ it.tempMinC }, preferMax = false),
                wettest = extreme({ it.precipSumMm }, preferMax = true),
            )
        }
    }

    /**
     * 小时序列来自 timezone=auto，是城市本地墙钟时间；
     * 不能用设备 [ZoneId.systemDefault]，否则跨时区会偏一小时甚至整天。
     */

    private fun decodeArchive(body: String): ArchiveResponse =
        try {
            json.decodeFromString(ArchiveResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    companion object {
        /** 常年对比窗口年数（含今年共 N 年），N-1 年用于多年平均。 */
        private const val PHENOLOGY_YEARS = 6
    }
}
