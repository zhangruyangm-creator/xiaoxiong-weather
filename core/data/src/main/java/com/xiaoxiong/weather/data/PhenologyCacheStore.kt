package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val Context.phenologyDataStore by preferencesDataStore("phenology_cache_v1")

/**
 * 物候偏移结果的按日缓存。
 *
 * 以「城市 + 年份」为键：跨年、换城自然失效；当日有效期内再次进入七日页
 * 直接命中缓存，保证同一城市一天最多联网拉一次。
 */
class PhenologyCacheStore(
    private val context: Context,
) : PhenologyCache {
    private val key = stringPreferencesKey("entries_json")
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun get(
        city: City,
        year: Int,
    ): PhenologyEntry? {
        val raw =
            context.phenologyDataStore.data
                .map { it[key] }
                .first()
        return decode(raw).firstOrNull { entry ->
            entry.year == year && entry.sameCity(city) && entry.isFreshToday()
        }
    }

    override suspend fun save(
        city: City,
        year: Int,
        shiftDays: Int?,
    ) {
        val entry =
            PhenologyEntry(
                cityLat = city.latitude,
                cityLon = city.longitude,
                year = year,
                shiftDays = shiftDays,
                fetchedAtEpochMs = System.currentTimeMillis(),
            )
        context.phenologyDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            current.removeAll { it.year == year && it.sameCity(city) }
            current.add(0, entry)
            prefs[key] =
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(PhenologyEntry.serializer()),
                    current.take(MAX_ENTRIES),
                )
        }
    }

    private fun decode(raw: String?): List<PhenologyEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(PhenologyEntry.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }

    @Serializable
    data class PhenologyEntry(
        val cityLat: Double,
        val cityLon: Double,
        val year: Int,
        /** null = 已尝试但无数据（今年天数不足 / 积温增速过慢），当日不再重试。 */
        val shiftDays: Int?,
        val fetchedAtEpochMs: Long,
    ) {
        fun sameCity(city: City): Boolean = abs(cityLat - city.latitude) < 0.01 && abs(cityLon - city.longitude) < 0.01

        /**
         * 当日有效：年龄必须落在 `[0, 24h]`（与 [isWithinFreshnessWindow] 同口径）。
         * 未来时间戳（时钟回拨 / 异常写入）视为过期——否则回拨后缓存会长期不过期；
         * 跨天但未满 24h 仍可用（与 WeatherCacheStore 的 24h 时效一致）。
         */
        fun isFreshToday(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
            isWithinFreshnessWindow(fetchedAtEpochMs, nowEpochMs, MAX_AGE_MS)
    }

    companion object {
        private const val MAX_ENTRIES = 24

        /** 缓存最大存活时长：与 WeatherCacheStore.MAX_AGE_MS 保持一致。 */
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
