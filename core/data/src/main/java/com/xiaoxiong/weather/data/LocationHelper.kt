package com.xiaoxiong.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.xiaoxiong.weather.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(
    private val context: Context,
) : LocationPermissionSource {
    private val placeCachePrefs by lazy {
        context.applicationContext.getSharedPreferences(PLACE_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * API 33 以下的阻塞式 Geocoder 专用线程池：系统调用不响应协程取消，
     * 放到守护线程执行并与协程超时/取消解耦，避免卡死 IO 线程与调用方。
     */
    private val geocoderExecutor by lazy {
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "geocoder-blocking").apply { isDaemon = true }
        }
    }

    override fun hasLocationPermission(): Boolean {
        val fine =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 后台路径（小组件 / Worker）的定位入口：短期内复用最近一次定位结果，
     * 避免每次刷新都重新走完整定位 + 逆地理（GPS 冷启动 + Geocoder 可能数秒）。
     * UI 主路径请用 [resolveCurrentCoordinates] + [provisionalCity] / [refineCityPlaceName]。
     *
     * 规则：
     * - 最近 [REUSE_FRESH_MS] 内定位过则直接复用，零定位开销；
     * - 否则重新完整定位（坐标 + 逆地理）并刷新缓存；
     * - 定位失败时退回最近一次结果（即使超时），保证后台路径不空转。
     */
    @SuppressLint("MissingPermission")
    suspend fun resolveCurrentCityReuseLast(): City =
        withContext(Dispatchers.IO) {
            reuseLastLocation()?.let { return@withContext it }
            try {
                val location = resolveCurrentCoordinates()
                val city = refineCityPlaceName(provisionalCity(location))
                rememberLastLocation(city)
                city
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readLastLocation() ?: error(context.getString(R.string.error_locate_failed))
            }
        }

    /** 只解析坐标，不逆地理——拿到即可拉天气。 */
    @SuppressLint("MissingPermission")
    suspend fun resolveCurrentCoordinates(): Location =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                error(context.getString(R.string.error_need_location_permission))
            }
            withTimeoutOrNull(LOCATION_OUTER_MS) { awaitLocation() }
                ?: error(context.getString(R.string.error_locate_timeout))
        }

    /** 先用缓存地名或「当前位置」，立刻可开天气。 */
    fun provisionalCity(location: Location): City {
        val cached = readCachedPlace(location.latitude, location.longitude)
        return cityFrom(location, cached ?: provisionalPlaceName(location))
    }

    /** 补全/刷新地名；失败则原样返回。 */
    suspend fun refineCityPlaceName(city: City): City =
        withContext(Dispatchers.IO) {
            val place =
                withTimeoutOrNull(GEOCODER_BUDGET_MS) {
                    reverseGeocode(city.latitude, city.longitude)
                } ?: return@withContext city
            city.copy(
                name = place.name,
                admin1 = place.admin1,
                admin2 = place.admin2,
                country = place.country,
                isCurrentLocation = true,
            )
        }

    private fun provisionalPlaceName(location: Location): PlaceName =
        // 逆地理解析成功前先展示「当前位置 + 经纬度」占位（天气可立即上屏）；
        // 解析成功后由 refineCityPlaceName 补全为真实地名。
        // 注意：此占位不会被写入缓存（reverseGeocode 失败不缓存），
        // 因此不会阻塞后续补全，经纬度只是临时的。
        PlaceName(
            name = context.getString(R.string.location_current),
            admin1 = String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude),
            country = null,
        )

    private fun cityFrom(
        location: Location,
        placeName: PlaceName,
    ): City =
        City(
            id = (("loc-${location.latitude}-${location.longitude}").hashCode()).toLong(),
            name = placeName.name,
            latitude = location.latitude,
            longitude = location.longitude,
            country = placeName.country,
            admin1 = placeName.admin1,
            admin2 = placeName.admin2,
            isCurrentLocation = true,
        )

    /**
     * 国内优先系统 LocationManager（NETWORK/GPS）。
     * Fused/GMS 在不少国产机上「显示可用却卡住」，只作瞬时 last-known 加速，
     * 以及系统完全失败时的短超时兜底，不再作为主路径。
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(): Location? {
        val now = System.currentTimeMillis()
        peekSystemLastKnown()?.takeIf { isAcceptableFix(it, now, FRESH_LAST_KNOWN_MS) }?.let {
            return it
        }
        if (isPlayServicesAvailable()) {
            val fused =
                try {
                    peekFusedLastKnown()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            fused
                ?.takeIf { isAcceptableFix(it, now, FRESH_LAST_KNOWN_MS) }
                ?.let { return it }
        }
        val system =
            try {
                awaitLocationSystem()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        system?.let { return it }
        if (isPlayServicesAvailable()) {
            return try {
                awaitLocationFusedFresh(FUSED_FALLBACK_WAIT_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun isPlayServicesAvailable(): Boolean =
        GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission")
    private fun peekSystemLastKnown(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                try {
                    manager.isProviderEnabled(provider)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }.mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }.maxWithOrNull(compareBy { locationScore(it) })
    }

    @SuppressLint("MissingPermission")
    private suspend fun peekFusedLastKnown(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return awaitTaskOrNull(client.lastLocation)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocationFusedFresh(waitMs: Long): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val last = awaitTaskOrNull(client.lastLocation)
        val now = System.currentTimeMillis()
        val request =
            CurrentLocationRequest
                .Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(FUSED_MAX_UPDATE_AGE_MS)
                .build()
        val fresh =
            withTimeoutOrNull(waitMs) {
                awaitTaskOrNull(client.getCurrentLocation(request, null))
            }
        if (fresh != null) return fresh
        if (last != null && now - last.time < MAX_LAST_KNOWN_AGE_MS) return last
        return null
    }

    private fun isAcceptableFix(
        location: Location,
        now: Long,
        maxAgeMs: Long,
    ): Boolean {
        if (now - location.time >= maxAgeMs) return false
        if (!location.hasAccuracy()) return true // 无精度信息时仍可用作城市级
        return location.accuracy <= ACCEPTABLE_ACCURACY_M
    }

    private suspend fun <T> awaitTaskOrNull(task: Task<T>): T? =
        suspendCancellableCoroutine { cont ->
            task
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }.addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocationSystem(): Location? =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers =
                listOf(
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER,
                ).filter { manager.isProviderEnabled(it) }

            if (providers.isEmpty()) {
                cont.resumeWithException(
                    IllegalStateException(context.getString(R.string.error_locate_disabled)),
                )
                return@suspendCancellableCoroutine
            }

            val last =
                providers
                    .mapNotNull { manager.getLastKnownLocation(it) }
                    .maxWithOrNull(compareBy<Location> { locationScore(it) })

            val now = System.currentTimeMillis()
            // 足够新且城市级精度即可直接用
            if (last != null && isAcceptableFix(last, now, FRESH_LAST_KNOWN_MS)) {
                cont.resume(last)
                return@suspendCancellableCoroutine
            }

            val completed = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val bestRef = AtomicReference<Location?>(last?.takeIf { now - it.time < MAX_LAST_KNOWN_AGE_MS })

            fun finish(location: Location?) {
                if (!completed.compareAndSet(false, true)) return
                if (cont.isActive) {
                    cont.resume(location)
                }
            }

            val listener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val prev = bestRef.get()
                        if (prev == null || locationScore(location) > locationScore(prev)) {
                            bestRef.set(location)
                        }
                        // 公里级精度即可结束（天气城市粒度）；不必干等 GPS 精修到 150m
                        val current = bestRef.get()
                        if (current != null && isAcceptableFix(current, System.currentTimeMillis(), MAX_LAST_KNOWN_AGE_MS)) {
                            providers.forEach { manager.removeUpdates(this) }
                            handler.removeCallbacksAndMessages(null)
                            finish(current)
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?,
                    ) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }

            val timeoutRunnable =
                Runnable {
                    providers.forEach { manager.removeUpdates(listener) }
                    finish(bestRef.get())
                }

            // 先挂取消清理，再注册监听：注册中途抛异常时不会漏掉已注册的 listener
            cont.invokeOnCancellation {
                providers.forEach { manager.removeUpdates(listener) }
                handler.removeCallbacks(timeoutRunnable)
                completed.set(true)
            }
            try {
                providers.forEach { provider ->
                    manager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            } catch (e: Exception) {
                providers.forEach { runCatching { manager.removeUpdates(listener) } }
                handler.removeCallbacks(timeoutRunnable)
                if (cont.isActive) cont.resumeWithException(e)
                return@suspendCancellableCoroutine
            }
            handler.postDelayed(timeoutRunnable, LOCATION_WAIT_MS)
        }

    private fun locationScore(location: Location): Double {
        val ageMin = ((System.currentTimeMillis() - location.time).coerceAtLeast(0L)) / 60_000.0
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 5_000.0
        // 精度越好、越新分越高
        return -(accuracy + ageMin * 80.0)
    }

    private data class PlaceName(
        val name: String,
        val admin1: String?,
        val admin2: String? = null,
        val country: String?,
    )

    /**
     * 正向地理编码：补足 Open-Meteo 搜不到的中国县级市/县城。
     * 天气接口只需经纬度，名称优先用系统返回的中文地名。
     */
    suspend fun searchPlaces(
        query: String,
        maxResults: Int = 4,
    ): List<City> =
        withContext(Dispatchers.IO) {
            // 部分机型 Geocoder 回调永不返回，必须整体限时
            withTimeoutOrNull(GEOCODER_SEARCH_TIMEOUT_MS) {
                val trimmed = query.trim()
                if (trimmed.length < 2 || !Geocoder.isPresent()) return@withTimeoutOrNull emptyList()

                val geocoder = Geocoder(context, Locale.CHINA)
                // 最多两次查询：先原词，无结果再试「市」后缀
                val queries = listOf(trimmed, "${trimmed}市")
                val addresses = ArrayList<Address>(maxResults)
                for (q in queries) {
                    val batch =
                        try {
                            forwardGeocode(geocoder, q, maxResults)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    addresses += batch
                    if (addresses.isNotEmpty()) break
                }
                addresses
                    .mapNotNull { address ->
                        val lat = address.latitude
                        val lon = address.longitude
                        if (lat == 0.0 && lon == 0.0) return@mapNotNull null
                        val name = bestMatchingPlaceName(address, trimmed) ?: return@mapNotNull null
                        City(
                            id = ("geo-$lat-$lon-$name").hashCode().toLong(),
                            name = name,
                            latitude = lat,
                            longitude = lon,
                            country = address.countryName,
                            admin1 = address.adminArea,
                            admin2 =
                                listOfNotNull(address.subAdminArea, address.locality)
                                    .firstOrNull {
                                        !WeatherRepository
                                            .normalizePlaceQuery(it)
                                            .equals(
                                                WeatherRepository.normalizePlaceQuery(name),
                                                ignoreCase = true,
                                            )
                                    },
                        )
                    }.distinctBy {
                        "${"%.2f".format(Locale.US, it.latitude)},${"%.2f".format(Locale.US, it.longitude)}"
                    }
            }.orEmpty()
        }

    /**
     * 中国地址里 locality 经常是地级市。搜索县城时应优先选带查询字的层级。
     */
    private fun bestMatchingPlaceName(
        address: Address,
        query: String,
    ): String? {
        val normalizedQuery = WeatherRepository.normalizePlaceQuery(query)
        val candidates =
            listOfNotNull(
                address.featureName,
                address.subLocality,
                address.subAdminArea,
                address.locality,
                address.thoroughfare,
                address.adminArea,
            ).filter { it.isNotBlank() && !it.matches(Regex("^\\d+$")) }

        val matched =
            candidates.firstOrNull { candidate ->
                val n = WeatherRepository.normalizePlaceQuery(candidate)
                n.contains(normalizedQuery, ignoreCase = true) ||
                    normalizedQuery.contains(n, ignoreCase = true)
            }
        if (matched != null) return matched

        // 海外 / 港澳台：系统常回英文名，无法与中文查询字面匹配
        val countryCode = address.countryCode?.uppercase(Locale.US)
        if (!countryCode.isNullOrBlank() && countryCode != "CN") {
            return candidates.firstOrNull()
        }

        // 没有任何层级包含查询词时，视为不相关（避免诸城→潍坊）
        return null
    }

    private suspend fun forwardGeocode(
        geocoder: Geocoder,
        query: String,
        maxResults: Int,
    ): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 单次回调也加超时，防止 getFromLocationName 永不回调
            withTimeoutOrNull(GEOCODER_ONCE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val completed = AtomicBoolean(false)
                    try {
                        geocoder.getFromLocationName(query, maxResults) { list ->
                            if (completed.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(list)
                            }
                        }
                    } catch (e: Exception) {
                        if (completed.compareAndSet(false, true) && cont.isActive) {
                            cont.resumeWithException(e)
                        }
                    }
                    cont.invokeOnCancellation { completed.set(true) }
                }
            }.orEmpty()
        } else {
            withTimeoutOrNull(GEOCODER_ONCE_TIMEOUT_MS) {
                runGeocoderBlocking {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, maxResults).orEmpty()
                }
            }.orEmpty()
        }

    /** 在守护线程池执行阻塞式 Geocoder 调用，协程取消/超时后不再等待（系统调用可能仍在后台跑完）。 */
    private suspend fun <T> runGeocoderBlocking(block: () -> T): T =
        suspendCancellableCoroutine { cont ->
            val future =
                geocoderExecutor.submit {
                    try {
                        val result = block()
                        if (cont.isActive) cont.resume(result)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            cont.invokeOnCancellation { future.cancel(true) }
        }

    /**
     * 仅用系统 Geocoder（免费、随用户量扩展无第三方配额）。
     * 同网格结果本地缓存，避免反复逆地理。
     * 失败（超时/无服务/无结果）返回 null，且**不写缓存**：
     * 否则占位经纬度会被缓存 7 天，阻塞后续补全真实地名。
     */
    private suspend fun reverseGeocode(
        lat: Double,
        lon: Double,
    ): PlaceName? =
        withContext(Dispatchers.IO) {
            readCachedPlace(lat, lon)?.let { return@withContext it }

            val fromSystem = reverseGeocodeSystem(lat, lon) ?: return@withContext null
            writeCachedPlace(lat, lon, fromSystem)
            fromSystem
        }

    private suspend fun reverseGeocodeSystem(
        lat: Double,
        lon: Double,
    ): PlaceName? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.CHINA)
        val addresses: List<Address> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(GEOCODER_ONCE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val completed = AtomicBoolean(false)
                        geocoder.getFromLocation(lat, lon, 5) { list ->
                            if (completed.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(list)
                            }
                        }
                        cont.invokeOnCancellation { completed.set(true) }
                    }
                }.orEmpty()
            } else {
                withTimeoutOrNull(GEOCODER_ONCE_TIMEOUT_MS) {
                    runGeocoderBlocking {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lon, 5).orEmpty()
                    }
                }.orEmpty()
            }
        if (addresses.isEmpty()) return null
        return addresses
            .mapNotNull { placeFromAddress(it) }
            .maxByOrNull { placeFineness(it.name) }
    }

    private fun placeFromAddress(address: Address): PlaceName? {
        val admin1 = cleanPlace(address.adminArea)
        val locality = cleanPlace(address.locality) // 多为地级市
        val subLocality = cleanPlace(address.subLocality)
        val subAdmin = cleanPlace(address.subAdminArea)
        val feature = cleanPlace(address.featureName)
        // 完整地址行里常带「…市历下区…」，字段拆分失败时仍可抠出区县
        val fromLine = districtFromAddressLines(address)

        // 中国：优先区/县，再县级市，最后才用地级市
        val district =
            listOfNotNull(subLocality, fromLine, subAdmin, feature)
                .firstOrNull { isDistrictLike(it) && !samePlace(it, admin1) }

        val countyLevelCity =
            listOfNotNull(subAdmin, feature)
                .firstOrNull {
                    isCityLike(it) &&
                        !samePlace(it, locality) &&
                        !samePlace(it, admin1) &&
                        !isTooSpecific(it)
                }

        val name =
            when {
                district != null -> district
                countyLevelCity != null -> countyLevelCity
                locality != null -> locality
                feature != null && !isTooSpecific(feature) -> feature
                admin1 != null -> admin1
                else -> return null
            }

        val admin2 =
            when {
                // 名称为区县时，把地级市放进 admin2，展示为「历下区, 济南市, 山东省」
                !samePlace(name, locality) && locality != null -> locality
                !samePlace(name, subAdmin) && subAdmin != null && isCityLike(subAdmin) -> subAdmin
                else -> null
            }

        return PlaceName(
            name = name,
            admin1 = admin1,
            admin2 = admin2,
            country = address.countryName,
        )
    }

    private fun districtFromAddressLines(address: Address): String? {
        val max = address.maxAddressLineIndex
        if (max < 0) return null
        val joined =
            buildString {
                for (i in 0..max) {
                    val line = address.getAddressLine(i) ?: continue
                    append(line)
                }
            }
        if (joined.isBlank()) return null
        // 先匹配更长后缀，避免「自治区」被拆成「区」
        val match =
            DISTRICT_IN_LINE
                .findAll(joined)
                .map { it.value }
                .lastOrNull { candidate ->
                    !candidate.endsWith("自治区") &&
                        !candidate.endsWith("市区") &&
                        !isTooSpecific(candidate)
                }
        return cleanPlace(match)
    }

    private fun gridKey(
        lat: Double,
        lon: Double,
    ): String {
        // ≈1km 网格，同区反复定位可复用缓存
        val gLat = (lat * 100.0).toInt()
        val gLon = (lon * 100.0).toInt()
        return "$gLat,$gLon"
    }

    private fun readCachedPlace(
        lat: Double,
        lon: Double,
    ): PlaceName? {
        val raw = placeCachePrefs.getString(gridKey(lat, lon), null) ?: return null
        val parts = raw.split('\u001f')
        if (parts.size < 4) return null
        val savedAt = parts[3].toLongOrNull() ?: return null
        if (!isWithinFreshnessWindow(savedAt, System.currentTimeMillis(), PLACE_CACHE_TTL_MS)) return null
        return PlaceName(
            name = parts[0],
            admin1 = parts[1].ifBlank { null },
            admin2 = parts[2].ifBlank { null },
            country = parts.getOrNull(4)?.ifBlank { null },
        )
    }

    private fun writeCachedPlace(
        lat: Double,
        lon: Double,
        place: PlaceName,
    ) {
        val value =
            listOf(
                place.name,
                place.admin1.orEmpty(),
                place.admin2.orEmpty(),
                System.currentTimeMillis().toString(),
                place.country.orEmpty(),
            ).joinToString("\u001f")
        placeCachePrefs.edit { putString(gridKey(lat, lon), value) }
    }

    // ---------- 最近一次定位（后台路径复用） ----------

    /** 供 UI 定位成功后写入，供 Worker / 小组件 [resolveCurrentCityReuseLast] 复用。 */
    fun rememberLastLocation(city: City) {
        val value =
            listOf(
                city.latitude.toString(),
                city.longitude.toString(),
                city.name,
                city.admin1.orEmpty(),
                city.admin2.orEmpty(),
                city.country.orEmpty(),
                System.currentTimeMillis().toString(),
            ).joinToString("\u001f")
        placeCachePrefs.edit { putString(LAST_LOCATION_KEY, value) }
    }

    private fun readLastLocation(): City? {
        val raw = placeCachePrefs.getString(LAST_LOCATION_KEY, null) ?: return null
        val parts = raw.split('\u001f')
        if (parts.size < 7) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return City(
            id = (("loc-$lat-$lon").hashCode()).toLong(),
            name = parts[2],
            latitude = lat,
            longitude = lon,
            country = parts[5].ifBlank { null },
            admin1 = parts[3].ifBlank { null },
            admin2 = parts[4].ifBlank { null },
            isCurrentLocation = true,
        )
    }

    /** 最近定位在新鲜窗口内则直接复用；否则 null。 */
    private fun reuseLastLocation(): City? {
        val raw = placeCachePrefs.getString(LAST_LOCATION_KEY, null) ?: return null
        val parts = raw.split('\u001f')
        if (parts.size < 7) return null
        val savedAt = parts[6].toLongOrNull() ?: return null
        if (!isWithinFreshnessWindow(savedAt, System.currentTimeMillis(), REUSE_FRESH_MS)) return null
        return readLastLocation()
    }

    private fun cleanPlace(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.matches(Regex("^\\d+$"))) return null
        return trimmed
    }

    private fun samePlace(
        a: String?,
        b: String?,
    ): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return WeatherRepository
            .normalizePlaceQuery(a)
            .equals(WeatherRepository.normalizePlaceQuery(b), ignoreCase = true)
    }

    /** 区 / 县 / 旗等，比地级市更细 */
    private fun isDistrictLike(name: String): Boolean {
        val n = name.trim()
        return n.endsWith("区") ||
            n.endsWith("县") ||
            n.endsWith("旗") ||
            n.endsWith("自治县") ||
            n.endsWith("自治旗") ||
            n.endsWith("林区") ||
            n.endsWith("新区") ||
            n.endsWith("镇") ||
            n.endsWith("乡") ||
            n.endsWith("苏木")
    }

    private fun isCityLike(name: String): Boolean {
        val n = name.trim()
        return n.endsWith("市") || n.endsWith("地区") || n.endsWith("盟") || n.endsWith("州")
    }

    private fun isTooSpecific(name: String): Boolean {
        val n = name.trim()
        return n.any { it.isDigit() } ||
            n.endsWith("路") ||
            n.endsWith("街") ||
            n.endsWith("道") ||
            n.endsWith("巷") ||
            n.endsWith("号") ||
            n.contains("附近")
    }

    private fun placeFineness(name: String): Int =
        when {
            name.endsWith("镇") || name.endsWith("乡") || name.endsWith("苏木") -> 40
            isDistrictLike(name) -> 30
            isCityLike(name) -> 20
            else -> 10
        }

    companion object {
        /** last-known 可直接采用的新鲜度（城市天气） */
        private const val FRESH_LAST_KNOWN_MS = 15 * 60 * 1000L
        private const val MAX_LAST_KNOWN_AGE_MS = 30 * 60 * 1000L
        private const val LOCATION_WAIT_MS = 4_000L
        private const val LOCATION_OUTER_MS = 6_000L

        /** Fused 兜底 getCurrentLocation 短超时，避免国产机假 GMS 长卡 */
        private const val FUSED_FALLBACK_WAIT_MS = 2_000L

        /** Fused 允许复用最近一次结果的年龄 */
        private const val FUSED_MAX_UPDATE_AGE_MS = 60_000L

        /** 城市级天气：1km 精度足够，不必等 GPS 精修到 150m */
        private const val ACCEPTABLE_ACCURACY_M = 1_000f
        private const val GEOCODER_BUDGET_MS = 3_000L
        private const val GEOCODER_SEARCH_TIMEOUT_MS = 3_000L
        private const val GEOCODER_ONCE_TIMEOUT_MS = 2_500L
        private const val PLACE_CACHE_PREFS = "location_place_cache"
        private const val PLACE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** 最近一次定位的复用新鲜窗口：期间后台路径（小组件/Worker）不重新定位 */
        private const val REUSE_FRESH_MS = 10L * 60 * 1000
        private const val LAST_LOCATION_KEY = "last_location"
        private val DISTRICT_IN_LINE =
            Regex("[\u4e00-\u9fff]{1,8}(?:自治县|新区|林区|区|县|旗)")
    }
}
