package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlinx.coroutines.delay

/** 金币音效最小间隔（纳秒）：同帧/连续吃多枚只响一次，避免爆音。 */
private const val COIN_SOUND_GAP_NANOS = 60_000_000L

/** 擦边 cue 最小间隔（毫秒）。 */
private const val GRAZE_CUE_MIN_INTERVAL_MS = 55L

/** 快照清档条件：只有结算态不可续玩（与既有死亡清档时机一致）。 */
internal fun jumpSnapshotShouldClear(phase: GamePhase): Boolean = phase == GamePhase.OVER

private fun runSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): RunScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> RunScene.FREEZING_RAIN
        isHailCode(sceneCode) -> RunScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> RunScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> RunScene.RAIN_LIGHT
                        RainStrength.HEAVY -> RunScene.RAIN_HEAVY
                        else -> RunScene.RAIN
                    }
                FarmWeather.STORMY -> RunScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> RunScene.CLOUDY
                FarmWeather.SUNNY -> RunScene.SUNNY
            }
    }

@Composable
fun BearGameScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val style = weatherSceneStyle(sceneCode, isDay, appDark = false)
    val pause = rememberGamePauseState()

    val prefs = rememberGamePrefs()

    var difficultyState by remember { mutableStateOf(loadRunDifficulty(prefs)) }
    val lbKey = remember(difficultyState) { ensureRunLeaderboardMigrated(prefs, difficultyState) }
    val bestKey = remember(difficultyState) { runBestKey(difficultyState) }
    val lb = rememberLeaderboardState(prefs, lbKey, bestKey)
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    val gameWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val game =
        remember {
            BearGame().also {
                it.difficulty = difficultyState
                it.best = loadRunBest(prefs, difficultyState)
            }
        }
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
            shouldClear = ::jumpSnapshotShouldClear,
            onRestored = {
                difficultyState = game.difficulty
                persistRunDifficulty(prefs, game.difficulty)
                game.best = loadRunBest(prefs, game.difficulty)
            },
        )
    LaunchedEffect(game.phase) {
        if (game.phase == GamePhase.OVER) GameAudio.play(GameCue.LOSE)
    }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（雨量强度决定雨点多少）
    LaunchedEffect(gameWeather, sceneCode) {
        game.scene = runSceneFromWeather(gameWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites(characterState.selected)
    // 减弱动画：跳过雨雪/雷电粒子，只保留静态天空与世界
    val reduceMotion = LocalReduceMotion.current
    var frame by remember { mutableLongStateOf(0L) }
    var milestoneBannerToken by remember { mutableIntStateOf(0) }
    var milestoneBannerScore by remember { mutableIntStateOf(0) }
    var iceBannerToken by remember { mutableIntStateOf(0) }
    var lastGrazeCueMs by remember { mutableLongStateOf(Long.MIN_VALUE) }
    // 主动退出视为放弃本局：guard 先清档再抑制兜底落盘，随后导航离开不会被 onDispose 复活。
    val exitGame: () -> Unit = {
        guard.exit(session, prefs)
        onExit()
    }

    fun applyDifficulty(difficulty: RunDifficulty) {
        difficultyState = difficulty
        game.difficulty = difficulty
        persistRunDifficulty(prefs, difficulty)
        game.best = loadRunBest(prefs, difficulty)
    }

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase == GamePhase.RUNNING -> pause.open()
            else -> exitGame()
        }
    }

    // 帧循环：切后台不推进、dt 钳制（防掉帧瞬移穿模）由共享层处理。
    // game.tick 只在对局运行（每帧）或节流帧（约 12fps）时调用；
    // frame 只在真正 tick 时更新，驱动 Canvas 重绘。
    var slowTick by remember { mutableStateOf(0) }
    // 节流帧的 dt 不能直接用单帧值：否则非对局动画会以真实速度的 1/5 慢动作运行。
    // 这里按真实帧累计 animDt，整笔提交给 tick（对齐 farm 的写法）。
    var animDt by remember { mutableFloatStateOf(0f) }
    var lastCoinSoundAt by remember { mutableLongStateOf(0L) }
    GameFrameLoop(
        paused = { lb.show || showCharacterPicker || pause.paused },
        onTick = { dt, now ->
            val running = game.phase == GamePhase.RUNNING
            // 标题/结算界面无需 60fps：每 5 帧处理一次（约 12fps），省电并减少重组
            animDt += dt
            if (running || slowTick % 5 == 0) {
                // 死亡结算走慢动作，其余界面按真实时间推进
                val worldDt = animDt * gameOverTimeScale(game.phase)
                animDt = 0f
                game.tick(
                    worldDt,
                    onCoin = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (now - lastCoinSoundAt >= COIN_SOUND_GAP_NANOS) {
                            lastCoinSoundAt = now
                            GameAudio.play(GameCue.COIN)
                        }
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        GameAudio.play(GameCue.HIT)
                        // 对局结束：清除快照，避免下次重进续上已死的一局
                        game.clearSnapshot(prefs)
                        game.best = persistRunBestIfHigher(prefs, game.difficulty, score)
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
                    onJump = { GameAudio.play(GameCue.JUMP) },
                    onGraze = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val nowMs = now / 1_000_000L
                        if (lastGrazeCueMs == Long.MIN_VALUE ||
                            nowMs - lastGrazeCueMs >= GRAZE_CUE_MIN_INTERVAL_MS
                        ) {
                            lastGrazeCueMs = nowMs
                            GameAudio.play(GameCue.GRAZE)
                        }
                    },
                    onMilestone = { score ->
                        milestoneBannerScore = score
                        milestoneBannerToken++
                        GameAudio.play(GameCue.POWERUP)
                    },
                    onIceUnlock = {
                        iceBannerToken++
                        GameAudio.play(GameCue.WARNING)
                    },
                )
                frame = now
            }
            if (!running) slowTick++
        },
    )

    LaunchedEffect(milestoneBannerToken) {
        if (milestoneBannerToken > 0) {
            delay(1400)
            milestoneBannerToken = 0
        }
    }
    LaunchedEffect(iceBannerToken) {
        if (iceBannerToken > 0) {
            delay(1600)
            iceBannerToken = 0
        }
    }

    // 浅色场景在渐变上叠纯黑暗角（与天气卡片同款兜底），保证白字标题/地面线可读；
    // 深色场景 scrimAlpha=0 时整层跳过，不产生多余合成。
    val scrimColor = remember(style.scrimAlpha) { Color.Black.copy(alpha = style.scrimAlpha) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(style.gradient))
                .onSizeChanged { sz ->
                    // 暂停中改尺寸（分屏/折叠）保留对局，避免菜单分数与实际进度不一致
                    val wasRunning = game.phase == GamePhase.RUNNING
                    game.resize(IntSize(sz.width, sz.height), preserveRunning = pause.paused)
                    // 改窗口导致对局回准备态：立即清掉旧档，停在 READY 被杀也不会僵尸续玩
                    if (wasRunning && game.phase != GamePhase.RUNNING) game.clearSnapshot(prefs)
                },
    ) {
        if (style.scrimAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(scrimColor),
            )
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (game.phase == GamePhase.READY) lb.resetLastRank()
                            game.tap(onJump = { GameAudio.play(GameCue.JUMP) })
                        }
                    },
        ) {
            frame // 每帧触发重绘
            if (game.size == IntSize.Zero) return@Canvas
            drawWorld(
                game,
                style.contentColor,
                isDay = isDay ?: true,
                sprites = bearSprites,
                reduceMotion = reduceMotion,
            )
        }

        GameHud(
            game = game,
            onExit = exitGame,
            onPause = if (game.phase == GamePhase.RUNNING) pause::open else null,
            onLeaderboard = {
                lb.entries = lb.store.load()
                lb.show = true
            },
            onCharacter = { showCharacterPicker = true },
        )

        if (milestoneBannerToken > 0 && iceBannerToken == 0) {
            RunBanner(text = stringResource(R.string.bear_run_milestone_banner, milestoneBannerScore))
        }
        if (iceBannerToken > 0) {
            RunBanner(text = stringResource(R.string.bear_run_ice_unlock_banner), warn = true)
        }

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
                    prefs.edit {
                        putInt(runBestKey(difficultyState), 0)
                        if (difficultyState == RunDifficulty.STANDARD) putInt(PREF_RUN_BEST_LEGACY, 0)
                    }
                },
                onDismiss = { lb.show = false },
            )
        }

        when (game.phase) {
            GamePhase.READY -> {
                GameReadyCard(
                    title = stringResource(R.string.bear_game_title),
                    hint = stringResource(R.string.bear_game_hint_jump),
                    bestLine = gameBestLabel() + " ${game.best}",
                    startText = stringResource(R.string.bear_game_tap_start),
                    onStart = {
                        lb.resetLastRank()
                        lastGrazeCueMs = Long.MIN_VALUE
                        milestoneBannerToken = 0
                        iceBannerToken = 0
                        game.start()
                    },
                    extra = {
                        RunReadyExtras(
                            difficulty = difficultyState,
                            onPickDifficulty = { applyDifficulty(it) },
                        )
                    },
                )
            }
            GamePhase.OVER -> {
                GameOverCard(
                    overTitle = stringResource(R.string.bear_game_over),
                    scoreLine = "${game.score()}",
                    bestLine = gameBestLabel() + " ${game.best}",
                    restartText = stringResource(R.string.bear_jump_restart),
                    onRestart = {
                        lb.resetLastRank()
                        lastGrazeCueMs = Long.MIN_VALUE
                        milestoneBannerToken = 0
                        iceBannerToken = 0
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
                    extra =
                        if (game.maxCombo >= 2 || game.grazeCount > 0) {
                            {
                                Column {
                                    if (game.maxCombo >= 2) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.bear_run_max_combo,
                                                    game.maxCombo,
                                                ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (game.grazeCount > 0) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.bear_run_graze,
                                                    game.grazeCount,
                                                ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
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
                scoreText = "${game.hudScore}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    lb.resetLastRank()
                    lastGrazeCueMs = Long.MIN_VALUE
                    milestoneBannerToken = 0
                    iceBannerToken = 0
                    game.clearSnapshot(prefs)
                    game.start()
                },
                onExit = exitGame,
            )
        }
    }
}
