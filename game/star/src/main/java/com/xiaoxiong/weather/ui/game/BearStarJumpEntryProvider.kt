package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.game.GameEntryProvider

/** ServiceLoader 提供者：装配方据此发现 [BearStarJumpEntry]。 */
class BearStarJumpEntryProvider : GameEntryProvider {
    override val entry: GameEntry = BearStarJumpEntry
}
