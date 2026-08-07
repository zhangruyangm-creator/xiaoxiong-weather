package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.favoriteDataStore by preferencesDataStore("weather_favorites")

class FavoriteCityStore(private val context: Context) : FavoriteCitySource {
    private val key = stringPreferencesKey("cities_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FavoriteCityDto.serializer())

    override val favorites: Flow<List<City>> = context.favoriteDataStore.data.map { prefs ->
        val raw = prefs[key].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(serializer, raw).map { it.toCity() }
        }.getOrDefault(emptyList())
    }

    override suspend fun toggle(city: City) {
        context.favoriteDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            }.orEmpty().toMutableList()

            val exists = current.any { sameCity(it.toCity(), city) }
            if (exists) {
                current.removeAll { sameCity(it.toCity(), city) }
            } else {
                current.add(0, FavoriteCityDto.from(city))
            }
            prefs[key] = json.encodeToString(serializer, current.take(12))
        }
    }

    override fun isFavorite(favorites: List<City>, city: City?): Boolean {
        if (city == null) return false
        return favorites.any { sameCity(it, city) }
    }

    override suspend fun firstOrNull(): City? = favorites.first().firstOrNull()

    private fun sameCity(a: City, b: City): Boolean =
        kotlin.math.abs(a.latitude - b.latitude) < 0.01 &&
            kotlin.math.abs(a.longitude - b.longitude) < 0.01

    @kotlinx.serialization.Serializable
    private data class FavoriteCityDto(
        val id: Long,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
        val admin2: String? = null
    ) {
        fun toCity() = City(id, name, latitude, longitude, country, admin1, admin2, false)

        companion object {
            fun from(city: City) = FavoriteCityDto(
                id = city.id,
                name = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                country = city.country,
                admin1 = city.admin1,
                admin2 = city.admin2
            )
        }
    }
}
