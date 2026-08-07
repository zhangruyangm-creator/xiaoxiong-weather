package com.xiaoxiong.weather.data

import android.content.res.Resources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 常见世界城市中文名 → Open-Meteo 更易命中的英文名（简体常搜不到或只认繁体）。
 * 数据外置在 assets/foreign_city_aliases.json，首次使用加载并缓存。
 */
object ForeignCityAliases {
    @Volatile
    private var cached: Map<String, String>? = null

    fun get(resources: Resources): Map<String, String> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(resources).also { cached = it }
        }
    }

    private fun load(resources: Resources): Map<String, String> = runCatching {
        val text = resources.assets.open("foreign_city_aliases.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())
}
