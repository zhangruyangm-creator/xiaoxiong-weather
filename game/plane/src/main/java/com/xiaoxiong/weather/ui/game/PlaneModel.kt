package com.xiaoxiong.weather.ui.game

/** 对局阶段：READY（标题）/ PLAYING / PAUSED（配合 common GamePauseState）/ SETTLED（死亡结算）。 */
enum class PlanePhase { READY, PLAYING, PAUSED, SETTLED }

/** 子弹形状：圆 / 胶囊（激光 / 横滚弹）/ 矩形（宽幅弹幕）。 */
enum class PlaneBulletShape { CIRCLE, CAPSULE, RECT }

/** 子弹阵营。 */
enum class PlaneBulletOwner { PLAYER, ENEMY }

/**
 * 道具类型（仅允许末尾追加，快照按 `.name` 编解码）。
 * P=武器 / B=炸弹 / S=护盾 / L=续命。
 */
enum class PlanePowerUpKind { WEAPON, BOMB, SHIELD, LIFE }

/**
 * 玩家战机运行时状态。
 *
 * 坐标是逻辑坐标（[PLANE_LOGICAL_WIDTH] × fieldH）；屏幕输入由 Screen 层映射后写入。
 */
class PlanePlayer(
    var x: Float = PLANE_LOGICAL_WIDTH * 0.5f,
    var y: Float = 0f,
    var lives: Int = PlaneConfig.START_LIVES,
    var bombs: Int = PlaneConfig.START_BOMBS,
    var weaponLevel: Int = PlaneConfig.MIN_WEAPON_LEVEL,
    var invulnTicks: Int = 0,
    var fireCooldownTicks: Int = 0,
    /** 火力过载剩余 tick：满级再吃 P 时触发，射速提升。 */
    var overdriveTicks: Int = 0,
    var alive: Boolean = true,
)

/**
 * 敌人实例。
 *
 * 行为由 [PlaneLogic.enemyVelocity] 依据 [type] / [ageTicks] / [side] 等字段纯函数推导；
 * 这里只保存状态，便于快照编解码。
 */
class PlaneEnemy(
    val id: Int,
    val type: PlaneEnemyType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var hp: Int,
    var maxHp: Int,
    var fireTimerTicks: Int,
    var ageTicks: Int = 0,
    /** Boss 阶段（0..2），非 Boss 恒为 0。 */
    var bossPhase: Int = 0,
    /** Boss 种类；非 Boss 恒为 [PlaneBossKind.STANDARD]。 */
    var bossKind: PlaneBossKind = PlaneBossKind.STANDARD,
    /** 摆渡兵的摆动中心（侧向入场后由游戏层在入场结束时重设）。 */
    var baseX: Float = x,
    /** 炮台停靠高度。 */
    var targetY: Float = 0f,
    /** 入场方向：-1 左 / 0 顶 / 1 右。 */
    var side: Int = 0,
    /** 侧向入场持续 tick：期间保持入场速度，之后转入常规行为。 */
    var entryTicks: Int = 0,
) {
    val isBoss: Boolean get() = type == PlaneEnemyType.BOSS

    val radius: Float get() = type.baseRadius
}

/**
 * 子弹实例。
 *
 * 形状约定：
 * - [PlaneBulletShape.CIRCLE]：半径 [radius]；
 * - [PlaneBulletShape.CAPSULE]：沿 [angle] 方向、半长 [halfLength]、半径 [radius]；
 * - [PlaneBulletShape.RECT]：[radius] 为半宽、[halfLength] 为半高（轴对齐，忽略 [angle]）。
 */
class PlaneBullet(
    val id: Int,
    val owner: PlaneBulletOwner,
    val shape: PlaneBulletShape,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Int,
    val radius: Float,
    val halfLength: Float = 0f,
    val angle: Float = 0f,
    var ttlTicks: Int = 0,
    val piercing: Boolean = false,
    /** Boss 专属弹（品红警示配色标记）；小兵 / 炮台弹恒为 false。 */
    val boss: Boolean = false,
) {
    /** 擦弹只计一次。 */
    var grazed: Boolean = false

    /** 穿透弹已命中的敌人 id（每敌只结算一次）。 */
    val hitEnemyIds: MutableSet<Int> = mutableSetOf()
}

/** 掉落道具：P / B / S / L。 */
class PlanePowerUp(
    val id: Int,
    val kind: PlanePowerUpKind,
    var x: Float,
    var y: Float,
    var vy: Float = 18f,
    var ttlTicks: Int = 600,
)

/** 爆炸 / 火花粒子（纯表现，不参与判定）。 */
class PlaneEffect(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var lifeTicks: Int,
    val color: Int,
    val size: Float = 0.9f,
)

/**
 * 一 tick 内产生的反馈事件快照（由 [PlaneGame.consumeEvents] 读取后复位）。
 *
 * 各标志在 tick 内是布尔去重：同一 tick 多次触发只置位一次，屏幕消费后不会重复播放。
 * [win] 保留给未来「有终点」的模式；无尽模式永远不置位（只有 [lose] 会结束对局）。
 */
data class PlaneEvents(
    val shot: Boolean = false,
    val explosion: Boolean = false,
    val powerup: Boolean = false,
    val playerHit: Boolean = false,
    val bomb: Boolean = false,
    val bossWarning: Boolean = false,
    val bossDown: Boolean = false,
    val graze: Boolean = false,
    val win: Boolean = false,
    val lose: Boolean = false,
)
