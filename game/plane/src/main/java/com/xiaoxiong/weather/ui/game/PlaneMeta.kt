package com.xiaoxiong.weather.ui.game

/**
 * 局外轻量成长：涂装随历史最远波次解锁，选择写入 prefs，不进对局快照。
 *
 * [unlockWave] 为解锁所需「任意难度」历史最远波次（屏幕侧取当前难度 bestWave，
 * 或跨难度最大值均可；当前实现按当前难度 bestWave）。
 */
enum class PlaneShipTint(
    val id: String,
    val unlockWave: Int,
) {
    CLASSIC(id = "classic", unlockWave = 0),
    GOLD(id = "gold", unlockWave = 10),
    MIDNIGHT(id = "midnight", unlockWave = 20),
    ROSE(id = "rose", unlockWave = 35),
    AURORA(id = "aurora", unlockWave = 50),
    ;

    companion object {
        fun fromId(id: String?): PlaneShipTint = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** 开局命数 prefs 旁路：战机涂装选择。 */
internal const val PLANE_SHIP_TINT_KEY = "plane_ship_tint"

/** [bestWave] 是否已解锁该涂装。 */
fun planeShipTintUnlocked(
    tint: PlaneShipTint,
    bestWave: Int,
): Boolean = bestWave.coerceAtLeast(0) >= tint.unlockWave

/** 已解锁涂装列表（含 CLASSIC，按解锁波次升序）。 */
fun planeUnlockedShipTints(bestWave: Int): List<PlaneShipTint> = PlaneShipTint.entries.filter { planeShipTintUnlocked(it, bestWave) }

/**
 * Boss 种类：同属 [PlaneEnemyType.BOSS]，按波次轮换弹幕与走位。
 * 名称稳定，写入快照编解码。
 */
enum class PlaneBossKind {
    /** 主力舰：扇形 + 环形（经典三阶段）。 */
    STANDARD,

    /** 环幕主宰：密环为主、走位更稳。 */
    RINGMASTER,

    /** 强袭战机：高速扫射 + 瞄准压制。 */
    STRIKER,
}
