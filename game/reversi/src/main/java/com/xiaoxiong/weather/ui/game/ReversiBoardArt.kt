package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

internal data class ReversiBoardLayout(
    val left: Float,
    val top: Float,
    val side: Float,
    val cell: Float,
)

internal fun reversiBoardLayout(
    width: Float,
    height: Float,
): ReversiBoardLayout {
    val side = min(width, height)
    val left = (width - side) / 2f
    val top = (height - side) / 2f
    return ReversiBoardLayout(left, top, side, side / REVERSI_SIZE)
}

/** 点中棋盘格则返回下标；点在木框/空白处返回 null（不夹到边线格）。 */
internal fun reversiHitIndex(
    layout: ReversiBoardLayout,
    x: Float,
    y: Float,
): Int? {
    if (x < layout.left || y < layout.top) return null
    if (x >= layout.left + layout.side || y >= layout.top + layout.side) return null
    val col = ((x - layout.left) / layout.cell).toInt()
    val row = ((y - layout.top) / layout.cell).toInt()
    if (row !in 0 until REVERSI_SIZE || col !in 0 until REVERSI_SIZE) return null
    return reversiIndex(row, col)
}

private val FRAME_DARK = Color(0xFF3D2817)
private val FRAME_MID = Color(0xFF6B4423)
private val FRAME_LIGHT = Color(0xFF8F5E32)
private val FELT_TOP = Color(0xFF357A55)
private val FELT_BOTTOM = Color(0xFF245A3D)
private val GRID_LINE = Color(0xFF163628)
private val GRID_LINE_SOFT = Color(0xFF2E6B4A)
private val HOSHI = Color(0xFF1A4530)
private val DISC_BLACK = Color(0xFF141A1F)
private val DISC_BLACK_EDGE = Color(0xFF2A3238)
private val DISC_WHITE = Color(0xFFF4EFE6)
private val DISC_WHITE_EDGE = Color(0xFFB8AEA0)
private val LEGAL_GHOST = Color(0xFF9AE8B0)
private val LAST_MOVE_RING = Color(0xFFFFCA28)

internal fun DrawScope.drawReversiBoard(
    layout: ReversiBoardLayout,
    board: IntArray,
    legal: Set<Int>,
    lastMove: Int,
    showLegalHints: Boolean,
) {
    val left = layout.left
    val top = layout.top
    val side = layout.side
    val cell = layout.cell
    val frame = cell * 0.42f
    val outerLeft = left - frame
    val outerTop = top - frame
    val outerSide = side + frame * 2f
    val corner = cell * 0.22f

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
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(outerLeft + frame * 0.15f, outerTop + frame * 0.2f),
        size = Size(outerSide - frame * 0.3f, outerSide - frame * 0.25f),
        cornerRadius = CornerRadius(corner * 0.85f, corner * 0.85f),
    )
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(FELT_TOP, FELT_BOTTOM),
                startY = top,
                endY = top + side,
            ),
        topLeft = Offset(left, top),
        size = Size(side, side),
        cornerRadius = CornerRadius(corner * 0.35f, corner * 0.35f),
    )

    val hoshiRadius = cell * 0.07f
    listOf(2 to 2, 2 to 5, 5 to 2, 5 to 5).forEach { (r, c) ->
        val hx = left + (c + 0.5f) * cell
        val hy = top + (r + 0.5f) * cell
        drawCircle(HOSHI, hoshiRadius, Offset(hx, hy))
    }

    val lineW = (cell * 0.045f).coerceIn(1.2f, 2.8f)
    for (i in 0..REVERSI_SIZE) {
        val x = left + i * cell
        val y = top + i * cell
        val edge = i == 0 || i == REVERSI_SIZE
        val color = if (edge) GRID_LINE else GRID_LINE_SOFT
        val w = if (edge) lineW * 1.15f else lineW
        drawLine(color, Offset(left, y), Offset(left + side, y), strokeWidth = w)
        drawLine(color, Offset(x, top), Offset(x, top + side), strokeWidth = w)
    }

    for (i in 0 until REVERSI_CELLS) {
        val r = reversiRow(i)
        val c = reversiCol(i)
        val cx = left + (c + 0.5f) * cell
        val cy = top + (r + 0.5f) * cell
        val center = Offset(cx, cy)
        val disc = board[i]
        if (showLegalHints && i in legal) {
            drawCircle(LEGAL_GHOST.copy(alpha = 0.22f), cell * 0.38f, center)
            drawCircle(LEGAL_GHOST.copy(alpha = 0.55f), cell * 0.11f, center)
        }
        if (i == lastMove) {
            drawCircle(
                LAST_MOVE_RING.copy(alpha = 0.55f),
                cell * 0.47f,
                center,
                style = Stroke(width = cell * 0.07f),
            )
        }
        when (disc) {
            REVERSI_BLACK -> drawReversiDisc(center, cell * 0.42f, black = true)
            REVERSI_WHITE -> drawReversiDisc(center, cell * 0.42f, black = false)
        }
    }
}

private fun DrawScope.drawReversiDisc(
    center: Offset,
    radius: Float,
    black: Boolean,
) {
    val shadow = Offset(radius * 0.08f, radius * 0.14f)
    drawCircle(Color.Black.copy(alpha = 0.28f), radius * 1.02f, center + shadow)
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
