package com.xiaoxiong.weather.ui.game

import kotlin.math.max
import kotlin.math.roundToInt

/** 逻辑场地宽度固定 100：实体 / 子弹 / 生成均用逻辑坐标，与设备像素解耦。 */
const val PLANE_LOGICAL_WIDTH = 100f

/** 逻辑场地高度建议区间 [150, 200]：调用方按画布纵横比换算后裁剪，保证不同机型公平。 */
const val PLANE_FIELD_H_MIN = 150f
const val PLANE_FIELD_H_MAX = 200f

/** 波次总数：清完第 [PLANE_TOTAL_WAVES] 波（Boss）判定通关。 */
const val PLANE_TOTAL_WAVES = 10

/** 每 [PLANE_BOSS_EVERY] 波出现一次 Boss（第 5 / 10 波）。 */
const val PLANE_BOSS_EVERY = 5

/**
 * 玩法难度：影响敌人 HP / 速度 / 弹速 / 弹幕密度 / 道具掉率与分数倍率。
 *
 * [id] 用于 prefs 持久化与排行榜分类键，**必须保持稳定**（修改会丢存档与榜单分类）。
 */
enum class PlaneDifficulty(
    val id: String,
    /** 敌人 HP 倍率。 */
    val enemyHpMul: Float,
    /** 敌人移动速度倍率。 */
    val enemySpeedMul: Float,
    /** 敌弹速度倍率。 */
    val bulletSpeedMul: Float,
    /** 弹幕密度倍率（开火间隔越短越密）。 */
    val fireDensityMul: Float,
    /** 道具掉率倍率。 */
    val dropChanceMul: Float,
    /** 击毁得分的难度倍率。 */
    val scoreMul: Float,
) {
    CASUAL(
        id = "casual",
        enemyHpMul = 0.8f,
        enemySpeedMul = 0.85f,
        bulletSpeedMul = 0.82f,
        fireDensityMul = 0.8f,
        dropChanceMul = 1.3f,
        scoreMul = 0.8f,
    ),
    STANDARD(
        id = "standard",
        enemyHpMul = 1f,
        enemySpeedMul = 1f,
        bulletSpeedMul = 1f,
        fireDensityMul = 1f,
        dropChanceMul = 1f,
        scoreMul = 1f,
    ),
    CHALLENGE(
        id = "challenge",
        enemyHpMul = 1.35f,
        enemySpeedMul = 1.12f,
        bulletSpeedMul = 1.18f,
        fireDensityMul = 1.2f,
        dropChanceMul = 0.85f,
        scoreMul = 1.5f,
    ),
    ;

    companion object {
        /** 缺省难度：旧档 / 非法 id 一律回退到它。 */
        val DEFAULT: PlaneDifficulty = STANDARD

        /** 按持久化 id 解析；null / 非法 id 回退 [DEFAULT]。 */
        fun fromId(id: String?): PlaneDifficulty = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 敌人类型：小兵 / 俯冲 / 摆渡 / 炮台 / 运输机 / Boss。 */
enum class PlaneEnemyType(
    val baseHp: Int,
    /** 基准速度（逻辑单位 / 秒）。 */
    val baseSpeed: Float,
    val baseScore: Int,
    val baseRadius: Float,
    /** 基准开火间隔（tick）；0 表示不开火。 */
    val baseFireIntervalTicks: Int,
    val baseBulletSpeed: Float,
) {
    /** 小兵：直线下行，按编号交替直射。 */
    GRUNT(3, 22f, 100, 2.4f, 150, 34f),

    /** 俯冲兵：短暂下坠后加速扑向玩家。 */
    DIVER(4, 20f, 150, 2.6f, 200, 26f),

    /** 摆渡兵：正弦左右摆动并三连散射。 */
    WEAVER(5, 16f, 180, 2.8f, 160, 30f),

    /** 炮台：定点停靠后瞄准三向射击。 */
    TURRET(8, 26f, 250, 3.0f, 110, 40f),

    /** 运输机：缓慢直线，击毁必掉道具。 */
    TRANSPORT(6, 14f, 220, 3.6f, 0, 0f),

    /** Boss（每 5 波）：三阶段弹幕。 */
    BOSS(120, 20f, 3000, 9f, 75, 36f),
}

/**
 * 敌人实例参数（已按难度 / 波次缩放）。
 */
data class PlaneEnemySpec(
    val hp: Int,
    val score: Int,
    val radius: Float,
    val fireIntervalTicks: Int,
    val bulletSpeed: Float,
)

/**
 * 武器规格：1 单发 / 2 双发 / 3 三向散射 / 4 穿透激光。
 */
data class PlaneWeaponSpec(
    val level: Int,
    /** 开火间隔（tick）。 */
    val intervalTicks: Int,
    val damage: Int,
    val count: Int,
    /** 散射张角（弧度，逐发偏移）。 */
    val spreadRad: Float,
    /** 平行双发的横向间距。 */
    val offsetX: Float,
    val speed: Float,
    /** 激光：穿透且每敌只结算一次伤害。 */
    val piercing: Boolean = false,
)

/** 一次刷怪的静态定义（波次表用；难度只缩放数值，不改变刷怪时序）。 */
data class PlaneSpawnDef(
    val type: PlaneEnemyType,
    val x: Float,
    val y: Float,
    /** 相对本波开始的延迟（tick）。 */
    val delayTicks: Int,
    /** 入场速度（仅侧向入场使用）。 */
    val vx: Float = 0f,
    val vy: Float = 0f,
    /** 入场方向：-1 左侧 / 0 顶部 / 1 右侧。 */
    val side: Int = 0,
    /** 炮台停靠高度（仅 TURRET 使用）。 */
    val targetY: Float = 0f,
)

/** 一波的完整定义。 */
data class PlaneWaveDef(
    val index: Int,
    val isBoss: Boolean,
    val spawns: List<PlaneSpawnDef>,
)

/**
 * 玩法常量 + 数值缩放 + 波次表（纯数据 / 纯函数，便于单测）。
 *
 * 时间基准是固定 60Hz 逻辑 tick：[PlaneConfig.LOGIC_STEP_SECONDS]，不依赖 wall clock。
 */
object PlaneConfig {
    /** 固定逻辑步长（秒）。 */
    const val LOGIC_STEP_SECONDS = 1f / 60f

    /** 单帧最多补算的逻辑步数（掉帧时世界慢动作而非瞬移）。 */
    const val MAX_STEPS_PER_FRAME = 4

    // 玩家
    const val PLAYER_RADIUS = 1.6f
    const val PLAYER_GRAZE_RADIUS = 4.6f
    const val START_LIVES = 3
    const val START_BOMBS = 2
    const val MAX_BOMBS = 5
    const val MIN_WEAPON_LEVEL = 1
    const val MAX_WEAPON_LEVEL = 4

    // 连杀 / 擦弹
    const val COMBO_WINDOW_TICKS = 180
    const val MAX_COMBO_BONUS_STEPS = 10
    const val COMBO_BONUS_PER_STEP = 5
    const val GRAZE_SCORE = 10

    // 受击 / 炸弹
    const val HIT_INVULN_TICKS = 120
    const val BOMB_INVULN_TICKS = 90
    const val BOMB_DAMAGE = 40
    const val BOSS_RAM_DAMAGE = 5
    const val RAM_DAMAGE = 999

    // 波次节奏
    const val WAVE_INTRO_TICKS = 90
    const val WAVE_GAP_TICKS = 90
    const val BOSS_WARNING_TICKS = 150
    const val SIDE_ENTRY_TICKS = 75

    // 子弹
    const val PLAYER_BULLET_RADIUS = 0.8f
    const val LASER_RADIUS = 1.4f
    const val LASER_HALF_LENGTH = 5f

    /**
     * 激光存活 tick：速度 120/s × 120 tick = 240 逻辑单位，
     * 保证从场地最低处也能打到顶部停驻的炮台 / Boss（旧值 14 只有 28 单位，构筑等级 4 后打不到远目标）。
     */
    const val LASER_TTL_TICKS = 120
    const val ENEMY_BULLET_RADIUS = 1.5f
    const val BOSS_BULLET_RADIUS = 1.8f

    // 炮台长条弹（RECT）：半宽 / 半高 / 相对弹速倍率（半高须不小于半宽，渲染与判定一致）。
    const val TURRET_BAR_HALF_WIDTH = 3f
    const val TURRET_BAR_HALF_HEIGHT = 9f
    const val TURRET_BAR_SPEED_MUL = 0.7f

    /** 快照语义清洗时敌人 HP 的上限（正常 Boss 血量远低于此）。 */
    const val MAX_ENEMY_HP = 1000

    // 道具 / 特效
    const val DROP_BASE_CHANCE = 0.22f
    const val POWERUP_RADIUS = 1.8f
    const val POWERUP_PICKUP_PADDING = 1.4f
    const val POWERUP_SCORE = 50
    const val MAX_EFFECTS = 240
    const val OFFSCREEN_MARGIN = 8f

    /**
     * 敌人实例参数：HP 随难度与波次增长；速度 / 弹速 / 开火密度 / 基础分按难度缩放。
     */
    fun enemySpec(
        type: PlaneEnemyType,
        difficulty: PlaneDifficulty,
        waveIndex: Int,
    ): PlaneEnemySpec {
        val wave = waveIndex.coerceAtLeast(1)
        val waveHpScale = 1f + (wave - 1) * 0.06f
        return PlaneEnemySpec(
            hp = max(1, (type.baseHp * difficulty.enemyHpMul * waveHpScale).roundToInt()),
            score = max(1, (type.baseScore * (1f + (wave - 1) * 0.04f)).roundToInt()),
            radius = type.baseRadius,
            fireIntervalTicks = fireIntervalTicks(type, difficulty),
            bulletSpeed = type.baseBulletSpeed * difficulty.bulletSpeedMul,
        )
    }

    /** 敌人开火间隔（tick）：难度密度越高间隔越短，下限 24 tick 防止弹幕过密。 */
    fun fireIntervalTicks(
        type: PlaneEnemyType,
        difficulty: PlaneDifficulty,
    ): Int =
        if (type.baseFireIntervalTicks <= 0) {
            0
        } else {
            (type.baseFireIntervalTicks / difficulty.fireDensityMul).roundToInt().coerceAtLeast(24)
        }

    /** 武器等级规格；越界自动钳制到 1..4。 */
    fun weaponSpec(level: Int): PlaneWeaponSpec =
        when (level.coerceIn(MIN_WEAPON_LEVEL, MAX_WEAPON_LEVEL)) {
            1 ->
                PlaneWeaponSpec(
                    level = 1,
                    intervalTicks = 11,
                    damage = 1,
                    count = 1,
                    spreadRad = 0f,
                    offsetX = 0f,
                    speed = 95f,
                )
            2 ->
                PlaneWeaponSpec(
                    level = 2,
                    intervalTicks = 10,
                    damage = 1,
                    count = 2,
                    spreadRad = 0f,
                    offsetX = 2.2f,
                    speed = 95f,
                )
            3 ->
                PlaneWeaponSpec(
                    level = 3,
                    intervalTicks = 9,
                    damage = 1,
                    count = 3,
                    spreadRad = 0.18f,
                    offsetX = 0f,
                    speed = 92f,
                )
            else ->
                PlaneWeaponSpec(
                    level = 4,
                    intervalTicks = 7,
                    damage = 3,
                    count = 1,
                    spreadRad = 0f,
                    offsetX = 0f,
                    speed = 120f,
                    piercing = true,
                )
        }

    /**
     * 天气对玩法的轻微影响（确定性倍率）：雪天敌人稍慢、雷暴稍快，其余不变。
     * 只作用于敌人移动，不改变弹幕与掉落。
     */
    fun weatherSpeedMul(weather: FarmWeather): Float =
        when (weather) {
            FarmWeather.SNOWY -> 0.92f
            FarmWeather.STORMY -> 1.05f
            else -> 1f
        }

    /** 击毁得分 = 基础分 × 难度倍率 + 连杀奖励（每级 +5，最多 10 级）。 */
    fun killScore(
        baseScore: Int,
        difficulty: PlaneDifficulty,
        combo: Int,
    ): Int =
        (baseScore * difficulty.scoreMul).roundToInt() +
            (combo - 1).coerceIn(0, MAX_COMBO_BONUS_STEPS) * COMBO_BONUS_PER_STEP

    /**
     * 波次表：静态定义，不消耗 RNG。
     *
     * - 每 [PLANE_BOSS_EVERY] 波为 Boss 波（仅一个 BOSS 条目）；
     * - 其余波由 4 种编队循环，第 2 轮（6..9 波）数量更多；
     * - 编队 2 从左右两侧切入，其余从顶部入场。
     */
    fun wave(index: Int): PlaneWaveDef {
        if (index <= 0) return PlaneWaveDef(index, isBoss = false, spawns = emptyList())
        if (index % PLANE_BOSS_EVERY == 0) {
            return PlaneWaveDef(
                index = index,
                isBoss = true,
                spawns =
                    listOf(
                        PlaneSpawnDef(
                            type = PlaneEnemyType.BOSS,
                            x = PLANE_LOGICAL_WIDTH * 0.5f,
                            y = -12f,
                            delayTicks = 0,
                        ),
                    ),
            )
        }
        val stage = (index - 1) / PLANE_BOSS_EVERY + 1
        val local = (index - 1) % PLANE_BOSS_EVERY + 1
        val spawns = mutableListOf<PlaneSpawnDef>()
        when (local) {
            1 -> {
                val count = 4 + stage
                for (i in 0 until count) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.GRUNT,
                            x = 18f + i * (64f / (count - 1)),
                            y = -6f,
                            delayTicks = 20 + i * 12,
                        )
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 30f, -8f, 170)
                if (stage > 1) spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 70f, -8f, 240)
            }
            2 -> {
                val count = 4 + stage
                for (i in 0 until count) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.GRUNT,
                            x = -8f,
                            y = 16f + i * 7f,
                            delayTicks = 15 + i * 10,
                            vx = 46f,
                            vy = 8f,
                            side = -1,
                        )
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.GRUNT,
                            x = PLANE_LOGICAL_WIDTH + 8f,
                            y = 16f + i * 7f,
                            delayTicks = 20 + i * 10,
                            vx = -46f,
                            vy = 8f,
                            side = 1,
                        )
                }
                for (i in 0 until 3 + stage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.DIVER,
                            x = 18f + i * 18f,
                            y = -5f,
                            delayTicks = 130 + i * 22,
                        )
                }
            }
            3 -> {
                for (i in 0 until 4 + stage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.WEAVER,
                            x = 14f + i * 13f,
                            y = -5f,
                            delayTicks = 15 + i * 16,
                        )
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TURRET, 28f, -5f, 140, targetY = 32f)
                spawns += PlaneSpawnDef(PlaneEnemyType.TURRET, 72f, -5f, 175, targetY = 32f)
            }
            else -> {
                for (i in 0 until 3 + stage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.GRUNT,
                            x = 15f + i * 25f,
                            y = -6f,
                            delayTicks = 20 + i * 14,
                        )
                }
                for (i in 0 until 3 + stage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.WEAVER,
                            x = 22f + i * 22f,
                            y = -5f,
                            delayTicks = 90 + i * 18,
                        )
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TURRET, 50f, -5f, 150, targetY = 30f)
                spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 50f, -8f, 210)
                for (i in 0 until 2 + stage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.DIVER,
                            x = 20f + i * 30f,
                            y = -5f,
                            delayTicks = 240 + i * 20,
                        )
                }
            }
        }
        return PlaneWaveDef(index, isBoss = false, spawns = spawns.sortedBy { it.delayTicks })
    }
}
