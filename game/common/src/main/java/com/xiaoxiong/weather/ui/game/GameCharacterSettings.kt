package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

/** 游戏 HUD 上的主角切换入口（半透明白色，与 [LeaderboardButton] 并列）。 */
@Composable
fun GameCharacterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.72f),
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Face,
            contentDescription = stringResource(R.string.cd_bear_character),
            tint = tint,
        )
    }
}

/** 主角选择弹窗：跳跳 / 摘星 / 迷宫 / 接物品等动作游戏共用。 */
@Composable
fun GameCharacterPickerDialog(
    state: BearCharacterState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.bear_character_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bear_character_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BearCharacter.entries.forEach { option ->
                    CharacterOptionCard(
                        option = option,
                        selected = state.selected == option,
                        onClick = { state.select(option) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bear_character_done))
            }
        },
    )
}

/** 储物间用的内嵌主角选择区（非弹窗）。 */
@Composable
fun GameCharacterPickerPanel(
    state: BearCharacterState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.bear_character_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.bear_character_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8D6E63),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        BearCharacter.entries.forEach { option ->
            CharacterOptionCard(
                option = option,
                selected = state.selected == option,
                onClick = { state.select(option) },
                modifier = Modifier.padding(horizontal = 16.dp),
                homeStyle = true,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CharacterOptionCard(
    option: BearCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeStyle: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor =
        if (homeStyle) {
            if (selected) Color(0xFF8D6E63) else Color(0xFFBCAAA4)
        } else if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }
    val bgColor =
        if (homeStyle) {
            if (selected) Color(0xFFFFF3E0) else Color.White
        } else if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    val titleColor =
        if (homeStyle) Color(0xFF5D4037) else MaterialTheme.colorScheme.onSurface
    val detailColor =
        if (homeStyle) {
            Color(0xFF8D6E63)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = shape,
        color = bgColor,
        shadowElevation =
            if (homeStyle && selected) {
                3.dp
            } else if (homeStyle) {
                1.dp
            } else {
                0.dp
            },
        modifier =
            modifier
                .fillMaxWidth()
                .border(2.dp, borderColor, shape)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(option.previewRes),
                contentDescription = stringResource(option.labelRes),
                modifier = Modifier.size(if (homeStyle) 80.dp else 72.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(option.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(option.detailRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor,
                )
            }
        }
    }
}
