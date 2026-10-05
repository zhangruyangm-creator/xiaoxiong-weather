package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal data class GomokuBoardLayout(
    val left: Float,
    val top: Float,
    val side: Float,
    val cell: Float,
)

internal fun gomokuBoardLayout(
    width: Float,
    height: Float,
): GomokuBoardLayout {
    val side = min(width, height)
    val left = (width - side) / 2f
    val top = (height - side) / 2f
    return GomokuBoardLayout(left, top, side, side / (GOMOKU_SIZE - 1))
}

/** 点中交叉点则返回下标；偏离交叉点超过半格或点在棋盘外返回 null。 */
internal fun gomokuHitIndex(
    layout: GomokuBoardLayout,
    x: Float,
    y: Float,
): Int? {
    val col = ((x - layout.left) / layout.cell)
    val row = ((y - layout.top) / layout.cell)
    val ci = col.roundToInt()
    val ri = row.roundToInt()
    if (ri !in 0 until GOMOKU_SIZE || ci !in 0 until GOMOKU_SIZE) return null
    if (abs(col - ci) > 0.5f || abs(row - ri) > 0.5f) return null
    return gomokuIndex(ri, ci)
}

private val FRAME_DARK = Color(0xFF3D2817)
private val FRAME_MID = Color(0xFF6B4423)
private val FRAME_LIGHT = Color(0xFF8F5E32)
private val FELT_TOP = Color(0xFFE8D5A8)
private val FELT_BOTTOM = Color(0xFFC9A962)
private val GRID_LINE = Color(0xFF6B5344)
private val HOSHI = Color(0xFF5C4A3A)
private val DISC_BLACK = Color(0xFF141A1F)
private val DISC_BLACK_EDGE = Color(0xFF2A3238)
private val DISC_WHITE = Color(0xFFF4EFE6)
private val DISC_WHITE_EDGE = Color(0xFFB8AEA0)
private val LEGAL_GHOST = Color(0xFF4CAF50)
private val LAST_MOVE_RING = Color(0xFFFF7043)

private val GOMOKU_HOSHI =
    listOf(
        3 to 3,
        3 to 11,
        7 to 7,
        11 to 3,
        11 to 11,
    )

internal fun DrawScope.drawGomokuBoard(
    layout: GomokuBoardLayout,
    board: IntArray,
    legal: Set<Int>,
    lastMove: Int,
    showLegalHints: Boolean,
) {
    val left = layout.left
    val top = layout.top
    val side = layout.side
    val cell = layout.cell
    val frame = cell * 0.55f
    val outerLeft = left - frame
    val outerTop = top - frame
    val outerSide = side + frame * 2f
    val corner = cell * 0.35f

    drawRoundRect(
        brush =
            Brush.linearGradient(
                colors = listOf(FRAME_LIGHT, FRAME_MID, FRAME_DARK),
                start = Offset(outerLeft, outerTop),
                end = Offset(outerLeft + outerSide, outerTop + outerSide),
            ),
        topLeft = Offset(outerLeft, outerTop),
        size = Size(outerSide, outerSide),
        cornerRadius = CornerRadius(corner, corner),
    )
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(FELT_TOP, FELT_BOTTOM),
                startY = top - cell * 0.5f,
                endY = top + side + cell * 0.5f,
            ),
        topLeft = Offset(left - cell * 0.5f, top - cell * 0.5f),
        size = Size(side + cell, side + cell),
        cornerRadius = CornerRadius(corner * 0.2f, corner * 0.2f),
    )

    val hoshiR = cell * 0.11f
    GOMOKU_HOSHI.forEach { (r, c) ->
        val hx = left + c * cell
        val hy = top + r * cell
        drawCircle(HOSHI, hoshiR, Offset(hx, hy))
    }

    val lineW = (cell * 0.06f).coerceIn(0.8f, 2.2f)
    for (i in 0 until GOMOKU_SIZE) {
        val x = left + i * cell
        val y = top + i * cell
        drawLine(GRID_LINE, Offset(left, y), Offset(left + side, y), strokeWidth = lineW)
        drawLine(GRID_LINE, Offset(x, top), Offset(x, top + side), strokeWidth = lineW)
    }

    for (i in 0 until GOMOKU_CELLS) {
        val r = gomokuRow(i)
        val c = gomokuCol(i)
        val cx = left + c * cell
        val cy = top + r * cell
        val center = Offset(cx, cy)
        val disc = board[i]
        if (showLegalHints && i in legal) {
            drawCircle(LEGAL_GHOST.copy(alpha = 0.18f), cell * 0.32f, center)
            drawCircle(LEGAL_GHOST.copy(alpha = 0.45f), cell * 0.08f, center)
        }
        if (i == lastMove) {
            drawCircle(
                LAST_MOVE_RING.copy(alpha = 0.6f),
                cell * 0.48f,
                center,
                style = Stroke(width = cell * 0.08f),
            )
        }
        when (disc) {
            GOMOKU_BLACK -> drawGomokuDisc(center, cell * 0.44f, black = true)
            GOMOKU_WHITE -> drawGomokuDisc(center, cell * 0.44f, black = false)
        }
    }
}

private fun DrawScope.drawGomokuDisc(
    center: Offset,
    radius: Float,
    black: Boolean,
) {
    val shadow = Offset(radius * 0.07f, radius * 0.12f)
    drawCircle(Color.Black.copy(alpha = 0.22f), radius * 1.02f, center + shadow)
    if (black) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3A454D), DISC_BLACK, Color(0xFF050708)),
                    center = center + Offset(-radius * 0.35f, -radius * 0.35f),
                    radius = radius * 1.35f,
                ),
            radius = radius,
            center = center,
        )
        drawCircle(DISC_BLACK_EDGE.copy(alpha = 0.35f), radius, center, style = Stroke(width = radius * 0.06f))
    } else {
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.White, DISC_WHITE, Color(0xFFD8CEBF)),
                    center = center + Offset(-radius * 0.4f, -radius * 0.45f),
                    radius = radius * 1.4f,
                ),
            radius = radius,
            center = center,
        )
        drawCircle(DISC_WHITE_EDGE.copy(alpha = 0.45f), radius, center, style = Stroke(width = radius * 0.05f))
    }
}
