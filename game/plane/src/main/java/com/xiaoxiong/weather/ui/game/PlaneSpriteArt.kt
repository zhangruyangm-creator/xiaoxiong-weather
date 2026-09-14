package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

/** 烘焙用的复用 Path / Paint（只在缓存未命中时触碰，非热路径）。 */
private object PlaneArt {
    val path = Path()

    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
}

// ---------------------------------------------------------------------
// 玩家机：奶油白 + 棕的小熊飞行员飞机
// ---------------------------------------------------------------------

internal fun DrawScope.drawPlayerArt(r: Float) {
    val cream = planePlayerCream
    val shade = planePlayerCreamShade
    val brown = planePlayerBrown
    val brownDark = planePlayerBrownDark
    val outline = Stroke(r * 0.07f)

    // 地面投影
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(-r * 0.95f, r * 0.20f),
        size = Size(r * 1.90f, r * 0.80f),
    )
    // 水平尾翼
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.62f, r * 0.46f)
    PlaneArt.path.lineTo(-r * 0.14f, r * 0.52f)
    PlaneArt.path.lineTo(-r * 0.14f, r * 0.72f)
    PlaneArt.path.lineTo(-r * 0.56f, r * 0.76f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, shade)
    drawPath(PlaneArt.path, brownDark, style = outline)
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(r * 0.62f, r * 0.46f)
    PlaneArt.path.lineTo(r * 0.14f, r * 0.52f)
    PlaneArt.path.lineTo(r * 0.14f, r * 0.72f)
    PlaneArt.path.lineTo(r * 0.56f, r * 0.76f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, shade)
    drawPath(PlaneArt.path, brownDark, style = outline)

    // 主翼（后掠）
    for (side in intArrayOf(-1, 1)) {
        PlaneArt.path.reset()
        PlaneArt.path.moveTo(side * r * 0.16f, -r * 0.14f)
        PlaneArt.path.lineTo(side * r * 0.98f, r * 0.22f)
        PlaneArt.path.lineTo(side * r * 0.90f, r * 0.50f)
        PlaneArt.path.lineTo(side * r * 0.18f, r * 0.34f)
        PlaneArt.path.close()
        drawPath(PlaneArt.path, cream)
        drawPath(PlaneArt.path, brown, style = outline)
        // 翼面棕条 + 小熊爪印
        drawLine(
            brown.copy(alpha = 0.85f),
            Offset(side * r * 0.26f, -r * 0.04f),
            Offset(side * r * 0.86f, r * 0.30f),
            strokeWidth = r * 0.07f,
        )
        val px = side * r * 0.66f
        val py = r * 0.24f
        drawCircle(brown, r * 0.055f, Offset(px, py))
        drawCircle(brown, r * 0.038f, Offset(px - r * 0.07f, py - r * 0.05f))
        drawCircle(brown, r * 0.038f, Offset(px, py - r * 0.075f))
        drawCircle(brown, r * 0.038f, Offset(px + r * 0.07f, py - r * 0.05f))
    }

    // 机身
    drawRoundRect(
        color = cream,
        topLeft = Offset(-r * 0.21f, -r * 0.66f),
        size = Size(r * 0.42f, r * 1.30f),
        cornerRadius = CornerRadius(r * 0.18f),
    )
    drawRoundRect(
        color = brownDark,
        topLeft = Offset(-r * 0.21f, -r * 0.66f),
        size = Size(r * 0.42f, r * 1.30f),
        cornerRadius = CornerRadius(r * 0.18f),
        style = outline,
    )
    // 机鼻
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.20f, -r * 0.60f)
    PlaneArt.path.lineTo(0f, -r * 0.92f)
    PlaneArt.path.lineTo(r * 0.20f, -r * 0.60f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, brown)
    drawPath(PlaneArt.path, brownDark, style = outline)
    // 垂尾
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(0f, r * 0.30f)
    PlaneArt.path.lineTo(0f, r * 0.86f)
    PlaneArt.path.lineTo(-r * 0.26f, r * 0.80f)
    PlaneArt.path.lineTo(-r * 0.08f, r * 0.46f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, brown)
    drawPath(PlaneArt.path, brownDark, style = outline)
    // 引擎短舱
    for (side in intArrayOf(-1, 1)) {
        drawCircle(brown, r * 0.15f, Offset(side * r * 0.40f, r * 0.12f))
        drawCircle(Color(0xFF3A2410), r * 0.075f, Offset(side * r * 0.40f, r * 0.12f))
    }
    // 座舱盖
    drawOval(
        planePlayerGlass,
        topLeft = Offset(-r * 0.15f, -r * 0.34f),
        size = Size(r * 0.30f, r * 0.42f),
    )
    drawOval(
        Color.White.copy(alpha = 0.55f),
        topLeft = Offset(-r * 0.10f, -r * 0.31f),
        size = Size(r * 0.09f, r * 0.20f),
    )
    drawOval(
        brownDark,
        topLeft = Offset(-r * 0.15f, -r * 0.34f),
        size = Size(r * 0.30f, r * 0.42f),
        style = Stroke(r * 0.05f),
    )
    // 小熊飞行员头
    val fur = planeBearFur
    drawCircle(planeBearEar, r * 0.13f, Offset(-r * 0.18f, -r * 0.50f))
    drawCircle(planeBearEar, r * 0.13f, Offset(r * 0.18f, -r * 0.50f))
    drawCircle(fur, r * 0.25f, Offset(0f, -r * 0.38f))
    drawCircle(planeBearMuzzle, r * 0.11f, Offset(0f, -r * 0.31f))
    drawCircle(brownDark, r * 0.04f, Offset(0f, -r * 0.35f))
    drawCircle(brownDark, r * 0.026f, Offset(-r * 0.09f, -r * 0.43f))
    drawCircle(brownDark, r * 0.026f, Offset(r * 0.09f, -r * 0.43f))
    drawCircle(planeBearBlush, r * 0.06f, Offset(-r * 0.19f, -r * 0.34f))
    drawCircle(planeBearBlush, r * 0.06f, Offset(r * 0.19f, -r * 0.34f))
}

// ---------------------------------------------------------------------
// 敌机：5 种小怪 + Boss 三阶段
// ---------------------------------------------------------------------

internal fun DrawScope.drawEnemyArt(
    variant: Int,
    half: Float,
) {
    val type = PlaneEnemyType.entries.getOrNull(variant / 4) ?: PlaneEnemyType.GRUNT
    val phase = variant % 4
    val r = half / (if (type == PlaneEnemyType.BOSS) 1.30f else 1.22f)
    val palette = planeEnemyPalette(type, phase)
    when (type) {
        PlaneEnemyType.GRUNT -> drawGruntArt(r, palette)
        PlaneEnemyType.DIVER -> drawDiverArt(r, palette)
        PlaneEnemyType.WEAVER -> drawWeaverArt(r, palette)
        PlaneEnemyType.TURRET -> drawTurretArt(r, palette)
        PlaneEnemyType.TRANSPORT -> drawTransportArt(r, palette)
        PlaneEnemyType.HELI -> drawHeliArt(r, palette)
        PlaneEnemyType.TANK -> drawTankArt(r, palette)
        PlaneEnemyType.BOSS -> drawBossArt(r, phase, palette)
    }
}

private fun DrawScope.drawGruntArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.09f)
    // 尾翼（朝上）
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.46f, -r * 0.70f)
    PlaneArt.path.lineTo(r * 0.46f, -r * 0.70f)
    PlaneArt.path.lineTo(r * 0.26f, -r * 0.42f)
    PlaneArt.path.lineTo(-r * 0.26f, -r * 0.42f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
    // 后掠主翼
    for (side in intArrayOf(-1, 1)) {
        PlaneArt.path.reset()
        PlaneArt.path.moveTo(side * r * 0.18f, -r * 0.18f)
        PlaneArt.path.lineTo(side * r * 0.98f, r * 0.10f)
        PlaneArt.path.lineTo(side * r * 0.90f, r * 0.36f)
        PlaneArt.path.lineTo(side * r * 0.20f, r * 0.24f)
        PlaneArt.path.close()
        drawPath(PlaneArt.path, p.panel)
        drawPath(PlaneArt.path, p.dark, style = outline)
        drawLine(p.accent, Offset(side * r * 0.84f, r * 0.12f), Offset(side * r * 0.90f, r * 0.32f), strokeWidth = r * 0.09f)
    }
    // 机身 + 机鼻（朝下）
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.26f, -r * 0.62f),
        size = Size(r * 0.52f, r * 1.12f),
        cornerRadius = CornerRadius(r * 0.20f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.26f, -r * 0.62f),
        size = Size(r * 0.52f, r * 1.12f),
        cornerRadius = CornerRadius(r * 0.20f),
        style = outline,
    )
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.26f, r * 0.42f)
    PlaneArt.path.lineTo(0f, r * 0.96f)
    PlaneArt.path.lineTo(r * 0.26f, r * 0.42f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.accent)
    drawPath(PlaneArt.path, p.dark, style = outline)
    // 座舱
    drawCircle(Color(0xFF243244), r * 0.12f, Offset(0f, r * 0.02f))
    drawCircle(Color(0xFF8FD8FF).copy(alpha = 0.7f), r * 0.055f, Offset(-r * 0.03f, -r * 0.01f))
    // 翼根引擎
    for (side in intArrayOf(-1, 1)) {
        drawCircle(p.dark, r * 0.10f, Offset(side * r * 0.34f, -r * 0.06f))
        drawCircle(Color(0xFFFFB74D), r * 0.045f, Offset(side * r * 0.34f, -r * 0.06f))
    }
}

private fun DrawScope.drawDiverArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.09f)
    // 前掠细翼 + 尾撑
    for (side in intArrayOf(-1, 1)) {
        PlaneArt.path.reset()
        PlaneArt.path.moveTo(side * r * 0.10f, r * 0.18f)
        PlaneArt.path.lineTo(side * r * 0.96f, -r * 0.30f)
        PlaneArt.path.lineTo(side * r * 0.90f, -r * 0.04f)
        PlaneArt.path.lineTo(side * r * 0.14f, r * 0.40f)
        PlaneArt.path.close()
        drawPath(PlaneArt.path, p.panel)
        drawPath(PlaneArt.path, p.dark, style = outline)
        drawLine(p.accent, Offset(side * r * 0.30f, r * 0.30f), Offset(side * r * 0.80f, -r * 0.14f), strokeWidth = r * 0.07f)
    }
    // 尖头机身（朝下）
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(0f, r * 1.00f)
    PlaneArt.path.lineTo(r * 0.30f, r * 0.12f)
    PlaneArt.path.lineTo(r * 0.26f, -r * 0.56f)
    PlaneArt.path.lineTo(-r * 0.26f, -r * 0.56f)
    PlaneArt.path.lineTo(-r * 0.30f, r * 0.12f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.body)
    drawPath(PlaneArt.path, p.dark, style = outline)
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(0f, r * 0.62f)
    PlaneArt.path.lineTo(r * 0.13f, r * 0.10f)
    PlaneArt.path.lineTo(-r * 0.13f, r * 0.10f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.accent)
    // 进气口 + 尾喷
    drawCircle(Color(0xFF1E2438), r * 0.14f, Offset(0f, -r * 0.40f))
    drawCircle(Color(0xFFFF8A65), r * 0.07f, Offset(0f, -r * 0.40f))
    // 小尾鳍
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.26f, -r * 0.40f)
    PlaneArt.path.lineTo(-r * 0.58f, -r * 0.72f)
    PlaneArt.path.lineTo(-r * 0.10f, -r * 0.58f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(r * 0.26f, -r * 0.40f)
    PlaneArt.path.lineTo(r * 0.58f, -r * 0.72f)
    PlaneArt.path.lineTo(r * 0.10f, -r * 0.58f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
}

private fun DrawScope.drawWeaverArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.08f)
    // 双翼
    drawRoundRect(
        color = p.panel,
        topLeft = Offset(-r * 0.94f, -r * 0.28f),
        size = Size(r * 1.88f, r * 0.26f),
        cornerRadius = CornerRadius(r * 0.12f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.94f, -r * 0.28f),
        size = Size(r * 1.88f, r * 0.26f),
        cornerRadius = CornerRadius(r * 0.12f),
        style = outline,
    )
    drawRoundRect(
        color = p.panel,
        topLeft = Offset(-r * 0.84f, r * 0.16f),
        size = Size(r * 1.68f, r * 0.24f),
        cornerRadius = CornerRadius(r * 0.12f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.84f, r * 0.16f),
        size = Size(r * 1.68f, r * 0.24f),
        cornerRadius = CornerRadius(r * 0.12f),
        style = outline,
    )
    // 翼间支柱
    for (side in intArrayOf(-1, 1)) {
        drawLine(p.dark, Offset(side * r * 0.52f, -r * 0.06f), Offset(side * r * 0.52f, r * 0.20f), strokeWidth = r * 0.05f)
    }
    // 机身
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.24f, -r * 0.60f),
        size = Size(r * 0.48f, r * 1.16f),
        cornerRadius = CornerRadius(r * 0.22f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.24f, -r * 0.60f),
        size = Size(r * 0.48f, r * 1.16f),
        cornerRadius = CornerRadius(r * 0.22f),
        style = outline,
    )
    // 机鼻风帽
    drawCircle(p.dark, r * 0.20f, Offset(0f, r * 0.58f))
    drawCircle(p.accent, r * 0.10f, Offset(0f, r * 0.58f))
    // 座舱
    drawCircle(Color(0xFF243244), r * 0.11f, Offset(0f, -r * 0.05f))
    // 尾翼
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.38f, -r * 0.62f)
    PlaneArt.path.lineTo(r * 0.38f, -r * 0.62f)
    PlaneArt.path.lineTo(r * 0.20f, -r * 0.40f)
    PlaneArt.path.lineTo(-r * 0.20f, -r * 0.40f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
}

private fun DrawScope.drawTurretArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.08f)
    // 厚重机身
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.56f, -r * 0.62f),
        size = Size(r * 1.12f, r * 1.10f),
        cornerRadius = CornerRadius(r * 0.24f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.56f, -r * 0.62f),
        size = Size(r * 1.12f, r * 1.10f),
        cornerRadius = CornerRadius(r * 0.24f),
        style = outline,
    )
    // 侧装甲板
    for (side in intArrayOf(-1, 1)) {
        drawRoundRect(
            color = p.panel,
            topLeft = Offset(side * r * 0.40f - r * 0.16f, -r * 0.44f),
            size = Size(r * 0.32f, r * 0.78f),
            cornerRadius = CornerRadius(r * 0.10f),
        )
        drawRoundRect(
            color = p.dark,
            topLeft = Offset(side * r * 0.40f - r * 0.16f, -r * 0.44f),
            size = Size(r * 0.32f, r * 0.78f),
            cornerRadius = CornerRadius(r * 0.10f),
            style = Stroke(r * 0.06f),
        )
    }
    // 警示斜纹（右上 / 左下）
    drawLine(p.accent, Offset(-r * 0.50f, -r * 0.54f), Offset(-r * 0.20f, -r * 0.32f), strokeWidth = r * 0.10f)
    drawLine(p.accent, Offset(-r * 0.36f, -r * 0.58f), Offset(-r * 0.10f, -r * 0.40f), strokeWidth = r * 0.10f)
    drawLine(p.accent, Offset(r * 0.20f, r * 0.34f), Offset(r * 0.50f, r * 0.54f), strokeWidth = r * 0.10f)
    // 炮管（朝下）
    drawRoundRect(
        color = Color(0xFF3A4658),
        topLeft = Offset(-r * 0.13f, r * 0.10f),
        size = Size(r * 0.26f, r * 0.82f),
        cornerRadius = CornerRadius(r * 0.10f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.13f, r * 0.10f),
        size = Size(r * 0.26f, r * 0.82f),
        cornerRadius = CornerRadius(r * 0.10f),
        style = Stroke(r * 0.05f),
    )
    drawCircle(p.panel, r * 0.13f, Offset(0f, r * 0.66f))
    drawCircle(p.dark, r * 0.07f, Offset(0f, r * 0.66f))
    // 顶部雷达 + 侧灯
    drawCircle(p.dark, r * 0.18f, Offset(0f, -r * 0.68f))
    drawCircle(p.accent, r * 0.08f, Offset(0f, -r * 0.68f))
    for (side in intArrayOf(-1, 1)) {
        drawCircle(Color(0xFFFF5252), r * 0.06f, Offset(side * r * 0.48f, -r * 0.52f))
    }
}

private fun DrawScope.drawTransportArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.07f)
    // 大展弦比机翼
    drawRoundRect(
        color = p.panel,
        topLeft = Offset(-r * 1.06f, -r * 0.16f),
        size = Size(r * 2.12f, r * 0.30f),
        cornerRadius = CornerRadius(r * 0.12f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 1.06f, -r * 0.16f),
        size = Size(r * 2.12f, r * 0.30f),
        cornerRadius = CornerRadius(r * 0.12f),
        style = outline,
    )
    // 发动机 ×4
    for (side in intArrayOf(-1, 1)) {
        for (offset in floatArrayOf(0.48f, 0.82f)) {
            drawCircle(p.body, r * 0.15f, Offset(side * r * offset, r * 0.02f))
            drawCircle(p.dark, r * 0.08f, Offset(side * r * offset, r * 0.06f))
            drawCircle(Color(0xFFFFB74D), r * 0.035f, Offset(side * r * offset, r * 0.09f))
        }
    }
    // 粗机身
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.34f, -r * 0.66f),
        size = Size(r * 0.68f, r * 1.30f),
        cornerRadius = CornerRadius(r * 0.24f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.34f, -r * 0.66f),
        size = Size(r * 0.68f, r * 1.30f),
        cornerRadius = CornerRadius(r * 0.24f),
        style = outline,
    )
    // 货舱门 + 舱灯（朝下）
    drawRoundRect(
        color = Color(0xFF3F4634),
        topLeft = Offset(-r * 0.22f, r * 0.30f),
        size = Size(r * 0.44f, r * 0.48f),
        cornerRadius = CornerRadius(r * 0.08f),
    )
    for (i in 0 until 3) {
        drawCircle(p.accent, r * 0.045f, Offset(-r * 0.13f + i * r * 0.13f, r * 0.56f))
    }
    // 驾驶舱 + 尾翼
    drawRoundRect(
        color = Color(0xFF243244),
        topLeft = Offset(-r * 0.16f, -r * 0.58f),
        size = Size(r * 0.32f, r * 0.20f),
        cornerRadius = CornerRadius(r * 0.08f),
    )
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.52f, -r * 0.62f)
    PlaneArt.path.lineTo(r * 0.52f, -r * 0.62f)
    PlaneArt.path.lineTo(r * 0.28f, -r * 0.38f)
    PlaneArt.path.lineTo(-r * 0.28f, -r * 0.38f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
    for (side in intArrayOf(-1, 1)) {
        drawCircle(Color(0xFFFF5252), r * 0.05f, Offset(side * r * 0.46f, -r * 0.02f))
    }
}

// ---------------------------------------------------------------------
// 直升机：悬停空中，主旋翼 + 尾撑 + 起落橇
// ---------------------------------------------------------------------

private fun DrawScope.drawHeliArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.07f)
    // 地面投影
    drawOval(
        Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(-r * 0.85f, r * 0.30f),
        size = Size(r * 1.70f, r * 0.60f),
    )
    // 尾撑 + 尾桨（朝上）
    drawRoundRect(
        color = p.panel,
        topLeft = Offset(-r * 0.09f, -r * 1.00f),
        size = Size(r * 0.18f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.08f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.09f, -r * 1.00f),
        size = Size(r * 0.18f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.08f),
        style = outline,
    )
    for (side in intArrayOf(-1, 1)) {
        drawLine(
            p.dark,
            Offset(0f, -r * 0.92f),
            Offset(side * r * 0.34f, -r * 1.00f),
            strokeWidth = r * 0.06f,
        )
    }
    drawCircle(p.accent, r * 0.07f, Offset(0f, -r * 0.94f))
    // 短翼 + 武器挂架
    for (side in intArrayOf(-1, 1)) {
        drawRoundRect(
            color = p.panel,
            topLeft = Offset(side * r * 0.20f - r * 0.30f, -r * 0.18f),
            size = Size(r * 0.30f, r * 0.16f),
            cornerRadius = CornerRadius(r * 0.06f),
        )
        drawRoundRect(
            color = p.dark,
            topLeft = Offset(side * r * 0.20f - r * 0.30f, -r * 0.18f),
            size = Size(r * 0.30f, r * 0.16f),
            cornerRadius = CornerRadius(r * 0.06f),
            style = Stroke(r * 0.05f),
        )
        drawRoundRect(
            color = Color(0xFF3A4658),
            topLeft = Offset(side * r * 0.42f - r * 0.05f, -r * 0.14f),
            size = Size(r * 0.10f, r * 0.34f),
            cornerRadius = CornerRadius(r * 0.04f),
        )
    }
    // 机身（水滴形，机鼻朝下）
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(0f, r * 0.88f)
    PlaneArt.path.cubicTo(r * 0.52f, r * 0.40f, r * 0.46f, -r * 0.36f, 0f, -r * 0.62f)
    PlaneArt.path.cubicTo(-r * 0.46f, -r * 0.36f, -r * 0.52f, r * 0.40f, 0f, r * 0.88f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.body)
    drawPath(PlaneArt.path, p.dark, style = outline)
    // 座舱（前风挡）
    drawOval(
        p.accent.copy(alpha = 0.55f),
        topLeft = Offset(-r * 0.22f, r * 0.06f),
        size = Size(r * 0.44f, r * 0.34f),
    )
    drawOval(
        Color(0xFF243244).copy(alpha = 0.75f),
        topLeft = Offset(-r * 0.16f, r * 0.14f),
        size = Size(r * 0.32f, r * 0.20f),
    )
    // 机腹机炮
    drawCircle(p.dark, r * 0.10f, Offset(0f, r * 0.62f))
    // 起落橇
    for (side in intArrayOf(-1, 1)) {
        drawLine(
            p.dark,
            Offset(side * r * 0.38f, -r * 0.04f),
            Offset(side * r * 0.46f, r * 0.36f),
            strokeWidth = r * 0.06f,
        )
        drawLine(
            p.dark,
            Offset(side * r * 0.30f, -r * 0.02f),
            Offset(side * r * 0.56f, r * 0.34f),
            strokeWidth = r * 0.06f,
        )
    }
    // 主旋翼（静止帧：十字桨叶 + 旋翼盘模糊）
    drawOval(
        Color.White.copy(alpha = 0.10f),
        topLeft = Offset(-r * 1.20f, r * 0.00f),
        size = Size(r * 2.40f, r * 0.18f),
    )
    drawLine(p.dark, Offset(-r * 1.08f, r * 0.09f), Offset(r * 1.08f, r * 0.09f), strokeWidth = r * 0.09f)
    drawLine(p.dark, Offset(-r * 0.30f, -r * 0.06f), Offset(r * 0.30f, r * 0.24f), strokeWidth = r * 0.07f)
    drawCircle(p.panel, r * 0.14f, Offset(0f, r * 0.09f))
    drawCircle(p.dark, r * 0.07f, Offset(0f, r * 0.09f))
    // 航行灯
    drawCircle(Color(0xFFFF5252), r * 0.05f, Offset(-r * 0.46f, r * 0.34f))
    drawCircle(Color(0xFF69F0AE), r * 0.05f, Offset(r * 0.46f, r * 0.34f))
}

// ---------------------------------------------------------------------
// 装甲车：地面载具，履带 + 车体 + 朝上炮管
// ---------------------------------------------------------------------

private fun DrawScope.drawTankArt(
    r: Float,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.07f)
    // 双履带（上下两条横向履带）
    for (side in intArrayOf(-1, 1)) {
        val top = side * r * 0.30f
        drawRoundRect(
            color = Color(0xFF3A4148),
            topLeft = Offset(-r * 1.00f, top),
            size = Size(r * 2.00f, r * 0.34f),
            cornerRadius = CornerRadius(r * 0.16f),
        )
        drawRoundRect(
            color = p.dark,
            topLeft = Offset(-r * 1.00f, top),
            size = Size(r * 2.00f, r * 0.34f),
            cornerRadius = CornerRadius(r * 0.16f),
            style = outline,
        )
        for (i in 0 until 5) {
            drawCircle(
                p.panel,
                r * 0.10f,
                Offset(-r * 0.76f + i * r * 0.38f, top + r * 0.17f),
            )
        }
    }
    // 车体
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.86f, -r * 0.30f),
        size = Size(r * 1.72f, r * 0.60f),
        cornerRadius = CornerRadius(r * 0.10f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.86f, -r * 0.30f),
        size = Size(r * 1.72f, r * 0.60f),
        cornerRadius = CornerRadius(r * 0.10f),
        style = outline,
    )
    // 车头 / 车尾斜甲
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(r * 0.86f, -r * 0.26f)
    PlaneArt.path.lineTo(r * 1.06f, -r * 0.04f)
    PlaneArt.path.lineTo(r * 1.06f, r * 0.10f)
    PlaneArt.path.lineTo(r * 0.86f, r * 0.26f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.panel)
    drawPath(PlaneArt.path, p.dark, style = outline)
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.86f, -r * 0.26f)
    PlaneArt.path.lineTo(-r * 1.02f, r * 0.02f)
    PlaneArt.path.lineTo(-r * 0.86f, r * 0.26f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
    // 警示斜纹
    drawLine(p.accent, Offset(-r * 0.62f, r * 0.22f), Offset(-r * 0.42f, r * 0.04f), strokeWidth = r * 0.10f)
    drawLine(p.accent, Offset(-r * 0.48f, r * 0.24f), Offset(-r * 0.28f, r * 0.06f), strokeWidth = r * 0.10f)
    // 炮塔 + 向上炮管
    drawCircle(p.panel, r * 0.34f, Offset.Zero)
    drawCircle(p.dark, r * 0.34f, Offset.Zero, style = outline)
    drawRoundRect(
        color = Color(0xFF3A4658),
        topLeft = Offset(-r * 0.09f, -r * 1.06f),
        size = Size(r * 0.18f, r * 0.86f),
        cornerRadius = CornerRadius(r * 0.06f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.09f, -r * 1.06f),
        size = Size(r * 0.18f, r * 0.86f),
        cornerRadius = CornerRadius(r * 0.06f),
        style = Stroke(r * 0.05f),
    )
    drawRoundRect(
        color = Color(0xFF3A4658),
        topLeft = Offset(-r * 0.17f, -r * 0.86f),
        size = Size(r * 0.34f, r * 0.16f),
        cornerRadius = CornerRadius(r * 0.05f),
    )
    drawCircle(p.accent, r * 0.09f, Offset(0f, r * 0.10f))
    drawCircle(p.dark, r * 0.04f, Offset(0f, r * 0.10f))
    // 舱盖 + 尾灯
    drawRoundRect(
        color = Color(0xFF243244),
        topLeft = Offset(-r * 0.16f, -r * 0.20f),
        size = Size(r * 0.32f, r * 0.14f),
        cornerRadius = CornerRadius(r * 0.05f),
    )
    drawCircle(Color(0xFFFF5252), r * 0.05f, Offset(-r * 0.90f, r * 0.12f))
    drawCircle(Color(0xFFFFF176), r * 0.05f, Offset(r * 0.94f, r * 0.00f))
}

private fun DrawScope.drawBossArt(
    r: Float,
    phase: Int,
    p: PlaneEnemyPalette,
) {
    val outline = Stroke(r * 0.05f)
    val coreColor =
        when (phase.coerceIn(0, 2)) {
            0 -> Color(0xFF7FE3F2)
            1 -> Color(0xFFFFD54F)
            else -> Color(0xFFFF5252)
        }
    // 大后掠主翼 + 引擎舱
    for (side in intArrayOf(-1, 1)) {
        PlaneArt.path.reset()
        PlaneArt.path.moveTo(side * r * 0.36f, -r * 0.28f)
        PlaneArt.path.lineTo(side * r * 1.14f, r * 0.04f)
        PlaneArt.path.lineTo(side * r * 1.02f, r * 0.42f)
        PlaneArt.path.lineTo(side * r * 0.40f, r * 0.34f)
        PlaneArt.path.close()
        drawPath(PlaneArt.path, p.panel)
        drawPath(PlaneArt.path, p.dark, style = outline)
        drawLine(
            p.accent.copy(alpha = 0.9f),
            Offset(side * r * 0.50f, r * 0.02f),
            Offset(side * r * 1.00f, r * 0.18f),
            strokeWidth = r * 0.045f,
        )
        drawCircle(p.dark, r * 0.16f, Offset(side * r * 0.84f, r * 0.16f))
        drawCircle(Color(0xFFFFB74D), r * 0.07f, Offset(side * r * 0.84f, r * 0.20f))
        // 翼端炮座（动态炮塔的底座）
        drawCircle(p.body, r * 0.13f, Offset(side * r * 0.60f, r * 0.34f))
        drawCircle(p.dark, r * 0.075f, Offset(side * r * 0.60f, r * 0.34f))
    }
    // 尾翼（朝上）
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.54f, -r * 0.52f)
    PlaneArt.path.lineTo(r * 0.54f, -r * 0.52f)
    PlaneArt.path.lineTo(r * 0.30f, -r * 0.22f)
    PlaneArt.path.lineTo(-r * 0.30f, -r * 0.22f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.dark)
    // 主机身
    drawRoundRect(
        color = p.body,
        topLeft = Offset(-r * 0.46f, -r * 0.34f),
        size = Size(r * 0.92f, r * 1.10f),
        cornerRadius = CornerRadius(r * 0.22f),
    )
    drawRoundRect(
        color = p.dark,
        topLeft = Offset(-r * 0.46f, -r * 0.34f),
        size = Size(r * 0.92f, r * 1.10f),
        cornerRadius = CornerRadius(r * 0.22f),
        style = Stroke(r * 0.06f),
    )
    // 装甲板
    for (i in 0 until 3) {
        drawLine(
            p.dark.copy(alpha = 0.7f),
            Offset(-r * 0.42f, -r * 0.14f + i * r * 0.22f),
            Offset(r * 0.42f, -r * 0.10f + i * r * 0.22f),
            strokeWidth = r * 0.035f,
        )
    }
    // 舰桥 / 前部（朝下）
    PlaneArt.path.reset()
    PlaneArt.path.moveTo(-r * 0.40f, r * 0.50f)
    PlaneArt.path.lineTo(-r * 0.20f, r * 0.92f)
    PlaneArt.path.lineTo(r * 0.20f, r * 0.92f)
    PlaneArt.path.lineTo(r * 0.40f, r * 0.50f)
    PlaneArt.path.close()
    drawPath(PlaneArt.path, p.panel)
    drawPath(PlaneArt.path, p.dark, style = outline)
    // 核心舱（动态辉光覆在其上）
    drawCircle(p.dark, r * 0.26f, Offset(0f, r * 0.24f))
    drawCircle(coreColor.copy(alpha = 0.45f), r * 0.20f, Offset(0f, r * 0.24f))
    drawCircle(Color(0xFF121A24), r * 0.13f, Offset(0f, r * 0.24f))
    // 舰桥双灯
    drawCircle(Color(0xFFFF5252), r * 0.06f, Offset(-r * 0.22f, r * 0.72f))
    drawCircle(Color(0xFFFF5252), r * 0.06f, Offset(r * 0.22f, r * 0.72f))
    // 损伤纹（随阶段加深）
    val damage = phase + 1
    for (i in 0 until damage * 2) {
        val hx = planeHash01(i, 71 + phase * 13)
        val hy = planeHash01(i, 97 + phase * 17)
        drawLine(
            Color.Black.copy(alpha = 0.28f),
            Offset((hx - 0.5f) * r * 0.8f, (hy - 0.5f) * r * 0.9f),
            Offset((hx - 0.5f) * r * 0.5f, (hy - 0.2f) * r * 0.9f),
            strokeWidth = r * 0.03f,
        )
    }
}

// ---------------------------------------------------------------------
// 道具：P（武器）/ B（炸弹）
// ---------------------------------------------------------------------

internal fun DrawScope.drawPowerUpArt(
    variant: Int,
    r: Float,
) {
    val weapon = variant == PlanePowerUpKind.WEAPON.ordinal
    val body = if (weapon) Color(0xFF2E7D4F) else Color(0xFF8A5A12)
    val ring = if (weapon) PlaneUi.ok else Color(0xFFFFD740)
    // 外辉光
    drawCircle(ring.copy(alpha = 0.18f), r * 1.30f, Offset.Zero)
    drawCircle(ring.copy(alpha = 0.24f), r * 1.05f, Offset.Zero)
    // 主体
    drawCircle(body, r * 0.86f, Offset.Zero)
    drawCircle(ring, r * 0.86f, Offset.Zero, style = Stroke(r * 0.14f))
    // 内圈刻度
    drawCircle(ring.copy(alpha = 0.6f), r * 0.55f, Offset.Zero, style = Stroke(r * 0.07f))
    // 字母
    val paint = PlaneArt.textPaint
    paint.color = android.graphics.Color.WHITE
    paint.textSize = r * 0.92f
    drawContext.canvas.nativeCanvas.drawText(if (weapon) "P" else "B", 0f, r * 0.33f, paint)
}
