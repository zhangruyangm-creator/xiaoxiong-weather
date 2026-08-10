package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自绘作物：替代 emoji，画风与农场整体统一。
 * 按类别（蔬菜/花卉/谷物/水果/香草/菇菌/其他）+ 生长阶段渲染，
 * 用 crop.color / crop.leafColor 区分作物，每株带高光与阴影增加立体感。
 */
fun DrawScope.drawFarmCrop(
    crop: CropDef,
    stage: Int,
    progress: Float,
    wilted: Boolean,
) {
    val u = size.minDimension
    val cx = size.width / 2f
    val baseY = size.height * 0.82f
    val seed = crop.id.hashCode()
    val grow =
        when (stage) {
            4, 5 -> 1f
            else -> (0.28f + 0.72f * progress.coerceIn(0f, 1f)).coerceIn(0.28f, 1f)
        }
    val alpha = if (wilted) 0.5f else 1f

    // 刚种下：只画土丘 + 一粒芽点
    if (stage == 1 && progress < 0.25f) {
        drawMound(cx, baseY, u, alpha)
        drawCircle(Color(0xFF795548), u * 0.045f, Offset(cx, baseY - u * 0.02f), alpha = alpha)
        return
    }

    if (wilted) {
        rotate(degrees = 14f, pivot = Offset(cx, baseY)) {
            drawCropShape(crop, cx, baseY, u, grow, alpha, seed)
        }
    } else {
        drawCropShape(crop, cx, baseY, u, grow, alpha, seed)
    }
}

private fun DrawScope.drawCropShape(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    seed: Int,
) {
    // 地面阴影
    drawOval(
        Color.Black.copy(alpha = 0.10f * alpha),
        topLeft = Offset(cx - u * 0.30f, baseY - u * 0.03f),
        size = Size(u * 0.60f, u * 0.10f),
    )
    when (crop.category) {
        "花卉" -> drawFlower(crop, cx, baseY, u, grow, alpha, seed)
        "谷物" -> drawGrain(crop, cx, baseY, u, grow, alpha, seed)
        "水果" -> drawFruit(crop, cx, baseY, u, grow, alpha, seed)
        "菇菌" -> drawMushroom(crop, cx, baseY, u, grow, alpha, seed)
        else -> drawLeafy(crop, cx, baseY, u, grow, alpha, seed)
    }
}

/** 叶子：从 (cx,cy) 朝 angle 方向伸出一片带尖叶，带中脉。 */
private fun DrawScope.drawLeaf(
    cx: Float,
    cy: Float,
    len: Float,
    angle: Float,
    color: Color,
    alpha: Float,
) {
    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        val leaf =
            Path().apply {
                moveTo(cx, cy)
                quadraticTo(cx + len * 0.35f, cy - len * 0.42f, cx + len, cy)
                quadraticTo(cx + len * 0.35f, cy + len * 0.42f, cx, cy)
                close()
            }
        drawPath(leaf, color.copy(alpha = alpha))
        drawLine(
            Color.Black.copy(alpha = 0.12f * alpha),
            Offset(cx + len * 0.08f, cy),
            Offset(cx + len * 0.78f, cy),
            strokeWidth = 1f,
        )
    }
}

/** 细茎：上段主色 + 底部深色，微弧。 */
private fun DrawScope.drawStem(
    x: Float,
    bottomY: Float,
    topY: Float,
    color: Color,
    width: Float,
    alpha: Float,
) {
    val stem =
        Path().apply {
            moveTo(x - width / 2f, bottomY)
            quadraticTo(x + width * 0.4f, (bottomY + topY) / 2f, x - width / 2f, topY)
            lineTo(x + width / 2f, topY)
            quadraticTo(x - width * 0.4f, (bottomY + topY) / 2f, x + width / 2f, bottomY)
            close()
        }
    drawPath(stem, color.copy(alpha = alpha))
}

/** 果实：圆球 + 高光 + 蒂。 */
private fun DrawScope.drawFruitBall(
    center: Offset,
    r: Float,
    color: Color,
    alpha: Float,
    leafColor: Color,
) {
    drawCircle(Color.Black.copy(alpha = 0.12f * alpha), r, center + Offset(0f, r * 0.08f))
    drawCircle(color.copy(alpha = alpha), r, center)
    // 高光
    drawCircle(
        Color.White.copy(alpha = 0.45f * alpha),
        r * 0.28f,
        center + Offset(-r * 0.32f, -r * 0.32f),
    )
    // 蒂
    drawLine(
        Color(0xFF33691E).copy(alpha = alpha),
        center + Offset(0f, -r * 0.9f),
        center + Offset(-r * 0.15f, -r * 1.25f),
        strokeWidth = 1.4f,
    )
    drawCircle(leafColor.copy(alpha = alpha), r * 0.16f, center + Offset(-r * 0.15f, -r * 1.28f))
}

/** 蔬菜/香草/其他：叶丛 + 中心果实（根茎类浅色或叶球）。 */
private fun DrawScope.drawLeafy(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    seed: Int,
) {
    val leaf = Color(crop.leafColor)
    val h = 0.68f * u * grow
    val topY = baseY - h
    drawStem(cx, baseY, topY + u * 0.10f, Color(0xFF558B2F), u * 0.07f, alpha)
    // 3 对叶子，随高度错落
    for (i in 0 until 3) {
        val y = topY + u * 0.10f + h * 0.28f * (i + 1)
        val len = (0.30f - i * 0.05f) * u * grow
        val sway = if ((seed + i) % 2 == 0) 1f else -1f
        drawLeaf(cx, y, len, 52f * sway + (i - 1) * 6f, leaf, alpha)
        drawLeaf(cx, y, len * 0.85f, -52f * sway - (i - 1) * 6f, leaf.copy(alpha = 0.85f), alpha)
    }
    // 成熟果实
    if (grow > 0.92f) {
        val r = 0.13f * u
        val fx = cx + ((seed % 5) - 2) * 0.06f * u
        drawFruitBall(Offset(fx, baseY - r * 0.85f), r, Color(crop.color), alpha, leaf)
    }
}

/** 花卉：细茎 + 两对叶 + 顶部花盘（花瓣 + 花蕊）。 */
private fun DrawScope.drawFlower(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    seed: Int,
) {
    val leaf = Color(crop.leafColor)
    val stemC = Color(0xFF558B2F)
    val h = 0.78f * u * grow
    val topY = baseY - h
    drawStem(cx, baseY, topY, stemC, u * 0.05f, alpha)
    drawLeaf(cx, baseY - h * 0.3f, 0.24f * u * grow, -58f, leaf, alpha)
    drawLeaf(cx, baseY - h * 0.5f, 0.22f * u * grow, 60f, leaf.copy(alpha = 0.9f), alpha)
    // 花盘
    val petal = Color(crop.color)
    val r = 0.20f * u * grow
    val c = Offset(cx, topY)
    repeat(6) { i ->
        val a = Math.PI.toFloat() * 2f * i / 6f + (seed % 7) * 0.05f
        drawCircle(
            petal.copy(alpha = alpha),
            r * 0.52f,
            c + Offset(cos(a) * r * 0.72f, sin(a) * r * 0.72f),
        )
    }
    drawCircle(Color(0xFFFFC107).copy(alpha = alpha), r * 0.30f, c)
    drawCircle(Color(0xFFFFF59D).copy(alpha = alpha), r * 0.14f, c)
}

/** 谷物：直立粗茎 + 顶部下垂穗（籽粒错开）。 */
private fun DrawScope.drawGrain(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    seed: Int,
) {
    val leaf = Color(crop.leafColor)
    val h = 0.8f * u * grow
    val topY = baseY - h
    drawStem(cx, baseY, topY, Color(0xFF8BC34A), u * 0.06f, alpha)
    // 窄长叶
    rotate(degrees = -12f, pivot = Offset(cx, baseY)) {
        drawLeaf(cx, baseY, 0.5f * u * grow, 0f, leaf, alpha * 0.9f)
    }
    // 穗：向上收拢的籽粒列
    val kernel = Color(crop.color)
    for (i in 0 until 5) {
        val t = i / 4f
        val sy = topY + h * 0.22f - t * h * 0.20f
        val sx = cx + sin(i * 2.1f + seed) * u * 0.05f
        val sSize = u * 0.075f * (1f - t * 0.35f)
        drawOval(
            kernel.copy(alpha = alpha),
            topLeft = Offset(sx - sSize * 0.5f, sy - sSize * 0.6f),
            size = Size(sSize, sSize * 1.2f),
        )
    }
}

/** 水果：低矮灌木叶团 + 枝头果实。 */
private fun DrawScope.drawFruit(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    seed: Int,
) {
    val leaf = Color(crop.leafColor)
    val h = 0.5f * u * grow
    val bushY = baseY - h
    // 叶团：三簇
    drawCircle(leaf.copy(alpha = 0.9f * alpha), 0.16f * u * grow, Offset(cx - 0.10f * u, bushY))
    drawCircle(leaf.copy(alpha = 0.85f * alpha), 0.17f * u * grow, Offset(cx + 0.09f * u, bushY - 0.03f * u))
    drawCircle(leaf.copy(alpha = alpha), 0.18f * u * grow, Offset(cx, bushY - 0.09f * u))
    // 果实挂在叶团下缘
    if (grow > 0.9f) {
        val r = 0.14f * u
        val spots = 2 + abs(seed) % 2
        for (i in 0 until spots) {
            val fx = cx + (i - (spots - 1) / 2f) * 0.18f * u
            drawFruitBall(Offset(fx, bushY + r * 0.55f), r, Color(crop.color), alpha, leaf)
        }
    }
}

/** 菇菌：浅柄 + 彩色伞盖（带白点）。 */
private fun DrawScope.drawMushroom(
    crop: CropDef,
    cx: Float,
    baseY: Float,
    u: Float,
    grow: Float,
    alpha: Float,
    @Suppress("UNUSED_PARAMETER") seed: Int,
) {
    val h = 0.42f * u * grow
    val topY = baseY - h
    val stemC = Color(0xFFEFEBE2)
    val cap = Color(crop.color)
    // 柄
    drawRoundRect(
        stemC.copy(alpha = alpha),
        topLeft = Offset(cx - u * 0.06f, topY + h * 0.3f),
        size = Size(u * 0.12f, h * 0.7f),
        cornerRadius = CornerRadius(u * 0.03f),
    )
    // 伞盖
    drawCircle(cap.copy(alpha = alpha), u * 0.17f * grow, Offset(cx, topY + h * 0.28f))
    // 伞沿亮色
    drawArc(
        cap.lighten().copy(alpha = alpha),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - u * 0.17f * grow, topY + h * 0.28f - u * 0.17f * grow),
        size = Size(u * 0.34f * grow, u * 0.34f * grow),
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = u * 0.04f),
    )
    // 白点
    drawCircle(
        Color.White.copy(alpha = 0.85f * alpha),
        u * 0.035f,
        Offset(cx - u * 0.07f, topY + h * 0.24f),
    )
    drawCircle(
        Color.White.copy(alpha = 0.7f * alpha),
        u * 0.03f,
        Offset(cx + u * 0.06f, topY + h * 0.32f),
    )
}

/** 刚播种的土丘。 */
private fun DrawScope.drawMound(
    cx: Float,
    baseY: Float,
    u: Float,
    alpha: Float,
) {
    drawOval(
        Color(0xFF6D4C41).copy(alpha = 0.22f * alpha),
        topLeft = Offset(cx - u * 0.22f, baseY - u * 0.10f),
        size = Size(u * 0.44f, u * 0.16f),
    )
}

private fun Color.lighten(): Color {
    val r = (red * 0.6f + 1f * 0.4f).coerceIn(0f, 1f)
    val g = (green * 0.6f + 1f * 0.4f).coerceIn(0f, 1f)
    val b = (blue * 0.6f + 1f * 0.4f).coerceIn(0f, 1f)
    return Color(r, g, b)
}
