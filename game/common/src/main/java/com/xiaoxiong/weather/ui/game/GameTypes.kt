package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color

/** 通用游戏阶段：跳跳 / 摘星共用。 */
enum class GamePhase { READY, RUNNING, OVER }

/**
 * 死亡（OVER）演出时间缩放：保留「慢动作沉入水底/淡出」的收尾演出，更有仪式感。
 * 只作用于 OVER；标题界面（READY）仍按真实速度推进，避免此前节流被误当慢动作的问题。
 */
const val GAME_OVER_SLOW_MOTION_SCALE = 0.3f

fun gameOverTimeScale(phase: GamePhase): Float = if (phase == GamePhase.OVER) GAME_OVER_SLOW_MOTION_SCALE else 1f

/** 通用飘分/特效粒子：跳跳 / 摘星共用。 */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float = life,
    val color: Color = Color.White,
)
