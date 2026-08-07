package com.xiaoxiong.weather.ui.game

/**
 * 带冰面滑行 / 钥匙锁 / 传送门的可达性判定（与 [MazeGame.tryMove] 规则对齐）。
 * 用于生成器拒收死关，以及单测。
 */
internal fun mazeMapSolvable(map: List<String>): Boolean {
    if (map.isEmpty()) return false
    val rows = map.size
    val cols = map.maxOf { it.length }
    val g = Array(rows) { r -> CharArray(cols) { c -> map[r].getOrElse(c) { '#' } } }

    var sr = -1
    var sc = -1
    val honeys = mutableListOf<Pair<Int, Int>>()
    val keyCells = mutableListOf<Pair<Int, Int>>()
    val lockCells = mutableListOf<Pair<Int, Int>>()
    val portals = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            when (val ch = g[r][c]) {
                'S' -> { sr = r; sc = c }
                'H' -> honeys.add(r to c)
                'K' -> keyCells.add(r to c)
                'L' -> lockCells.add(r to c)
                in '1'..'9' -> portals.getOrPut(ch - '0') { mutableListOf() }.add(r to c)
            }
        }
    }
    if (sr < 0) return false
    // 状态空间过大时放行，避免生成卡住（战役关蜂蜜 ≤ 10）
    if (honeys.size > 12 || keyCells.size > 4 || lockCells.size > 4) return true

    val honeyIndex = honeys.withIndex().associate { (i, p) -> p to i }
    val keyIndex = keyCells.withIndex().associate { (i, p) -> p to i }
    val lockIndex = lockCells.withIndex().associate { (i, p) -> p to i }
    val allHoneyMask = if (honeys.isEmpty()) 0 else (1 shl honeys.size) - 1

    fun walkable(r: Int, c: Int, keys: Int, lockOpen: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return false
        return when (g[r][c]) {
            '#' -> false
            'L' -> {
                val idx = lockIndex[r to c]
                if (idx != null && (lockOpen and (1 shl idx)) != 0) true
                else keys > 0
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
        dc: Int
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
                    val idx = honeyIndex[r to c]
                    if (idx != null) honey = honey or (1 shl idx)
                }
                'K' -> {
                    val idx = keyIndex[r to c]
                    if (idx != null && (keyMask and (1 shl idx)) == 0) {
                        keyMask = keyMask or (1 shl idx)
                        // 与 MazeGame 一致不设上限；入口已保证钥匙 ≤ 4，4 bit 装得下
                        keys++
                    }
                }
                'L' -> {
                    val idx = lockIndex[r to c]
                    if (idx != null && (lockOpen and (1 shl idx)) == 0 && keys > 0) {
                        keys--
                        lockOpen = lockOpen or (1 shl idx)
                    }
                }
                in '1'..'9' -> {
                    val id = ch - '0'
                    val ends = portals[id]
                    if (ends != null && ends.size == 2) {
                        val other = if (ends[0] == r to c) ends[1] else ends[0]
                        r = other.first
                        c = other.second
                        sliding = false
                    }
                }
                'E' -> if (honey == allHoneyMask) {
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
        lockOpen: Int
    ): Long =
        (r.toLong() shl 48) or
            (c.toLong() shl 40) or
            (keys.toLong() shl 36) or
            (honey.toLong() shl 20) or
            (keyMask.toLong() shl 12) or
            lockOpen.toLong()

    val q = ArrayDeque<Long>()
    val seen = HashSet<Long>()
    val start = pack(sr, sc, 0, 0, 0, 0)
    q.add(start)
    seen.add(start)
    val dirs = intArrayOf(-1, 0, 1, 0, 0, -1, 0, 1)
    while (q.isNotEmpty()) {
        val st = q.removeFirst()
        val r = (st shr 48).toInt()
        val c = ((st shr 40) and 0xFF).toInt()
        val keys = ((st shr 36) and 0xF).toInt()
        val honey = ((st shr 20) and 0xFFFF).toInt()
        val keyMask = ((st shr 12) and 0xFF).toInt()
        val lockOpen = (st and 0xFFF).toInt()
        if (g[r][c] == 'E' && honey == allHoneyMask) return true
        for (i in 0 until 4) {
            val next = step(
                r, c, keys, honey, keyMask, lockOpen,
                dirs[i * 2], dirs[i * 2 + 1]
            ) ?: continue
            if (next[6] == 1) return true
            val p = pack(next[0], next[1], next[2], next[3], next[4], next[5])
            if (seen.add(p)) q.add(p)
        }
    }
    return false
}
