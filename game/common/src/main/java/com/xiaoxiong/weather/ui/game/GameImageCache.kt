package com.xiaoxiong.weather.ui.game

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.xiaoxiong.weather.ui.SampledDrawableCache
import com.xiaoxiong.weather.ui.rememberSampledDrawable
import com.xiaoxiong.weather.ui.sampledDrawableSampleSize

/**
 * 游戏大图入口：委托 [SampledDrawableCache] / [rememberSampledDrawable]，
 * 保持历史 API，避免各游戏模块大面积改调用点。
 */
object GameImageCache {
    fun get(
        @DrawableRes id: Int,
        targetPx: Int,
    ): ImageBitmap? = SampledDrawableCache.get(id, targetPx)

    suspend fun load(
        context: android.content.Context,
        @DrawableRes id: Int,
        targetPx: Int,
    ): ImageBitmap? = SampledDrawableCache.load(context, id, targetPx)

    fun placeholder(
        context: android.content.Context,
        @DrawableRes id: Int,
    ): ImageBitmap = SampledDrawableCache.placeholder(context, id)
}

/** @see sampledDrawableSampleSize */
internal fun gameImageSampleSize(
    width: Int,
    height: Int,
    targetPx: Int,
): Int = sampledDrawableSampleSize(width, height, targetPx)

/**
 * 列表 / 详情里的游戏大图统一入口：按 [targetPx] 采样、缓存、异步解码。
 *
 * 返回值可能先为占位位图，解码完成后自动切到真实图；命中缓存时同步返回，不闪占位。
 */
@Composable
fun rememberGameImage(
    @DrawableRes id: Int,
    targetPx: Int,
): ImageBitmap = rememberSampledDrawable(id, targetPx).bitmap
