package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaoxiong.weather.game.stringOr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 恢复会话时蜂蜜计数与棋盘的一致性。
 *
 * 背景：计数来自存档、棋盘是按 gridRows 重建的，两者可能不一致（手改档、写入截断、
 * 旧版本迁移）。`honeyLeft=0` 但棋盘还有 H 能白嫖过关；棋盘无 H 而 `honeyLeft>0`
 * 则永远收不齐、出口永不开启。两种都以棋盘为准重建。
 *
 * 走真实的 saveSession → 篡改 JSON → restoreSession 路径，不依赖私有 data class。
 */
class MazeHoneyReconcileTest {
    private val sessionKey = "maze_session_v1"
    private val prefs = FakePrefs()

    private fun savedSessionJson(): String {
        val game = MazeGame()
        game.startLevel(0)
        game.saveSession(prefs)
        return requireNotNull(prefs.stringOr(sessionKey)) { "saveSession 未写入" }
    }

    @Test
    fun honeyLeftIsRecomputedFromGrid_notTrustedFromSave() {
        val tampered =
            savedSessionJson()
                .replace("\"honeyLeft\":${gridHoneyCount()}", "\"honeyLeft\":0")
                .replace("\"honeyTotal\":${gridHoneyCount()}", "\"honeyTotal\":0")
        prefs.edit { putString(sessionKey, tampered) }
        val game = MazeGame()
        game.restoreSession(prefs)
        assertEquals("棋盘上还有蜂蜜，不能凭存档的 0 直接过关", gridHoneyCount(), game.honeyLeft)
    }

    @Test
    fun honeyLeftIsRebuiltEvenWhenSaveClaimsMoreThanGridHas() {
        val tampered = savedSessionJson().replace("\"honeyLeft\":${gridHoneyCount()}", "\"honeyLeft\":99")
        prefs.edit { putString(sessionKey, tampered) }
        val game = MazeGame()
        game.restoreSession(prefs)
        assertEquals("以棋盘为准，不让出口永久关闭", gridHoneyCount(), game.honeyLeft)
    }

    @Test
    fun negativeCountersAreClamped() {
        val tampered =
            savedSessionJson()
                .replace("\"keys\":0", "\"keys\":-3")
                .replace("\"moves\":0", "\"moves\":-9")
        prefs.edit { putString(sessionKey, tampered) }
        val game = MazeGame()
        game.restoreSession(prefs)
        assertTrue("负值不得进入运行状态", game.keys >= 0)
        assertTrue(game.moves >= 0)
        assertTrue(game.honeyLeft >= 0)
    }

    /** 第 0 关棋盘上的蜂蜜格数；测试里作为期望值的唯一来源，避免硬编码漂移。 */
    private fun gridHoneyCount(): Int {
        val game = MazeGame()
        game.startLevel(0)
        return game.honeyTotal
    }

    private class FakePrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = map[key] as? String ?: defValue

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = map[key] as? Int ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = map[key] as? Long ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = map[key] as? Float ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = map[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor =
            object : SharedPreferences.Editor {
                override fun putString(
                    key: String,
                    value: String?,
                ): SharedPreferences.Editor = apply { map[key] = value }

                override fun putStringSet(
                    key: String,
                    values: MutableSet<String>?,
                ): SharedPreferences.Editor = apply { map[key] = values }

                override fun putInt(
                    key: String,
                    value: Int,
                ): SharedPreferences.Editor = apply { map[key] = value }

                override fun putLong(
                    key: String,
                    value: Long,
                ): SharedPreferences.Editor = apply { map[key] = value }

                override fun putFloat(
                    key: String,
                    value: Float,
                ): SharedPreferences.Editor = apply { map[key] = value }

                override fun putBoolean(
                    key: String,
                    value: Boolean,
                ): SharedPreferences.Editor = apply { map[key] = value }

                override fun remove(key: String): SharedPreferences.Editor = apply { map.remove(key) }

                override fun clear(): SharedPreferences.Editor = apply { map.clear() }

                override fun commit(): Boolean = true

                override fun apply() = Unit
            }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
