package com.xiaoxiong.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

/**
 * 减弱动画标志：应用内「减弱动画」开关 或 系统「移除动画」（ANIMATOR_DURATION_SCALE=0）开启时为 true。
 * 天气场景等重动画组件据此跳过无限循环动画，避免前庭敏感用户不适。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * 加载指示：默认用 Material3 不定进度圈（持续旋转），
 * 减弱动画时改为静态文字提示——不定进度圈是无意义的持续运动，
 * 且读屏会反复播报进度百分比。
 */
@Composable
fun ReduceMotionAwareProgress(modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) {
        Text(
            text = stringResource(R.string.loading_ellipsis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.clearAndSetSemantics {},
        )
    } else {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(40.dp),
        )
    }
}

/** 居中铺满的加载态（首屏/历史页用）。 */
@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReduceMotionAwareProgress()
    }
}

/** 行内居中的加载态（历史列表等）。 */
@Composable
fun InlineLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReduceMotionAwareProgress()
    }
}
