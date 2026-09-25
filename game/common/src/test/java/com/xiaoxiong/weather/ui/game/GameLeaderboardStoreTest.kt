package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import com.xiaoxiong.weather.game.intOr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [GameLeaderboardStore] 的 JVM 单测：旧 JSON 兼容、排序提交、清榜与旧 key 清理。 */
class GameLeaderboardStoreTest {
    private val key = "leaderboard_test_v2"
    private val bestKey = "best_test_v2"

    @Test
    fun load_legacyJsonWithoutTieBreak_decodesWithDefault() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, """[{"score":5,"timestamp":1000}]""").apply()
        val entries = GameLeaderboardStore(prefs, key).load()
        assertEquals(1, entries.size)
        assertEquals(5, entries[0].score)
        assertEquals("旧 JSON 缺 tieBreak 应按默认 0 解码", 0, entries[0].tieBreak)
    }

    @Test
    fun load_legacyMatchLabel_derivesLostFramesAsTieBreak() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putString(
                key,
                """[{"score":2,"timestamp":1000,"label":"4 : 2"},{"score":5,"timestamp":2000}]""",
            ).apply()
        val entries = GameLeaderboardStore(prefs, key).load()
        assertEquals("比分与净胜自洽时还原失局数", 2, entries.first { it.score == 2 }.tieBreak)
        assertEquals("无比分的普通分数不还原", 0, entries.first { it.score == 5 }.tieBreak)
    }

    @Test
    fun load_legacyLabelInconsistentWithScore_keepsDefault() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, """[{"score":3,"timestamp":1000,"label":"4 : 2"}]""").apply()
        val entries = GameLeaderboardStore(prefs, key).load()
        assertEquals("比分差与 score 不一致时不还原", 0, entries[0].tieBreak)
    }

    @Test
    fun submit_sameNetFewerLossesRanksFirst_andReturnsRank() {
        val store = GameLeaderboardStore(FakePrefs(), key)
        val loose = LeaderboardEntry(score = 2, timestamp = 100L, label = "4 : 2", tieBreak = 2)
        assertEquals(1, store.submit(loose))
        val tight = LeaderboardEntry(score = 2, timestamp = 200L, label = "3 : 1", tieBreak = 1)
        assertEquals("同净胜失局少者应排第 1", 1, store.submit(tight))
        assertEquals(listOf(1, 2), store.load().map { it.tieBreak })
    }

    @Test
    fun submit_negativeNetIsKeptAndRankedBelowPositive() {
        val store = GameLeaderboardStore(FakePrefs(), key)
        store.submit(LeaderboardEntry(score = 1, timestamp = 100L, label = "3 : 2", tieBreak = 2))
        store.submit(LeaderboardEntry(score = -2, timestamp = 200L, label = "1 : 3", tieBreak = 3))
        assertEquals(listOf(1, -2), store.load().map { it.score })
    }

    @Test
    fun retainRecent_keepsTwentyMostRecent_butDisplaysByNet() {
        val store = GameLeaderboardStore(FakePrefs(), key, retainRecent = true)
        for (i in 0 until 25) {
            store.submit(
                LeaderboardEntry(
                    score = (i % 7) - 3,
                    timestamp = i.toLong(),
                    label = "x : y",
                ),
            )
        }
        val entries = store.load()
        assertEquals("对局榜只保留最近 20 条", 20, entries.size)
        assertTrue("最老的 5 条应被挤出榜外", entries.none { it.timestamp < 5L })
        assertEquals("展示仍按净胜降序 → 失局升序 → 新者在前", entries.sortedWith(leaderboardEntryOrder), entries)
    }

    @Test
    fun dailyShotScores_sortByScoreAndAreNotMistakenForTieBreak() {
        val prefs = FakePrefs()
        val store = GameLeaderboardStore(prefs, "leaderboard_pool_daily_1_v1")
        store.submit(LeaderboardEntry(score = 60, timestamp = 1L, label = "20 杆"))
        store.submit(LeaderboardEntry(score = 100, timestamp = 2L, label = "10 杆"))
        assertEquals(listOf(100, 60), store.load().map { it.score })
        assertEquals("杆数文本不应被当比分反推 tieBreak", 0, store.load().first { it.score == 60 }.tieBreak)
    }

    @Test
    fun displayRank_negativeFifteenOrFive_isHidden() {
        assertEquals(0, leaderboardDisplayRank(score = -5, rank = 1))
        assertEquals(0, leaderboardDisplayRank(score = -15, rank = 3))
        assertEquals(0, leaderboardDisplayRank(score = 0, rank = 2))
    }

    @Test
    fun purgeLegacyKeys_removesOnlyListedKeys() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putString("leaderboard_old_v1", "[]")
            .putInt("best_old", 9)
            .putInt("keep_me", 7)
            .apply()
        val store = GameLeaderboardStore(prefs, key)
        store.purgeLegacyKeys("leaderboard_old_v1", "best_old")
        assertFalse(prefs.contains("leaderboard_old_v1"))
        assertFalse(prefs.contains("best_old"))
        assertEquals(7, prefs.intOr("keep_me", 0))
    }

    @Test
    fun submit_legacyEntryWithoutTieBreak_rankMatchesAfterReload() {
        val prefs = FakePrefs()
        val store = GameLeaderboardStore(prefs, key)
        store.submit(LeaderboardEntry(score = 2, timestamp = 100L, label = "3 : 1", tieBreak = 1))
        // 旧式 entry 只带 score + label（tieBreak 缺省 0），提交时应先归一化再排序
        val rank = store.submit(LeaderboardEntry(score = 2, timestamp = 200L, label = "4 : 2"))
        assertEquals("当次排名应按归一化后的失局数计算", 2, rank)
        val reloaded = GameLeaderboardStore(prefs, key).load()
        assertEquals("重载后应保持同一排名", 2, reloaded.indexOfFirst { it.timestamp == 200L } + 1)
    }

    @Test
    fun submit_legacyKeysPurgedOnlyAfterEntryEntersTable() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putString("leaderboard_old_v1", "[]")
            .putInt("best_old", 9)
            .apply()
        val store = GameLeaderboardStore(prefs, key)
        repeat(20) { i -> store.submit(LeaderboardEntry(score = 100 - i, timestamp = i.toLong())) }
        // 低分进不了前 20：提交后旧 key 仍保留，用户可回滚找回
        assertNull(store.submit(LeaderboardEntry(score = 1, timestamp = 99L), "leaderboard_old_v1", "best_old"))
        assertTrue("未进榜时旧 key 不应被清", prefs.contains("leaderboard_old_v1"))
        assertTrue(prefs.contains("best_old"))
        // 成功进榜后才清理
        assertEquals(1, store.submit(LeaderboardEntry(score = 999, timestamp = 100L), "leaderboard_old_v1", "best_old"))
        assertFalse("首次成功进榜后应清理旧 key", prefs.contains("leaderboard_old_v1"))
        assertFalse(prefs.contains("best_old"))
    }

    @Test
    fun displayRank_onlyPositiveScoreIsShown() {
        assertEquals(2, leaderboardDisplayRank(score = 3, rank = 2))
        assertEquals("零净胜不展示名次", 0, leaderboardDisplayRank(score = 0, rank = 1))
        assertEquals("负净胜不展示名次", 0, leaderboardDisplayRank(score = -5, rank = 1))
        assertEquals("未进榜不展示名次", 0, leaderboardDisplayRank(score = 3, rank = null))
    }

    @Test
    fun clear_removesTableBestAndLegacyKeys_only() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putString(key, "[]")
            .putInt(bestKey, 3)
            .putString("leaderboard_old_v1", "[]")
            .putInt("keep_me", 7)
            .apply()
        val store = GameLeaderboardStore(prefs, key, bestKey = bestKey)
        store.clear("leaderboard_old_v1")
        assertFalse(prefs.contains(key))
        assertFalse(prefs.contains(bestKey))
        assertFalse("清榜应一并清掉显式传入的旧 key", prefs.contains("leaderboard_old_v1"))
        assertEquals(7, prefs.intOr("keep_me", 0))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun submit_rankLookupSurvivesUnrelatedEntries() {
        val store = GameLeaderboardStore(FakePrefs(), key)
        store.submit(LeaderboardEntry(score = 9, timestamp = 1L))
        val entry = LeaderboardEntry(score = 4, timestamp = 2L)
        assertEquals(2, store.submit(entry))
        assertEquals(2, store.load().indexOf(entry) + 1)
    }

    /** 覆盖 GameLeaderboardStore 用到的 SharedPreferences 子集（String / Int / contains / remove）。 */
    private class FakePrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (values[key] as? MutableSet<String>)?.toMutableSet() ?: defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = values?.toMutableSet()
                return this
            }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                pending.clear()
                values.clear()
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                pending.forEach { (key, value) ->
                    if (value == null) {
                        values.remove(key)
                    } else {
                        values[key] = value
                    }
                }
                pending.clear()
            }
        }
    }
}
