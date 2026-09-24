package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import java.util.Locale

/** 商店道具卡片：点击购买并立即生效。 */
@Composable
private fun FarmShopItemCard(
    item: FarmShopItem,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val (interactionSource, pressModifier) = rememberFarmPress()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = pressModifier,
        shape = RoundedCornerShape(14.dp),
        color = if (affordable) farmScheme().secondaryContainer else farmScheme().surfaceVariant,
        border = BorderStroke(1.dp, if (affordable) farmScheme().outlineVariant else farmScheme().outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.icon, fontSize = 22.sp)
            Text(
                stringResource(item.nameRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = farmScheme().onSurface,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.bear_farm_item_buy, item.price),
                style = MaterialTheme.typography.labelSmall,
                color = if (affordable) farmScheme().onSecondaryContainer else Color(0xFFB09B80),
            )
        }
    }
}

@Composable
internal fun FarmShopDialog(
    farm: BearFarm,
    onDismiss: () -> Unit,
) {
    val categories =
        remember {
            farmCropCategories()
        }
    var selectedCat by remember { mutableStateOf<CropCategory?>(null) }
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
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = farmScheme().surface),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_farm_shop_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = farmScheme().onSurface,
                )
                Text(
                    stringResource(
                        R.string.bear_farm_shop_meta,
                        // 「累计收获」用图鉴口径（跨周目），本季计数在开新一季后会清零
                        farm.lifetimeHarvests,
                        farm.unlocked.size,
                        FARM_CROPS.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = farmScheme().onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.bear_farm_item_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6D4C41),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FARM_SHOP_ITEMS.forEach { item ->
                        FarmShopItemCard(
                            item = item,
                            affordable = farm.coins >= item.price,
                            onClick = {
                                when (item.id) {
                                    "rain_can" -> farm.rainAll()
                                    "bug_spray" -> farm.bugAll()
                                    "weed_killer" -> farm.weedAll()
                                }
                            },
                        )
                    }
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
                                color = if (sel) farmScheme().onSecondary else Color(0xFF6D4C41),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    itemsIndexed(filtered, key = { _, c -> c.id }) { _, crop ->
                        val owned = crop.id in farm.unlocked
                        val unlockPrice = farmUnlockPrice(crop)
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (owned) {
                                            farmScheme().secondaryContainer
                                        } else {
                                            Color(0xFFFFF3E0).copy(alpha = 0.85f)
                                        },
                                ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .alpha(if (owned) 1f else 0.92f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    bitmap = rememberGameImage(FarmCropArt.ripe(crop), 256),
                                    contentDescription = cropName(crop),
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    cropName(crop),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = farmScheme().onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.bear_farm_shop_crop_meta,
                                        String.format(Locale.US, "%.0f", crop.totalHours),
                                        crop.sellPrice,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.sp,
                                    color = farmScheme().onSurfaceVariant,
                                )
                                crop.preferredWeather?.let { preferred ->
                                    Text(
                                        stringResource(
                                            R.string.bear_farm_crop_prefers,
                                            stringResource(farmWeatherNameRes(preferred)),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp,
                                        color = Color(0xFF6D4C41),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (owned) {
                                    Text(
                                        stringResource(R.string.bear_farm_shop_owned),
                                        color = farmScheme().onSecondaryContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                } else {
                                    Button(
                                        onClick = { farm.buyUnlock(crop.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.bear_farm_shop_unlock_btn, unlockPrice),
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.bear_farm_close_shop)) }
            }
        }
    }
}
