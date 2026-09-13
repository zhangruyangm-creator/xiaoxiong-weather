package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 进行中对局快照的 prefs key。 */
internal const val PLANE_SNAPSHOT_KEY = "plane_snapshot_v1"

/** 上方向角度（弧度）。 */
private const val UP_ANGLE = (-PI / 2).toFloat()

/** 一圈弧度。 */
private const val TAU = (2 * PI).toFloat()

/**
 * 小熊战机运行时：固定步长逻辑 + 波次调度 + 弹幕 / 碰撞 + 计分 + 快照。
 *
 * 坐标系是逻辑坐标（[PLANE_LOGICAL_WIDTH] × [fieldH]），屏幕映射见 [resize] / [toLogicalX]；
 * 所有推进都由 60Hz 固定 tick 驱动（[tick] 收敛浮点 dt，[advanceTicks] 供测试精确推进），
 * 随机性全部来自 [PlaneRng]（状态可落盘），因此同 seed 同输入必然同结果。
 */
class PlaneGame {
    var phase: PlanePhase = PlanePhase.READY
        private set

    /** 当前难度；影响敌人 HP / 速度 / 弹速 / 密度 / 掉率 / 分数倍率。 */
    var difficulty: PlaneDifficulty = PlaneDifficulty.DEFAULT

    /** 屏幕层从 prefs 读入的最高分（只用于展示 / 结算比较，不参与快照）。 */
    var best: Int = 0

    /** 天气联动输入：只存值，不 import feature 层。 */
    var sceneCode: Int? = null
        private set
    var isDay: Boolean = true
        private set
    var weather: FarmWeather = FarmWeather.SUNNY
        private set

    var fieldH: Float = PLANE_FIELD_H_MAX
        private set
    var viewScale: Float = 1f
        private set
    var viewOffsetX: Float = 0f
        private set
    var viewOffsetY: Float = 0f
        private set
    private var canvasW = 0f
    private var canvasH = 0f

    val player = PlanePlayer()
    val enemies = mutableListOf<PlaneEnemy>()
    val playerBullets = mutableListOf<PlaneBullet>()
    val enemyBullets = mutableListOf<PlaneBullet>()
    val powerUps = mutableListOf<PlanePowerUp>()
    val effects = mutableListOf<PlaneEffect>()

    var score: Int = 0
        private set
    var kills: Int = 0
        private set
    var grazeCount: Int = 0
        private set
    var combo: Int = 0
        private set
    var comboWindowTicks: Int = 0
        private set
    var tickCount: Long = 0L
        private set

    /** 累计自动开火轮次：仅用于音效按逻辑计数节流，不参与模拟 / 快照。 */
    var shotCount: Long = 0L
        private set

    /** 当前波次（1 起；0 表示开场倒计时中）。 */
    var waveIndex: Int = 0
        private set
    var waveTick: Int = 0
        private set
    var betweenWaveTicks: Int = 0
        private set
    var bossWarningTicks: Int = 0
        private set
    var bossActive: Boolean = false
        private set

    var seed: Long = 0L
        private set

    // 事件标志：供屏幕消费的 pendingHaptic* 风格布尔，tick 内布尔去重。
    var pendingHapticShot = false
    var pendingHapticExplosion = false
    var pendingHapticPowerup = false
    var pendingHapticPlayerHit = false
    var pendingHapticBomb = false
    var pendingHapticBossWarning = false
    var pendingHapticBossDown = false
    var pendingHapticWin = false
    var pendingHapticLose = false

    private var rng = PlaneRng(1L)
    private var nextEntityId = 1
    private var spinPhaseRad = 0f
    private var stepAccumulator = 0f
    private var waveDef: PlaneWaveDef = PlaneWaveDef(0, isBoss = false, spawns = emptyList())
    private var spawnCursor = 0
    private var waveSpawningDone = true

    // ---------------------------------------------------------------------
    // 天气 / 视图
    // ---------------------------------------------------------------------

    /** 写入天气输入并派生 [weather]；只存 sceneCode/isDay 两个值。 */
    fun setWeather(
        sceneCode: Int?,
        isDay: Boolean?,
    ) {
        this.sceneCode = sceneCode
        this.isDay = isDay ?: true
        weather = farmWeatherFromScene(sceneCode, isDay)
    }

    /** 画布尺寸变化：按纵横比换算逻辑场高（裁剪到 [PLANE_FIELD_H_MIN]..[PLANE_FIELD_H_MAX]）并重建视图变换。 */
    fun resize(
        width: Float,
        height: Float,
    ) {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        canvasW = width
        canvasH = height
        fieldH = (PLANE_LOGICAL_WIDTH * height / width).coerceIn(PLANE_FIELD_H_MIN, PLANE_FIELD_H_MAX)
        rebuildViewTransform()
        clampPlayerToField()
    }

    /** 用当前 [fieldH] 与画布尺寸重建视图变换（[resize] 与快照恢复共用）。 */
    private fun rebuildViewTransform() {
        if (canvasW <= 0f || canvasH <= 0f) return
        viewScale = min(canvasW / PLANE_LOGICAL_WIDTH, canvasH / fieldH)
        viewOffsetX = (canvasW - PLANE_LOGICAL_WIDTH * viewScale) * 0.5f
        viewOffsetY = (canvasH - fieldH * viewScale) * 0.5f
    }

    /** 把玩家夹回场地内（画布 / 场高变化后调用）。 */
    private fun clampPlayerToField() {
        player.x = player.x.coerceIn(PlaneConfig.PLAYER_RADIUS, PLANE_LOGICAL_WIDTH - PlaneConfig.PLAYER_RADIUS)
        player.y = player.y.coerceIn(PlaneConfig.PLAYER_RADIUS * 2f, fieldH - PlaneConfig.PLAYER_RADIUS * 2f)
    }

    fun toLogicalX(screenX: Float): Float = (screenX - viewOffsetX) / viewScale

    fun toLogicalY(screenY: Float): Float = (screenY - viewOffsetY) / viewScale

    fun toScreenX(x: Float): Float = viewOffsetX + x * viewScale

    fun toScreenY(y: Float): Float = viewOffsetY + y * viewScale

    // ---------------------------------------------------------------------
    // 开局 / 暂停
    // ---------------------------------------------------------------------

    /**
     * 开始一局。
     *
     * @param seed 随机种子（同 seed 同输入结果一致）。
     * @param startWave 起始波次（1..[PLANE_TOTAL_WAVES]；测试 / 调试可直接跳到 Boss 波）。
     */
    fun startGame(
        seed: Long = System.nanoTime(),
        startWave: Int = 1,
    ) {
        this.seed = seed
        rng = PlaneRng(seed)
        phase = PlanePhase.PLAYING
        player.x = PLANE_LOGICAL_WIDTH * 0.5f
        player.y = (fieldH - 12f).coerceAtLeast(8f)
        player.lives = PlaneConfig.START_LIVES
        player.bombs = PlaneConfig.START_BOMBS
        player.weaponLevel = PlaneConfig.MIN_WEAPON_LEVEL
        player.invulnTicks = PlaneConfig.HIT_INVULN_TICKS
        player.fireCooldownTicks = 0
        player.alive = true
        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        powerUps.clear()
        effects.clear()
        score = 0
        kills = 0
        grazeCount = 0
        combo = 0
        comboWindowTicks = 0
        tickCount = 0L
        shotCount = 0L
        waveIndex = (startWave - 1).coerceIn(0, PLANE_TOTAL_WAVES)
        waveDef = PlaneWaveDef(0, isBoss = false, spawns = emptyList())
        waveTick = 0
        spawnCursor = 0
        waveSpawningDone = true
        betweenWaveTicks = PlaneConfig.WAVE_INTRO_TICKS
        bossWarningTicks = 0
        bossActive = false
        spinPhaseRad = 0f
        stepAccumulator = 0f
        nextEntityId = 1
        clearEventFlags()
    }

    fun pause() {
        if (phase == PlanePhase.PLAYING) phase = PlanePhase.PAUSED
    }

    fun resume() {
        if (phase == PlanePhase.PAUSED) phase = PlanePhase.PLAYING
    }

    /** 拖动移动：屏幕坐标由 Screen 层映射成逻辑坐标（手指上方偏移也在 Screen 层处理）。 */
    fun movePlayerTo(
        x: Float,
        y: Float,
    ) {
        if (phase != PlanePhase.PLAYING || !player.alive) return
        if (!x.isFinite() || !y.isFinite()) return
        player.x = x.coerceIn(PlaneConfig.PLAYER_RADIUS, PLANE_LOGICAL_WIDTH - PlaneConfig.PLAYER_RADIUS)
        player.y = y.coerceIn(PlaneConfig.PLAYER_RADIUS * 2f, fieldH - PlaneConfig.PLAYER_RADIUS * 2f)
    }

    // ---------------------------------------------------------------------
    // 帧推进
    // ---------------------------------------------------------------------

    /** 实时 dt 驱动：累计到固定步长后推进逻辑（掉帧时世界慢动作）。 */
    fun tick(dt: Float) {
        if (phase != PlanePhase.PLAYING) return
        if (!dt.isFinite()) return
        stepAccumulator += dt.coerceIn(0f, 0.1f)
        var steps = 0
        while (stepAccumulator >= PlaneConfig.LOGIC_STEP_SECONDS && steps < PlaneConfig.MAX_STEPS_PER_FRAME) {
            step()
            stepAccumulator -= PlaneConfig.LOGIC_STEP_SECONDS
            steps++
            if (phase != PlanePhase.PLAYING) break
        }
        val cap = PlaneConfig.LOGIC_STEP_SECONDS * PlaneConfig.MAX_STEPS_PER_FRAME
        if (stepAccumulator > cap) stepAccumulator = cap
        if (phase != PlanePhase.PLAYING) stepAccumulator = 0f
    }

    /** 精确推进 [ticks] 个逻辑 tick（测试 / 回放用）。 */
    fun advanceTicks(ticks: Int) {
        repeat(ticks) {
            if (phase != PlanePhase.PLAYING) return
            step()
        }
        stepAccumulator = 0f
    }

    private fun step() {
        tickCount++
        spinPhaseRad = (spinPhaseRad + 0.12f) % TAU
        if (player.invulnTicks > 0) player.invulnTicks--
        advanceWaves()
        if (phase != PlanePhase.PLAYING) return
        updatePlayerFire()
        updatePlayerBullets()
        updateEnemies()
        if (phase != PlanePhase.PLAYING) return
        updateEnemyBullets()
        updatePowerUps()
        updateEffects()
        updateCombo()
    }

    // ---------------------------------------------------------------------
    // 玩家开火 / 武器
    // ---------------------------------------------------------------------

    private fun updatePlayerFire() {
        if (!player.alive) return
        if (player.fireCooldownTicks > 0) player.fireCooldownTicks--
        if (player.fireCooldownTicks > 0) return
        val spec = PlaneConfig.weaponSpec(player.weaponLevel)
        for (muzzle in PlaneLogic.muzzleSpecs(spec)) {
            val angle = UP_ANGLE + muzzle.angleRad
            playerBullets +=
                PlaneBullet(
                    id = nextEntityId++,
                    owner = PlaneBulletOwner.PLAYER,
                    shape = if (spec.piercing) PlaneBulletShape.CAPSULE else PlaneBulletShape.CIRCLE,
                    x = (player.x + muzzle.offsetX).coerceIn(1f, PLANE_LOGICAL_WIDTH - 1f),
                    y = player.y - 3f,
                    vx = cos(angle) * spec.speed,
                    vy = sin(angle) * spec.speed,
                    damage = spec.damage,
                    radius = if (spec.piercing) PlaneConfig.LASER_RADIUS else PlaneConfig.PLAYER_BULLET_RADIUS,
                    halfLength = if (spec.piercing) PlaneConfig.LASER_HALF_LENGTH else 0f,
                    angle = angle,
                    ttlTicks = if (spec.piercing) PlaneConfig.LASER_TTL_TICKS else 0,
                    piercing = spec.piercing,
                )
        }
        player.fireCooldownTicks = spec.intervalTicks
        shotCount++
        pendingHapticShot = true
    }

    /** 使用炸弹：清空敌弹、对全屏敌人高伤、短无敌。无炸弹 / 非对局返回 false。 */
    fun useBomb(): Boolean {
        if (phase != PlanePhase.PLAYING || !player.alive || player.bombs <= 0) return false
        player.bombs--
        enemyBullets.clear()
        for (enemy in enemies) damageEnemy(enemy, PlaneConfig.BOMB_DAMAGE)
        purgeDeadEnemies()
        player.invulnTicks = max(player.invulnTicks, PlaneConfig.BOMB_INVULN_TICKS)
        pendingHapticBomb = true
        spawnExplosion(player.x, player.y, 18, 0xFFFFE082.toInt())
        return true
    }

    // ---------------------------------------------------------------------
    // 波次调度
    // ---------------------------------------------------------------------

    private fun advanceWaves() {
        when {
            betweenWaveTicks > 0 -> {
                betweenWaveTicks--
                if (betweenWaveTicks == 0) beginWave()
            }
            bossWarningTicks > 0 -> {
                bossWarningTicks--
                if (bossWarningTicks == 0) {
                    waveTick = 0
                    spawnCursor = 0
                    spawnDue()
                }
            }
            else -> {
                spawnDue()
                waveTick++
                if (waveSpawningDone && enemies.isEmpty()) {
                    if (waveIndex >= PLANE_TOTAL_WAVES) {
                        settle(win = true)
                    } else {
                        betweenWaveTicks = PlaneConfig.WAVE_GAP_TICKS
                    }
                }
            }
        }
    }

    private fun beginWave() {
        waveIndex = (waveIndex + 1).coerceAtMost(PLANE_TOTAL_WAVES)
        waveDef = PlaneConfig.wave(waveIndex)
        waveTick = 0
        spawnCursor = 0
        waveSpawningDone = waveDef.spawns.isEmpty()
        bossActive = false
        if (waveDef.isBoss) {
            bossWarningTicks = PlaneConfig.BOSS_WARNING_TICKS
            pendingHapticBossWarning = true
        }
    }

    private fun spawnDue() {
        while (spawnCursor < waveDef.spawns.size && waveDef.spawns[spawnCursor].delayTicks <= waveTick) {
            spawnEnemy(waveDef.spawns[spawnCursor])
            spawnCursor++
        }
        if (spawnCursor >= waveDef.spawns.size) waveSpawningDone = true
    }

    private fun spawnEnemy(def: PlaneSpawnDef) {
        val spec = PlaneConfig.enemySpec(def.type, difficulty, waveIndex)
        val speedMul = difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(weather)
        val spawnX = if (def.side != 0) def.x else def.x.coerceIn(8f, PLANE_LOGICAL_WIDTH - 8f)
        val enemy =
            PlaneEnemy(
                id = nextEntityId++,
                type = def.type,
                x = spawnX,
                y = def.y,
                vx = def.vx * speedMul,
                vy = def.vy * speedMul,
                hp = spec.hp,
                maxHp = spec.hp,
                fireTimerTicks = spec.fireIntervalTicks,
                baseX = spawnX,
                targetY = def.targetY,
                side = def.side,
                entryTicks = if (def.side != 0) PlaneConfig.SIDE_ENTRY_TICKS else 0,
            )
        enemies += enemy
        if (def.type == PlaneEnemyType.BOSS) bossActive = true
    }

    // ---------------------------------------------------------------------
    // 敌人推进
    // ---------------------------------------------------------------------

    private fun updateEnemies() {
        val dt = PlaneConfig.LOGIC_STEP_SECONDS
        val speedMul = difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(weather)
        for (enemy in enemies) {
            val (vx, vy) = PlaneLogic.enemyVelocity(enemy, player.x, fieldH, speedMul)
            enemy.x += vx * dt
            enemy.y += vy * dt
            enemy.ageTicks++
            if (enemy.side != 0 && enemy.ageTicks == enemy.entryTicks) {
                enemy.baseX = enemy.x.coerceIn(8f, PLANE_LOGICAL_WIDTH - 8f)
            }
            if (enemy.fireTimerTicks > 0) enemy.fireTimerTicks--
            if (enemy.fireTimerTicks <= 0 && PlaneLogic.enemyWantsFire(enemy, fieldH)) {
                fireEnemy(enemy)
                enemy.fireTimerTicks = PlaneConfig.fireIntervalTicks(enemy.type, difficulty)
            }
        }
        val margin = PlaneConfig.OFFSCREEN_MARGIN
        enemies.removeAll {
            !it.isBoss &&
                (it.y > fieldH + margin || it.x < -margin * 2f || it.x > PLANE_LOGICAL_WIDTH + margin * 2f)
        }
        checkRamCollision()
    }

    private fun fireEnemy(enemy: PlaneEnemy) {
        for (spec in PlaneLogic.enemyBulletSpecs(enemy, player.x, player.y, difficulty, spinPhaseRad)) {
            enemyBullets +=
                PlaneBullet(
                    id = nextEntityId++,
                    owner = PlaneBulletOwner.ENEMY,
                    shape = spec.shape,
                    x = spec.x,
                    y = spec.y,
                    vx = spec.vx,
                    vy = spec.vy,
                    damage = spec.damage,
                    radius = spec.radius,
                    halfLength = spec.halfLength,
                    angle = spec.angle,
                    boss = enemy.isBoss,
                )
        }
    }

    private fun checkRamCollision() {
        if (!player.alive || player.invulnTicks > 0) return
        for (enemy in enemies) {
            if (enemy.hp <= 0) continue
            if (PlaneLogic.circleHit(enemy.x, enemy.y, enemy.radius, player.x, player.y, PlaneConfig.PLAYER_RADIUS)) {
                damageEnemy(enemy, if (enemy.isBoss) PlaneConfig.BOSS_RAM_DAMAGE else PlaneConfig.RAM_DAMAGE)
                applyPlayerHit()
                break
            }
        }
        purgeDeadEnemies()
    }

    // ---------------------------------------------------------------------
    // 子弹推进 / 命中
    // ---------------------------------------------------------------------

    private fun updatePlayerBullets() {
        val dt = PlaneConfig.LOGIC_STEP_SECONDS
        val iterator = playerBullets.iterator()
        while (iterator.hasNext()) {
            val bullet = iterator.next()
            bullet.x += bullet.vx * dt
            bullet.y += bullet.vy * dt
            if (bullet.ttlTicks > 0) {
                bullet.ttlTicks--
                if (bullet.ttlTicks <= 0) {
                    iterator.remove()
                    continue
                }
            }
            if (isOffscreen(bullet.x, bullet.y)) {
                iterator.remove()
                continue
            }
            var consumed = false
            for (enemy in enemies) {
                if (enemy.hp <= 0) continue
                if (bullet.piercing && enemy.id in bullet.hitEnemyIds) continue
                if (PlaneLogic.bulletHitsCircle(bullet, enemy.x, enemy.y, enemy.radius)) {
                    if (bullet.piercing) bullet.hitEnemyIds += enemy.id
                    damageEnemy(enemy, bullet.damage)
                    if (!bullet.piercing) {
                        consumed = true
                        break
                    }
                }
            }
            if (consumed) iterator.remove()
        }
        purgeDeadEnemies()
    }

    private fun updateEnemyBullets() {
        val dt = PlaneConfig.LOGIC_STEP_SECONDS
        val iterator = enemyBullets.iterator()
        while (iterator.hasNext()) {
            val bullet = iterator.next()
            bullet.x += bullet.vx * dt
            bullet.y += bullet.vy * dt
            if (bullet.ttlTicks > 0) {
                bullet.ttlTicks--
                if (bullet.ttlTicks <= 0) {
                    iterator.remove()
                    continue
                }
            }
            if (isOffscreen(bullet.x, bullet.y)) {
                iterator.remove()
                continue
            }
            // 无敌期不结算命中也不吞弹：子弹穿过，仍可正常擦弹（与 ram 的 invuln 处理一致）。
            if (player.alive &&
                player.invulnTicks <= 0 &&
                PlaneLogic.bulletHitsCircle(bullet, player.x, player.y, PlaneConfig.PLAYER_RADIUS)
            ) {
                iterator.remove()
                applyPlayerHit()
                continue
            }
            if (player.alive &&
                !bullet.grazed &&
                PlaneLogic.enemyBulletGrazes(bullet, player.x, player.y, PlaneConfig.PLAYER_GRAZE_RADIUS)
            ) {
                bullet.grazed = true
                grazeCount++
                score += PlaneConfig.GRAZE_SCORE
            }
        }
    }

    private fun damageEnemy(
        enemy: PlaneEnemy,
        damage: Int,
    ) {
        if (enemy.hp <= 0 || damage <= 0) return
        enemy.hp -= damage
        if (enemy.isBoss) updateBossPhase(enemy)
    }

    /** Boss 阶段推进：跨过 2/3、1/3 血量阈值时清空敌弹并短促停顿（阶段切换）。 */
    private fun updateBossPhase(boss: PlaneEnemy) {
        val newPhase =
            when {
                boss.hp <= boss.maxHp / 3 -> 2
                boss.hp <= boss.maxHp * 2 / 3 -> 1
                else -> 0
            }
        if (newPhase > boss.bossPhase) {
            boss.bossPhase = newPhase
            boss.fireTimerTicks = 30
            enemyBullets.clear()
            spawnExplosion(boss.x, boss.y, 14, 0xFFFFAB40.toInt())
        }
    }

    private fun purgeDeadEnemies() {
        if (enemies.none { it.hp <= 0 }) return
        val dead = enemies.filter { it.hp <= 0 }
        for (enemy in dead) onEnemyKilled(enemy)
        enemies.removeAll { it.hp <= 0 }
        if (bossActive && enemies.none { it.isBoss }) bossActive = false
    }

    private fun onEnemyKilled(enemy: PlaneEnemy) {
        kills++
        combo = (combo + 1).coerceAtMost(99)
        comboWindowTicks = PlaneConfig.COMBO_WINDOW_TICKS
        val baseScore = PlaneConfig.enemySpec(enemy.type, difficulty, waveIndex).score
        score += PlaneConfig.killScore(baseScore, difficulty, combo)
        spawnExplosion(enemy.x, enemy.y, if (enemy.isBoss) 28 else 10, colorFor(enemy.type))
        if (enemy.isBoss) {
            // Boss 死亡只置 bossDown（爆炸音由屏幕侧按 Boss 专属音效播放），避免双音叠放。
            bossActive = false
            pendingHapticBossDown = true
        } else {
            pendingHapticExplosion = true
        }
        maybeDropPowerUp(enemy)
    }

    private fun applyPlayerHit() {
        if (!player.alive || player.invulnTicks > 0) return
        player.lives--
        player.weaponLevel = max(PlaneConfig.MIN_WEAPON_LEVEL, player.weaponLevel - 1)
        combo = 0
        comboWindowTicks = 0
        player.invulnTicks = PlaneConfig.HIT_INVULN_TICKS
        pendingHapticPlayerHit = true
        spawnExplosion(player.x, player.y, 10, 0xFFFF8A65.toInt())
        if (player.lives <= 0) {
            player.alive = false
            settle(win = false)
        }
    }

    private fun settle(win: Boolean) {
        if (phase == PlanePhase.SETTLED) return
        phase = PlanePhase.SETTLED
        stepAccumulator = 0f
        if (win) pendingHapticWin = true else pendingHapticLose = true
    }

    // ---------------------------------------------------------------------
    // 道具 / 特效
    // ---------------------------------------------------------------------

    private fun maybeDropPowerUp(enemy: PlaneEnemy) {
        val chance =
            if (enemy.type == PlaneEnemyType.TRANSPORT) {
                1f
            } else {
                PlaneConfig.DROP_BASE_CHANCE * difficulty.dropChanceMul
            }
        if (chance < 1f && rng.nextFloat() >= chance) return
        val kind = if (rng.nextInt(4) == 0) PlanePowerUpKind.BOMB else PlanePowerUpKind.WEAPON
        powerUps += PlanePowerUp(id = nextEntityId++, kind = kind, x = enemy.x, y = enemy.y)
    }

    private fun applyPowerUp(kind: PlanePowerUpKind) {
        when (kind) {
            PlanePowerUpKind.WEAPON ->
                player.weaponLevel = min(PlaneConfig.MAX_WEAPON_LEVEL, player.weaponLevel + 1)
            PlanePowerUpKind.BOMB ->
                player.bombs = min(PlaneConfig.MAX_BOMBS, player.bombs + 1)
        }
        score += PlaneConfig.POWERUP_SCORE
        pendingHapticPowerup = true
    }

    private fun updatePowerUps() {
        val dt = PlaneConfig.LOGIC_STEP_SECONDS
        val iterator = powerUps.iterator()
        while (iterator.hasNext()) {
            val powerUp = iterator.next()
            powerUp.y += powerUp.vy * dt
            powerUp.ttlTicks--
            if (powerUp.ttlTicks <= 0 || powerUp.y > fieldH + PlaneConfig.OFFSCREEN_MARGIN) {
                iterator.remove()
                continue
            }
            if (player.alive &&
                PlaneLogic.circleHit(
                    powerUp.x,
                    powerUp.y,
                    PlaneConfig.POWERUP_RADIUS,
                    player.x,
                    player.y,
                    PlaneConfig.PLAYER_RADIUS + PlaneConfig.POWERUP_PICKUP_PADDING,
                )
            ) {
                applyPowerUp(powerUp.kind)
                iterator.remove()
            }
        }
    }

    private fun updateEffects() {
        val dt = PlaneConfig.LOGIC_STEP_SECONDS
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.lifeTicks--
            if (effect.lifeTicks <= 0) {
                iterator.remove()
                continue
            }
            effect.x += effect.vx * dt
            effect.y += effect.vy * dt
            effect.vx *= 0.97f
            effect.vy *= 0.97f
        }
        val overflow = effects.size - PlaneConfig.MAX_EFFECTS
        if (overflow > 0) effects.subList(0, overflow).clear()
    }

    private fun updateCombo() {
        if (comboWindowTicks <= 0) return
        comboWindowTicks--
        if (comboWindowTicks == 0) combo = 0
    }

    private fun spawnExplosion(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
    ) {
        val room = PlaneConfig.MAX_EFFECTS - effects.size
        if (room <= 0) return
        repeat(count.coerceAtMost(room)) {
            val angle = rng.nextFloat() * TAU
            val speed = 8f + rng.nextFloat() * 26f
            effects +=
                PlaneEffect(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    lifeTicks = 18 + rng.nextInt(12),
                    color = color,
                    size = 0.8f + rng.nextFloat() * 0.6f,
                )
        }
    }

    private fun colorFor(type: PlaneEnemyType): Int =
        when (type) {
            PlaneEnemyType.GRUNT -> 0xFF80DEEA.toInt()
            PlaneEnemyType.DIVER -> 0xFF9FA8DA.toInt()
            PlaneEnemyType.WEAVER -> 0xFFA5D6A7.toInt()
            PlaneEnemyType.TURRET -> 0xFFFFCC80.toInt()
            PlaneEnemyType.TRANSPORT -> 0xFFFFF59D.toInt()
            PlaneEnemyType.BOSS -> 0xFFFF8A80.toInt()
        }

    private fun isOffscreen(
        x: Float,
        y: Float,
    ): Boolean =
        x < -PlaneConfig.OFFSCREEN_MARGIN ||
            x > PLANE_LOGICAL_WIDTH + PlaneConfig.OFFSCREEN_MARGIN ||
            y < -PlaneConfig.OFFSCREEN_MARGIN ||
            y > fieldH + PlaneConfig.OFFSCREEN_MARGIN

    // ---------------------------------------------------------------------
    // 事件消费
    // ---------------------------------------------------------------------

    /**
     * 读取并复位本 tick 的事件标志：同一 tick 内多次触发只置位一次（布尔去重），
     * 连续调用第二次必然全为 false。
     */
    fun consumeEvents(): PlaneEvents {
        val events =
            PlaneEvents(
                shot = pendingHapticShot,
                explosion = pendingHapticExplosion,
                powerup = pendingHapticPowerup,
                playerHit = pendingHapticPlayerHit,
                bomb = pendingHapticBomb,
                bossWarning = pendingHapticBossWarning,
                bossDown = pendingHapticBossDown,
                win = pendingHapticWin,
                lose = pendingHapticLose,
            )
        clearEventFlags()
        return events
    }

    /** 清掉未消费的事件标志（开局 / 恢复快照时调用）。 */
    fun clearEventFlags() {
        pendingHapticShot = false
        pendingHapticExplosion = false
        pendingHapticPowerup = false
        pendingHapticPlayerHit = false
        pendingHapticBomb = false
        pendingHapticBossWarning = false
        pendingHapticBossDown = false
        pendingHapticWin = false
        pendingHapticLose = false
    }

    // ---------------------------------------------------------------------
    // 快照
    // ---------------------------------------------------------------------

    /**
     * 落盘进行中的对局（PLAYING / PAUSED）；标题 / 结算视为无对局，直接清档。
     * 快照包含全部活动实体、波次 / 分数 / 命 / 弹 / 武器 / seed / RNG 状态与累计计数，
     * 恢复后可从同一 tick 精确续玩。
     */
    fun saveSnapshot(prefs: SharedPreferences) {
        if (phase != PlanePhase.PLAYING && phase != PlanePhase.PAUSED) {
            clearSnapshot(prefs)
            return
        }
        val payload = runCatching { PlaneSnapshotCodec.encode(captureSnapshot()) }.getOrNull() ?: return
        GameSnapshot.save(prefs, PLANE_SNAPSHOT_KEY, payload)
    }

    /** 恢复进行中快照；无档 / 坏档 / 版本不符返回 false 且清档，当前对局保持 READY。 */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        if (phase != PlanePhase.READY) return false
        val raw = GameSnapshot.load(prefs, PLANE_SNAPSHOT_KEY) ?: return false
        val snapshot = runCatching { PlaneSnapshotCodec.decode(raw) }.getOrNull()
        if (snapshot == null) {
            clearSnapshot(prefs)
            return false
        }
        return runCatching {
            applySnapshot(snapshot)
            true
        }.getOrElse {
            runCatching { phase = PlanePhase.READY }
            clearSnapshot(prefs)
            false
        }
    }

    /** 清档：重开 / 结算 / 退出时调用，避免陈旧续玩。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        GameSnapshot.clear(prefs, PLANE_SNAPSHOT_KEY)
    }

    private fun captureSnapshot(): PlaneSnapshot =
        PlaneSnapshot(
            phase = phase,
            difficulty = difficulty,
            fieldH = fieldH,
            sceneCode = sceneCode,
            isDay = isDay,
            weather = weather,
            seed = seed,
            rngState = rng.state,
            waveIndex = waveIndex,
            waveTick = waveTick,
            betweenWaveTicks = betweenWaveTicks,
            bossWarningTicks = bossWarningTicks,
            bossActive = bossActive,
            spawnCursor = spawnCursor,
            waveSpawningDone = waveSpawningDone,
            spinPhaseRad = spinPhaseRad,
            player =
                PlanePlayerSnap(
                    x = player.x,
                    y = player.y,
                    lives = player.lives,
                    bombs = player.bombs,
                    weaponLevel = player.weaponLevel,
                    invulnTicks = player.invulnTicks,
                    fireCooldownTicks = player.fireCooldownTicks,
                    alive = player.alive,
                ),
            score = score,
            kills = kills,
            grazeCount = grazeCount,
            combo = combo,
            comboWindowTicks = comboWindowTicks,
            tickCount = tickCount,
            nextEntityId = nextEntityId,
            stepAccumulator = stepAccumulator,
            enemies = enemies.map { it.toSnapshot() },
            playerBullets = playerBullets.map { it.toSnapshot() },
            enemyBullets = enemyBullets.map { it.toSnapshot() },
            powerUps = powerUps.map { it.toSnapshot() },
            effects = effects.map { it.toSnapshot() },
        )

    private fun applySnapshot(snapshot: PlaneSnapshot) {
        val safe = snapshot.sanitized()
        // 恢复成功即进入暂停：屏幕侧据此弹出暂停菜单，避免带弹幕立刻开跑第一帧掉命。
        phase = PlanePhase.PAUSED
        difficulty = safe.difficulty
        fieldH = safe.fieldH
        sceneCode = safe.sceneCode
        isDay = safe.isDay
        weather = safe.weather
        seed = safe.seed
        rng.restore(safe.rngState)
        waveIndex = safe.waveIndex
        waveTick = safe.waveTick
        betweenWaveTicks = safe.betweenWaveTicks
        bossWarningTicks = safe.bossWarningTicks
        bossActive = safe.bossActive
        spawnCursor = safe.spawnCursor
        waveSpawningDone = safe.waveSpawningDone
        spinPhaseRad = safe.spinPhaseRad
        waveDef = PlaneConfig.wave(safe.waveIndex)
        player.x = safe.player.x
        player.y = safe.player.y
        player.lives = safe.player.lives
        player.bombs = safe.player.bombs
        player.weaponLevel = safe.player.weaponLevel
        player.invulnTicks = safe.player.invulnTicks
        player.fireCooldownTicks = safe.player.fireCooldownTicks
        player.alive = safe.player.alive
        score = safe.score
        kills = safe.kills
        grazeCount = safe.grazeCount
        combo = safe.combo
        comboWindowTicks = safe.comboWindowTicks
        tickCount = safe.tickCount
        stepAccumulator = safe.stepAccumulator
        enemies.clear()
        enemies += safe.enemies.map { it.toEnemy() }
        playerBullets.clear()
        playerBullets += safe.playerBullets.map { it.toBullet() }
        enemyBullets.clear()
        enemyBullets += safe.enemyBullets.map { it.toBullet() }
        powerUps.clear()
        powerUps += safe.powerUps.map { it.toPowerUp() }
        effects.clear()
        effects += safe.effects.map { it.toEffect() }
        val maxId =
            (
                enemies.map { it.id } +
                    playerBullets.map { it.id } +
                    enemyBullets.map { it.id } +
                    powerUps.map { it.id }
            ).maxOrNull() ?: 0
        nextEntityId = max(safe.nextEntityId, maxId + 1)
        clearEventFlags()
        shotCount = 0L
        // 画布可用时按当前纵横比重算 fieldH / 视图变换（存档 fieldH 仅作无画布回退），
        // 再夹回玩家并清理越界实体，避免早期 / 旋转后恢复残留旧坐标系。
        resize(canvasW, canvasH)
        clampPlayerToField()
        purgeOutOfBoundsEntities()
    }

    /** 清理落在场地围栏外的实体（与每 tick 的离屏规则一致；Boss 不参与场高剔除）。 */
    private fun purgeOutOfBoundsEntities() {
        val margin = PlaneConfig.OFFSCREEN_MARGIN
        enemies.removeAll {
            !it.isBoss &&
                (it.y > fieldH + margin || it.x < -margin * 2f || it.x > PLANE_LOGICAL_WIDTH + margin * 2f)
        }
        playerBullets.removeAll { isOffscreen(it.x, it.y) }
        enemyBullets.removeAll { isOffscreen(it.x, it.y) }
        powerUps.removeAll { it.y > fieldH + margin }
    }

    /**
     * 语义清洗：结构合法的快照仍可能语义坏损（布尔与命数矛盾 / 计数越界 / NaN 坐标），
     * 恢复前统一收敛到合法值，而不是整档丢弃。
     */
    private fun PlaneSnapshot.sanitized(): PlaneSnapshot {
        val maxInvuln = max(PlaneConfig.HIT_INVULN_TICKS, PlaneConfig.BOMB_INVULN_TICKS)
        val maxCooldown =
            (PlaneConfig.MIN_WEAPON_LEVEL..PlaneConfig.MAX_WEAPON_LEVEL)
                .maxOf { PlaneConfig.weaponSpec(it).intervalTicks }
        val safeLives = player.lives.coerceAtLeast(1)
        val safeWaveIndex = waveIndex.coerceIn(0, PLANE_TOTAL_WAVES)
        val safeSpawnCount = PlaneConfig.wave(safeWaveIndex).spawns.size
        return copy(
            waveIndex = safeWaveIndex,
            waveTick = waveTick.coerceAtLeast(0),
            betweenWaveTicks = betweenWaveTicks.coerceIn(0, max(PlaneConfig.WAVE_INTRO_TICKS, PlaneConfig.WAVE_GAP_TICKS)),
            bossWarningTicks = bossWarningTicks.coerceIn(0, PlaneConfig.BOSS_WARNING_TICKS),
            spawnCursor = spawnCursor.coerceIn(0, safeSpawnCount),
            spinPhaseRad = spinPhaseRad.finiteOr(0f),
            player =
                player.copy(
                    x = player.x.finiteOr(PLANE_LOGICAL_WIDTH * 0.5f),
                    y = player.y.finiteOr(0f),
                    lives = safeLives,
                    bombs = player.bombs.coerceIn(0, PlaneConfig.MAX_BOMBS),
                    weaponLevel = player.weaponLevel.coerceIn(PlaneConfig.MIN_WEAPON_LEVEL, PlaneConfig.MAX_WEAPON_LEVEL),
                    invulnTicks = player.invulnTicks.coerceIn(0, maxInvuln),
                    fireCooldownTicks = player.fireCooldownTicks.coerceIn(0, maxCooldown),
                    alive = safeLives > 0,
                ),
            tickCount = tickCount.coerceAtLeast(0L),
            stepAccumulator =
                stepAccumulator
                    .finiteOr(0f)
                    .coerceIn(0f, PlaneConfig.LOGIC_STEP_SECONDS * PlaneConfig.MAX_STEPS_PER_FRAME),
            enemies = enemies.map { it.sanitized() },
            playerBullets = playerBullets.map { it.sanitized() },
            enemyBullets = enemyBullets.map { it.sanitized() },
            powerUps = powerUps.map { it.sanitized() },
            effects = effects.map { it.sanitized() },
        )
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

private fun PlaneEnemySnap.sanitized(): PlaneEnemySnap {
    val safeMaxHp = maxHp.coerceIn(0, PlaneConfig.MAX_ENEMY_HP)
    return copy(
        x = x.finiteOr(0f),
        y = y.finiteOr(0f),
        vx = vx.finiteOr(0f),
        vy = vy.finiteOr(0f),
        hp = hp.coerceIn(0, safeMaxHp),
        maxHp = safeMaxHp,
        fireTimerTicks = fireTimerTicks.coerceAtLeast(0),
        ageTicks = ageTicks.coerceAtLeast(0),
        bossPhase = if (type == PlaneEnemyType.BOSS) bossPhase.coerceIn(0, 2) else 0,
        baseX = baseX.finiteOr(0f),
        targetY = targetY.finiteOr(0f),
        side = side.coerceIn(-1, 1),
        entryTicks = entryTicks.coerceIn(0, PlaneConfig.SIDE_ENTRY_TICKS),
    )
}

private fun PlaneBulletSnap.sanitized(): PlaneBulletSnap =
    copy(
        x = x.finiteOr(0f),
        y = y.finiteOr(0f),
        vx = vx.finiteOr(0f),
        vy = vy.finiteOr(0f),
        radius = radius.finiteOr(0f).coerceAtLeast(0f),
        halfLength = halfLength.finiteOr(0f).coerceAtLeast(0f),
        angle = angle.finiteOr(0f),
        ttlTicks = ttlTicks.coerceIn(0, PlaneConfig.LASER_TTL_TICKS),
        hitEnemyIds = hitEnemyIds.filter { it > 0 }.distinct(),
    )

private fun PlanePowerUpSnap.sanitized(): PlanePowerUpSnap =
    copy(
        x = x.finiteOr(0f),
        y = y.finiteOr(0f),
        vy = vy.finiteOr(0f),
        ttlTicks = ttlTicks.coerceAtLeast(0),
    )

private fun PlaneEffectSnap.sanitized(): PlaneEffectSnap =
    copy(
        x = x.finiteOr(0f),
        y = y.finiteOr(0f),
        vx = vx.finiteOr(0f),
        vy = vy.finiteOr(0f),
        lifeTicks = lifeTicks.coerceAtLeast(0),
        size = size.finiteOr(0.9f).coerceAtLeast(0.1f),
    )

private data class PlanePlayerSnap(
    val x: Float,
    val y: Float,
    val lives: Int,
    val bombs: Int,
    val weaponLevel: Int,
    val invulnTicks: Int,
    val fireCooldownTicks: Int,
    val alive: Boolean,
)

private data class PlaneEnemySnap(
    val id: Int,
    val type: PlaneEnemyType,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val hp: Int,
    val maxHp: Int,
    val fireTimerTicks: Int,
    val ageTicks: Int,
    val bossPhase: Int,
    val baseX: Float,
    val targetY: Float,
    val side: Int,
    val entryTicks: Int,
)

private data class PlaneBulletSnap(
    val id: Int,
    val owner: PlaneBulletOwner,
    val shape: PlaneBulletShape,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Int,
    val radius: Float,
    val halfLength: Float,
    val angle: Float,
    val ttlTicks: Int,
    val piercing: Boolean,
    val grazed: Boolean,
    val boss: Boolean,
    val hitEnemyIds: List<Int>,
)

private data class PlanePowerUpSnap(
    val id: Int,
    val kind: PlanePowerUpKind,
    val x: Float,
    val y: Float,
    val vy: Float,
    val ttlTicks: Int,
)

private data class PlaneEffectSnap(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val lifeTicks: Int,
    val color: Int,
    val size: Float,
)

private data class PlaneSnapshot(
    val phase: PlanePhase,
    val difficulty: PlaneDifficulty,
    val fieldH: Float,
    val sceneCode: Int?,
    val isDay: Boolean,
    val weather: FarmWeather,
    val seed: Long,
    val rngState: Long,
    val waveIndex: Int,
    val waveTick: Int,
    val betweenWaveTicks: Int,
    val bossWarningTicks: Int,
    val bossActive: Boolean,
    val spawnCursor: Int,
    val waveSpawningDone: Boolean,
    val spinPhaseRad: Float,
    val player: PlanePlayerSnap,
    val score: Int,
    val kills: Int,
    val grazeCount: Int,
    val combo: Int,
    val comboWindowTicks: Int,
    val tickCount: Long,
    val nextEntityId: Int,
    val stepAccumulator: Float,
    val enemies: List<PlaneEnemySnap>,
    val playerBullets: List<PlaneBulletSnap>,
    val enemyBullets: List<PlaneBulletSnap>,
    val powerUps: List<PlanePowerUpSnap>,
    val effects: List<PlaneEffectSnap>,
)

private fun PlaneEnemy.toSnapshot(): PlaneEnemySnap =
    PlaneEnemySnap(
        id = id,
        type = type,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        hp = hp,
        maxHp = maxHp,
        fireTimerTicks = fireTimerTicks,
        ageTicks = ageTicks,
        bossPhase = bossPhase,
        baseX = baseX,
        targetY = targetY,
        side = side,
        entryTicks = entryTicks,
    )

private fun PlaneEnemySnap.toEnemy(): PlaneEnemy =
    PlaneEnemy(
        id = id,
        type = type,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        hp = hp,
        maxHp = maxHp,
        fireTimerTicks = fireTimerTicks,
        ageTicks = ageTicks,
        bossPhase = bossPhase,
        baseX = baseX,
        targetY = targetY,
        side = side,
        entryTicks = entryTicks,
    )

private fun PlaneBullet.toSnapshot(): PlaneBulletSnap =
    PlaneBulletSnap(
        id = id,
        owner = owner,
        shape = shape,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        damage = damage,
        radius = radius,
        halfLength = halfLength,
        angle = angle,
        ttlTicks = ttlTicks,
        piercing = piercing,
        grazed = grazed,
        boss = boss,
        hitEnemyIds = hitEnemyIds.toList(),
    )

private fun PlaneBulletSnap.toBullet(): PlaneBullet =
    PlaneBullet(
        id = id,
        owner = owner,
        shape = shape,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        damage = damage,
        radius = radius,
        halfLength = halfLength,
        angle = angle,
        ttlTicks = ttlTicks,
        piercing = piercing,
        boss = boss,
    ).also {
        it.grazed = grazed
        it.hitEnemyIds += hitEnemyIds
    }

private fun PlanePowerUp.toSnapshot(): PlanePowerUpSnap = PlanePowerUpSnap(id = id, kind = kind, x = x, y = y, vy = vy, ttlTicks = ttlTicks)

private fun PlanePowerUpSnap.toPowerUp(): PlanePowerUp = PlanePowerUp(id = id, kind = kind, x = x, y = y, vy = vy, ttlTicks = ttlTicks)

private fun PlaneEffect.toSnapshot(): PlaneEffectSnap =
    PlaneEffectSnap(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        lifeTicks = lifeTicks,
        color = color,
        size = size,
    )

private fun PlaneEffectSnap.toEffect(): PlaneEffect =
    PlaneEffect(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        lifeTicks = lifeTicks,
        color = color,
        size = size,
    )

private inline fun <reified T : Enum<T>> planeEnumOrNull(name: String?): T? =
    if (name.isNullOrBlank()) null else runCatching { enumValueOf<T>(name) }.getOrNull()

private fun planeBool(value: Boolean): String = if (value) "1" else "0"

private fun String.planeBoolOrNull(): Boolean? =
    when (this) {
        "1" -> true
        "0" -> false
        else -> null
    }

private fun String.planeIntList(): List<Int>? = if (isBlank()) emptyList() else split(':').map { it.toIntOrNull() ?: return null }

/**
 * 对局快照的紧凑文本编解码（每行 `key=value`，实体每行一条）。
 *
 * 版本不符 / 缺字段 / 结构损坏一律 [decode] 返回 null（旧档安全拒绝），
 * 不使用 kotlinx.serialization 以保持游戏模块零序列化运行时依赖。
 */
private object PlaneSnapshotCodec {
    private const val VERSION = 3

    fun encode(snapshot: PlaneSnapshot): String {
        val lines = ArrayList<String>()
        lines += "v=$VERSION"
        lines += "phase=${snapshot.phase.name}"
        lines += "diff=${snapshot.difficulty.id}"
        lines += "field=${snapshot.fieldH}"
        lines += "weather=${snapshot.sceneCode ?: -1},${planeBool(snapshot.isDay)},${snapshot.weather.name}"
        lines += "seed=${snapshot.seed}"
        lines += "rng=${snapshot.rngState}"
        lines +=
            "wave=${snapshot.waveIndex},${snapshot.waveTick},${snapshot.betweenWaveTicks}," +
            "${snapshot.bossWarningTicks},${planeBool(snapshot.bossActive)},${snapshot.spawnCursor}," +
            "${planeBool(snapshot.waveSpawningDone)},${snapshot.spinPhaseRad}"
        val p = snapshot.player
        lines +=
            "player=${p.x},${p.y},${p.lives},${p.bombs},${p.weaponLevel}," +
            "${p.invulnTicks},${p.fireCooldownTicks},${planeBool(p.alive)}"
        lines +=
            "stats=${snapshot.score},${snapshot.kills},${snapshot.grazeCount},${snapshot.combo}," +
            "${snapshot.comboWindowTicks},${snapshot.tickCount},${snapshot.nextEntityId},${snapshot.stepAccumulator}"
        for (e in snapshot.enemies) {
            lines +=
                "enemy=${e.id},${e.type.name},${e.x},${e.y},${e.vx},${e.vy},${e.hp},${e.maxHp}," +
                "${e.fireTimerTicks},${e.ageTicks},${e.bossPhase},${e.baseX}," +
                "${e.targetY},${e.side},${e.entryTicks}"
        }
        for (b in snapshot.playerBullets) lines += encodeBullet("pbullet", b)
        for (b in snapshot.enemyBullets) lines += encodeBullet("ebullet", b)
        for (power in snapshot.powerUps) {
            lines += "power=${power.id},${power.kind.name},${power.x},${power.y},${power.vy},${power.ttlTicks}"
        }
        for (effect in snapshot.effects) {
            lines +=
                "effect=${effect.x},${effect.y},${effect.vx},${effect.vy}," +
                "${effect.lifeTicks},${effect.color},${effect.size}"
        }
        return lines.joinToString("\n")
    }

    private fun encodeBullet(
        key: String,
        b: PlaneBulletSnap,
    ): String =
        "$key=${b.id},${b.owner.name},${b.shape.name},${b.x},${b.y},${b.vx},${b.vy},${b.damage}," +
            "${b.radius},${b.halfLength},${b.angle},${b.ttlTicks},${planeBool(b.piercing)}," +
            "${planeBool(b.grazed)},${planeBool(b.boss)},${b.hitEnemyIds.joinToString(":")}"

    fun decode(raw: String): PlaneSnapshot? {
        if (raw.isBlank()) return null
        val values = HashMap<String, String>()
        val enemyLines = ArrayList<String>()
        val playerBulletLines = ArrayList<String>()
        val enemyBulletLines = ArrayList<String>()
        val powerLines = ArrayList<String>()
        val effectLines = ArrayList<String>()
        for (line in raw.split('\n')) {
            if (line.isBlank()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) return null
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            when (key) {
                "enemy" -> enemyLines += value
                "pbullet" -> playerBulletLines += value
                "ebullet" -> enemyBulletLines += value
                "power" -> powerLines += value
                "effect" -> effectLines += value
                else -> values[key] = value
            }
        }
        if (values["v"]?.toIntOrNull() != VERSION) return null

        val phase = planeEnumOrNull<PlanePhase>(values["phase"]) ?: return null
        if (phase != PlanePhase.PLAYING && phase != PlanePhase.PAUSED) return null
        val difficulty =
            if (values["diff"].isNullOrBlank()) {
                PlaneDifficulty.DEFAULT
            } else {
                PlaneDifficulty.entries.firstOrNull { it.id == values["diff"] } ?: return null
            }
        val fieldH = values["field"]?.toFloatOrNull() ?: return null
        if (!fieldH.isFinite() || fieldH <= 0f) return null
        val seed = values["seed"]?.toLongOrNull() ?: return null
        val rngState = values["rng"]?.toLongOrNull() ?: return null

        val weatherParts = values["weather"]?.split(',') ?: return null
        if (weatherParts.size != 3) return null
        val sceneCode = weatherParts[0].toIntOrNull() ?: return null
        val isDay = weatherParts[1].planeBoolOrNull() ?: return null
        val weather =
            planeEnumOrNull<FarmWeather>(weatherParts[2])
                ?: farmWeatherFromScene(sceneCode.takeIf { it >= 0 }, isDay)

        val waveParts = values["wave"]?.split(',') ?: return null
        if (waveParts.size != 8) return null
        val waveIndex = waveParts[0].toIntOrNull() ?: return null
        val waveTick = waveParts[1].toIntOrNull() ?: return null
        val betweenWaveTicks = waveParts[2].toIntOrNull() ?: return null
        val bossWarningTicks = waveParts[3].toIntOrNull() ?: return null
        val bossActive = waveParts[4].planeBoolOrNull() ?: return null
        val spawnCursor = waveParts[5].toIntOrNull() ?: return null
        val waveSpawningDone = waveParts[6].planeBoolOrNull() ?: return null
        val spinPhaseRad = waveParts[7].toFloatOrNull() ?: return null
        if (waveIndex < 0 || waveTick < 0 || betweenWaveTicks < 0 || bossWarningTicks < 0 || spawnCursor < 0) return null

        val playerParts = values["player"]?.split(',') ?: return null
        if (playerParts.size != 8) return null
        val player =
            PlanePlayerSnap(
                x = playerParts[0].toFloatOrNull() ?: return null,
                y = playerParts[1].toFloatOrNull() ?: return null,
                lives = playerParts[2].toIntOrNull() ?: return null,
                bombs = playerParts[3].toIntOrNull() ?: return null,
                weaponLevel = playerParts[4].toIntOrNull() ?: return null,
                invulnTicks = playerParts[5].toIntOrNull() ?: return null,
                fireCooldownTicks = playerParts[6].toIntOrNull() ?: return null,
                alive = playerParts[7].planeBoolOrNull() ?: return null,
            )
        // 坐标 / 速度等语义坏值不在这里整档拒绝，交给 PlaneGame.sanitized() 统一清洗；
        // lives=0 无法继续对局（软锁），整档拒绝并清档。
        if (player.lives <= 0 || player.lives > 9 || player.bombs < 0 || player.bombs > 9) return null

        val statsParts = values["stats"]?.split(',') ?: return null
        if (statsParts.size != 8) return null
        val score = statsParts[0].toIntOrNull() ?: return null
        val kills = statsParts[1].toIntOrNull() ?: return null
        val grazeCount = statsParts[2].toIntOrNull() ?: return null
        val combo = statsParts[3].toIntOrNull() ?: return null
        val comboWindowTicks = statsParts[4].toIntOrNull() ?: return null
        val tickCount = statsParts[5].toLongOrNull() ?: return null
        val nextEntityId = statsParts[6].toIntOrNull() ?: return null
        val stepAccumulator = statsParts[7].toFloatOrNull() ?: return null
        if (score < 0 || kills < 0 || grazeCount < 0 || combo < 0 || comboWindowTicks < 0 || tickCount < 0) return null
        if (nextEntityId <= 0 || !stepAccumulator.isFinite() || stepAccumulator < 0f) return null

        val enemies = enemyLines.map { parseEnemy(it) ?: return null }
        val playerBullets = playerBulletLines.map { parseBullet(it) ?: return null }
        val enemyBullets = enemyBulletLines.map { parseBullet(it) ?: return null }
        val powerUps = powerLines.map { parsePowerUp(it) ?: return null }
        val effects = effectLines.map { parseEffect(it) ?: return null }

        return PlaneSnapshot(
            phase = phase,
            difficulty = difficulty,
            fieldH = fieldH.coerceIn(PLANE_FIELD_H_MIN, PLANE_FIELD_H_MAX),
            sceneCode = sceneCode.takeIf { it >= 0 },
            isDay = isDay,
            weather = weather,
            seed = seed,
            rngState = rngState,
            waveIndex = waveIndex.coerceAtMost(PLANE_TOTAL_WAVES),
            waveTick = waveTick,
            betweenWaveTicks = betweenWaveTicks,
            bossWarningTicks = bossWarningTicks,
            bossActive = bossActive,
            spawnCursor = spawnCursor,
            waveSpawningDone = waveSpawningDone,
            spinPhaseRad = if (spinPhaseRad.isFinite()) spinPhaseRad else 0f,
            player = player,
            score = score,
            kills = kills,
            grazeCount = grazeCount,
            combo = combo,
            comboWindowTicks = comboWindowTicks,
            tickCount = tickCount,
            nextEntityId = nextEntityId,
            stepAccumulator = stepAccumulator,
            enemies = enemies,
            playerBullets = playerBullets,
            enemyBullets = enemyBullets,
            powerUps = powerUps,
            effects = effects,
        )
    }

    private fun parseEnemy(line: String): PlaneEnemySnap? {
        val f = line.split(',')
        if (f.size != 15) return null
        return PlaneEnemySnap(
            id = f[0].toIntOrNull() ?: return null,
            type = planeEnumOrNull<PlaneEnemyType>(f[1]) ?: return null,
            x = f[2].toFloatOrNull() ?: return null,
            y = f[3].toFloatOrNull() ?: return null,
            vx = f[4].toFloatOrNull() ?: return null,
            vy = f[5].toFloatOrNull() ?: return null,
            hp = f[6].toIntOrNull() ?: return null,
            maxHp = f[7].toIntOrNull() ?: return null,
            fireTimerTicks = f[8].toIntOrNull() ?: return null,
            ageTicks = f[9].toIntOrNull() ?: return null,
            bossPhase = f[10].toIntOrNull() ?: return null,
            baseX = f[11].toFloatOrNull() ?: return null,
            targetY = f[12].toFloatOrNull() ?: return null,
            side = f[13].toIntOrNull() ?: return null,
            entryTicks = f[14].toIntOrNull() ?: return null,
        )
    }

    private fun parseBullet(line: String): PlaneBulletSnap? {
        val f = line.split(',')
        if (f.size != 16) return null
        return PlaneBulletSnap(
            id = f[0].toIntOrNull() ?: return null,
            owner = planeEnumOrNull<PlaneBulletOwner>(f[1]) ?: return null,
            shape = planeEnumOrNull<PlaneBulletShape>(f[2]) ?: return null,
            x = f[3].toFloatOrNull() ?: return null,
            y = f[4].toFloatOrNull() ?: return null,
            vx = f[5].toFloatOrNull() ?: return null,
            vy = f[6].toFloatOrNull() ?: return null,
            damage = f[7].toIntOrNull() ?: return null,
            radius = f[8].toFloatOrNull() ?: return null,
            halfLength = f[9].toFloatOrNull() ?: return null,
            angle = f[10].toFloatOrNull() ?: return null,
            ttlTicks = f[11].toIntOrNull() ?: return null,
            piercing = f[12].planeBoolOrNull() ?: return null,
            grazed = f[13].planeBoolOrNull() ?: return null,
            boss = f[14].planeBoolOrNull() ?: return null,
            hitEnemyIds = f[15].planeIntList() ?: return null,
        )
    }

    private fun parsePowerUp(line: String): PlanePowerUpSnap? {
        val f = line.split(',')
        if (f.size != 6) return null
        return PlanePowerUpSnap(
            id = f[0].toIntOrNull() ?: return null,
            kind = planeEnumOrNull<PlanePowerUpKind>(f[1]) ?: return null,
            x = f[2].toFloatOrNull() ?: return null,
            y = f[3].toFloatOrNull() ?: return null,
            vy = f[4].toFloatOrNull() ?: return null,
            ttlTicks = f[5].toIntOrNull() ?: return null,
        )
    }

    private fun parseEffect(line: String): PlaneEffectSnap? {
        val f = line.split(',')
        if (f.size != 7) return null
        return PlaneEffectSnap(
            x = f[0].toFloatOrNull() ?: return null,
            y = f[1].toFloatOrNull() ?: return null,
            vx = f[2].toFloatOrNull() ?: return null,
            vy = f[3].toFloatOrNull() ?: return null,
            lifeTicks = f[4].toIntOrNull() ?: return null,
            color = f[5].toIntOrNull() ?: return null,
            size = f[6].toFloatOrNull() ?: return null,
        )
    }
}
