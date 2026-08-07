package com.xiaoxiong.weather.ui.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppScreenBackground(
    modifier: Modifier = Modifier,
    sceneCode: Int? = null,
    isDay: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    // 有天气数据时，整屏背景跟随场景取色（低透明度，保证卡片可读）；
    // 无数据时保持原来的静态渐变
    val sceneStyle = sceneCode?.let { weatherSceneStyle(it, isDay, dark) }
    val topTarget =
        if (sceneStyle != null) {
            sceneStyle.gradient[0].copy(alpha = if (dark) 0.34f else 0.28f)
        } else {
            scheme.primary.copy(alpha = 0.18f)
        }
    val midTarget =
        if (sceneStyle != null) {
            sceneStyle.gradient
                .getOrElse(1) { sceneStyle.gradient[0] }
                .copy(alpha = if (dark) 0.14f else 0.10f)
        } else {
            scheme.surfaceVariant.copy(alpha = 0.55f)
        }
    val bottomTarget = scheme.background
    val top by animateColorAsState(topTarget, tween(1200), label = "bgTop")
    val mid by animateColorAsState(midTarget, tween(1200), label = "bgMid")
    val bottom by animateColorAsState(bottomTarget, tween(1200), label = "bgBottom")
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(listOf(top, mid, bottom)),
            ),
    ) {
        content()
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Column(
            modifier =
                modifier
                    .shadow(
                        elevation = if (dark) 2.dp else 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        clip = false,
                        ambientColor = if (dark) Color.Black.copy(alpha = 0.35f) else Color.Black,
                        spotColor = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black,
                    ).clip(RoundedCornerShape(22.dp))
                    .background(scheme.surface)
                    .border(
                        width = 1.dp,
                        color = scheme.outline.copy(alpha = if (dark) 0.35f else 0.55f),
                        shape = RoundedCornerShape(22.dp),
                    ).padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    scheme.primary.copy(alpha = 0.22f),
                                    scheme.secondary.copy(alpha = 0.16f),
                                ),
                            ),
                        ).border(
                            width = 1.dp,
                            color = scheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FrostedMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    contentColor: Color,
    mutedColor: Color,
) {
    // 深色字场景用深色半透明底，浅色字用白色玻璃，避免暗色模式对比失调
    val glass =
        if (contentColor.luminance() < 0.45f) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
    val stroke =
        if (contentColor.luminance() < 0.45f) {
            Color.Black.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.22f)
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(glass)
                .border(1.dp, stroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = mutedColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
fun QualityBar(
    label: String,
    valueText: String,
    progress: Float,
    barColor: Color,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = onSurface,
            )
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(barColor),
            )
        }
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
    )
}
