package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.LocationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 定位请求编排：可取消、可丢弃过期结果，避免换城后被迟到的 GPS 盖回去。
 * 先回调坐标（开天气），再异步补地名；逆地理失败延迟重试一次，
 * 避免系统 Geocoder 冷启动慢导致地名长期停留在「当前位置」占位。
 */
class LocationWeatherCoordinator(
    private val locationHelper: LocationHelper,
    private val scope: CoroutineScope,
) : LocationSession {
    private val requestId = AtomicInteger(0)
    private var job: Job? = null

    override fun cancelPending() {
        job?.cancel()
        job = null
        requestId.incrementAndGet()
    }

    override fun locate(
        onResolved: (City) -> Unit,
        onError: (Throwable) -> Unit,
        onPlaceUpdated: (City) -> Unit,
    ) {
        val locateId = requestId.incrementAndGet()
        job?.cancel()
        job =
            scope.launch {
                try {
                    val location = locationHelper.resolveCurrentCoordinates()
                    if (locateId != requestId.get()) return@launch
                    val quick = locationHelper.provisionalCity(location)
                    onResolved(quick)

                    var refined = locationHelper.refineCityPlaceName(quick)
                    if (locateId != requestId.get()) return@launch
                    // refineCityPlaceName 失败时原样返回 quick（同一实例）；
                    // 成功时总是 copy 出新实例，即使字段恰好相同。
                    if (refined === quick) {
                        // 逆地理失败（超时/无服务）：延迟重试一次，成功后补全地名
                        delay(PLACE_RETRY_DELAY_MS)
                        if (locateId != requestId.get()) return@launch
                        refined = locationHelper.refineCityPlaceName(quick)
                        if (locateId != requestId.get()) return@launch
                    }
                    if (placeRefined(quick, refined)) {
                        onPlaceUpdated(refined)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (locateId != requestId.get()) return@launch
                    onError(e)
                }
            }
    }

    /** 地名是否被逆地理补全/刷新（name/admin1/admin2 任一变化）。 */
    private fun placeRefined(
        quick: City,
        refined: City,
    ): Boolean =
        refined.name != quick.name ||
            refined.admin1 != quick.admin1 ||
            refined.admin2 != quick.admin2

    companion object {
        /** 逆地理失败后的重试间隔：给 Geocoder 服务一点就绪时间 */
        private const val PLACE_RETRY_DELAY_MS = 2_500L
    }
}
