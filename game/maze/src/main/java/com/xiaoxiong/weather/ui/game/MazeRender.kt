package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------- 绘制 ----------------

// 常量颜色：避免每帧/每格重复 copy(alpha)。
private val boardShadow = Color.Black.copy(alpha = 0.18f)
private val wallShadow = Color.Black.copy(alpha = 0.22f)
private val wallSeamHigh = Color.Black.copy(alpha = 0.10f)
private val wallSeamLow = Color.Black.copy(alpha = 0.07f)
private val floorInnerShadow = Color.Black.copy(alpha = 0.06f)
private val floorHighlight = Color.White.copy(alpha = 0.10f)
private val honeyBase = Color(0xFFFFB300)
private val honeyTop = Color(0xFFFFD54F)
private val honeyLid = Color(0xFF8D6E63)
private val keyGold = Color(0xFFFFD54F)
private val lockBrown = Color(0xFF6D4C41)
private val portalOuter = Color(0xFF7E57C2)
private val portalRing = Color(0xFFB39DDB)
private val portalCore = Color(0xFFE1BEE7)
private val portalBand1 = Color(0xFF80CBC4)
private val portalBand2 = Color(0xFFFFAB91)
private val portalBand3 = Color(0xFFCE93D8)
private val exitFrame = Color(0xFF6A7A6E)
private val exitDoorOpen = Color(0xFF66BB6A)
private val exitDoorClosed = Color(0xFF90A4AE)
private val exitKnob = Color(0xFFFFF176)
private val exitGlow = Color(0xFFA5D6A7)
private val exitInner = Color(0xFFC8E6C9)

/** 棋盘布局：由画布尺寸与行列数决定，供静态层烘焙与动态层共用，保证坐标一致。 */
private class BoardLayout(
    val cell: Float,
    val ox: Float,
    val oy: Float,
)

private fun boardLayout(
    rows: Int,
    cols: Int,
    size: Size,
): BoardLayout {
    val pad = 16f
    val hudTop = 110f
    val hudBottom = 200f
    val availW = size.width - pad * 2
    val availH = size.height - hudTop - hudBottom
    val cell = min(availW / cols, availH / rows)
    val boardW = cell * cols
    val boardH = cell * rows
    return BoardLayout(
        cell = cell,
        ox = (size.width - boardW) / 2f,
        oy = hudTop + (availH - boardH) / 2f,
    )
}

/**
 * 静态底层（墙/地板）缓存：墙与地板不随对局变化，按关卡/主题/画布尺寸烘焙一次，
 * 之后每帧只 drawImage。蜂蜜/钥匙/锁等叠加物仍逐帧绘制。
 */
internal class MazeBaseLayerCache {
    private var bitmap: ImageBitmap? = null
    private var cachedLevel: MazeLevel? = null
    private var cachedStyle: MazeThemeStyle? = null
    private var cachedWidth = 0
    private var cachedHeight = 0

    fun bitmapFor(
        game: MazeGame,
        style: MazeThemeStyle,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
    ): ImageBitmap? {
        val w = ceil(size.width).toInt()
        val h = ceil(size.height).toInt()
        if (w <= 0 || h <= 0) return null
        val level = game.level
        val cached = bitmap
        if (cached != null &&
            cachedWidth == w &&
            cachedHeight == h &&
            cachedLevel === level &&
            cachedStyle == style
        ) {
            return cached
        }
        val next = ImageBitmap(w, h)
        // 用原始浮点 size 布局，保证与直接绘制坐标一致；bitmap 取 ceil 仅多出边缘空像素。
        CanvasDrawScope().draw(density, layoutDirection, Canvas(next), size) {
            drawMazeCellBases(game, style)
        }
        bitmap = next
        cachedLevel = level
        cachedStyle = style
        cachedWidth = w
        cachedHeight = h
        return next
    }
}

internal fun DrawScope.drawMazeWorld(
    game: MazeGame,
    sprites: BearSprites,
    style: MazeThemeStyle,
    baseLayer: MazeBaseLayerCache,
) {
    val layout = boardLayout(game.rows, game.cols, size)
    val cell = layout.cell
    val ox = layout.ox
    val oy = layout.oy
    val boardW = cell * game.cols
    val boardH = cell * game.rows

    // 天空地平线光晕：位于棋盘上方天空带，让背景更立体
    val horizonY = oy - cell * 0.5f
    if (horizonY > 0f) {
        drawHorizonGlow(horizonY, midColor(style.skyBottom, Color.White), size.height * 0.16f)
    }

    // 主题氛围装饰
    drawThemeFx(game, style)

    // 棋盘阴影
    drawRoundRect(
        boardShadow,
        Offset(ox + 4f, oy + 6f),
        Size(boardW, boardH),
        CornerRadius(12f),
    )

    // 静态底层：命中缓存则一次 drawImage，否则回退逐格绘制
    val base = baseLayer.bitmapFor(game, style, size, this, layoutDirection)
    if (base != null) {
        drawImage(
            image = base,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(base.width, base.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(base.width, base.height),
            filterQuality = FilterQuality.None,
        )
    } else {
        for (r in 0 until game.rows) {
            val y = oy + r * cell
            for (c in 0 until game.cols) {
                drawCellBase(game, style, r, c, ox + c * cell, y, cell)
            }
        }
    }

    // 动态/叠加层：蜂蜜脉冲、传送门旋转、出口开门、冰/钥匙/锁/起点
    for (r in 0 until game.rows) {
        val y = oy + r * cell
        for (c in 0 until game.cols) {
            drawCellOverlay(game, style, r, c, ox + c * cell, y, cell)
        }
    }

    // 小熊
    val (br, bc) = game.bearDrawPos()
    val bx = ox + (bc + 0.5f) * cell
    val by = oy + (br + 0.5f) * cell
    drawMazeBear(game, sprites, bx, by, cell)
}

/** 烘焙静态底层：遍历所有格绘制墙/地板，供 [MazeBaseLayerCache] 调用。 */
private fun DrawScope.drawMazeCellBases(
    game: MazeGame,
    style: MazeThemeStyle,
) {
    val layout = boardLayout(game.rows, game.cols, size)
    val cell = layout.cell
    for (r in 0 until game.rows) {
        val y = layout.oy + r * cell
        for (c in 0 until game.cols) {
            drawCellBase(game, style, r, c, layout.ox + c * cell, y, cell)
        }
    }
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
            drawStormLightning(game.time, 1f, size.width, size.height)
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

/** 静态底层单格：墙或地板。不随对局变化，可安全烘焙。 */
private fun DrawScope.drawCellBase(
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
                wallShadow,
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
                wallSeamHigh,
                Offset(x + pad * 1.6f, y + pad + cell * 0.52f),
                Offset(x + cell - pad * 1.6f, y + pad + cell * 0.52f),
                strokeWidth = 1.5f,
            )
            drawLine(
                wallSeamLow,
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
                floorInnerShadow,
                Offset(x + pad * 0.5f, y + pad * 0.5f),
                Size(cell - pad, cell - pad),
                CornerRadius(cell * 0.1f),
                style = Stroke(width = 1.5f),
            )
            // 轻量纹理：左上高光小角
            drawCircle(
                floorHighlight,
                cell * 0.08f,
                Offset(x + pad + cell * 0.16f, y + pad + cell * 0.18f),
            )
        }
    }
}

/** 动态叠加单格：冰/蜂蜜/钥匙/锁/传送门/出口/起点。 */
private fun DrawScope.drawCellOverlay(
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
            drawCircle(honeyBase, cell * 0.22f, Offset(cx, cy + cell * 0.02f))
            drawCircle(honeyTop, cell * 0.14f, Offset(cx, cy - cell * 0.02f))
            drawRoundRect(
                honeyLid,
                Offset(cx - cell * 0.12f, cy - cell * 0.22f),
                Size(cell * 0.24f, cell * 0.1f),
                CornerRadius(3f),
            )
            // 闪光
            val pulse = 0.7f + 0.3f * sin(game.time * 4f + r + c)
            drawCircle(Color.White.copy(alpha = 0.35f * pulse), cell * 0.08f, Offset(cx - cell * 0.06f, cy - cell * 0.06f))
        }
        MazeTile.KEY -> {
            drawCircle(keyGold, cell * 0.14f, Offset(cx, cy - cell * 0.08f))
            drawRoundRect(
                keyGold,
                Offset(cx - cell * 0.05f, cy - cell * 0.02f),
                Size(cell * 0.1f, cell * 0.28f),
                CornerRadius(2f),
            )
            drawCircle(style.floor, cell * 0.06f, Offset(cx, cy - cell * 0.08f))
        }
        MazeTile.LOCK -> {
            drawRoundRect(
                lockBrown,
                Offset(cx - cell * 0.22f, cy - cell * 0.1f),
                Size(cell * 0.44f, cell * 0.32f),
                CornerRadius(4f),
            )
            drawArc(
                lockBrown,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - cell * 0.16f, cy - cell * 0.32f),
                size = Size(cell * 0.32f, cell * 0.28f),
                style = Stroke(width = cell * 0.08f),
            )
            drawCircle(keyGold, cell * 0.06f, Offset(cx, cy + cell * 0.02f))
        }
        MazeTile.PORTAL -> {
            val spin = game.time * 120f
            run {
                drawCircle(portalOuter.copy(alpha = 0.35f), cell * 0.32f, Offset(cx, cy))
                drawCircle(
                    portalRing,
                    cell * 0.22f,
                    Offset(cx, cy),
                    style = Stroke(width = cell * 0.06f),
                )
                drawCircle(portalCore, cell * 0.10f, Offset(cx, cy))
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
                    1 -> portalBand1
                    2 -> portalBand2
                    else -> portalBand3
                }
            drawCircle(band, cell * 0.06f, Offset(cx, cy - cell * 0.28f))
        }
        MazeTile.EXIT -> {
            val open = game.honeyLeft == 0
            // 门框：深色立柱 + 门楣
            drawRoundRect(
                exitFrame,
                Offset(cx - cell * 0.32f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                exitFrame,
                Offset(cx + cell * 0.24f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                exitFrame.copy(alpha = 0.85f),
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
            val doorCol = if (open) exitDoorOpen else exitDoorClosed
            if (openT < 1f) {
                val dy = -openT * cell * 0.42f
                drawRoundRect(
                    doorCol.copy(alpha = 1f - openT * 0.5f),
                    Offset(cx - cell * 0.28f, cy - cell * 0.32f + dy),
                    Size(cell * 0.56f, cell * 0.6f),
                    CornerRadius(cell * 0.08f),
                )
                drawCircle(exitKnob, cell * 0.07f, Offset(cx + cell * 0.14f, cy + dy))
            }
            if (open) {
                // 门洞发光：脉动光环
                val pulse = 0.5f + 0.5f * sin(game.time * 3f)
                drawCircle(exitGlow.copy(alpha = 0.40f + 0.2f * pulse), cell * 0.42f, Offset(cx, cy))
                // 门洞内侧亮光
                drawRoundRect(
                    exitInner.copy(alpha = 0.5f),
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
