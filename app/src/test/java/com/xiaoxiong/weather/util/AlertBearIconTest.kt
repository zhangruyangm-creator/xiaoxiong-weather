package com.xiaoxiong.weather.util

import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.AlertKind
import com.xiaoxiong.weather.data.AlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertBearIconTest {
    @Test
    fun everyAlertKind_hasDrawableMapping() {
        AlertKind.entries.forEach { kind ->
            val res = AlertBearIcon.drawableRes(kind)
            assertNotEquals("kind=$kind should map to a drawable", 0, res)
        }
    }

    @Test
    fun dedicatedAlertBears_coverSpecialKinds() {
        assertEquals(R.drawable.ic_alert_bear_hot, AlertBearIcon.drawableRes(AlertKind.HEAT))
        assertEquals(
            R.drawable.ic_alert_bear_heat_wave,
            AlertBearIcon.drawableRes(AlertKind.HEAT_WAVE),
        )
        assertEquals(R.drawable.ic_alert_bear_cold, AlertBearIcon.drawableRes(AlertKind.COLD))
        assertEquals(
            R.drawable.ic_alert_bear_cooldown,
            AlertBearIcon.drawableRes(AlertKind.COOLDOWN),
        )
        assertEquals(
            R.drawable.ic_alert_bear_cold_wave,
            AlertBearIcon.drawableRes(AlertKind.COLD_WAVE),
        )
        assertEquals(R.drawable.ic_alert_bear_uv, AlertBearIcon.drawableRes(AlertKind.UV))
        assertEquals(R.drawable.ic_alert_bear_wind, AlertBearIcon.drawableRes(AlertKind.WIND))
        assertEquals(R.drawable.ic_alert_bear_storm, AlertBearIcon.drawableRes(AlertKind.STORM))
        assertEquals(R.drawable.ic_alert_bear_hail, AlertBearIcon.drawableRes(AlertKind.HAIL))
        assertEquals(R.drawable.ic_alert_bear_freezing_rain, AlertBearIcon.drawableRes(AlertKind.FREEZING_RAIN))
        assertEquals(R.drawable.ic_alert_bear_heavy_rain, AlertBearIcon.drawableRes(AlertKind.HEAVY_RAIN))
        assertEquals(
            R.drawable.ic_alert_bear_heavy_rain,
            AlertBearIcon.drawableRes(AlertKind.HEAVY_RAIN, 10.0, AlertSeverity.WATCH),
        )
        assertEquals(
            R.drawable.ic_alert_bear_heavy_rain_warning,
            AlertBearIcon.drawableRes(AlertKind.HEAVY_RAIN, 18.0, AlertSeverity.WARNING),
        )
        assertEquals(
            R.drawable.ic_alert_bear_heavy_rain_warning,
            AlertBearIcon.heavyRainUmbrellaRes(16.0),
        )
        assertEquals(R.drawable.ic_alert_bear_ponding, AlertBearIcon.drawableRes(AlertKind.PONDING))
        assertEquals(R.drawable.ic_alert_bear_snow, AlertBearIcon.drawableRes(AlertKind.SNOW))
        assertEquals(
            R.drawable.ic_launcher_bear_blizzard,
            AlertBearIcon.drawableRes(AlertKind.SNOW, 6.0, AlertSeverity.WARNING),
        )
        assertEquals(
            R.drawable.ic_alert_bear_snow,
            AlertBearIcon.drawableRes(AlertKind.SNOW_SOON, 3.0, AlertSeverity.WATCH),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_snowy,
            AlertBearIcon.drawableRes(AlertKind.SNOWING),
        )
        assertEquals(R.drawable.ic_alert_bear_snowy_road, AlertBearIcon.drawableRes(AlertKind.SNOWY_ROAD))
        assertEquals(R.drawable.ic_alert_bear_aqi, AlertBearIcon.drawableRes(AlertKind.AIR_QUALITY))
        assertEquals(R.drawable.ic_alert_bear_dust, AlertBearIcon.drawableRes(AlertKind.DUST))
    }

    @Test
    fun weatherStyleKinds_reuseLauncherBears() {
        assertEquals(R.drawable.ic_launcher_bear_foggy, AlertBearIcon.drawableRes(AlertKind.FOG))
        assertEquals(
            R.drawable.ic_launcher_bear_foggy,
            AlertBearIcon.drawableRes(AlertKind.LOW_VISIBILITY),
        )
        assertEquals(R.drawable.ic_launcher_bear_sunny, AlertBearIcon.drawableRes(AlertKind.CLEAR))
    }

    @Test
    fun rainSoon_umbrellaColorByPeakMm() {
        assertEquals(
            R.drawable.ic_launcher_bear_rainy,
            AlertBearIcon.drawableRes(AlertKind.RAIN_SOON, 0.5),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_rainy,
            AlertBearIcon.drawableRes(AlertKind.RAINING, 0.5),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_moderate_rain,
            AlertBearIcon.drawableRes(AlertKind.RAINING, 2.2),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_moderate_rain,
            AlertBearIcon.drawableRes(AlertKind.RAIN_SOON, 2.0),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_heavy_rain,
            AlertBearIcon.drawableRes(AlertKind.RAIN_SOON, 10.0),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_violent_rain,
            AlertBearIcon.drawableRes(AlertKind.RAIN_SOON, 20.0),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_extreme_rain,
            AlertBearIcon.drawableRes(AlertKind.RAIN_SOON, 55.0),
        )
        assertEquals(
            R.drawable.ic_launcher_bear_rainy,
            AlertBearIcon.rainSoonUmbrellaRes(null),
        )
    }

    @Test
    fun dedicatedAndReuse_coverAllKinds() {
        val dedicated =
            setOf(
                AlertKind.HEAT,
                AlertKind.HEAT_WAVE,
                AlertKind.COLD,
                AlertKind.COOLDOWN,
                AlertKind.COLD_WAVE,
                AlertKind.UV,
                AlertKind.WIND,
                AlertKind.STORM,
                AlertKind.HAIL,
                AlertKind.FREEZING_RAIN,
                AlertKind.HEAVY_RAIN,
                AlertKind.PONDING,
                AlertKind.SNOW,
                AlertKind.SNOWY_ROAD,
                AlertKind.AIR_QUALITY,
                AlertKind.DUST,
            )
        val reused =
            setOf(
                AlertKind.FOG,
                AlertKind.LOW_VISIBILITY,
                AlertKind.RAINING,
                AlertKind.RAIN_SOON,
                AlertKind.SNOWING,
                AlertKind.SNOW_SOON,
                AlertKind.CLEAR,
            )
        assertTrue(dedicated.intersect(reused).isEmpty())
        assertEquals(AlertKind.entries.toSet(), dedicated + reused)
    }
}
