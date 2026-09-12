package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

@Composable
internal fun MazeLevelSelect(
    game: MazeGame,
    campaignMetas: List<MazeLevelMeta>,
    onPick: (Int) -> Unit,
    onEndless: () -> Unit,
    onExit: () -> Unit,
    onCharacter: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF5BA8E8), Color(0xFFC8E8F8))))
                .statusBarsPadding()
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close_game))
            }
            Text(
                text = stringResource(R.string.bear_maze_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            GameCharacterButton(
                onClick = onCharacter,
                tint = Color(0xFF2A4A6A),
            )
        }
        Text(
            text = stringResource(R.string.bear_maze_select_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF2A4A6A),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        // 无尽随机入口
        Button(
            onClick = onEndless,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.bear_maze_endless),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.bear_maze_endless_best, game.endlessBest),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.bear_maze_campaign_label, MAZE_CAMPAIGN_SIZE),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF2A4A6A),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            items(campaignMetas.size) { i ->
                val meta = campaignMetas[i]
                val open = i < game.unlocked
                val style = mazeThemeStyle(meta.theme)
                val handcrafted = i < MAZE_HANDCRAFTED_COUNT
                Card(
                    onClick = { if (open) onPick(i) },
                    enabled = open,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = if (open) style.floor else Color(0xFFB0BEC5),
                        ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "${meta.id}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.wall,
                        )
                        Text(
                            text = meta.titleZh,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = style.wall.copy(alpha = 0.85f),
                        )
                        Text(
                            text =
                                when {
                                    !open -> "🔒"
                                    else -> "★".repeat(game.stars[i]) + "☆".repeat(3 - game.stars[i])
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = style.accent,
                        )
                        Text(
                            text =
                                if (handcrafted) {
                                    stringResource(R.string.bear_maze_tag_hand)
                                } else {
                                    stringResource(R.string.bear_maze_tag_gen)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = style.wall.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}
