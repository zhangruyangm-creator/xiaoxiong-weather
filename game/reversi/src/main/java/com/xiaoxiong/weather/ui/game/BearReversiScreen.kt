package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.game.intOr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val TABLE_TOP = Color(0xFF1E4D38)
private val TABLE_BOTTOM = Color(0xFF0F2419)

@Composable
fun BearReversiScreen(
    sceneCode: Int?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = rememberGamePrefs()
    val pause = rememberGamePauseState()
    val game = remember { ReversiGame() }
    val lb = rememberLeaderboardState(prefs, REVERSI_LEADERBOARD_KEY, REVERSI_BEST_KEY)
    var best by remember { mutableIntStateOf(prefs.intOr(REVERSI_BEST_KEY, 0)) }
    var difficulty by remember { mutableStateOf(reversiDifficultyFromPrefs(prefs)) }
    var frame by remember { mutableLongStateOf(0L) }
    var resultSubmitted by remember { mutableStateOf(false) }
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)

    LaunchedEffect(Unit) { GameAudio.preload(context) }

    fun submitIfNeeded() {
        if (resultSubmitted) return
        resultSubmitted = true
        val score = reversiPlayerScore(game.outcome, game.blackCount)
        if (score > best) {
            best = score
            prefs.edit { putInt(REVERSI_BEST_KEY, best) }
        }
        if (game.outcome == ReversiOutcome.BLACK_WIN || game.outcome == ReversiOutcome.TIE) {
            lb.lastRank =
                lb.store.submit(
                    LeaderboardEntry(
                        score = score,
                        timestamp = System.currentTimeMillis(),
                        city = reportCityName,
                        weather = leaderboardWeatherOf(context, reportSceneCode),
                    ),
                ) ?: 0
            lb.entries = lb.store.load()
        } else {
            lb.resetLastRank()
        }
    }

    fun startMatch() {
        GameAudio.play(GameCue.UI_CLICK)
        resultSubmitted = false
        lb.resetLastRank()
        pause.resume()
        game.start(difficulty)
        frame++
    }

    LaunchedEffect(game.turn, game.phase, game.board, pause.paused) {
        if (game.phase != ReversiPhase.PLAYING || pause.paused) return@LaunchedEffect
        while (game.passTurnIfNoLegalMove()) {
            frame++
            if (game.phase == ReversiPhase.OVER && !resultSubmitted) {
                submitIfNeeded()
                GameAudio.play(reversiOutcomeCue(game.outcome))
                return@LaunchedEffect
            }
        }
        if (game.phase == ReversiPhase.PLAYING && game.turn == REVERSI_WHITE) {
            delay(320)
            if (game.phase != ReversiPhase.PLAYING || game.turn != REVERSI_WHITE || pause.paused) {
                return@LaunchedEffect
            }
            // 搜索放后台线程：8×8 minimax（最高深度 7）在主线程会卡帧甚至 ANR
            val boardSnapshot = game.board.copyOf()
            val diff = difficulty
            val index = withContext(Dispatchers.Default) { reversiChooseAiMove(boardSnapshot, diff) }
            if (game.phase != ReversiPhase.PLAYING || game.turn != REVERSI_WHITE || pause.paused) {
                return@LaunchedEffect
            }
            if (index != null) game.applyAiMove(index) else game.passTurnIfNoLegalMove()
            if (game.phase == ReversiPhase.OVER && !resultSubmitted) {
                submitIfNeeded()
                GameAudio.play(reversiOutcomeCue(game.outcome))
            } else {
                GameAudio.play(GameCue.KEY)
            }
            frame++
        }
    }

    BackHandler {
        when (game.phase) {
            ReversiPhase.PLAYING -> {
                pause.open()
                frame++
            }
            else -> onExit()
        }
    }

    val boardCd = stringResource(R.string.cd_reversi_board)
    val tableBrush = remember { Brush.verticalGradient(listOf(TABLE_TOP, TABLE_BOTTOM)) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tableBrush),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (game.phase == ReversiPhase.PLAYING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reversi_score, game.blackCount, game.whiteCount),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (game.turn == REVERSI_BLACK) {
                                        if (game.legalMovesForTurn().isEmpty()) {
                                            R.string.reversi_turn_black_pass
                                        } else {
                                            R.string.reversi_turn_black
                                        }
                                    } else {
                                        R.string.reversi_turn_white
                                    },
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        if (game.passNotice) {
                            Text(
                                text = stringResource(R.string.reversi_pass),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFE082),
                            )
                        }
                    }
                    GamePauseButton(onClick = {
                        GameAudio.play(GameCue.UI_CLICK)
                        pause.open()
                        frame++
                    })
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { contentDescription = boardCd }
                        .pointerInput(game.phase, game.turn, game.board, pause.paused) {
                            if (pause.paused || game.phase != ReversiPhase.PLAYING || game.turn != REVERSI_BLACK) {
                                return@pointerInput
                            }
                            detectTapGestures { offset ->
                                val layout = reversiBoardLayout(size.width.toFloat(), size.height.toFloat())
                                val index = reversiHitIndex(layout, offset.x, offset.y) ?: return@detectTapGestures
                                if (game.playHumanMove(index)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    if (game.phase == ReversiPhase.OVER && !resultSubmitted) {
                                        submitIfNeeded()
                                        GameAudio.play(reversiOutcomeCue(game.outcome))
                                    } else {
                                        GameAudio.play(GameCue.KEY)
                                    }
                                    frame++
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                frame
                Canvas(modifier = Modifier.fillMaxSize()) {
                    frame
                    val layout = reversiBoardLayout(size.width, size.height)
                    val legal =
                        if (game.phase == ReversiPhase.PLAYING && game.turn == REVERSI_BLACK) {
                            game.legalMovesForTurn().toSet()
                        } else {
                            emptySet()
                        }
                    drawReversiBoard(
                        layout = layout,
                        board = game.board,
                        legal = legal,
                        lastMove = game.lastMove,
                        showLegalHints = game.phase == ReversiPhase.PLAYING,
                    )
                }
            }
        }

        when (game.phase) {
            ReversiPhase.READY ->
                GameReadyCard(
                    title = stringResource(R.string.reversi_title),
                    hint = stringResource(R.string.reversi_hint),
                    bestLine = gameBestLabel() + " $best",
                    startText = stringResource(R.string.reversi_start),
                    onStart = { startMatch() },
                    secondary =
                        GameCardAction(gameViewRank()) {
                            GameAudio.play(GameCue.UI_CLICK)
                            lb.entries = lb.store.load()
                            lb.show = true
                        },
                    extra = {
                        Text(
                            text = stringResource(R.string.reversi_diff_section),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        ReversiDifficulty.entries.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { mode ->
                                    FilterChip(
                                        selected = difficulty == mode,
                                        onClick = {
                                            difficulty = mode
                                            prefs.edit { putString(REVERSI_PREF_DIFF, mode.id) }
                                        },
                                        label = { Text(stringResource(reversiDifficultyLabelRes(mode))) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    },
                )
            ReversiPhase.OVER ->
                GameOverCard(
                    overTitle =
                        stringResource(
                            when (game.outcome) {
                                ReversiOutcome.BLACK_WIN -> R.string.reversi_over_win
                                ReversiOutcome.WHITE_WIN -> R.string.reversi_over_lose
                                ReversiOutcome.TIE -> R.string.reversi_over_tie
                                ReversiOutcome.NONE -> R.string.reversi_over_tie
                            },
                        ),
                    scoreLine = stringResource(R.string.reversi_score, game.blackCount, game.whiteCount),
                    bestLine = gameBestLabel() + " $best",
                    restartText = gameRestart(),
                    onRestart = { startMatch() },
                    onViewRank = {
                        GameAudio.play(GameCue.UI_CLICK)
                        lb.entries = lb.store.load()
                        lb.show = true
                    },
                    onExit = onExit,
                    rankLine = lb.lastRank.takeIf { it > 0 }?.let { "#$it" },
                )
            ReversiPhase.PLAYING -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = stringResource(R.string.reversi_score, game.blackCount, game.whiteCount),
                onResume = {
                    GameAudio.play(GameCue.UI_CLICK)
                    pause.resume()
                    frame++
                },
                onRestart = { startMatch() },
                onExit = onExit,
            )
        }

        if (lb.show) {
            GameLeaderboardDialog(
                title = stringResource(R.string.reversi_leaderboard),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    lb.resetLastRank()
                    best = 0
                    prefs.edit { putInt(REVERSI_BEST_KEY, 0) }
                },
                onDismiss = { lb.show = false },
            )
        }
    }
}

@StringRes
internal fun reversiDifficultyLabelRes(d: ReversiDifficulty): Int =
    when (d) {
        ReversiDifficulty.BEGINNER -> R.string.reversi_diff_beginner
        ReversiDifficulty.EASY -> R.string.reversi_diff_easy
        ReversiDifficulty.NORMAL -> R.string.reversi_diff_normal
        ReversiDifficulty.ADVANCED -> R.string.reversi_diff_advanced
        ReversiDifficulty.HARD -> R.string.reversi_diff_hard
        ReversiDifficulty.MASTER -> R.string.reversi_diff_master
        ReversiDifficulty.EXPERT -> R.string.reversi_diff_expert
    }
