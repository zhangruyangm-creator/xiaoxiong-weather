package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.delay

private val BOARD_GREEN = Color(0xFF2A5C44)
private val BOARD_LINE = Color(0xFF1E4030)
private val DISC_BLACK = Color(0xFF1A2228)
private val DISC_WHITE = Color(0xFFF2EDE4)
private val LEGAL_HINT = Color(0xFF7BE495)
private val LAST_MOVE = Color(0xFFFFD54F)

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

    LaunchedEffect(game.turn, game.phase) {
        if (game.phase == ReversiPhase.PLAYING && game.turn == REVERSI_WHITE) {
            delay(320)
            if (game.phase == ReversiPhase.PLAYING && game.turn == REVERSI_WHITE) {
                game.playAiMove()
                if (game.phase == ReversiPhase.OVER && !resultSubmitted) {
                    submitIfNeeded()
                    GameAudio.play(if (game.outcome == ReversiOutcome.BLACK_WIN) GameCue.WIN else GameCue.LOSE)
                } else {
                    GameAudio.play(GameCue.KEY)
                }
                frame++
            }
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
    Box(modifier = Modifier.fillMaxSize()) {
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
                                        R.string.reversi_turn_black
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
                        .pointerInput(game.phase, game.turn, game.board) {
                            if (game.phase != ReversiPhase.PLAYING || game.turn != REVERSI_BLACK) return@pointerInput
                            detectTapGestures { offset ->
                                val side = minOf(size.width, size.height).toFloat()
                                val left = (size.width - side) / 2f
                                val top = (size.height - side) / 2f
                                val cell = side / REVERSI_SIZE
                                val col = ((offset.x - left) / cell).toInt().coerceIn(0, REVERSI_SIZE - 1)
                                val row = ((offset.y - top) / cell).toInt().coerceIn(0, REVERSI_SIZE - 1)
                                val index = reversiIndex(row, col)
                                if (game.playHumanMove(index)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    if (game.phase == ReversiPhase.OVER && !resultSubmitted) {
                                        submitIfNeeded()
                                        GameAudio.play(
                                            if (game.outcome == ReversiOutcome.BLACK_WIN) GameCue.WIN else GameCue.LOSE,
                                        )
                                    } else {
                                        GameAudio.play(GameCue.KEY)
                                    }
                                    frame++
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                frame
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = size.minDimension
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    val cell = side / REVERSI_SIZE
                    drawRect(
                        BOARD_GREEN,
                        topLeft = Offset(left, top),
                        size = Size(side, side),
                    )
                    for (i in 0..REVERSI_SIZE) {
                        val x = left + i * cell
                        val y = top + i * cell
                        drawLine(BOARD_LINE, Offset(left, y), Offset(left + side, y), strokeWidth = 2f)
                        drawLine(BOARD_LINE, Offset(x, top), Offset(x, top + side), strokeWidth = 2f)
                    }
                    val legal =
                        if (game.phase == ReversiPhase.PLAYING && game.turn == REVERSI_BLACK) {
                            game.legalMovesForTurn().toSet()
                        } else {
                            emptySet()
                        }
                    for (i in 0 until REVERSI_CELLS) {
                        val r = reversiRow(i)
                        val c = reversiCol(i)
                        val cx = left + (c + 0.5f) * cell
                        val cy = top + (r + 0.5f) * cell
                        val disc = game.board[i]
                        if (i in legal) {
                            drawCircle(LEGAL_HINT.copy(alpha = 0.35f), cell * 0.12f, Offset(cx, cy))
                        }
                        if (i == game.lastMove) {
                            drawCircle(
                                LAST_MOVE.copy(alpha = 0.45f),
                                cell * 0.46f,
                                Offset(cx, cy),
                                style = Stroke(width = 3f),
                            )
                        }
                        when (disc) {
                            REVERSI_BLACK -> drawCircle(DISC_BLACK, cell * 0.42f, Offset(cx, cy))
                            REVERSI_WHITE -> drawCircle(DISC_WHITE, cell * 0.42f, Offset(cx, cy))
                        }
                    }
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
