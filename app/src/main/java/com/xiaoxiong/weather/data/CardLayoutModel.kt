package com.xiaoxiong.weather.data

import androidx.annotation.StringRes

/** 页面可配置卡片模块的通用契约。 */
interface CardId {
    @get:StringRes val titleRes: Int

    /** 始终展示、不可关闭的卡片。 */
    val alwaysVisible: Boolean
}

/** 一张卡片的显隐偏好。 */
data class CardPref<T>(
    val id: T,
    val visible: Boolean,
)

/**
 * 页面卡片布局的通用实现：默认表、与默认表合并、编解码、显隐切换、拖动排序。
 * 每个页面的卡片对象（如 [TodayCardLayout]）只提供各自的卡片枚举，即可获得全部能力，
 * 避免页面间复制同一套 merge/encode/decode/move 逻辑。
 */
open class CardLayoutModel<T : CardId>(
    private val values: Array<T>,
) {
    val defaults: List<CardPref<T>> =
        values.map { CardPref(id = it, visible = true) }

    /**
     * 与默认表合并：保留已保存顺序与显隐；丢弃未知 ID；
     * 缺省的新卡片追加到末尾并默认显示；强制 alwaysVisible 卡片可见。
     */
    fun mergeWithDefaults(saved: List<CardPref<T>>?): List<CardPref<T>> {
        if (saved.isNullOrEmpty()) return defaults
        val known = linkedMapOf<T, Boolean>()
        for (pref in saved) {
            if (pref.id in values) {
                known[pref.id] = if (pref.id.alwaysVisible) true else pref.visible
            }
        }
        val ordered = known.map { (id, visible) -> CardPref(id, visible) }.toMutableList()
        for (id in values) {
            if (id !in known) {
                ordered += CardPref(id, visible = true)
            }
        }
        return ordered.map {
            if (it.id.alwaysVisible) it.copy(visible = true) else it
        }
    }

    /** 格式：`ALERTS:1,CURRENT:1,LIFE_INDICES:0` */
    fun encode(prefs: List<CardPref<T>>): String =
        mergeWithDefaults(prefs).joinToString(",") { pref ->
            "${enumName(pref.id)}:${if (pref.visible) 1 else 0}"
        }

    fun decode(raw: String?): List<CardPref<T>> {
        if (raw.isNullOrBlank()) return defaults
        val parsed =
            raw
                .split(',')
                .mapNotNull { token ->
                    val parts = token.trim().split(':')
                    if (parts.size != 2) return@mapNotNull null
                    val id = values.firstOrNull { enumName(it) == parts[0] } ?: return@mapNotNull null
                    val visible = parts[1] == "1" || parts[1].equals("true", ignoreCase = true)
                    CardPref(id, visible)
                }
        return mergeWithDefaults(parsed)
    }

    fun withVisibility(
        prefs: List<CardPref<T>>,
        id: T,
        visible: Boolean,
    ): List<CardPref<T>> {
        if (id.alwaysVisible) return mergeWithDefaults(prefs)
        return mergeWithDefaults(
            prefs.map { if (it.id == id) it.copy(visible = visible) else it },
        )
    }

    fun move(
        prefs: List<CardPref<T>>,
        fromIndex: Int,
        toIndex: Int,
    ): List<CardPref<T>> {
        val list = mergeWithDefaults(prefs).toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
            return list
        }
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        return list
    }

    private fun enumName(id: T): String = (id as Enum<*>).name
}
