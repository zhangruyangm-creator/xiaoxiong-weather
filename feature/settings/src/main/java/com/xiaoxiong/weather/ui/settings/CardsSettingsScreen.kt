package com.xiaoxiong.weather.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.CardId
import com.xiaoxiong.weather.data.CardLayoutModel
import com.xiaoxiong.weather.data.CardPref
import com.xiaoxiong.weather.ui.weather.AppScreenBackground
import com.xiaoxiong.weather.ui.weather.SectionHeader
import com.xiaoxiong.weather.ui.weather.SoftCard
import kotlin.math.roundToInt

/**
 * 卡片设置屏的页面配置：文案、图标与布局随页面而异，交互逻辑全部通用。
 */
data class CardsSettingsConfig<T : CardId>(
    val layout: CardLayoutModel<T>,
    @param:StringRes val titleRes: Int,
    @param:StringRes val sectionTitleRes: Int,
    @param:StringRes val hintRes: Int,
    @param:StringRes val lockedHintRes: Int,
    @param:StringRes val resetRes: Int,
    val icon: ImageVector,
)

/**
 * 通用卡片设置屏：拖动排序 + 显隐开关 + 恢复默认。
 * 今天页 / 小时页共用，页面差异通过 [CardsSettingsConfig] 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : CardId> CardsSettingsScreen(
    config: CardsSettingsConfig<T>,
    savedCards: List<CardPref<T>>,
    onCardsChange: (List<CardPref<T>>) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var cards by remember { mutableStateOf(config.layout.defaults) }
    LaunchedEffect(savedCards) {
        cards = config.layout.mergeWithDefaults(savedCards)
    }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // 拖拽步进按实际行高推导（含行间距）：固定 72.dp 在大字号/大字体缩放下会漂移。
    // 行高按卡片 id 分别测量——锁定行多一行副标题、比普通行高（七日页第一行就是锁定行），
    // 取单一高度会让另一种行型的换位阈值偏大/偏小。
    val rowHeightsPx = remember { mutableStateMapOf<CardId, Float>() }
    val rowSpacingPx = with(density) { 12.dp.toPx() }
    val fallbackRowHeightPx = with(density) { 72.dp.toPx() }

    fun rowHeight(id: CardId): Float = rowHeightsPx[id]?.takeIf { it > 0f } ?: fallbackRowHeightPx

    fun persist(next: List<CardPref<T>>) {
        cards = config.layout.mergeWithDefaults(next)
        onCardsChange(cards)
    }

    BackHandler(onBack = onBack)

    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            stringResource(config.titleRes),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(16.dp)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = stringResource(config.sectionTitleRes),
                        subtitle = stringResource(config.hintRes),
                        icon = config.icon,
                    )
                }

                cards.forEachIndexed { index, pref ->
                    // key(pref.id)：让行随卡片身份而非下标移动。否则换位后同一 slot 会
                    // 换到另一张卡，CardPrefRow 内以标题为 key 的 pointerInput 被重置，
                    // 拖动只换一格就中断（连续拖动/残余位移逻辑失效）
                    key(pref.id) {
                        val dragging = draggingIndex == index
                        CardPrefRow(
                            modifier = Modifier.onSizeChanged { rowHeightsPx[pref.id] = it.height.toFloat() },
                            title = stringResource(pref.id.titleRes),
                            locked = pref.id.alwaysVisible,
                            lockedHint = stringResource(config.lockedHintRes),
                            checked = pref.visible,
                            dragging = dragging,
                            dragOffsetY = if (dragging) dragOffsetY else 0f,
                            onVisibleChange = { visible ->
                                persist(config.layout.withVisibility(cards, pref.id, visible))
                            },
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { dy ->
                                dragOffsetY += dy
                                val from = draggingIndex
                                if (from < 0) return@CardPrefRow
                                // 步进取被拖行自身高度 + 行间距（列表整体等高时即精确值）
                                val stride = rowHeight(cards[from].id) + rowSpacingPx
                                val step = cardDragStep(from, dragOffsetY, stride, cards.size)
                                if (step.to != from) {
                                    val moved = config.layout.move(cards, from, step.to)
                                    cards = moved
                                    draggingIndex = step.to
                                    dragOffsetY = step.remainingOffsetY
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                                persist(cards)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        cards = config.layout.defaults
                        onReset()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(config.resetRes))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 拖拽排序的一次换位结果：[to] 为目标下标（未越界），[remainingOffsetY] 为扣除
 * 已消费整步后的残余位移，保证连续拖动时行不会"跳"。
 */
internal data class CardDragStep(
    val to: Int,
    val remainingOffsetY: Float,
)

/**
 * 由被拖行下标、累计位移与行步进算出目标下标（纯函数，供单测）。
 *
 * @param stridePx 被拖行自身高度 + 行间距；<=0 时视为不换位（防除零）
 * @param itemCount 列表长度；<=1 时原地不动
 */
internal fun cardDragStep(
    from: Int,
    offsetY: Float,
    stridePx: Float,
    itemCount: Int,
): CardDragStep {
    if (stridePx <= 0f || itemCount <= 1 || from !in 0 until itemCount) {
        return CardDragStep(from, offsetY)
    }
    val lastIndex = itemCount - 1
    val shift = (offsetY / stridePx).roundToInt()
    val to = (from + shift).coerceIn(0, lastIndex)
    if (to == from) return CardDragStep(from, offsetY)
    // 撞到列表首/尾时残余直接归零：钳位后 (to-from) 恒定，
    // 留着越界残余会无意义地一直累积成脏值
    val consumed = (to - from) * stridePx
    val remaining = if (to == 0 || to == lastIndex) 0f else offsetY - consumed
    return CardDragStep(to, remaining)
}
