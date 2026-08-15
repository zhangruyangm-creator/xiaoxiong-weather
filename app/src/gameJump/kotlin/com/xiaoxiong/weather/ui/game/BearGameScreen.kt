package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 彩蛋口令：搜索框输入「#小熊跳跳」进入跑酷小游戏。 */
const val EASTER_EGG_BEAR_GAME = "#小熊跳跳"

/** 跑酷场景：跟随当前天气。阴/雨（按强度分档）/雪/雷暴/冻雨/冰雹渲染对应粒子与灰调背景。 */
enum class RunScene { SUNNY, CLOUDY, RAIN_LIGHT, RAIN, RAIN_HEAVY, SNOW, STORM, FREEZING_RAIN, HAIL }

private fun runSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): RunScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> RunScene.FREEZING_RAIN
        isHailCode(sceneCode) -> RunScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> RunScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> RunScene.RAIN_LIGHT
                        RainStrength.HEAVY -> RunScene.RAIN_HEAVY
                        else -> RunScene.RAIN
                    }
                FarmWeather.STORMY -> RunScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> RunScene.CLOUDY
                FarmWeather.SUNNY -> RunScene.SUNNY
            }
    }

enum class ObstacleType { PUDDLE, SNOWMAN, STORM_CLOUD, ICE_WALL }

class Obstacle(
    val type: ObstacleType,
    var x: Float,
    val w: Float,
    val h: Float,
)

class Coin(
    var x: Float,
    val y: Float,
    val r: Float,
)

class BgCloud(
    var x: Float,
    var y: Float,
    val scale: Float,
    val layer: Float,
)

class FloatText(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float = 0.8f,
)

/**
 * 小熊跑酷全部可变状态。物理量在普通字段里（每帧变化不走快照），
 * 只有阶段/最高分用 Compose state 驱动 HUD 重组。
 */
class BearGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)

    /** 当前天气场景；由 [BearGameScreen] 在天气变化时刷新。 */
    var scene by mutableStateOf(RunScene.SUNNY)

    var size = IntSize.Zero
    var time = 0f
    var distance = 0f
    var speed = 0f
    var coinCount = 0
    var bearY = 0f
    var bearVY = 0f
    var jumpsLeft = 2
    var grounded = true
    var nextGap = Float.MAX_VALUE

    /** 视差山丘 Path 缓存：key = heightRatio，避免每帧重建 Path 引发 GC。 */
    var hillPathCache: Pair<Float, Path>? = null
    var bob = 0f
    var runPhase = 0f
    var deadT = 0f

    /** 眨眼倒计时；< 0 的 0.12s 内为闭眼。 */
    var blinkT = 2.5f

    /** 落地挤压计时；< 0 表示未触发。 */
    var squashT = -1f

    /** 二段跳空翻进度计时；< 0 表示未触发。 */
    var flipT = -1f

    /** 死亡屏幕震动剩余时间。 */
    var shakeT = 0f
    var dustT = 0f

    val obstacles = mutableListOf<Obstacle>()
    val coins = mutableListOf<Coin>()
    val clouds = mutableListOf<BgCloud>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<FloatText>()

    val s get() = size.height / 820f
    val groundY get() = size.height * 0.80f
    val bearSize get() = size.height * 0.115f
    val bearX get() = size.width * 0.18f
    private val floorY get() = groundY - bearSize
    private val gravity get() = 2500f * s
    private val jumpV get() = -960f * s
    private val jumpV2 get() = -830f * s
    private val maxSpeed get() = 950f * s

    /**
     * 速度曲线：由纯距离分数（不含金币）驱动，而不是按时间线性加速。
     * 开局 340，约 5000 分时才达到极速 950；前期较快、越到后期增速越缓。
     */
    private fun speedAt(distanceScore: Float): Float {
        val p = (distanceScore / 5000f).coerceIn(0f, 1f)
        return (340f + 610f * sqrt(p)) * s
    }

    /**
     * 奔跑循环相位速率（rad/s）：开局 ~1.68 循环/s（比旧版 2.16 更从容），
     * 随速度渐快；速度超过 [runCadenceCapSpeed] 后步频封顶 ~3.2 循环/s，
     * 剩余增速靠拉长步幅表达，避免高速时 8 帧循环切换逼近刷新率导致抽帧/腿糊。
     */
    private val runCadenceCapSpeed get() = 640f * s

    private fun runPhaseRate(): Float = min(speed, runCadenceCapSpeed) * 0.031f / s

    fun score(): Int = if (s <= 0f) 0 else (distance / (60f * s)).toInt() + coinCount * 10

    fun resize(newSize: IntSize) {
        if (newSize == size || newSize.height <= 0) return
        size = newSize
        // 对局中改尺寸会让障碍像素与碰撞错位，直接整局回准备态
        if (phase == GamePhase.RUNNING || phase == GamePhase.OVER) {
            phase = GamePhase.READY
            reset()
        } else if (phase == GamePhase.READY) {
            reset()
        }
        bearY = bearY.coerceIn(-size.height.toFloat(), floorY)
        if (clouds.isEmpty()) {
            repeat(5) {
                clouds +=
                    BgCloud(
                        x = Random.nextFloat() * size.width,
                        y = size.height * (0.06f + Random.nextFloat() * 0.22f),
                        scale = 0.6f + Random.nextFloat() * 0.9f,
                        layer = if (it % 2 == 0) 0.15f else 0.3f,
                    )
            }
        }
    }

    fun reset() {
        time = 0f
        distance = 0f
        speed = 0f
        coinCount = 0
        bearY = floorY
        bearVY = 0f
        jumpsLeft = 2
        grounded = true
        nextGap = 560f * s
        runPhase = 0f
        deadT = 0f
        bob = 0f
        blinkT = 2.5f
        squashT = -1f
        flipT = -1f
        shakeT = 0f
        dustT = 0f
        obstacles.clear()
        coins.clear()
        particles.clear()
        texts.clear()
    }

    fun start() {
        reset()
        phase = GamePhase.RUNNING
        speed = 340f * s
    }

    fun tap() {
        when (phase) {
            GamePhase.READY -> start()
            GamePhase.RUNNING ->
                if (jumpsLeft > 0) {
                    bearVY = if (jumpsLeft == 2) jumpV else jumpV2
                    jumpsLeft--
                    grounded = false
                    // 二段跳触发前空翻
                    if (jumpsLeft == 0) flipT = 0f
                    burst(bearX + bearSize / 2, groundY, count = 3, spread = 140f * s)
                }
            GamePhase.OVER -> Unit
        }
    }

    fun die(onDeath: (score: Int) -> Unit) {
        phase = GamePhase.OVER
        deadT = 0f
        shakeT = 0.35f
        flipT = -1f
        burst(bearX + bearSize / 2, bearY + bearSize / 2, count = 16, spread = 540f * s)
        onDeath(score())
    }

    private fun onLand() {
        squashT = 0f
        flipT = -1f
        burst(
            bearX + bearSize / 2,
            groundY,
            count = 6,
            spread = 170f * s,
            color = Color(0xFFD7CCC8),
        )
    }

    fun burst(
        cx: Float,
        cy: Float,
        count: Int,
        spread: Float,
        color: Color = Color.White,
    ) {
        repeat(count) {
            val a = Random.nextFloat() * (Math.PI * 2).toFloat()
            val v = (0.3f + Random.nextFloat() * 0.7f) * spread
            val life = 0.5f + Random.nextFloat() * 0.4f
            particles +=
                Particle(
                    x = cx,
                    y = cy,
                    vx = kotlin.math.cos(a) * v,
                    vy = kotlin.math.sin(a) * v - 120f * s,
                    life = life,
                    maxLife = life,
                    color = color,
                )
        }
    }

    /**
     * 场景 → 障碍概率映射（纯函数，可单测）。r ∈ [0,1)。
     * 雷暴/冰雹天雷云占主导，普通雨天水坑占主导，雪天雪人占主导，晴天较均衡。
     * 雪/冻雨才出现冰墙（与冰主题一致）。
     */
    fun obstacleTypeFor(
        scene: RunScene,
        r: Float,
    ): ObstacleType =
        when (scene) {
            RunScene.SNOW ->
                when {
                    r < 0.14f -> ObstacleType.PUDDLE
                    r < 0.52f -> ObstacleType.SNOWMAN
                    r < 0.76f -> ObstacleType.ICE_WALL
                    else -> ObstacleType.STORM_CLOUD
                }
            RunScene.FREEZING_RAIN ->
                when {
                    r < 0.42f -> ObstacleType.PUDDLE
                    r < 0.60f -> ObstacleType.SNOWMAN
                    r < 0.78f -> ObstacleType.ICE_WALL
                    else -> ObstacleType.STORM_CLOUD
                }
            // 雷暴/冰雹：雷暴云占主导（50%+），而不是和晴天里一样只是偶尔出现
            RunScene.STORM, RunScene.HAIL ->
                when {
                    r < 0.30f -> ObstacleType.PUDDLE
                    r < 0.45f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
            // 普通雨：水坑变多，雪人减少
            RunScene.RAIN, RunScene.RAIN_LIGHT, RunScene.RAIN_HEAVY ->
                when {
                    r < 0.62f -> ObstacleType.PUDDLE
                    r < 0.80f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
            RunScene.SUNNY, RunScene.CLOUDY ->
                when {
                    r < 0.40f -> ObstacleType.PUDDLE
                    r < 0.72f -> ObstacleType.SNOWMAN
                    else -> ObstacleType.STORM_CLOUD
                }
        }

    /**
     * 冰墙高度：单跳（~184·s）够不到、二段跳（~322·s）稳过；
     * 雪天比冻雨略矮，手感更宽松。
     */
    private fun iceWallHeight(scene: RunScene): Float =
        when (scene) {
            RunScene.SNOW -> 195f * s
            else -> 210f * s
        }

    /** 冰墙出现所需的最低分数：雪天 300、冻雨 200，低分段先不出（需二段跳），前松后紧。 */
    private fun iceWallMinScore(scene: RunScene): Int =
        when (scene) {
            RunScene.SNOW -> 300
            else -> 200
        }

    /** 出生障碍类型：冰墙在低分段降级为雪人。 */
    fun spawnType(
        scene: RunScene,
        r: Float,
        score: Int,
    ): ObstacleType {
        val type = obstacleTypeFor(scene, r)
        return if (type == ObstacleType.ICE_WALL && score < iceWallMinScore(scene)) {
            ObstacleType.SNOWMAN
        } else {
            type
        }
    }

    private fun spawn() {
        val r = Random.nextFloat()
        val type = spawnType(scene, r, score())
        val (w, h) =
            when (type) {
                ObstacleType.PUDDLE -> 78f * s to 20f * s
                ObstacleType.SNOWMAN -> 56f * s to 84f * s
                ObstacleType.STORM_CLOUD -> 68f * s to 50f * s
                ObstacleType.ICE_WALL -> 46f * s to iceWallHeight(scene)
            }
        val x = size.width + 48f * s
        obstacles += Obstacle(type, x, w, h)
        if (Random.nextFloat() < 0.4f) {
            // 障碍上方一道金币弧线，鼓励踩着节奏跳
            val cy = groundY - bearSize - (30f + Random.nextFloat() * 46f) * s
            for (i in -2..2) {
                val lift = (2 - abs(i)) * 14f * s
                coins += Coin(x + w / 2 + i * 34f * s, cy - lift, 11f * s)
            }
        }
    }

    fun tick(
        dt: Float,
        onCoin: () -> Unit,
        onDeath: (score: Int) -> Unit,
    ) {
        if (size == IntSize.Zero) return
        time += dt
        if (shakeT > 0f) shakeT -= dt
        if (squashT >= 0f) squashT += dt
        if (phase != GamePhase.OVER) {
            blinkT -= dt
            if (blinkT < -0.12f) blinkT = 2.2f + Random.nextFloat() * 2.2f
        }

        // 粒子任何阶段都继续飞
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.vy += 900f * s * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
        // 飘分
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= 70f * s * dt
            if (t.life <= 0f) ti.remove()
        }

        if (phase == GamePhase.READY) bob = sin(time * 2.5f) * 1.5f * s
        if (phase == GamePhase.OVER) deadT += dt
        if (phase != GamePhase.RUNNING) return

        speed = min(maxSpeed, speedAt(distance / (60f * s)))
        val dx = speed * dt
        distance += dx
        if (flipT >= 0f) flipT += dt

        // 小熊物理
        bearVY += gravity * dt
        bearY += bearVY * dt
        if (bearY >= floorY) {
            val landed = !grounded
            bearY = floorY
            bearVY = 0f
            jumpsLeft = 2
            if (landed) onLand()
            grounded = true
            // 步频跟速度走：步幅相位驱动四肢摆动与身体起伏
            runPhase += runPhaseRate() * dt
            bob = -abs(sin(runPhase)) * 3f * s
            // 高速跑动脚下扬尘
            if (speed > 520f * s) {
                dustT += dt
                if (dustT > 0.09f) {
                    dustT = 0f
                    val life = 0.3f + Random.nextFloat() * 0.2f
                    particles +=
                        Particle(
                            x = bearX + bearSize * 0.25f,
                            y = groundY - 5f * s,
                            vx = -(70f + Random.nextFloat() * 90f) * s,
                            vy = -(20f + Random.nextFloat() * 60f) * s,
                            life = life,
                            maxLife = life,
                            color = Color(0xFFD7CCC8),
                        )
                }
            }
        } else {
            grounded = false
            bob = 0f
        }

        // 世界推进
        obstacles.forEach { it.x -= dx }
        obstacles.removeAll { it.x < -it.w - 20f * s }
        coins.forEach { it.x -= dx }
        coins.removeAll { it.x < -it.r - 20f * s }
        clouds.forEach {
            it.x -= dx * it.layer + 10f * s * dt
            if (it.x < -140f * s * it.scale) {
                it.x = size.width + Random.nextFloat() * 160f * s
            }
        }

        nextGap -= dx
        if (nextGap <= 0f) {
            spawn()
            nextGap = (460f + Random.nextFloat() * 340f) * s
        }

        // 碰撞（判定盒内缩，手感宽松一点）
        val insetX = bearSize * 0.20f
        val insetY = bearSize * 0.14f
        val bearRect =
            Rect(
                bearX + insetX,
                bearY + insetY,
                bearX + bearSize - insetX,
                bearY + bearSize - insetY * 0.5f,
            )
        val overlapping =
            coins.filter { c ->
                val dxC = c.x - (bearX + bearSize / 2)
                val dyC = c.y - (bearY + bearSize / 2)
                val reach = c.r + bearSize * 0.52f
                dxC * dxC + dyC * dyC < reach * reach
            }
        if (overlapping.isNotEmpty()) {
            for (coinHit in overlapping) {
                coins.remove(coinHit)
                coinCount++
                texts += FloatText(coinHit.x, coinHit.y - 24f * s, "+10")
                burst(coinHit.x, coinHit.y, count = 4, spread = 150f * s, color = Color(0xFFFFCA28))
                onCoin()
            }
        }
        val dead =
            obstacles.any { ob ->
                val obTop =
                    when (ob.type) {
                        ObstacleType.PUDDLE -> groundY - ob.h
                        ObstacleType.SNOWMAN -> groundY - ob.h
                        ObstacleType.STORM_CLOUD -> groundY - ob.h - 4f * s
                        ObstacleType.ICE_WALL -> groundY - ob.h
                    }
                val shrinkX = ob.w * 0.16f
                val shrinkTop = ob.h * 0.14f
                bearRect.overlaps(
                    Rect(ob.x + shrinkX, obTop + shrinkTop, ob.x + ob.w - shrinkX, obTop + ob.h),
                )
            }
        if (dead) die(onDeath)
    }
}

@Composable
fun BearGameScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    BackHandler(onBack = onExit)
    val context = LocalContext.current
    val view = LocalView.current
    val style = weatherSceneStyle(sceneCode, isDay, appDark = false)

    val prefs = rememberGamePrefs()
    val lb = rememberLeaderboardState(prefs, "leaderboard_run", "best")
    val gameWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val game =
        remember {
            BearGame().also { it.best = prefs.getInt("best", 0) }
        }
    // 天气/城市变化时实时更新上报所用值，避免 LaunchedEffect(Unit) 闭包捕获开局时的旧值
    val reportSceneCode by rememberUpdatedState(sceneCode)
    val reportCityName by rememberUpdatedState(cityName)
    // 天气变化时更新游戏场景（雨量强度决定雨点多少）
    LaunchedEffect(gameWeather, sceneCode) {
        game.scene = runSceneFromWeather(gameWeather, sceneCode)
    }
    val bearSprites = rememberBearSprites()
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = Long.MIN_VALUE
        var slowTick = 0
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
                if (lb.show) return@withFrameNanos
                val running = game.phase == GamePhase.RUNNING
                // 标题/结算界面无需 60fps：每 5 帧处理一次（约 12fps），省电并减少重组
                if (running || slowTick % 5 == 0) {
                    game.tick(
                        dt,
                        onCoin = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        onDeath = { score ->
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            if (score > game.best) {
                                game.best = score
                                prefs.edit { putInt("best", score) }
                            }
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
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(style.gradient))
                .onSizeChanged { sz ->
                    game.resize(IntSize(sz.width, sz.height))
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            game.tap()
                        }
                    },
        ) {
            frame // 每帧触发重绘
            if (game.size == IntSize.Zero) return@Canvas
            drawWorld(game, style.contentColor, isDay = isDay ?: true, sprites = bearSprites)
        }

        GameHud(
            game = game,
            frame = frame,
            onExit = onExit,
            onLeaderboard = {
                lb.entries = lb.store.load()
                lb.show = true
            },
        )

        if (lb.show) {
            GameLeaderboardDialog(
                title = stringResource(R.string.bear_leaderboard_title),
                entries = lb.entries,
                onClear = {
                    lb.store.clear()
                    lb.entries = emptyList()
                    game.best = 0
                },
                onDismiss = { lb.show = false },
            )
        }

        when (game.phase) {
            GamePhase.READY -> {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.bear_game_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = style.contentColor,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.bear_game_tap_start),
                        style = MaterialTheme.typography.titleMedium,
                        color = style.contentColor,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.bear_game_hint_jump),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedContentColor,
                    )
                }
            }
            GamePhase.OVER -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .pointerInput(Unit) {
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
                            text = stringResource(R.string.bear_game_over),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${game.score()}",
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
private fun GameHud(
    game: BearGame,
    frame: Long,
    onExit: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    // frame 仅用于驱动 HUD 每帧刷新分数
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
                text = "${game.score()}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.bear_game_best_label) + " ${game.best}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        LeaderboardButton(onClick = onLeaderboard)
    }
}
