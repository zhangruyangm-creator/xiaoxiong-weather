package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 小熊的帧（共 8）：
 * 跑步 6 拍循环 RUN_A → RUN_PUSH_A → RUN_PASS → RUN_B → RUN_PUSH_B → RUN_PASS，
 * 加上 JUMP（上升）/ FALL（下落）/ LAND（落地）。
 */
enum class BearFrame {
    RUN_A, RUN_PUSH_A, RUN_PASS, RUN_B, RUN_PUSH_B, JUMP, FALL, LAND
}

/** 小熊精灵表：各帧位图，由 Screen 层用 imageResource 加载后传入。 */
class BearSprites(
    val runA: ImageBitmap,
    val runPushA: ImageBitmap,
    val runPass: ImageBitmap,
    val runB: ImageBitmap,
    val runPushB: ImageBitmap,
    val jump: ImageBitmap,
    val fall: ImageBitmap,
    val land: ImageBitmap
) {
    fun of(frame: BearFrame): ImageBitmap = when (frame) {
        BearFrame.RUN_A -> runA
        BearFrame.RUN_PUSH_A -> runPushA
        BearFrame.RUN_PASS -> runPass
        BearFrame.RUN_B -> runB
        BearFrame.RUN_PUSH_B -> runPushB
        BearFrame.JUMP -> jump
        BearFrame.FALL -> fall
        BearFrame.LAND -> land
    }
}

/**
 * 小熊绘制姿态：各游戏（跑酷/跳台）各自算好物理变换与当前帧，
 * 统一交给 [drawGameBear] 绘制，保证两个游戏角色一致。
 */
class BearPose(
    val time: Float = 0f,
    val dead: Boolean = false,
    val frame: BearFrame = BearFrame.JUMP,
    /** 整体旋转（倾斜 + 翻滚），角度制。 */
    val bodyTilt: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    /** 水平镜像（朝左）。枢轴为小熊中心，画布绝对坐标。 */
    val flipX: Boolean = false,
    val alpha: Float = 1f
)

/**
 * 绘制太阳熊：官方插画序列帧 + 游戏手感变换（倾斜/挤压拉伸/翻滚/镜像/透明度）。
 * 帧图是"面向右侧"的全身像；死亡时头顶绕眩晕星星。
 *
 * 所有内容都按画布绝对坐标绘制，变换枢轴也全部用绝对坐标：
 * 不能先 translate 再用默认枢轴 scale/rotate——后声明的变换作用在
 * 外层坐标系上，枢轴会落到画布原点，导致镜像/倾斜时小熊飞出格子。
 */
fun DrawScope.drawGameBear(
    sprites: BearSprites,
    centerX: Float,
    centerY: Float,
    bs: Float,
    pose: BearPose
) {
    if (pose.alpha <= 0f) return
    withTransform({
        // 声明顺序即作用于点坐标的顺序：先镜像帧图，再倾斜，最后挤压拉伸
        if (pose.flipX) {
            scale(-1f, 1f, pivot = Offset(centerX, centerY))
        }
        if (pose.bodyTilt != 0f) {
            rotate(pose.bodyTilt, pivot = Offset(centerX, centerY))
        }
        if (pose.scaleX != 1f || pose.scaleY != 1f) {
            scale(pose.scaleX, pose.scaleY, pivot = Offset(centerX, centerY + bs * 0.5f))
        }
    }) {
        val img = sprites.of(pose.frame)
        val side = bs.roundToInt().coerceAtLeast(1)
        val half = (side / 2)
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(
                (centerX - half).roundToInt(),
                (centerY - half).roundToInt()
            ),
            dstSize = IntSize(side, side),
            alpha = pose.alpha,
            filterQuality = FilterQuality.High
        )
        if (pose.dead) {
            // 眩晕星星：三颗金星绕头顶转圈
            for (i in 0 until 3) {
                val a = pose.time * 7f + i * (Math.PI.toFloat() * 2f / 3f)
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = bs * 0.05f,
                    center = Offset(
                        centerX + cos(a) * bs * 0.32f,
                        centerY - bs * 0.55f + sin(a) * bs * 0.10f
                    ),
                    alpha = pose.alpha
                )
            }
        }
    }
}

/** 跑步 6 拍循环：按 runPhase 分档切帧。 */
fun runCycleFrame(runPhase: Float): BearFrame {
    val twoPi = Math.PI.toFloat() * 2f
    val phase = ((runPhase % twoPi) + twoPi) % twoPi
    val step = (phase / (twoPi / 6f)).toInt().coerceIn(0, 5)
    return when (step) {
        0 -> BearFrame.RUN_A
        1 -> BearFrame.RUN_PUSH_A
        2 -> BearFrame.RUN_PASS
        3 -> BearFrame.RUN_B
        4 -> BearFrame.RUN_PUSH_B
        else -> BearFrame.RUN_PASS
    }
}
