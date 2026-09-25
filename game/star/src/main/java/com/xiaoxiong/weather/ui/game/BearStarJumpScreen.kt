package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlin.random.Random

/** 快照清档条件：只有结算态不可续玩（与既有死亡清档时机一致）。 */
internal fun starSnapshotShouldClear(phase: GamePhase): Boolean = phase == GamePhase.OVER

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
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    val lb = rememberLeaderboardState(prefs, "leaderboard_jump", "best_jump")
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    val game =
        remember {
            StarJumpGame().also { it.best = prefs.intOr("best_jump", 0) }
        }
    val jumpWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    // 进程重建（旋转/被杀）后恢复局中对局：由 GameSnapshotLifecycle 在进入时恢复，
    // 避免组合期写 snapshot state 触发额外重组；恢复失败保持 READY 不自动弹暂停。
    LaunchedEffect(Unit) {
        GameAudio.preload(context)
    }
    // 快照生命周期统一交给公共件：进入恢复 → OVER 清档 → ON_STOP/onDispose 兜底落盘 → 退出抑制。
    val session =
        remember(game, prefs) {
            gameSnapshotSession(
                save = game::saveSnapshot,
                restore = game::restoreSnapshot,
                clear = game::clearSnapshot,
            )
        }
    val guard =
        GameSnapshotLifecycle(
            session = session,
            prefs = prefs,
            phase = game.phase,
            shouldClear = ::starSnapshotShouldClear,
        )
    LaunchedEffect(game.phase) {
        if (game.phase == GamePhase.OVER) GameAudio.play(GameCue.LOSE)
    }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（雨量强度决定雨点多少）
    LaunchedEffect(jumpWeather, sceneCode) {
        game.scene = jumpSceneFromWeather(jumpWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites(characterState.selected)
    // 减弱动画：跳过雨雪/雷电粒子，只保留静态天空
    val reduceMotion = LocalReduceMotion.current
    var frame by remember { mutableLongStateOf(0L) }
    // 主动退出视为放弃本局：guard 先清档再抑制兜底落盘，随后导航离开不会被 onDispose 复活。
    val exitGame: () -> Unit = {
        guard.exit(session, prefs)
        onExit()
    }

    // READY / OVER 菜单不需要 60fps：节流到约 12fps。dt 按真实帧累计后整笔提交，
    // 避免节流状态下动画以真实速度的 1/5 慢动作运行（对齐 jump/catch/farm 写法）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showCharacterPicker || pause.paused },
        onTick = { dt, now ->
            val running = game.phase == GamePhase.RUNNING
            animDt += dt
            if (running || slowTick % 5 == 0) {
                // 死亡结算走慢动作，其余界面按真实时间推进
                val worldDt = animDt * gameOverTimeScale(game.phase)
                animDt = 0f
                game.tick(
                    worldDt,
                    onBounce = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        GameAudio.play(GameCue.JUMP)
                    },
                    onStar = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        GameAudio.play(GameCue.STAR)
                    },
                    onSpring = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        GameAudio.play(GameCue.JUMP)
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        GameAudio.play(GameCue.HIT)
                        // 对局结束：清除快照，避免下次重进续上已死的一局
                        game.clearSnapshot(prefs)
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

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase == GamePhase.RUNNING -> pause.open()
            else -> exitGame()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    // 暂停中改尺寸（分屏/折叠）保留对局，避免菜单分数与实际进度不一致
                    val wasRunning = game.phase == GamePhase.RUNNING
                    game.setDims(
                        sz.width.toFloat(),
                        sz.height.toFloat(),
                        density,
                        preserveRunning = pause.paused,
                    )
                    // 改窗口导致对局回准备态：立即清掉旧档，停在 READY 被杀也不会僵尸续玩
                    if (wasRunning && game.phase != GamePhase.RUNNING) game.clearSnapshot(prefs)
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
                drawJumpWorld(game, bearSprites, reduceMotion = reduceMotion)
            }
        }

        JumpHud(
            game = game,
            onExit = exitGame,
            onPause = if (game.phase == GamePhase.RUNNING) pause::open else null,
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
                GameReadyCard(
                    title = stringResource(R.string.bear_jump_title),
                    hint = stringResource(R.string.bear_jump_hint),
                    startText = stringResource(R.string.bear_jump_start),
                    onStart = {
                        lb.resetLastRank()
                        game.start()
                    },
                )
            }
            GamePhase.OVER -> {
                GameOverCard(
                    overTitle = stringResource(R.string.bear_jump_over),
                    scoreLine = "${game.score}",
                    bestLine = gameBestLabel() + " ${game.best}",
                    restartText = stringResource(R.string.bear_star_restart),
                    onRestart = {
                        lb.resetLastRank()
                        game.clearSnapshot(prefs)
                        game.start()
                    },
                    onViewRank = {
                        lb.entries = lb.store.load()
                        lb.show = true
                    },
                    onExit = exitGame,
                    rankLine =
                        if (lb.lastRank > 0) {
                            stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank)
                        } else {
                            null
                        },
                )
            }
            GamePhase.RUNNING -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = "${game.score}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    lb.resetLastRank()
                    game.clearSnapshot(prefs)
                    game.start()
                },
                onExit = exitGame,
            )
        }
    }
}

@Composable
private fun JumpHud(
    game: StarJumpGame,
    onExit: () -> Unit,
    onPause: (() -> Unit)?,
    onLeaderboard: () -> Unit,
    onCharacter: () -> Unit,
) {
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
        if (onPause != null) GamePauseButton(onClick = onPause)
        GameCharacterButton(onClick = onCharacter)
        LeaderboardButton(onClick = onLeaderboard)
    }
}
