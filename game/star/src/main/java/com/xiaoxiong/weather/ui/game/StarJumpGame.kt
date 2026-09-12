package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal enum class PlatType { NORMAL, MOVING, BREAK, SPRING }

internal class Platform(
    var x: Float,
    val y: Float,
    val w: Float,
    val type: PlatType,
    var vx: Float = 0f,
    var broken: Boolean = false,
    var breakT: Float = 0f,
)

internal class StarItem(
    val x: Float,
    val y: Float,
    var taken: Boolean = false,
    val seed: Float,
)

internal class JumpFloatText(
    val text: String,
    val x: Float,
    val y: Float,
    var life: Float,
)

/** 摘星场景：纯晴天（非雨天）时为夜晚（star 字面义，星最多）；阴天灰云；雨/雪/雷暴渲染雨雪。 */
enum class JumpScene { SUNNY, CLOUDY, RAIN_LIGHT, RAIN, RAIN_HEAVY, SNOW, STORM, FREEZING_RAIN, HAIL }

internal fun jumpSceneFromWeather(
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
internal class StarJumpGame {
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

    val altitudeKm get() = StarJumpLogic.altitudeKm(cameraY, s)

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
            val roll = Random.nextFloat()
            val early = nextPlatY > -700f * s
            // 类型与宽度决策委托引擎层（可测）；x 与 vx 仍用独立随机，保持原生成分布
            val (type, pw) = StarJumpLogic.platformShape(progress, early, roll, w, s)
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
        // 垂直物理（委托引擎层，帧行为可测）
        val falling = StarJumpLogic.stepFalling(bearY, bearVY, s, dt)
        bearY = falling.first
        bearVY = falling.second

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
            // 踩平台：仅下落时、脚线穿越台面（判定委托引擎层）
            if (bearVY > 0f) {
                val contact =
                    StarJumpLogic.resolveContact(
                        bearX = bearX,
                        prevFeetY = prevY + bs / 2f,
                        newFeetY = bearY + bs / 2f,
                        bearVY = bearVY,
                        platforms = platforms,
                        w = w,
                        h = h,
                        s = s,
                        cameraY = cameraY,
                    )
                if (contact.hit && contact.platform != null) {
                    val p = contact.platform
                    bearY = contact.landY
                    if (p.type == PlatType.BREAK) {
                        p.broken = true
                        p.breakT = 0f
                        onBounce()
                    } else if (p.type == PlatType.SPRING) {
                        shakeT = 0.10f
                        onSpring()
                    } else {
                        onBounce()
                    }
                    bearVY = contact.bounceVY
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
                }
            }

            // 吃星星
            for (st in stars) {
                if (st.taken) continue
                if (StarJumpLogic.starPickup(bearX, bearY, st.x, st.y, w, s)) {
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
            score = StarJumpLogic.scoreFor(maxHeight, starsCollected, s)

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
