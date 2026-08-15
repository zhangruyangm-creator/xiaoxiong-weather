package com.xiaoxiong.weather.ui.game

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.SunPathPhase
import com.xiaoxiong.weather.ui.weather.sunPathBearMoment
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

const val EASTER_EGG_BEAR_HOME = "#小熊回家"

private const val DISABLED = "BEAR_GAME_DISABLED"

private enum class HomeRoom {
    HUB,
    BEDROOM,
    KITCHEN,
    PLAYROOM,
    CLOSET,
}

/**
 * 小熊的家：世界客厅枢纽。口令 [EASTER_EGG_BEAR_HOME] 打开。
 * 房间子页在浮层内导航；游乐间按编译期启用的游戏显隐入口。
 */
@Composable
fun BearHomeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    onOpenJump: () -> Unit = {},
    onOpenStar: () -> Unit = {},
    onOpenMaze: () -> Unit = {},
    onOpenFarm: () -> Unit = {},
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            HomeTopBar(
                title =
                    when (room) {
                        HomeRoom.HUB -> stringResource(R.string.bear_home_title)
                        HomeRoom.BEDROOM -> stringResource(R.string.bear_home_room_bedroom)
                        HomeRoom.KITCHEN -> stringResource(R.string.bear_home_room_kitchen)
                        HomeRoom.PLAYROOM -> stringResource(R.string.bear_home_room_playroom)
                        HomeRoom.CLOSET -> stringResource(R.string.bear_home_room_closet)
                    },
                showBack = room != HomeRoom.HUB,
                onBack = { room = HomeRoom.HUB },
                onClose = onExit,
            )
            when (room) {
                HomeRoom.HUB ->
                    HomeHub(
                        bearRes = bearRes,
                        isDay = isDayTheme,
                        onOpenBedroom = { room = HomeRoom.BEDROOM },
                        onOpenKitchen = { room = HomeRoom.KITCHEN },
                        onOpenPlayroom = { room = HomeRoom.PLAYROOM },
                        onOpenCloset = { room = HomeRoom.CLOSET },
                        onTapBear = { showNowDialog = true },
                    )
                HomeRoom.BEDROOM -> HomeBedroom(date = today, time = now)
                HomeRoom.KITCHEN -> HomeKitchen()
                HomeRoom.PLAYROOM ->
                    HomePlayroom(
                        onOpenJump = onOpenJump,
                        onOpenStar = onOpenStar,
                        onOpenMaze = onOpenMaze,
                        onOpenFarm = onOpenFarm,
                    )
                HomeRoom.CLOSET -> HomeCloset()
            }
        }
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
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
    onTapBear: () -> Unit,
) {
    val sky =
        if (isDay) {
            Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFFFFF6E8)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFFFFF6E8)))
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(sky)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_bear_home_hub),
                contentDescription = stringResource(R.string.bear_home_title),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.Center),
                contentScale = ContentScale.Fit,
            )
            // 热区：按画面大致分区叠透明按钮
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.Center),
            ) {
                Row(modifier = Modifier.weight(1.1f).fillMaxWidth()) {
                    HotZone(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        label = stringResource(R.string.bear_home_room_bedroom),
                        onClick = onOpenBedroom,
                    )
                    HotZone(
                        modifier = Modifier.weight(1.2f).fillMaxSize(),
                        label = stringResource(R.string.bear_home_room_living),
                        onClick = onTapBear,
                    )
                    HotZone(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        label = stringResource(R.string.bear_home_room_kitchen),
                        onClick = onOpenKitchen,
                    )
                }
                Row(modifier = Modifier.weight(0.9f).fillMaxWidth()) {
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
        }
        Image(
            painter = painterResource(bearRes),
            contentDescription = stringResource(R.string.bear_home_room_living),
            modifier =
                Modifier
                    .size(96.dp)
                    .clickable(onClick = onTapBear)
                    .padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.bear_home_summary_stub),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8D6E63),
            modifier = Modifier.padding(bottom = 12.dp),
        )
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
    Image(
        painter = painterResource(artRes),
        contentDescription = contentDescription,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .aspectRatio(1f),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun HomeBedroom(
    date: LocalDate,
    time: LocalTime,
) {
    val slots = remember(date) { homeRoutineSlots(date) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(slots, key = { "${it.moment.name}-${it.startMinute}" }) { slot ->
            val status = homeRoutineStatus(slot, time)
            val highlight = status == HomeRoutineStatus.NOW
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (highlight) Color(0xFFFFE0B2) else Color.White,
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun HomeKitchen() {
    var slot by remember { mutableStateOf(HomeMealSlot.BREAKFAST) }
    val groups = remember(slot) { slot.groups() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HomeRoomHero(
                artRes = R.drawable.ic_bear_home_kitchen,
                contentDescription = stringResource(R.string.bear_home_room_kitchen),
            )
        }
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
                MealDexCard(dish)
            }
        }
    }
}

@Composable
private fun MealDexCard(dish: HomeMealDish) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
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
    onOpenJump: () -> Unit,
    onOpenStar: () -> Unit,
    onOpenMaze: () -> Unit,
    onOpenFarm: () -> Unit,
) {
    val games =
        buildList {
            if (EASTER_EGG_BEAR_GAME != DISABLED) {
                add(PlayGame(R.string.bear_game_title, onOpenJump))
            }
            if (EASTER_EGG_BEAR_STAR != DISABLED) {
                add(PlayGame(R.string.bear_jump_title, onOpenStar))
            }
            if (EASTER_EGG_BEAR_MAZE != DISABLED) {
                add(PlayGame(R.string.bear_maze_title, onOpenMaze))
            }
            if (EASTER_EGG_BEAR_FARM != DISABLED) {
                add(PlayGame(R.string.bear_farm_title, onOpenFarm))
            }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(games) { game ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = game.onOpen),
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

private data class PlayGame(
    @param:StringRes val titleRes: Int,
    val onOpen: () -> Unit,
)

@Composable
private fun HomeCloset() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bear_home_closet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8D6E63),
            textAlign = TextAlign.Center,
        )
    }
}
