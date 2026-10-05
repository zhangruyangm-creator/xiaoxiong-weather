package com.xiaoxiong.weather.data

import android.content.res.Resources
import com.github.promeg.pinyinhelper.Pinyin
import com.xiaoxiong.weather.data.remote.GeocodingResponse
import com.xiaoxiong.weather.data.remote.OpenMeteoHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** 城市搜索：Open-Meteo 地理编码 + 拼音 + 中文→英文别名 + 中国城市目录重写排序。 */
class CitySearchFetcher(
    private val resources: Resources,
    private val searchClient: OkHttpClient,
    private val json: Json,
    private val geocodingBase: String,
) {
    /** 相同 normalizedQuery 的短 TTL 缓存（60–120s），避免输入抖动/重复搜索反复打上游。 */
    private val resultCache = CitySearchCache()

    /** 同一 query 的并发请求合并为一次上游调用。 */
    private val singleFlight = SingleFlight<String, List<City>>()

    suspend fun searchCities(query: String): List<City> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val normalized = normalizePlaceQuery(trimmed)
            resultCache.get(normalized, System.currentTimeMillis())?.let { return@withContext it }

            val result =
                singleFlight.run(normalized) {
                    withContext(NonCancellable) { searchUpstream(normalized) }
                }
            resultCache.put(normalized, result, System.currentTimeMillis())
            result
        }

    private suspend fun searchUpstream(normalized: String): List<City> {
        val pinyinQuery = if (containsHan(normalized)) toPinyinQuery(normalized) else ""
        // Open-Meteo 对「纽约」等中文世界城市常搜不到，用英文别名补查
        val englishAlias = ForeignCityAliases.get(resources)[normalized]
        return coroutineScope {
            // 主查询错误应抛出（让 UI 显示网络异常）；拼音/英文别名是补充，失败静默
            val primaryDeferred = async { searchOpenMeteo(normalized) }
            val pinyinDeferred =
                async {
                    if (pinyinQuery.length >= 2) {
                        searchOpenMeteoOrEmpty(pinyinQuery)
                    } else {
                        emptyList()
                    }
                }
            val englishDeferred =
                async {
                    if (!englishAlias.isNullOrBlank()) {
                        searchOpenMeteoOrEmpty(englishAlias)
                    } else {
                        emptyList()
                    }
                }
            val primary = mergeCityResults(primaryDeferred.await(), pinyinDeferred.await())
            val english = englishDeferred.await()
            // 有中文→英文别名时优先英文结果：避免「高雄/花莲」等命中大陆同名点
            if (english.isNotEmpty() && !englishAlias.isNullOrBlank()) {
                mergeCityResults(english, primary)
            } else {
                mergeCityResults(primary, english)
            }
        }
    }

    // 拼音/英文别名分支是辅助查询，失败降级为空列表：但 CancellationException 必须透传，
    // 避免挂后台时该协程被取消却被吞掉、走完 fallback 返回"无结果"列表而无法传播取消信号
    private suspend fun searchOpenMeteoOrEmpty(name: String): List<City> =
        try {
            searchOpenMeteo(name)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun searchOpenMeteo(name: String): List<City> {
        val url =
            geocodingBase
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("name", name)
                .addQueryParameter("count", "6")
                .addQueryParameter("language", "zh")
                .addQueryParameter("format", "json")
                .build()

        val response = decodeGeocoding(OpenMeteoHttp.execute(url.toString(), searchClient))

        // 单条残缺（缺 name / 缺坐标 / 非法坐标）直接丢弃，不拖垮整次搜索。
        // 坐标必须是 JSON 里的真实字段：DTO 缺省为 null，不能再默认成 (0,0)，
        // 否则残缺条目会变成几内亚湾上的假城市并进入搜索结果。
        return response.results.orEmpty().mapNotNull { result ->
            val latitude = result.latitude ?: return@mapNotNull null
            val longitude = result.longitude ?: return@mapNotNull null
            if (result.name.isBlank()) return@mapNotNull null
            City(
                id = result.id,
                name = result.name,
                latitude = latitude,
                longitude = longitude,
                country = result.country,
                admin1 = result.admin1,
                admin2 = result.admin2,
                timezone = result.timezone,
            ).takeIf { it.isUsableForList() }
        }
    }

    private fun decodeGeocoding(body: String): GeocodingResponse =
        try {
            json.decodeFromString(GeocodingResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw AppError.DataFormat(e)
        }

    companion object {
        private val PLACE_SUFFIX =
            Regex("(特别行政区|自治州|自治区|自治县|地区|盟|市|县|区|旗|林区)$")

        fun normalizePlaceQuery(query: String): String {
            var current = query.trim()
            repeat(2) {
                val stripped = PLACE_SUFFIX.replace(current, "")
                if (stripped.length < 2) return current
                current = stripped
            }
            return current
        }

        fun containsHan(text: String): Boolean = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

        fun toPinyinQuery(text: String): String = Pinyin.toPinyin(text, "").lowercase().filter { it.isLetter() }

        fun mergeCityResults(
            preferred: List<City>,
            extras: List<City>,
        ): List<City> {
            val merged = preferred.toMutableList()
            extras.forEach { candidate ->
                if (merged.none { it.isNearSamePlace(candidate) }) {
                    merged += candidate
                }
            }
            return merged.take(12)
        }

        fun rankCityResults(
            query: String,
            cities: List<City>,
        ): List<City> =
            cities
                .map { ChinaCityCatalog.rewriteDisplayName(it, query) }
                .distinctBy {
                    "${"%.2f".format(java.util.Locale.US, it.latitude)}," +
                        "%.2f".format(java.util.Locale.US, it.longitude)
                }.sortedByDescending { ChinaCityCatalog.relevanceScore(it, query) }
                .take(12)
    }
}

/** 城市搜索短 TTL 缓存时长：输入抖动/重复搜索窗口内不重复打上游。 */
internal const val CITY_SEARCH_CACHE_TTL_MS = 90_000L

/**
 * 搜索结果 LRU：按 normalizedQuery 缓存，60–120s 内重复搜索直接命中。
 * 空结果也缓存，避免无结果查询在 TTL 内反复打上游。线程安全（Mutex）。
 */
internal class CitySearchCache(
    private val maxEntries: Int = 16,
    private val ttlMs: Long = CITY_SEARCH_CACHE_TTL_MS,
) {
    private data class Entry(
        val cities: List<City>,
        val storedAtEpochMs: Long,
    )

    private val mutex = Mutex()
    private val entries =
        object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > maxEntries
        }

    suspend fun get(
        query: String,
        nowEpochMs: Long,
    ): List<City>? =
        mutex.withLock {
            val entry = entries[query] ?: return@withLock null
            if (nowEpochMs - entry.storedAtEpochMs !in 0 until ttlMs) {
                entries.remove(query)
                return@withLock null
            }
            entry.cities
        }

    suspend fun put(
        query: String,
        cities: List<City>,
        nowEpochMs: Long,
    ) {
        mutex.withLock {
            entries[query] = Entry(cities, nowEpochMs)
        }
    }
}
