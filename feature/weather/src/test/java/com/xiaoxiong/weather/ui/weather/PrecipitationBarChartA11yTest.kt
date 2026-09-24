package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.data.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecipitationBarChartA11yTest {
    private fun bar(
        time: String,
        mm: Double?,
        isNow: Boolean = false,
        isFuture: Boolean = false,
    ) = PrecipBar(
        item = HourlyWeather(time = time, precipitationMm = mm),
        isFuture = isFuture,
        isNow = isNow,
    )

    @Test
    fun barsSpokenText_listsEveryBarWithValue() {
        val bars = listOf(bar("2026-09-25T14:00", 0.2), bar("2026-09-25T15:00", 1.53, isFuture = true))
        val text =
            precipBarsSpokenText(
                bars = bars,
                nowLabel = "现在",
                hourFormatter = { it.substring(11, 13) },
                unit = " 毫米",
                separator = "、",
            )
        assertEquals("14 0.2 毫米、15 1.5 毫米", text)
    }

    @Test
    fun barsSpokenText_usesNowLabelForCurrentBar() {
        val bars = listOf(bar("2026-09-25T14:00", 0.0, isNow = true))
        val text =
            precipBarsSpokenText(
                bars = bars,
                nowLabel = "Now",
                hourFormatter = { it.substring(11, 13) },
                unit = " mm",
                separator = "; ",
            )
        assertEquals("Now 0.0 mm", text)
    }

    @Test
    fun barsSpokenText_treatsNullPrecipitationAsZero() {
        val bars = listOf(bar("2026-09-25T14:00", null))
        val text =
            precipBarsSpokenText(
                bars = bars,
                nowLabel = "现在",
                hourFormatter = { "X" },
                unit = " 毫米",
                separator = "、",
            )
        assertTrue("缺测应读作 0.0 而不是空白", text.contains("0.0"))
    }

    @Test
    fun barsSpokenText_emptyBarsProducesEmptyString() {
        val text =
            precipBarsSpokenText(
                bars = emptyList(),
                nowLabel = "现在",
                hourFormatter = { it },
                unit = " 毫米",
                separator = "、",
            )
        assertEquals("", text)
    }
}
