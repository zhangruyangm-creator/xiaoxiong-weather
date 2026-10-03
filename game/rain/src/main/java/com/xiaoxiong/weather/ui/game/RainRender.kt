package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * 小熊雨幕 · 绘制层。
 *
 * ## 区分度是这个玩法的命脉，因此用**四重冗余**表达"能点 / 不能点"
 *
 * 只靠颜色区分在高密度高速下会失效（色弱、小屏、强光下都不可靠），所以：
 *  1. **形状**：雨滴=圆形（圆润、亲和）；冰雹=六边形（棱角、硬质）。形状是前注意加工，
 *     人类能在高速运动中先感知轮廓再感知颜色，是最强的一重信号；
 *  2. **色相**：雨滴=暖金（40°），冰雹=冷冰蓝（202°）。冷暖对立在暗背景上极易分辨；
 *  3. **描边**：冰雹带一圈**深藏青粗描边**，雨滴没有描边。描边让"禁击"有物理边框感；
 *  4. **标记**：冰雹额外画一道**深色斜杠**（类似禁止符号），雨滴只带高光。
 *
 * 另加一条**动态冗余**：处于命中窗内的方块会亮起光环——把"现在该点哪一块"
 * 也变成视觉信号，同时不替代上面四重（去掉它依然可分辨）。
 *
 * 这些约束由 `RainPaletteTest` 断言（形状不同 + 亮度/色相差足够），不只靠肉眼。
 */

/**
 * 天气场景 → 配色。
 *
 * ## 为什么要有 [playField] 底板
 * 方块的辨识度**不能依赖天空底色**：晴天底色很亮、冰雹是浅色，若直接把方块画在天空上，
 * 冰雹与背景的对比度只有 1.04（几乎隐形）——这是实测出来的真实问题。
 * 因此在游戏区叠一层半透明深色底板，让方块对比度与天气解耦：
 * 三种天气下底板合成后的有效底色都在暗区间，方块对比度稳定在 4~10。
 */
data class RainPalette(
    val skyTop: Color,
    val skyBottom: Color,
    /** 游戏区底板色（半透明，叠在天空上）。 */
    val playField: Color,
    /** 底板不透明度。 */
    val playFieldAlpha: Float,
    /** 雨滴本体色（暖金，且亮度高于冰雹——"该点的"必须更显眼）。 */
    val dropFill: Color,
    /** 雨滴高光。 */
    val dropHighlight: Color,
    /** 冰雹本体色（冷冰蓝）。 */
    val hailFill: Color,
    /** 冰雹描边与斜杠色（近黑深藏青）。 */
    val hailEdge: Color,
    /** 判定线。 */
    val judgeLine: Color,
    /** 可点光环。 */
    val hittableRing: Color,
    /** 背景雨丝。 */
    val rainColor: Color,
)

/**
 * 底板叠加在天空上之后的**有效底色**。
 *
 * 方块真正衬在它上面，所以对比度必须对这个合成结果计算，而不是对天空色。
 * 提取成函数供测试直接复用，避免"测试算一套、画面画另一套"。
 */
fun effectiveBackdrop(palette: RainPalette): Color {
    val a = palette.playFieldAlpha
    return Color(
        red = palette.playField.red * a + palette.skyBottom.red * (1 - a),
        green = palette.playField.green * a + palette.skyBottom.green * (1 - a),
        blue = palette.playField.blue * a + palette.skyBottom.blue * (1 - a),
        alpha = 1f,
    )
}

/** 取配色。 */
fun rainPaletteOf(scene: RainScene): RainPalette =
    when (scene) {
        RainScene.SUNNY ->
            RainPalette(
                skyTop = Color(0xFF2E86C8),
                skyBottom = Color(0xFF5FB0E8),
                playField = Color(0xFF0C1A28),
                playFieldAlpha = 0.62f,
                dropFill = Color(0xFFFFC531),
                dropHighlight = Color(0xFFFFF6D2),
                hailFill = Color(0xFF5FB4D8),
                hailEdge = Color(0xFF08131F),
                judgeLine = Color(0xFFFFFFFF),
                hittableRing = Color(0xFFFFF0B0),
                rainColor = Color(0xFFFFFFFF),
            )
        RainScene.RAIN ->
            RainPalette(
                skyTop = Color(0xFF22384C),
                skyBottom = Color(0xFF4A6E88),
                playField = Color(0xFF0C1A28),
                playFieldAlpha = 0.62f,
                dropFill = Color(0xFFFFC531),
                dropHighlight = Color(0xFFFFF6D2),
                hailFill = Color(0xFF5FB4D8),
                hailEdge = Color(0xFF08131F),
                judgeLine = Color(0xFFFFFFFF),
                hittableRing = Color(0xFFFFF0B0),
                rainColor = Color(0xFFD6E9F5),
            )
        RainScene.STORM ->
            RainPalette(
                skyTop = Color(0xFF161C2A),
                skyBottom = Color(0xFF33405A),
                playField = Color(0xFF0C1A28),
                playFieldAlpha = 0.62f,
                dropFill = Color(0xFFFFC531),
                dropHighlight = Color(0xFFFFF6D2),
                hailFill = Color(0xFF5FB4D8),
                hailEdge = Color(0xFF08131F),
                judgeLine = Color(0xFFFFFFFF),
                hittableRing = Color(0xFFFFF0B0),
                rainColor = Color(0xFFC7D9E8),
            )
    }

/** 天气场景（只影响背景氛围，不影响玩法判定）。 */
enum class RainScene {
    SUNNY,
    RAIN,
    STORM,
}

/**
 * 方块的目标半径（相对屏高）。
 *
 * 冰雹略小：读作"不是目标"，与形状/描边/斜杠共同强化"不能点"。
 */
const val RAIN_DROP_RADIUS = 0.062f

/** 冰雹半径系数（略小于雨滴）。 */
const val RAIN_HAIL_RADIUS_SCALE = 0.86f

/**
 * 冰雹描边宽度（相对半径）。
 *
 * 上限约束：描边沿路径向内外各吃 `w/2`，取得过粗（如 0.22）时，
 * 深色边框会与斜杠糊成一片，冰蓝填充被压成两条小缝——
 * 冰雹就读作「黑色六边形」而不是「冰块」。取 0.13 让填充保持主导。
 */
const val RAIN_HAIL_STROKE_RATIO = 0.13f

/** 冰雹斜杠宽度（相对半径）。与描边同粗，二者才不会连成一片黑。 */
const val RAIN_HAIL_SLASH_RATIO = 0.13f

/** 冰雹斜杠的半长（相对半径）。 */
private const val RAIN_HAIL_SLASH_HALF = 0.52f

/**
 * 雨滴高光半径（相对半径）。
 *
 * 取得过大（如 0.42）时高光占满大半且偏心，远看像「带白心的蛋黄」而非水珠；
 * 缩小并把偏移推向外缘，才读作水面反光。
 */
const val RAIN_DROP_HIGHLIGHT_RATIO = 0.26f

/** 雨滴高光中心偏移（相对半径，x 向左、y 向上）。 */
private const val RAIN_DROP_HIGHLIGHT_DX = -0.34f

/** 雨滴高光中心偏移（相对半径，y 方向）。 */
private const val RAIN_DROP_HIGHLIGHT_DY = -0.36f

/**
 * 绘制一整局。
 *
 * @param hittable 该方块是否处于命中窗（用于画可点光环）。
 */
internal fun DrawScope.drawRainWorld(
    game: RainFall,
    palette: RainPalette,
    reduceMotion: Boolean,
    decorativeTime: Float,
    hitFlash: Float,
    laneWidth: Dp,
) {
    val w = size.width
    val h = size.height
    val radius = h * RAIN_DROP_RADIUS
    val lineY = h * RainFall.HIT_LINE_RATIO

    // 背景渐变
    drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom)))

    // 游戏区底板：把方块对比度与天空解耦（见 RainPalette.playField）
    drawRect(
        brush =
            Brush.verticalGradient(
                listOf(
                    palette.playField.copy(alpha = palette.playFieldAlpha),
                    palette.playField.copy(alpha = palette.playFieldAlpha * 0.55f),
                ),
            ),
    )

    // 背景雨丝：纯装饰，走 decorativeTime（减弱动画时冻结）
    drawBackdropRain(palette.rainColor, decorativeTime, reduceMotion, w, h)

    // 可点光环（画在方块下层，形成"这一块现在该点"的引导）
    for (tile in game.activeTiles()) {
        if (!game.isHittable(tile)) continue
        val pos = game.tilePositionAt(tile)
        if (pos < 0f || pos > lineY) continue
        val cx = w / 2f
        val cy = pos
        drawCircle(
            color = palette.hittableRing.copy(alpha = 0.30f),
            radius = radius * 1.45f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = palette.hittableRing.copy(alpha = 0.85f),
            radius = radius * 1.18f,
            center = Offset(cx, cy),
            style = Stroke(width = h * 0.006f),
        )
    }

    // 判定线：命中区中心
    drawLine(
        color = palette.judgeLine.copy(alpha = 0.9f),
        start = Offset(w * 0.08f, lineY),
        end = Offset(w * 0.92f, lineY),
        strokeWidth = h * 0.008f,
        cap = StrokeCap.Round,
    )

    // 方块
    for (tile in game.activeTiles()) {
        val pos = game.tilePositionAt(tile)
        if (pos < -radius * 2f || pos > lineY + radius * 2f) continue
        drawRainTile(
            kind = tile.kind,
            center = Offset(w / 2f, pos),
            radius = if (tile.kind == RainKind.DROP) radius else radius * RAIN_HAIL_RADIUS_SCALE,
            palette = palette,
        )
    }

    // 命中闪光：判定线处的一圈扩散
    if (hitFlash > 0.01f) {
        drawCircle(
            color = palette.judgeLine.copy(alpha = 0.35f * hitFlash),
            radius = radius * (1.0f + (1f - hitFlash) * 2.2f),
            center = Offset(w / 2f, lineY),
        )
    }
}

/**
 * 画一块方块：**形状 + 颜色 + 描边 + 标记** 四重区分全在这里。
 *
 * - 雨滴：圆形 + 暖金填充 + 顶部高光
 * - 冰雹：六边形 + 冷冰蓝填充 + 深藏青粗描边 + 深色斜杠
 */
internal fun DrawScope.drawRainTile(
    kind: RainKind,
    center: Offset,
    radius: Float,
    palette: RainPalette,
) {
    when (kind) {
        RainKind.DROP -> {
            // 雨滴：圆 + 小而靠外的水面反光
            drawCircle(color = palette.dropFill, radius = radius, center = center)
            drawCircle(
                color = palette.dropHighlight.copy(alpha = 0.85f),
                radius = radius * RAIN_DROP_HIGHLIGHT_RATIO,
                center = Offset(center.x + radius * RAIN_DROP_HIGHLIGHT_DX, center.y + radius * RAIN_DROP_HIGHLIGHT_DY),
            )
        }
        RainKind.HAIL -> {
            // 冰雹：六边形 + 深描边 + 斜杠
            val path = hexagonPath(center, radius)
            drawPath(path, palette.hailFill)
            drawPath(path, palette.hailEdge, style = Stroke(width = radius * RAIN_HAIL_STROKE_RATIO))
            // 深色斜杠（禁止符号）：与描边同粗，避免与外框糊成一片
            drawLine(
                color = palette.hailEdge,
                start =
                    Offset(
                        center.x - radius * RAIN_HAIL_SLASH_HALF,
                        center.y + radius * RAIN_HAIL_SLASH_HALF,
                    ),
                end =
                    Offset(
                        center.x + radius * RAIN_HAIL_SLASH_HALF,
                        center.y - radius * RAIN_HAIL_SLASH_HALF,
                    ),
                strokeWidth = radius * RAIN_HAIL_SLASH_RATIO,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 生成正六边形路径（冰雹专用，与雨滴的圆形形成形状对立）。 */
private fun hexagonPath(
    center: Offset,
    radius: Float,
): Path {
    val path = Path()
    val rotation = PI / 6.0 // 顶点朝上，比平顶更"硬"
    for (i in 0..6) {
        val angle = rotation + i * PI / 3.0
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 背景雨丝：纯装饰运动。 */
private fun DrawScope.drawBackdropRain(
    color: Color,
    time: Float,
    reduceMotion: Boolean,
    width: Float,
    height: Float,
    count: Int = 26,
) {
    if (reduceMotion) return
    for (i in 0 until count) {
        val h1 = pseudoHash(i * 1.7f + 0.3f)
        val h2 = pseudoHash(i * 3.1f + 1.9f)
        val speed = height * (0.55f + h2 * 0.5f)
        val y = (time * speed + h1 * height) % height
        val x = ((h2 * width) + 8f * sin(time * 0.7f + i)) % width
        drawLine(
            color = color.copy(alpha = 0.16f + h1 * 0.12f),
            start = Offset(x, y),
            end = Offset(x - 3f, y + height * 0.035f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

/** 确定性伪随机（0..1），避免引入 Random 状态。 */
private fun pseudoHash(x: Float): Float {
    val v = sin(x * 12.9898f) * 43758.5453f
    return v - v.toInt()
}

/** 命中闪光衰减步进（每帧调用）。 */
fun decayHitFlash(
    state: Float,
    dt: Float,
): Float = max(0f, state - dt * 4.2f)

/** 命中光强调制（衰减速率由 [decayHitFlash] 控制）。 */
fun hitFlashFromJudgment(judgment: RainTapResult): Float =
    when (judgment) {
        RainTapResult.HIT_DROP -> 1f
        RainTapResult.TAPPED_HAIL -> 1f
    }

/**
 * 色相角（0..360）——区分度测试用。
 *
 * 把"雨滴与冰雹色相足够不同"变成可断言的数值，而不是靠肉眼判断。
 */
fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxV = max(r, max(g, b))
    val minV = min(r, min(g, b))
    val delta = maxV - minV
    if (delta < 1e-4f) return 0f
    val hue =
        when (maxV) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
    return (hue + 360f) % 360f
}

/** 相对亮度（WCAG 定义）。 */
fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).let { it * it * it }
        }
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

/** WCAG 对比度（1..21）。 */
fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** 色相差的最小圆周距离（度）。 */
fun hueDistance(
    a: Float,
    b: Float,
): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

/** 供测试读取的绘制常量。 */
object RainRenderMetrics {
    /** 雨滴形状标识：圆形。 */
    const val DROP_SHAPE = "circle"

    /** 冰雹形状标识：六边形。 */
    const val HAIL_SHAPE = "hexagon"

    /** 冰雹描边宽度系数（相对半径）。 */
    const val HAIL_STROKE_RATIO = RAIN_HAIL_STROKE_RATIO
}

/**
 * 六边形 Shape（Compose 无内置）。
 *
 * 与游戏内冰雹的 [hexagonPath] 使用**同一套角度**（顶点朝上），保证图例与实物形状一致。
 */
val RainHexagonShape: androidx.compose.ui.graphics.Shape =
    object : androidx.compose.ui.graphics.Shape {
        override fun createOutline(
            size: androidx.compose.ui.geometry.Size,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ): androidx.compose.ui.graphics.Outline =
            androidx.compose.ui.graphics.Outline
                .Generic(hexagonPath(size))
    }

/** 以中心为原点的六边形路径（供 Shape 与绘制共用）。 */
private fun hexagonPath(size: androidx.compose.ui.geometry.Size): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    // 用外接圆半径换算，使六边形尽量填满给定尺寸
    val radius = min(size.width, size.height) / 2f
    val path = Path()
    val rotation = PI / 6.0
    for (i in 0..6) {
        val angle = rotation + i * PI / 3.0
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
