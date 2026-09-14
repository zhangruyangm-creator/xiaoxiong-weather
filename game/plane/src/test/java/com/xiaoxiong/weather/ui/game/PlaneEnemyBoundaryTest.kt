package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新敌人（TANK / HELI）边界回归：
 * - TANK 从场底 `fieldH + 4` 进场，不能被每 tick 离屏规则或恢复后的 purge 误删；
 * - TANK 只在地面线附近开火（修复：此前会从画面外冒炮弹）；
 * - resize 后 TANK 会回到新地面线（修复：此前会悬停在旧地面线）；
 * - HELI 悬停超时后必然离场，不卡波。
 */
class PlaneEnemyBoundaryTest {
    private fun newGame(): PlaneGame =
        PlaneGame().apply {
            resize(600f, 1080f)
            difficulty = PlaneDifficulty.STANDARD
        }

    private fun addTank(
        game: PlaneGame,
        y: Float,
        vx: Float = 0f,
        fireTimerTicks: Int = 99999,
    ): PlaneEnemy {
        val spec = PlaneConfig.enemySpec(PlaneEnemyType.TANK, game.difficulty, 1)
        return PlaneEnemy(
            id = 1,
            type = PlaneEnemyType.TANK,
            x = 50f,
            y = y,
            vx = vx,
            vy = 0f,
            hp = spec.hp,
            maxHp = spec.hp,
            fireTimerTicks = fireTimerTicks,
        ).also { game.enemies += it }
    }

    @Test
    fun tank_spawnedBelowField_survivesPerTickCull_andReachesGroundLine() {
        val game = newGame()
        game.startGame(seed = 1L)
        game.enemies.clear()
        val tank = addTank(game, y = game.fieldH + 4f, vx = 12f)
        game.advanceTicks(1)
        assertTrue("场底 +4 进场不能被离屏规则立即剔除", game.enemies.contains(tank))
        assertTrue("Spawn 后仍应在场内下方", tank.y <= game.fieldH + PlaneConfig.OFFSCREEN_MARGIN)
        val groundY = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN
        var reachedGround = false
        repeat(60 * 60) { tick ->
            game.enemies.retainAll { it === tank }
            if (!game.enemies.contains(tank)) return@repeat
            game.advanceTicks(1)
            if (tank.y <= groundY + 0.5f) reachedGround = true
            if (tick == 0) assertFalse("爬升途中不应开火", game.enemyBullets.any { it.vy < 0f && it.x == tank.x })
        }
        assertTrue("坦克应爬升到地面线", reachedGround)
    }

    @Test
    fun tank_neverFiresWhileBelowField() {
        val game = newGame()
        game.startGame(seed = 2L)
        game.enemies.clear()
        val tank = addTank(game, y = game.fieldH + 4f, vx = 0f, fireTimerTicks = 0)
        var fired = false
        repeat(60 * 4) {
            game.enemies.retainAll { it === tank }
            if (!game.enemies.contains(tank)) game.enemies += tank
            game.enemyBullets.clear()
            game.advanceTicks(1)
            for (bullet in game.enemyBullets) {
                assertTrue(
                    "炮弹必须从场内生成（bullet.y=${bullet.y} fieldH=${game.fieldH}）",
                    bullet.y <= game.fieldH,
                )
                fired = true
            }
        }
        assertTrue("贴地后应开火（否则测试没覆盖到）", fired)
        assertTrue("坦克应已贴地", tank.y <= game.fieldH - PlaneLogic.TANK_GROUND_MARGIN + 1f)
    }

    @Test
    fun tank_afterTallerResize_descendsBackToGroundLine() {
        val game = newGame()
        game.startGame(seed = 3L)
        game.enemies.clear()
        val oldGroundY = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN
        val tank = addTank(game, y = oldGroundY, vx = 0f)
        game.resize(600f, 1400f)
        val newGroundY = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN
        assertTrue("测试前提：场高应变大", newGroundY > oldGroundY + 5f)
        repeat(60 * 10) {
            game.enemies.retainAll { it === tank }
            if (!game.enemies.contains(tank)) return@repeat
            game.advanceTicks(1)
        }
        assertEquals("resize 变大后应下降回新地面线", newGroundY, tank.y, 0.6f)
    }

    @Test
    fun tank_afterShorterResize_isRemovedNotStuck() {
        val game = newGame()
        game.startGame(seed = 4L)
        game.enemies.clear()
        val tank = addTank(game, y = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN, vx = 0f)
        game.resize(600f, 500f) // fieldH 收窄 -> 原地面线在场外
        game.advanceTicks(1)
        assertTrue("场高收窄后坦克应被清理而不是卡在场外", game.enemies.none { it === tank })
    }

    @Test
    fun heli_hoverHoldExpires_andLeavesField() {
        val game = newGame()
        game.startGame(seed = 5L)
        game.enemies.clear()
        // 高血量避免被玩家自动火力击毁，只观察悬停 / 离场路径
        val heli =
            PlaneEnemy(
                id = 2,
                type = PlaneEnemyType.HELI,
                x = 50f,
                y = -7f,
                vx = 0f,
                vy = 0f,
                hp = 99999,
                maxHp = 99999,
                fireTimerTicks = 99999,
            )
        game.enemies += heli
        var hovered = false
        var removed = false
        repeat(60 * 60) {
            game.enemies.retainAll { it === heli }
            if (!game.enemies.contains(heli)) {
                removed = true
                return@repeat
            }
            game.advanceTicks(1)
            if (heli.y >= PlaneLogic.HELI_HOVER_Y) hovered = true
        }
        assertTrue("直升机应先悬停", hovered)
        assertTrue("悬停超时后应离场（否则卡波）", removed)
    }

    @Test
    fun tankRam_atGroundLine_hitsPlayer_andIsDestroyed() {
        val game = newGame()
        game.startGame(seed = 7L)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        game.enemies.clear()
        game.enemyBullets.clear()
        val tank = addTank(game, y = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN, vx = 0f, fireTimerTicks = 99999)
        tank.hp = 500
        tank.maxHp = 500
        val lives = game.player.lives
        game.movePlayerTo(tank.x, game.fieldH - PlaneConfig.PLAYER_RADIUS * 2f)
        game.advanceTicks(1)
        assertEquals("贴地坦克应能撞到玩家", lives - 1, game.player.lives)
        assertTrue("撞机后坦克应被击毁（非 Boss 撞机即毁）", game.enemies.none { it === tank })
        assertTrue(game.pendingHapticPlayerHit)
    }

    @Test
    fun tankSnapshot_atSpawnRoundTrips_onSameCanvas() {
        val prefs = FakePlanePrefs()
        val game = newGame()
        game.startGame(seed = 6L)
        game.enemies.clear()
        addTank(game, y = game.fieldH + 4f, vx = 12f, fireTimerTicks = 90)
        game.saveSnapshot(prefs)

        val restored = PlaneGame().apply { resize(600f, 1080f) }
        assertTrue(restored.restoreSnapshot(prefs))
        val tank = restored.enemies.firstOrNull { it.type == PlaneEnemyType.TANK }
        assertTrue("同画布恢复不能把场底进场坦克清掉", tank != null)
        assertEquals(12f, tank!!.vx, 0f)
    }
}
