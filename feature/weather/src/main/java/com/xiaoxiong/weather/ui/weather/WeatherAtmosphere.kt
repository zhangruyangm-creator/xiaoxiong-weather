package com.xiaoxiong.weather.ui.weather

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI_F = 6.2831853f
private const val PI_F = 3.1415927f
private const val SCENE_FADE_MILLIS = 650
private const val STAR_COUNT = 18

internal const val PARTICLE_LAYER_FAR = 0
internal const val PARTICLE_LAYER_MID = 1
internal const val PARTICLE_LAYER_NEAR = 2

// 场景配色：常量只读，draw 阶段直接引用，避免每帧新建 Color/Brush
private val CloudPartlyBody = Color.White.copy(alpha = 0.70f)
private val CloudPartlyBase = Color(0xFFD8E5F2).copy(alpha = 0.45f)
private val CloudOvercastBody = Color(0xFFE7EDF3).copy(alpha = 0.52f)
private val CloudOvercastBase = Color(0xFF93A3B3).copy(alpha = 0.50f)
private val CloudDrizzleBody = Color(0xFFC2D2E0).copy(alpha = 0.58f)
private val CloudDrizzleBase = Color(0xFF7A91A6).copy(alpha = 0.50f)
private val CloudRainBody = Color(0xFFA8BBCB).copy(alpha = 0.62f)
private val CloudRainBase = Color(0xFF587085).copy(alpha = 0.58f)
private val CloudHeavyBody = Color(0xFF8094A4).copy(alpha = 0.68f)
private val CloudHeavyBase = Color(0xFF35434F).copy(alpha = 0.66f)
private val CloudThunderBody = Color(0xFF67798A).copy(alpha = 0.72f)
private val CloudThunderBase = Color(0xFF17212D).copy(alpha = 0.72f)
private val CloudIceBody = Color(0xFFB7C9D8).copy(alpha = 0.58f)
private val CloudIceBase = Color(0xFF6A8096).copy(alpha = 0.52f)
private val CloudFogBody = Color(0xFFDDE4EA).copy(alpha = 0.30f)
private val CloudFogBase = Color(0xFFA7B4BF).copy(alpha = 0.26f)
private val RainDropColor = Color(0xFFE3F2FD)
private val HailColor = Color(0xFFE3F2FD)
private val IceLineColor = Color(0xFFB3E5FC)
private val IceTipColor = Color.White
private val MoonColor = Color(0xFFFFF59D)
private val MoonHaloColor = Color(0xFFFFF8E1)
private val SunCoreColor = Color(0xFFFFF176)
private val SunHaloColor = Color(0xFFFFFDE7)
private val BoltColor = Color(0xFFFFF59D)
private val StormLightColor = Color(0xFFFFF59D).copy(alpha = 0.5f)
private val IceGlazeColor = Color(0xFFB3E5FC).copy(alpha = 0.45f)
private val FogBandColor = Color.White

@Composable
fun WeatherAtmosphereCard(
    code: Int?,
    isDay: Boolean?,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(WeatherSceneStyle) -> Unit,
) {
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = remember(code, isDay, appDark) { weatherSceneStyle(code, isDay, appDark) }
    val shape = RoundedCornerShape(20.dp)
    val reduceMotion = LocalReduceMotion.current
    val animateScene = !reduceMotion && !paused
    val day = isDay != false
    // 渐变过渡与场景淡入淡出共用 650ms；颜色 State 只在 draw 阶段读，避免卡片每帧重组
    val colorSpec: AnimationSpec<Color> = if (animateScene) tween(SCENE_FADE_MILLIS) else snap()
    val gradient = style.gradient
    val topColor = animateColorAsState(gradient[0], colorSpec, label = "atmosphere-top")
    val midColor = animateColorAsState(gradient.getOrElse(1) { gradient[0] }, colorSpec, label = "atmosphere-mid")
    val bottomColor = animateColorAsState(gradient.getOrElse(2) { gradient.last() }, colorSpec, label = "atmosphere-bottom")

    Box(
        modifier =
            modifier
                .clip(shape)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(topColor.value, midColor.value, bottomColor.value)))
                },
    ) {
        if (animateScene) {
            // matchParentSize：动画层随卡片 Box 尺寸测量。直接 fillMaxSize 会在
            // LazyColumn 无界高度下先被量成 0 高，导致太阳/云/雨雪从未绘制
            Crossfade(
                targetState = style.scene to day,
                animationSpec = tween(SCENE_FADE_MILLIS),
                modifier = Modifier.matchParentSize(),
                label = "weather-atmosphere-scene",
            ) { (scene, sceneDay) ->
                // Crossfade 已撑满卡片，此处约束有界，fillMaxSize 拿到卡片真实尺寸
                WeatherAtmosphereOverlay(
                    scene = scene,
                    isDay = sceneDay,
                    paused = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            WeatherAtmosphereOverlay(
                scene = style.scene,
                isDay = day,
                paused = true,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (style.scrimAlpha > 0f) {
            // 浅色渐变场景在动画层上叠纯黑暗角，保证白字/muted 对复合后背景 ≥3:1；
            // Brush 随 scrimAlpha 缓存，绘制阶段不重复分配
            val scrim =
                remember(style.scrimAlpha) {
                    val color = Color.Black.copy(alpha = style.scrimAlpha)
                    Brush.verticalGradient(listOf(color, color))
                }
            Box(modifier = Modifier.matchParentSize().background(scrim))
        }
        content(style)
    }
}

@Composable
private fun WeatherAtmosphereOverlay(
    scene: WeatherScene,
    isDay: Boolean,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val particles = remember(scene) { atmosphereParticles(scene, seed = scene.ordinal * 131 + 17) }
    // 闪电路径跨帧复用：只在轮次变化时 reset 重建，不在每帧新建 Path
    val lightning = remember { LightningPaths() }
    val reduceMotion = LocalReduceMotion.current
    if (scene == WeatherScene.UNKNOWN || reduceMotion || paused) {
        // 静态一帧：phase/flash/drift 归零，不启动无限动画
        AtmosphereCanvas(
            scene = scene,
            isDay = isDay,
            particles = particles,
            lightning = lightning,
            phase = { 0f },
            flash = { 0f },
            drift = { 0f },
            cycle = { 0f },
            modifier = modifier,
        )
    } else {
        AnimatedAtmosphereCanvas(scene, isDay, particles, lightning, modifier)
    }
}

@Composable
private fun AnimatedAtmosphereCanvas(
    scene: WeatherScene,
    isDay: Boolean,
    particles: List<Particle>,
    lightning: LightningPaths,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "weather-atmosphere")
    // 不用 `by`：State 只在 draw 阶段读取，只重绘 Canvas，避免整卡每帧重组
    val phase =
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    val flash =
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "flash",
        )
    // 慢速漂移：云层/雾带/太阳光芒用，周期拉长避免晃眼
    val drift =
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
    // 雷暴轮次：64s = 20 个 flash 周期，闪电每轮形态/位置不同且与 flash 起点对齐
    val cycle =
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(64000, easing = LinearEasing), RepeatMode.Restart),
            label = "cycle",
        )
    AtmosphereCanvas(
        scene = scene,
        isDay = isDay,
        particles = particles,
        lightning = lightning,
        phase = { phase.value },
        flash = { flash.value },
        drift = { drift.value },
        cycle = { cycle.value },
        modifier = modifier,
    )
}

@Composable
private fun AtmosphereCanvas(
    scene: WeatherScene,
    isDay: Boolean,
    particles: List<Particle>,
    lightning: LightningPaths,
    phase: () -> Float,
    flash: () -> Float,
    drift: () -> Float,
    cycle: () -> Float,
    modifier: Modifier = Modifier,
) {
    // 纯装饰背景：clearAndSetSemantics 让 TalkBack 跳过，天气信息由上层 Text 朗读。
    // 尺寸由调用方（matchParentSize/fillMaxSize）决定，保证 drawWithCache 的 cache
    // 段拿到卡片真实尺寸；随尺寸构建的 Brush/Path/Stroke 都在 cache 段完成，
    // onDrawBehind 只读取动画 State，绘制阶段零 Path/Brush/listOf 新建。
    Spacer(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .drawWithCache {
                    val art = atmosphereArt(scene, isDay, size)
                    onDrawBehind {
                        drawAtmosphere(scene, isDay, particles, art, lightning, phase, flash, drift, cycle)
                    }
                },
    )
}

/** drawWithCache 缓存段产物：尺寸相关的渐变/路径/线型，绘制阶段直接复用。 */
private class AtmosphereArt(
    val fogBands: List<FogBand>,
    val iceBand: Brush?,
    val stormLight: Brush?,
    val moon: Path?,
    val rippleStroke: Stroke?,
    val boltStroke: Stroke?,
)

private class FogBand(
    val brush: Brush,
    val top: Float,
    val height: Float,
    val driftPhase: Float,
    val alpha: Float,
)

/** 可复用闪电路径：seed 变化才 reset 重建。 */
private class LightningPaths {
    val main: Path = Path()
    val branch: Path = Path()
    var seed: Int = Int.MIN_VALUE
}

private fun Density.atmosphereArt(
    scene: WeatherScene,
    isDay: Boolean,
    size: Size,
): AtmosphereArt {
    val fogBands = if (scene == WeatherScene.FOG) buildFogBands(size) else emptyList()
    val iceBand = if (scene == WeatherScene.FREEZING_RAIN) buildIceBand(size) else null
    val stormLight = if (scene == WeatherScene.THUNDER) buildStormLight(size) else null
    val nightSky = !isDay && (scene == WeatherScene.CLEAR || scene == WeatherScene.PARTLY_CLOUDY)
    val moon = if (nightSky) buildMoonCrescent(size) else null
    val rippleStroke = if (scene.isRainScene()) Stroke(width = 1.2f.dp.toPx()) else null
    val boltStroke = if (scene == WeatherScene.THUNDER) Stroke(width = 2.4f.dp.toPx(), cap = StrokeCap.Round) else null
    return AtmosphereArt(fogBands, iceBand, stormLight, moon, rippleStroke, boltStroke)
}

private fun WeatherScene.isRainScene(): Boolean =
    this == WeatherScene.DRIZZLE ||
        this == WeatherScene.RAIN ||
        this == WeatherScene.HEAVY_RAIN ||
        this == WeatherScene.THUNDER

private fun Density.buildFogBands(size: Size): List<FogBand> {
    val bandHeight = 24.dp.toPx()
    return List(5) { index ->
        val top = size.height * (0.2f + index * 0.12f)
        FogBand(
            brush =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.42f to FogBandColor,
                    0.58f to FogBandColor,
                    1f to Color.Transparent,
                    startY = top,
                    endY = top + bandHeight,
                ),
            top = top,
            height = bandHeight,
            driftPhase = index * 1.1f,
            alpha = 0.14f + 0.035f * index,
        )
    }
}

private fun Density.buildIceBand(size: Size): Brush =
    Brush.verticalGradient(
        0f to Color.Transparent,
        1f to IceGlazeColor,
        startY = size.height * 0.84f,
        endY = size.height,
    )

private fun Density.buildStormLight(size: Size): Brush =
    Brush.verticalGradient(
        0f to StormLightColor,
        1f to Color.Transparent,
        startY = 0f,
        endY = size.height * 0.8f,
    )

/** 真实月牙：外圆 Path.op(Difference) 减掉偏移裁切圆，替代旧的「固定色圆盖」。 */
private fun Density.buildMoonCrescent(size: Size): Path {
    val radius = 26.dp.toPx()
    val center = Offset(size.width * 0.8f, size.height * 0.2f)
    val lit = Path().apply { addOval(Rect(center = center, radius = radius)) }
    val cut =
        Path().apply {
            addOval(
                Rect(
                    center = Offset(center.x - radius * 0.5f, center.y - radius * 0.3f),
                    radius = radius * 0.86f,
                ),
            )
        }
    return Path().apply { op(lit, cut, PathOperation.Difference) }
}

private fun DrawScope.drawAtmosphere(
    scene: WeatherScene,
    isDay: Boolean,
    particles: List<Particle>,
    art: AtmosphereArt,
    lightning: LightningPaths,
    phase: () -> Float,
    flash: () -> Float,
    drift: () -> Float,
    cycle: () -> Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    when (scene) {
        WeatherScene.CLEAR -> if (isDay) drawDaySky(phase, drift) else drawNightSky(phase, art)

        WeatherScene.PARTLY_CLOUDY -> {
            if (isDay) drawDaySky(phase, drift) else drawNightSky(phase, art)
            drawSceneClouds(scene, drift)
        }

        WeatherScene.OVERCAST -> drawSceneClouds(scene, drift)

        WeatherScene.FOG -> {
            drawSceneClouds(scene, drift)
            drawFogBands(art, drift)
        }

        WeatherScene.DRIZZLE,
        WeatherScene.RAIN,
        WeatherScene.HEAVY_RAIN,
        -> {
            drawSceneClouds(scene, drift)
            drawRainParticles(scene, particles, phase, art)
        }

        WeatherScene.THUNDER -> {
            drawSceneClouds(scene, drift)
            drawRainParticles(scene, particles, phase, art)
            drawThunderFlash(art, lightning, flash, cycle)
        }

        WeatherScene.HAIL -> {
            drawSceneClouds(scene, drift)
            drawHailParticles(particles, phase)
        }

        WeatherScene.FREEZING_RAIN -> {
            drawSceneClouds(scene, drift)
            drawFreezingParticles(particles, phase, art)
        }

        WeatherScene.SNOW -> drawSnowParticles(particles = particles, phase = phase, heavy = false)

        WeatherScene.HEAVY_SNOW -> drawSnowParticles(particles = particles, phase = phase, heavy = true)

        WeatherScene.UNKNOWN -> Unit
    }
}

// —— 云：分层实心圆组，不用渐变 Brush ——

private fun DrawScope.drawSceneClouds(
    scene: WeatherScene,
    drift: () -> Float,
) {
    val d = drift()
    val swayA = sin(d * TWO_PI_F) * size.width * 0.03f
    val swayB = sin(d * TWO_PI_F + 2.1f) * size.width * 0.025f
    val swayC = sin(d * TWO_PI_F + 3.2f) * size.width * 0.02f
    when (scene) {
        WeatherScene.PARTLY_CLOUDY -> {
            drawCloudLayered(
                size.width * 0.26f + swayA,
                size.height * 0.20f,
                1.05f,
                1.3f,
                CloudPartlyBody,
                CloudPartlyBase,
                0.85f,
            )
            drawCloudLayered(
                size.width * 0.62f + swayB,
                size.height * 0.15f,
                0.85f,
                4.7f,
                CloudPartlyBody,
                CloudPartlyBase,
                0.85f,
            )
        }

        WeatherScene.OVERCAST -> {
            drawCloudLayered(
                size.width * 0.22f + swayA,
                size.height * 0.15f,
                1.30f,
                2.1f,
                CloudOvercastBody,
                CloudOvercastBase,
                1.05f,
            )
            drawCloudLayered(
                size.width * 0.58f + swayB,
                size.height * 0.12f,
                1.45f,
                5.3f,
                CloudOvercastBody,
                CloudOvercastBase,
                1.10f,
            )
            drawCloudLayered(
                size.width * 0.85f + swayC,
                size.height * 0.22f,
                1.10f,
                8.9f,
                CloudOvercastBody,
                CloudOvercastBase,
                1.00f,
            )
        }

        WeatherScene.DRIZZLE -> {
            drawCloudLayered(
                size.width * 0.30f + swayA,
                size.height * 0.13f,
                1.25f,
                1.7f,
                CloudDrizzleBody,
                CloudDrizzleBase,
                1.05f,
            )
            drawCloudLayered(
                size.width * 0.70f + swayB,
                size.height * 0.11f,
                1.15f,
                5.9f,
                CloudDrizzleBody,
                CloudDrizzleBase,
                1.00f,
            )
        }

        WeatherScene.RAIN -> {
            drawCloudLayered(
                size.width * 0.30f + swayA,
                size.height * 0.13f,
                1.30f,
                2.9f,
                CloudRainBody,
                CloudRainBase,
                1.15f,
            )
            drawCloudLayered(
                size.width * 0.70f + swayB,
                size.height * 0.11f,
                1.20f,
                6.1f,
                CloudRainBody,
                CloudRainBase,
                1.10f,
            )
        }

        WeatherScene.HEAVY_RAIN -> {
            drawCloudLayered(
                size.width * 0.28f + swayA,
                size.height * 0.12f,
                1.35f,
                3.7f,
                CloudHeavyBody,
                CloudHeavyBase,
                1.30f,
            )
            drawCloudLayered(
                size.width * 0.68f + swayB,
                size.height * 0.10f,
                1.28f,
                6.7f,
                CloudHeavyBody,
                CloudHeavyBase,
                1.25f,
            )
        }

        WeatherScene.THUNDER -> {
            drawCloudLayered(
                size.width * 0.28f + swayA,
                size.height * 0.11f,
                1.40f,
                4.1f,
                CloudThunderBody,
                CloudThunderBase,
                1.45f,
            )
            drawCloudLayered(
                size.width * 0.68f + swayB,
                size.height * 0.09f,
                1.32f,
                7.3f,
                CloudThunderBody,
                CloudThunderBase,
                1.40f,
            )
        }

        WeatherScene.HAIL,
        WeatherScene.FREEZING_RAIN,
        -> {
            drawCloudLayered(
                size.width * 0.30f + swayA,
                size.height * 0.13f,
                1.25f,
                3.1f,
                CloudIceBody,
                CloudIceBase,
                1.10f,
            )
            drawCloudLayered(
                size.width * 0.70f + swayB,
                size.height * 0.11f,
                1.15f,
                6.9f,
                CloudIceBody,
                CloudIceBase,
                1.05f,
            )
        }

        WeatherScene.FOG ->
            drawCloudLayered(
                size.width * 0.34f + swayA * 0.6f,
                size.height * 0.32f,
                1.50f,
                3.3f,
                CloudFogBody,
                CloudFogBase,
                0.90f,
            )

        else -> Unit
    }
}

/**
 * 工业级云：底部阴影椭圆 + 下层深色圆组 + 上层亮色圆组（共 6 圆 + 1 椭圆），
 * 形状由 seed 确定性微调；不使用渐变 Brush，颜色由场景调色板决定。
 */
private fun DrawScope.drawCloudLayered(
    cx: Float,
    cy: Float,
    scale: Float,
    seed: Float,
    bodyColor: Color,
    baseColor: Color,
    thickness: Float,
) {
    val u = 18.dp.toPx() * scale
    val wide = 1f + 0.12f * sin(seed * 2.3f + 1f)
    val tilt = 0.14f * cos(seed * 1.3f)
    val lift = 1f + 0.10f * sin(seed * 1.7f)
    // 底部阴影椭圆
    drawOval(
        color = baseColor,
        topLeft = Offset(cx - u * 1.55f * wide, cy + u * 0.10f),
        size = Size(u * 3.1f * wide, u * 0.68f * thickness * lift),
    )
    // 下层深色云组
    drawCircle(baseColor, radius = u * 0.92f * lift, center = Offset(cx - u * 0.95f, cy + u * 0.08f * thickness))
    drawCircle(baseColor, radius = u * 1.02f, center = Offset(cx + u * 0.15f, cy + u * 0.14f * thickness))
    drawCircle(baseColor, radius = u * 0.78f, center = Offset(cx + u * 1.02f, cy + u * 0.08f))
    // 上层亮色云组
    drawCircle(bodyColor, radius = u * 0.85f * lift, center = Offset(cx - u * 0.70f + u * tilt, cy - u * 0.26f))
    drawCircle(bodyColor, radius = u * 1.10f, center = Offset(cx + u * 0.15f, cy - u * 0.40f))
    drawCircle(bodyColor, radius = u * 0.76f, center = Offset(cx + u * 0.95f + u * tilt, cy - u * 0.18f))
}

// —— 雾：5 条垂直渐变软带，Brush 尺寸缓存，绘制仅改 alpha/位移 ——

private fun DrawScope.drawFogBands(
    art: AtmosphereArt,
    drift: () -> Float,
) {
    val d = drift()
    art.fogBands.forEach { band ->
        val sway = sin(d * TWO_PI_F * 0.5f + band.driftPhase) * size.width * 0.05f
        drawRect(
            brush = band.brush,
            topLeft = Offset(-size.width * 0.08f + sway, band.top),
            size = Size(size.width * 1.16f, band.height),
            alpha = band.alpha,
        )
    }
}

// —— 雨：正模环绕 + 统一风倾角 + 分层速度/大小/透明度 + 落地涟漪 ——

private fun DrawScope.drawRainParticles(
    scene: WeatherScene,
    particles: List<Particle>,
    phase: () -> Float,
    art: AtmosphereArt,
) {
    val phaseV = phase()
    val baseLength =
        when (scene) {
            WeatherScene.DRIZZLE -> 10.dp.toPx()
            WeatherScene.RAIN -> 16.dp.toPx()
            else -> 22.dp.toPx()
        }
    val sceneSpeed =
        when (scene) {
            WeatherScene.DRIZZLE -> 0.85f
            WeatherScene.RAIN -> 1.10f
            else -> 1.40f
        }
    val sceneAlpha =
        when (scene) {
            WeatherScene.DRIZZLE -> 0.55f
            WeatherScene.RAIN -> 0.75f
            else -> 0.85f
        }
    val slope = rainWindSlope(scene)
    particles.forEach { p ->
        val layerSpeed = particleLayerSpeedMultiplier(p.layer)
        val layerSize = particleLayerSizeMultiplier(p.layer)
        val layerAlpha = particleLayerAlphaMultiplier(p.layer)
        val fall = positiveModulo(p.y + phaseV * p.speed * sceneSpeed * layerSpeed, 1.2f) * size.height
        val x = positiveModulo(p.x + phaseV * p.drift - phaseV * slope * 0.4f, 1f) * size.width
        val length = baseLength * layerSize * (1f + p.lenJitter * 0.4f)
        val jitterAlpha = 0.8f + 0.4f * (p.lenJitter + 0.25f)
        val alpha = (sceneAlpha * layerAlpha * jitterAlpha).coerceIn(0f, 1f)
        drawLine(
            color = RainDropColor.copy(alpha = alpha),
            start = Offset(x, fall),
            end = Offset(x - slope * length, fall + length),
            strokeWidth = (0.7f + p.size * 0.18f).dp.toPx() * layerSize,
            cap = StrokeCap.Round,
        )
    }
    drawRainRipples(scene, phaseV, art)
}

/** 底部落地涟漪：固定槽位 + phase 确定性扩散，alpha 随扩散衰减。 */
private fun DrawScope.drawRainRipples(
    scene: WeatherScene,
    phaseV: Float,
    art: AtmosphereArt,
) {
    val stroke = art.rippleStroke ?: return
    val slots =
        when (scene) {
            WeatherScene.DRIZZLE -> 5
            WeatherScene.RAIN -> 8
            else -> 11
        }
    val speed =
        when (scene) {
            WeatherScene.DRIZZLE -> 1.2f
            WeatherScene.RAIN -> 1.6f
            else -> 2.0f
        }
    for (i in 0 until slots) {
        val t = positiveModulo(phaseV * speed + i.toFloat() / slots, 1f)
        val cx = size.width * positiveModulo(i * 0.618f + 0.13f, 1f)
        val cy = size.height * (0.9f + ((i * 7) % 5) * 0.012f)
        val radius = 2.5f.dp.toPx() + t * 9.dp.toPx()
        drawOval(
            color = Color.White.copy(alpha = (1f - t) * 0.2f),
            topLeft = Offset(cx - radius, cy - radius * 0.35f),
            size = Size(radius * 2f, radius * 0.7f),
            style = stroke,
        )
    }
}

// —— 雪：远/中/近三层速度与大小，近处六角晶旋转 ——

private fun DrawScope.drawSnowParticles(
    particles: List<Particle>,
    phase: () -> Float,
    heavy: Boolean,
) {
    val phaseV = phase()
    val fallSpeed = if (heavy) 0.72f else 0.42f
    val sizeBoost = if (heavy) 1.15f else 1f
    val baseAlpha = if (heavy) 0.95f else 0.85f
    particles.forEach { p ->
        val layerSpeed = particleLayerSpeedMultiplier(p.layer)
        val layerSize = particleLayerSizeMultiplier(p.layer)
        val layerAlpha = particleLayerAlphaMultiplier(p.layer)
        val fall = positiveModulo(p.y + phaseV * p.speed * fallSpeed * layerSpeed, 1.12f) * size.height
        val wobble = sin(phaseV * TWO_PI_F * (0.8f + p.layer * 0.35f) + p.wobblePhase) * (3.dp.toPx() * (1f + p.layer))
        val x = positiveModulo(p.x + wobble / size.width, 1f) * size.width
        val radius = (p.size * 0.40f).dp.toPx() * layerSize * sizeBoost
        val alpha = (baseAlpha * layerAlpha).coerceAtMost(1f)
        if (p.layer == PARTICLE_LAYER_NEAR) {
            drawSnowCrystal(Offset(x, fall), radius, phaseV + p.wobblePhase, Color.White.copy(alpha = alpha))
        } else {
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = Offset(x, fall))
        }
    }
}

private fun DrawScope.drawSnowCrystal(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color,
) {
    val base = rotation * TWO_PI_F * 0.12f
    for (i in 0 until 3) {
        val angle = base + i * PI_F / 3f
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        drawLine(
            color = color,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = radius * 0.32f,
            cap = StrokeCap.Round,
        )
    }
}

// —— 冰雹：拉长冰粒 + 少量落地溅射 ——

private fun DrawScope.drawHailParticles(
    particles: List<Particle>,
    phase: () -> Float,
) {
    val phaseV = phase()
    particles.forEach { p ->
        val layerSpeed = particleLayerSpeedMultiplier(p.layer)
        val layerSize = particleLayerSizeMultiplier(p.layer)
        val layerAlpha = particleLayerAlphaMultiplier(p.layer)
        val fall = positiveModulo(p.y + phaseV * p.speed * 1.35f * layerSpeed, 1.15f) * size.height
        val x = positiveModulo(p.x + phaseV * p.drift * 0.5f, 1f) * size.width
        val radius = (p.size * 0.50f).dp.toPx() * layerSize
        drawOval(
            color = HailColor.copy(alpha = (0.85f * layerAlpha).coerceAtMost(1f)),
            topLeft = Offset(x - radius * 0.65f, fall - radius * 1.1f),
            size = Size(radius * 1.3f, radius * 2.2f),
        )
    }
    drawHailSplashes(phaseV)
}

private fun DrawScope.drawHailSplashes(phaseV: Float) {
    val slotCount = 7
    for (i in 0 until slotCount) {
        val t = positiveModulo(phaseV * 2.4f + i.toFloat() / slotCount, 1f)
        val cx = size.width * positiveModulo(i * 0.37f + 0.13f, 1f)
        val cy = size.height * 0.93f
        val spread = 2.dp.toPx() + t * 7.dp.toPx()
        val alpha = (1f - t) * 0.35f
        val color = Color.White.copy(alpha = alpha)
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx - spread * 0.7f, cy - spread),
            strokeWidth = 1.2f.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + spread * 0.7f, cy - spread),
            strokeWidth = 1.2f.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

// —— 冻雨：冰线 + 水滴 + 底部积冰渐变带 ——

private fun DrawScope.drawFreezingParticles(
    particles: List<Particle>,
    phase: () -> Float,
    art: AtmosphereArt,
) {
    val phaseV = phase()
    val slope = rainWindSlope(WeatherScene.FREEZING_RAIN)
    val baseLength = 14.dp.toPx()
    particles.forEach { p ->
        val layerSpeed = particleLayerSpeedMultiplier(p.layer)
        val layerSize = particleLayerSizeMultiplier(p.layer)
        val layerAlpha = particleLayerAlphaMultiplier(p.layer)
        val fall = positiveModulo(p.y + phaseV * p.speed * 0.95f * layerSpeed, 1.2f) * size.height
        val x = positiveModulo(p.x + phaseV * p.drift, 1f) * size.width
        val length = baseLength * layerSize * (1f + p.lenJitter * 0.3f)
        val alpha = (0.7f * layerAlpha).coerceAtMost(1f)
        val tipX = x - slope * length
        val tipY = fall + length
        drawLine(
            color = IceLineColor.copy(alpha = alpha),
            start = Offset(x, fall),
            end = Offset(tipX, tipY),
            strokeWidth = (0.6f + p.size * 0.16f).dp.toPx() * layerSize,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = IceTipColor.copy(alpha = alpha * 0.8f),
            radius = (0.5f + p.size * 0.08f).dp.toPx() * layerSize,
            center = Offset(tipX, tipY),
        )
    }
    art.iceBand?.let { brush -> drawRect(brush = brush, alpha = 0.55f) }
}

// —— 天空：太阳 / 月亮 + 星星 ——

private fun DrawScope.drawDaySky(
    phase: () -> Float,
    drift: () -> Float,
) {
    val cx = size.width * 0.82f
    val cy = size.height * 0.22f
    val pulse = 1f + 0.05f * sin(phase() * TWO_PI_F)
    val rotation = drift() * TWO_PI_F
    // 光晕 3 层同心圆叠加（不新建 Brush）
    drawCircle(SunHaloColor.copy(alpha = 0.09f), radius = 88.dp.toPx() * pulse, center = Offset(cx, cy))
    drawCircle(SunHaloColor.copy(alpha = 0.13f), radius = 64.dp.toPx() * pulse, center = Offset(cx, cy))
    drawCircle(SunHaloColor.copy(alpha = 0.18f), radius = 46.dp.toPx() * pulse, center = Offset(cx, cy))
    // 光芒双层：8 长 + 8 短错位
    drawSunRays(cx, cy, 40.dp.toPx() * pulse, 58.dp.toPx() * pulse, rotation, SunCoreColor.copy(alpha = 0.55f), 3.dp.toPx(), 0f)
    drawSunRays(cx, cy, 40.dp.toPx() * pulse, 48.dp.toPx() * pulse, rotation, SunCoreColor.copy(alpha = 0.28f), 2.dp.toPx(), PI_F / 8f)
    drawCircle(SunCoreColor.copy(alpha = 0.95f), radius = 34.dp.toPx() * pulse, center = Offset(cx, cy))
}

private fun DrawScope.drawSunRays(
    cx: Float,
    cy: Float,
    innerR: Float,
    outerR: Float,
    rotation: Float,
    rayColor: Color,
    strokeWidth: Float,
    phaseOffset: Float,
) {
    for (i in 0 until 8) {
        val angle = rotation + phaseOffset + i * TWO_PI_F / 8f
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = rayColor,
            start = Offset(cx + dx * innerR, cy + dy * innerR),
            end = Offset(cx + dx * outerR, cy + dy * outerR),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawNightSky(
    phase: () -> Float,
    art: AtmosphereArt,
) {
    val cx = size.width * 0.8f
    val cy = size.height * 0.2f
    val moonR = 26.dp.toPx()
    // 柔和光晕
    drawCircle(MoonHaloColor.copy(alpha = 0.05f), radius = moonR * 3.0f, center = Offset(cx, cy))
    drawCircle(MoonHaloColor.copy(alpha = 0.08f), radius = moonR * 2.2f, center = Offset(cx, cy))
    drawCircle(MoonHaloColor.copy(alpha = 0.12f), radius = moonR * 1.6f, center = Offset(cx, cy))
    val moon = art.moon
    if (moon != null) {
        drawPath(moon, color = MoonColor)
    }
    drawStars(phase)
}

private fun DrawScope.drawStars(phase: () -> Float) {
    val p = phase()
    for (i in 0 until STAR_COUNT) {
        val sx = size.width * ((i * 37 + 11) % 100) / 100f
        val sy = size.height * ((i * 53 + 7) % 55) / 100f
        val base = 0.3f + 0.5f * ((i * 29 + 13) % 10) / 9f
        val twinkle = 0.65f + 0.35f * sin(p * TWO_PI_F * 2f + i * 1.7f)
        val alpha = (base * twinkle).coerceIn(0.08f, 1f)
        val big = i % 5 == 2
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = if (big) 2.2f.dp.toPx() else 1.4f.dp.toPx(),
            center = Offset(sx, sy),
        )
        if (big) {
            // 3-4 颗大星加十字星芒
            val ray = 6.dp.toPx()
            val rayColor = Color.White.copy(alpha = alpha * 0.6f)
            drawLine(rayColor, Offset(sx - ray, sy), Offset(sx + ray, sy), strokeWidth = 1f.dp.toPx(), cap = StrokeCap.Round)
            drawLine(rayColor, Offset(sx, sy - ray), Offset(sx, sy + ray), strokeWidth = 1f.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

// —— 雷暴：确定性分叉闪电 + 云底径向辉光 + 环境光渐变 ——

private fun DrawScope.drawThunderFlash(
    art: AtmosphereArt,
    lightning: LightningPaths,
    flash: () -> Float,
    cycle: () -> Float,
) {
    // 保留原有「闪电可见窗口」节奏：flash 周期内 70%-80% 可见
    val window = (flash() * 10f).toInt() % 10
    if (window != 7 && window != 8) return
    val intensity = if (window == 7) 0.45f else 0.9f
    // 云底径向辉光：同心圆叠加
    val glowX = size.width * 0.55f
    val glowY = size.height * 0.12f
    drawCircle(BoltColor.copy(alpha = 0.08f * intensity), radius = 92.dp.toPx(), center = Offset(glowX, glowY))
    drawCircle(BoltColor.copy(alpha = 0.14f * intensity), radius = 62.dp.toPx(), center = Offset(glowX, glowY))
    drawCircle(SunHaloColor.copy(alpha = 0.22f * intensity), radius = 36.dp.toPx(), center = Offset(glowX, glowY))
    // 环境光：缓存渐变 Brush，只按闪烁强度调 alpha
    art.stormLight?.let { brush -> drawRect(brush = brush, alpha = intensity * 0.4f) }
    // 每轮（3.2s）不同子种子，形态/位置变化
    val seed = (cycle() * 20f).toInt() % 20
    drawLightning(art, lightning, seed, intensity)
}

private fun DrawScope.drawLightning(
    art: AtmosphereArt,
    lightning: LightningPaths,
    seed: Int,
    intensity: Float,
) {
    val stroke = art.boltStroke ?: return
    if (lightning.seed != seed) {
        rebuildLightning(lightning, seed, size)
        lightning.seed = seed
    }
    val alpha = (0.7f + 0.3f * intensity).coerceAtMost(1f)
    drawPath(lightning.main, color = BoltColor.copy(alpha = alpha), style = stroke)
    drawPath(lightning.branch, color = BoltColor.copy(alpha = alpha * 0.7f), style = stroke)
}

/** 主 Bolt 折线 + 1-2 条分支，均由 seed 确定性生成；仅在轮次变化时重建。 */
private fun rebuildLightning(
    lightning: LightningPaths,
    seed: Int,
    size: Size,
) {
    val random = Random(seed * 7919 + 101)
    val startX = size.width * (0.32f + random.nextFloat() * 0.36f)
    val startY = size.height * 0.05f
    val endY = size.height * (0.46f + random.nextFloat() * 0.18f)
    val segments = 5
    val segmentHeight = (endY - startY) / segments
    lightning.main.reset()
    lightning.main.moveTo(startX, startY)
    var x = startX
    var y = startY
    repeat(segments) {
        x += (random.nextFloat() - 0.5f) * size.width * 0.1f
        y += segmentHeight * (0.75f + random.nextFloat() * 0.5f)
        lightning.main.lineTo(x, y)
    }
    lightning.branch.reset()
    val branches = 1 + random.nextInt(2)
    repeat(branches) {
        val branchX = size.width * (0.30f + random.nextFloat() * 0.40f)
        val branchY = size.height * (0.14f + random.nextFloat() * 0.20f)
        val direction = if (random.nextBoolean()) 1f else -1f
        val length = size.height * (0.12f + random.nextFloat() * 0.12f)
        lightning.branch.moveTo(branchX, branchY)
        lightning.branch.lineTo(branchX + direction * length * 0.42f, branchY + length * 0.55f)
        lightning.branch.lineTo(branchX + direction * length * 0.80f, branchY + length)
    }
}

// —— 粒子：分层生成与层参数（纯函数，可单测）——

internal data class Particle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val drift: Float,
    val layer: Int,
    val wobblePhase: Float,
    val lenJitter: Float,
)

/** 每场景三层粒子数（远/中/近），生成顺序即 0→1→2。 */
internal fun atmosphereParticleLayers(scene: WeatherScene): List<Int> =
    when (scene) {
        WeatherScene.DRIZZLE -> listOf(14, 10, 6)
        WeatherScene.RAIN -> listOf(20, 22, 12)
        WeatherScene.HEAVY_RAIN, WeatherScene.THUNDER -> listOf(30, 34, 20)
        WeatherScene.HAIL -> listOf(22, 24, 14)
        WeatherScene.FREEZING_RAIN -> listOf(22, 24, 12)
        WeatherScene.SNOW -> listOf(16, 16, 10)
        WeatherScene.HEAVY_SNOW -> listOf(26, 30, 20)
        else -> emptyList()
    }

internal fun atmosphereParticles(
    scene: WeatherScene,
    seed: Int,
): List<Particle> {
    val layerCounts = atmosphereParticleLayers(scene)
    if (layerCounts.isEmpty()) return emptyList()
    val random = Random(seed)
    val particles = ArrayList<Particle>(layerCounts.sum())
    layerCounts.forEachIndexed { layer, count ->
        repeat(count) {
            particles.add(
                Particle(
                    x = random.nextFloat(),
                    y = random.nextFloat(),
                    speed = 0.35f + random.nextFloat() * 0.9f,
                    size = 1.5f + random.nextFloat() * 3.5f,
                    drift = -0.15f + random.nextFloat() * 0.3f,
                    layer = layer,
                    wobblePhase = random.nextFloat() * TWO_PI_F,
                    lenJitter = -0.25f + random.nextFloat() * 0.5f,
                ),
            )
        }
    }
    return particles
}

internal fun particleLayerSpeedMultiplier(layer: Int): Float =
    when (layer) {
        PARTICLE_LAYER_FAR -> 0.7f
        PARTICLE_LAYER_NEAR -> 1.4f
        else -> 1f
    }

internal fun particleLayerSizeMultiplier(layer: Int): Float =
    when (layer) {
        PARTICLE_LAYER_FAR -> 0.6f
        PARTICLE_LAYER_NEAR -> 1.35f
        else -> 1f
    }

internal fun particleLayerAlphaMultiplier(layer: Int): Float =
    when (layer) {
        PARTICLE_LAYER_FAR -> 0.55f
        PARTICLE_LAYER_NEAR -> 1.1f
        else -> 1f
    }

/** 负值安全取模：修复 `%` 对负数返回负值导致雨滴横向跳变的问题。 */
internal fun positiveModulo(
    value: Float,
    modulus: Float,
): Float {
    if (modulus == 0f) return 0f
    val remainder = value % modulus
    return if (remainder < 0f) remainder + modulus else remainder
}

/** 各降水场景统一风倾角（水平位移 / 下落长度）。 */
internal fun rainWindSlope(scene: WeatherScene): Float =
    when (scene) {
        WeatherScene.DRIZZLE -> 0.14f
        WeatherScene.RAIN -> 0.20f
        WeatherScene.HEAVY_RAIN, WeatherScene.THUNDER -> 0.26f
        WeatherScene.FREEZING_RAIN -> 0.10f
        else -> 0f
    }
