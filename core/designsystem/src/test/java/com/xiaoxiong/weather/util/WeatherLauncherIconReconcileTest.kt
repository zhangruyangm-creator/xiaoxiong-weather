package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherLauncherIconReconcileTest {
    private val sunny = WeatherLauncherIcon.Style.SUNNY
    private val rainy = WeatherLauncherIcon.Style.RAINY
    private val foggy = WeatherLauncherIcon.Style.FOGGY

    @Test
    fun noEnabledAlias_fallsBackToStoredTarget() {
        assertEquals(rainy, reconcileTarget(stored = rainy, enabled = emptyList()))
    }

    @Test
    fun singleEnabledAlias_winsOverStoredPrefs() {
        // prefs 写盘失败时以 PackageManager 实际状态为准
        assertEquals(foggy, reconcileTarget(stored = rainy, enabled = listOf(foggy)))
    }

    @Test
    fun oneEnabledAlias_matchingStored_keepsIt() {
        assertEquals(sunny, reconcileTarget(stored = sunny, enabled = listOf(sunny)))
    }

    @Test
    fun multipleEnabled_residuals_preferStoredWhenPresent() {
        // 异常中断残留双启用时，优先保留 prefs 记录为用户目标
        assertEquals(
            rainy,
            reconcileTarget(stored = rainy, enabled = listOf(sunny, rainy, foggy)),
        )
    }

    @Test
    fun multipleEnabled_storedNotAmongThem_fallsBackToFirstByEntriesOrder() {
        assertEquals(
            sunny,
            reconcileTarget(stored = rainy, enabled = listOf(sunny, foggy)),
        )
    }
}
