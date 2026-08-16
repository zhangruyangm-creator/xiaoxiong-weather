package com.xiaoxiong.weather.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RainViewerMapsResponse(
    val version: String? = null,
    val generated: Long? = null,
    val host: String,
    val radar: RainViewerRadar? = null,
)

@Serializable
data class RainViewerRadar(
    val past: List<RainViewerFrame> = emptyList(),
    val nowcast: List<RainViewerFrame> = emptyList(),
)

@Serializable
data class RainViewerFrame(
    val time: Long,
    val path: String,
)
