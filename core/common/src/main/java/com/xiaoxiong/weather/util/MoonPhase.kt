package com.xiaoxiong.weather.util

import com.xiaoxiong.weather.R
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos

/**
 * 朔望月相（本地天文近似，无需网络）。
 *
 * [cycleFraction]：0 = 新月，0.25 ≈ 上弦，0.5 = 满月，0.75 ≈ 下弦。
 * [illumination]：被照亮比例 0…1。
 */
data class MoonPhaseInfo(
    val cycleFraction: Double,
    val illumination: Double,
    val ageDays: Double,
    val phase: MoonPhase,
) {
    val labelRes: Int get() = phase.labelRes
}

enum class MoonPhase(
    val labelRes: Int,
) {
    NEW(R.string.moon_new),
    WAXING_CRESCENT(R.string.moon_waxing_crescent),
    FIRST_QUARTER(R.string.moon_first_quarter),
    WAXING_GIBBOUS(R.string.moon_waxing_gibbous),
    FULL(R.string.moon_full),
    WANING_GIBBOUS(R.string.moon_waning_gibbous),
    LAST_QUARTER(R.string.moon_last_quarter),
    WANING_CRESCENT(R.string.moon_waning_crescent),
}

object MoonPhaseCalculator {
    /** 朔望月平均长度（日） */
    const val SYNODIC_DAYS: Double = 29.530588853

    /** 已知新月：2000-01-06 18:14 UTC ≈ JD 2451550.1 */
    private const val KNOWN_NEW_MOON_JD: Double = 2451550.1

    fun of(date: LocalDate): MoonPhaseInfo {
        val jd = julianDay(date)
        val age = ((jd - KNOWN_NEW_MOON_JD) % SYNODIC_DAYS + SYNODIC_DAYS) % SYNODIC_DAYS
        val cycle = age / SYNODIC_DAYS
        val illumination = ((1.0 - cos(2.0 * PI * cycle)) / 2.0).coerceIn(0.0, 1.0)
        return MoonPhaseInfo(
            cycleFraction = cycle,
            illumination = illumination,
            ageDays = age,
            phase = phaseForCycle(cycle),
        )
    }

    fun phaseForCycle(cycleFraction: Double): MoonPhase {
        val t = ((cycleFraction % 1.0) + 1.0) % 1.0
        return when {
            t < 0.0625 || t >= 0.9375 -> MoonPhase.NEW
            t < 0.1875 -> MoonPhase.WAXING_CRESCENT
            t < 0.3125 -> MoonPhase.FIRST_QUARTER
            t < 0.4375 -> MoonPhase.WAXING_GIBBOUS
            t < 0.5625 -> MoonPhase.FULL
            t < 0.6875 -> MoonPhase.WANING_GIBBOUS
            t < 0.8125 -> MoonPhase.LAST_QUARTER
            else -> MoonPhase.WANING_CRESCENT
        }
    }

    /** 简化儒略日：取该日 12:00 UTC，足够做日级月相。 */
    fun julianDay(date: LocalDate): Double {
        val instant = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val epochDay = instant.epochSecond / 86400.0
        return epochDay + 2440587.5
    }

}
