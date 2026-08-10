package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.xiaoxiong.weather.R

/**
 * 各游戏共用的 SharedPreferences（排行榜 / 最高分 / 存档进度）。
 * 统一为「bear_game」文件名，避免各游戏重复写获取逻辑。
 */
@Composable
fun rememberGamePrefs(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) {
        context.getSharedPreferences("bear_game", Context.MODE_PRIVATE)
    }
}

/** 跑酷 / 摘星 / 迷宫共用的小熊精灵图（[ImageBitmap.imageResource] 内部已按资源缓存）。 */
@Composable
fun rememberBearSprites(): BearSprites =
    BearSprites(
        runA = ImageBitmap.imageResource(R.drawable.bear_run_a),
        runPushA = ImageBitmap.imageResource(R.drawable.bear_run_push_a),
        runPass = ImageBitmap.imageResource(R.drawable.bear_run_pass),
        runB = ImageBitmap.imageResource(R.drawable.bear_run_b),
        runPushB = ImageBitmap.imageResource(R.drawable.bear_run_push_b),
        jump = ImageBitmap.imageResource(R.drawable.bear_jump),
        fall = ImageBitmap.imageResource(R.drawable.bear_fall),
        land = ImageBitmap.imageResource(R.drawable.bear_land),
    )

/** 排行榜 UI 状态：弹窗开关、本次排名、条目列表（跳跳 / 摘星共用）。 */
class GameLeaderboardState(
    val store: GameLeaderboardStore,
) {
    var show by mutableStateOf(false)
    var lastRank by mutableIntStateOf(0)
    var entries by mutableStateOf(store.load())
}

/** 记住排行榜状态：数据从 [GameLeaderboardStore] 读取，弹窗 / 排名随 UI 更新。 */
@Composable
fun rememberLeaderboardState(
    prefs: SharedPreferences,
    tableKey: String,
    bestKey: String,
): GameLeaderboardState {
    val store =
        remember(prefs, tableKey, bestKey) {
            GameLeaderboardStore(prefs, tableKey, bestKey = bestKey)
        }
    return remember(store) { GameLeaderboardState(store) }
}
