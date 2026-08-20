package com.xiaoxiong.weather.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.ui.weather.SunPathPhase
import com.xiaoxiong.weather.ui.weather.sunPathBearMoment
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sin

private enum class HomeRoom {
    HUB,
    BEDROOM,
    KITCHEN,
    PLAYROOM,
    CLOSET,
    BALCONY,
    SUMMARY,
}

/**
 * 小熊的家：世界客厅枢纽。口令 [EASTER_EGG_BEAR_HOME] 打开。
 * 房间子页在浮层内导航；游乐间按 [otherGames]（装配方注入，随 `-Pgames` 裁剪增减）显隐入口。
 */
@Composable
fun BearHomeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    otherGames: List<GameEntry> = emptyList(),
    onOpenGame: (GameEntry) -> Unit = {},
) {
    var room by remember { mutableStateOf(HomeRoom.HUB) }
    var showNowDialog by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
            today = LocalDate.now()
        }
    }
    val phase = remember(isDay) { isDay?.let { SunPathPhase(isDay = it, progress = 0.5f) } }
    // 天气未加载（isDay==null）时按白天处理：片刻兜底是晨间活动，Hub 主题保持一致
    val isDayTheme = isDay != false
    val moment = remember(phase, today, now) { sunPathBearMoment(phase, today, now) }
    val bearRes = remember(moment, today) { moment.bearResFor(today) }
    val caption = stringResource(moment.captionResFor(today))

    BackHandler {
        if (room == HomeRoom.HUB) onExit() else room = HomeRoom.HUB
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF6E8)),
    ) {
        // 内容全屏铺满；顶栏叠在上方，插画贴顶贴边
        when (room) {
            HomeRoom.HUB ->
                HomeHub(
                    bearRes = bearRes,
                    isDay = isDayTheme,
                    onOpenBedroom = { room = HomeRoom.BEDROOM },
                    onOpenKitchen = { room = HomeRoom.KITCHEN },
                    onOpenPlayroom = { room = HomeRoom.PLAYROOM },
                    onOpenCloset = { room = HomeRoom.CLOSET },
                    onOpenBalcony = { room = HomeRoom.BALCONY },
                    onOpenLiving = { showNowDialog = true },
                    onOpenSummary = { room = HomeRoom.SUMMARY },
                )
            HomeRoom.BEDROOM -> HomeBedroom(date = today, time = now)
            HomeRoom.KITCHEN -> HomeKitchen()
            HomeRoom.PLAYROOM ->
                HomePlayroom(
                    games = otherGames,
                    onOpenGame = onOpenGame,
                )
            HomeRoom.CLOSET -> HomeCloset()
            HomeRoom.BALCONY -> HomeBalcony(sceneCode = sceneCode, isDay = isDay)
            HomeRoom.SUMMARY -> HomeSummary(sceneCode = sceneCode, isDay = isDay, date = today, time = now)
        }
        HomeTopBar(
            title =
                when (room) {
                    HomeRoom.HUB -> stringResource(R.string.bear_home_title)
                    HomeRoom.BEDROOM -> stringResource(R.string.bear_home_room_bedroom)
                    HomeRoom.KITCHEN -> stringResource(R.string.bear_home_room_kitchen)
                    HomeRoom.PLAYROOM -> stringResource(R.string.bear_home_room_playroom)
                    HomeRoom.CLOSET -> stringResource(R.string.bear_home_room_closet)
                    HomeRoom.BALCONY -> stringResource(R.string.bear_home_room_balcony)
                    HomeRoom.SUMMARY -> stringResource(R.string.bear_home_summary_stub)
                },
            showBack = room != HomeRoom.HUB,
            onBack = { room = HomeRoom.HUB },
            onClose = onExit,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
        )
    }

    if (showNowDialog) {
        AlertDialog(
            onDismissRequest = { showNowDialog = false },
            confirmButton = {
                TextButton(onClick = { showNowDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.bear_home_room_living)) },
            text = { Text(stringResource(R.string.bear_home_now_doing, caption)) },
        )
    }
}

@Composable
private fun HomeTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xE6FFF6E8),
                            Color(0x00FFF6E8),
                        ),
                    ),
                ).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.bear_home_back),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_game))
        }
    }
}

@Composable
private fun HomeHub(
    bearRes: Int,
    isDay: Boolean,
    onOpenBedroom: () -> Unit,
    onOpenKitchen: () -> Unit,
    onOpenPlayroom: () -> Unit,
    onOpenCloset: () -> Unit,
    onOpenBalcony: () -> Unit,
    onOpenLiving: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    val sky =
        if (isDay) {
            Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFFFFF6E8)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFFFFF6E8)))
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(sky),
    ) {
        // 宽度顶满、贴顶；FillWidth 保证不留左右边距，高度随图比例
        Image(
            painter = painterResource(R.drawable.ic_bear_home_hub),
            contentDescription = stringResource(R.string.bear_home_title),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth,
        )
        // 热区与插画同宽同顶：Hub 为 9:16 竖图，热区按同一比例叠在画面上
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .align(Alignment.TopCenter),
        ) {
            // 上层：卧室、客厅（窗户顶部窄条进阳台）、厨房
            Row(modifier = Modifier.weight(1.2f).fillMaxWidth()) {
                HotZone(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.bear_home_room_bedroom),
                    onClick = onOpenBedroom,
                )
                // 客厅占主要面积；阳台在客厅上方。顶部留白让阳台点击区下移，
                // 使其标签大致落在「小熊的家」标题与客厅标签之间的中线附近。
                Column(modifier = Modifier.weight(1.3f).fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(1.05f).fillMaxWidth())
                    HotZone(
                        modifier = Modifier.weight(0.28f).fillMaxWidth(),
                        label = stringResource(R.string.bear_home_room_balcony),
                        onClick = onOpenBalcony,
                    )
                    HotZone(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = stringResource(R.string.bear_home_room_living),
                        onClick = onOpenLiving,
                    )
                }
                HotZone(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.bear_home_room_kitchen),
                    onClick = onOpenKitchen,
                )
            }
            // 下层：游乐间、储物间
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HotZone(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.bear_home_room_playroom),
                    onClick = onOpenPlayroom,
                )
                HotZone(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.bear_home_room_closet),
                    onClick = onOpenCloset,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x00FFF6E8), Color(0xFFFFF6E8)),
                        ),
                    ).padding(top = 32.dp, bottom = 16.dp)
                    .clickable(onClick = onOpenSummary),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(bearRes),
                contentDescription = stringResource(R.string.bear_home_summary_stub),
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = stringResource(R.string.bear_home_summary_stub),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8D6E63),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HotZone(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.72f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF5D4037),
            )
        }
    }
}

@Composable
private fun HomeRoomHero(
    @androidx.annotation.DrawableRes artRes: Int,
    contentDescription: String,
) {
    // 宽度顶满、贴顶；FillWidth 按原图比例撑开高度，不裁切、不留左右边
    Image(
        painter = painterResource(artRes),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
private fun HomeBedroom(
    date: LocalDate,
    time: LocalTime,
) {
    val routineDay = homeRoutineDay(date, time)
    val slots = remember(routineDay) { homeRoutineSlots(routineDay) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HomeRoomHero(
                artRes = R.drawable.ic_bear_home_bedroom,
                contentDescription = stringResource(R.string.bear_home_room_bedroom),
            )
        }
        item {
            Text(
                text = stringResource(R.string.bear_home_routine_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(slots, key = { "${it.moment.name}-${it.startMinute}" }) { slot ->
            val status = homeRoutineStatus(slot, time)
            val highlight = status == HomeRoutineStatus.NOW
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (highlight) Color(0xFFFFE0B2) else Color.White,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(slot.moment.bearResFor(routineDay)),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = stringResource(slot.moment.captionResFor(routineDay)),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatHomeMinuteRange(slot.startMinute, slot.endMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8D6E63),
                        )
                    }
                    Text(
                        text = stringResource(homeRoutineStatusLabel(status)),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (status) {
                                HomeRoutineStatus.NOW -> Color(0xFFE65100)
                                HomeRoutineStatus.DONE -> Color(0xFF9E9E9E)
                                HomeRoutineStatus.SOON -> Color(0xFF5D4037)
                            },
                    )
                }
            }
        }
    }
}

/** 今天小结：天气概况 + 今日作息时间线。 */
@Composable
private fun HomeSummary(
    sceneCode: Int?,
    isDay: Boolean?,
    date: LocalDate,
    time: LocalTime,
) {
    val weatherLabel =
        remember(sceneCode, isDay) {
            val code = sceneCode ?: return@remember "—"
            // 复用统一天气分类口径（与阳台/其它小游戏一致），避免各组件映射漂移
            when (farmWeatherFromScene(code, isDay)) {
                FarmWeather.STORMY -> "雷暴"
                FarmWeather.SNOWY -> "下雪"
                FarmWeather.FOGGY -> "大雾"
                FarmWeather.RAINY ->
                    when (rainStrengthOf(code)) {
                        RainStrength.LIGHT -> "小雨"
                        else -> "大雨"
                    }
                FarmWeather.SUNNY -> if (isDay == false) "晴朗夜晚" else "晴天"
                FarmWeather.CLOUDY -> "多云"
            }
        }
    val routineDay = homeRoutineDay(date, time)
    val slots = remember(routineDay) { homeRoutineSlots(routineDay) }
    val doneCount =
        remember(slots, time) {
            slots.count { homeRoutineStatus(it, time) == HomeRoutineStatus.DONE }
        }
    val currentMoment =
        remember(slots, time) {
            slots.firstOrNull { homeRoutineStatus(it, time) == HomeRoutineStatus.NOW }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 天气概况卡片
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.bear_home_summary_weather_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = weatherLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isDay != false) "☀" else "🌙",
                            fontSize = 28.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.bear_home_summary_progress,
                                doneCount,
                                slots.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8D6E63),
                    )
                    if (currentMoment != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.bear_home_summary_now_doing,
                                    stringResource(currentMoment.moment.captionResFor(routineDay)),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        // 今日作息时间线
        item {
            Text(
                text = stringResource(R.string.bear_home_summary_timeline_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(slots, key = { "${it.moment.name}-${it.startMinute}" }) { slot ->
            val status = homeRoutineStatus(slot, time)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color =
                    when (status) {
                        HomeRoutineStatus.NOW -> Color(0xFFFFE0B2)
                        HomeRoutineStatus.DONE -> Color(0xFFF5F5F5)
                        HomeRoutineStatus.SOON -> Color.White
                    },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(slot.moment.bearResFor(routineDay)),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            text = stringResource(slot.moment.captionResFor(routineDay)),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatHomeMinuteRange(slot.startMinute, slot.endMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8D6E63),
                        )
                    }
                    Text(
                        text = stringResource(homeRoutineStatusLabel(status)),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (status) {
                                HomeRoutineStatus.NOW -> Color(0xFFE65100)
                                HomeRoutineStatus.DONE -> Color(0xFF9E9E9E)
                                HomeRoutineStatus.SOON -> Color(0xFF5D4037)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeKitchen() {
    var slot by remember { mutableStateOf(HomeMealSlot.BREAKFAST) }
    val groups = remember(slot) { slot.groups() }
    Column(modifier = Modifier.fillMaxSize()) {
        // 横幅插画全宽贴边
        HomeRoomHero(
            artRes = R.drawable.ic_bear_home_kitchen,
            contentDescription = stringResource(R.string.bear_home_room_kitchen),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.bear_home_meal_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeMealSlot.entries.forEach { s ->
                        val selected = s == slot
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) Color(0xFFFFCC80) else Color.White,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clickable { slot = s },
                        ) {
                            Text(
                                text = stringResource(s.titleRes),
                                modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(group.cuisine.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                items(group.dishes) { dish ->
                    MealDexCard(dish = dish)
                }
            }
        }
    }
}

@Composable
private fun MealDexCard(
    dish: HomeMealDish,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(dish.drawableRes),
                contentDescription = stringResource(dish.nameRes),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    text = stringResource(dish.nameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(dish.detailRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8D6E63),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun HomePlayroom(
    games: List<GameEntry>,
    onOpenGame: (GameEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HomeRoomHero(
                artRes = R.drawable.ic_bear_home_playroom,
                contentDescription = stringResource(R.string.bear_home_room_playroom),
            )
        }
        if (games.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.bear_home_playroom_empty),
                    color = Color(0xFF8D6E63),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(games, key = { it.password }) { game ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = { onOpenGame(game) }),
                ) {
                    Text(
                        text = stringResource(game.titleRes),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCloset() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeRoomHero(
            artRes = R.drawable.ic_bear_home_closet,
            contentDescription = stringResource(R.string.bear_home_room_closet),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.bear_home_closet_stub),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bear_home_closet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8D6E63),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── 阳台观景 ──────────────────────────────────────────────────────

/** 阳台天气场景分类：复用游戏共用的 [farmWeatherFromScene] 口径，再细分日夜与粒子。 */
private sealed interface BalconyWeather {
    val weather: FarmWeather

    data class Day(
        override val weather: FarmWeather,
    ) : BalconyWeather

    data class Night(
        override val weather: FarmWeather,
    ) : BalconyWeather

    /** 是否夜晚（决定星空/灯火与暖色小熊）。 */
    val isNight: Boolean
        get() = this is Night
}

/** 由 sceneCode / isDay 映射：先复用统一天气分类，再按日夜细分。 */
private fun balconyWeatherFrom(
    sceneCode: Int?,
    isDay: Boolean?,
): BalconyWeather {
    val weather = farmWeatherFromScene(sceneCode, isDay)
    return if (isDay == false) BalconyWeather.Night(weather) else BalconyWeather.Day(weather)
}

/** 雨/雪类天气需 60fps 粒子动画；晴/多云/雾降频即可省电。 */
private val BalconyWeather.needsHighFps: Boolean
    get() =
        when (weather) {
            FarmWeather.RAINY, FarmWeather.STORMY, FarmWeather.SNOWY -> true
            else -> false
        }

/** 灯光/雨雪等粒子是否存在。 */
private val BalconyWeather.hasParticles: Boolean
    get() =
        when (weather) {
            FarmWeather.RAINY, FarmWeather.STORMY, FarmWeather.SNOWY, FarmWeather.FOGGY -> true
            else -> false
        }

/** 天空渐变色。 */
private fun skyGradient(w: BalconyWeather): List<Color> {
    val night = w.isNight
    return when (w.weather) {
        FarmWeather.SUNNY ->
            if (night) {
                listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF2C3E50))
            } else {
                listOf(Color(0xFF4A90D9), Color(0xFF87CEEB), Color(0xFFB0E0FF))
            }
        FarmWeather.CLOUDY ->
            if (night) {
                listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF4A5568))
            } else {
                listOf(Color(0xFF6B7B8D), Color(0xFF8E9EAB), Color(0xFFB0BEC5))
            }
        FarmWeather.RAINY ->
            if (night) {
                listOf(Color(0xFF1A1A2E), Color(0xFF263238), Color(0xFF37474F))
            } else {
                listOf(Color(0xFF4A5568), Color(0xFF6B7B8D), Color(0xFF8E9EAB))
            }
        FarmWeather.STORMY ->
            listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF2D3748))
        FarmWeather.SNOWY ->
            if (night) {
                listOf(Color(0xFF37474F), Color(0xFF4A5568), Color(0xFF6B7B8D))
            } else {
                listOf(Color(0xFFCBD5E0), Color(0xFFE2E8F0), Color(0xFFF7FAFC))
            }
        FarmWeather.FOGGY ->
            if (night) {
                listOf(Color(0xFF37474F), Color(0xFF4A5568), Color(0xFF6B7B8D))
            } else {
                listOf(Color(0xFFA0AEC0), Color(0xFFCBD5E0), Color(0xFFE2E8F0))
            }
    }
}

/** 远景颜色（城市/山景剪影）。 */
private fun distantColor(w: BalconyWeather): Color {
    val night = w.isNight
    return when (w.weather) {
        FarmWeather.SUNNY ->
            if (night) {
                Color(0xFF1A237E).copy(alpha = 0.8f)
            } else {
                Color(0xFF78909C).copy(alpha = 0.65f)
            }
        FarmWeather.CLOUDY ->
            if (night) {
                Color(0xFF1A237E).copy(alpha = 0.85f)
            } else {
                Color(0xFF455A64).copy(alpha = 0.8f)
            }
        FarmWeather.RAINY ->
            if (night) {
                Color(0xFF101B2D).copy(alpha = 0.85f)
            } else {
                Color(0xFF37474F).copy(alpha = 0.75f)
            }
        FarmWeather.STORMY ->
            Color(0xFF1A1A2E).copy(alpha = 0.9f)
        FarmWeather.SNOWY ->
            if (night) {
                Color(0xFF37474F).copy(alpha = 0.7f)
            } else {
                Color(0xFF90A4AE).copy(alpha = 0.6f)
            }
        FarmWeather.FOGGY ->
            if (night) {
                Color(0xFF37474F).copy(alpha = 0.6f)
            } else {
                Color(0xFF78909C).copy(alpha = 0.5f)
            }
    }
}

/**
 * 阳台观景：天气驱动的全屏场景。
 * 上半部分是天空 + 远景 + 天气粒子，下半部分是阳台栏杆 + 小熊。
 */
@Composable
private fun HomeBalcony(
    sceneCode: Int?,
    isDay: Boolean?,
) {
    val w = remember(sceneCode, isDay) { balconyWeatherFrom(sceneCode, isDay) }
    // 用 Double 累计秒，避免 Float 长期累加后精度饱和导致动画停滞/相位劣化
    var time by remember { mutableStateOf(0.0) }
    LaunchedEffect(w) {
        var last = Long.MIN_VALUE
        var slowTick = 0
        // 以帧时钟刷新：雨/雪/雷暴 60fps；晴/多云/雾每 5 帧更新一次（约 12fps）省电
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt =
                    if (last == Long.MIN_VALUE) {
                        1.0 / 60.0
                    } else {
                        ((now - last) / 1e9).coerceIn(0.0, 0.05)
                    }
                last = now
                slowTick++
                if (w.needsHighFps || slowTick % 5 == 0) {
                    time += dt
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1) 天空渐变背景
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(skyGradient(w))),
        )

        // 2) 远景剪影（城市/山）
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.TopCenter),
        ) {
            CanvasDrawDistantLayer(color = distantColor(w), weather = w, time = time)
        }

        // 3) 天气粒子层（雨/雪/雷）
        if (w.hasParticles) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                CanvasDrawWeatherParticles(weather = w, time = time)
            }
        }

        // 4) 天气信息文字
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 20.dp),
        ) {
            Text(
                text = stringResource(balconyWeatherLabel(w)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // 5) 阳台栏杆 + 小熊（底部）
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 小熊按天气/昼夜选插画，而不是一概用「傍晚阳台」
            Image(
                painter = painterResource(balconyBearRes(w)),
                contentDescription = stringResource(R.string.bear_home_room_balcony),
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit,
            )
            // 阳台栏杆
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x008D6E63), Color(0xFF8D6E63)),
                            ),
                        ),
            )
        }
    }
}

/**
 * 阳台小熊插画：白天/夜晚与天气匹配，尽量用现有插画资源让场景「有戏」:
 * 雨天看雨、雪天看雪、夜晚观景/休息。
 */
@androidx.annotation.DrawableRes
private fun balconyBearRes(w: BalconyWeather): Int =
    when (w.weather) {
        FarmWeather.RAINY, FarmWeather.STORMY ->
            // 雨天：夜晚看雨/白天撑伞看雨（用晚间阳台/听雨类插画表达）
            if (w.isNight) {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_balcony
            } else {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_weekend_dessert
            }
        FarmWeather.SNOWY ->
            // 雪天：看雪（用夜间暖屋/观景插画）
            if (w.isNight) {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_cityview
            } else {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_weekend_nightmarket
            }
        FarmWeather.FOGGY ->
            // 雾天：室内或阳台静观
            if (w.isNight) {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_balcony
            } else {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening
            }
        FarmWeather.SUNNY ->
            // 晴：白天城市观景，夜晚观星/放松
            if (w.isNight) {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_cityview
            } else {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening
            }
        FarmWeather.CLOUDY ->
            if (w.isNight) {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_balcony
            } else {
                com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening
            }
    }

@androidx.annotation.StringRes
private fun balconyWeatherLabel(w: BalconyWeather): Int {
    val night = w.isNight
    return when (w.weather) {
        FarmWeather.SUNNY -> if (night) R.string.balcony_weather_clear_night else R.string.balcony_weather_sunny
        FarmWeather.CLOUDY -> R.string.balcony_weather_cloudy
        FarmWeather.RAINY -> R.string.balcony_weather_rain_light
        FarmWeather.STORMY -> R.string.balcony_weather_storm
        FarmWeather.SNOWY -> R.string.balcony_weather_snow
        FarmWeather.FOGGY -> R.string.balcony_weather_fog
    }
}

/** 城市天际线建筑位置/高度（横向比例, 高度比例），常量以避免每帧分配。 */
private val BALCONY_BUILDINGS =
    listOf(
        0.05f to 0.55f,
        0.12f to 0.70f,
        0.20f to 0.45f,
        0.28f to 0.80f,
        0.35f to 0.60f,
        0.42f to 0.50f,
        0.50f to 0.75f,
        0.58f to 0.55f,
        0.65f to 0.65f,
        0.72f to 0.85f,
        0.80f to 0.50f,
        0.88f to 0.60f,
        0.95f to 0.45f,
    )

/** 远景层：用 Canvas 绘制城市/山景剪影，雨天更暗，雾天更淡。 */
@Composable
private fun CanvasDrawDistantLayer(
    color: Color,
    weather: BalconyWeather,
    time: Double,
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height
        // 城市天际线（多栋高楼剪影）
        for ((xFrac, hFrac) in BALCONY_BUILDINGS) {
            val bw = w * 0.06f
            val bx = xFrac * w - bw / 2
            val bh = h * hFrac
            drawRect(
                color = color,
                topLeft = Offset(bx, h - bh),
                size =
                    androidx.compose.ui.geometry
                        .Size(bw, bh),
            )
        }
        // 山景剪影（雾/雪天时更突出）
        if (weather.weather == FarmWeather.FOGGY || weather.weather == FarmWeather.SNOWY) {
            val mountainColor = color.copy(alpha = color.alpha * 0.6f)
            drawLine(
                color = mountainColor,
                start = Offset(0f, h),
                end = Offset(w * 0.3f, h * 0.3f),
                strokeWidth = 3f,
            )
            drawLine(
                color = mountainColor,
                start = Offset(w * 0.3f, h * 0.3f),
                end = Offset(w * 0.6f, h * 0.5f),
                strokeWidth = 3f,
            )
            drawLine(
                color = mountainColor,
                start = Offset(w * 0.6f, h * 0.5f),
                end = Offset(w, h * 0.2f),
                strokeWidth = 3f,
            )
        }
        // 夜晚城市灯火：固定在建筑位置闪烁，不左右游走
        if (weather.isNight) {
            for (i in 0 until 20) {
                val bx = BALCONY_BUILDINGS[i % BALCONY_BUILDINGS.size].first * w
                val ly = h * (0.35f + (i % 4) * 0.12f)
                val flicker = (0.5 + 0.5 * sin(time * 2.0 + i * 1.3)).toFloat()
                drawCircle(
                    color = Color(0xFFFFE082).copy(alpha = flicker * 0.7f),
                    radius = 1.5f,
                    center = Offset(bx, ly),
                )
            }
        }
    }
}

/** 天气粒子层：雨/雪/雷电。 */
@Composable
private fun CanvasDrawWeatherParticles(
    weather: BalconyWeather,
    time: Double,
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height
        when (weather.weather) {
            FarmWeather.RAINY -> drawBalconyRain(time, w, h, count = 32, lenMul = 0.9f, alpha = 0.55f)
            FarmWeather.STORMY -> {
                drawBalconyRain(time, w, h, count = 48, lenMul = 1.2f, alpha = 0.6f)
                drawBalconyLightning(time, w, h)
            }
            FarmWeather.SNOWY -> drawBalconySnow(time, w, h, count = 30)
            FarmWeather.FOGGY -> drawBalconyFog(time, w, h)
            else -> Unit
        }
    }
}

/** 阳台雨：双层雨滴，带微摆动。 */
private fun DrawScope.drawBalconyRain(
    time: Double,
    w: Float,
    h: Float,
    count: Int,
    lenMul: Float,
    alpha: Float,
) {
    val wd = w.toDouble()
    val hd = h.toDouble()
    for (i in 0 until count) {
        val speed = 900.0 + (i % 4) * 150.0
        val x = (((i * 97.0 + time * speed * 0.55) % (wd + 40.0)) - 20.0).toFloat()
        val y = ((((i * 53.0 + time * speed) % (hd + 60.0) + (hd + 60.0)) % (hd + 60.0)) - 30.0).toFloat()
        val wobble = (sin(time * 3.0 + i * 1.1) * 2.5).toFloat()
        val dropLen = 14f * lenMul
        drawLine(
            color = Color(0xFFB4D2F0).copy(alpha = alpha),
            start = Offset(x + wobble, y),
            end = Offset(x + wobble - 3f, y + dropLen),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

/** 阳台闪电：两条亮线周期性闪现。 */
private fun DrawScope.drawBalconyLightning(
    time: Double,
    w: Float,
    h: Float,
) {
    if (sin(time * 6.0) > 0.88) {
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.85f),
            start = Offset(w * 0.72f, 0f),
            end = Offset(w * 0.60f, h * 0.20f),
            strokeWidth = 2.5f,
        )
        drawLine(
            color = Color(0xFFFFF59D).copy(alpha = 0.85f),
            start = Offset(w * 0.64f, h * 0.06f),
            end = Offset(w * 0.52f, h * 0.22f),
            strokeWidth = 1.6f,
        )
    }
}

/** 阳台雪花：带水平飘动的白色圆点。 */
private fun DrawScope.drawBalconySnow(
    time: Double,
    w: Float,
    h: Float,
    count: Int,
) {
    val wd = w.toDouble()
    val hd = h.toDouble()
    for (i in 0 until count) {
        val x = ((i * 83.0 + time * 20.0 + sin(time * 1.2 + i) * 15.0) % (wd + 30.0)).toFloat()
        val y = ((i * 61.0 + time * 45.0) % (hd + 30.0)).toFloat()
        val size = 2f + (i % 3) * 0.8f
        drawCircle(Color.White.copy(alpha = 0.85f), size, Offset(x, y))
    }
}

/** 阳台雾气：水平飘动的半透明条带。 */
private fun DrawScope.drawBalconyFog(
    time: Double,
    w: Float,
    h: Float,
) {
    for (i in 0 until 8) {
        val y = h * (0.2f + i * 0.1f)
        val xOff = (sin(time * 0.3 + i * 0.8) * 40.0).toFloat()
        val fogAlpha = (0.12 + 0.05 * sin(time * 0.5 + i)).toFloat()
        drawLine(
            color = Color.White.copy(alpha = fogAlpha),
            start = Offset(-20f + xOff, y),
            end = Offset(w + 20f + xOff, y + 8f),
            strokeWidth = 12f,
            cap = StrokeCap.Round,
        )
    }
}
