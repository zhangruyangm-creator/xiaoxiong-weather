package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 排行榜排序：净胜降序 → 失分升序 → 时间新者靠前。
 * 同净胜失局优先与负净胜靠后由 [GameLeaderboardStoreTest] 的提交用例覆盖，此处只保留最小集合。
 */
class LeaderboardOrderTest {
    private fun entry(
        score: Int,
        tieBreak: Int = 0,
        time: Long = 0L,
    ) = LeaderboardEntry(score = score, timestamp = time, tieBreak = tieBreak)

    @Test
    fun netWinBeatsHigherTotalFrames() {
        // 3:0（净胜 +3）应排在 7:6（净胜 +1）之前
        val ranked =
            listOf(entry(1, tieBreak = 6), entry(3, tieBreak = 0))
                .sortedWith(leaderboardEntryOrder)
        assertEquals(listOf(3, 1), ranked.map { it.score })
    }

    @Test
    fun sameScoreAndTie_newerFirst() {
        val ranked =
            listOf(entry(2, 1, time = 100L), entry(2, 1, time = 200L))
                .sortedWith(leaderboardEntryOrder)
        assertEquals(listOf(200L, 100L), ranked.map { it.timestamp })
    }
}
