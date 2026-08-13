package com.xiaoxiong.weather.ui.game

import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R

/**
 * 农场插画资源：土壤 / 生长阶段 / 杂草虫灾覆盖物 / 成熟作物。
 * 阶段图共享；成熟优先按作物 id，再按分类兜底。
 */
internal object FarmCropArt {
    @DrawableRes
    fun soil(dry: Boolean): Int = if (dry) R.drawable.farm_soil_dry else R.drawable.farm_soil_moist

    /** stage: 1 种子 / 2 发芽 / 3 成长 / 5 枯萎；成熟走 [ripe]。 */
    @DrawableRes
    fun stage(stage: Int): Int? =
        when (stage) {
            1 -> R.drawable.farm_stage_seed
            2 -> R.drawable.farm_stage_sprout
            3 -> R.drawable.farm_stage_grow
            5 -> R.drawable.farm_stage_wilt
            else -> null
        }

    @DrawableRes
    fun ripe(crop: CropDef): Int = RIPE_BY_ID[crop.id] ?: ripeByCategory(crop.category)

    @DrawableRes
    fun weed(): Int = R.drawable.farm_overlay_weed

    @DrawableRes
    fun bug(): Int = R.drawable.farm_overlay_bug

    @DrawableRes
    private fun ripeByCategory(category: String): Int =
        when (category) {
            "花卉" -> R.drawable.farm_ripe_flower
            "水果" -> R.drawable.farm_ripe_fruit
            "谷物" -> R.drawable.farm_ripe_wheat
            "香草" -> R.drawable.farm_ripe_herb
            "菇菌" -> R.drawable.farm_ripe_mushroom
            "蔬菜" -> R.drawable.farm_ripe_veg
            else -> R.drawable.farm_ripe_veg
        }

    private val RIPE_BY_ID: Map<String, Int> =
        mapOf(
            // 蔬菜
            "radish" to R.drawable.farm_ripe_radish,
            "cabbage" to R.drawable.farm_ripe_cabbage,
            "tomato" to R.drawable.farm_ripe_tomato,
            "cucumber" to R.drawable.farm_ripe_cucumber,
            "carrot" to R.drawable.farm_ripe_carrot,
            "lettuce" to R.drawable.farm_ripe_lettuce,
            "potato" to R.drawable.farm_ripe_potato,
            "onion" to R.drawable.farm_ripe_onion,
            "garlic" to R.drawable.farm_ripe_garlic,
            "chili" to R.drawable.farm_ripe_chili,
            "eggplant" to R.drawable.farm_ripe_eggplant,
            "spinach" to R.drawable.farm_ripe_spinach,
            "celery" to R.drawable.farm_ripe_celery,
            "pea" to R.drawable.farm_ripe_pea,
            "bean" to R.drawable.farm_ripe_bean,
            "broccoli" to R.drawable.farm_ripe_broccoli,
            "squash" to R.drawable.farm_ripe_squash,
            "pepper_bell" to R.drawable.farm_ripe_pepper_bell,
            "cauliflower" to R.drawable.farm_ripe_cauliflower,
            "beet" to R.drawable.farm_ripe_beet,
            "turnip" to R.drawable.farm_ripe_turnip,
            "asparagus" to R.drawable.farm_ripe_asparagus,
            "yam" to R.drawable.farm_ripe_yam,
            "taro" to R.drawable.farm_ripe_taro,
            "lotus_root" to R.drawable.farm_ripe_lotus_root,
            // 花卉
            "dandelion" to R.drawable.farm_ripe_dandelion,
            "clover" to R.drawable.farm_ripe_clover,
            "daisy" to R.drawable.farm_ripe_daisy,
            "marigold" to R.drawable.farm_ripe_marigold,
            "cosmos" to R.drawable.farm_ripe_cosmos,
            "forget_me_not" to R.drawable.farm_ripe_forget_me_not,
            "bluebell" to R.drawable.farm_ripe_bluebell,
            "violet" to R.drawable.farm_ripe_violet,
            "sunflower" to R.drawable.farm_ripe_sunflower,
            "chrysanthemum" to R.drawable.farm_ripe_chrysanthemum,
            "jasmine" to R.drawable.farm_ripe_jasmine,
            "narcissus" to R.drawable.farm_ripe_narcissus,
            "gardenia" to R.drawable.farm_ripe_gardenia,
            "hyacinth" to R.drawable.farm_ripe_hyacinth,
            "lavender" to R.drawable.farm_ripe_lavender,
            "azalea" to R.drawable.farm_ripe_cherry_blossom,
            "carnation" to R.drawable.farm_ripe_rose,
            "poppy" to R.drawable.farm_ripe_poppy,
            "tulip" to R.drawable.farm_ripe_tulip,
            "lily" to R.drawable.farm_ripe_lily,
            "iris" to R.drawable.farm_ripe_iris,
            "osmanthus" to R.drawable.farm_ripe_marigold,
            "hibiscus" to R.drawable.farm_ripe_hibiscus,
            "hydrangea" to R.drawable.farm_ripe_hydrangea,
            "blossom" to R.drawable.farm_ripe_cherry_blossom,
            "camellia" to R.drawable.farm_ripe_camellia,
            "lotus" to R.drawable.farm_ripe_lotus,
            "rose" to R.drawable.farm_ripe_rose,
            "magnolia" to R.drawable.farm_ripe_magnolia,
            "cherry_blossom" to R.drawable.farm_ripe_cherry_blossom,
            "orchid" to R.drawable.farm_ripe_orchid,
            "peony" to R.drawable.farm_ripe_peony,
            "four_leaf" to R.drawable.farm_ripe_four_leaf,
            "lilac" to R.drawable.farm_ripe_lilac,
            "begonia" to R.drawable.farm_ripe_hibiscus,
            "impatiens" to R.drawable.farm_ripe_pansy,
            "zinnia" to R.drawable.farm_ripe_marigold,
            "petunia" to R.drawable.farm_ripe_morning_glory,
            "pansy" to R.drawable.farm_ripe_pansy,
            "snapdragon" to R.drawable.farm_ripe_tulip,
            "morning_glory" to R.drawable.farm_ripe_morning_glory,
            "wisteria" to R.drawable.farm_ripe_wisteria,
            "plumeria" to R.drawable.farm_ripe_gardenia,
            "bird_of_paradise" to R.drawable.farm_ripe_orchid,
            "calla" to R.drawable.farm_ripe_calla,
            "anthurium" to R.drawable.farm_ripe_anthurium,
            "protea" to R.drawable.farm_ripe_peony,
            // 谷物
            "wheat" to R.drawable.farm_ripe_wheat,
            "corn" to R.drawable.farm_ripe_corn,
            "rice" to R.drawable.farm_ripe_rice,
            "barley" to R.drawable.farm_ripe_wheat,
            "oat" to R.drawable.farm_ripe_wheat,
            "sorghum" to R.drawable.farm_ripe_sorghum,
            "millet" to R.drawable.farm_ripe_millet,
            "buckwheat" to R.drawable.farm_ripe_buckwheat,
            // 水果
            "strawberry" to R.drawable.farm_ripe_strawberry,
            "watermelon" to R.drawable.farm_ripe_watermelon,
            "pumpkin" to R.drawable.farm_ripe_pumpkin,
            "grape" to R.drawable.farm_ripe_grape,
            "apple" to R.drawable.farm_ripe_apple,
            "pear" to R.drawable.farm_ripe_pear,
            "peach" to R.drawable.farm_ripe_peach,
            "cherry" to R.drawable.farm_ripe_cherry,
            "banana" to R.drawable.farm_ripe_banana,
            "orange" to R.drawable.farm_ripe_orange,
            "lemon" to R.drawable.farm_ripe_lemon,
            "mango" to R.drawable.farm_ripe_mango,
            "pineapple" to R.drawable.farm_ripe_pineapple,
            "kiwi" to R.drawable.farm_ripe_kiwi,
            "blueberry" to R.drawable.farm_ripe_blueberry,
            "raspberry" to R.drawable.farm_ripe_raspberry,
            "melon" to R.drawable.farm_ripe_melon,
            "coconut" to R.drawable.farm_ripe_coconut,
            "pomegranate" to R.drawable.farm_ripe_pomegranate,
            "fig" to R.drawable.farm_ripe_fig,
            "lychee" to R.drawable.farm_ripe_lychee,
            "longan" to R.drawable.farm_ripe_longan,
            "persimmon" to R.drawable.farm_ripe_persimmon,
            "plum" to R.drawable.farm_ripe_plum,
            // 香草
            "mint" to R.drawable.farm_ripe_mint,
            "basil" to R.drawable.farm_ripe_basil,
            "rosemary" to R.drawable.farm_ripe_herb,
            "thyme" to R.drawable.farm_ripe_herb,
            "sage" to R.drawable.farm_ripe_herb,
            "cilantro" to R.drawable.farm_ripe_herb,
            "parsley" to R.drawable.farm_ripe_herb,
            "ginger" to R.drawable.farm_ripe_ginger,
            "tea" to R.drawable.farm_ripe_tea,
            "coffee" to R.drawable.farm_ripe_coffee,
            "cocoa" to R.drawable.farm_ripe_cocoa,
            // 菇菌 / 其他
            "mushroom" to R.drawable.farm_ripe_mushroom,
            "shiitake" to R.drawable.farm_ripe_mushroom,
            "enoki" to R.drawable.farm_ripe_enoki,
            "bamboo" to R.drawable.farm_ripe_bamboo,
            "cotton" to R.drawable.farm_ripe_cotton,
            "sugarcane" to R.drawable.farm_ripe_sugarcane,
            "peanut" to R.drawable.farm_ripe_peanut,
            "soybean" to R.drawable.farm_ripe_soybean,
            "sesame" to R.drawable.farm_ripe_sesame,
            "aloe" to R.drawable.farm_ripe_aloe,
            "cactus" to R.drawable.farm_ripe_cactus,
            "fern" to R.drawable.farm_ripe_fern,
            "moss" to R.drawable.farm_ripe_moss,
        )
}
