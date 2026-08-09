package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 飘分用的原生文字画笔，全程复用，只改 alpha / textSize。 */
private val floatTextPaint =
    Paint().apply {
        color = android.graphics.Color.rgb(255, 202, 40)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(120, 0, 0, 0))
    }

fun DrawScope.drawWorld(
    game: BearGame,
    contentColor: Color,
    isDay: Boolean,
    sprites: BearSprites,
) {
    val s = game.s

    drawSky(game, isDay, scene = game.scene)

    // 天气：雨/雪/雷暴压暗天空
    drawRunWeather(game)

    // 死亡震动只作用于世界层（天空除外）
    val shakeAmp = if (game.shakeT > 0f) (game.shakeT / 0.35f) * 9f * s else 0f
    withTransform({
        if (shakeAmp > 0f) {
            translate(
                (Random.nextFloat() * 2f - 1f) * shakeAmp,
                (Random.nextFloat() * 2f - 1f) * shakeAmp,
            )
        }
    }) {
        drawHills(game, contentColor, parallax = 0.08f, heightRatio = 0.11f, alpha = 0.07f)
        drawHills(game, contentColor, parallax = 0.18f, heightRatio = 0.075f, alpha = 0.12f)
        drawBgClouds(game)
        drawGround(game, contentColor)
        drawCoins(game)
        drawObstacles(game)
        drawParticles(game)
        drawFloatTexts(game)
        drawAnimatedBear(game, sprites)
    }

    // 雨/雪/闪电盖在最上层，看起来像真的在下
    drawRunPrecip(game)
}

// ---------------- 天空 ----------------

private fun DrawScope.drawSky(
    game: BearGame,
    isDay: Boolean,
    scene: RunScene,
) {
    val s = game.s
    val cx = size.width * 0.80f
    val cy = size.height * 0.15f
    // 阴天：灰云压顶、太阳隐去，只留一点暖光
    if (scene == RunScene.CLOUDY) {
        for (i in 0 until 4) {
            val x = ((i * 220f + game.time * 14f) % (size.width + 180f)) - 90f
            val y = size.height * (0.07f + i * 0.06f)
            drawCircle(Color(0xFF9AA7B5).copy(alpha = 0.55f), 34f * s, Offset(x, y))
            drawCircle(Color(0xFFAEB9C4).copy(alpha = 0.55f), 26f * s, Offset(x + 30f * s, y + 6f * s))
            drawCircle(Color(0xFFAEB9C4).copy(alpha = 0.55f), 24f * s, Offset(x - 28f * s, y + 8f * s))
        }
        drawCircle(Color(0xFFFFE082).copy(alpha = 0.30f), 24f * s, Offset(cx, cy))
        drawHorizonGlow(game.groundY, Color(0xFFDDE3E8), size.height * 0.12f)
        return
    }
    if (isDay) {
        if (scene == RunScene.SUNNY) {
            // 太阳：内芯 + 慢转光芒
            drawCircle(Color(0xFFFFE082), 30f * s, Offset(cx, cy), alpha = 0.95f)
            drawCircle(Color(0xFFFFF8E1), 22f * s, Offset(cx - 3f * s, cy - 3f * s))
            rotate(degrees = game.time * 6f, pivot = Offset(cx, cy)) {
                for (i in 0 until 12) {
                    val a = Math.PI.toFloat() * 2f * i / 12f
                    val inner = 38f * s
                    val outer = 48f * s
                    drawLine(
                        color = Color(0xFFFFE082),
                        start = Offset(cx + cos(a) * inner, cy + sin(a) * inner),
                        end = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                        strokeWidth = 3.5f * s,
                        cap = StrokeCap.Round,
                        alpha = 0.85f,
                    )
                }
            }
        }
    } else {
        // 星星：位置按黄金比例伪随机铺开，各自闪烁
        for (i in 0 until 14) {
            val fx = (i * 0.618034f + 0.11f) % 1f
            val fy = (i * 0.417f + 0.07f) % 1f
            val sx = size.width * fx
            val sy = size.height * 0.30f * fy + size.height * 0.03f
            val tw = 0.45f + 0.55f * abs(sin(game.time * 1.8f + i * 1.3f))
            drawCircle(Color.White, (1.6f + (i % 3) * 0.7f) * s, Offset(sx, sy), alpha = tw)
        }
        // 月亮 + 环形山
        drawCircle(Color(0xFFF5F3CE), 27f * s, Offset(cx, cy), alpha = 0.95f)
        drawCircle(Color(0xFFE8E3B0), 7f * s, Offset(cx - 8f * s, cy - 5f * s))
        drawCircle(Color(0xFFE8E3B0), 4.5f * s, Offset(cx + 9f * s, cy + 7f * s))
        drawCircle(Color(0xFFE8E3B0), 3.5f * s, Offset(cx + 2f * s, cy - 12f * s))
    }
    // 地平线光晕
    drawHorizonGlow(
        game.groundY,
        if (isDay && scene == RunScene.SUNNY) Color(0xFFFFE082) else Color(0xFFB0C4DE),
        size.height * 0.12f,
    )
}

// ---------------- 天气联动 ----------------

/** 跑酷天气氛围：按当前天气压暗天色（粒子与闪电在 [drawRunPrecip] 顶层绘制）。 */
private fun DrawScope.drawRunWeather(game: BearGame) {
    when (game.scene) {
        RunScene.SUNNY, RunScene.CLOUDY -> Unit
        RunScene.RAIN_LIGHT -> drawRect(Color(0xFF29364A).copy(alpha = 0.10f))
        RunScene.RAIN -> drawRect(Color(0xFF29364A).copy(alpha = 0.18f))
        RunScene.RAIN_HEAVY -> drawRect(Color(0xFF1F2A3A).copy(alpha = 0.26f))
        RunScene.SNOW -> drawRect(Color(0xFFB9C7D6).copy(alpha = 0.10f))
        RunScene.STORM -> drawRect(Color(0xFF151B26).copy(alpha = 0.32f))
    }
}

/** 跑酷降水层：雨/雪粒子 + 雷暴闪电，盖在世界之上；雨点密度随雨势。 */
private fun DrawScope.drawRunPrecip(game: BearGame) {
    val s = game.s
    when (game.scene) {
        RunScene.SUNNY, RunScene.CLOUDY -> Unit
        RunScene.RAIN_LIGHT -> drawRain(game, 20, lenMul = 0.8f)
        RunScene.RAIN -> drawRain(game, 44)
        RunScene.RAIN_HEAVY -> drawRain(game, 72, lenMul = 1.2f, alpha = 0.7f)
        RunScene.SNOW -> drawSnow(game, 32)
        RunScene.STORM -> {
            drawRain(game, 80, lenMul = 1.25f, alpha = 0.7f)
            if (sin(game.time * 6f) > 0.90f) {
                drawLine(
                    color = Color(0xFFFFF59D).copy(alpha = 0.85f),
                    start = Offset(size.width * 0.70f, 0f),
                    end = Offset(size.width * 0.58f, size.height * 0.18f),
                    strokeWidth = 2.5f * s,
                )
                drawLine(
                    color = Color(0xFFFFF59D).copy(alpha = 0.85f),
                    start = Offset(size.width * 0.62f, size.height * 0.06f),
                    end = Offset(size.width * 0.50f, size.height * 0.20f),
                    strokeWidth = 1.6f * s,
                )
            }
        }
    }
}

private fun DrawScope.drawRain(
    game: BearGame,
    count: Int,
    lenMul: Float = 1f,
    alpha: Float = 0.55f,
) {
    val s = game.s
    for (i in 0 until count) {
        val speed = 1100f * s + (i % 3) * 160f * s
        val x = ((i * 97 + game.time * (speed * 0.55f)) % (size.width + 60)) - 30
        val y = ((i * 53 + game.time * speed) % (size.height + 80)) - 40
        drawLine(
            color = Color(0xFFA9C6E8).copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x - 3f * s, y + 14f * s * lenMul),
            strokeWidth = 2f * s,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(
    game: BearGame,
    count: Int,
) {
    val s = game.s
    for (i in 0 until count) {
        val x = ((i * 83 + game.time * 24f + sin(game.time * 1.4f + i) * 12f) % (size.width + 30))
        val y = ((i * 61 + game.time * 50f) % (size.height + 30))
        drawCircle(Color.White.copy(alpha = 0.85f), (2f + i % 3) * s, Offset(x, y))
    }
}

// ---------------- 远景 ----------------

private fun DrawScope.drawHills(
    game: BearGame,
    contentColor: Color,
    parallax: Float,
    heightRatio: Float,
    alpha: Float,
) {
    val s = game.s
    val groundY = game.groundY
    val segW = 260f * s
    val hillH = size.height * heightRatio
    // 山丘带形状固定，缓存复用；每帧只做平移，避免每帧 new Path 引发 GC
    val cached = game.hillPathCache
    val path =
        if (cached != null && cached.first == heightRatio) {
            cached.second
        } else {
            Path()
                .apply {
                    // 覆盖足够宽的固定山丘带（4096*s 逻辑像素 > 任何手机屏幕）
                    val span = 4096f * s
                    var x = -segW
                    while (x < span + segW) {
                        moveTo(x, 0f)
                        quadraticTo(x + segW / 2, -hillH * 2, x + segW, 0f)
                        x += segW
                    }
                    lineTo(span, 0f)
                    lineTo(span, 4f * s)
                    lineTo(0f, 4f * s)
                    close()
                }.also { game.hillPathCache = heightRatio to it }
        }
    val offset = -(game.distance * parallax % segW)
    withTransform({
        translate(left = offset, top = groundY)
    }) {
        drawPath(path, contentColor, alpha = alpha)
    }
}

private fun DrawScope.drawBgClouds(game: BearGame) {
    val s = game.s
    for (c in game.clouds) {
        val cw = 110f * s * c.scale
        val ch = 34f * s * c.scale
        val col = Color.White.copy(alpha = if (c.layer < 0.2f) 0.35f else 0.55f)
        drawRoundRect(
            color = col,
            topLeft = Offset(c.x, c.y),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(ch / 2),
        )
        drawCircle(col, ch * 0.72f, Offset(c.x + cw * 0.32f, c.y + ch * 0.15f))
    }
}

// ---------------- 地面 ----------------

private fun DrawScope.drawGround(
    game: BearGame,
    contentColor: Color,
) {
    val s = game.s
    val groundY = game.groundY
    drawRect(
        color = contentColor.copy(alpha = 0.10f),
        topLeft = Offset(0f, groundY),
        size = Size(size.width, size.height - groundY),
    )
    drawLine(
        color = contentColor.copy(alpha = 0.45f),
        start = Offset(0f, groundY),
        end = Offset(size.width, groundY),
        strokeWidth = 2f * s,
    )
    // 速度刻度
    var dashX = -(game.distance % (90f * s))
    while (dashX < size.width) {
        drawRoundRect(
            color = contentColor.copy(alpha = 0.22f),
            topLeft = Offset(dashX, groundY + 12f * s),
            size = Size(26f * s, 4f * s),
            cornerRadius = CornerRadius(2f * s),
        )
        dashX += 90f * s
    }
    // 草丛：每 120px 一簇，按段索引伪随机决定形态
    val tuftGap = 120f * s
    val baseIdx = kotlin.math.floor(game.distance / tuftGap).toInt()
    var tx = -(game.distance % tuftGap)
    var idx = baseIdx
    while (tx < size.width) {
        val seed = abs(idx * 73 % 10)
        if (seed < 5) {
            val bx = tx + (seed * 13f % 60f) * s
            val grassCol = contentColor.copy(alpha = 0.30f)
            val gh = (10f + seed * 1.5f) * s
            for (b in -1..1) {
                drawLine(
                    color = grassCol,
                    start = Offset(bx + b * 4f * s, groundY + 2f * s),
                    end = Offset(bx + b * 7f * s, groundY + 2f * s - gh),
                    strokeWidth = 2.2f * s,
                    cap = StrokeCap.Round,
                )
            }
        }
        tx += tuftGap
        idx++
    }
}

// ---------------- 金币 ----------------

private fun DrawScope.drawCoins(game: BearGame) {
    val s = game.s
    for (c in game.coins) {
        val cy = c.y + sin(game.time * 3f + c.x * 0.02f) * 3f * s
        val center = Offset(c.x, cy)
        drawCircle(Color(0xFFFFCA28), c.r, center)
        drawCircle(Color(0xFFFFF3C4), c.r * 0.62f, center + Offset(-c.r * 0.12f, -c.r * 0.12f))
        // 旋转高光
        rotate(degrees = (game.time * 300f) % 360f, pivot = center) {
            drawCircle(
                Color.White.copy(alpha = 0.9f),
                c.r * 0.16f,
                center + Offset(0f, -c.r * 0.4f),
            )
        }
        // 每隔一颗画十字星闪
        if ((c.x / (34f * s)).toInt() % 2 == 0) {
            val tw = abs(sin(game.time * 4f + c.x)) * c.r * 0.5f
            drawLine(
                Color.White.copy(alpha = 0.85f),
                center + Offset(-tw, 0f),
                center + Offset(tw, 0f),
                strokeWidth = 1.6f * s,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White.copy(alpha = 0.85f),
                center + Offset(0f, -tw),
                center + Offset(0f, tw),
                strokeWidth = 1.6f * s,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------- 障碍 ----------------

private fun DrawScope.drawObstacles(game: BearGame) {
    val s = game.s
    val groundY = game.groundY
    for (ob in game.obstacles) {
        when (ob.type) {
            ObstacleType.PUDDLE -> {
                drawOval(
                    color = Color(0xFF0288D1),
                    topLeft = Offset(ob.x, groundY - ob.h),
                    size = Size(ob.w, ob.h),
                )
                drawOval(
                    color = Color(0xFF4FC3F7),
                    topLeft = Offset(ob.x + ob.w * 0.14f, groundY - ob.h * 0.78f),
                    size = Size(ob.w * 0.55f, ob.h * 0.45f),
                )
                // 涟漪两圈
                drawOval(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(ob.x + ob.w * 0.20f, groundY - ob.h * 0.62f),
                    size = Size(ob.w * 0.26f, ob.h * 0.30f),
                    style = Stroke(width = 1.4f * s),
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(ob.x + ob.w * 0.52f, groundY - ob.h * 0.55f),
                    size = Size(ob.w * 0.20f, ob.h * 0.24f),
                    style = Stroke(width = 1.2f * s),
                )
            }
            ObstacleType.SNOWMAN -> {
                val bodyR = ob.w * 0.5f
                val headR = ob.w * 0.34f
                val bodyC = Offset(ob.x + ob.w / 2, groundY - bodyR)
                val headC = Offset(ob.x + ob.w / 2, groundY - bodyR * 2 - headR * 0.7f)
                // 影子
                drawOval(
                    color = Color(0xFF37474F).copy(alpha = 0.18f),
                    topLeft = Offset(ob.x - ob.w * 0.1f, groundY - ob.h * 0.06f),
                    size = Size(ob.w * 1.2f, ob.h * 0.10f),
                )
                drawCircle(Color.White, bodyR, bodyC)
                drawCircle(Color.White, headR, headC)
                // 树枝手臂
                drawLine(
                    Color(0xFF8D6E63),
                    bodyC + Offset(-bodyR * 0.8f, -bodyR * 0.2f),
                    bodyC + Offset(-bodyR * 1.5f, -bodyR * 0.9f),
                    strokeWidth = 3f * s,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color(0xFF8D6E63),
                    bodyC + Offset(bodyR * 0.8f, -bodyR * 0.2f),
                    bodyC + Offset(bodyR * 1.5f, -bodyR * 0.9f),
                    strokeWidth = 3f * s,
                    cap = StrokeCap.Round,
                )
                // 围巾：颈圈 + 垂下来的尾巴
                drawRoundRect(
                    Color(0xFFE53935),
                    topLeft = Offset(headC.x - headR * 0.95f, headC.y + headR * 0.42f),
                    size = Size(headR * 1.9f, headR * 0.42f),
                    cornerRadius = CornerRadius(headR * 0.2f),
                )
                drawRoundRect(
                    Color(0xFFC62828),
                    topLeft = Offset(headC.x + headR * 0.25f, headC.y + headR * 0.7f),
                    size = Size(headR * 0.5f, headR * 0.85f),
                    cornerRadius = CornerRadius(headR * 0.18f),
                )
                // 煤球纽扣
                drawCircle(Color(0xFF37474F), bodyR * 0.11f, bodyC + Offset(0f, -bodyR * 0.25f))
                drawCircle(Color(0xFF37474F), bodyR * 0.11f, bodyC + Offset(0f, bodyR * 0.25f))
                // 眼 + 胡萝卜鼻 + 微笑
                drawCircle(Color(0xFF263238), headR * 0.14f, headC + Offset(-headR * 0.34f, -headR * 0.12f))
                drawCircle(Color(0xFF263238), headR * 0.14f, headC + Offset(headR * 0.34f, -headR * 0.12f))
                drawCircle(Color(0xFFFF7043), headR * 0.16f, headC + Offset(0f, headR * 0.22f))
                drawArc(
                    color = Color(0xFF37474F),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(headC.x - headR * 0.4f, headC.y + headR * 0.1f),
                    size = Size(headR * 0.8f, headR * 0.5f),
                    style = Stroke(width = 1.6f * s, cap = StrokeCap.Round),
                )
            }
            ObstacleType.STORM_CLOUD -> {
                val top = groundY - ob.h - 4f * s
                // 云下雨丝
                for (i in 0..2) {
                    val rx = ob.x + ob.w * (0.22f + 0.28f * i)
                    val prog = (game.time * 1.7f + i * 0.33f) % 1f
                    val y0 = top + ob.h * 0.8f
                    val ry = y0 + prog * (groundY - y0 - 4f * s)
                    drawLine(
                        color = Color(0xFF90CAF9).copy(alpha = 1f - prog),
                        start = Offset(rx, ry),
                        end = Offset(rx - 2f * s, ry + 8f * s),
                        strokeWidth = 2.4f * s,
                        cap = StrokeCap.Round,
                    )
                }
                drawRoundRect(
                    color = Color(0xFF546E7A),
                    topLeft = Offset(ob.x, top + ob.h * 0.25f),
                    size = Size(ob.w, ob.h * 0.55f),
                    cornerRadius = CornerRadius(ob.h * 0.27f),
                )
                drawCircle(Color(0xFF78909C), ob.h * 0.33f, Offset(ob.x + ob.w * 0.3f, top + ob.h * 0.3f))
                drawCircle(Color(0xFF78909C), ob.h * 0.4f, Offset(ob.x + ob.w * 0.62f, top + ob.h * 0.26f))
                // 怒目
                drawLine(
                    Color(0xFF263238),
                    Offset(ob.x + ob.w * 0.26f, top + ob.h * 0.42f),
                    Offset(ob.x + ob.w * 0.40f, top + ob.h * 0.52f),
                    strokeWidth = 2.4f * s,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color(0xFF263238),
                    Offset(ob.x + ob.w * 0.74f, top + ob.h * 0.42f),
                    Offset(ob.x + ob.w * 0.60f, top + ob.h * 0.52f),
                    strokeWidth = 2.4f * s,
                    cap = StrokeCap.Round,
                )
                // 闪电
                val bolt =
                    Path().apply {
                        val bx = ob.x + ob.w * 0.5f
                        val by = top + ob.h * 0.72f
                        moveTo(bx + 6f * s, by)
                        lineTo(bx - 8f * s, by + 16f * s)
                        lineTo(bx, by + 16f * s)
                        lineTo(bx - 6f * s, by + 30f * s)
                        lineTo(bx + 8f * s, by + 12f * s)
                        lineTo(bx + 1f * s, by + 12f * s)
                        close()
                    }
                drawPath(bolt, Color(0xFFFFD54F))
            }
        }
    }
}

// ---------------- 粒子 / 飘分 ----------------

private fun DrawScope.drawParticles(game: BearGame) {
    val s = game.s
    for (p in game.particles) {
        val k = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(
            color = p.color,
            radius = 5f * s * (k * 0.6f + 0.4f),
            center = Offset(p.x, p.y),
            alpha = k,
        )
    }
}

private fun DrawScope.drawFloatTexts(game: BearGame) {
    floatTextPaint.textSize = 15f * game.s
    for (t in game.texts) {
        floatTextPaint.alpha = ((t.life / 0.8f) * 255).toInt().coerceIn(0, 255)
        drawContext.canvas.nativeCanvas.drawText(t.text, t.x, t.y, floatTextPaint)
    }
}

// ---------------- 小熊 ----------------

/**
 * 跑酷小熊姿态计算：跑步=帧动画（步频切帧）+ 起伏摇滚；起跳=跳帧 + 前倾拉伸；
 * 落地=挤压回弹；二段跳=前空翻；死亡=翻滚淡出 + 眩晕星星。绘制走 [drawGameBear]。
 */
private fun DrawScope.drawAnimatedBear(
    game: BearGame,
    sprites: BearSprites,
) {
    val s = game.s
    val bs = game.bearSize
    val cx = game.bearX + bs / 2
    var cy = game.bearY + bs / 2 + game.bob
    val dead = game.phase == GamePhase.OVER
    var alpha = 1f
    var tumble = 0f
    if (dead) {
        val t = game.deadT
        val k = (t / 0.5f).coerceIn(0f, 1f)
        val ease = 1f - (1f - k) * (1f - k) * (1f - k)
        tumble = -95f * ease
        if (t > 0.35f) cy += (t - 0.35f) * 260f * s
        if (t > 0.55f) alpha = (1f - (t - 0.55f) / 0.55f).coerceIn(0f, 1f)
        if (alpha <= 0f) return
    }
    val airborne = !game.grounded && !dead
    val tilt =
        if (airborne) {
            (game.bearVY / (1400f * s)).coerceIn(-1f, 1f) * 14f
        } else if (!dead) {
            sin(game.runPhase) * 3f // 跑步重心轻微摇摆
        } else {
            0f
        }
    // 二段跳前空翻：0.55s 转满一圈
    val flip =
        if (game.flipT >= 0f) {
            val k = (game.flipT / 0.55f).coerceIn(0f, 1f)
            val e = if (k < 0.5f) 2f * k * k else 1f - (-2f * k + 2f) * (-2f * k + 2f) / 2f
            e * 360f
        } else {
            0f
        }
    // 空中拉伸 / 落地挤压
    val stretch =
        if (airborne && game.flipT < 0f) {
            1f + min(abs(game.bearVY) / (2200f * s), 0.13f)
        } else {
            1f
        }
    val squash =
        if (game.squashT in 0f..0.16f) {
            val k = game.squashT / 0.16f
            0.80f + 0.20f * (1f - (1f - k) * (1f - k))
        } else {
            1f
        }
    // 帧选择：上升跳帧、下落下落帧、落地触地帧、地面六拍跑步循环
    val frame =
        when {
            dead -> BearFrame.FALL
            airborne && game.bearVY < 0f -> BearFrame.JUMP
            airborne -> BearFrame.FALL
            game.squashT in 0f..0.16f -> BearFrame.LAND
            else -> runCycleFrame(game.runPhase)
        }

    // 地面软阴影：跳得越高越淡越小
    if (!dead) {
        val hFrac = ((game.groundY - (game.bearY + bs)) / (280f * s)).coerceIn(0f, 1f)
        drawOval(
            color = Color(0xFF1A2A12),
            topLeft =
                Offset(
                    cx - bs * 0.42f * (1f - 0.35f * hFrac),
                    game.groundY - bs * 0.085f * (1f - 0.35f * hFrac),
                ),
            size =
                Size(
                    bs * 0.84f * (1f - 0.35f * hFrac),
                    bs * 0.17f * (1f - 0.35f * hFrac),
                ),
            alpha = 0.16f * (1f - 0.6f * hFrac),
        )
    }

    drawGameBear(
        sprites = sprites,
        centerX = cx,
        centerY = cy,
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                dead = dead,
                frame = frame,
                bodyTilt = tilt + tumble + flip,
                scaleX = (2f - stretch) * (1f + (1f - squash) * 0.8f),
                scaleY = stretch * squash,
                alpha = alpha,
            ),
    )
}
