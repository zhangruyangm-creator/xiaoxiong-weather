package com.xiaoxiong.weather.ui.game

import kotlin.math.max
import kotlin.math.roundToInt

/** 逻辑场地宽度固定 100：实体 / 子弹 / 生成均用逻辑坐标，与设备像素解耦。 */
const val PLANE_LOGICAL_WIDTH = 100f

/** 逻辑场地高度建议区间 [150, 200]：调用方按画布纵横比换算后裁剪，保证不同机型公平。 */
const val PLANE_FIELD_H_MIN = 150f
const val PLANE_FIELD_H_MAX = 200f

/**
 * 无尽模式波次安全上限：只用于防溢出 / 清洗坏档，不参与通关判定。
 * 第 [PLANE_MAX_WAVE] 波之后波次号不再增长（数值成长早已进入软上限平台期）。
 */
const val PLANE_MAX_WAVE = 9999

/** 每 [PLANE_BOSS_EVERY] 波出现一次 Boss（第 5 / 10 / 15 … 波，永久循环）。 */
const val PLANE_BOSS_EVERY = 5

/**
 * 玩法难度：影响敌人 HP / 速度 / 弹速 / 弹幕密度 / 道具掉率、分数倍率与受击无敌时长。
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
    /** 开局 / 受击后无敌时长（逻辑 tick，60Hz）。须 ≤ [PlaneConfig.HIT_INVULN_TICKS]。 */
    val hitInvulnTicks: Int,
) {
    CASUAL(
        id = "casual",
        enemyHpMul = 0.8f,
        enemySpeedMul = 0.85f,
        bulletSpeedMul = 0.85f,
        fireDensityMul = 0.8f,
        dropChanceMul = 1.3f,
        scoreMul = 0.8f,
        hitInvulnTicks = 75,
    ),
    STANDARD(
        id = "standard",
        enemyHpMul = 1f,
        enemySpeedMul = 1f,
        bulletSpeedMul = 1f,
        fireDensityMul = 1f,
        dropChanceMul = 1f,
        scoreMul = 1f,
        hitInvulnTicks = 45,
    ),
    CHALLENGE(
        id = "challenge",
        enemyHpMul = 1.35f,
        enemySpeedMul = 1.12f,
        bulletSpeedMul = 1.12f,
        fireDensityMul = 1.2f,
        dropChanceMul = 0.85f,
        scoreMul = 1.5f,
        hitInvulnTicks = 30,
    ),
    ;

    companion object {
        /** 缺省难度：旧档 / 非法 id 一律回退到它。 */
        val DEFAULT: PlaneDifficulty = STANDARD

        /** 按持久化 id 解析；null / 非法 id 回退 [DEFAULT]。 */
        fun fromId(id: String?): PlaneDifficulty = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 操作方式：拖动瞬移跟手，或虚拟方向键按住匀速移动。
 * [id] 写入 prefs，必须保持稳定。
 */
enum class PlaneControlMode(
    val id: String,
) {
    /** 手指拖到哪，战机跟到哪。 */
    DRAG("drag"),

    /** 左下虚拟方向键：八向按住移动，速度封顶。 */
    DPAD("dpad"),
    ;

    companion object {
        val DEFAULT: PlaneControlMode = DRAG

        fun fromId(id: String?): PlaneControlMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 敌人类型：小兵 / 俯冲 / 摆渡 / 炮台 / 运输机 / 直升机 / 装甲车 / 侦察 / 轰炸 / 狙击 / Boss。 */
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
    GRUNT(3, 22f, 100, 2.4f, 180, 56f),

    /** 俯冲兵：短暂下坠后加速扑向玩家。 */
    DIVER(4, 20f, 150, 2.6f, 235, 44f),

    /** 摆渡兵：正弦左右摆动并三连散射。 */
    WEAVER(5, 16f, 180, 2.8f, 190, 50f),

    /** 炮台：定点停靠后瞄准三向射击。 */
    TURRET(8, 26f, 250, 3.0f, 135, 62f),

    /** 运输机：缓慢直线，击毁必掉道具。 */
    TRANSPORT(6, 14f, 220, 3.6f, 0, 0f),

    /** 直升机：悬停后缓慢左右摆动，向下扇形射击。 */
    HELI(6, 17f, 220, 2.9f, 170, 52f),

    /** 装甲车：从画面下方进场爬升至地面线，横向巡行并向上发射炮弹 / 瞄准弹。 */
    TANK(9, 15f, 260, 3.3f, 165, 60f),

    /** 侦察机：高速之字形穿梭，低血高频瞄准点射。 */
    SCOUT(2, 32f, 130, 2.0f, 110, 68f),

    /** 轰炸机：缓慢下行，投下三连垂直落弹封锁走位。 */
    BOMBER(7, 12f, 210, 3.5f, 155, 42f),

    /** 狙击机：下降到 [PlaneSpawnDef.targetY] 停驻，高速点射后脱离。 */
    SNIPER(4, 24f, 200, 2.5f, 95, 92f),

    /** Boss（每 5 波）：三阶段弹幕。 */
    BOSS(120, 20f, 3000, 9f, 95, 58f),
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
    /** 炮台停靠高度（TURRET / SNIPER 使用）。 */
    val targetY: Float = 0f,
    /** 从画面下方进场（仅 TANK 使用；非 0 时 [y] 忽略，按当前场高换算）。 */
    val fromBottom: Boolean = false,
)

/**
 * 波次主题：决定刷怪表与标题横幅文案。
 * 由波次号纯函数推导，不写入快照（恢复时 [PlaneConfig.wave] 重算）。
 */
enum class PlaneWaveTheme {
    /** 常规四编队循环。 */
    NORMAL,

    /** Boss 波。 */
    BOSS,

    /** 侦察机潮：高速之字机群。 */
    SCOUT_SWARM,

    /** 轰炸跑道：重型轰炸机压境。 */
    BOMBER_RUN,

    /** 狙击巢穴：定点高速点射。 */
    SNIPER_NEST,
}

/** 一波的完整定义。 */
data class PlaneWaveDef(
    val index: Int,
    val isBoss: Boolean,
    val spawns: List<PlaneSpawnDef>,
    val theme: PlaneWaveTheme = PlaneWaveTheme.NORMAL,
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

    /**
     * 方向键移动速度（逻辑单位 / 秒）。场宽 100，约 1.25 秒横穿；
     * 比拖动瞬移慢，用来压低「指哪打哪」的容错。
     */
    const val DPAD_SPEED = 80f

    /** 默认开局命数；实际命数由 [PlaneGame.startLives]（设置持久化）决定。 */
    const val START_LIVES = 3
    const val START_LIVES_MIN = 1
    const val START_LIVES_MAX = 30

    /**
     * HUD 生命小熊头最多并排几个；超过后改用「图标 + 数字」紧凑显示，
     * 避免 30 命时底部状态条被图标撑爆。
     */
    const val HUD_LIFE_ICON_MAX = 8
    const val START_BOMBS = 2
    const val MAX_BOMBS = 5
    const val MIN_WEAPON_LEVEL = 1
    const val MAX_WEAPON_LEVEL = 5

    // 连杀 / 擦弹
    const val COMBO_WINDOW_TICKS = 180
    const val MAX_COMBO_BONUS_STEPS = 10
    const val COMBO_BONUS_PER_STEP = 5
    const val GRAZE_SCORE = 10

    // 受击 / 炸弹

    /** 受击无敌上限（= CASUAL）；实际时长见 [PlaneDifficulty.hitInvulnTicks]。 */
    const val HIT_INVULN_TICKS = 75
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
     * 激光存活 tick：速度 205/s × 120 tick = 410 逻辑单位，
     * 保证从场地最低处也能打到顶部停驻的炮台 / Boss（旧值 14 只有 28 单位，构筑等级 4 后打不到远目标）。
     */
    const val LASER_TTL_TICKS = 120
    const val ENEMY_BULLET_RADIUS = 1.5f
    const val BOSS_BULLET_RADIUS = 1.8f

    // 炮台长条弹（RECT）：半宽 / 半高 / 相对弹速倍率（半高须不小于半宽，渲染与判定一致）。
    const val TURRET_BAR_HALF_WIDTH = 3f
    const val TURRET_BAR_HALF_HEIGHT = 9f
    const val TURRET_BAR_SPEED_MUL = 0.7f

    /** 开火间隔硬下限（tick）：随波次密度提升也不会低于此值，避免弹幕糊屏。 */
    const val MIN_FIRE_INTERVAL_TICKS = 30

    /** 快照语义清洗时敌人 HP 的上限（正常 Boss 血量远低于此）。 */
    const val MAX_ENEMY_HP = 1000

    // 波次成长（无尽模式）：线性增长到软上限后进入平台期，避免数值爆炸。
    private const val WAVE_HP_GROWTH = 0.055f
    private const val WAVE_HP_BONUS_MAX = 4.5f
    private const val WAVE_SCORE_GROWTH = 0.04f
    private const val WAVE_SCORE_BONUS_MAX = 4f
    private const val WAVE_SPEED_GROWTH = 0.012f
    private const val WAVE_SPEED_BONUS_MAX = 0.5f
    private const val WAVE_BULLET_SPEED_GROWTH = 0.010f
    private const val WAVE_BULLET_SPEED_BONUS_MAX = 0.4f
    private const val WAVE_FIRE_DENSITY_GROWTH = 0.012f
    private const val WAVE_FIRE_DENSITY_BONUS_MAX = 0.5f

    /** 编队规模软上限：每个编队基础数量 + min(stage, 8)，防止高波次单屏敌机无限膨胀。 */
    private const val WAVE_FORMATION_STAGE_CAP = 8

    // 道具 / 特效
    const val DROP_BASE_CHANCE = 0.26f

    /** 护盾道具无敌时长（tick）。 */
    const val SHIELD_INVULN_TICKS = 180

    /** 满级再吃武器时的过载时长（tick，约 5 秒）。 */
    const val OVERDRIVE_TICKS = 300
    const val POWERUP_RADIUS = 1.8f
    const val POWERUP_PICKUP_PADDING = 1.4f
    const val POWERUP_SCORE = 50
    const val MAX_EFFECTS = 240
    const val OFFSCREEN_MARGIN = 8f

    /**
     * 波次成长（无尽模式）的线性增长 + 软上限：`1 + min(step × (wave-1), cap)`。
     */
    private fun waveBonus(
        waveIndex: Int,
        step: Float,
        cap: Float,
    ): Float = ((waveIndex.coerceAtLeast(1) - 1) * step).coerceAtMost(cap)

    /** 敌人 HP 波次倍率（1 .. 5.5）。 */
    fun waveHpScale(waveIndex: Int): Float = 1f + waveBonus(waveIndex, WAVE_HP_GROWTH, WAVE_HP_BONUS_MAX)

    /** 击毁得分波次倍率（1 .. 5），随波次提升但不会让单杀分数无限膨胀。 */
    fun waveScoreScale(waveIndex: Int): Float = 1f + waveBonus(waveIndex, WAVE_SCORE_GROWTH, WAVE_SCORE_BONUS_MAX)

    /** 敌人移动速度波次倍率（1 .. 1.5）。 */
    fun waveSpeedMul(waveIndex: Int): Float = 1f + waveBonus(waveIndex, WAVE_SPEED_GROWTH, WAVE_SPEED_BONUS_MAX)

    /** 敌弹速度波次倍率（1 .. 1.4）。 */
    fun waveBulletSpeedMul(waveIndex: Int): Float = 1f + waveBonus(waveIndex, WAVE_BULLET_SPEED_GROWTH, WAVE_BULLET_SPEED_BONUS_MAX)

    /** 敌人开火密度波次倍率（1 .. 1.5）。 */
    fun waveFireDensityMul(waveIndex: Int): Float = 1f + waveBonus(waveIndex, WAVE_FIRE_DENSITY_GROWTH, WAVE_FIRE_DENSITY_BONUS_MAX)

    /**
     * 敌人实例参数：HP / 分数 / 弹速随难度与波次增长（波次成长带软上限）；
     * 开火间隔 = 基准 ÷ 难度密度 ÷ 波次密度，下限 [MIN_FIRE_INTERVAL_TICKS]。
     */
    fun enemySpec(
        type: PlaneEnemyType,
        difficulty: PlaneDifficulty,
        waveIndex: Int,
    ): PlaneEnemySpec {
        val wave = waveIndex.coerceAtLeast(1)
        return PlaneEnemySpec(
            hp = max(1, (type.baseHp * difficulty.enemyHpMul * waveHpScale(wave)).roundToInt()),
            score = max(1, (type.baseScore * waveScoreScale(wave)).roundToInt()),
            radius = type.baseRadius,
            fireIntervalTicks = fireIntervalTicks(type, difficulty, wave),
            bulletSpeed = type.baseBulletSpeed * difficulty.bulletSpeedMul * waveBulletSpeedMul(wave),
        )
    }

    /** 敌人开火间隔（tick）：难度密度越高间隔越短，下限 [MIN_FIRE_INTERVAL_TICKS] 防止弹幕过密。 */
    fun fireIntervalTicks(
        type: PlaneEnemyType,
        difficulty: PlaneDifficulty,
        waveIndex: Int = 1,
    ): Int =
        if (type.baseFireIntervalTicks <= 0) {
            0
        } else {
            (
                type.baseFireIntervalTicks /
                    (difficulty.fireDensityMul * waveFireDensityMul(waveIndex))
            ).roundToInt().coerceAtLeast(MIN_FIRE_INTERVAL_TICKS)
        }

    /** 武器等级规格；越界自动钳制到 1..[MAX_WEAPON_LEVEL]。 */
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
                    speed = 165f,
                )
            2 ->
                PlaneWeaponSpec(
                    level = 2,
                    intervalTicks = 10,
                    damage = 1,
                    count = 2,
                    spreadRad = 0f,
                    offsetX = 2.2f,
                    speed = 165f,
                )
            3 ->
                PlaneWeaponSpec(
                    level = 3,
                    intervalTicks = 9,
                    damage = 1,
                    count = 3,
                    spreadRad = 0.18f,
                    offsetX = 0f,
                    speed = 160f,
                )
            4 ->
                PlaneWeaponSpec(
                    level = 4,
                    intervalTicks = 7,
                    damage = 3,
                    count = 1,
                    spreadRad = 0f,
                    offsetX = 0f,
                    speed = 205f,
                    piercing = true,
                )
            else ->
                // Lv5 双管穿透激光：火力顶格，吃满道具线的正反馈
                PlaneWeaponSpec(
                    level = 5,
                    intervalTicks = 6,
                    damage = 2,
                    count = 2,
                    spreadRad = 0f,
                    offsetX = 2.8f,
                    speed = 220f,
                    piercing = true,
                )
        }

    /**
     * 非 Boss 波次主题：每 20 波循环插入 3 种特殊内容波，其余走常规编队。
     * 纯函数，便于横幅文案与单测。
     */
    fun waveTheme(index: Int): PlaneWaveTheme {
        if (index <= 0) return PlaneWaveTheme.NORMAL
        if (index % PLANE_BOSS_EVERY == 0) return PlaneWaveTheme.BOSS
        return when (index % 20) {
            3 -> PlaneWaveTheme.SCOUT_SWARM
            8 -> PlaneWaveTheme.BOMBER_RUN
            13 -> PlaneWaveTheme.SNIPER_NEST
            else -> PlaneWaveTheme.NORMAL
        }
    }

    /** 是否展示「里程碑」横幅：第 9 / 19 / 29… 波（Boss 前一波，避开 Boss 与特殊主题冲突）。 */
    fun isMilestoneWave(index: Int): Boolean = index >= 9 && index % 10 == 9

    /**
     * Boss 种类：按 Boss 序号轮换（第 5→STANDARD、第 10→RINGMASTER、第 15→STRIKER…）。
     * 纯函数，与刷怪表一致。
     */
    fun bossKindForWave(index: Int): PlaneBossKind {
        if (index <= 0 || index % PLANE_BOSS_EVERY != 0) return PlaneBossKind.STANDARD
        val cycle = ((index / PLANE_BOSS_EVERY) - 1).coerceAtLeast(0)
        return PlaneBossKind.entries[cycle % PlaneBossKind.entries.size]
    }

    /**
     * 掉落权重：武器 48 / 炸弹 24 / 护盾 16 / 续命 12。
     * [roll] 为 [0, 100) 的确定性掷骰结果。
     */
    fun rollPowerUpKind(roll: Int): PlanePowerUpKind =
        when (roll.coerceIn(0, 99)) {
            in 0 until 48 -> PlanePowerUpKind.WEAPON
            in 48 until 72 -> PlanePowerUpKind.BOMB
            in 72 until 88 -> PlanePowerUpKind.SHIELD
            else -> PlanePowerUpKind.LIFE
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
     * 波次表（无尽模式）：静态定义，不消耗 RNG。
     *
     * - 每 [PLANE_BOSS_EVERY] 波为 Boss 波（仅一个 BOSS 条目，永久循环）；
     * - 其余波由 4 种编队循环，编队内的敌人数量随 stage 增长、到 [WAVE_FORMATION_STAGE_CAP] 后软封顶；
     * - 编队 2 从左右两侧切入，坦克从下方进场，其余从顶部入场。
     */
    fun wave(index: Int): PlaneWaveDef {
        if (index <= 0) return PlaneWaveDef(index, isBoss = false, spawns = emptyList())
        if (index % PLANE_BOSS_EVERY == 0) {
            return PlaneWaveDef(
                index = index,
                isBoss = true,
                theme = PlaneWaveTheme.BOSS,
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
        val theme = waveTheme(index)
        if (theme != PlaneWaveTheme.NORMAL) {
            return PlaneWaveDef(
                index = index,
                isBoss = false,
                theme = theme,
                spawns = specialWaveSpawns(index, theme).sortedBy { it.delayTicks },
            )
        }
        val stage = (index - 1) / PLANE_BOSS_EVERY + 1
        val cappedStage = stage.coerceAtMost(WAVE_FORMATION_STAGE_CAP)
        val local = (index - 1) % PLANE_BOSS_EVERY + 1
        val spawns = mutableListOf<PlaneSpawnDef>()
        when (local) {
            1 -> {
                val count = 4 + cappedStage
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
                val helis = (1 + stage / 2).coerceAtMost(4)
                for (i in 0 until helis) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.HELI,
                            x = 22f + i * 18f,
                            y = -7f,
                            delayTicks = 60 + i * 26,
                        )
                }
                val scouts = (1 + stage / 2).coerceAtMost(4)
                for (i in 0 until scouts) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.SCOUT,
                            x = 20f + i * 16f,
                            y = -5f,
                            delayTicks = 100 + i * 18,
                        )
                }
            }
            2 -> {
                val count = 4 + cappedStage
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
                for (i in 0 until 3 + cappedStage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.DIVER,
                            x = 18f + i * 18f,
                            y = -5f,
                            delayTicks = 130 + i * 22,
                        )
                }
                val tanks = (1 + stage / 2).coerceAtMost(3)
                for (i in 0 until tanks) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.TANK,
                            x = if (i % 2 == 0) 26f else 74f,
                            y = 0f,
                            delayTicks = 200 + i * 40,
                            vx = if (i % 2 == 0) 12f else -12f,
                            fromBottom = true,
                        )
                }
                val bombers = (1 + stage / 2).coerceAtMost(3)
                for (i in 0 until bombers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.BOMBER,
                            x = 28f + i * 22f,
                            y = -8f,
                            delayTicks = 250 + i * 28,
                        )
                }
            }
            3 -> {
                for (i in 0 until 4 + cappedStage) {
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
                val helis = (1 + stage / 2).coerceAtMost(4)
                for (i in 0 until helis) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.HELI,
                            x = 26f + i * 16f,
                            y = -7f,
                            delayTicks = 110 + i * 24,
                        )
                }
                spawns +=
                    PlaneSpawnDef(
                        type = PlaneEnemyType.TANK,
                        x = 50f,
                        y = 0f,
                        delayTicks = 240,
                        vx = if (stage % 2 == 0) 12f else -12f,
                        fromBottom = true,
                    )
                val snipers = (1 + stage / 2).coerceAtMost(3)
                for (i in 0 until snipers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.SNIPER,
                            x = if (i % 2 == 0) 34f else 66f,
                            y = -5f,
                            delayTicks = 190 + i * 30,
                            targetY = 40f + i * 4f,
                        )
                }
            }
            else -> {
                for (i in 0 until 3 + cappedStage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.GRUNT,
                            x = 15f + i * 25f,
                            y = -6f,
                            delayTicks = 20 + i * 14,
                        )
                }
                for (i in 0 until 3 + cappedStage) {
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
                for (i in 0 until 2 + cappedStage) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.DIVER,
                            x = 20f + i * 30f,
                            y = -5f,
                            delayTicks = 240 + i * 20,
                        )
                }
                val helis = (1 + stage / 2).coerceAtMost(3)
                for (i in 0 until helis) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.HELI,
                            x = 24f + i * 24f,
                            y = -7f,
                            delayTicks = 320 + i * 24,
                        )
                }
                val tanks = (1 + stage / 2).coerceAtMost(3)
                for (i in 0 until tanks) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.TANK,
                            x = if (i % 2 == 0) 30f else 70f,
                            y = 0f,
                            delayTicks = 380 + i * 30,
                            vx = if (i % 2 == 0) 12f else -12f,
                            fromBottom = true,
                        )
                }
                // 编队 4：三种新敌人各露一手，保证高波次也有辨识度
                spawns +=
                    PlaneSpawnDef(
                        type = PlaneEnemyType.SCOUT,
                        x = 42f,
                        y = -5f,
                        delayTicks = 160,
                    )
                spawns +=
                    PlaneSpawnDef(
                        type = PlaneEnemyType.BOMBER,
                        x = 58f,
                        y = -8f,
                        delayTicks = 280,
                    )
                spawns +=
                    PlaneSpawnDef(
                        type = PlaneEnemyType.SNIPER,
                        x = 50f,
                        y = -5f,
                        delayTicks = 340,
                        targetY = 38f,
                    )
            }
        }
        return PlaneWaveDef(
            index = index,
            isBoss = false,
            theme = PlaneWaveTheme.NORMAL,
            spawns = spawns.sortedBy { it.delayTicks },
        )
    }

    /** 特殊内容波刷怪表（主题已非 NORMAL）。 */
    private fun specialWaveSpawns(
        index: Int,
        theme: PlaneWaveTheme,
    ): List<PlaneSpawnDef> {
        val stage = ((index - 1) / PLANE_BOSS_EVERY + 1).coerceAtMost(WAVE_FORMATION_STAGE_CAP)
        val spawns = mutableListOf<PlaneSpawnDef>()
        when (theme) {
            PlaneWaveTheme.SCOUT_SWARM -> {
                // 行数 / 列数软封顶，避免高 stage 同屏 60+ 机把峰值与性能冲破
                val rows = (2 + stage / 2).coerceAtMost(4)
                val count = (5 + stage / 2).coerceAtMost(9)
                for (row in 0 until rows) {
                    for (i in 0 until count) {
                        spawns +=
                            PlaneSpawnDef(
                                type = PlaneEnemyType.SCOUT,
                                x = 12f + i * (76f / (count - 1).coerceAtLeast(1)),
                                y = -5f - row * 8f,
                                delayTicks = 12 + row * 28 + i * 6,
                            )
                    }
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 50f, -8f, 160 + rows * 20)
            }
            PlaneWaveTheme.BOMBER_RUN -> {
                val bombers = (3 + stage / 2).coerceAtMost(7)
                for (i in 0 until bombers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.BOMBER,
                            x = 18f + i * (64f / (bombers - 1).coerceAtLeast(1)),
                            y = -8f,
                            delayTicks = 20 + i * 22,
                        )
                }
                val divers = (2 + stage / 2).coerceAtMost(5)
                for (i in 0 until divers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.DIVER,
                            x = 22f + i * 18f,
                            y = -5f,
                            delayTicks = 140 + i * 18,
                        )
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 35f, -8f, 200)
                spawns += PlaneSpawnDef(PlaneEnemyType.TRANSPORT, 65f, -8f, 230)
            }
            PlaneWaveTheme.SNIPER_NEST -> {
                val snipers = (3 + stage / 2).coerceAtMost(6)
                for (i in 0 until snipers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.SNIPER,
                            x = 18f + i * (64f / (snipers - 1).coerceAtLeast(1)),
                            y = -5f,
                            delayTicks = 16 + i * 20,
                            targetY = 34f + (i % 3) * 8f,
                        )
                }
                spawns += PlaneSpawnDef(PlaneEnemyType.TURRET, 30f, -5f, 120, targetY = 28f)
                spawns += PlaneSpawnDef(PlaneEnemyType.TURRET, 70f, -5f, 150, targetY = 28f)
                val weavers = (2 + stage / 2).coerceAtMost(5)
                for (i in 0 until weavers) {
                    spawns +=
                        PlaneSpawnDef(
                            type = PlaneEnemyType.WEAVER,
                            x = 20f + i * 20f,
                            y = -5f,
                            delayTicks = 200 + i * 16,
                        )
                }
            }
            else -> Unit
        }
        return spawns
    }
}
