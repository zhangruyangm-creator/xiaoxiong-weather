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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoxiong.weather.R
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

/** 发球倒数总时长（3 拍，每拍 0.5s）；超出部分显示「预备」。与 HockeyGame 的发球计时对应。 */
private const val SERVE_COUNTDOWN_SECONDS = 1.5f

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
    // 排行榜 v2：每局按净胜分入榜并展示比分（v1 是「分数制」，对局语义不通，弃用旧数据）
    val lb = rememberLeaderboardState(prefs, "leaderboard_hockey_v2", PREF_BEST, retainRecent = true)
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
                    when (prefs.getString(PREF_DIFF, "normal")) {
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
                        .getInt(PREF_DIFF_LEVEL, 5)
                        .coerceIn(HockeyLogic.CUSTOM_LEVEL_MIN, HockeyLogic.CUSTOM_LEVEL_MAX)
                it.winScore =
                    prefs
                        .getInt(PREF_WIN, 5)
                        .coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
                it.paddleScale =
                    prefs
                        .getFloat(PREF_PADDLE, HockeyLogic.PADDLE_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX)
                it.paddleScaleBoth = prefs.getBoolean(PREF_PADDLE_BOTH, true)
                it.puckScale =
                    prefs
                        .getFloat(PREF_PUCK, HockeyLogic.PUCK_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX)
                it.goalScale =
                    prefs
                        .getFloat(PREF_GOAL, HockeyLogic.GOAL_SCALE_DEFAULT)
                        .coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX)
                it.rinkTheme = RinkTheme.fromId(prefs.getString(PREF_RINK_THEME, RinkTheme.DEFAULT.id))
                it.bestWins = prefs.getInt(PREF_BEST, 0)
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

    // 进程重建（旋转/被系统回收后重进）时恢复局中快照；坏档/无档保持标题。
    // restore 可能早于首次 resize：restoreSnapshot 会用存档画布尺寸先建球桌再套坐标。
    LaunchedEffect(Unit) {
        game.restoreSnapshot(prefs)
    }
    // 退出流程（HUD 关闭 / 暂停退出 / 返回键）：先清档并置标记，
    // 避免随后的 onDispose 兜底把已退出的对局重新落盘
    var exiting by remember { mutableStateOf(false) }

    fun exitGame() {
        exiting = true
        game.clearSnapshot(prefs)
        onExit()
    }
    // ON_STOP（按 Home 等切后台）与组合销毁时落盘，避免进程被系统回收丢档
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && !exiting) game.saveSnapshot(prefs)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!exiting) game.saveSnapshot(prefs)
        }
    }
    // 结算后清档：重进应从标题开始，而不是续到已结束的对局
    LaunchedEffect(game.phase) {
        if (game.phase == Phase.MATCH_OVER) game.clearSnapshot(prefs)
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
        lb.lastRank =
            lb.store.submit(
                LeaderboardEntry(
                    score = game.playerScore - game.aiScore,
                    timestamp = System.currentTimeMillis(),
                    city = reportCity,
                    weather = leaderboardWeatherOf(context, reportScene),
                    label = "${game.playerScore} : ${game.aiScore}",
                ),
            ) ?: 0
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
                lb.store.clear()
                lb.entries = emptyList()
                lb.resetLastRank()
                game.bestWins = 0
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
private fun difficultyLabel(
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

@Composable
private fun HockeySettingsDialog(
    difficulty: Difficulty,
    customLevel: Int,
    winScore: Int,
    paddleScale: Float,
    paddleScaleBoth: Boolean,
    puckScale: Float,
    goalScale: Float,
    rinkTheme: RinkTheme,
    rulesLocked: Boolean,
    onOpenCareer: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Difficulty, Int, Int, Float, Boolean, Float, Float, RinkTheme, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var levelText by remember(customLevel) { mutableStateOf(customLevel.toString()) }
    var winText by remember(winScore) { mutableStateOf(winScore.toString()) }
    var scale by remember(paddleScale) { mutableStateOf(paddleScale) }
    var scaleBoth by remember(paddleScaleBoth) { mutableStateOf(paddleScaleBoth) }
    var puck by remember(puckScale) { mutableStateOf(puckScale) }
    var goal by remember(goalScale) { mutableStateOf(goalScale) }
    var theme by remember(rinkTheme) { mutableStateOf(rinkTheme) }
    // 音效开关：只改本地状态，点「保存」才随 onSave 写入全局 GameAudio（旧 hockey_sound 仅由 GameAudio 首次迁移读取）
    var sound by remember { mutableStateOf(GameAudio.settings(context).enabled) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_hockey_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (rulesLocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_hockey_rules_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = HockeyUi.warn,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_diff), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.BEGINNER, Difficulty.EASY, Difficulty.NORMAL).forEach { opt ->
                        HockeyFilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.ADVANCED, Difficulty.HARD, Difficulty.MASTER).forEach { opt ->
                        HockeyFilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HockeyFilterChip(
                    selected = d == Difficulty.CUSTOM,
                    onClick = { d = Difficulty.CUSTOM },
                    label = { Text(stringResource(R.string.bear_hockey_diff_custom)) },
                )
                if (d == Difficulty.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    HockeyOutlinedField(
                        value = levelText,
                        onValueChange = { raw ->
                            levelText = raw.filter { it.isDigit() }.take(2)
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.bear_hockey_diff_level_hint,
                                    HockeyLogic.CUSTOM_LEVEL_MIN,
                                    HockeyLogic.CUSTOM_LEVEL_MAX,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_win_score), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7).forEach { n ->
                        HockeyFilterChip(
                            selected = winText.toIntOrNull() == n,
                            onClick = { winText = n.toString() },
                            label = { Text("$n") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HockeyOutlinedField(
                    value = winText,
                    onValueChange = { raw ->
                        winText = raw.filter { it.isDigit() }.take(2)
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.bear_hockey_win_score_hint,
                                HockeyLogic.WIN_SCORE_MIN,
                                HockeyLogic.WIN_SCORE_MAX,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_paddle_size),
                    value = scale,
                    range = HockeyLogic.PADDLE_SCALE_MIN..HockeyLogic.PADDLE_SCALE_MAX,
                    onChange = { scale = it },
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.bear_hockey_paddle_scope), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HockeyFilterChip(
                        selected = scaleBoth,
                        onClick = { scaleBoth = true },
                        label = { Text(stringResource(R.string.bear_hockey_paddle_scope_both)) },
                    )
                    HockeyFilterChip(
                        selected = !scaleBoth,
                        onClick = { scaleBoth = false },
                        label = { Text(stringResource(R.string.bear_hockey_paddle_scope_mine)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_puck_size),
                    value = puck,
                    range = HockeyLogic.PUCK_SCALE_MIN..HockeyLogic.PUCK_SCALE_MAX,
                    onChange = { puck = it },
                )
                Spacer(Modifier.height(12.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_goal_size),
                    value = goal,
                    range = HockeyLogic.GOAL_SCALE_MIN..HockeyLogic.GOAL_SCALE_MAX,
                    onChange = { goal = it },
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_rink_theme), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                RinkTheme.entries.chunked(2).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowThemes.forEach { opt ->
                            RinkThemeChip(
                                theme = opt,
                                selected = theme == opt,
                                onClick = { theme = opt },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bear_hockey_sound),
                        color = HockeyUi.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = sound,
                        onCheckedChange = { sound = it },
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenCareer,
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.bear_hockey_career))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.textSecondary),
                    ) {
                        Text(stringResource(R.string.bear_hockey_cancel))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val level =
                                levelText
                                    .toIntOrNull()
                                    ?.coerceIn(HockeyLogic.CUSTOM_LEVEL_MIN, HockeyLogic.CUSTOM_LEVEL_MAX)
                                    ?: customLevel.coerceIn(
                                        HockeyLogic.CUSTOM_LEVEL_MIN,
                                        HockeyLogic.CUSTOM_LEVEL_MAX,
                                    )
                            val win =
                                winText
                                    .toIntOrNull()
                                    ?.coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
                                    ?: winScore.coerceIn(
                                        HockeyLogic.WIN_SCORE_MIN,
                                        HockeyLogic.WIN_SCORE_MAX,
                                    )
                            onSave(
                                d,
                                level,
                                win,
                                scale.coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX),
                                scaleBoth,
                                puck.coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX),
                                goal.coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX),
                                theme,
                                sound,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.bear_hockey_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun HockeyAchievementTitle(a: HockeyAchievement): String =
    when (a) {
        HockeyAchievement.FIRST_WIN -> stringResource(R.string.bear_hockey_ach_first_win)
        HockeyAchievement.SHUTOUT -> stringResource(R.string.bear_hockey_ach_shutout)
        HockeyAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_hockey_ach_win_streak)
        HockeyAchievement.HARD_WIN -> stringResource(R.string.bear_hockey_ach_hard_win)
        HockeyAchievement.MASTER_WIN -> stringResource(R.string.bear_hockey_ach_master_win)
        HockeyAchievement.FIVE_GOALS -> stringResource(R.string.bear_hockey_ach_five_goals)
        HockeyAchievement.PERFECT_FIVE -> stringResource(R.string.bear_hockey_ach_perfect_five)
        HockeyAchievement.VETERAN -> stringResource(R.string.bear_hockey_ach_veteran)
    }

@Composable
private fun HockeyAchievementDesc(a: HockeyAchievement): String =
    when (a) {
        HockeyAchievement.FIRST_WIN -> stringResource(R.string.bear_hockey_ach_first_win_desc)
        HockeyAchievement.SHUTOUT -> stringResource(R.string.bear_hockey_ach_shutout_desc)
        HockeyAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_hockey_ach_win_streak_desc)
        HockeyAchievement.HARD_WIN -> stringResource(R.string.bear_hockey_ach_hard_win_desc)
        HockeyAchievement.MASTER_WIN -> stringResource(R.string.bear_hockey_ach_master_win_desc)
        HockeyAchievement.FIVE_GOALS -> stringResource(R.string.bear_hockey_ach_five_goals_desc)
        HockeyAchievement.PERFECT_FIVE -> stringResource(R.string.bear_hockey_ach_perfect_five_desc)
        HockeyAchievement.VETERAN -> stringResource(R.string.bear_hockey_ach_veteran_desc)
    }

/** 生涯成就面板：列出全部成就与解锁状态及统计。 */
@Composable
private fun HockeyCareerDialog(
    progress: HockeyProgress,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_hockey_career),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line,
                        progress.games,
                        progress.wins,
                        progress.goalsFor,
                        progress.goalsAgainst,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = HockeyUi.textSecondary,
                )
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line2,
                        progress.shutouts,
                        progress.bestStreak,
                        progress.bestGoals,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = HockeyUi.textSecondary,
                )
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line3,
                        progress.winsOn(Difficulty.BEGINNER),
                        progress.winsOn(Difficulty.EASY),
                        progress.winsOn(Difficulty.NORMAL),
                        progress.winsOn(Difficulty.ADVANCED),
                        progress.winsOn(Difficulty.HARD),
                        progress.winsOn(Difficulty.MASTER),
                        progress.winsOn(Difficulty.CUSTOM),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HockeyUi.textSecondary.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(12.dp))
                HockeyAchievement.entries.forEach { ach ->
                    val unlocked = ach.isUnlocked(progress)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (unlocked) "★" else "☆",
                            color = if (unlocked) HockeyUi.accentWarm else Color(0x66FFFFFF),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = HockeyAchievementTitle(ach),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) Color.White else Color(0x80FFFFFF),
                            )
                            Text(
                                text = HockeyAchievementDesc(ach),
                                style = MaterialTheme.typography.labelSmall,
                                color = HockeyUi.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.danger),
                    ) {
                        Text(stringResource(R.string.bear_hockey_clear_stats))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.bear_hockey_save)) }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = HockeyUi.glassDeep,
            titleContentColor = Color.White,
            textContentColor = HockeyUi.textSecondary,
            title = { Text(stringResource(R.string.bear_hockey_clear_stats)) },
            text = { Text(stringResource(R.string.bear_hockey_clear_stats_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.danger),
                ) {
                    Text(stringResource(R.string.bear_hockey_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                ) {
                    Text(stringResource(R.string.bear_hockey_cancel))
                }
            },
        )
    }
}

/** 新成就解锁弹窗。 */
@Composable
private fun HockeyUnlockDialog(
    achievements: List<HockeyAchievement>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_hockey_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HockeyUi.accentWarm,
                )
                Spacer(Modifier.height(10.dp))
                achievements.forEach { ach ->
                    Text(
                        text = "★ " + HockeyAchievementTitle(ach),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = HockeyAchievementDesc(ach),
                        style = MaterialTheme.typography.labelSmall,
                        color = HockeyUi.textSecondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.bear_hockey_save)) }
            }
        }
    }
}

/** 尺寸设置行：标题 + 当前倍率 + 连续滑块（挡板/冰球/球门共用）。 */
@Composable
private fun SizeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = HockeyUi.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.bear_hockey_size_value,
                        value.coerceIn(range.start, range.endInclusive),
                    ),
                color = HockeyUi.accentCool,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = HockeyUi.accentCool,
                    activeTrackColor = HockeyUi.accentCool,
                    inactiveTrackColor = HockeyUi.accentCool.copy(alpha = 0.22f),
                ),
        )
    }
}

/** 场地主题选项：主题名 + 摩擦手感说明，当前项高亮，附无障碍内容描述。 */
@Composable
private fun RinkThemeChip(
    theme: RinkTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(theme.titleRes)
    val desc = rinkThemeDesc(theme)
    val a11y = stringResource(R.string.bear_hockey_rink_theme_a11y, name, desc)
    HockeyFilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else HockeyUi.textSecondary,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.80f) else HockeyUi.textSecondary.copy(alpha = 0.75f),
                )
            }
        },
        modifier = modifier.semantics { contentDescription = a11y },
    )
}

/** 场地主题的摩擦手感说明（模型只带标题资源，说明文案留在 UI 层）。 */
@Composable
private fun rinkThemeDesc(theme: RinkTheme): String =
    when (theme) {
        RinkTheme.ICE -> stringResource(R.string.bear_hockey_rink_ice_desc)
        RinkTheme.BEAR -> stringResource(R.string.bear_hockey_rink_bear_desc)
        RinkTheme.WOOD -> stringResource(R.string.bear_hockey_rink_wood_desc)
        RinkTheme.CONCRETE -> stringResource(R.string.bear_hockey_rink_concrete_desc)
    }

/** 冰球主题筛选胶囊：深色玻璃底 + 冰蓝选中态。 */
@Composable
private fun HockeyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color(0x14FFFFFF),
                labelColor = HockeyUi.textSecondary,
                selectedContainerColor = Color(0x5534B6E8),
                selectedLabelColor = Color.White,
            ),
        modifier = modifier,
    )
}

/** 冰球主题输入框：深色底 + 冰蓝描边。 */
@Composable
private fun HockeyOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = HockeyUi.accentCool,
                focusedBorderColor = HockeyUi.accentCool,
                unfocusedBorderColor = HockeyUi.iceBorder,
                focusedLabelColor = HockeyUi.accentCool,
                unfocusedLabelColor = HockeyUi.textSecondary,
            ),
        modifier = modifier,
    )
}
