package com.xiaoxiong.weather.ui.weather

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

private const val COLUMNS = 4

/**
 * 小熊插画相册（口令 `#小熊相册`）。
 *
 * 分组数据来自 [bearAlbumSections]（生活片刻池）+ 节日节（[BearHoliday] 各节日全部变体），
 * 二者都从插画池单一数据源派生，新增插画自动入册，无需配置文案。
 * 点缩略图进入全屏大图浏览，可横向跨分组连续滑动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearAlbumScreen(onBack: () -> Unit) {
    val sections = remember { bearAlbumSections() + holidayAlbumSection() }
    val total = remember(sections) { sections.sumOf { it.drawableRes.size } }

    // 当前大图浏览的起始位置（扁平索引）；-1 表示未打开
    var viewerStart by remember { mutableIntStateOf(-1) }

    // 扁平化全部插画（供 pager 跨组连续滑动）
    val flatList =
        remember(sections) {
            val list = ArrayList<Int>(total)
            sections.forEach { list.addAll(it.drawableRes) }
            list
        }

    BackHandler(onBack = onBack)
    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.album_title),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.album_count, total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                // 逐组渲染：组标题 + 每组 4 列缩略图行
                var flatOffset = 0
                sections.forEachIndexed { index, section ->
                    item(key = "header_$index") { AlbumSectionHeader(section) }
                    section.drawableRes.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                        val offset = flatOffset + rowIndex * COLUMNS
                        item(key = "row_${index}_$rowIndex") {
                            AlbumRow(
                                drawables = row,
                                startIndex = offset,
                                onOpen = { viewerStart = it },
                            )
                        }
                    }
                    flatOffset += section.drawableRes.size
                }
            }
        }
    }

    if (viewerStart >= 0) {
        BearAlbumViewer(
            drawables = flatList,
            initialIndex = viewerStart,
            onClose = { viewerStart = -1 },
        )
    }
}

/** 节日节：所有节日全部插画变体。 */
private fun holidayAlbumSection(): BearAlbumSection =
    BearAlbumSection(
        titleRes = R.string.sun_bear_fest_spring,
        drawableRes = BearHoliday.values().flatMap { it.bearVariantDrawables().toList() },
    )

@Composable
private fun AlbumSectionHeader(section: BearAlbumSection) {
    Text(
        text = stringResource(section.titleRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun AlbumRow(
    drawables: List<Int>,
    startIndex: Int,
    onOpen: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        drawables.forEachIndexed { i, drawable ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(startIndex + i) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        repeat(COLUMNS - drawables.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** 全屏大图浏览：黑色背景横向滑动，跨全部分组无缝。 */
@Composable
private fun BearAlbumViewer(
    drawables: List<Int>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { drawables.size }
    BackHandler(onBack = onClose)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Image(
                painter = painterResource(drawables[page]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                )
            }
            Text(
                text = "${pagerState.currentPage + 1}/${drawables.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
            )
        }
    }
}
