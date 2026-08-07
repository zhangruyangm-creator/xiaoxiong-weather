package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.LocationHelper
import com.xiaoxiong.weather.data.LocationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 定位请求编排：可取消、可丢弃过期结果，避免换城后被迟到的 GPS 盖回去。
 * 先回调坐标（开天气），再异步补地名。
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

                    val refined = locationHelper.refineCityPlaceName(quick)
                    if (locateId != requestId.get()) return@launch
                    if (refined.name != quick.name ||
                        refined.admin1 != quick.admin1 ||
                        refined.admin2 != quick.admin2
                    ) {
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
}
