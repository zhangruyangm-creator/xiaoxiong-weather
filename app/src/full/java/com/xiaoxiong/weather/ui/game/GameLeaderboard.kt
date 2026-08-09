package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 排行榜单条记录：分数 + 达成时间 + 地点 + 天气。 */
@Serializable
data class LeaderboardEntry(
    val score: Int,
    val timestamp: Long,
    val city: String? = null,
    val weather: String? = null,
)

private const val MAX_ENTRIES = 20

private val leaderboardJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * 游戏排行榜存储：SharedPreferences + JSON，最多保留前 [MAX_ENTRIES] 名。
 * 每个游戏一个 key。
 */
class GameLeaderboardStore(
    private val prefs: SharedPreferences,
    private val key: String,
    private val bestKey: String? = null,
) {
    private var cache: List<LeaderboardEntry>? = null

    fun load(): List<LeaderboardEntry> {
        cache?.let { return it }
        val raw = prefs.getString(key, null) ?: return emptyList()
        cache =
            runCatching {
                leaderboardJson.decodeFromString<List<LeaderboardEntry>>(raw)
            }.getOrDefault(emptyList()).sortedByDescending { it.score }
        return cache!!
    }

    /** 提交一局成绩；返回当前排名（1 起），未进前 [MAX_ENTRIES] 返回 null。 */
    fun submit(entry: LeaderboardEntry): Int? {
        val merged = (load() + entry).sortedByDescending { it.score }.take(MAX_ENTRIES)
        prefs.edit().putString(key, leaderboardJson.encodeToString(merged)).apply()
        cache = merged
        val idx = merged.indexOfFirst { it.timestamp == entry.timestamp && it.score == entry.score }
        return if (idx >= 0) idx + 1 else null
    }

    /** 清空全部纪录；若配置了 [bestKey]，最高分一并清零。 */
    fun clear() {
        prefs.edit().apply {
            remove(key)
            if (bestKey != null) remove(bestKey)
        }.apply()
        cache = emptyList()
    }
}

private val leaderboardTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatLeaderboardTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(leaderboardTimeFormat)

/**
 * 低调排行榜按钮：半透明白色奖杯，适合叠在任何背景上而不刺眼。
 */
@Composable
fun LeaderboardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = stringResource(R.string.cd_leaderboard),
            tint = Color.White.copy(alpha = 0.72f),
        )
    }
}

/** 排行榜弹窗：列出历史记录（分数 / 时间 / 地点 / 天气），可清空。 */
@Composable
fun GameLeaderboardDialog(
    title: String,
    entries: List<LeaderboardEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.heightIn(max = 480.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text(
                                text = stringResource(R.string.bear_leaderboard_clear),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.bear_leaderboard_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(entries) { i, e ->
                            LeaderboardRow(rank = i + 1, entry = e)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.bear_leaderboard_clear_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.bear_leaderboard_clear_msg))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.bear_leaderboard_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.share_card_cancel))
                }
            },
        )
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
) {
    // 前三名显示金银铜牌，其余显示名次数字
    val medalColor =
        when (rank) {
            1 -> Color(0xFFFFB300)
            2 -> Color(0xFF9E9E9E)
            3 -> Color(0xFFBF8A5F)
            else -> null
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (medalColor != null) {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = stringResource(R.string.bear_leaderboard_rank_label, rank),
                tint = medalColor,
                modifier = Modifier.size(26.dp).padding(end = 2.dp),
            )
        } else {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.width(28.dp),
            )
        }
        // 分数 + 日期：内容自适应宽度，不被右侧挤压
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.bear_leaderboard_score_label, entry.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatLeaderboardTime(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 地点 + 天气：占剩余宽度，地名过长时换行，不挤压日期列
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = entry.city ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
            )
            Text(
                text = entry.weather ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 由 sceneCode 生成当前天气文本（用于排行榜记录）。 */
fun leaderboardWeatherOf(context: Context, sceneCode: Int?): String =
    WeatherCodes.description(context, sceneCode)
