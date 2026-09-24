package com.xiaoxiong.weather.ui.game

import androidx.annotation.DrawableRes
import com.xiaoxiong.weather.R

/**
 * 农场插画资源：土壤 / 生长阶段 / 杂草虫灾覆盖物 / 成熟作物。
 *
 * - 种子 / 枯萎：全作物共用
 * - 发芽 / 成长：按作物分类 + 2–3 种生长子形态；蔬菜再细分为根菜 / 藤蔓果菜 / 叶菜
 * - 成熟：按作物 id 一对一映射，缺失时按分类兜底
 */
internal object FarmCropArt {
    @DrawableRes
    fun soil(dry: Boolean): Int = if (dry) R.drawable.farm_soil_dry else R.drawable.farm_soil_moist

    /**
     * stage: 1 种子 / 2 发芽 / 3 成长 / 5 枯萎；成熟走 [ripe]。
     * [crop] 用于发芽/成长阶段按分类（及蔬菜亚型）选图；为空时回退叶菜通用图。
     */
    @DrawableRes
    fun stage(
        stage: Int,
        crop: CropDef? = null,
    ): Int? =
        when (stage) {
            1 -> R.drawable.farm_stage_seed
            2 -> sproutOf(crop)
            3 -> growOf(crop)
            5 -> R.drawable.farm_stage_wilt
            else -> null
        }

    @DrawableRes
    fun ripe(crop: CropDef): Int = RIPE_BY_ID[crop.id] ?: ripeByCategory(crop.category)

    @DrawableRes
    fun weed(): Int = R.drawable.farm_overlay_weed

    @DrawableRes
    fun bug(): Int = R.drawable.farm_overlay_bug

    /** 蔬菜生长形态：根菜 / 藤蔓果菜 / 叶菜。 */
    private enum class VegMorph { ROOT, VINE, LEAFY }

    /** 花卉：单茎 / 灌木 / 藤本。 */
    private enum class FlowerMorph { STEM, SHRUB, VINE }

    /** 水果：乔木 / 藤本 / 灌木（含地被浆果）。 */
    private enum class FruitMorph { TREE, VINE, BUSH }

    /** 谷物：麦类穗 / 散穗 / 高秆（玉米）。 */
    private enum class GrainMorph { SPIKE, PANICLE, STALK }

    /** 香草：叶丛 / 木本（茶、咖啡等）。 */
    private enum class HerbMorph { LEAFY, WOODY }

    /** 菇菌：伞柄 / 束生。 */
    private enum class MushroomMorph { CAP, CLUSTER }

    /** 其他：高秆 / 地表 / 多肉。 */
    private enum class OtherMorph { CANE, GROUND, SUCCULENT }

    @DrawableRes
    private fun sproutOf(crop: CropDef?): Int {
        val cat = crop?.category
        return when (cat) {
            CropCategory.FLOWER ->
                when (flowerMorph(crop?.id)) {
                    FlowerMorph.STEM -> R.drawable.farm_stage_sprout_flower
                    FlowerMorph.SHRUB -> R.drawable.farm_stage_sprout_flower_shrub
                    FlowerMorph.VINE -> R.drawable.farm_stage_sprout_flower_vine
                }
            CropCategory.GRAIN ->
                when (grainMorph(crop?.id)) {
                    GrainMorph.SPIKE -> R.drawable.farm_stage_sprout_grain
                    GrainMorph.PANICLE -> R.drawable.farm_stage_sprout_grain_panicle
                    GrainMorph.STALK -> R.drawable.farm_stage_sprout_grain_stalk
                }
            CropCategory.FRUIT ->
                when (fruitMorph(crop?.id)) {
                    FruitMorph.TREE -> R.drawable.farm_stage_sprout_fruit_tree
                    FruitMorph.VINE -> R.drawable.farm_stage_sprout_fruit_vine
                    FruitMorph.BUSH -> R.drawable.farm_stage_sprout_fruit_bush
                }
            CropCategory.HERB ->
                when (herbMorph(crop?.id)) {
                    HerbMorph.LEAFY -> R.drawable.farm_stage_sprout_herb
                    HerbMorph.WOODY -> R.drawable.farm_stage_sprout_herb_woody
                }
            CropCategory.MUSHROOM ->
                when (mushroomMorph(crop?.id)) {
                    MushroomMorph.CAP -> R.drawable.farm_stage_sprout_mushroom
                    MushroomMorph.CLUSTER -> R.drawable.farm_stage_sprout_mushroom_cluster
                }
            CropCategory.OTHER ->
                when (otherMorph(crop?.id)) {
                    OtherMorph.CANE -> R.drawable.farm_stage_sprout_other_cane
                    OtherMorph.GROUND -> R.drawable.farm_stage_sprout_other
                    OtherMorph.SUCCULENT -> R.drawable.farm_stage_sprout_other_succulent
                }
            CropCategory.VEGETABLE, null ->
                when (vegMorph(crop?.id)) {
                    VegMorph.ROOT -> R.drawable.farm_stage_sprout_veg_root
                    VegMorph.VINE -> R.drawable.farm_stage_sprout_veg_vine
                    VegMorph.LEAFY -> R.drawable.farm_stage_sprout
                }
            else -> R.drawable.farm_stage_sprout
        }
    }

    @DrawableRes
    private fun growOf(crop: CropDef?): Int {
        val cat = crop?.category
        return when (cat) {
            CropCategory.FLOWER ->
                when (flowerMorph(crop?.id)) {
                    FlowerMorph.STEM -> R.drawable.farm_stage_grow_flower
                    FlowerMorph.SHRUB -> R.drawable.farm_stage_grow_flower_shrub
                    FlowerMorph.VINE -> R.drawable.farm_stage_grow_flower_vine
                }
            CropCategory.GRAIN ->
                when (grainMorph(crop?.id)) {
                    GrainMorph.SPIKE -> R.drawable.farm_stage_grow_grain
                    GrainMorph.PANICLE -> R.drawable.farm_stage_grow_grain_panicle
                    GrainMorph.STALK -> R.drawable.farm_stage_grow_grain_stalk
                }
            CropCategory.FRUIT ->
                when (fruitMorph(crop?.id)) {
                    FruitMorph.TREE -> R.drawable.farm_stage_grow_fruit_tree
                    FruitMorph.VINE -> R.drawable.farm_stage_grow_fruit_vine
                    FruitMorph.BUSH -> R.drawable.farm_stage_grow_fruit_bush
                }
            CropCategory.HERB ->
                when (herbMorph(crop?.id)) {
                    HerbMorph.LEAFY -> R.drawable.farm_stage_grow_herb
                    HerbMorph.WOODY -> R.drawable.farm_stage_grow_herb_woody
                }
            CropCategory.MUSHROOM ->
                when (mushroomMorph(crop?.id)) {
                    MushroomMorph.CAP -> R.drawable.farm_stage_grow_mushroom
                    MushroomMorph.CLUSTER -> R.drawable.farm_stage_grow_mushroom_cluster
                }
            CropCategory.OTHER ->
                when (otherMorph(crop?.id)) {
                    OtherMorph.CANE -> R.drawable.farm_stage_grow_other_cane
                    OtherMorph.GROUND -> R.drawable.farm_stage_grow_other
                    OtherMorph.SUCCULENT -> R.drawable.farm_stage_grow_other_succulent
                }
            CropCategory.VEGETABLE, null ->
                when (vegMorph(crop?.id)) {
                    VegMorph.ROOT -> R.drawable.farm_stage_grow_veg_root
                    VegMorph.VINE -> R.drawable.farm_stage_grow_veg_vine
                    VegMorph.LEAFY -> R.drawable.farm_stage_grow
                }
            else -> R.drawable.farm_stage_grow
        }
    }

    private fun flowerMorph(id: String?): FlowerMorph =
        when (id) {
            "morning_glory", "wisteria" -> FlowerMorph.VINE
            "clover", "marigold", "cosmos", "violet", "chrysanthemum", "jasmine",
            "gardenia", "lavender", "azalea", "carnation", "osmanthus", "hibiscus",
            "hydrangea", "blossom", "camellia", "rose", "magnolia", "cherry_blossom",
            "orchid", "peony", "four_leaf", "lilac", "begonia", "impatiens", "zinnia",
            "petunia", "pansy",
            -> FlowerMorph.SHRUB
            else -> FlowerMorph.STEM
        }

    private fun fruitMorph(id: String?): FruitMorph =
        when (id) {
            "grape", "watermelon", "melon", "pumpkin" -> FruitMorph.VINE
            "strawberry", "blueberry", "raspberry", "pineapple" -> FruitMorph.BUSH
            else -> FruitMorph.TREE
        }

    private fun grainMorph(id: String?): GrainMorph =
        when (id) {
            "rice", "millet", "sorghum" -> GrainMorph.PANICLE
            "corn" -> GrainMorph.STALK
            else -> GrainMorph.SPIKE
        }

    private fun herbMorph(id: String?): HerbMorph =
        when (id) {
            "ginger", "tea", "coffee", "cocoa" -> HerbMorph.WOODY
            else -> HerbMorph.LEAFY
        }

    private fun mushroomMorph(id: String?): MushroomMorph =
        when (id) {
            "enoki" -> MushroomMorph.CLUSTER
            else -> MushroomMorph.CAP
        }

    private fun otherMorph(id: String?): OtherMorph =
        when (id) {
            "bamboo", "sugarcane" -> OtherMorph.CANE
            "aloe", "cactus" -> OtherMorph.SUCCULENT
            else -> OtherMorph.GROUND
        }

    private fun vegMorph(id: String?): VegMorph =
        when (id) {
            // 根菜：地下块根/鳞茎，地上为羽状或管状叶
            "radish", "carrot", "potato", "onion", "garlic",
            "beet", "turnip", "yam", "taro", "lotus_root",
            -> VegMorph.ROOT
            // 藤蔓/果菜：攀缘或灌丛，结小果
            "tomato", "cucumber", "chili", "eggplant",
            "pea", "bean", "squash", "pepper_bell",
            -> VegMorph.VINE
            // 叶菜：白菜/生菜/花椰菜/芦笋等
            else -> VegMorph.LEAFY
        }

    @DrawableRes
    private fun ripeByCategory(category: CropCategory): Int =
        when (category) {
            CropCategory.FLOWER -> R.drawable.farm_ripe_flower
            CropCategory.FRUIT -> R.drawable.farm_ripe_fruit
            CropCategory.GRAIN -> R.drawable.farm_ripe_wheat
            CropCategory.HERB -> R.drawable.farm_ripe_herb
            CropCategory.MUSHROOM -> R.drawable.farm_ripe_mushroom
            CropCategory.VEGETABLE -> R.drawable.farm_ripe_veg
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
            "azalea" to R.drawable.farm_ripe_azalea,
            "carnation" to R.drawable.farm_ripe_carnation,
            "poppy" to R.drawable.farm_ripe_poppy,
            "tulip" to R.drawable.farm_ripe_tulip,
            "lily" to R.drawable.farm_ripe_lily,
            "iris" to R.drawable.farm_ripe_iris,
            "osmanthus" to R.drawable.farm_ripe_osmanthus,
            "hibiscus" to R.drawable.farm_ripe_hibiscus,
            "hydrangea" to R.drawable.farm_ripe_hydrangea,
            "blossom" to R.drawable.farm_ripe_blossom,
            "camellia" to R.drawable.farm_ripe_camellia,
            "lotus" to R.drawable.farm_ripe_lotus,
            "rose" to R.drawable.farm_ripe_rose,
            "magnolia" to R.drawable.farm_ripe_magnolia,
            "cherry_blossom" to R.drawable.farm_ripe_cherry_blossom,
            "orchid" to R.drawable.farm_ripe_orchid,
            "peony" to R.drawable.farm_ripe_peony,
            "four_leaf" to R.drawable.farm_ripe_four_leaf,
            "lilac" to R.drawable.farm_ripe_lilac,
            "begonia" to R.drawable.farm_ripe_begonia,
            "impatiens" to R.drawable.farm_ripe_impatiens,
            "zinnia" to R.drawable.farm_ripe_zinnia,
            "petunia" to R.drawable.farm_ripe_petunia,
            "pansy" to R.drawable.farm_ripe_pansy,
            "snapdragon" to R.drawable.farm_ripe_snapdragon,
            "morning_glory" to R.drawable.farm_ripe_morning_glory,
            "wisteria" to R.drawable.farm_ripe_wisteria,
            "plumeria" to R.drawable.farm_ripe_plumeria,
            "bird_of_paradise" to R.drawable.farm_ripe_bird_of_paradise,
            "calla" to R.drawable.farm_ripe_calla,
            "anthurium" to R.drawable.farm_ripe_anthurium,
            "protea" to R.drawable.farm_ripe_protea,
            // 谷物
            "wheat" to R.drawable.farm_ripe_wheat,
            "corn" to R.drawable.farm_ripe_corn,
            "rice" to R.drawable.farm_ripe_rice,
            "barley" to R.drawable.farm_ripe_barley,
            "oat" to R.drawable.farm_ripe_oat,
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
            "rosemary" to R.drawable.farm_ripe_rosemary,
            "thyme" to R.drawable.farm_ripe_thyme,
            "sage" to R.drawable.farm_ripe_sage,
            "cilantro" to R.drawable.farm_ripe_cilantro,
            "parsley" to R.drawable.farm_ripe_parsley,
            "ginger" to R.drawable.farm_ripe_ginger,
            "tea" to R.drawable.farm_ripe_tea,
            "coffee" to R.drawable.farm_ripe_coffee,
            "cocoa" to R.drawable.farm_ripe_cocoa,
            // 菇菌 / 其他
            "mushroom" to R.drawable.farm_ripe_mushroom,
            "shiitake" to R.drawable.farm_ripe_shiitake,
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
