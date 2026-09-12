package com.xiaoxiong.weather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MET Norway Locationforecast 2.0（compact）响应 DTO，仅覆盖本项目使用的字段。
 * 官方文档：https://api.met.no/weatherapi/locationforecast/2.0/documentation
 */
@Serializable
data class MetNorwayResponse(
    val properties: MetNorwayProperties? = null,
)

@Serializable
data class MetNorwayProperties(
    val meta: MetNorwayMeta? = null,
    val timeseries: List<MetNorwayTimeseriesEntry> = emptyList(),
)

@Serializable
data class MetNorwayMeta(
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MetNorwayTimeseriesEntry(
    val time: String,
    val data: MetNorwayData? = null,
)

@Serializable
data class MetNorwayData(
    val instant: MetNorwayInstant? = null,
    @SerialName("next_1_hours") val next1Hours: MetNorwayNext? = null,
    @SerialName("next_6_hours") val next6Hours: MetNorwayNext? = null,
    @SerialName("next_12_hours") val next12Hours: MetNorwayNext? = null,
)

@Serializable
data class MetNorwayInstant(
    val details: MetNorwayDetails? = null,
)

/** compact 端点只含常用要素；体感/阵风/露点等需 complete 端点，缺失时为 null。 */
@Serializable
data class MetNorwayDetails(
    @SerialName("air_temperature") val airTemperature: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_from_direction") val windFromDirection: Double? = null,
    @SerialName("air_pressure_at_sea_level") val airPressureAtSeaLevel: Double? = null,
    @SerialName("cloud_area_fraction") val cloudAreaFraction: Double? = null,
    @SerialName("fog_area_fraction") val fogAreaFraction: Double? = null,
)

@Serializable
data class MetNorwayNext(
    val summary: MetNorwaySummary? = null,
    val details: MetNorwayNextDetails? = null,
)

@Serializable
data class MetNorwaySummary(
    @SerialName("symbol_code") val symbolCode: String? = null,
)

@Serializable
data class MetNorwayNextDetails(
    @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
    @SerialName("probability_of_precipitation") val probabilityOfPrecipitation: Double? = null,
)
