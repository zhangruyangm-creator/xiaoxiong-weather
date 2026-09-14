package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty

@Composable
internal fun CatchHud(
    game: BearCatchGame,
    onExit: () -> Unit,
    onPause: (() -> Unit)?,
    onLeaderboard: () -> Unit,
    onCharacter: () -> Unit,
) {
    // 派生整数秒/布尔翻转：doubleT/shieldCooldownT 每帧变化，但只在显示值变化时重组 HUD，
    // 避免整个顶栏跟着帧循环 60fps 重组（数值口径与原先 toInt()+1 一致）。
    val doubleSeconds by remember { derivedStateOf { if (game.doubleT > 0f) game.doubleT.toInt() + 1 else 0 } }
    val shieldSeconds by remember { derivedStateOf { if (game.shieldCooldownT > 0f) game.shieldCooldownT.toInt() + 1 else 0 } }
    val blink by remember { derivedStateOf { game.invincibleT > 0f && ((game.invincibleT * 10f) % 1f < 0.5f) } }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close_game),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 双倍得分倒计时
                if (doubleSeconds > 0) {
                    Text(
                        text = stringResource(R.string.bear_catch_double_time, doubleSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                    )
                }
                // 连击（与 comboScoreMultiplier 的 ×5 封顶保持一致）
                if (game.combo >= 2) {
                    Text(
                        text = stringResource(R.string.bear_catch_combo, minOf(game.combo, 5)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF176),
                    )
                }
                Text(
                    text = stringResource(R.string.bear_catch_score_value, game.score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.then(if (blink) Modifier else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(BearCatchGame.MAX_HEARTS) { i ->
                        Icon(
                            imageVector = if (i < game.hearts) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription =
                                if (i < game.hearts) {
                                    stringResource(R.string.cd_catch_heart_full, i + 1, BearCatchGame.MAX_HEARTS)
                                } else {
                                    stringResource(R.string.cd_catch_heart_empty, i + 1, BearCatchGame.MAX_HEARTS)
                                },
                            tint =
                                if (i <
                                    game.hearts
                                ) {
                                    Color(0xFFFF6B6B).copy(alpha = if (blink) 0.35f else 1f)
                                } else {
                                    Color.White.copy(alpha = 0.45f)
                                },
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    // 一次性护盾：持有时亮色 + 半透明边框，冷却中显示倒计时并灰显
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription =
                            when {
                                game.hasShield -> stringResource(R.string.cd_catch_shield_ready)
                                shieldSeconds > 0 ->
                                    stringResource(
                                        R.string.cd_catch_shield_cooldown,
                                        shieldSeconds,
                                    )
                                else -> stringResource(R.string.cd_catch_shield_none)
                            },
                        tint =
                            when {
                                game.hasShield -> Color(0xFF80DEEA)
                                shieldSeconds > 0 -> Color.White.copy(alpha = 0.30f)
                                else -> Color.White.copy(alpha = 0.20f)
                            },
                        modifier = Modifier.size(14.dp),
                    )
                    if (!game.hasShield && shieldSeconds > 0) {
                        Text(
                            text =
                                stringResource(
                                    R.string.bear_catch_shield_cooldown,
                                    shieldSeconds,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                    // 风暴爆发波次提示
                    if (game.inBurstWave) {
                        val burstDesc = stringResource(R.string.cd_catch_burst)
                        Text(
                            text = stringResource(R.string.bear_catch_burst_icon),
                            modifier =
                                Modifier.semantics {
                                    contentDescription = burstDesc
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFFEB3B),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bear_catch_best_suffix, game.best),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (onPause != null) GamePauseButton(onClick = onPause)
        GameCharacterButton(onClick = onCharacter)
        LeaderboardButton(onClick = onLeaderboard)
    }
}

/** 难度档位选择胶囊：当前档高亮，点击后立即写入 prefs。 */
@Composable
internal fun CatchDifficultyChip(
    difficulty: CatchDifficulty,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(difficultyLabelRes(difficulty))) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@StringRes
private fun difficultyLabelRes(difficulty: CatchDifficulty): Int =
    when (difficulty) {
        CatchDifficulty.CASUAL -> R.string.bear_catch_difficulty_casual
        CatchDifficulty.STANDARD -> R.string.bear_catch_difficulty_standard
        CatchDifficulty.CHALLENGE -> R.string.bear_catch_difficulty_challenge
    }
