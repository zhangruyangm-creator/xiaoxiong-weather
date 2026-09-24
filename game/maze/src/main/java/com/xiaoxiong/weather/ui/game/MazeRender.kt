package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- 绘制 ----------------

/**
 * 静态底层（场景装饰 + 墙/地板 + 外框/暗角）缓存：
 * 只在「关卡 / 主题 / 画布尺寸」变化时烘焙一次，之后每帧只 drawImage。
 * 蜂蜜/钥匙/锁/传送门/出口/小熊等仍逐帧绘制。最多保留 [MAX_ENTRIES] 张做 LRU。
 *
 * 缓存实现复用 `game:common` 的 [BakedLayerCache]（与冰球/台球同一套内存策略：
 * 先淘汰最旧再烘焙，峰值不出现双份全屏位图；分配或烘焙失败返回 null 由调用方
 * 回退逐格绘制，不影响玩法）。
 */
internal class MazeBaseLayerCache {
    /** 缓存键：关卡用身份比较（`===`），因此不放进 data class 的 equals。 */
    private class Key(
        val width: Int,
        val height: Int,
        val level: MazeLevel,
        val style: MazeThemeStyle,
        val topReservePx: Float,
        val bottomReservePx: Float,
    ) {
        fun matches(other: Key): Boolean =
            width == other.width &&
                height == other.height &&
                level === other.level &&
                style == other.style &&
                topReservePx == other.topReservePx &&
                bottomReservePx == other.bottomReservePx
    }

    private val cache =
        BakedLayerCache<Key>(
            maxEntries = MAX_ENTRIES,
            reusable = { cached, target -> cached.matches(target) },
        )

    fun bitmapFor(
        game: MazeGame,
        style: MazeThemeStyle,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
        topReservePx: Float,
        bottomReservePx: Float,
    ): ImageBitmap? {
        if (game.rows <= 0 || game.cols <= 0) return null
        // [size] 已由调用方量化到 4px 网格，这里再兜底量化一次保证幂等。
        val w = quantizeBitmapPx(size.width)
        val h = quantizeBitmapPx(size.height)
        if (w <= 0 || h <= 0) return null
        val level = game.level
        return cache
            .getOrBake(
                key =
                    Key(
                        width = w,
                        height = h,
                        level = level,
                        style = style,
                        topReservePx = topReservePx,
                        bottomReservePx = bottomReservePx,
                    ),
                widthPx = w,
                heightPx = h,
                logicalWidth = w.toFloat(),
                logicalHeight = h.toFloat(),
                density = density,
                layoutDirection = layoutDirection,
            ) {
                drawMazeBaseLayer(game, style, topReservePx, bottomReservePx)
            }?.image
    }

    /** 释放位图（离屏时调用）。 */
    fun clear() = cache.clear()

    private companion object {
        const val MAX_ENTRIES = 2
    }
}

/**
 * 位图尺寸量化到 4px 网格：分屏拖拽时画布尺寸的亚像素抖动不再触发反复重烘。
 * 缓存键、静态层烘焙与动态层布局共用同一量化尺寸，保证两层像素对齐。
 */
internal fun quantizeBitmapPx(value: Float): Int {
    val roundedUp = ceil(value).toInt()
    if (roundedUp <= 0) return 0
    return (roundedUp + 3) / 4 * 4
}

/** 对局世界：静态棋盘 + 动态叠加 + 特效粒子 + 小熊演出（天气覆盖层由调用方后绘）。 */
internal fun DrawScope.drawMazeWorld(
    game: MazeGame,
    sprites: BearSprites,
    style: MazeThemeStyle,
    baseLayer: MazeBaseLayerCache,
    fx: MazeFxHost,
    hintDir: MazeDir?,
    reduceMotion: Boolean,
    statusBarTopPx: Float,
    navigationBarBottomPx: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    if (game.rows <= 0 || game.cols <= 0) return
    fx.observe(game, style)
    // HUD / D-pad 的系统栏 inset 由宿主以 px 传入，再叠加固定 dp 预留
    val topReserve = statusBarTopPx + MAZE_HUD_TOP_DP.dp.toPx()
    val bottomReserve = navigationBarBottomPx + MAZE_HUD_BOTTOM_DP.dp.toPx()
    // 静态层缓存键与动态层布局都用量化后的画布尺寸，避免分屏拖拽时的亚像素错位
    val layoutSize =
        Size(
            quantizeBitmapPx(size.width).toFloat(),
            quantizeBitmapPx(size.height).toFloat(),
        )
    if (layoutSize.width <= 0f || layoutSize.height <= 0f) return
    val base = mazeBoardLayout(game.rows, game.cols, layoutSize, topReserve, bottomReserve)
    if (base.cell <= 0f) return
    val time = game.time

    // 主题氛围微粒在棋盘背后，让场景先「活」起来
    drawThemeMotes(game.level.theme, style, time, reduceMotion)

    // 撞墙轻抖：仅偏移棋盘世界，HUD/天气不动
    val shake = if (reduceMotion) 0f else fx.shakeAmp(time, base.cell)
    val shakeX = (shake * fx.bumpDc).roundToInt()
    val shakeY = (shake * fx.bumpDr).roundToInt()
    val layout =
        if (shakeX != 0 || shakeY != 0) {
            MazeBoardLayout(base.cell, base.ox + shakeX, base.oy + shakeY)
        } else {
            base
        }

    // 静态底层：命中缓存则一次 drawImage，否则回退逐格绘制
    val cached =
        baseLayer.bitmapFor(
            game,
            style,
            layoutSize,
            this,
            layoutDirection,
            topReserve,
            bottomReserve,
        )
    if (cached != null) {
        drawImage(
            image = cached,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(cached.width, cached.height),
            dstOffset = IntOffset(shakeX, shakeY),
            dstSize = IntSize(cached.width, cached.height),
            filterQuality = FilterQuality.None,
        )
    } else {
        drawMazeBoardCells(game, style, layout)
    }

    // 动态/叠加层：蜂蜜脉冲、传送门旋转、出口开门、冰/钥匙/锁/起点
    for (r in 0 until game.rows) {
        val y = layout.oy + r * layout.cell
        for (c in 0 until game.cols) {
            drawMazeTileOverlay(
                game = game,
                style = style,
                r = r,
                c = c,
                x = layout.ox + c * layout.cell,
                y = y,
                cell = layout.cell,
                reduceMotion = reduceMotion,
            )
        }
    }

    drawPortalMotes(game, style, layout, reduceMotion)
    drawExitGlow(game, style, layout, reduceMotion)
    drawMazeHint(game, hintDir, layout, reduceMotion)
    drawIceTrail(game, layout, reduceMotion)
    drawPortalGhost(fx, game, style, layout, reduceMotion)
    drawUnlockGhost(fx, game, layout, reduceMotion)
    // 减弱动画时不播事件粒子，只保留静态叠加与光晕
    if (!reduceMotion) with(fx) { drawParticles(layout, time) }

    // 小熊
    drawMazeBear(game, sprites, style, layout, fx, reduceMotion)
}

/**
 * 整局天气覆盖层：雨/雪/雷暴时叠加与当前天气一致的粒子与天色；雨点密度随雨势。
 *
 * [reduceMotion]（减弱动态效果）下只保留静态天色，粒子和闪电等动态天气
 * 由共享特效层与本地分支跳过；[MazeGame.time] 仍照常推进，其它动画不受影响。
 */
internal fun DrawScope.drawMazeWeather(
    game: MazeGame,
    weather: FarmWeather,
    rainStrength: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    reduceMotion: Boolean,
) {
    // 冻雨/冰雹优先于普通雨/雷暴识别
    if (freezingRain) {
        drawRect(Color(0xFF5E7390).copy(alpha = 0.20f))
        drawFreezingRain(game.time, 1f, size.width, size.height, reduceMotion = reduceMotion)
        return
    }
    if (hail) {
        drawRect(Color(0xFF141B28).copy(alpha = 0.30f))
        // 冰雹：雨更少，突出冰雹粒子
        drawRainLayered(
            fgCount = 18,
            bgCount = 9,
            time = game.time,
            width = size.width,
            height = size.height,
            scale = 1f,
            lenMul = 1.1f,
            alpha = 0.55f,
            fgColor = Color(0xFFD8E6F8),
            reduceMotion = reduceMotion,
        )
        drawHailPellets(game.time, 1f, size.width, size.height, count = 44, reduceMotion = reduceMotion)
        drawStormLightning(game.time, 1f, size.width, size.height, reduceMotion = reduceMotion)
        return
    }
    when (weather) {
        FarmWeather.SUNNY, FarmWeather.CLOUDY, FarmWeather.FOGGY -> Unit
        FarmWeather.RAINY -> {
            drawRect(Color(0xFF29364A).copy(alpha = 0.12f))
            when (rainStrength) {
                RainStrength.LIGHT ->
                    drawRainLayered(
                        fgCount = 15,
                        bgCount = 7,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        alpha = 0.45f,
                        reduceMotion = reduceMotion,
                    )
                RainStrength.HEAVY ->
                    drawRainLayered(
                        fgCount = 47,
                        bgCount = 23,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        lenMul = 1.15f,
                        alpha = 0.65f,
                        reduceMotion = reduceMotion,
                    )
                else ->
                    drawRainLayered(
                        fgCount = 31,
                        bgCount = 15,
                        time = game.time,
                        width = size.width,
                        height = size.height,
                        scale = 1f,
                        alpha = 0.55f,
                        reduceMotion = reduceMotion,
                    )
            }
        }
        FarmWeather.STORMY -> {
            drawRect(Color(0xFF151B26).copy(alpha = 0.28f))
            // 雷暴：以闪电为主，雨量收敛到中雨量级
            drawRainLayered(
                fgCount = 32,
                bgCount = 16,
                time = game.time,
                width = size.width,
                height = size.height,
                scale = 1f,
                lenMul = 1.25f,
                alpha = 0.6f,
                reduceMotion = reduceMotion,
            )
            drawStormLightning(game.time, 1f, size.width, size.height, reduceMotion = reduceMotion)
        }
        FarmWeather.SNOWY -> {
            drawRect(Color(0xFFB9C7D6).copy(alpha = 0.08f))
            if (reduceMotion) return
            for (i in 0 until 34) {
                val x = ((i * 83 + game.time * 26) % (size.width + 20))
                val y = ((i * 61 + game.time * 48) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.8f), 2.5f + (i % 3), Offset(x, y))
            }
        }
    }
}

/**
 * 小熊演出：静止呼吸起伏、冰面滑行前倾、落地挤压、撞墙回弹、传送旋入、
 * 拾取上跳、通关庆祝、失败垂头。参数全部按状态插值，遵守 [reduceMotion]。
 */
internal fun DrawScope.drawMazeBear(
    game: MazeGame,
    sprites: BearSprites,
    style: MazeThemeStyle,
    layout: MazeBoardLayout,
    fx: MazeFxHost,
    reduceMotion: Boolean,
) {
    val cell = layout.cell
    if (cell <= 0f) return
    fx.setTrailDir(game.facing.dr, game.facing.dc)
    val (br, bc) = game.bearDrawPos()
    var cx = layout.ox + (bc + 0.5f) * cell
    var cy = layout.oy + (br + 0.5f) * cell
    val time = game.time
    val bs = cell * 0.94f

    // 撞墙回弹：沿撞击反方向小幅位移
    val bump = if (reduceMotion) 0f else fx.bumpDecay(time)
    if (bump > 0f) {
        cx += fx.bumpDc * cell * 0.10f * bump
        cy += fx.bumpDr * cell * 0.10f * bump
    }
    // 拾取上跳
    val hop = if (reduceMotion) 0f else fx.hopLift(time)
    cy -= hop * cell * 0.20f
    // 落地挤压 / 静止呼吸
    val land = if (reduceMotion) 0f else fx.landDecay(time)
    var breath = 0f
    if (!reduceMotion && !game.moving && !game.cleared && game.phase == MazePhase.PLAYING) {
        breath = sin(time * 2.1f) * 0.022f
    }
    var scaleX = 1f + land * 0.16f - breath * 0.6f
    var scaleY = 1f - land * 0.20f + breath

    // 传送门旋入
    var alpha = 1f
    var spin = 0f
    if (!reduceMotion && fx.portalActive(time)) {
        val k = fx.portalSpin(time)
        spin = 360f * (1f - k)
        val s = 0.35f + 0.65f * k
        scaleX *= s
        scaleY *= s
        alpha = k
    }

    // 朝向倾斜
    var tilt =
        when (game.facing) {
            MazeDir.UP -> -6f
            MazeDir.DOWN -> 6f
            else -> 0f
        }
    // 冰面滑行前倾
    if (!reduceMotion && game.moving && game.sliding) {
        tilt +=
            when (game.facing) {
                MazeDir.LEFT -> -9f
                MazeDir.RIGHT -> 9f
                MazeDir.UP -> -4f
                MazeDir.DOWN -> 4f
            }
    }

    // 通关庆祝：转圈 + 跳跃；失败：垂头
    val celebrate = game.cleared && !game.failed
    if (celebrate && !reduceMotion) {
        val age = fx.celebrateAge(time)
        if (age >= 0f) {
            cy -= cell * 0.16f * abs(sin(age * 3.4f))
            spin = sin(age * 2.6f) * 12f
            tilt = 0f
        }
    }
    if (game.failed && !reduceMotion) {
        tilt = 10f
        scaleX *= 1.03f
        scaleY *= 0.94f
    }

    val frame =
        when {
            game.failed -> BearFrame.LAND
            celebrate -> if (reduceMotion) BearFrame.LAND else BearFrame.JUMP
            game.moving && game.sliding -> BearFrame.JUMP
            // 减弱动画：移动时固定单帧，不跑循环动画（格子位移本身照常）
            game.moving -> if (reduceMotion) BearFrame.RUN_A else runCycleFrame(time * 10f)
            else -> BearFrame.LAND
        }
    val flip = game.facing == MazeDir.LEFT

    // 脚下影子：高度越高越淡越小
    val lift = (hop * 0.20f + (if (celebrate && !reduceMotion) 0.10f else 0f)).coerceIn(0f, 0.3f)
    val shadowScale = 1f - lift * 1.4f
    drawOval(
        Color.Black.copy(alpha = 0.18f - lift * 0.4f),
        Offset(cx - bs * 0.28f * shadowScale, cy + bs * 0.28f),
        Size(bs * 0.56f * shadowScale, bs * 0.16f * shadowScale),
    )
    drawGameBear(
        sprites = sprites,
        centerX = cx,
        centerY = cy - bs * 0.05f,
        bs = bs,
        pose =
            BearPose(
                time = time,
                frame = frame,
                bodyTilt = tilt + spin,
                flipX = flip,
                scaleX = scaleX,
                scaleY = scaleY,
                alpha = alpha,
            ),
    )
    // 起点/出口的方向粒子由 fx 负责；这里仅在拾取瞬间补一圈上扬光点
    if (hop > 0f && !reduceMotion) {
        val k = 1f - hop
        drawCircle(
            style.fx.copy(alpha = 0.25f * k),
            cell * (0.16f + 0.18f * (1f - k)),
            Offset(cx, cy + cell * 0.46f),
        )
    }
}
