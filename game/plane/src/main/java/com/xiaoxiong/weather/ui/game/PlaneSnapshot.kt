package com.xiaoxiong.weather.ui.game

import kotlin.math.max

internal fun PlaneGame.captureSnapshot(): PlaneSnapshot =
    PlaneSnapshot(
        phase = phase,
        difficulty = difficulty,
        startLives = startLives,
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

internal fun PlaneGame.applySnapshot(snapshot: PlaneSnapshot) {
    val safe = snapshot.sanitized()
    // 恢复成功即进入暂停：屏幕侧据此弹出暂停菜单，避免带弹幕立刻开跑第一帧掉命。
    phase = PlanePhase.PAUSED
    difficulty = safe.difficulty
    startLives = safe.startLives
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
    // 已死敌人（hp<=0）不恢复：否则恢复后的第一 tick 会被 purgeDeadEnemies 当成新击杀计分。
    enemies += safe.enemies.map { it.toEnemy() }.filter { it.hp > 0 }
    // bossActive 与实体一致：坏档里 boss 已不存在时不能残留 Boss HUD / 血条。
    bossActive = safe.bossActive && enemies.any { it.isBoss }
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
internal fun PlaneGame.purgeOutOfBoundsEntities() {
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
    val safeLives = player.lives.coerceIn(1, PlaneConfig.START_LIVES_MAX)
    val safeWaveIndex = waveIndex.coerceIn(0, PLANE_MAX_WAVE)
    val safeSpawnCount = PlaneConfig.wave(safeWaveIndex).spawns.size
    return copy(
        startLives = startLives.coerceIn(PlaneConfig.START_LIVES_MIN, PlaneConfig.START_LIVES_MAX),
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

internal data class PlanePlayerSnap(
    val x: Float,
    val y: Float,
    val lives: Int,
    val bombs: Int,
    val weaponLevel: Int,
    val invulnTicks: Int,
    val fireCooldownTicks: Int,
    val alive: Boolean,
)

internal data class PlaneEnemySnap(
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

internal data class PlaneBulletSnap(
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

internal data class PlanePowerUpSnap(
    val id: Int,
    val kind: PlanePowerUpKind,
    val x: Float,
    val y: Float,
    val vy: Float,
    val ttlTicks: Int,
)

internal data class PlaneEffectSnap(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val lifeTicks: Int,
    val color: Int,
    val size: Float,
)

internal data class PlaneSnapshot(
    val phase: PlanePhase,
    val difficulty: PlaneDifficulty,
    val startLives: Int,
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
internal object PlaneSnapshotCodec {
    private const val VERSION = 4

    fun encode(snapshot: PlaneSnapshot): String {
        val lines = ArrayList<String>()
        lines += "v=$VERSION"
        lines += "phase=${snapshot.phase.name}"
        lines += "diff=${snapshot.difficulty.id}"
        lines += "start=${snapshot.startLives}"
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
        val startLives =
            (values["start"]?.toIntOrNull() ?: PlaneConfig.START_LIVES)
                .coerceIn(PlaneConfig.START_LIVES_MIN, PlaneConfig.START_LIVES_MAX)
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
            startLives = startLives,
            fieldH = fieldH.coerceIn(PLANE_FIELD_H_MIN, PLANE_FIELD_H_MAX),
            sceneCode = sceneCode.takeIf { it >= 0 },
            isDay = isDay,
            weather = weather,
            seed = seed,
            rngState = rngState,
            waveIndex = waveIndex.coerceAtMost(PLANE_MAX_WAVE),
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
