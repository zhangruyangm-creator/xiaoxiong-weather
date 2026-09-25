package com.xiaoxiong.weather.widget

import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.SelectedCityStore.PersistedSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 小组件 30s 去重缓存的键。
 *
 * 背景：键原先只用 `useLocation + 城市名`，于是同名城市（多个"南京"）会互相命中缓存，
 * 定位移动但地名不变时也会显示旧位置的天气——短时间显示另一城市或旧位置的天气。
 */
class WidgetLoadKeyTest {
    private fun selection(
        name: String,
        lat: Double,
        lon: Double,
        useLocation: Boolean = false,
    ) = PersistedSelection(
        city = City(id = name.hashCode().toLong(), name = name, latitude = lat, longitude = lon),
        useLocation = useLocation,
    )

    @Test
    fun `same city and position yields the same key`() {
        assertEquals(
            widgetLoadKeyOf(selection("南京", 32.06, 118.79)),
            widgetLoadKeyOf(selection("南京", 32.06, 118.79)),
        )
    }

    @Test
    fun `same name but different coordinates yields different keys`() {
        assertNotEquals(
            "同名不同城（多个「南京」）不能命中同一份缓存",
            widgetLoadKeyOf(selection("南京", 32.06, 118.79)),
            widgetLoadKeyOf(selection("南京", 39.90, 116.41)),
        )
    }

    @Test
    fun `location drift beyond about 100m yields a different key`() {
        assertNotEquals(
            "定位移动后地名不变也必须失效，否则显示旧位置的天气",
            widgetLoadKeyOf(selection("当前位置", 32.0600, 118.7900, useLocation = true)),
            widgetLoadKeyOf(selection("当前位置", 32.0700, 118.7900, useLocation = true)),
        )
    }

    @Test
    fun `sub 100m jitter keeps the same key`() {
        assertEquals(
            "几十米级抖动不值得重新联网",
            widgetLoadKeyOf(selection("南京", 32.06000, 118.79000)),
            widgetLoadKeyOf(selection("南京", 32.06005, 118.79005)),
        )
    }

    @Test
    fun `location mode is part of the key`() {
        assertNotEquals(
            widgetLoadKeyOf(selection("南京", 32.06, 118.79, useLocation = false)),
            widgetLoadKeyOf(selection("南京", 32.06, 118.79, useLocation = true)),
        )
    }

    private fun widgetLoadKeyOf(selection: PersistedSelection): String = WeatherWidgetData.widgetLoadKey(selection)
}
