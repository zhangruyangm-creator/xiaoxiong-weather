package com.xiaoxiong.weather.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val Context.selectedCityDataStore by preferencesDataStore("weather_selected_city")

class SelectedCityStore(
    private val context: Context,
) : SelectedCitySource {
    private val cityKey = stringPreferencesKey("city_json")
    private val useLocationKey = booleanPreferencesKey("use_location")
    private val json = Json { ignoreUnknownKeys = true }

    val selectedCity: Flow<PersistedSelection?> =
        context.selectedCityDataStore.data.map { prefs -> decodeSelection(prefs) }

    override suspend fun save(
        city: City,
        useLocation: Boolean,
    ) {
        context.selectedCityDataStore.edit { prefs ->
            writeSelection(prefs, city, useLocation)
        }
    }

    /**
     * 仅当 DataStore 当前选择仍与 [expected] 一致时才写入：
     * 后台抓取期间用户刚切的城市不能被迟到的定位结果覆盖。
     *
     * @return 是否实际写入
     */
    suspend fun saveIfUnchanged(
        expected: PersistedSelection?,
        city: City,
        useLocation: Boolean,
    ): Boolean {
        var saved = false
        context.selectedCityDataStore.edit { prefs ->
            if (!isSameSelection(expected, decodeSelection(prefs))) {
                return@edit
            }
            writeSelection(prefs, city, useLocation)
            saved = true
        }
        return saved
    }

    override suspend fun getOnce(): PersistedSelection? = selectedCity.first()

    private fun decodeSelection(prefs: Preferences): PersistedSelection? {
        val raw = prefs[cityKey] ?: return null
        val city =
            runCatching { json.decodeFromString(CityDto.serializer(), raw) }.getOrNull()
                ?: return null
        return PersistedSelection(
            city = city.toCity(),
            useLocation = prefs[useLocationKey] == true,
        )
    }

    private fun writeSelection(
        prefs: MutablePreferences,
        city: City,
        useLocation: Boolean,
    ) {
        prefs[cityKey] =
            json.encodeToString(
                CityDto.serializer(),
                CityDto.from(city.copy(isCurrentLocation = false)),
            )
        prefs[useLocationKey] = useLocation
    }

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

/** [expected] 与 DataStore 当前选择是否一致：城市坐标 0.01 阈值内视为同城，flag 必须相等。 */
internal fun isSameSelection(
    expected: SelectedCityStore.PersistedSelection?,
    current: SelectedCityStore.PersistedSelection?,
): Boolean {
    if (expected == null || current == null) return expected == null && current == null
    val sameCoords =
        abs(expected.city.latitude - current.city.latitude) < 0.01 &&
            abs(expected.city.longitude - current.city.longitude) < 0.01
    return expected.useLocation == current.useLocation && sameCoords
}
