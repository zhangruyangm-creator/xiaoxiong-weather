package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 摘星快照键：对局进行中落盘，结束/重开/退出时清除。 */
private const val STAR_SNAPSHOT_KEY = "star_snapshot_v1"

/** 快照 meta 段的字段数，用于坏档校验（≥19 兼容旧档；≥24 含难度/连杀/擦边）。 */
private const val STAR_SNAPSHOT_META_FIELDS = 19
private const val STAR_SNAPSHOT_META_FIELDS_V2 = 24

internal enum class PlatType { NORMAL, MOVING, BREAK, SPRING }

internal class Platform(
    var x: Float,
    val y: Float,
    val w: Float,
    val type: PlatType,
    var vx: Float = 0f,
    var broken: Boolean = false,
    var breakT: Float = 0f,
    /** 本局是否已计过擦边（每台一次）。 */
    var grazed: Boolean = false,
)

internal class StarItem(
    val x: Float,
    val y: Float,
    var taken: Boolean = false,
    val seed: Float,
)

internal class JumpFloatText(
    val text: String,
    val x: Float,
    val y: Float,
    var life: Float,
)

/** 摘星场景：纯晴天（非雨天）时为夜晚（star 字面义，星最多）；阴天灰云；雨/雪/雷暴渲染雨雪。 */
enum class JumpScene { SUNNY, CLOUDY, RAIN_LIGHT, RAIN, RAIN_HEAVY, SNOW, STORM, FREEZING_RAIN, HAIL }

internal fun jumpSceneFromWeather(
    weather: FarmWeather,
    sceneCode: Int?,
): JumpScene =
    when {
        // 冻雨/冰雹：原码保留在 displayCode 中，优先于普通雨/雷暴识别
        isFreezingRainCode(sceneCode) -> JumpScene.FREEZING_RAIN
        isHailCode(sceneCode) -> JumpScene.HAIL
        else ->
            when (weather) {
                FarmWeather.SNOWY -> JumpScene.SNOW
                FarmWeather.RAINY ->
                    when (rainStrengthOf(sceneCode)) {
                        RainStrength.LIGHT -> JumpScene.RAIN_LIGHT
                        RainStrength.HEAVY -> JumpScene.RAIN_HEAVY
                        else -> JumpScene.RAIN
                    }
                FarmWeather.STORMY -> JumpScene.STORM
                FarmWeather.CLOUDY, FarmWeather.FOGGY -> JumpScene.CLOUDY
                FarmWeather.SUNNY -> JumpScene.SUNNY
            }
    }

/**
 * 小熊摘星：竖版跳台。拖动控制方向，踩云向上；
 * 天空随高度从白天渐变到黄昏、夜空再到太空。
 * 背景天气跟随当前天气：阴/雨/雪时天空变灰、星星减少、并落下雨雪。
 */
internal class StarJumpGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var difficulty by mutableStateOf(StarDifficulty.STANDARD)
    var trailTint by mutableStateOf(StarTrailTint.CLASSIC)

    /** 当前天气场景；由 [BearStarJumpScreen] 在天气变化时刷新。 */
    var scene by mutableStateOf(JumpScene.SUNNY)

    var w = 0f
    var h = 0f
    var s = 1f
    var time = 0f

    val bearSize get() = 58f * s
    var bearX = 0f // 中心 x（屏幕坐标，环绕）
    var bearY = 0f // 中心 y（世界坐标，向上为负）
    var bearVX = 0f
    var bearVY = 0f
    var cameraY = 0f // 屏幕顶对应的世界 y
    var maxHeight = 0f // 到达过的最小 bearY
    var starsCollected by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var comboWindowSec = 0f
    var comboBonusTotal = 0
    var grazeCount by mutableIntStateOf(0)
    var pendingGraze = false
    var pendingBandTheme: StarAltitudeTheme? = null
    private var lastBandTheme = StarAltitudeTheme.DAY

    var touchX: Float? = null
    var squashT = -1f
    var shakeT = 0f
    var deadT = 0f

    val platforms = mutableListOf<Platform>()
    val stars = mutableListOf<StarItem>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<JumpFloatText>()
    var nextPlatY = 0f

    /** 主动放弃/结束/退出后置位：阻止 ON_STOP/onDispose 的兜底落盘把已清理的对局写回复活。 */
    private var snapshotDiscarded = false

    /** 恢复出的快照尺寸：首次 setDims 若与之一致则保留恢复状态，避免被整局回准备态。 */
    private var restoredDims: Triple<Float, Float, Float>? = null

    val altitudeKm get() = StarJumpLogic.altitudeKm(cameraY, s)

    fun setDims(
        width: Float,
        height: Float,
        scale: Float,
        preserveRunning: Boolean = false,
    ) {
        if (width <= 0f || height <= 0f) return
        val first = w <= 0f || h <= 0f
        if (!first && w == width && h == height && abs(s - scale) < 0.001f) return
        // 恢复快照后首次拿到与存档一致的尺寸：保留恢复出的对局，不能整局回准备态；
        // 真实旋转导致尺寸变化时 restoredDims 不匹配，仍按原逻辑重置。
        // preserveRunning：暂停中改窗口尺寸（分屏折叠等）保留对局，避免暂停菜单分数失效。
        val restoredMatch =
            restoredDims?.let { abs(it.first - width) < 0.5f && abs(it.second - height) < 0.5f } == true
        val keepRestored = phase == GamePhase.RUNNING && (restoredMatch || preserveRunning)
        w = width
        h = height
        s = scale
        // 注：jumpTextPaint（Android Paint）的字号在绘制时按 s 设置，
        // 不在这里触碰，保证 setDims 可在纯 JVM 单测中调用。
        restoredDims = null
        if (keepRestored) return
        when (phase) {
            // 结算界面改窗口（旋转/分屏）：只更新尺寸，保留本局分数（对齐摘星/台球的结算改窗处理）
            GamePhase.OVER -> Unit
            // 对局中改尺寸会让平台像素与碰撞错位，整局回准备态（与跑酷一致）；
            // 同时标记快照失效，避免停在 READY 被兜底落盘写回复活。
            GamePhase.RUNNING -> {
                snapshotDiscarded = true
                phase = GamePhase.READY
                reset()
            }
            else -> {
                snapshotDiscarded = true
                reset()
            }
        }
    }

    fun reset() {
        val bs = bearSize
        platforms.clear()
        stars.clear()
        particles.clear()
        texts.clear()
        bearX = w / 2f
        bearY = -bs / 2f
        bearVX = 0f
        bearVY = 0f
        cameraY = -h * 0.55f
        maxHeight = bearY
        starsCollected = 0
        score = 0
        combo = 0
        comboWindowSec = 0f
        comboBonusTotal = 0
        grazeCount = 0
        pendingGraze = false
        pendingBandTheme = null
        lastBandTheme = StarAltitudeTheme.DAY
        time = 0f
        squashT = -1f
        shakeT = 0f
        deadT = 0f
        touchX = null
        // 起跳平台（贴地面）+ 预生成上方云朵
        platforms.add(Platform(w / 2f - 110f * s, 0f, 220f * s, PlatType.NORMAL))
        nextPlatY = -150f * s
        genPlatforms(cameraY - h)
    }

    fun start() {
        snapshotDiscarded = false
        reset()
        phase = GamePhase.RUNNING
        bearVY = -1400f * s
    }

    private fun genPlatforms(limitY: Float) {
        val margin = 8f * s
        val maxTravel = (w - margin * 2f).coerceAtLeast(1f)
        while (nextPlatY > limitY) {
            val progress = (-nextPlatY / (3400f * s)).coerceIn(0f, 1f)
            val roll = Random.nextFloat()
            val early = nextPlatY > -700f * s
            // 类型与宽度决策委托引擎层（可测）；x 与 vx 仍用独立随机，保持原生成分布
            val (type, pw) = StarJumpLogic.platformShape(progress, early, roll, w, s, difficulty)
            val xSpan = (maxTravel - pw).coerceAtLeast(1f)
            val x = margin + Random.nextFloat() * xSpan
            val vx =
                if (type == PlatType.MOVING) {
                    (50f + 85f * progress) * s * difficulty.movingSpeedMul * if (Random.nextBoolean()) 1f else -1f
                } else {
                    0f
                }
            platforms.add(Platform(x, nextPlatY, pw, type, vx))
            val gap = (95f + 78f * progress + Random.nextFloat() * 30f) * s * difficulty.gapMul
            val starChance = 0.26f * difficulty.starChanceMul
            if (!early && Random.nextFloat() < starChance) {
                stars.add(
                    StarItem(
                        x = 30f * s + Random.nextFloat() * (w - 60f * s).coerceAtLeast(1f),
                        y = nextPlatY - gap * 0.45f,
                        seed = Random.nextFloat() * 6.28f,
                    ),
                )
            }
            nextPlatY -= gap
        }
    }

    fun tick(
        dt: Float,
        onBounce: () -> Unit,
        onStar: () -> Unit,
        onSpring: () -> Unit,
        onDeath: (Int) -> Unit,
    ) {
        time += dt
        if (phase == GamePhase.READY) return
        val bs = bearSize

        if (phase == GamePhase.RUNNING) {
            // 水平：跟随手指目标位置，无触摸时惯性衰减
            val tx = touchX
            if (tx != null) {
                val desired = ((tx - bearX) * 8f).coerceIn(-760f * s, 760f * s)
                bearVX += (desired - bearVX) * min(1f, dt * 11f)
            } else {
                bearVX *= 1f - min(1f, dt * 2.2f)
            }
            bearX += bearVX * dt
            // 左右环绕
            if (bearX < -bs * 0.5f) bearX += w + bs
            if (bearX > w + bs * 0.5f) bearX -= w + bs
        }

        val prevY = bearY
        // 垂直物理（委托引擎层，帧行为可测）
        val falling = StarJumpLogic.stepFalling(bearY, bearVY, s, dt)
        bearY = falling.first
        bearVY = falling.second

        // 移动平台
        val margin = 8f * s
        for (p in platforms) {
            if (p.type == PlatType.MOVING && !p.broken) {
                val maxX = (w - margin - p.w).coerceAtLeast(margin)
                if (maxX <= margin + 1f) {
                    // 几乎无行程：钉在中间，避免贴边抖动
                    p.x = (w - p.w) * 0.5f
                    p.vx = 0f
                } else {
                    p.x += p.vx * dt
                    if (p.x < margin) {
                        p.x = margin
                        p.vx = abs(p.vx)
                    } else if (p.x > maxX) {
                        p.x = maxX
                        p.vx = -abs(p.vx)
                    }
                }
            }
            if (p.broken) p.breakT += dt
        }

        if (phase == GamePhase.RUNNING) {
            if (comboWindowSec > 0f) {
                comboWindowSec -= dt
                if (comboWindowSec <= 0f) {
                    comboWindowSec = 0f
                    combo = 0
                }
            }
            // 踩平台：仅下落时、脚线穿越台面（判定委托引擎层）
            if (bearVY > 0f) {
                val contact =
                    StarJumpLogic.resolveContact(
                        bearX = bearX,
                        prevFeetY = prevY + bs / 2f,
                        newFeetY = bearY + bs / 2f,
                        bearVY = bearVY,
                        platforms = platforms,
                        w = w,
                        h = h,
                        s = s,
                        cameraY = cameraY,
                    )
                if (contact.hit && contact.platform != null) {
                    val p = contact.platform
                    bearY = contact.landY
                    combo = (combo + 1).coerceAtMost(99)
                    comboWindowSec = STAR_COMBO_WINDOW_SEC
                    val bounceBonus = StarJumpLogic.bounceComboBonus(combo)
                    comboBonusTotal += bounceBonus
                    if (bounceBonus > 0) {
                        texts.add(JumpFloatText("×$combo +$bounceBonus", bearX, bearY - bs, 0.85f))
                    }
                    if (p.type == PlatType.BREAK) {
                        p.broken = true
                        p.breakT = 0f
                        onBounce()
                    } else if (p.type == PlatType.SPRING) {
                        shakeT = 0.10f
                        onSpring()
                    } else {
                        onBounce()
                    }
                    bearVY = contact.bounceVY
                    squashT = 0f
                    val trailColor = starTrailParticleColor(trailTint)
                    repeat(8) {
                        particles.add(
                            Particle(
                                bearX + (Random.nextFloat() - 0.5f) * bs,
                                p.y,
                                (Random.nextFloat() - 0.5f) * 160f * s,
                                -Random.nextFloat() * 120f * s,
                                0.4f + Random.nextFloat() * 0.3f,
                                color = trailColor,
                            ),
                        )
                    }
                } else {
                    // 擦边：贴台未踩中
                    for (p in platforms) {
                        if (p.grazed || p.broken) continue
                        if (
                            StarJumpLogic.nearMissGraze(
                                bearX = bearX,
                                prevFeetY = prevY + bs / 2f,
                                newFeetY = bearY + bs / 2f,
                                bearVY = bearVY,
                                platform = p,
                                w = w,
                                h = h,
                                s = s,
                                cameraY = cameraY,
                            )
                        ) {
                            p.grazed = true
                            grazeCount++
                            pendingGraze = true
                            texts.add(JumpFloatText("+${STAR_GRAZE_SCORE}", bearX, p.y, 0.7f))
                            break
                        }
                    }
                }
            }

            // 吃星星
            for (st in stars) {
                if (st.taken) continue
                if (StarJumpLogic.starPickup(bearX, bearY, st.x, st.y, w, s)) {
                    st.taken = true
                    starsCollected++
                    texts.add(JumpFloatText("+25", st.x, st.y, 0.9f))
                    repeat(10) {
                        val a = Random.nextFloat() * 6.28f
                        particles.add(
                            Particle(
                                st.x,
                                st.y,
                                kotlin.math.cos(a) * (60f + Random.nextFloat() * 140f) * s,
                                sin(a) * (60f + Random.nextFloat() * 140f) * s,
                                0.5f + Random.nextFloat() * 0.3f,
                                color = Color(0xFFFFD54F),
                            ),
                        )
                    }
                    onStar()
                }
            }

            // 相机只上不下
            val target = bearY - h * 0.42f
            if (target < cameraY) cameraY = target
            maxHeight = min(maxHeight, bearY)
            score =
                StarJumpLogic.scoreFor(
                    maxHeight = maxHeight,
                    starsCollected = starsCollected,
                    s = s,
                    difficulty = difficulty,
                    comboBonusTotal = comboBonusTotal,
                    grazeCount = grazeCount,
                )
            val band = starAltitudeTheme(altitudeKm)
            if (band != lastBandTheme) {
                lastBandTheme = band
                if (band != StarAltitudeTheme.DAY) pendingBandTheme = band
            }

            genPlatforms(cameraY - h * 0.5f)
            platforms.removeAll { it.y > cameraY + h + 120f * s }
            stars.removeAll { it.y > cameraY + h + 120f * s }

            // 掉出屏幕底部 → 结束
            if (bearY - cameraY > h + bs) {
                phase = GamePhase.OVER
                touchX = null
                deadT = 0f
                shakeT = 0.3f
                onDeath(score)
            }
        } else {
            deadT += dt
        }

        if (squashT >= 0f) {
            squashT += dt
            if (squashT > 0.16f) squashT = -1f
        }
        if (shakeT > 0f) shakeT = max(0f, shakeT - dt)
        for (pt in particles) {
            pt.life -= dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.vy += 900f * s * dt
        }
        particles.removeAll { it.life <= 0f }
        texts.forEach { it.life -= dt }
        texts.removeAll { it.life <= 0f }
    }

    /** 把足以续玩的一局编码落盘；仅在对局进行中写入，READY/OVER 不写。 */
    fun saveSnapshot(prefs: SharedPreferences) {
        // 已放弃/改窗口重开：兜底落盘也不得把陈旧档写回复活
        if (snapshotDiscarded) {
            GameSnapshot.clear(prefs, STAR_SNAPSHOT_KEY)
            return
        }
        if (phase != GamePhase.RUNNING) return
        val meta =
            SnapshotCodec.encode(
                phase.ordinal,
                time,
                bearX,
                bearY,
                bearVX,
                bearVY,
                cameraY,
                maxHeight,
                starsCollected,
                score,
                squashT,
                shakeT,
                deadT,
                best,
                scene.ordinal,
                w,
                h,
                s,
                nextPlatY,
                difficulty.ordinal,
                combo,
                comboWindowSec,
                comboBonusTotal,
                grazeCount,
            )
        val platformGroups =
            platforms.map {
                listOf<Number>(
                    it.type.ordinal,
                    it.x,
                    it.y,
                    it.w,
                    it.vx,
                    if (it.broken) 1 else 0,
                    it.breakT,
                    if (it.grazed) 1 else 0,
                )
            }
        val starGroups = stars.map { listOf<Number>(it.x, it.y, if (it.taken) 1 else 0, it.seed) }
        val payload =
            listOf(
                meta,
                SnapshotCodec.encodeGroups(platformGroups),
                SnapshotCodec.encodeGroups(starGroups),
            ).joinToString("\n")
        GameSnapshot.save(prefs, STAR_SNAPSHOT_KEY, payload)
    }

    /** 读取并恢复一局；无档/坏档返回 false，保持 READY。恢复成功后保留快照供再次落盘。 */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        val restored = restoreSnapshotOrNull(prefs)
        // 校验失败一律清档：坏档留在 prefs 里会被每次进入重复解析、反复失败，
        // 还会让「继续」入口的可用性判断与实际不符
        if (!restored) clearSnapshot(prefs)
        return restored
    }

    @Suppress("ReturnCount")
    private fun restoreSnapshotOrNull(prefs: SharedPreferences): Boolean {
        val raw = GameSnapshot.load(prefs, STAR_SNAPSHOT_KEY) ?: return false
        val sections = raw.split('\n')
        val meta = SnapshotCodec.decodeFloatArray(sections.getOrNull(0)) ?: return false
        if (meta.size < STAR_SNAPSHOT_META_FIELDS) return false
        // 只接受 RUNNING 档：旧档/标题档恢复后会造成「标题态却续着残缺对局」的错乱
        if (GamePhase.entries.getOrNull(meta[0].toInt()) != GamePhase.RUNNING) return false
        val platformGroups = decodeSnapshotSection(sections.getOrNull(1)) ?: return false
        val starGroups = decodeSnapshotSection(sections.getOrNull(2)) ?: return false
        // 逐个实体容错：字段不足/类型非法的实体只丢自己，不让整局进度被清档。
        val newPlatforms =
            platformGroups.mapNotNull { g ->
                if (g.size < 7) return@mapNotNull null
                val type = PlatType.values().getOrNull(g[0].toInt()) ?: return@mapNotNull null
                Platform(
                    g[1],
                    g[2],
                    g[3],
                    type,
                    vx = g[4],
                    broken = g[5] >= 0.5f,
                    breakT = g[6],
                    grazed = g.size >= 8 && g[7] >= 0.5f,
                )
            }
        val newStars =
            starGroups.mapNotNull { g ->
                if (g.size < 4) return@mapNotNull null
                StarItem(g[0], g[1], taken = g[2] >= 0.5f, seed = g[3])
            }

        phase = GamePhase.RUNNING
        time = meta[1]
        bearX = meta[2]
        bearY = meta[3]
        bearVX = meta[4]
        bearVY = meta[5]
        cameraY = meta[6]
        maxHeight = meta[7]
        starsCollected = meta[8].toInt()
        score = meta[9].toInt()
        squashT = meta[10]
        shakeT = meta[11]
        deadT = meta[12]
        // 与现有最高分合并取大：旧快照不得把当前已提升的最高分改小
        best = max(best, meta[13].toInt())
        scene = JumpScene.values().getOrElse(meta[14].toInt()) { JumpScene.SUNNY }
        restoredDims = Triple(meta[15], meta[16], meta[17])
        nextPlatY = meta[18]
        if (meta.size >= STAR_SNAPSHOT_META_FIELDS_V2) {
            difficulty = StarDifficulty.entries.getOrElse(meta[19].toInt()) { StarDifficulty.STANDARD }
            combo = meta[20].toInt().coerceAtLeast(0)
            comboWindowSec = meta[21].coerceAtLeast(0f)
            comboBonusTotal = meta[22].toInt().coerceAtLeast(0)
            grazeCount = meta[23].toInt().coerceAtLeast(0)
        } else {
            combo = 0
            comboWindowSec = 0f
            comboBonusTotal = 0
            grazeCount = 0
        }
        lastBandTheme = starAltitudeTheme(altitudeKm)
        pendingBandTheme = null
        pendingGraze = false
        touchX = null
        snapshotDiscarded = false
        platforms.clear()
        platforms += newPlatforms
        stars.clear()
        stars += newStars
        return true
    }

    /** 清除落盘快照并抑制后续兜底落盘（结束/重开/退出时调用）。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        snapshotDiscarded = true
        GameSnapshot.clear(prefs, STAR_SNAPSHOT_KEY)
    }
}

/** 解析快照的一个列表段；空白段视为空列表，非空但解析失败返回 null（坏档）。 */
private fun decodeSnapshotSection(text: String?): List<FloatArray>? =
    if (text.isNullOrBlank()) emptyList() else SnapshotCodec.decodeGroups(text)
