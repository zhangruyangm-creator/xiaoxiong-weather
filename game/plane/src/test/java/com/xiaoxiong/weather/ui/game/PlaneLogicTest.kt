package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt

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
        val l5 = PlaneConfig.weaponSpec(5)
        assertEquals(1, l1.count)
        assertEquals(2, l2.count)
        assertEquals(3, l3.count)
        assertEquals(1, l4.count)
        assertEquals(2, l5.count)
        assertFalse(l1.piercing)
        assertFalse(l2.piercing)
        assertFalse(l3.piercing)
        assertTrue(l4.piercing)
        assertTrue(l5.piercing)
        assertTrue("高等级射速更快", l1.intervalTicks > l2.intervalTicks)
        assertTrue("高等级射速更快", l2.intervalTicks > l3.intervalTicks)
        assertTrue("高等级射速更快", l3.intervalTicks > l4.intervalTicks)
        assertTrue("高等级射速更快", l4.intervalTicks > l5.intervalTicks)
        assertTrue("激光伤害更高", l4.damage > l1.damage)
        assertEquals(PlaneConfig.MAX_WEAPON_LEVEL, l5.level)
    }

    @Test
    fun weaponSpec_outOfRange_isClamped() {
        assertEquals(1, PlaneConfig.weaponSpec(0).level)
        assertEquals(PlaneConfig.MAX_WEAPON_LEVEL, PlaneConfig.weaponSpec(9).level)
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
    fun bossBulletSpecs_kinds_differ() {
        val standard = enemy(PlaneEnemyType.BOSS, x = 50f, y = 20f).also { it.bossKind = PlaneBossKind.STANDARD }
        val ring = enemy(PlaneEnemyType.BOSS, x = 50f, y = 20f).also { it.bossKind = PlaneBossKind.RINGMASTER }
        val striker = enemy(PlaneEnemyType.BOSS, x = 50f, y = 20f).also { it.bossKind = PlaneBossKind.STRIKER }
        assertEquals(3, PlaneLogic.bossBulletSpecs(standard, 50f, 150f, 30f, 0f).size)
        assertEquals(8, PlaneLogic.bossBulletSpecs(ring, 50f, 150f, 30f, 0f).size)
        assertEquals(5, PlaneLogic.bossBulletSpecs(striker, 50f, 150f, 30f, 0f).size)
        ring.bossPhase = 1
        assertEquals(16, PlaneLogic.bossBulletSpecs(ring, 50f, 150f, 30f, 0f).size)
        striker.bossPhase = 1
        assertEquals(5, PlaneLogic.bossBulletSpecs(striker, 50f, 150f, 30f, 0f).size)
    }

    @Test
    fun boss_sweepVelocity_isSymmetricAroundSpawn() {
        // 速度取 cos 才能积分为围绕出生点的对称往返；取 sin 会变成单侧漂移
        val halfPeriodTicks = ((PI / PlaneLogic.BOSS_SWEEP_OMEGA) * 60).roundToInt()
        val start = enemy(PlaneEnemyType.BOSS, x = 50f, y = PlaneLogic.BOSS_HOLD_Y, ageTicks = 0)
        val (vx0, _) = PlaneLogic.enemyVelocity(start, 50f, 180f, 1f)
        assertEquals("起始向右扫", PlaneLogic.BOSS_SWEEP_SPEED, vx0, 1e-3f)
        start.ageTicks = halfPeriodTicks
        val (vxHalf, _) = PlaneLogic.enemyVelocity(start, 50f, 180f, 1f)
        assertEquals("半周期后反向（对称）", -PlaneLogic.BOSS_SWEEP_SPEED, vxHalf, 5e-2f)
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

    @Test
    fun waveScaling_growsThenSaturatesAtSoftCaps() {
        assertTrue("HP 随波次增长", PlaneConfig.waveHpScale(3) > PlaneConfig.waveHpScale(1))
        assertEquals("HP 进入软上限", 5.5f, PlaneConfig.waveHpScale(5000), 1e-4f)
        assertEquals("分数进入软上限", 5f, PlaneConfig.waveScoreScale(5000), 1e-4f)
        assertEquals("速度进入软上限", 1.5f, PlaneConfig.waveSpeedMul(5000), 1e-4f)
        assertEquals("弹速进入软上限", 1.4f, PlaneConfig.waveBulletSpeedMul(5000), 1e-4f)
        assertEquals("密度进入软上限", 1.5f, PlaneConfig.waveFireDensityMul(5000), 1e-4f)
        assertEquals(
            "远波次使用同一平台值",
            PlaneConfig.waveHpScale(PLANE_MAX_WAVE),
            PlaneConfig.waveHpScale(PLANE_MAX_WAVE - 100),
            1e-6f,
        )
    }

    @Test
    fun enemySpec_highWave_boundedAndFireIntervalHasHardFloor() {
        val far = PlaneConfig.enemySpec(PlaneEnemyType.BOSS, PlaneDifficulty.CHALLENGE, PLANE_MAX_WAVE)
        assertTrue("Boss 高波次 HP 必须低于快照清洗上限", far.hp <= PlaneConfig.MAX_ENEMY_HP)
        val early = PlaneConfig.fireIntervalTicks(PlaneEnemyType.GRUNT, PlaneDifficulty.CHALLENGE, 1)
        val late = PlaneConfig.fireIntervalTicks(PlaneEnemyType.GRUNT, PlaneDifficulty.CHALLENGE, PLANE_MAX_WAVE)
        assertTrue("高波次开火更密", late <= early)
        assertTrue("开火间隔存在硬下限", late >= PlaneConfig.MIN_FIRE_INTERVAL_TICKS)
    }

    @Test
    fun heli_hoversThenFiresDownwardFanOnly() {
        val descending = enemy(PlaneEnemyType.HELI, y = 8f)
        val (vx0, vy0) = PlaneLogic.enemyVelocity(descending, 50f, 180f, 1f)
        assertEquals("悬停前不横移", 0f, vx0, 1e-4f)
        assertTrue("悬停前下降", vy0 > 0f)
        assertFalse(
            "未到悬停高度不开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.HELI, y = PlaneLogic.HELI_HOVER_Y - 4f), 180f),
        )

        val hovering = enemy(PlaneEnemyType.HELI, y = PlaneLogic.HELI_HOVER_Y, ageTicks = 0)
        val (vx1, _) = PlaneLogic.enemyVelocity(hovering, 50f, 180f, 1f)
        assertEquals("悬停后按正弦速度摆动", PlaneLogic.HELI_SWAY_AMP * PlaneLogic.HELI_SWAY_OMEGA, vx1, 1e-3f)
        assertTrue(PlaneLogic.enemyWantsFire(hovering, 180f))

        val leaving =
            enemy(PlaneEnemyType.HELI, y = PlaneLogic.HELI_HOVER_Y, ageTicks = PlaneLogic.HELI_HOLD_TICKS)
        val (_, exitVy) = PlaneLogic.enemyVelocity(leaving, 50f, 180f, 1f)
        assertTrue("悬停超时后应下撤离场（保证波次可清空）", exitVy > 0f)

        val fan = PlaneLogic.enemyBulletSpecs(hovering, 50f, 150f, PlaneDifficulty.STANDARD, 0f)
        assertEquals(3, fan.size)
        assertTrue("直升机只向下射击", fan.all { it.vy > 0f && it.shape == PlaneBulletShape.CIRCLE })
        assertEquals("向下扇形左右对称", -fan[0].vx, fan[2].vx, 1e-3f)
        assertEquals("中轴竖直向下", 0f, fan[1].vx, 1e-3f)
    }

    @Test
    fun tank_climbsToGroundLine_thenPatrolsAndFiresUpward() {
        val climbing = enemy(PlaneEnemyType.TANK, y = 186f)
        val (_, climbVy) = PlaneLogic.enemyVelocity(climbing, 50f, 180f, 1f)
        assertTrue("地面线以下向上爬升", climbVy < 0f)
        assertFalse(
            "未到地面线不开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.TANK, y = 120f), 180f),
        )

        val grounded = enemy(PlaneEnemyType.TANK, y = 180f - PlaneLogic.TANK_GROUND_MARGIN)
        val (_, patrolVy) = PlaneLogic.enemyVelocity(grounded, 50f, 180f, 1f)
        assertEquals("到地面线后不再升降", 0f, patrolVy, 1e-4f)
        assertTrue(PlaneLogic.enemyWantsFire(grounded, 180f))

        val specs = PlaneLogic.enemyBulletSpecs(grounded, 30f, 100f, PlaneDifficulty.STANDARD, 0f)
        assertEquals("炮弹 + 跟踪弹两发", 2, specs.size)
        val shell = specs.first { it.shape == PlaneBulletShape.CIRCLE }
        assertTrue("瞄准炮弹朝左上方的玩家", shell.vx < 0f && shell.vy < 0f)
        val mortar = specs.first { it.shape == PlaneBulletShape.CAPSULE }
        assertEquals("直射弹竖直向上", 0f, mortar.vx, 1e-4f)
        assertTrue(mortar.vy < 0f)
        assertTrue("CAPSULE 半长不小于半径", mortar.halfLength >= mortar.radius)
    }

    @Test
    fun tank_onlyFiresOnGroundLine_notWhileClimbingOrOutsideField() {
        val fieldH = 180f
        val groundY = fieldH - PlaneLogic.TANK_GROUND_MARGIN
        // 从屏幕下方进场（fieldH+4）时禁止开火：否则炮弹从画面外冒出
        assertFalse(
            "坦克未爬升到地面线（画面外）不得开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.TANK, y = fieldH + 4f), fieldH),
        )
        assertFalse(
            "坦克仍在屏幕下方爬升时不得开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.TANK, y = fieldH - 1f), fieldH),
        )
        assertFalse(
            "坦克悬浮在地面线上方（resize 后）不得开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.TANK, y = groundY - 5f), fieldH),
        )
        assertTrue(
            "贴地后才开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.TANK, y = groundY), fieldH),
        )
    }

    @Test
    fun tank_velocityConvergesToGroundLine_fromBothSides() {
        val fieldH = 180f
        val groundY = fieldH - PlaneLogic.TANK_GROUND_MARGIN
        val below = PlaneLogic.enemyVelocity(enemy(PlaneEnemyType.TANK, y = fieldH + 4f), 50f, fieldH, 1f)
        assertTrue("地面线下方应向上爬升", below.second < 0f)
        val above = PlaneLogic.enemyVelocity(enemy(PlaneEnemyType.TANK, y = groundY - 20f), 50f, fieldH, 1f)
        assertTrue("地面线上方（场高变大）应下降归位", above.second > 0f)
        val onLine = PlaneLogic.enemyVelocity(enemy(PlaneEnemyType.TANK, y = groundY), 50f, fieldH, 1f)
        assertEquals("到达地面线后停住", 0f, onLine.second, 1e-4f)
    }

    @Test
    fun scout_zigzagsAndFiresAimedShot() {
        val leftLeg = enemy(PlaneEnemyType.SCOUT, ageTicks = 0)
        val rightLeg = enemy(PlaneEnemyType.SCOUT, ageTicks = PlaneLogic.SCOUT_ZIG_TICKS)
        val (vx0, vy0) = PlaneLogic.enemyVelocity(leftLeg, 50f, 180f, 1f)
        val (vx1, _) = PlaneLogic.enemyVelocity(rightLeg, 50f, 180f, 1f)
        assertTrue("侦察机持续下降", vy0 > 0f)
        assertTrue("第一段向右", vx0 > 0f)
        assertTrue("第二段向左", vx1 < 0f)
        assertEquals("之字左右对称", vx0, -vx1, 1e-3f)

        val scout = enemy(PlaneEnemyType.SCOUT, x = 40f, y = 40f)
        assertTrue(PlaneLogic.enemyWantsFire(scout, 180f))
        val shots = PlaneLogic.enemyBulletSpecs(scout, 50f, 150f, PlaneDifficulty.STANDARD, 0f)
        assertEquals(1, shots.size)
        assertTrue("侦察机瞄准玩家", shots[0].vx > 0f && shots[0].vy > 0f)
    }

    @Test
    fun bomber_dropsThreeVerticalBombs() {
        val bomber = enemy(PlaneEnemyType.BOMBER, x = 50f, y = 40f)
        val (_, vy) = PlaneLogic.enemyVelocity(bomber, 50f, 180f, 1f)
        assertTrue("轰炸机缓慢下降", vy > 0f)
        assertTrue(PlaneLogic.enemyWantsFire(bomber, 180f))
        val bombs = PlaneLogic.enemyBulletSpecs(bomber, 10f, 10f, PlaneDifficulty.STANDARD, 0f)
        assertEquals(3, bombs.size)
        assertTrue("落弹全部竖直", bombs.all { it.vx == 0f && it.vy > 0f })
        assertEquals("左右落弹对称", bombs[0].x + bombs[2].x, 2 * bombs[1].x, 1e-3f)
    }

    @Test
    fun sniper_holdsAtTargetThenExits() {
        val descending = enemy(PlaneEnemyType.SNIPER, y = 10f, targetY = 40f)
        val (_, downVy) = PlaneLogic.enemyVelocity(descending, 50f, 180f, 1f)
        assertTrue("未到停靠高度应下降", downVy > 0f)
        assertFalse(
            "未停靠不开火",
            PlaneLogic.enemyWantsFire(enemy(PlaneEnemyType.SNIPER, y = 20f, targetY = 40f), 180f),
        )

        val holding = enemy(PlaneEnemyType.SNIPER, y = 40f, targetY = 40f, ageTicks = 120)
        val (holdVx, holdVy) = PlaneLogic.enemyVelocity(holding, 50f, 180f, 1f)
        assertEquals(0f, holdVx, 1e-4f)
        assertEquals(0f, holdVy, 1e-4f)
        assertTrue(PlaneLogic.enemyWantsFire(holding, 180f))

        val leaving =
            enemy(
                PlaneEnemyType.SNIPER,
                y = 40f,
                targetY = 40f,
                ageTicks = PlaneLogic.SNIPER_EXIT_AFTER_TICKS,
            )
        val (_, exitVy) = PlaneLogic.enemyVelocity(leaving, 50f, 180f, 1f)
        assertTrue("超时后下撤离场", exitVy > 0f)
        assertFalse("脱离阶段不开火", PlaneLogic.enemyWantsFire(leaving, 180f))

        val shot = PlaneLogic.enemyBulletSpecs(holding, 50f, 150f, PlaneDifficulty.STANDARD, 0f)
        assertEquals(1, shot.size)
        assertTrue(
            "狙击弹更快",
            kotlin.math.hypot(shot[0].vx, shot[0].vy) > PlaneEnemyType.GRUNT.baseBulletSpeed * 0.9f,
        )
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
