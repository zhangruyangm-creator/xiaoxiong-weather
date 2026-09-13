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
import androidx.core.content.edit
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
    /**
     * 主排序键，越大越靠前。对局类游戏用「净胜」（胜局-负局），而非各自总得分之和。
     */
    val score: Int,
    val timestamp: Long,
    val city: String? = null,
    val weather: String? = null,
    /** 自定义展示文本（如冰球的比分 "3 : 1"）；为空则按 [score] 显示分数。 */
    val label: String? = null,
    /**
     * 同分次级排序键，越小越靠前（对局类填失局数：同为净胜 +2 时 3:1 排在 4:2 前面）。
     */
    val tieBreak: Int = 0,
)

/** 排行榜统一排序：净胜分降序 → 失分升序 → 时间新者靠前。 */
internal val leaderboardEntryOrder: Comparator<LeaderboardEntry> =
    compareByDescending<LeaderboardEntry> { it.score }
        .thenBy { it.tieBreak }
        .thenByDescending { it.timestamp }

/** 旧对局记录展示用比分格式（如 "3 : 1"）。 */
private val scoreLabelPattern = Regex("""^\s*(\d+)\s*:\s*(\d+)\s*$""")

/**
 * 兼容旧 JSON：`tieBreak` 字段缺失时默认 0。对局类旧记录可从比分 label（"3 : 1"）还原失局数，
 * 仅当 label 与 score 自洽（净胜 = 比分差）时才还原，避免误改其它游戏的普通分数文本。
 */
private fun deriveTieBreak(entry: LeaderboardEntry): LeaderboardEntry {
    if (entry.tieBreak != 0) return entry
    val label = entry.label ?: return entry
    val match = scoreLabelPattern.matchEntire(label) ?: return entry
    val player = match.groupValues[1].toIntOrNull() ?: return entry
    val rival = match.groupValues[2].toIntOrNull() ?: return entry
    if (rival <= 0 || player - rival != entry.score) return entry
    return entry.copy(tieBreak = rival)
}

private const val MAX_ENTRIES = 20

private val leaderboardJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * 游戏排行榜存储：SharedPreferences + JSON。
 * 每个游戏一个 key。
 *
 * 保留策略由 [retainRecent] 决定：
 *  - false：只保留分数最高的前 [MAX_ENTRIES] 条（跳跳 / 摘星 / 接雨雪等计分游戏）；
 *  - true：保留最近 [MAX_ENTRIES] 条，展示时仍按分数排序（冰球等对局：大胜在前、惨败在后，
 *    惨败的局不会被挤出榜外）。
 */
class GameLeaderboardStore(
    private val prefs: SharedPreferences,
    private val key: String,
    private val bestKey: String? = null,
    private val retainRecent: Boolean = false,
) {
    private var cache: List<LeaderboardEntry>? = null

    fun load(): List<LeaderboardEntry> {
        cache?.let { return it }
        val raw = prefs.getString(key, null) ?: return emptyList()
        cache =
            runCatching {
                leaderboardJson.decodeFromString<List<LeaderboardEntry>>(raw)
            }.getOrDefault(emptyList())
                .map(::deriveTieBreak)
                .sortedWith(leaderboardEntryOrder)
        return cache!!
    }

    /**
     * 提交一局成绩；返回当前排名（1 起），未进前 [MAX_ENTRIES] 返回 null。
     *
     * 传入的 [entry] 先做兼容归一化（缺 `tieBreak` 的旧式记录从比分 label 还原），
     * 保证当次排名与下次 [load] 完全一致。只有成绩成功进榜（返回非空排名）才清理
     * [legacyKeys] 指定的旧榜 key，避免用户一进游戏旧记录就永久消失。
     */
    fun submit(
        entry: LeaderboardEntry,
        vararg legacyKeys: String,
    ): Int? {
        val normalized = deriveTieBreak(entry)
        val kept =
            if (retainRecent) {
                (load() + normalized).sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
            } else {
                (load() + normalized).sortedWith(leaderboardEntryOrder).take(MAX_ENTRIES)
            }
        val display = kept.sortedWith(leaderboardEntryOrder)
        prefs.edit { putString(key, leaderboardJson.encodeToString(display)) }
        cache = display
        val idx = display.indexOfFirst { it == normalized }
        val rank = if (idx >= 0) idx + 1 else null
        if (rank != null && legacyKeys.isNotEmpty()) purgeLegacyKeys(*legacyKeys)
        return rank
    }

    /**
     * 清理被新 key 取代的旧版本数据（如 v1 排行榜 / 旧最高分），避免孤儿数据常驻。
     * 只删除显式传入的 key，不触碰其它统计与设置。
     */
    fun purgeLegacyKeys(vararg keys: String) {
        val stale = keys.filter { prefs.contains(it) }
        if (stale.isEmpty()) return
        prefs.edit { stale.forEach { remove(it) } }
    }

    /**
     * 清空全部纪录；若配置了 [bestKey]，最高分一并清零。
     * [legacyKeys] 为被当前榜取代的旧榜 key，用户主动清榜时一并清理。
     */
    fun clear(vararg legacyKeys: String) {
        prefs.edit {
            remove(key)
            if (bestKey != null) remove(bestKey)
            legacyKeys.forEach { remove(it) }
        }
        cache = emptyList()
    }
}

/**
 * 结算界面展示用的名次：仅净胜为正才算「上榜」；负净胜 / 零分返回 0（不展示名次）。
 * 只影响展示，榜单存储仍按 [GameLeaderboardStore.retainRecent] 保留最近记录。
 */
fun leaderboardDisplayRank(
    score: Int,
    rank: Int?,
): Int = if (score > 0) rank ?: 0 else 0

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
        // 分数（或自定义比分文本）+ 日期：内容自适应宽度，不被右侧挤压
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = entry.label ?: stringResource(R.string.bear_leaderboard_score_label, entry.score),
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
fun leaderboardWeatherOf(
    context: Context,
    sceneCode: Int?,
): String = WeatherCodes.description(context, sceneCode)
