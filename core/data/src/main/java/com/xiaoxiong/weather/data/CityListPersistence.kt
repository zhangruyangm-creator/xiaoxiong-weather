package com.xiaoxiong.weather.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.math.abs
import kotlin.math.round

// 城市列表的容错读写。
//
// 背景：收藏/最近城市都以 JSON 数组整串落盘。此前用
// `runCatching { decodeFromString(ListSerializer(...)) }.getOrDefault(emptyList())`，
// **单条坏数据（手工改档、版本漂移、写盘截断）会让整批解析失败变成空列表**；
// 而收藏的下一次 toggle 会以这个空列表为基准写回，等于把用户其余收藏永久抹掉。
//
// 这里改为逐条解码：坏条目只丢它自己，其余照常可用；再统一做合法性过滤、
// 同城去重与数量上限，避免坏数据经由 UI 流入渲染与检索。

/** 城市是否适合进入列表：名字非空、坐标有限且在合法范围内。 */
fun City.isUsableForList(): Boolean =
    name.isNotBlank() &&
        latitude.isFinite() &&
        longitude.isFinite() &&
        abs(latitude) <= 90.0 &&
        abs(longitude) <= 180.0

/** 同城去重键：坐标量化到约 100m，避免同一城市因小数位差异重复出现。 */
fun City.listDedupKey(): String = "${round(latitude * 1000.0).toLong()},${round(longitude * 1000.0).toLong()}"

/**
 * 逐条解码城市 DTO 列表：单条损坏只丢该条，不影响其余条目。
 * 顶层不是数组时返回空列表（与旧行为一致）。
 */
fun <D> decodeCityListTolerant(
    json: Json,
    raw: String?,
    serializer: KSerializer<D>,
): List<D> {
    if (raw.isNullOrBlank()) return emptyList()
    val elements =
        runCatching { json.decodeFromString(JsonArray.serializer(), raw) }.getOrNull()
            ?: return emptyList()
    return elements.mapNotNull { element ->
        runCatching { json.decodeFromJsonElement(serializer, element) }.getOrNull()
    }
}

/**
 * 列表规范化：过滤非法 → 同城去重（保留首次出现，即最新在前）→ 截断到 [max]。
 */
fun List<City>.normalizeCityList(max: Int): List<City> =
    asSequence()
        .filter { it.isUsableForList() }
        .distinctBy { it.listDedupKey() }
        .take(max)
        .toList()
