package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.util.parseFlexibleDateTime
import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.abs

/**
 * 按纬度、海拔（及行政区兜底）与日历推算春夏秋冬花季与红叶窗口（经验时令，非观测物候）。
 *
 * 海拔越高春花越晚、秋色越早；青藏高原与长江中下游同纬度也会拉开数周。
 */
object SeasonTipGenerator {

    private const val UPCOMING_DAYS = 14

    enum class Kind {
        WINTER_BLOOM,
        SPRING_BLOOM,
        SUMMER_BLOOM,
        AUTUMN_BLOOM,
        FOLIAGE
    }

    enum class Phase { UPCOMING, STARTING, PEAK, ENDING }

    /** 地形/气候带：平原 → 高原，物候窗逐级偏移。 */
    enum class ReliefBand {
        PLAIN,
        HILL,
        HIGHLAND,
        PLATEAU
    }

    data class Tip(
        val kind: Kind,
        val phase: Phase,
        val windowStart: MonthDay,
        val windowEnd: MonthDay,
        /** 该纬度带常见观赏对象文案（花种 / 彩叶树） */
        @param:StringRes val subjectsRes: Int
    )

    /** 当前所有命中的时令（含即将进入）；重叠时全部返回。 */
    fun tipsFor(
        latitude: Double,
        longitude: Double = 0.0,
        onDate: LocalDate,
        elevationMeters: Double? = null,
        admin1: String? = null
    ): List<Tip> {
        val southern = latitude < 0
        val lat = abs(latitude)
        val band = if (southern) {
            ReliefBand.PLAIN
        } else {
            reliefBand(elevationMeters, lat, longitude, admin1)
        }
        // 南半球季节相反：用「当地日期 +6 个月」对齐北半球物候窗，再把展示月日映回当地
        val date = if (southern) onDate.plusMonths(6) else onDate
        val md = MonthDay.from(date)
        val highlandish = band == ReliefBand.HIGHLAND || band == ReliefBand.PLATEAU

        val tips = listOfNotNull(
            evaluate(
                Kind.WINTER_BLOOM,
                shiftWindow(winterBloomWindow(lat), days = reliefShiftDays(Kind.WINTER_BLOOM, band)),
                md,
                date
            )?.withSubjects(lat, highlandish),
            evaluate(
                Kind.SPRING_BLOOM,
                shiftWindow(springBloomWindow(lat), days = reliefShiftDays(Kind.SPRING_BLOOM, band)),
                md,
                date
            )?.withSubjects(lat, highlandish),
            evaluate(
                Kind.SUMMER_BLOOM,
                shiftWindow(summerBloomWindow(lat), days = reliefShiftDays(Kind.SUMMER_BLOOM, band)),
                md,
                date
            )?.withSubjects(lat, highlandish),
            evaluate(
                Kind.AUTUMN_BLOOM,
                shiftWindow(autumnBloomWindow(lat), days = reliefShiftDays(Kind.AUTUMN_BLOOM, band)),
                md,
                date
            )?.withSubjects(lat, highlandish),
            evaluate(
                Kind.FOLIAGE,
                shiftWindow(foliageWindow(lat), days = reliefShiftDays(Kind.FOLIAGE, band)),
                md,
                date
            )?.withSubjects(lat, highlandish)
        )
        return if (southern) tips.map { it.withLocalSouthernWindow() } else tips
    }

    fun tipsForCity(
        city: City,
        referenceIsoTime: String?,
        elevationMeters: Double? = null
    ): List<Tip> {
        val date = parseFlexibleDateTime(referenceIsoTime.orEmpty())?.toLocalDate()
            ?: LocalDate.now()
        return tipsFor(
            latitude = city.latitude,
            longitude = city.longitude,
            onDate = date,
            elevationMeters = elevationMeters,
            admin1 = city.admin1
        )
    }

    /**
     * 物候偏移天数：正数表示整体推迟（春/夏/冬花），秋花与红叶多为负（更早）。
     * 粗略参照「约每 100m 晚 3～4 天」量级，按带阶梯取值。
     */
    internal fun reliefShiftDays(kind: Kind, band: ReliefBand): Int = when (band) {
        ReliefBand.PLAIN -> 0
        ReliefBand.HILL -> when (kind) {
            Kind.WINTER_BLOOM -> 5
            Kind.SPRING_BLOOM -> 7
            Kind.SUMMER_BLOOM -> 5
            Kind.AUTUMN_BLOOM -> -3
            Kind.FOLIAGE -> -5
        }
        ReliefBand.HIGHLAND -> when (kind) {
            Kind.WINTER_BLOOM -> 12
            Kind.SPRING_BLOOM -> 16
            Kind.SUMMER_BLOOM -> 10
            Kind.AUTUMN_BLOOM -> -7
            Kind.FOLIAGE -> -12
        }
        ReliefBand.PLATEAU -> when (kind) {
            Kind.WINTER_BLOOM -> 18
            Kind.SPRING_BLOOM -> 25
            Kind.SUMMER_BLOOM -> 12
            Kind.AUTUMN_BLOOM -> -10
            Kind.FOLIAGE -> -14
        }
    }

    /**
     * 优先用 Open-Meteo elevation；无海拔时用行政区 / 经纬度粗判高原。
     */
    internal fun reliefBand(
        elevationMeters: Double?,
        absLat: Double,
        longitude: Double,
        admin1: String?
    ): ReliefBand {
        val elev = elevationMeters
        if (elev != null && !elev.isNaN()) {
            return when {
                elev >= 3000.0 -> ReliefBand.PLATEAU
                elev >= 1500.0 -> ReliefBand.HIGHLAND
                elev >= 800.0 -> ReliefBand.HILL
                else -> ReliefBand.PLAIN
            }
        }
        if (isPlateauAdmin(admin1) || isHighPlateau(absLat, longitude)) {
            return ReliefBand.PLATEAU
        }
        return ReliefBand.PLAIN
    }

    /** 冬花：梅、蜡梅、山茶等。 */
    internal fun winterBloomWindow(absLat: Double): Pair<MonthDay, MonthDay> = when {
        absLat < 22 -> md(12, 1) to md(2, 15)
        absLat < 28 -> md(12, 10) to md(2, 20)
        absLat < 34 -> md(12, 15) to md(2, 28)
        absLat < 40 -> md(1, 5) to md(3, 5)
        absLat < 46 -> md(2, 1) to md(3, 20)
        else -> md(3, 1) to md(4, 10)
    }

    /** 春花：樱、桃、玉兰、油菜等。 */
    internal fun springBloomWindow(absLat: Double): Pair<MonthDay, MonthDay> = when {
        absLat < 22 -> md(2, 1) to md(4, 10)
        absLat < 28 -> md(2, 15) to md(4, 15)
        absLat < 34 -> md(3, 1) to md(4, 25)
        absLat < 40 -> md(3, 15) to md(5, 10)
        absLat < 46 -> md(4, 1) to md(5, 25)
        else -> md(4, 20) to md(6, 10)
    }

    /** 夏花：荷花、紫薇、茉莉等。 */
    internal fun summerBloomWindow(absLat: Double): Pair<MonthDay, MonthDay> = when {
        absLat < 22 -> md(5, 1) to md(9, 15)
        absLat < 28 -> md(5, 15) to md(9, 10)
        absLat < 34 -> md(6, 1) to md(8, 31)
        absLat < 40 -> md(6, 10) to md(8, 25)
        absLat < 46 -> md(6, 20) to md(8, 15)
        else -> md(7, 1) to md(8, 10)
    }

    /** 秋花：桂花、菊花等，一般略早于或与红叶季交错。 */
    internal fun autumnBloomWindow(absLat: Double): Pair<MonthDay, MonthDay> = when {
        absLat < 22 -> md(9, 1) to md(11, 25)
        absLat < 28 -> md(9, 1) to md(11, 10)
        absLat < 34 -> md(9, 5) to md(11, 5)
        absLat < 40 -> md(9, 10) to md(10, 25)
        absLat < 46 -> md(8, 25) to md(10, 10)
        else -> md(8, 15) to md(9, 25)
    }

    /** 低纬常绿区不提示红叶。 */
    internal fun foliageWindow(absLat: Double): Pair<MonthDay, MonthDay>? = when {
        absLat < 22 -> null
        absLat < 28 -> md(11, 15) to md(12, 31)
        absLat < 34 -> md(10, 20) to md(11, 25)
        absLat < 40 -> md(10, 10) to md(11, 15)
        absLat < 46 -> md(9, 25) to md(10, 31)
        else -> md(9, 10) to md(10, 20)
    }

    /** 中国西部高原粗判：偏西且中纬（无海拔数据时的兜底）。 */
    internal fun isHighPlateau(absLat: Double, longitude: Double): Boolean =
        longitude in 75.0..103.0 && absLat in 25.0..40.0

    /** 西藏、青海等行政区兜底（无 elevation 时）。 */
    internal fun isPlateauAdmin(admin1: String?): Boolean {
        val a = admin1?.trim().orEmpty()
        if (a.isEmpty()) return false
        return a.contains("西藏") ||
            a.contains("青海") ||
            a.contains("Xizang", ignoreCase = true) ||
            a.contains("Tibet", ignoreCase = true) ||
            a.contains("Qinghai", ignoreCase = true)
    }

    @StringRes
    internal fun subjectsRes(
        kind: Kind,
        absLat: Double,
        plateau: Boolean
    ): Int = when (kind) {
        Kind.WINTER_BLOOM -> when {
            plateau -> R.string.season_subjects_winter_plateau
            absLat < 22 -> R.string.season_subjects_winter_tropical
            absLat < 28 -> R.string.season_subjects_winter_south
            absLat < 34 -> R.string.season_subjects_winter_central
            absLat < 40 -> R.string.season_subjects_winter_north
            absLat < 46 -> R.string.season_subjects_winter_northeast
            else -> R.string.season_subjects_winter_cold
        }
        Kind.SPRING_BLOOM -> when {
            plateau -> R.string.season_subjects_spring_plateau
            absLat < 22 -> R.string.season_subjects_spring_tropical
            absLat < 28 -> R.string.season_subjects_spring_south
            absLat < 34 -> R.string.season_subjects_spring_central
            absLat < 40 -> R.string.season_subjects_spring_north
            absLat < 46 -> R.string.season_subjects_spring_northeast
            else -> R.string.season_subjects_spring_cold
        }
        Kind.SUMMER_BLOOM -> when {
            plateau -> R.string.season_subjects_summer_plateau
            absLat < 22 -> R.string.season_subjects_summer_tropical
            absLat < 28 -> R.string.season_subjects_summer_south
            absLat < 34 -> R.string.season_subjects_summer_central
            absLat < 40 -> R.string.season_subjects_summer_north
            absLat < 46 -> R.string.season_subjects_summer_northeast
            else -> R.string.season_subjects_summer_cold
        }
        Kind.AUTUMN_BLOOM -> when {
            plateau -> R.string.season_subjects_autumn_plateau
            absLat < 22 -> R.string.season_subjects_autumn_tropical
            absLat < 28 -> R.string.season_subjects_autumn_south
            absLat < 34 -> R.string.season_subjects_autumn_central
            absLat < 40 -> R.string.season_subjects_autumn_north
            absLat < 46 -> R.string.season_subjects_autumn_northeast
            else -> R.string.season_subjects_autumn_cold
        }
        Kind.FOLIAGE -> when {
            plateau -> R.string.season_subjects_foliage_plateau
            absLat < 28 -> R.string.season_subjects_foliage_south
            absLat < 34 -> R.string.season_subjects_foliage_central
            absLat < 40 -> R.string.season_subjects_foliage_north
            else -> R.string.season_subjects_foliage_northeast
        }
    }

    private fun Tip.withSubjects(absLat: Double, plateau: Boolean): Tip =
        copy(subjectsRes = subjectsRes(kind, absLat, plateau))

    private fun shiftWindow(
        window: Pair<MonthDay, MonthDay>?,
        days: Int
    ): Pair<MonthDay, MonthDay>? {
        if (window == null || days == 0) return window
        val year = 2024 // 闰年友好，仅作月日平移锚点
        val start = window.first.atYear(year).plusDays(days.toLong())
        val end = window.second.atYear(year).plusDays(days.toLong())
        return MonthDay.from(start) to MonthDay.from(end)
    }

    private fun evaluate(
        kind: Kind,
        window: Pair<MonthDay, MonthDay>?,
        todayMd: MonthDay,
        today: LocalDate
    ): Tip? {
        if (window == null) return null
        val (start, end) = window
        val placeholder = R.string.season_subjects_spring_north
        if (inWindow(todayMd, start, end)) {
            val phase = phaseInWindow(today, start, end)
            return Tip(kind, phase, start, end, placeholder)
        }
        // 跨年窗（冬花）：即将进入时看「距最近一次开窗」
        val nextStart = nextOccurrence(start, today)
        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, nextStart)
        if (daysUntil in 0..UPCOMING_DAYS) {
            return Tip(kind, Phase.UPCOMING, start, end, placeholder)
        }
        return null
    }

    private fun nextOccurrence(monthDay: MonthDay, today: LocalDate): LocalDate {
        var date = monthDay.atYear(today.year)
        if (!date.isAfter(today)) {
            date = monthDay.atYear(today.year + 1)
        }
        return date
    }

    internal fun inWindow(day: MonthDay, start: MonthDay, end: MonthDay): Boolean =
        if (!start.isAfter(end)) {
            !day.isBefore(start) && !day.isAfter(end)
        } else {
            // 跨年窗口（冬花常见）
            !day.isBefore(start) || !day.isAfter(end)
        }

    internal fun phaseInWindow(today: LocalDate, start: MonthDay, end: MonthDay): Phase {
        val year = today.year
        var startDate = start.atYear(year)
        var endDate = end.atYear(year)
        if (endDate.isBefore(startDate) || start.isAfter(end)) {
            // 跨年：若今天在年末段，结束落到明年；若在年初段，起点落在去年
            if (!today.isBefore(start.atYear(year)) || today.monthValue >= start.monthValue) {
                startDate = start.atYear(year)
                endDate = end.atYear(year + 1)
                if (today.isBefore(startDate) && today.monthValue <= end.monthValue) {
                    startDate = start.atYear(year - 1)
                    endDate = end.atYear(year)
                }
            } else {
                startDate = start.atYear(year - 1)
                endDate = end.atYear(year)
            }
        }
        val total = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(1)
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, today).coerceIn(0, total)
        val ratio = elapsed.toDouble() / total
        return when {
            ratio < 0.2 -> Phase.STARTING
            ratio > 0.8 -> Phase.ENDING
            else -> Phase.PEAK
        }
    }

    /** 把北半球窗口月日映回南半球当地日历（−6 个月）。 */
    private fun Tip.withLocalSouthernWindow(): Tip = copy(
        windowStart = shiftMonthDay(windowStart, months = -6),
        windowEnd = shiftMonthDay(windowEnd, months = -6)
    )

    private fun shiftMonthDay(monthDay: MonthDay, months: Int): MonthDay {
        val year = 2024
        return MonthDay.from(monthDay.atYear(year).plusMonths(months.toLong()))
    }

    private fun md(month: Int, day: Int): MonthDay = MonthDay.of(month, day)
}
