package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

@Composable
internal fun GameHud(
    game: BearGame,
    onExit: () -> Unit,
    onPause: (() -> Unit)?,
    onLeaderboard: () -> Unit,
    onCharacter: () -> Unit,
) {
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
                if (game.combo >= 2) {
                    Text(
                        text = stringResource(R.string.bear_game_combo, game.combo),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF176),
                    )
                }
                if (game.grazeCount > 0) {
                    Text(
                        text = stringResource(R.string.bear_run_graze, game.grazeCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF80D8FF),
                    )
                }
                Text(
                    text = "${game.hudScore}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.bear_game_best_label) + " ${game.best}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        if (onPause != null) GamePauseButton(onClick = onPause)
        GameCharacterButton(onClick = onCharacter)
        LeaderboardButton(onClick = onLeaderboard)
    }
}

/** READY：难度三档 + 围巾色选择。 */
@Composable
internal fun ColumnScope.RunReadyExtras(
    difficulty: RunDifficulty,
    scarfTint: RunScarfTint,
    best: Int,
    onPickDifficulty: (RunDifficulty) -> Unit,
    onPickScarf: (RunScarfTint) -> Unit,
) {
    Text(
        text = stringResource(R.string.bear_run_diff_section),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RunDifficulty.entries.forEach { d ->
            FilterChip(
                selected = difficulty == d,
                onClick = { onPickDifficulty(d) },
                label = { Text(runDifficultyLabel(d)) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = runDifficultyHint(difficulty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.bear_run_scarf_tint),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RunScarfTint.entries.forEach { tint ->
            val unlocked = runScarfTintUnlocked(tint, best)
            FilterChip(
                selected = scarfTint == tint,
                onClick = { if (unlocked) onPickScarf(tint) },
                enabled = unlocked,
                label = { Text(runScarfTintLabel(tint), maxLines = 1) },
                modifier = Modifier.weight(1f),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = runScarfAccent(tint).copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

/** 里程碑 / 冰墙解锁横幅。 */
@Composable
internal fun RunBanner(
    text: String,
    warn: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (warn) Color(0xE62A0A10) else Color(0xE8152433),
            shadowElevation = 12.dp,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                color = if (warn) Color(0xFFFF8A80) else Color(0xFF7FE3F2),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun runDifficultyLabel(difficulty: RunDifficulty): String =
    stringResource(
        when (difficulty) {
            RunDifficulty.CASUAL -> R.string.bear_run_diff_casual
            RunDifficulty.STANDARD -> R.string.bear_run_diff_standard
            RunDifficulty.CHALLENGE -> R.string.bear_run_diff_challenge
        },
    )

@Composable
internal fun runDifficultyHint(difficulty: RunDifficulty): String =
    stringResource(
        when (difficulty) {
            RunDifficulty.CASUAL -> R.string.bear_run_diff_hint_casual
            RunDifficulty.STANDARD -> R.string.bear_run_diff_hint_standard
            RunDifficulty.CHALLENGE -> R.string.bear_run_diff_hint_challenge
        },
    )

@Composable
internal fun runScarfTintLabel(tint: RunScarfTint): String =
    stringResource(
        when (tint) {
            RunScarfTint.CLASSIC -> R.string.bear_run_tint_classic
            RunScarfTint.GOLD -> R.string.bear_run_tint_gold
            RunScarfTint.MIDNIGHT -> R.string.bear_run_tint_midnight
            RunScarfTint.ROSE -> R.string.bear_run_tint_rose
            RunScarfTint.AURORA -> R.string.bear_run_tint_aurora
        },
    )

@Suppress("unused")
@StringRes
internal fun runDifficultyLabelRes(difficulty: RunDifficulty): Int =
    when (difficulty) {
        RunDifficulty.CASUAL -> R.string.bear_run_diff_casual
        RunDifficulty.STANDARD -> R.string.bear_run_diff_standard
        RunDifficulty.CHALLENGE -> R.string.bear_run_diff_challenge
    }
