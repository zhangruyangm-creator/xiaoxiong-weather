package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 提示高亮持续时间（毫秒）。 */
private const val HINT_HIGHLIGHT_MS = 1500L

/**
 * 迷宫对局装配层：只负责帧循环读取、手势、HUD/方向键/弹窗的组装。
 * 棋盘绘制在 MazeRender / MazeThemeArt / MazeOverlays，粒子在 MazeParticles，
 * HUD 在 MazeHud。玩法状态全部由 [MazeGame] 持有，本文件不修改规则。
 */
@Composable
internal fun MazePlayField(
    game: MazeGame,
    sprites: BearSprites,
    frame: State<Long>,
    fx: MazeFxHost,
    weather: FarmWeather,
    rainStrength: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    onExitSelect: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
    onPause: (() -> Unit)? = null,
    onMove: (MazeDir) -> Unit,
    onCharacter: () -> Unit,
) {
    val style = remember(game.level.theme) { mazeThemeStyle(game.level.theme) }
    val skyBrush = remember(style) { Brush.verticalGradient(skyGradient(style.skyTop, style.skyBottom)) }
    val baseLayer = remember(game.level) { MazeBaseLayerCache() }
    val reduceMotion = LocalReduceMotion.current
    // 系统栏 inset 以 px 传入绘制层：棋盘预留区与顶栏/D-pad 的实际占位一致
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    // moving/canUndo 由每帧变化的 moveT 派生：收敛为布尔翻转，避免滑行期间每帧重组
    val moving by remember { derivedStateOf { game.moving } }
    val canUndo by remember {
        derivedStateOf {
            // 撤销历史是非 State 的 ArrayDeque；历史变化必伴随 moves 变化，显式读取以驱动失效
            game.moves
            game.canUndo
        }
    }
    var swipeAcc by remember { mutableStateOf(Offset.Zero) }
    // 提示：高亮下一步方向，1.5s 后自动消失；无解 / 状态空间过大时短暂提示文案
    var hintDir by remember { mutableStateOf<MazeDir?>(null) }
    var showNoHint by remember { mutableStateOf(false) }
    var showHintUnavailable by remember { mutableStateOf(false) }

    // 移动/撤销/重开都会让提示对应的局面失效，统一清掉三类提示 UI
    fun clearHintUi() {
        hintDir = null
        showNoHint = false
        showHintUnavailable = false
    }

    fun showHint(result: MazeHint) {
        when (result) {
            is MazeHint.Direction -> hintDir = result.dir
            MazeHint.NoSolution -> showNoHint = true
            MazeHint.TooLarge -> showHintUnavailable = true
        }
    }

    // 提示求解放到后台线程：忙碌期间禁用按钮，避免连点重复 BFS
    val scope = rememberCoroutineScope()
    val hintSession = remember(game.level) { MazeHintSession() }
    var hintJob by remember { mutableStateOf<Job?>(null) }
    // 换关：取消在途求解并复位忙碌/缓存，旧 BFS 的结果不会残留到新关卡
    LaunchedEffect(game.level) {
        hintJob?.cancel()
        hintJob = null
    }
    LaunchedEffect(hintDir) {
        if (hintDir != null) {
            delay(HINT_HIGHLIGHT_MS)
            hintDir = null
        }
    }
    LaunchedEffect(showNoHint) {
        if (showNoHint) {
            delay(HINT_HIGHLIGHT_MS)
            showNoHint = false
        }
    }
    LaunchedEffect(showHintUnavailable) {
        if (showHintUnavailable) {
            delay(HINT_HIGHLIGHT_MS)
            showHintUnavailable = false
        }
    }
    // 任何一次移动都清掉提示；被墙/锁挡住时补一次回弹 + 尘土（与触感反馈同源条件）
    val move: (MazeDir) -> Unit = { dir ->
        clearHintUi()
        val wasMoving = game.moving
        onMove(dir)
        if (!wasMoving &&
            !game.moving &&
            game.phase == MazePhase.PLAYING &&
            !game.cleared
        ) {
            fx.bump(game.time, dir.dr, dir.dc, game.bearR, game.bearC)
        }
    }
    // 结算卡片稍后弹出：先让通关庆祝/失败垂头演出一小段，再展示星级
    var showResult by remember(game.level) { mutableStateOf(false) }
    LaunchedEffect(game.phase, game.level) {
        if (game.phase == MazePhase.CLEAR) {
            delay(if (game.failed) 420L else 1000L)
            showResult = true
        } else {
            showResult = false
        }
    }
    // 中英标题：无尽按波次用资源模板，每日用固定名，战役关卡按系统语言
    val levelTitle =
        when {
            game.endless -> stringResource(R.string.bear_maze_endless_wave, game.endlessWave)
            game.daily -> stringResource(R.string.bear_maze_daily)
            else -> "${game.level.id}. ${game.level.displayTitle()}"
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(skyBrush),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(game.levelIndex, game.phase) {
                        detectDragGestures(
                            onDragStart = { swipeAcc = Offset.Zero },
                            onDrag = { _, drag -> swipeAcc += drag },
                            onDragEnd = {
                                val dx = swipeAcc.x
                                val dy = swipeAcc.y
                                if (abs(dx) < 24f && abs(dy) < 24f) return@detectDragGestures
                                val dir =
                                    if (abs(dx) > abs(dy)) {
                                        if (dx > 0) MazeDir.RIGHT else MazeDir.LEFT
                                    } else {
                                        if (dy > 0) MazeDir.DOWN else MazeDir.UP
                                    }
                                move(dir)
                            },
                        )
                    }.pointerInput(game.levelIndex) {
                        detectTapGestures { /* 吞掉，避免误触 */ }
                    },
        ) {
            // 在 draw lambda 内读 frame：只使绘制阶段失效，不触发 MazePlayField 重组
            frame.value
            drawMazeWorld(
                game,
                sprites,
                style,
                baseLayer,
                fx,
                hintDir,
                reduceMotion,
                statusBarTopPx,
                navigationBarBottomPx,
            )
            drawMazeWeather(game, weather, rainStrength, freezingRain, hail, reduceMotion)
        }

        MazeHudTopBar(
            levelTitle = levelTitle,
            theme = game.level.theme,
            style = style,
            game = game,
            moving = moving,
            canUndo = canUndo,
            hintBusy = hintSession.busy,
            onClose = onExitSelect,
            onUndo = {
                clearHintUi()
                game.undo()
            },
            onHint = {
                if (!hintSession.busy) {
                    clearHintUi()
                    val request = game.hintRequest()
                    if (request != null) {
                        val cached = hintSession.cachedFor(request)
                        if (cached != null) {
                            // 同一局面重复点击：复用上次结果，不重复跑 BFS、不重复计数
                            showHint(cached)
                        } else {
                            val token = hintSession.begin(request)
                            hintJob =
                                scope.launch {
                                    try {
                                        val outcome =
                                            withContext(Dispatchers.Default) {
                                                mazeSolve(request.map, request.bearR, request.bearC, request.keys)
                                            }
                                        // 回主线程写回；局面已变化时 applyHint 返回 null 直接丢弃
                                        val hint = game.applyHint(request, outcome)
                                        if (hint != null && hintSession.complete(token, hint)) {
                                            showHint(hint)
                                        }
                                    } finally {
                                        hintSession.finish(token)
                                    }
                                }
                        }
                    }
                }
            },
            onRestart = {
                clearHintUi()
                // 重开可能复用同一关卡对象（remember/LaunchedEffect 不会重启）：
                // 这里显式取消在途求解并复位忙碌，避免旧 BFS 阻塞新一局的提示按钮
                hintJob?.cancel()
                hintJob = null
                hintSession.cancel()
                onRestart()
            },
            onPause = onPause,
            onCharacter = onCharacter,
        )

        val banner =
            when {
                game.needHoneyHintT > 0f ->
                    stringResource(R.string.bear_maze_need_honey) to Color(0xFFFFC107)
                showNoHint ->
                    stringResource(R.string.bear_maze_hint_none) to Color(0xFFFF8A80)
                showHintUnavailable ->
                    stringResource(R.string.bear_maze_hint_unavailable) to Color(0xFFB0BEC5)
                else -> null
            }
        if (banner != null) {
            MazeHintBanner(
                text = banner.first,
                accent = banner.second,
                style = style,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 250.dp),
            )
        }

        // 方向键（提示时高亮下一步方向；移动中禁用避免连点）；导航栏 inset 与顶栏对称
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MazeDPad(
                style = style,
                enabled = game.phase == MazePhase.PLAYING && !moving && !game.cleared,
                highlightDir = hintDir,
                onMove = move,
            )
        }

        if (game.phase == MazePhase.CLEAR && showResult) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                MazeResultOverlay(
                    game = game,
                    theme = game.level.theme,
                    style = style,
                    reduceMotion = reduceMotion,
                    onExitSelect = onExitSelect,
                    onRestart = onRestart,
                    onNext = onNext,
                )
            }
        }
    }
}
