// 需求指定文件名 HockeyRinkTheme.kt（单枚举文件），与 ktlint filename 规则冲突，按文件名保留
@file:Suppress("ktlint:standard:filename")

package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R

/**
 * 冰球场地主题：同一张球桌换成不同「场地」，几何完全一致，仅地面摩擦与静态外观不同。
 *
 * - [id] 用于 prefs / 快照持久化，**必须保持稳定**（不可因枚举顺序调整而改变语义）；
 * - [friction] 传给 [HockeyLogic.integratePuck]，越大球速衰减越快、手感越「涩」；
 * - [titleRes] 设置弹窗展示名，说明文案由 UI 层按主题映射。
 *
 * 顺序 = 摩擦从滑到涩：冰面最滑、小熊画布次之、木质球桌更阻、水泥地最涩。
 */
enum class RinkTheme(
    val id: String,
    val friction: Float,
    @param:StringRes val titleRes: Int,
) {
    /** 冰面：默认主题，现有基线手感。 */
    ICE("ice", 0.18f, R.string.bear_hockey_rink_ice),

    /** 小熊画布：奶油 / 冰蓝帆布与极淡水印，比冰面略阻。 */
    BEAR("bear", 0.22f, R.string.bear_hockey_rink_bear),

    /** 木质球桌：蜂蜜木纹 + 清漆，摩擦中等。 */
    WOOD("wood", 0.28f, R.string.bear_hockey_rink_wood),

    /** 水泥地：分格缝与裂纹，摩擦最大、球最快停下。 */
    CONCRETE("concrete", 0.38f, R.string.bear_hockey_rink_concrete),
    ;

    companion object {
        /** 默认主题（非法 / 缺省回退）。 */
        val DEFAULT: RinkTheme = ICE

        /** 按持久化 id 解析主题；null / 非法 id 一律回退 [ICE]（默认）。 */
        fun fromId(id: String?): RinkTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
