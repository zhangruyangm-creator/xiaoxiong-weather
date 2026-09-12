package com.xiaoxiong.weather.ui.game

/** 教学课的目标判定方式。 */
enum class Goal {
    /** 任意一颗目标球落袋即可。 */
    POT_ANY,

    /** 指定球（[PoolLesson.setup] 中第一颗非白球）落袋。 */
    POT_ONE,

    /** 清空台面全部目标球。 */
    CLEAR,

    /** 击球后白球回缩到起始位置之后（缩杆成功）。 */
    DRAW_BACK,

    /** 至少一颗目标球先碰库、再落袋（翻袋）。 */
    BANK_SHOT,
}

/**
 * 一节新手教学课。
 *
 * [setup] 已包含白球（[PoolBallKind.CUE]，id 0）；[goal] 决定通过条件，[tipZh]/[tipEn]
 * 是进入对局后顶部展示的简短提示。教学复用关卡的摆球思路与校验（见 [PoolTutorial.isLayoutValid]）。
 */
data class PoolLesson(
    val id: Int,
    val nameZh: String,
    val nameEn: String,
    val setup: List<PoolBallSetup>,
    val goal: Goal,
    val tipZh: String,
    val tipEn: String,
) {
    /** 白球摆位（约定每节恰有一颗）。 */
    val cue: PoolBallSetup?
        get() = setup.firstOrNull { it.kind == PoolBallKind.CUE }

    /** 目标球数量（不含白球）。 */
    val targetCount: Int
        get() = setup.count { it.kind != PoolBallKind.CUE }

    /** 指定球编号：[Goal.POT_ONE] 判定用；无目标球为 null。 */
    val targetNumber: Int?
        get() = setup.firstOrNull { it.kind != PoolBallKind.CUE }?.id
}

/**
 * 新手教学静态数据（纯 Kotlin，无 Compose / R 依赖，可裸 JVM 单测）。
 *
 * 5 节由易到难：瞄准 → 力度 → 高低杆 → 侧旋 → 翻袋；每节固定摆球 + 文字提示 + 目标判定。
 */
object PoolTutorial {
    /** 单颗目标球摆位。 */
    private data class Target(
        val x: Float,
        val y: Float,
        val id: Int,
        val kind: PoolBallKind,
    )

    /** 5 节教学（返回的列表不可变）。 */
    fun lessons(): List<PoolLesson> = LESSONS

    /**
     * 复用关卡校验：恰一颗白球、编号唯一、在台内、不压袋口、球间距 ≥ 2.2r。
     * 通过构造一个临时 [PoolChallenge] 直接调用 [PoolChallenges.isLayoutValid]，避免修改关卡文件。
     */
    fun isLayoutValid(lesson: PoolLesson): Boolean =
        PoolChallenges.isLayoutValid(
            PoolChallenge(
                id = lesson.id,
                nameZh = lesson.nameZh,
                nameEn = lesson.nameEn,
                par = 1,
                balls = lesson.setup,
            ),
        )

    private fun t(
        x: Float,
        y: Float,
        id: Int,
        kind: PoolBallKind,
    ): Target = Target(x, y, id, kind)

    private fun lesson(
        id: Int,
        nameZh: String,
        nameEn: String,
        goal: Goal,
        cueX: Float,
        cueY: Float,
        targets: List<Target>,
        tipZh: String,
        tipEn: String,
    ): PoolLesson {
        val balls = ArrayList<PoolBallSetup>(targets.size + 1)
        targets.forEach { balls += PoolBallSetup(it.id, it.kind, it.x, it.y) }
        balls += PoolBallSetup(0, PoolBallKind.CUE, cueX, cueY)
        return PoolLesson(id, nameZh, nameEn, balls, goal, tipZh, tipEn)
    }

    private val LESSONS: List<PoolLesson> =
        listOf(
            lesson(
                id = 1,
                nameZh = "瞄准与击球",
                nameEn = "Aim & Shoot",
                goal = Goal.POT_ANY,
                cueX = 0.635f,
                cueY = 0.50f,
                targets = listOf(t(0.635f, 1.00f, 1, PoolBallKind.SOLID)),
                tipZh = "拖动球桌瞄准，把球对准袋口后松手击球；先用中等力度试着让目标球落袋。",
                tipEn = "Drag on the table to aim, line the ball up with a pocket, then release. Start with medium power.",
            ),
            lesson(
                id = 2,
                nameZh = "力度控制",
                nameEn = "Power Control",
                goal = Goal.POT_ONE,
                cueX = 0.635f,
                cueY = 0.45f,
                targets = listOf(t(0.635f, 1.90f, 2, PoolBallKind.SOLID)),
                tipZh = "目标球离你较远：力度太小进不了、太大白球容易跟着落袋。控制拖拽距离试试。",
                tipEn = "The target is far away: too little power won't reach, too much may scratch. Control your drag distance.",
            ),
            lesson(
                id = 3,
                nameZh = "高低杆（跟/缩）",
                nameEn = "Follow & Draw",
                goal = Goal.DRAW_BACK,
                cueX = 0.635f,
                cueY = 1.00f,
                targets = listOf(t(0.635f, 1.50f, 1, PoolBallKind.SOLID)),
                tipZh = "把白球控件的小点往下拖（低杆/缩杆），击中目标球后白球会往回走。",
                tipEn = "Drag the cue-ball pad dot downward (draw). After contact the cue ball should come back toward you.",
            ),
            lesson(
                id = 4,
                nameZh = "侧旋",
                nameEn = "Side Spin",
                goal = Goal.CLEAR,
                cueX = 0.635f,
                cueY = 0.45f,
                targets = listOf(t(0.635f, 1.20f, 1, PoolBallKind.SOLID)),
                tipZh = "左右拖动白球控件的小点制造侧塞，体会白球受偏杆影响后的走位；清台即通过。",
                tipEn = "Drag the cue-ball pad dot sideways to apply side spin and see how the cue ball deflects. Clear the table to pass.",
            ),
            lesson(
                id = 5,
                nameZh = "一库翻袋",
                nameEn = "One-Cushion Bank",
                goal = Goal.BANK_SHOT,
                cueX = 0.45f,
                cueY = 0.70f,
                targets = listOf(t(0.95f, 1.30f, 1, PoolBallKind.SOLID)),
                tipZh = "目标球不直对袋口：让它先撞库边、反弹后再落袋。瞄准反射角试试翻袋。",
                tipEn = "The target is off the pocket line: bank it off a cushion so it rebounds into the pocket.",
            ),
        )

    init {
        // 开发期自检：所有课程布局必须合法（与关卡同一套规则），失败即暴露数据错误。
        val invalid = LESSONS.filterNot { isLayoutValid(it) }
        require(invalid.isEmpty()) { "invalid lesson layouts: ${invalid.map { it.id }}" }
        require(LESSONS.map { it.id }.toSet().size == LESSONS.size) { "lesson ids must be unique" }
    }
}
