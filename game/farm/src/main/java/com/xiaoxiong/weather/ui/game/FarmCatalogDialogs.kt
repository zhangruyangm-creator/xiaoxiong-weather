package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import kotlin.math.roundToInt

/** 解锁新种子的弹卡：大图 + 属性 + 入场动画。 */
@Composable
internal fun FarmUnlockDialog(
    crop: CropDef,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.78f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = farmScheme().surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.bear_farm_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = farmScheme().onSurface,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(contentAlignment = Alignment.Center) {
                    // 作物身后的柔光
                    Box(
                        modifier =
                            Modifier
                                .size(112.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFFF59D).copy(alpha = 0.55f),
                                            Color(0xFFFFF59D).copy(alpha = 0f),
                                        ),
                                    ),
                                    CircleShape,
                                ),
                    )
                    Image(
                        bitmap = rememberGameImage(FarmCropArt.ripe(crop), 512),
                        contentDescription = cropName(crop),
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    cropName(crop),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = farmScheme().onSurface,
                )
                Text(
                    stringResource(crop.category.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = farmScheme().onSurfaceVariant,
                )
                crop.preferredWeather?.let { preferred ->
                    Text(
                        stringResource(
                            R.string.bear_farm_crop_prefers,
                            stringResource(farmWeatherNameRes(preferred)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = farmScheme().onSurfaceVariant,
                    )
                }
                val description = cropDescription(crop)
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = farmScheme().onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_growth, crop.totalHours.roundToInt()),
                    )
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_cost, crop.seedCost),
                    )
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_sell, crop.sellPrice),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = farmScheme().secondary,
                            contentColor = farmScheme().onSecondary,
                        ),
                ) {
                    Text(
                        stringResource(R.string.bear_farm_unlock_gotit),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FarmUnlockInfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = farmScheme().surfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = farmScheme().onSurfaceVariant,
        )
    }
}

/** 作物图鉴：收集进度 + 每作物累计收获次数 + 新一季入口。 */
@Composable
internal fun FarmCatalogDialog(
    farm: BearFarm,
    onDismiss: () -> Unit,
) {
    val categories =
        remember {
            farmCropCategories()
        }
    var selectedCat by remember { mutableStateOf<CropCategory?>(null) }
    var selectedCrop by remember { mutableStateOf<CropDef?>(null) }
    val filtered =
        remember(selectedCat) {
            if (selectedCat == null) FARM_CROPS else FARM_CROPS.filter { it.category == selectedCat }
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.94f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = farmScheme().surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.bear_farm_catalog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = farmScheme().onSurface,
                        )
                        Text(
                            stringResource(
                                R.string.bear_farm_catalog_meta,
                                farm.currentSeason,
                                farm.lifetimeHarvests,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = farmScheme().onSurfaceVariant,
                        )
                    }
                    if (farm.completedSeasons > 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = farmScheme().secondaryContainer,
                        ) {
                            Text(
                                stringResource(R.string.bear_farm_catalog_bonus, seasonGrowthPercent(farm.completedSeasons)),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = farmScheme().onSecondaryContainer,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.bear_farm_catalog_progress, farm.collectedCount, FARM_CROPS.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = farmScheme().onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { farm.collectedCount.toFloat() / FARM_CROPS.size },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        color = farmScheme().secondary,
                        trackColor = farmScheme().surfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        val label =
                            cat?.let { stringResource(it.labelRes()) }
                                ?: stringResource(R.string.bear_farm_shop_cat_all)
                        val sel = selectedCat == cat
                        Surface(
                            onClick = { selectedCat = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) farmScheme().secondary else farmScheme().surface,
                            border = BorderStroke(1.dp, farmScheme().outlineVariant),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) farmScheme().onSecondary else farmScheme().onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(340.dp),
                ) {
                    itemsIndexed(filtered, key = { _, c -> c.id }) { _, crop ->
                        val count = farm.harvestCounts[crop.id] ?: 0
                        val owned = count > 0
                        Card(
                            onClick = { selectedCrop = crop },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = if (owned) farmScheme().secondaryContainer else farmScheme().surfaceVariant,
                                ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        bitmap = rememberGameImage(FarmCropArt.ripe(crop), 256),
                                        contentDescription = cropName(crop),
                                        modifier =
                                            Modifier
                                                .size(42.dp)
                                                .alpha(if (owned) 1f else 0.32f),
                                        contentScale = ContentScale.Fit,
                                    )
                                    if (!owned) {
                                        Text(
                                            stringResource(R.string.bear_farm_catalog_unknown),
                                            color = farmScheme().onSurfaceVariant,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(
                                    cropName(crop),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = farmScheme().onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (owned) {
                                    Text(
                                        stringResource(R.string.bear_farm_catalog_count, count),
                                        fontSize = 10.sp,
                                        color = farmScheme().onSecondaryContainer,
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.bear_farm_catalog_locked),
                                        fontSize = 10.sp,
                                        color = farmScheme().onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.bear_farm_season_progress, farm.seasonCollectedCount, farm.seasonGoal),
                    style = MaterialTheme.typography.labelMedium,
                    color = farmScheme().onSurfaceVariant,
                )
                if (farm.allCollected) {
                    Text(
                        stringResource(R.string.bear_farm_all_collected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = farmScheme().onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (farm.seasonReady) {
                        Text(
                            stringResource(R.string.bear_farm_new_season_ready),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = farmScheme().onSecondaryContainer,
                        )
                        Button(
                            onClick = { farm.showSeasonConfirm = true },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = farmScheme().secondary,
                                    contentColor = farmScheme().onSecondary,
                                ),
                        ) {
                            Text(
                                stringResource(R.string.bear_farm_new_season),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.bear_farm_new_season_hint, farm.seasonGoal - farm.seasonCollectedCount),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = farmScheme().onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.bear_farm_close_shop))
                    }
                }
            }
        }
        selectedCrop?.let { crop ->
            FarmCatalogCropDetail(
                crop = crop,
                count = farm.harvestCounts[crop.id] ?: 0,
                onDismiss = { selectedCrop = null },
            )
        }
    }
}

/** 图鉴点击放大：作物大图 + 生长/价格/收获次数说明。 */
@Composable
private fun FarmCatalogCropDetail(
    crop: CropDef,
    count: Int,
    onDismiss: () -> Unit,
) {
    val owned = count > 0
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.78f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = farmScheme().surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.bear_farm_catalog_detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = farmScheme().onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(contentAlignment = Alignment.Center) {
                    // 大图身后的柔光
                    Box(
                        modifier =
                            Modifier
                                .size(140.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFFF59D).copy(alpha = 0.55f),
                                            Color(0xFFFFF59D).copy(alpha = 0f),
                                        ),
                                    ),
                                    CircleShape,
                                ),
                    )
                    Image(
                        bitmap = rememberGameImage(FarmCropArt.ripe(crop), 512),
                        contentDescription = cropName(crop),
                        modifier =
                            Modifier
                                .size(120.dp)
                                .alpha(if (owned) 1f else 0.45f),
                        contentScale = ContentScale.Fit,
                    )
                    if (!owned) {
                        Text(
                            stringResource(R.string.bear_farm_catalog_unknown),
                            color = farmScheme().onSurfaceVariant,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    cropName(crop),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = farmScheme().onSurface,
                )
                Text(
                    stringResource(crop.category.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = farmScheme().onSurfaceVariant,
                )
                crop.preferredWeather?.let { preferred ->
                    Text(
                        stringResource(
                            R.string.bear_farm_crop_prefers,
                            stringResource(farmWeatherNameRes(preferred)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = farmScheme().onSurfaceVariant,
                    )
                }
                val description = cropDescription(crop)
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = farmScheme().onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_growth, crop.totalHours.roundToInt()),
                    )
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_cost, crop.seedCost),
                    )
                    FarmUnlockInfoChip(
                        text = stringResource(R.string.bear_farm_unlock_sell, crop.sellPrice),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (owned) {
                    Text(
                        stringResource(R.string.bear_farm_catalog_count, count),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = farmScheme().onSecondaryContainer,
                    )
                } else {
                    Text(
                        stringResource(R.string.bear_farm_catalog_locked),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = farmScheme().onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = farmScheme().secondary,
                            contentColor = farmScheme().onSecondary,
                        ),
                ) {
                    Text(
                        stringResource(R.string.bear_farm_close_shop),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 新一季确认：说明重置/保留内容。 */
@Composable
internal fun NewSeasonConfirmDialog(
    farm: BearFarm,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.82f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = farmScheme().surface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.bear_farm_new_season_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = farmScheme().onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.bear_farm_new_season_msg, seasonGrowthPercent(farm.completedSeasons + 1)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = farmScheme().onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.bear_farm_new_season_cancel))
                    }
                    Button(
                        onClick = { farm.startNewSeason() },
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = farmScheme().secondary,
                                contentColor = farmScheme().onSecondary,
                            ),
                    ) {
                        Text(
                            stringResource(R.string.bear_farm_new_season_confirm),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
