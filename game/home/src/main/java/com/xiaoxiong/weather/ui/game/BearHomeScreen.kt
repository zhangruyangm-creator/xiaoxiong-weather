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
                // 客厅占主要面积；阳台只占窗户上方窄条，避免抢走客厅点击
                Column(modifier = Modifier.weight(1.3f).fillMaxSize()) {
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
    val slots = remember(date) { homeRoutineSlots(date) }
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
                        painter = painterResource(slot.moment.bearResFor(date)),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = stringResource(slot.moment.captionResFor(date)),
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
    val weatherLabel = remember(sceneCode, isDay) {
        val code = sceneCode ?: return@remember "—"
        when (code) {
            in 95..99 -> "雷暴"
            in 71..77, 86 -> "下雪"
            in 45..48 -> "大雾"
            51, 52, 53, 54, 80 -> "小雨"
            55, 56, 57, 61, 63, 66, 67, 81, 82 -> "大雨"
            0, 1 -> if (isDay == false) "晴朗夜晚" else "晴天"
            2, 3 -> "多云"
            else -> "多云"
        }
    }
    val slots = remember(date) { homeRoutineSlots(date) }
    val doneCount = remember(slots, time) {
        slots.count { homeRoutineStatus(it, time) == HomeRoutineStatus.DONE }
    }
    val currentMoment = remember(slots, time) {
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
                        text = stringResource(
                            R.string.bear_home_summary_progress,
                            doneCount,
                            slots.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8D6E63),
                    )
                    if (currentMoment != null) {
                        Text(
                            text = stringResource(
                                R.string.bear_home_summary_now_doing,
                                stringResource(currentMoment.captionResFor(date)),
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
                color = when (status) {
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
                        painter = painterResource(slot.moment.bearResFor(date)),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            text = stringResource(slot.moment.captionResFor(date)),
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

/** 阳台天气场景分类：由 sceneCode 映射，控制天空渐变、远景、粒子。 */
private enum class BalconyWeather {
    SUNNY_DAY,
    SUNNY_NIGHT,
    CLOUDY,
    RAIN_LIGHT,
    RAIN_HEAVY,
    STORM,
    SNOW,
    FOG,
}

private fun balconyWeatherFrom(
    sceneCode: Int?,
    isDay: Boolean?,
): BalconyWeather {
    val code = sceneCode ?: return if (isDay == false) BalconyWeather.SUNNY_NIGHT else BalconyWeather.SUNNY_DAY
    return when (code) {
        in 95..99 -> BalconyWeather.STORM
        in 71..77, 86 -> BalconyWeather.SNOW
        in 45..48 -> BalconyWeather.FOG
        51, 52, 53, 54, 80 -> BalconyWeather.RAIN_LIGHT
        55, 56, 57, 61, 63, 66, 67, 81, 82 -> BalconyWeather.RAIN_HEAVY
        0, 1 -> if (isDay == false) BalconyWeather.SUNNY_NIGHT else BalconyWeather.SUNNY_DAY
        2, 3 -> BalconyWeather.CLOUDY
        else -> if (isDay == false) BalconyWeather.SUNNY_NIGHT else BalconyWeather.SUNNY_DAY
    }
}

/** 天空渐变色。 */
private fun skyGradient(w: BalconyWeather): List<Color> =
    when (w) {
        BalconyWeather.SUNNY_DAY -> listOf(Color(0xFF4A90D9), Color(0xFF87CEEB), Color(0xFFB0E0FF))
        BalconyWeather.SUNNY_NIGHT -> listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF2C3E50))
        BalconyWeather.CLOUDY -> listOf(Color(0xFF6B7B8D), Color(0xFF8E9EAB), Color(0xFFB0BEC5))
        BalconyWeather.RAIN_LIGHT -> listOf(Color(0xFF4A5568), Color(0xFF6B7B8D), Color(0xFF8E9EAB))
        BalconyWeather.RAIN_HEAVY -> listOf(Color(0xFF2D3748), Color(0xFF4A5568), Color(0xFF6B7B8D))
        BalconyWeather.STORM -> listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF2D3748))
        BalconyWeather.SNOW -> listOf(Color(0xFFCBD5E0), Color(0xFFE2E8F0), Color(0xFFF7FAFC))
        BalconyWeather.FOG -> listOf(Color(0xFFA0AEC0), Color(0xFFCBD5E0), Color(0xFFE2E8F0))
    }

/** 远景颜色（城市/山景剪影）。 */
private fun distantColor(w: BalconyWeather): Color =
    when (w) {
        BalconyWeather.SUNNY_DAY -> Color(0xFF78909C).copy(alpha = 0.65f)
        BalconyWeather.SUNNY_NIGHT -> Color(0xFF1A237E).copy(alpha = 0.8f)
        BalconyWeather.CLOUDY -> Color(0xFF455A64).copy(alpha = 0.8f)
        BalconyWeather.RAIN_LIGHT -> Color(0xFF37474F).copy(alpha = 0.75f)
        BalconyWeather.RAIN_HEAVY -> Color(0xFF263238).copy(alpha = 0.85f)
        BalconyWeather.STORM -> Color(0xFF1A1A2E).copy(alpha = 0.9f)
        BalconyWeather.SNOW -> Color(0xFF90A4AE).copy(alpha = 0.6f)
        BalconyWeather.FOG -> Color(0xFF78909C).copy(alpha = 0.5f)
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
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            time = (System.nanoTime() - start) / 1_000_000_000f
            delay(16) // ~60fps
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            CanvasDrawWeatherParticles(weather = w, time = time)
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
            // 小熊插画
            Image(
                painter = painterResource(com.xiaoxiong.weather.resillustrations.R.drawable.ic_sun_bear_evening_balcony),
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

@androidx.annotation.StringRes
private fun balconyWeatherLabel(w: BalconyWeather): Int =
    when (w) {
        BalconyWeather.SUNNY_DAY -> R.string.balcony_weather_sunny
        BalconyWeather.SUNNY_NIGHT -> R.string.balcony_weather_clear_night
        BalconyWeather.CLOUDY -> R.string.balcony_weather_cloudy
        BalconyWeather.RAIN_LIGHT -> R.string.balcony_weather_rain_light
        BalconyWeather.RAIN_HEAVY -> R.string.balcony_weather_rain_heavy
        BalconyWeather.STORM -> R.string.balcony_weather_storm
        BalconyWeather.SNOW -> R.string.balcony_weather_snow
        BalconyWeather.FOG -> R.string.balcony_weather_fog
    }

/** 远景层：用 Canvas 绘制城市/山景剪影，雨天更暗，雾天更淡。 */
@Composable
private fun CanvasDrawDistantLayer(
    color: Color,
    weather: BalconyWeather,
    time: Float,
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height
        // 城市天际线（多栋高楼剪影）
        val buildings =
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
        for ((xFrac, hFrac) in buildings) {
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
        if (weather == BalconyWeather.FOG || weather == BalconyWeather.SNOW) {
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
        // 夜晚城市灯火
        if (weather == BalconyWeather.SUNNY_NIGHT) {
            for (i in 0 until 20) {
                val lx = ((i * 137 + time * 8f) % w)
                val ly = h * (0.4f + (i % 5) * 0.12f)
                val flicker = 0.5f + 0.5f * sin(time * 2f + i * 1.3f)
                drawCircle(
                    color = Color(0xFFFFE082).copy(alpha = flicker * 0.7f),
                    radius = 1.5f,
                    center = Offset(lx, ly),
                )
            }
        }
    }
}

/** 天气粒子层：雨/雪/雷电。 */
@Composable
private fun CanvasDrawWeatherParticles(
    weather: BalconyWeather,
    time: Float,
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val w = size.width
        val h = size.height
        when (weather) {
            BalconyWeather.RAIN_LIGHT -> drawBalconyRain(time, w, h, count = 24, lenMul = 0.8f, alpha = 0.5f)
            BalconyWeather.RAIN_HEAVY -> drawBalconyRain(time, w, h, count = 56, lenMul = 1.1f, alpha = 0.65f)
            BalconyWeather.STORM -> {
                drawBalconyRain(time, w, h, count = 48, lenMul = 1.2f, alpha = 0.6f)
                drawBalconyLightning(time, w, h)
            }
            BalconyWeather.SNOW -> drawBalconySnow(time, w, h, count = 30)
            BalconyWeather.FOG -> drawBalconyFog(time, w, h)
            else -> Unit
        }
    }
}

/** 阳台雨：双层雨滴，带微摆动。 */
private fun DrawScope.drawBalconyRain(
    time: Float,
    w: Float,
    h: Float,
    count: Int,
    lenMul: Float,
    alpha: Float,
) {
    for (i in 0 until count) {
        val speed = 900f + (i % 4) * 150f
        val x = ((i * 97 + time * speed * 0.55f) % (w + 40)) - 20
        val y = ((i * 53 + time * speed) % (h + 60) + (h + 60)) % (h + 60) - 30
        val wobble = sin(time * 3f + i * 1.1f) * 2.5f
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
    time: Float,
    w: Float,
    h: Float,
) {
    if (sin(time * 6f) > 0.88f) {
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
    time: Float,
    w: Float,
    h: Float,
    count: Int,
) {
    for (i in 0 until count) {
        val x = ((i * 83 + time * 20f + sin(time * 1.2f + i) * 15f) % (w + 30))
        val y = ((i * 61 + time * 45f) % (h + 30))
        val size = 2f + (i % 3) * 0.8f
        drawCircle(Color.White.copy(alpha = 0.85f), size, Offset(x, y))
    }
}

/** 阳台雾气：水平飘动的半透明条带。 */
private fun DrawScope.drawBalconyFog(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 8) {
        val y = h * (0.2f + i * 0.1f)
        val xOff = sin(time * 0.3f + i * 0.8f) * 40f
        val fogAlpha = 0.12f + 0.05f * sin(time * 0.5f + i)
        drawLine(
            color = Color.White.copy(alpha = fogAlpha),
            start = Offset(-20f + xOff, y),
            end = Offset(w + 20f + xOff, y + 8f),
            strokeWidth = 12f,
            cap = StrokeCap.Round,
        )
    }
}
