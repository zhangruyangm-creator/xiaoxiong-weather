package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

        /** 当日有效：跨天视为过期，下次进入七日页会重拉。 */
        fun isFreshToday(): Boolean {
            val fetchedDate =
                Instant
                    .ofEpochMilli(fetchedAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            return ChronoUnit.DAYS.between(fetchedDate, LocalDate.now()) < 1
        }
    }

    companion object {
        private const val MAX_ENTRIES = 24
    }
}
