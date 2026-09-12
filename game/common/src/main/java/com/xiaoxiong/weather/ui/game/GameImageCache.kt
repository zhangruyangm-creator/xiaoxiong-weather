package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 游戏大图（drawable-nodpi 作物插画 / 房间背景）的共享解码缓存。
 *
 * 原图可达 1024×1024 / 1024×1536，ARGB_8888 解码单张 4–6MB；网格 / 图鉴里同一资源
 * 会被多个列表项重复解码。这里按 (id, targetPx) 用 [BitmapFactory] 采样到目标尺寸，
 * 再用全局 [LruCache] 跨 composable 复用。解码在 IO 线程进行，加载期间显示按原图
 * 宽高比缩出的占位位图，失败时回退 Compose 的 [imageResource]。
 */
object GameImageCache {
    /** 占位位图最长边像素：只用来撑住宽高比，越省越好。 */
    private const val PLACEHOLDER_MAX_PX = 8

    private val cache =
        object : LruCache<String, ImageBitmap>(maxCacheBytes()) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ): Int = value.width * value.height * 4
        }

    private val boundsCache = ConcurrentHashMap<Int, IntArray>()
    private val placeholderCache = ConcurrentHashMap<Int, ImageBitmap>()
    private val decodeLock = Mutex()

    private val transparent by lazy { ImageBitmap(1, 1) }

    /** 同步命中缓存（供合成首帧直接复用，避免先闪占位）。 */
    fun get(
        @DrawableRes id: Int,
        targetPx: Int,
    ): ImageBitmap? = cache.get(key(id, targetPx))

    /** 解码到不超过 [targetPx] 的尺寸并写入缓存；失败返回 null。 */
    suspend fun load(
        context: Context,
        @DrawableRes id: Int,
        targetPx: Int,
    ): ImageBitmap? {
        get(id, targetPx)?.let { return it }
        decodeLock.withLock {
            get(id, targetPx)?.let { return it }
            val bitmap = withContext(Dispatchers.IO) { decode(context.resources, id, targetPx) }
            if (bitmap != null) cache.put(key(id, targetPx), bitmap)
            return bitmap
        }
    }

    /** 加载中占位：按原图宽高比生成极小透明位图，保持布局尺寸不跳变。 */
    fun placeholder(
        context: Context,
        @DrawableRes id: Int,
    ): ImageBitmap =
        placeholderCache.getOrPut(id) {
            val b = bounds(context.resources, id)
            val w = b[0]
            val h = b[1]
            if (w <= 0 || h <= 0) {
                transparent
            } else {
                val m = max(w, h).toFloat()
                ImageBitmap(
                    max(1, (w * PLACEHOLDER_MAX_PX / m).roundToInt()),
                    max(1, (h * PLACEHOLDER_MAX_PX / m).roundToInt()),
                )
            }
        }

    private fun decode(
        res: Resources,
        id: Int,
        targetPx: Int,
    ): ImageBitmap? {
        val b = bounds(res, id)
        if (b[0] <= 0 || b[1] <= 0) return null
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = gameImageSampleSize(b[0], b[1], targetPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            runCatching {
                res.openRawResource(id).use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull() ?: return null
        return bitmap.asImageBitmap()
    }

    /** drawable-nodpi 直接读原始像素；只读文件头，开销极小。 */
    private fun bounds(
        res: Resources,
        id: Int,
    ): IntArray =
        boundsCache.getOrPut(id) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                res.openRawResource(id).use { BitmapFactory.decodeStream(it, null, opts) }
            }
            intArrayOf(opts.outWidth, opts.outHeight)
        }

    private fun key(
        id: Int,
        targetPx: Int,
    ): String = "$id@$targetPx"

    private fun maxCacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory() / 8
        return max.coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    }
}

/** 2 的幂采样：保证解码后最长边不超过 [targetPx]。 */
internal fun gameImageSampleSize(
    width: Int,
    height: Int,
    targetPx: Int,
): Int {
    if (targetPx <= 0) return 1
    var sample = 1
    val maxDim = max(width, height)
    while (maxDim / sample > targetPx) {
        sample *= 2
    }
    return sample
}

private sealed interface GameImageState {
    data object Loading : GameImageState

    data class Ready(
        val bitmap: ImageBitmap,
    ) : GameImageState

    data object Failed : GameImageState
}

/**
 * 列表 / 详情里的游戏大图统一入口：按 [targetPx] 采样、缓存、异步解码。
 *
 * 返回值可能先为占位位图，解码完成后自动切到真实图；命中缓存时同步返回，不闪占位。
 * 注意 [targetPx] 是尺寸上限而非精确目标（[BitmapFactory.inSampleSize] 按 2 的幂采样）。
 */
@Composable
fun rememberGameImage(
    @DrawableRes id: Int,
    targetPx: Int,
): ImageBitmap {
    val context = LocalContext.current
    val remembered = remember(id, targetPx) { GameImageCache.get(id, targetPx) }
    if (remembered != null) return remembered
    val placeholder = remember(context, id) { GameImageCache.placeholder(context, id) }
    val state by
        produceState<GameImageState>(GameImageState.Loading, id, targetPx) {
            val bitmap = GameImageCache.load(context, id, targetPx)
            value = if (bitmap != null) GameImageState.Ready(bitmap) else GameImageState.Failed
        }
    return when (val s = state) {
        is GameImageState.Ready -> s.bitmap
        GameImageState.Failed -> ImageBitmap.imageResource(id)
        GameImageState.Loading -> placeholder
    }
}
