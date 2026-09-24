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

private val Context.favoriteDataStore by preferencesDataStore(
    name = "weather_favorites",
    corruptionHandler = corruptionHandlerLogging("weather_favorites"),
)

class FavoriteCityStore(
    private val context: Context,
) : FavoriteCitySource {
    private val key = stringPreferencesKey("cities_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FavoriteCityDto.serializer())
    private val itemSerializer = FavoriteCityDto.serializer()

    override val favorites: Flow<List<City>> =
        context.favoriteDataStore.data.map { prefs ->
            decodeCityListTolerant(json, prefs[key], itemSerializer)
                .map { it.toCity() }
                .normalizeCityList(MAX_FAVORITES)
        }

    override suspend fun toggle(city: City) {
        context.favoriteDataStore.edit { prefs ->
            // 容错解码：坏条目只丢自己。旧实现整批失败成空列表时，本次写回会把
            // 用户其余收藏全部覆盖掉。
            val current =
                decodeCityListTolerant(json, prefs[key], itemSerializer)
                    .map { it.toCity() }
                    .normalizeCityList(MAX_FAVORITES)
                    .toMutableList()

            val exists = current.any { it.isSamePlace(city) }
            when {
                exists -> current.removeAll { it.isSamePlace(city) }
                canAddFavorite(current.size, isAlreadyFavorite = false) ->
                    current.add(0, city)
                // 达到上限：拒绝新增，而不是静默丢弃最旧收藏
                else -> return@edit
            }
            prefs[key] =
                json.encodeToString(
                    serializer,
                    current.map { FavoriteCityDto.from(it) },
                )
        }
    }

    override fun isFavorite(
        favorites: List<City>,
        city: City?,
    ): Boolean {
        if (city == null) return false
        return favorites.any { it.isSamePlace(city) }
    }

    override suspend fun firstOrNull(): City? = favorites.first().firstOrNull()

    companion object {
        /** 收藏城市上限：达到后拒绝新增，避免静默丢弃旧收藏。 */
        const val MAX_FAVORITES = 12

        /** 是否允许新增收藏；取消已有收藏（[isAlreadyFavorite]）始终允许。 */
        fun canAddFavorite(
            currentCount: Int,
            isAlreadyFavorite: Boolean,
        ): Boolean = isAlreadyFavorite || currentCount < MAX_FAVORITES
    }

    @kotlinx.serialization.Serializable
    internal data class FavoriteCityDto(
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
                FavoriteCityDto(
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
}
