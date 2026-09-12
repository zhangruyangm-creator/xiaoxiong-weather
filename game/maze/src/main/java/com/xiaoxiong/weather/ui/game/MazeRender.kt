package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------- 绘制 ----------------

internal fun DrawScope.drawMazeWorld(
    game: MazeGame,
    sprites: BearSprites,
    style: MazeThemeStyle,
) {
    val pad = 16f
    val hudTop = 110f
    val hudBottom = 200f
    val availW = size.width - pad * 2
    val availH = size.height - hudTop - hudBottom
    val cell = min(availW / game.cols, availH / game.rows)
    val boardW = cell * game.cols
    val boardH = cell * game.rows
    val ox = (size.width - boardW) / 2f
    val oy = hudTop + (availH - boardH) / 2f

    // 天空地平线光晕：位于棋盘上方天空带，让背景更立体
    val horizonY = oy - cell * 0.5f
    if (horizonY > 0f) {
        drawHorizonGlow(horizonY, midColor(style.skyBottom, Color.White), size.height * 0.16f)
    }

    // 主题氛围装饰
    drawThemeFx(game, style)

    // 棋盘阴影
    drawRoundRect(
        Color.Black.copy(alpha = 0.18f),
        Offset(ox + 4f, oy + 6f),
        Size(boardW, boardH),
        CornerRadius(12f),
    )

    for (r in 0 until game.rows) {
        for (c in 0 until game.cols) {
            val x = ox + c * cell
            val y = oy + r * cell
            drawCell(game, style, r, c, x, y, cell)
        }
    }

    // 小熊
    val (br, bc) = game.bearDrawPos()
    val bx = ox + (bc + 0.5f) * cell
    val by = oy + (br + 0.5f) * cell
    drawMazeBear(game, sprites, bx, by, cell)
}

/** 整局天气覆盖层：雨/雪/雷暴时叠加与当前天气一致的粒子与天色；雨点密度随雨势。 */
internal fun DrawScope.drawMazeWeather(
    game: MazeGame,
    weather: FarmWeather,
    rainStrength: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
) {
    // 冻雨/冰雹优先于普通雨/雷暴识别
    if (freezingRain) {
        drawRect(Color(0xFF5E7390).copy(alpha = 0.20f))
        drawFreezingRain(game.time, 1f, size.width, size.height)
        return
    }
    if (hail) {
        drawRect(Color(0xFF141B28).copy(alpha = 0.30f))
        // 冰雹：雨更少，突出冰雹粒子
        drawRainLayered(
            fgCount = 18,
            bgCount = 9,
            time = game.time,
            width = size.width,
            height = size.height,
            scale = 1f,
            lenMul = 1.1f,
            alpha = 0.55f,
            fgColor = Color(0xFFD8E6F8),
        )
        drawHailPellets(game.time, 1f, size.width, size.height, count = 44)
        drawStormLightning(game.time, 1f, size.width, size.height)
        return
    }
    when (weather) {
        FarmWeather.SUNNY, FarmWeather.CLOUDY, FarmWeather.FOGGY -> Unit
        FarmWeather.RAINY -> {
            drawRect(Color(0xFF29364A).copy(alpha = 0.12f))
            when (rainStrength) {
                RainStrength.LIGHT ->
                    drawRainLayered(
                        fgCount = 15,
                        bgCount = 7,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        alpha = 0.45f,
                    )
                RainStrength.HEAVY ->
                    drawRainLayered(
                        fgCount = 47,
                        bgCount = 23,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        lenMul = 1.15f,
                        alpha = 0.65f,
                    )
                else ->
                    drawRainLayered(
                        fgCount = 31,
                        bgCount = 15,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        alpha = 0.55f,
                    )
            }
        }
        FarmWeather.STORMY -> {
            drawRect(Color(0xFF151B26).copy(alpha = 0.28f))
            // 雷暴：以闪电为主，雨量收敛到中雨量级
            drawRainLayered(
                fgCount = 32,
                bgCount = 16,
                time = game.time,
                width = size.width,
                height = size.height,
                scale = 1f,
                lenMul = 1.25f,
                alpha = 0.6f,
            )
            if (sin(game.time * 6f) > 0.90f) {
                drawLine(
                    Color(0xFFFFF59D).copy(alpha = 0.85f),
                    Offset(size.width * 0.7f, 0f),
                    Offset(size.width * 0.58f, size.height * 0.16f),
                    strokeWidth = 3f,
                )
            }
        }
        FarmWeather.SNOWY -> {
            drawRect(Color(0xFFB9C7D6).copy(alpha = 0.08f))
            for (i in 0 until 34) {
                val x = ((i * 83 + game.time * 26) % (size.width + 20))
                val y = ((i * 61 + game.time * 48) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.8f), 2.5f + (i % 3), Offset(x, y))
            }
        }
    }
}

internal fun DrawScope.drawThemeFx(
    game: MazeGame,
    style: MazeThemeStyle,
) {
    when (game.level.theme) {
        MazeTheme.RAIN -> {
            drawRainLayered(
                fgCount = 27,
                bgCount = 13,
                time = game.time,
                width = size.width,
                height = size.height,
                scale = 1f,
                alpha = 0.4f,
                fgColor = Color.White,
                bgColor = Color.White,
            )
        }
        MazeTheme.SNOW -> {
            for (i in 0 until 35) {
                val x = ((i * 83 + game.time * 30) % (size.width + 20))
                val y = ((i * 61 + game.time * 50) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.7f), 2.5f + (i % 3), Offset(x, y))
            }
        }
        MazeTheme.NIGHT -> {
            for (i in 0 until 50) {
                val x = (i * 137) % size.width
                val y = (i * 89) % (size.height * 0.55f)
                val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(game.time * 2f + i))
                drawCircle(Color.White, 1.2f + (i % 3) * 0.4f, Offset(x, y), alpha = tw)
            }
        }
        MazeTheme.FESTIVAL -> {
            for (i in 0 until 8) {
                val x = size.width * (0.08f + i * 0.12f)
                val y = 80f + sin(game.time + i) * 6f
                drawCircle(Color(0xFFFF5252), 10f, Offset(x, y))
                drawLine(Color(0xFFFFD54F), Offset(x, y + 10f), Offset(x, y + 28f), strokeWidth = 2f)
            }
        }
        MazeTheme.CLOUD -> {
            for (i in 0 until 5) {
                val x = ((i * 180f + game.time * 18f) % (size.width + 160f)) - 80f
                val y = 70f + i * 28f
                drawCircle(Color.White.copy(alpha = 0.55f), 28f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.55f), 22f, Offset(x + 24f, y + 4f))
                drawCircle(Color.White.copy(alpha = 0.55f), 20f, Offset(x - 22f, y + 6f))
            }
        }
        MazeTheme.GARDEN -> {
            // 太阳
            val sun = Offset(size.width * 0.85f, 90f)
            drawCircle(Color(0xFFFFE082), 28f, sun)
            for (i in 0 until 10) {
                val a = Math.PI.toFloat() * 2f * i / 10f + game.time * 0.4f
                drawLine(
                    Color(0xFFFFD54F),
                    sun + Offset(cos(a) * 34f, sin(a) * 34f),
                    sun + Offset(cos(a) * 44f, sin(a) * 44f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

internal fun DrawScope.drawCell(
    game: MazeGame,
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val tile = game.grid[r][c]
    val pad = cell * 0.06f
    when (tile.tile) {
        MazeTile.WALL -> {
            // 墙体底部阴影（右下）
            drawRoundRect(
                Color.Black.copy(alpha = 0.22f),
                Offset(x + pad * 0.55f, y + pad * 0.55f),
                Size(cell - pad * 0.5f, cell - pad * 0.5f),
                CornerRadius(cell * 0.12f),
            )
            // 墙身
            drawRoundRect(
                style.wall,
                Offset(x + pad * 0.3f, y + pad * 0.3f),
                Size(cell - pad * 0.6f, cell - pad * 0.6f),
                CornerRadius(cell * 0.12f),
            )
            // 顶部受光面
            drawRoundRect(
                style.wallTop,
                Offset(x + pad, y + pad),
                Size(cell - pad * 2, cell * 0.34f),
                CornerRadius(cell * 0.08f),
            )
            // 砖缝：两条细横线压暗，做出立体分层
            drawLine(
                Color.Black.copy(alpha = 0.10f),
                Offset(x + pad * 1.6f, y + pad + cell * 0.52f),
                Offset(x + cell - pad * 1.6f, y + pad + cell * 0.52f),
                strokeWidth = 1.5f,
            )
            drawLine(
                Color.Black.copy(alpha = 0.07f),
                Offset(x + pad * 1.6f, y + pad + cell * 0.78f),
                Offset(x + cell - pad * 1.6f, y + pad + cell * 0.78f),
                strokeWidth = 1.5f,
            )
        }
        else -> {
            val floorCol = if ((r + c) % 2 == 0) style.floor else style.floorAlt
            drawRoundRect(
                floorCol,
                Offset(x + pad * 0.5f, y + pad * 0.5f),
                Size(cell - pad, cell - pad),
                CornerRadius(cell * 0.1f),
            )
            // 地板四周内阴影，增强格子边界立体感
            drawRoundRect(
                Color.Black.copy(alpha = 0.06f),
                Offset(x + pad * 0.5f, y + pad * 0.5f),
                Size(cell - pad, cell - pad),
                CornerRadius(cell * 0.1f),
                style = Stroke(width = 1.5f),
            )
            // 轻量纹理：左上高光小角
            drawCircle(
                Color.White.copy(alpha = 0.10f),
                cell * 0.08f,
                Offset(x + pad + cell * 0.16f, y + pad + cell * 0.18f),
            )
        }
    }
    val cx = x + cell / 2
    val cy = y + cell / 2
    when (tile.tile) {
        MazeTile.ICE -> {
            drawRoundRect(
                style.ice.copy(alpha = 0.85f),
                Offset(x + pad, y + pad),
                Size(cell - pad * 2, cell - pad * 2),
                CornerRadius(cell * 0.12f),
            )
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(cx - cell * 0.2f, cy - cell * 0.15f),
                Offset(cx - cell * 0.05f, cy - cell * 0.25f),
                strokeWidth = 2f,
            )
        }
        MazeTile.HONEY -> {
            // 蜜罐
            drawCircle(Color(0xFFFFB300), cell * 0.22f, Offset(cx, cy + cell * 0.02f))
            drawCircle(Color(0xFFFFD54F), cell * 0.14f, Offset(cx, cy - cell * 0.02f))
            drawRoundRect(
                Color(0xFF8D6E63),
                Offset(cx - cell * 0.12f, cy - cell * 0.22f),
                Size(cell * 0.24f, cell * 0.1f),
                CornerRadius(3f),
            )
            // 闪光
            val pulse = 0.7f + 0.3f * sin(game.time * 4f + r + c)
            drawCircle(Color.White.copy(alpha = 0.35f * pulse), cell * 0.08f, Offset(cx - cell * 0.06f, cy - cell * 0.06f))
        }
        MazeTile.KEY -> {
            drawCircle(Color(0xFFFFD54F), cell * 0.14f, Offset(cx, cy - cell * 0.08f))
            drawRoundRect(
                Color(0xFFFFD54F),
                Offset(cx - cell * 0.05f, cy - cell * 0.02f),
                Size(cell * 0.1f, cell * 0.28f),
                CornerRadius(2f),
            )
            drawCircle(style.floor, cell * 0.06f, Offset(cx, cy - cell * 0.08f))
        }
        MazeTile.LOCK -> {
            drawRoundRect(
                Color(0xFF6D4C41),
                Offset(cx - cell * 0.22f, cy - cell * 0.1f),
                Size(cell * 0.44f, cell * 0.32f),
                CornerRadius(4f),
            )
            drawArc(
                Color(0xFF6D4C41),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - cell * 0.16f, cy - cell * 0.32f),
                size = Size(cell * 0.32f, cell * 0.28f),
                style = Stroke(width = cell * 0.08f),
            )
            drawCircle(Color(0xFFFFD54F), cell * 0.06f, Offset(cx, cy + cell * 0.02f))
        }
        MazeTile.PORTAL -> {
            val spin = game.time * 120f
            run {
                drawCircle(Color(0xFF7E57C2).copy(alpha = 0.35f), cell * 0.32f, Offset(cx, cy))
                drawCircle(
                    Color(0xFFB39DDB),
                    cell * 0.22f,
                    Offset(cx, cy),
                    style = Stroke(width = cell * 0.06f),
                )
                drawCircle(Color(0xFFE1BEE7), cell * 0.10f, Offset(cx, cy))
                // 旋转星芒
                for (i in 0 until 4) {
                    val a = Math.PI.toFloat() / 2f * i + spin * 0.017f
                    drawLine(
                        Color.White.copy(alpha = 0.8f),
                        Offset(cx + cos(a) * cell * 0.12f, cy + sin(a) * cell * 0.12f),
                        Offset(cx + cos(a) * cell * 0.28f, cy + sin(a) * cell * 0.28f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // 门号
            // skip text — use color bands by id
            val band =
                when (tile.portalId % 3) {
                    1 -> Color(0xFF80CBC4)
                    2 -> Color(0xFFFFAB91)
                    else -> Color(0xFFCE93D8)
                }
            drawCircle(band, cell * 0.06f, Offset(cx, cy - cell * 0.28f))
        }
        MazeTile.EXIT -> {
            val open = game.honeyLeft == 0
            // 门框：深色立柱 + 门楣
            val frameC = Color(0xFF6A7A6E)
            drawRoundRect(
                frameC,
                Offset(cx - cell * 0.32f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                frameC,
                Offset(cx + cell * 0.24f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                frameC.copy(alpha = 0.85f),
                Offset(cx - cell * 0.32f, cy - cell * 0.42f),
                Size(cell * 0.64f, cell * 0.10f),
                CornerRadius(cell * 0.04f),
            )
            // 门扇：开启时向上滑动淡出，关闭时实心
            val openT =
                if (open) {
                    doorOpenProgress(game.time, game.lastHoneyAt)
                } else {
                    0f
                }
            val doorCol = if (open) Color(0xFF66BB6A) else Color(0xFF90A4AE)
            if (openT < 1f) {
                val dy = -openT * cell * 0.42f
                drawRoundRect(
                    doorCol.copy(alpha = 1f - openT * 0.5f),
                    Offset(cx - cell * 0.28f, cy - cell * 0.32f + dy),
                    Size(cell * 0.56f, cell * 0.6f),
                    CornerRadius(cell * 0.08f),
                )
                drawCircle(Color(0xFFFFF176), cell * 0.07f, Offset(cx + cell * 0.14f, cy + dy))
            }
            if (open) {
                // 门洞发光：脉动光环
                val pulse = 0.5f + 0.5f * sin(game.time * 3f)
                drawCircle(Color(0xFFA5D6A7).copy(alpha = 0.40f + 0.2f * pulse), cell * 0.42f, Offset(cx, cy))
                // 门洞内侧亮光
                drawRoundRect(
                    Color(0xFFC8E6C9).copy(alpha = 0.5f),
                    Offset(cx - cell * 0.26f, cy - cell * 0.30f),
                    Size(cell * 0.52f, cell * 0.56f),
                    CornerRadius(cell * 0.06f),
                )
            }
        }
        MazeTile.START -> {
            drawCircle(style.accent.copy(alpha = 0.25f), cell * 0.28f, Offset(cx, cy))
        }
        else -> Unit
    }
}

internal fun DrawScope.drawMazeBear(
    game: MazeGame,
    sprites: BearSprites,
    cx: Float,
    cy: Float,
    cell: Float,
) {
    val bs = cell * 0.92f
    val frame =
        when {
            game.moving && game.sliding -> BearFrame.JUMP
            game.moving -> runCycleFrame(game.time * 10f)
            else -> BearFrame.LAND
        }
    val flip = game.facing == MazeDir.LEFT
    val tilt =
        when (game.facing) {
            MazeDir.UP -> -6f
            MazeDir.DOWN -> 6f
            else -> 0f
        }
    // 脚下影子
    drawOval(
        Color.Black.copy(alpha = 0.18f),
        Offset(cx - bs * 0.28f, cy + bs * 0.28f),
        Size(bs * 0.56f, bs * 0.16f),
    )
    drawGameBear(
        sprites = sprites,
        centerX = cx,
        centerY = cy - bs * 0.05f,
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                frame = frame,
                bodyTilt = tilt,
                flipX = flip,
                alpha = 1f,
            ),
    )
}
