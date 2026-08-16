package com.xiaoxiong.weather.ui.game

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 农场 UI 的 Material 色板：跟随应用主题（含深色模式自动适配）。 */
@Composable
internal fun farmScheme() = MaterialTheme.colorScheme

/** 农场弹窗进出场：打开 = 淡入 + 放大 + 从下上滑；关闭 = 淡出 + 缩小。 */
internal val farmDialogEnter: EnterTransition =
    fadeIn(animationSpec = tween(160)) +
        scaleIn(initialScale = 0.94f, animationSpec = tween(220)) +
        slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(220))

internal val farmDialogExit: ExitTransition =
    fadeOut(animationSpec = tween(120)) +
        scaleOut(targetScale = 0.96f, animationSpec = tween(120))

/**
 * 按压缩放反馈：点击时轻微缩小（配合 Material 涟漪，手感更像实体按键）。
 * 返回的 [MutableInteractionSource] 必须传给同一个可点击组件（Surface/Card/Button）。
 */
@Composable
internal fun rememberFarmPress(): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) 0.95f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "farmPressScale",
        )
    val modifier =
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    return interactionSource to modifier
}
