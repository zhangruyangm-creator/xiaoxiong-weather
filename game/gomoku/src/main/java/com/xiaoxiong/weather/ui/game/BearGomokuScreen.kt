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
import kotlin.math.roundToInt

private val TABLE_TOP = Color(0xFF1E4D38)
private val TABLE_BOTTOM = Color(0xFF0F2419)

@Composable
fun BearGomokuScreen(
    sceneCode: Int?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = rememberGamePrefs()
    val pause = rememberGamePauseState()
    val game = remember { GomokuGame() }
    val lb = rememberLeaderboardState(prefs, GOMOKU_LEADERBOARD_KEY, GOMOKU_BEST_KEY)
    var best by remember { mutableIntStateOf(prefs.intOr(GOMOKU_BEST_KEY, 0)) }
    var difficulty by remember { mutableStateOf(gomokuDifficultyFromPrefs(prefs)) }
    var frame by remember { mutableLongStateOf(0L) }
    var resultSubmitted by remember { mutableStateOf(false) }
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)

    LaunchedEffect(Unit) { GameAudio.preload(context) }

    fun audioForOutcome() {
        when (game.outcome) {
            GomokuOutcome.BLACK_WIN -> GameAudio.play(GameCue.WIN)
            GomokuOutcome.WHITE_WIN -> GameAudio.play(GameCue.LOSE)
            GomokuOutcome.DRAW -> GameAudio.play(GameCue.STAR)
            GomokuOutcome.NONE -> Unit
        }
    }

    fun submitIfNeeded() {
        if (resultSubmitted) return
        resultSubmitted = true
        val score = gomokuPlayerScore(game.outcome)
        if (score > best) {
            best = score
            prefs.edit { putInt(GOMOKU_BEST_KEY, best) }
        }
        if (game.outcome == GomokuOutcome.BLACK_WIN || game.outcome == GomokuOutcome.DRAW) {
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
        if (game.phase != GomokuPhase.PLAYING || game.turn != GOMOKU_WHITE || pause.paused) return@LaunchedEffect
        val thinkMs = if (difficulty.searchDepth >= 3) 480L else 320L
        delay(thinkMs)
        if (game.phase != GomokuPhase.PLAYING || game.turn != GOMOKU_WHITE || pause.paused) return@LaunchedEffect
        val boardSnapshot = game.board.copyOf()
        val diff = difficulty
        val index =
            withContext(Dispatchers.Default) {
                gomokuChooseAiMove(boardSnapshot, diff)
            }
        if (game.phase != GomokuPhase.PLAYING || game.turn != GOMOKU_WHITE || pause.paused) return@LaunchedEffect
        val played =
            if (index != null) {
                game.applyAiMove(index)
            } else {
                game.playAiMove()
            }
        if (!played) return@LaunchedEffect
        if (game.phase == GomokuPhase.OVER && !resultSubmitted) {
            submitIfNeeded()
            audioForOutcome()
        } else {
            GameAudio.play(GameCue.KEY)
        }
        frame++
    }

    BackHandler {
        when (game.phase) {
            GomokuPhase.PLAYING -> {
                pause.open()
                frame++
            }
            else -> onExit()
        }
    }

    val boardCd = stringResource(R.string.cd_gomoku_board)
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (game.phase == GomokuPhase.PLAYING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    if (game.turn == GOMOKU_BLACK) {
                                        R.string.gomoku_turn_black
                                    } else {
                                        R.string.gomoku_turn_white
                                    },
                                ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    GamePauseButton(onClick = {
                        GameAudio.play(GameCue.UI_CLICK)
                        pause.open()
                        frame++
                    })
                }
                Spacer(Modifier.height(4.dp))
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { contentDescription = boardCd }
                        .pointerInput(game.phase, game.turn) {
                            if (game.phase != GomokuPhase.PLAYING || game.turn != GOMOKU_BLACK) return@pointerInput
                            detectTapGestures { offset ->
                                val layout = gomokuBoardLayout(size.width.toFloat(), size.height.toFloat())
                                val col =
                                    ((offset.x - layout.left) / layout.cell)
                                        .roundToInt()
                                        .coerceIn(0, GOMOKU_SIZE - 1)
                                val row =
                                    ((offset.y - layout.top) / layout.cell)
                                        .roundToInt()
                                        .coerceIn(0, GOMOKU_SIZE - 1)
                                val index = gomokuIndex(row, col)
                                if (game.isLegalHumanMove(index)) {
                                    if (game.playHumanMove(index)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        if (game.phase == GomokuPhase.OVER && !resultSubmitted) {
                                            submitIfNeeded()
                                            audioForOutcome()
                                        } else {
                                            GameAudio.play(GameCue.KEY)
                                        }
                                        frame++
                                    }
                                } else if (game.phase == GomokuPhase.PLAYING && game.turn == GOMOKU_BLACK) {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                frame
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val layout = gomokuBoardLayout(size.width, size.height)
                    val legal = game.legalHintMoves().toSet()
                    drawGomokuBoard(
                        layout = layout,
                        board = game.board,
                        legal = legal,
                        lastMove = game.lastMove,
                        showLegalHints = game.phase == GomokuPhase.PLAYING && game.turn == GOMOKU_BLACK,
                    )
                }
            }
        }

        when (game.phase) {
            GomokuPhase.READY ->
                GameReadyCard(
                    title = stringResource(R.string.gomoku_title),
                    hint = stringResource(R.string.gomoku_hint),
                    bestLine = gameBestLabel() + " $best",
                    startText = stringResource(R.string.gomoku_start),
                    onStart = { startMatch() },
                    secondary =
                        GameCardAction(gameViewRank()) {
                            GameAudio.play(GameCue.UI_CLICK)
                            lb.entries = lb.store.load()
                            lb.show = true
                        },
                    extra = {
                        Text(
                            text = stringResource(R.string.gomoku_diff_section),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        GomokuDifficulty.entries.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { mode ->
                                    FilterChip(
                                        selected = difficulty == mode,
                                        onClick = {
                                            difficulty = mode
                                            prefs.edit { putString(GOMOKU_PREF_DIFF, mode.id) }
                                        },
                                        label = { Text(stringResource(gomokuDifficultyLabelRes(mode))) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    },
                )
            GomokuPhase.OVER ->
                GameOverCard(
                    overTitle =
                        stringResource(
                            when (game.outcome) {
                                GomokuOutcome.BLACK_WIN -> R.string.gomoku_over_win
                                GomokuOutcome.WHITE_WIN -> R.string.gomoku_over_lose
                                GomokuOutcome.DRAW -> R.string.gomoku_over_draw
                                GomokuOutcome.NONE -> R.string.gomoku_over_draw
                            },
                        ),
                    scoreLine = stringResource(R.string.gomoku_match_score, gomokuPlayerScore(game.outcome)),
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
            GomokuPhase.PLAYING -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = stringResource(R.string.gomoku_best_line, best),
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
                title = stringResource(R.string.gomoku_leaderboard),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    lb.resetLastRank()
                    best = 0
                    prefs.edit { putInt(GOMOKU_BEST_KEY, 0) }
                },
                onDismiss = { lb.show = false },
            )
        }
    }
}

@StringRes
internal fun gomokuDifficultyLabelRes(d: GomokuDifficulty): Int =
    when (d) {
        GomokuDifficulty.BEGINNER -> R.string.gomoku_diff_beginner
        GomokuDifficulty.EASY -> R.string.gomoku_diff_easy
        GomokuDifficulty.NORMAL -> R.string.gomoku_diff_normal
        GomokuDifficulty.ADVANCED -> R.string.gomoku_diff_advanced
        GomokuDifficulty.HARD -> R.string.gomoku_diff_hard
        GomokuDifficulty.MASTER -> R.string.gomoku_diff_master
        GomokuDifficulty.EXPERT -> R.string.gomoku_diff_expert
    }
