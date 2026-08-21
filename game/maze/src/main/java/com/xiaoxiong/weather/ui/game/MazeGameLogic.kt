package com.xiaoxiong.weather.ui.game

/**
 * 小熊迷宫的纯逻辑层：与 Compose / Android 完全解耦，可被普通 JUnit 单测。
 *
 * 这里只放"输入 → 输出 确定"的纯函数，供 [MazeGame]（Compose 状态容器/存档适配）
 * 与 [MazeGenerator] 复用。任何涉及 UI 触发、SharedPreferences 或 Compose state 的
 * 状态都不要写进本文件。
 */
object MazeGameLogic {
    // ── 难度换算 ────────────────────────────────────────────────

    /** 无尽第 [wave] 波的难度；越高越难，封顶 45。 */
    fun endlessDifficulty(wave: Int): Int = (8 + wave.coerceAtLeast(1) * 2).coerceAtMost(45)

    /**
     * 按实际步数与预算结算 1..3 星：
     * 步数 ≤ budget → 3 星；≤ budget×1.35 → 2 星；否则 1 星。
     */
    fun starsFor(
        moves: Int,
        budget: Int,
    ): Int =
        when {
            moves <= budget -> 3
            moves <= (budget * 1.35f).toInt() -> 2
            else -> 1
        }

    /**
     * 步数预算估算。与 [MazeGenerator] 的最短路 × 系数公式一致，
     * 集中在这里避免散落多处难保持一致。
     * @param shortestPathLen 起点到出口的最短路步数
     * @param honeyCount 本关蜂蜜数
     * @param difficulty 难度
     */
    fun moveBudget(
        shortestPathLen: Int,
        honeyCount: Int,
        difficulty: Int,
    ): Int {
        val pathLen = shortestPathLen.coerceAtLeast(8)
        return (pathLen * 2.2f + honeyCount * 6 + difficulty * 2).toInt().coerceIn(30, 220)
    }

    // ── 可走性 / 冰面滑行 ──────────────────────────────────────

    /**
     * 冰面滑行单步模拟（纯逻辑，可单测）：
     * 从 `(r,c)` 朝方向迈入 `(nr,nc)`，若落点为冰面 `I` 则沿同方向持续滑行，
     * 直到下一步不可通行（撞墙/越界）为止，返回最终停留位置。
     *
     * 语义与 [MazeGame.tryMove]/[MazeSolvability.step] 的冰滑规则严格一致：
     * 滑行只被"下一格不可走"阻断，途中经过的事件格（蜂蜜/钥匙/锁/传送门/出口）
     * 由调用方另行结算，不影响滑行本身。
     *
     * @return 停留位置 `(row, col)`；若第一步即不可走返回 null。
     */
    fun simulateIceStep(
        level: MazeLevel,
        r: Int,
        c: Int,
        dr: Int,
        dc: Int,
    ): Pair<Int, Int>? {
        val grid = level.parse()
        val rows = level.rows
        val cols = level.cols

        fun inBounds(
            rr: Int,
            cc: Int,
        ) = rr in 0 until rows && cc in 0 until cols

        fun walkable(
            rr: Int,
            cc: Int,
        ): Boolean = inBounds(rr, cc) && grid[rr][cc].tile != MazeTile.WALL

        // 第一步：目标格必须可走
        val nr = r + dr
        val nc = c + dc
        if (!walkable(nr, nc)) return null

        var curR = nr
        var curC = nc
        // 从落点开始滑：若落下是冰面则继续
        while (grid[curR][curC].tile == MazeTile.ICE) {
            val sr = curR + dr
            val sc = curC + dc
            if (!walkable(sr, sc)) break
            curR = sr
            curC = sc
        }
        return curR to curC
    }
}
