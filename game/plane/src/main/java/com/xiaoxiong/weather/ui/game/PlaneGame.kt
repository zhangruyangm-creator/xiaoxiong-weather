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
        internal set

    /** 当前难度；影响敌人 HP / 速度 / 弹速 / 密度 / 掉率 / 分数倍率。 */
    var difficulty: PlaneDifficulty = PlaneDifficulty.DEFAULT

    /** 开局命数（1..[PlaneConfig.START_LIVES_MAX]，默认 [PlaneConfig.START_LIVES]）；只在 [startGame] 时写入玩家。 */
    var startLives: Int = PlaneConfig.START_LIVES
        set(value) {
            field = value.coerceIn(PlaneConfig.START_LIVES_MIN, PlaneConfig.START_LIVES_MAX)
        }

    /** 屏幕层从 prefs 读入的最高分（只用于展示 / 结算比较，不参与快照）。 */
    var best: Int = 0

    /** 天气联动输入：只存值，不 import feature 层。 */
    var sceneCode: Int? = null
        internal set
    var isDay: Boolean = true
        internal set
    var weather: FarmWeather = FarmWeather.SUNNY
        internal set

    var fieldH: Float = PLANE_FIELD_H_MAX
        internal set
    var viewScale: Float = 1f
        private set
    var viewOffsetX: Float = 0f
        private set
    var viewOffsetY: Float = 0f
        private set
    internal var canvasW = 0f
    internal var canvasH = 0f

    val player = PlanePlayer()
    val enemies = mutableListOf<PlaneEnemy>()
    val playerBullets = mutableListOf<PlaneBullet>()
    val enemyBullets = mutableListOf<PlaneBullet>()
    val powerUps = mutableListOf<PlanePowerUp>()
    val effects = mutableListOf<PlaneEffect>()

    var score: Int = 0
        internal set
    var kills: Int = 0
        internal set
    var grazeCount: Int = 0
        internal set
    var combo: Int = 0
        internal set
    var comboWindowTicks: Int = 0
        internal set
    var tickCount: Long = 0L
        internal set

    /** 累计自动开火轮次：仅用于音效按逻辑计数节流，不参与模拟 / 快照。 */
    var shotCount: Long = 0L
        internal set

    /** 当前波次（1 起；0 表示开场倒计时中）。 */
    var waveIndex: Int = 0
        internal set
    var waveTick: Int = 0
        internal set
    var betweenWaveTicks: Int = 0
        internal set
    var bossWarningTicks: Int = 0
        internal set
    var bossActive: Boolean = false
        internal set

    var seed: Long = 0L
        internal set

    // 事件标志：供屏幕消费的 pendingHaptic* 风格布尔，tick 内布尔去重。
    var pendingHapticShot = false
    var pendingHapticExplosion = false
    var pendingHapticPowerup = false
    var pendingHapticPlayerHit = false
    var pendingHapticBomb = false
    var pendingHapticBossWarning = false
    var pendingHapticBossDown = false
    var pendingHapticGraze = false
    var pendingHapticWin = false
    var pendingHapticLose = false

    internal var rng = PlaneRng(1L)
    internal var nextEntityId = 1
    internal var spinPhaseRad = 0f
    internal var stepAccumulator = 0f
    internal var waveDef: PlaneWaveDef = PlaneWaveDef(0, isBoss = false, spawns = emptyList())
    internal var spawnCursor = 0
    internal var waveSpawningDone = true

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
    internal fun clampPlayerToField() {
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
     * 开始一局（无尽模式：死亡才结束，Boss 每 5 波永久循环）。
     *
     * @param seed 随机种子（同 seed 同输入结果一致）。
     * @param startWave 起始波次（1..[PLANE_MAX_WAVE]；测试 / 调试可直接跳到 Boss 波）。
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
        player.lives = startLives
        player.bombs = PlaneConfig.START_BOMBS
        player.weaponLevel = PlaneConfig.MIN_WEAPON_LEVEL
        player.invulnTicks = difficulty.hitInvulnTicks
        player.fireCooldownTicks = 0
        player.overdriveTicks = 0
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
        waveIndex = (startWave - 1).coerceIn(0, PLANE_MAX_WAVE)
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
        if (player.overdriveTicks > 0) player.overdriveTicks--
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
        val overdrive = player.overdriveTicks > 0
        val damage = if (overdrive) spec.damage + 1 else spec.damage
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
                    damage = damage,
                    radius = if (spec.piercing) PlaneConfig.LASER_RADIUS else PlaneConfig.PLAYER_BULLET_RADIUS,
                    halfLength = if (spec.piercing) PlaneConfig.LASER_HALF_LENGTH else 0f,
                    angle = angle,
                    ttlTicks = if (spec.piercing) PlaneConfig.LASER_TTL_TICKS else 0,
                    piercing = spec.piercing,
                )
        }
        val interval =
            if (overdrive) {
                (spec.intervalTicks * 2 / 3).coerceAtLeast(4)
            } else {
                spec.intervalTicks
            }
        player.fireCooldownTicks = interval
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
                    // 无尽模式：清波后只进入间隔，永不判胜；死亡才会 SETTLED。
                    betweenWaveTicks = PlaneConfig.WAVE_GAP_TICKS
                }
            }
        }
    }

    private fun beginWave() {
        waveIndex = (waveIndex + 1).coerceAtMost(PLANE_MAX_WAVE)
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
        val speedMul = PlaneConfig.waveSpeedMul(waveIndex) * difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(weather)
        val spawnX = if (def.side != 0) def.x else def.x.coerceIn(8f, PLANE_LOGICAL_WIDTH - 8f)
        val spawnY = if (def.fromBottom) fieldH + 4f else def.y
        val enemy =
            PlaneEnemy(
                id = nextEntityId++,
                type = def.type,
                x = spawnX,
                y = spawnY,
                vx = def.vx * speedMul,
                vy = def.vy * speedMul,
                hp = spec.hp,
                maxHp = spec.hp,
                fireTimerTicks = spec.fireIntervalTicks,
                bossKind =
                    if (def.type == PlaneEnemyType.BOSS) {
                        PlaneConfig.bossKindForWave(waveIndex)
                    } else {
                        PlaneBossKind.STANDARD
                    },
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
        val speedMul = PlaneConfig.waveSpeedMul(waveIndex) * difficulty.enemySpeedMul * PlaneConfig.weatherSpeedMul(weather)
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
                enemy.fireTimerTicks = PlaneConfig.fireIntervalTicks(enemy.type, difficulty, waveIndex)
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
        for (spec in PlaneLogic.enemyBulletSpecs(enemy, player.x, player.y, difficulty, spinPhaseRad, waveIndex)) {
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
                pendingHapticGraze = true
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
        player.overdriveTicks = 0
        combo = 0
        comboWindowTicks = 0
        player.invulnTicks = difficulty.hitInvulnTicks
        pendingHapticPlayerHit = true
        spawnExplosion(player.x, player.y, 10, 0xFFFF8A65.toInt())
        if (player.lives <= 0) {
            player.alive = false
            settle(win = false)
        }
    }

    /** 对局结束。[win] 分支保留给未来有终点的模式；无尽模式只以失败结算。 */
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
        val kind = PlaneConfig.rollPowerUpKind(rng.nextInt(100))
        powerUps += PlanePowerUp(id = nextEntityId++, kind = kind, x = enemy.x, y = enemy.y)
    }

    private fun applyPowerUp(kind: PlanePowerUpKind) {
        when (kind) {
            PlanePowerUpKind.WEAPON ->
                if (player.weaponLevel >= PlaneConfig.MAX_WEAPON_LEVEL) {
                    player.overdriveTicks =
                        max(player.overdriveTicks, PlaneConfig.OVERDRIVE_TICKS)
                } else {
                    player.weaponLevel += 1
                }
            PlanePowerUpKind.BOMB ->
                player.bombs = min(PlaneConfig.MAX_BOMBS, player.bombs + 1)
            PlanePowerUpKind.SHIELD ->
                player.invulnTicks =
                    max(player.invulnTicks, PlaneConfig.SHIELD_INVULN_TICKS)
            PlanePowerUpKind.LIFE ->
                player.lives = min(PlaneConfig.START_LIVES_MAX, player.lives + 1)
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
            PlaneEnemyType.HELI -> 0xFF80CBC4.toInt()
            PlaneEnemyType.TANK -> 0xFFB0BEC5.toInt()
            PlaneEnemyType.SCOUT -> 0xFF81D4FA.toInt()
            PlaneEnemyType.BOMBER -> 0xFFFFAB91.toInt()
            PlaneEnemyType.SNIPER -> 0xFFB39DDB.toInt()
            PlaneEnemyType.BOSS -> 0xFFFF8A80.toInt()
        }

    internal fun isOffscreen(
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
                graze = pendingHapticGraze,
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
        pendingHapticGraze = false
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
}
