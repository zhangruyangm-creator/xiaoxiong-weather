package com.xiaoxiong.weather.ui.game

/** 单颗球的快照（位置 / 线速度 / 角速度 / 朝向 / 落袋状态）。 */
internal data class PoolBallSnapshot(
    val id: Int,
    val kind: PoolBallKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val wx: Float,
    val wy: Float,
    val wz: Float,
    val z: Float,
    val vz: Float,
    val curve: Float,
    val qw: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val onTable: Boolean,
    val potted: Boolean,
    val pocketIndex: Int,
    val pocketAnim: Float,
)

/** 一杆判定采集中间态：恢复到 SHOOTING 后据此继续飞行并正常结算。 */
internal data class PoolShotTrackingSnapshot(
    val firstContact: PoolBallKind?,
    val firstContactNumber: Int?,
    val contactHappened: Boolean,
    val railAfter: Boolean,
    val potted: List<PoolBallKind>,
    val pottedNumbers: List<Int>,
    val lowestBefore: Int?,
    val cuePotted: Boolean,
    val cueStartY: Float,
    val railedBalls: Set<Int>,
)

/** 击球历史条目快照。 */
internal data class ShotSummarySnapshot(
    val index: Int,
    val turn: PoolSide,
    val pottedNumbers: List<Int>,
    val foul: Boolean,
    val scratch: Boolean,
)

/** 整个进行中对局的快照（球桌几何固定为 `PoolTable.standard()`，无需存储）。 */
internal data class PoolSnapshot(
    val phase: PoolGame.Phase,
    val mode: PoolMode,
    val openTable: Boolean,
    val playerGroup: PoolGroup?,
    val aiGroup: PoolGroup?,
    val turn: PoolSide,
    val playerFrames: Int,
    val aiFrames: Int,
    val matchMode: PoolMode,
    val raceTo: Int,
    val best: Int,
    /** 本场锁定的难度；旧档（无该字段）恢复时保持当前难度。 */
    val matchDifficulty: PoolAi.Difficulty? = null,
    val challengeId: Int?,
    val challengePar: Int,
    val challengeDate: Long,
    val lessonId: Int?,
    val shotCount: Int,
    val isBreak: Boolean,
    val shooterClearedBefore: Boolean,
    val ballInHand: Boolean,
    val placingCue: Boolean,
    val aimAngle: Float,
    val elevation: Float,
    val spinSide: Float,
    val spinVertical: Float,
    val frameSeed: Long,
    val breaker: PoolSide,
    val frameWinner: PoolSide?,
    val lastFrameWinner: PoolSide?,
    val breakRunsThisMatch: Int,
    val potsThisMatch: Int,
    val bestShotPots: Int,
    val lastShotPots: Int,
    val frameBreaker: PoolSide,
    val aiTouchedInFrame: Boolean,
    val breakAndRunFrame: Boolean,
    val balls: List<PoolBallSnapshot>,
    val tracking: PoolShotTrackingSnapshot,
    val history: List<ShotSummarySnapshot>,
)

internal fun PoolBall.toSnapshot(): PoolBallSnapshot =
    PoolBallSnapshot(
        id = id,
        kind = kind,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        wx = wx,
        wy = wy,
        wz = wz,
        z = z,
        vz = vz,
        curve = curve,
        qw = qw,
        qx = qx,
        qy = qy,
        qz = qz,
        onTable = onTable,
        potted = potted,
        pocketIndex = pocketIndex,
        pocketAnim = pocketAnim,
    )

internal fun PoolBallSnapshot.toBall(): PoolBall =
    PoolBall(id = id, kind = kind, x = x, y = y, vx = vx, vy = vy, wx = wx, wy = wy, wz = wz).also {
        it.z = z
        it.vz = vz
        it.curve = curve
        it.qw = qw
        it.qx = qx
        it.qy = qy
        it.qz = qz
        it.onTable = onTable
        it.potted = potted
        it.pocketIndex = pocketIndex
        it.pocketAnim = pocketAnim
    }

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name.isNullOrBlank()) null else runCatching { enumValueOf<T>(name) }.getOrNull()

/**
 * 对局快照的紧凑文本编解码（每行 `key=value`，球/历史各占一行）。
 * 不使用 kotlinx.serialization，读写均容错；任何字段异常都让 [decode] 返回 null。
 *
 * 字段：`v` 版本；`phase`/`mode`/`match`/`flags`/`aim`/`frame`/`stats`/`challenge`/
 * `challengeDate`/`lesson`/`raceTo`/`best`/`shotCount`；`ball` 每球一行；`track` 一杆采集中间态；
 * `hist` 击球历史每行一条。
 *
 * 版本历史：v1 = 1.9.0 前的旧袋口/台面几何（中袋位置与 `pocketIndex` 含义已变），
 * 升 v2 后旧 payload 一律拒绝，避免续玩时球落入错误袋口。
 */
internal object PoolSnapshotCodec {
    private const val VERSION = 2

    fun encode(snapshot: PoolSnapshot): String {
        val lines = ArrayList<String>()
        lines += "v=$VERSION"
        lines += "phase=${snapshot.phase.name}"
        lines += "mode=${snapshot.mode.name}"
        lines +=
            "match=${bool(snapshot.openTable)},${snapshot.playerGroup?.name.orEmpty()}," +
            "${snapshot.aiGroup?.name.orEmpty()},${snapshot.turn.name},${snapshot.playerFrames}," +
            "${snapshot.aiFrames},${snapshot.matchMode.name}"
        lines += "raceTo=${snapshot.raceTo}"
        lines += "best=${snapshot.best}"
        lines += "matchDiff=${snapshot.matchDifficulty?.name.orEmpty()}"
        lines += "challenge=${snapshot.challengeId ?: ""},${snapshot.challengePar}"
        lines += "challengeDate=${snapshot.challengeDate}"
        lines += "lesson=${snapshot.lessonId ?: ""}"
        lines += "shotCount=${snapshot.shotCount}"
        lines +=
            "flags=${bool(snapshot.isBreak)},${bool(snapshot.shooterClearedBefore)}," +
            "${bool(snapshot.ballInHand)},${bool(snapshot.placingCue)}"
        lines += "aim=${snapshot.aimAngle},${snapshot.elevation},${snapshot.spinSide},${snapshot.spinVertical}"
        lines +=
            "frame=${snapshot.frameSeed},${snapshot.breaker.name}," +
            "${snapshot.frameWinner?.name.orEmpty()},${snapshot.lastFrameWinner?.name.orEmpty()}"
        lines +=
            "stats=${snapshot.breakRunsThisMatch},${snapshot.potsThisMatch},${snapshot.bestShotPots}," +
            "${snapshot.lastShotPots},${snapshot.frameBreaker.name}," +
            "${bool(snapshot.aiTouchedInFrame)},${bool(snapshot.breakAndRunFrame)}"
        for (b in snapshot.balls) {
            lines +=
                "ball=${b.id},${b.kind.name},${b.x},${b.y},${b.vx},${b.vy},${b.wx},${b.wy},${b.wz}," +
                "${b.z},${b.vz},${b.curve},${b.qw},${b.qx},${b.qy},${b.qz}," +
                "${bool(b.onTable)},${bool(b.potted)},${b.pocketIndex},${b.pocketAnim}"
        }
        val t = snapshot.tracking
        lines +=
            "track=${t.firstContact?.name.orEmpty()},${t.firstContactNumber ?: ""}," +
            "${bool(t.contactHappened)},${bool(t.railAfter)},${t.lowestBefore ?: ""}," +
            "${bool(t.cuePotted)},${t.cueStartY},${t.potted.joinToString(":") { it.name }}," +
            "${t.pottedNumbers.joinToString(":")},${t.railedBalls.joinToString(":")}"
        for (h in snapshot.history) {
            lines += "hist=${h.index},${h.turn.name},${h.pottedNumbers.joinToString(":")},${bool(h.foul)},${bool(h.scratch)}"
        }
        return lines.joinToString("\n")
    }

    fun decode(raw: String): PoolSnapshot? {
        if (raw.isBlank()) return null
        val values = HashMap<String, String>()
        val ballLines = ArrayList<String>()
        val histLines = ArrayList<String>()
        var trackLine: String? = null
        for (line in raw.split('\n')) {
            if (line.isBlank()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) return null
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            when (key) {
                "ball" -> ballLines += value
                "hist" -> histLines += value
                "track" -> trackLine = value
                else -> values[key] = value
            }
        }
        if (values["v"]?.toIntOrNull() != VERSION) return null

        val phase = enumOrNull<PoolGame.Phase>(values["phase"]) ?: return null
        val mode = enumOrNull<PoolMode>(values["mode"]) ?: return null
        val raceTo = clampRaceTo(values["raceTo"]?.toIntOrNull() ?: return null)
        val best = values["best"]?.toIntOrNull() ?: return null
        // 可选字段（旧档没有本场难度）：缺失 / 非法均回退 null，由 applySnapshot 保持当前难度
        val matchDifficulty = enumOrNull<PoolAi.Difficulty>(values["matchDiff"])
        val shotCount = values["shotCount"]?.toIntOrNull() ?: return null

        val matchParts = values["match"]?.split(',') ?: return null
        if (matchParts.size != 7) return null
        val turn = enumOrNull<PoolSide>(matchParts[3]) ?: return null
        val matchMode = enumOrNull<PoolMode>(matchParts[6]) ?: return null
        val playerFrames = matchParts[4].toIntOrNull() ?: return null
        val aiFrames = matchParts[5].toIntOrNull() ?: return null

        val flags = values["flags"]?.split(',') ?: return null
        if (flags.size != 4) return null

        val aim = values["aim"]?.split(',') ?: return null
        if (aim.size != 4) return null

        val frame = values["frame"]?.split(',') ?: return null
        if (frame.size != 4) return null
        val frameSeed = frame[0].toLongOrNull() ?: return null
        val breaker = enumOrNull<PoolSide>(frame[1]) ?: return null

        val stats = values["stats"]?.split(',') ?: return null
        if (stats.size != 7) return null
        val frameBreaker = enumOrNull<PoolSide>(stats[4]) ?: return null

        val challengeParts = values["challenge"]?.split(',') ?: return null
        if (challengeParts.size != 2) return null
        val challengePar = challengeParts[1].toIntOrNull() ?: return null

        val balls = ballLines.mapNotNull { parseBall(it) }
        if (balls.size != ballLines.size) return null
        val tracking = parseTracking(trackLine ?: return null) ?: return null
        val history = histLines.mapNotNull { parseHistory(it) }
        if (history.size != histLines.size) return null

        return PoolSnapshot(
            phase = phase,
            mode = mode,
            openTable = matchParts[0].toBool(),
            playerGroup = enumOrNull<PoolGroup>(matchParts[1]),
            aiGroup = enumOrNull<PoolGroup>(matchParts[2]),
            turn = turn,
            playerFrames = playerFrames,
            aiFrames = aiFrames,
            matchMode = matchMode,
            raceTo = raceTo,
            best = best,
            matchDifficulty = matchDifficulty,
            challengeId = challengeParts[0].toIntOrNull(),
            challengePar = challengePar,
            challengeDate = values["challengeDate"]?.toLongOrNull() ?: 0L,
            lessonId = values["lesson"]?.toIntOrNull(),
            shotCount = shotCount,
            isBreak = flags[0].toBool(),
            shooterClearedBefore = flags[1].toBool(),
            ballInHand = flags[2].toBool(),
            placingCue = flags[3].toBool(),
            aimAngle = aim[0].toFloatOrNull() ?: return null,
            elevation = aim[1].toFloatOrNull() ?: return null,
            spinSide = aim[2].toFloatOrNull() ?: return null,
            spinVertical = aim[3].toFloatOrNull() ?: return null,
            frameSeed = frameSeed,
            breaker = breaker,
            frameWinner = enumOrNull<PoolSide>(frame[2]),
            lastFrameWinner = enumOrNull<PoolSide>(frame[3]),
            breakRunsThisMatch = stats[0].toIntOrNull() ?: return null,
            potsThisMatch = stats[1].toIntOrNull() ?: return null,
            bestShotPots = stats[2].toIntOrNull() ?: return null,
            lastShotPots = stats[3].toIntOrNull() ?: return null,
            frameBreaker = frameBreaker,
            aiTouchedInFrame = stats[5].toBool(),
            breakAndRunFrame = stats[6].toBool(),
            balls = balls,
            tracking = tracking,
            history = history,
        )
    }

    private fun parseBall(line: String): PoolBallSnapshot? {
        val f = line.split(',')
        if (f.size != 20) return null
        return PoolBallSnapshot(
            id = f[0].toIntOrNull() ?: return null,
            kind = enumOrNull<PoolBallKind>(f[1]) ?: return null,
            x = f[2].toFloatOrNull() ?: return null,
            y = f[3].toFloatOrNull() ?: return null,
            vx = f[4].toFloatOrNull() ?: return null,
            vy = f[5].toFloatOrNull() ?: return null,
            wx = f[6].toFloatOrNull() ?: return null,
            wy = f[7].toFloatOrNull() ?: return null,
            wz = f[8].toFloatOrNull() ?: return null,
            z = f[9].toFloatOrNull() ?: return null,
            vz = f[10].toFloatOrNull() ?: return null,
            curve = f[11].toFloatOrNull() ?: return null,
            qw = f[12].toFloatOrNull() ?: return null,
            qx = f[13].toFloatOrNull() ?: return null,
            qy = f[14].toFloatOrNull() ?: return null,
            qz = f[15].toFloatOrNull() ?: return null,
            onTable = f[16].toBool(),
            potted = f[17].toBool(),
            pocketIndex = f[18].toIntOrNull() ?: return null,
            pocketAnim = f[19].toFloatOrNull() ?: return null,
        )
    }

    private fun parseTracking(line: String): PoolShotTrackingSnapshot? {
        val f = line.split(',')
        if (f.size != 10) return null
        return PoolShotTrackingSnapshot(
            firstContact = enumOrNull<PoolBallKind>(f[0]),
            firstContactNumber = f[1].toIntOrNull(),
            contactHappened = f[2].toBool(),
            railAfter = f[3].toBool(),
            potted = kindList(f[7]),
            pottedNumbers = intList(f[8]),
            lowestBefore = f[4].toIntOrNull(),
            cuePotted = f[5].toBool(),
            cueStartY = f[6].toFloatOrNull() ?: return null,
            railedBalls = intList(f[9]).toSet(),
        )
    }

    private fun parseHistory(line: String): ShotSummarySnapshot? {
        val f = line.split(',')
        if (f.size != 5) return null
        return ShotSummarySnapshot(
            index = f[0].toIntOrNull() ?: return null,
            turn = enumOrNull<PoolSide>(f[1]) ?: return null,
            pottedNumbers = intList(f[2]),
            foul = f[3].toBool(),
            scratch = f[4].toBool(),
        )
    }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private fun String.toBool(): Boolean = this == "1"

    private fun intList(value: String): List<Int> = if (value.isBlank()) emptyList() else value.split(':').mapNotNull { it.toIntOrNull() }

    private fun kindList(value: String): List<PoolBallKind> =
        if (value.isBlank()) emptyList() else value.split(':').mapNotNull { enumOrNull<PoolBallKind>(it) }
}
