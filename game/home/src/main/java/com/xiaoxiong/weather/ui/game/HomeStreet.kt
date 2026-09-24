package com.xiaoxiong.weather.ui.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.resillustrations.R as IllustR

/** 街上可逛的点位（P0：便利店 / 茶馆 / 车站）。 */
enum class StreetSpot(
    @param:DrawableRes val artRes: Int,
    @param:StringRes val nameRes: Int,
    @param:StringRes val tipRes: Int,
    @param:StringRes val tipNightRes: Int,
) {
    CONVENIENCE(
        IllustR.drawable.ic_sun_bear_evening_convenience,
        R.string.bear_home_street_spot_convenience,
        R.string.bear_home_street_tip_convenience,
        R.string.bear_home_street_tip_convenience_night,
    ),
    TEAHOUSE(
        IllustR.drawable.ic_sun_bear_tea_tea,
        R.string.bear_home_street_spot_teahouse,
        R.string.bear_home_street_tip_teahouse,
        R.string.bear_home_street_tip_teahouse_night,
    ),
    STATION(
        IllustR.drawable.ic_sun_bear_weekend_station,
        R.string.bear_home_street_spot_station,
        R.string.bear_home_street_tip_station,
        R.string.bear_home_street_tip_station_night,
    ),
}

/**
 * 小熊的街：屋外一层。点位列表；点进某家看插画旁白。
 */
@Composable
internal fun HomeStreet(
    isDay: Boolean?,
    selected: StreetSpot?,
    onSelect: (StreetSpot) -> Unit,
) {
    if (selected != null) {
        HomeStreetSpot(spot = selected, isDay = isDay)
    } else {
        HomeStreetHub(isDay = isDay, onSelect = onSelect)
    }
}

@Composable
private fun HomeStreetHub(
    isDay: Boolean?,
    onSelect: (StreetSpot) -> Unit,
) {
    val night = isDay == false
    val intro =
        if (night) {
            R.string.bear_home_street_intro_night
        } else {
            R.string.bear_home_street_intro_day
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 72.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(intro),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5D4037),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        items(StreetSpot.entries, key = { it.name }) { spot ->
            StreetSpotCard(
                spot = spot,
                isNight = night,
                onClick = { onSelect(spot) },
            )
        }
    }
}

@Composable
private fun StreetSpotCard(
    spot: StreetSpot,
    isNight: Boolean,
    onClick: () -> Unit,
) {
    val status =
        remember(spot, isNight) {
            streetSpotStatusRes(spot, isNight)
        }
    // 街点卡：改用公共暖纸面板。与原实现不同——原卡片没有按下反馈，
    // 这里用 GameControl 统一提供按压缩放（只改绘制不参与布局，不影响可点区域）
    GameControl(
        onClick = onClick,
        style =
            GameControlStyle(
                containerColor = WarmPaperPanelStyle.containerColor,
                contentColor = WarmPaperPanelStyle.contentColor,
                borderColor = WarmPaperPanelStyle.borderColor,
                shape = WarmPaperPanelStyle.shape,
                shadowElevation = WarmPaperPanelStyle.shadowElevation,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = rememberGameImage(spot.artRes, 512),
                contentDescription = stringResource(spot.nameRes),
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = stringResource(spot.nameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8D6E63),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.bear_home_street_enter),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE65100),
            )
        }
    }
}

@Composable
private fun HomeStreetSpot(
    spot: StreetSpot,
    isDay: Boolean?,
) {
    val night = isDay == false
    val tip = if (night) spot.tipNightRes else spot.tipRes
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFFFF6E8)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = rememberGameImage(spot.artRes, 512),
            contentDescription = stringResource(spot.nameRes),
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(spot.nameRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4E342E),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(tip),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5D4037),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@StringRes
internal fun streetSpotStatusRes(
    spot: StreetSpot,
    isNight: Boolean,
): Int =
    when (spot) {
        StreetSpot.CONVENIENCE ->
            if (isNight) {
                R.string.bear_home_street_status_open_late
            } else {
                R.string.bear_home_street_status_open
            }
        StreetSpot.TEAHOUSE ->
            if (isNight) {
                R.string.bear_home_street_status_closing
            } else {
                R.string.bear_home_street_status_open
            }
        StreetSpot.STATION ->
            if (isNight) {
                R.string.bear_home_street_status_last_buses
            } else {
                R.string.bear_home_street_status_open
            }
    }
