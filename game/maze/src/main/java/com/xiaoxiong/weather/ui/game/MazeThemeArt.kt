package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// 小熊迷宫的静态美术层：棋盘布局、主题墙面/地表纹理与装饰烘焙。
// 这里的内容只在「关卡 / 主题 / 画布尺寸」变化时执行一次（见 MazeBaseLayerCache），
// 因此允许分配 Path/Brush；动态层（MazeOverlays/MazeParticles）必须复用缓存。

/** 棋盘布局：由画布尺寸与行列数决定，供静态层烘焙与动态层共用，保证坐标一致。 */
internal class MazeBoardLayout(
    val cell: Float,
    val ox: Float,
    val oy: Float,
)

/** 顶栏预留高度（px）：与 MazePlayField 的 HUD 约定一致，避免棋盘压住胶囊芯片。 */
private const val HUD_TOP_PX = 110f
private const val HUD_BOTTOM_PX = 200f

internal fun mazeBoardLayout(
    rows: Int,
    cols: Int,
    size: Size,
): MazeBoardLayout {
    val pad = 16f
    val availW = size.width - pad * 2
    val availH = size.height - HUD_TOP_PX - HUD_BOTTOM_PX
    if (rows <= 0 || cols <= 0 || availW <= 0f || availH <= 0f) {
        return MazeBoardLayout(cell = 0f, ox = size.width / 2f, oy = HUD_TOP_PX)
    }
    val cell = min(availW / cols, availH / rows)
    val boardW = cell * cols
    val boardH = cell * rows
    return MazeBoardLayout(
        cell = cell,
        ox = (size.width - boardW) / 2f,
        oy = HUD_TOP_PX + (availH - boardH) / 2f,
    )
}

/** 确定性伪随机 0..1：同一 (i, salt) 永远同值，纹理不随帧抖动。 */
internal fun mazeHash01(
    i: Int,
    salt: Int = 0,
): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}

/** 按比例提亮（保持 alpha）。 */
internal fun mazeLighten(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red + (1f - c.red) * amount).coerceIn(0f, 1f),
        green = (c.green + (1f - c.green) * amount).coerceIn(0f, 1f),
        blue = (c.blue + (1f - c.blue) * amount).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

/** 按比例压暗（保持 alpha）。 */
internal fun mazeDarken(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red * (1f - amount)).coerceIn(0f, 1f),
        green = (c.green * (1f - amount)).coerceIn(0f, 1f),
        blue = (c.blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

/**
 * 按宽度复用 [Stroke]（A/B 双槽）：动态叠加层热路径上不 new Stroke。
 * 单线程绘制，无需加锁。
 */
private class MazeStrokeSlot {
    var width = -1f
    var stroke: Stroke? = null
}

private val strokeSlotA = MazeStrokeSlot()
private val strokeSlotB = MazeStrokeSlot()

internal fun mazeStroke(width: Float): Stroke {
    val w = round(max(width, 0.1f) * 10f) / 10f
    val a = strokeSlotA
    if (a.stroke != null && a.width == w) return a.stroke!!
    val b = strokeSlotB
    if (b.stroke != null && b.width == w) {
        val swapStroke = a.stroke
        val swapWidth = a.width
        a.stroke = b.stroke
        a.width = b.width
        b.stroke = swapStroke
        b.width = swapWidth
        return a.stroke!!
    }
    val fresh = Stroke(w)
    b.stroke = a.stroke
    b.width = a.width
    a.stroke = fresh
    a.width = w
    return fresh
}

private fun DrawScope.wallAt(
    grid: Array<Array<MazeCell>>,
    r: Int,
    c: Int,
): Boolean = r in grid.indices && c in grid[r].indices && grid[r][c].tile == MazeTile.WALL

/** 节日红毯的明暗双色。 */
private val mazeCarpet = Color(0xFF9E2B2B)
private val mazeCarpetAlt = Color(0xFF8A2323)

/**
 * 烘焙静态底层：天空/场地装饰 + 棋盘阴影 + 全部墙/地板 + 外框与暗角。
 * 不读 [MazeGame] 的 mutableState（time/honeyLeft 等），可安全缓存。
 */
internal fun DrawScope.drawMazeBaseLayer(
    game: MazeGame,
    style: MazeThemeStyle,
) {
    if (size.width <= 0f || size.height <= 0f) return
    if (game.rows <= 0 || game.cols <= 0) return
    val layout = mazeBoardLayout(game.rows, game.cols, size)
    if (layout.cell <= 0.5f) return
    val boardW = layout.cell * game.cols
    val boardH = layout.cell * game.rows

    drawMazeSceneDecor(game.level.theme, style, layout, game.rows, game.cols)

    // 棋盘投影：右下偏移的软阴影，让场地浮起来
    drawRoundRect(
        Color.Black.copy(alpha = 0.20f),
        Offset(layout.ox + layout.cell * 0.10f, layout.oy + layout.cell * 0.14f),
        Size(boardW, boardH),
        CornerRadius(layout.cell * 0.28f),
    )

    drawMazeBoardCells(game, style, layout)

    drawBoardRim(style, layout, boardW, boardH)
    drawBoardVignette(style, layout, boardW, boardH)
}

/** 逐格绘制墙/地板；供静态层烘焙与位图不可用时的回退路径共用。 */
internal fun DrawScope.drawMazeBoardCells(
    game: MazeGame,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
) {
    if (layout.cell <= 0f) return
    for (r in 0 until game.rows) {
        val y = layout.oy + r * layout.cell
        for (c in 0 until game.cols) {
            val x = layout.ox + c * layout.cell
            if (game.grid[r][c].tile == MazeTile.WALL) {
                drawWallCell(game.grid, style, game.level.theme, r, c, x, y, layout.cell)
            } else {
                drawFloorCell(style, game.level.theme, r, c, x, y, layout.cell)
            }
        }
    }
}

// ---------------------------------------------------------------------
// 墙：按主题的砖 / 石 / 雪 / 灯笼柱
// ---------------------------------------------------------------------

private fun DrawScope.drawWallCell(
    grid: Array<Array<MazeCell>>,
    style: MazeThemeStyle,
    theme: MazeTheme,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val up = wallAt(grid, r - 1, c)
    val down = wallAt(grid, r + 1, c)
    val left = wallAt(grid, r, c - 1)
    val right = wallAt(grid, r, c + 1)
    val gap = cell * 0.045f
    val l = x + if (left) 0f else gap
    val t = y + if (up) 0f else gap
    val rr = x + cell - if (right) 0f else gap
    val b = y + cell - if (down) 0f else gap
    if (rr - l <= 0f || b - t <= 0f) return
    val rad = cell * 0.18f
    val round =
        RoundRect(
            rect = Rect(l, t, rr, b),
            topLeft = CornerRadius(if (left || up) 0f else rad),
            topRight = CornerRadius(if (right || up) 0f else rad),
            bottomRight = CornerRadius(if (right || down) 0f else rad),
            bottomLeft = CornerRadius(if (left || down) 0f else rad),
        )
    val path = Path().apply { addRoundRect(round) }
    drawPath(path, style.wall)
    clipPath(path) {
        when (theme) {
            MazeTheme.GARDEN -> drawWoodWall(style, r, c, l, t, rr - l, b - t, cell)
            MazeTheme.RAIN -> drawBrickWall(style, r, c, l, t, rr - l, b - t, cell, wet = true)
            MazeTheme.NIGHT -> drawNightWall(style, r, c, l, t, rr - l, b - t, cell)
            MazeTheme.SNOW -> drawSnowWall(style, r, c, l, t, rr - l, b - t, cell)
            MazeTheme.FESTIVAL -> drawLanternWall(style, r, c, l, t, rr - l, b - t, cell)
            MazeTheme.CLOUD -> drawCloudWall(style, r, c, l, t, rr - l, b - t, cell)
        }
    }
    // 受光/背光边：只画暴露的外沿，相邻墙共享边不落线，整片墙自然连成整体
    val edge = max(1f, cell * 0.028f)
    val inL = l + rad * 0.35f
    val inR = rr - rad * 0.35f
    val inT = t + rad * 0.35f
    val inB = b - rad * 0.35f
    if (!up) {
        drawLine(style.wallEdge.copy(alpha = 0.55f), Offset(inL, t + edge * 0.5f), Offset(inR, t + edge * 0.5f), strokeWidth = edge)
    }
    if (!left) {
        drawLine(style.wallEdge.copy(alpha = 0.40f), Offset(l + edge * 0.5f, inT), Offset(l + edge * 0.5f, inB), strokeWidth = edge)
    }
    if (!down) {
        drawLine(style.wallDeep.copy(alpha = 0.55f), Offset(inL, b - edge * 0.5f), Offset(inR, b - edge * 0.5f), strokeWidth = edge)
    }
    if (!right) {
        drawLine(style.wallDeep.copy(alpha = 0.40f), Offset(rr - edge * 0.5f, inT), Offset(rr - edge * 0.5f, inB), strokeWidth = edge)
    }
}

/** 花园：木篱笆横板 + 木纹 + 苔藓。 */
private fun DrawScope.drawWoodWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
) {
    val plank = h / 3f
    for (i in 0 until 3) {
        val py = t + i * plank
        val tone = if ((i + r + c) % 2 == 0) 0.10f else 0.02f
        drawRect(
            mazeLighten(style.wall, tone),
            topLeft = Offset(l, py + plank * 0.06f),
            size = Size(w, plank * 0.88f),
        )
        drawLine(
            mazeDarken(style.wall, 0.30f).copy(alpha = 0.55f),
            Offset(l, py + plank * 0.03f),
            Offset(l + w, py + plank * 0.03f),
            strokeWidth = max(1f, cell * 0.018f),
        )
        // 木纹：每块板两条确定性的短纹
        val n = 2
        for (k in 0 until n) {
            val hx = mazeHash01(r * 31 + c * 17 + i * 7 + k, 11)
            val hw = cell * (0.18f + 0.26f * mazeHash01(r * 13 + c * 29 + k, 12))
            val gx = l + w * (0.10f + 0.62f * hx)
            val gy = py + plank * (0.30f + 0.42f * mazeHash01(r * 7 + c * 5 + i, 13))
            drawLine(
                mazeDarken(style.wall, 0.16f).copy(alpha = 0.35f),
                Offset(gx, gy),
                Offset(min(gx + hw, l + w - cell * 0.06f), gy),
                strokeWidth = max(1f, cell * 0.012f),
                cap = StrokeCap.Round,
            )
        }
    }
    // 顶面受光：最后叠加，保证不被木板覆盖
    drawRect(
        mazeLighten(style.wall, 0.30f).copy(alpha = 0.50f),
        topLeft = Offset(l, t),
        size = Size(w, h * 0.13f),
    )
    // 顶沿苔藓
    val moss = Color(0xFF6EA83E)
    for (k in 0 until 4) {
        val hx = l + w * (0.12f + 0.24f * k) + mazeHash01(r * 9 + c * 3 + k, 21) * cell * 0.05f
        drawCircle(
            moss.copy(alpha = 0.55f),
            cell * (0.05f + 0.03f * mazeHash01(r + k, c + 5)),
            Offset(hx, t + cell * 0.05f),
        )
    }
}

/** 雨巷：错缝石砖 + 湿润顶光。 */
private fun DrawScope.drawBrickWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
    wet: Boolean,
) {
    val rows = 3
    val rowH = h / rows
    drawRect(style.wallSide, topLeft = Offset(l, t), size = Size(w, h))
    for (i in 0 until rows) {
        val by = t + i * rowH
        val offset = if ((i + r) % 2 == 0) 0f else w * 0.5f
        for (j in -1..1) {
            val bx = l + offset + j * w
            val bw = w * 0.5f
            drawRect(
                mazeLighten(style.wall, 0.06f + 0.05f * ((i * 3 + j + c) % 2)),
                topLeft = Offset(max(bx, l) + 1f, by + 1f),
                size = Size(min(bw - 2f, l + w - max(bx, l) - 1f), rowH - 2f),
            )
        }
        drawLine(
            style.wallDeep.copy(alpha = 0.70f),
            Offset(l, by),
            Offset(l + w, by),
            strokeWidth = max(1f, cell * 0.02f),
        )
    }
    drawRect(
        style.wallTop.copy(alpha = if (wet) 0.42f else 0.30f),
        topLeft = Offset(l, t),
        size = Size(w, h * 0.22f),
    )
    if (wet) {
        // 雨水在墙面留下的两道竖痕
        for (k in 0 until 2) {
            val gx = l + w * (0.24f + 0.46f * k) + mazeHash01(r * 5 + c * 11 + k, 31) * cell * 0.06f
            drawLine(
                Color.White.copy(alpha = 0.16f),
                Offset(gx, t + h * 0.10f),
                Offset(gx, t + h * 0.78f),
                strokeWidth = max(1f, cell * 0.018f),
            )
        }
    }
}

/** 夜空：深色砖石 + 微光符文点。 */
private fun DrawScope.drawNightWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
) {
    drawBrickWall(style, r, c, l, t, w, h, cell, wet = false)
    drawRect(
        style.wallTop.copy(alpha = 0.24f),
        topLeft = Offset(l, t),
        size = Size(w, h * 0.16f),
    )
    if ((r + c) % 5 == 0) {
        val gx = l + w * (0.36f + 0.28f * mazeHash01(r * 3 + c, 41))
        val gy = t + h * (0.40f + 0.26f * mazeHash01(r + c * 7, 42))
        drawCircle(style.accent.copy(alpha = 0.30f), cell * 0.09f, Offset(gx, gy))
        drawCircle(Color.White.copy(alpha = 0.55f), cell * 0.035f, Offset(gx, gy))
    }
}

/** 雪林：石基 + 积雪顶盖 + 冰棱。 */
private fun DrawScope.drawSnowWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
) {
    drawBrickWall(style, r, c, l, t, w, h, cell, wet = false)
    val capH = h * 0.42f
    val snow = Color(0xFFF8FCFF)
    drawRect(snow.copy(alpha = 0.96f), topLeft = Offset(l, t), size = Size(w, capH))
    // 雪盖下缘的圆弧起伏
    for (k in 0 until 3) {
        drawCircle(
            snow.copy(alpha = 0.96f),
            w * 0.12f,
            Offset(l + w * (0.16f + 0.34f * k), t + capH),
        )
    }
    drawLine(
        Color(0xFF9CBCD8).copy(alpha = 0.65f),
        Offset(l, t + capH + cell * 0.02f),
        Offset(l + w, t + capH + cell * 0.02f),
        strokeWidth = max(1f, cell * 0.02f),
    )
    // 冰棱
    for (k in 0 until 3) {
        val hx = l + w * (0.20f + 0.30f * k) + mazeHash01(r * 17 + c * 3 + k, 51) * cell * 0.06f
        val len = cell * (0.10f + 0.10f * mazeHash01(r + c + k, 52))
        val half = cell * 0.035f
        drawLine(
            Color(0xFFDDEFFC).copy(alpha = 0.85f),
            Offset(hx, t + capH),
            Offset(hx, t + capH + len),
            strokeWidth = half,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.35f), w * 0.10f, Offset(l + w * 0.24f, t + capH * 0.42f))
}

/** 节日：红墙金边 + 挂灯笼。 */
private fun DrawScope.drawLanternWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
) {
    drawRect(style.wall, topLeft = Offset(l, t), size = Size(w, h))
    drawBrickWall(style, r, c, l, t, w, h, cell, wet = false)
    val gold = Color(0xFFFFD25A)
    drawRect(gold.copy(alpha = 0.85f), topLeft = Offset(l, t), size = Size(max(1f, cell * 0.05f), h))
    drawRect(gold.copy(alpha = 0.85f), topLeft = Offset(l + w - max(1f, cell * 0.05f), t), size = Size(max(1f, cell * 0.05f), h))
    // 灯笼：流苏 + 提线 + 灯身 + 顶盖
    val lx = l + w * 0.5f
    val ly = t + h * 0.42f
    val lr = cell * 0.16f
    val body = if ((r + c) % 2 == 0) Color(0xFFFF5252) else Color(0xFFFFB300)
    drawLine(gold.copy(alpha = 0.8f), Offset(lx, t), Offset(lx, ly - lr), strokeWidth = max(1f, cell * 0.02f))
    drawCircle(body, lr, Offset(lx, ly))
    drawCircle(mazeLighten(body, 0.35f).copy(alpha = 0.55f), lr * 0.5f, Offset(lx - lr * 0.3f, ly - lr * 0.3f))
    drawRoundRect(
        gold,
        Offset(lx - lr * 0.42f, ly - lr * 1.22f),
        Size(lr * 0.84f, lr * 0.34f),
        CornerRadius(lr * 0.12f),
    )
    drawRoundRect(
        gold,
        Offset(lx - lr * 0.42f, ly + lr * 0.92f),
        Size(lr * 0.84f, lr * 0.30f),
        CornerRadius(lr * 0.12f),
    )
    drawLine(
        gold,
        Offset(lx, ly + lr * 1.2f),
        Offset(lx, ly + lr * 1.9f),
        strokeWidth = max(1f, cell * 0.03f),
        cap = StrokeCap.Round,
    )
}

/** 云端：蓬松云块。 */
private fun DrawScope.drawCloudWall(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    l: Float,
    t: Float,
    w: Float,
    h: Float,
    cell: Float,
) {
    drawRect(mazeDarken(style.wall, 0.10f), topLeft = Offset(l, t), size = Size(w, h))
    val puff = Color.White
    val shade = Color(0xFFB9CFE6)
    val seed = r * 101 + c * 53
    drawCircle(shade.copy(alpha = 0.55f), w * 0.46f, Offset(l + w * 0.5f, t + h * 0.66f))
    for (k in 0 until 4) {
        val hx = l + w * (0.14f + 0.24f * mazeHash01(seed + k, 61))
        val hy = t + h * (0.30f + 0.34f * mazeHash01(seed + k, 62))
        val rad = w * (0.16f + 0.12f * mazeHash01(seed + k, 63))
        drawCircle(puff.copy(alpha = 0.96f), rad, Offset(hx, hy))
        drawCircle(puff.copy(alpha = 0.75f), rad * 0.8f, Offset(hx + w * 0.06f, hy - h * 0.10f))
    }
    drawRect(
        Color.White.copy(alpha = 0.35f),
        topLeft = Offset(l, t + h * 0.72f),
        size = Size(w, h * 0.28f),
    )
    drawCircle(Color.White.copy(alpha = 0.30f), w * 0.08f, Offset(l + w * 0.72f, t + h * 0.20f))
}

// ---------------------------------------------------------------------
// 地板：每主题一种地表 + 轻微棋盘明暗
// ---------------------------------------------------------------------

private fun DrawScope.drawFloorCell(
    style: MazeThemeStyle,
    theme: MazeTheme,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val pad = cell * 0.028f
    val inset = cell - pad * 2
    if (inset <= 0f) return
    val even = (r + c) % 2 == 0
    val base =
        when (theme) {
            MazeTheme.FESTIVAL -> if (even) mazeCarpet else mazeCarpetAlt
            else -> if (even) style.floor else style.floorAlt
        }
    val corner = CornerRadius(cell * (if (theme == MazeTheme.CLOUD) 0.24f else 0.10f))
    drawRoundRect(base, Offset(x + pad, y + pad), Size(inset, inset), corner)
    when (theme) {
        MazeTheme.GARDEN -> drawGrassFloor(style, r, c, x, y, cell)
        MazeTheme.RAIN -> drawStoneFloor(style, r, c, x, y, cell)
        MazeTheme.SNOW -> drawSnowFloor(style, r, c, x, y, cell)
        MazeTheme.NIGHT -> drawNightFloor(style, r, c, x, y, cell)
        MazeTheme.FESTIVAL -> drawCarpetFloor(style, r, c, x, y, cell)
        MazeTheme.CLOUD -> drawCloudFloor(style, r, c, x, y, cell)
    }
    drawRoundRect(
        style.wallDeep.copy(alpha = 0.10f),
        Offset(x + pad, y + pad),
        Size(inset, inset),
        corner,
        style = mazeStroke(max(1f, cell * 0.012f)),
    )
}

private fun DrawScope.drawGrassFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val blade = mazeDarken(style.floor, 0.24f)
    for (k in 0 until 5) {
        val hx = x + cell * (0.16f + 0.68f * mazeHash01(r * 19 + c * 7 + k, 71))
        val hy = y + cell * (0.40f + 0.46f * mazeHash01(r * 11 + c * 23 + k, 72))
        val len = cell * (0.08f + 0.07f * mazeHash01(r + c + k, 73))
        drawLine(
            blade.copy(alpha = 0.45f),
            Offset(hx, hy),
            Offset(hx + cell * 0.03f, hy - len),
            strokeWidth = max(1f, cell * 0.014f),
            cap = StrokeCap.Round,
        )
    }
    if (mazeHash01(r * 47 + c * 31, 74) < 0.20f) {
        val fx = x + cell * (0.30f + 0.40f * mazeHash01(r * 5 + c, 75))
        val fy = y + cell * (0.30f + 0.40f * mazeHash01(r + c * 3, 76))
        val petal = if (mazeHash01(r + c, 77) < 0.5f) Color(0xFFFFF3E0) else Color(0xFFFFE082)
        for (p in 0 until 4) {
            val a = Math.PI.toFloat() * 0.5f * p
            drawCircle(petal, cell * 0.035f, Offset(fx + kotlin.math.cos(a) * cell * 0.035f, fy + kotlin.math.sin(a) * cell * 0.035f))
        }
        drawCircle(Color(0xFFFFB300), cell * 0.025f, Offset(fx, fy))
    }
}

private fun DrawScope.drawStoneFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val seam = mazeDarken(style.floor, 0.30f)
    drawLine(seam.copy(alpha = 0.30f), Offset(x, y + cell * 0.5f), Offset(x + cell, y + cell * 0.5f), strokeWidth = max(1f, cell * 0.012f))
    drawLine(seam.copy(alpha = 0.22f), Offset(x + cell * 0.5f, y), Offset(x + cell * 0.5f, y + cell), strokeWidth = max(1f, cell * 0.010f))
    if (mazeHash01(r * 37 + c * 13, 81) < 0.26f) {
        val px = x + cell * (0.28f + 0.40f * mazeHash01(r + c, 82))
        val py = y + cell * (0.30f + 0.40f * mazeHash01(r * 3 + c, 83))
        drawOval(
            Color(0xFFBFE0F2).copy(alpha = 0.32f),
            Offset(px - cell * 0.16f, py - cell * 0.07f),
            Size(cell * 0.32f, cell * 0.14f),
        )
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(px - cell * 0.08f, py - cell * 0.02f),
            Offset(px + cell * 0.02f, py - cell * 0.05f),
            strokeWidth = max(1f, cell * 0.012f),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnowFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    for (k in 0 until 7) {
        val hx = x + cell * mazeHash01(r * 23 + c * 41 + k, 91)
        val hy = y + cell * mazeHash01(r * 29 + c * 17 + k, 92)
        drawCircle(
            Color.White.copy(alpha = 0.75f),
            cell * (0.012f + 0.018f * mazeHash01(k + r, 93)),
            Offset(hx, hy),
        )
    }
    if (mazeHash01(r * 53 + c * 7, 94) < 0.22f) {
        val sx = x + cell * (0.28f + 0.44f * mazeHash01(r + c, 95))
        val sy = y + cell * (0.28f + 0.44f * mazeHash01(r * 9 + c, 96))
        val r0 = cell * 0.07f
        drawLine(Color.White.copy(alpha = 0.85f), Offset(sx - r0, sy), Offset(sx + r0, sy), strokeWidth = max(1f, cell * 0.012f))
        drawLine(Color.White.copy(alpha = 0.85f), Offset(sx, sy - r0), Offset(sx, sy + r0), strokeWidth = max(1f, cell * 0.012f))
    }
}

private fun DrawScope.drawNightFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val seam = Color.Black.copy(alpha = 0.22f)
    drawLine(seam, Offset(x, y), Offset(x + cell, y), strokeWidth = max(1f, cell * 0.012f))
    drawLine(seam, Offset(x, y), Offset(x, y + cell), strokeWidth = max(1f, cell * 0.012f))
    drawLine(
        style.wallTop.copy(alpha = 0.16f),
        Offset(x + cell * 0.5f, y + cell * 0.5f),
        Offset(x + cell * 0.5f, y + cell * 0.92f),
        strokeWidth = max(1f, cell * 0.010f),
    )
    if (mazeHash01(r * 67 + c * 43, 101) < 0.24f) {
        val gx = x + cell * (0.26f + 0.48f * mazeHash01(r + c, 102))
        val gy = y + cell * (0.26f + 0.48f * mazeHash01(r * 3 + c, 103))
        drawCircle(style.accent.copy(alpha = 0.28f), cell * 0.09f, Offset(gx, gy))
        drawCircle(Color.White.copy(alpha = 0.75f), cell * 0.03f, Offset(gx, gy))
    }
}

private fun DrawScope.drawCarpetFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val gold = Color(0xFFE6B84A)
    drawRoundRect(
        gold.copy(alpha = 0.30f),
        Offset(x + cell * 0.07f, y + cell * 0.07f),
        Size(cell * 0.86f, cell * 0.86f),
        CornerRadius(cell * 0.06f),
        style = mazeStroke(max(1f, cell * 0.018f)),
    )
    val weave = Color.Black.copy(alpha = 0.10f)
    for (k in 0 until 3) {
        val off = cell * (0.30f + 0.16f * k)
        drawLine(weave, Offset(x + off, y + cell * 0.2f), Offset(x + cell * 0.2f, y + off), strokeWidth = max(1f, cell * 0.010f))
    }
    if ((r + c) % 4 == 0) {
        drawCircle(gold.copy(alpha = 0.45f), cell * 0.045f, Offset(x + cell * 0.2f, y + cell * 0.2f))
        drawCircle(gold.copy(alpha = 0.45f), cell * 0.045f, Offset(x + cell * 0.8f, y + cell * 0.8f))
    }
}

private fun DrawScope.drawCloudFloor(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    drawCircle(
        Color.White.copy(alpha = 0.55f),
        cell * 0.20f,
        Offset(x + cell * (0.30f + 0.10f * mazeHash01(r + c, 111)), y + cell * 0.34f),
    )
    drawCircle(
        Color(0xFFBFD9F0).copy(alpha = 0.28f),
        cell * 0.18f,
        Offset(x + cell * 0.66f, y + cell * 0.70f),
    )
    if (mazeHash01(r * 71 + c * 19, 112) < 0.18f) {
        drawCircle(Color.White.copy(alpha = 0.7f), cell * 0.05f, Offset(x + cell * 0.7f, y + cell * 0.3f))
    }
}

// ---------------------------------------------------------------------
// 棋盘外框与暗角
// ---------------------------------------------------------------------

private fun DrawScope.drawBoardRim(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val cell = layout.cell
    val inset = cell * 0.06f
    val rimW = max(2f, cell * 0.075f)
    drawRoundRect(
        style.boardRim,
        Offset(layout.ox - inset, layout.oy - inset),
        Size(boardW + inset * 2, boardH + inset * 2),
        CornerRadius(cell * 0.30f),
        style = mazeStroke(rimW),
    )
    drawRoundRect(
        mazeLighten(style.boardRim, 0.30f).copy(alpha = 0.55f),
        Offset(layout.ox - inset + rimW * 0.4f, layout.oy - inset + rimW * 0.4f),
        Size(boardW + inset * 2 - rimW * 0.8f, boardH + inset * 2 - rimW * 0.8f),
        CornerRadius(cell * 0.26f),
        style = mazeStroke(max(1f, rimW * 0.35f)),
    )
}

private fun DrawScope.drawBoardVignette(
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    boardW: Float,
    boardH: Float,
) {
    val corner = CornerRadius(layout.cell * 0.24f)
    val path =
        Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(layout.ox, layout.oy, layout.ox + boardW, layout.oy + boardH),
                    topLeft = corner,
                    topRight = corner,
                    bottomRight = corner,
                    bottomLeft = corner,
                ),
            )
        }
    clipPath(path) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                center = Offset(layout.ox + boardW * 0.5f, layout.oy + boardH * 0.42f),
                radius = max(boardW, boardH) * 0.72f,
            ),
            topLeft = Offset(layout.ox, layout.oy),
            size = Size(boardW, boardH),
        )
        // 内侧一圈柔和暗边，收住棋盘
        drawRoundRect(
            style.wallDeep.copy(alpha = 0.12f),
            Offset(layout.ox, layout.oy),
            Size(boardW, boardH),
            corner,
            style = mazeStroke(layout.cell * 0.22f),
        )
    }
}
