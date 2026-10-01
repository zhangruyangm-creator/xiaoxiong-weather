package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPrefsTest {
    @Test
    fun loadRunBest_migratesLegacyBestToStandard() {
        val prefs = FakePrefs()
        prefs.edit().putInt(PREF_RUN_BEST_LEGACY, 777).apply()
        assertEquals(777, loadRunBest(prefs, RunDifficulty.STANDARD))
        assertTrue(prefs.contains(runBestKey(RunDifficulty.STANDARD)))
        assertEquals(0, loadRunBest(prefs, RunDifficulty.CASUAL))
    }

    @Test
    fun persistRunBestIfHigher_updatesAndMirrorsLegacyForStandard() {
        val prefs = FakePrefs()
        assertEquals(100, persistRunBestIfHigher(prefs, RunDifficulty.STANDARD, 100))
        assertEquals(100, prefs.getInt(runBestKey(RunDifficulty.STANDARD), 0))
        assertEquals(100, prefs.getInt(PREF_RUN_BEST_LEGACY, 0))
        assertEquals(100, persistRunBestIfHigher(prefs, RunDifficulty.STANDARD, 50))
        assertEquals(200, persistRunBestIfHigher(prefs, RunDifficulty.CHALLENGE, 200))
        assertFalse(prefs.contains(PREF_RUN_BEST_LEGACY) && prefs.getInt(PREF_RUN_BEST_LEGACY, 0) == 200)
        assertEquals(100, prefs.getInt(PREF_RUN_BEST_LEGACY, 0))
    }

    @Test
    fun ensureRunLeaderboardMigrated_copiesLegacyJsonForStandard() {
        val prefs = FakePrefs()
        prefs.edit().putString(PREF_RUN_LEADERBOARD_LEGACY, "[{\"score\":1,\"timestamp\":2}]").apply()
        val key = ensureRunLeaderboardMigrated(prefs, RunDifficulty.STANDARD)
        assertEquals(runLeaderboardKey(RunDifficulty.STANDARD), key)
        assertEquals("[{\"score\":1,\"timestamp\":2}]", prefs.getString(key, null))
    }

    @Test
    fun loadAndPersistDifficulty() {
        val prefs = FakePrefs()
        assertEquals(RunDifficulty.STANDARD, loadRunDifficulty(prefs))
        persistRunDifficulty(prefs, RunDifficulty.CHALLENGE)
        assertEquals(RunDifficulty.CHALLENGE, loadRunDifficulty(prefs))
    }

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
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }
        }
    }
}
