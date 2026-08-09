package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.graphics.Paint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 彩蛋口令：搜索框输入「#小熊摘星」进入跳台小游戏。 */
const val EASTER_EGG_BEAR_STAR = "#小熊摘星"

private enum class PlatType { NORMAL, MOVING, BREAK, SPRING }

private class Platform(
    var x: Float,
    val y: Float,
    val w: Float,
    val type: PlatType,
    var vx: Float = 0f,
    var broken: Boolean = false,
    var breakT: Float = 0f,
)

private class StarItem(
    val x: Float,
    val y: Float,
    var taken: Boolean = false,
    val seed: Float,
)

private class JumpFloatText(
    val text: String,
    val x: Float,
    val y: Float,
    var life: Float,
)

private val jumpTextPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

/** 摘星场景：纯晴天（非雨天）时为夜晚（star 字面义，星最多）；阴天灰云；雨/雪/雷暴渲染雨雪。 */
enum class JumpScene { SUNNY, CLOUDY, RAIN_LIGHT, RAIN, RAIN_HEAVY, SNOW, STORM, FREEZING_RAIN, HAIL }

private fun jumpSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): JumpScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> JumpScene.FREEZING_RAIN
        isHailCode(sceneCode) -> JumpScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> JumpScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> JumpScene.RAIN_LIGHT
                        RainStrength.HEAVY -> JumpScene.RAIN_HEAVY
                        else -> JumpScene.RAIN
                    }
                FarmWeather.STORMY -> JumpScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> JumpScene.CLOUDY
                FarmWeather.SUNNY -> JumpScene.SUNNY
            }
    }

/**
 * 小熊摘星：竖版跳台。拖动控制方向，踩云向上；
 * 天空随高度从白天渐变到黄昏、夜空再到太空。
 * 背景天气跟随当前天气：阴/雨/雪时天空变灰、星星减少、并落下雨雪。
 */
private class StarJumpGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)

    /** 当前天气场景；由 [BearStarJumpScreen] 在天气变化时刷新。 */
    var scene by mutableStateOf(JumpScene.SUNNY)

    var w = 0f
    var h = 0f
    var s = 1f
    var time = 0f

    val bearSize get() = 58f * s
    var bearX = 0f // 中心 x（屏幕坐标，环绕）
    var bearY = 0f // 中心 y（世界坐标，向上为负）
    var bearVX = 0f
    var bearVY = 0f
    var cameraY = 0f // 屏幕顶对应的世界 y
    var maxHeight = 0f // 到达过的最小 bearY
    var starsCollected = 0

    var touchX: Float? = null
    var squashT = -1f
    var shakeT = 0f
    var deadT = 0f

    val platforms = mutableListOf<Platform>()
    val stars = mutableListOf<StarItem>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<JumpFloatText>()
    var nextPlatY = 0f

    val altitudeKm get() = (-cameraY / (2400f * s)).coerceAtLeast(0f)

    fun setDims(
        width: Float,
        height: Float,
        scale: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        val first = w <= 0f || h <= 0f
        if (!first && w == width && h == height && abs(s - scale) < 0.001f) return
        w = width
        h = height
        s = scale
        jumpTextPaint.textSize = 15f * s
        // 对局中改尺寸会让平台像素与碰撞错位，整局回准备态（与跑酷一致）
        phase = GamePhase.READY
        reset()
    }

    fun reset() {
        val bs = bearSize
        platforms.clear()
        stars.clear()
        particles.clear()
        texts.clear()
        bearX = w / 2f
        bearY = -bs / 2f
        bearVX = 0f
        bearVY = 0f
        cameraY = -h * 0.55f
        maxHeight = bearY
        starsCollected = 0
        score = 0
        squashT = -1f
        shakeT = 0f
        deadT = 0f
        touchX = null
        // 起跳平台（贴地面）+ 预生成上方云朵
        platforms.add(Platform(w / 2f - 110f * s, 0f, 220f * s, PlatType.NORMAL))
        nextPlatY = -150f * s
        genPlatforms(cameraY - h)
    }

    fun start() {
        reset()
        phase = GamePhase.RUNNING
        bearVY = -1400f * s
    }

    private fun genPlatforms(limitY: Float) {
        val margin = 8f * s
        val maxTravel = (w - margin * 2f).coerceAtLeast(1f)
        while (nextPlatY > limitY) {
            val progress = (-nextPlatY / (3400f * s)).coerceIn(0f, 1f)
            // 移动台宽度必须小于可移动区间，否则会贴边来回抖
            var pw = (150f - 26f * progress) * s
            val roll = Random.nextFloat()
            val early = nextPlatY > -700f * s
            val type =
                when {
                    early -> PlatType.NORMAL
                    roll < 0.07f -> PlatType.SPRING
                    roll < 0.07f + 0.10f + 0.08f * progress -> PlatType.BREAK
                    roll < 0.07f + 0.10f + 0.08f * progress + 0.15f + 0.10f * progress ->
                        PlatType.MOVING
                    else -> PlatType.NORMAL
                }
            if (type == PlatType.MOVING) {
                pw = min(pw, maxTravel * 0.55f).coerceAtLeast(48f * s)
            } else {
                pw = min(pw, maxTravel)
            }
            val xSpan = (maxTravel - pw).coerceAtLeast(1f)
            val x = margin + Random.nextFloat() * xSpan
            val vx =
                if (type == PlatType.MOVING) {
                    (50f + 85f * progress) * s * if (Random.nextBoolean()) 1f else -1f
                } else {
                    0f
                }
            platforms.add(Platform(x, nextPlatY, pw, type, vx))
            val gap = (95f + 78f * progress + Random.nextFloat() * 30f) * s
            if (!early && Random.nextFloat() < 0.26f) {
                stars.add(
                    StarItem(
                        x = 30f * s + Random.nextFloat() * (w - 60f * s).coerceAtLeast(1f),
                        y = nextPlatY - gap * 0.45f,
                        seed = Random.nextFloat() * 6.28f,
                    ),
                )
            }
            nextPlatY -= gap
        }
    }

    fun tick(
        dt: Float,
        onBounce: () -> Unit,
        onStar: () -> Unit,
        onSpring: () -> Unit,
        onDeath: (Int) -> Unit,
    ) {
        time += dt
        if (phase == GamePhase.READY) return
        val bs = bearSize
        val g = 2600f * s

        if (phase == GamePhase.RUNNING) {
            // 水平：跟随手指目标位置，无触摸时惯性衰减
            val tx = touchX
            if (tx != null) {
                val desired = ((tx - bearX) * 8f).coerceIn(-760f * s, 760f * s)
                bearVX += (desired - bearVX) * min(1f, dt * 11f)
            } else {
                bearVX *= 1f - min(1f, dt * 2.2f)
            }
            bearX += bearVX * dt
            // 左右环绕
            if (bearX < -bs * 0.5f) bearX += w + bs
            if (bearX > w + bs * 0.5f) bearX -= w + bs
        }

        val prevY = bearY
        bearVY += g * dt
        bearY += bearVY * dt

        // 移动平台
        val margin = 8f * s
        for (p in platforms) {
            if (p.type == PlatType.MOVING && !p.broken) {
                val maxX = (w - margin - p.w).coerceAtLeast(margin)
                if (maxX <= margin + 1f) {
                    // 几乎无行程：钉在中间，避免贴边抖动
                    p.x = (w - p.w) * 0.5f
                    p.vx = 0f
                } else {
                    p.x += p.vx * dt
                    if (p.x < margin) {
                        p.x = margin
                        p.vx = abs(p.vx)
                    } else if (p.x > maxX) {
                        p.x = maxX
                        p.vx = -abs(p.vx)
                    }
                }
            }
            if (p.broken) p.breakT += dt
        }

        if (phase == GamePhase.RUNNING) {
            // 踩平台：仅下落时、脚线穿越台面
            if (bearVY > 0f) {
                val feetPrev = prevY + bs / 2f
                val feetNow = bearY + bs / 2f
                for (p in platforms) {
                    if (p.broken) continue
                    // 死亡线（屏幕底缘再下半个身位）以下的平台不可踩：
                    // 它们已不可见，踩上会像"踩空气弹回"，或弹起后同帧被判死
                    if (p.y - cameraY > h + bs) continue
                    if (feetPrev <= p.y && feetNow >= p.y) {
                        var dx = bearX - (p.x + p.w / 2f)
                        if (dx > w / 2f) dx -= w + bs
                        if (dx < -w / 2f) dx += w + bs
                        if (abs(dx) < p.w / 2f + bs * 0.24f) {
                            bearY = p.y - bs / 2f
                            if (p.type == PlatType.BREAK) {
                                p.broken = true
                                p.breakT = 0f
                                bearVY = -1350f * s
                                onBounce()
                            } else if (p.type == PlatType.SPRING) {
                                bearVY = -2350f * s
                                shakeT = 0.10f
                                onSpring()
                            } else {
                                bearVY = -1350f * s
                                onBounce()
                            }
                            squashT = 0f
                            repeat(8) {
                                particles.add(
                                    Particle(
                                        bearX + (Random.nextFloat() - 0.5f) * bs,
                                        p.y,
                                        (Random.nextFloat() - 0.5f) * 160f * s,
                                        -Random.nextFloat() * 120f * s,
                                        0.4f + Random.nextFloat() * 0.3f,
                                        color = Color.White.copy(alpha = 0.85f),
                                    ),
                                )
                            }
                            break
                        }
                    }
                }
            }

            // 吃星星
            for (st in stars) {
                if (st.taken) continue
                var dx = bearX - st.x
                if (dx > w / 2f) dx -= w + bs
                if (dx < -w / 2f) dx += w + bs
                val dy = bearY - st.y
                if (dx * dx + dy * dy < (bs * 0.62f) * (bs * 0.62f)) {
                    st.taken = true
                    starsCollected++
                    texts.add(JumpFloatText("+25", st.x, st.y, 0.9f))
                    repeat(10) {
                        val a = Random.nextFloat() * 6.28f
                        particles.add(
                            Particle(
                                st.x,
                                st.y,
                                kotlin.math.cos(a) * (60f + Random.nextFloat() * 140f) * s,
                                sin(a) * (60f + Random.nextFloat() * 140f) * s,
                                0.5f + Random.nextFloat() * 0.3f,
                                color = Color(0xFFFFD54F),
                            ),
                        )
                    }
                    onStar()
                }
            }

            // 相机只上不下
            val target = bearY - h * 0.42f
            if (target < cameraY) cameraY = target
            maxHeight = min(maxHeight, bearY)
            score = ((-maxHeight) / (60f * s)).toInt().coerceAtLeast(0) + starsCollected * 25

            genPlatforms(cameraY - h * 0.5f)
            platforms.removeAll { it.y > cameraY + h + 120f * s }
            stars.removeAll { it.y > cameraY + h + 120f * s }

            // 掉出屏幕底部 → 结束
            if (bearY - cameraY > h + bs) {
                phase = GamePhase.OVER
                touchX = null
                deadT = 0f
                shakeT = 0.3f
                onDeath(score)
            }
        } else {
            deadT += dt
        }

        if (squashT >= 0f) {
            squashT += dt
            if (squashT > 0.16f) squashT = -1f
        }
        if (shakeT > 0f) shakeT = max(0f, shakeT - dt)
        for (pt in particles) {
            pt.life -= dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.vy += 900f * s * dt
        }
        particles.removeAll { it.life <= 0f }
        texts.forEach { it.life -= dt }
        texts.removeAll { it.life <= 0f }
    }
}

@Composable
fun BearStarJumpScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val prefs = remember { context.getSharedPreferences("bear_game", Context.MODE_PRIVATE) }
    val leaderboard = remember { GameLeaderboardStore(prefs, "leaderboard_jump") }
    var showLeaderboard by remember { mutableStateOf(false) }
    var lastRank by remember { mutableIntStateOf(0) }
    var leaderboardEntries by remember { mutableStateOf(leaderboard.load()) }
    val game =
        remember {
            StarJumpGame().also { it.best = prefs.getInt("best_jump", 0) }
        }
    val jumpWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    // 天气变化时更新游戏场景（雨量强度决定雨点多少）
    LaunchedEffect(jumpWeather, sceneCode) {
        game.scene = jumpSceneFromWeather(jumpWeather, sceneCode)
    }
    val bearSprites =
        BearSprites(
            runA = ImageBitmap.imageResource(R.drawable.bear_run_a),
            runPushA = ImageBitmap.imageResource(R.drawable.bear_run_push_a),
            runPass = ImageBitmap.imageResource(R.drawable.bear_run_pass),
            runB = ImageBitmap.imageResource(R.drawable.bear_run_b),
            runPushB = ImageBitmap.imageResource(R.drawable.bear_run_push_b),
            jump = ImageBitmap.imageResource(R.drawable.bear_jump),
            fall = ImageBitmap.imageResource(R.drawable.bear_fall),
            land = ImageBitmap.imageResource(R.drawable.bear_land),
        )
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = Long.MIN_VALUE
        while (isActive) {
            withFrameNanos { now ->
                val dt =
                    if (last == Long.MIN_VALUE) {
                        1f / 60f
                    } else {
                        min((now - last) / 1e9f, 0.033f)
                    }
                last = now
                // 查看排行榜时暂停：跳过 tick，画面定格
                if (showLeaderboard) return@withFrameNanos
                game.tick(
                    dt,
                    onBounce = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onStar = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onSpring = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onDeath = { score ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (score > game.best) {
                            game.best = score
                            prefs.edit().putInt("best_jump", score).apply()
                        }
                        if (score > 0) {
                            lastRank =
                                leaderboard.submit(
                                    LeaderboardEntry(
                                        score = score,
                                        timestamp = System.currentTimeMillis(),
                                        city = cityName,
                                        weather = leaderboardWeatherOf(context, sceneCode),
                                    ),
                                ) ?: 0
                        }
                    },
                )
                frame = now
            }
        }
    }

    BackHandler { onExit() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    game.setDims(sz.width.toFloat(), sz.height.toFloat(), density)
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    // 仅对局中启用转向，避免与「开始」按钮/点按手势抢事件
                    .pointerInput(game.phase) {
                        if (game.phase != GamePhase.RUNNING) return@pointerInput
                        // 按住即转向：不走 drag 的 touchSlop，静止按住也能控左右
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.touchX = down.position.x
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    game.touchX = change.position.x
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                            game.touchX = null
                        }
                    },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            frame
            if (game.w <= 0f || game.h <= 0f) return@Canvas
            val shake = if (game.shakeT > 0f) game.shakeT * 14f * game.s else 0f
            withTransform({
                translate(
                    (Random.nextFloat() - 0.5f) * shake * 2f,
                    (Random.nextFloat() - 0.5f) * shake * 2f,
                )
            }) {
                drawJumpWorld(game, bearSprites)
            }
        }

        JumpHud(
            game = game,
            frame = frame,
            onExit = onExit,
            onLeaderboard = {
                leaderboardEntries = leaderboard.load()
                showLeaderboard = true
            },
        )

        if (showLeaderboard) {
            GameLeaderboardDialog(
                title = stringResource(R.string.bear_leaderboard_title),
                entries = leaderboardEntries,
                onClear = {
                    leaderboard.clear()
                    leaderboardEntries = emptyList()
                },
                onDismiss = { showLeaderboard = false },
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
                            text = stringResource(R.string.bear_jump_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bear_jump_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = { game.start() }) {
                            Text(stringResource(R.string.bear_jump_start))
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
                                // 吞掉底层 Canvas 的按下转向 / 点击
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
                            text = stringResource(R.string.bear_jump_over),
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
                        if (lastRank > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text =
                                    stringResource(R.string.bear_leaderboard_rank_label, lastRank),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onExit) {
                                Text(stringResource(R.string.bear_game_exit))
                            }
                            Button(onClick = { game.start() }) {
                                Text(stringResource(R.string.bear_game_restart))
                            }
                        }
                    }
                }
            }
            GamePhase.RUNNING -> Unit
        }
    }
}

@Composable
private fun JumpHud(
    game: StarJumpGame,
    frame: Long,
    onExit: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    frame
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
            Text(
                text = "${game.score}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text =
                    "★ ${game.starsCollected} · " +
                        stringResource(R.string.bear_game_best_label) + " ${game.best}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        LeaderboardButton(onClick = onLeaderboard)
    }
}

// ---------------- 世界绘制 ----------------

private class SkyStop(
    val alt: Float,
    val top: Color,
    val bottom: Color,
)

private val skyStops =
    listOf(
        SkyStop(0f, Color(0xFF2E6FB8), Color(0xFF9BDBF5)), // 白天
        SkyStop(0.9f, Color(0xFF474A8C), Color(0xFFF79E5F)), // 黄昏
        SkyStop(1.8f, Color(0xFF121B3E), Color(0xFF3D4E85)), // 夜空
        SkyStop(2.8f, Color(0xFF04041A), Color(0xFF20123E)), // 太空
    )

private fun skyColors(alt: Float): Pair<Color, Color> {
    if (alt <= skyStops.first().alt) {
        return skyStops.first().top to skyStops.first().bottom
    }
    for (i in 0 until skyStops.size - 1) {
        val a = skyStops[i]
        val b = skyStops[i + 1]
        if (alt <= b.alt) {
            val t = (alt - a.alt) / (b.alt - a.alt)
            return lerp(a.top, b.top, t) to lerp(a.bottom, b.bottom, t)
        }
    }
    return skyStops.last().top to skyStops.last().bottom
}

/** 五角星路径。 */
private fun starPath(r: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = -Math.PI.toFloat() / 2f + i * Math.PI.toFloat() / 5f
        val x = kotlin.math.cos(a) * rr
        val y = sin(a) * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawJumpWorld(
    game: StarJumpGame,
    sprites: BearSprites,
) {
    val w = game.w
    val h = game.h
    val s = game.s
    val cam = game.cameraY
    val alt = game.altitudeKm
    val scene = game.scene
    val (skyTop, skyBottom) = skyColors(alt)

    // 天气适配：雨/雪/雷暴压低天空亮度与饱和度，仍随高度渐变
    val weatherDim =
        when (scene) {
            JumpScene.RAIN_LIGHT -> 0.72f
            JumpScene.RAIN -> 0.55f
            JumpScene.RAIN_HEAVY -> 0.45f
            JumpScene.SNOW -> 0.70f
            JumpScene.STORM -> 0.40f
            JumpScene.FREEZING_RAIN -> 0.52f
            JumpScene.HAIL -> 0.40f
            JumpScene.CLOUDY -> 0.62f
            JumpScene.SUNNY -> 1f
        }
    drawRect(
        Brush.verticalGradient(
            listOf(
                skyTop.copy(red = skyTop.red * weatherDim, green = skyTop.green * weatherDim, blue = skyTop.blue * weatherDim),
                skyBottom.copy(red = skyBottom.red * weatherDim, green = skyBottom.green * weatherDim, blue = skyBottom.blue * weatherDim),
            ),
        ),
    )
    // 底部柔光：模拟云海/地平线反射，任何高度都增强层次
    drawHorizonGlow(h * 0.88f, midColor(skyBottom, Color.White), h * 0.18f)

    // 星星（哈希星空：视差 0.5，无需存状态）；雨天很少、晴天最多
    val starAlpha = ((alt - 0.9f) / 0.7f).coerceIn(0f, 1f)
    if (starAlpha > 0.01f) {
        val cell = 110f * s
        val yOff = cam * 0.5f
        val row0 = floor(yOff / cell).toInt() - 1
        val rows = (h / cell).toInt() + 3
        val cols = (w / cell).toInt() + 2
        // 密度：晴天全画，阴天略稀，雨/雪/雷暴按天气稀疏
        val starSkip =
            when (scene) {
                JumpScene.SUNNY -> 1
                JumpScene.CLOUDY -> 2
                JumpScene.RAIN_LIGHT -> 4
                JumpScene.RAIN -> 5
                JumpScene.RAIN_HEAVY -> 7
                JumpScene.SNOW -> 4
                JumpScene.STORM -> 8
                JumpScene.FREEZING_RAIN -> 6
                JumpScene.HAIL -> 8
            }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // 按密度跳过格子
                if ((r * (cols + 1) + c) % starSkip != 0) continue
                val ry = row0 + r
                val h1 = abs((c * 73856093) xor (ry * 19349663))
                val h2 = abs((c * 83492791) xor (ry * 29712151))
                val px = c * cell + (h1 % 1000) / 1000f * cell
                val py = ry * cell + (h2 % 1000) / 1000f * cell - yOff
                if (py < -20f || py > h + 20f) continue
                val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(game.time * 2f + (h1 % 628) / 100f))
                val a = starAlpha * twinkle
                if (h1 % 7 == 0) {
                    // 十字星芒
                    val cr = (2.5f + (h2 % 30) / 10f) * s
                    drawLine(
                        Color.White,
                        Offset(px - cr, py),
                        Offset(px + cr, py),
                        strokeWidth = 1.2f * s,
                        alpha = a,
                    )
                    drawLine(
                        Color.White,
                        Offset(px, py - cr),
                        Offset(px, py + cr),
                        strokeWidth = 1.2f * s,
                        alpha = a,
                    )
                } else {
                    drawCircle(
                        Color.White,
                        (1f + (h2 % 3) * 0.5f) * s,
                        Offset(px, py),
                        alpha = a,
                    )
                }
            }
        }
    }

    // 太阳（低空）→ 月亮（高空）交接；阴/雨/雪/雷暴时太阳几乎隐去
    val sunDim =
        when (scene) {
            JumpScene.SUNNY -> 1f
            JumpScene.CLOUDY -> 0.12f
            JumpScene.RAIN_LIGHT -> 0.35f
            JumpScene.RAIN -> 0.22f
            JumpScene.RAIN_HEAVY -> 0.10f
            JumpScene.SNOW -> 0.3f
            JumpScene.STORM -> 0.08f
            JumpScene.FREEZING_RAIN -> 0.12f
            JumpScene.HAIL -> 0.06f
        }
    val sunA = (1f - alt / 1.5f).coerceIn(0f, 1f) * sunDim
    if (sunA > 0.01f) {
        val sunC = Offset(w * 0.80f, h * 0.20f)
        rotate(game.time * 3f, pivot = sunC) {
            for (i in 0 until 12) {
                val a = Math.PI.toFloat() * 2f * i / 12f
                drawLine(
                    Color(0xFFFFD54F),
                    sunC + Offset(kotlin.math.cos(a) * 34f * s, sin(a) * 34f * s),
                    sunC + Offset(kotlin.math.cos(a) * 46f * s, sin(a) * 46f * s),
                    strokeWidth = 3f * s,
                    cap = StrokeCap.Round,
                    alpha = 0.7f * sunA,
                )
            }
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE082), Color(0xFFFFB300)),
                center = sunC,
                radius = 30f * s,
            ),
            radius = 30f * s,
            center = sunC,
            alpha = sunA,
        )
    }
    val moonA = ((alt - 1.1f) / 0.6f).coerceIn(0f, 1f)
    if (moonA > 0.01f) {
        val moonC = Offset(w * 0.78f, h * 0.18f)
        drawCircle(Color(0xFFFFF8E1), 26f * s, moonC, alpha = 0.16f * moonA)
        drawCircle(Color(0xFFFFF8E1), 20f * s, moonC, alpha = moonA)
        drawCircle(Color(0xFFE8E0C8), 4.5f * s, moonC + Offset(-6f * s, -4f * s), alpha = moonA)
        drawCircle(Color(0xFFE8E0C8), 3f * s, moonC + Offset(5f * s, 6f * s), alpha = moonA)
        drawCircle(Color(0xFFE8E0C8), 2.2f * s, moonC + Offset(8f * s, -7f * s), alpha = moonA)
    }

    // 背景云（低空有、高空稀）；雨/雪时云更低更灰
    val cloudA = (1.15f - alt * 0.5f).coerceIn(0f, 1f)
    if (cloudA > 0.01f) {
        val cloudGrey =
            when (scene) {
                JumpScene.SUNNY -> Color.White
                JumpScene.CLOUDY -> Color(0xFFC2CBD3)
                JumpScene.RAIN_LIGHT -> Color(0xFFCBD3DA)
                JumpScene.RAIN -> Color(0xFFB8C4CE)
                JumpScene.RAIN_HEAVY -> Color(0xFF9AA7B5)
                JumpScene.SNOW -> Color(0xFFD8DEE4)
                JumpScene.STORM -> Color(0xFF8A94A0)
                JumpScene.FREEZING_RAIN -> Color(0xFF9FAFBF)
                JumpScene.HAIL -> Color(0xFF7E8A99)
            }
        for (i in 0 until 4) {
            val speed = 10f * s * (1 + (i % 3))
            val cx = ((i * 283f * s + game.time * speed) % (w + 260f * s)) - 130f * s
            val cy =
                ((i * 251f * s - cam * 0.3f) % (h + 200f * s) + (h + 200f * s)) %
                    (h + 200f * s) - 100f * s
            val cw = (54f + i * 11f) * s
            val col = cloudGrey
            drawCircle(col, cw * 0.55f, Offset(cx - cw * 0.4f, cy + cw * 0.1f), alpha = 0.30f * cloudA)
            drawCircle(col, cw * 0.75f, Offset(cx, cy), alpha = 0.30f * cloudA)
            drawCircle(col, cw * 0.5f, Offset(cx + cw * 0.5f, cy + cw * 0.12f), alpha = 0.30f * cloudA)
            drawRect(
                col,
                Offset(cx - cw * 0.85f, cy),
                Size(cw * 1.7f, cw * 0.45f),
                alpha = 0.30f * cloudA,
            )
        }
    }

    // 雨/雪粒子（视差 + 随风漂）
    drawJumpWeatherParticles(game, scene)

    // 地面（起点附近才可见）
    val groundTop = -cam
    if (groundTop < h + 40f * s) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8BC34A), Color(0xFF558B2F))),
            Offset(0f, groundTop),
            Size(w, h - groundTop + 40f * s),
        )
        // 草丛与小花
        var gx = 12f * s
        var i = 0
        while (gx < w) {
            drawLine(
                Color(0xFF6FA03A),
                Offset(gx, groundTop + 4f * s),
                Offset(gx + sin(i * 1.7f) * 3f * s, groundTop - 7f * s),
                strokeWidth = 2f * s,
                cap = StrokeCap.Round,
            )
            gx += 15f * s
            i++
        }
        for (f in 0 until 5) {
            val fx = (f * 197f * s + 60f * s) % w
            val fy = groundTop + (14f + (f % 3) * 10f) * s
            drawCircle(Color(0xFFFFF176), 3f * s, Offset(fx, fy))
            for (p in 0 until 5) {
                val a = Math.PI.toFloat() * 2f * p / 5f
                drawCircle(
                    Color.White,
                    2.2f * s,
                    Offset(fx + kotlin.math.cos(a) * 4.5f * s, fy + sin(a) * 4.5f * s),
                )
            }
        }
    }

    // 平台（云朵）
    for (p in game.platforms) {
        val py = p.y - cam
        if (py < -60f * s || py > h + 60f * s) continue
        drawPlatform(game, p, py)
    }

    // 星星道具
    for (st in game.stars) {
        if (st.taken) continue
        val sy = st.y - cam
        if (sy < -40f * s || sy > h + 40f * s) continue
        val pulse = 1f + 0.12f * sin(game.time * 3f + st.seed)
        val r = 13f * s * pulse
        drawCircle(Color(0xFFFFD54F), r * 1.7f, Offset(st.x, sy), alpha = 0.20f)
        withTransform({
            translate(st.x, sy)
            rotate(sin(game.time + st.seed) * 10f)
        }) {
            drawPath(starPath(r), Color(0xFFFFD54F))
            drawPath(
                starPath(r),
                Color(0xFFFFF8E1),
                style = Stroke(width = 1.6f * s),
            )
        }
    }

    drawJumpParticles(game)

    // 小熊（含环绕幻影）
    drawJumpBear(game, sprites, game.bearX)
    if (game.bearX < game.bearSize) drawJumpBear(game, sprites, game.bearX + w + game.bearSize)
    if (game.bearX > w) drawJumpBear(game, sprites, game.bearX - w - game.bearSize)
}

private fun DrawScope.drawJumpWeatherParticles(
    game: StarJumpGame,
    scene: JumpScene,
) {
    if (scene == JumpScene.SUNNY || scene == JumpScene.CLOUDY) return
    val w = game.w
    val h = game.h
    val s = game.s
    val cam = game.cameraY
    when (scene) {
        JumpScene.RAIN_LIGHT -> {
            drawRainLayered(
                fgCount = 12,
                bgCount = 6,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 0.8f,
                alpha = 0.35f,
                yOff = -cam * 0.15f,
            )
        }
        JumpScene.RAIN -> {
            drawRainLayered(
                fgCount = 30,
                bgCount = 14,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                alpha = 0.45f,
                yOff = -cam * 0.15f,
            )
        }
        JumpScene.RAIN_HEAVY -> {
            drawRainLayered(
                fgCount = 48,
                bgCount = 24,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.15f,
                alpha = 0.6f,
                yOff = -cam * 0.15f,
            )
        }
        JumpScene.STORM -> {
            // 雷暴：以闪电为主，雨量收敛到中雨量级
            drawRainLayered(
                fgCount = 34,
                bgCount = 16,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.25f,
                alpha = 0.6f,
                yOff = -cam * 0.15f,
            )
        }
        JumpScene.FREEZING_RAIN -> {
            drawFreezingRain(game.time, s, w, h, yOff = -cam * 0.15f)
        }
        JumpScene.HAIL -> {
            // 冰雹：雨更少，突出冰雹粒子
            drawRainLayered(
                fgCount = 16,
                bgCount = 8,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.1f,
                alpha = 0.5f,
                yOff = -cam * 0.15f,
                fgColor = Color(0xFFD8E6F8),
            )
            drawHailPellets(game.time, s, w, h, yOff = -cam * 0.15f, count = 40)
            drawStormLightning(game.time, s, w, h)
        }
        JumpScene.SNOW -> {
            for (i in 0 until 34) {
                val x = ((i * 83 + game.time * 22f + sin(game.time * 1.4f + i) * 12f) % (w + 30))
                val y = ((i * 61 + game.time * 46f) % (h + 30)) - cam * 0.12f
                drawCircle(Color.White.copy(alpha = 0.85f), (2f + i % 3) * s, Offset(x, y))
            }
        }
        JumpScene.SUNNY, JumpScene.CLOUDY -> Unit
    }
}

private fun DrawScope.drawPlatform(
    game: StarJumpGame,
    p: Platform,
    py: Float,
) {
    val s = game.s
    val ph = 20f * s
    if (p.broken) {
        // 碎裂：两瓣坠落 + 外旋 + 淡出
        val t = p.breakT
        val alpha = (1f - t / 0.6f).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        val fall = t * t * 900f * s
        for (side in listOf(-1f, 1f)) {
            val halfW = p.w / 2f
            val cx = p.x + halfW / 2f + (if (side > 0f) halfW else 0f) + side * t * 90f * s
            withTransform({
                translate(cx, py + ph / 2f + fall)
                rotate(side * t * 120f)
            }) {
                cloudPuffs(
                    Offset(-halfW / 2f, -ph / 2f),
                    halfW,
                    ph,
                    Color(0xFF8B93A6),
                    Color(0xFF6E7689),
                    s,
                    alpha,
                )
            }
        }
        return
    }
    // 云底阴影
    drawOval(
        Color.Black.copy(alpha = 0.10f),
        Offset(p.x + 4f * s, py + ph * 0.9f),
        Size(p.w * 0.9f, ph * 0.5f),
    )
    when (p.type) {
        PlatType.NORMAL ->
            cloudPuffs(
                Offset(p.x, py),
                p.w,
                ph,
                Color.White,
                Color(0xFFE3ECF5),
                s,
                1f,
            )
        PlatType.MOVING -> {
            cloudPuffs(Offset(p.x, py), p.w, ph, Color(0xFFEAF4FF), Color(0xFFBCD6F0), s, 1f)
            // 移动风向线
            val dir = if (p.vx > 0f) -1f else 1f
            for (i in 0 until 2) {
                val ly = py + ph * (0.25f + i * 0.35f)
                val lx = p.x + (if (dir < 0f) p.w + 6f * s else -6f * s - 18f * s)
                drawLine(
                    Color(0xFF9CC3E8),
                    Offset(lx, ly),
                    Offset(lx + 18f * s, ly),
                    strokeWidth = 2f * s,
                    cap = StrokeCap.Round,
                    alpha = 0.8f,
                )
            }
        }
        PlatType.BREAK -> {
            cloudPuffs(Offset(p.x, py), p.w, ph, Color(0xFF9AA1B1), Color(0xFF767E8E), s, 1f)
            // 裂纹
            drawLine(
                Color(0xFF5D6472),
                Offset(p.x + p.w * 0.35f, py + 2f * s),
                Offset(p.x + p.w * 0.5f, py + ph - 3f * s),
                strokeWidth = 1.8f * s,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color(0xFF5D6472),
                Offset(p.x + p.w * 0.5f, py + ph - 3f * s),
                Offset(p.x + p.w * 0.68f, py + 3f * s),
                strokeWidth = 1.8f * s,
                cap = StrokeCap.Round,
            )
        }
        PlatType.SPRING -> {
            cloudPuffs(Offset(p.x, py), p.w, ph, Color.White, Color(0xFFE3ECF5), s, 1f)
            // 彩虹拱
            val bands = listOf(0xFFFF5252, 0xFFFFB300, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3)
            val cx = p.x + p.w / 2f
            bands.forEachIndexed { i, c ->
                drawArc(
                    color = Color(c),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft =
                        Offset(
                            cx - (p.w * 0.42f - i * 4f * s),
                            py - (p.w * 0.42f - i * 4f * s) * 0.55f,
                        ),
                    size =
                        Size(
                            (p.w * 0.42f - i * 4f * s) * 2f,
                            (p.w * 0.42f - i * 4f * s) * 1.1f,
                        ),
                    style = Stroke(width = 3.6f * s, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** 云朵团：三圆 + 底条，上半亮色、底部阴影色。 */
private fun DrawScope.cloudPuffs(
    topLeft: Offset,
    w: Float,
    h: Float,
    main: Color,
    shade: Color,
    s: Float,
    alpha: Float,
) {
    val cx = topLeft.x + w / 2f
    val baseY = topLeft.y + h * 0.55f
    drawRoundRect(
        shade,
        topLeft + Offset(w * 0.06f, h * 0.35f),
        Size(w * 0.88f, h * 0.7f),
        CornerRadius(h * 0.35f),
        alpha = alpha,
    )
    drawCircle(main, h * 0.62f, Offset(cx - w * 0.26f, baseY), alpha = alpha)
    drawCircle(main, h * 0.8f, Offset(cx, baseY - h * 0.18f), alpha = alpha)
    drawCircle(main, h * 0.58f, Offset(cx + w * 0.27f, baseY), alpha = alpha)
    drawRoundRect(
        main,
        topLeft + Offset(w * 0.04f, h * 0.28f),
        Size(w * 0.92f, h * 0.62f),
        CornerRadius(h * 0.3f),
        alpha = alpha,
    )
}

private fun DrawScope.drawJumpParticles(game: StarJumpGame) {
    for (p in game.particles) {
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(
            p.color,
            5f * game.s * (alpha * 0.6f + 0.4f),
            Offset(p.x, p.y - game.cameraY),
            alpha = alpha * 0.9f,
        )
    }
    for (t in game.texts) {
        val k = (t.life / 0.9f).coerceIn(0f, 1f)
        jumpTextPaint.alpha = (255 * k).toInt()
        drawContext.canvas.nativeCanvas.drawText(
            t.text,
            t.x,
            t.y - game.cameraY - (1f - k) * 30f * game.s,
            jumpTextPaint,
        )
    }
}

/** 跳台小熊：上升拉伸前倾、下落微收、倾斜随水平速度；帧用 JUMP。绘制走 [drawGameBear]。 */
private fun DrawScope.drawJumpBear(
    game: StarJumpGame,
    sprites: BearSprites,
    x: Float,
) {
    val s = game.s
    val bs = game.bearSize
    val cy = game.bearY - game.cameraY
    val dead = game.phase == GamePhase.OVER
    if (dead && game.deadT > 1.4f) return

    val tilt =
        (game.bearVX / (700f * s)).coerceIn(-1f, 1f) * 12f +
            (game.bearVY / (1500f * s)).coerceIn(-1f, 1f) * 6f
    val stretch = 1f + min(abs(game.bearVY) / (2600f * s), 0.13f)
    val squash =
        if (game.squashT in 0f..0.16f) {
            val sq = game.squashT / 0.16f
            0.80f + 0.20f * (1f - (1f - sq) * (1f - sq))
        } else {
            1f
        }
    val alpha = if (dead) (1f - (game.deadT - 0.9f) / 0.5f).coerceIn(0f, 1f) else 1f
    val tumble =
        if (dead) {
            val tk = (game.deadT / 0.5f).coerceIn(0f, 1f)
            -95f * (1f - (1f - tk) * (1f - tk) * (1f - tk))
        } else {
            0f
        }
    // 帧：待机跑循环呼吸感，落地瞬间用落地帧，上升跳帧，下落下落帧
    val frame =
        when {
            game.phase == GamePhase.READY -> runCycleFrame(game.time * 3.5f)
            game.squashT in 0f..0.16f -> BearFrame.LAND
            game.bearVY < 0f -> BearFrame.JUMP
            else -> BearFrame.FALL
        }

    drawGameBear(
        sprites = sprites,
        centerX = x,
        centerY =
            cy +
                if (game.phase == GamePhase.READY) {
                    sin(game.time * 2.2f) * 2f * s
                } else {
                    0f
                },
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                dead = dead,
                frame = frame,
                bodyTilt = tilt + tumble,
                scaleX = (2f - stretch) * (1f + (1f - squash) * 0.8f),
                scaleY = stretch * squash,
                alpha = alpha,
            ),
    )
}
