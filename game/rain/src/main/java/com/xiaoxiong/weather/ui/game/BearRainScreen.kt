package com.xiaoxiong.weather.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion

/*
 * 小熊雨幕 · 对局界面。
 *
 * 架构沿用项目统一约定：
 *  - 玩法全在 [RainFall]（纯逻辑、可单测）；帧循环用公共 [GameFrameLoop]；
 *    快照用公共 [GameSnapshotLifecycle]；卡片 / 芯片 / 排行榜 / 主角复用 `game:common`。
 *
 * 时钟：歌曲时间由累加帧 dt 得到（[RainFall.tick]）。**本玩法不读音频时钟**——
 * 判定依据是方块的视觉位置，所以蓝牙耳机延迟不影响可玩性。
 *
 * 无障碍：装饰运动（背景雨丝）走 [decorativeTime]；玩法必需运动（方块下落）不受影响。
 */
@Composable
fun BearRainScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    cityName: String?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val prefs = rememberGamePrefs()
    val game = remember { RainFall() }

    val scene = remember(sceneCode, isDay) { rainSceneOf(sceneCode, isDay) }
    val palette = remember(scene) { rainPaletteOf(scene) }
    val lb = rememberLeaderboardState(prefs, RAIN_LEADERBOARD_KEY)
    val characterState = rememberBearCharacterState()
    val bearSprites = rememberBearSprites(characterState.selected)

    // 快照：本玩法不中途续玩，生命周期只负责清档
    val session =
        remember(game, prefs) {
            gameSnapshotSession(
                save = { p -> rainSaveSnapshot(p, seed = game.seed, score = game.score, maxCombo = game.maxCombo, elapsed = game.elapsed) },
                restore = { p -> rainRestoreSnapshot(game, p) },
                clear = { p -> rainClearSnapshot(p) },
            )
        }
    val exitGuard =
        GameSnapshotLifecycle(
            session = session,
            prefs = prefs,
            phase = rainPersistPhase(game.phase),
            shouldClear = ::rainSnapshotShouldClear,
        )

    val exitGame: () -> Unit = {
        exitGuard.exit(session, prefs)
        onExit()
    }

    // 离散 UI 状态（只在点击 / 结算时更新）
    var frame by remember { mutableIntStateOf(0) }
    var scoreState by remember { mutableIntStateOf(0) }
    var comboState by remember { mutableIntStateOf(0) }
    var isRecord by remember { mutableStateOf(false) }

    // 表现层瞬态
    var decorativeTime by remember { mutableFloatStateOf(0f) }
    var hitFlash by remember { mutableFloatStateOf(0f) }
    var showLeaderboard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { GameAudio.preload(context) }

    BackHandler {
        when {
            showLeaderboard -> showLeaderboard = false
            else -> exitGame()
        }
    }

    // 帧循环：推进 + 衰减闪光 + 唤醒重绘
    GameFrameLoop(
        key = rainSessionKey(game.seed),
        paused = { game.phase != RainPhase.PLAYING },
    ) { dt, _ ->
        game.tick(dt)
        if (!reduceMotion) decorativeTime += dt
        hitFlash = decayHitFlash(hitFlash, dt)
        frame++
    }

    /** 是否已结算：直接由引擎阶段派生，不使用手动标志。 */
    val isOver = game.phase == RainPhase.OVER

    // 结算入库（只做一次）
    LaunchedEffect(isOver) {
        if (isOver) {
            isRecord = rainRecordScore(prefs, game.score)
            if (game.score > 0) {
                lb.lastRank =
                    lb.store.submit(
                        LeaderboardEntry(
                            score = game.score,
                            timestamp = System.currentTimeMillis(),
                            city = cityName,
                            weather = leaderboardWeatherOf(context, sceneCode),
                        ),
                    ) ?: 0
            }
            rainClearSnapshot(prefs)
        }
    }

    val startRound = {
        game.prepare(rainNewSeed())
        game.start()
        isRecord = false
        scoreState = 0
        comboState = 0
        lb.resetLastRank()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(game.phase) {
                    // 单列玩法：点哪都是"点当前那块"，故不需要按 x 分列
                    detectTapGestures {
                        if (game.phase != RainPhase.PLAYING) return@detectTapGestures
                        when (game.tap(0.5f)) {
                            RainTapResult.HIT_DROP -> {
                                hitFlash = 1f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                GameAudio.play(rainCueFor(RainKind.DROP))
                                scoreState = game.score
                                comboState = game.combo
                            }
                            RainTapResult.TAPPED_HAIL -> {
                                hitFlash = 1f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                GameAudio.play(rainCueFor(RainKind.HAIL))
                                scoreState = game.score
                            }
                            null -> Unit // 空敲不罚
                        }
                    }
                },
    ) {
        // 场景绘制（frame 只在 draw 作用域读 → 每帧只重绘不重组）
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frame
            drawRainWorld(
                game = game,
                palette = palette,
                reduceMotion = reduceMotion,
                decorativeTime = decorativeTime,
                hitFlash = hitFlash,
                laneWidth = 0.dp,
            )
        }

        // 小熊在判定线下方（命中时轻微弹跳）
        RainBearStrip(
            sprites = bearSprites,
            jumping = hitFlash > 0.15f,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 顶部信息
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = scoreState.toString(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
            if (comboState >= 3) {
                Text(
                    text = stringResource(R.string.rain_combo, comboState),
                    color = palette.hittableRing,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // READY：必须给出图例，否则玩家无法分辨该点哪个
        if (game.phase == RainPhase.READY) {
            GameReadyCard(
                title = stringResource(R.string.rain_title),
                hint = stringResource(R.string.rain_hint),
                bestLine = stringResource(R.string.rain_best, rainBestScore(prefs)),
                startText = stringResource(R.string.rain_start),
                onStart = { startRound() },
                secondary = GameCardAction(text = gameExit(), onClick = exitGame),
                extra = { RainLegend(palette = palette) },
            )
        }

        // 结算
        if (isOver) {
            GameOverCard(
                overTitle =
                    stringResource(
                        when (game.overReason) {
                            RainOverReason.TAPPED_HAIL -> R.string.rain_over_hail
                            else -> R.string.rain_over_miss
                        },
                    ),
                scoreLine = stringResource(R.string.rain_score, game.score),
                bestLine = stringResource(R.string.rain_best, rainBestScore(prefs)),
                restartText = stringResource(R.string.rain_restart),
                onRestart = { startRound() },
                onViewRank = { showLeaderboard = true },
                onExit = exitGame,
                rankLine =
                    lb.lastRank
                        .takeIf { it > 0 }
                        ?.let { stringResource(R.string.rain_rank, it) },
            )
        }
    }

    if (showLeaderboard) {
        GameLeaderboardDialog(
            title = stringResource(R.string.rain_leaderboard),
            entries = lb.entries,
            onClear = {
                lb.store.clear(RAIN_LEADERBOARD_KEY)
                lb.entries = lb.store.load()
            },
            onDismiss = { showLeaderboard = false },
        )
    }
}

/**
 * READY 卡里的图例：**同时展示"要点"与"不能点"两种方块**。
 *
 * 这是区分度的第一道保险——玩家开局就明确知道规则，而不是靠第一次误点学。
 *
 * 关键：图例**直接调用 [drawRainTile]**（与游戏内完全相同的绘制路径），
 * 而不是用 Box + clip 近似拼一个"像圆的方块"。此前图例缺少斜杠、
 * 描边粗细也与实物不同，等于给玩家看了一张失真的说明图。
 */
@Composable
private fun RainLegend(
    palette: RainPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RainLegendItem(RainKind.DROP, palette, stringResource(R.string.rain_legend_drop))
        RainLegendItem(RainKind.HAIL, palette, stringResource(R.string.rain_legend_hail))
    }
}

/** 图例里的一行：用与游戏内相同的绘制代码画一个放大的方块 + 文字说明。 */
@Composable
private fun RainLegendItem(
    kind: RainKind,
    palette: RainPalette,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(34.dp)) {
            drawRainTile(
                kind = kind,
                center = center,
                radius = size.minDimension * 0.42f,
                palette = palette,
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 小熊条：判定线下方的角色，随命中轻微弹跳。 */
@Composable
private fun RainBearStrip(
    sprites: BearSprites,
    jumping: Boolean,
    modifier: Modifier = Modifier,
) {
    // 必须给**显式高度**：Canvas 底层是 Spacer，没有内在尺寸，
    // 只写 fillMaxWidth() 会因最小约束为 0 而量成 0 高度——小熊将完全不显示。
    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxWidth().height(RAIN_BEAR_STRIP_HEIGHT).padding(bottom = 6.dp),
    ) {
        val bearH = size.height * 0.92f
        drawGameBear(
            sprites = sprites,
            centerX = size.width / 2f,
            centerY = size.height * 0.58f,
            bs = bearH,
            pose = BearPose(frame = BearFrame.JUMP, scaleY = if (jumping) 0.92f else 1f),
        )
    }
}
