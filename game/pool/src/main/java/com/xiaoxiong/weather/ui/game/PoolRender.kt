package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.ceil

/**
 * 小熊台球的 Compose Canvas 渲染层。
 *
 * 设计基调：小熊蜂蜜棕木框 + 湖蓝绿台呢的绘本风，靠渐变/高光/暗角做出质感。
 * 台桌空间（米）经 [PoolGame.toCanvasX]/[PoolGame.toCanvasY] 映射到画布。
 *
 * 性能约定：路径与文字 Paint 放在 [PoolRenderCache] 里复用；织纹用整数哈希做
 * 确定性伪随机，不依赖帧时间，因此不会逐帧抖动；每帧只有少量小对象分配。
 */

internal fun DrawScope.drawPoolTable(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val scale = game.viewScale
    if (scale <= 0f) return
    val table = game.table
    val l = game.toCanvasX(table.left)
    val t = game.toCanvasY(table.top)
    val r = game.toCanvasX(table.right)
    val b = game.toCanvasY(table.bottom)
    if (r - l <= 1f || b - t <= 1f) return
    val ballR = table.ballRadius * scale
    if (ballR < 0.5f) return

    // 静态层：命中缓存则一次 drawImage（尺寸一致时 1:1 且不插值，避免糊边；
    // 连续 resize 复用旧位图时按当前画布尺寸拉伸，用 Low 插值避免锯齿）
    val base = PoolTableLayerCache.bitmap(game, style, isDay, size, this, layoutDirection)
    if (base != null) {
        val dstW = ceil(size.width).toInt()
        val dstH = ceil(size.height).toInt()
        val same = base.key.width == dstW && base.key.height == dstH
        drawImage(
            image = base.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(base.key.width, base.key.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = if (same) FilterQuality.None else FilterQuality.Low,
        )
    } else {
        drawPoolTableStatic(game, style, isDay)
    }

    // 结算阶段给台面一点压暗（Screen 已有浮层，这里只轻补，避免重复过暗）
    if (game.phase == PoolGame.Phase.FRAME_OVER || game.phase == PoolGame.Phase.MATCH_OVER) {
        clipRect(l, t, r, b) {
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(l, t), Size(r - l, b - t))
        }
    }
}

// ---------------------------------------------------------------------
// 粒子特效
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolEffects(game: PoolGame) {
    if (game.effects.isEmpty()) return
    val scale = game.viewScale
    if (scale <= 0f) return
    for (e in game.effects) {
        val k = (e.life / 0.5f).coerceIn(0f, 1f)
        if (k <= 0f) continue
        val x = game.toCanvasX(e.x)
        val y = game.toCanvasY(e.y)
        val r = (e.size * scale).coerceAtLeast(0.8f) * (0.5f + 0.5f * k)
        val c = Color(e.color)
        drawCircle(c.copy(alpha = 0.18f * k), r * 2.4f, Offset(x, y))
        drawCircle(c.copy(alpha = 0.85f * k), r, Offset(x, y))
    }
}
