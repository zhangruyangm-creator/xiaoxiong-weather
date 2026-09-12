package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MazeGame] 会话存档 / 进度读档的 JVM 单测：
 * 覆盖三种模式的存档-恢复往返、选关阶段不清档、主动放弃清档与星数读档 clamp。
 */
class MazeGameSessionTest {
    private val sessionKey = "maze_session_v1"

    @Test
    fun saveSession_selectAfterKeepEndless_keepsSavedSession() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startEndless(1)
        game.saveSession(prefs)
        val saved = prefs.getString(sessionKey, null)
        assertNotNull(saved)

        // 模拟「保留进度并退出」：存档后回到选关（grid 仍在），随后 onDispose/ON_STOP 再落盘
        game.openSelect()
        game.saveSession(prefs)

        assertEquals("选关阶段不应覆盖已保存的无尽会话", saved, prefs.getString(sessionKey, null))
        assertTrue(saved!!.contains("\"endless\":true"))
    }

    @Test
    fun saveSession_selectKeepsCampaignAndDailySessions() {
        val prefs = FakePrefs()
        val game = MazeGame()

        game.startLevel(5)
        game.saveSession(prefs)
        val campaignSaved = prefs.getString(sessionKey, null)
        game.openSelect()
        game.saveSession(prefs)
        assertEquals("看一眼选关不应清掉战役存档", campaignSaved, prefs.getString(sessionKey, null))

        game.startDaily(20_000L)
        game.saveSession(prefs)
        val dailySaved = prefs.getString(sessionKey, null)
        game.openSelect()
        game.saveSession(prefs)
        assertEquals("看一眼选关不应清掉每日存档", dailySaved, prefs.getString(sessionKey, null))
    }

    @Test
    fun abandonSession_clearsSavedSession() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startLevel(2)
        game.saveSession(prefs)
        assertTrue(prefs.contains(sessionKey))

        game.abandonSession(prefs)
        game.openSelect()
        game.saveSession(prefs)

        assertFalse("主动放弃后选关兜底 saveSession 不应复活存档", prefs.contains(sessionKey))
    }

    @Test
    fun startNewRunAfterAbandon_clearsOldSession() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startLevel(5)
        game.saveSession(prefs)

        // 选关页点新关卡：确认放弃旧档 → 开始新局
        game.abandonSession(prefs)
        game.startLevel(3)

        assertFalse("开始新局应清掉旧档", prefs.contains(sessionKey))
    }

    @Test
    fun restoreSession_keepsSnapshotSoProgressSurvivesAnotherKill() {
        val prefs = FakePrefs()
        val source = MazeGame()
        source.startEndless(2)
        source.saveSession(prefs)
        val saved = prefs.getString(sessionKey, null)

        val restored = MazeGame()
        assertTrue(restored.restoreSession(prefs))
        assertEquals(MazePhase.PLAYING, restored.phase)
        assertTrue(restored.endless)
        assertEquals("恢复后应保留快照，供再次被杀时恢复", saved, prefs.getString(sessionKey, null))
    }

    @Test
    fun restoreSession_campaignRoundTripsLevelIndex() {
        val prefs = FakePrefs()
        val source = MazeGame()
        source.startLevel(7)
        source.moves = 3
        source.saveSession(prefs)

        val restored = MazeGame()
        assertTrue(restored.restoreSession(prefs))
        assertFalse(restored.endless)
        assertFalse(restored.daily)
        assertEquals(7, restored.levelIndex)
        assertEquals(3, restored.moves)
        assertEquals(MazePhase.PLAYING, restored.phase)
    }

    @Test
    fun restoreSession_endlessRoundTripsWave() {
        val prefs = FakePrefs()
        val source = MazeGame()
        source.startEndless(6)
        source.saveSession(prefs)

        val restored = MazeGame()
        assertTrue(restored.restoreSession(prefs))
        assertTrue(restored.endless)
        assertFalse(restored.daily)
        assertEquals(6, restored.endlessWave)
        assertTrue(
            "恢复的无尽棋盘应与原局一致",
            restored.level.map == source.level.map,
        )
    }

    @Test
    fun restoreSession_dailyRoundTripsDateInsteadOfCampaignLevel() {
        val prefs = FakePrefs()
        val source = MazeGame()
        source.startDaily(20_123L)
        source.saveSession(prefs)

        val restored = MazeGame()
        assertTrue(restored.restoreSession(prefs))
        assertTrue("每日档必须按每日模式恢复", restored.daily)
        assertFalse(restored.endless)
        assertEquals(20_123L, restored.dailyDate)
        assertEquals(-1, restored.levelIndex)
        assertEquals(MazePhase.PLAYING, restored.phase)
        assertTrue(
            "每日档恢复的棋盘应与当日布局一致",
            restored.level.map == source.level.map,
        )
    }

    @Test
    fun peekSavedSession_recognizesAllThreeTypes() {
        val prefs = FakePrefs()
        val game = MazeGame()
        assertNull("无会话应返回 null", game.peekSavedSession(prefs))

        game.startLevel(9)
        game.saveSession(prefs)
        val campaign = game.peekSavedSession(prefs)
        assertTrue("战役会话应识别为 Campaign", campaign is SavedSessionInfo.Campaign)
        assertEquals(9, (campaign as SavedSessionInfo.Campaign).levelIndex)

        game.startEndless(4)
        game.saveSession(prefs)
        val endless = game.peekSavedSession(prefs)
        assertTrue("无尽会话应识别为 Endless", endless is SavedSessionInfo.Endless)
        assertEquals(4, (endless as SavedSessionInfo.Endless).wave)

        game.startDaily(20_321L)
        game.saveSession(prefs)
        val daily = game.peekSavedSession(prefs)
        assertTrue("每日会话应识别为 Daily", daily is SavedSessionInfo.Daily)
        assertEquals(20_321L, (daily as SavedSessionInfo.Daily).dateEpochDay)
    }

    @Test
    fun saveSession_afterClear_dropsResumableSession() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startLevel(1)
        game.saveSession(prefs)
        assertTrue(prefs.contains(sessionKey))

        // 结算相位：不应再保留可续会话，避免选关页出现已结算的旧档
        game.phase = MazePhase.CLEAR
        game.saveSession(prefs)

        assertFalse(prefs.contains(sessionKey))
        assertNull(game.peekSavedSession(prefs))
    }

    @Test
    fun peekSavedSession_nullAfterAbandon() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startDaily(20_321L)
        game.saveSession(prefs)
        assertNotNull(game.peekSavedSession(prefs))

        game.abandonSession(prefs)
        assertNull(game.peekSavedSession(prefs))
    }

    @Test
    fun restoreSession_roundTripsHintsUsed() {
        val prefs = FakePrefs()
        val source = MazeGame()
        source.startEndless(1)
        source.hintsUsed = 3
        source.saveSession(prefs)

        val restored = MazeGame()
        assertTrue(restored.restoreSession(prefs))
        assertEquals(3, restored.hintsUsed)
    }

    @Test
    fun loadProgress_clampsStarsIntoZeroToThree() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putInt("maze_star_0", 99)
            .putInt("maze_star_1", -5)
            .putInt("maze_star_2", 2)
            .apply()

        val game = MazeGame()
        game.loadProgress(prefs)

        assertEquals(3, game.stars[0])
        assertEquals(0, game.stars[1])
        assertEquals(2, game.stars[2])
    }

    /** 覆盖 MazeGame 用到的 SharedPreferences 子集（String/Int/putString/putInt/remove）。 */
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
