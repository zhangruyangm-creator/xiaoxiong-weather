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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.ui.weather.SunPathBearMoment
import com.xiaoxiong.weather.ui.weather.isWeekend
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

private enum class HomeRoom {
    HUB,
    LIVING,
    BEDROOM,
    KITCHEN,
    PLAYROOM,
    CLOSET,
    BALCONY,
    STREET,
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
    val context = LocalContext.current
    // 入口预加载全部音效样本，消除首次播放的加载延迟
    LaunchedEffect(Unit) { GameAudio.preload(context) }
    var room by remember { mutableStateOf(HomeRoom.HUB) }

    /** 从阳台/枢纽进街后，返回时回到来处。 */
    var streetReturn by remember { mutableStateOf(HomeRoom.HUB) }
    var streetSpot by remember { mutableStateOf<StreetSpot?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
            today = LocalDate.now()
        }
    }
    // 天气未加载（isDay==null）时按白天处理：片刻兜底是晨间活动，Hub 主题保持一致
    val isDayTheme = isDay != false
    // 与卧室作息时间表同源，避免伪造 progress=0.5 导致凌晨永远进不了黎明
    val routineDay = remember(today, now) { homeRoutineDay(today, now) }
    val moment =
        remember(routineDay, now) {
            homeRoutineSlots(routineDay)
                .firstOrNull { homeRoutineStatus(it, now) == HomeRoutineStatus.NOW }
                ?.moment
                ?: if (isWeekend(routineDay)) {
                    SunPathBearMoment.WEEKEND_MORNING
                } else {
                    SunPathBearMoment.MORNING_EXERCISE
                }
        }
    val bearRes = remember(moment, today) { moment.bearResFor(today) }
    val narration = stringResource(homeMomentNarrationRes(moment))

    /** 房间跳转统一入口：每次点击至多播一次低频 UI_CLICK。 */
    fun openRoom(target: HomeRoom) {
        GameAudio.play(GameCue.UI_CLICK)
        room = target
    }

    fun openStreet(from: HomeRoom) {
        GameAudio.play(GameCue.UI_CLICK)
        streetReturn = from
        streetSpot = null
        room = HomeRoom.STREET
    }

    BackHandler {
        when {
            room == HomeRoom.HUB -> onExit()
            room == HomeRoom.STREET && streetSpot != null -> streetSpot = null
            room == HomeRoom.STREET -> room = streetReturn
            else -> room = HomeRoom.HUB
        }
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
                    narration = narration,
                    isDay = isDayTheme,
                    onOpenBedroom = { openRoom(HomeRoom.BEDROOM) },
                    onOpenKitchen = { openRoom(HomeRoom.KITCHEN) },
                    onOpenPlayroom = { openRoom(HomeRoom.PLAYROOM) },
                    onOpenCloset = { openRoom(HomeRoom.CLOSET) },
                    onOpenBalcony = { openRoom(HomeRoom.BALCONY) },
                    onOpenLiving = { openRoom(HomeRoom.LIVING) },
                    onOpenStreet = { openStreet(HomeRoom.HUB) },
                    onOpenSummary = { openRoom(HomeRoom.SUMMARY) },
                )
            HomeRoom.LIVING ->
                HomeLiving(
                    bearRes = bearRes,
                    narration = narration,
                    onOpenBalcony = { openRoom(HomeRoom.BALCONY) },
                    onOpenStreet = { openStreet(HomeRoom.LIVING) },
                    onOpenSummary = { openRoom(HomeRoom.SUMMARY) },
                )
            HomeRoom.BEDROOM -> HomeBedroom(date = today, time = now)
            HomeRoom.KITCHEN -> HomeKitchen()
            HomeRoom.PLAYROOM ->
                HomePlayroom(
                    games = otherGames,
                    onOpenGame = { game ->
                        GameAudio.play(GameCue.UI_CLICK)
                        onOpenGame(game)
                    },
                )
            HomeRoom.CLOSET -> HomeCloset()
            HomeRoom.BALCONY ->
                HomeBalcony(
                    sceneCode = sceneCode,
                    isDay = isDay,
                    date = today,
                    onGoStreet = { openStreet(HomeRoom.BALCONY) },
                )
            HomeRoom.STREET ->
                HomeStreet(
                    isDay = isDay,
                    selected = streetSpot,
                    onSelect = { spot ->
                        GameAudio.play(GameCue.UI_CLICK)
                        streetSpot = spot
                    },
                )
            HomeRoom.SUMMARY -> HomeSummary(sceneCode = sceneCode, isDay = isDay, date = today, time = now)
        }
        HomeTopBar(
            title =
                when (room) {
                    HomeRoom.HUB -> stringResource(R.string.bear_home_title)
                    HomeRoom.LIVING -> stringResource(R.string.bear_home_room_living)
                    HomeRoom.BEDROOM -> stringResource(R.string.bear_home_room_bedroom)
                    HomeRoom.KITCHEN -> stringResource(R.string.bear_home_room_kitchen)
                    HomeRoom.PLAYROOM -> stringResource(R.string.bear_home_room_playroom)
                    HomeRoom.CLOSET -> stringResource(R.string.bear_home_room_closet)
                    HomeRoom.BALCONY -> stringResource(R.string.bear_home_room_balcony)
                    HomeRoom.STREET -> {
                        val spot = streetSpot
                        if (spot != null) {
                            stringResource(spot.nameRes)
                        } else {
                            stringResource(R.string.bear_home_street_title)
                        }
                    }
                    HomeRoom.SUMMARY -> stringResource(R.string.bear_home_summary_title)
                },
            showBack = room != HomeRoom.HUB,
            onBack = {
                GameAudio.play(GameCue.UI_CLICK)
                when {
                    room == HomeRoom.STREET && streetSpot != null -> streetSpot = null
                    room == HomeRoom.STREET -> room = streetReturn
                    else -> room = HomeRoom.HUB
                }
            },
            onClose = {
                GameAudio.play(GameCue.UI_CLICK)
                onExit()
            },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
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
    narration: String,
    isDay: Boolean,
    onOpenBedroom: () -> Unit,
    onOpenKitchen: () -> Unit,
    onOpenPlayroom: () -> Unit,
    onOpenCloset: () -> Unit,
    onOpenBalcony: () -> Unit,
    onOpenLiving: () -> Unit,
    onOpenStreet: () -> Unit,
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
            bitmap = rememberGameImage(R.drawable.ic_bear_home_hub, 1280),
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
                            listOf(Color(0x00FFF6E8), Color(0xF2FFF6E8), Color(0xFFFFF6E8)),
                        ),
                    ).padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.bear_home_hub_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x998D6E63),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFE0B2),
                modifier =
                    Modifier
                        .padding(bottom = 10.dp)
                        .clickable(onClick = onOpenStreet),
            ) {
                Text(
                    text = stringResource(R.string.bear_home_street_exit),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5D4037),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onOpenSummary),
            ) {
                Image(
                    bitmap = rememberGameImage(bearRes, 256),
                    contentDescription = stringResource(R.string.bear_home_summary_title),
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = stringResource(R.string.bear_home_now_doing, narration),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5D4037),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                )
                Text(
                    text = stringResource(R.string.bear_home_summary_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8D6E63),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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
                .padding(6.dp)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 轻标签：低对比、无厚白底，避免盖住插画
        Text(
            text = label,
            modifier =
                Modifier
                    .padding(bottom = 10.dp)
                    .background(Color(0x55FFF8E7), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xCC4E342E),
        )
    }
}

@Composable
private fun HomeLiving(
    bearRes: Int,
    narration: String,
    onOpenBalcony: () -> Unit,
    onOpenStreet: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        Image(
            bitmap = rememberGameImage(bearRes, 512),
            contentDescription = narration,
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .padding(horizontal = 24.dp),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.bear_home_now_doing, narration),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.bear_home_living_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8D6E63),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            TextButton(onClick = onOpenBalcony) {
                Text(stringResource(R.string.bear_home_room_balcony))
            }
            TextButton(onClick = onOpenStreet) {
                Text(stringResource(R.string.bear_home_street_exit))
            }
            TextButton(onClick = onOpenSummary) {
                Text(stringResource(R.string.bear_home_summary_title))
            }
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
        bitmap = rememberGameImage(artRes, 768),
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
                        bitmap = rememberGameImage(slot.moment.bearResFor(routineDay), 256),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = stringResource(homeMomentNarrationRes(slot.moment)),
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
    // 复用 home 内共享映射：降雨强度细分、冻雨单列、晴夜特判，与阳台完全一致
    val weatherLabelRes = remember(sceneCode, isDay) { homeWeatherLabelRes(sceneCode, isDay) }
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
                            text = stringResource(weatherLabelRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text =
                                stringResource(
                                    if (isDay != false) {
                                        R.string.bear_home_summary_icon_day
                                    } else {
                                        R.string.bear_home_summary_icon_night
                                    },
                                ),
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
                                    stringResource(homeMomentNarrationRes(currentMoment.moment)),
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
                        bitmap = rememberGameImage(slot.moment.bearResFor(routineDay), 256),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            text = stringResource(homeMomentNarrationRes(slot.moment)),
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
                                    .clickable {
                                        GameAudio.play(GameCue.UI_CLICK)
                                        slot = s
                                    },
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
                bitmap = rememberGameImage(dish.drawableRes, 512),
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
    val characterState = rememberBearCharacterState()
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
        Spacer(modifier = Modifier.height(12.dp))
        // 标题由 GameCharacterPickerPanel 自带（bear_character_title），页面不再重复渲染
        GameCharacterPickerPanel(state = characterState)
        Spacer(modifier = Modifier.height(24.dp))
    }
}
