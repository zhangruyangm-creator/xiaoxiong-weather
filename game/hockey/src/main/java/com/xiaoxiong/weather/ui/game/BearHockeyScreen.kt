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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.xiaoxiong.weather.R
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

/** 发球倒数总时长（3 拍，每拍 0.5s）；超出部分显示「预备」。与 HockeyGame 的发球计时对应。 */
private const val SERVE_COUNTDOWN_SECONDS = 1.5f

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
    var frame by remember { mutableLongStateOf(0L) }
    val reportScene by rememberUpdatedState(sceneCode)
    val reportCity by rememberUpdatedState(cityName)

    // 结算落盘 + 提交排行榜（幂等）。BackHandler/onExit 进入时也调用，
    // 避免在最后一球的进球横幅（SCORED）期间退出丢掉整局战绩。
    fun submitResultIfNeeded() {
        if (game.resultSubmitted) return
        val matchDecided =
            game.phase == Phase.MATCH_OVER ||
                game.matchWinner != null ||
                (
                    game.phase == Phase.SCORED &&
                        HockeyLogic.matchOver(game.playerScore, game.aiScore, game.winScore)
                )
        if (!matchDecided) return
        game.resultSubmitted = true
        // 横幅期间退出时胜负/连胜尚未结算：按 tick 的 MATCH_OVER 分支补结算；
        // 同时把 phase 推进到 MATCH_OVER，避免退出前最后一帧 tick 再结算一次叠加连胜
        if (game.matchWinner == null) {
            game.matchWinner = HockeyLogic.winner(game.playerScore, game.aiScore)
            if (game.matchWinner == Scorer.PLAYER) game.bestWins += 1
        }
        game.phase = Phase.MATCH_OVER
        prefs.edit { putInt(PREF_BEST, game.bestWins) }
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
        submitResultIfNeeded()
        onExit()
    }

    // READY（主菜单）/ MATCH_OVER（结算）无对局逻辑（tick 内提前 return），
    // 不必 60fps 空转：节流到约 12fps。dt 按真实帧累计后整笔提交，
    // 避免慢动作/闪烁计时失真（对齐 jump/catch/farm 的写法）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showSettings },
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
                }
                if (game.pendingHapticGoal) {
                    game.pendingHapticGoal = false
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { sz ->
                        game.resize(sz.width.toFloat(), sz.height.toFloat(), padH, padV)
                    }.pointerInput(Unit) {
                        // 不以 phase 为 key：阶段切换（如倒数结束进入对局）会取消重建手势协程，
                        // 手指已按下却没有新的 down 事件，拖动会"死掉"直到抬手重按。
                        // READY / MATCH_OVER 阶段游戏逻辑本就不读触摸点，常开手势无副作用。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
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
            IconButton(onClick = onExit) {
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

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
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
            Phase.SERVE -> {
                // 倒数 3、2、1（每拍 0.5s）；之前的零头时间显示「预备」
                val n = ceil(game.serveTimer / (SERVE_COUNTDOWN_SECONDS / 3f)).toInt().coerceIn(1, 3)
                CenterBanner(
                    text =
                        if (game.serveTimer > SERVE_COUNTDOWN_SECONDS) {
                            stringResource(R.string.bear_hockey_get_ready)
                        } else {
                            "$n"
                        },
                )
            }
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
                                difficultyLabel(game.difficulty, game.customLevel),
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
                    Button(onClick = { game.startMatch() }) {
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
                }
            }
        }
    }

    if (showSettings) {
        HockeySettingsDialog(
            difficulty = game.difficulty,
            customLevel = game.customLevel,
            winScore = game.winScore,
            paddleScale = game.paddleScale,
            paddleScaleBoth = game.paddleScaleBoth,
            puckScale = game.puckScale,
            goalScale = game.goalScale,
            onDismiss = { showSettings = false },
            onSave = { d, level, w, paddle, paddleBoth, puck, goal ->
                game.difficulty = d
                game.customLevel = level
                game.winScore = w
                game.paddleScale = paddle
                game.paddleScaleBoth = paddleBoth
                game.puckScale = puck
                game.goalScale = goal
                game.relayout()
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
                game.bestWins = 0
            },
            onDismiss = { lb.show = false },
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

@Composable
private fun CenterBanner(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
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
    onDismiss: () -> Unit,
    onSave: (Difficulty, Int, Int, Float, Boolean, Float, Float) -> Unit,
) {
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var levelText by remember(customLevel) { mutableStateOf(customLevel.toString()) }
    var winText by remember(winScore) { mutableStateOf(winScore.toString()) }
    var scale by remember(paddleScale) { mutableStateOf(paddleScale) }
    var scaleBoth by remember(paddleScaleBoth) { mutableStateOf(paddleScaleBoth) }
    var puck by remember(puckScale) { mutableStateOf(puckScale) }
    var goal by remember(goalScale) { mutableStateOf(goalScale) }
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
                Text(stringResource(R.string.bear_hockey_paddle_size), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        HockeyLogic.PADDLE_SCALE_DEFAULT to stringResource(R.string.bear_hockey_paddle_normal),
                        1.3f to stringResource(R.string.bear_hockey_paddle_large),
                        HockeyLogic.PADDLE_SCALE_MAX to stringResource(R.string.bear_hockey_paddle_xl),
                    ).forEach { (s, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(scale - s) < 0.01f,
                            onClick = { scale = s },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_puck_size), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0.75f to stringResource(R.string.bear_hockey_size_small),
                        HockeyLogic.PUCK_SCALE_DEFAULT to stringResource(R.string.bear_hockey_size_normal),
                        1.25f to stringResource(R.string.bear_hockey_size_large),
                    ).forEach { (s, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(puck - s) < 0.01f,
                            onClick = { puck = s },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_goal_size), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0.7f to stringResource(R.string.bear_hockey_size_small),
                        HockeyLogic.GOAL_SCALE_DEFAULT to stringResource(R.string.bear_hockey_size_normal),
                        1.3f to stringResource(R.string.bear_hockey_size_large),
                    ).forEach { (s, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(goal - s) < 0.01f,
                            onClick = { goal = s },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
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
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color(0xFFE8F6FF), Color(0xFFD2EAF8), Color(0xFFC5E0F2)),
                startY = top,
                endY = top + h,
            ),
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
    for (s in game.sparks) {
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
    }
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
                colors = listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFEF6C00)),
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
