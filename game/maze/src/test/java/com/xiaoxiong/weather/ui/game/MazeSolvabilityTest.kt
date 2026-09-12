package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁/钥匙数量、传送门成对，并对战役/无尽关做冰面可解性校验。
 */
class MazeSolvabilityTest {
    private data class Counts(
        val locks: Int,
        val keys: Int,
        val portalBad: List<Int>,
        val honeys: Int,
    )

    private fun counts(level: MazeLevel): Counts {
        val grid = level.parse()
        var locks = 0
        var keys = 0
        var honeys = 0
        val portals = mutableMapOf<Int, Int>()
        for (r in 0 until level.rows) {
            for (c in 0 until level.cols) {
                when (grid[r][c].tile) {
                    MazeTile.LOCK -> locks++
                    MazeTile.KEY -> keys++
                    MazeTile.HONEY -> honeys++
                    MazeTile.PORTAL -> {
                        val id = grid[r][c].portalId
                        portals[id] = (portals[id] ?: 0) + 1
                    }
                    else -> Unit
                }
            }
        }
        val bad = portals.filter { it.value != 2 }.map { it.key }
        return Counts(locks, keys, bad, honeys)
    }

    @Test
    fun handcraftedLevels_areSolvable() {
        val problems = mutableListOf<String>()
        for (lv in MAZE_HANDCRAFTED) {
            if (!mazeMapSolvable(lv.map)) {
                problems += "handcrafted id=${lv.id} ${lv.titleZh}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun campaignLevels_locksHaveKeys_portalsPaired_solvable() {
        val problems = mutableListOf<String>()
        for (i in 0 until MAZE_CAMPAIGN_SIZE) {
            val lv = mazeCampaignLevel(i)
            val c = counts(lv)
            if (c.locks > c.keys) {
                problems += "campaign[$i] locks=${c.locks} keys=${c.keys}"
            }
            if (c.portalBad.isNotEmpty()) {
                problems += "campaign[$i] badPortals=${c.portalBad}"
            }
            if (!mazeMapSolvable(lv.map)) {
                problems += "campaign[$i] unsolvable h=${c.honeys} L=${c.locks}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun endlessWaves_locksHaveKeys_portalsPaired_solvable() {
        val problems = mutableListOf<String>()
        val seeds = longArrayOf(1L, 42L, 99L, 12345L, 0xBEEFL, 7L, 999L, 2026L)
        for (seed in seeds) {
            for (wave in 1..40) {
                val diff = (8 + wave * 2).coerceAtMost(45)
                val lv =
                    generateMazeLevel(
                        seed = seed xor (wave.toLong() * 0x9E3779B9L),
                        id = wave,
                        difficulty = diff,
                    )
                val c = counts(lv)
                if (c.locks > c.keys) {
                    problems += "seed=$seed wave=$wave locks=${c.locks} keys=${c.keys} h=${c.honeys}"
                }
                if (c.portalBad.isNotEmpty()) {
                    problems += "seed=$seed wave=$wave badPortals=${c.portalBad}"
                }
                if (!mazeMapSolvable(lv.map)) {
                    problems += "seed=$seed wave=$wave unsolvable h=${c.honeys} L=${c.locks}"
                }
            }
        }
        assertTrue(
            "Found ${problems.size}\n" + problems.take(40).joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun campaignLevels_honeyWithinSolveBudget_neverTooLarge() {
        val problems = mutableListOf<String>()
        for (i in 0 until MAZE_CAMPAIGN_SIZE) {
            val lv = mazeCampaignLevel(i)
            val h = counts(lv).honeys
            if (h > MAX_SOLVE_HONEY) {
                problems += "campaign[$i] honey=$h"
            }
            if (mazeSolve(lv.map) is MazeSolveOutcome.TooLarge) {
                problems += "campaign[$i] returned TooLarge (honey=$h)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun generatedHardLevels_honeyWithinSolveBudget_neverTooLarge() {
        val problems = mutableListOf<String>()
        val seeds = longArrayOf(1L, 42L, 777L, 2026L)
        for (seed in seeds) {
            for (wave in 1..60) {
                val diff = MazeGameLogic.endlessDifficulty(wave)
                val lv =
                    generateMazeLevel(
                        seed = seed xor (wave.toLong() * 0x9E3779B9L),
                        id = wave,
                        difficulty = diff,
                    )
                val h = counts(lv).honeys
                if (h > MAX_SOLVE_HONEY) {
                    problems += "seed=$seed wave=$wave honey=$h"
                }
                if (mazeSolve(lv.map) is MazeSolveOutcome.TooLarge) {
                    problems += "seed=$seed wave=$wave tooLarge honey=$h"
                }
            }
        }
        assertTrue(
            "Found ${problems.size}\n" + problems.take(40).joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun mazeSolve_atHoneyBudget_solvesInsteadOfTooLarge() {
        // 恰好 MAX_SOLVE_HONEY 罐蜂蜜：仍应给出精确解，而不是 TooLarge
        val map =
            listOf(
                "#".repeat(MAX_SOLVE_HONEY + 4),
                "#S" + "H".repeat(MAX_SOLVE_HONEY) + "E#",
                "#".repeat(MAX_SOLVE_HONEY + 4),
            )
        val solved = mazeSolve(map)
        assertTrue("正好 $MAX_SOLVE_HONEY 罐蜂蜜应可解：$solved", solved is MazeSolveOutcome.Solved)
    }

    @Test
    fun mazeSolve_beyondHoneyBudget_isTooLarge() {
        val map =
            listOf(
                "#".repeat(MAX_SOLVE_HONEY + 5),
                "#S" + "H".repeat(MAX_SOLVE_HONEY + 1) + "E#",
                "#".repeat(MAX_SOLVE_HONEY + 5),
            )
        assertEquals(MazeSolveOutcome.TooLarge, mazeSolve(map))
    }

    @Test
    fun handcraftedLevels_budgetCoversMinMoves() {
        val problems = mutableListOf<String>()
        for (lv in MAZE_HANDCRAFTED) {
            val solved = mazeSolve(lv.map)
            if (solved is MazeSolveOutcome.Solved && solved.minMoves > lv.moveBudget) {
                problems += "id=${lv.id} ${lv.titleZh} min=${solved.minMoves} budget=${lv.moveBudget}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun generatedLevels_budgetCoversMinMoves() {
        val problems = mutableListOf<String>()
        val seeds = longArrayOf(3L, 42L, 777L, 2026L)
        for (seed in seeds) {
            for (wave in 1..25) {
                val diff = (8 + wave * 2).coerceAtMost(45)
                val lv =
                    generateMazeLevel(
                        seed = seed xor (wave.toLong() * 0x9E3779B9L),
                        id = wave,
                        difficulty = diff,
                    )
                val solved = mazeSolve(lv.map)
                if (solved is MazeSolveOutcome.Solved && solved.minMoves > lv.moveBudget) {
                    problems += "seed=$seed wave=$wave min=${solved.minMoves} budget=${lv.moveBudget}"
                }
            }
        }
        assertTrue(
            "预算必须覆盖最小步数：\n" + problems.take(20).joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun simpleIceAndHoney_solvable() {
        val map =
            listOf(
                "#######",
                "#S..H.#",
                "#.###.#",
                "#..I.E#",
                "#######",
            )
        assertTrue(mazeMapSolvable(map))
    }

    @Test
    fun lockWithoutKey_unsolvable() {
        // 蜂蜜与出口都在锁后，且地图无钥匙
        val map =
            listOf(
                "########",
                "#S..LH.E#",
                "########",
            )
        assertTrue(!mazeMapSolvable(map))
    }
}
