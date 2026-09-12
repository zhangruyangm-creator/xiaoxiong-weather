package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun MazePlayField(
    game: MazeGame,
    sprites: BearSprites,
    frame: State<Long>,
    weather: FarmWeather,
    rainStrength: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    onExitSelect: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
    onPause: (() -> Unit)? = null,
    onMove: (MazeDir) -> Unit,
    onCharacter: () -> Unit,
) {
    val style = remember(game.level.theme) { mazeThemeStyle(game.level.theme) }
    val skyBrush = remember(style) { Brush.verticalGradient(skyGradient(style.skyTop, style.skyBottom)) }
    val baseLayer = remember(game.level) { MazeBaseLayerCache() }
    var swipeAcc by remember { mutableStateOf(Offset.Zero) }
    // 提示：高亮下一步方向，1.5s 后自动消失；无解 / 状态空间过大时短暂提示文案
    var hintDir by remember { mutableStateOf<MazeDir?>(null) }
    var showNoHint by remember { mutableStateOf(false) }
    var showHintUnavailable by remember { mutableStateOf(false) }

    // 移动/撤销/重开都会让提示对应的局面失效，统一清掉三类提示 UI
    fun clearHintUi() {
        hintDir = null
        showNoHint = false
        showHintUnavailable = false
    }
    LaunchedEffect(hintDir) {
        if (hintDir != null) {
            delay(HINT_HIGHLIGHT_MS)
            hintDir = null
        }
    }
    LaunchedEffect(showNoHint) {
        if (showNoHint) {
            delay(HINT_HIGHLIGHT_MS)
            showNoHint = false
        }
    }
    LaunchedEffect(showHintUnavailable) {
        if (showHintUnavailable) {
            delay(HINT_HIGHLIGHT_MS)
            showHintUnavailable = false
        }
    }
    // 任何一次移动都清掉提示（含滑动/方向键）
    val move: (MazeDir) -> Unit = { dir ->
        clearHintUi()
        onMove(dir)
    }
    // 中英标题：无尽按波次用资源模板，每日用固定名，战役关卡按系统语言
    val levelTitle =
        when {
            game.endless -> stringResource(R.string.bear_maze_endless_wave, game.endlessWave)
            game.daily -> stringResource(R.string.bear_maze_daily)
            else -> "${game.level.id}. ${game.level.displayTitle()}"
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(skyBrush),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(game.levelIndex, game.phase) {
                        detectDragGestures(
                            onDragStart = { swipeAcc = Offset.Zero },
                            onDrag = { _, drag -> swipeAcc += drag },
                            onDragEnd = {
                                val dx = swipeAcc.x
                                val dy = swipeAcc.y
                                if (abs(dx) < 24f && abs(dy) < 24f) return@detectDragGestures
                                val dir =
                                    if (abs(dx) > abs(dy)) {
                                        if (dx > 0) MazeDir.RIGHT else MazeDir.LEFT
                                    } else {
                                        if (dy > 0) MazeDir.DOWN else MazeDir.UP
                                    }
                                move(dir)
                            },
                        )
                    }.pointerInput(game.levelIndex) {
                        detectTapGestures { /* 吞掉，避免误触 */ }
                    },
        ) {
            // 在 draw lambda 内读 frame：只使绘制阶段失效，不触发 MazePlayField 重组
            frame.value
            drawMazeWorld(game, sprites, style, baseLayer)
            drawMazeWeather(game, weather, rainStrength, freezingRain, hail)
        }

        // HUD
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExitSelect) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = levelTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.bear_maze_hud,
                            game.honeyLeft,
                            game.honeyTotal,
                            game.keys,
                            game.moves,
                            game.level.moveBudget,
                        ),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (game.phase == MazePhase.PLAYING) {
                IconButton(
                    onClick = {
                        clearHintUi()
                        game.undo()
                    },
                    enabled = game.canUndo,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        stringResource(R.string.cd_maze_undo),
                        tint = if (game.canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
                IconButton(
                    onClick = {
                        clearHintUi()
                        when (val result = game.hint()) {
                            is MazeHint.Direction -> hintDir = result.dir
                            MazeHint.NoSolution -> showNoHint = true
                            MazeHint.TooLarge -> showHintUnavailable = true
                        }
                    },
                    enabled = !game.moving,
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        stringResource(R.string.cd_maze_hint),
                        tint = Color.White,
                    )
                }
            }
            IconButton(
                onClick = {
                    clearHintUi()
                    onRestart()
                },
            ) {
                Icon(Icons.Default.Refresh, stringResource(R.string.bear_maze_restart), tint = Color.White)
            }
            if (onPause != null) GamePauseButton(onClick = onPause)
            GameCharacterButton(onClick = onCharacter)
        }

        if (game.needHoneyHintT > 0f) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp)
                        .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE6333333),
            ) {
                Text(
                    text = stringResource(R.string.bear_maze_need_honey),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (showNoHint) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp)
                        .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE6B00020),
            ) {
                Text(
                    text = stringResource(R.string.bear_maze_hint_none),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (showHintUnavailable) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp)
                        .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE6333333),
            ) {
                Text(
                    text = stringResource(R.string.bear_maze_hint_unavailable),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 方向键（提示时高亮下一步方向）
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MazeArrow(
                Icons.Default.KeyboardArrowUp,
                highlight = hintDir == MazeDir.UP,
                highlightColor = style.pathHint,
            ) { move(MazeDir.UP) }
            Row {
                MazeArrow(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    highlight = hintDir == MazeDir.LEFT,
                    highlightColor = style.pathHint,
                ) { move(MazeDir.LEFT) }
                Spacer(modifier = Modifier.width(56.dp))
                MazeArrow(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    highlight = hintDir == MazeDir.RIGHT,
                    highlightColor = style.pathHint,
                ) { move(MazeDir.RIGHT) }
            }
            MazeArrow(
                Icons.Default.KeyboardArrowDown,
                highlight = hintDir == MazeDir.DOWN,
                highlightColor = style.pathHint,
            ) { move(MazeDir.DOWN) }
        }

        if (game.phase == MazePhase.CLEAR) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
            )
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (game.failed) {
                        Text(
                            stringResource(R.string.bear_maze_failed),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(
                                R.string.bear_maze_failed_detail,
                                game.endlessWave,
                                game.moves,
                                game.level.moveBudget,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            stringResource(R.string.bear_maze_clear),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "★".repeat(game.clearStars) + "☆".repeat(3 - game.clearStars),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFFB300),
                        )
                        Text(
                            stringResource(R.string.bear_maze_clear_moves, game.moves),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (game.hintsUsed > 0) {
                            Text(
                                stringResource(R.string.bear_maze_clear_hints, game.hintsUsed),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onExitSelect) {
                            Text(stringResource(R.string.bear_maze_levels))
                        }
                        Button(onClick = onRestart) {
                            Text(stringResource(R.string.bear_maze_restart))
                        }
                        if (!game.failed &&
                            !game.daily &&
                            (game.endless || game.levelIndex + 1 < MAZE_CAMPAIGN_SIZE)
                        ) {
                            Button(onClick = onNext) {
                                Text(
                                    if (game.endless) {
                                        stringResource(R.string.bear_maze_next_wave)
                                    } else {
                                        stringResource(R.string.bear_maze_next)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MazeArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean = false,
    highlightColor: Color = Color(0xFFFFD54F),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (highlight) highlightColor else Color.White.copy(alpha = 0.28f),
        modifier =
            Modifier
                .padding(4.dp)
                .size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                null,
                tint = if (highlight) Color(0xFF2A4A6A) else Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** 提示高亮持续时间（毫秒）。 */
private const val HINT_HIGHLIGHT_MS = 1500L
