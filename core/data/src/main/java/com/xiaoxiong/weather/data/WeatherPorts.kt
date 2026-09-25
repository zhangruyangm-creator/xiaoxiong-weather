package com.xiaoxiong.weather.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 拉取天气快照（便于 ViewModel 单测替换）。 */
fun interface WeatherFetcher {
    suspend fun fetchWeather(city: City): WeatherSnapshot

    /**
     * 物候积温偏移天数（正 = 推迟，负 = 提前）；无数据/失败返回 null。
     * 默认不拉取：只由进入七日页时按需调用（低频率，跨年/换城/跨天才重拉）。
     */
    suspend fun fetchPhenologyShiftDays(
        city: City,
        today: LocalDate,
    ): Int? = null

    /**
     * 查询某天的历史天气（Archive / ERA5）；该日期确实无数据返回 null，
     * 网络/服务端错误抛 [AppError]（HTTP、连接失败、响应解析失败）。
     * 用户主动触发，默认不实现（UI 未接入口时为无操作）。
     */
    suspend fun fetchHistoryDay(
        city: City,
        date: LocalDate,
    ): HistoryWeather? = null

    /**
     * 查询一段日期范围的历史天气并聚合极值（最高温/最低温/最多降水）；
     * 区间内无任何可用数据返回 null，网络/服务端错误抛 [AppError]。默认不实现。
     */
    suspend fun fetchHistoryRange(
        city: City,
        start: LocalDate,
        end: LocalDate,
    ): HistoryRangeSummary? = null
}

interface WeatherCache {
    /**
     * 读缓存。[presentationStamp] 为当前语言+温度单位指纹（见 [presentationStampOf]）：
     * 与条目落盘时的指纹不一致时，数值照常返回，但会剥掉旧语言的预警/建议/生活指数。
     */
    suspend fun get(
        city: City,
        presentationStamp: String,
    ): WeatherCacheStore.CacheEntry?

    /**
     * 写缓存；[presentationStamp] 随条目落盘，供下次读取比对文案是否过期。
     *
     * [fetchedAtEpochMs] 必须是**发起本次取数的时刻**，不是写盘时刻：ViewModel、
     * Worker 与 Widget 三条路径会为同一城市并发取数，若只按写入顺序落盘，
     * 先发后至的旧数据会以"更新"的时间戳覆盖新数据，冷启动/离线就会读到旧天气
     * 却以为它是新鲜的。实现应拒绝比已存条目更旧的写入。
     */
    suspend fun save(
        snapshot: WeatherSnapshot,
        presentationStamp: String,
        fetchedAtEpochMs: Long = 0L,
    )
}

/** 物候偏移结果的按日缓存：同城同年当日有效，跨天/换城/跨年需重拉。 */
interface PhenologyCache {
    suspend fun get(
        city: City,
        year: Int,
    ): PhenologyCacheStore.PhenologyEntry?

    suspend fun save(
        city: City,
        year: Int,
        shiftDays: Int?,
    )
}

interface FavoriteCitySource {
    val favorites: Flow<List<City>>

    suspend fun toggle(city: City)

    fun isFavorite(
        favorites: List<City>,
        city: City?,
    ): Boolean

    suspend fun firstOrNull(): City?
}

interface RecentCitySource {
    val recent: Flow<List<City>>

    suspend fun add(city: City)

    suspend fun clear()
}

interface SelectedCitySource {
    suspend fun save(
        city: City,
        useLocation: Boolean,
    )

    suspend fun getOnce(): SelectedCityStore.PersistedSelection?
}

interface AppSettingsSource {
    val settings: Flow<AppSettings>

    suspend fun getOnce(): AppSettings
}

interface DebugWeatherSource {
    val state: Flow<DebugWeatherState>

    suspend fun getOnce(): DebugWeatherState

    suspend fun setEnabled(enabled: Boolean)

    suspend fun saveOverride(override: DebugWeatherOverride)

    suspend fun clearOverride()
}

interface LocationPermissionSource {
    fun hasLocationPermission(): Boolean
}

/** 定位会话：可取消过期请求，也可发起一次定位。 */
interface LocationSession {
    fun cancelPending()

    /**
     * @param onResolved 坐标就绪（可立刻拉天气；地名可能仍是占位/缓存）
     * @param onPlaceUpdated 逆地理完成后的地名刷新（同坐标，勿再整局重拉）
     */
    fun locate(
        onResolved: (City) -> Unit,
        onError: (Throwable) -> Unit,
        onPlaceUpdated: (City) -> Unit = {},
    )
}

fun interface LocationSessionFactory {
    fun create(scope: CoroutineScope): LocationSession
}
