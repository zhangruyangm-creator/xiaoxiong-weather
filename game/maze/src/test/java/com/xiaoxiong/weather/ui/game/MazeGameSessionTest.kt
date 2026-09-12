package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MazeGame] 会话存档 / 进度读档的 JVM 单测：
 * 覆盖「保留无尽进度退出不再被 onDispose/ON_STOP 覆盖」与星数读档 clamp。
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
    fun saveSession_selectWithoutResumeEndless_clearsSession() {
        val prefs = FakePrefs()
        val game = MazeGame()
        game.startEndless(1)
        game.saveSession(prefs)
        assertTrue(prefs.contains(sessionKey))

        game.openSelect()
        game.resumeEndless = false
        game.saveSession(prefs)

        assertFalse("战役对局选关退出应清理会话", prefs.contains(sessionKey))
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
        assertTrue(restored.resumeEndless)
        assertEquals("恢复后应保留快照，供再次被杀时恢复", saved, prefs.getString(sessionKey, null))
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
