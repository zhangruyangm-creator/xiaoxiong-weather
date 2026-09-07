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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    // 拖拽步进按实际行高推导（含行间距）：固定 72.dp 在大字号/大字体缩放下会漂移
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val rowSpacingPx = with(density) { 12.dp.toPx() }
    val rowStridePx =
        if (rowHeightPx > 0f) rowHeightPx + rowSpacingPx else with(density) { 72.dp.toPx() }

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
                    val dragging = draggingIndex == index
                    CardPrefRow(
                        modifier =
                            Modifier.onSizeChanged {
                                if (rowHeightPx <= 0f) rowHeightPx = it.height.toFloat()
                            },
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
                            val shift = (dragOffsetY / rowStridePx).roundToInt()
                            val to = (from + shift).coerceIn(0, cards.lastIndex)
                            if (to != from) {
                                val moved = config.layout.move(cards, from, to)
                                cards = moved
                                draggingIndex = to
                                dragOffsetY -= (to - from) * rowStridePx
                            }
                        },
                        onDragEnd = {
                            draggingIndex = -1
                            dragOffsetY = 0f
                            persist(cards)
                        },
                    )
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
