package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.stringOr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaneGame] 对局快照的 JVM 单测：往返一致性、恢复后确定性续玩、坏档 / 旧版本拒绝。
 */
class PlaneSnapshotTest {
    private val key = PLANE_SNAPSHOT_KEY

    private fun newGame(): PlaneGame =
        PlaneGame().apply {
            resize(600f, 1080f)
        }

    @Test
    fun saveRestore_keepsStateAndEntities() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.setWeather(51, isDay = false)
        source.difficulty = PlaneDifficulty.CHALLENGE
        source.startGame(seed = 77L, startWave = 3)
        source.advanceTicks(500)
        assertTrue("500 tick 后应有活动实体", source.enemies.isNotEmpty() || source.playerBullets.isNotEmpty())

        source.saveSnapshot(prefs)
        assertTrue("进行中应写入快照", prefs.contains(key))

        val restored = newGame()
        assertTrue("应能从快照恢复", restored.restoreSnapshot(prefs))
        assertEquals("恢复后应先停在暂停，等待玩家确认", PlanePhase.PAUSED, restored.phase)
        assertEquals(source.difficulty, restored.difficulty)
        assertEquals(source.weather, restored.weather)
        assertEquals(source.sceneCode, restored.sceneCode)
        assertEquals(source.isDay, restored.isDay)
        assertEquals(source.score, restored.score)
        assertEquals(source.kills, restored.kills)
        assertEquals(source.grazeCount, restored.grazeCount)
        assertEquals(source.waveIndex, restored.waveIndex)
        assertEquals(source.player.lives, restored.player.lives)
        assertEquals(source.player.bombs, restored.player.bombs)
        assertEquals(source.player.weaponLevel, restored.player.weaponLevel)
        assertEquals(source.player.x, restored.player.x, 0f)
        assertEquals(source.player.y, restored.player.y, 0f)
        assertEquals(source.enemies.size, restored.enemies.size)
        assertEquals(source.playerBullets.size, restored.playerBullets.size)
        assertEquals(source.enemyBullets.size, restored.enemyBullets.size)
        assertEquals(source.powerUps.size, restored.powerUps.size)
        source.enemies.zip(restored.enemies).forEach { (left, right) ->
            assertEquals(left.id, right.id)
            assertEquals(left.type, right.type)
            assertEquals(left.x, right.x, 0f)
            assertEquals(left.y, right.y, 0f)
            assertEquals(left.hp, right.hp)
            assertEquals(left.ageTicks, right.ageTicks)
        }
    }

    @Test
    fun restoredGame_continuesIdentically() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 123L, startWave = 2)
        source.advanceTicks(300)
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(PlanePhase.PAUSED, restored.phase)
        source.advanceTicks(300)
        restored.resume()
        restored.advanceTicks(300)
        assertEquals(source.phase, restored.phase)
        assertEquals(source.score, restored.score)
        assertEquals(source.kills, restored.kills)
        assertEquals(source.waveIndex, restored.waveIndex)
        assertEquals(source.combo, restored.combo)
        assertEquals(source.enemies.size, restored.enemies.size)
        source.enemies.zip(restored.enemies).forEach { (left, right) ->
            assertEquals(left.x, right.x, 0f)
            assertEquals(left.y, right.y, 0f)
            assertEquals(left.hp, right.hp)
            assertEquals(left.fireTimerTicks, right.fireTimerTicks)
        }
        assertEquals(source.playerBullets.size, restored.playerBullets.size)
        source.playerBullets.zip(restored.playerBullets).forEach { (left, right) ->
            assertEquals(left.x, right.x, 0f)
            assertEquals(left.y, right.y, 0f)
            assertEquals(left.ttlTicks, right.ttlTicks)
        }
    }

    @Test
    fun restoredGame_continuesIdentically_atHighWaveWithNewEnemies() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.difficulty = PlaneDifficulty.CHALLENGE
        source.startGame(seed = 424242L, startWave = 12)
        // 挂机玩家在高波次会秒死；用短无敌（<= 快照清洗上限）保证能采集到含 TANK/HELI 的状态
        repeat(60 * 60) {
            source.player.invulnTicks = 60
            source.advanceTicks(1)
        }
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(PlanePhase.PAUSED, restored.phase)
        source.advanceTicks(600)
        restored.resume()
        restored.advanceTicks(600)
        assertEquals(source.phase, restored.phase)
        assertEquals(source.waveIndex, restored.waveIndex)
        assertEquals(source.score, restored.score)
        assertEquals(source.kills, restored.kills)
        assertEquals(source.enemies.size, restored.enemies.size)
        source.enemies.zip(restored.enemies).forEach { (left, right) ->
            assertEquals(left.id, right.id)
            assertEquals(left.type, right.type)
            assertEquals(left.x, right.x, 0f)
            assertEquals(left.y, right.y, 0f)
            assertEquals(left.hp, right.hp)
            assertEquals(left.ageTicks, right.ageTicks)
        }
        assertEquals(source.enemyBullets.size, restored.enemyBullets.size)
        assertEquals(source.powerUps.size, restored.powerUps.size)
    }

    @Test
    fun restoreSnapshot_playingSave_isPaused_andTicksDoNotAdvance() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 21L)
        source.advanceTicks(120)
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(PlanePhase.PAUSED, restored.phase)
        val ticks = restored.tickCount
        restored.tick(1f)
        restored.advanceTicks(30)
        assertEquals("恢复后未继续不应推进 tick", ticks, restored.tickCount)
        restored.resume()
        restored.advanceTicks(1)
        assertEquals(ticks + 1, restored.tickCount)
    }

    @Test
    fun restoreSnapshot_semanticBadSave_isSanitized() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 22L)
        source.advanceTicks(30)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)

        val broken =
            (
                payload!!.lineSequence().map { line ->
                    when {
                        line.startsWith("player=") -> {
                            val f = line.removePrefix("player=").split(',').toMutableList()
                            f[0] = "NaN" // x 坏值
                            f[4] = "9" // weaponLevel 越界
                            f[5] = "-5" // invulnTicks 负值
                            f[7] = "0" // alive 与 lives>0 矛盾
                            "player=" + f.joinToString(",")
                        }
                        line.startsWith("wave=") -> {
                            val f = line.removePrefix("wave=").split(',').toMutableList()
                            f[2] = "999999" // betweenWaveTicks 超大
                            "wave=" + f.joinToString(",")
                        }
                        else -> line
                    }
                } + "ebullet=1,ENEMY,CIRCLE,NaN,0,0,0,1,1.5,0,0,99999,0,0,0,"
            ).joinToString("\n")
        prefs.edit().putString(key, broken).apply()

        val restored = newGame()
        assertTrue("语义坏档清洗后仍应可恢复", restored.restoreSnapshot(prefs))
        assertEquals(PlanePhase.PAUSED, restored.phase)
        assertTrue("lives 必须为正（软锁档已在解码拒绝）", restored.player.lives > 0)
        assertTrue("alive 必须与 lives 一致", restored.player.alive)
        assertEquals(PlaneConfig.MAX_WEAPON_LEVEL, restored.player.weaponLevel)
        assertEquals(0, restored.player.invulnTicks)
        assertTrue("NaN 玩家坐标应被清洗", restored.player.x.isFinite())
        assertTrue(
            "超大 betweenWaveTicks 应封顶",
            restored.betweenWaveTicks <= maxOf(PlaneConfig.WAVE_INTRO_TICKS, PlaneConfig.WAVE_GAP_TICKS),
        )
        val badBullet = restored.enemyBullets.firstOrNull { it.id == 1 }
        assertNotNull("坏弹结构合法应保留（值被清洗）", badBullet)
        assertTrue("NaN 子弹坐标应被清洗", badBullet!!.x.isFinite())
        assertTrue("异常 ttlTicks 应封顶", badBullet.ttlTicks <= PlaneConfig.LASER_TTL_TICKS)
    }

    @Test
    fun restoreSnapshot_extremeCanvasAspect_rebuildsTransformAndClampsPlayer() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 31L)
        source.advanceTicks(200)
        source.saveSnapshot(prefs)

        for ((width, height) in listOf(3200f to 480f, 480f to 3200f)) {
            val restored = PlaneGame().apply { resize(width, height) }
            assertTrue("${width}x$height 应能恢复", restored.restoreSnapshot(prefs))
            assertEquals(PlanePhase.PAUSED, restored.phase)
            // 屏幕 <-> 逻辑往返一致（视图变换与当前画布匹配）
            assertEquals(42f, restored.toLogicalY(restored.toScreenY(42f)), 1e-3f)
            assertEquals(37f, restored.toLogicalX(restored.toScreenX(37f)), 1e-3f)
            // 玩家被 clamp 在新 fieldH 内
            assertTrue(restored.player.x >= PlaneConfig.PLAYER_RADIUS)
            assertTrue(restored.player.x <= PLANE_LOGICAL_WIDTH - PlaneConfig.PLAYER_RADIUS)
            assertTrue(restored.player.y >= PlaneConfig.PLAYER_RADIUS * 2f)
            assertTrue(restored.player.y <= restored.fieldH - PlaneConfig.PLAYER_RADIUS * 2f)
            // 越界敌人 / 子弹被清理
            val margin = PlaneConfig.OFFSCREEN_MARGIN
            assertTrue(
                "${width}x$height 恢复后不应有越界敌人",
                restored.enemies.none { !it.isBoss && it.y > restored.fieldH + margin },
            )
            assertTrue(restored.enemyBullets.none { it.y > restored.fieldH + margin })
            assertTrue(restored.playerBullets.none { it.y < -margin })
        }
    }

    @Test
    fun restoreSnapshot_beforeResize_keepsSavedFieldH_thenResizeRebuildsTransform() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 32L)
        source.advanceTicks(200)
        source.saveSnapshot(prefs)

        // 首帧 resize 尚未发生：沿用存档场高，玩家先夹回场内
        val restored = PlaneGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(180f, restored.fieldH, 0.01f)
        assertTrue(restored.player.y <= restored.fieldH - PlaneConfig.PLAYER_RADIUS * 2f)

        // resize 晚于 restore：按当前画布重算场高与变换，玩家再次夹回
        restored.resize(3200f, 480f)
        assertEquals(150f, restored.fieldH, 0.01f)
        assertEquals(42f, restored.toLogicalY(restored.toScreenY(42f)), 1e-3f)
        assertTrue(restored.player.y <= restored.fieldH - PlaneConfig.PLAYER_RADIUS * 2f)
    }

    @Test
    fun saveRestore_preservesBossBulletFlag() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 25L, startWave = PLANE_BOSS_EVERY * 2)
        source.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS + PlaneConfig.BOSS_WARNING_TICKS)
        source.advanceTicks(120)
        assertTrue("Boss 应已开火", source.enemyBullets.any { it.boss })
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(
            "Boss 弹标记应随快照往返",
            source.enemyBullets.count { it.boss },
            restored.enemyBullets.count { it.boss },
        )
        assertTrue(restored.enemyBullets.filter { it.boss }.all { it.shape == PlaneBulletShape.CIRCLE })
    }

    @Test
    fun restoreSnapshot_empty_returnsFalse() {
        assertFalse(newGame().restoreSnapshot(FakePlanePrefs()))
    }

    @Test
    fun restoreSnapshot_corrupt_returnsFalseAndClears() {
        val prefs = FakePlanePrefs()
        prefs.edit().putString(key, "这不是合法快照\nnot=valid").apply()
        val game = newGame()
        assertFalse("坏档不应崩溃，返回 false", game.restoreSnapshot(prefs))
        assertFalse("坏档应被清掉", prefs.contains(key))
        assertEquals(PlanePhase.READY, game.phase)
    }

    @Test
    fun restoreSnapshot_versionMismatch_rejectedAndCleared() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 9L)
        source.advanceTicks(60)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        assertTrue("新档应为 v=5: $payload", payload!!.startsWith("v=5"))
        prefs.edit().putString(key, payload.replaceFirst("v=5", "v=4")).apply()

        val restored = newGame()
        assertFalse("旧版本 payload 不应被恢复", restored.restoreSnapshot(prefs))
        assertFalse("旧档应被清掉", prefs.contains(key))
    }

    @Test
    fun restoreSnapshot_zeroLives_rejectedAndCleared() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 24L)
        source.advanceTicks(60)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        val broken =
            payload!!
                .lineSequence()
                .map { line ->
                    if (line.startsWith("player=")) {
                        val f = line.removePrefix("player=").split(',').toMutableList()
                        f[2] = "0" // lives=0：恢复后会软锁
                        "player=" + f.joinToString(",")
                    } else {
                        line
                    }
                }.joinToString("\n")
        prefs.edit().putString(key, broken).apply()

        val restored = newGame()
        assertFalse("lives=0 的软锁档应被拒绝", restored.restoreSnapshot(prefs))
        assertFalse("拒绝后应清档", prefs.contains(key))
        assertEquals(PlanePhase.READY, restored.phase)
    }

    @Test
    fun restoreSnapshot_missingPlayerLine_rejectedAndCleared() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 10L)
        source.advanceTicks(30)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        val broken = payload!!.lineSequence().filterNot { it.startsWith("player=") }.joinToString("\n")
        prefs.edit().putString(key, broken).apply()

        val restored = newGame()
        assertFalse(restored.restoreSnapshot(prefs))
        assertFalse(prefs.contains(key))
    }

    @Test
    fun restoreSnapshot_nonPlayingPhase_rejected() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 11L)
        source.advanceTicks(30)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        prefs.edit().putString(key, payload!!.replaceFirst("phase=PLAYING", "phase=READY")).apply()

        val restored = newGame()
        assertFalse(restored.restoreSnapshot(prefs))
        assertFalse(prefs.contains(key))
    }

    @Test
    fun saveSnapshot_outsideActivePlay_clears() {
        val prefs = FakePlanePrefs()
        val game = newGame()
        // READY：未开局不应有快照
        game.saveSnapshot(prefs)
        assertFalse(prefs.contains(key))
    }

    @Test
    fun settledMatch_save_clearsSnapshot() {
        val prefs = FakePlanePrefs()
        val game = newGame()
        game.startGame(seed = 12L)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        // 无尽模式只有死亡结算：用贴脸敌弹打光命
        repeat(game.player.lives) { index ->
            game.enemies.clear()
            game.enemyBullets.clear()
            game.enemyBullets +=
                PlaneBullet(
                    id = 990 + index,
                    owner = PlaneBulletOwner.ENEMY,
                    shape = PlaneBulletShape.CIRCLE,
                    x = game.player.x,
                    y = game.player.y,
                    vx = 0f,
                    vy = 0f,
                    damage = 1,
                    radius = PlaneConfig.ENEMY_BULLET_RADIUS,
                )
            game.advanceTicks(1)
            if (game.phase == PlanePhase.PLAYING) game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        }
        assertEquals(PlanePhase.SETTLED, game.phase)
        game.saveSnapshot(prefs)
        assertFalse("结算后不应保留续玩快照", prefs.contains(key))
        assertFalse(newGame().restoreSnapshot(prefs))
    }

    @Test
    fun saveRestore_preservesStartLives_andNewEnemyTypes() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startLives = 8
        source.startGame(seed = 26L)
        source.advanceTicks(1)
        source.enemies.clear()
        source.enemies +=
            PlaneEnemy(
                id = 7001,
                type = PlaneEnemyType.HELI,
                x = 30f,
                y = PlaneLogic.HELI_HOVER_Y,
                vx = 0f,
                vy = 0f,
                hp = 6,
                maxHp = 6,
                fireTimerTicks = 5,
                ageTicks = 40,
            )
        source.enemies +=
            PlaneEnemy(
                id = 7002,
                type = PlaneEnemyType.TANK,
                x = 70f,
                y = source.fieldH - PlaneLogic.TANK_GROUND_MARGIN,
                vx = -12f,
                vy = 0f,
                hp = 9,
                maxHp = 9,
                fireTimerTicks = 7,
                ageTicks = 60,
            )
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("快照必须恢复本局的开局命数", 8, restored.startLives)
        assertEquals(8, restored.player.lives)
        val heli = restored.enemies.first { it.type == PlaneEnemyType.HELI }
        val tank = restored.enemies.first { it.type == PlaneEnemyType.TANK }
        assertEquals(7001, heli.id)
        assertEquals(PlaneLogic.HELI_HOVER_Y, heli.y, 0f)
        assertEquals(7002, tank.id)
        assertEquals(-12f, tank.vx, 0f)
        assertTrue("装甲车方向应随快照往返", tank.vx < 0f)
    }

    @Test
    fun saveRestore_preservesStartLivesBounds_minAndMax() {
        val prefs = FakePlanePrefs()
        for (lives in listOf(PlaneConfig.START_LIVES_MIN, PlaneConfig.START_LIVES_MAX)) {
            val source = newGame()
            source.startLives = lives
            source.startGame(seed = 50L + lives)
            source.saveSnapshot(prefs)
            val restored = newGame()
            assertTrue("startLives=$lives 应能恢复", restored.restoreSnapshot(prefs))
            assertEquals("快照应保留开局命数边界 $lives", lives, restored.startLives)
            assertEquals("玩家命数应与开局命数一致", lives, restored.player.lives)
        }
    }

    @Test
    fun decode_startLivesOutOfRange_isClampedToBounds() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startLives = 5
        source.startGame(seed = 55L)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        for ((raw, expected) in listOf("0" to PlaneConfig.START_LIVES_MIN, "99" to PlaneConfig.START_LIVES_MAX)) {
            val broken = payload!!.lineSequence().map { if (it.startsWith("start=")) "start=$raw" else it }.joinToString("\n")
            prefs.edit().putString(key, broken).apply()
            val restored = newGame()
            assertTrue("start=$raw 坏档仍应清洗恢复", restored.restoreSnapshot(prefs))
            assertEquals("start=$raw 应夹到 $expected", expected, restored.startLives)
        }
    }

    @Test
    fun restoreSnapshot_bossActiveWithoutBoss_isCleared() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 41L, startWave = 2)
        source.advanceTicks(120)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        val broken =
            payload!!
                .lineSequence()
                .map { line ->
                    if (line.startsWith("wave=")) {
                        val f = line.removePrefix("wave=").split(',').toMutableList()
                        f[4] = "1" // bossActive=true，但快照里没有 Boss（状态残留坏档）
                        "wave=" + f.joinToString(",")
                    } else {
                        line
                    }
                }.joinToString("\n")
        prefs.edit().putString(key, broken).apply()

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertFalse("没有 Boss 实体时不得残留 bossActive", restored.bossActive)
    }

    @Test
    fun restoreSnapshot_deadEnemyLine_droppedWithoutKillCredit() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 42L)
        source.advanceTicks(30)
        source.saveSnapshot(prefs)
        val payload = prefs.stringOr(key)
        assertNotNull(payload)
        // hp=0 的实体（如坏档）恢复后不应在首个 tick 被当成击杀计分
        val broken = payload!! + "\nenemy=9999,GRUNT,50,50,0,0,0,3,100,0,0,STANDARD,50,0,0,0"
        prefs.edit().putString(key, broken).apply()

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertTrue("hp<=0 实体不应被恢复", restored.enemies.none { it.id == 9999 })
        val kills = restored.kills
        val score = restored.score
        restored.resume()
        restored.advanceTicks(1)
        assertEquals("恢复后不应产生免费击杀", kills, restored.kills)
        assertEquals("恢复后不应产生免费分数", score, restored.score)
    }

    @Test
    fun clearSnapshot_removesPayload() {
        val prefs = FakePlanePrefs()
        val source = newGame()
        source.startGame(seed = 13L)
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))
        source.clearSnapshot(prefs)
        assertFalse(prefs.contains(key))
    }

    /** shouldClear 谓词：仅 SETTLED（结算）清档，标题 / 进行中 / 暂停保留（可续玩）。 */
    @Test
    fun shouldClearSnapshot_onlySettled() {
        assertFalse(planeShouldClearSnapshot(PlanePhase.READY))
        assertFalse(planeShouldClearSnapshot(PlanePhase.PLAYING))
        assertFalse(planeShouldClearSnapshot(PlanePhase.PAUSED))
        assertTrue(planeShouldClearSnapshot(PlanePhase.SETTLED))
    }
}
