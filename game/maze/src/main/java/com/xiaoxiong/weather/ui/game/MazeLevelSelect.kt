package com.xiaoxiong.weather.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R

/** 选关页顶部进度汇总：一次 remember 算出，避免重组/动画期间反复扫描 stars。 */
private data class MazeSelectProgress(
    val totalStars: Int,
    val cleared: Int,
    val nextIndex: Int,
)

/**
 * 选关界面：主题化「关卡徽章地图」+ 总进度头 + 特殊模式卡（继续/无尽/每日）+ 动态云层背景。
 * 关卡徽章按各关主题取色，未解锁灰显、三星加冕、下一关脉冲高亮。
 */
@Composable
internal fun MazeLevelSelect(
    game: MazeGame,
    campaignMetas: List<MazeLevelMeta>,
    savedSession: SavedSessionInfo?,
    dailyBest: Int,
    dailyDone: Boolean,
    onPick: (Int) -> Unit,
    onEndless: () -> Unit,
    onResume: () -> Unit,
    onDaily: () -> Unit,
    onExit: () -> Unit,
    onCharacter: () -> Unit,
) {
    // 进度只随 progressVersion 变化时重算一次，避免动画/重组期间反复复制 stars
    val progress =
        remember(game.progressVersion) {
            val totalStars = game.stars.sum()
            val cleared = game.stars.count { it > 0 }
            // 「下一关」= 已解锁但尚未通关的第一关；全清后不再高亮
            val nextIndex =
                (0 until MAZE_CAMPAIGN_SIZE).firstOrNull { it < game.unlocked && game.stars[it] == 0 } ?: -1
            MazeSelectProgress(totalStars, cleared, nextIndex)
        }
    val maxStars = MAZE_CAMPAIGN_SIZE * 3

    val transition = rememberInfiniteTransition(label = "maze-select")
    // 动画值以 State 形式下传：只在 Canvas draw lambda / graphicsLayer 内读取，
    // 动画期间不触发选关列表重组
    val drift =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "clouds",
        )
    val pulse =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "nextPulse",
        )

    Box(modifier = Modifier.fillMaxSize()) {
        MazeSelectBackground(drift)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            MazeSelectHeader(
                onExit = onExit,
                onCharacter = onCharacter,
            )
            Spacer(Modifier.height(10.dp))
            MazeProgressCard(
                cleared = progress.cleared,
                total = MAZE_CAMPAIGN_SIZE,
                stars = progress.totalStars,
                maxStars = maxStars,
            )
            Spacer(Modifier.height(10.dp))
            MazeModeCards(
                savedSession = savedSession,
                endlessBest = game.endlessBest,
                dailyBest = dailyBest,
                dailyDone = dailyDone,
                onResume = onResume,
                onEndless = onEndless,
                onDaily = onDaily,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.bear_maze_campaign_short, MAZE_CAMPAIGN_SIZE),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12324F),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.bear_maze_select_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2A4A6A).copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))

            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 20.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                items(campaignMetas.size) { i ->
                    val meta = campaignMetas[i]
                    MazeLevelNode(
                        index = i,
                        meta = meta,
                        open = i < game.unlocked,
                        stars = game.stars.getOrElse(i) { 0 },
                        handcrafted = i < MAZE_HANDCRAFTED_COUNT,
                        isNext = i == progress.nextIndex,
                        pulse = pulse,
                        onClick = { onPick(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MazeSelectHeader(
    onExit: () -> Unit,
    onCharacter: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close_game),
                    tint = Color(0xFF12324F),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bear_maze_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0E2A44),
            )
            Text(
                text = stringResource(R.string.bear_maze_levels),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2A4A6A).copy(alpha = 0.85f),
            )
        }
        GameCharacterButton(
            onClick = onCharacter,
            tint = Color(0xFF12324F),
        )
    }
}

@Composable
private fun MazeProgressCard(
    cleared: Int,
    total: Int,
    stars: Int,
    maxStars: Int,
) {
    val fraction = if (maxStars > 0) (stars.toFloat() / maxStars).coerceIn(0f, 1f) else 0f
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xF2FFFDF7), Color(0xE8F3E9D8))),
                ).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bear_maze_progress, cleared, total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.bear_maze_stars_total, stars, maxStars),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE6A100),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp)),
                color = Color(0xFFF9A825),
                trackColor = Color(0x22000000),
            )
        }
    }
}

@Composable
private fun MazeModeCards(
    savedSession: SavedSessionInfo?,
    endlessBest: Int,
    dailyBest: Int,
    dailyDone: Boolean,
    onResume: () -> Unit,
    onEndless: () -> Unit,
    onDaily: () -> Unit,
) {
    Column {
        if (savedSession != null) {
            MazeModeCard(
                emoji = "⏯️",
                title =
                    when (savedSession) {
                        is SavedSessionInfo.Campaign ->
                            stringResource(R.string.bear_maze_continue_campaign, savedSession.levelIndex + 1)
                        is SavedSessionInfo.Endless ->
                            stringResource(R.string.bear_maze_continue_endless, savedSession.wave)
                        is SavedSessionInfo.Daily -> stringResource(R.string.bear_maze_continue_daily)
                    },
                subtitle = null,
                colors = listOf(Color(0xFF43A047), Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth(),
                onClick = onResume,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MazeModeCard(
                emoji = "🎲",
                title = stringResource(R.string.bear_maze_endless),
                subtitle = stringResource(R.string.bear_maze_endless_best, endlessBest),
                colors = listOf(Color(0xFF7E57C2), Color(0xFF4527A0)),
                modifier = Modifier.weight(1f),
                onClick = onEndless,
            )
            MazeModeCard(
                emoji = "📅",
                title = stringResource(R.string.bear_maze_daily),
                subtitle =
                    if (dailyDone) {
                        stringResource(R.string.bear_maze_daily_best, dailyBest)
                    } else {
                        stringResource(R.string.bear_maze_daily_hint)
                    },
                colors = listOf(Color(0xFFF9A825), Color(0xFFEF6C00)),
                modifier = Modifier.weight(1f),
                onClick = onDaily,
            )
        }
    }
}

@Composable
private fun MazeModeCard(
    emoji: String,
    title: String,
    subtitle: String?,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(6.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(colors))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MazeLevelNode(
    index: Int,
    meta: MazeLevelMeta,
    open: Boolean,
    stars: Int,
    handcrafted: Boolean,
    isNext: Boolean,
    pulse: State<Float>,
    onClick: () -> Unit,
) {
    val style = remember(meta.theme) { mazeThemeStyle(meta.theme) }
    val shape = RoundedCornerShape(18.dp)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appeared = true
    }
    val appear by
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = tween(240, delayMillis = (index % 9) * 24, easing = FastOutSlowInEasing),
            label = "appear",
        )

    val cardTop = if (open) lerp(Color.White, style.floor, 0.30f) else Color(0xFFEDEDED)
    val cardBottom = if (open) lerp(Color.White, style.floor, 0.55f) else Color(0xFFE0E0E0)
    val numberBg = if (open) style.floor else Color(0xFFCFD8DC)
    val numberFg = if (open) style.wall else Color(0xFF90A4AE)
    val bannerColors =
        if (open) {
            listOf(style.skyTop, style.accent)
        } else {
            listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE))
        }
    val borderColor =
        if (isNext) {
            style.accent
        } else if (open) {
            style.wall.copy(alpha = 0.12f)
        } else {
            Color(0x22000000)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .graphicsLayer {
                    // pulse 只在 layer 块内读取：动画期间只失效绘制层，不重组列表项
                    val scale = if (isNext) 1f + pulse.value * 0.035f else 1f
                    alpha = appear
                    translationY = (1f - appear) * 22f
                    scaleX = scale
                    scaleY = scale
                }.shadow(5.dp, shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                .border(if (isNext) 2.dp else 1.dp, borderColor, shape)
                .clickable(enabled = open, onClick = onClick),
    ) {
        // 顶部主题横幅 + 主题图标
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Brush.horizontalGradient(bannerColors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = mazeThemeEmoji(meta.theme), fontSize = 16.sp)
        }

        // 关卡编号徽章
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(numberBg)
                    .border(2.dp, style.accent.copy(alpha = if (open) 0.9f else 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${meta.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = numberFg,
            )
        }

        // 底部：关卡名 + 星级 + 来源标签
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = meta.displayTitle(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (open) style.wall.copy(alpha = 0.9f) else Color(0xFF78909C),
            )
            Text(
                text = starRow(stars, open),
                style = MaterialTheme.typography.labelLarge,
                color = if (open) Color(0xFFF9A825) else Color(0xFFB0BEC5),
            )
            Text(
                text = stringResource(if (handcrafted) R.string.bear_maze_tag_hand else R.string.bear_maze_tag_gen),
                style = MaterialTheme.typography.labelSmall,
                color = (if (open) style.wall else Color(0xFF90A4AE)).copy(alpha = 0.55f),
            )
        }

        // 三星加冕
        if (open && stars >= 3) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF9A825))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = "★", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // 下一关提示
        if (isNext) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(style.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.bear_maze_next),
                    fontSize = 10.sp,
                    color = style.wall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 未解锁遮罩
        if (!open) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99ECEFF1)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.bear_maze_locked),
                        tint = Color(0xFF78909C),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.bear_maze_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF607D8B),
                    )
                }
            }
        }
    }
}

private fun starRow(
    stars: Int,
    open: Boolean,
): String {
    if (!open) return "☆ ☆ ☆"
    val s = stars.coerceIn(0, 3)
    return "★".repeat(s) + "☆".repeat(3 - s)
}

/** 各主题的迷你图标（与迷宫氛围对应）。 */
private fun mazeThemeEmoji(theme: MazeTheme): String =
    when (theme) {
        MazeTheme.GARDEN -> "🌿"
        MazeTheme.RAIN -> "🌧️"
        MazeTheme.SNOW -> "❄️"
        MazeTheme.NIGHT -> "🌙"
        MazeTheme.FESTIVAL -> "🏮"
        MazeTheme.CLOUD -> "☁️"
    }

/** 选关背景：晴空渐变 + 阳光光晕 + 远山 + 缓慢漂移的云。 */
@Composable
private fun MazeSelectBackground(drift: State<Float>) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF4E96D8), Color(0xFF9BD2F2), Color(0xFFD8EFFB)))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 只在绘制阶段读动画 State，漂移不触发重组
            val driftValue = drift.value
            // 阳光光晕
            drawCircle(
                color = Color(0x33FFE082),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.84f, size.height * 0.10f),
            )
            drawCircle(
                color = Color(0x22FFF59D),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.84f, size.height * 0.10f),
            )
            // 漂移的云：所有云同一速度（每周期恰好走满一屏），仅相位/高度/大小不同，
            // 循环回到起点时位移对模连续 → 无可见跳变
            val cloudCount = 5
            for (i in 0 until cloudCount) {
                val span = size.width + 320f
                val x = (driftValue * span + i * 260f) % span - 160f
                val y = size.height * (0.08f + i * 0.075f)
                val s = 1f + (i % 3) * 0.25f
                drawCloud(x, y, s)
            }
            // 远山两层
            val near =
                Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, size.height * 0.86f)
                    quadraticBezierTo(size.width * 0.22f, size.height * 0.78f, size.width * 0.46f, size.height * 0.86f)
                    quadraticBezierTo(size.width * 0.72f, size.height * 0.94f, size.width, size.height * 0.84f)
                    lineTo(size.width, size.height)
                    close()
                }
            drawPath(near, Color(0x332E7D32))
            val far =
                Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, size.height * 0.92f)
                    quadraticBezierTo(size.width * 0.3f, size.height * 0.86f, size.width * 0.6f, size.height * 0.94f)
                    quadraticBezierTo(size.width * 0.82f, size.height * 0.98f, size.width, size.height * 0.92f)
                    lineTo(size.width, size.height)
                    close()
                }
            drawPath(far, Color(0x222E7D32))
        }
    }
}

/** 在 [x]、[y] 处画一朵由三个圆叠成的云（[scale] 控制大小）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    x: Float,
    y: Float,
    scale: Float,
) {
    val r = 26f * scale
    val color = Color(0x55FFFFFF)
    drawCircle(color, r, Offset(x, y))
    drawCircle(color, r * 0.78f, Offset(x + r * 0.9f, y + r * 0.12f))
    drawCircle(color, r * 0.66f, Offset(x - r * 0.95f, y + r * 0.22f))
    drawCircle(color, r * 0.6f, Offset(x + r * 0.1f, y - r * 0.5f))
}
