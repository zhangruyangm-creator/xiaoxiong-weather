package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.booleanOr
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.game.stringOr
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.game.PoolGame.Phase
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlin.math.max
import kotlin.math.min

/** MATCH_OVER 无法续玩：快照生命周期在该阶段清档；FRAME_OVER 保留以便恢复后继续点下一局。 */
internal fun poolShouldClearSnapshot(phase: Phase): Boolean = phase == Phase.MATCH_OVER

/**
 * 小熊台球：竖屏八球。玩家与 AI 轮番击球，清空本方花色后打进黑八赢下一局，
 * 先赢下设定局数者获得整场胜利。底部左侧白球控件调整高低杆/左右塞，拖拖桌面蓄力瞄准。
 */
@Composable
fun BearPoolScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // 全局音效：入口预加载，事件处直接 play；开关 / 音量为全局设置（GameAudio 管理）
    LaunchedEffect(Unit) {
        GameAudio.preload(context)
    }
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showShotHistory by remember { mutableStateOf(false) }
    var doneLessons by remember { mutableStateOf(loadDoneLessons(prefs)) }
    var clothTheme by remember { mutableIntStateOf(loadClothTheme(prefs)) }
    var reduceMotionPref by remember { mutableStateOf(prefs.booleanOr(PREF_POOL_REDUCE_MOTION, false)) }
    var storedReplay by remember { mutableStateOf(loadReplay(prefs)) }
    // 系统级「减弱动态效果」与游戏内设置任一开启即生效
    val systemReduceMotion = LocalReduceMotion.current
    val reduceMotion = reduceMotionPref || systemReduceMotion

    // 退出时释放静态台面层的全屏位图缓存；音效对象由全局 GameAudio 管理，无需 release
    DisposableEffect(Unit) {
        onDispose {
            // 退出对局时释放静态台面层的两张全屏 Bitmap；二次进入按需重建
            clearPoolTableLayerCache()
        }
    }

    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val rain = remember(sceneCode) { rainStrengthOf(sceneCode) }
    val freezingRain = remember(sceneCode) { isFreezingRainCode(sceneCode) }
    val hail = remember(sceneCode) { isHailCode(sceneCode) }
    val style = remember(sceneCode, isDay) { weatherSceneStyle(sceneCode, isDay, appDark = false) }

    val game =
        remember {
            PoolGame().also {
                it.difficulty = difficultyOf(prefs.getString(PREF_POOL_DIFF, "normal"))
                it.aids = aidsOf(prefs.getString(PREF_POOL_AIDS, "full"))
                it.raceTo = clampRaceTo(prefs.intOr(PREF_POOL_RACE, 2))
                it.mode = modeOf(prefs.stringOr(PREF_POOL_MODE))
                it.best = prefs.intOr(bestModeKey(it.mode), 0)
                it.reduceMotion = reduceMotion
                it.clothTheme = clothTheme
            }
        }
    LaunchedEffect(reduceMotion) {
        game.reduceMotion = reduceMotion
    }
    LaunchedEffect(clothTheme) {
        game.clothTheme = clothTheme
    }
    // 恢复进行中的对局（旋转屏幕 / 进程被回收后重进）。恢复在 LaunchedEffect 而非
    // remember 初始化块：恢复会写 phase/balls 等 snapshot state，组合期写入会触发额外重组；
    // 且若该次组合被丢弃（如立即导航离开），组合期执行恢复的副作用不应生效。
    // 快照生命周期统一走公共件：进入恢复 → ON_STOP 落盘 → onDispose 兜底 → 退出抑制 → MATCH_OVER 清档
    // （FRAME_OVER 保留以便恢复后继续点下一局；game.saveSnapshot 内部对不可续玩阶段也会清档）。
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
            phase = game.phase,
            shouldClear = ::poolShouldClearSnapshot,
            onRestored = {
                // 快照里的 best 可能是旧计分口径（v1 帧数）：恢复后统一以当前模式的 v2 记录为准
                game.best = prefs.intOr(bestModeKey(game.mode), 0)
                // 局中改设置只暂存在内存 + prefs：进程被杀后内存暂存丢失，按 prefs 重建暂存，
                // 否则设置界面会显示旧值，保存时还会把用户已选的下一场规则改回去
                pendingRulesAfterSnapshotRestore(
                    snapshotRaceTo = game.raceTo,
                    snapshotMode = game.mode,
                    prefsRaceTo = prefs.intOr(PREF_POOL_RACE, 2),
                    prefsMode = modeOf(prefs.stringOr(PREF_POOL_MODE)),
                )?.let { (race, mode) -> game.stageRuleChange(race, mode) }
            },
        )
    var progress by remember { mutableStateOf(loadPoolProgress(prefs)) }
    var pendingUnlocks by remember { mutableStateOf<List<PoolAchievement>>(emptyList()) }

    // 关卡 / 每日挑战状态
    val todayDate = remember { todayEpochDay() }
    val dailyChallenge = remember(todayDate) { PoolChallenges.daily(todayDate) }
    var showChallenges by remember { mutableStateOf(false) }
    var showDaily by remember { mutableStateOf(false) }
    var showDailyLeaderboard by remember { mutableStateOf(false) }
    var dailyEntries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var clearedLevels by remember { mutableStateOf(loadClearedLevels(prefs)) }
    var bestLevels by remember { mutableStateOf(loadBestLevels(prefs)) }
    var dailyBest by remember(todayDate) { mutableIntStateOf(prefs.intOr(dailyBestKey(todayDate), 0)) }
    var lastChallengeResult by remember { mutableStateOf<ChallengeResult?>(null) }

    // 排行榜按模式分开，练习模式保留高分（其它保留最近局，惨败不被挤出）
    val mode = game.mode
    val lb =
        rememberLeaderboardState(
            prefs,
            leaderboardKey(mode),
            bestModeKey(mode),
            retainRecent = mode != PoolMode.PRACTICE,
        )
    LaunchedEffect(mode) {
        game.best = prefs.intOr(bestModeKey(mode), 0)
        lb.resetLastRank()
    }

    fun startDailyChallenge() {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startChallenge(dailyChallenge, todayDate)
        showDaily = false
    }

    fun startLevel(challenge: PoolChallenge) {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startChallenge(challenge)
        showChallenges = false
    }

    fun startLesson(lesson: PoolLesson) {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startLesson(lesson)
        showTutorial = false
    }

    var frame by remember { mutableLongStateOf(0L) }
    val reportScene by rememberUpdatedState(sceneCode)
    val reportCity by rememberUpdatedState(cityName)

    // 结算落盘 + 提交排行榜（幂等）。BackHandler / LaunchedEffect 进入时都调用，
    // 避免在 MATCH_OVER 浮层出现前退出丢掉整场战绩。
    fun submitResultIfNeeded() {
        if (game.resultSubmitted) return
        if (game.phase != Phase.MATCH_OVER && !game.matchOver()) return
        // 教学不与排行榜 / 生涯统计挂钩
        if (game.lesson != null) {
            game.resultSubmitted = true
            GameAudio.play(if (game.lessonPassed) GameCue.WIN else GameCue.LOSE)
            return
        }
        game.resultSubmitted = true

        // 关卡 / 每日挑战：只记录清台成绩，不进入常规排行榜与生涯成就统计
        val challenge = game.challenge
        if (challenge != null) {
            val cleared = game.match.playerFrames > 0
            // 清台即胜（WIN），未清台为未通过（LOSE）
            GameAudio.play(if (cleared) GameCue.WIN else GameCue.LOSE)
            if (!cleared) return
            val shots = game.shots
            val score = game.challengeScore()
            val daily = challenge.id == PoolChallenges.DAILY_ID
            if (daily) {
                val date = if (game.challengeDate > 0L) game.challengeDate else todayDate
                val bestKey = dailyBestKey(date)
                val prev = prefs.intOr(bestKey, 0)
                val newRecord = score > prev
                if (newRecord) prefs.edit { putInt(bestKey, score) }
                dailyBest = max(prev, score)
                runCatching {
                    GameLeaderboardStore(prefs, dailyLeaderboardKey(date)).submit(
                        LeaderboardEntry(
                            score = score,
                            timestamp = System.currentTimeMillis(),
                            city = reportCity,
                            weather = leaderboardWeatherOf(context, reportScene),
                            label = context.getString(R.string.bear_pool_shots_label, shots),
                        ),
                    )
                }
                lastChallengeResult =
                    ChallengeResult(challengeDisplayName(challenge), challenge.par, shots, score, newRecord, daily = true)
            } else {
                val bestKey = bestLevelKey(challenge.id)
                val prev = prefs.intOr(bestKey, Int.MAX_VALUE)
                val newRecord = shots < prev
                if (newRecord) prefs.edit { putInt(bestKey, shots) }
                prefs.edit { putBoolean(clearedLevelKey(challenge.id), true) }
                clearedLevels = clearedLevels + challenge.id
                bestLevels = bestLevels + (challenge.id to min(prev, shots))
                lastChallengeResult =
                    ChallengeResult(challengeDisplayName(challenge), challenge.par, shots, score, newRecord, daily = false)
            }
            return
        }

        val won = game.match.playerFrames > game.match.aiFrames
        val practice = game.match.mode == PoolMode.PRACTICE
        val cleared = practice && game.match.playerFrames > 0
        // 整场 / 练习结束：胜 WIN、负 LOSE（练习以是否清台计胜负）
        val playerWon = if (practice) cleared else won
        GameAudio.play(if (playerWon) GameCue.WIN else GameCue.LOSE)
        // 对局类排行榜按「净胜」排序（胜局-负局），而不是玩家赢下的总局数；
        // 同净胜时失局少者靠前（3:0 优于 4:1）
        val score = if (practice) game.practiceScore() else poolRaceScore(game.match.playerFrames, game.match.aiFrames)
        val label =
            if (practice) {
                context.getString(R.string.bear_pool_shots_label, game.shots)
            } else {
                "${game.match.playerFrames} : ${game.match.aiFrames}"
            }

        // 生涯统计：任何模式每场都计入场次/局数/进球/单杆
        val advanced =
            progress.advance(
                won = if (practice) cleared else won,
                framesPlayed = game.match.playerFrames + game.match.aiFrames,
                aiFrames = game.match.aiFrames,
                potsThisMatch = game.matchPots,
                bestShotThisMatch = game.bestShot,
                breakAndRun = game.breakRunsThisMatch > 0,
                mode = game.match.mode,
                // 本场锁定的难度：局中临时调难度不影响本场成就记账
                difficulty = game.matchDifficulty,
                practiceCleared = cleared,
                practiceScore = if (practice) score else 0,
            )
        // 新解锁成就（弹窗一次）
        val newly = PoolAchievement.entries.filter { it.isUnlocked(advanced) && it.id !in progress.unlocked }
        if (newly.isNotEmpty()) pendingUnlocks = pendingUnlocks + newly
        val withUnlocks =
            if (newly.isEmpty()) {
                advanced
            } else {
                advanced.copy(unlocked = advanced.unlocked + newly.map { it.id })
            }
        progress = withUnlocks
        savePoolProgress(prefs, withUnlocks)

        if (score > game.best) {
            game.best = score
            prefs.edit { putInt(bestModeKey(game.match.mode), score) }
        }
        val rank =
            lb.store.submit(
                poolLeaderboardEntry(
                    practice = practice,
                    playerFrames = game.match.playerFrames,
                    aiFrames = game.match.aiFrames,
                    practiceScore = game.practiceScore(),
                    label = label,
                    timestamp = System.currentTimeMillis(),
                    city = reportCity,
                    weather = leaderboardWeatherOf(context, reportScene),
                ),
                *POOL_LEGACY_LEADERBOARD_KEYS.toTypedArray(),
            )
        // 负净胜（如首局 0:5）不展示名次；榜单本身仍保留最近记录
        lb.lastRank = leaderboardDisplayRank(score, rank)
        lb.entries = lb.store.load()
        // 持久化最近一场回放（足够冷启动重现最后一局），并同步本会话的「回放上一场」入口
        saveReplay(prefs, game)
        storedReplay = loadReplay(prefs)
    }

    // 明确退出：清掉进行中快照并抑制兜底落盘，避免「退出后又能续玩」
    fun exitGame() {
        snapshotExitGuard.exit(snapshotSession, prefs)
        onExit()
    }

    /**
     * 新一场按最新设置开局：旧快照恢复后内存里可能还是旧赛制，
     * 开新比赛时统一从 prefs 同步赛制/模式并清掉暂存，避免自定义设置不生效。
     */
    fun startFreshMatch() {
        game.raceTo = clampRaceTo(prefs.intOr(PREF_POOL_RACE, 2))
        game.mode = modeOf(prefs.stringOr(PREF_POOL_MODE))
        game.clearPendingRules()
        game.startMatch()
    }

    // 暂停菜单「重新开始」：优先重开当前教学 / 关卡 / 每日挑战，否则重开普通对局
    fun restartCurrentGame() {
        // 重开前清掉旧快照，避免新对局尚未落盘时旧的续玩档被恢复
        game.clearSnapshot(prefs)
        val lesson = game.lesson
        val challenge = game.challenge
        when {
            lesson != null -> startLesson(lesson)
            challenge != null ->
                if (challenge.id == PoolChallenges.DAILY_ID) {
                    startDailyChallenge()
                } else {
                    startLevel(challenge)
                }
            else -> {
                lb.resetLastRank()
                startFreshMatch()
            }
        }
    }

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase == Phase.AIMING || game.phase == Phase.SHOOTING -> pause.open()
            else -> {
                submitResultIfNeeded()
                exitGame()
            }
        }
    }

    // READY / FRAME_OVER / MATCH_OVER 无对局物理推进，节流到约 12fps；
    // dt 仍按真实帧累计后整笔提交，避免慢动作计时/动画失真（对齐 hockey/jump）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showSettings || showCharacterPicker || showAchievements || pendingUnlocks.isNotEmpty() || pause.paused },
        onTick = { dt, now ->
            val active = game.phase == Phase.AIMING || game.phase == Phase.SHOOTING
            animDt += dt
            if (active || slowTick % 5 == 0) {
                val worldDt = animDt
                animDt = 0f
                game.tick(worldDt)
                if (game.pendingHapticCue) {
                    game.pendingHapticCue = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 出杆：短促击打音
                    GameAudio.play(GameCue.CUE)
                }
                if (game.pendingHapticHit) {
                    game.pendingHapticHit = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 球-球碰撞：只在每杆首次白球接触时响一次
                    GameAudio.play(GameCue.HIT)
                }
                if (game.pendingHapticPot) {
                    game.pendingHapticPot = false
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    // 落袋：带下坠尾音的进袋音
                    GameAudio.play(GameCue.POT)
                }
                if (game.pendingHapticRail) {
                    game.pendingHapticRail = false
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    // 碰库：轻碰木音
                    GameAudio.play(GameCue.RAIL)
                }
                if (game.pendingHapticFoul) {
                    game.pendingHapticFoul = false
                    // 犯规：低哑提示音（无震动，避免与击球反馈混淆）
                    GameAudio.play(GameCue.FOUL)
                }
                frame = now
            }
            if (!active) slowTick++
        },
    )

    LaunchedEffect(game.phase) {
        if (game.phase == Phase.MATCH_OVER) submitResultIfNeeded()
    }

    // 教学通过：持久化通过状态（按课次）。
    LaunchedEffect(game.lesson?.id, game.lessonPassed) {
        val lesson = game.lesson
        if (lesson != null && game.lessonPassed && lesson.id !in doneLessons) {
            prefs.edit { putBoolean(lessonDoneKey(lesson.id), true) }
            doneLessons = doneLessons + lesson.id
        }
    }

    val poolTableCd = stringResource(R.string.cd_pool_table)
    // 底部控制区高度：把台面布局让出来，避免杆法盘/力度条压住台呢与左下角袋口。
    var controlsBandPx by remember { mutableIntStateOf(0) }
    val controlsBandDp = with(LocalDensity.current) { controlsBandPx.toDp() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(style.gradient)),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = controlsBandDp)
                    .semantics { contentDescription = poolTableCd }
                    .onSizeChanged { sz -> game.resize(sz.width.toFloat(), sz.height.toFloat()) }
                    .pointerInput(Unit) {
                        // 不以 phase 为 key：阶段切换会取消重建手势协程，手指已按下的拖动会「死掉」
                        // 直到抬手重按。READY / 结算阶段本就不读触摸点，常开手势无副作用。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.onDown(down.position.x, down.position.y)
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        game.onMove(change.position.x, change.position.y)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                game.onUp()
                            }
                        }
                    },
        ) {
            frame // 每帧触发重绘
            val day = isDay ?: true
            drawPoolWeather(game, weather, rain, freezingRain, hail, style, day, reduceMotion)
            drawPoolTable(game, style, day)
            drawPoolBalls(game, style, day)
            drawPoolAim(game, game.aids)
            drawPoolEffects(game)
        }

        if (game.phase != Phase.READY && !game.replaying) {
            PoolScoreboard(game)
        }

        // 教学进行中：顶部提示 + 目标
        game.lesson?.let { lesson ->
            if (game.phase != Phase.READY && game.phase != Phase.MATCH_OVER && !game.replaying) {
                PoolLessonBanner(lesson)
            }
        }

        // 击球历史面板（可展开）
        if (showShotHistory && game.phase != Phase.READY && !game.replaying) {
            ShotHistoryPanel(game.shotHistory)
        }

        if ((game.phase == Phase.AIMING || game.phase == Phase.SHOOTING) && !game.replaying) {
            PoolControls(
                game = game,
                onBeginPlacing = { game.beginPlacing() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { controlsBandPx = it.height },
            )
        }

        if (game.replaying) {
            ReplayOverlay(
                progress = game.replayProgress,
                total = game.replayTotal,
                onStop = { game.stopReplay() },
            )
        }

        when (game.phase) {
            Phase.READY -> {
                OverlayScrim {
                    Text(
                        text = stringResource(R.string.bear_pool_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4E342E),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_pool_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6D4C41),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    ModeSelector(
                        mode = game.displayMode(),
                        onSelect = {
                            game.mode = it
                            game.clearPendingRules()
                            prefs.edit { putString(PREF_POOL_MODE, modeKey(it)) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = configLine(game),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8D6E63),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            lb.resetLastRank()
                            startFreshMatch()
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_start))
                    }
                    TextButton(onClick = { showChallenges = true }) {
                        Text(stringResource(R.string.bear_pool_levels))
                    }
                    TextButton(
                        onClick = {
                            doneLessons = loadDoneLessons(prefs)
                            showTutorial = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_tutorial))
                    }
                    TextButton(
                        onClick = {
                            dailyBest = prefs.intOr(dailyBestKey(todayDate), 0)
                            showDaily = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_daily))
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.bear_pool_settings))
                    }
                    TextButton(
                        onClick = {
                            lb.entries = lb.store.load()
                            lb.show = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_leaderboard))
                    }
                    TextButton(onClick = { showAchievements = true }) {
                        Text(stringResource(R.string.bear_pool_achievements))
                    }
                    storedReplay?.let { stored ->
                        TextButton(
                            onClick = {
                                game.loadReplay(
                                    mode = modeOf(stored.mode),
                                    seed = stored.seed,
                                    breaker = runCatching { PoolSide.valueOf(stored.breaker) }.getOrDefault(PoolSide.PLAYER),
                                    shots =
                                        stored.shots.map {
                                            PoolGame.RecordedShot(it.angle, it.power, it.side, it.vertical, it.elevation, it.x, it.y)
                                        },
                                )
                                if (game.startReplay()) {
                                    lb.resetLastRank()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_replay_last))
                        }
                    }
                }
            }

            Phase.FRAME_OVER -> {
                OverlayScrim {
                    Text(
                        text =
                            if (game.frameWinner == PoolSide.PLAYER) {
                                stringResource(R.string.bear_pool_frame_win)
                            } else {
                                stringResource(R.string.bear_pool_frame_lose)
                            },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4E342E),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_pool_frames, game.match.playerFrames, game.match.aiFrames),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6D4C41),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { game.nextFrame() }) {
                        Text(stringResource(R.string.bear_pool_next_frame))
                    }
                    if (game.replayShots().isNotEmpty()) {
                        TextButton(onClick = { game.startReplay() }) {
                            Text(stringResource(R.string.bear_pool_replay))
                        }
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.bear_pool_settings))
                    }
                }
            }

            Phase.MATCH_OVER -> {
                val challenge = game.challenge
                val lesson = game.lesson
                val playerWon = game.match.playerFrames >= game.raceTo
                val practice = game.mode == PoolMode.PRACTICE
                if (lesson != null) {
                    PoolLessonResultOverlay(
                        lesson = lesson,
                        passed = game.lessonPassed,
                        onRetry = { startLesson(lesson) },
                        onNext = { next -> startLesson(next) },
                        onMenu = { game.returnToReady() },
                    )
                } else if (challenge != null) {
                    PoolChallengeResultOverlay(
                        game = game,
                        challenge = challenge,
                        result = lastChallengeResult,
                        dailyBest = dailyBest,
                        onReplay = {
                            if (challenge.id == PoolChallenges.DAILY_ID) {
                                startDailyChallenge()
                            } else {
                                startLevel(challenge)
                            }
                        },
                        onNextLevel = {
                            PoolChallenges.campaigns().firstOrNull { it.id == challenge.id + 1 }?.let { startLevel(it) }
                        },
                        onLeaderboard = {
                            dailyEntries =
                                GameLeaderboardStore(prefs, dailyLeaderboardKey(game.challengeDate.takeIf { it > 0L } ?: todayDate)).load()
                            showDailyLeaderboard = true
                        },
                        onMenu = { game.returnToReady() },
                    )
                } else {
                    OverlayScrim {
                        Text(
                            text =
                                if (practice) {
                                    stringResource(R.string.bear_pool_practice_done)
                                } else if (playerWon) {
                                    stringResource(R.string.bear_pool_win)
                                } else {
                                    stringResource(R.string.bear_pool_lose)
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E342E),
                        )
                        Spacer(Modifier.height(8.dp))
                        if (practice) {
                            Text(
                                text = stringResource(R.string.bear_pool_practice_stats, game.shots, game.practiceScore()),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF6D4C41),
                            )
                            Text(
                                text = stringResource(R.string.bear_pool_pots_value, game.matchPots),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF8D6E63),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.bear_pool_frames, game.match.playerFrames, game.match.aiFrames),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF6D4C41),
                            )
                        }
                        if (lb.lastRank > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                lb.resetLastRank()
                                startFreshMatch()
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_rematch))
                        }
                        if (game.replayShots().isNotEmpty()) {
                            TextButton(onClick = { game.startReplay() }) {
                                Text(stringResource(R.string.bear_pool_replay))
                            }
                        }
                        TextButton(
                            onClick = {
                                lb.entries = lb.store.load()
                                lb.show = true
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_leaderboard))
                        }
                        TextButton(onClick = { showAchievements = true }) {
                            Text(stringResource(R.string.bear_pool_achievements))
                        }
                        TextButton(onClick = { showSettings = true }) {
                            Text(stringResource(R.string.bear_pool_settings))
                        }
                    }
                }
            }

            else -> Unit
        }

        // 顶栏放最后：即使结算浮层铺满全屏，关闭 / 设置 / 排行榜仍可点击
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { exitGame() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_game),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.bear_pool_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            GameCharacterButton(onClick = { showCharacterPicker = true })
            IconButton(onClick = { showShotHistory = !showShotHistory }) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.bear_pool_history),
                    tint = if (showShotHistory) POOL_PLAYER_ACCENT else Color.White,
                )
            }
            if (game.phase == Phase.AIMING || game.phase == Phase.SHOOTING) {
                GamePauseButton(onClick = { pause.open() })
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.bear_pool_settings),
                    tint = Color.White,
                )
            }
            LeaderboardButton(
                onClick = {
                    lb.entries = lb.store.load()
                    lb.show = true
                },
            )
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = "${game.match.playerFrames} : ${game.match.aiFrames}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    restartCurrentGame()
                },
                onSettings = {
                    pause.resume()
                    showSettings = true
                },
                onExit = {
                    submitResultIfNeeded()
                    exitGame()
                },
            )
        }
    }

    if (showSettings) {
        PoolSettingsDialog(
            difficulty = game.difficulty,
            aids = game.aids,
            raceTo = game.displayRaceTo(),
            mode = game.displayMode(),
            reduceMotion = reduceMotionPref,
            clothTheme = clothTheme,
            dailyBest = dailyBest,
            onOpenDaily = {
                dailyBest = prefs.intOr(dailyBestKey(todayDate), 0)
                showSettings = false
                showDaily = true
            },
            onDismiss = { showSettings = false },
            onSave = { d, a, r, m, s, rm, cl ->
                game.difficulty = d
                game.aids = a
                // 对局进行中不改变赛制/模式（否则 raceTo 变小会当场判 matchOver，成就口径也被污染）
                val canChangeRule = game.phase == Phase.READY || game.phase == Phase.MATCH_OVER
                val race = clampRaceTo(r)
                if (canChangeRule) {
                    game.raceTo = race
                    game.mode = m
                    game.clearPendingRules()
                } else {
                    // 局中改动暂存，下一场生效（与冰球的规则暂存语义一致）
                    game.stageRuleChange(race, m)
                }
                // 音效开关与其它设置一致：只有点保存才写入全局
                GameAudio.setEnabled(context, s)
                reduceMotionPref = rm
                game.reduceMotion = rm || systemReduceMotion
                clothTheme = clampClothTheme(cl)
                game.clothTheme = clothTheme
                prefs.edit {
                    putString(PREF_POOL_DIFF, difficultyKey(d))
                    putString(PREF_POOL_AIDS, aidsKey(a))
                    putInt(PREF_POOL_RACE, race)
                    putString(PREF_POOL_MODE, modeKey(m))
                    putBoolean(PREF_POOL_REDUCE_MOTION, rm)
                }
                saveClothTheme(prefs, clothTheme)
                showSettings = false
            },
        )
    }

    if (lb.show) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_pool_title),
            entries = lb.entries,
            onClear = {
                lb.store.clear(*POOL_LEGACY_LEADERBOARD_KEYS.toTypedArray())
                lb.entries = emptyList()
                lb.resetLastRank()
                game.best = 0
            },
            onDismiss = { lb.show = false },
        )
    }

    if (showCharacterPicker) {
        GameCharacterPickerDialog(
            state = characterState,
            onDismiss = { showCharacterPicker = false },
        )
    }

    if (showAchievements) {
        PoolAchievementsDialog(
            progress = progress,
            onDismiss = { showAchievements = false },
            onClear = {
                prefs.edit { POOL_STAT_KEYS.forEach { remove(it) } }
                progress = PoolProgress()
                pendingUnlocks = emptyList()
                storedReplay = null
            },
        )
    }

    if (showChallenges) {
        PoolChallengesDialog(
            cleared = clearedLevels,
            best = bestLevels,
            onStart = { startLevel(it) },
            onDismiss = { showChallenges = false },
        )
    }

    if (showTutorial) {
        PoolTutorialDialog(
            done = doneLessons,
            onStart = { startLesson(it) },
            onDismiss = { showTutorial = false },
        )
    }

    if (showDaily) {
        PoolDailyDialog(
            challenge = dailyChallenge,
            todayBest = dailyBest,
            onStart = { startDailyChallenge() },
            onLeaderboard = {
                dailyEntries = GameLeaderboardStore(prefs, dailyLeaderboardKey(todayDate)).load()
                showDaily = false
                showDailyLeaderboard = true
            },
            onDismiss = { showDaily = false },
        )
    }

    if (showDailyLeaderboard) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_pool_daily_leaderboard),
            entries = dailyEntries,
            onClear = {
                GameLeaderboardStore(prefs, dailyLeaderboardKey(game.challengeDate.takeIf { it > 0L } ?: todayDate)).clear()
                dailyEntries = emptyList()
            },
            onDismiss = { showDailyLeaderboard = false },
        )
    }

    if (pendingUnlocks.isNotEmpty()) {
        PoolUnlockDialog(
            achievements = pendingUnlocks,
            onDismiss = { pendingUnlocks = emptyList() },
        )
    }
}
