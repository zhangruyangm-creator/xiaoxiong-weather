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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val Context.selectedCityDataStore by preferencesDataStore(
    name = "weather_selected_city",
    corruptionHandler = corruptionHandlerLogging("weather_selected_city"),
)

class SelectedCityStore(
    private val context: Context,
) : SelectedCitySource {
    private val cityKey = stringPreferencesKey("city_json")
    private val useLocationKey = booleanPreferencesKey("use_location")
    private val json = Json { ignoreUnknownKeys = true }

    /** 进程内串行化「读当前选择 → 比较 → 写入」，避免 check-then-act 竞态。 */
    private val selectionGate = SelectionWriteGate()

    val selectedCity: Flow<PersistedSelection?> =
        context.selectedCityDataStore.data.map { prefs -> decodeSelection(prefs) }

    override suspend fun save(
        city: City,
        useLocation: Boolean,
    ) {
        selectionGate.run {
            context.selectedCityDataStore.edit { prefs ->
                writeSelection(prefs, city, useLocation)
            }
        }
    }

    /**
     * 仅当 DataStore 当前选择仍与 [expected] 一致时才写入：
     * 后台抓取期间用户刚切的城市不能被迟到的定位结果覆盖。
     *
     * 注意不要用 `edit {}` transform 内的局部变量记录是否写入：DataStore 的 transform
     * 在 I/O 失败重试时可能被多次调用，写入最终失败也会误报 true。
     * 这里改为锁内先读快照、比较一致后执行一次写入，只有写入完成才返回 true。
     *
     * @return 是否实际写入
     */
    suspend fun saveIfUnchanged(
        expected: PersistedSelection?,
        city: City,
        useLocation: Boolean,
    ): Boolean =
        selectionGate.saveIfUnchanged(
            expected = expected,
            read = { selectedCity.first() },
            write = {
                context.selectedCityDataStore.edit { prefs ->
                    writeSelection(prefs, city, useLocation)
                }
            },
        )

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
        val timezone: String? = null,
    ) {
        fun toCity() = City(id, name, latitude, longitude, country, admin1, admin2, false, timezone)

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
                    timezone = city.timezone,
                )
        }
    }
}

/**
 * 「比较后再写入」的串行化门闩。
 *
 * DataStore 的 `edit` transform 在磁盘 I/O 失败后可能重试并多次执行，
 * 在 transform 内部记录成功会掩盖最终写入失败；因此这里在进程内 [Mutex] 内
 * 先读取当前快照，确认与 [SelectedCityStore.PersistedSelection] 期望一致后
 * 才执行一次 [write]，写入真正完成才返回 true（写失败异常向上抛，不吞）。
 */
internal class SelectionWriteGate {
    private val mutex = Mutex()

    suspend fun saveIfUnchanged(
        expected: SelectedCityStore.PersistedSelection?,
        read: suspend () -> SelectedCityStore.PersistedSelection?,
        write: suspend () -> Unit,
    ): Boolean =
        mutex.withLock {
            if (!isSameSelection(expected, read())) return@withLock false
            write()
            true
        }

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

/** [expected] 与 DataStore 当前选择是否一致：城市坐标 0.01 阈值内视为同城，flag 必须相等。 */
fun isSameSelection(
    expected: SelectedCityStore.PersistedSelection?,
    current: SelectedCityStore.PersistedSelection?,
): Boolean {
    if (expected == null || current == null) return expected == null && current == null
    val sameCoords =
        abs(expected.city.latitude - current.city.latitude) < 0.01 &&
            abs(expected.city.longitude - current.city.longitude) < 0.01
    return expected.useLocation == current.useLocation && sameCoords
}
