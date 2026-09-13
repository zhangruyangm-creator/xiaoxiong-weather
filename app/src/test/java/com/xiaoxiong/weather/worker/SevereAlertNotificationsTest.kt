package com.xiaoxiong.weather.worker

import android.app.NotificationManager
import com.xiaoxiong.weather.data.AlertKind
import com.xiaoxiong.weather.data.AlertSeverity
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.WeatherAlertGenerator
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.util.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevereAlertNotificationsTest {
    private fun spec(
        kind: AlertKind,
        severity: AlertSeverity,
        titleRes: Int = kind.ordinal + 1,
    ) = WeatherAlertGenerator.Spec(
        kind = kind,
        severity = severity,
        titleRes = titleRes,
        messageRes = titleRes + 1000,
    )

    private fun kindsOf(specs: List<WeatherAlertGenerator.Spec>): List<AlertKind> = selectSevereNotificationSpecs(specs).map { it.kind }

    @Test
    fun warningSeverity_anyKind_isSelected() {
        val selected =
            kindsOf(
                listOf(
                    spec(AlertKind.HEAT, AlertSeverity.WARNING),
                    spec(AlertKind.COLD, AlertSeverity.WARNING),
                    spec(AlertKind.DUST, AlertSeverity.WARNING),
                    spec(AlertKind.UV, AlertSeverity.WARNING),
                ),
            )
        assertEquals(
            listOf(AlertKind.HEAT, AlertKind.COLD, AlertKind.DUST, AlertKind.UV),
            selected,
        )
    }

    @Test
    fun watchSeverity_severeKinds_areSelected() {
        val selected =
            kindsOf(
                listOf(
                    spec(AlertKind.HEAT_WAVE, AlertSeverity.WATCH),
                    spec(AlertKind.COLD_WAVE, AlertSeverity.WATCH),
                    spec(AlertKind.FOG, AlertSeverity.WATCH),
                    spec(AlertKind.LOW_VISIBILITY, AlertSeverity.WATCH),
                    spec(AlertKind.DUST, AlertSeverity.WATCH),
                    spec(AlertKind.WIND, AlertSeverity.WATCH),
                    spec(AlertKind.UV, AlertSeverity.WATCH),
                    spec(AlertKind.PONDING, AlertSeverity.WATCH),
                    spec(AlertKind.AIR_QUALITY, AlertSeverity.WATCH),
                ),
            )
        assertEquals(9, selected.size)
    }

    @Test
    fun soonRainAndSnow_keptFromLegacyBehavior_atAnySeverity() {
        val selected =
            kindsOf(
                listOf(
                    spec(AlertKind.RAIN_SOON, AlertSeverity.INFO),
                    spec(AlertKind.SNOW_SOON, AlertSeverity.WATCH),
                ),
            )
        assertEquals(listOf(AlertKind.RAIN_SOON, AlertKind.SNOW_SOON), selected)
    }

    @Test
    fun plainRainSnowAndClear_areExcluded() {
        val selected =
            kindsOf(
                listOf(
                    spec(AlertKind.RAINING, AlertSeverity.WATCH),
                    spec(AlertKind.SNOWING, AlertSeverity.WATCH),
                    spec(AlertKind.CLEAR, AlertSeverity.INFO),
                ),
            )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun sameKindAndTitle_areCollapsed() {
        val selected =
            kindsOf(
                listOf(
                    spec(AlertKind.WIND, AlertSeverity.WATCH, titleRes = 7),
                    spec(AlertKind.WIND, AlertSeverity.WARNING, titleRes = 7),
                ),
            )
        assertEquals(listOf(AlertKind.WIND), selected)
    }

    @Test
    fun signature_includesCityKindAndTitle() {
        val spec = spec(AlertKind.FOG, AlertSeverity.WARNING, titleRes = 42)
        assertEquals(
            "青岛|FOG|42",
            severeNotificationSignature("青岛", spec),
        )
        assertTrue(
            severeNotificationSignature("北京", spec) !=
                severeNotificationSignature("青岛", spec),
        )
    }

    @Test
    fun notificationSpecs_selectsSevereAlertsFromSnapshot() {
        // fresh 命中分支复用同一评估入口：大风 WARNING 必须仍被选中
        val snapshot =
            WeatherSnapshot(
                city = City(id = 1L, name = "测试市", latitude = 36.65, longitude = 117.12),
                current =
                    CurrentWeather(
                        time = "2026-08-02T12:00",
                        weatherCode = 0,
                        windGustsKmh = WeatherCodes.WIND_WARNING_KMH,
                    ),
            )

        val selected = selectSevereNotificationSpecs(notificationSpecs(snapshot))

        assertTrue(selected.any { it.kind == AlertKind.WIND })
    }

    @Test
    fun canDisplaySystemNotifications_requiresSystemAndChannelEnabled() {
        assertTrue(
            canDisplaySystemNotifications(
                systemNotificationsEnabled = true,
                channelImportance = null,
            ),
        )
        assertTrue(
            canDisplaySystemNotifications(
                systemNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_LOW,
            ),
        )
        assertFalse(
            canDisplaySystemNotifications(
                systemNotificationsEnabled = false,
                channelImportance = NotificationManager.IMPORTANCE_LOW,
            ),
        )
        // 渠道被用户关闭：不展示也不写去重记忆
        assertFalse(
            canDisplaySystemNotifications(
                systemNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_NONE,
            ),
        )
    }
}
