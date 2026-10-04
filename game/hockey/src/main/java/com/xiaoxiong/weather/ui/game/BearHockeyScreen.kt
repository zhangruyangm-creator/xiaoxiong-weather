package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.booleanOr
import com.xiaoxiong.weather.game.floatOr
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.game.stringOr
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlin.math.ceil

private const val PREF_DIFF = "hockey_difficulty"
private const val PREF_DIFF_LEVEL = "hockey_difficulty_level"
private const val PREF_WIN = "hockey_win_score"
private const val PREF_BEST = "best_hockey"
private const val PREF_PADDLE = "hockey_paddle_scale"
private const val PREF_PADDLE_BOTH = "hockey_paddle_scale_both"
private const val PREF_PUCK = "hockey_puck_scale"
private const val PREF_GOAL = "hockey_goal_scale"
private const val PREF_RINK_THEME = "hockey_rink_theme"

/** 被 v2 取代的旧榜 key：清理推迟到首次成功进榜或用户主动清榜时。 */
private const val LEGACY_LEADERBOARD_KEY = "leaderboard_hockey"

/**
 * 冰球排行榜条目（纯函数，便于对净胜 / tieBreak 口径做单测）：
 * score = 净胜球，tieBreak = 失球数（同为净胜 +2 时 3:1 排在 4:2 前）。[label] 仅展示。
 */
internal fun hockeyLeaderboardEntry(
    playerScore: Int,
    aiScore: Int,
    timestamp: Long,
    city: String?,
    weather: String?,
): LeaderboardEntry =
    LeaderboardEntry(
        score = playerScore - aiScore,
        timestamp = timestamp,
        city = city,
        weather = weather,
        label = "$playerScore : $aiScore",
        tieBreak = aiScore,
    )

/** 发球倒数总时长（3 拍，每拍 0.5s）；超出部分显示「预备」。与 HockeyGame 的发球计时对应。 */
private const val SERVE_COUNTDOWN_SECONDS = 1.5f

/** MATCH_OVER 无法续玩：快照生命周期在该阶段清档（重进从标题开始）。 */
internal fun hockeyShouldClearSnapshot(phase: Phase): Boolean = phase == Phase.MATCH_OVER

/**
 * 小熊冰球：竖屏空气壁球。下方玩家 / 上方 AI；设置内调难度与先赢几分。
 */
@Composable
fun BearHockeyScreen(
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
    // 排行榜 v2：每局按净胜分入榜并展示比分（v1 是「分数制」，对局语义不通，弃用旧数据）。
    // bestKey 传 null：best_hockey 是「累计胜场」生涯数据，不能随清榜一起清零。
    // v1 旧榜延迟到首次成功进榜或用户清榜时再清理，避免一进游戏旧记录就永久消失。
    val lb = rememberLeaderboardState(prefs, "leaderboard_hockey_v2", retainRecent = true)
    val density = LocalDensity.current
    // 天气/场景样式：驱动冰面底色、场外氛围与整屏背景
    val day = isDay ?: true
    val style = remember(sceneCode, isDay) { weatherSceneStyle(sceneCode, isDay, appDark = true) }
    val backdrop = remember(style) { hockeyBackdrop(style) }
    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val rain = remember(sceneCode) { rainStrengthOf(sceneCode) }
    val freezingRain = remember(sceneCode) { isFreezingRainCode(sceneCode) }
    val hail = remember(sceneCode) { isHailCode(sceneCode) }
    val game =
        remember {
            HockeyGame().also {
                it.difficulty =
                    when (prefs.stringOr(PREF_DIFF, "normal")) {
                        "beginner" -> Difficulty.BEGINNER
                        "easy" -> Difficulty.EASY
                        "advanced" -> Difficulty.ADVANCED
                        "hard" -> Difficulty.HARD
                        "master" -> Difficulty.MASTER
                        "custom" -> Difficulty.CUSTOM
                        else -> Difficulty.NORMAL
                    }
                it.customLevel =
                    prefs
                        .intOr(PREF_DIFF_LEVEL, 5)
                        .coerceIn(HockeyLogic.CUSTOM_LEVEL_MIN, HockeyLogic.CUSTOM_LEVEL_MAX)
                it.winScore =
                    prefs
                        .intOr(PREF_WIN, 5)
                        .coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
                it.paddleScale =
                    prefs
                        .floatOr(PREF_PADDLE, HockeyLogic.PADDLE_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX)
                it.paddleScaleBoth = prefs.booleanOr(PREF_PADDLE_BOTH, true)
                it.puckScale =
                    prefs
                        .floatOr(PREF_PUCK, HockeyLogic.PUCK_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX)
                it.goalScale =
                    prefs
                        .floatOr(PREF_GOAL, HockeyLogic.GOAL_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX)
                it.rinkTheme = RinkTheme.fromId(prefs.stringOr(PREF_RINK_THEME, RinkTheme.DEFAULT.id))
                it.bestWins = prefs.intOr(PREF_BEST, 0)
            }
        }
    var showSettings by remember { mutableStateOf(false) }
    var showCareer by remember { mutableStateOf(false) }
    // 系统「减弱动态效果」开启时冻结装饰动画（音效由全局 GameAudio 独立管理）
    val systemReduceMotion = LocalReduceMotion.current
    // 同步进游戏：冻结装饰用 time（蜜罐滚动 / 发球脉冲 / 场外天气），物理 dt 不受影响
    LaunchedEffect(systemReduceMotion) {
        game.reduceMotion = systemReduceMotion
    }
    var progress by remember { mutableStateOf(loadHockeyProgress(prefs)) }
    var pendingUnlocks by remember { mutableStateOf<List<HockeyAchievement>>(emptyList()) }
    DisposableEffect(Unit) {
        onDispose {
            // 退出即释放两张全屏静态层位图与精灵缓存，避免常驻
            clearHockeyRenderCaches()
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    val reportScene by rememberUpdatedState(sceneCode)
    val reportCity by rememberUpdatedState(cityName)

    // 快照生命周期统一走公共件：进入恢复（坏档/无档保持标题）→ ON_STOP 落盘 →
    // onDispose 兜底 → 退出抑制 → MATCH_OVER 清档。
    // restore 可能早于首次 resize：restoreSnapshot 会用存档画布尺寸先建球桌再套坐标。
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
            shouldClear = ::hockeyShouldClearSnapshot,
        )

    // 退出流程（HUD 关闭 / 暂停退出 / 返回键）：先清档并抑制兜底落盘，
    // 避免随后的 onDispose 兜底把已退出的对局重新落盘
    fun exitGame() {
        snapshotExitGuard.exit(snapshotSession, prefs)
        onExit()
    }

    // 结算落盘 + 提交排行榜（幂等）。BackHandler/onExit 进入时也调用，
    // 避免在最后一球的进球横幅（SCORED）期间退出丢掉整局战绩。
    fun submitResultIfNeeded() {
        if (game.resultSubmitted) return
        // 幂等补结算（横幅期间退出 / 提交榜单）：不改 phase，决胜球的 SCORED
        // 横幅照常由 tick 播完再转 MATCH_OVER
        if (!game.settleDecidedMatch()) return
        game.resultSubmitted = true
        prefs.edit { putInt(PREF_BEST, game.bestWins) }
        // 生涯统计 + 成就（幂等：resultSubmitted 保证每局只结算一次）；
        // 难度用开局锁定的 matchDifficulty，局中改难度不影响本局记账
        val won = game.matchWinner == Scorer.PLAYER
        val advanced =
            progress.advance(
                won = won,
                goalsFor = game.playerScore,
                goalsAgainst = game.aiScore,
                difficulty = game.matchDifficulty,
            )
        val newly =
            HockeyAchievement.entries.filter { it.isUnlocked(advanced) && it.id !in progress.unlocked }
        if (newly.isNotEmpty()) pendingUnlocks = pendingUnlocks + newly
        val withUnlocks =
            if (newly.isEmpty()) {
                advanced
            } else {
                advanced.copy(unlocked = advanced.unlocked + newly.map { it.id })
            }
        progress = withUnlocks
        saveHockeyProgress(prefs, withUnlocks)
        // 胜负提示音：与击球 / 进球音区分（长提示音）
        GameAudio.play(if (won) GameCue.WIN else GameCue.LOSE)
        // 每局（含输局）都入榜：按净胜分排序，大胜在前、惨败在后；榜单展示真实比分
        val score = game.playerScore - game.aiScore
        val rank =
            lb.store.submit(
                hockeyLeaderboardEntry(
                    playerScore = game.playerScore,
                    aiScore = game.aiScore,
                    timestamp = System.currentTimeMillis(),
                    city = reportCity,
                    weather = leaderboardWeatherOf(context, reportScene),
                ),
                LEGACY_LEADERBOARD_KEY,
            )
        // 负净胜不展示名次；榜单本身仍保留最近记录
        lb.lastRank = leaderboardDisplayRank(score, rank)
        lb.entries = lb.store.load()
    }

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase != Phase.READY && game.phase != Phase.MATCH_OVER -> pause.open()
            else -> {
                submitResultIfNeeded()
                exitGame()
            }
        }
    }

    // READY（主菜单）/ MATCH_OVER（结算）无对局逻辑（tick 内提前 return），
    // 不必 60fps 空转：节流到约 12fps。dt 按真实帧累计后整笔提交，
    // 避免慢动作/闪烁计时失真（对齐 jump/catch/farm 的写法）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showSettings || showCareer || pendingUnlocks.isNotEmpty() || pause.paused },
        onTick = { dt, _ ->
            val active = game.phase != Phase.READY && game.phase != Phase.MATCH_OVER
            animDt += dt
            if (active || slowTick % 5 == 0) {
                val worldDt = animDt
                animDt = 0f
                game.tick(worldDt)
                if (game.pendingHapticHit) {
                    game.pendingHapticHit = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 挡板击球：短促实心音
                    GameAudio.play(GameCue.HIT)
                }
                if (game.pendingHapticWall) {
                    game.pendingHapticWall = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 库边反弹：更轻的碰木音
                    GameAudio.play(GameCue.BUMP)
                }
                if (game.pendingHapticGoal) {
                    game.pendingHapticGoal = false
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    // 进球：自己得分用庆祝音，对方得分用下行音（不刺耳）
                    GameAudio.play(if (game.lastScorer == Scorer.PLAYER) GameCue.GOAL else GameCue.LOSE)
                }
                frame++
            }
            if (!active) slowTick++
        },
    )

    LaunchedEffect(game.phase, game.matchWinner) {
        submitResultIfNeeded()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(backdrop)),
    ) {
        val padH = with(density) { 18.dp.toPx() }
        val padV = with(density) { 96.dp.toPx() }
        val tableCd = stringResource(R.string.cd_hockey_table)

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = tableCd }
                    .onSizeChanged { sz ->
                        game.resize(sz.width.toFloat(), sz.height.toFloat(), padH, padV)
                    }.pointerInput(Unit) {
                        // 不以 phase 为 key：阶段切换（如倒数结束进入对局）会取消重建手势协程，
                        // 手指已按下却没有新的 down 事件，拖动会"死掉"直到抬手重按。
                        // READY / MATCH_OVER 阶段游戏逻辑本就不读触摸点，常开手势无副作用。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // READY 标题阶段触摸给轻反馈（提示可交互）
                            if (game.phase == Phase.READY) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            game.touchX = down.position.x
                            game.touchY = down.position.y
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        game.touchX = change.position.x
                                        game.touchY = change.position.y
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                game.touchX = null
                                game.touchY = null
                            }
                        }
                    },
        ) {
            frame
            drawHockeyWorld(
                game = game,
                style = style,
                isDay = day,
                reduceMotion = systemReduceMotion,
                character = characterState.selected,
                weather = weather,
                rain = rain,
                freezingRain = freezingRain,
                hail = hail,
            )
        }

        // 比分放进顶部按钮栏中间：不占据冰面/球门进攻路线，也不再是显眼的深色浮层。
        val scoreA11y = stringResource(R.string.bear_hockey_a11y_score, game.playerScore, game.aiScore)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                submitResultIfNeeded()
                exitGame()
            }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_game),
                    tint = Color.White,
                )
            }
            // 平衡右侧按钮总宽（排行 40dp + 设置 48dp [+ 暂停 48dp]），
            // 否则中间权重区中心比屏幕中心偏左。
            Spacer(Modifier.width(40.dp))
            if (game.phase != Phase.READY && game.phase != Phase.MATCH_OVER) {
                Spacer(Modifier.width(48.dp))
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        // 合并子文本：否则 TalkBack 会把「你」「比分」「小熊」逐条朗读，
                        // 与 contentDescription 重复
                        .semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = scoreA11y
                        },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bear_hockey_you),
                    color = HockeyUi.accentWarm,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "${game.playerScore} : ${game.aiScore}",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.bear_hockey_rival),
                    color = HockeyUi.accentCool,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (game.phase != Phase.READY && game.phase != Phase.MATCH_OVER) {
                GamePauseButton(onClick = { pause.open() })
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.bear_hockey_settings),
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

        when (game.phase) {
            Phase.SERVE -> ServeBanner(game)
            Phase.SCORED -> {
                val msg =
                    when (game.lastScorer) {
                        Scorer.PLAYER -> stringResource(R.string.bear_hockey_goal_you)
                        Scorer.AI -> stringResource(R.string.bear_hockey_goal_rival)
                        null -> ""
                    }
                if (msg.isNotEmpty()) CenterBanner(text = msg)
            }
            else -> Unit
        }

        if (game.phase == Phase.READY || game.phase == Phase.MATCH_OVER) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(28.dp)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = HockeyUi.glassDeep,
                border = BorderStroke(1.dp, HockeyUi.iceBorder),
                shadowElevation = 14.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, HockeyUi.accentCool, HockeyUi.accentWarm, Color.Transparent),
                                    ),
                                ),
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text =
                                when {
                                    game.phase == Phase.READY -> stringResource(R.string.bear_hockey_title)
                                    game.matchWinner == Scorer.PLAYER -> stringResource(R.string.bear_hockey_win)
                                    else -> stringResource(R.string.bear_hockey_lose)
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (game.matchWinner == Scorer.PLAYER &&
                                    game.phase == Phase.MATCH_OVER
                                ) {
                                    HockeyUi.accentWarm
                                } else {
                                    Color.White
                                },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text =
                                if (game.phase == Phase.READY) {
                                    stringResource(R.string.bear_hockey_intro)
                                } else {
                                    stringResource(
                                        R.string.bear_hockey_final_score,
                                        game.playerScore,
                                        game.aiScore,
                                    )
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = HockeyUi.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.bear_hockey_diff_line,
                                    difficultyLabel(
                                        if (game.phase == Phase.MATCH_OVER) game.matchDifficulty else game.difficulty,
                                        game.customLevel,
                                    ),
                                    game.winScore,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = HockeyUi.textSecondary,
                        )
                        if (game.bestWins > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.bear_hockey_career_wins, game.bestWins),
                                style = MaterialTheme.typography.labelMedium,
                                color = HockeyUi.textSecondary,
                            )
                        }
                        if (game.phase == Phase.MATCH_OVER && lb.lastRank > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = HockeyUi.accentWarm,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            lb.resetLastRank()
                            game.clearSnapshot(prefs)
                            game.startMatch()
                        }) {
                            Text(
                                text =
                                    if (game.phase == Phase.READY) {
                                        stringResource(R.string.bear_hockey_start)
                                    } else {
                                        stringResource(R.string.bear_hockey_again)
                                    },
                            )
                        }
                        TextButton(
                            onClick = { showSettings = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                        ) {
                            Text(stringResource(R.string.bear_hockey_settings))
                        }
                        TextButton(
                            onClick = { showCareer = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                        ) {
                            Text(stringResource(R.string.bear_hockey_career))
                        }
                    }
                }
            }
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = "${game.playerScore} : ${game.aiScore}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    lb.resetLastRank()
                    game.clearSnapshot(prefs)
                    game.startMatch()
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
        HockeySettingsDialog(
            difficulty = game.difficulty,
            customLevel = game.customLevel,
            winScore = game.displayWinScore(),
            paddleScale = game.displayPaddleScale(),
            paddleScaleBoth = game.displayPaddleScaleBoth(),
            puckScale = game.displayPuckScale(),
            goalScale = game.displayGoalScale(),
            rinkTheme = game.displayRinkTheme(),
            rulesLocked = game.rulesLocked,
            onOpenCareer = {
                showSettings = false
                showCareer = true
            },
            onDismiss = { showSettings = false },
            onSave = { d, level, w, paddle, paddleBoth, puck, goal, theme, sound ->
                game.applySettings(d, level, w, paddle, paddleBoth, puck, goal, theme)
                // 音效开关与其它设置一致：只在保存路径写入全局，取消 / 返回不生效
                GameAudio.setEnabled(context, sound)
                prefs.edit {
                    putString(
                        PREF_DIFF,
                        when (d) {
                            Difficulty.BEGINNER -> "beginner"
                            Difficulty.EASY -> "easy"
                            Difficulty.NORMAL -> "normal"
                            Difficulty.ADVANCED -> "advanced"
                            Difficulty.HARD -> "hard"
                            Difficulty.MASTER -> "master"
                            Difficulty.CUSTOM -> "custom"
                        },
                    )
                    putInt(PREF_DIFF_LEVEL, level)
                    putInt(PREF_WIN, w)
                    putFloat(PREF_PADDLE, paddle)
                    putBoolean(PREF_PADDLE_BOTH, paddleBoth)
                    putFloat(PREF_PUCK, puck)
                    putFloat(PREF_GOAL, goal)
                    putString(PREF_RINK_THEME, theme.id)
                }
                showSettings = false
            },
        )
    }

    if (lb.show) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_hockey_title),
            entries = lb.entries,
            onClear = {
                lb.store.clear(LEGACY_LEADERBOARD_KEY)
                lb.entries = emptyList()
                lb.resetLastRank()
            },
            onDismiss = { lb.show = false },
        )
    }

    if (showCareer) {
        HockeyCareerDialog(
            progress = progress,
            onDismiss = { showCareer = false },
            onClear = {
                prefs.edit { HOCKEY_STAT_KEYS.forEach { remove(it) } }
                progress = HockeyProgress()
                pendingUnlocks = emptyList()
            },
        )
    }

    if (pendingUnlocks.isNotEmpty()) {
        HockeyUnlockDialog(
            achievements = pendingUnlocks,
            onDismiss = { pendingUnlocks = emptyList() },
        )
    }
}

/**
 * 发球倒数横幅：用 [derivedStateOf] 把每帧变化的 [HockeyGame.serveTimer] 收敛成
 * 倒数数字（1..3）与「预备 / 倒数」布尔，只在整数翻转时触发重组。
 */
@Composable
private fun ServeBanner(game: HockeyGame) {
    // 倒数 3、2、1（每拍 0.5s）；之前的零头时间显示「预备」
    val countdown by remember {
        derivedStateOf { ceil(game.serveTimer / (SERVE_COUNTDOWN_SECONDS / 3f)).toInt().coerceIn(1, 3) }
    }
    val ready by remember { derivedStateOf { game.serveTimer > SERVE_COUNTDOWN_SECONDS } }
    // 发球哨：每次进入发球倒数只吹一声（低频一次），替代原来的逐拍点击音
    LaunchedEffect(Unit) {
        GameAudio.play(GameCue.WHISTLE)
    }
    // 发球方向提示：失球方接发球（球飞向失球一侧）
    val hint =
        when (game.serveTowardPlayer) {
            true -> stringResource(R.string.bear_hockey_serve_to_you)
            false -> stringResource(R.string.bear_hockey_serve_to_rival)
            null -> stringResource(R.string.bear_hockey_serve_random)
        }
    CenterBanner(
        text =
            if (ready) {
                stringResource(R.string.bear_hockey_get_ready)
            } else {
                "$countdown"
            },
        hint = hint,
    )
}

@Composable
private fun CenterBanner(
    text: String,
    hint: String? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    color = HockeyUi.accentCool,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        color = HockeyUi.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun difficultyLabel(
    d: Difficulty,
    customLevel: Int = 5,
): String =
    when (d) {
        Difficulty.BEGINNER -> stringResource(R.string.bear_hockey_diff_beginner)
        Difficulty.EASY -> stringResource(R.string.bear_hockey_diff_easy)
        Difficulty.NORMAL -> stringResource(R.string.bear_hockey_diff_normal)
        Difficulty.ADVANCED -> stringResource(R.string.bear_hockey_diff_advanced)
        Difficulty.HARD -> stringResource(R.string.bear_hockey_diff_hard)
        Difficulty.MASTER -> stringResource(R.string.bear_hockey_diff_master)
        Difficulty.CUSTOM -> stringResource(R.string.bear_hockey_diff_custom_level, customLevel)
    }
