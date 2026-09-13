package com.xiaoxiong.weather.ui.weather

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.RadarFrame
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.isWifiConnected
import com.xiaoxiong.weather.data.radarObservedNowIndex
import com.xiaoxiong.weather.ui.theme.AccentCoral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LiveRadarPane(
    city: City,
    repository: RadarMapRepository,
    timezoneId: String? = null,
    /** 游戏/弹窗等场景活跃：暂停轮播与预取，省电省流量 */
    paused: Boolean = false,
) {
    var frames by remember(city.id, city.latitude, city.longitude) {
        mutableStateOf<List<RadarFrame>>(emptyList())
    }
    var basemap by remember(city.id, city.latitude, city.longitude) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var imageLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val loadPolicy = remember { RadarTimelineLoadPolicy() }
    var prefetchStarted by remember(city.id, city.latitude, city.longitude) { mutableStateOf(false) }
    var everPlayed by remember(city.id, city.latitude, city.longitude) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val appStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    // 系统开启「减弱动画」时不做自动播放（手动点播放不受限）
    val reduceMotion = com.xiaoxiong.weather.ui.LocalReduceMotion.current
    val zoom = RadarMapRepository.DEFAULT_ZOOM
    // Wi-Fi 全量预取并自动播放；蜂窝只下底图+最新帧，点播放才预取
    val wifi = remember(city.id, city.latitude, city.longitude) { context.isWifiConnected() }

    // 帧缓存按 URL 复用（RadarMapRepository 内 LRU 自动淘汰），切城/重进都无需清空：
    // 清空会把刚预取好的帧扔掉，重进页面又得重下，白白浪费流量

    // 切城 / 刷新：底图与帧列表并行；先出最新一帧再后台预取其余。
    // 10 分钟内的重新进入直接复用时间轴缓存（图片仍走仓库 LRU），
    // 不再次请求 RainViewer、也不重播扫描；用户手动刷新只对触发它的那次加载绕过缓存
    LaunchedEffect(city.id, city.latitude, city.longitude, refreshKey) {
        loadPolicy.onCity("${city.id}|${city.latitude}|${city.longitude}")
        val bypassCache = loadPolicy.consumeBypassCache()
        loading = true
        errorRes = null
        playing = false
        prefetchStarted = false
        everPlayed = false
        currentBitmap = null
        frames = emptyList()
        try {
            coroutineScope {
                val basemapJob =
                    async {
                        repository.loadBasemap(city.latitude, city.longitude, zoom)
                    }
                val framesJob =
                    async {
                        val cached =
                            if (bypassCache) {
                                null
                            } else {
                                RadarTimelineCache.shared.get(city.latitude, city.longitude, zoom)
                            }
                        cached
                            ?: repository
                                .fetchRadarFrames(city.latitude, city.longitude, zoom)
                                .also {
                                    RadarTimelineCache.shared.put(city.latitude, city.longitude, zoom, it)
                                }
                    }
                basemap = basemapJob.await()
                val list = framesJob.await()
                frames = list
                val nowIndex = radarObservedNowIndex(list)
                frameIndex = nowIndex
                if (list.isEmpty()) {
                    loading = false
                    errorRes = R.string.radar_empty
                    return@coroutineScope
                }
                // 首屏停在最新实况（非短临末帧），马上结束转圈
                val latestObserved = list[nowIndex]
                currentBitmap = repository.loadImage(latestObserved.imageUrl)
                loading = false
                // Wi-Fi：默认自动播放；蜂窝：停在最新实况，等用户点播放。
                // 减弱动画模式下也停在最新实况，避免自动循环播放
                playing = wifi && !reduceMotion
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loading = false
            frames = emptyList()
            errorRes = R.string.radar_load_failed
        }
    }

    fun reload() {
        loadPolicy.requestManualRefresh()
        refreshKey += 1
    }

    LaunchedEffect(frames, frameIndex) {
        val frame =
            frames.getOrNull(frameIndex) ?: run {
                currentBitmap = null
                return@LaunchedEffect
            }
        val cachedHint = currentBitmap
        if (cachedHint == null) imageLoading = true
        currentBitmap = repository.loadImage(frame.imageUrl)
        imageLoading = false
    }

    // 首次进入播放时预取其余帧：Wi-Fi 加载完即自动播放随即触发；蜂窝由用户点播放触发。
    // paused（游戏进行中等）时不预取，避免后台占用带宽
    LaunchedEffect(frames, playing, paused) {
        if (playing && !paused && frames.size > 1 && !prefetchStarted) {
            prefetchStarted = true
            repository.prefetchImages(frames.dropLast(1).map { it.imageUrl })
        }
    }

    // 首次进入播放（Wi-Fi 自动 / 蜂窝首次点播放）从最早帧开始，按时间正序播到最新；
    // 暂停后再次播放从当前帧继续，不打断观看进度
    LaunchedEffect(playing) {
        if (playing) {
            if (!everPlayed) frameIndex = 0
            everPlayed = true
        }
    }

    LaunchedEffect(playing, frames, appStarted, paused) {
        // 游戏/弹窗活跃（paused）时停止 500ms 轮播，恢复后自动续播
        if (!playing || !appStarted || paused || frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(500)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2A38)),
    ) {
        when {
            loading -> {
                RadarScanLoadingOverlay(
                    cityName = city.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            errorRes != null && frames.isEmpty() && basemap == null -> {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(errorRes ?: R.string.radar_load_failed_generic),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { reload() }) {
                        Text(
                            stringResource(R.string.label_retry),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            else -> {
                basemap?.let { map ->
                    Image(
                        bitmap = map,
                        contentDescription = stringResource(R.string.cd_basemap),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                currentBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = stringResource(R.string.cd_radar_echo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.88f,
                    )
                }
                if (imageLoading && currentBitmap == null) {
                    RadarScanLoadingOverlay(
                        cityName = city.name,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 城市定位点（雷达图中心即所选城市）
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(AccentCoral.copy(alpha = 0.28f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AccentCoral)
                                .border(1.5.dp, Color.White, CircleShape),
                    )
                }
                Text(
                    text = city.name,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                frames.getOrNull(frameIndex)?.let { frame ->
                    val frameTimeText = formatRadarTime(frame.timeEpochSec, timezoneId)
                    // Canvas 内容对读屏不可见，这里用 stateDescription 说明当前帧时间
                    val frameTimeDesc = stringResource(R.string.cd_radar_frame_time, frameTimeText)
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .semantics { stateDescription = frameTimeDesc },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (frame.isNowcast) {
                            Text(
                                text = stringResource(R.string.radar_nowcast_badge),
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE65100).copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = frameTimeText,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                val overlayError = errorRes
                if (overlayError != null && frames.isEmpty()) {
                    Text(
                        text = stringResource(overlayError),
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (frames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledIconButton(
                onClick = { playing = !playing },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription =
                        stringResource(
                            if (playing) R.string.cd_pause else R.string.cd_play,
                        ),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            FilledIconButton(
                onClick = { reload() },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_radar),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 单帧时没有可滑动区间：不渲染 Slider，避免 valueRange 0f..0f 触发异常
            if (frames.size >= 2) {
                // 避免播放时每帧重建 MutableState
                var sliderValue by remember(frames.size) { mutableFloatStateOf(frameIndex.toFloat()) }
                LaunchedEffect(frameIndex) {
                    sliderValue = frameIndex.toFloat()
                }
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        playing = false
                        sliderValue = it
                        frameIndex = it.toInt().coerceIn(0, frames.lastIndex)
                    },
                    valueRange = 0f..frames.lastIndex.toFloat(),
                    steps = frames.size - 2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.radar_hint_center),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                val url = RadarMapRepository.windyRadarUrl(city.latitude, city.longitude)
                // 无浏览器/无 Activity 处理的设备上 startActivity 会抛
                // ActivityNotFoundException，这里降级为非阻塞提示
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    .onFailure {
                        Toast.makeText(context, R.string.radar_open_failed, Toast.LENGTH_SHORT).show()
                    }
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.radar_fullscreen))
        }
    }
    Text(
        text = stringResource(R.string.radar_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal val RadarTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun zoneOf(timezoneId: String?): ZoneId = runCatching { timezoneId?.let { ZoneId.of(it) } }.getOrNull() ?: ZoneId.systemDefault()

internal fun formatRadarTime(
    epochSec: Long,
    timezoneId: String? = null,
): String =
    Instant
        .ofEpochSecond(epochSec)
        .atZone(zoneOf(timezoneId))
        .format(RadarTimeFormatter)

/**
 * 雷达时间轴 TTL 缓存的手动刷新策略：
 * 手动刷新只对触发它的那一次加载生效（消费即归零），换城时未消费的标记作废。
 * 这样换城后的重新加载会重新走 TTL 缓存，不会因为 refreshKey 长期驻留而永久绕过缓存。
 */
internal class RadarTimelineLoadPolicy {
    private var cityKey: String? = null
    private var manualRefreshPending = false

    fun onCity(cityKey: String) {
        if (this.cityKey != cityKey) {
            this.cityKey = cityKey
            manualRefreshPending = false
        }
    }

    fun requestManualRefresh() {
        manualRefreshPending = true
    }

    /** 本次加载是否应绕过 TTL 缓存；一次性标记，读取后归零。 */
    fun consumeBypassCache(): Boolean {
        val bypass = manualRefreshPending
        manualRefreshPending = false
        return bypass
    }
}

/**
 * 雷达时间轴短 TTL 缓存（进程内单例）：
 * 重新进入今天页 / 关闭浮层后直接复用既有帧，不再每次请求 RainViewer 并重播扫描；
 * 帧图片本身仍由 [RadarMapRepository] 的 LRU 负责。用户手动刷新绕过缓存。
 */
internal class RadarTimelineCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val frames: List<RadarFrame>,
        val fetchedAtMs: Long,
    )

    private val lock = Any()

    /** accessOrder=true：最近使用的城市优先保留，来回切城不丢 */
    private val entries =
        object : LinkedHashMap<String, Entry>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
        }

    fun get(
        latitude: Double,
        longitude: Double,
        zoom: Int,
    ): List<RadarFrame>? =
        synchronized(lock) {
            val key = key(latitude, longitude, zoom)
            val entry = entries[key] ?: return null
            if (nowMs() - entry.fetchedAtMs > ttlMs) {
                entries.remove(key)
                return null
            }
            entry.frames
        }

    fun put(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        frames: List<RadarFrame>,
    ) {
        synchronized(lock) {
            entries[key(latitude, longitude, zoom)] = Entry(frames, nowMs())
        }
    }

    private fun key(
        latitude: Double,
        longitude: Double,
        zoom: Int,
    ): String = String.format(Locale.US, "%.4f,%.4f|%d", latitude, longitude, zoom)

    companion object {
        /** 短临时间轴新鲜度与重进体验的折中：10 分钟内直接复用。 */
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 4
        val shared = RadarTimelineCache()
    }
}
