package com.xiaoxiong.weather.ui.weather

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.net.toUri
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.SatelliteCloudMap
import com.xiaoxiong.weather.ui.theme.AccentCoral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 真彩云图半透明，让底下 CARTO 底图透出道路与地名 */
private const val CLOUD_OVERLAY_ALPHA = 0.78f

@Composable
internal fun SatelliteCloudPane(
    city: City,
    repository: RadarMapRepository,
) {
    var cloud by remember(city.id, city.latitude, city.longitude) {
        mutableStateOf<SatelliteCloudMap?>(null)
    }
    var basemap by remember(city.id, city.latitude, city.longitude) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember(city.id) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val zoom = RadarMapRepository.CLOUD_ZOOM
    val size = RadarMapRepository.CLOUD_IMAGE_SIZE

    LaunchedEffect(city.id, city.latitude, city.longitude, refreshKey) {
        loading = true
        errorRes = null
        try {
            coroutineScope {
                val forceRefresh = refreshKey > 0
                val cloudJob =
                    async {
                        repository.loadSatelliteCloudMap(
                            latitude = city.latitude,
                            longitude = city.longitude,
                            zoom = zoom,
                            size = size,
                            forceRefresh = forceRefresh,
                        )
                    }
                val basemapJob =
                    async {
                        repository.loadBasemap(
                            latitude = city.latitude,
                            longitude = city.longitude,
                            zoom = zoom,
                            size = size,
                        )
                    }
                cloud = cloudJob.await()
                basemap = basemapJob.await()
            }
            loading = false
            if (cloud == null) errorRes = R.string.cloud_empty
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loading = false
            cloud = null
            errorRes = R.string.cloud_load_failed
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF15202B)),
    ) {
        when {
            loading && cloud == null && basemap == null -> {
                RadarScanLoadingOverlay(
                    cityName = city.name,
                    modifier = Modifier.fillMaxSize(),
                    detailRes = R.string.cloud_scan_detail,
                )
            }
            errorRes != null && cloud == null -> {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(errorRes ?: R.string.cloud_load_failed),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { refreshKey += 1 }) {
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
                cloud?.let { map ->
                    Image(
                        bitmap = map.image,
                        contentDescription = stringResource(R.string.cd_cloud_map),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = if (basemap != null) CLOUD_OVERLAY_ALPHA else 1f,
                    )
                }
                if (loading && cloud == null) {
                    RadarScanLoadingOverlay(
                        cityName = city.name,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                        detailRes = R.string.cloud_scan_detail,
                    )
                }
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
                cloud?.let { map ->
                    Text(
                        text = stringResource(R.string.cloud_date, map.dateUtc),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = { refreshKey += 1 },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_cloud),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.cloud_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                val url = RadarMapRepository.windySatelliteUrl(city.latitude, city.longitude)
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
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
        text = stringResource(R.string.cloud_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
