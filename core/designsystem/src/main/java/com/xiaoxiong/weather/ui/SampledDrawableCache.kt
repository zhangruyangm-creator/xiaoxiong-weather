package com.xiaoxiong.weather.ui

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
 * drawable-nodpi 大图（天气插画 / 游戏场景）的共享采样解码缓存。
 *
 * 原图常见 384–1024 边长；[painterResource] 会在主线程整图解码。这里按
 * (id, targetPx) 用 [BitmapFactory] 采样，IO 线程解码，[LruCache] 跨 composable 复用。
 */
object SampledDrawableCache {
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
                inSampleSize = sampledDrawableSampleSize(b[0], b[1], targetPx)
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
fun sampledDrawableSampleSize(
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

/** [rememberSampledDrawable] 的结果：位图 + 是否已完成真实解码（非占位）。 */
data class SampledDrawable(
    val bitmap: ImageBitmap,
    val isReady: Boolean,
)

private sealed interface SampledDrawableState {
    data object Loading : SampledDrawableState

    data class Ready(
        val bitmap: ImageBitmap,
    ) : SampledDrawableState

    data object Failed : SampledDrawableState
}

/**
 * 列表 / 卡片 / 相册里的 nodpi 插画统一入口：按 [targetPx] 采样、缓存、异步解码。
 *
 * 命中缓存时同步返回且 [SampledDrawable.isReady]=true；加载中先给占位（isReady=false）；
 * 失败回退 Compose [imageResource]（isReady=true，避免分享/录制永远等不到）。
 */
@Composable
fun rememberSampledDrawable(
    @DrawableRes id: Int,
    targetPx: Int,
): SampledDrawable {
    val context = LocalContext.current
    val remembered = remember(id, targetPx) { SampledDrawableCache.get(id, targetPx) }
    if (remembered != null) return SampledDrawable(remembered, isReady = true)
    val placeholder = remember(context, id) { SampledDrawableCache.placeholder(context, id) }
    val state by
        produceState<SampledDrawableState>(SampledDrawableState.Loading, id, targetPx) {
            val bitmap = SampledDrawableCache.load(context, id, targetPx)
            value =
                if (bitmap != null) {
                    SampledDrawableState.Ready(bitmap)
                } else {
                    SampledDrawableState.Failed
                }
        }
    return when (val s = state) {
        is SampledDrawableState.Ready -> SampledDrawable(s.bitmap, isReady = true)
        SampledDrawableState.Failed ->
            SampledDrawable(ImageBitmap.imageResource(id), isReady = true)
        SampledDrawableState.Loading -> SampledDrawable(placeholder, isReady = false)
    }
}
