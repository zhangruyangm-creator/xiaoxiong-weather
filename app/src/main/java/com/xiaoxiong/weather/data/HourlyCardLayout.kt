package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/** 小时页可配置模块。 */
enum class HourlyCardId(
    @param:StringRes override val titleRes: Int,
) : CardId {
    PRECIP(R.string.hourly_card_precip),
    STRIP(R.string.hourly_card_strip),
    CHART(R.string.hourly_card_chart),
    ;

    /** 小时走势图始终展示，不可关闭。 */
    override val alwaysVisible: Boolean get() = this == CHART
}

/** 小时页卡片显隐偏好（泛型核心见 [CardLayoutModel]）。 */
typealias HourlyCardPref = CardPref<HourlyCardId>

/** 小时页卡片布局：默认表、合并、编解码、显隐、拖动均由 [CardLayoutModel] 提供。 */
object HourlyCardLayout : CardLayoutModel<HourlyCardId>(HourlyCardId.entries.toTypedArray())
