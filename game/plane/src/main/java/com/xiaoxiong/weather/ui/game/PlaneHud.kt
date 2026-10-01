package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlin.math.roundToInt

// ---------------------------------------------------------------------
// 顶栏：得分 / 最高分 / 波次 / 暂停
// ---------------------------------------------------------------------

/** 顶栏：左上分数 + 最高分，中间波次（Boss 战显示 BOSS），右上暂停。 */
@Composable
internal fun PlaneTopBar(
    score: Int,
    best: Int,
    wave: Int,
    bossActive: Boolean,
    onPause: () -> Unit,
) {
    val scoreCd = stringResource(R.string.cd_plane_score, score, best)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = scoreCd
                    },
        ) {
            Text(
                text = stringResource(R.string.bear_plane_score_label),
                color = PlaneUi.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "$score",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.bear_plane_best, best),
                color = PlaneUi.textSecondary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(50),
                color = PlaneUi.glass.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, PlaneUi.skyBorder.copy(alpha = 0.7f)),
            ) {
                Text(
                    text =
                        if (bossActive) {
                            stringResource(R.string.bear_plane_boss)
                        } else {
                            stringResource(R.string.bear_plane_wave, wave.coerceAtLeast(1))
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    color = if (bossActive) PlaneUi.danger else PlaneUi.accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            GamePauseButton(onClick = onPause, tint = Color.White)
        }
    }
}

/**
 * Boss 分段血条（[frame] 为取值函数，只在 draw 阶段读取，不触发每帧重组）。
 * 三段对应三阶段；颜色随阶段由冰蓝 → 琥珀 → 赤红。
 */
@Composable
internal fun PlaneBossBar(
    game: PlaneGame,
    visible: Boolean,
    frame: () -> Long,
) {
    if (!visible) return
    val cd = stringResource(R.string.cd_plane_boss_hp)
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .height(16.dp)
                .semantics { contentDescription = cd },
    ) {
        frame()
        // 定长索引循环找 Boss：draw 阶段零分配
        var boss: PlaneEnemy? = null
        var index = 0
        while (index < game.enemies.size) {
            val candidate = game.enemies[index]
            if (candidate.isBoss) {
                boss = candidate
                break
            }
            index++
        }
        val current = boss ?: return@Canvas
        val hp = current.hp
        val maxHp = current.maxHp.coerceAtLeast(1)
        val phase = current.bossPhase.coerceIn(0, 2)
        val gap = size.width * 0.012f
        val segW = (size.width - gap * 2f) / 3f
        val trackColor = Color(0xCC0B1622)
        for (seg in 0 until PLANE_BOSS_HP_SEGMENTS) {
            val left = seg * (segW + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(segW, size.height),
                cornerRadius = CornerRadius(size.height * 0.5f),
            )
            val fill = planeBossSegmentFill(hp, maxHp, seg)
            if (fill > 0.001f) {
                val color =
                    when (phase) {
                        0 -> PlaneUi.accent
                        1 -> PlaneUi.warn
                        else -> PlaneUi.danger
                    }
                val fillWidth = segW * fill
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(fillWidth, size.height),
                    cornerRadius = CornerRadius(size.height * 0.5f),
                )
                // 掉到分段末尾时填充宽不足 2px：跳过内高光，避免负尺寸绘制
                if (fillWidth > 2f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(left + 1f, 1f),
                        size = Size(fillWidth - 2f, size.height * 0.32f),
                        cornerRadius = CornerRadius(size.height * 0.25f),
                    )
                }
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset(left, 0f),
                size = Size(segW, size.height),
                cornerRadius = CornerRadius(size.height * 0.5f),
                style = PlaneStroke.of(1.2f),
            )
        }
    }
}

// ---------------------------------------------------------------------
// 底部：生命 / 炸弹 / 火力 + 炸弹按钮
// ---------------------------------------------------------------------

/** 左下状态组 + 右下炸弹按钮。 */
@Composable
internal fun PlaneBottomBar(
    lives: Int,
    maxLives: Int,
    bombs: Int,
    weaponLevel: Int,
    combo: Int,
    graze: Int,
    overdrive: Boolean,
    bombEnabled: Boolean,
    onBomb: () -> Unit,
) {
    val statsCd = stringResource(R.string.cd_plane_hud_stats, lives, maxLives, bombs, weaponLevel)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = PlaneUi.glass.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, PlaneUi.skyBorder.copy(alpha = 0.55f)),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = statsCd },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                PlaneLivesHud(lives = lives, maxLives = maxLives)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BombGlyph(
                        modifier = Modifier.size(14.dp),
                        tint = if (bombs > 0) PlaneUi.accentWarm else PlaneUi.textSecondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "×$bombs",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.bear_plane_weapon, weaponLevel),
                        color = PlaneUi.accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (overdrive) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.bear_plane_overdrive),
                            color = PlaneUi.accentWarm,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(PlaneConfig.MAX_WEAPON_LEVEL) { index ->
                        Box(
                            modifier =
                                Modifier
                                    .width(14.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (index < weaponLevel) {
                                            if (overdrive) PlaneUi.accentWarm else PlaneUi.accent
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        },
                                    ),
                        )
                    }
                }
                if (combo >= 2 || graze > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (combo >= 2) {
                            Text(
                                text = stringResource(R.string.bear_plane_combo, combo),
                                color = PlaneUi.accentWarm,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (combo >= 2 && graze > 0) Spacer(Modifier.width(10.dp))
                        if (graze > 0) {
                            Text(
                                text = stringResource(R.string.bear_plane_graze, graze),
                                color = PlaneUi.accent.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PlaneBombButton(
            enabled = bombEnabled,
            bombs = bombs,
            onClick = onBomb,
        )
    }
}

/** 炸弹按钮：圆形琥珀胶囊 + 炸弹图标 + 余量角标。 */
@Composable
private fun PlaneBombButton(
    enabled: Boolean,
    bombs: Int,
    onClick: () -> Unit,
) {
    val cd = stringResource(R.string.bear_plane_bomb_use, bombs)
    // 渐变只随可用状态变化，缓存避免每次重组重建 Brush + list
    val buttonBrush =
        remember(enabled) {
            Brush.verticalGradient(
                listOf(
                    if (enabled) Color(0xFF3A2A12) else Color(0xFF20262E),
                    if (enabled) Color(0xFF1C1408) else Color(0xFF14181E),
                ),
            )
        }
    Box(
        modifier =
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(buttonBrush)
                .semantics { contentDescription = cd }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(66.dp)) {
            drawCircle(
                color = if (enabled) PlaneUi.accentWarm.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                radius = size.minDimension * 0.46f,
                center = center,
                style = Stroke(width = size.minDimension * 0.045f),
            )
            if (enabled) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.40f,
                    center = Offset(center.x, center.y - size.minDimension * 0.04f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BombGlyph(
                modifier = Modifier.size(26.dp),
                tint = if (enabled) PlaneUi.accentWarm else Color.White.copy(alpha = 0.35f),
            )
            Text(
                text = "×$bombs",
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 底部生命显示：少命用小熊头条，多命改「图标 + 当前/上限」避免撑爆 HUD。 */
@Composable
private fun PlaneLivesHud(
    lives: Int,
    maxLives: Int,
) {
    val cappedMax = maxLives.coerceAtLeast(1)
    if (cappedMax <= PlaneConfig.HUD_LIFE_ICON_MAX) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(cappedMax) { index ->
                BearLifeIcon(
                    filled = index < lives,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BearLifeIcon(filled = lives > 0)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.bear_plane_lives_hud, lives.coerceAtLeast(0), cappedMax),
                color = if (lives > 0) PlaneUi.accentWarm else PlaneUi.danger,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 小熊头生命图标：存活实心（奶油 + 棕描边），失去为空心轮廓。 */
@Composable
internal fun BearLifeIcon(
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val r = size.minDimension * 0.32f
        val cx = size.width * 0.5f
        val cy = size.height * 0.56f
        val outline =
            if (filled) planePlayerBrownDark else Color.White.copy(alpha = 0.30f)
        val fur = if (filled) planeBearFur else Color.Transparent
        drawCircle(outline, r * 0.46f, Offset(cx - r * 0.72f, cy - r * 0.72f))
        drawCircle(outline, r * 0.46f, Offset(cx + r * 0.72f, cy - r * 0.72f))
        if (filled) {
            drawCircle(planeBearEar, r * 0.36f, Offset(cx - r * 0.72f, cy - r * 0.72f))
            drawCircle(planeBearEar, r * 0.36f, Offset(cx + r * 0.72f, cy - r * 0.72f))
        }
        drawCircle(fur, r, Offset(cx, cy))
        drawCircle(outline, r, Offset(cx, cy), style = Stroke(width = r * 0.20f))
        if (filled) {
            drawCircle(Color(0xFF3E2A18), r * 0.12f, Offset(cx - r * 0.34f, cy - r * 0.12f))
            drawCircle(Color(0xFF3E2A18), r * 0.12f, Offset(cx + r * 0.34f, cy - r * 0.12f))
            drawCircle(planeBearMuzzle, r * 0.34f, Offset(cx, cy + r * 0.30f))
            drawCircle(Color(0xFF3E2A18), r * 0.12f, Offset(cx, cy + r * 0.22f))
        }
    }
}

/** 炸弹图标：圆身 + 引信 + 火花。 */
@Composable
internal fun BombGlyph(
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.32f
        val cx = size.width * 0.44f
        val cy = size.height * 0.58f
        drawCircle(tint, r, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, Offset(cx - r * 0.3f, cy - r * 0.3f))
        drawLine(
            tint,
            Offset(cx + r * 0.35f, cy - r * 0.85f),
            Offset(cx + r * 0.85f, cy - r * 1.35f),
            strokeWidth = size.minDimension * 0.08f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            Color(0xFFFFE082),
            size.minDimension * 0.09f,
            Offset(cx + r * 0.95f, cy - r * 1.45f),
        )
    }
}

// ---------------------------------------------------------------------
// 横幅 / 主菜单 / 结算
// ---------------------------------------------------------------------

/** 波次横幅文案：特殊主题 / 里程碑优先于普通「第 N 波」。 */
@Composable
internal fun planeWaveBannerText(wave: Int): String {
    val w = wave.coerceAtLeast(1)
    if (PlaneConfig.isMilestoneWave(w)) {
        return stringResource(R.string.bear_plane_wave_milestone, w)
    }
    return when (PlaneConfig.waveTheme(w)) {
        PlaneWaveTheme.SCOUT_SWARM -> stringResource(R.string.bear_plane_wave_scout, w)
        PlaneWaveTheme.BOMBER_RUN -> stringResource(R.string.bear_plane_wave_bomber, w)
        PlaneWaveTheme.SNIPER_NEST -> stringResource(R.string.bear_plane_wave_sniper, w)
        else -> stringResource(R.string.bear_plane_wave, w)
    }
}

/** 天气风味一句：标题卡简介下方的轻提示。 */
@Composable
internal fun planeWeatherFlavorText(weather: FarmWeather): String =
    stringResource(
        when (weather) {
            FarmWeather.SNOWY -> R.string.bear_plane_weather_snow
            FarmWeather.STORMY -> R.string.bear_plane_weather_storm
            FarmWeather.RAINY -> R.string.bear_plane_weather_rain
            FarmWeather.FOGGY -> R.string.bear_plane_weather_fog
            FarmWeather.CLOUDY -> R.string.bear_plane_weather_cloudy
            FarmWeather.SUNNY -> R.string.bear_plane_weather_clear
        },
    )

/** Boss 预警文案：按种类区分。 */
@Composable
internal fun planeBossWarningText(kind: PlaneBossKind): String =
    stringResource(
        when (kind) {
            PlaneBossKind.STANDARD -> R.string.bear_plane_boss_warning_standard
            PlaneBossKind.RINGMASTER -> R.string.bear_plane_boss_warning_ringmaster
            PlaneBossKind.STRIKER -> R.string.bear_plane_boss_warning_striker
        },
    )

@Composable
internal fun planeShipTintLabel(tint: PlaneShipTint): String =
    stringResource(
        when (tint) {
            PlaneShipTint.CLASSIC -> R.string.bear_plane_tint_classic
            PlaneShipTint.GOLD -> R.string.bear_plane_tint_gold
            PlaneShipTint.MIDNIGHT -> R.string.bear_plane_tint_midnight
            PlaneShipTint.ROSE -> R.string.bear_plane_tint_rose
            PlaneShipTint.AURORA -> R.string.bear_plane_tint_aurora
        },
    )

/** 波次横幅：主题玻璃卡片。 */
@Composable
internal fun PlaneWaveBanner(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PlaneUi.glassDeep.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, PlaneUi.skyBorder),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    color = PlaneUi.accent,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Boss 预警横幅：红色警示主题卡片。 */
@Composable
internal fun PlaneBossWarningBanner(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xE62A0A10),
            border = BorderStroke(1.dp, PlaneUi.danger),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    color = PlaneUi.danger,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** READY：标题 / 简介 / 难度 3 档（含说明）/ 开局命数滑条（1..30）/ 开始 / 最高分 / 排行榜 / 音效开关。 */
@Composable
internal fun PlaneReadyOverlay(
    game: PlaneGame,
    best: Int,
    bestWave: Int,
    startLives: Int,
    shipTint: PlaneShipTint,
    soundEnabled: Boolean,
    frame: () -> Long,
    isDay: Boolean,
    reduceMotion: Boolean,
    onPick: (PlaneDifficulty) -> Unit,
    onStartLivesChange: (Int) -> Unit,
    onShipTint: (PlaneShipTint) -> Unit,
    onStart: () -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onLeaderboard: () -> Unit,
) {
    val scrim = remember { Brush.verticalGradient(listOf(Color(0xB30B1622), Color(0xE60B1622))) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        PlaneGlassCard(modifier = Modifier.padding(horizontal = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlanePreview(
                    frame = frame,
                    isDay = isDay,
                    reduceMotion = reduceMotion,
                    shipTint = shipTint,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.bear_plane_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.bear_plane_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlaneUi.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = planeWeatherFlavorText(game.weather),
                        style = MaterialTheme.typography.labelSmall,
                        color = PlaneUi.accent.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.bear_plane_diff_section),
                color = PlaneUi.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaneDifficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = game.difficulty == difficulty,
                        onClick = { onPick(difficulty) },
                        label = { Text(planeDifficultyLabel(difficulty)) },
                        modifier = Modifier.weight(1f),
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PlaneUi.accent.copy(alpha = 0.28f),
                                selectedLabelColor = Color.White,
                                labelColor = PlaneUi.textSecondary,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = planeDifficultyHint(game.difficulty),
                color = PlaneUi.textSecondary.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.bear_plane_ship_tint),
                color = PlaneUi.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaneShipTint.entries.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { tint ->
                            val unlocked = planeShipTintUnlocked(tint, bestWave)
                            FilterChip(
                                selected = shipTint == tint,
                                onClick = {
                                    if (unlocked) onShipTint(tint)
                                },
                                enabled = unlocked,
                                label = {
                                    Text(
                                        text = planeShipTintLabel(tint),
                                        maxLines = 1,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = planePlayerPalette(tint).brown.copy(alpha = 0.45f),
                                        selectedLabelColor = Color.White,
                                        labelColor = PlaneUi.textSecondary,
                                        disabledContainerColor = Color.White.copy(alpha = 0.06f),
                                        disabledLabelColor = PlaneUi.textSecondary.copy(alpha = 0.45f),
                                    ),
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bear_plane_start_lives),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.bear_plane_lives_value, startLives),
                    color = PlaneUi.accentWarm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = startLives.toFloat(),
                onValueChange = { onStartLivesChange(it.roundToInt()) },
                valueRange =
                    PlaneConfig.START_LIVES_MIN.toFloat()..PlaneConfig.START_LIVES_MAX.toFloat(),
                steps = (PlaneConfig.START_LIVES_MAX - PlaneConfig.START_LIVES_MIN - 1).coerceAtLeast(0),
                colors =
                    SliderDefaults.colors(
                        thumbColor = PlaneUi.accentWarm,
                        activeTrackColor = PlaneUi.accentWarm.copy(alpha = 0.85f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { onStartLivesChange(startLives - 1) },
                    enabled = startLives > PlaneConfig.START_LIVES_MIN,
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.cd_plane_lives_minus),
                        tint = Color.White,
                    )
                }
                Text(
                    text =
                        stringResource(
                            R.string.bear_plane_lives_range_hint,
                            PlaneConfig.START_LIVES_MIN,
                            PlaneConfig.START_LIVES_MAX,
                        ),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(
                    onClick = { onStartLivesChange(startLives + 1) },
                    enabled = startLives < PlaneConfig.START_LIVES_MAX,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_plane_lives_plus),
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.bear_plane_best, best),
                color = PlaneUi.accentWarm,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            if (bestWave > 0) {
                Text(
                    text = stringResource(R.string.bear_plane_best_wave, bestWave),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PlaneUi.accent),
            ) {
                Text(
                    text = stringResource(R.string.bear_plane_start),
                    color = Color(0xFF06202A),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLeaderboard) {
                    Text(
                        text = stringResource(R.string.bear_plane_leaderboard),
                        color = PlaneUi.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.bear_plane_sound),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onToggleSound,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF06202A),
                            checkedTrackColor = PlaneUi.accent,
                        ),
                )
            }
        }
    }
}

/** 结算：分数 / 新纪录 / 击毁 / 波次 / 最远波次 / 排名 / 再来 / 退出 / 排行榜。 */
@Composable
internal fun PlaneSettledOverlay(
    game: PlaneGame,
    best: Int,
    bestWave: Int,
    isNewWaveRecord: Boolean,
    rank: Int,
    isNewRecord: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    val win = game.player.alive
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f)),
        contentAlignment = Alignment.Center,
    ) {
        PlaneGlassCard(modifier = Modifier.padding(horizontal = 30.dp)) {
            Text(
                text = stringResource(if (win) R.string.bear_plane_win else R.string.bear_plane_lose),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (win) PlaneUi.accentWarm else PlaneUi.danger,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${game.score}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bear_plane_score_label),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (isNewRecord) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PlaneUi.accentWarm.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, PlaneUi.accentWarm),
                ) {
                    Text(
                        text = stringResource(R.string.bear_plane_new_record),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        color = PlaneUi.accentWarm,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bear_plane_best, best),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    text = stringResource(R.string.bear_plane_kills, game.kills),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.bear_plane_graze, game.grazeCount),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.bear_plane_wave_reached, game.waveIndex.coerceAtLeast(1)),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (isNewWaveRecord) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PlaneUi.accent.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, PlaneUi.accent),
                ) {
                    Text(
                        text = stringResource(R.string.bear_plane_new_wave_record),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        color = PlaneUi.accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (bestWave > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bear_plane_best_wave, bestWave),
                    color = PlaneUi.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (rank > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bear_plane_rank, rank),
                    color = PlaneUi.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PlaneUi.accent),
            ) {
                Text(
                    text = stringResource(R.string.bear_plane_again),
                    color = Color(0xFF06202A),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLeaderboard) {
                    Text(
                        text = stringResource(R.string.bear_plane_leaderboard),
                        color = PlaneUi.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onExit) {
                    Text(
                        text = stringResource(R.string.bear_plane_exit),
                        color = PlaneUi.danger,
                    )
                }
            }
        }
    }
}

/** 主菜单 / 结算通用玻璃卡片：顶部流光条 + 深色玻璃 + 冰蓝描边。 */
@Composable
private fun PlaneGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PlaneUi.glassDeep,
        border = BorderStroke(1.dp, PlaneUi.skyBorder),
        shadowElevation = 14.dp,
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, PlaneUi.accent, PlaneUi.accentWarm, Color.Transparent),
                            ),
                        ),
            )
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                content = content,
            )
        }
    }
}

/** 主菜单上的玩家机预览（螺旋桨随 [frame] 转动；reduceMotion 时冻结静态帧）。 */
@Composable
private fun PlanePreview(
    frame: () -> Long,
    isDay: Boolean,
    reduceMotion: Boolean,
    shipTint: PlaneShipTint,
) {
    Canvas(modifier = Modifier.size(86.dp)) {
        // 动态模式才在 draw 阶段读取帧状态注册重绘；静态预览不注册帧依赖
        val tick = if (reduceMotion) 0L else frame()
        val r = size.minDimension * 0.30f
        val x = size.width * 0.5f
        val y = size.height * 0.52f
        val palette = planePlayerPalette(shipTint)
        val sprite = PlaneSprites.player(r, isDay, this, layoutDirection, tint = shipTint)
        if (sprite != null) {
            drawPlaneSprite(sprite, x, y)
        } else {
            drawCircle(palette.cream, r * 0.7f, Offset(x, y))
        }
        val propY = y - r * 0.78f
        val angle = (tick * 0.8f) % 360f
        withTransform(
            {
                rotate(angle, pivot = Offset(x, propY))
            },
        ) {
            drawLine(
                palette.brownDark,
                Offset(x - r * 0.78f, propY),
                Offset(x + r * 0.78f, propY),
                strokeWidth = r * 0.10f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 难度标签（zh/en 资源）。 */
@Composable
internal fun planeDifficultyLabel(difficulty: PlaneDifficulty): String =
    stringResource(
        when (difficulty) {
            PlaneDifficulty.CASUAL -> R.string.bear_plane_diff_casual
            PlaneDifficulty.STANDARD -> R.string.bear_plane_diff_standard
            PlaneDifficulty.CHALLENGE -> R.string.bear_plane_diff_challenge
        },
    )

/** 难度说明：标题页 Chip 下方短提示。 */
@Composable
internal fun planeDifficultyHint(difficulty: PlaneDifficulty): String =
    stringResource(
        when (difficulty) {
            PlaneDifficulty.CASUAL -> R.string.bear_plane_diff_hint_casual
            PlaneDifficulty.STANDARD -> R.string.bear_plane_diff_hint_standard
            PlaneDifficulty.CHALLENGE -> R.string.bear_plane_diff_hint_challenge
        },
    )

/** Boss 血条分段数（与三阶段对应）。 */
internal const val PLANE_BOSS_HP_SEGMENTS = 3

/** Boss 血条分段填充比例（0..1，从右向左掉血）；纯函数便于单测。 */
internal fun planeBossSegmentFill(
    hp: Int,
    maxHp: Int,
    segment: Int,
    segments: Int = PLANE_BOSS_HP_SEGMENTS,
): Float {
    if (maxHp <= 0 || segments <= 0 || segment < 0 || segment >= segments) return 0f
    val total = hp.coerceAtLeast(0).toFloat() / maxHp * segments
    return (total - segment).coerceIn(0f, 1f)
}
