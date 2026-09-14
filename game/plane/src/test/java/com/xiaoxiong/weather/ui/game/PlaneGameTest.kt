package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaneGame] 运行时单测：状态机、自动开火、武器升降、炸弹、Boss 阶段、胜负、
 * 事件消费、暂停与确定性。
 */
class PlaneGameTest {
    private fun newGame(difficulty: PlaneDifficulty = PlaneDifficulty.STANDARD): PlaneGame =
        PlaneGame().apply {
            resize(600f, 1080f)
            this.difficulty = difficulty
        }

    private fun enemyBulletAtPlayer(
        game: PlaneGame,
        id: Int,
    ): PlaneBullet =
        PlaneBullet(
            id = id,
            owner = PlaneBulletOwner.ENEMY,
            shape = PlaneBulletShape.CIRCLE,
            x = game.player.x,
            y = game.player.y,
            vx = 0f,
            vy = 0f,
            damage = 1,
            radius = PlaneConfig.ENEMY_BULLET_RADIUS,
        )

    private fun playerBulletAt(
        id: Int,
        x: Float,
        y: Float,
        damage: Int,
    ): PlaneBullet =
        PlaneBullet(
            id = id,
            owner = PlaneBulletOwner.PLAYER,
            shape = PlaneBulletShape.CIRCLE,
            x = x,
            y = y,
            vx = 0f,
            vy = 0f,
            damage = damage,
            radius = PlaneConfig.PLAYER_BULLET_RADIUS,
        )

    // ---------------------------------------------------------------------
    // 开局 / 基础
    // ---------------------------------------------------------------------

    @Test
    fun startGame_initialState() {
        val game = newGame()
        game.startGame(seed = 1L)
        assertEquals(PlanePhase.PLAYING, game.phase)
        assertEquals(PlaneConfig.START_LIVES, game.startLives)
        assertEquals(PlaneConfig.START_LIVES, game.player.lives)
        assertEquals(PlaneConfig.START_BOMBS, game.player.bombs)
        assertEquals(PlaneConfig.MIN_WEAPON_LEVEL, game.player.weaponLevel)
        assertEquals(180f, game.fieldH, 0.01f)
        assertEquals(0, game.waveIndex)
        assertEquals(PlaneConfig.WAVE_INTRO_TICKS, game.betweenWaveTicks)
        assertTrue(game.player.alive)
    }

    @Test
    fun startGame_usesConfiguredStartLives_andClamps() {
        val game = newGame()
        game.startLives = 7
        game.startGame(seed = 101L)
        assertEquals(7, game.player.lives)

        game.startLives = 99
        assertEquals("设置值应夹到上限", PlaneConfig.START_LIVES_MAX, game.startLives)
        game.startGame(seed = 102L)
        assertEquals(PlaneConfig.START_LIVES_MAX, game.player.lives)

        game.startLives = -3
        assertEquals("设置值应夹到下限", PlaneConfig.START_LIVES_MIN, game.startLives)
        game.startGame(seed = 103L)
        assertEquals(PlaneConfig.START_LIVES_MIN, game.player.lives)
    }

    @Test
    fun startLivesOne_settlesOnFirstHit_withoutShieldLeak() {
        val game = newGame()
        game.startLives = 1
        game.startGame(seed = 104L)
        assertEquals(1, game.player.lives)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        game.enemies.clear()
        game.enemyBullets.clear()
        game.enemyBullets += enemyBulletAtPlayer(game, 995)
        game.advanceTicks(1)
        assertEquals(0, game.player.lives)
        assertFalse(game.player.alive)
        assertEquals("命数 0 必须立即结算", PlanePhase.SETTLED, game.phase)
    }

    @Test
    fun startLivesNine_keepsFullHealthPool() {
        val game = newGame()
        game.startLives = 9
        game.startGame(seed = 105L)
        assertEquals(9, game.player.lives)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        repeat(8) { index ->
            game.enemies.clear()
            game.enemyBullets.clear()
            game.enemyBullets += enemyBulletAtPlayer(game, 1000 + index)
            game.advanceTicks(1)
            game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        }
        assertEquals("9 条命应能承受 8 次受击仍存活", 1, game.player.lives)
        assertTrue(game.player.alive)
        assertEquals(PlanePhase.PLAYING, game.phase)
    }

    @Test
    fun startGame_clearsPreviousEventFlags() {
        val game = newGame()
        game.pendingHapticLose = true
        game.pendingHapticBossDown = true
        game.startGame(seed = 2L)
        assertFalse(game.pendingHapticLose)
        assertFalse(game.pendingHapticBossDown)
    }

    @Test
    fun autoFire_spawnsLevelOneBullet() {
        val game = newGame()
        game.startGame(seed = 3L)
        game.advanceTicks(1)
        assertEquals(1, game.playerBullets.size)
        assertTrue(game.pendingHapticShot)
        assertEquals(PlaneBulletShape.CIRCLE, game.playerBullets[0].shape)
    }

    @Test
    fun sameSeed_sameTicks_produceIdenticalState() {
        val a = newGame()
        val b = newGame()
        a.startGame(seed = 42L)
        b.startGame(seed = 42L)
        a.advanceTicks(1200)
        b.advanceTicks(1200)
        assertEquals(a.phase, b.phase)
        assertEquals(a.score, b.score)
        assertEquals(a.kills, b.kills)
        assertEquals(a.waveIndex, b.waveIndex)
        assertEquals(a.enemies.size, b.enemies.size)
        a.enemies.zip(b.enemies).forEach { (left, right) ->
            assertEquals(left.id, right.id)
            assertEquals(left.x, right.x, 0f)
            assertEquals(left.y, right.y, 0f)
            assertEquals(left.hp, right.hp)
        }
        assertEquals(a.player.x, b.player.x, 0f)
        assertEquals(a.player.y, b.player.y, 0f)
    }

    // ---------------------------------------------------------------------
    // 武器 / 道具
    // ---------------------------------------------------------------------

    @Test
    fun weaponPowerUp_upgradesToMax_thenStays() {
        val game = newGame()
        game.startGame(seed = 4L)
        repeat(6) { index ->
            game.powerUps +=
                PlanePowerUp(
                    id = 100 + index,
                    kind = PlanePowerUpKind.WEAPON,
                    x = game.player.x,
                    y = game.player.y,
                    vy = 0f,
                )
            game.advanceTicks(1)
        }
        assertEquals(PlaneConfig.MAX_WEAPON_LEVEL, game.player.weaponLevel)
        assertTrue(game.pendingHapticPowerup)
        assertTrue(game.score >= 6 * PlaneConfig.POWERUP_SCORE)
    }

    @Test
    fun playerHit_dropsWeaponLevel_andBlocksWhileInvulnerable() {
        val game = newGame()
        game.startGame(seed = 5L)
        game.player.weaponLevel = 3
        // 初始无敌（1.2s）耗尽
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        game.enemyBullets += enemyBulletAtPlayer(game, 900)
        game.advanceTicks(1)
        assertEquals(2, game.player.lives)
        assertEquals(2, game.player.weaponLevel)
        assertTrue(game.pendingHapticPlayerHit)
        assertTrue(game.player.invulnTicks > 0)

        // 无敌期内再命中：不掉命，且子弹不被吞掉（穿过 / 可擦弹）
        game.clearEventFlags()
        game.enemyBullets += enemyBulletAtPlayer(game, 901)
        game.advanceTicks(1)
        assertEquals(2, game.player.lives)
        assertFalse(game.pendingHapticPlayerHit)
        assertTrue("无敌期内子弹应保留而不是被无声删除", game.enemyBullets.any { it.id == 901 })
    }

    @Test
    fun enemyBullet_overlappingPlayer_afterInvulnExpires_dealsDamage() {
        val game = newGame()
        game.startGame(seed = 51L)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        game.enemyBullets += enemyBulletAtPlayer(game, 960)
        game.advanceTicks(1)
        assertEquals(2, game.player.lives)

        // 清掉场上干扰，留下一颗与玩家重叠的静止弹等待无敌结束
        game.enemies.clear()
        game.enemyBullets.clear()
        game.enemyBullets += enemyBulletAtPlayer(game, 961)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS - 1)
        assertEquals("无敌结束前不掉命", 2, game.player.lives)
        assertTrue(game.enemyBullets.any { it.id == 961 })

        game.advanceTicks(1)
        assertEquals("无敌结束同一颗弹应立即结算", 1, game.player.lives)
        assertFalse("结算后被移除", game.enemyBullets.any { it.id == 961 })
    }

    @Test
    fun turretFire_includesRectBarBullet() {
        val game = newGame()
        game.startGame(seed = 52L)
        game.advanceTicks(1)
        game.enemies.clear()
        game.enemies +=
            PlaneEnemy(
                id = 970,
                type = PlaneEnemyType.TURRET,
                x = 50f,
                y = 32f,
                vx = 0f,
                vy = 0f,
                hp = 8,
                maxHp = 8,
                fireTimerTicks = 0,
                targetY = 32f,
            )
        game.advanceTicks(1)
        assertTrue("炮台开火应生成 RECT 长条弹", game.enemyBullets.any { it.shape == PlaneBulletShape.RECT })
        val bar = game.enemyBullets.first { it.shape == PlaneBulletShape.RECT }
        assertTrue("长条弹向下压", bar.vy > 0f)
        assertFalse("炮台长条弹不应带 Boss 标记（避免品红配色）", bar.boss)
        assertEquals(PlaneConfig.TURRET_BAR_HALF_WIDTH, bar.radius, 1e-4f)
        assertEquals(PlaneConfig.TURRET_BAR_HALF_HEIGHT, bar.halfLength, 1e-4f)
    }

    @Test
    fun heliAndTank_fireTheirOwnBulletPatterns() {
        val game = newGame()
        game.startGame(seed = 53L)
        game.advanceTicks(1)
        game.enemies.clear()
        game.enemyBullets.clear()
        game.enemies +=
            PlaneEnemy(
                id = 975,
                type = PlaneEnemyType.HELI,
                x = 50f,
                y = PlaneLogic.HELI_HOVER_Y,
                vx = 0f,
                vy = 0f,
                hp = 6,
                maxHp = 6,
                fireTimerTicks = 0,
            )
        game.enemies +=
            PlaneEnemy(
                id = 976,
                type = PlaneEnemyType.TANK,
                x = 50f,
                y = game.fieldH - PlaneLogic.TANK_GROUND_MARGIN,
                vx = 12f,
                vy = 0f,
                hp = 9,
                maxHp = 9,
                fireTimerTicks = 0,
            )
        game.advanceTicks(1)
        assertEquals("直升机 3 发向下扇形 + 装甲车 1 发瞄准炮弹", 4, game.enemyBullets.count { it.shape == PlaneBulletShape.CIRCLE })
        assertTrue(
            "直升机向下扇形至少有 3 发朝下",
            game.enemyBullets.count { it.shape == PlaneBulletShape.CIRCLE && it.vy > 0f } >= 3,
        )
        assertTrue("装甲车应发射竖直向上的 CAPSULE 跟踪弹", game.enemyBullets.any { it.shape == PlaneBulletShape.CAPSULE && it.vx == 0f && it.vy < 0f })
    }

    @Test
    fun bomb_clearsBullets_andDamagesAllEnemies() {
        val game = newGame()
        game.startGame(seed = 6L)
        game.advanceTicks(1)
        game.enemies +=
            PlaneEnemy(
                id = 500,
                type = PlaneEnemyType.GRUNT,
                x = 20f,
                y = 40f,
                vx = 0f,
                vy = 0f,
                hp = 3,
                maxHp = 3,
                fireTimerTicks = 999,
            )
        game.enemies +=
            PlaneEnemy(
                id = 501,
                type = PlaneEnemyType.WEAVER,
                x = 80f,
                y = 40f,
                vx = 0f,
                vy = 0f,
                hp = 5,
                maxHp = 5,
                fireTimerTicks = 999,
            )
        game.enemyBullets +=
            PlaneBullet(
                id = 600,
                owner = PlaneBulletOwner.ENEMY,
                shape = PlaneBulletShape.CIRCLE,
                x = 10f,
                y = 100f,
                vx = 0f,
                vy = 0f,
                damage = 1,
                radius = PlaneConfig.ENEMY_BULLET_RADIUS,
            )
        assertTrue(game.useBomb())
        assertEquals(1, game.player.bombs)
        assertTrue(game.enemyBullets.isEmpty())
        assertTrue("炸弹应击毁两个小怪", game.kills >= 2)
        assertTrue(game.pendingHapticBomb)
        assertTrue(game.player.invulnTicks >= PlaneConfig.BOMB_INVULN_TICKS)

        game.player.bombs = 0
        assertFalse("无炸弹时使用应失败", game.useBomb())
    }

    // ---------------------------------------------------------------------
    // 擦弹
    // ---------------------------------------------------------------------

    @Test
    fun graze_awardsOncePerBullet() {
        val game = newGame()
        game.startGame(seed = 7L)
        val before = game.score
        game.enemyBullets +=
            PlaneBullet(
                id = 700,
                owner = PlaneBulletOwner.ENEMY,
                shape = PlaneBulletShape.CIRCLE,
                x = game.player.x + 3.5f,
                y = game.player.y,
                vx = 0f,
                vy = 0f,
                damage = 1,
                radius = PlaneConfig.ENEMY_BULLET_RADIUS,
            )
        game.advanceTicks(1)
        assertEquals(1, game.grazeCount)
        assertEquals(before + PlaneConfig.GRAZE_SCORE, game.score)
        game.advanceTicks(1)
        assertEquals("同一颗子弹只擦一次", 1, game.grazeCount)
    }

    // ---------------------------------------------------------------------
    // 波次 / Boss / 胜负
    // ---------------------------------------------------------------------

    @Test
    fun bossWave_warns_thenSpawnsBoss() {
        val game = newGame()
        game.startGame(seed = 8L, startWave = 5)
        game.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS)
        assertEquals(5, game.waveIndex)
        assertTrue(game.bossWarningTicks > 0)
        assertTrue(game.pendingHapticBossWarning)
        assertTrue(game.enemies.none { it.isBoss })

        game.advanceTicks(PlaneConfig.BOSS_WARNING_TICKS)
        val boss = game.enemies.firstOrNull { it.isBoss }
        assertNotNull("Boss 应在警告结束后出现", boss)
        assertTrue(game.bossActive)
    }

    @Test
    fun boss_sweepCoversBothSidesOfSpawnWithinAmplitude() {
        val game = newGame()
        game.startGame(seed = 106L, startWave = PLANE_BOSS_EVERY)
        game.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS + PlaneConfig.BOSS_WARNING_TICKS)
        val boss = game.enemies.first { it.isBoss }
        val spawnX = boss.x
        var minX = boss.x
        var maxX = boss.x
        repeat(60 * 20) {
            // 压住玩家火力，避免 20 秒内把 Boss 打死导致采样停止
            game.player.fireCooldownTicks = 99999
            game.player.invulnTicks = 99999
            game.enemyBullets.clear()
            game.advanceTicks(1)
            minX = minOf(minX, boss.x)
            maxX = maxOf(maxX, boss.x)
        }
        val amplitude = PlaneLogic.BOSS_SWEEP_SPEED / PlaneLogic.BOSS_SWEEP_OMEGA
        assertTrue("Boss 必须扫到出生点左侧（minX=$minX spawn=$spawnX）", minX < spawnX - 10f)
        assertTrue("Boss 扫射幅度应受速度约束（maxX=$maxX）", maxX <= spawnX + amplitude + 1f)
    }

    @Test
    fun bossPhaseTransition_clearsBullets() {
        val game = newGame()
        game.startGame(seed = 9L, startWave = 5)
        game.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS + PlaneConfig.BOSS_WARNING_TICKS)
        game.advanceTicks(120)
        val boss = game.enemies.first { it.isBoss }
        val damageToPhaseOne = boss.maxHp / 2 + 1
        game.enemyBullets += enemyBulletAtPlayer(game, 910)
        game.playerBullets += playerBulletAt(911, boss.x, boss.y, damage = damageToPhaseOne)
        game.advanceTicks(1)
        assertEquals(1, boss.bossPhase)
        assertTrue("阶段切换应清空敌弹", game.enemyBullets.isEmpty())

        game.enemyBullets += enemyBulletAtPlayer(game, 912)
        game.playerBullets += playerBulletAt(913, boss.x, boss.y, damage = 40)
        game.advanceTicks(1)
        assertEquals(2, boss.bossPhase)
        assertTrue("第二次阶段切换同样清弹", game.enemyBullets.isEmpty())
    }

    @Test
    fun defeatingBoss_continuesEndlessly_withoutWinEvent() {
        val game = newGame()
        game.startGame(seed = 10L, startWave = PLANE_BOSS_EVERY * 2)
        game.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS + PlaneConfig.BOSS_WARNING_TICKS)
        game.advanceTicks(120)
        val boss = game.enemies.first { it.isBoss }
        game.playerBullets += playerBulletAt(920, boss.x, boss.y, damage = 99999)
        game.advanceTicks(1)
        assertTrue(game.enemies.isEmpty())
        assertTrue(game.pendingHapticBossDown)
        assertFalse("Boss 死亡只触发 bossDown，不应再叠加 explosion 音", game.pendingHapticExplosion)
        game.advanceTicks(1)
        assertEquals("无尽模式：击破 Boss 不结算", PlanePhase.PLAYING, game.phase)
        assertFalse("无尽模式不再触发 WIN 事件", game.pendingHapticWin)
        assertFalse(game.pendingHapticLose)
        assertTrue(game.player.alive)
        // 波次间隔后继续进入下一波非 Boss 编队
        game.advanceTicks(PlaneConfig.WAVE_GAP_TICKS)
        assertEquals(PLANE_BOSS_EVERY * 2 + 1, game.waveIndex)
        assertEquals(PlanePhase.PLAYING, game.phase)
    }

    @Test
    fun losingAllLives_settlesDefeat() {
        val game = newGame()
        game.startGame(seed = 11L)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        repeat(3) { index ->
            game.enemies.clear()
            game.enemyBullets.clear()
            game.enemyBullets += enemyBulletAtPlayer(game, 930 + index)
            game.advanceTicks(1)
            if (index < 2) game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        }
        assertEquals(0, game.player.lives)
        assertFalse(game.player.alive)
        assertEquals(PlanePhase.SETTLED, game.phase)
        assertTrue(game.pendingHapticLose)
        assertFalse(game.pendingHapticWin)
    }

    // ---------------------------------------------------------------------
    // 事件去重 / 掉落 / 暂停 / 天气
    // ---------------------------------------------------------------------

    @Test
    fun events_areDeduplicatedWithinTick_andConsumedOnce() {
        val game = newGame()
        game.startGame(seed = 12L)
        game.advanceTicks(1)
        game.enemies +=
            PlaneEnemy(
                id = 800,
                type = PlaneEnemyType.GRUNT,
                x = 20f,
                y = 30f,
                vx = 0f,
                vy = 0f,
                hp = 1,
                maxHp = 1,
                fireTimerTicks = 999,
            )
        game.enemies +=
            PlaneEnemy(
                id = 801,
                type = PlaneEnemyType.GRUNT,
                x = 80f,
                y = 30f,
                vx = 0f,
                vy = 0f,
                hp = 1,
                maxHp = 1,
                fireTimerTicks = 999,
            )
        game.playerBullets += playerBulletAt(810, 20f, 30f, damage = 5)
        game.playerBullets += playerBulletAt(811, 80f, 30f, damage = 5)
        game.advanceTicks(1)
        val events = game.consumeEvents()
        assertTrue(events.explosion)
        assertFalse("消费后标志应复位", game.pendingHapticExplosion)
        assertFalse("同一 tick 的重复触发只算一次", game.consumeEvents().explosion)
    }

    @Test
    fun transportKill_dropsPowerUpGuaranteed() {
        val game = newGame()
        game.startGame(seed = 13L)
        game.advanceTicks(1)
        game.enemies +=
            PlaneEnemy(
                id = 820,
                type = PlaneEnemyType.TRANSPORT,
                x = 50f,
                y = 60f,
                vx = 0f,
                vy = 0f,
                hp = 1,
                maxHp = 1,
                fireTimerTicks = 999,
            )
        game.playerBullets += playerBulletAt(821, 50f, 60f, damage = 5)
        game.advanceTicks(1)
        assertEquals(1, game.kills)
        assertTrue("运输机击毁必掉道具", game.powerUps.isNotEmpty())
    }

    @Test
    fun pause_blocksTick_untilResume() {
        val game = newGame()
        game.startGame(seed = 14L)
        game.advanceTicks(10)
        val ticks = game.tickCount
        game.pause()
        assertEquals(PlanePhase.PAUSED, game.phase)
        game.tick(0.5f)
        assertEquals(ticks, game.tickCount)
        game.resume()
        game.tick(1f / 60f)
        assertEquals(ticks + 1, game.tickCount)
    }

    @Test
    fun snowWeather_slowsEnemyMotion() {
        val game = newGame()
        val enemy =
            PlaneEnemy(
                id = 1,
                type = PlaneEnemyType.GRUNT,
                x = 50f,
                y = 20f,
                vx = 0f,
                vy = 0f,
                hp = 3,
                maxHp = 3,
                fireTimerTicks = 0,
            )
        game.setWeather(71, true)
        assertEquals(FarmWeather.SNOWY, game.weather)
        val snowy =
            PlaneLogic
                .enemyVelocity(enemy, 50f, 180f, game.difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(game.weather))
                .second
        game.setWeather(0, true)
        assertEquals(FarmWeather.SUNNY, game.weather)
        val sunny =
            PlaneLogic
                .enemyVelocity(enemy, 50f, 180f, game.difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(game.weather))
                .second
        assertTrue("雪天敌人应稍慢", snowy < sunny)
    }
}
