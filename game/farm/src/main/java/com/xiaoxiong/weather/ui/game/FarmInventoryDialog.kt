package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import kotlin.math.roundToInt

/** 背包：查看收获的果实，逐种或一键卖出换金币。 */
@Composable
internal fun FarmInventoryDialog(
    farm: BearFarm,
    weather: FarmWeather,
    onDismiss: () -> Unit,
) {
    val bonus = bestSellBonusOf(farm.inventory.keys, weather)
    val entries = farm.inventory.entries.sortedBy { it.key }
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
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.bear_farm_inventory_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = farmScheme().onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = farmScheme().surfaceVariant.copy(alpha = 0.92f),
                    ) {
                        Text(
                            stringResource(R.string.bear_farm_coins, farm.coins),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            color = farmScheme().onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.bear_farm_inventory_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = farmScheme().onSurfaceVariant,
                )
                if (bonus > 1f) {
                    Text(
                        stringResource(R.string.bear_farm_inventory_bonus, ((bonus - 1f) * 100).roundToInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = farmScheme().onSecondaryContainer,
                    )
                } else {
                    Text(
                        stringResource(R.string.bear_farm_inventory_bonus_none),
                        style = MaterialTheme.typography.labelMedium,
                        color = farmScheme().onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.bear_farm_inventory_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = farmScheme().onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(340.dp),
                    ) {
                        items(entries, key = { it.key }) { (cropId, count) ->
                            val crop = cropById(cropId) ?: return@items
                            FarmInventoryCard(
                                crop = crop,
                                count = count,
                                unitPrice = sellPriceOf(crop, weather),
                                onSell = { farm.sellAll(cropId, weather) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.bear_farm_inventory_total, entries.sumOf { it.value }),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = farmScheme().onSurfaceVariant,
                        )
                        Button(
                            onClick = { farm.sellAllInventory(weather) },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = farmScheme().secondary,
                                    contentColor = farmScheme().onSecondary,
                                ),
                        ) {
                            Text(
                                stringResource(R.string.bear_farm_inventory_sell_all),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.bear_farm_close_shop)) }
            }
        }
    }
}

@Composable
private fun FarmInventoryCard(
    crop: CropDef,
    count: Int,
    unitPrice: Int,
    onSell: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = farmScheme().secondaryContainer,
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
            Image(
                bitmap = rememberGameImage(FarmCropArt.ripe(crop), 256),
                contentDescription = cropName(crop),
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                cropName(crop),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = farmScheme().onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.bear_farm_inventory_count, count),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = farmScheme().onSecondaryContainer,
            )
            Text(
                stringResource(R.string.bear_farm_inventory_price, unitPrice),
                fontSize = 10.sp,
                color = farmScheme().onSurfaceVariant,
            )
            Button(
                onClick = onSell,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = farmScheme().secondary,
                        contentColor = farmScheme().onSecondary,
                    ),
            ) {
                Text(
                    stringResource(R.string.bear_farm_inventory_sell_one),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
