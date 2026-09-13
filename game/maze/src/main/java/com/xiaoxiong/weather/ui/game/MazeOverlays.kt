package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 迷宫动态叠加层：蜂蜜/冰面/钥匙/锁门/传送门/出口/起点的逐帧绘制。
 *
 * 性能约定：不创建 [androidx.compose.ui.graphics.Path]/[androidx.compose.ui.graphics.Brush]，
 * 只用 drawRect/drawCircle/drawArc/drawLine 的组合；[mazeStroke] 复用 Stroke。
 * 所有动效由 `game.time` 驱动，确定性且不依赖帧率。
 */

private val honeyShell = Color(0xFFE08A00)
private val honeyBody = Color(0xFFFFB300)
private val honeyLight = Color(0xFFFFD54F)
private val honeyGlass = Color(0xFFFFF3C4)
private val honeyLidWood = Color(0xFF8D6E63)
private val honeyLidDark = Color(0xFF6D4C41)

private val ironDark = Color(0xFF546E7A)
private val ironMid = Color(0xFF90A4AE)
private val ironLight = Color(0xFFCFD8DC)
private val woodDark = Color(0xFF5D4037)
private val woodMid = Color(0xFF8D6E63)
private val woodLight = Color(0xFFA1887F)

private val iceDeep = Color(0xFF81D4FA)
private val iceBright = Color(0xFFE1F5FE)

private val exitStone = Color(0xFF7E8C82)
private val exitStoneDark = Color(0xFF5C6A60)
private val exitWood = Color(0xFFA1662F)
private val exitWoodDark = Color(0xFF7A4A20)
private val exitGold = Color(0xFFFFCA6B)

/** 单格动态叠加绘制入口。 */
internal fun DrawScope.drawMazeTileOverlay(
    game: MazeGame,
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    if (cell <= 0f) return
    val tile = game.grid[r][c]
    val cx = x + cell * 0.5f
    val cy = y + cell * 0.5f
    when (tile.tile) {
        MazeTile.ICE -> drawIceTile(style, r, c, x, y, cell)
        MazeTile.HONEY -> drawHoneyTile(game, r, c, cx, cy, cell, reduceMotion)
        MazeTile.KEY -> drawKeyTile(game, r, c, cx, cy, cell, reduceMotion)
        MazeTile.LOCK -> drawLockTile(game, r, c, cx, cy, cell, reduceMotion)
        MazeTile.PORTAL -> drawPortalTile(game, style, tile.portalId, cx, cy, cell, reduceMotion)
        MazeTile.EXIT -> drawExitTile(game, cx, cy, cell, reduceMotion)
        MazeTile.START -> drawStartTile(game, style, r, c, cx, cy, cell, reduceMotion)
        else -> Unit
    }
}

// ---------------------------------------------------------------------
// 冰面
// ---------------------------------------------------------------------

private fun DrawScope.drawIceTile(
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val pad = cell * 0.03f
    val size = cell - pad * 2f
    if (size <= 0f) return
    drawRoundRect(
        style.ice.copy(alpha = 0.62f),
        Offset(x + pad, y + pad),
        Size(size, size),
        CornerRadius(cell * 0.14f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.16f),
        Offset(x + pad + size * 0.06f, y + pad + size * 0.04f),
        Size(size * 0.88f, size * 0.42f),
        CornerRadius(cell * 0.10f),
    )
    // 裂缝：每格两条确定性折线
    val crack = iceDeep.copy(alpha = 0.55f)
    for (k in 0 until 2) {
        val hx = x + cell * (0.16f + 0.5f * mazeHash01(r * 13 + c * 7 + k, 701))
        val hy = y + cell * (0.20f + 0.52f * mazeHash01(r * 11 + c * 17 + k, 702))
        val len1 = cell * (0.14f + 0.14f * mazeHash01(r + c + k, 703))
        val a1 = 0.5f + 0.9f * mazeHash01(r * 3 + c + k, 704)
        val mx = hx + cos(a1) * len1
        val my = hy + sin(a1) * len1
        drawLine(
            crack,
            Offset(hx, hy),
            Offset(mx, my),
            strokeWidth = max(1f, cell * 0.018f),
            cap = StrokeCap.Round,
        )
        drawLine(
            crack.copy(alpha = 0.35f),
            Offset(mx, my),
            Offset(mx + cos(a1 + 0.9f) * len1 * 0.6f, my + sin(a1 + 0.9f) * len1 * 0.6f),
            strokeWidth = max(1f, cell * 0.014f),
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        Color.White.copy(alpha = 0.55f),
        Offset(x + cell * 0.24f, y + cell * 0.72f),
        Offset(x + cell * 0.46f, y + cell * 0.30f),
        strokeWidth = max(1f, cell * 0.035f),
        cap = StrokeCap.Round,
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.30f),
        Offset(x + pad, y + pad),
        Size(size, size),
        CornerRadius(cell * 0.14f),
        style = mazeStroke(max(1f, cell * 0.016f)),
    )
}

// ---------------------------------------------------------------------
// 蜂蜜
// ---------------------------------------------------------------------

private fun DrawScope.drawHoneyTile(
    game: MazeGame,
    r: Int,
    c: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    val breathe = if (reduceMotion) 0f else 0.035f * sin(game.time * 3.2f + r * 0.7f + c * 1.3f)
    val scale = 1f + breathe
    val potW = cell * 0.46f * scale
    val potH = cell * 0.42f * scale
    val top = cy - potH * 0.52f
    val left = cx - potW * 0.5f
    // 投影
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - potW * 0.52f, cy + potH * 0.38f), Size(potW * 1.04f, potH * 0.30f))
    // 罐体（下半深色、上半亮色）
    drawRoundRect(honeyShell, Offset(left, top), Size(potW, potH), CornerRadius(cell * 0.16f))
    drawRoundRect(honeyBody, Offset(left, top), Size(potW, potH * 0.72f), CornerRadius(cell * 0.16f))
    // 蜜面
    drawOval(honeyLight, Offset(left + potW * 0.08f, top + potH * 0.10f), Size(potW * 0.84f, potH * 0.26f))
    // 木盖
    drawRoundRect(
        honeyLidWood,
        Offset(cx - potW * 0.56f, top - potH * 0.18f),
        Size(potW * 1.12f, potH * 0.26f),
        CornerRadius(cell * 0.06f),
    )
    drawRoundRect(
        honeyLidDark,
        Offset(cx - potW * 0.56f, top - potH * 0.02f),
        Size(potW * 1.12f, potH * 0.08f),
        CornerRadius(cell * 0.03f),
    )
    // 玻璃高光
    drawOval(
        honeyGlass.copy(alpha = 0.85f),
        Offset(left + potW * 0.16f, top + potH * 0.30f),
        Size(potW * 0.22f, potH * 0.38f),
    )
    // 侧壁蜜滴
    drawCircle(honeyLight, cell * 0.045f, Offset(left + potW * 0.06f, top + potH * 0.86f))
    drawCircle(honeyBody, cell * 0.035f, Offset(left + potW * 0.06f, top + potH * 0.94f))
    drawCircle(honeyLight, cell * 0.03f, Offset(left + potW * 0.10f, top + potH * 1.02f))
    // 星光：两枚旋转闪光
    val tw = if (reduceMotion) 0.6f else 0.5f + 0.5f * sin(game.time * 4.5f + r + c)
    star(Offset(cx - potW * 0.34f, top + potH * 0.18f), cell * 0.09f * (0.8f + 0.3f * tw), Color.White.copy(alpha = 0.55f * tw))
    star(Offset(cx + potW * 0.38f, top + potH * 0.54f), cell * 0.06f * (0.8f + 0.3f * tw), Color(0xFFFFF59D).copy(alpha = 0.7f * tw))
}

// ---------------------------------------------------------------------
// 钥匙 / 锁门
// ---------------------------------------------------------------------

private fun DrawScope.drawKeyTile(
    game: MazeGame,
    r: Int,
    c: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    val bob = if (reduceMotion) 0f else sin(game.time * 2.4f + r + c) * cell * 0.03f
    val ky = cy + bob
    val goldDark = Color(0xFFC08A00)
    val goldMid = Color(0xFFFFC107)
    val goldLight = Color(0xFFFFF176)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - cell * 0.20f, cy + cell * 0.24f), Size(cell * 0.40f, cell * 0.12f))
    // 钥匙环
    drawCircle(goldMid, cell * 0.16f, Offset(cx, ky - cell * 0.12f))
    drawCircle(goldDark, cell * 0.16f, Offset(cx, ky - cell * 0.12f), style = mazeStroke(max(1f, cell * 0.03f)))
    drawCircle(Color(0xFF2A3A4A), cell * 0.075f, Offset(cx, ky - cell * 0.12f))
    // 键杆
    drawRoundRect(
        goldMid,
        Offset(cx - cell * 0.045f, ky - cell * 0.02f),
        Size(cell * 0.09f, cell * 0.34f),
        CornerRadius(cell * 0.03f),
    )
    drawRoundRect(
        goldLight.copy(alpha = 0.7f),
        Offset(cx - cell * 0.035f, ky - cell * 0.01f),
        Size(cell * 0.03f, cell * 0.30f),
        CornerRadius(cell * 0.015f),
    )
    // 齿口
    drawRect(goldDark, Offset(cx + cell * 0.045f, ky + cell * 0.18f), Size(cell * 0.10f, cell * 0.055f))
    drawRect(goldDark, Offset(cx + cell * 0.045f, ky + cell * 0.27f), Size(cell * 0.085f, cell * 0.05f))
    // 微闪
    val tw = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(game.time * 5f + r * 2 + c)
    star(Offset(cx + cell * 0.14f, ky - cell * 0.20f), cell * 0.07f * (0.7f + 0.5f * tw), Color.White.copy(alpha = 0.35f + 0.5f * tw))
}

private fun DrawScope.drawLockTile(
    game: MazeGame,
    r: Int,
    c: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    // 木门
    val doorW = cell * 0.72f
    val doorH = cell * 0.78f
    val dl = cx - doorW * 0.5f
    val dt = cy - doorH * 0.5f
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(dl + cell * 0.02f, cy + doorH * 0.34f), Size(doorW, cell * 0.16f))
    drawRoundRect(woodDark, Offset(dl, dt), Size(doorW, doorH), CornerRadius(cell * 0.07f))
    drawRoundRect(
        woodMid,
        Offset(dl + cell * 0.02f, dt + cell * 0.02f),
        Size(doorW - cell * 0.04f, doorH - cell * 0.06f),
        CornerRadius(cell * 0.06f),
    )
    // 门板纹
    for (k in 1..2) {
        val py = dt + doorH * (0.30f * k)
        drawLine(
            woodDark.copy(alpha = 0.45f),
            Offset(dl + cell * 0.05f, py),
            Offset(dl + doorW - cell * 0.05f, py),
            strokeWidth = max(1f, cell * 0.016f),
        )
    }
    // 铁箍
    drawRect(ironDark, Offset(dl, dt + doorH * 0.10f), Size(doorW, cell * 0.07f))
    drawRect(ironDark, Offset(dl, dt + doorH * 0.78f), Size(doorW, cell * 0.07f))
    for (k in 0 until 3) {
        val px = dl + doorW * (0.18f + 0.32f * k)
        drawCircle(ironLight, cell * 0.022f, Offset(px, dt + doorH * 0.135f))
        drawCircle(ironLight, cell * 0.022f, Offset(px, dt + doorH * 0.815f))
    }
    // 锁片 + 钥匙孔
    drawRoundRect(
        ironMid,
        Offset(cx - cell * 0.13f, cy - cell * 0.14f),
        Size(cell * 0.26f, cell * 0.30f),
        CornerRadius(cell * 0.05f),
    )
    drawRoundRect(
        ironLight.copy(alpha = 0.8f),
        Offset(cx - cell * 0.13f, cy - cell * 0.14f),
        Size(cell * 0.26f, cell * 0.08f),
        CornerRadius(cell * 0.04f),
    )
    drawCircle(Color(0xFF2E3A44), cell * 0.045f, Offset(cx, cy - cell * 0.04f))
    drawRect(Color(0xFF2E3A44), Offset(cx - cell * 0.022f, cy - cell * 0.04f), Size(cell * 0.044f, cell * 0.10f))
    // 链条：三节椭圆环
    for (k in 0 until 3) {
        val lx = dl + doorW * (0.22f + 0.28f * k)
        drawArc(
            color = ironDark,
            startAngle = 200f,
            sweepAngle = 320f,
            useCenter = false,
            topLeft = Offset(lx - cell * 0.05f, cy - cell * 0.06f),
            size = Size(cell * 0.10f, cell * 0.12f),
            style = mazeStroke(max(1f, cell * 0.022f)),
        )
    }
    // 未开锁的轻微晃动（这里用时间让锁片微颤，提示“需要钥匙”）；减弱动画时冻结
    val wobble = if (reduceMotion) 0f else sin(game.time * 5f + r + c) * cell * 0.008f
    drawCircle(Color.White.copy(alpha = 0.25f), cell * 0.03f, Offset(cx - cell * 0.06f + wobble, cy - cell * 0.10f))
}

// ---------------------------------------------------------------------
// 传送门
// ---------------------------------------------------------------------

private fun DrawScope.drawPortalTile(
    game: MazeGame,
    style: MazeThemeStyle,
    portalId: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    val spin = if (reduceMotion) 0f else game.time * 95f + portalId * 41f
    val band =
        when (portalId % 3) {
            1 -> Color(0xFF4DD0E1)
            2 -> Color(0xFFFF8A65)
            else -> Color(0xFFBA68C8)
        }
    // 地面光晕
    drawOval(
        band.copy(alpha = 0.22f),
        Offset(cx - cell * 0.42f, cy + cell * 0.24f),
        Size(cell * 0.84f, cell * 0.26f),
    )
    drawCircle(band.copy(alpha = 0.28f), cell * 0.42f, Offset(cx, cy))
    // 外环：分段旋转
    val segments = 6
    for (i in 0 until segments) {
        drawArc(
            color = band.copy(alpha = 0.30f + 0.12f * ((i + portalId) % 3)),
            startAngle = spin + i * (360f / segments),
            sweepAngle = 360f / segments - 10f,
            useCenter = false,
            topLeft = Offset(cx - cell * 0.36f, cy - cell * 0.36f),
            size = Size(cell * 0.72f, cell * 0.72f),
            style = mazeStroke(max(1f, cell * 0.07f)),
        )
    }
    // 内环反向
    for (i in 0 until 4) {
        drawArc(
            color = Color.White.copy(alpha = 0.45f),
            startAngle = -spin * 1.6f + i * 90f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(cx - cell * 0.22f, cy - cell * 0.22f),
            size = Size(cell * 0.44f, cell * 0.44f),
            style = mazeStroke(max(1f, cell * 0.045f)),
        )
    }
    // 中心旋涡
    drawCircle(style.fx.copy(alpha = 0.55f), cell * 0.14f, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.85f), cell * 0.06f, Offset(cx, cy))
    val vortexBase = (if (reduceMotion) 0f else game.time * 2.4f) + mazeHash01(portalId, 711)
    for (i in 0 until 3) {
        drawArc(
            color = Color.White.copy(alpha = 0.35f),
            startAngle = (vortexBase + i * 2.094f) * 57.29578f,
            sweepAngle = 200f,
            useCenter = false,
            topLeft = Offset(cx - cell * (0.10f + 0.05f * i), cy - cell * (0.10f + 0.05f * i)),
            size = Size(cell * (0.20f + 0.10f * i), cell * (0.20f + 0.10f * i)),
            style = mazeStroke(max(1f, cell * 0.02f)),
        )
    }
    // 门号色点
    drawCircle(band, cell * 0.07f, Offset(cx, cy - cell * 0.36f))
    drawCircle(Color.White.copy(alpha = 0.75f), cell * 0.03f, Offset(cx, cy - cell * 0.36f))
}

// ---------------------------------------------------------------------
// 出口 / 起点
// ---------------------------------------------------------------------

private fun DrawScope.drawExitTile(
    game: MazeGame,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    // 减弱动画：开门相位冻结为全开静态，不播 1s 滑门
    val progress = if (reduceMotion) 1f else doorOpenProgress(game.time, game.lastHoneyAt)
    val pillarW = cell * 0.14f
    val pillarH = cell * 0.80f
    val top = cy - cell * 0.42f
    // 拱门石柱
    drawRoundRect(
        exitStoneDark,
        Offset(cx - cell * 0.40f, top),
        Size(pillarW, pillarH),
        CornerRadius(cell * 0.05f),
    )
    drawRoundRect(
        exitStoneDark,
        Offset(cx + cell * 0.40f - pillarW, top),
        Size(pillarW, pillarH),
        CornerRadius(cell * 0.05f),
    )
    // 拱顶
    drawArc(
        color = exitStone,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - cell * 0.40f, top - cell * 0.12f),
        size = Size(cell * 0.80f, cell * 0.34f),
        style = mazeStroke(pillarW),
    )
    drawArc(
        color = exitStoneDark.copy(alpha = 0.6f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - cell * 0.40f, top - cell * 0.12f),
        size = Size(cell * 0.80f, cell * 0.34f),
        style = mazeStroke(max(1f, cell * 0.02f)),
    )
    // 拱心石
    drawRoundRect(
        exitGold.copy(alpha = 0.85f),
        Offset(cx - cell * 0.06f, top - cell * 0.20f),
        Size(cell * 0.12f, cell * 0.10f),
        CornerRadius(cell * 0.03f),
    )
    // 门扇：随 doorOpenProgress 上滑收起
    val doorH = cell * 0.68f
    val doorTop = top + cell * 0.10f
    if (progress < 1f) {
        val dy = -progress * doorH * 1.05f
        drawRoundRect(
            exitWoodDark,
            Offset(cx - cell * 0.30f, doorTop + dy),
            Size(cell * 0.60f, doorH),
            CornerRadius(cell * 0.06f),
        )
        drawRoundRect(
            exitWood,
            Offset(cx - cell * 0.28f, doorTop + cell * 0.02f + dy),
            Size(cell * 0.56f, doorH - cell * 0.05f),
            CornerRadius(cell * 0.05f),
        )
        for (k in 1..2) {
            val py = doorTop + doorH * (0.30f * k) + dy
            drawLine(
                exitWoodDark.copy(alpha = 0.5f),
                Offset(cx - cell * 0.24f, py),
                Offset(cx + cell * 0.24f, py),
                strokeWidth = max(1f, cell * 0.016f),
            )
        }
        drawCircle(exitGold, cell * 0.055f, Offset(cx + cell * 0.16f, doorTop + doorH * 0.52f + dy))
        drawRect(
            ironDark.copy(alpha = 0.35f),
            Offset(cx - cell * 0.30f, doorTop + doorH * 0.96f + dy),
            Size(cell * 0.60f, cell * 0.04f),
        )
    } else {
        // 门洞：内侧亮光 + 地垫
        drawRoundRect(
            Color(0xFFB9F6CA).copy(alpha = 0.55f),
            Offset(cx - cell * 0.28f, doorTop),
            Size(cell * 0.56f, doorH),
            CornerRadius(cell * 0.06f),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.35f),
            Offset(cx - cell * 0.20f, doorTop + cell * 0.06f),
            Size(cell * 0.40f, doorH * 0.72f),
            CornerRadius(cell * 0.05f),
        )
        if (!reduceMotion) {
            val p = (game.time * 0.8f) % 1f
            drawOval(
                Color.White.copy(alpha = 0.35f * (1f - p)),
                Offset(cx - cell * 0.22f, cy + cell * 0.22f),
                Size(cell * 0.44f * (0.6f + p), cell * 0.12f * (0.6f + p)),
            )
        }
    }
    // 石柱青苔点
    drawCircle(Color(0xFF6E9C5A).copy(alpha = 0.5f), cell * 0.03f, Offset(cx - cell * 0.35f, top + pillarH * 0.8f))
    drawCircle(Color(0xFF6E9C5A).copy(alpha = 0.4f), cell * 0.025f, Offset(cx + cell * 0.33f, top + pillarH * 0.6f))
}

private fun DrawScope.drawStartTile(
    game: MazeGame,
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    cx: Float,
    cy: Float,
    cell: Float,
    reduceMotion: Boolean,
) {
    val pulse = if (reduceMotion) 0f else 0.5f + 0.5f * sin(game.time * 2.2f)
    // 地垫
    drawRoundRect(
        style.accent.copy(alpha = 0.20f),
        Offset(cx - cell * 0.34f, cy - cell * 0.34f),
        Size(cell * 0.68f, cell * 0.68f),
        CornerRadius(cell * 0.14f),
    )
    drawRoundRect(
        style.accent.copy(alpha = 0.35f + 0.15f * pulse),
        Offset(cx - cell * 0.34f, cy - cell * 0.34f),
        Size(cell * 0.68f, cell * 0.68f),
        CornerRadius(cell * 0.14f),
        style = mazeStroke(max(1f, cell * 0.02f)),
    )
    // 小熊爪印
    val padColor = style.wallDeep.copy(alpha = 0.55f)
    drawOval(padColor, Offset(cx - cell * 0.11f, cy - cell * 0.02f), Size(cell * 0.22f, cell * 0.18f))
    for (i in 0 until 4) {
        val tx = cx + (i - 1.5f) * cell * 0.09f
        drawCircle(padColor, cell * 0.045f, Offset(tx, cy - cell * 0.18f))
    }
    // 站在起点时地垫发亮
    if (game.bearR == r && game.bearC == c) {
        drawRoundRect(
            style.accent.copy(alpha = 0.25f + 0.20f * pulse),
            Offset(cx - cell * 0.40f, cy - cell * 0.40f),
            Size(cell * 0.80f, cell * 0.80f),
            CornerRadius(cell * 0.16f),
            style = mazeStroke(max(1f, cell * 0.025f)),
        )
    }
}

// ---------------------------------------------------------------------
// 提示浮标
// ---------------------------------------------------------------------

/** 提示方向：在目标格画一枚脉动箭头。 */
internal fun DrawScope.drawMazeHint(
    game: MazeGame,
    dir: MazeDir?,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (dir == null) return
    val cell = layout.cell
    if (cell <= 0f) return
    val tr = game.bearR + dir.dr
    val tc = game.bearC + dir.dc
    if (tr !in 0 until game.rows || tc !in 0 until game.cols) return
    val cx = layout.ox + (tc + 0.5f) * cell
    val cy = layout.oy + (tr + 0.5f) * cell
    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(game.time * 5f)
    drawCircle(Color(0xFFFFD54F).copy(alpha = 0.18f + 0.14f * pulse), cell * (0.34f + 0.06f * pulse), Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.5f), cell * 0.30f, Offset(cx, cy), style = mazeStroke(max(1f, cell * 0.03f)))
    val ux = dir.dc.toFloat()
    val uy = dir.dr.toFloat()
    val px = -uy
    val py = ux
    val tip = cell * 0.16f
    val back = -cell * 0.10f
    drawLine(
        Color(0xFFFFB300),
        Offset(cx + ux * back - px * tip * 0.6f, cy + uy * back - py * tip * 0.6f),
        Offset(cx + ux * (back + tip * 1.6f), cy + uy * (back + tip * 1.6f)),
        strokeWidth = max(1f, cell * 0.05f),
        cap = StrokeCap.Round,
    )
    drawLine(
        Color(0xFFFFB300),
        Offset(cx + ux * back + px * tip * 0.6f, cy + uy * back + py * tip * 0.6f),
        Offset(cx + ux * (back + tip * 1.6f), cy + uy * (back + tip * 1.6f)),
        strokeWidth = max(1f, cell * 0.05f),
        cap = StrokeCap.Round,
    )
}

/** 四向尖角星闪光。 */
private fun DrawScope.star(
    center: Offset,
    radius: Float,
    color: Color,
) {
    if (radius <= 0f) return
    drawLine(
        color,
        Offset(center.x - radius, center.y),
        Offset(center.x + radius, center.y),
        strokeWidth = max(1f, radius * 0.28f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color,
        Offset(center.x, center.y - radius),
        Offset(center.x, center.y + radius),
        strokeWidth = max(1f, radius * 0.28f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color.copy(alpha = color.alpha * 0.55f),
        Offset(center.x - radius * 0.6f, center.y - radius * 0.6f),
        Offset(center.x + radius * 0.6f, center.y + radius * 0.6f),
        strokeWidth = max(1f, radius * 0.18f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color.copy(alpha = color.alpha * 0.55f),
        Offset(center.x - radius * 0.6f, center.y + radius * 0.6f),
        Offset(center.x + radius * 0.6f, center.y - radius * 0.6f),
        strokeWidth = max(1f, radius * 0.18f),
        cap = StrokeCap.Round,
    )
}

/** 传送出发点的残留旋涡：小熊已被吸入的收束环。 */
internal fun DrawScope.drawPortalGhost(
    fx: MazeFxHost,
    game: MazeGame,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val age = game.time - fx.portalAt
    if (age < 0f || age > 0.42f) return
    val cell = layout.cell
    if (cell <= 0f) return
    if (fx.portalFromR !in 0 until game.rows || fx.portalFromC !in 0 until game.cols) return
    val k = age / 0.42f
    val cx = layout.ox + (fx.portalFromC + 0.5f) * cell
    val cy = layout.oy + (fx.portalFromR + 0.5f) * cell
    drawCircle(
        style.fx.copy(alpha = 0.55f * (1f - k)),
        cell * (0.14f + 0.34f * k),
        Offset(cx, cy),
        style = mazeStroke(max(1f, cell * 0.04f * (1f - k))),
    )
    drawCircle(
        Color.White.copy(alpha = 0.65f * (1f - k)),
        cell * 0.10f * (1f - k * 0.6f),
        Offset(cx, cy),
    )
}

/** 开锁瞬间的门影：短暂保留一扇正在碎裂/淡出的门板。 */
internal fun DrawScope.drawUnlockGhost(
    fx: MazeFxHost,
    game: MazeGame,
    layout: MazeBoardLayout,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val age = game.time - fx.unlockAt
    if (age < 0f || age > 0.45f) return
    val cell = layout.cell
    if (cell <= 0f) return
    val k = 1f - age / 0.45f
    val cx = layout.ox + (fx.unlockC + 0.5f) * cell
    val cy = layout.oy + (fx.unlockR + 0.5f) * cell
    val w = cell * 0.66f * (0.7f + 0.3f * k)
    val h = cell * 0.72f * (0.7f + 0.3f * k)
    drawRoundRect(
        woodMid.copy(alpha = 0.55f * k),
        Offset(cx - w * 0.5f, cy - h * 0.5f),
        Size(w, h),
        CornerRadius(cell * 0.06f),
    )
    drawLine(
        Color.White.copy(alpha = 0.7f * k),
        Offset(cx - w * 0.5f, cy - h * 0.5f),
        Offset(cx + w * 0.5f, cy + h * 0.5f),
        strokeWidth = max(1f, cell * 0.02f),
    )
    drawRect(
        ironLight.copy(alpha = 0.5f * k),
        Offset(cx - cell * 0.05f, cy - cell * 0.04f),
        Size(cell * 0.10f, cell * 0.08f),
    )
}
