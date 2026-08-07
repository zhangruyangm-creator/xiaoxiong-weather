package com.xiaoxiong.weather.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xiaoxiong.weather.BuildConfig
import com.xiaoxiong.weather.data.remote.RainViewerFrame
import com.xiaoxiong.weather.data.remote.RainViewerMapsResponse
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class RadarFrame(
    val timeEpochSec: Long,
    val path: String,
    val imageUrl: String
)

data class SatelliteCloudMap(
    val image: ImageBitmap,
    /** UTC 日期 yyyy-MM-dd（GIBS 日合成产品） */
    val dateUtc: String,
    val layerName: String
)

class RadarMapRepository(
    private val client: OkHttpClient,
    private val json: Json = defaultJson()
) {
    private val cacheMutex = Mutex()
    /** accessOrder=true：按访问做 LRU，避免切城后内存无限涨 */
    private val imageCache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_MEMORY_FRAMES
    }
    /** 底图按 zoom/size/坐标缓存，雷达与云图参数不同时可并存 */
    private val basemapCache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_BASEMAP_CACHE
    }
    private var satelliteKey: String? = null
    private var satelliteMap: SatelliteCloudMap? = null

    suspend fun fetchPastRadarFrames(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM
    ): List<RadarFrame> = withContext(Dispatchers.IO) {
        val body = execute(MAPS_JSON_URL)
        val response = json.decodeFromString(RainViewerMapsResponse.serializer(), body)
        val host = response.host.trimEnd('/')
        response.radar?.past.orEmpty().map { frame ->
            RadarFrame(
                timeEpochSec = frame.time,
                path = frame.path,
                imageUrl = buildRadarImageUrl(host, frame, latitude, longitude, zoom)
            )
        }
    }

    /**
     * 与 RainViewer 同中心、同缩放的 Web Mercator 底图（Carto Voyager），
     * 便于识别城市与地形；雷达帧叠在上方。
     */
    suspend fun loadBasemap(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
        size: Int = IMAGE_SIZE
    ): ImageBitmap? {
        val key = "base|$zoom|$size|${"%.4f".format(Locale.US, latitude)}|${"%.4f".format(Locale.US, longitude)}"
        cacheMutex.withLock {
            basemapCache[key]?.let { return it }
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
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
        forceRefresh: Boolean = false
    ): SatelliteCloudMap? {
        val coordKey =
            "$zoom|$size|${"%.4f".format(Locale.US, latitude)}|${"%.4f".format(Locale.US, longitude)}"
        if (!forceRefresh) {
            cacheMutex.withLock {
                if (satelliteKey == coordKey) satelliteMap?.let { return it }
            }
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { composeSatellite(latitude, longitude, zoom, size) }.getOrNull()
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
        val loaded = withContext(Dispatchers.IO) {
            runCatching { decodeUrl(url) }.getOrNull()?.asImageBitmap()
        }
        if (loaded != null) {
            cacheMutex.withLock {
                imageCache[url] = loaded
            }
        }
        return loaded
    }

    /** 并行预取动画帧（限并发，避免打爆连接） */
    suspend fun prefetchImages(urls: List<String>) = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope
        val gate = Semaphore(PREFETCH_PARALLELISM)
        urls.map { url ->
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
        size: Int
    ): SatelliteCloudMap? {
        val candidates = gibsDateCandidates()
        for (date in candidates) {
            val bitmap = composeCenteredMap(latitude, longitude, zoom, size) { z, x, y ->
                fetchGibsTile(date, z, x, y)
            } ?: continue
            // 至少拼上一部分瓦片才算成功（全失败时 bitmap 也可能是空画布）
            if (bitmap.width > 0) {
                return SatelliteCloudMap(
                    image = bitmap.asImageBitmap(),
                    dateUtc = date,
                    layerName = GIBS_LAYER_SHORT
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
        fetchTile: suspend (zoom: Int, x: Int, y: Int) -> Bitmap?
    ): Bitmap? = coroutineScope {
        val scale = TILE_SIZE * (1 shl zoom).toDouble()
        val (cx, cy) = latLonToWorldPixels(latitude, longitude, scale)
        val left = cx - size / 2.0
        val top = cy - size / 2.0
        val maxTileIndex = (1 shl zoom) - 1

        data class TileJob(val tx: Int, val ty: Int, val wrappedX: Int)

        val jobs = ArrayList<TileJob>()
        for (ty in startYRange(top, size, maxTileIndex)) {
            for (tx in startXRange(left, size)) {
                val wrappedX = ((tx % (maxTileIndex + 1)) + (maxTileIndex + 1)) % (maxTileIndex + 1)
                jobs += TileJob(tx, ty, wrappedX)
            }
        }
        if (jobs.isEmpty()) return@coroutineScope null

        val tiles = jobs.map { job ->
            async(Dispatchers.IO) {
                job to fetchTile(zoom, job.wrappedX, job.ty)
            }
        }.awaitAll()

        var drawn = 0
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
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

    private fun startYRange(top: Double, size: Int, maxTileIndex: Int): IntRange {
        val startY = floor(top / TILE_SIZE).toInt()
        val endY = floor((top + size - 1) / TILE_SIZE).toInt()
        return startY.coerceAtLeast(0)..endY.coerceAtMost(maxTileIndex)
    }

    private fun startXRange(left: Double, size: Int): IntRange {
        val startX = floor(left / TILE_SIZE).toInt()
        val endX = floor((left + size - 1) / TILE_SIZE).toInt()
        return startX..endX
    }

    private fun fetchCartoTile(zoom: Int, x: Int, y: Int): Bitmap? {
        val url = "https://basemaps.cartocdn.com/rastertiles/voyager/$zoom/$x/$y.png"
        return decodeUrl(url, opaque = true)
    }

    private fun fetchGibsTile(dateUtc: String, zoom: Int, x: Int, y: Int): Bitmap? {
        // GIBS GoogleMapsCompatible：路径为 {z}/{y}/{x}
        val url =
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
                "$GIBS_LAYER/default/$dateUtc/GoogleMapsCompatible_Level9/$zoom/$y/$x.jpg"
        return decodeUrl(url, opaque = true)
    }

    private fun decodeUrl(url: String, opaque: Boolean = false): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "XiaoxiongWeather/${BuildConfig.VERSION_NAME} (Android; personal use)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val options = BitmapFactory.Options().apply {
                if (opaque) {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    private fun latLonToWorldPixels(
        latitude: Double,
        longitude: Double,
        scale: Double
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
        zoom: Int
    ): String {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        // color=2 Universal Blue；smooth=1 snow=1；256 更小更快，显示足够
        return "$host${frame.path}/$IMAGE_SIZE/$zoom/$lat/$lon/2/1_1.png"
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "XiaoxiongWeather/${BuildConfig.VERSION_NAME} (Android; personal use)")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AppError.Http(response.code)
                }
                return body
            }
        } catch (e: AppError) {
            throw e
        } catch (e: java.io.IOException) {
            throw AppError.NetworkUnavailable(e)
        }
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
        /** 约两段近 2 小时雷达序列，足够循环播放 */
        private const val MAX_MEMORY_FRAMES = 28
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

        fun windyRadarUrl(latitude: Double, longitude: Double): String =
            windyEmbedUrl(latitude, longitude, overlay = "radar", product = "radar")

        fun windySatelliteUrl(latitude: Double, longitude: Double): String =
            windyEmbedUrl(latitude, longitude, overlay = "satellite", product = "satellite")

        private fun windyEmbedUrl(
            latitude: Double,
            longitude: Double,
            overlay: String,
            product: String
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

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
