package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlinx.coroutines.delay
import kotlin.math.max

private const val PREF_PLANE_DIFF = "plane_difficulty"
private const val PREF_PLANE_BEST_PREFIX = "plane_best_"
private const val PREF_PLANE_BEST_WAVE_PREFIX = "plane_best_wave_"
private const val PREF_PLANE_LEADERBOARD_PREFIX = "leaderboard_plane_"

/** 开局命数持久化 key（1..9，默认 3）。 */
internal const val PLANE_START_LIVES_KEY = "plane_start_lives"

/** 波次横幅展示时长（毫秒）。 */
private const val PLANE_WAVE_BANNER_MS = 1500L

/**
 * 手指上方偏移（**逻辑单位**）：让战机不被手指遮挡，同时保持「指哪打哪」的跟手手感。
 * 逻辑场宽固定 100、典型场高 180，10 单位约屏高的 1/18，远小于旧值 26 的拖拽延迟感。
 */
internal const val PLANE_FINGER_OFFSET_LOGICAL = 10f

/** 触摸逻辑坐标 -> 战机目标 y（手指上方固定偏移）；非有限值原样透传，由 [PlaneGame.movePlayerTo] 忽略。 */
internal fun planeTouchTargetY(touchLogicalY: Float): Float =
    if (touchLogicalY.isFinite()) touchLogicalY - PLANE_FINGER_OFFSET_LOGICAL else touchLogicalY

/** 读取持久化的开局命数并夹到 1..9（缺省 / 坏值回退默认 3）。 */
internal fun planeStartLivesOf(prefs: SharedPreferences): Int =
    planeClampStartLives(prefs.getInt(PLANE_START_LIVES_KEY, PlaneConfig.START_LIVES))

/** 开局命数夹取：1..9。 */
internal fun planeClampStartLives(value: Int): Int = value.coerceIn(PlaneConfig.START_LIVES_MIN, PlaneConfig.START_LIVES_MAX)

private fun planeBestKey(difficulty: PlaneDifficulty): String = PREF_PLANE_BEST_PREFIX + difficulty.id

private fun planeBestWaveKey(difficulty: PlaneDifficulty): String = PREF_PLANE_BEST_WAVE_PREFIX + difficulty.id

private fun planeLeaderboardKey(difficulty: PlaneDifficulty): String = PREF_PLANE_LEADERBOARD_PREFIX + difficulty.id + "_v1"

/** SETTLED（结算）无法续玩：快照生命周期在该阶段清档。 */
internal fun planeShouldClearSnapshot(phase: PlanePhase): Boolean = phase == PlanePhase.SETTLED

/**
 * 小熊战机：1942 风格竖屏卷轴弹幕射击。
 *
 * 世界渲染见 [drawPlaneWorld]（静态天空烘焙 + 视差层 + 精灵缓存 + 弹幕 / 粒子），
 * HUD / 主菜单 / 结算见 PlaneHud.kt；音效按 [PlaneGame.consumeEvents] 事件接线并做节流。
 */
@Composable
fun BearPlaneScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = rememberGamePrefs()
    val pause = rememberGamePauseState()
    val day = isDay ?: true
    val systemReduceMotion = LocalReduceMotion.current

    // 天气 / 场景样式：驱动天空渐变、scrim 与天气粒子
    val style = remember(sceneCode, isDay) { weatherSceneStyle(sceneCode, isDay, appDark = true) }
    val backdrop = remember(style) { planeBackdrop(style) }
    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val rain = remember(sceneCode) { rainStrengthOf(sceneCode) }
    val freezingRain = remember(sceneCode) { isFreezingRainCode(sceneCode) }
    val hail = remember(sceneCode) { isHailCode(sceneCode) }

    val game =
        remember {
            PlaneGame().also {
                it.setWeather(sceneCode, isDay)
                it.difficulty = PlaneDifficulty.fromId(prefs.getString(PREF_PLANE_DIFF, null))
                it.startLives = planeStartLivesOf(prefs)
            }
        }
    val frameState = remember { PlaneFrameState() }
    val cueGate = remember { PlaneCueGate() }
    LaunchedEffect(sceneCode, isDay) { game.setWeather(sceneCode, isDay) }
    LaunchedEffect(Unit) { GameAudio.preload(context) }

    // HUD 快照：只在数值变化时写入 Compose 状态，避免每帧重组
    var hudPhase by remember { mutableStateOf(game.phase) }
    var hudScore by remember { mutableIntStateOf(game.score) }
    var hudLives by remember { mutableIntStateOf(game.player.lives) }
    var hudBombs by remember { mutableIntStateOf(game.player.bombs) }
    var hudWeapon by remember { mutableIntStateOf(game.player.weaponLevel) }
    var hudWave by remember { mutableIntStateOf(game.waveIndex) }
    var hudBossActive by remember { mutableStateOf(game.bossActive) }

    var difficultyState by remember { mutableStateOf(game.difficulty) }
    var best by remember { mutableIntStateOf(0) }
    var bestWave by remember { mutableIntStateOf(0) }
    var startLives by remember { mutableIntStateOf(planeStartLivesOf(prefs)) }
    var soundEnabled by remember { mutableStateOf(GameAudio.settings(context).enabled) }
    var isNewRecord by remember { mutableStateOf(false) }
    var isNewWaveRecord by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var waveBannerToken by remember { mutableIntStateOf(0) }
    var waveBannerWave by remember { mutableIntStateOf(0) }
    var bossWarningVisible by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    var resultSubmitted by remember { mutableStateOf(false) }
    val lb = rememberLeaderboardState(prefs, planeLeaderboardKey(difficultyState), planeBestKey(difficultyState))

    // 快照生命周期统一走公共件：进入恢复（坏档退回标题）→ ON_STOP 落盘 →
    // onDispose 兜底 → 退出抑制 → SETTLED 结算清档。
    val snapshotSession =
        remember(game, prefs) {
            gameSnapshotSession(
                save = game::saveSnapshot,
                restore = game::restoreSnapshot,
                clear = game::clearSnapshot,
            )
        }
    val snapshotExitGuard =
        GameSnapshotLifecycle(
            session = snapshotSession,
            prefs = prefs,
            phase = hudPhase,
            shouldClear = ::planeShouldClearSnapshot,
            onRestored = {
                // 逻辑层把 PLAYING 恢复统一降级为 PAUSED：只要恢复成功就拉起暂停菜单，避免后台挨打
                if (game.phase == PlanePhase.PAUSED) pause.open()
                // 快照难度可能与 prefs 不同：同步状态与最高分 key，避免读写错档
                difficultyState = game.difficulty
                bossWarningVisible = game.bossWarningTicks > 0
            },
        )

    fun bestOf(difficulty: PlaneDifficulty): Int = prefs.getInt(planeBestKey(difficulty), 0)

    fun bestWaveOf(difficulty: PlaneDifficulty): Int = prefs.getInt(planeBestWaveKey(difficulty), 0)

    fun syncHud() {
        if (hudPhase != game.phase) hudPhase = game.phase
        if (hudScore != game.score) hudScore = game.score
        if (hudLives != game.player.lives) hudLives = game.player.lives
        if (hudBombs != game.player.bombs) hudBombs = game.player.bombs
        if (hudWeapon != game.player.weaponLevel) hudWeapon = game.player.weaponLevel
        if (hudWave != game.waveIndex) hudWave = game.waveIndex
        if (hudBossActive != game.bossActive) hudBossActive = game.bossActive
    }

    fun submitResult() {
        val key = planeBestKey(difficultyState)
        val prevBest = prefs.getInt(key, 0)
        isNewRecord = game.score > prevBest && game.score > 0
        if (game.score > prevBest) prefs.edit { putInt(key, game.score) }
        best = max(prevBest, game.score)
        // 无尽模式的正反馈：记录并刷新「历史最远波次」
        val waveKey = planeBestWaveKey(difficultyState)
        val prevBestWave = prefs.getInt(waveKey, 0)
        val reachedWave = game.waveIndex.coerceAtLeast(1)
        isNewWaveRecord = reachedWave > prevBestWave
        if (isNewWaveRecord) prefs.edit { putInt(waveKey, reachedWave) }
        bestWave = max(prevBestWave, reachedWave)
        if (game.score > 0) {
            lb.lastRank =
                lb.store.submit(
                    LeaderboardEntry(
                        score = game.score,
                        timestamp = System.currentTimeMillis(),
                        city = cityName,
                        weather = leaderboardWeatherOf(context, sceneCode),
                    ),
                ) ?: 0
            lb.entries = lb.store.load()
        } else {
            // 0 分不入榜，也不显示上一局残留名次（与 jump / star / catch 口径一致）
            lb.resetLastRank()
        }
    }

    fun exitGame() {
        snapshotExitGuard.exit(snapshotSession, prefs)
        onExit()
    }

    fun startGame() {
        game.clearSnapshot(prefs)
        game.startLives = startLives
        game.startGame()
        pause.resume()
        resultSubmitted = false
        isNewRecord = false
        isNewWaveRecord = false
        bossWarningVisible = false
        waveBannerToken = 0
        waveBannerWave = 0
        cueGate.reset()
        frameState.reset()
        syncHud()
        frame++
    }

    // 恢复后（无论成败）对齐视觉基线，避免把恢复瞬间的波次 / 擦弹误判成新事件
    LaunchedEffect(Unit) {
        frameState.onRestore(game)
        waveBannerWave = game.waveIndex
        best = bestOf(game.difficulty)
        bestWave = bestWaveOf(game.difficulty)
        syncHud()
        frame++
    }
    // 退出对局时释放天空 / 精灵等烘焙缓存，二次进入按需重建
    DisposableEffect(Unit) {
        onDispose { clearPlaneRenderCaches() }
    }

    GameFrameLoop(
        paused = { pause.paused || showLeaderboard },
        onTick = { dt, nowNanos ->
            game.tick(dt)
            frameState.onTick(game)
            if (game.waveIndex != frameState.lastWaveIndex) {
                val started = game.waveIndex > 0 && game.phase == PlanePhase.PLAYING
                frameState.lastWaveIndex = game.waveIndex
                if (started && game.waveIndex % PLANE_BOSS_EVERY != 0) {
                    // 横幅文案取真实波号（token 只做可见性 / 计时，Boss 波不递增 token）
                    waveBannerWave = game.waveIndex
                    waveBannerToken++
                }
            }
            val events = game.consumeEvents()
            val nowMs = nowNanos / 1_000_000L
            if (events.shot && cueGate.allowShot(game.shotCount)) {
                GameAudio.play(planeShotCue(game.player.weaponLevel))
            }
            if (events.explosion) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (cueGate.allowExplosion(nowMs)) GameAudio.play(GameCue.EXPLOSION)
            }
            if (events.powerup) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                frameState.onPowerUp()
                GameAudio.play(GameCue.POWERUP)
            }
            if (events.playerHit) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                frameState.onPlayerHit()
                GameAudio.play(GameCue.HIT)
            }
            if (events.bomb) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                frameState.onBombUsed(nowMs)
                GameAudio.play(GameCue.BOMB)
            }
            if (events.bossWarning) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                bossWarningVisible = true
                GameAudio.play(GameCue.WARNING)
            }
            if (bossWarningVisible && game.bossWarningTicks == 0) bossWarningVisible = false
            if (events.bossDown) {
                frameState.onBossDown(nowMs)
                // Boss 击破：逻辑层不再置 explosion，这里补一次爆炸声；同帧已有普通爆炸时跳过，避免双播
                if (!events.explosion && cueGate.allowExplosion(nowMs)) GameAudio.play(GameCue.EXPLOSION)
            }
            if (events.win) GameAudio.play(GameCue.WIN)
            if (events.lose) GameAudio.play(GameCue.LOSE)
            if (game.phase == PlanePhase.SETTLED && !resultSubmitted) {
                resultSubmitted = true
                submitResult()
                game.clearSnapshot(prefs)
            }
            syncHud()
            frame++
        },
    )

    BackHandler {
        when {
            pause.showMenu -> {
                pause.resume()
                game.resume()
                frame++
            }
            game.phase == PlanePhase.PLAYING || game.phase == PlanePhase.PAUSED -> {
                pause.open()
                game.pause()
                frame++
            }
            else -> exitGame()
        }
    }

    LaunchedEffect(waveBannerToken) {
        if (waveBannerToken > 0) {
            delay(PLANE_WAVE_BANNER_MS)
            waveBannerToken = 0
        }
    }

    val fieldCd = stringResource(R.string.cd_plane_field)
    val backdropBrush = remember(backdrop) { Brush.verticalGradient(backdrop) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backdropBrush),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = fieldCd }
                    .onSizeChanged {
                        game.resize(it.width.toFloat(), it.height.toFloat())
                        frame++
                    }.pointerInput(Unit) {
                        // 无 touch slop 的即时拖动：按下即跟手，逐事件更新（对齐 pool / hockey / star 的指针写法）。
                        // 偏移固定为手指上方少量逻辑单位；非有限值由 movePlayerTo 忽略，边界由其钳制。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.movePlayerTo(
                                game.toLogicalX(down.position.x),
                                planeTouchTargetY(game.toLogicalY(down.position.y)),
                            )
                            frame++
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    game.movePlayerTo(
                                        game.toLogicalX(change.position.x),
                                        planeTouchTargetY(game.toLogicalY(change.position.y)),
                                    )
                                    change.consume()
                                    frame++
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        ) {
            // 读取 Compose 帧状态只为在 draw 阶段注册重绘依赖：不触发任何重组
            frame
            drawPlaneWorld(
                game = game,
                state = frameState,
                style = style,
                isDay = day,
                weather = weather,
                rain = rain,
                freezingRain = freezingRain,
                hail = hail,
                reduceMotion = systemReduceMotion,
            )
        }

        // HUD：运行 / 暂停阶段展示，标题与结算由各自卡片覆盖
        if (hudPhase != PlanePhase.READY && hudPhase != PlanePhase.SETTLED) {
            // 同时避开状态栏与 3 键导航栏：右下炸弹按钮不能被导航栏盖住
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                PlaneTopBar(
                    score = hudScore,
                    best = best,
                    wave = hudWave,
                    bossActive = hudBossActive,
                    onPause = {
                        GameAudio.play(GameCue.UI_CLICK)
                        pause.open()
                        game.pause()
                        frame++
                    },
                )
                PlaneBossBar(
                    game = game,
                    visible = hudBossActive,
                    frame = { frame },
                )
                Box(modifier = Modifier.weight(1f))
                PlaneBottomBar(
                    lives = hudLives,
                    maxLives = game.startLives,
                    bombs = hudBombs,
                    weaponLevel = hudWeapon,
                    bombEnabled = hudBombs > 0 && hudPhase == PlanePhase.PLAYING,
                    onBomb = {
                        game.useBomb()
                        frame++
                    },
                )
            }
        }

        // 波次 / Boss 预警横幅（主题卡片）
        if (waveBannerToken > 0 && !bossWarningVisible) {
            PlaneWaveBanner(text = stringResource(R.string.bear_plane_wave, waveBannerWave.coerceAtLeast(1)))
        }
        if (bossWarningVisible) {
            PlaneBossWarningBanner(text = stringResource(R.string.bear_plane_boss_warning))
        }

        when (hudPhase) {
            PlanePhase.READY ->
                PlaneReadyOverlay(
                    game = game,
                    best = best,
                    bestWave = bestWave,
                    startLives = startLives,
                    soundEnabled = soundEnabled,
                    frame = { frame },
                    isDay = day,
                    reduceMotion = systemReduceMotion,
                    onPick = { difficulty ->
                        GameAudio.play(GameCue.UI_CLICK)
                        difficultyState = difficulty
                        game.difficulty = difficulty
                        prefs.edit { putString(PREF_PLANE_DIFF, difficulty.id) }
                        best = bestOf(difficulty)
                        bestWave = bestWaveOf(difficulty)
                        frame++
                    },
                    onStartLivesChange = { value ->
                        val clamped = planeClampStartLives(value)
                        if (clamped != startLives) {
                            GameAudio.play(GameCue.UI_CLICK)
                            startLives = clamped
                            game.startLives = clamped
                            prefs.edit { putInt(PLANE_START_LIVES_KEY, clamped) }
                            frame++
                        }
                    },
                    onStart = {
                        GameAudio.play(GameCue.UI_CLICK)
                        startGame()
                    },
                    onToggleSound = { enabled ->
                        soundEnabled = enabled
                        GameAudio.setEnabled(context, enabled)
                        if (enabled) GameAudio.play(GameCue.UI_CLICK)
                    },
                    onLeaderboard = {
                        GameAudio.play(GameCue.UI_CLICK)
                        lb.entries = lb.store.load()
                        showLeaderboard = true
                    },
                )
            PlanePhase.SETTLED ->
                PlaneSettledOverlay(
                    game = game,
                    best = best,
                    bestWave = bestWave,
                    isNewWaveRecord = isNewWaveRecord,
                    rank = lb.lastRank,
                    isNewRecord = isNewRecord,
                    onRestart = {
                        GameAudio.play(GameCue.UI_CLICK)
                        lb.resetLastRank()
                        startGame()
                    },
                    onExit = {
                        GameAudio.play(GameCue.UI_CLICK)
                        exitGame()
                    },
                    onLeaderboard = {
                        GameAudio.play(GameCue.UI_CLICK)
                        lb.entries = lb.store.load()
                        showLeaderboard = true
                    },
                )
            else -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = stringResource(R.string.bear_plane_score, hudScore),
                onResume = {
                    pause.resume()
                    game.resume()
                    frame++
                },
                onRestart = {
                    pause.resume()
                    startGame()
                },
                onExit = { exitGame() },
            )
        }
    }

    if (showLeaderboard) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_plane_title),
            entries = lb.entries,
            onClear = {
                lb.store.clear()
                lb.entries = emptyList()
                lb.resetLastRank()
                best = 0
            },
            onDismiss = { showLeaderboard = false },
        )
    }
}
