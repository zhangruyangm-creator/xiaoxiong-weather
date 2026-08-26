package com.xiaoxiong.weather.ui.weather

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.launch

private const val COLUMNS = 4

/** 双击放大目标倍数 / 捏合上限。 */
private const val MAX_ZOOM = 2.5f

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

    AnimatedContent(
        targetState = viewerStart,
        transitionSpec = {
            if (targetState >= 0 && initialState < 0) {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(tween(160)))
            } else if (targetState < 0 && initialState >= 0) {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.98f)).togetherWith(fadeOut(tween(200)))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
    ) { start ->
        if (start >= 0) {
            BearAlbumViewer(
                drawables = flatList,
                initialIndex = start,
                onClose = { viewerStart = -1 },
            )
        }
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

/** 全屏大图浏览：黑色背景，可双击/捏合缩放，横向滑动跨组连续浏览。 */
@Composable
private fun BearAlbumViewer(
    drawables: List<Int>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { drawables.size }
    BackHandler(onBack = onClose)

    // 任一片段放大中即停用翻页，避免单指平移/捏合与横滑打架
    var anyZoomed by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !anyZoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                painter = painterResource(drawables[page]),
                contentDescription = null,
                onZoomChange = { anyZoomed = it },
                onSingleTap = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 底部返回栏：放大的大图页里也有明确的退出控件，单手拇指也能点到
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Text(
                text = stringResource(R.string.album_pointer, pagerState.currentPage + 1, drawables.size),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * 可缩放图片：双击放大/还原 + 捏合缩放 + 放大后单指平移。
 *
 * - 双击：在 1x 与 [MAX_ZOOM] 之间切换（弹簧动画），放大以点击处为中心
 * - 捏合：围绕双指中心缩放，越界自动夹紧，不露出黑边
 * - 平移：仅缩放态可拖，单指拖动；边界由缩放比例决定
 * - 缩放状态变化通过 [onZoomChange] 上报，调用方据此关闭翻页手势
 */
@Composable
private fun ZoomableImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onZoomChange: (Boolean) -> Unit = {},
    onSingleTap: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    val zoomSpec = SpringSpec<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val offsetSpring = SpringSpec<Offset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    // 平移边界：由容器尺寸与当前缩放决定
    fun maxPan(): Offset {
        val w = container.width.toFloat()
        val h = container.height.toFloat()
        return Offset(
            x = (scale.value - 1f) * w / 2f,
            y = (scale.value - 1f) * h / 2f,
        )
    }

    /**
     * 围绕手势中心缩放：让该点在缩放前后保持同一屏幕位置。
     * [centroid] 是容器内坐标，[oldScale] → [newScale]，offset 按比例补偿。
     */
    suspend fun zoomAround(
        centroid: Offset,
        oldScale: Float,
        newScale: Float,
    ) {
        if (newScale == oldScale) return
        val k = newScale / oldScale
        val newOffset =
            Offset(
                x = centroid.x - (centroid.x - offset.value.x) * k,
                y = centroid.y - (centroid.y - offset.value.y) * k,
            )
        scale.snapTo(newScale)
        offset.snapTo(newOffset.clamped(maxPan()))
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                }.onSizeChanged { container = it }
                .pointerInput(painter) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale.value > 1.01f) {
                                scope.launch {
                                    scale.animateTo(1f, zoomSpec)
                                    offset.animateTo(Offset.Zero, offsetSpring)
                                    onZoomChange(false)
                                }
                            } else {
                                scope.launch {
                                    zoomAround(tap, scale.value, MAX_ZOOM)
                                    onZoomChange(true)
                                }
                            }
                        },
                        onTap = { onSingleTap() },
                    )
                }.pointerInput(painter) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scope.launch {
                            val newScale = (scale.value * zoom).coerceIn(1f, MAX_ZOOM)
                            if (newScale != scale.value) {
                                zoomAround(centroid, scale.value, newScale)
                            }
                            if (scale.value > 1.01f) {
                                offset.snapTo((offset.value + pan).clamped(maxPan()))
                                onZoomChange(true)
                            }
                        }
                    }
                },
    )
}

private fun Offset.clamped(bounds: Offset): Offset =
    Offset(
        x = x.coerceIn(-bounds.x, bounds.x),
        y = y.coerceIn(-bounds.y, bounds.y),
    )
