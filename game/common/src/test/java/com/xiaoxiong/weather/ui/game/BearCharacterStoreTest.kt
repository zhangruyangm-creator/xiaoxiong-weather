package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class BearCharacterStoreTest {
    @Test
    fun load_defaultsToPeach() {
        assertEquals(BearCharacter.PEACH, BearCharacterStore.load(FakePrefs()))
    }

    @Test
    fun saveAndLoad_roundTripsSun() {
        val prefs = FakePrefs()
        BearCharacterStore.save(prefs, BearCharacter.SUN)
        assertEquals(BearCharacter.SUN, BearCharacterStore.load(prefs))
    }

    @Test
    fun load_invalidValue_fallsBackToPeach() {
        val prefs = FakePrefs(initial = mapOf("bear_character" to "NOT_A_BEAR"))
        assertEquals(BearCharacter.PEACH, BearCharacterStore.load(prefs))
    }

    private class FakePrefs(
        initial: Map<String, String> = emptyMap(),
    ) : SharedPreferences {
        private val values = initial.toMutableMap()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = if (key == null) defValue else values[key] ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = defValue

        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()

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
            ): SharedPreferences.Editor = this

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor = this

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor = this

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor = this

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor = this

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
