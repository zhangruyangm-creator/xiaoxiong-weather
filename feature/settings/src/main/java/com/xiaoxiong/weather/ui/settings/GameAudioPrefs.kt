package com.xiaoxiong.weather.ui.settings

import android.content.Context
import com.xiaoxiong.weather.game.GameAudioPrefsContract
import com.xiaoxiong.weather.game.GameAudioSettings

/**
 * 设置页对「游戏音效」偏好的读写入口。
 *
 * 文件名 / 键名 / 取值范围 / 默认值 / 旧键迁移的唯一实现在 `:game:api` 的
 * [GameAudioPrefsContract]，游戏实现侧（`game/common` 的 `GameAudio`）与本设置页
 * 读写同一份契约，不再各自维护副本；lite 版只引入契约层，不打包音效资源。
 */
object GameAudioPrefs {
    /** 未读取到偏好时的快照（enabled=true、默认音量），供设置页初始状态使用。 */
    val default: GameAudioSettings = GameAudioPrefsContract.default

    /** 把任意音量收敛到契约范围，NaN 视为 0。 */
    fun coerceVolume(volume: Float): Float = GameAudioPrefsContract.coerceVolume(volume)

    /** 读取偏好；请在 IO 线程调用（首次会触发 prefs 文件加载与一次旧键迁移写盘）。 */
    fun read(context: Context): GameAudioSettings = GameAudioPrefsContract.read(context)

    /** 仅由用户操作触发写入。 */
    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        GameAudioPrefsContract.setEnabled(context, enabled)
    }

    /** 仅由用户操作触发写入。 */
    fun setVolume(
        context: Context,
        volume: Float,
    ) {
        GameAudioPrefsContract.setVolume(context, volume)
    }
}
