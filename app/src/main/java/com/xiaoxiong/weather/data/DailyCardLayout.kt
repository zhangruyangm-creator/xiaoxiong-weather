package com.xiaoxiong.weather.data

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/** 七日页可配置模块。 */
enum class DailyCardId(
    @param:StringRes override val titleRes: Int,
) : CardId {
    FORECAST(R.string.daily_card_forecast),
    SEASON_TIPS(R.string.daily_card_season),
    HISTORY(R.string.daily_card_history),
    ;

    /** 七日预报始终展示，不可关闭。 */
    override val alwaysVisible: Boolean get() = this == FORECAST
}

/** 七日页卡片显隐偏好（泛型核心见 [CardLayoutModel]）。 */
typealias DailyCardPref = CardPref<DailyCardId>

/** 七日页卡片布局：默认表、合并、编解码、显隐、拖动均由 [CardLayoutModel] 提供。 */
object DailyCardLayout : CardLayoutModel<DailyCardId>(DailyCardId.entries.toTypedArray())
