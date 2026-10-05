package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.data.remote.ArchiveDailyDto
import com.xiaoxiong.weather.data.remote.ArchiveResponse
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

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
                    .addQueryParameter("latitude", openMeteoQueryCoord(city.latitude))
                    .addQueryParameter("longitude", openMeteoQueryCoord(city.longitude))
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
                } catch (e: AppError) {
                    throw e
                } catch (e: Exception) {
                    // 网络/解析失败要抛出去而不是返回 null：null 的语义是"确实没有
                    // 足够数据"，调用方会把它缓存一整天。若这里也返回 null，一次断网
                    // 会把当天剩余时间的物候重试全部抑制掉。
                    throw AppError.DataFormat(e)
                }
            val daily = response.daily ?: return@withContext null

            val records =
                daily.time.mapIndexedNotNull { index, time ->
                    val date = runCatching { java.time.LocalDate.parse(time) }.getOrNull()
                    val mean =
                        WeatherValueClamp.temperatureC(
                            WeatherValueClamp.finiteOrNull(daily.temperatureMean.getOrNull(index)),
                        )
                    if (date == null || mean == null) null else date to mean
                }

            val thisYear = today.year
            val fromDate = java.time.LocalDate.of(thisYear, 1, 1)
            val thisYearRecords = records.filter { it.first.year == thisYear }
            val normalByDay = GrowingDegreeDays.averageByDayOfYear(records.filter { it.first.year < thisYear })
            val (thisYearTemps, normalTemps) =
                GrowingDegreeDays.alignedDailyTemps(
                    thisYearRecords = thisYearRecords,
                    normalByDay = normalByDay,
                    fromDate = fromDate,
                    throughDate = endDate,
                )
            if (thisYearTemps.size < GrowingDegreeDays.MIN_DAYS ||
                normalTemps.size < GrowingDegreeDays.MIN_DAYS
            ) {
                return@withContext null
            }

            GrowingDegreeDays.shiftDays(thisYearTemps, normalTemps)
        }

    /**
     * 查询某天的历史天气（Archive / ERA5）；该日期确实无数据返回 null，
     * 网络/服务端错误抛 [AppError]（HTTP、连接失败、响应解析失败）。
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
                    .addQueryParameter("latitude", openMeteoQueryCoord(city.latitude))
                    .addQueryParameter("longitude", openMeteoQueryCoord(city.longitude))
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
                } catch (e: AppError) {
                    throw e
                } catch (e: Exception) {
                    throw AppError.DataFormat(e)
                }
            val daily = response.daily ?: return@withContext null
            // 单天请求 API 应只返回一行；如返回多天（API 异常或请求参数被改），
            // 此前会静默取首行导致用户查的是 5/10 却拿到 5/1 的数据而 UI 不报错，
            // 这里只接受唯一一行，多行则视为数据异常返回 null
            if (daily.time.size != 1) return@withContext null
            val day = daily.time.first()
            val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@withContext null
            if (parsed != date) return@withContext null

            archiveDailyToHistory(daily, index = 0, date = day)
        }

    /**
     * 查询一段日期范围的历史天气（Archive / ERA5）并本地聚合三个极值：
     * 最高温（temperature_2m_max 最大）、最低温（temperature_2m_min 最小）、
     * 最多降水（precipitation_sum 最大）。逐日列表也一并返回供 UI 展示。
     * 区间内无任何可用数据返回 null；网络/服务端错误抛 [AppError]。
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
                    .addQueryParameter("latitude", openMeteoQueryCoord(city.latitude))
                    .addQueryParameter("longitude", openMeteoQueryCoord(city.longitude))
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
                } catch (e: AppError) {
                    throw e
                } catch (e: Exception) {
                    throw AppError.DataFormat(e)
                } ?: return@withContext null

            val days =
                daily.time.mapIndexedNotNull { index, day ->
                    val parsed = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return@mapIndexedNotNull null
                    if (parsed < start || parsed > end) return@mapIndexedNotNull null
                    archiveDailyToHistory(daily, index = index, date = day)
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
                months = monthlySummaries(days),
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

    /**
     * 从 archive daily 的某一列构造 [HistoryWeather]，并统一夹紧物理量。
     *
     * 历史数据同样来自远端，会原样进入折线图与极值摘要；与实况走同一套区间，
     * 否则"实况有夹紧、历史没有"就成了半截防护（离谱值仍能上屏或撑爆图表）。
     */
    internal fun archiveDailyToHistory(
        daily: ArchiveDailyDto,
        index: Int,
        date: String,
    ): HistoryWeather =
        HistoryWeather(
            date = date,
            weatherCode = WeatherCodes.normalizeCode(daily.weatherCode.getOrNull(index)),
            tempMaxC = WeatherValueClamp.temperatureC(daily.tempMax.getOrNull(index)),
            tempMinC = WeatherValueClamp.temperatureC(daily.tempMin.getOrNull(index)),
            tempMeanC = WeatherValueClamp.temperatureC(daily.temperatureMean.getOrNull(index)),
            precipSumMm = WeatherValueClamp.precipitationMm(daily.precipSum.getOrNull(index)),
            humidityMeanPercent = WeatherValueClamp.percent(daily.humidityMean.getOrNull(index)),
            windMaxKmh = WeatherValueClamp.windKmh(daily.windMax.getOrNull(index)),
            sunrise = daily.sunrise.getOrNull(index),
            sunset = daily.sunset.getOrNull(index),
        )

    companion object {
        /** 常年对比窗口年数（含今年共 N 年），N-1 年用于多年平均。 */
        private const val PHENOLOGY_YEARS = 6

        /** Open-Meteo 查询坐标必须用小数点，德/法默认 Locale 会写成逗号导致 400。 */
        internal fun openMeteoQueryCoord(value: Double): String = String.format(Locale.US, "%.4f", value)

        /** 日降水量 ≥ 此值（mm）计为"降水日"。 */
        private const val PRECIP_DAY_MM = 0.1

        /**
         * 按「年-月」聚合逐日数据，返回按月排序的汇总列表：
         * 最高温取当月 tempMaxC 最大、最低温取 tempMinC 最小、总降水求和、降水天数统计。
         * 纯函数，供单测；月份按时间升序。
         */
        fun monthlySummaries(days: List<HistoryWeather>): List<HistoryMonthSummary> {
            val byMonth = LinkedHashMap<Pair<Int, Int>, MutableList<HistoryWeather>>()
            for (day in days) {
                val date =
                    runCatching { java.time.LocalDate.parse(day.date) }.getOrNull()
                        ?: continue
                byMonth.getOrPut(date.year to date.monthValue) { mutableListOf() } += day
            }
            return byMonth.map { (key, list) ->
                val precip = list.mapNotNull { it.precipSumMm }
                HistoryMonthSummary(
                    year = key.first,
                    month = key.second,
                    highestTempC = list.mapNotNull { it.tempMaxC }.maxOrNull(),
                    lowestTempC = list.mapNotNull { it.tempMinC }.minOrNull(),
                    totalPrecipMm = if (precip.isEmpty()) null else precip.sum(),
                    precipDays = list.count { (it.precipSumMm ?: 0.0) >= PRECIP_DAY_MM },
                )
            }
        }
    }
}
