package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import kotlin.math.cos
import kotlin.math.sin

private fun catchSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): CatchScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> CatchScene.FREEZING_RAIN
        isHailCode(sceneCode) -> CatchScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> CatchScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> CatchScene.RAIN_LIGHT
                        RainStrength.HEAVY -> CatchScene.RAIN_HEAVY
                        else -> CatchScene.RAIN
                    }
                FarmWeather.STORMY -> CatchScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> CatchScene.CLOUDY
                FarmWeather.SUNNY -> CatchScene.SUNNY
            }
    }

/** 飘分浮字（接住/中招提示）。 */
private class CatchFloatText(
    val text: String,
    val x: Float,
    var y: Float,
    var life: Float,
    val color: Color,
)

private val catchTextPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

/** 单位闪电（以原点为中心、落在 [-1,1]），绘制时 translate/scale 复用，避免每帧 new Path。 */
private val unitLightningPath: Path =
    Path().apply {
        moveTo(-0.5f, -1f)
        lineTo(0.2f, -0.1f)
        lineTo(-0.05f, -0.05f)
        lineTo(0.6f, 1f)
        lineTo(0.1f, 0.3f)
        lineTo(0.35f, 0.28f)
        lineTo(-0.6f, -0.5f)
        close()
    }

private val catchGroundBrush =
    Brush.verticalGradient(listOf(Color(0xFF8BC34A), Color(0xFF558B2F)))

/**
 * 小熊接雨雪：竖屏接住类。小熊底部左右移动（手指跟随），接得分物、躲惩罚物。
 * 掉落物种类由当前天气决定：“接雨滴/雪花/硬币/花瓣，躲乌云/冰雹/闪电”。
 */
@Composable
fun BearCatchScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    val lb =
        rememberLeaderboardState(
            prefs,
            "leaderboard_catch",
            BearCatchGame.PREFS_BEST_SCORE_KEY,
        )
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    val game =
        remember {
            BearCatchGame().also {
                it.best = BearCatchGame.loadBestScore(prefs)
                it.difficulty = BearCatchGame.loadDifficulty(prefs)
            }
        }
    // 进程重建（如旋转/被系统回收后重进）时恢复局中快照；坏档/无档保持标题
    LaunchedEffect(Unit) {
        GameAudio.preload(context)
        game.restoreSnapshot(prefs)
    }
    // 退出流程（HUD 关闭 / 暂停退出 / 返回键）：先清档并置标记，
    // 避免随后的 onDispose 兜底把已结束/已退出的对局重新落盘
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
    // 死亡结算后清档：重进应从标题开始，而不是续到已结束的对局
    LaunchedEffect(game.phase) {
        if (game.phase == GamePhase.OVER) {
            game.clearSnapshot(prefs)
            GameAudio.play(GameCue.LOSE)
        }
    }
    val texts = remember { mutableListOf<CatchFloatText>() }
    // 飘字在非组合的帧回调里生成，提前在组合期解析文案
    val blockedText = stringResource(R.string.bear_catch_blocked)
    val catchWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（天气决定掉落物种类与节奏）
    LaunchedEffect(catchWeather, sceneCode) {
        game.scene = catchSceneFromWeather(catchWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites(characterState.selected)
    // 减弱动画：跳过雨雪/雷电/风暴粒子，只保留静态天空
    val reduceMotion = LocalReduceMotion.current
    var frame by remember { mutableLongStateOf(0L) }

    // 帧循环：切后台不推进、dt 钳制由共享层处理。
    // tick / 飘字推进只在对局运行（每帧）或节流帧（约 12fps）时执行。
    var slowTick by remember { mutableStateOf(0) }
    // 节流帧的 dt 不能直接用单帧值：否则非对局界面（结算飘字/死亡粒子等）会以
    // 真实速度的 1/5 慢动作运行。这里按真实帧累计 animDt 后整笔提交（对齐 farm 写法）。
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showCharacterPicker || pause.paused },
        onTick = { dt, now ->
            val running = game.phase == GamePhase.RUNNING
            animDt += dt
            if (running || slowTick % 5 == 0) {
                // 死亡结算走慢动作，其余界面按真实时间推进
                val worldDt = animDt * gameOverTimeScale(game.phase)
                animDt = 0f
                // 飘字整体推进（原地更新，避免每帧重建列表）
                val ite = texts.iterator()
                while (ite.hasNext()) {
                    val t = ite.next()
                    t.life -= worldDt
                    if (t.life <= 0f) {
                        ite.remove()
                    } else {
                        t.y = t.y - 46f * game.s * worldDt
                    }
                }
                var caughtSoundPlayed = false
                var powerUpSoundPlayed = false
                game.tick(
                    worldDt,
                    onCaught = { kind ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        when (kind) {
                            ItemKind.GIFT, ItemKind.BUBBLE -> {
                                if (!powerUpSoundPlayed) {
                                    powerUpSoundPlayed = true
                                    GameAudio.play(GameCue.STAR)
                                }
                            }
                            else -> {
                                if (!caughtSoundPlayed) {
                                    caughtSoundPlayed = true
                                    GameAudio.play(GameCue.CATCH)
                                }
                            }
                        }
                        // 护盾泡泡不加分：跳过 "+0" 飘字，避免误导
                        if (kind != ItemKind.BUBBLE) {
                            texts.add(
                                CatchFloatText(
                                    text = "+${BearCatchLogic.scoreOf(kind)}",
                                    x = game.catchX,
                                    y = game.catchY - game.catchR * 1.4f,
                                    life = 0.8f,
                                    color = Color(0xFFFFD54F),
                                ),
                            )
                        }
                    },
                    onHit = { kind ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        GameAudio.play(GameCue.HIT)
                        texts.add(
                            CatchFloatText(
                                text = "-${BearCatchLogic.damageOf(kind)}",
                                x = game.catchX,
                                y = game.catchY - game.catchR * 1.4f,
                                life = 0.8f,
                                color = Color(0xFFFF6E6E),
                            ),
                        )
                    },
                    onBlocked = {
                        // 护盾挡下：青色「格挡」飘字 + 轻震，不再飘红字「-N」
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        GameAudio.play(GameCue.BLOCK)
                        texts.add(
                            CatchFloatText(
                                text = blockedText,
                                x = game.catchX,
                                y = game.catchY - game.catchR * 1.4f,
                                life = 0.8f,
                                color = Color(0xFF4DD0E1),
                            ),
                        )
                    },
                    onMilestone = { milestone ->
                        // 里程碑只飘一次大字，不阻塞操作
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        texts.add(
                            CatchFloatText(
                                text = context.getString(R.string.bear_catch_milestone, milestone),
                                x = game.catchX,
                                y = game.catchY - game.catchR * 2.4f,
                                life = 1.2f,
                                color = Color(0xFFFFC107),
                            ),
                        )
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        // 死亡即清档：不能只等 LaunchedEffect，进程被回收会漏掉清档而重复续玩
                        game.clearSnapshot(prefs)
                        game.recordBestScore(prefs, score)
                        if (score > 0) {
                            lb.lastRank =
                                lb.store.submit(
                                    LeaderboardEntry(
                                        score = score,
                                        timestamp = System.currentTimeMillis(),
                                        city = reportCityName,
                                        weather = leaderboardWeatherOf(context, reportSceneCode),
                                    ),
                                ) ?: 0
                        }
                    },
                )
                frame = now
            }
            if (!running) slowTick++
        },
    )

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase == GamePhase.RUNNING -> pause.open()
            else -> exitGame()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    // 暂停中改尺寸（分屏/折叠）保留对局，避免菜单分数与实际进度不一致
                    val wasRunning = game.phase == GamePhase.RUNNING
                    game.resize(IntSize(sz.width, sz.height), preserveRunning = pause.paused)
                    // 改窗口导致对局回准备态：立即清掉旧档，停在 READY 被杀也不会僵尸续玩
                    if (wasRunning && game.phase != GamePhase.RUNNING) game.clearSnapshot(prefs)
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(game.phase) {
                        // 仅对局中启用手势，避免与「开始」按钮/点按手势抢事件
                        if (game.phase != GamePhase.RUNNING) return@pointerInput
                        // 按住即转向：不走 drag 的 touchSlop，静止按住也能控左右。
                        // try/finally：phase 变化会取消本协程（pointerInput key），
                        // 拖动中被取消也要复位 touchX，否则残留坐标会让熊朝旧位置漂移
                        // （对齐 hockey 的写法）。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.touchX = down.position.x
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        game.touchX = change.position.x
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                game.touchX = null
                            }
                        }
                    },
        ) {
            frame // 订阅每帧更新，触发逐帧重绘
            if (game.size == IntSize.Zero) return@Canvas
            // 命中惩罚的屏幕震动：整幅画面随机小位移
            val shake = if (game.shakeT > 0f) game.shakeT * 12f * game.s else 0f
            withTransform({
                translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * shake * 2f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * shake * 2f,
                )
            }) {
                drawCatchWorld(
                    game = game,
                    isDay = isDay ?: true,
                    sprites = bearSprites,
                    texts = texts,
                    reduceMotion = reduceMotion,
                )
            }
        }

        CatchHud(
            game = game,
            onExit = { exitGame() },
            onPause = if (game.phase == GamePhase.RUNNING) pause::open else null,
            onLeaderboard = {
                lb.entries = lb.store.load()
                lb.show = true
            },
            onCharacter = { showCharacterPicker = true },
        )

        if (showCharacterPicker) {
            GameCharacterPickerDialog(
                state = characterState,
                onDismiss = { showCharacterPicker = false },
            )
        }

        if (lb.show) {
            GameLeaderboardDialog(
                title = stringResource(R.string.bear_leaderboard_title),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    lb.resetLastRank()
                    game.best = 0
                    // 旧版最高分键一并清掉，避免下次读取时又被迁移回来
                    prefs.edit { remove(BearCatchGame.PREFS_BEST_SCORE_LEGACY_KEY) }
                },
                onDismiss = { lb.show = false },
            )
        }

        when (game.phase) {
            GamePhase.READY -> {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.bear_catch_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bear_catch_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.bear_catch_difficulty_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CatchDifficulty.entries.forEach { difficulty ->
                                CatchDifficultyChip(
                                    difficulty = difficulty,
                                    selected = game.difficulty == difficulty,
                                    onClick = {
                                        game.difficulty = difficulty
                                        game.persistDifficulty(prefs)
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text =
                                stringResource(R.string.bear_game_best_label) +
                                    " ${game.best}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                            lb.resetLastRank()
                            game.start()
                        }) {
                            Text(stringResource(R.string.bear_catch_start))
                        }
                    }
                }
            }
            GamePhase.OVER -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .pointerInput(Unit) {
                                // 吞掉底层 Canvas 的拖动 / 点击
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                )
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.bear_catch_over),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${game.score}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text =
                                stringResource(R.string.bear_game_best_label) +
                                    " ${game.best}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lb.lastRank > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text =
                                    stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        TextButton(
                            onClick = {
                                lb.entries = lb.store.load()
                                lb.show = true
                            },
                        ) {
                            Text(stringResource(R.string.bear_game_view_rank))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { exitGame() }) {
                                Text(stringResource(R.string.bear_game_exit))
                            }
                            Button(onClick = {
                                lb.resetLastRank()
                                game.clearSnapshot(prefs)
                                game.start()
                            }) {
                                Text(stringResource(R.string.bear_catch_restart))
                            }
                        }
                    }
                }
            }
            GamePhase.RUNNING -> Unit
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = "${game.score}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    lb.resetLastRank()
                    game.clearSnapshot(prefs)
                    game.start()
                },
                onExit = { exitGame() },
            )
        }
    }
}

@Composable
private fun CatchHud(
    game: BearCatchGame,
    onExit: () -> Unit,
    onPause: (() -> Unit)?,
    onLeaderboard: () -> Unit,
    onCharacter: () -> Unit,
) {
    // 派生整数秒/布尔翻转：doubleT/shieldCooldownT 每帧变化，但只在显示值变化时重组 HUD，
    // 避免整个顶栏跟着帧循环 60fps 重组（数值口径与原先 toInt()+1 一致）。
    val doubleSeconds by remember { derivedStateOf { if (game.doubleT > 0f) game.doubleT.toInt() + 1 else 0 } }
    val shieldSeconds by remember { derivedStateOf { if (game.shieldCooldownT > 0f) game.shieldCooldownT.toInt() + 1 else 0 } }
    val blink by remember { derivedStateOf { game.invincibleT > 0f && ((game.invincibleT * 10f) % 1f < 0.5f) } }
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
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close_game),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 双倍得分倒计时
                if (doubleSeconds > 0) {
                    Text(
                        text = stringResource(R.string.bear_catch_double_time, doubleSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                    )
                }
                // 连击（与 comboScoreMultiplier 的 ×5 封顶保持一致）
                if (game.combo >= 2) {
                    Text(
                        text = stringResource(R.string.bear_catch_combo, minOf(game.combo, 5)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF176),
                    )
                }
                Text(
                    text = stringResource(R.string.bear_catch_score_value, game.score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.then(if (blink) Modifier else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(BearCatchGame.MAX_HEARTS) { i ->
                        Icon(
                            imageVector = if (i < game.hearts) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription =
                                if (i < game.hearts) {
                                    stringResource(R.string.cd_catch_heart_full, i + 1, BearCatchGame.MAX_HEARTS)
                                } else {
                                    stringResource(R.string.cd_catch_heart_empty, i + 1, BearCatchGame.MAX_HEARTS)
                                },
                            tint =
                                if (i <
                                    game.hearts
                                ) {
                                    Color(0xFFFF6B6B).copy(alpha = if (blink) 0.35f else 1f)
                                } else {
                                    Color.White.copy(alpha = 0.45f)
                                },
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    // 一次性护盾：持有时亮色 + 半透明边框，冷却中显示倒计时并灰显
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription =
                            when {
                                game.hasShield -> stringResource(R.string.cd_catch_shield_ready)
                                shieldSeconds > 0 ->
                                    stringResource(
                                        R.string.cd_catch_shield_cooldown,
                                        shieldSeconds,
                                    )
                                else -> stringResource(R.string.cd_catch_shield_none)
                            },
                        tint =
                            when {
                                game.hasShield -> Color(0xFF80DEEA)
                                shieldSeconds > 0 -> Color.White.copy(alpha = 0.30f)
                                else -> Color.White.copy(alpha = 0.20f)
                            },
                        modifier = Modifier.size(14.dp),
                    )
                    if (!game.hasShield && shieldSeconds > 0) {
                        Text(
                            text =
                                stringResource(
                                    R.string.bear_catch_shield_cooldown,
                                    shieldSeconds,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                    // 风暴爆发波次提示
                    if (game.inBurstWave) {
                        val burstDesc = stringResource(R.string.cd_catch_burst)
                        Text(
                            text = stringResource(R.string.bear_catch_burst_icon),
                            modifier =
                                Modifier.semantics {
                                    contentDescription = burstDesc
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFFEB3B),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bear_catch_best_suffix, game.best),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (onPause != null) GamePauseButton(onClick = onPause)
        GameCharacterButton(onClick = onCharacter)
        LeaderboardButton(onClick = onLeaderboard)
    }
}

/** 难度档位选择胶囊：当前档高亮，点击后立即写入 prefs。 */
@Composable
private fun CatchDifficultyChip(
    difficulty: CatchDifficulty,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(difficultyLabelRes(difficulty))) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@StringRes
private fun difficultyLabelRes(difficulty: CatchDifficulty): Int =
    when (difficulty) {
        CatchDifficulty.CASUAL -> R.string.bear_catch_difficulty_casual
        CatchDifficulty.STANDARD -> R.string.bear_catch_difficulty_standard
        CatchDifficulty.CHALLENGE -> R.string.bear_catch_difficulty_challenge
    }

// ---------------- 世界绘制 ----------------

private fun DrawScope.drawCatchWorld(
    game: BearCatchGame,
    isDay: Boolean,
    sprites: BearSprites,
    texts: List<CatchFloatText>,
    /** 减弱动画：跳过天气氛围粒子，只保留静态天空底色。 */
    reduceMotion: Boolean = false,
) {
    val w = size.width
    val h = size.height
    val s = game.s
    val scene = game.scene

    drawCatchSky(scene, isDay, w, h)

    // 背景云（所有场景淡淡一层）
    drawCatchBgClouds(game)

    // 天气氛围粒子（作为背景层，与跑酷/摘星/迷宫共用渲染）
    drawCatchWeatherBackdrop(scene, game.time, s, w, h, reduceMotion)

    // HUD 暗角：天空/云层之上、掉落物等内容之下，只影响背景不压暗玩法元素
    drawCatchHudScrim(catchSceneSky(scene, isDay), w, h)

    // 掉落物
    for (it in game.items) {
        drawCatchItem(it, game.time)
    }

    // 地面（底部一条绿草坡）
    val groundY = game.catchY + game.catchR * 0.6f
    drawRect(
        catchGroundBrush,
        topLeft = Offset(0f, groundY),
        size = Size(w, h - groundY),
    )
    drawLine(
        Color(0xFF6FA03A),
        Offset(0f, groundY),
        Offset(w, groundY),
        strokeWidth = 3f * s,
        cap = StrokeCap.Round,
    )

    // 小熊
    drawCatchBear(game, sprites)

    // 爆点粒子
    for (p in game.particles) {
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(
            p.color,
            (4f * s) * (alpha * 0.7f + 0.3f),
            Offset(p.x, p.y),
            alpha = alpha * 0.9f,
        )
    }

    // 飘字
    drawCatchTexts(game, texts)

    // 受击闪红
    if (game.shakeT > 0f) {
        drawRect(Color(0xFFFF5252).copy(alpha = game.shakeT * 0.22f))
    }
}

private fun DrawScope.drawCatchSky(
    scene: CatchScene,
    isDay: Boolean,
    w: Float,
    h: Float,
) {
    val sky = catchSceneSky(scene, isDay)
    drawRect(sky.skyBrush)
    drawHorizonGlow(h * 0.95f, midColor(sky.bottom, Color.White), h * 0.16f)
}

/** 顶部暗角：压暗 HUD 身后的天空/云层，浅色场景按 HUD 区间最亮色保证白字 ≥4.5:1。 */
private fun DrawScope.drawCatchHudScrim(
    sky: CatchSkyStyle,
    w: Float,
    h: Float,
) {
    if (sky.hudScrimAlpha <= 0f) return
    drawRect(
        sky.hudScrimBrush,
        topLeft = Offset.Zero,
        size = Size(w, h * CATCH_HUD_SCRIM_BAND),
    )
}

private fun DrawScope.drawCatchBgClouds(game: BearCatchGame) {
    val s = game.s
    val w = size.width
    for (i in 0 until 3) {
        val cy = (i * 97f + 40f) * s
        if (cy > size.height * 0.55f) continue
        val cx = ((i * 331f * s + game.time * (3f + i)) % (w + 200f * s)) - 100f * s
        val cw = (60f + i * 16f) * s
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.55f, Offset(cx - cw * 0.4f, cy))
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.75f, Offset(cx, cy + cw * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.22f), cw * 0.5f, Offset(cx + cw * 0.55f, cy + cw * 0.12f))
        drawRect(Color.White.copy(alpha = 0.22f), Offset(cx - cw * 0.85f, cy), Size(cw * 1.7f, cw * 0.45f))
    }
}

private fun DrawScope.drawCatchWeatherBackdrop(
    scene: CatchScene,
    time: Float,
    s: Float,
    w: Float,
    h: Float,
    reduceMotion: Boolean,
) {
    // 减弱动画：雨/雪/风线/闪电全部为高频粒子，直接跳过，保留上方静态天空
    if (reduceMotion) return
    when (scene) {
        CatchScene.RAIN_LIGHT -> {
            drawRainLayered(
                fgCount = 10,
                bgCount = 5,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 0.8f,
                alpha = 0.30f,
            )
            drawCatchWindLines(time, s, w, h, count = 3)
        }
        CatchScene.RAIN -> {
            drawRainLayered(
                fgCount = 24,
                bgCount = 12,
                time = time,
                width = w,
                height = h,
                scale = s,
                alpha = 0.40f,
            )
            drawCatchWindLines(time, s, w, h, count = 4)
        }
        CatchScene.RAIN_HEAVY -> {
            drawRainLayered(
                fgCount = 38,
                bgCount = 18,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.1f,
                alpha = 0.55f,
            )
            drawCatchWindLines(time, s, w, h, count = 6)
        }
        CatchScene.STORM -> {
            drawRainLayered(
                fgCount = 28,
                bgCount = 14,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.2f,
                alpha = 0.55f,
            )
            drawStormLightning(time, s, w, h)
            drawCatchWindLines(time, s, w, h, count = 6, speedMul = 1.4f)
        }
        CatchScene.FREEZING_RAIN -> drawFreezingRain(time, s, w, h)
        CatchScene.HAIL -> {
            drawRainLayered(
                fgCount = 12,
                bgCount = 6,
                time = time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.1f,
                alpha = 0.45f,
            )
            drawHailPellets(time, s, w, h, count = 28)
            drawStormLightning(time, s, w, h)
        }
        CatchScene.SNOW ->
            for (i in 0 until 18) {
                val x = ((i * 83 + time * 16f + sin(time * 1.2f + i) * 10f) % (w + 30f))
                val y = ((i * 61 + time * 34f) % (h + 30f)) - 15f
                drawCircle(Color.White.copy(alpha = 0.8f), (2f + i % 3) * s, Offset(x, y))
            }
        CatchScene.SUNNY, CatchScene.CLOUDY -> Unit
    }
}

/** 横风视觉线：几缕向右飘的细线，暗示掉落物会被风带着横移（预判落点）。 */
private fun DrawScope.drawCatchWindLines(
    time: Float,
    s: Float,
    w: Float,
    h: Float,
    count: Int,
    speedMul: Float = 1f,
) {
    val baseY = h * 0.18f
    for (i in 0 until count) {
        val drift = time * (90f * speedMul + (i % 3) * 26f * speedMul)
        val span = (w + 260f * s) % (w + 240f * s)
        val x = ((i * 173f * s + drift) % span) - 120f * s
        val y = baseY + ((i * 83f * s) % (h * 0.55f))
        val len = (90f + (i % 3) * 34f) * s
        val alpha = 0.10f + (i % 3) * 0.03f
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + len, y - len * 0.08f),
            strokeWidth = (1.4f + (i % 2)) * s,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCatchItem(
    it: BearCatchLogic.Item,
    time: Float,
) {
    val s = size.height / 820f
    val wobble = sin(time * 2f + it.phase) * 2f * s
    val x = it.x
    val y = it.y
    val r = it.r
    when (it.kind) {
        ItemKind.RAINDROP -> {
            val dropLen = r * 1.6f
            drawLine(
                Color(0xFF8FC3F0),
                Offset(x, y - dropLen / 2f),
                Offset(x, y + dropLen / 2f),
                strokeWidth = r * 0.7f,
                cap = StrokeCap.Round,
            )
            drawCircle(Color(0xFFADD8F7), r * 0.35f, Offset(x + wobble, y + dropLen * 0.3f))
        }
        ItemKind.SNOWFLAKE -> {
            for (i in 0 until 6) {
                val a = Math.PI.toFloat() * 2f * i / 6f + sin(time + it.phase) * 0.05f
                drawLine(
                    Color.White,
                    Offset(x + cos(a) * r * 0.2f, y + sin(a) * r * 0.2f),
                    Offset(x + cos(a) * r, y + sin(a) * r),
                    strokeWidth = (1.6f + (i % 2)) * s,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.35f, Offset(x, y))
        }
        ItemKind.COIN -> {
            val pulse = 1f + 0.1f * sin(time * 4f + it.phase)
            drawCircle(Color(0xFFFFCA28).copy(alpha = 0.35f), r * pulse * 1.4f, Offset(x, y))
            drawCircle(Color(0xFFFFB300), r * pulse * 0.9f, Offset(x, y))
            drawCircle(Color(0xFFFFE082), r * pulse * 0.6f, Offset(x, y))
            drawCircle(Color(0xFFFFD54F), r * pulse * 0.3f, Offset(x, y))
        }
        ItemKind.PETAL -> {
            drawCircle(Color(0xFFF8BBD0), r * 0.7f, Offset(x, y))
            drawCircle(Color(0xFFF06292), r * 0.35f, Offset(x + wobble * 0.3f, y - r * 0.2f))
        }
        ItemKind.GIFT -> {
            val bump = 1f + 0.12f * sin(time * 5f + it.phase)
            drawSoftGlow(x, y, r * 1.5f, Color(0xFFFFE082))
            drawRoundRect(
                Color(0xFFFF8F00),
                topLeft = Offset(x - r * bump, y - r * bump),
                size = Size(r * 2f * bump, r * 2f * bump),
                cornerRadius = CornerRadius(r * 0.22f),
            )
            drawLine(
                Color(0xFFFFF3E0),
                Offset(x - r * bump, y),
                Offset(x + r * bump, y),
                strokeWidth = r * 0.35f,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color(0xFFFFF3E0),
                Offset(x, y - r * bump),
                Offset(x, y + r * bump),
                strokeWidth = r * 0.35f,
                cap = StrokeCap.Round,
            )
        }
        ItemKind.BUBBLE -> {
            val pulse = 1f + 0.08f * sin(time * 3f + it.phase)
            drawCircle(
                Color(0xFF80DEEA).copy(alpha = 0.16f),
                r * pulse * 1.35f,
                Offset(x, y),
            )
            drawCircle(
                Color(0xFF80DEEA).copy(alpha = 0.45f),
                r * pulse,
                Offset(x, y),
            )
            drawCircle(
                Color.White.copy(alpha = 0.85f),
                r * 0.32f,
                Offset(x - r * 0.32f, y - r * 0.32f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.55f),
                r * 0.16f,
                Offset(x + r * 0.3f, y + r * 0.2f),
            )
        }
        ItemKind.CLOUD -> {
            drawCircle(Color(0xFF78828E), r, Offset(x, y))
            drawCircle(Color(0xFF8A94A0), r * 0.72f, Offset(x - r * 0.4f, y - r * 0.25f))
            drawCircle(Color(0xFF97A2AE), r * 0.6f, Offset(x + r * 0.45f, y - r * 0.2f))
        }
        ItemKind.HAIL -> {
            drawCircle(Color(0xFFE9F0F6), r, Offset(x, y))
            drawCircle(Color(0xFFC4CFDA), r * 0.6f, Offset(x - r * 0.3f, y - r * 0.3f))
        }
        ItemKind.LIGHTNING -> {
            drawSoftGlow(x, y, r * 1.6f, Color(0xFFFFEB3B))
            withTransform({
                translate(x, y)
                scale(r, r, pivot = Offset.Zero)
            }) {
                drawPath(unitLightningPath, Color(0xFFFFF176))
                drawPath(
                    unitLightningPath,
                    Color(0xFFFFFDE7),
                    style = Stroke(width = (1.2f * s) / r.coerceAtLeast(0.1f)),
                )
            }
        }
    }
}

private fun DrawScope.drawCatchBear(
    game: BearCatchGame,
    sprites: BearSprites,
) {
    val bs = game.catchR * 1.55f
    val cy = game.catchY
    val dead = game.phase == GamePhase.OVER
    // 无敌闪烁
    val invBlink = game.invincibleT > 0f && ((game.invincibleT * 12f) % 1f < 0.5f)
    // 死亡淡出用 deadT（死亡后从 0 开始累计）：整局 time 很大时也能正常淡出
    val alphaBase = if (dead) (1f - (game.deadT * 2f).coerceIn(0f, 1f)) else 1f
    val alpha = (if (invBlink) alphaBase * 0.45f else alphaBase).coerceIn(0f, 1f)

    // 移动倾斜：跟随手指方向轻微侧倾
    val tiltMove = ((game.touchX ?: game.catchX) - game.catchX) * 0.06f

    // 接住挤压：x 展宽、y 压扁，0.16s 内快速回弹
    val squash =
        if (game.squashT in 0f..0.16f) {
            val k = game.squashT / 0.16f
            1f - 0.22f * (1f - k) * (1f - k)
        } else {
            1f
        }
    val scaleX = 2f - squash
    val scaleY = squash

    // 地面柔影，随无敌闪烁
    if (!dead) {
        val groundY = game.catchY + game.catchR * 0.6f
        drawOval(
            color = Color.Black.copy(alpha = if (invBlink) 0.08f else 0.18f),
            topLeft = Offset(game.catchX - bs * 0.55f, groundY - 4f),
            size = Size(bs * 1.1f, bs * 0.22f),
        )
    }

    // 一次性护盾泡泡：持有时显示淡蓝光圈；冷却中完全隐藏（避免误导）
    if (game.hasShield && !dead) {
        drawCircle(
            color = Color(0xFF80DEEA).copy(alpha = 0.28f),
            radius = bs * (0.82f + 0.06f * sin(game.time * 3f)),
            center = Offset(game.catchX, cy),
            style = Stroke(width = 2.5f * game.s),
        )
        drawCircle(
            color = Color(0xFF80DEEA).copy(alpha = 0.14f),
            radius = bs * (0.86f + 0.05f * sin(game.time * 3f + 1.2f)),
            center = Offset(game.catchX, cy),
        )
        for (i in 0 until BearCatchLogic.MAX_SHIELD) {
            val a = game.time * 1.4f + i * (Math.PI.toFloat() * 2f / BearCatchLogic.MAX_SHIELD)
            drawCircle(
                color = Color(0xFFFFE082),
                radius = 3.2f * game.s,
                center =
                    Offset(
                        game.catchX + cos(a) * bs * 0.72f,
                        cy + sin(a) * bs * 0.72f,
                    ),
            )
        }
    }

    drawGameBear(
        sprites = sprites,
        centerX = game.catchX,
        centerY = cy,
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                dead = dead,
                frame = runCycleFrame(game.time * 3.2f),
                bodyTilt = tiltMove,
                scaleX = scaleX,
                scaleY = scaleY,
                alpha = alpha,
            ),
    )
}

private fun DrawScope.drawCatchTexts(
    game: BearCatchGame,
    texts: List<CatchFloatText>,
) {
    for (t in texts) {
        val k = (t.life / 0.8f).coerceIn(0f, 1f)
        catchTextPaint.color = t.color.copy(alpha = k).toArgb()
        catchTextPaint.textSize = 16f * game.s
        drawContext.canvas.nativeCanvas.drawText(t.text, t.x, t.y, catchTextPaint)
    }
}
