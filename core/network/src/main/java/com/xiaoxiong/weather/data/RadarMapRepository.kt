package com.xiaoxiong.weather.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.xiaoxiong.weather.data.remote.RainViewerFrame
import com.xiaoxiong.weather.data.remote.RainViewerMapsResponse
import com.xiaoxiong.weather.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class RadarFrame(
    val timeEpochSec: Long,
    val path: String,
    val imageUrl: String,
    /** RainViewer 短临外推帧（未来约 0.5～2h）；false 为已观测实况。 */
    val isNowcast: Boolean = false,
)

/**
 * 单类雷达帧（past / nowcast）的数量上限。
 *
 * 正常响应约 10 + 30 帧；上游异常返回海量帧时，每帧都会拼 URL 并被并发信号量放行下载，
 * 数组与协程数量都会失控。取 64 既覆盖正常上限（30 帧短临）又有充足余量。
 */
internal const val MAX_RASTER_FRAMES = 64

data class SatelliteCloudMap(
    val image: ImageBitmap,
    /** UTC 日期 yyyy-MM-dd（GIBS 日合成产品） */
    val dateUtc: String,
    val layerName: String,
)

class RadarMapRepository(
    private val client: OkHttpClient,
    private val json: Json = defaultJson(),
    private val versionName: String = "",
) {
    private val cacheMutex = Mutex()

    /** accessOrder=true：按访问做 LRU，避免切城后内存无限涨 */
    private val imageCache =
        object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > MAX_MEMORY_FRAMES
        }

    /** 底图按 zoom/size/坐标缓存，雷达与云图参数不同时可并存 */
    private val basemapCache =
        object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > MAX_BASEMAP_CACHE
        }
    private var satelliteKey: String? = null
    private var satelliteMap: SatelliteCloudMap? = null

    /**
     * 雷达时间轴：实况 past + 短临 nowcast（按时间升序）。
     * 短临为 RainViewer 外推，通常覆盖未来约 1～2 小时。
     */
    suspend fun fetchRadarFrames(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
    ): List<RadarFrame> =
        withContext(Dispatchers.IO) {
            val body = execute(MAPS_JSON_URL)
            val response =
                try {
                    json.decodeFromString(RainViewerMapsResponse.serializer(), body)
                } catch (e: kotlinx.serialization.SerializationException) {
                    throw AppError.DataFormat(e)
                }
            val host = response.host.trimEnd('/')
            // 帧数上限：RainViewer 正常只返回过去 ~2h（10 帧）+ 短临（30 帧）。
            // 上游异常返回十万帧时，map 会为每帧拼 URL 并各起一个协程下载 → OOM。
            val past =
                response.radar?.past.orEmpty().take(MAX_RASTER_FRAMES).map { frame ->
                    RadarFrame(
                        timeEpochSec = frame.time,
                        path = frame.path,
                        imageUrl = buildRadarImageUrl(host, frame, latitude, longitude, zoom),
                        isNowcast = false,
                    )
                }
            val nowcast =
                response.radar?.nowcast.orEmpty().take(MAX_RASTER_FRAMES).map { frame ->
                    RadarFrame(
                        timeEpochSec = frame.time,
                        path = frame.path,
                        imageUrl = buildRadarImageUrl(host, frame, latitude, longitude, zoom),
                        isNowcast = true,
                    )
                }
            mergeRadarTimeline(past, nowcast)
        }

    /** @deprecated 使用 [fetchRadarFrames]（含短临）；保留别名以免外部旧调用断裂。 */
    suspend fun fetchPastRadarFrames(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
    ): List<RadarFrame> = fetchRadarFrames(latitude, longitude, zoom)

    /**
     * 与 RainViewer 同中心、同缩放的 Web Mercator 底图（Carto Voyager），
     * 便于识别城市与地形；雷达帧叠在上方。
     */
    suspend fun loadBasemap(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
        size: Int = IMAGE_SIZE,
    ): ImageBitmap? {
        val key = "base|$zoom|$size|${"%.4f".format(Locale.US, latitude)}|${"%.4f".format(Locale.US, longitude)}"
        cacheMutex.withLock {
            basemapCache[key]?.let { return it }
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    composeCenteredMap(latitude, longitude, zoom, size) { z, x, y ->
                        fetchCartoTile(z, x, y)
                    }?.asImageBitmap()
                }.getOrNull()
            }
        if (loaded != null) {
            cacheMutex.withLock {
                basemapCache[key] = loaded
            }
        }
        return loaded
    }

    /**
     * NASA GIBS VIIRS 真彩日合成云图（免 key）。
     * RainViewer 卫星红外已停服，改用此层；按需加载并缓存。
     * 默认比雷达更高 zoom / 分辨率，便于叠底图后认出城市。
     */
    suspend fun loadSatelliteCloudMap(
        latitude: Double,
        longitude: Double,
        zoom: Int = CLOUD_ZOOM,
        size: Int = CLOUD_IMAGE_SIZE,
        forceRefresh: Boolean = false,
    ): SatelliteCloudMap? {
        val coordKey =
            "$zoom|$size|${"%.4f".format(Locale.US, latitude)}|${"%.4f".format(Locale.US, longitude)}"
        if (!forceRefresh) {
            cacheMutex.withLock {
                if (satelliteKey == coordKey) satelliteMap?.let { return it }
            }
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatchingCancellable { composeSatellite(latitude, longitude, zoom, size) }.getOrNull()
            }
        if (loaded != null) {
            cacheMutex.withLock {
                satelliteKey = coordKey
                satelliteMap = loaded
            }
        }
        return loaded
    }

    suspend fun loadImage(url: String): ImageBitmap? {
        cacheMutex.withLock {
            imageCache[url]?.let { return it }
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatchingCancellable { decodeUrl(url) }.getOrNull()?.asImageBitmap()
            }
        if (loaded != null) {
            cacheMutex.withLock {
                imageCache[url] = loaded
            }
        }
        return loaded
    }

    /** 并行预取动画帧（限并发，避免打爆连接） */
    suspend fun prefetchImages(urls: List<String>) =
        coroutineScope {
            if (urls.isEmpty()) return@coroutineScope
            val gate = Semaphore(PREFETCH_PARALLELISM)
            urls
                .map { url ->
                    async(Dispatchers.IO) {
                        gate.withPermit { loadImage(url) }
                    }
                }.awaitAll()
        }

    /** 只清雷达帧；底图 / 云图按城市键复用 */
    suspend fun clearRadarFrameCache() {
        cacheMutex.withLock { imageCache.clear() }
    }

    private suspend fun composeSatellite(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        size: Int,
    ): SatelliteCloudMap? {
        val candidates = gibsDateCandidates()
        for (date in candidates) {
            val bitmap =
                composeCenteredMap(latitude, longitude, zoom, size) { z, x, y ->
                    fetchGibsTile(date, z, x, y)
                } ?: continue
            // 至少拼上一部分瓦片才算成功（全失败时 bitmap 也可能是空画布）
            if (bitmap.width > 0) {
                return SatelliteCloudMap(
                    image = bitmap.asImageBitmap(),
                    dateUtc = date,
                    layerName = GIBS_LAYER_SHORT,
                )
            }
        }
        return null
    }

    private suspend fun composeCenteredMap(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        size: Int,
        fetchTile: suspend (zoom: Int, x: Int, y: Int) -> Bitmap?,
    ): Bitmap? =
        coroutineScope {
            val scale = TILE_SIZE * (1 shl zoom).toDouble()
            val (cx, cy) = latLonToWorldPixels(latitude, longitude, scale)
            val left = cx - size / 2.0
            val top = cy - size / 2.0
            val maxTileIndex = (1 shl zoom) - 1

            data class TileJob(
                val tx: Int,
                val ty: Int,
                val wrappedX: Int,
            )

            val jobs = ArrayList<TileJob>()
            for (ty in startYRange(top, size, maxTileIndex)) {
                for (tx in startXRange(left, size)) {
                    val wrappedX = ((tx % (maxTileIndex + 1)) + (maxTileIndex + 1)) % (maxTileIndex + 1)
                    jobs += TileJob(tx, ty, wrappedX)
                }
            }
            if (jobs.isEmpty()) return@coroutineScope null

            val tiles =
                jobs
                    .map { job ->
                        async(Dispatchers.IO) {
                            job to fetchTile(zoom, job.wrappedX, job.ty)
                        }
                    }.awaitAll()

            var drawn = 0
            val result = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            tiles.forEach { (job, tile) ->
                if (tile == null) return@forEach
                val dstX = (job.tx * TILE_SIZE - left).toFloat()
                val dstY = (job.ty * TILE_SIZE - top).toFloat()
                canvas.drawBitmap(tile, dstX, dstY, null)
                tile.recycle()
                drawn++
            }
            if (drawn == 0) {
                result.recycle()
                null
            } else {
                result
            }
        }

    private fun startYRange(
        top: Double,
        size: Int,
        maxTileIndex: Int,
    ): IntRange {
        val startY = floor(top / TILE_SIZE).toInt()
        val endY = floor((top + size - 1) / TILE_SIZE).toInt()
        return startY.coerceAtLeast(0)..endY.coerceAtMost(maxTileIndex)
    }

    private fun startXRange(
        left: Double,
        size: Int,
    ): IntRange {
        val startX = floor(left / TILE_SIZE).toInt()
        val endX = floor((left + size - 1) / TILE_SIZE).toInt()
        return startX..endX
    }

    private suspend fun fetchCartoTile(
        zoom: Int,
        x: Int,
        y: Int,
    ): Bitmap? {
        val url = "https://basemaps.cartocdn.com/rastertiles/voyager/$zoom/$x/$y.png"
        return decodeUrl(url, opaque = true)
    }

    private suspend fun fetchGibsTile(
        dateUtc: String,
        zoom: Int,
        x: Int,
        y: Int,
    ): Bitmap? {
        // GIBS GoogleMapsCompatible：路径为 {z}/{y}/{x}
        val url =
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
                "$GIBS_LAYER/default/$dateUtc/GoogleMapsCompatible_Level9/$zoom/$y/$x.jpg"
        return decodeUrl(url, opaque = true)
    }

    private suspend fun decodeUrl(
        url: String,
        opaque: Boolean = false,
    ): Bitmap? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .get()
                .build()
        return client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            val options =
                BitmapFactory.Options().apply {
                    if (opaque) {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                }
            return@use BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    private fun latLonToWorldPixels(
        latitude: Double,
        longitude: Double,
        scale: Double,
    ): Pair<Double, Double> {
        val x = (longitude + 180.0) / 360.0 * scale
        val latRad = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
        return x to y
    }

    private fun buildRadarImageUrl(
        host: String,
        frame: RainViewerFrame,
        latitude: Double,
        longitude: Double,
        zoom: Int,
    ): String {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        // color=2 Universal Blue；smooth=1 snow=1；256 更小更快，显示足够
        return "$host${frame.path}/$IMAGE_SIZE/$zoom/$lat/$lon/2/1_1.png"
    }

    private suspend fun execute(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .get()
                .build()
        return try {
            client.newCall(request).awaitResponse().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AppError.Http(response.code)
                }
                body
            }
        } catch (e: AppError) {
            throw e
        } catch (e: java.io.IOException) {
            throw AppError.NetworkUnavailable(e)
        }
    }

    private fun userAgent(): String =
        if (versionName.isNotEmpty()) {
            "XiaoxiongWeather/$versionName (Android; personal use)"
        } else {
            "XiaoxiongWeather (Android; personal use)"
        }

    /**
     * enqueue 包装阻塞调用：协程取消时 cancel 底层 Call，
     * 避免同步 execute() 的僵尸请求占用 IO 线程与连接池直到超时（见 OpenMeteoHttp.awaitBody）。
     */
    private suspend fun okhttp3.Call.awaitResponse(): okhttp3.Response =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            enqueue(
                object : okhttp3.Callback {
                    override fun onFailure(
                        call: okhttp3.Call,
                        e: java.io.IOException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: okhttp3.Call,
                        response: okhttp3.Response,
                    ) {
                        // 取消回调先于 resume 执行，避免 isActive 判断与取消之间的竞态泄漏响应
                        // （与 OpenMeteoHttp.awaitBody 同一写法；瓦片并发拉取时取消更频繁）
                        cont.resume(response) { response.close() }
                    }
                },
            )
            cont.invokeOnCancellation { cancel() }
        }

    companion object {
        private const val MAPS_JSON_URL =
            "https://api.rainviewer.com/public/weather-maps.json"
        private const val TILE_SIZE = 256

        /** 雷达拼图：256 体积更小，首屏更快 */
        private const val IMAGE_SIZE = 256

        /** 云图拼图：512 减轻放大发糊 */
        const val CLOUD_IMAGE_SIZE = 512

        /** 雷达：略拉近，便于认出城市与周边 */
        const val DEFAULT_ZOOM = 6

        /**
         * 云图：z8 + 512px，视野约为雷达的一半、像素密度更高；
         * GIBS GoogleMapsCompatible_Level9 最高支持 z9。
         */
        const val CLOUD_ZOOM = 8

        /** past + nowcast 约 20～30 帧，略留余量 */
        private const val MAX_MEMORY_FRAMES = 40
        private const val MAX_BASEMAP_CACHE = 4
        private const val PREFETCH_PARALLELISM = 6
        private const val GIBS_LAYER =
            "VIIRS_SNPP_CorrectedReflectance_TrueColor"
        private const val GIBS_LAYER_SHORT = "VIIRS SNPP"

        /** 优先昨天（日合成更完整），再回退今天 / 前天 */
        fun gibsDateCandidates(): List<String> {
            val today = LocalDate.now(ZoneOffset.UTC)
            return listOf(today.minusDays(1), today, today.minusDays(2))
                .map { it.toString() }
                .distinct()
        }

        fun windyRadarUrl(
            latitude: Double,
            longitude: Double,
        ): String = windyEmbedUrl(latitude, longitude, overlay = "radar", product = "radar")

        fun windySatelliteUrl(
            latitude: Double,
            longitude: Double,
        ): String = windyEmbedUrl(latitude, longitude, overlay = "satellite", product = "satellite")

        private fun windyEmbedUrl(
            latitude: Double,
            longitude: Double,
            overlay: String,
            product: String,
        ): String {
            val lat = String.format(Locale.US, "%.4f", latitude)
            val lon = String.format(Locale.US, "%.4f", longitude)
            return buildString {
                append("https://embed.windy.com/embed.html?")
                append("type=map&location=coordinates")
                append("&metricRain=mm&metricTemp=%C2%B0C&metricWind=km%2Fh")
                append("&zoom=6&overlay=$overlay&product=$product&level=surface")
                append("&lat=$lat&lon=$lon")
                append("&detailLat=$lat&detailLon=$lon")
                append("&detail=true&calendar=now&message=true")
                append("&marker=true&pressure=false")
            }
        }

        private fun defaultJson(): Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}

/** 合并实况与短临，按时间升序；同刻优先保留实况。 */
fun mergeRadarTimeline(
    past: List<RadarFrame>,
    nowcast: List<RadarFrame>,
): List<RadarFrame> {
    if (nowcast.isEmpty()) return past
    if (past.isEmpty()) return nowcast.sortedBy { it.timeEpochSec }
    val pastTimes = past.mapTo(HashSet()) { it.timeEpochSec }
    val extra = nowcast.filter { it.timeEpochSec !in pastTimes }
    // 合并后同样设总上限：past+nowcast 可达 2×MAX_RASTER_FRAMES，
    // 而轮播只用到最近的一小段，超出的部分纯属浪费内存与预取。
    val merged = (past + extra).sortedBy { it.timeEpochSec }
    return if (merged.size > MAX_RASTER_FRAMES) merged.takeLast(MAX_RASTER_FRAMES) else merged
}

/** 首屏停在最新实况帧；若无实况则落在最后一帧。 */
fun radarObservedNowIndex(frames: List<RadarFrame>): Int {
    if (frames.isEmpty()) return 0
    val lastPast = frames.indexOfLast { !it.isNowcast }
    return if (lastPast >= 0) lastPast else frames.lastIndex
}
