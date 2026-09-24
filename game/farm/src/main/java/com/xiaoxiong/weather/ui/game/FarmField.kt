package com.xiaoxiong.weather.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
internal fun FarmToolBar(
    selected: FarmTool,
    onSelect: (FarmTool) -> Unit,
    onCareAll: () -> Unit,
) {
    var showExtra by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRIMARY_TOOLS.forEach { tool ->
                FarmToolChip(
                    tool = tool,
                    selected = selected == tool,
                    onClick = {
                        showExtra = false
                        onSelect(tool)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            FarmToolChip(
                tool = null,
                label = stringResource(R.string.bear_farm_tool_more),
                icon = "⋯",
                selected = showExtra || selected in EXTRA_TOOLS,
                onClick = { showExtra = !showExtra },
                modifier = Modifier.weight(1f),
            )
        }
        if (showExtra) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FarmToolChip(
                    tool = null,
                    label = stringResource(R.string.bear_farm_care_all),
                    icon = "🌾",
                    selected = false,
                    onClick = {
                        showExtra = false
                        onCareAll()
                    },
                )
                EXTRA_TOOLS.forEach { tool ->
                    FarmToolChip(
                        tool = tool,
                        selected = selected == tool,
                        onClick = {
                            onSelect(tool)
                            showExtra = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FarmToolChip(
    tool: FarmTool?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: String? = null,
) {
    val resolvedLabel =
        label
            ?: tool
                ?.let {
                    stringResource(
                        when (it) {
                            FarmTool.CARE -> R.string.bear_farm_tool_care
                            FarmTool.INFO -> R.string.bear_farm_tool_info
                            FarmTool.PLANT -> R.string.bear_farm_tool_plant
                            FarmTool.WATER -> R.string.bear_farm_tool_water
                            FarmTool.WEED -> R.string.bear_farm_tool_weed
                            FarmTool.BUG -> R.string.bear_farm_tool_bug
                            FarmTool.HARVEST -> R.string.bear_farm_tool_harvest
                            FarmTool.CLEAR -> R.string.bear_farm_tool_clear
                            FarmTool.FERTILIZE -> R.string.bear_farm_tool_fertilize
                        },
                    )
                }.orEmpty()
    val resolvedIcon =
        icon
            ?: when (tool) {
                FarmTool.CARE -> "🧺"
                FarmTool.INFO -> "👀"
                FarmTool.PLANT -> "🌱"
                FarmTool.WATER -> "💧"
                FarmTool.WEED -> "✂️"
                FarmTool.BUG -> "🛡️"
                FarmTool.HARVEST -> "✨"
                FarmTool.CLEAR -> "🧹"
                FarmTool.FERTILIZE -> "💛"
                null -> "⋯"
            }
    val (interactionSource, pressModifier) = rememberFarmPress()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.then(pressModifier),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) farmScheme().secondary else farmScheme().surface.copy(alpha = 0.92f),
        shadowElevation = if (selected) 3.dp else 1.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(resolvedIcon, fontSize = 18.sp)
            Text(
                resolvedLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) farmScheme().onSecondary else farmScheme().onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FarmPlotCell(
    plot: FarmPlotState,
    time: () -> Float,
    onClick: () -> Unit,
) {
    val crop = cropById(plot.cropId)
    val dry = plot.stage in 1..5 && plot.moisture < 0.25f
    val (interactionSource, pressModifier) = rememberFarmPress()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 3.dp,
        modifier = Modifier.aspectRatio(1f).then(pressModifier),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FarmPlotVisual(
                plot = plot,
                crop = crop,
                dry = dry,
                time = time,
                modifier = Modifier.fillMaxSize(),
            )
            if (plot.weed || plot.bug) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (plot.weed) {
                        Image(
                            bitmap = rememberGameImage(FarmCropArt.weed(), 256),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (plot.bug) {
                        Image(
                            bitmap = rememberGameImage(FarmCropArt.bug(), 256),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            if (plot.stage in 1..4) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(plot.moisture.coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(
                                    when {
                                        plot.moisture < 0.25f -> Color(0xFFE53935)
                                        plot.moisture < 0.5f -> Color(0xFFFFB300)
                                        else -> Color(0xFF42A5F5)
                                    },
                                    RoundedCornerShape(3.dp),
                                ),
                    )
                }
            }
            // 需求气泡：与智能照料同一优先级（虫 > 草 > 成熟 > 渴 > 枯萎），一格只冒一个
            val needEmoji =
                when {
                    plot.bug -> "🐛"
                    plot.weed -> "🌿"
                    plot.stage == 4 -> "✨"
                    plot.stage in 1..4 && plot.moisture < 0.35f -> "💧"
                    plot.stage == 5 -> "🧹"
                    else -> null
                }
            if (needEmoji != null) {
                FarmNeedBubble(
                    emoji = needEmoji,
                    time = time,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** 作物需求气泡：白色圆角气泡，用共享时钟 [time] 派生轻微上下浮动。 */
@Composable
private fun FarmNeedBubble(
    emoji: String,
    time: () -> Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.graphicsLayer {
                val bob = wave(time(), BUBBLE_BOB_FREQ)
                translationY = -2.dp.toPx() * bob
            },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, farmScheme().outlineVariant),
    ) {
        Text(
            emoji,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                    vertical = 3.dp,
                ),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 田地格子：卡通土壤底图 + 阶段/成熟作物图 + 呼吸动画。 */
@Composable
private fun FarmPlotVisual(
    plot: FarmPlotState,
    crop: CropDef?,
    dry: Boolean,
    time: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            bitmap = rememberGameImage(FarmCropArt.soil(dry = dry), 256),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        when (plot.stage) {
            0 -> {
                Text(
                    "＋",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            4 -> {
                val c = crop ?: return
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pulse = wave(time(), RIPE_PULSE_FREQ)
                    drawCircle(
                        Color(0xFFFFF59D).copy(alpha = 0.12f + 0.14f * pulse),
                        radius = size.minDimension * 0.42f,
                        center = center,
                    )
                }
                Image(
                    bitmap = rememberGameImage(FarmCropArt.ripe(c), 256),
                    contentDescription = c.nameZh,
                    modifier =
                        Modifier
                            .fillMaxSize(0.78f)
                            .graphicsLayer {
                                val s = 1f + 0.08f * wave(time(), RIPE_PULSE_FREQ)
                                scaleX = s
                                scaleY = s
                            },
                    contentScale = ContentScale.Fit,
                )
            }
            else -> {
                val stageRes = FarmCropArt.stage(plot.stage, crop)
                if (stageRes != null) {
                    val scale =
                        when (plot.stage) {
                            1 -> 0.52f
                            2 -> 0.62f
                            3 -> 0.70f + 0.08f * plot.stageProgress
                            else -> 0.72f
                        }
                    if (plot.stage in 1..3) {
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(5.dp),
                        ) {
                            drawArc(
                                Color.White.copy(alpha = 0.55f),
                                startAngle = -90f,
                                sweepAngle = 360f * plot.stageProgress,
                                useCenter = false,
                                style = Stroke(width = 2.5f),
                            )
                        }
                    }
                    Image(
                        bitmap = rememberGameImage(stageRes, 256),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(scale),
                        contentScale = ContentScale.Fit,
                    )
                }
                // 成长阶段角落小预览：成熟后的作物长相
                if (plot.stage in 2..3 && crop != null) {
                    Image(
                        bitmap = rememberGameImage(FarmCropArt.ripe(crop), 256),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .alpha(0.85f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/** 收获金币飘字：从对应格子上方飘起、淡出、轻微放大。 */
@Composable
internal fun FloatingRewardItem(
    reward: FarmReward,
    areaWidthPx: Float,
    areaHeightPx: Float,
    onDone: () -> Unit,
) {
    val progress = remember(reward.id) { Animatable(0f) }
    // 减弱动画：不做 1.3s 的上浮+放大，但保留静态文字 ~900ms 再移除，
    // 直接消失等于没有收获反馈
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(reward.id) {
        if (reduceMotion) {
            delay(900)
            onDone()
            return@LaunchedEffect
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
        )
        onDone()
    }
    val anchorX = (reward.col + 0.5f) / FARM_COLS
    val anchorY = (reward.row + 0.38f) / FARM_ROWS
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    translationX = areaWidthPx * anchorX - size.width / 2f
                    translationY = areaHeightPx * anchorY - size.height / 2f - p * 72.dp.toPx()
                    alpha = (1f - p * p).coerceIn(0f, 1f)
                    scaleX = 1f + 0.16f * p
                    scaleY = 1f + 0.16f * p
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            reward.label,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = farmScheme().surfaceVariant,
                    shadow =
                        Shadow(
                            color = farmScheme().onSurface,
                            offset = Offset(0f, 2f),
                            blurRadius = 6f,
                        ),
                ),
        )
    }
}

/** 需求气泡上下浮动频率（约 1.8s 一个周期）。 */
private const val BUBBLE_BOB_FREQ = 3.5f

/** 成熟 pulse 频率（约 1.4s 一个周期）。 */
private const val RIPE_PULSE_FREQ = 4.5f

/** 把共享时钟 t 归一化到 0..1 的正弦波，供气泡 / pulse 在 layer/draw 里派生动画。 */
private fun wave(
    t: Float,
    freq: Float,
): Float = 0.5f + 0.5f * sin(t * freq)
