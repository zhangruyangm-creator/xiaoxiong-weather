package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.xiaoxiong.weather.R

/** 尺寸设置行：标题 + 当前倍率 + 连续滑块（挡板/冰球/球门共用）。 */
@Composable
internal fun SizeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = HockeyUi.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.bear_hockey_size_value,
                        value.coerceIn(range.start, range.endInclusive),
                    ),
                color = HockeyUi.accentCool,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = HockeyUi.accentCool,
                    activeTrackColor = HockeyUi.accentCool,
                    inactiveTrackColor = HockeyUi.accentCool.copy(alpha = 0.22f),
                ),
        )
    }
}

/** 场地主题选项：主题名 + 摩擦手感说明，当前项高亮，附无障碍内容描述。 */
@Composable
internal fun RinkThemeChip(
    theme: RinkTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(theme.titleRes)
    val desc = rinkThemeDesc(theme)
    val a11y = stringResource(R.string.bear_hockey_rink_theme_a11y, name, desc)
    HockeyFilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else HockeyUi.textSecondary,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.80f) else HockeyUi.textSecondary.copy(alpha = 0.75f),
                )
            }
        },
        modifier = modifier.semantics { contentDescription = a11y },
    )
}

/** 场地主题的摩擦手感说明（模型只带标题资源，说明文案留在 UI 层）。 */
@Composable
private fun rinkThemeDesc(theme: RinkTheme): String =
    when (theme) {
        RinkTheme.ICE -> stringResource(R.string.bear_hockey_rink_ice_desc)
        RinkTheme.BEAR -> stringResource(R.string.bear_hockey_rink_bear_desc)
        RinkTheme.WOOD -> stringResource(R.string.bear_hockey_rink_wood_desc)
        RinkTheme.CONCRETE -> stringResource(R.string.bear_hockey_rink_concrete_desc)
    }

/** 冰球主题筛选胶囊：深色玻璃底 + 冰蓝选中态。 */
@Composable
internal fun HockeyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color(0x14FFFFFF),
                labelColor = HockeyUi.textSecondary,
                selectedContainerColor = Color(0x5534B6E8),
                selectedLabelColor = Color.White,
            ),
        modifier = modifier,
    )
}

/** 冰球主题输入框：深色底 + 冰蓝描边。 */
@Composable
internal fun HockeyOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = HockeyUi.accentCool,
                focusedBorderColor = HockeyUi.accentCool,
                unfocusedBorderColor = HockeyUi.iceBorder,
                focusedLabelColor = HockeyUi.accentCool,
                unfocusedLabelColor = HockeyUi.textSecondary,
            ),
        modifier = modifier,
    )
}
