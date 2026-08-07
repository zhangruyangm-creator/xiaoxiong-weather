package com.xiaoxiong.weather.ui.weather

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.RadarFrame
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.ui.theme.AccentCoral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun LiveRadarPane(
    city: City,
    repository: RadarMapRepository,
    timezoneId: String? = null
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val appStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val zoom = RadarMapRepository.DEFAULT_ZOOM

    // 切城 / 刷新：底图与帧列表并行；先出最新一帧再后台预取其余
    LaunchedEffect(city.id, city.latitude, city.longitude, refreshKey) {
        loading = true
        errorRes = null
        playing = false
        currentBitmap = null
        frames = emptyList()
        repository.clearRadarFrameCache()
        try {
            coroutineScope {
                val basemapJob = async {
                    repository.loadBasemap(city.latitude, city.longitude, zoom)
                }
                val framesJob = async {
                    repository.fetchPastRadarFrames(city.latitude, city.longitude, zoom)
                }
                basemap = basemapJob.await()
                val list = framesJob.await()
                frames = list
                frameIndex = (list.lastIndex).coerceAtLeast(0)
                if (list.isEmpty()) {
                    loading = false
                    errorRes = R.string.radar_empty
                    return@coroutineScope
                }
                // 只等最新一帧，马上结束转圈
                val latest = list.last()
                currentBitmap = repository.loadImage(latest.imageUrl)
                loading = false
                playing = true
                // 其余帧后台并行预取，不挡首屏
                launch {
                    val rest = list.dropLast(1).map { it.imageUrl }
                    repository.prefetchImages(rest)
                }
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
        refreshKey += 1
    }

    LaunchedEffect(frames, frameIndex) {
        val frame = frames.getOrNull(frameIndex) ?: run {
            currentBitmap = null
            return@LaunchedEffect
        }
        val cachedHint = currentBitmap
        if (cachedHint == null) imageLoading = true
        currentBitmap = repository.loadImage(frame.imageUrl)
        imageLoading = false
    }

    LaunchedEffect(playing, frames, appStarted) {
        if (!playing || !appStarted || frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(500)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B2A38))
    ) {
        when {
            loading -> {
                RadarScanLoadingOverlay(
                    cityName = city.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
            errorRes != null && frames.isEmpty() && basemap == null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(errorRes ?: R.string.radar_load_failed_generic),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { reload() }) {
                        Text(
                            stringResource(R.string.label_retry),
                            color = MaterialTheme.colorScheme.primary
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
                        contentScale = ContentScale.Crop
                    )
                }
                currentBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = stringResource(R.string.cd_radar_echo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.88f
                    )
                }
                if (imageLoading && currentBitmap == null) {
                    RadarScanLoadingOverlay(
                        cityName = city.name,
                        compact = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 城市定位点（雷达图中心即所选城市）
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AccentCoral.copy(alpha = 0.28f))
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentCoral)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
                Text(
                    text = city.name,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                frames.getOrNull(frameIndex)?.let { frame ->
                    Text(
                        text = formatRadarTime(frame.timeEpochSec, timezoneId),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                val overlayError = errorRes
                if (overlayError != null && frames.isEmpty()) {
                    Text(
                        text = stringResource(overlayError),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    if (frames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledIconButton(
                onClick = { playing = !playing },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.cd_pause else R.string.cd_play
                    )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            FilledIconButton(
                onClick = { reload() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_radar)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
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
                valueRange = 0f..(frames.lastIndex.coerceAtLeast(0).toFloat()),
                steps = (frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.radar_hint_center),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = {
                val url = RadarMapRepository.windyRadarUrl(city.latitude, city.longitude)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.radar_fullscreen))
        }
    }
    Text(
        text = stringResource(R.string.radar_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal val RadarTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun zoneOf(timezoneId: String?): ZoneId =
    runCatching { timezoneId?.let { ZoneId.of(it) } }.getOrNull() ?: ZoneId.systemDefault()

internal fun formatRadarTime(epochSec: Long, timezoneId: String? = null): String =
    Instant.ofEpochSecond(epochSec)
        .atZone(zoneOf(timezoneId))
        .format(RadarTimeFormatter)
