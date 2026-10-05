package com.xiaoxiong.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformancePathLabelTest {
    @Test
    fun skipsVersionAndApiPrefix() {
        assertEquals("forecast", performancePathLabel("/v1/forecast"))
        assertEquals("air-quality", performancePathLabel("/v1/air-quality"))
        assertEquals("search", performancePathLabel("/v1/search"))
        assertEquals("forecast", performancePathLabel("/api/v1/forecast"))
    }

    @Test
    fun radarTilesShareOneLabelInsteadOfPerFrame() {
        assertEquals("radar", performancePathLabel("/radar/20241005/24.png"))
        assertEquals("radar", performancePathLabel("/radar/20241005/25.png"))
    }

    @Test
    fun emptyPathIsUnknown() {
        assertEquals("unknown", performancePathLabel("/"))
        assertEquals("unknown", performancePathLabel(""))
    }
}
