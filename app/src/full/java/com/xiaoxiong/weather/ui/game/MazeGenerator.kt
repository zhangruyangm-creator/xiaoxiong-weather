package com.xiaoxiong.weather.ui.game

import kotlin.random.Random

/** 战役总关数：前 12 关手写，其后种子随机生成（可复现）。 */
const val MAZE_HANDCRAFTED_COUNT = 12
const val MAZE_CAMPAIGN_SIZE = 60

private val GEN_TITLES = mapOf(
    MazeTheme.GARDEN to listOf("蜂语小径", "青草迷阵", "阳光裂谷", "花粉走廊", "绿篱深处"),
    MazeTheme.RAIN to listOf("雨幕回廊", "积水深巷", "伞下迷宫", "潮汐石路", "檐滴曲径"),
    MazeTheme.SNOW to listOf("霜晶洞穴", "雪狐足迹", "冰棱栈道", "白雾林间", "寒星地宫"),
    MazeTheme.NIGHT to listOf("暗星回旋", "月影地牢", "夜枭密道", "银河裂隙", "寂静星井"),
    MazeTheme.FESTIVAL to listOf("灯火迷城", "爆竹暗道", "锦缎曲坊", "年兽巢穴", "喜糖迷宫"),
    MazeTheme.CLOUD to listOf("云絮迷宫", "风筝栈道", "虹桥密径", "天阶回廊", "浮岛迷阵")
)

/**
 * 选关列表所需的轻量信息（不生成迷宫，O(1) 可得）。
 * 只有真正开始游玩某一关时才调用 [mazeCampaignLevel] 生成完整地图。
 */
data class MazeLevelMeta(
    val id: Int,
    val titleZh: String,
    val titleEn: String,
    val theme: MazeTheme
)

/**
 * 取选关列表元数据：手写关直接读常量，生成关只按 id 推主题与标题，
 * 避免进入选关界面就批量生成全部关卡。
 */
fun mazeCampaignMeta(index: Int): MazeLevelMeta {
    val i = index.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1)
    if (i < MAZE_HANDCRAFTED.size) {
        val lv = MAZE_HANDCRAFTED[i]
        return MazeLevelMeta(lv.id, lv.titleZh, lv.titleEn, lv.theme)
    }
    val id = i + 1
    val theme = MazeTheme.entries[id % MazeTheme.entries.size]
    val titles = GEN_TITLES[theme] ?: listOf("迷雾迷宫")
    return MazeLevelMeta(id, titles[id % titles.size], "Maze $id", theme)
}

/**
 * 取战役关卡：0..11 手写，12..59 用固定种子生成（同一关永远同一张图）。
 */
fun mazeCampaignLevel(index: Int): MazeLevel {
    val i = index.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1)
    if (i < MAZE_HANDCRAFTED.size) return MAZE_HANDCRAFTED[i]
    val difficulty = i // 越高越难
    val seed = 0xBEEF0000L xor (i.toLong() * 0x9E3779B9L)
    return generateMazeLevel(seed = seed, id = i + 1, difficulty = difficulty)
}

/**
 * DFS 挖通迷宫 + 按难度洒蜂蜜/冰面/钥匙锁/传送门。
 * 生成后做冰面可解性校验，最多换种子重试；仍失败则去掉冰面再生成。
 * @param difficulty 大约等于战役下标，或无尽波次换算值
 */
fun generateMazeLevel(
    seed: Long,
    id: Int,
    difficulty: Int,
    themeOverride: MazeTheme? = null
): MazeLevel {
    repeat(24) { attempt ->
        val level = generateMazeLevelOnce(
            seed = seed xor (attempt.toLong() * 0x9E3779B97F4A7C15UL.toLong()),
            id = id,
            difficulty = difficulty,
            themeOverride = themeOverride,
            allowIce = true
        )
        if (mazeMapSolvable(level.map)) return level
    }
    // 兜底：无冰面，极大概率可解
    return generateMazeLevelOnce(
        seed = seed xor 0xDEADBEEFL,
        id = id,
        difficulty = difficulty,
        themeOverride = themeOverride,
        allowIce = false
    )
}

private fun generateMazeLevelOnce(
    seed: Long,
    id: Int,
    difficulty: Int,
    themeOverride: MazeTheme?,
    allowIce: Boolean
): MazeLevel {
    val rng = Random(seed)
    val theme = themeOverride ?: MazeTheme.entries[id % MazeTheme.entries.size]

    // 奇数尺寸，便于标准迷宫雕刻
    val cols = (11 + (difficulty / 5) * 2).coerceIn(11, 21).let { if (it % 2 == 0) it + 1 else it }
    val rows = (9 + (difficulty / 6) * 2).coerceIn(9, 19).let { if (it % 2 == 0) it + 1 else it }

    val g = Array(rows) { CharArray(cols) { '#' } }

    fun carve(r: Int, c: Int) {
        g[r][c] = '.'
    }

    // 递归回溯：只在奇数格落脚
    val dr = intArrayOf(-2, 2, 0, 0)
    val dc = intArrayOf(0, 0, -2, 2)
    fun dfs(r: Int, c: Int) {
        carve(r, c)
        val order = intArrayOf(0, 1, 2, 3).also { it.shuffle(rng) }
        for (i in order) {
            val nr = r + dr[i]
            val nc = c + dc[i]
            if (nr in 1 until rows - 1 && nc in 1 until cols - 1 && g[nr][nc] == '#') {
                carve(r + dr[i] / 2, c + dc[i] / 2)
                dfs(nr, nc)
            }
        }
    }
    dfs(1, 1)

    // 地板列表
    fun floors(): MutableList<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        for (r in 1 until rows - 1) {
            for (c in 1 until cols - 1) {
                if (g[r][c] != '#') list.add(r to c)
            }
        }
        return list
    }

    // 非墙邻格数：等于 1 即死胡同
    fun openNeighborCount(r: Int, c: Int): Int {
        var n = 0
        val d4 = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in d4) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until rows && nc in 0 until cols && g[nr][nc] != '#') n++
        }
        return n
    }

    // BFS 距离
    fun bfsDist(sr: Int, sc: Int): Array<IntArray> {
        val dist = Array(rows) { IntArray(cols) { -1 } }
        val q = ArrayDeque<Pair<Int, Int>>()
        dist[sr][sc] = 0
        q.add(sr to sc)
        val d4 = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (q.isNotEmpty()) {
            val (r, c) = q.removeFirst()
            for ((ddr, ddc) in d4) {
                val nr = r + ddr
                val nc = c + ddc
                if (nr !in 0 until rows || nc !in 0 until cols) continue
                if (g[nr][nc] == '#' || dist[nr][nc] >= 0) continue
                dist[nr][nc] = dist[r][c] + 1
                q.add(nr to nc)
            }
        }
        return dist
    }

    val start = 1 to 1
    g[start.first][start.second] = 'S'
    val distFromStart = bfsDist(start.first, start.second)
    var exit = start
    var best = -1
    for (r in 1 until rows - 1) {
        for (c in 1 until cols - 1) {
            if (distFromStart[r][c] > best) {
                best = distFromStart[r][c]
                exit = r to c
            }
        }
    }
    g[exit.first][exit.second] = 'E'

    val open = floors().filter { (r, c) ->
        g[r][c] == '.' && distFromStart[r][c] >= 0
    }.shuffled(rng).toMutableList()

    // 蜂蜜：随难度增多
    val honeyCount = (2 + difficulty / 4).coerceIn(2, 10)
    repeat(honeyCount) {
        if (open.isEmpty()) return@repeat
        val (r, c) = open.removeAt(0)
        g[r][c] = 'H'
    }

    // 冰面：难度 ≥ 2 开始出现（可解性由外层重试验证）
    if (allowIce && difficulty >= 2) {
        val iceCount = (3 + difficulty / 3).coerceIn(3, 18)
        repeat(iceCount) {
            if (open.isEmpty()) return@repeat
            val (r, c) = open.removeAt(0)
            // 避开紧贴起点
            if (distFromStart[r][c] <= 2) return@repeat
            g[r][c] = 'I'
        }
    }

    // 钥匙 + 锁：难度 ≥ 4；把一把蜂蜜关进死胡同
    if (difficulty >= 4 && open.size >= 3) {
        // 只选真死胡同（唯一开放邻格）：锁落在唯一通路上才锁得住蜂蜜，
        // 岔路口随机放锁会被从另一侧绕开
        val candidates = open.filter { (r, c) ->
            distFromStart[r][c] >= 4 && openNeighborCount(r, c) == 1
        }
        if (candidates.isNotEmpty()) {
            val (hr, hc) = candidates.first()
            open.remove(hr to hc)
            g[hr][hc] = 'H'
            // 在通向它的一格放锁：取离它最近的开放邻格
            val neigh = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                .map { (dr, dc) -> hr + dr to hc + dc }
                .filter { (r, c) ->
                    r in 0 until rows && c in 0 until cols && g[r][c] == '.'
                }
            if (neigh.isNotEmpty()) {
                val (lr, lc) = neigh.random(rng)
                open.remove(lr to lc)
                g[lr][lc] = 'L'
                val lockDist = distFromStart[lr][lc]
                // 钥匙必须在锁的起点侧；找不到则整组撤回（与第二把锁一致）
                val keySpot = open.firstOrNull { (r, c) ->
                    val d = distFromStart[r][c]
                    d in 2 until lockDist.coerceAtLeast(3)
                } ?: open.firstOrNull { (r, c) ->
                    distFromStart[r][c] in 1 until lockDist
                }
                if (keySpot != null) {
                    open.remove(keySpot)
                    g[keySpot.first][keySpot.second] = 'K'
                } else {
                    g[lr][lc] = '.'
                    open.add(lr to lc)
                    g[hr][hc] = '.'
                    open.add(hr to hc)
                }
            } else {
                g[hr][hc] = '.'
                open.add(hr to hc)
            }
        }
        // 高难度第二把锁：同样保证钥匙在锁的起点侧，避免死关
        if (difficulty >= 10 && open.size >= 4) {
            val candidates2 = open.filter { (r, c) ->
                distFromStart[r][c] >= 5 && openNeighborCount(r, c) == 1
            }
            if (candidates2.isNotEmpty()) {
                val (hr, hc) = candidates2.first()
                open.remove(hr to hc)
                g[hr][hc] = 'H'
                val neigh = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    .map { (dr, dc) -> hr + dr to hc + dc }
                    .filter { (r, c) ->
                        r in 0 until rows && c in 0 until cols && g[r][c] == '.'
                    }
                if (neigh.isNotEmpty()) {
                    val (lr, lc) = neigh.random(rng)
                    open.remove(lr to lc)
                    g[lr][lc] = 'L'
                    val lockDist = distFromStart[lr][lc]
                    val keySpot = open.firstOrNull { (r, c) ->
                        val d = distFromStart[r][c]
                        d in 2 until lockDist.coerceAtLeast(3)
                    } ?: open.firstOrNull { (r, c) ->
                        distFromStart[r][c] in 1 until lockDist
                    }
                    if (keySpot != null) {
                        open.remove(keySpot)
                        g[keySpot.first][keySpot.second] = 'K'
                    } else {
                        // 找不到安全钥匙位则撤回，宁缺毋死锁
                        g[lr][lc] = '.'
                        open.add(lr to lc)
                        g[hr][hc] = '.'
                        open.add(hr to hc)
                    }
                } else {
                    g[hr][hc] = '.'
                    open.add(hr to hc)
                }
            }
        }
    }

    // 传送门：难度 ≥ 6，1~3 对
    if (difficulty >= 6) {
        val pairs = (1 + (difficulty - 6) / 8).coerceIn(1, 3)
        var portalId = 1
        repeat(pairs) {
            if (open.size < 2) return@repeat
            val a = open.removeAt(0)
            val b = open.removeAt(open.lastIndex)
            g[a.first][a.second] = ('0' + portalId)
            g[b.first][b.second] = ('0' + portalId)
            portalId++
        }
    }

    // 难度高时再挖几条捷径，避免太堵
    if (difficulty >= 8) {
        repeat(2 + difficulty / 15) {
            val r = rng.nextInt(1, rows - 1)
            val c = rng.nextInt(1, cols - 1)
            if (g[r][c] == '#') {
                val nOpen = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    .count { (dr, dc) ->
                        val nr = r + dr; val nc = c + dc
                        nr in 0 until rows && nc in 0 until cols && g[nr][nc] != '#'
                    }
                if (nOpen >= 2) g[r][c] = '.'
            }
        }
    }

    val map = g.map { String(it) }
    // 预算：最短路 × 系数 + 蜂蜜绕路
    val pathLen = best.coerceAtLeast(8)
    val budget = (pathLen * 2.2f + honeyCount * 6 + difficulty * 2).toInt().coerceIn(30, 220)

    val titles = GEN_TITLES[theme] ?: listOf("迷雾迷宫")
    val title = titles[id % titles.size]

    return MazeLevel(
        id = id,
        titleZh = title,
        titleEn = "Maze $id",
        theme = theme,
        moveBudget = budget,
        map = map
    )
}
