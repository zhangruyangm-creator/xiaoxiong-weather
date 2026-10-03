package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/** 今天页可配置模块。 */
enum class TodayCardId(
    @param:StringRes override val titleRes: Int,
) : CardId {
    ALERTS(R.string.today_card_alerts),
    CURRENT(R.string.today_card_current),
    LIFE_INDICES(R.string.today_card_life),
    AIR_QUALITY(R.string.today_card_air),
    RADAR(R.string.today_card_radar),
    SUN_EXTRAS(R.string.today_card_sun),
    ;

    /** 实况始终展示，不可关闭。 */
    override val alwaysVisible: Boolean get() = this == CURRENT
}

/** 今天页卡片显隐偏好（泛型核心见 [CardLayoutModel]）。 */
typealias TodayCardPref = CardPref<TodayCardId>

/** 今天页卡片布局：默认表、合并、编解码、显隐、拖动均由 [CardLayoutModel] 提供。 */
object TodayCardLayout : CardLayoutModel<TodayCardId>(TodayCardId.entries.toTypedArray())
