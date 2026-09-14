package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 台呢主题纯逻辑回归：四档颜色（白天/夜晚、晴/雨/雪）两两可区分，主题基础色越界回退默认，
 * 织纹短线尺度以球半径为界（不能用 viewScale 放大，否则会把台面洗成灰），以及
 * `pool_cloth_theme` 的持久化往返 / 旧键回退 / 越界钳制。
 */
class PoolClothThemeTest {
    private val dayStyles =
        listOf(
            weatherSceneStyle(0, isDay = true, appDark = true),
            weatherSceneStyle(61, isDay = true, appDark = true),
            weatherSceneStyle(71, isDay = true, appDark = true),
        )
    private val nightStyles =
        listOf(
            weatherSceneStyle(0, isDay = false, appDark = true),
            weatherSceneStyle(61, isDay = false, appDark = true),
            weatherSceneStyle(71, isDay = false, appDark = true),
        )

    /** 四档主题在白天/夜晚以及晴/雨/雪下的两两 RGB 距离下限（实测最小约 62）。 */
    private val minThemeDistance = 48f

    private fun distance(
        a: Color,
        b: Color,
    ): Float {
        val dr = (a.red - b.red) * 255f
        val dg = (a.green - b.green) * 255f
        val db = (a.blue - b.blue) * 255f
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun assertThemesDistinct(
        isDay: Boolean,
        styles: List<WeatherSceneStyle>,
    ) {
        styles.forEach { style ->
            val colors = (0 until CLOTH_THEME_COUNT).map { feltBase(style, isDay, it) }
            for (i in 0 until CLOTH_THEME_COUNT) {
                for (j in i + 1 until CLOTH_THEME_COUNT) {
                    val d = distance(colors[i], colors[j])
                    assertTrue(
                        "isDay=$isDay scene=${style.scene} 主题 $i 与 $j 距离过小: $d",
                        d > minThemeDistance,
                    )
                }
            }
        }
    }

    @Test
    fun clothThemes_distinctInDayAndNight() {
        assertThemesDistinct(isDay = true, styles = dayStyles)
        assertThemesDistinct(isDay = false, styles = nightStyles)
    }

    @Test
    fun clothThemes_distinctUnderRainAndSnow() {
        // 冷色叠加后仍要保留主题差异（叠加是 16% 冷色，不会抹平四档）
        assertThemesDistinct(isDay = true, styles = listOf(dayStyles[1], dayStyles[2]))
        assertThemesDistinct(isDay = false, styles = listOf(nightStyles[1], nightStyles[2]))
    }

    @Test
    fun outOfRangeTheme_fallsBackToDefaultFelt() {
        val style = weatherSceneStyle(0, isDay = true, appDark = true)
        for (theme in listOf(-1, CLOTH_THEME_COUNT, 99)) {
            assertEquals(
                "越界主题 $theme 应回退默认湖蓝",
                feltBase(style, true, 0),
                feltBase(style, true, theme),
            )
        }
    }

    @Test
    fun clothWeave_staysShortRelativeToBall() {
        // 回归：织纹不能用 viewScale（约 850px/m）放大，否则长度/线宽会铺满整桌洗掉主题色
        val ballR = 24f
        for (i in 0..40) {
            val rnd = i / 40f
            val len = clothWeaveLength(ballR, rnd)
            val width = clothWeaveWidth(ballR)
            assertTrue("织纹长度应保持短线: $len", len in 0.5f..(ballR * 0.5f))
            assertTrue("织纹线宽应保持细线: $width", width in 0.1f..(ballR * 0.1f))
        }
    }

    @Test
    fun coldOverlay_blendsEachThemeByExactBoundedAmount() {
        val rain = weatherSceneStyle(61, isDay = true, appDark = true)
        val clear = weatherSceneStyle(0, isDay = true, appDark = true)
        for (theme in 0 until CLOTH_THEME_COUNT) {
            val base = clothThemeFeltColor(theme, true)
            assertEquals(
                "雨天台呢应为『主题色 16% 冷色』的确定叠加",
                lerp(base, feltColdTint, FELT_COLD_BLEND),
                feltBase(rain, true, theme),
            )
            assertEquals("晴/多云场景不得叠加冷色", base, feltBase(clear, true, theme))
        }
    }

    @Test
    fun clothWeave_worstCaseCoverage_staysSubtle() {
        // 以 1080p 竖屏（球半径约 23px）估算：即使所有短线取最长值，墨迹面积也 < 0.2% 台面，
        // 不可能把主题色「洗灰」；同时线宽保持可见量级，避免过疏到完全看不见。
        val ballR = 23f
        val tableW = 1.27f * 810f
        val tableH = 2.54f * 810f
        val worstLen = clothWeaveLength(ballR, rnd = 1f)
        val width = clothWeaveWidth(ballR)
        val coverage = 240f * worstLen * width / (tableW * tableH)
        assertTrue("织纹覆盖面积应保持微妙: $coverage", coverage < 0.002f)
        assertTrue("织纹线宽应有可见量级: $width", width >= 0.5f)
    }

    @Test
    fun prefs_missingKey_fallsBackToDefault() {
        assertEquals(0, loadClothTheme(FakePrefs()))
    }

    @Test
    fun prefs_roundTrip_keepsTheme() {
        val prefs = FakePrefs()
        for (theme in 0 until CLOTH_THEME_COUNT) {
            saveClothTheme(prefs, theme)
            assertEquals("主题 $theme 应持久化", theme, loadClothTheme(prefs))
        }
    }

    @Test
    fun prefs_corruptOrLegacyValue_isClamped() {
        val prefs = FakePrefs()
        prefs.edit().putInt(PREF_POOL_CLOTH, 99).apply()
        assertEquals("越界旧档应钳制到上限", CLOTH_THEME_COUNT - 1, loadClothTheme(prefs))
        prefs.edit().putInt(PREF_POOL_CLOTH, -5).apply()
        assertEquals("负值旧档应钳制到 0", 0, loadClothTheme(prefs))
        saveClothTheme(prefs, 99)
        assertEquals("保存越界值也应钳制", CLOTH_THEME_COUNT - 1, loadClothTheme(prefs))
    }

    /** 覆盖台呢主题用到的 SharedPreferences 子集（Int 读写 + edit）。 */
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
