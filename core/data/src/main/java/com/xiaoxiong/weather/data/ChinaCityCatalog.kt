package com.xiaoxiong.weather.data

import android.content.Context
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * 本地城市索引（assets/china_cities.json：国内城市 + 常见世界城市）。
 * 启动时预计算拼音与坐标网格，搜索时避免重复算拼音。
 */
object ChinaCityCatalog {
    data class Entry(
        val name: String,
        val admin1: String,
        val admin2: String? = null,
        val country: String = "中国",
        val latitude: Double,
        val longitude: Double,
        val aliases: List<String> = emptyList(),
    )

    private data class IndexedEntry(
        val entry: Entry,
        val city: City,
        val nameLower: String,
        val aliasLowers: List<String>,
        val pinyinKeys: List<String>,
        val pinyinInitials: List<String>,
    )

    @Serializable
    private data class EntryDto(
        val name: String,
        val admin1: String,
        val admin2: String? = null,
        val country: String? = null,
        val lat: Double,
        val lon: Double,
        val aliases: List<String> = emptyList(),
    )

    private val indexedRef = AtomicReference<List<IndexedEntry>?>(null)
    private val gridRef = AtomicReference<Map<Long, List<IndexedEntry>>?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        if (indexedRef.get() != null) return
        synchronized(this) {
            if (indexedRef.get() != null) return
            val raw =
                context.applicationContext.assets
                    .open("china_cities.json")
                    .bufferedReader()
                    .use { it.readText() }
            val indexed =
                json
                    .decodeFromString(
                        ListSerializer(EntryDto.serializer()),
                        raw,
                    ).map { dto ->
                        val entry =
                            Entry(
                                name = dto.name,
                                admin1 = dto.admin1,
                                admin2 = dto.admin2,
                                country = dto.country ?: "中国",
                                latitude = dto.lat,
                                longitude = dto.lon,
                                aliases = dto.aliases,
                            )
                        val nameNorm = WeatherRepository.normalizePlaceQuery(entry.name)
                        // 英文别名不做「去市/县」处理，保留 New York 这类空格名
                        val aliasNorms =
                            entry.aliases.map { alias ->
                                if (alias.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }) {
                                    alias.trim()
                                } else {
                                    WeatherRepository.normalizePlaceQuery(alias)
                                }
                            }
                        val pinyinKeys =
                            (listOf(nameNorm) + aliasNorms.filter { WeatherRepository.containsHan(it) })
                                .map { toPinyin(it) }
                                .filter { it.length >= 2 }
                                .distinct()
                        IndexedEntry(
                            entry = entry,
                            city = entry.toCity(),
                            nameLower = nameNorm.lowercase(),
                            aliasLowers = aliasNorms.map { it.lowercase() },
                            pinyinKeys = pinyinKeys,
                            pinyinInitials =
                                (listOf(nameNorm) + aliasNorms.filter { WeatherRepository.containsHan(it) })
                                    .map { toPinyinInitials(it) }
                                    .filter { it.length >= 2 }
                                    .distinct(),
                        )
                    }
            indexedRef.set(indexed)
            gridRef.set(
                indexed.groupBy { cellKey(it.entry.latitude, it.entry.longitude) },
            )
        }
    }

    private fun indexed(context: Context? = null): List<IndexedEntry> {
        indexedRef.get()?.let { return it }
        if (context != null) {
            init(context)
            return indexedRef.get().orEmpty()
        }
        return emptyList()
    }

    fun search(
        query: String,
        limit: Int = 10,
        context: Context? = null,
    ): List<City> {
        val normalized = WeatherRepository.normalizePlaceQuery(query)
        if (normalized.length < 2) return emptyList()
        val qLower = normalized.lowercase().trim()
        val qPinyin = toPinyin(normalized)
        val qInitials = toPinyinInitials(normalized)
        val hits = ArrayList<Pair<Int, City>>(12)

        for (item in indexed(context)) {
            val score = score(item, qLower, qPinyin, qInitials) ?: continue
            hits += score to item.city
        }
        if (hits.isEmpty()) return emptyList()
        hits.sortByDescending { it.first }
        return hits.take(limit).map { it.second }
    }

    /** 空搜索时展示的热门城市（本地目录命中）。 */
    fun popular(
        context: Context? = null,
        limit: Int = 12,
    ): List<City> {
        val indexed = indexed(context)
        if (indexed.isEmpty()) return emptyList()
        val byName = indexed.associateBy { it.nameLower }
        val result = ArrayList<City>(limit)
        for (name in POPULAR_NAMES) {
            val hit = byName[name] ?: continue
            if (result.none { WeatherRepository.nearSamePlace(it, hit.city) }) {
                result += hit.city
            }
            if (result.size >= limit) break
        }
        return result
    }

    /** 本地是否已有高置信命中（可跳过慢速 Geocoder / 减少等待）。 */
    fun hasStrongMatch(
        query: String,
        cities: List<City>,
    ): Boolean {
        if (cities.any { relevanceScore(it, query) >= 90 }) return true
        val normalized = WeatherRepository.normalizePlaceQuery(query)
        if (normalized.length < 2) return false
        val qLower = normalized.lowercase()
        // 别名精确命中也算强匹配（如「阿蒙森」→ 阿蒙森-斯科特站）
        return cities.any { city ->
            indexedRef.get().orEmpty().any { item ->
                WeatherRepository.nearSamePlace(item.city, city) &&
                    item.aliasLowers.any { it == qLower || it.startsWith(qLower) }
            }
        }
    }

    fun rewriteDisplayName(
        city: City,
        query: String,
        context: Context? = null,
    ): City {
        val normalized = WeatherRepository.normalizePlaceQuery(query)
        if (normalized.length < 2) return city
        val qLower = normalized.lowercase()
        val qPinyin = toPinyin(normalized)
        val grid = gridRef.get()
        val candidates =
            if (grid != null) {
                nearbyCells(city.latitude, city.longitude).flatMap { grid[it].orEmpty() }
            } else {
                indexed(context)
            }
        val qInitials = toPinyinInitials(normalized)
        val hit =
            candidates.firstOrNull { item ->
                WeatherRepository.nearSamePlace(item.city, city) &&
                    score(item, qLower, qPinyin, qInitials) != null
            } ?: return city
        return city.copy(
            name = hit.entry.name,
            admin1 = hit.entry.admin1,
            admin2 = hit.entry.admin2,
            country = hit.entry.country,
        )
    }

    fun relevanceScore(
        city: City,
        query: String,
    ): Int {
        val normalized = WeatherRepository.normalizePlaceQuery(query)
        if (normalized.isEmpty()) return 0
        val qLower = normalized.lowercase()
        val name = WeatherRepository.normalizePlaceQuery(city.name).lowercase()
        val admin2 = city.admin2.orEmpty().lowercase()
        val admin1 = city.admin1.orEmpty().lowercase()
        return when {
            name == qLower -> 100
            name.startsWith(qLower) -> 90
            name.contains(qLower) -> 80
            toPinyin(city.name) == toPinyin(normalized) -> 85
            admin2.contains(qLower) -> 40
            admin1.contains(qLower) -> 20
            else -> 0
        }
    }

    private fun score(
        item: IndexedEntry,
        qLower: String,
        qPinyin: String,
        qInitials: String,
    ): Int? {
        val name = item.nameLower
        val aliases = item.aliasLowers
        val pinyinHit =
            qPinyin.length >= 2 &&
                item.pinyinKeys.any {
                    it == qPinyin || it.startsWith(qPinyin) || (qPinyin.length >= 4 && it.contains(qPinyin))
                }
        val initialsHit =
            qInitials.length >= 2 &&
                item.pinyinInitials.any {
                    it == qInitials || it.startsWith(qInitials)
                }
        val matched =
            name == qLower ||
                name.startsWith(qLower) ||
                qLower.startsWith(name) ||
                name.contains(qLower) ||
                aliases.any {
                    it == qLower || it.startsWith(qLower) || qLower.startsWith(it) || it.contains(qLower)
                } ||
                pinyinHit ||
                initialsHit
        if (!matched) return null
        return when {
            name == qLower || aliases.any { it == qLower } -> 100
            name.startsWith(qLower) || aliases.any { it.startsWith(qLower) } -> 90
            pinyinHit && item.pinyinKeys.any { it == qPinyin } -> 88
            initialsHit && item.pinyinInitials.any { it == qInitials } -> 86
            name.contains(qLower) || aliases.any { it.contains(qLower) } -> 80
            pinyinHit -> 78
            initialsHit -> 74
            else -> 70
        }
    }

    private fun Entry.toCity(): City =
        City(
            id = ("city-$name-$latitude-$longitude").hashCode().toLong(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            country = country,
            admin1 = admin1,
            admin2 = admin2,
        )

    private fun cellKey(
        lat: Double,
        lon: Double,
    ): Long {
        val latCell = (lat * 20).roundToInt()
        val lonCell = (lon * 20).roundToInt()
        return (latCell.toLong() shl 32) xor (lonCell.toLong() and 0xffffffffL)
    }

    private fun nearbyCells(
        lat: Double,
        lon: Double,
    ): List<Long> {
        val latCell = (lat * 20).roundToInt()
        val lonCell = (lon * 20).roundToInt()
        val keys = ArrayList<Long>(9)
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                keys += ((latCell + dLat).toLong() shl 32) xor
                    ((lonCell + dLon).toLong() and 0xffffffffL)
            }
        }
        return keys
    }

    private fun toPinyin(text: String): String = Pinyin.toPinyin(text, "").lowercase().filter { it.isLetter() }

    /** 拼音首字母，如「北京」→ bj、「上海」→ sh */
    private fun toPinyinInitials(text: String): String =
        buildString {
            for (ch in text) {
                when {
                    ch.isWhitespace() || ch == '-' || ch == '\'' -> Unit
                    ch.code < 128 -> if (ch.isLetter()) append(ch.lowercaseChar())
                    else -> {
                        val py = Pinyin.toPinyin(ch)
                        if (py.isNotEmpty() && py[0].isLetter()) append(py[0].lowercaseChar())
                    }
                }
            }
        }

    private val POPULAR_NAMES =
        listOf(
            "北京",
            "上海",
            "广州",
            "深圳",
            "杭州",
            "成都",
            "重庆",
            "西安",
            "武汉",
            "南京",
            "苏州",
            "天津",
            "长沙",
            "郑州",
            "青岛",
            "厦门",
            "台北",
            "香港",
            "澳门",
            "东京",
            "新加坡",
            "首尔",
            "曼谷",
            "纽约",
            "伦敦",
            "巴黎",
            "悉尼",
            "洛杉矶",
            "雷克雅未克",
        )
}
