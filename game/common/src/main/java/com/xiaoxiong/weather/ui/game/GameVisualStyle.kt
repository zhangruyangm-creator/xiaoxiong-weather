package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 游戏内浮层的统一视觉规范。
 *
 * 「深色玻璃」= 近黑蓝底 + 冷色描边 + 顶部一道冷→暖高光条 + 投影。
 * 冰球最先用这套（判定/结算/设置都是深色玻璃卡片），这里抽成公共件后
 * 迷宫、农场、小熊的家共用同一套外观，消掉"同类游戏质感落差"。
 *
 * 只描述外观，不含 padding 与尺寸：调用方保留原有布局尺寸，
 * 视觉统一不顺带改排版。
 */
@Immutable
data class GamePanelStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val borderWidth: Dp = 1.dp,
    val shape: Shape = RoundedCornerShape(22.dp),
    val shadowElevation: Dp = 12.dp,
    /** 顶部高光条；null 表示不加。 */
    val sheenColors: List<Color>? = null,
)

/** 深色玻璃面板默认样式，与冰球结算卡一致。 */
val GamePanelStyleDefaults: GamePanelStyle
    @Composable get() =
        GamePanelStyle(
            containerColor = Color(0xF20C1622),
            contentColor = Color(0xFFEAF2FF),
            borderColor = Color(0x998FD9F2),
            sheenColors =
                listOf(
                    Color(0xFF7FE3F2).copy(alpha = 0.85f),
                    Color(0xFFFFC46B).copy(alpha = 0.5f),
                    Color.Transparent,
                ),
        )

/**
 * 暖纸浅色面板：给绘本/田园类场景（家、农场卡面）用。
 * 保持浅色底，但补上深色玻璃那套的**描边 + 投影 + 统一圆角**，
 * 这样"精致度"对齐，而不是把暖色界面硬改成深色。
 */
val WarmPaperPanelStyle: GamePanelStyle
    @Composable get() =
        GamePanelStyle(
            containerColor = Color(0xF2FFF8EF),
            contentColor = Color(0xFF5D4037),
            borderColor = Color(0x33FFFFFF),
            borderWidth = 1.dp,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp,
            sheenColors =
                listOf(
                    Color.White.copy(alpha = 0.9f),
                    Color(0xFFFFE0B2).copy(alpha = 0.35f),
                    Color.Transparent,
                ),
        )

/**
 * 统一浮层面板。背景/描边/高光/投影由 [style] 决定，内容原样透传。
 *
 * 高光条画在容器内并被 [shape] 裁剪，因此不需要调用方额外处理圆角。
 */
@Composable
fun GamePanel(
    style: GamePanelStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(
                    elevation = style.shadowElevation,
                    shape = style.shape,
                    clip = false,
                ).clip(style.shape)
                .background(style.containerColor)
                .border(style.borderWidth, style.borderColor, style.shape),
    ) {
        val sheen = style.sheenColors
        if (sheen != null) {
            // 顶部 3dp 冷→暖→透明：深色玻璃的关键细节，贴着顶边才不显脏
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Brush.horizontalGradient(sheen)),
            )
        }
        CompositionLocalProvider(
            LocalContentColor provides style.contentColor,
            content = content,
        )
    }
}

/**
 * 游戏内小型控件的统一样式（图标钮 / 芯片 / 选择卡）。
 *
 * [pressedScale] 让"按下去有反馈"在所有游戏里一致；它通过 [scale] 只改绘制
 * 不参与布局，不会缩小可点区域。
 */
@Immutable
data class GameControlStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val selectedContainerColor: Color = containerColor,
    val selectedContentColor: Color = contentColor,
    val selectedBorderColor: Color = borderColor,
    val borderWidth: Dp = 1.dp,
    val shape: Shape = RoundedCornerShape(16.dp),
    val shadowElevation: Dp = 0.dp,
    val pressedScale: Float = 0.94f,
    val disabledAlpha: Float = 0.45f,
) {
    companion object {
        /** 深色玻璃图标钮（与迷宫玻璃圆钮同款）。 */
        val glassIcon: GameControlStyle
            @Composable get() =
                GameControlStyle(
                    containerColor = Color(0xB31A2433),
                    contentColor = Color(0xFFEAF2FF),
                    borderColor = Color(0x52FFFFFF),
                    shape = CircleShape,
                )

        /** 芯片：未选中近透明底，选中蓝底白字。 */
        val chip: GameControlStyle
            @Composable get() =
                GameControlStyle(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color(0xFFDCE7F5),
                    borderColor = Color(0x33FFFFFF),
                    selectedContainerColor = Color(0x5534B6E8),
                    selectedContentColor = Color.White,
                    selectedBorderColor = Color(0x887FE3F2),
                )
    }
}

/**
 * 统一样式的小控件：背景/描边/按压反馈由 [style] 决定。
 *
 * [enabled] / [onClick] / [contentDescription] / 选中态语义全部原样透传，
 * 本组件不吞事件、不改语义角色。
 */
@Composable
fun GameControl(
    onClick: () -> Unit,
    style: GameControlStyle,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = if (selected) style.selectedContainerColor else style.containerColor
    val contentColor = if (selected) style.selectedContentColor else style.contentColor
    val shape = style.shape
    Box(
        modifier =
            modifier
                .then(
                    if (style.shadowElevation > 0.dp) {
                        Modifier.shadow(style.shadowElevation, shape, clip = false)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(if (enabled) container else container.copy(alpha = style.disabledAlpha))
                .border(style.borderWidth, style.borderColor, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ).scale(
                    if (pressed && enabled) {
                        style.pressedScale
                    } else {
                        1f
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            content = content,
        )
    }
}

/** 圆形图标钮：容器直径由 [diameter] 决定，图标取直径的一半。 */
@Composable
fun GameIconControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GameControlStyle = GameControlStyle.glassIcon,
    diameter: Dp = 38.dp,
) {
    GameControl(
        onClick = onClick,
        style = style,
        enabled = enabled,
        contentDescription = contentDescription,
        modifier = modifier.size(diameter),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(diameter * 0.5f),
        )
    }
}

/** 文本芯片：顶部状态胶囊、工具切换等。 */
@Composable
fun GameChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    style: GameControlStyle = GameControlStyle.chip,
    textStyle: TextStyle = TextStyle(fontSize = 12.sp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
) {
    GameControl(
        onClick = onClick,
        style = style,
        selected = selected,
        enabled = enabled,
        modifier = modifier,
    ) {
        ProvideTextStyle(textStyle) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}
