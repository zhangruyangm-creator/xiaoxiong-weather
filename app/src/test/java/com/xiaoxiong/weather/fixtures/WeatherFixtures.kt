package com.xiaoxiong.weather.fixtures

import com.xiaoxiong.weather.data.AirQuality
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.DailyWeather
import com.xiaoxiong.weather.data.HourlyWeather

object WeatherFixtures {
    fun current(
        time: String = "2026-08-02T12:00",
        weatherCode: Int? = 0,
        temperatureC: Double? = 22.0,
        apparentTemperatureC: Double? = null,
        windSpeedKmh: Double? = null,
        windGustsKmh: Double? = null,
        visibilityMeters: Double? = null,
        precipitationMm: Double? = null,
        precipitationIntervalSeconds: Int? = null,
        /** 测试默认：precipitationRate 与小时桶一致（整点合计 mm）。 */
        precipitationRateMmPerHour: Double? = precipitationMm,
        isDay: Boolean? = true,
    ) = CurrentWeather(
        time = time,
        weatherCode = weatherCode,
        temperatureC = temperatureC,
        apparentTemperatureC = apparentTemperatureC,
        windSpeedKmh = windSpeedKmh,
        windGustsKmh = windGustsKmh,
        visibilityMeters = visibilityMeters,
        precipitationMm = precipitationMm,
        precipitationIntervalSeconds = precipitationIntervalSeconds,
        precipitationRateMmPerHour = precipitationRateMmPerHour,
        isDay = isDay,
    )

    fun hourly(
        time: String,
        precipitationMm: Double? = null,
        precipProbability: Int? = null,
        temperatureC: Double? = 20.0,
        weatherCode: Int? = null,
    ) = HourlyWeather(
        time = time,
        temperatureC = temperatureC,
        weatherCode = weatherCode,
        precipitationMm = precipitationMm,
        precipProbability = precipProbability,
    )

    fun daily(
        date: String = "2026-08-02",
        uvIndexMax: Double? = null,
        tempMaxC: Double? = null,
        tempMinC: Double? = null,
    ) = DailyWeather(
        date = date,
        tempMaxC = tempMaxC,
        tempMinC = tempMinC,
        uvIndexMax = uvIndexMax,
    )

    fun air(
        europeanAqi: Int? = null,
        uvIndex: Double? = null,
        dustUgM3: Double? = null,
    ) = AirQuality(
        europeanAqi = europeanAqi,
        uvIndex = uvIndex,
        dustUgM3 = dustUgM3,
    )

    fun city(
        name: String,
        lat: Double = 36.65,
        lon: Double = 117.12,
        admin1: String? = "山东",
        admin2: String? = null,
    ) = City(
        id = name.hashCode().toLong(),
        name = name,
        latitude = lat,
        longitude = lon,
        country = "中国",
        admin1 = admin1,
        admin2 = admin2,
    )

    /** 过去 2 小时 + 当前 + 未来若干小时 */
    fun hourlyAroundNow(
        currentTime: String = "2026-08-02T12:00",
        futureHours: Int = 6,
        futurePrecipMm: Double? = null,
        futurePrecipProb: Int? = null,
    ): List<HourlyWeather> {
        val baseHour = currentTime.take(13) // yyyy-MM-ddTHH
        val prefix = baseHour.take(11) // yyyy-MM-ddT
        val hour = baseHour.takeLast(2).toInt()
        val list = mutableListOf<HourlyWeather>()
        for (h in (hour - 2)..(hour + futureHours)) {
            val hh = ((h % 24) + 24) % 24
            val time = "%s%02d:00".format(prefix, hh)
            val isFutureOrNow = h >= hour
            list +=
                hourly(
                    time = time,
                    precipitationMm = if (isFutureOrNow) futurePrecipMm else 0.0,
                    precipProbability = if (isFutureOrNow) futurePrecipProb else 0,
                )
        }
        return list
    }
}
