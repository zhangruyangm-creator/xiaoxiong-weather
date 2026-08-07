package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color

/** 迷宫主题：决定天空/地面/墙体配色与装饰物。 */
enum class MazeTheme {
    GARDEN,     // 晴天花园
    RAIN,       // 雨巷
    SNOW,       // 雪林
    NIGHT,      // 夜空
    FESTIVAL,   // 节日灯笼
    CLOUD       // 云端
}

data class MazeThemeStyle(
    val skyTop: Color,
    val skyBottom: Color,
    val floor: Color,
    val floorAlt: Color,
    val wall: Color,
    val wallTop: Color,
    val accent: Color,
    val ice: Color,
    val pathHint: Color
)

fun mazeThemeStyle(theme: MazeTheme): MazeThemeStyle = when (theme) {
    MazeTheme.GARDEN -> MazeThemeStyle(
        skyTop = Color(0xFF5BA8E8), skyBottom = Color(0xFFC8E8F8),
        floor = Color(0xFFB8E08A), floorAlt = Color(0xFFA8D478),
        wall = Color(0xFF7A5A3A), wallTop = Color(0xFF9A7A5A),
        accent = Color(0xFFFFD54F), ice = Color(0xFFB3E5FC),
        pathHint = Color(0xFFE8F5C8)
    )
    MazeTheme.RAIN -> MazeThemeStyle(
        skyTop = Color(0xFF4A5E78), skyBottom = Color(0xFF8AA0B8),
        floor = Color(0xFF6E7A88), floorAlt = Color(0xFF5E6A78),
        wall = Color(0xFF3A4555), wallTop = Color(0xFF556070),
        accent = Color(0xFF81D4FA), ice = Color(0xFF90CAF9),
        pathHint = Color(0xFF7A8898)
    )
    MazeTheme.SNOW -> MazeThemeStyle(
        skyTop = Color(0xFF8EB8D8), skyBottom = Color(0xFFE8F4FC),
        floor = Color(0xFFF0F6FA), floorAlt = Color(0xFFE0EAF2),
        wall = Color(0xFF6A8AAA), wallTop = Color(0xFF9AB8D0),
        accent = Color(0xFFFFAB91), ice = Color(0xFFB3E5FC),
        pathHint = Color(0xFFDCE8F0)
    )
    MazeTheme.NIGHT -> MazeThemeStyle(
        skyTop = Color(0xFF0D1B3A), skyBottom = Color(0xFF2A3F6E),
        floor = Color(0xFF2C3A55), floorAlt = Color(0xFF24324A),
        wall = Color(0xFF1A2438), wallTop = Color(0xFF3A4A6A),
        accent = Color(0xFFFFE082), ice = Color(0xFF5C6BC0),
        pathHint = Color(0xFF354560)
    )
    MazeTheme.FESTIVAL -> MazeThemeStyle(
        skyTop = Color(0xFF5C1A1A), skyBottom = Color(0xFFC45A3A),
        floor = Color(0xFFE8C070), floorAlt = Color(0xFFD8B060),
        wall = Color(0xFF8B2E2E), wallTop = Color(0xFFC04A4A),
        accent = Color(0xFFFFEB3B), ice = Color(0xFFFFCC80),
        pathHint = Color(0xFFF0D090)
    )
    MazeTheme.CLOUD -> MazeThemeStyle(
        skyTop = Color(0xFF7EC8F0), skyBottom = Color(0xFFE8F6FF),
        floor = Color(0xFFF5FBFF), floorAlt = Color(0xFFE0F0FA),
        wall = Color(0xFF90A4C0), wallTop = Color(0xFFC5D5E8),
        accent = Color(0xFFFFD54F), ice = Color(0xFFB2EBF2),
        pathHint = Color(0xFFEEF6FC)
    )
}

enum class MazeTile {
    FLOOR, WALL, START, EXIT, HONEY, ICE, KEY, LOCK, PORTAL
}

data class MazeCell(
    val tile: MazeTile,
    /** 传送门配对 id；仅 PORTAL 有意义。 */
    val portalId: Int = 0
)

/**
 * 一手关卡：用字符图画迷宫。
 * #墙 .地 S起点 E出口 H蜂蜜 I冰面 K钥匙 L锁门
 * 1-9 传送门（同号互传）
 */
data class MazeLevel(
    val id: Int,
    val titleZh: String,
    val titleEn: String,
    val theme: MazeTheme,
    val moveBudget: Int,
    val map: List<String>
) {
    val rows: Int get() = map.size
    val cols: Int get() = map.maxOf { it.length }

    fun parse(): Array<Array<MazeCell>> {
        val grid = Array(rows) { r ->
            Array(cols) { c ->
                val ch = map[r].getOrElse(c) { '#' }
                when (ch) {
                    '#' -> MazeCell(MazeTile.WALL)
                    'S' -> MazeCell(MazeTile.START)
                    'E' -> MazeCell(MazeTile.EXIT)
                    'H' -> MazeCell(MazeTile.HONEY)
                    'I' -> MazeCell(MazeTile.ICE)
                    'K' -> MazeCell(MazeTile.KEY)
                    'L' -> MazeCell(MazeTile.LOCK)
                    in '1'..'9' -> MazeCell(MazeTile.PORTAL, portalId = ch - '0')
                    else -> MazeCell(MazeTile.FLOOR)
                }
            }
        }
        return grid
    }
}

/** 12 关手写教程：其后战役关由 [mazeCampaignLevel] 种子生成。 */
val MAZE_HANDCRAFTED: List<MazeLevel> = listOf(
    // 1 花园入门
    MazeLevel(
        1, "阳光小径", "Sunny Path", MazeTheme.GARDEN, 40,
        listOf(
            "###########",
            "#S....H...#",
            "#.###.###.#",
            "#...#...#.#",
            "###.#.#.#.#",
            "#...#.#...#",
            "#.###.#####",
            "#.....H..E#",
            "###########"
        )
    ),
    // 2 花园蜂蜜
    MazeLevel(
        2, "花丛寻蜜", "Honey Garden", MazeTheme.GARDEN, 55,
        listOf(
            "#############",
            "#S#...H...#E#",
            "#.#.#####.#.#",
            "#...H...#...#",
            "###.###.#.###",
            "#...#...H...#",
            "#.#.#.#####.#",
            "#H#.......#.#",
            "#.#########.#",
            "#...........#",
            "#############"
        )
    ),
    // 3 雨巷冰面入门
    MazeLevel(
        3, "雨巷初滑", "Rainy Slide", MazeTheme.RAIN, 50,
        listOf(
            "###########",
            "#S..IIII..#",
            "#.##I##I###",
            "#..HI..H..#",
            "###I##I##.#",
            "#..IIII..E#",
            "###########"
        )
    ),
    // 4 雨巷钥匙
    MazeLevel(
        4, "湿漉钥匙", "Wet Key", MazeTheme.RAIN, 70,
        listOf(
            "#############",
            "#S....#...H.#",
            "#.###.#.###.#",
            "#.#K#...#L#.#",
            "#.#.#####.#.#",
            "#H#.......#E#",
            "#.#########.#",
            "#.....H.....#",
            "#############"
        )
    ),
    // 5 雪林
    MazeLevel(
        5, "雪林曲径", "Snow Trail", MazeTheme.SNOW, 65,
        listOf(
            "#############",
            "#S..III..H..#",
            "#.##I##.###.#",
            "#H.#I..I..#.#",
            "###.#.##I.#.#",
            "#...#...I...#",
            "#.#####.#####",
            "#.....H....E#",
            "#############"
        )
    ),
    // 6 雪林双钥
    MazeLevel(
        6, "冰窖双钥", "Twin Keys", MazeTheme.SNOW, 90,
        listOf(
            "###############",
            "#S..#...H...#E#",
            "#.#.#.#####L#.#",
            "#K#...#...#...#",
            "###.###.#.#.###",
            "#...#H#.#.#.K.#",
            "#.#.#.#.#.#####",
            "#.#...#L......#",
            "#.#############",
            "#......H......#",
            "###############"
        )
    ),
    // 7 夜空传送（同号传送门成对）
    MazeLevel(
        7, "星门初开", "Star Gate", MazeTheme.NIGHT, 55,
        listOf(
            "###########",
            "#S....H..1#",
            "#.#######.#",
            "#.#.....#.#",
            "#.#.###.#.#",
            "#...#H#...#",
            "#####.#####",
            "#1....H..E#",
            "###########"
        )
    ),
    // 8 夜空综合
    MazeLevel(
        8, "银河回廊", "Galaxy Hall", MazeTheme.NIGHT, 100,
        listOf(
            "###############",
            "#S..1#...H..1E#",
            "#.##.#.#####.##",
            "#H.#...#...#..#",
            "###.###.#.#.#.#",
            "#...#K#.#L#.#.#",
            "#.#.#.#.#.#...#",
            "#.#...#...###.#",
            "#.#####.###H#.#",
            "#.....2.....2.#",
            "###############"
        )
    ),
    // 9 节日灯笼
    MazeLevel(
        9, "灯笼巷弄", "Lantern Lane", MazeTheme.FESTIVAL, 80,
        listOf(
            "#############",
            "#S.H.#...H.E#",
            "#.###.#.#.###",
            "#...#...#...#",
            "###.#.###.#.#",
            "#K#.......#.#",
            "#.#.#####L#.#",
            "#...H.......#",
            "#############"
        )
    ),
    // 10 节日大餐
    MazeLevel(
        10, "年味迷宫", "New Year Maze", MazeTheme.FESTIVAL, 110,
        listOf(
            "#################",
            "#S..H.#.....H..E#",
            "#.###.#.#######.#",
            "#.#K#...#...#L#.#",
            "#.#.#####.#.#.#.#",
            "#...#.1...#...#.#",
            "#####.#.#####.#.#",
            "#...H.#.#...#.#.#",
            "#.#####.#.#.#...#",
            "#.......#.#.###.#",
            "#.#######.#..H#.#",
            "#1........#####.#",
            "#.........H.....#",
            "#################"
        )
    ),
    // 11 云端
    MazeLevel(
        11, "云上散步", "Cloud Walk", MazeTheme.CLOUD, 75,
        listOf(
            "#############",
            "#S..III..H.1#",
            "#.##I##.###.#",
            "#..HI..I..#.#",
            "###.####I.#.#",
            "#1..IIII....#",
            "#.#########.#",
            "#.....H....E#",
            "#############"
        )
    ),
    // 12 云端终章
    MazeLevel(
        12, "天际终章", "Sky Finale", MazeTheme.CLOUD, 140,
        listOf(
            "#################",
            "#S..H.....H....E#",
            "#.###.#######L#.#",
            "#.#K#.#...#...#.#",
            "#.#.#.#.#.#.###.#",
            "#...#.1.#.#..H..#",
            "#####.#.#.#######",
            "#.H...#.#.......#",
            "#.#####.#.#####.#",
            "#.#.....2.....#.#",
            "#.#.#########.#.#",
            "#...#.K.#L#...#.#",
            "#####.#.#.#####.#",
            "#1....#2#......H#",
            "#################"
        )
    )
)
