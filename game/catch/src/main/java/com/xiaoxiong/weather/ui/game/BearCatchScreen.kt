package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.stringOr
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import kotlinx.coroutines.delay

/** 横幅展示时长（毫秒）。 */
private const val CATCH_BANNER_MS = 1500L

private fun catchSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): CatchScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> CatchScene.FREEZING_RAIN
        isHailCode(sceneCode) -> CatchScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> CatchScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> CatchScene.RAIN_LIGHT
                        RainStrength.HEAVY -> CatchScene.RAIN_HEAVY
                        else -> CatchScene.RAIN
                    }
                FarmWeather.STORMY -> CatchScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> CatchScene.CLOUDY
                FarmWeather.SUNNY -> CatchScene.SUNNY
            }
    }

/** 快照清档条件：只有结算态不可续玩（与既有死亡清档时机一致）。 */
internal fun catchSnapshotShouldClear(phase: GamePhase): Boolean = phase == GamePhase.OVER

/**
 * 小熊接雨雪：竖屏接住类。小熊底部左右移动（手指跟随），接得分物、躲惩罚物。
 * 掉落物种类由当前天气决定：“接雨滴/雪花/硬币/花瓣，躲乌云/冰雹/闪电”。
 */
@Composable
fun BearCatchScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    var difficultyState by remember {
        mutableStateOf(BearCatchGame.loadDifficulty(prefs))
    }
    val lb =
        rememberLeaderboardState(
            prefs,
            catchLeaderboardKey(difficultyState),
            catchBestKey(difficultyState),
        )
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    var gearTint by remember {
        mutableStateOf(CatchGearTint.fromId(prefs.stringOr(CATCH_GEAR_TINT_KEY)))
    }
    var unlockBest by remember {
        mutableIntStateOf(catchBestAcrossDifficulties(prefs).coerceAtLeast(0))
    }
    val cueGate = remember { CatchCueGate() }
    var bannerKind by remember { mutableStateOf<CatchBannerKind?>(null) }
    var bannerToken by remember { mutableIntStateOf(0) }
    val game =
        remember {
            BearCatchGame().also {
                it.difficulty = difficultyState
                it.best = BearCatchGame.loadBestScore(prefs, difficultyState)
            }
        }
    // 进程重建（如旋转/被系统回收后重进）时恢复局中快照；坏档/无档保持标题
    LaunchedEffect(Unit) {
        GameAudio.preload(context)
        migrateCatchBestPrefs(prefs)
        unlockBest = catchBestAcrossDifficulties(prefs)
        if (!catchGearTintUnlocked(gearTint, unlockBest)) {
            gearTint = CatchGearTint.CLASSIC
            prefs.edit { putString(CATCH_GEAR_TINT_KEY, gearTint.id) }
        }
        game.best = BearCatchGame.loadBestScore(prefs, game.difficulty)
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
            shouldClear = ::catchSnapshotShouldClear,
            onRestored = {
                difficultyState = game.difficulty
                game.best = BearCatchGame.loadBestScore(prefs, game.difficulty)
            },
        )

    // 退出流程（HUD 关闭 / 暂停退出 / 返回键）：guard 先清档再抑制兜底落盘，
    // 随后的 ON_STOP/onDispose 不会把已退出的对局重新落盘
    fun exitGame() {
        guard.exit(session, prefs)
        onExit()
    }

    fun showBanner(kind: CatchBannerKind) {
        bannerKind = kind
        bannerToken++
    }

    fun pickDifficulty(difficulty: CatchDifficulty) {
        if (difficulty == difficultyState && difficulty == game.difficulty) return
        difficultyState = difficulty
        game.difficulty = difficulty
        game.persistDifficulty(prefs)
        game.best = BearCatchGame.loadBestScore(prefs, difficulty)
        unlockBest = catchBestAcrossDifficulties(prefs)
        if (!catchGearTintUnlocked(gearTint, unlockBest)) {
            gearTint = CatchGearTint.CLASSIC
            prefs.edit { putString(CATCH_GEAR_TINT_KEY, gearTint.id) }
        }
        lb.resetLastRank()
    }

    fun startRun() {
        lb.resetLastRank()
        cueGate.reset()
        bannerKind = null
        bannerToken = 0
        game.start()
    }

    // 死亡结算后清档：重进应从标题开始，而不是续到已结束的对局
    LaunchedEffect(game.phase) {
        if (game.phase == GamePhase.OVER) {
            game.clearSnapshot(prefs)
            GameAudio.play(GameCue.LOSE)
            unlockBest = catchBestAcrossDifficulties(prefs)
        }
    }
    LaunchedEffect(bannerToken) {
        if (bannerToken > 0) {
            delay(CATCH_BANNER_MS)
            bannerKind = null
        }
    }
    val texts = remember { mutableListOf<CatchFloatText>() }
    // 飘字在非组合的帧回调里生成，提前在组合期解析文案
    val blockedText = stringResource(R.string.bear_catch_blocked)
    val perfectText = stringResource(R.string.bear_catch_perfect)
    val catchWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（天气决定掉落物种类与节奏）
    LaunchedEffect(catchWeather, sceneCode) {
        game.scene = catchSceneFromWeather(catchWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites(characterState.selected)
    // 减弱动画：跳过雨雪/雷电/风暴粒子，只保留静态天空
    val reduceMotion = LocalReduceMotion.current
    var frame by remember { mutableLongStateOf(0L) }

    // 帧循环：切后台不推进、dt 钳制由共享层处理。
    // tick / 飘字推进只在对局运行（每帧）或节流帧（约 12fps）时执行。
    var slowTick by remember { mutableStateOf(0) }
    // 节流帧的 dt 不能直接用单帧值：否则非对局界面（结算飘字/死亡粒子等）会以
    // 真实速度的 1/5 慢动作运行。这里按真实帧累计 animDt 后整笔提交（对齐 farm 写法）。
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
                // 飘字整体推进（原地更新，避免每帧重建列表）
                val ite = texts.iterator()
                while (ite.hasNext()) {
                    val t = ite.next()
                    t.life -= worldDt
                    if (t.life <= 0f) {
                        ite.remove()
                    } else {
                        t.y = t.y - 46f * game.s * worldDt
                    }
                }
                var powerUpSoundPlayed = false
                val nowMs = now / 1_000_000L
                game.tick(
                    worldDt,
                    onCaught = { kind, points, perfect ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        when (kind) {
                            ItemKind.GIFT, ItemKind.BUBBLE -> {
                                if (!powerUpSoundPlayed) {
                                    powerUpSoundPlayed = true
                                    GameAudio.play(GameCue.POWERUP)
                                }
                            }
                            else -> {
                                if (cueGate.allowCatch(nowMs)) {
                                    GameAudio.play(GameCue.CATCH)
                                }
                            }
                        }
                        // 护盾泡泡不加分：跳过 "+0" 飘字，避免误导
                        if (kind != ItemKind.BUBBLE && points > 0) {
                            texts.add(
                                CatchFloatText(
                                    text = "+$points",
                                    x = game.catchX,
                                    y = game.catchY - game.catchR * 1.4f,
                                    life = 0.8f,
                                    color = if (perfect) Color(0xFFFFF59D) else Color(0xFFFFD54F),
                                ),
                            )
                        }
                        if (perfect) {
                            GameAudio.play(GameCue.GRAZE)
                            texts.add(
                                CatchFloatText(
                                    text = perfectText,
                                    x = game.catchX,
                                    y = game.catchY - game.catchR * 2.0f,
                                    life = 0.7f,
                                    color = Color(0xFFFFECB3),
                                ),
                            )
                        }
                    },
                    onHit = { kind ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        GameAudio.play(GameCue.HIT)
                        texts.add(
                            CatchFloatText(
                                text = "-${BearCatchLogic.damageOf(kind)}",
                                x = game.catchX,
                                y = game.catchY - game.catchR * 1.4f,
                                life = 0.8f,
                                color = Color(0xFFFF6E6E),
                            ),
                        )
                    },
                    onBlocked = {
                        // 护盾挡下：青色「格挡」飘字 + 轻震，不再飘红字「-N」
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        GameAudio.play(GameCue.BLOCK)
                        texts.add(
                            CatchFloatText(
                                text = blockedText,
                                x = game.catchX,
                                y = game.catchY - game.catchR * 1.4f,
                                life = 0.8f,
                                color = Color(0xFF4DD0E1),
                            ),
                        )
                    },
                    onMilestone = { milestone ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        GameAudio.play(GameCue.POWERUP)
                        showBanner(CatchBannerKind.Milestone(milestone))
                    },
                    onBurstStart = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        GameAudio.play(GameCue.WARNING)
                        showBanner(CatchBannerKind.BURST)
                    },
                    onSceneIntro = { scene ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        GameAudio.play(GameCue.WARNING)
                        showBanner(CatchBannerKind.SceneIntro(scene))
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        // 死亡即清档：不能只等 LaunchedEffect，进程被回收会漏掉清档而重复续玩
                        game.clearSnapshot(prefs)
                        game.recordBestScore(prefs, score)
                        unlockBest = catchBestAcrossDifficulties(prefs)
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
                    game.resize(IntSize(sz.width, sz.height), preserveRunning = pause.paused)
                    // 改窗口导致对局回准备态：立即清掉旧档，停在 READY 被杀也不会僵尸续玩
                    if (wasRunning && game.phase != GamePhase.RUNNING) game.clearSnapshot(prefs)
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(game.phase) {
                        // 仅对局中启用手势，避免与「开始」按钮/点按手势抢事件
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
            frame // 订阅每帧更新，触发逐帧重绘
            if (game.size == IntSize.Zero) return@Canvas
            // 命中惩罚的屏幕震动：整幅画面随机小位移
            val shake = if (game.shakeT > 0f) game.shakeT * 12f * game.s else 0f
            withTransform({
                translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * shake * 2f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * shake * 2f,
                )
            }) {
                drawCatchWorld(
                    game = game,
                    isDay = isDay ?: true,
                    sprites = bearSprites,
                    texts = texts,
                    reduceMotion = reduceMotion,
                    gearTint = gearTint,
                )
            }
        }

        CatchHud(
            game = game,
            onExit = { exitGame() },
            onPause = if (game.phase == GamePhase.RUNNING) pause::open else null,
            onLeaderboard = {
                lb.entries = lb.store.load()
                lb.show = true
            },
            onCharacter = { showCharacterPicker = true },
        )

        val activeBanner = bannerKind
        if (activeBanner != null && game.phase == GamePhase.RUNNING) {
            CatchEventBanner(
                text = catchBannerText(activeBanner),
                warning = activeBanner.isWarning,
            )
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
                    clearCatchLegacyBestKeys(prefs)
                    unlockBest = catchBestAcrossDifficulties(prefs)
                    if (!catchGearTintUnlocked(gearTint, unlockBest)) {
                        gearTint = CatchGearTint.CLASSIC
                        prefs.edit { putString(CATCH_GEAR_TINT_KEY, gearTint.id) }
                    }
                },
                onDismiss = { lb.show = false },
            )
        }

        when (game.phase) {
            GamePhase.READY -> {
                GameReadyCard(
                    title = stringResource(R.string.bear_catch_title),
                    hint = stringResource(R.string.bear_catch_hint),
                    bestLine = gameBestLabel() + " ${game.best}",
                    startText = stringResource(R.string.bear_catch_start),
                    onStart = { startRun() },
                    extra = {
                        CatchReadyExtras(
                            selectedDifficulty = difficultyState,
                            onDifficulty = ::pickDifficulty,
                            gearTint = gearTint,
                            unlockBest = unlockBest,
                            onGearTint = { tint ->
                                if (catchGearTintUnlocked(tint, unlockBest) && tint != gearTint) {
                                    gearTint = tint
                                    prefs.edit { putString(CATCH_GEAR_TINT_KEY, tint.id) }
                                }
                            },
                        )
                    },
                )
            }
            GamePhase.OVER -> {
                GameOverCard(
                    overTitle = stringResource(R.string.bear_catch_over),
                    scoreLine = "${game.score}",
                    bestLine = gameBestLabel() + " ${game.best}",
                    restartText = stringResource(R.string.bear_catch_restart),
                    onRestart = {
                        game.clearSnapshot(prefs)
                        startRun()
                    },
                    onViewRank = {
                        lb.entries = lb.store.load()
                        lb.show = true
                    },
                    onExit = { exitGame() },
                    rankLine =
                        if (lb.lastRank > 0) {
                            stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank)
                        } else {
                            null
                        },
                    extra =
                        if (game.maxCombo >= 2 || game.perfectCount > 0) {
                            {
                                Column {
                                    if (game.maxCombo >= 2) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.bear_catch_max_combo,
                                                    game.maxCombo,
                                                ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (game.perfectCount > 0) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.bear_catch_perfect_count,
                                                    game.perfectCount,
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
                scoreText = "${game.score}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    game.clearSnapshot(prefs)
                    startRun()
                },
                onExit = { exitGame() },
            )
        }
    }
}
