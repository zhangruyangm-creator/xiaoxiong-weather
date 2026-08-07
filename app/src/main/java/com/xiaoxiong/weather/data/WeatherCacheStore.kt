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

private val Context.weatherCacheDataStore by preferencesDataStore("weather_cache_v1")

class WeatherCacheStore(private val context: Context) : WeatherCache {
    private val key = stringPreferencesKey("entries_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun save(snapshot: WeatherSnapshot) {
        val entry = CacheEntry(
            snapshot = snapshot,
            savedAtEpochMs = System.currentTimeMillis()
        )
        context.weatherCacheDataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            current.removeAll { sameCity(it.snapshot.city, snapshot.city) }
            current.add(0, entry)
            prefs[key] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(CacheEntry.serializer()),
                current.take(MAX_ENTRIES)
            )
        }
    }

    override suspend fun get(city: City): CacheEntry? {
        val raw = context.weatherCacheDataStore.data.map { it[key] }.first()
        return decode(raw).firstOrNull { sameCity(it.snapshot.city, city) }
    }

    private fun decode(raw: String?): List<CacheEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CacheEntry.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

    private fun sameCity(a: City, b: City): Boolean =
        abs(a.latitude - b.latitude) < 0.01 && abs(a.longitude - b.longitude) < 0.01

    @Serializable
    data class CacheEntry(
        val snapshot: WeatherSnapshot,
        val savedAtEpochMs: Long
    )

    companion object {
        private const val MAX_ENTRIES = 12
    }
}
