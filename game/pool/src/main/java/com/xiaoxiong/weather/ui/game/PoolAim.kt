package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------------
// 瞄准：球杆 / 幽灵球 / 辅助线
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolAim(
    game: PoolGame,
    aids: PoolGame.Aids,
) {
    if (game.phase != PoolGame.Phase.AIMING) return
    val cue = game.cueBall() ?: return
    if (!cue.onTable || cue.potted) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val ballR = game.table.ballRadius * scale
    if (ballR < 0.5f) return

    val cx = game.toCanvasX(cue.x)
    val cy = game.toCanvasY(cue.y)
    val angle = game.aimAngle
    val dx = cos(angle)
    val dy = sin(angle)

    if (game.placingCue || game.ballInHand) {
        drawPlacementPulse(cx, cy, ballR, game.time)
    }

    when (aids) {
        PoolGame.Aids.OFF -> Unit
        PoolGame.Aids.GHOST -> drawAimAssist(game, cue, cx, cy, ballR, dx, dy, full = false)
        PoolGame.Aids.FULL -> drawAimAssist(game, cue, cx, cy, ballR, dx, dy, full = true)
    }

    // 弧线暗示：抬杆 + 侧旋时，在瞄准方向一侧画一段弯曲提示
    if (game.elevation > 0.01f && abs(game.spin.side) > 0.01f) {
        drawCurveHint(cx, cy, ballR, dx, dy, game.elevation, game.spin.side)
    }

    drawCueStick(cx, cy, ballR, angle, dx, dy, game.power, game.spin)
}

/** 弧线瞄准暗示：从白球出发、向侧旋一侧弯曲的虚线弧与端点。 */
private fun DrawScope.drawCurveHint(
    cx: Float,
    cy: Float,
    ballR: Float,
    dx: Float,
    dy: Float,
    elevation: Float,
    side: Float,
) {
    val elev = elevation.coerceIn(0f, 1f)
    val sign = if (side >= 0f) 1f else -1f
    val perpX = -dy
    val perpY = dx
    val len = ballR * (2.6f + 3.2f * elev)
    val bow = ballR * (1.3f + 2.4f * elev) * sign
    val path = PoolRenderCache.curveHint
    path.reset()
    val steps = 10
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val along = len * t
        val lateral = bow * t * t
        val px = cx + dx * along + perpX * lateral
        val py = cy + dy * along + perpY * lateral
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(
        path,
        curveHintColor.copy(alpha = 0.55f),
        style = Stroke(width = ballR * 0.08f, cap = StrokeCap.Round),
    )
    val tipX = cx + dx * len + perpX * bow
    val tipY = cy + dy * len + perpY * bow
    drawCircle(curveHintColor.copy(alpha = 0.80f), ballR * 0.13f, Offset(tipX, tipY))
}

private fun DrawScope.drawCueStick(
    cx: Float,
    cy: Float,
    ballR: Float,
    angle: Float,
    dx: Float,
    dy: Float,
    power: Float,
    spin: PoolGame.Spin,
) {
    val backX = -dx
    val backY = -dy
    val pull = ballR * (1.35f + power * 6.5f)
    val tipX = cx + backX * pull
    val tipY = cy + backY * pull
    val stickLen = ballR * 18f
    val buttX = tipX + backX * stickLen
    val buttY = tipY + backY * stickLen
    val perpX = -backY
    val perpY = backX
    val tipW = ballR * 0.15f
    val buttW = ballR * 0.42f

    // 局部坐标构建杆身（x 沿皮头→杆尾），笔刷按杆长缓存后用 translate/rotate 落到世界坐标
    val path = PoolRenderCache.shape
    path.reset()
    path.moveTo(0f, tipW)
    path.lineTo(stickLen, buttW)
    path.lineTo(stickLen, -buttW)
    path.lineTo(0f, -tipW)
    path.close()
    val stickDeg = atan2(backY, backX) * 180f / PI.toFloat()
    withTransform({
        translate(tipX, tipY)
        rotate(degrees = stickDeg, pivot = Offset.Zero)
    }) {
        drawPath(path, cueStickBrushes.get(stickLen))
    }
    // 前节白箍
    val ferruleX = tipX + backX * ballR * 1.6f
    val ferruleY = tipY + backY * ballR * 1.6f
    drawLine(
        cueFerrule.copy(alpha = 0.9f),
        Offset(tipX, tipY),
        Offset(ferruleX, ferruleY),
        strokeWidth = ballR * 0.26f,
        cap = StrokeCap.Round,
    )
    // 皮头
    drawCircle(cueTipBlue, tipW * 1.35f, Offset(tipX, tipY))
    drawCircle(Color.White.copy(alpha = 0.25f), tipW * 0.6f, Offset(tipX - perpX * tipW * 0.4f, tipY - perpY * tipW * 0.4f))
    // 杆身高光
    drawLine(
        Color.White.copy(alpha = 0.22f),
        Offset(tipX + perpX * tipW * 0.4f, tipY + perpY * tipW * 0.4f),
        Offset(buttX + perpX * buttW * 0.45f, buttY + perpY * buttW * 0.45f),
        strokeWidth = ballR * 0.06f,
        cap = StrokeCap.Round,
    )
    // 杆法接触点提示
    if (spin.side != 0f || spin.vertical != 0f) {
        val sx = cx + perpX * spin.side * ballR * 0.55f + dx * spin.vertical * ballR * 0.55f
        val sy = cy + perpY * spin.side * ballR * 0.55f + dy * spin.vertical * ballR * 0.55f
        drawCircle(Color.White.copy(alpha = 0.55f), ballR * 0.15f, Offset(sx, sy))
        drawCircle(Color(0xFF37474F).copy(alpha = 0.75f), ballR * 0.08f, Offset(sx, sy))
    }
}

/**
 * 白球碰后分离线方向（单位向量）：白球切线速度 `v - (v·n)n` 的方向。
 * [targetDirX]/[targetDirY] 为幽灵球指向目标球的单位法线 n，入射方向取当前白球到
 * 幽灵球的方向，因此分离符号为 `-(cue - ghost)·perp(n)`（perp 为 n 的 90° 法向）。
 */
internal fun cueSeparationDirection(
    targetDirX: Float,
    targetDirY: Float,
    cueX: Float,
    cueY: Float,
    ghostX: Float,
    ghostY: Float,
): Pair<Float, Float> {
    val perpX = -targetDirY
    val perpY = targetDirX
    val sign = if ((cueX - ghostX) * perpX + (cueY - ghostY) * perpY >= 0f) -1f else 1f
    return perpX * sign to perpY * sign
}

private fun DrawScope.drawAimAssist(
    game: PoolGame,
    cue: PoolBall,
    cx: Float,
    cy: Float,
    ballR: Float,
    dx: Float,
    dy: Float,
    full: Boolean,
) {
    val table = game.table
    val scale = game.viewScale
    val fc = PoolPhysics.predictFirstContact(game.balls, table, cue, game.aimAngle)
    if (fc != null && fc.target != null) {
        val ghostX = game.toCanvasX(fc.x)
        val ghostY = game.toCanvasY(fc.y)
        val target = fc.target
        // 首碰是否合法：非法时幽灵球 / 引导线 / 目标线统一切警示色
        val legalContact = PoolRules.isLegalFirstContact(game.match, game.balls, target)
        val ghostStroke = if (legalContact) ghostRing else aimWarnColor
        val ghostInnerStroke = if (legalContact) ghostInner else aimWarnColor.copy(alpha = 0.55f)
        val cueGuideColor = if (legalContact) aimLineColor else aimWarnColor
        val targetGuideColor = if (legalContact) targetLineColor else aimWarnColor
        val potArrowColor = if (legalContact) potLineColor else aimWarnColor
        val breakArrowColor = if (legalContact) cueBreakColor else aimWarnColor

        if (full) {
            drawGuideLine(
                Offset(cx, cy),
                Offset(ghostX, ghostY),
                cueGuideColor,
                ballR * 0.10f,
                ballR * 0.60f,
                ballR * 0.48f,
            )
        }

        // 幽灵球：外发光 + 高亮描边，命中点更醒目
        drawCircle(ghostFill, ballR, Offset(ghostX, ghostY))
        drawCircle(ghostStroke.copy(alpha = 0.20f), ballR * 1.16f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.18f))
        drawCircle(ghostStroke, ballR, Offset(ghostX, ghostY), style = Stroke(ballR * 0.14f))
        drawCircle(ghostInnerStroke, ballR * 0.55f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.06f))

        // 目标球路径：预测到袋口 / 其它球 / 库边（与物理同一套捕获判定，画到哪就打到哪）
        val tvx = target.x - fc.x
        val tvy = target.y - fc.y
        val tl = hypot(tvx, tvy)
        if (tl > 1e-4f) {
            val ux = tvx / tl
            val uy = tvy / tl
            val startX = game.toCanvasX(target.x)
            val startY = game.toCanvasY(target.y)
            val path =
                PoolPhysics.predictObjectPath(
                    game.balls,
                    table,
                    target.x,
                    target.y,
                    ux,
                    uy,
                    target.radius,
                    target,
                )
            val pathEndX = game.toCanvasX(path.endX)
            val pathEndY = game.toCanvasY(path.endY)
            if (path.pocket != null) {
                // 预测落袋：实线 + 袋口高亮圈
                drawArrowLine(
                    Offset(startX, startY),
                    Offset(pathEndX, pathEndY),
                    potArrowColor,
                    ballR * 0.12f,
                    ballR * 0.90f,
                )
                val holeR = max(path.pocket.radius * scale * 1.15f, ballR * 1.9f)
                val holeX = game.toCanvasX(path.pocket.x)
                val holeY = game.toCanvasY(path.pocket.y)
                drawCircle(potRingColor.copy(alpha = 0.20f), holeR, Offset(holeX, holeY))
                drawCircle(potRingColor, holeR, Offset(holeX, holeY), style = Stroke(ballR * 0.16f))
            } else {
                drawGuideLine(
                    Offset(startX, startY),
                    Offset(pathEndX, pathEndY),
                    targetGuideColor,
                    ballR * 0.10f,
                    ballR * 0.60f,
                    ballR * 0.48f,
                )
                val blocker = path.blocker
                if (full && blocker != null) {
                    // 被其它球挡住：给出被撞球沿两球心连线的分离方向
                    val bx = blocker.x - path.endX
                    val by = blocker.y - path.endY
                    val bl = hypot(bx, by)
                    if (bl > 1e-4f) {
                        drawArrowLine(
                            Offset(pathEndX, pathEndY),
                            Offset(pathEndX + bx / bl * ballR * 7f, pathEndY + by / bl * ballR * 7f),
                            breakArrowColor,
                            ballR * 0.10f,
                            ballR * 0.70f,
                        )
                    }
                } else if (full && path.railDistance != null && path.distance > 1e-3f) {
                    val rail = nearestRail(table, path.endX, path.endY)
                    if (rail != null) {
                        val (nx, ny) = PoolPhysics.railInwardNormal(rail, table)
                        val dot = ux * nx + uy * ny
                        val rdx = ux - 2f * dot * nx
                        val rdy = uy - 2f * dot * ny
                        drawGuideLine(
                            Offset(pathEndX, pathEndY),
                            Offset(
                                game.toCanvasX(path.endX + rdx * ballR * 8f),
                                game.toCanvasY(path.endY + rdy * ballR * 8f),
                            ),
                            reflectLineColor,
                            ballR * 0.09f,
                            ballR * 0.60f,
                            ballR * 0.50f,
                        )
                    }
                }
            }

            if (full) {
                // 白球分离线（切线方向，符号取白球碰后实际切线速度方向）
                val (sepX, sepY) = cueSeparationDirection(ux, uy, cue.x, cue.y, fc.x, fc.y)
                // 沿白球分离方向预测白球路径：终点是袋口则预警「这一杆白球会落袋」
                val cuePath =
                    PoolPhysics.predictObjectPath(
                        game.balls,
                        table,
                        fc.x,
                        fc.y,
                        sepX,
                        sepY,
                        cue.radius,
                        cue,
                    )
                val scratchPocket = cuePath.pocket
                val sepColor = if (scratchPocket != null || !legalContact) aimWarnColor else cueBreakColor
                drawGuideLine(
                    Offset(ghostX, ghostY),
                    Offset(ghostX + sepX * ballR * 6f, ghostY + sepY * ballR * 6f),
                    sepColor,
                    ballR * 0.10f,
                    ballR * 0.55f,
                    ballR * 0.45f,
                )
                if (scratchPocket != null) {
                    val holeR = max(scratchPocket.radius * scale * 1.15f, ballR * 1.9f)
                    val holeX = game.toCanvasX(scratchPocket.x)
                    val holeY = game.toCanvasY(scratchPocket.y)
                    drawCircle(aimWarnColor.copy(alpha = 0.20f), holeR, Offset(holeX, holeY))
                    drawCircle(aimWarnColor, holeR, Offset(holeX, holeY), style = Stroke(ballR * 0.16f))
                }
            }
        }
        return
    }

    // 无有效目标：瞄准线到库，并做一次反射预测
    val tRail = PoolPhysics.raycastRail(cue.x, cue.y, dx, dy, table, table.height * 2f)
    val endTableX = if (tRail != null) cue.x + dx * tRail else cue.x + dx * table.height * 2f
    val endTableY = if (tRail != null) cue.y + dy * tRail else cue.y + dy * table.height * 2f
    val endX = game.toCanvasX(endTableX)
    val endY = game.toCanvasY(endTableY)
    drawGuideLine(Offset(cx, cy), Offset(endX, endY), aimLineColor, ballR * 0.10f, ballR * 0.60f, ballR * 0.48f)

    if (full && tRail != null) {
        val rail = nearestRail(table, endTableX, endTableY)
        if (rail != null) {
            val (nx, ny) = PoolPhysics.railInwardNormal(rail, table)
            val dot = dx * nx + dy * ny
            val rdx = dx - 2f * dot * nx
            val rdy = dy - 2f * dot * ny
            drawGuideLine(
                Offset(endX, endY),
                Offset(game.toCanvasX(endTableX + rdx * ballR * 8f), game.toCanvasY(endTableY + rdy * ballR * 8f)),
                reflectLineColor,
                ballR * 0.09f,
                ballR * 0.60f,
                ballR * 0.50f,
            )
        }
    }
}

private fun nearestRail(
    table: PoolTable,
    x: Float,
    y: Float,
): PoolRail? {
    var best: PoolRail? = null
    var bestD = Float.MAX_VALUE
    for (rail in table.rails) {
        val dx = rail.x2 - rail.x1
        val dy = rail.y2 - rail.y1
        val len2 = dx * dx + dy * dy
        val d =
            if (len2 < 1e-9f) {
                hypot(x - rail.x1, y - rail.y1)
            } else {
                val t = (((x - rail.x1) * dx + (y - rail.y1) * dy) / len2).coerceIn(0f, 1f)
                hypot(x - (rail.x1 + dx * t), y - (rail.y1 + dy * t))
            }
        if (d < bestD) {
            bestD = d
            best = rail
        }
    }
    return best
}

private fun DrawScope.drawPlacementPulse(
    cx: Float,
    cy: Float,
    ballR: Float,
    time: Float,
) {
    val pulse = 0.5f + 0.5f * sin(time * 5f)
    drawCircle(
        placeRing.copy(alpha = 0.16f + 0.20f * pulse),
        ballR * (2.4f + pulse * 0.5f),
        Offset(cx, cy),
    )
    drawCircle(
        Color.White.copy(alpha = 0.60f),
        ballR * 1.45f,
        Offset(cx, cy),
        style = Stroke(ballR * 0.14f),
    )
}

// ---------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------

/** 虚线辅助线 + 两端小圆端点，用于瞄准提示，使起止点更清晰。 */
private fun DrawScope.drawGuideLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    dash: Float,
    gap: Float,
) {
    drawDashedLine(from, to, color, strokeWidth, dash, gap)
    val dot = strokeWidth * 1.25f
    drawCircle(color, dot, from)
    drawCircle(color, dot, to)
}

private fun DrawScope.drawArrowLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    headLen: Float,
) {
    drawLine(color, from, to, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    val ang = atan2(to.y - from.y, to.x - from.x)
    val path = PoolRenderCache.shape
    path.reset()
    path.moveTo(to.x, to.y)
    path.lineTo(to.x - cos(ang - 0.5f) * headLen, to.y - sin(ang - 0.5f) * headLen)
    path.lineTo(to.x - cos(ang + 0.5f) * headLen, to.y - sin(ang + 0.5f) * headLen)
    path.close()
    drawPath(path, color)
}
