package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * 确定性实战模拟 + 回归测试。
 *
 * - 挂机玩家：三档难度都应在有限时间内掉光命并进入 SETTLED（无卡死）；
 * - 躲避机器人：固定 seed / 固定策略跑限时窗口，验证波次持续推进、不卡波、实体数量有界；
 * - 无尽数值报告：1 / 5 / 10 / 20 / 30 波的挂机生存与机器人推进数据；
 * - 随机输入模糊：任意移动 / 炸弹 / resize / 天气 / 难度切换不产生 NaN 或数值崩塌；
 * - 几何回归：炮台长条弹（RECT）不穿透玩家、擦弹椭圆包络边界正确、L4 激光射程覆盖全屏。
 *
 * 机器人只用公开 API（startGame / advanceTicks / movePlayerTo / useBomb / resize / setWeather），
 * 不读 wall clock，同 seed 必然同结果。
 */
class PlaneSimulationTest {
    // ---------------------------------------------------------------------
    // 场景 1：挂机玩家
    // ---------------------------------------------------------------------

    @Test
    fun idlePlayer_losesAllLives_andSettles_onEveryDifficulty() {
        for (difficulty in PlaneDifficulty.entries) {
            val game = gameFor(difficulty)
            game.startGame(seed = 20260913L)
            var ticks = 0
            while (game.phase == PlanePhase.PLAYING && ticks < 60 * 60 * 5) {
                game.advanceTicks(1)
                ticks++
            }
            assertEquals("挂机 $difficulty 应在 5 分钟内结算", PlanePhase.SETTLED, game.phase)
            assertFalse("挂机 $difficulty 应掉光命失败", game.player.alive)
            assertEquals(0, game.player.lives)
        }
    }

    // ---------------------------------------------------------------------
    // 场景 2：躲避机器人
    // ---------------------------------------------------------------------

    @Test
    fun dodgeBot_makesProgress_throughBossWaves_withoutStall() {
        val report = runDodge(PlaneDifficulty.STANDARD, seed = 777L, maxTicks = 20 * 60 * 60)
        assertTrue(
            "STANDARD 躲避机器人不应卡波（结果=${report.result} wave=${report.wave} ticks=${report.ticks}）",
            report.result != RunResult.STALL,
        )
        assertTrue("机器人应至少推进到第 6 波（实际 ${report.wave}）", report.wave >= PLANE_BOSS_EVERY + 1)
        assertTrue(
            "无尽模式只可能落败或限时未结束（结果=${report.result}）",
            report.result == RunResult.LOSE || report.result == RunResult.TIMEOUT,
        )
    }

    @Test
    fun challengeTier_pressesHarderThanCasual_inDeterministicBotRun() {
        // 同一时间窗口（3 分钟）对比，避免无尽模式下「先死的档位峰值反而低」的混淆
        val window = 60 * 180
        val casual = runDodge(PlaneDifficulty.CASUAL, seed = 777L, maxTicks = window)
        val standard = runDodge(PlaneDifficulty.STANDARD, seed = 777L, maxTicks = window)
        val challenge = runDodge(PlaneDifficulty.CHALLENGE, seed = 777L, maxTicks = window)
        assertTrue(
            "CHALLENGE 前 3 分钟同屏敌弹峰值应不低于 CASUAL（casual=${casual.peakBullets}, challenge=${challenge.peakBullets}）",
            challenge.peakBullets >= casual.peakBullets,
        )
        assertTrue(
            "STANDARD 前 3 分钟同屏敌弹峰值应不低于 CASUAL（casual=${casual.peakBullets}, standard=${standard.peakBullets}）",
            standard.peakBullets >= casual.peakBullets,
        )
        assertTrue("三档机器人都应产生击杀（casual=${casual.kills}）", casual.kills > 0 && challenge.kills > 0)
    }

    @Test
    fun longDodgeRun_keepsEntityCountsBounded() {
        val report = runDodge(PlaneDifficulty.CHALLENGE, seed = 2L, maxTicks = 30 * 60 * 60)
        assertTrue("敌弹峰值应有界（实测 ${report.peakBullets}）", report.peakBullets <= 200)
        assertTrue("敌机峰值应有界（实测 ${report.peakEnemies}）", report.peakEnemies <= 40)
        assertTrue(
            "特效应被 MAX_EFFECTS 限制（实测 ${report.peakEffects} / ${PlaneConfig.MAX_EFFECTS}）",
            report.peakEffects <= PlaneConfig.MAX_EFFECTS,
        )
        assertTrue("道具峰值应有界（实测 ${report.peakPowerUps}）", report.peakPowerUps <= 64)
    }

    // ---------------------------------------------------------------------
    // 场景 3：模糊测试与边界输入
    // ---------------------------------------------------------------------

    @Test
    fun randomInputFuzz_staysFinite_andBounded() {
        val rng = Random(424242L)
        val game = PlaneGame().apply { resize(600f, 1080f) }
        var restarts = 0
        game.startGame(seed = rng.nextLong())
        repeat(6000) {
            if (game.phase != PlanePhase.PLAYING) {
                restarts++
                game.startGame(seed = rng.nextLong(), startWave = rng.nextInt(1, 16))
            }
            when (rng.nextInt(10)) {
                0 -> game.movePlayerTo(rng.nextFloat() * 160f - 30f, rng.nextFloat() * 260f - 40f)
                1 -> game.movePlayerTo(Float.NaN, Float.POSITIVE_INFINITY)
                2 -> game.useBomb()
                3 -> {
                    val values = listOf(rng.nextFloat() * 4000f, -5f, 0f, Float.NaN, Float.POSITIVE_INFINITY)
                    game.resize(values[rng.nextInt(values.size)], values[rng.nextInt(values.size)])
                }
                4 -> game.setWeather(rng.nextInt(100), rng.nextBoolean())
                5 -> game.difficulty = PlaneDifficulty.entries[rng.nextInt(3)]
                6 -> {
                    val steps = listOf(1f / 60f, 0.1f, -1f, Float.NaN, Float.POSITIVE_INFINITY)
                    game.tick(steps[rng.nextInt(steps.size)])
                }
            }
            game.advanceTicks(1)
            assertTrue("fieldH 必须有限", game.fieldH.isFinite())
            assertTrue("玩家坐标必须有限", game.player.x.isFinite() && game.player.y.isFinite())
            assertTrue("敌机坐标必须有限", game.enemies.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue("玩家子弹坐标必须有限", game.playerBullets.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue("敌弹坐标必须有限", game.enemyBullets.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue("特效应有上限", game.effects.size <= PlaneConfig.MAX_EFFECTS + 8)
            assertTrue("敌弹应有界（实测 ${game.enemyBullets.size}）", game.enemyBullets.size <= 300)
            assertTrue("敌机应有界（实测 ${game.enemies.size}）", game.enemies.size <= 64)
        }
        assertTrue("模糊测试期间应至少经历一次结算重开", restarts >= 1)
    }

    @Test
    fun nonFiniteInputs_doNotCorruptPlayerOrFreezeTick() {
        val game = gameFor(PlaneDifficulty.STANDARD)
        game.startGame(seed = 7L)
        val fieldH = game.fieldH
        val playerX = game.player.x
        val playerY = game.player.y

        game.resize(Float.NaN, 1080f)
        game.resize(Float.POSITIVE_INFINITY, 1080f)
        game.resize(600f, Float.NaN)
        assertEquals("非法 resize 不应改写场高", fieldH, game.fieldH, 0f)

        game.movePlayerTo(Float.NaN, Float.NaN)
        game.movePlayerTo(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        assertEquals("非法移动不应改写玩家 x", playerX, game.player.x, 0f)
        assertEquals("非法移动不应改写玩家 y", playerY, game.player.y, 0f)

        game.advanceTicks(1)
        val ticks = game.tickCount
        game.tick(Float.NaN)
        assertEquals("NaN dt 应被忽略而不是冻结累加器", ticks, game.tickCount)
        game.tick(PlaneConfig.LOGIC_STEP_SECONDS)
        assertEquals("后续合法 dt 应正常推进", ticks + 1, game.tickCount)
    }

    // ---------------------------------------------------------------------
    // 场景 4：RECT 长条弹 / 擦弹椭圆 / 激光射程
    // ---------------------------------------------------------------------

    @Test
    fun turretBar_doesNotTunnelThroughPlayer_atChallengeBulletSpeed() {
        val game = gameFor(PlaneDifficulty.CHALLENGE)
        game.startGame(seed = 52L)
        game.advanceTicks(PlaneConfig.HIT_INVULN_TICKS + 2)
        game.enemies.clear()
        game.enemyBullets.clear()
        val barSpeed =
            PlaneEnemyType.TURRET.baseBulletSpeed *
                PlaneDifficulty.CHALLENGE.bulletSpeedMul *
                PlaneConfig.TURRET_BAR_SPEED_MUL
        game.enemyBullets +=
            PlaneBullet(
                id = 9001,
                owner = PlaneBulletOwner.ENEMY,
                shape = PlaneBulletShape.RECT,
                x = game.player.x,
                y = game.player.y - 20f,
                vx = 0f,
                vy = barSpeed,
                damage = 1,
                radius = PlaneConfig.TURRET_BAR_HALF_WIDTH,
                halfLength = PlaneConfig.TURRET_BAR_HALF_HEIGHT,
            )
        val lives = game.player.lives
        repeat(40) { game.advanceTicks(1) }
        assertEquals("长条弹扫过玩家应命中（不穿透）", lives - 1, game.player.lives)
        assertTrue("命中后长条弹应被移除", game.enemyBullets.none { it.id == 9001 })
    }

    @Test
    fun rectHit_andGrazeEllipse_boundaries() {
        val halfW = PlaneConfig.TURRET_BAR_HALF_WIDTH
        val halfH = PlaneConfig.TURRET_BAR_HALF_HEIGHT
        val playerR = PlaneConfig.PLAYER_RADIUS
        // 矩形-圆：正上方贴边命中 / 超出不命中；角点按欧氏距离
        assertTrue(PlaneLogic.rectCircleHit(0f, halfH + playerR - 0.02f, playerR, 0f, 0f, halfW, halfH))
        assertFalse(PlaneLogic.rectCircleHit(0f, halfH + playerR + 0.02f, playerR, 0f, 0f, halfW, halfH))
        assertTrue(PlaneLogic.rectCircleHit(halfW + playerR - 0.02f, 0f, playerR, 0f, 0f, halfW, halfH))
        assertFalse(PlaneLogic.rectCircleHit(halfW + playerR + 0.02f, 0f, playerR, 0f, 0f, halfW, halfH))
        assertTrue(PlaneLogic.rectCircleHit(3.5f, 9.5f, playerR, 0f, 0f, halfW, halfH))

        val bar =
            PlaneBullet(
                id = 1,
                owner = PlaneBulletOwner.ENEMY,
                shape = PlaneBulletShape.RECT,
                x = 0f,
                y = 0f,
                vx = 0f,
                vy = 0f,
                damage = 1,
                radius = halfW,
                halfLength = halfH,
            )
        val grazeR = PlaneConfig.PLAYER_GRAZE_RADIUS
        assertTrue("横轴包络边界内擦弹", PlaneLogic.enemyBulletGrazes(bar, halfW + grazeR, 0f, grazeR))
        assertFalse("横轴包络外不擦弹", PlaneLogic.enemyBulletGrazes(bar, halfW + grazeR + 0.01f, 0f, grazeR))
        assertTrue("纵轴包络边界内擦弹", PlaneLogic.enemyBulletGrazes(bar, 0f, halfH + grazeR, grazeR))
        assertFalse("纵轴包络外不擦弹", PlaneLogic.enemyBulletGrazes(bar, 0f, halfH + grazeR + 0.01f, grazeR))
        assertTrue("椭圆内对角点擦弹", PlaneLogic.enemyBulletGrazes(bar, 5.3f, 9.5f, grazeR))
        assertFalse("椭圆外对角点不擦弹", PlaneLogic.enemyBulletGrazes(bar, 5.5f, 9.9f, grazeR))
        // 只擦不中：纵向包络内、但已超出矩形碰撞体
        assertTrue(PlaneLogic.enemyBulletGrazes(bar, 0f, 13f, grazeR))
        assertFalse(PlaneLogic.bulletHitsCircle(bar, 0f, 13f, playerR))
    }

    @Test
    fun maxWeaponLaser_reachesParkedBoss_fromBottom() {
        val game = gameFor(PlaneDifficulty.STANDARD)
        game.startGame(seed = 777L, startWave = PLANE_BOSS_EVERY)
        game.advanceTicks(PlaneConfig.WAVE_INTRO_TICKS + PlaneConfig.BOSS_WARNING_TICKS + 60)
        game.player.weaponLevel = PlaneConfig.MAX_WEAPON_LEVEL
        val startHp = game.enemies.first { it.isBoss }.hp
        repeat(60 * 15) {
            val boss = game.enemies.firstOrNull { it.isBoss } ?: return@repeat
            game.movePlayerTo(boss.x, game.fieldH * 0.8f)
            game.enemyBullets.clear()
            game.advanceTicks(1)
        }
        val endHp = game.enemies.firstOrNull { it.isBoss }?.hp ?: 0
        assertTrue("L4 激光从下半屏应能打到停驻 Boss（start=$startHp end=$endHp）", endHp < startHp)
    }

    @Test
    fun laserRange_coversFullFieldHeight_plusMargin() {
        val range = PlaneConfig.weaponSpec(PlaneConfig.MAX_WEAPON_LEVEL).speed * PlaneConfig.LASER_TTL_TICKS / 60f
        assertTrue(
            "激光射程（$range）应覆盖最大场高 + 离屏边距（${PLANE_FIELD_H_MAX + PlaneConfig.OFFSCREEN_MARGIN}）",
            range >= PLANE_FIELD_H_MAX + PlaneConfig.OFFSCREEN_MARGIN,
        )
    }

    // ---------------------------------------------------------------------
    // 内容回归：WAVE 9 的顶部入场不应生成在场外
    // ---------------------------------------------------------------------

    @Test
    fun topEntryEnemies_spawnInsideField_evenOnLateWaves() {
        val game = gameFor(PlaneDifficulty.STANDARD)
        game.startGame(seed = 1L, startWave = 9)
        val firstSeenX = HashMap<Int, Float>()
        repeat(60 * 40) {
            for (enemy in game.enemies) firstSeenX.putIfAbsent(enemy.id, enemy.x)
            game.player.invulnTicks = 999
            game.player.fireCooldownTicks = 9999
            game.enemyBullets.clear()
            game.advanceTicks(1)
        }
        assertTrue("第 9 波应完整刷出 16 个敌人（实测 ${firstSeenX.size}）", firstSeenX.size >= 16)
        assertTrue(
            "顶部入场敌人首次出现必须在场内（实测越界 ${firstSeenX.values.filter { it < 1f || it > PLANE_LOGICAL_WIDTH - 1f }}）",
            firstSeenX.values.all { it in 1f..(PLANE_LOGICAL_WIDTH - 1f) },
        )
    }

    // ---------------------------------------------------------------------
    // 无尽模式数值曲线报告：挂机 / 躲避机器人在 1、5、10、20、30 波
    // ---------------------------------------------------------------------

    @Test
    fun endlessScaling_report_atMilestoneWaves() {
        val milestones = listOf(1, 5, 10, 20, 30)
        println("plane-sim | wave | gruntHP | bossHP | speedMul | bulletMul | idleTicks | idleKills | botKills | botWave | botPeakBullets")
        for (wave in milestones) {
            // 挂机：跳到目标波，观察存活时间（死亡即结束）
            val idle = gameFor(PlaneDifficulty.STANDARD)
            idle.startGame(seed = 20260913L, startWave = wave)
            var idleTicks = 0
            while (idle.phase == PlanePhase.PLAYING && idleTicks < 60 * 60 * 5) {
                idle.advanceTicks(1)
                idleTicks++
            }
            assertFalse("挂机应在 5 分钟内落败（wave=$wave）", idle.player.alive)
            // 躲避机器人：固定 2 分钟窗口，记录推进节奏
            val bot = gameFor(PlaneDifficulty.STANDARD)
            bot.startGame(seed = 777L, startWave = wave)
            val dodgeBot = DodgeBot()
            var botTicks = 0
            var peakBullets = 0
            while (bot.phase == PlanePhase.PLAYING && botTicks < 60 * 120) {
                dodgeBot.step(bot)
                bot.advanceTicks(1)
                botTicks++
                peakBullets = maxOf(peakBullets, bot.enemyBullets.size)
            }
            val grunt = PlaneConfig.enemySpec(PlaneEnemyType.GRUNT, PlaneDifficulty.STANDARD, wave)
            val boss = PlaneConfig.enemySpec(PlaneEnemyType.BOSS, PlaneDifficulty.STANDARD, wave)
            println(
                "plane-sim | $wave | ${grunt.hp} | ${boss.hp} | " +
                    "${"%.3f".format(PlaneConfig.waveSpeedMul(wave))} | " +
                    "${"%.3f".format(PlaneConfig.waveBulletSpeedMul(wave))} | " +
                    "$idleTicks | ${idle.kills} | ${bot.kills} | ${bot.waveIndex} | $peakBullets",
            )
            assertTrue("机器人模拟必须保持有限状态（wave=$wave）", bot.player.x.isFinite() && bot.player.y.isFinite())
            assertTrue("敌弹峰值应有界（wave=$wave，实测 $peakBullets）", peakBullets <= 300)
        }
        assertTrue(
            "高波次 HP 应进入软上限",
            PlaneConfig.waveHpScale(30) <= PlaneConfig.waveHpScale(PLANE_MAX_WAVE),
        )
    }

    // ---------------------------------------------------------------------
    // 模拟工具
    // ---------------------------------------------------------------------

    private enum class RunResult { WIN, LOSE, STALL, TIMEOUT }

    private data class RunReport(
        val ticks: Int,
        val wave: Int,
        val kills: Int,
        val result: RunResult,
        val peakBullets: Int,
        val peakEnemies: Int,
        val peakEffects: Int,
        val peakPowerUps: Int,
    )

    private fun gameFor(difficulty: PlaneDifficulty): PlaneGame =
        PlaneGame().apply {
            resize(600f, 1080f)
            this.difficulty = difficulty
        }

    private fun runDodge(
        difficulty: PlaneDifficulty,
        seed: Long,
        maxTicks: Int,
    ): RunReport {
        val game = gameFor(difficulty)
        game.startGame(seed = seed)
        val bot = DodgeBot()
        var ticks = 0
        var peakBullets = 0
        var peakEnemies = 0
        var peakEffects = 0
        var peakPowerUps = 0
        var lastProgressTick = 0
        var lastWave = game.waveIndex
        var lastKills = game.kills
        while (game.phase == PlanePhase.PLAYING && ticks < maxTicks) {
            bot.step(game)
            game.advanceTicks(1)
            ticks++
            peakBullets = maxOf(peakBullets, game.enemyBullets.size)
            peakEnemies = maxOf(peakEnemies, game.enemies.size)
            peakEffects = maxOf(peakEffects, game.effects.size)
            peakPowerUps = maxOf(peakPowerUps, game.powerUps.size)
            if (game.waveIndex != lastWave || game.kills != lastKills) {
                lastProgressTick = ticks
                lastWave = game.waveIndex
                lastKills = game.kills
            }
            if (ticks - lastProgressTick > 60 * 150) {
                return RunReport(ticks, game.waveIndex, game.kills, RunResult.STALL, peakBullets, peakEnemies, peakEffects, peakPowerUps)
            }
        }
        val result =
            when {
                game.phase != PlanePhase.SETTLED -> RunResult.TIMEOUT
                game.player.alive -> RunResult.WIN
                else -> RunResult.LOSE
            }
        return RunReport(ticks, game.waveIndex, game.kills, result, peakBullets, peakEnemies, peakEffects, peakPowerUps)
    }

    /**
     * 躲避机器人：势场斥力躲敌弹 / 敌机 + 下半区偏置；
     * 敌弹贴近本体（含长条弹半高）时放炸弹；对停驻炮台 / Boss 按子弹飞行时间预判对齐以推进波次。
     */
    private class DodgeBot {
        fun step(game: PlaneGame) {
            val player = game.player
            if (!player.alive) return
            if (player.bombs > 0 &&
                (nearestBulletGap(game) < 2.2f || (player.lives <= 1 && nearestEnemyGap(game) < 2.5f))
            ) {
                game.useBomb()
                return
            }
            var dx = (PLANE_LOGICAL_WIDTH * 0.5f - player.x) * 0.01f
            var dy = (game.fieldH * 0.78f - player.y) * 0.05f
            for (bullet in game.enemyBullets) {
                val ex = player.x - bullet.x
                val ey = player.y - bullet.y
                val distance = hypot(ex, ey)
                val threat = 11f + bullet.halfLength
                if (distance < threat) {
                    val weight = ((threat - distance) / (distance + 1f)) * 3.2f
                    dx += ex * weight
                    dy += ey * weight
                }
            }
            for (enemy in game.enemies) {
                val ex = player.x - enemy.x
                val ey = player.y - enemy.y
                val distance = hypot(ex, ey)
                val threat = enemy.radius + 9f
                if (distance < threat) {
                    val weight = ((threat - distance) / (distance + 1f)) * 2.5f
                    dx += ex * weight
                    dy += ey * weight
                }
            }
            val target =
                game.enemies
                    .filter { it.type == PlaneEnemyType.TURRET || it.isBoss }
                    .minByOrNull { abs(it.x - player.x) }
            if (target != null && game.enemyBullets.none { hypot(player.x - it.x, player.y - it.y) < 6f }) {
                val flight = (player.y - target.y).coerceAtLeast(0f) / PlaneConfig.weaponSpec(player.weaponLevel).speed
                val weatherMul = PlaneConfig.weatherSpeedMul(game.weather)
                val targetVx = PlaneLogic.enemyVelocity(target, player.x, game.fieldH, game.difficulty.enemySpeedMul * weatherMul).first
                dx += (target.x + targetVx * flight - player.x) * 0.08f
            }
            val magnitude = hypot(dx, dy)
            if (magnitude > 1e-4f) {
                game.movePlayerTo(player.x + dx / magnitude * 1.1f, player.y + dy / magnitude * 1.1f)
            }
        }

        private fun nearestBulletGap(game: PlaneGame): Float {
            var nearest = Float.MAX_VALUE
            for (bullet in game.enemyBullets) {
                val gap = hypot(game.player.x - bullet.x, game.player.y - bullet.y) - bullet.radius - bullet.halfLength
                if (gap < nearest) nearest = gap
            }
            return nearest
        }

        private fun nearestEnemyGap(game: PlaneGame): Float {
            var nearest = Float.MAX_VALUE
            for (enemy in game.enemies) {
                val gap = hypot(game.player.x - enemy.x, game.player.y - enemy.y) - enemy.radius
                if (gap < nearest) nearest = gap
            }
            return nearest
        }
    }
}
