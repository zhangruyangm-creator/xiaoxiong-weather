package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import kotlin.random.Random

@Composable
fun BearStarJumpScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val prefs = rememberGamePrefs()
    val lb = rememberLeaderboardState(prefs, "leaderboard_jump", "best_jump")
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    val game =
        remember {
            StarJumpGame().also { it.best = prefs.getInt("best_jump", 0) }
        }
    val jumpWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（雨量强度决定雨点多少）
    LaunchedEffect(jumpWeather, sceneCode) {
        game.scene = jumpSceneFromWeather(jumpWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites(characterState.selected)
    var frame by remember { mutableLongStateOf(0L) }

    // READY / OVER 菜单不需要 60fps：节流到约 12fps。dt 按真实帧累计后整笔提交，
    // 避免节流状态下动画以真实速度的 1/5 慢动作运行（对齐 jump/catch/farm 写法）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showCharacterPicker },
        onTick = { dt, now ->
            val running = game.phase == GamePhase.RUNNING
            animDt += dt
            if (running || slowTick % 5 == 0) {
                val worldDt = animDt
                animDt = 0f
                game.tick(
                    worldDt,
                    onBounce = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onStar = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onSpring = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (score > game.best) {
                            game.best = score
                            prefs.edit { putInt("best_jump", score) }
                        }
                        if (score > 0) {
                            lb.lastRank =
                                lb.store.submit(
                                    LeaderboardEntry(
                                        score = score,
                                        timestamp = System.currentTimeMillis(),
                                        city = reportCityName,
                                        weather = leaderboardWeatherOf(context, reportSceneCode),
                                    ),
                                ) ?: 0
                        }
                    },
                )
                frame = now
            }
            if (!running) slowTick++
        },
    )

    BackHandler { onExit() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    game.setDims(sz.width.toFloat(), sz.height.toFloat(), density)
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    // 仅对局中启用转向，避免与「开始」按钮/点按手势抢事件
                    .pointerInput(game.phase) {
                        if (game.phase != GamePhase.RUNNING) return@pointerInput
                        // 按住即转向：不走 drag 的 touchSlop，静止按住也能控左右。
                        // try/finally：phase 变化会取消本协程（pointerInput key），
                        // 拖动中被取消也要复位 touchX，否则残留坐标会让熊朝旧位置漂移
                        // （对齐 hockey 的写法）。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.touchX = down.position.x
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        game.touchX = change.position.x
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                game.touchX = null
                            }
                        }
                    },
        ) {
            frame // 订阅每帧更新，触发逐帧重绘（与跑酷 BearGameScreen 的 Canvas 一致）
            if (game.w <= 0f || game.h <= 0f) return@Canvas
            val shake = if (game.shakeT > 0f) game.shakeT * 14f * game.s else 0f
            withTransform({
                translate(
                    (Random.nextFloat() - 0.5f) * shake * 2f,
                    (Random.nextFloat() - 0.5f) * shake * 2f,
                )
            }) {
                drawJumpWorld(game, bearSprites)
            }
        }

        JumpHud(
            game = game,
            frame = frame,
            onExit = onExit,
            onLeaderboard = {
                lb.entries = lb.store.load()
                lb.show = true
            },
            onCharacter = { showCharacterPicker = true },
        )

        if (showCharacterPicker) {
            GameCharacterPickerDialog(
                state = characterState,
                onDismiss = { showCharacterPicker = false },
            )
        }

        if (lb.show) {
            GameLeaderboardDialog(
                title = stringResource(R.string.bear_leaderboard_title),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    lb.resetLastRank()
                    game.best = 0
                },
                onDismiss = { lb.show = false },
            )
        }

        when (game.phase) {
            GamePhase.READY -> {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.bear_jump_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bear_jump_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = {
                            lb.resetLastRank()
                            game.start()
                        }) {
                            Text(stringResource(R.string.bear_jump_start))
                        }
                    }
                }
            }
            GamePhase.OVER -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .pointerInput(Unit) {
                                // 吞掉底层 Canvas 的按下转向 / 点击
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
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.bear_jump_over),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${game.score}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text =
                                stringResource(R.string.bear_game_best_label) +
                                    " ${game.best}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lb.lastRank > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text =
                                    stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        TextButton(
                            onClick = {
                                lb.entries = lb.store.load()
                                lb.show = true
                            },
                        ) {
                            Text(stringResource(R.string.bear_game_view_rank))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onExit) {
                                Text(stringResource(R.string.bear_game_exit))
                            }
                            Button(onClick = {
                                lb.resetLastRank()
                                game.start()
                            }) {
                                Text(stringResource(R.string.bear_game_restart))
                            }
                        }
                    }
                }
            }
            GamePhase.RUNNING -> Unit
        }
    }
}

@Composable
private fun JumpHud(
    game: StarJumpGame,
    frame: Long,
    onExit: () -> Unit,
    onLeaderboard: () -> Unit,
    onCharacter: () -> Unit,
) {
    // frame 仅用于驱动 HUD 每帧刷新分数
    @Suppress("UNUSED_EXPRESSION")
    frame
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close_game),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${game.score}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text =
                    "★ ${game.starsCollected} · " +
                        stringResource(R.string.bear_game_best_label) + " ${game.best}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        GameCharacterButton(onClick = onCharacter)
        LeaderboardButton(onClick = onLeaderboard)
    }
}
