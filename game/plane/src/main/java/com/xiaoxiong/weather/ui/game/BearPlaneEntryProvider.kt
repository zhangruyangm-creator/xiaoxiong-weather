package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.game.GameEntryProvider

/** ServiceLoader 提供者：装配方据此发现 [BearPlaneEntry]。必须是普通 class（不可用 object）。 */
class BearPlaneEntryProvider : GameEntryProvider {
    override val entry: GameEntry = BearPlaneEntry
}
