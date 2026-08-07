package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.selectedCityDataStore by preferencesDataStore("weather_selected_city")

class SelectedCityStore(
    private val context: Context,
) : SelectedCitySource {
    private val cityKey = stringPreferencesKey("city_json")
    private val useLocationKey = booleanPreferencesKey("use_location")
    private val json = Json { ignoreUnknownKeys = true }

    val selectedCity: Flow<PersistedSelection?> =
        context.selectedCityDataStore.data.map { prefs ->
            val raw = prefs[cityKey] ?: return@map null
            val city =
                runCatching { json.decodeFromString(CityDto.serializer(), raw) }.getOrNull()
                    ?: return@map null
            PersistedSelection(
                city = city.toCity(),
                useLocation = prefs[useLocationKey] == true,
            )
        }

    override suspend fun save(
        city: City,
        useLocation: Boolean,
    ) {
        context.selectedCityDataStore.edit { prefs ->
            prefs[cityKey] =
                json.encodeToString(
                    CityDto.serializer(),
                    CityDto.from(city.copy(isCurrentLocation = false)),
                )
            prefs[useLocationKey] = useLocation
        }
    }

    override suspend fun getOnce(): PersistedSelection? = selectedCity.first()

    data class PersistedSelection(
        val city: City,
        val useLocation: Boolean,
    )

    @Serializable
    private data class CityDto(
        val id: Long,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
        val admin2: String? = null,
    ) {
        fun toCity() = City(id, name, latitude, longitude, country, admin1, admin2, false)

        companion object {
            fun from(city: City) =
                CityDto(
                    id = city.id,
                    name = city.name,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    country = city.country,
                    admin1 = city.admin1,
                    admin2 = city.admin2,
                )
        }
    }
}
