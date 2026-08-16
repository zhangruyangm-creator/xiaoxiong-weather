package com.xiaoxiong.weather.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 减弱动画标志：应用内「减弱动画」开关 或 系统「移除动画」（ANIMATOR_DURATION_SCALE=0）开启时为 true。
 * 天气场景等重动画组件据此跳过无限循环动画，避免前庭敏感用户不适。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
