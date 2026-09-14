package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import kotlin.math.hypot
import kotlin.math.sin

private val iceSpark = Color(0xFFB3E5FC)
private val confettiColors =
    listOf(
        Color(0xFFFFC46B),
        Color(0xFF7FE3F2),
        Color.White,
        Color(0xFFFF8A65),
    )

private const val SERVE_READY_SECONDS = 1.5f
private const val SERVE_BEAT_SECONDS = 0.5f

// ---------------------------------------------------------------------
// 特效：火花 / 冰屑 / 进球光效 / 结算庆祝
// ---------------------------------------------------------------------

internal fun DrawScope.drawHockeySparks(game: HockeyGame) {
    val sparks = game.sparks
    if (sparks.isEmpty()) return
    for (i in sparks.indices) {
        val s = sparks[i]
        val a = (s.life / 0.5f).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        if (s.color == iceSpark) {
            val sp = hypot(s.vx, s.vy).coerceAtLeast(1f)
            val ux = s.vx / sp
            val uy = s.vy / sp
            val len = 2.2f + 3.6f * a
            drawLine(
                Color.White.copy(alpha = 0.30f * a),
                Offset(s.x - ux * len * 2.4f, s.y - uy * len * 2.4f),
                Offset(s.x, s.y),
                strokeWidth = 1.2f,
                cap = StrokeCap.Round,
            )
            drawIceShard(s.x, s.y, ux, uy, len, a)
        } else {
            drawCircle(s.color.copy(alpha = 0.16f * a), 6.5f * (0.6f + a * 0.6f), Offset(s.x, s.y))
            drawCircle(s.color.copy(alpha = 0.90f * a), 2.4f * (0.5f + a), Offset(s.x, s.y))
        }
    }
}

private fun DrawScope.drawIceShard(
    x: Float,
    y: Float,
    ux: Float,
    uy: Float,
    len: Float,
    alpha: Float,
) {
    val px = -uy
    val py = ux
    val path = HockeyRenderCache.shard
    path.reset()
    path.moveTo(x + ux * len, y + uy * len)
    path.lineTo(x + px * len * 0.62f, y + py * len * 0.62f)
    path.lineTo(x - ux * len, y - uy * len)
    path.lineTo(x - px * len * 0.62f, y - py * len * 0.62f)
    path.close()
    drawPath(path, Color.White.copy(alpha = 0.72f * alpha))
}

internal fun DrawScope.drawGoalLight(game: HockeyGame) {
    val flash = game.scoreFlash
    if (flash <= 0.01f) return
    val t = game.table ?: return
    val scorer = game.lastScorer ?: return
    val team = if (scorer == Scorer.PLAYER) playerGoal else aiGoal
    val y = if (scorer == Scorer.PLAYER) t.top else t.bottom
    val goalHalf = t.goalWidth * 0.5f
    drawCircle(team.copy(alpha = 0.34f * flash), goalHalf * 1.45f, Offset(t.midX, y))
    drawCircle(Color.White.copy(alpha = 0.20f * flash), goalHalf * 0.85f, Offset(t.midX, y))
    val k = 1f - flash
    drawCircle(
        team.copy(alpha = 0.45f * flash),
        goalHalf * (1.05f + 0.95f * k),
        Offset(t.midX, y),
        style = strokeOf(goalHalf * 0.06f),
    )
    drawCircle(
        Color.White.copy(alpha = 0.30f * flash),
        goalHalf * (0.70f + 0.55f * k),
        Offset(t.midX, y),
        style = strokeOf(goalHalf * 0.03f),
    )
}

internal fun DrawScope.drawWinnerConfetti(
    game: HockeyGame,
    time: Float,
) {
    val t = game.table ?: return
    val short = t.shortSide
    val span = t.height + short * 0.16f
    for (i in 0 until 30) {
        val h1 = hockeyHash01(i * 7 + 3)
        val h2 = hockeyHash01(i * 11 + 5)
        val speed = short * (0.10f + h1 * 0.20f)
        val y = t.bottom - ((time * speed + h2 * span) % span)
        val sway = sin(time * (0.8f + h2 * 0.8f) + i) * short * 0.012f
        val x = t.left + h1 * t.width + sway
        val alpha = 0.32f + 0.50f * h2
        val radius = short * (0.005f + 0.007f * h2)
        drawCircle(confettiColors[i % confettiColors.size].copy(alpha = alpha), radius, Offset(x, y))
    }
}

// ---------------------------------------------------------------------
// 发球提示
// ---------------------------------------------------------------------

internal fun DrawScope.drawServeHint(
    game: HockeyGame,
    t: HockeyLogic.Table,
    time: Float,
) {
    val p = game.puck
    val r = p.r
    if (r < 0.5f) return
    val pulse = 0.5f + 0.5f * sin(time * 6f)
    drawCircle(Color.White.copy(alpha = 0.08f + 0.09f * pulse), r * 2.5f, Offset(p.x, p.y))
    if (game.serveTimer > SERVE_READY_SECONDS) {
        drawCircle(
            HockeyUi.accentCool.copy(alpha = 0.35f),
            r * 1.55f,
            Offset(p.x, p.y),
            style = strokeOf(r * 0.16f),
        )
    } else {
        val frac = ((game.serveTimer / SERVE_BEAT_SECONDS) % 1f).coerceIn(0f, 1f)
        drawArc(
            color = HockeyUi.accentCool.copy(alpha = 0.85f),
            startAngle = -90f,
            sweepAngle = 360f * frac,
            useCenter = false,
            topLeft = Offset(p.x - r * 1.55f, p.y - r * 1.55f),
            size = Size(r * 3.10f, r * 3.10f),
            style = strokeOf(r * 0.16f),
        )
        drawCircle(
            HockeyUi.accentCool.copy(alpha = 0.18f),
            r * 1.55f,
            Offset(p.x, p.y),
            style = strokeOf(r * 0.05f),
        )
    }
    game.serveTowardPlayer?.let { drawServeArrow(t, it, time) }
}

private fun DrawScope.drawServeArrow(
    t: HockeyLogic.Table,
    towardPlayer: Boolean,
    time: Float,
) {
    val x = t.midX
    val fromY = if (towardPlayer) t.midY + t.height * 0.10f else t.midY - t.height * 0.10f
    val toY = if (towardPlayer) t.midY + t.height * 0.26f else t.midY - t.height * 0.26f
    val color = if (towardPlayer) playerGoal else aiGoal
    val w = t.shortSide * 0.05f
    val tip = if (towardPlayer) t.shortSide * 0.06f else -t.shortSide * 0.06f
    val pulse = 0.72f + 0.28f * sin(time * 4f)
    drawLine(
        color.copy(alpha = 0.55f * pulse),
        Offset(x, fromY),
        Offset(x, toY),
        strokeWidth = t.shortSide * 0.018f,
        cap = StrokeCap.Round,
    )
    val path = HockeyRenderCache.shape
    path.reset()
    path.moveTo(x, toY)
    path.lineTo(x - w, toY - tip)
    path.lineTo(x + w, toY - tip)
    path.close()
    drawPath(path, color.copy(alpha = 0.85f * pulse))
}

// ---------------------------------------------------------------------
// 天气氛围（冰场外）
// ---------------------------------------------------------------------

internal fun DrawScope.drawHockeyAmbient(
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    isDay: Boolean,
    time: Float,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    if (freezingRain) {
        drawFreezingRain(time, 1f, w, h, splash = false, reduceMotion = reduceMotion)
        return
    }
    if (hail) {
        drawRainLayered(
            fgCount = 16,
            bgCount = 8,
            time = time,
            width = w,
            height = h,
            scale = 1f,
            lenMul = 1.05f,
            alpha = 0.42f,
            splash = false,
            reduceMotion = reduceMotion,
        )
        drawHailPellets(time, 1f, w, h, count = 40, reduceMotion = reduceMotion)
        drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        return
    }
    when (weather) {
        FarmWeather.RAINY ->
            when (rain) {
                RainStrength.LIGHT ->
                    drawRainLayered(
                        fgCount = 14,
                        bgCount = 7,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.38f,
                        splash = false,
                        reduceMotion = reduceMotion,
                    )
                RainStrength.MODERATE ->
                    drawRainLayered(
                        fgCount = 26,
                        bgCount = 13,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.48f,
                        splash = false,
                        reduceMotion = reduceMotion,
                    )
                RainStrength.HEAVY ->
                    drawRainLayered(
                        fgCount = 40,
                        bgCount = 20,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.58f,
                        splash = false,
                        reduceMotion = reduceMotion,
                    )
                RainStrength.NONE ->
                    drawRainLayered(
                        fgCount = 10,
                        bgCount = 5,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.32f,
                        splash = false,
                        reduceMotion = reduceMotion,
                    )
            }
        FarmWeather.STORMY -> {
            drawRainLayered(
                fgCount = 40,
                bgCount = 20,
                time = time,
                width = w,
                height = h,
                scale = 1f,
                alpha = 0.58f,
                splash = false,
                reduceMotion = reduceMotion,
            )
            drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        }
        FarmWeather.SNOWY -> drawHockeySnow(time, w, h)
        FarmWeather.FOGGY -> drawHockeyFog(time, w, h)
        FarmWeather.SUNNY -> drawHockeySunGlow(isDay, w, h)
        FarmWeather.CLOUDY -> drawHockeyClouds(time, w, h, isDay)
    }
}

private fun DrawScope.drawHockeySnow(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 44) {
        val phase = ((i * 67) % 29) / 29f
        val speed = 22f + phase * 30f
        val x = ((i * 83 + time * speed * 0.6f) % (w + 40f)) - 20f
        val y = ((i * 61 + time * speed) % (h + 40f)) - 20f
        val sway = sin(time * 1.2f + i) * 6f
        val r = 1.5f + (i % 4) * 0.7f
        drawCircle(Color.White.copy(alpha = 0.50f), r, Offset(x + sway, y))
    }
}

private fun DrawScope.drawHockeyFog(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 5) {
        val y = h * (0.14f + i * 0.17f) + sin(time * 0.4f + i * 1.3f) * 8f
        val x = ((i * 211f + time * 9f) % (w + 260f)) - 130f
        drawOval(
            Color.White.copy(alpha = 0.07f),
            topLeft = Offset(x - w * 0.42f, y - h * 0.05f),
            size = Size(w * 0.84f, h * 0.10f),
        )
    }
}

private fun DrawScope.drawHockeyClouds(
    time: Float,
    w: Float,
    h: Float,
    isDay: Boolean,
) {
    val cloud = if (isDay) Color.White else Color(0xFF93A9CE)
    for (i in 0 until 4) {
        val x = ((i * 210f + time * 10f) % (w + 180f)) - 90f
        val y = h * (0.04f + i * 0.03f)
        val a = 0.10f + i * 0.015f
        drawCircle(cloud.copy(alpha = a), w * 0.10f, Offset(x, y))
        drawCircle(cloud.copy(alpha = a), w * 0.08f, Offset(x + w * 0.07f, y + h * 0.008f))
        drawCircle(cloud.copy(alpha = a), w * 0.07f, Offset(x - w * 0.07f, y + h * 0.006f))
    }
}

private fun DrawScope.drawHockeySunGlow(
    isDay: Boolean,
    w: Float,
    h: Float,
) {
    if (!isDay) return
    val cx = w * 0.88f
    val cy = h * 0.05f
    // 多层圆叠出柔和辉光（避免径向 Brush 的逐帧分配）
    for (i in 0 until 5) {
        val r = w * (0.34f - i * 0.06f)
        drawCircle(Color(0xFFFFE082).copy(alpha = if (i == 4) 0.12f else 0.05f), r, Offset(cx, cy))
    }
}
