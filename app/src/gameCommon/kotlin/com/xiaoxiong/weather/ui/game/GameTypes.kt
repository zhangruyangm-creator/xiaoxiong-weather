package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color

/** 通用游戏阶段：跳跳 / 摘星共用。 */
enum class GamePhase { READY, RUNNING, OVER }

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
