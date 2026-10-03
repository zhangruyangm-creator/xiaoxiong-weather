package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

private val TILE_DARK = Color(0xFF1C2733)
private val TILE_DARK_PRIMED = Color(0xFF2E4054)
private val TILE_LIGHT = Color(0xFFF3EFE6)
private val TILE_HIT = Color(0xFF3DDC97)
private val TILE_FAIL = Color(0xFFE85D4C)
private val BOARD_GAP = Color(0xFF0B1016)

private const val HIT_FX_MS = 380L

private data class RainHitFx(
    val row: Int,
    val col: Int,
    val startedAt: Long,
    val sparkle: Boolean,
)

/**
 * 小熊方块：四列点黑块；部分黑块带小熊图案或需连点两次。
 */
@Composable
fun BearRainScreen(
    sceneCode: Int?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = rememberGamePrefs()
    val pause = rememberGamePauseState()
    val game = remember { RainTiles() }
    val lb = rememberLeaderboardState(prefs, RAIN_LEADERBOARD_KEY, RAIN_BEST_KEY)
    var best by remember { mutableIntStateOf(prefs.intOr(RAIN_BEST_KEY, 0)) }
    var doubleTapEnabled by remember { mutableStateOf(tilesDoubleTapEnabled(prefs)) }
    var frame by remember { mutableLongStateOf(0L) }
    var resultSubmitted by remember { mutableStateOf(false) }
    val hitFx = remember { mutableStateListOf<RainHitFx>() }
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)

    LaunchedEffect(Unit) { GameAudio.preload(context) }

    fun submitIfNeeded() {
        if (resultSubmitted) return
        resultSubmitted = true
        if (game.score > best) {
            best = game.score
            prefs.edit { putInt(RAIN_BEST_KEY, best) }
        }
        if (game.score > 0) {
            lb.lastRank =
                lb.store.submit(
                    LeaderboardEntry(
                        score = game.score,
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

    fun registerFx(
        row: Int,
        col: Int,
        sparkle: Boolean,
    ) {
        hitFx.add(RainHitFx(row, col, System.currentTimeMillis(), sparkle))
        if (hitFx.size > 12) hitFx.removeAt(0)
    }

    fun handleTapResult(
        row: Int,
        col: Int,
        result: RainTapResult,
    ) {
        when (result) {
            RainTapResult.HIT -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                GameAudio.play(GameCue.KEY)
            }
            RainTapResult.HIT_BEAR -> {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                GameAudio.play(GameCue.STAR)
                registerFx(row, col, sparkle = true)
            }
            RainTapResult.HIT_DOUBLE_PRIMED -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                GameAudio.play(GameCue.POWERUP)
            }
            RainTapResult.HIT_DOUBLE_CLEAR -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                GameAudio.play(GameCue.COIN)
                registerFx(row, col, sparkle = true)
            }
            RainTapResult.WHITE -> {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                GameAudio.play(GameCue.HIT)
                if (!resultSubmitted) {
                    submitIfNeeded()
                    GameAudio.play(GameCue.LOSE)
                }
            }
            RainTapResult.WHITE_WARMUP -> {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                GameAudio.play(GameCue.HIT)
            }
            RainTapResult.IGNORE -> Unit
        }
    }

    fun startRun() {
        GameAudio.play(GameCue.UI_CLICK)
        resultSubmitted = false
        lb.resetLastRank()
        pause.resume()
        hitFx.clear()
        game.start(seed = System.nanoTime(), allowDouble = doubleTapEnabled)
        frame++
    }

    BackHandler {
        when (game.phase) {
            RainPhase.PLAYING -> {
                pause.open()
                frame++
            }
            else -> onExit()
        }
    }

    GameFrameLoop(
        paused = { lb.show || pause.paused || game.phase != RainPhase.PLAYING },
        onTick = { dt, _ ->
            game.tick(dt)
            val now = System.currentTimeMillis()
            hitFx.removeAll { now - it.startedAt > HIT_FX_MS }
            if (game.phase == RainPhase.OVER && !resultSubmitted) {
                submitIfNeeded()
                GameAudio.play(GameCue.LOSE)
            }
            frame++
        },
    )

    val boardCd = stringResource(R.string.cd_rain_board)
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = boardCd }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (game.phase != RainPhase.PLAYING || pause.paused) return@awaitEachGesture
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val col = rainColAt(down.position.x / w)
                            val row = rainRowAt(game.scroll, down.position.y / h)
                            val result = game.tap(down.position.x / w, down.position.y / h)
                            if (row >= 0) handleTapResult(row, col, result)
                            frame++
                        }
                    },
        ) {
            frame
            val now = System.currentTimeMillis()
            val w = size.width
            val h = size.height
            drawRect(BOARD_GAP)
            val first = floor(game.scroll - RAIN_ROWS_ON_SCREEN).toInt()
            val last = ceil(game.scroll.toDouble()).toInt()
            val colW = w / RAIN_COLUMNS
            val rowH = h / RAIN_ROWS_ON_SCREEN
            for (row in first..last) {
                if (row < 0) continue
                val top = rainCellTopPx(game.scroll, row, h)
                if (top >= h || top + rowH <= 0f) continue
                val blackCol = rainBlackCol(row, game.seed)
                val kind = game.kindAt(row)
                val primed = game.isPrimed(row)
                for (col in 0 until RAIN_COLUMNS) {
                    val failed = game.phase == RainPhase.OVER && row == game.failRow && col == game.failCol
                    val cleared = game.isCleared(row)
                    val isBlack = col == blackCol
                    val color =
                        when {
                            failed -> TILE_FAIL
                            cleared && isBlack -> TILE_HIT
                            isBlack && primed -> TILE_DARK_PRIMED
                            isBlack -> TILE_DARK
                            else -> TILE_LIGHT
                        }
                    val left = rainCellLeftPx(col, w) + 1f
                    val cellTop = top + 1f
                    val cellW = colW - 2f
                    val cellH = rowH - 2f
                    drawRect(
                        color = color,
                        topLeft = Offset(left, cellTop),
                        size = Size(cellW, cellH),
                    )
                    if (!isBlack || cleared) continue
                    val cx = left + cellW * 0.5f
                    val cy = cellTop + cellH * 0.5f
                    val cellMin = min(cellW, cellH)
                    when {
                        primed || kind == RainBlackKind.BEAR ->
                            drawRainTileBearFace(cx, cy, cellMin, alpha = 1f, primed = primed)
                        kind == RainBlackKind.DOUBLE ->
                            drawRainTileDoubleHint(cx, cy, cellMin)
                        else -> Unit
                    }
                }
            }
            for (fx in hitFx) {
                if (fx.row < first || fx.row > last) continue
                val top = rainCellTopPx(game.scroll, fx.row, h)
                val left = rainCellLeftPx(fx.col, w)
                val cx = left + colW * 0.5f
                val cy = top + rowH * 0.5f
                val phase = ((now - fx.startedAt).toFloat() / HIT_FX_MS).coerceIn(0f, 1f)
                if (fx.sparkle) drawRainHitSparkles(cx, cy, min(colW, rowH), phase)
            }
        }

        if (game.phase == RainPhase.PLAYING) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rain_score, game.score),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                GamePauseButton(onClick = {
                    GameAudio.play(GameCue.UI_CLICK)
                    pause.open()
                    frame++
                })
            }
        }

        when (game.phase) {
            RainPhase.READY ->
                GameReadyCard(
                    title = stringResource(R.string.rain_title),
                    hint = stringResource(R.string.rain_hint),
                    bestLine = gameBestLabel() + " $best",
                    startText = stringResource(R.string.rain_start),
                    onStart = { startRun() },
                    secondary =
                        GameCardAction(gameViewRank()) {
                            GameAudio.play(GameCue.UI_CLICK)
                            lb.entries = lb.store.load()
                            lb.show = true
                        },
                    extra = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.rain_option_double),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = doubleTapEnabled,
                                onCheckedChange = { enabled ->
                                    doubleTapEnabled = enabled
                                    prefs.edit { putBoolean(TILES_PREF_DOUBLE, enabled) }
                                },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.rain_speed_cap_note,
                                    RainSpeed.MAX,
                                    String.format("%.1f", RainSpeed.secondsPerScreen(999)),
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!doubleTapEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.rain_option_double_off_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            RainPhase.OVER ->
                GameOverCard(
                    overTitle =
                        stringResource(
                            if (game.fail == RainFail.MISS) R.string.rain_over_miss else R.string.rain_over_white,
                        ),
                    scoreLine = stringResource(R.string.rain_score, game.score),
                    bestLine = gameBestLabel() + " $best",
                    restartText = gameRestart(),
                    onRestart = { startRun() },
                    onViewRank = {
                        GameAudio.play(GameCue.UI_CLICK)
                        lb.entries = lb.store.load()
                        lb.show = true
                    },
                    onExit = onExit,
                    rankLine = lb.lastRank.takeIf { it > 0 }?.let { "#$it" },
                )
            RainPhase.PLAYING -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = stringResource(R.string.rain_score, game.score),
                onResume = {
                    GameAudio.play(GameCue.UI_CLICK)
                    pause.resume()
                    frame++
                },
                onRestart = { startRun() },
                onExit = onExit,
            )
        }

        if (lb.show) {
            GameLeaderboardDialog(
                title = stringResource(R.string.rain_leaderboard),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    lb.resetLastRank()
                    best = 0
                    prefs.edit { putInt(RAIN_BEST_KEY, 0) }
                },
                onDismiss = { lb.show = false },
            )
        }
    }
}
