package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开局命数设置 + 跟手触摸映射的纯逻辑单测（不依赖 Compose / Android 运行环境）。
 */
class PlaneSettingsTest {
    // ---------------------------------------------------------------------
    // 开局命数
    // ---------------------------------------------------------------------

    @Test
    fun clampStartLives_boundsTo1To9() {
        assertEquals(1, planeClampStartLives(Int.MIN_VALUE))
        assertEquals(1, planeClampStartLives(-1))
        assertEquals(1, planeClampStartLives(1))
        assertEquals(5, planeClampStartLives(5))
        assertEquals(9, planeClampStartLives(9))
        assertEquals(9, planeClampStartLives(10))
        assertEquals(9, planeClampStartLives(Int.MAX_VALUE))
    }

    @Test
    fun startLivesFromPrefs_defaultsToThree_andReadsPersistedValue() {
        val prefs = FakePlanePrefs()
        assertEquals("无存档时使用默认 3 条", PlaneConfig.START_LIVES, planeStartLivesOf(prefs))

        prefs.edit().putInt(PLANE_START_LIVES_KEY, 7).apply()
        assertEquals("读回持久化的 7 条", 7, planeStartLivesOf(prefs))

        prefs.edit().putInt(PLANE_START_LIVES_KEY, 99).apply()
        assertEquals("越界存档读取时夹到 9", PlaneConfig.START_LIVES_MAX, planeStartLivesOf(prefs))

        prefs.edit().putInt(PLANE_START_LIVES_KEY, 0).apply()
        assertEquals("越界存档读取时夹到 1", PlaneConfig.START_LIVES_MIN, planeStartLivesOf(prefs))
    }

    @Test
    fun startLives_roundTripsThroughPrefs_andAppliesOnlyOnNewGame() {
        val prefs = FakePlanePrefs()
        val game = PlaneGame().apply { resize(600f, 1080f) }

        // 设置 5 条并开新局：新局生效
        prefs.edit().putInt(PLANE_START_LIVES_KEY, 5).apply()
        game.startLives = planeStartLivesOf(prefs)
        game.startGame(seed = 42L)
        assertEquals(5, game.player.lives)

        // 局中改设置（仅写入 prefs / game.startLives）不改变当前命数
        prefs.edit().putInt(PLANE_START_LIVES_KEY, 2).apply()
        game.startLives = planeStartLivesOf(prefs)
        assertEquals("局中改设置不影响当前对局", 5, game.player.lives)

        // 下一局生效
        game.startGame(seed = 43L)
        assertEquals(2, game.player.lives)
    }

    // ---------------------------------------------------------------------
    // 跟手触摸映射
    // ---------------------------------------------------------------------

    @Test
    fun fingerOffset_isWithinReasonableLogicalRange() {
        assertTrue(
            "逻辑场宽 100，跟手偏移应在 8..12 之间（实测 $PLANE_FINGER_OFFSET_LOGICAL）",
            PLANE_FINGER_OFFSET_LOGICAL in 8f..12f,
        )
        assertTrue("偏移不应超过玩家半径的 10 倍", PLANE_FINGER_OFFSET_LOGICAL <= PlaneConfig.PLAYER_RADIUS * 10f)
    }

    @Test
    fun touchTargetY_subtractsOffset_andPassesThroughNonFinite() {
        assertEquals(60f - PLANE_FINGER_OFFSET_LOGICAL, planeTouchTargetY(60f), 0f)
        assertEquals(0f, planeTouchTargetY(PLANE_FINGER_OFFSET_LOGICAL), 0f)
        assertTrue(planeTouchTargetY(Float.NaN).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, planeTouchTargetY(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun fingerOffset_keepsPlaneClearOfFingerAtFieldBottom() {
        assertTrue(
            "贴底拖动时偏移应大于机身视觉半径（offset=$PLANE_FINGER_OFFSET_LOGICAL visual=$PLANE_PLAYER_VISUAL_RADIUS）",
            PLANE_FINGER_OFFSET_LOGICAL >= PLANE_PLAYER_VISUAL_RADIUS,
        )
    }

    @Test
    fun rapidExtremeDrags_stayFinite_andClamped_onWideAndTallCanvases() {
        for ((width, height) in listOf(3200f to 480f, 480f to 3200f)) {
            val game = PlaneGame().apply { resize(width, height) }
            game.startGame(seed = 9L)
            repeat(200) { i ->
                val x = if (i % 2 == 0) -1e9f else 1e9f
                val y = if (i % 3 == 0) 1e9f else -1e9f
                game.movePlayerTo(game.toLogicalX(x), planeTouchTargetY(game.toLogicalY(y)))
                assertTrue("玩家坐标必须有限", game.player.x.isFinite() && game.player.y.isFinite())
                assertTrue(
                    "玩家 x 越界: ${game.player.x}",
                    game.player.x in PlaneConfig.PLAYER_RADIUS..(PLANE_LOGICAL_WIDTH - PlaneConfig.PLAYER_RADIUS),
                )
                assertTrue(
                    "玩家 y 越界: ${game.player.y}",
                    game.player.y in PlaneConfig.PLAYER_RADIUS * 2f..(game.fieldH - PlaneConfig.PLAYER_RADIUS * 2f),
                )
            }
        }
    }

    @Test
    fun touchTargetY_clampsInsideField_whenNearTopAndBottomEdges() {
        val game = PlaneGame().apply { resize(600f, 1080f) }
        game.startGame(seed = 7L)
        // 触摸顶部：目标越界应被钳制到玩家上半场边界
        game.movePlayerTo(50f, planeTouchTargetY(0f))
        assertEquals(PlaneConfig.PLAYER_RADIUS * 2f, game.player.y, 0f)
        // 触摸底部：目标越界应被钳制到场底边界
        game.movePlayerTo(50f, planeTouchTargetY(game.fieldH * 2f))
        assertEquals(game.fieldH - PlaneConfig.PLAYER_RADIUS * 2f, game.player.y, 0f)
        // 场中的触摸按偏移映射
        val middle = game.fieldH * 0.5f
        game.movePlayerTo(50f, planeTouchTargetY(middle))
        assertEquals(middle - PLANE_FINGER_OFFSET_LOGICAL, game.player.y, 1e-4f)
    }
}
