package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaneLogic] / [PlaneConfig] 纯逻辑单测：武器、碰撞、弹幕模式、难度 / 天气缩放、RNG。
 * 全部不依赖 Compose / Android，运行在纯 JVM。
 */
class PlaneLogicTest {
    private fun bullet(
        shape: PlaneBulletShape,
        x: Float = 50f,
        y: Float = 50f,
        vx: Float = 0f,
        vy: Float = 0f,
        radius: Float = 1f,
        halfLength: Float = 0f,
        angle: Float = 0f,
        key: Int = 1,
    ): PlaneBullet =
        PlaneBullet(
            id = key,
            owner = PlaneBulletOwner.ENEMY,
            shape = shape,
            x = x,
            y = y,
            vx = vx,
            vy = vy,
            damage = 1,
            radius = radius,
            halfLength = halfLength,
            angle = angle,
        )

    private fun enemy(
        type: PlaneEnemyType,
        x: Float = 50f,
        y: Float = 10f,
        ageTicks: Int = 0,
        targetY: Float = 0f,
        fireTimerTicks: Int = 0,
    ): PlaneEnemy =
        PlaneEnemy(
            id = 1,
            type = type,
            x = x,
            y = y,
            vx = 0f,
            vy = 0f,
            hp = 3,
            maxHp = 3,
            fireTimerTicks = fireTimerTicks,
            ageTicks = ageTicks,
            targetY = targetY,
        )

    // ---------------------------------------------------------------------
    // 武器
    // ---------------------------------------------------------------------

    @Test
    fun weaponSpec_levels_matchDesign() {
        val l1 = PlaneConfig.weaponSpec(1)
        val l2 = PlaneConfig.weaponSpec(2)
        val l3 = PlaneConfig.weaponSpec(3)
        val l4 = PlaneConfig.weaponSpec(4)
        assertEquals(1, l1.count)
        assertEquals(2, l2.count)
        assertEquals(3, l3.count)
        assertEquals(1, l4.count)
        assertFalse(l1.piercing)
        assertFalse(l2.piercing)
        assertFalse(l3.piercing)
        assertTrue(l4.piercing)
        assertTrue("高等级射速更快", l1.intervalTicks > l2.intervalTicks)
        assertTrue("高等级射速更快", l2.intervalTicks > l3.intervalTicks)
        assertTrue("高等级射速更快", l3.intervalTicks > l4.intervalTicks)
        assertTrue("激光伤害更高", l4.damage > l1.damage)
    }

    @Test
    fun weaponSpec_outOfRange_isClamped() {
        assertEquals(1, PlaneConfig.weaponSpec(0).level)
        assertEquals(4, PlaneConfig.weaponSpec(9).level)
        assertEquals(1, PlaneConfig.weaponSpec(-3).level)
    }

    @Test
    fun muzzleSpecs_singleDoubleTriple() {
        val single = PlaneLogic.muzzleSpecs(PlaneConfig.weaponSpec(1))
        assertEquals(1, single.size)
        assertEquals(0f, single[0].offsetX, 1e-4f)

        val double = PlaneLogic.muzzleSpecs(PlaneConfig.weaponSpec(2))
        assertEquals(2, double.size)
        assertEquals(-double[1].offsetX, double[0].offsetX, 1e-4f)

        val triple = PlaneLogic.muzzleSpecs(PlaneConfig.weaponSpec(3))
        assertEquals(3, triple.size)
        assertEquals(0f, triple[1].angleRad, 1e-4f)
        assertEquals(-triple[2].angleRad, triple[0].angleRad, 1e-4f)
    }

    // ---------------------------------------------------------------------
    // 碰撞
    // ---------------------------------------------------------------------

    @Test
    fun circleHit_touchingCountsAsHit() {
        assertTrue(PlaneLogic.circleHit(0f, 0f, 1f, 1.5f, 0f, 0.5f))
        assertFalse(PlaneLogic.circleHit(0f, 0f, 1f, 1.6f, 0f, 0.5f))
    }

    @Test
    fun capsuleCircleHit_sideAndEnds() {
        // 沿 x 轴、半长 5、半径 1 的胶囊；圆心在正上方距离 1.5 -> 命中
        assertTrue(PlaneLogic.capsuleCircleHit(0f, 1.5f, 0.5f, 0f, 0f, 0f, 5f, 1f))
        // 正上方距离 2 -> 不命中
        assertFalse(PlaneLogic.capsuleCircleHit(0f, 2.1f, 0.5f, 0f, 0f, 0f, 5f, 1f))
        // 端点延长线外：x=7 时距离端点 2 -> 不命中
        assertFalse(PlaneLogic.capsuleCircleHit(7f, 0f, 0.5f, 0f, 0f, 0f, 5f, 1f))
    }

    @Test
    fun rectCircleHit_insideAndOutside() {
        assertTrue("圆在矩形内", PlaneLogic.rectCircleHit(0f, 0f, 0.5f, 0f, 0f, 3f, 2f))
        assertTrue("圆贴边命中", PlaneLogic.rectCircleHit(3.4f, 0f, 0.5f, 0f, 0f, 3f, 2f))
        assertFalse("圆在角外", PlaneLogic.rectCircleHit(3.6f, 2.6f, 0.4f, 0f, 0f, 3f, 2f))
    }

    @Test
    fun ellipseCircleHit_insideAndOutside() {
        assertTrue(PlaneLogic.ellipseCircleHit(3f, 0f, 0.5f, 0f, 0f, 4f, 2f))
        assertFalse(PlaneLogic.ellipseCircleHit(4.8f, 0f, 0.5f, 0f, 0f, 4f, 2f))
        assertFalse(PlaneLogic.ellipseCircleHit(0f, 0f, 0.5f, 0f, 0f, 0f, 2f))
    }

    @Test
    fun bulletHitsCircle_dispatchesByShape() {
        assertTrue(
            PlaneLogic.bulletHitsCircle(
                bullet(PlaneBulletShape.CIRCLE, x = 0f, y = 0f, radius = 1f),
                2f,
                0f,
                1.4f,
            ),
        )
        assertTrue(
            PlaneLogic.bulletHitsCircle(
                bullet(PlaneBulletShape.CAPSULE, x = 0f, y = 0f, radius = 1f, halfLength = 4f, angle = 0f),
                0f,
                1.4f,
                0.5f,
            ),
        )
        assertTrue(
            PlaneLogic.bulletHitsCircle(
                bullet(PlaneBulletShape.RECT, x = 0f, y = 0f, radius = 3f, halfLength = 1f),
                0f,
                1.4f,
                0.5f,
            ),
        )
        assertFalse(
            PlaneLogic.bulletHitsCircle(
                bullet(PlaneBulletShape.RECT, x = 0f, y = 0f, radius = 3f, halfLength = 1f),
                0f,
                3.6f,
                0.5f,
            ),
        )
    }

    // ---------------------------------------------------------------------
    // 弹幕模式
    // ---------------------------------------------------------------------

    @Test
    fun aimedFan_centersOnTarget() {
        val fan = PlaneLogic.aimedFan(0f, 0f, 0f, 10f, count = 3, spreadRad = 0.2f, speed = 10f)
        assertEquals(3, fan.size)
        // 中轴朝向玩家：向下的正分量
        assertTrue(fan[1].vy > 0f)
        assertEquals(0f, fan[1].vx, 1e-4f)
        // 两侧角度对称
        assertEquals(-fan[2].vx, fan[0].vx, 1e-3f)
        assertEquals(fan[2].vy, fan[0].vy, 1e-3f)
        // 速度模长守恒
        assertEquals(10f, kotlin.math.hypot(fan[0].vx, fan[0].vy), 1e-3f)
    }

    @Test
    fun aimedFan_singleShot_pointsAtTarget() {
        val fan = PlaneLogic.aimedFan(0f, 0f, 10f, 0f, count = 1, spreadRad = 0.3f, speed = 5f)
        assertEquals(1, fan.size)
        assertEquals(5f, fan[0].vx, 1e-3f)
        assertEquals(0f, fan[0].vy, 1e-3f)
    }

    @Test
    fun ring_evenSpacingAndCount() {
        val ring = PlaneLogic.ring(0f, 0f, count = 8, speed = 6f, phaseRad = 0f)
        assertEquals(8, ring.size)
        for (spec in ring) {
            assertEquals(6f, kotlin.math.hypot(spec.vx, spec.vy), 1e-3f)
        }
        val angles =
            ring.map {
                val a = kotlin.math.atan2(it.vy, it.vx)
                if (a < 0f) a + (2 * Math.PI).toFloat() else a
            }
        val steps = angles.zipWithNext { a, b -> b - a }
        for (step in steps) {
            assertEquals((2 * Math.PI / 8).toFloat(), step, 1e-3f)
        }
    }

    @Test
    fun bossBulletSpecs_phaseCounts() {
        val boss = enemy(PlaneEnemyType.BOSS, x = 50f, y = 20f)
        boss.bossPhase = 0
        assertEquals(3, PlaneLogic.bossBulletSpecs(boss, 50f, 150f, 30f, 0f).size)
        boss.bossPhase = 1
        assertEquals(12, PlaneLogic.bossBulletSpecs(boss, 50f, 150f, 30f, 0f).size)
        boss.bossPhase = 2
        assertEquals(14, PlaneLogic.bossBulletSpecs(boss, 50f, 150f, 30f, 0f).size)
    }

    @Test
    fun turretBulletSpecs_includeRectBar() {
        val turret = enemy(PlaneEnemyType.TURRET, x = 30f, y = 32f, targetY = 32f)
        val specs = PlaneLogic.enemyBulletSpecs(turret, 50f, 150f, PlaneDifficulty.STANDARD, 0f)
        assertEquals("双向瞄准 + 一根长条", 3, specs.size)
        assertEquals(2, specs.count { it.shape == PlaneBulletShape.CIRCLE })
        val bar = specs.single { it.shape == PlaneBulletShape.RECT }
        assertEquals(PlaneConfig.TURRET_BAR_HALF_WIDTH, bar.radius, 1e-4f)
        assertEquals(PlaneConfig.TURRET_BAR_HALF_HEIGHT, bar.halfLength, 1e-4f)
        assertTrue("长条弹竖直下压", bar.vy > 0f)
        assertEquals(0f, bar.vx, 1e-4f)
        assertTrue("半高不小于半宽（渲染 / 判定一致约定）", bar.halfLength >= bar.radius)
    }

    @Test
    fun enemyBulletGrazes_rectUsesEllipseEnvelope() {
        val bar = bullet(PlaneBulletShape.RECT, x = 0f, y = 0f, radius = 3f, halfLength = 8f)
        assertTrue("长条弹纵长方向椭圆包络内擦弹", PlaneLogic.enemyBulletGrazes(bar, 0f, 8f, 1f))
        assertFalse("超出椭圆包络不擦弹", PlaneLogic.enemyBulletGrazes(bar, 0f, 9.5f, 1f))
        // 普通弹仍按圆形外扩判定
        val dot = bullet(PlaneBulletShape.CIRCLE, x = 0f, y = 0f, radius = 1f)
        assertTrue(PlaneLogic.enemyBulletGrazes(dot, 1.5f, 0f, 1f))
        assertFalse(PlaneLogic.enemyBulletGrazes(dot, 2.5f, 0f, 1f))
    }

    // ---------------------------------------------------------------------
    // 难度 / 天气 / 计分
    // ---------------------------------------------------------------------

    @Test
    fun enemySpec_difficultyAndWaveScaling() {
        val casual = PlaneConfig.enemySpec(PlaneEnemyType.GRUNT, PlaneDifficulty.CASUAL, 1)
        val standard = PlaneConfig.enemySpec(PlaneEnemyType.GRUNT, PlaneDifficulty.STANDARD, 1)
        val challenge = PlaneConfig.enemySpec(PlaneEnemyType.GRUNT, PlaneDifficulty.CHALLENGE, 1)
        assertTrue(casual.hp < standard.hp)
        assertTrue(standard.hp < challenge.hp)
        assertTrue(casual.bulletSpeed < challenge.bulletSpeed)
        assertTrue("挑战难度开火间隔更短", challenge.fireIntervalTicks < casual.fireIntervalTicks)
        val laterWave = PlaneConfig.enemySpec(PlaneEnemyType.GRUNT, PlaneDifficulty.STANDARD, 6)
        assertTrue("后续波次 HP 更高", laterWave.hp > standard.hp)
    }

    @Test
    fun difficultyIds_areStableAndDefaultStandard() {
        assertEquals(PlaneDifficulty.STANDARD, PlaneDifficulty.fromId(null))
        assertEquals(PlaneDifficulty.STANDARD, PlaneDifficulty.fromId("bogus"))
        assertEquals(PlaneDifficulty.CASUAL, PlaneDifficulty.fromId("casual"))
        assertEquals(PlaneDifficulty.CHALLENGE, PlaneDifficulty.fromId("challenge"))
    }

    @Test
    fun weatherSpeedMul_onlySnowAndStorm() {
        assertEquals(1f, PlaneConfig.weatherSpeedMul(FarmWeather.SUNNY), 1e-6f)
        assertTrue(PlaneConfig.weatherSpeedMul(FarmWeather.SNOWY) < 1f)
        assertTrue(PlaneConfig.weatherSpeedMul(FarmWeather.STORMY) > 1f)
    }

    @Test
    fun killScore_appliesDifficultyAndCombo() {
        val base = 100
        assertEquals(80, PlaneConfig.killScore(base, PlaneDifficulty.CASUAL, combo = 1))
        assertEquals(100, PlaneConfig.killScore(base, PlaneDifficulty.STANDARD, combo = 1))
        assertEquals(150, PlaneConfig.killScore(base, PlaneDifficulty.CHALLENGE, combo = 1))
        // 连杀每级 +5，封顶 10 级
        assertEquals(105, PlaneConfig.killScore(base, PlaneDifficulty.STANDARD, combo = 2))
        assertEquals(145, PlaneConfig.killScore(base, PlaneDifficulty.STANDARD, combo = 10))
        assertEquals(150, PlaneConfig.killScore(base, PlaneDifficulty.STANDARD, combo = 30))
    }

    // ---------------------------------------------------------------------
    // 敌人行为
    // ---------------------------------------------------------------------

    @Test
    fun enemyVelocity_turretStopsAtTarget() {
        val descending = enemy(PlaneEnemyType.TURRET, y = 10f, targetY = 30f)
        val down = PlaneLogic.enemyVelocity(descending, 50f, 180f, 1f)
        assertTrue(down.second > 0f)
        val arrived = enemy(PlaneEnemyType.TURRET, y = 30f, targetY = 30f)
        val still = PlaneLogic.enemyVelocity(arrived, 50f, 180f, 1f)
        assertEquals(0f, still.first, 1e-4f)
        assertEquals(0f, still.second, 1e-4f)
    }

    @Test
    fun enemyVelocity_diverAcceleratesAfterDelay() {
        val early = enemy(PlaneEnemyType.DIVER, ageTicks = 0)
        val earlyVy = PlaneLogic.enemyVelocity(early, 50f, 180f, 1f).second
        val diving = enemy(PlaneEnemyType.DIVER, ageTicks = PlaneLogic.DIVER_DIVE_AFTER_TICKS + 60)
        val divingPair = PlaneLogic.enemyVelocity(diving, 10f, 180f, 1f)
        assertTrue("俯冲后竖直速度更快", divingPair.second > earlyVy)
        assertTrue("俯冲会朝玩家横向修正", divingPair.first < 0f)
    }

    @Test
    fun enemyWantsFire_respectsTypeAndBounds() {
        val gruntEven = enemy(PlaneEnemyType.GRUNT, x = 50f, y = 30f).apply { }
        // id=1 为奇数：小兵按奇偶交替开火
        assertFalse(PlaneLogic.enemyWantsFire(gruntEven, 180f))
        val offscreen = enemy(PlaneEnemyType.WEAVER, x = -4f, y = 30f)
        assertFalse(PlaneLogic.enemyWantsFire(offscreen, 180f))
        val transport = enemy(PlaneEnemyType.TRANSPORT, y = 30f)
        assertFalse(PlaneLogic.enemyWantsFire(transport, 180f))
    }

    // ---------------------------------------------------------------------
    // RNG
    // ---------------------------------------------------------------------

    @Test
    fun planeRng_sameSeed_sameSequence_andRestoreResumes() {
        val a = PlaneRng(2026L)
        val b = PlaneRng(2026L)
        repeat(32) {
            assertEquals(a.nextLong(), b.nextLong())
        }
        // 序列推进后恢复：续算结果与另一条同状态流一致
        val c = PlaneRng(7L)
        repeat(5) { c.nextLong() }
        val restored = PlaneRng(123L)
        restored.restore(c.state)
        assertEquals(c.nextLong(), restored.nextLong())
        assertNotEquals(a.state, PlaneRng(2027L).state)
    }

    @Test
    fun planeRng_nextIntAndFloat_inRange() {
        val rng = PlaneRng(99L)
        repeat(100) {
            val i = rng.nextInt(7)
            assertTrue(i in 0 until 7)
            val f = rng.nextFloat()
            assertTrue(f >= 0f && f < 1f)
        }
    }
}
