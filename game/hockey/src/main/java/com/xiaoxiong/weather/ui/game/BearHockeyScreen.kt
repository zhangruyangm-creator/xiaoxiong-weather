package com.xiaoxiong.weather.ui.game

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private const val PREF_DIFF = "hockey_difficulty"
private const val PREF_DIFF_LEVEL = "hockey_difficulty_level"
private const val PREF_WIN = "hockey_win_score"
private const val PREF_BEST = "best_hockey"
private const val PREF_PADDLE = "hockey_paddle_scale"
private const val PREF_PADDLE_BOTH = "hockey_paddle_scale_both"
private const val PREF_PUCK = "hockey_puck_scale"
private const val PREF_GOAL = "hockey_goal_scale"
private const val PREF_SOUND = "hockey_sound"

/** 发球倒数总时长（3 拍，每拍 0.5s）；超出部分显示「预备」。与 HockeyGame 的发球计时对应。 */
private const val SERVE_COUNTDOWN_SECONDS = 1.5f

/** 蜜球径向渐变的固定颜色列表（center 随球位置变化，无法整体缓存 Brush）。 */
private val HONEY_PUCK_COLORS = listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFEF6C00))

/**
 * 小熊冰球：竖屏空气壁球。下方玩家 / 上方 AI；设置内调难度与先赢几分。
 */
@Composable
fun BearHockeyScreen(
    sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    // 排行榜 v2：每局按净胜分入榜并展示比分（v1 是「分数制」，对局语义不通，弃用旧数据）
    val lb = rememberLeaderboardState(prefs, "leaderboard_hockey_v2", PREF_BEST, retainRecent = true)
    val density = LocalDensity.current
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
                it.bestWins = prefs.getInt(PREF_BEST, 0)
            }
        }
    var showSettings by remember { mutableStateOf(false) }
    var showCareer by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean(PREF_SOUND, true)) }
    // 系统「减弱动态效果」开启时静音（提示音与减动效偏好联动）
    val systemReduceMotion = LocalReduceMotion.current
    var progress by remember { mutableStateOf(loadHockeyProgress(prefs)) }
    var pendingUnlocks by remember { mutableStateOf<List<HockeyAchievement>>(emptyList()) }
    // 短促提示音：惰性创建，退出时释放；创建 / 播放失败静默忽略
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
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
        if (soundOn && !systemReduceMotion) {
            playTone(
                toneGen,
                if (won) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
                200,
            )
        }
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
                    // 击球 / 碰库：轻短提示音
                    if (soundOn && !systemReduceMotion) playTone(toneGen, ToneGenerator.TONE_PROP_BEEP, 45)
                }
                if (game.pendingHapticGoal) {
                    game.pendingHapticGoal = false
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    // 进球：较长提示音
                    if (soundOn && !systemReduceMotion) playTone(toneGen, ToneGenerator.TONE_PROP_ACK, 90)
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1C2C), Color(0xFF16324A), Color(0xFF0E2233)),
                    ),
                ),
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
            drawHockeyWorld(game)
        }

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
            Text(
                text = stringResource(R.string.bear_hockey_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
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

        val scoreA11y = stringResource(R.string.bear_hockey_a11y_score, game.playerScore, game.aiScore)
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = scoreA11y
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.35f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ScorePill(
                        label = stringResource(R.string.bear_hockey_you),
                        score = game.playerScore,
                        accent = Color(0xFFFFCC80),
                    )
                    Text(
                        text = stringResource(R.string.bear_hockey_first_to, game.winScore),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ScorePill(
                        label = stringResource(R.string.bear_hockey_rival),
                        score = game.aiScore,
                        accent = Color(0xFF80DEEA),
                    )
                }
            }
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
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF2FFF8EF),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
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
                        color = Color(0xFF4E342E),
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
                        color = Color(0xFF6D4C41),
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
                        color = Color(0xFF8D6E63),
                    )
                    if (game.bestWins > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.bear_hockey_career_wins, game.bestWins),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA1887F),
                        )
                    }
                    if (game.phase == Phase.MATCH_OVER && lb.lastRank > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
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
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.bear_hockey_settings))
                    }
                    TextButton(onClick = { showCareer = true }) {
                        Text(stringResource(R.string.bear_hockey_career))
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
            rulesLocked = game.rulesLocked,
            soundOn = soundOn,
            onOpenCareer = {
                showSettings = false
                showCareer = true
            },
            onDismiss = { showSettings = false },
            onSave = { d, level, w, paddle, paddleBoth, puck, goal, sound ->
                game.applySettings(d, level, w, paddle, paddleBoth, puck, goal)
                soundOn = sound
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
                    putBoolean(PREF_SOUND, sound)
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

@Composable
private fun ScorePill(
    label: String,
    score: Int,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
        Text(
            "$score",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

/** 发球倒数横幅：只在自身作用域读 [HockeyGame.serveTimer]，把每帧重组限制在本子项内。 */
@Composable
private fun ServeBanner(game: HockeyGame) {
    // 倒数 3、2、1（每拍 0.5s）；之前的零头时间显示「预备」
    val n = ceil(game.serveTimer / (SERVE_COUNTDOWN_SECONDS / 3f)).toInt().coerceIn(1, 3)
    // 发球方向提示：失球方接发球（球飞向失球一侧）
    val hint =
        when (game.serveTowardPlayer) {
            true -> stringResource(R.string.bear_hockey_serve_to_you)
            false -> stringResource(R.string.bear_hockey_serve_to_rival)
            null -> stringResource(R.string.bear_hockey_serve_random)
        }
    CenterBanner(
        text =
            if (game.serveTimer > SERVE_COUNTDOWN_SECONDS) {
                stringResource(R.string.bear_hockey_get_ready)
            } else {
                "$n"
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
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        color = Color.White.copy(alpha = 0.85f),
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
    rulesLocked: Boolean,
    soundOn: Boolean,
    onOpenCareer: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Difficulty, Int, Int, Float, Boolean, Float, Float, Boolean) -> Unit,
) {
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var levelText by remember(customLevel) { mutableStateOf(customLevel.toString()) }
    var winText by remember(winScore) { mutableStateOf(winScore.toString()) }
    var scale by remember(paddleScale) { mutableStateOf(paddleScale) }
    var scaleBoth by remember(paddleScaleBoth) { mutableStateOf(paddleScaleBoth) }
    var puck by remember(puckScale) { mutableStateOf(puckScale) }
    var goal by remember(goalScale) { mutableStateOf(goalScale) }
    var sound by remember(soundOn) { mutableStateOf(soundOn) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF8EF)) {
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
                    color = Color(0xFF4E342E),
                )
                if (rulesLocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_hockey_rules_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_diff), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.BEGINNER, Difficulty.EASY, Difficulty.NORMAL).forEach { opt ->
                        FilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.ADVANCED, Difficulty.HARD, Difficulty.MASTER).forEach { opt ->
                        FilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilterChip(
                    selected = d == Difficulty.CUSTOM,
                    onClick = { d = Difficulty.CUSTOM },
                    label = { Text(stringResource(R.string.bear_hockey_diff_custom)) },
                )
                if (d == Difficulty.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_win_score), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7).forEach { n ->
                        FilterChip(
                            selected = winText.toIntOrNull() == n,
                            onClick = { winText = n.toString() },
                            label = { Text("$n") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                Text(stringResource(R.string.bear_hockey_paddle_scope), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scaleBoth,
                        onClick = { scaleBoth = true },
                        label = { Text(stringResource(R.string.bear_hockey_paddle_scope_both)) },
                    )
                    FilterChip(
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bear_hockey_sound),
                        color = Color(0xFF6D4C41),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = sound, onCheckedChange = { sound = it })
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenCareer,
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.bear_hockey_career))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
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
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFF8EF),
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
                    color = Color(0xFF4E342E),
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
                    color = Color(0xFF6D4C41),
                )
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line2,
                        progress.shutouts,
                        progress.bestStreak,
                        progress.bestGoals,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6D4C41),
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
                    color = Color(0xFF8D6E63),
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
                            color = if (unlocked) Color(0xFFFFA000) else Color(0xFFBDBDBD),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = HockeyAchievementTitle(ach),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) Color(0xFF4E342E) else Color(0xFF9E9E9E),
                            )
                            Text(
                                text = HockeyAchievementDesc(ach),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8D6E63),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(
                            text = stringResource(R.string.bear_hockey_clear_stats),
                            color = Color(0xFFC62828),
                        )
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
            title = { Text(stringResource(R.string.bear_hockey_clear_stats)) },
            text = { Text(stringResource(R.string.bear_hockey_clear_stats_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.bear_hockey_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
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
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF8EF)) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_hockey_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                )
                Spacer(Modifier.height(10.dp))
                achievements.forEach { ach ->
                    Text(
                        text = "★ " + HockeyAchievementTitle(ach),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4E342E),
                    )
                    Text(
                        text = HockeyAchievementDesc(ach),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8D6E63),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.bear_hockey_save)) }
            }
        }
    }
}

/** 播放短提示音；生成器为空或播放失败时静默忽略，不影响游戏。 */
private fun playTone(
    gen: ToneGenerator?,
    tone: Int,
    durationMs: Int,
) {
    if (gen == null) return
    runCatching { gen.startTone(tone, durationMs) }
}

private fun DrawScope.drawHockeyWorld(game: HockeyGame) {
    val t = game.table ?: return
    val left = t.left
    val top = t.top
    val w = t.width
    val h = t.height

    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(left + 6f, top + 10f),
        size = Size(w, h),
        cornerRadius = CornerRadius(28f, 28f),
    )
    val iceBrush =
        game.tableGradient
            ?: Brush.verticalGradient(
                colors = listOf(Color(0xFFE8F6FF), Color(0xFFD2EAF8), Color(0xFFC5E0F2)),
                startY = top,
                endY = top + h,
            )
    drawRoundRect(
        brush = iceBrush,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(26f, 26f),
    )
    drawRoundRect(
        color = Color(0xFF5C6BC0).copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(26f, 26f),
        style = Stroke(width = 5f),
    )
    drawRoundRect(
        color = Color(0xFFFFF8E1).copy(alpha = 0.5f),
        topLeft = Offset(left + 5f, top + 5f),
        size = Size(w - 10f, h - 10f),
        cornerRadius = CornerRadius(22f, 22f),
        style = Stroke(width = 2f),
    )

    drawLine(
        color = Color(0xFF90A4AE).copy(alpha = 0.55f),
        start = Offset(left + 16f, t.midY),
        end = Offset(left + w - 16f, t.midY),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = Color(0xFF90A4AE).copy(alpha = 0.45f),
        radius = t.shortSide * 0.11f,
        center = Offset(t.midX, t.midY),
        style = Stroke(width = 2.5f),
    )
    drawCircle(Color(0xFF90A4AE).copy(alpha = 0.35f), 4f, Offset(t.midX, t.midY))

    val goalHalf = t.goalWidth * 0.5f
    // 球门：深色门区 + 描边 + 门柱，浅色冰面上也一眼可见（颜色与双方挡板一致）
    drawGoalMouth(t.midX, top, t.goalWidth, isTop = true, fill = Color(0xFF0288D1), post = Color(0xFF01579B))
    drawGoalMouth(t.midX, top + h, t.goalWidth, isTop = false, fill = Color(0xFFEF6C00), post = Color(0xFFBF360C))
    if (game.scoreFlash > 0.01f) {
        val glow =
            when (game.lastScorer) {
                Scorer.PLAYER -> Color(0xFFFFD54F).copy(alpha = 0.35f * game.scoreFlash)
                Scorer.AI -> Color(0xFF4FC3F7).copy(alpha = 0.35f * game.scoreFlash)
                null -> Color.Transparent
            }
        val gy = if (game.lastScorer == Scorer.PLAYER) top else top + h - 18f
        drawCircle(glow, radius = goalHalf * 1.1f, center = Offset(t.midX, gy))
    }

    game.trail.forEachIndexed { i, (x, y) ->
        val a = (1f - i / 12f) * 0.28f
        drawCircle(Color(0xFFFFB300).copy(alpha = a), game.puck.r * 0.7f, Offset(x, y))
    }
    for (i in game.sparks.indices) {
        val s = game.sparks[i]
        val a = (s.life / 0.5f).coerceIn(0f, 1f)
        drawCircle(s.color.copy(alpha = a), 2.8f, Offset(s.x, s.y))
    }

    drawBearPaddle(game.ai.x, game.ai.y, game.ai.r, Color(0xFF4DD0E1), Color(0xFF00838F), Color(0xFF26C6DA), game.hitFlash)
    drawBearPaddle(game.player.x, game.player.y, game.player.r, Color(0xFFFFCC80), Color(0xFFE65100), Color(0xFFFFB74D), game.hitFlash)
    drawHoneyPuck(game.puck.x, game.puck.y, game.puck.r, game.time)

    if (game.phase == Phase.SERVE) {
        drawCircle(
            color = Color.White.copy(alpha = 0.25f + 0.2f * sin(game.time * 6f)),
            radius = game.puck.r * 2.2f,
            center = Offset(game.puck.x, game.puck.y),
            style = Stroke(width = 3f),
        )
        // 发球方向箭头：指向本次开球飞向的半场（首球随机则跳过）
        game.serveTowardPlayer?.let { drawServeArrow(t, it) }
    }
}

/** 发球方向箭头：指向本次开球飞向的半场。 */
private fun DrawScope.drawServeArrow(
    table: HockeyLogic.Table,
    towardPlayer: Boolean,
) {
    val x = table.midX
    val from = if (towardPlayer) table.midY + table.height * 0.10f else table.midY - table.height * 0.10f
    val to = if (towardPlayer) table.midY + table.height * 0.26f else table.midY - table.height * 0.26f
    val color = if (towardPlayer) Color(0xFFFFB74D) else Color(0xFF4DD0E1)
    drawLine(
        color = color.copy(alpha = 0.55f),
        start = Offset(x, from),
        end = Offset(x, to),
        strokeWidth = table.shortSide * 0.018f,
        cap = StrokeCap.Round,
    )
    val w = table.shortSide * 0.05f
    val tip = if (towardPlayer) table.shortSide * 0.06f else -table.shortSide * 0.06f
    val path =
        Path().apply {
            moveTo(x, to)
            lineTo(x - w, to - tip)
            lineTo(x + w, to - tip)
            close()
        }
    drawPath(path, color.copy(alpha = 0.8f))
}

/**
 * 球门：深色门区 + 描边 + 两根门柱（带高光）。
 * [y] 为球门线位置：上球门取桌顶、下球门取桌底；门区略探出桌沿，像真实球门的门洞。
 */
private fun DrawScope.drawGoalMouth(
    x: Float,
    y: Float,
    goalWidth: Float,
    isTop: Boolean,
    fill: Color,
    post: Color,
) {
    val half = goalWidth * 0.5f
    val depth = 18f
    val topEdge = if (isTop) y - 3f else y - depth + 3f
    drawRoundRect(
        color = fill.copy(alpha = 0.45f),
        topLeft = Offset(x - half, topEdge),
        size = Size(goalWidth, depth),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = fill.copy(alpha = 0.95f),
        topLeft = Offset(x - half, topEdge),
        size = Size(goalWidth, depth),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 3.5f),
    )
    val postY = if (isTop) y + 4f else y - 4f
    drawCircle(post, 6f, Offset(x - half, postY))
    drawCircle(post, 6f, Offset(x + half, postY))
    drawCircle(Color.White.copy(alpha = 0.65f), 2.2f, Offset(x - half, postY))
    drawCircle(Color.White.copy(alpha = 0.65f), 2.2f, Offset(x + half, postY))
}

private fun DrawScope.drawBearPaddle(
    x: Float,
    y: Float,
    r: Float,
    fill: Color,
    rim: Color,
    ear: Color,
    hit: Float,
) {
    drawCircle(Color(0x55000000), r * 1.05f, Offset(x + 2f, y + 4f))
    drawCircle(ear, r * 0.38f, Offset(x - r * 0.62f, y - r * 0.62f))
    drawCircle(ear, r * 0.38f, Offset(x + r * 0.62f, y - r * 0.62f))
    drawCircle(rim.copy(alpha = 0.35f), r * 0.22f, Offset(x - r * 0.62f, y - r * 0.62f))
    drawCircle(rim.copy(alpha = 0.35f), r * 0.22f, Offset(x + r * 0.62f, y - r * 0.62f))
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), fill, rim.copy(alpha = 0.85f)),
                center = Offset(x - r * 0.25f, y - r * 0.3f),
                radius = r * 1.35f,
            ),
        radius = r,
        center = Offset(x, y),
    )
    drawCircle(rim.copy(alpha = 0.7f), r, Offset(x, y), style = Stroke(width = r * 0.12f))
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.42f, Offset(x, y + r * 0.08f))
    drawCircle(Color(0xFF4E342E), r * 0.09f, Offset(x - r * 0.18f, y - r * 0.06f))
    drawCircle(Color(0xFF4E342E), r * 0.09f, Offset(x + r * 0.18f, y - r * 0.06f))
    drawCircle(Color(0xFF6D4C41), r * 0.11f, Offset(x, y + r * 0.16f))
    if (hit > 0.05f) {
        drawCircle(Color.White.copy(alpha = 0.35f * hit), r * 1.15f, Offset(x, y), style = Stroke(width = 3f))
    }
}

private fun DrawScope.drawHoneyPuck(
    x: Float,
    y: Float,
    r: Float,
    time: Float,
) {
    drawCircle(Color(0x66000000), r * 1.05f, Offset(x + 1.5f, y + 2.5f))
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = HONEY_PUCK_COLORS,
                center = Offset(x - r * 0.3f, y - r * 0.35f),
                radius = r * 1.4f,
            ),
        radius = r,
        center = Offset(x, y),
    )
    drawCircle(Color(0xFFBF360C).copy(alpha = 0.55f), r, Offset(x, y), style = Stroke(width = r * 0.14f))
    drawCircle(
        Color.White.copy(alpha = 0.7f),
        r * 0.22f,
        Offset(x + cos(time * 2.2f) * r * 0.25f, y - r * 0.28f + sin(time * 1.7f) * r * 0.08f),
    )
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
                color = Color(0xFF6D4C41),
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.bear_hockey_size_value,
                        value.coerceIn(range.start, range.endInclusive),
                    ),
                color = Color(0xFF6D4C41),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}
