package com.xiaoxiong.weather.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 容错 prefs 读取：类型不匹配时回退默认值而不是抛 `ClassCastException`。
 *
 * 背景：把 `bear_game.xml` 里某个 `<int name="best">` 改成 `<string name="best">`
 * （手改档、跨版本改键类型、旧迁移写错），进游戏时 `remember { prefs.getInt(...) }`
 * 会在组合期直接崩掉整 app。
 */
class PrefsReadingTest {
    /** getInt 期待 Int，但键上存着 String——与被手改/错误迁移的 XML 一致。 */
    private class TypeMismatchPrefs : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf("best" to "ten")

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = "ten"

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = null

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = throw ClassCastException("key $key is String, expected Int")

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = throw ClassCastException("key $key is String, expected Long")

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = throw ClassCastException("key $key is String, expected Float")

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = throw ClassCastException("key $key is String, expected Boolean")

        override fun contains(key: String): Boolean = key == "best"

        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private val prefs = TypeMismatchPrefs()

    @Test
    fun `intOr falls back to default on type mismatch`() {
        assertEquals(7, prefs.intOr("best", 7))
    }

    @Test
    fun `longOr falls back to default on type mismatch`() {
        assertEquals(5L, prefs.longOr("best", 5L))
    }

    @Test
    fun `floatOr falls back to default on type mismatch`() {
        assertEquals(0.5f, prefs.floatOr("best", 0.5f), 0f)
    }

    @Test
    fun `booleanOr falls back to default on type mismatch`() {
        assertEquals(true, prefs.booleanOr("best", true))
    }

    @Test
    fun `stringOr still reads strings`() {
        assertEquals("ten", prefs.stringOr("best"))
    }
}
