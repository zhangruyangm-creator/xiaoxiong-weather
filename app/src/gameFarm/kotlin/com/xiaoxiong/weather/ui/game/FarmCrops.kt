package com.xiaoxiong.weather.ui.game

import androidx.annotation.StringRes
import com.xiaoxiong.weather.R
import kotlinx.serialization.Serializable

data class FarmWeatherEffect(
    @param:StringRes val labelRes: Int,
    val growthMul: Float,
    val dryPerHour: Float,
    val rainPerHour: Float,
    val weedChancePerHour: Float,
    val bugChancePerHour: Float,
    val skyTop: Long,
    val skyBottom: Long,
    val soil: Long,
)

fun farmWeatherEffect(w: FarmWeather): FarmWeatherEffect =
    when (w) {
        FarmWeather.SUNNY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_sunny,
                growthMul = 1.25f,
                dryPerHour = 0.22f,
                rainPerHour = 0f,
                weedChancePerHour = 0.04f,
                bugChancePerHour = 0.05f,
                skyTop = 0xFF4FA3E0,
                skyBottom = 0xFFB8E4F8,
                soil = 0xFF8B5A2B,
            )
        FarmWeather.CLOUDY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_cloudy,
                growthMul = 1.0f,
                dryPerHour = 0.12f,
                rainPerHour = 0f,
                weedChancePerHour = 0.06f,
                bugChancePerHour = 0.06f,
                skyTop = 0xFF7A91A8,
                skyBottom = 0xFFC5D2DE,
                soil = 0xFF7A4E28,
            )
        FarmWeather.RAINY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_rainy,
                growthMul = 1.1f,
                dryPerHour = 0.02f,
                rainPerHour = 0.35f,
                weedChancePerHour = 0.14f,
                bugChancePerHour = 0.04f,
                skyTop = 0xFF5A6E82,
                skyBottom = 0xFF8FA3B5,
                soil = 0xFF5C3A1E,
            )
        FarmWeather.STORMY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_stormy,
                growthMul = 0.9f,
                dryPerHour = 0.03f,
                rainPerHour = 0.45f,
                weedChancePerHour = 0.10f,
                bugChancePerHour = 0.18f,
                skyTop = 0xFF2E3A4A,
                skyBottom = 0xFF5A6A7A,
                soil = 0xFF4A2E16,
            )
        FarmWeather.SNOWY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_snowy,
                growthMul = 0.45f,
                dryPerHour = 0.04f,
                rainPerHour = 0.05f,
                weedChancePerHour = 0.02f,
                bugChancePerHour = 0.02f,
                skyTop = 0xFF9EB6C8,
                skyBottom = 0xFFE8F2F8,
                soil = 0xFF6E5338,
            )
        FarmWeather.FOGGY ->
            FarmWeatherEffect(
                R.string.bear_farm_weather_foggy,
                growthMul = 0.75f,
                dryPerHour = 0.08f,
                rainPerHour = 0.08f,
                weedChancePerHour = 0.08f,
                bugChancePerHour = 0.07f,
                skyTop = 0xFF9AA8B0,
                skyBottom = 0xFFD0D8DC,
                soil = 0xFF6B4A2E,
            )
    }

enum class CropStage { EMPTY, SEED, SPROUT, GROWING, RIPE, WILTED }

/**
 * 作物定义。stageHours = 各阶段时长（小时）：种子→发芽、发芽→成长、成长→成熟。
 */
data class CropDef(
    val id: String,
    val nameZh: String,
    val emoji: String,
    val seedCost: Int,
    val sellPrice: Int,
    val stageHours: FloatArray,
    val color: Long,
    val leafColor: Long,
    val category: String = "蔬菜",
) {
    val totalHours: Float get() = stageHours.sum()
}

private fun crop(
    id: String,
    name: String,
    emoji: String,
    cost: Int,
    sell: Int,
    a: Float,
    b: Float,
    c: Float,
    color: Long,
    leaf: Long = 0xFF66BB6A,
    cat: String = "蔬菜",
) = CropDef(id, name, emoji, cost, sell, floatArrayOf(a, b, c), color, leaf, cat)

/**
 * 110 种作物：蔬菜 / 花卉 / 谷物 / 水果 / 香草 / 菇菌 / 其他。
 * 列表顺序即收获解锁顺序；花卉靠前，方便早点种到。
 * 时长单位：小时，偏休闲节奏；最低档约 7.5h（萝卜），中档约 13-20h，
 * 高档约 26-33h，最高的竹子/帝王花等约 37h，几乎都要过夜或好几天。
 */
val FARM_CROPS: List<CropDef> =
    listOf(
        // —— 蔬菜（起步）——
        crop("radish", "萝卜", "🥕", 8, 18, 2f, 2.5f, 3f, 0xFFF0F0F0, cat = "蔬菜"),
        crop("cabbage", "白菜", "🥬", 10, 22, 2.5f, 3.5f, 4f, 0xFFE8F5E9, cat = "蔬菜"),
        crop("wheat", "小麦", "🌾", 12, 28, 3f, 4.5f, 5.5f, 0xFFFFE082, 0xFFC0CA33, "谷物"),
        crop("tomato", "番茄", "🍅", 15, 36, 4f, 5f, 6f, 0xFFE53935, cat = "蔬菜"),
        crop("cucumber", "黄瓜", "🥒", 12, 30, 2.5f, 4f, 5f, 0xFF7CB342, 0xFF558B2F, "蔬菜"),
        crop("carrot", "胡萝卜", "🥕", 9, 20, 2f, 3f, 3.5f, 0xFFFF7043, cat = "蔬菜"),
        crop("lettuce", "生菜", "🥗", 9, 21, 2f, 3f, 3.5f, 0xFFC8E6C9, cat = "蔬菜"),
        crop("potato", "土豆", "🥔", 11, 26, 3f, 4f, 4.5f, 0xFFD7CCC8, 0xFF8D6E63, "蔬菜"),
        crop("onion", "洋葱", "🧅", 10, 24, 2.5f, 3.5f, 4f, 0xFFFFF3E0, 0xFFAED581, "蔬菜"),
        crop("garlic", "大蒜", "🧄", 11, 25, 2.5f, 3.5f, 4f, 0xFFFAFAFA, 0xFF9CCC65, "蔬菜"),
        crop("chili", "辣椒", "🌶️", 14, 34, 3f, 4.5f, 5.5f, 0xFFD32F2F, cat = "蔬菜"),
        crop("eggplant", "茄子", "🍆", 16, 40, 3.5f, 5f, 6.5f, 0xFF6A1B9A, cat = "蔬菜"),
        crop("spinach", "菠菜", "🥬", 10, 23, 2.5f, 3f, 4f, 0xFF81C784, 0xFF388E3C, "蔬菜"),
        crop("celery", "芹菜", "🌿", 12, 28, 3f, 4f, 4.5f, 0xFFAED581, 0xFF689F38, "蔬菜"),
        crop("pea", "豌豆", "🟢", 13, 32, 3f, 4f, 5f, 0xFF8BC34A, cat = "蔬菜"),
        crop("bean", "四季豆", "🫘", 13, 31, 3f, 4f, 5f, 0xFF7CB342, cat = "蔬菜"),
        crop("broccoli", "西兰花", "🥦", 16, 38, 3.5f, 5f, 6f, 0xFF43A047, 0xFF2E7D32, "蔬菜"),
        crop("squash", "西葫芦", "🥒", 14, 33, 3.5f, 4.5f, 5.5f, 0xFFCDDC39, 0xFF827717, "蔬菜"),
        crop("pepper_bell", "甜椒", "🫑", 15, 37, 3.5f, 5f, 6f, 0xFF43A047, cat = "蔬菜"),
        crop("cauliflower", "花菜", "🥦", 17, 42, 4f, 5.5f, 6.5f, 0xFFF5F5F5, 0xFF7CB342, "蔬菜"),
        crop("beet", "甜菜", "🟣", 12, 29, 3f, 4f, 5f, 0xFFAD1457, 0xFF66BB6A, "蔬菜"),
        crop("turnip", "芜菁", "⚪", 10, 24, 2.5f, 3.5f, 4f, 0xFFEEEEEE, cat = "蔬菜"),
        crop("asparagus", "芦笋", "🌿", 20, 50, 4.5f, 6f, 7.5f, 0xFF9CCC65, 0xFF558B2F, "蔬菜"),
        crop("yam", "山药", "🟤", 18, 44, 4f, 5.5f, 7f, 0xFFBCAAA4, 0xFF8D6E63, "蔬菜"),
        crop("taro", "芋头", "🟣", 17, 41, 3.5f, 5f, 6.5f, 0xFF7E57C2, 0xFF66BB6A, "蔬菜"),
        crop("lotus_root", "莲藕", "🪷", 22, 55, 5f, 6.5f, 8f, 0xFFE1BEE7, 0xFF4CAF50, "蔬菜"),
        // —— 花卉（大量，靠前解锁）——
        crop("dandelion", "蒲公英", "🌼", 8, 16, 2f, 2.5f, 3f, 0xFFFFEB3B, cat = "花卉"),
        crop("clover", "三叶草", "☘️", 10, 22, 2.5f, 3f, 3.5f, 0xFF66BB6A, 0xFF2E7D32, "花卉"),
        crop("daisy", "雏菊", "🌼", 18, 48, 4f, 5f, 6.5f, 0xFFFFF59D, cat = "花卉"),
        crop("marigold", "万寿菊", "🧡", 17, 44, 3.5f, 4.5f, 6f, 0xFFFF9800, cat = "花卉"),
        crop("cosmos", "波斯菊", "💗", 19, 50, 4f, 5f, 6.5f, 0xFFF48FB1, cat = "花卉"),
        crop("forget_me_not", "勿忘我", "💙", 18, 46, 3.5f, 4.5f, 5.5f, 0xFF5C6BC0, cat = "花卉"),
        crop("bluebell", "风铃草", "💙", 20, 52, 4f, 5f, 6.5f, 0xFF42A5F5, cat = "花卉"),
        crop("violet", "紫罗兰", "💜", 21, 54, 4.5f, 5.5f, 7f, 0xFF7E57C2, cat = "花卉"),
        crop("sunflower", "向日葵", "🌻", 22, 58, 5f, 6.5f, 8f, 0xFFFFC107, 0xFF8BC34A, "花卉"),
        crop("chrysanthemum", "菊花", "🌼", 24, 62, 5f, 6.5f, 8f, 0xFFFFF176, 0xFF9E9D24, "花卉"),
        crop("jasmine", "茉莉", "🤍", 23, 58, 4.5f, 6f, 7.5f, 0xFFFAFAFA, 0xFF81C784, "花卉"),
        crop("narcissus", "水仙", "💛", 24, 60, 5f, 6.5f, 8f, 0xFFFFF59D, cat = "花卉"),
        crop("gardenia", "栀子", "🤍", 25, 64, 5f, 6.5f, 8f, 0xFFFFFDE7, cat = "花卉"),
        crop("hyacinth", "风信子", "💜", 25, 64, 5f, 6.5f, 8.5f, 0xFFBA68C8, cat = "花卉"),
        crop("lavender", "薰衣草", "💜", 26, 68, 5.5f, 7f, 9f, 0xFF9575CD, 0xFF7E57C2, "花卉"),
        crop("azalea", "杜鹃", "🌸", 26, 66, 5.5f, 7f, 8.5f, 0xFFF06292, cat = "花卉"),
        crop("carnation", "康乃馨", "💗", 27, 70, 6f, 7.5f, 9f, 0xFFF48FB1, cat = "花卉"),
        crop("poppy", "虞美人", "🔴", 22, 56, 4.5f, 6f, 7.5f, 0xFFE53935, cat = "花卉"),
        crop("tulip", "郁金香", "🌷", 28, 72, 6f, 7.5f, 9.5f, 0xFFF06292, 0xFF66BB6A, "花卉"),
        crop("lily", "百合", "🤍", 29, 76, 6.5f, 8f, 10f, 0xFFFAFAFA, 0xFF66BB6A, "花卉"),
        crop("iris", "鸢尾", "💙", 28, 74, 6f, 7.5f, 9.5f, 0xFF5C6BC0, cat = "花卉"),
        crop("osmanthus", "桂花", "🟡", 29, 76, 6.5f, 8f, 10f, 0xFFFFD54F, 0xFF558B2F, "花卉"),
        crop("hibiscus", "芙蓉", "🌺", 30, 78, 7f, 8.5f, 10.5f, 0xFFE91E63, cat = "花卉"),
        crop("hydrangea", "绣球", "💙", 31, 82, 7f, 8.5f, 10.5f, 0xFF64B5F6, cat = "花卉"),
        crop("blossom", "梅花", "💮", 32, 86, 7.5f, 9f, 11f, 0xFFFCE4EC, 0xFF558B2F, "花卉"),
        crop("camellia", "山茶", "🔴", 33, 88, 7.5f, 9f, 11f, 0xFFE53935, cat = "花卉"),
        crop("lotus", "荷花", "🪷", 34, 90, 8f, 9.5f, 11.5f, 0xFFF8BBD0, 0xFF43A047, "花卉"),
        crop("rose", "玫瑰", "🌹", 35, 95, 8f, 10f, 12f, 0xFFE91E63, cat = "花卉"),
        crop("magnolia", "玉兰", "🤍", 38, 102, 9f, 11f, 13f, 0xFFFAFAFA, 0xFF81C784, "花卉"),
        crop("cherry_blossom", "樱花", "🌸", 40, 110, 9.5f, 11.5f, 13.5f, 0xFFF8BBD0, 0xFF81C784, "花卉"),
        crop("orchid", "兰花", "🏵️", 42, 115, 9.5f, 11.5f, 14f, 0xFFE1BEE7, 0xFF4CAF50, "花卉"),
        crop("peony", "牡丹", "🌺", 45, 125, 10f, 12f, 14.5f, 0xFFEC407A, 0xFF66BB6A, "花卉"),
        crop("four_leaf", "四叶草", "🍀", 50, 140, 10.5f, 12.5f, 15f, 0xFF43A047, 0xFF1B5E20, "花卉"),
        crop("lilac", "丁香", "💜", 27, 70, 6f, 7.5f, 9f, 0xFFCE93D8, cat = "花卉"),
        crop("begonia", "海棠", "💗", 28, 74, 6f, 7.5f, 9f, 0xFFF48FB1, cat = "花卉"),
        crop("impatiens", "凤仙", "🩷", 19, 48, 4f, 5f, 6f, 0xFFF06292, cat = "花卉"),
        crop("zinnia", "百日草", "🧡", 18, 46, 3.5f, 4.5f, 5.5f, 0xFFFF7043, cat = "花卉"),
        crop("petunia", "矮牵牛", "💜", 20, 52, 4f, 5f, 6.5f, 0xFFAB47BC, cat = "花卉"),
        crop("pansy", "三色堇", "💛", 21, 54, 4.5f, 5.5f, 7f, 0xFFFFEB3B, 0xFF7E57C2, "花卉"),
        crop("snapdragon", "金鱼草", "🩷", 22, 56, 4.5f, 5.5f, 7f, 0xFFEC407A, cat = "花卉"),
        crop("morning_glory", "牵牛花", "💙", 17, 42, 3.5f, 4f, 5f, 0xFF42A5F5, cat = "花卉"),
        crop("wisteria", "紫藤", "💜", 36, 96, 8.5f, 10f, 12f, 0xFF9575CD, cat = "花卉"),
        crop("plumeria", "鸡蛋花", "🤍", 30, 80, 7f, 8.5f, 10f, 0xFFFFF8E1, 0xFFF8BBD0, "花卉"),
        crop("bird_of_paradise", "鹤望兰", "🧡", 44, 120, 10f, 12f, 14f, 0xFFFF9800, 0xFF5C6BC0, "花卉"),
        crop("calla", "马蹄莲", "🤍", 31, 82, 7f, 8.5f, 10.5f, 0xFFFAFAFA, cat = "花卉"),
        crop("anthurium", "红掌", "❤️", 33, 88, 7.5f, 9f, 11f, 0xFFE53935, 0xFF43A047, "花卉"),
        crop("protea", "帝王花", "💗", 48, 130, 10.5f, 12.5f, 14.5f, 0xFFE91E63, cat = "花卉"),
        // —— 谷物 ——
        crop("corn", "玉米", "🌽", 18, 45, 4.5f, 6f, 8f, 0xFFFFEE58, 0xFF7CB342, "谷物"),
        crop("rice", "水稻", "🍚", 16, 40, 4f, 5.5f, 7f, 0xFFFFFDE7, 0xFF8BC34A, "谷物"),
        crop("barley", "大麦", "🌾", 13, 30, 3.5f, 4.5f, 6f, 0xFFFFECB3, 0xFFAFB42B, "谷物"),
        crop("oat", "燕麦", "🌾", 14, 33, 3.5f, 5f, 6f, 0xFFFFF8E1, 0xFF9E9D24, "谷物"),
        crop("sorghum", "高粱", "🔴", 15, 36, 4f, 5f, 6.5f, 0xFFE53935, 0xFF689F38, "谷物"),
        crop("millet", "小米", "🟡", 11, 26, 3f, 4f, 5f, 0xFFFFF176, 0xFFC0CA33, "谷物"),
        crop("buckwheat", "荞麦", "🟤", 14, 34, 3.5f, 4.5f, 6f, 0xFFD7CCC8, 0xFF8D6E63, "谷物"),
        // —— 水果 ——
        crop("strawberry", "草莓", "🍓", 20, 52, 4f, 5.5f, 7f, 0xFFE53935, cat = "水果"),
        crop("watermelon", "西瓜", "🍉", 30, 80, 7f, 9f, 10.5f, 0xFF43A047, 0xFF2E7D32, "水果"),
        crop("pumpkin", "南瓜", "🎃", 25, 65, 6f, 7.5f, 9.5f, 0xFFFF8F00, 0xFF558B2F, "水果"),
        crop("grape", "葡萄", "🍇", 28, 72, 6.5f, 8.5f, 10f, 0xFF8E24AA, cat = "水果"),
        crop("apple", "苹果", "🍎", 32, 85, 7.5f, 9.5f, 11.5f, 0xFFE53935, 0xFF43A047, "水果"),
        crop("pear", "梨", "🍐", 30, 78, 7f, 9f, 11f, 0xFFC5E1A5, 0xFF7CB342, "水果"),
        crop("peach", "桃子", "🍑", 30, 82, 7f, 9f, 11f, 0xFFFFAB91, 0xFF66BB6A, "水果"),
        crop("cherry", "樱桃", "🍒", 26, 70, 6f, 7.5f, 9f, 0xFFD32F2F, cat = "水果"),
        crop("banana", "香蕉", "🍌", 24, 62, 5f, 7f, 8.5f, 0xFFFFEB3B, 0xFF8BC34A, "水果"),
        crop("orange", "橙子", "🍊", 27, 68, 6f, 8f, 9.5f, 0xFFFF9800, 0xFF66BB6A, "水果"),
        crop("lemon", "柠檬", "🍋", 22, 56, 5f, 6.5f, 8f, 0xFFFFEE58, 0xFF9CCC65, "水果"),
        crop("mango", "芒果", "🥭", 34, 90, 8f, 10f, 12f, 0xFFFFB300, 0xFF558B2F, "水果"),
        crop("pineapple", "菠萝", "🍍", 36, 95, 8.5f, 10.5f, 12.5f, 0xFFFFC107, 0xFF689F38, "水果"),
        crop("kiwi", "猕猴桃", "🥝", 28, 74, 6.5f, 8f, 10f, 0xFF8BC34A, 0xFF33691E, "水果"),
        crop("blueberry", "蓝莓", "🫐", 24, 64, 5f, 6.5f, 8f, 0xFF5C6BC0, cat = "水果"),
        crop("raspberry", "覆盆子", "🫐", 23, 60, 5f, 6f, 7.5f, 0xFFE91E63, cat = "水果"),
        crop("melon", "哈密瓜", "🍈", 29, 76, 7f, 8.5f, 10.5f, 0xFFDCE775, 0xFF7CB342, "水果"),
        crop("coconut", "椰子", "🥥", 38, 100, 9f, 11f, 13.5f, 0xFF6D4C41, 0xFF4CAF50, "水果"),
        crop("pomegranate", "石榴", "🔴", 31, 84, 7.5f, 9f, 11f, 0xFFC62828, 0xFF66BB6A, "水果"),
        crop("fig", "无花果", "🟤", 27, 70, 6.5f, 8f, 9.5f, 0xFF8D6E63, cat = "水果"),
        crop("lychee", "荔枝", "🔴", 32, 86, 7.5f, 9f, 11f, 0xFFD32F2F, 0xFF66BB6A, "水果"),
        crop("longan", "龙眼", "🟤", 30, 80, 7f, 8.5f, 10.5f, 0xFFBCAAA4, cat = "水果"),
        crop("persimmon", "柿子", "🟠", 29, 76, 6.5f, 8.5f, 10f, 0xFFFF7043, cat = "水果"),
        crop("plum", "李子", "🟣", 26, 68, 6f, 7.5f, 9f, 0xFF8E24AA, cat = "水果"),
        // —— 香草 / 茶 ——
        crop("mint", "薄荷", "🌿", 12, 30, 2f, 3f, 4f, 0xFFA5D6A7, 0xFF388E3C, "香草"),
        crop("basil", "罗勒", "🌿", 13, 32, 2.5f, 3.5f, 4.5f, 0xFF81C784, cat = "香草"),
        crop("rosemary", "迷迭香", "🌿", 15, 38, 3f, 4f, 5f, 0xFF66BB6A, 0xFF33691E, "香草"),
        crop("thyme", "百里香", "🌿", 14, 34, 2.5f, 3.5f, 4.5f, 0xFF9CCC65, cat = "香草"),
        crop("sage", "鼠尾草", "🌿", 14, 35, 3f, 4f, 5f, 0xFFAED581, cat = "香草"),
        crop("cilantro", "香菜", "🌿", 9, 20, 2f, 2.5f, 3f, 0xFF8BC34A, cat = "香草"),
        crop("parsley", "欧芹", "🌿", 10, 22, 2f, 3f, 3.5f, 0xFF7CB342, cat = "香草"),
        crop("ginger", "生姜", "🫚", 16, 40, 3.5f, 5f, 6f, 0xFFFFCC80, 0xFF8D6E63, "香草"),
        crop("tea", "茶叶", "🍵", 28, 74, 6f, 8f, 10f, 0xFFC5E1A5, 0xFF33691E, "香草"),
        crop("coffee", "咖啡豆", "☕", 36, 96, 8f, 10.5f, 12.5f, 0xFF6D4C41, 0xFF558B2F, "香草"),
        crop("cocoa", "可可", "🍫", 38, 102, 8.5f, 11f, 13f, 0xFF5D4037, 0xFF6D4C41, "香草"),
        // —— 菇菌 / 其他 ——
        crop("mushroom", "蘑菇", "🍄", 18, 48, 2f, 3f, 4f, 0xFFEF5350, 0xFFECEFF1, "菇菌"),
        crop("shiitake", "香菇", "🍄", 20, 52, 2.5f, 3.5f, 4.5f, 0xFF6D4C41, 0xFFD7CCC8, "菇菌"),
        crop("enoki", "金针菇", "🍄", 16, 42, 2f, 3f, 4f, 0xFFFFF8E1, 0xFFFFE082, "菇菌"),
        crop("bamboo", "竹子", "🎋", 40, 110, 10f, 12.5f, 15f, 0xFFA5D6A7, 0xFF2E7D32, "其他"),
        crop("cotton", "棉花", "☁️", 22, 56, 4.5f, 6.5f, 8f, 0xFFFAFAFA, 0xFF8BC34A, "其他"),
        crop("sugarcane", "甘蔗", "🎋", 26, 68, 5.5f, 7.5f, 9f, 0xFFDCEDC8, 0xFF689F38, "其他"),
        crop("peanut", "花生", "🥜", 15, 38, 3f, 4.5f, 5.5f, 0xFFFFE0B2, 0xFF8D6E63, "其他"),
        crop("soybean", "大豆", "🫘", 14, 34, 3f, 4f, 5f, 0xFFFFF59D, cat = "其他"),
        crop("sesame", "芝麻", "⚪", 13, 32, 3f, 4f, 5f, 0xFFECEFF1, 0xFF9E9E9E, "其他"),
        crop("aloe", "芦荟", "🌵", 24, 62, 5f, 7f, 8.5f, 0xFFAED581, 0xFF558B2F, "其他"),
        crop("cactus", "仙人掌", "🌵", 20, 50, 4f, 5.5f, 7f, 0xFF66BB6A, 0xFF2E7D32, "其他"),
        crop("fern", "蕨类", "🌿", 15, 36, 3f, 4f, 5f, 0xFF81C784, 0xFF1B5E20, "其他"),
        crop("moss", "青苔", "🟢", 8, 16, 2f, 2.5f, 3f, 0xFF558B2F, 0xFF33691E, "其他"),
    )

fun cropById(id: String?): CropDef? = id?.let { cid -> FARM_CROPS.find { it.id == cid } }

/** 按已解锁数量计算下一次收获解锁所需累计次数。 */
fun farmUnlockNeed(unlockedCount: Int): Int {
    if (unlockedCount >= FARM_CROPS.size) return Int.MAX_VALUE
    // 前期快、后期慢：约 2,4,6... 再抬升
    return when {
        unlockedCount < 8 -> (unlockedCount - 3).coerceAtLeast(1) * 2
        unlockedCount < 20 -> 10 + (unlockedCount - 8) * 3
        unlockedCount < 40 -> 46 + (unlockedCount - 20) * 4
        unlockedCount < 60 -> 126 + (unlockedCount - 40) * 5
        else -> 226 + (unlockedCount - 60) * 6
    }
}

/**
 * 离线追赶：把「每小时发生一次的概率 p」换成连续 [hours] 内至少发生一次的概率。
 * 避免错误地用 `p * hours`（会大于 1，长时间离开必出草/虫/枯萎）。
 */
fun farmChanceAtLeastOnce(
    pPerHour: Float,
    hours: Float,
): Float {
    if (hours <= 0f || pPerHour <= 0f) return 0f
    if (pPerHour >= 1f) return 1f
    return (1.0 - Math.pow((1.0 - pPerHour.toDouble()), hours.toDouble()))
        .toFloat()
        .coerceIn(0f, 1f)
}

@Serializable
data class FarmPlotState(
    var cropId: String? = null,
    /** 0空 1种 2芽 3长 4熟 5枯 */
    var stage: Int = 0,
    /** 当前阶段进度 0..1 */
    var stageProgress: Float = 0f,
    var moisture: Float = 0.5f,
    var weed: Boolean = false,
    var bug: Boolean = false,
    var lastTickMs: Long = 0L,
)

@Serializable
data class FarmSave(
    var coins: Int = 80,
    var plots: List<FarmPlotState> = List(20) { FarmPlotState() },
    var unlockedCropIds: List<String> = listOf("radish", "cabbage", "wheat", "tomato"),
    var harvestedTotal: Int = 0,
)

const val FARM_COLS = 5
const val FARM_ROWS = 4
const val FARM_PLOT_COUNT = FARM_COLS * FARM_ROWS
