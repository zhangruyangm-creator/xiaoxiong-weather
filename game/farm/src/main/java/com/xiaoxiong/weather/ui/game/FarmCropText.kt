package com.xiaoxiong.weather.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.xiaoxiong.weather.R
import java.util.Locale

/**
 * 作物分类。
 *
 * 此前分类直接用中文字符串，既当显示文案又当玩法键（`preferredWeatherOf` 用
 * `"蔬菜"` 之类的中文字面量匹配），一旦本地化就会连带改坏玩法。这里拆成枚举：
 * 玩法只认枚举，显示走 [labelRes]。
 */
enum class CropCategory {
    VEGETABLE,
    GRAIN,
    FRUIT,
    FLOWER,
    HERB,
    MUSHROOM,
    OTHER,
}

/** 分类的本地化标签（与冰球场地主题 id 一样走稳定 id，不因语言变化）。 */
fun CropCategory.labelRes(): Int =
    when (this) {
        CropCategory.VEGETABLE -> R.string.farm_cat_vegetable
        CropCategory.GRAIN -> R.string.farm_cat_grain
        CropCategory.FRUIT -> R.string.farm_cat_fruit
        CropCategory.FLOWER -> R.string.farm_cat_flower
        CropCategory.HERB -> R.string.farm_cat_herb
        CropCategory.MUSHROOM -> R.string.farm_cat_mushroom
        CropCategory.OTHER -> R.string.farm_cat_other
    }

/** 分类的喜好天气：喜欢阴天/雨天等特定天气的作物长得更好。 */
fun CropCategory.preferredWeather(): FarmWeather? =
    when (this) {
        CropCategory.GRAIN, CropCategory.HERB -> FarmWeather.RAINY
        CropCategory.FRUIT, CropCategory.VEGETABLE -> FarmWeather.SUNNY
        CropCategory.FLOWER -> FarmWeather.CLOUDY
        CropCategory.MUSHROOM -> FarmWeather.FOGGY
        CropCategory.OTHER -> null
    }

/** 中文名直接从 [CropDef.nameZh] 派生，不另抄一份，避免两张表漂移。 */
private val CROP_NAMES_ZH: Map<String, String> = FARM_CROPS.associate { it.id to it.nameZh }

/** 语言判定：非中文一律用英文表（表里没有的条目回退中文，保证不空）。 */
private fun localeStartsChinese(locale: Locale): Boolean = locale.language == "zh"

/**
 * 取作物展示名。
 *
 * 此前是硬编码的 `nameZh` 字段，英文环境仍显示中文；现在按当前语言取，
 * 缺失时回退中文（宁可中文也不要空白）。
 */
fun cropDisplayName(
    crop: CropDef,
    locale: Locale = Locale.getDefault(),
): String {
    val table = if (localeStartsChinese(locale)) CROP_NAMES_ZH else CROP_NAMES_EN
    return table[crop.id] ?: CROP_NAMES_ZH[crop.id] ?: crop.id
}

/** 取作物一句话介绍（图鉴/解锁弹卡）。缺失时返回空串，调用方已有 isNotBlank 保护。 */
fun cropDisplayDescription(
    crop: CropDef,
    locale: Locale = Locale.getDefault(),
): String {
    val table =
        if (localeStartsChinese(locale)) {
            FARM_CROP_DESCRIPTIONS_ZH
        } else {
            FARM_CROP_DESCRIPTIONS_EN
        }
    return table[crop.id] ?: FARM_CROP_DESCRIPTIONS_ZH[crop.id].orEmpty()
}

/** 当前界面语言的作物名（Compose 侧入口，随配置变化重算）。 */
@Composable
fun cropName(crop: CropDef): String {
    val locale = currentLocale()
    return cropDisplayName(crop, locale)
}

/** 当前界面语言的作物介绍。 */
@Composable
fun cropDescription(crop: CropDef): String {
    val locale = currentLocale()
    return cropDisplayDescription(crop, locale)
}

/** 读当前配置语言；取不到时回退 [Locale.getDefault]。 */
@Composable
private fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0] ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        configuration.locale ?: Locale.getDefault()
    }
}
