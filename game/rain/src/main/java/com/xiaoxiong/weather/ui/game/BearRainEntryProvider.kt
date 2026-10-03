package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.game.GameEntryProvider

/** ServiceLoader 提供者：装配方据此发现 [BearRainEntry]。必须是普通 class（不可用 object）。 */
class BearRainEntryProvider : GameEntryProvider {
    override val entry: GameEntry = BearRainEntry
}
