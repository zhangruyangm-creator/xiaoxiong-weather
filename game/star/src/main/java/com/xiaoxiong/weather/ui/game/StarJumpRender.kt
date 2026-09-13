package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

internal val jumpTextPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

// ---------------- 世界绘制 ----------------

/** 单位五角星（半径 1），绘制时 scale 复用，避免每帧 new Path。 */
private val unitStarPath: Path =
    Path().apply {
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) 1f else 0.45f
            val a = -Math.PI.toFloat() / 2f + i * Math.PI.toFloat() / 5f
            val x = kotlin.math.cos(a) * rr
            val y = sin(a) * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** 碎裂平台两瓣的方向，避免每帧 listOf 分配。 */
private val breakSides = floatArrayOf(-1f, 1f)

/** 弹簧彩虹拱色带，避免每帧 listOf 分配。 */
private val springBandColors =
    longArrayOf(0xFFFF5252, 0xFFFFB300, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3)

/** 地面草坡渐变与位置/时间无关，缓存复用。 */
private val jumpGroundBrush =
    Brush.verticalGradient(listOf(Color(0xFF8BC34A), Color(0xFF558B2F)))

/**
 * HUD 顶部暗角：全强度覆盖 HUD 后向下渐隐；参数见 StarSkyPalette.kt。
 */
private val jumpHudScrimBrush =
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = JUMP_HUD_SCRIM_ALPHA),
        JUMP_HUD_SCRIM_PLATEAU to Color.Black.copy(alpha = JUMP_HUD_SCRIM_ALPHA),
        1f to Color.Transparent,
    )

/**
 * 天空渐变缓存：颜色/天气压暗系数不变时复用；爬升期间颜色逐帧变化会自然重建，
 * 悬停/下落/结算等静止帧则命中，避免每帧 new Brush + list。
 */
private class JumpSkyBrushCache {
    private var top: Color = Color.Unspecified
    private var bottom: Color = Color.Unspecified
    private var dim = Float.NaN
    private var brush: Brush? = null

    fun get(
        top: Color,
        bottom: Color,
        dim: Float,
    ): Brush {
        val cached = brush
        if (cached != null && top == this.top && bottom == this.bottom && dim == this.dim) {
            return cached
        }
        return Brush
            .verticalGradient(
                listOf(
                    top.copy(red = top.red * dim, green = top.green * dim, blue = top.blue * dim),
                    bottom.copy(red = bottom.red * dim, green = bottom.green * dim, blue = bottom.blue * dim),
                ),
            ).also {
                brush = it
                this.top = top
                this.bottom = bottom
                this.dim = dim
            }
    }
}

private val jumpSkyBrushCache = JumpSkyBrushCache()

/** 太阳径向渐变：以原点为中心缓存，绘制时平移到太阳位置。 */
private class JumpSunBrushCache {
    private var radius = Float.NaN
    private var brush: Brush? = null

    fun get(radius: Float): Brush {
        val cached = brush
        if (cached != null && this.radius == radius) return cached
        return Brush
            .radialGradient(
                listOf(Color(0xFFFFE082), Color(0xFFFFB300)),
                center = Offset.Zero,
                radius = radius,
            ).also {
                brush = it
                this.radius = radius
            }
    }
}

private val jumpSunBrushCache = JumpSunBrushCache()

internal fun DrawScope.drawJumpWorld(
    game: StarJumpGame,
    sprites: BearSprites,
    /** 减弱动画：跳过雨雪/雷电粒子，只保留静态天空。 */
    reduceMotion: Boolean = false,
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
    drawRect(jumpSkyBrushCache.get(skyTop, skyBottom, weatherDim))
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
        translate(sunC.x, sunC.y) {
            drawCircle(
                jumpSunBrushCache.get(30f * s),
                radius = 30f * s,
                center = Offset.Zero,
                alpha = sunA,
            )
        }
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
    drawJumpWeatherParticles(game, scene, reduceMotion)

    // HUD 暗角：天空/云层/粒子之上、地面与平台等内容之下，保证顶部白字可读
    drawRect(
        jumpHudScrimBrush,
        Offset.Zero,
        Size(w, h * JUMP_HUD_SCRIM_BAND),
    )

    // 地面（起点附近才可见）
    val groundTop = -cam
    if (groundTop < h + 40f * s) {
        drawRect(
            jumpGroundBrush,
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
            rotate(sin(game.time + st.seed) * 10f, pivot = Offset.Zero)
            scale(r, r, pivot = Offset.Zero)
        }) {
            drawPath(unitStarPath, Color(0xFFFFD54F))
            drawPath(
                unitStarPath,
                Color(0xFFFFF8E1),
                style = Stroke(width = (1.6f * s) / r.coerceAtLeast(0.1f)),
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
    reduceMotion: Boolean,
) {
    if (scene == JumpScene.SUNNY || scene == JumpScene.CLOUDY) return
    val w = game.w
    val h = game.h
    val s = game.s
    val cam = game.cameraY
    when (scene) {
        JumpScene.RAIN_LIGHT -> {
            drawRainLayered(
                fgCount = 20,
                bgCount = 12,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.05f,
                alpha = 0.52f,
                yOff = -cam * 0.12f,
                splash = false,
                reduceMotion = reduceMotion,
            )
        }
        JumpScene.RAIN -> {
            drawRainLayered(
                fgCount = 38,
                bgCount = 20,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.2f,
                alpha = 0.62f,
                yOff = -cam * 0.12f,
                splash = false,
                reduceMotion = reduceMotion,
            )
        }
        JumpScene.RAIN_HEAVY -> {
            drawRainLayered(
                fgCount = 56,
                bgCount = 28,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.4f,
                alpha = 0.74f,
                yOff = -cam * 0.12f,
                splash = false,
                reduceMotion = reduceMotion,
            )
        }
        JumpScene.STORM -> {
            drawRainLayered(
                fgCount = 42,
                bgCount = 22,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.35f,
                alpha = 0.7f,
                yOff = -cam * 0.12f,
                splash = false,
                reduceMotion = reduceMotion,
            )
            drawStormLightning(game.time, s, w, h, reduceMotion = reduceMotion)
        }
        JumpScene.FREEZING_RAIN -> {
            drawFreezingRain(game.time, s, w, h, yOff = -cam * 0.12f, splash = false, reduceMotion = reduceMotion)
        }
        JumpScene.HAIL -> {
            drawRainLayered(
                fgCount = 18,
                bgCount = 10,
                time = game.time,
                width = w,
                height = h,
                scale = s,
                lenMul = 1.15f,
                alpha = 0.55f,
                yOff = -cam * 0.12f,
                fgColor = Color(0xFFD8E6F8),
                splash = false,
                reduceMotion = reduceMotion,
            )
            drawHailPellets(game.time, s, w, h, yOff = -cam * 0.12f, count = 40, reduceMotion = reduceMotion)
            drawStormLightning(game.time, s, w, h, reduceMotion = reduceMotion)
        }
        JumpScene.SNOW -> {
            if (reduceMotion) return
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
        for (side in breakSides) {
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
            val cx = p.x + p.w / 2f
            springBandColors.forEachIndexed { i, c ->
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
    if (game.texts.isNotEmpty()) {
        // 字号随缩放走（原先在 setDims 里设置；移到绘制期，保持 setDims 纯 JVM 可测）
        jumpTextPaint.textSize = 15f * game.s
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
