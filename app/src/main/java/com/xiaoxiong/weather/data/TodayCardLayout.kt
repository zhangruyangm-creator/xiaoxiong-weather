package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/** 今天页可配置模块。 */
enum class TodayCardId(@param:StringRes val titleRes: Int) {
    ALERTS(R.string.today_card_alerts),
    CURRENT(R.string.today_card_current),
    LIFE_INDICES(R.string.today_card_life),
    AIR_QUALITY(R.string.today_card_air),
    RADAR(R.string.today_card_radar),
    SUN_EXTRAS(R.string.today_card_sun);

    /** 实况始终展示，不可关闭。 */
    val alwaysVisible: Boolean get() = this == CURRENT
}

data class TodayCardPref(
    val id: TodayCardId,
    val visible: Boolean
)

object TodayCardLayout {
    val DEFAULT: List<TodayCardPref> = TodayCardId.entries.map {
        TodayCardPref(id = it, visible = true)
    }

    /**
     * 与默认表合并：保留已保存顺序与显隐；丢弃未知 ID；
     * 缺省的新卡片追加到末尾并默认显示；强制 CURRENT 可见。
     */
    fun mergeWithDefaults(saved: List<TodayCardPref>?): List<TodayCardPref> {
        if (saved.isNullOrEmpty()) return DEFAULT
        val known = linkedMapOf<TodayCardId, Boolean>()
        for (pref in saved) {
            if (pref.id in TodayCardId.entries.toSet()) {
                known[pref.id] = if (pref.id.alwaysVisible) true else pref.visible
            }
        }
        val ordered = known.map { (id, visible) -> TodayCardPref(id, visible) }.toMutableList()
        for (id in TodayCardId.entries) {
            if (id !in known) {
                ordered += TodayCardPref(id, visible = true)
            }
        }
        return ordered.map {
            if (it.id.alwaysVisible) it.copy(visible = true) else it
        }
    }

    /** 格式：`ALERTS:1,CURRENT:1,LIFE_INDICES:0` */
    fun encode(prefs: List<TodayCardPref>): String =
        mergeWithDefaults(prefs).joinToString(",") { pref ->
            "${pref.id.name}:${if (pref.visible) 1 else 0}"
        }

    fun decode(raw: String?): List<TodayCardPref> {
        if (raw.isNullOrBlank()) return DEFAULT
        val parsed = raw.split(',')
            .mapNotNull { token ->
                val parts = token.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = runCatching { TodayCardId.valueOf(parts[0]) }.getOrNull()
                    ?: return@mapNotNull null
                val visible = parts[1] == "1" || parts[1].equals("true", ignoreCase = true)
                TodayCardPref(id, visible)
            }
        return mergeWithDefaults(parsed)
    }

    fun withVisibility(prefs: List<TodayCardPref>, id: TodayCardId, visible: Boolean): List<TodayCardPref> {
        if (id.alwaysVisible) return mergeWithDefaults(prefs)
        return mergeWithDefaults(
            prefs.map { if (it.id == id) it.copy(visible = visible) else it }
        )
    }

    fun move(prefs: List<TodayCardPref>, fromIndex: Int, toIndex: Int): List<TodayCardPref> {
        val list = mergeWithDefaults(prefs).toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
            return list
        }
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        return list
    }
}
