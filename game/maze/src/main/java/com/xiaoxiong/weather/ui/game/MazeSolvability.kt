package com.xiaoxiong.weather.ui.game

/**
 * 求解状态空间允许的最大蜂蜜数：蜂蜜收集状态为 2^n，超过该值 BFS 状态数在主线程不可接受。
 * 生成器必须保证「蜂蜜总数（含锁旁附加）≤ [MAX_SOLVE_HONEY]」，
 * 这样 [mazeSolve] 对所有生成关卡都能给出精确结果，而不会退化为 [MazeSolveOutcome.TooLarge]。
 */
internal const val MAX_SOLVE_HONEY = 10

/**
 * 求解结果。
 *  - [Solved]：可解，附带「收齐全部蜂蜜并到达出口」的最小移动数与第一步方向（起点即终点时为 null）。
 *  - [Unsolvable]：状态空间内确认无解。
 *  - [TooLarge]：蜂蜜/钥匙/锁过多，状态空间过大而放弃求解（调用方自行决定放行或拒绝）。
 */
internal sealed interface MazeSolveOutcome {
    data class Solved(
        val minMoves: Int,
        val firstDir: MazeDir?,
    ) : MazeSolveOutcome

    data object Unsolvable : MazeSolveOutcome

    data object TooLarge : MazeSolveOutcome
}

/**
 * 带冰面滑行 / 钥匙锁 / 传送门的可达性判定（与 [MazeGame.tryMove] 规则对齐）。
 * 用于生成器拒收死关、提示（hint）与单测。
 *
 * 从地图解析起点、蜂蜜、钥匙、锁与传送门；[initialKeys] 为开局已持有的钥匙数
 * （提示场景下玩家可能已经捡了钥匙），初始蜂蜜/钥匙/锁收集状态均为空。
 * BFS 的每「步」对应玩家按一次方向键（冰面滑行算一步），返回最小步数与第一步方向。
 */
internal fun mazeSolve(
    map: List<String>,
    startR: Int = -1,
    startC: Int = -1,
    initialKeys: Int = 0,
): MazeSolveOutcome {
    if (map.isEmpty()) return MazeSolveOutcome.Unsolvable
    val rows = map.size
    val cols = map.maxOf { it.length }
    val g = Array(rows) { r -> CharArray(cols) { c -> map[r].getOrElse(c) { '#' } } }

    var sr = startR
    var sc = startC
    if (sr !in 0 until rows || sc !in 0 until cols || g[sr][sc] == '#') {
        sr = -1
        sc = -1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (g[r][c] == 'S') {
                    sr = r
                    sc = c
                }
            }
        }
    }
    val honeys = mutableListOf<Pair<Int, Int>>()
    val keyCells = mutableListOf<Pair<Int, Int>>()
    val lockCells = mutableListOf<Pair<Int, Int>>()
    // 传送门两端用 r*cols+c 的 Int 编码，避免热循环内 Pair 分配
    val portals = mutableMapOf<Int, MutableList<Int>>()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            when (val ch = g[r][c]) {
                'H' -> honeys.add(r to c)
                'K' -> keyCells.add(r to c)
                'L' -> lockCells.add(r to c)
                in '1'..'9' -> portals.getOrPut(ch - '0') { mutableListOf() }.add(r * cols + c)
            }
        }
    }
    if (sr < 0) return MazeSolveOutcome.Unsolvable
    // 状态空间过大时交给调用方定夺（生成器拒收，提示返回 TooLarge）
    if (honeys.size > MAX_SOLVE_HONEY || keyCells.size > 4 || lockCells.size > 4) return MazeSolveOutcome.TooLarge

    // 索引表用 r*cols+c 的 Int 键，遍历/查询不再分配 Pair
    val honeyIndex = IntArray(rows * cols) { -1 }
    val keyIndex = IntArray(rows * cols) { -1 }
    val lockIndex = IntArray(rows * cols) { -1 }
    honeys.forEachIndexed { i, p -> honeyIndex[p.first * cols + p.second] = i }
    keyCells.forEachIndexed { i, p -> keyIndex[p.first * cols + p.second] = i }
    lockCells.forEachIndexed { i, p -> lockIndex[p.first * cols + p.second] = i }
    val allHoneyMask = if (honeys.isEmpty()) 0 else (1 shl honeys.size) - 1

    fun walkable(
        r: Int,
        c: Int,
        keys: Int,
        lockOpen: Int,
    ): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return false
        return when (g[r][c]) {
            '#' -> false
            'L' -> {
                val idx = lockIndex[r * cols + c]
                if (idx >= 0 && (lockOpen and (1 shl idx)) != 0) {
                    true
                } else {
                    keys > 0
                }
            }
            else -> true
        }
    }

    /** 朝 dir 主动走一步（含冰滑与事件）；不可走返回 null。末位 1 表示通关。 */
    fun step(
        r0: Int,
        c0: Int,
        keys0: Int,
        honey0: Int,
        keyMask0: Int,
        lockOpen0: Int,
        dr: Int,
        dc: Int,
    ): IntArray? {
        var r = r0
        var c = c0
        var keys = keys0
        var honey = honey0
        var keyMask = keyMask0
        var lockOpen = lockOpen0
        val nr = r + dr
        val nc = c + dc
        if (!walkable(nr, nc, keys, lockOpen)) return null
        r = nr
        c = nc
        var sliding = g[r][c] == 'I'
        var guard = 0
        while (true) {
            when (val ch = g[r][c]) {
                'H' -> {
                    val idx = honeyIndex[r * cols + c]
                    if (idx >= 0) honey = honey or (1 shl idx)
                }
                'K' -> {
                    val idx = keyIndex[r * cols + c]
                    if (idx >= 0 && (keyMask and (1 shl idx)) == 0) {
                        keyMask = keyMask or (1 shl idx)
                        // 与 MazeGame 一致不设上限；入口已保证钥匙 ≤ 4，4 bit 装得下
                        keys++
                    }
                }
                'L' -> {
                    val idx = lockIndex[r * cols + c]
                    if (idx >= 0 && (lockOpen and (1 shl idx)) == 0 && keys > 0) {
                        keys--
                        lockOpen = lockOpen or (1 shl idx)
                    }
                }
                in '1'..'9' -> {
                    val id = ch - '0'
                    val ends = portals[id]
                    if (ends != null && ends.size == 2) {
                        val here = r * cols + c
                        val other = if (ends[0] == here) ends[1] else ends[0]
                        r = other / cols
                        c = other % cols
                        sliding = false
                    }
                }
                'E' ->
                    if (honey == allHoneyMask) {
                        return intArrayOf(r, c, keys, honey, keyMask, lockOpen, 1)
                    }
            }
            if (!sliding) break
            val sr2 = r + dr
            val sc2 = c + dc
            if (!walkable(sr2, sc2, keys, lockOpen)) {
                sliding = false
                break
            }
            r = sr2
            c = sc2
            sliding = g[r][c] == 'I'
            if (++guard > rows * cols) break
        }
        return intArrayOf(r, c, keys, honey, keyMask, lockOpen, 0)
    }

    fun pack(
        r: Int,
        c: Int,
        keys: Int,
        honey: Int,
        keyMask: Int,
        lockOpen: Int,
    ): Long =
        (r.toLong() shl 48) or
            (c.toLong() shl 40) or
            (keys.toLong() shl 36) or
            (honey.toLong() shl 20) or
            (keyMask.toLong() shl 12) or
            lockOpen.toLong()

    val q = ArrayDeque<Long>()
    val dist = HashMap<Long, Int>()
    // 每个状态首次到达所走的第一步方向，用于把「最小步数」还原成「下一步怎么按」
    val firstDir = HashMap<Long, MazeDir>()
    val dirs =
        arrayOf(
            MazeDir.UP,
            MazeDir.DOWN,
            MazeDir.LEFT,
            MazeDir.RIGHT,
        )
    val start = pack(sr, sc, initialKeys, 0, 0, 0)
    dist[start] = 0
    q.add(start)
    while (q.isNotEmpty()) {
        val st = q.removeFirst()
        val steps = dist.getValue(st)
        val r = (st shr 48).toInt()
        val c = ((st shr 40) and 0xFF).toInt()
        val keys = ((st shr 36) and 0xF).toInt()
        val honey = ((st shr 20) and 0xFFFF).toInt()
        val keyMask = ((st shr 12) and 0xFF).toInt()
        val lockOpen = (st and 0xFFF).toInt()
        if (g[r][c] == 'E' && honey == allHoneyMask) {
            return MazeSolveOutcome.Solved(steps, firstDir[st])
        }
        for (dir in dirs) {
            val next =
                step(
                    r,
                    c,
                    keys,
                    honey,
                    keyMask,
                    lockOpen,
                    dir.dr,
                    dir.dc,
                ) ?: continue
            if (next[6] == 1) {
                return MazeSolveOutcome.Solved(steps + 1, firstDir[st] ?: dir)
            }
            val p = pack(next[0], next[1], next[2], next[3], next[4], next[5])
            if (p !in dist) {
                dist[p] = steps + 1
                firstDir[p] = firstDir[st] ?: dir
                q.add(p)
            }
        }
    }
    return MazeSolveOutcome.Unsolvable
}

/** 从初始地图判定可解性（状态空间过大时放行，供生成器使用）。 */
internal fun mazeMapSolvable(map: List<String>): Boolean =
    when (mazeSolve(map)) {
        is MazeSolveOutcome.Solved, MazeSolveOutcome.TooLarge -> true
        MazeSolveOutcome.Unsolvable -> false
    }

/**
 * 从当前对局状态求「收齐全部蜂蜜并到达出口」的最小步数与下一步方向。
 * 传入的 [map] 应为**当前**棋盘（已消耗的蜂蜜/钥匙/已开的锁都已是 '.'），
 * [initialKeys] 为玩家当前持有的钥匙数。无解或状态过大返回 null。
 */
internal fun mazeSolveFromState(
    map: List<String>,
    startR: Int,
    startC: Int,
    initialKeys: Int = 0,
): MazeSolveOutcome.Solved? =
    when (val outcome = mazeSolve(map, startR, startC, initialKeys)) {
        is MazeSolveOutcome.Solved -> outcome
        else -> null
    }
