package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.ceil

/**
 * 小熊冰球的 Compose Canvas 渲染层。
 *
 * 设计基调：蜂蜜棕木框 + 冷调冰面的桌面空气曲棍球；球面高光、投影、暗角与
 * 确定性划痕做质感。所有不变内容（木框 / 冰面 / 标记 / 库边 / 球门）按
 * 「画布尺寸 + 场景样式 + isDay + 球桌几何」烘焙成 [ImageBitmap]，每帧一次
 * drawImage；挡板与蜜罐也各烘焙一张精灵图，逐帧零 Path / Brush 分配。
 */
internal object HockeyUi {
    val iceBorder = Color(0x998FD9F2)
    val glass = Color(0xE8172533)
    val glassDeep = Color(0xF20C1622)
    val textSecondary = Color(0xB3C3DCEF)
    val accentWarm = Color(0xFFFFC46B)
    val accentCool = Color(0xFF7FE3F2)
    val danger = Color(0xFFFF8A80)
    val warn = Color(0xFFFFB74D)
}

// ---------------------------------------------------------------------
// 入口：每帧绘制
// ---------------------------------------------------------------------

/** 所有动态内容的绘制入口；静态内容命中缓存时每帧仅一次 drawImage。 */
internal fun DrawScope.drawHockeyWorld(
    game: HockeyGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
    reduceMotion: Boolean,
    character: BearCharacter,
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val t = game.table ?: return
    val time = game.time

    drawHockeyAmbient(weather, rain, freezingRain, hail, isDay, time, reduceMotion)

    val layer = HockeyTableLayerCache.bitmap(game, style, isDay, size, this, layoutDirection)
    if (layer != null) {
        val dstW = ceil(size.width).toInt()
        val dstH = ceil(size.height).toInt()
        val same = layer.key.width == dstW && layer.key.height == dstH
        drawImage(
            image = layer.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(layer.key.width, layer.key.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = if (same) FilterQuality.None else FilterQuality.Low,
        )
    } else {
        drawHockeyTableStatic(game, style, isDay)
    }

    drawGoalLight(game)

    if (game.phase == Phase.MATCH_OVER && game.matchWinner == Scorer.PLAYER && !reduceMotion) {
        drawWinnerConfetti(game, time)
    }

    if (!reduceMotion) drawPuckTrail(game)
    if (!reduceMotion) drawHockeySparks(game)

    val short = t.shortSide
    drawBearPaddle(
        x = game.ai.x,
        y = game.ai.y,
        r = game.ai.r,
        palette = paddleAi,
        paletteId = 2,
        vx = game.ai.vx,
        vy = game.ai.vy,
        shortSide = short,
        hit = game.hitFlash,
        reduceMotion = reduceMotion,
    )
    val palette = if (character == BearCharacter.PEACH) paddlePeach else paddleSun
    drawBearPaddle(
        x = game.player.x,
        y = game.player.y,
        r = game.player.r,
        palette = palette,
        paletteId = paletteId(character),
        vx = game.player.vx,
        vy = game.player.vy,
        shortSide = short,
        hit = game.hitFlash,
        reduceMotion = reduceMotion,
    )
    drawHoneyPuck(game, time, reduceMotion)

    if (game.phase == Phase.SERVE) {
        drawServeHint(game, t, time)
    }

    val flash = game.scoreFlash
    if (flash > 0.01f) {
        val scorer = game.lastScorer
        val tint =
            when (scorer) {
                Scorer.PLAYER -> playerGoal
                Scorer.AI -> aiGoal
                null -> Color.Transparent
            }
        if (tint != Color.Transparent) {
            drawRect(tint.copy(alpha = 0.09f * flash))
            // 减弱动画："+1" 固定在终点不漂移（保留"谁得分"的反馈，去掉位移；
            // alpha 仍随 flash 淡出，淡出不属位移类动效）
            val drift = if (reduceMotion) 1f else 1f - flash
            drawScoreFloat(
                text = "+1",
                x = t.midX,
                y =
                    if (scorer == Scorer.PLAYER) {
                        t.top + short * (0.09f + 0.12f * drift)
                    } else {
                        t.bottom - short * (0.09f + 0.12f * drift)
                    },
                color = tint,
                sizePx = short * 0.06f,
                alpha = flash,
            )
        }
    }
}

/** 深色冰场背景：天气渐变统一压暗，保持 HUD 白字对比度。 */
internal fun hockeyBackdrop(style: WeatherSceneStyle): List<Color> = style.gradient.map { lerp(it, Color(0xFF07131F), 0.52f) }

private fun DrawScope.drawScoreFloat(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizePx: Float,
    alpha: Float,
) {
    if (alpha <= 0.02f || sizePx <= 1f) return
    val paint = HockeyRenderCache.textPaint
    paint.textSize = sizePx
    paint.color = color.toArgb()
    paint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
