package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.recentCityDataStore by preferencesDataStore(
    name = "weather_recent_cities",
    corruptionHandler = corruptionHandlerLogging("weather_recent_cities"),
)

class RecentCityStore(
    private val context: Context,
) : RecentCitySource {
    private val key = stringPreferencesKey("cities_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RecentCityDto.serializer())

    override val recent: Flow<List<City>> =
        context.recentCityDataStore.data.map { prefs ->
            val raw = prefs[key].orEmpty()
            if (raw.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString(serializer, raw).map { it.toCity() }
                }.getOrDefault(emptyList())
            }
        }

    override suspend fun add(city: City) {
        context.recentCityDataStore.edit { prefs ->
            val current =
                prefs[key]
                    ?.let {
                        runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
                    }.orEmpty()
                    .toMutableList()
            current.removeAll { it.toCity().isSamePlace(city) }
            current.add(0, RecentCityDto.from(city.copy(isCurrentLocation = false)))
            prefs[key] = json.encodeToString(serializer, current.take(MAX_RECENT))
        }
    }

    override suspend fun clear() {
        context.recentCityDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    @Serializable
    internal data class RecentCityDto(
        val id: Long,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
        val admin2: String? = null,
        val timezone: String? = null,
    ) {
        fun toCity() = City(id, name, latitude, longitude, country, admin1, admin2, false, timezone)

        companion object {
            fun from(city: City) =
                RecentCityDto(
                    id = city.id,
                    name = city.name,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    country = city.country,
                    admin1 = city.admin1,
                    admin2 = city.admin2,
                    timezone = city.timezone,
                )
        }
    }

    companion object {
        private const val MAX_RECENT = 8
    }
}
