package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 精灵烘焙缓存（玩家机 / 7 种敌机 / Boss 三阶段 / 道具）。
 *
 * 键含「类型（kind/variant）+ 尺寸（像素半径取整）+ 阵营 / 昼夜」，同一键只烘焙一次；
 * 新增敌人类型通过 [PlaneEnemyType.ordinal] 进入 variant，缓存键天然区分新类型。
 * 小图（最长边约 2×290px）做 LRU 限制常驻，先淘汰最旧再分配，分配 / 绘制失败静默降级。
 * 仅主线程绘制，无需加锁。
 */
internal data class PlaneSpriteKey(
    val kind: Int,
    val variant: Int,
    val radiusPx: Int,
    val isDay: Boolean,
)

internal class PlaneSprite(
    val bitmap: ImageBitmap,
    val half: Float,
    val key: PlaneSpriteKey,
)

internal object PlaneSprites {
    const val KIND_PLAYER = 0
    const val KIND_ENEMY = 1
    const val KIND_POWERUP = 2

    private const val MAX_ENTRIES = 24

    private val entries = ArrayList<PlaneSprite>(MAX_ENTRIES)

    /** 玩家机：[radiusPx] 为机身视觉半径（像素）。 */
    fun player(
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? = sprite(KIND_PLAYER, 0, radiusPx, isDay, 1.30f, density, layoutDirection)

    /** 敌机：[variant] = 类型序号；Boss 额外携带阶段（0..2）。 */
    fun enemy(
        type: PlaneEnemyType,
        bossPhase: Int,
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? {
        val variant = type.ordinal * 4 + bossPhase.coerceIn(0, 2)
        val extent = if (type == PlaneEnemyType.BOSS) 1.30f else 1.22f
        return sprite(KIND_ENEMY, variant, radiusPx, isDay, extent, density, layoutDirection)
    }

    /** 道具（P / B）。 */
    fun powerUp(
        kind: PlanePowerUpKind,
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? = sprite(KIND_POWERUP, kind.ordinal, radiusPx, isDay, 1.45f, density, layoutDirection)

    private fun sprite(
        kind: Int,
        variant: Int,
        radiusPx: Float,
        isDay: Boolean,
        extent: Float,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? {
        if (!radiusPx.isFinite() || radiusPx < 1f || radiusPx > 2048f) return null
        val keyR = round(radiusPx).toInt().coerceAtLeast(1)
        val key = PlaneSpriteKey(kind, variant, keyR, isDay)
        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.key == key) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        val half = keyR * extent
        val side = ceil(half * 2f).toInt().coerceAtLeast(2)
        // 先淘汰最旧再分配：控制位图常驻量，分配失败可直接降级为不绘制精灵
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        val bitmap = runCatching { ImageBitmap(side, side) }.getOrNull() ?: return null
        runCatching {
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
                translate(half, half) {
                    when (kind) {
                        KIND_PLAYER -> drawPlayerArt(half / 1.30f)
                        KIND_ENEMY -> drawEnemyArt(variant, half)
                        else -> drawPowerUpArt(variant, half / 1.45f)
                    }
                }
            }
        }.onFailure { return null }
        val sprite = PlaneSprite(bitmap, half, key)
        entries.add(sprite)
        return sprite
    }

    fun clear() {
        entries.clear()
    }
}

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

private fun DrawScope.drawPlayerArt(r: Float) {
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

private fun DrawScope.drawEnemyArt(
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

private fun DrawScope.drawPowerUpArt(
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

/**
 * 把烘焙精灵画进中心点（[cx], [cy]），可选旋转 / 压扁 / 闪白。
 * 热路径只有 drawImage，不分配 Path/Brush。
 */
internal fun DrawScope.drawPlaneSprite(
    sprite: PlaneSprite,
    cx: Float,
    cy: Float,
    rotationDeg: Float = 0f,
    scaleY: Float = 1f,
    alpha: Float = 1f,
    flashAlpha: Float = 0f,
) {
    val side = sprite.bitmap.width
    val left = cx - sprite.half
    val top = cy - sprite.half
    if (rotationDeg == 0f && scaleY == 1f) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(side, side),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    } else {
        withTransform(
            {
                rotate(degrees = rotationDeg, pivot = Offset(cx, cy))
                scale(scaleX = 1f, scaleY = scaleY, pivot = Offset(cx, cy))
            },
        ) {
            drawImage(
                image = sprite.bitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(side, side),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
    if (flashAlpha > 0.01f) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(side, side),
            alpha = flashAlpha.coerceIn(0f, 1f),
            colorFilter = ColorFilter.tint(Color.White),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** 道具精灵的自发光脉冲（与精灵叠加，不参与烘焙；[reduceMotion] 时冻结静态亮度）。 */
internal fun DrawScope.drawPowerUpGlow(
    cx: Float,
    cy: Float,
    r: Float,
    kind: PlanePowerUpKind,
    tick: Long,
    reduceMotion: Boolean,
) {
    val color = if (kind == PlanePowerUpKind.WEAPON) PlaneUi.ok else Color(0xFFFFD740)
    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(tick * 0.16f)
    drawCircle(color.copy(alpha = 0.10f + 0.10f * pulse), r * 1.6f, Offset(cx, cy))
}

/** 弹幕 / 特效用的复用描边：按 0.1px 取整宽度缓存（同一设备宽度集合有限，热路径零分配）。 */
internal object PlaneStroke {
    private const val MAX_ENTRIES = 24

    // 插入序 LinkedHashMap：缓存满时淘汰最旧，而不是整表 clear（避免连带清掉常用宽度）
    private val cache = LinkedHashMap<Int, Stroke>(MAX_ENTRIES)

    fun of(width: Float): Stroke {
        val key = round(width * 10f).toInt()
        cache[key]?.let { return it }
        if (cache.size >= MAX_ENTRIES) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) cache.remove(oldest)
        }
        return Stroke(key / 10f).also { cache[key] = it }
    }
}

/** 确定性伪随机 0..1（同一 (i,salt) 永远同值）。 */
internal fun planeHash01(
    i: Int,
    salt: Int = 0,
): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}

/** 正模运算：结果恒落在 [0, span)。 */
internal fun planeMod(
    value: Float,
    span: Float,
): Float {
    if (span <= 0f) return 0f
    val r = value % span
    return if (r < 0f) r + span else r
}
