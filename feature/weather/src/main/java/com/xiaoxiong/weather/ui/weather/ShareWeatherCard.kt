package com.xiaoxiong.weather.ui.weather

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.util.WeatherCodes
import com.xiaoxiong.weather.util.formatTemperature
import com.xiaoxiong.weather.util.parseFlexibleDateTime
import com.xiaoxiong.weather.util.weatherCodeDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 天气分享卡片：太阳熊插画（节日覆盖 > 日常片刻）+ 场景渐变 + 当前天气。
 * 预览即所得——弹窗里看到的就是分享出去的图。
 */
@Composable
fun ShareWeatherCard(
    snapshot: WeatherSnapshot,
    quote: String,
    modifier: Modifier = Modifier,
) {
    val current = snapshot.current
    val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
    val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
    val style = weatherSceneStyle(displayCode, current.isDay, appDark = false)
    val tempUnit = LocalTemperatureUnit.current

    val bear = remember(snapshot.current.time) { shareBearArtwork(snapshot) }
    val dateLabel =
        remember(snapshot.current.time) {
            val date = parseFlexibleDateTime(snapshot.current.time)?.toLocalDate() ?: LocalDate.now()
            date.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
            )
        }
    val today = snapshot.daily.firstOrNull()

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(style.gradient))
                .then(
                    if (style.scrimAlpha > 0f) {
                        // 浅色场景白字必须走暗角：与首页卡片同一套复合对比度保障
                        Modifier.background(Color.Black.copy(alpha = style.scrimAlpha))
                    } else {
                        Modifier
                    },
                ).padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = style.mutedContentColor,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = style.contentColor,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        val bearShape = RoundedCornerShape(20.dp)
        Image(
            painter = painterResource(bear.drawableRes),
            contentDescription = stringResource(bear.captionRes),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(bearShape)
                    .align(Alignment.CenterHorizontally)
                    .background(style.contentColor.copy(alpha = 0.06f)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(bear.captionRes),
            style = MaterialTheme.typography.titleMedium,
            color = style.contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherIcon(
                code = displayCode,
                isDay = current.isDay ?: true,
                modifier = Modifier.size(44.dp),
                precipMmPerHour = precipRate,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.city.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = style.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = weatherCodeDescription(displayCode, precipRate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.mutedContentColor,
                )
            }
            Text(
                text = formatTemperature(current.temperatureC, tempUnit),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = style.contentColor,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                stringResource(
                    R.string.high_low_temps,
                    formatTemperature(today?.tempMaxC, tempUnit),
                    formatTemperature(today?.tempMinC, tempUnit),
                ),
            style = MaterialTheme.typography.labelMedium,
            color = style.mutedContentColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = style.mutedContentColor.copy(alpha = 0.35f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = quote,
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedContentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 分享卡片预览弹窗：卡片内容实时录进 GraphicsLayer，点分享时导出 PNG。 */
@Composable
fun ShareWeatherCardDialog(
    snapshot: WeatherSnapshot,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 每次打开弹窗随机抽一句，预览与导出同一句。
    // key 只能用内容而不能用 quotes.size：切换语言后数组长度可能不变，会保留旧语言的句子。
    val quotes = stringArrayResource(R.array.share_card_quotes)
    val quote =
        remember(quotes) {
            if (quotes.isEmpty()) "" else quotes.random()
        }
    // 卡片内容在弹窗打开时已固定（snapshot + quote），录制一次即可出图；
    // 逐帧 record 会在每次重绘（含弹窗内任何动画）时重复录制+拷贝，纯属浪费。
    //
    // 但 key 必须覆盖全部渲染输入：弹窗打开后若后台刷新了天气、或用户切了温度单位，
    // 预览会按新内容重绘，而这里仍重放第一次录制的旧图层——导出的图片与预览不一致。
    val cardKey = "${snapshot.current.time}|${snapshot.current.temperatureC}|$quote"
    val graphicsLayer = rememberGraphicsLayer()
    val recordedOnce = remember(cardKey) { booleanArrayOf(false) }
    var sharing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!sharing) onDismiss() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // key() 而非只重置 recordedOnce：graphicsLayer 自身也持有录制状态，
            // 不换实例就会继续 drawLayer 旧内容。
            key(cardKey) {
                ShareWeatherCard(
                    snapshot = snapshot,
                    quote = quote,
                    modifier =
                        Modifier
                            .width(300.dp)
                            // 首帧把卡片内容录进 layer，之后直接重放录制结果；
                            // 分享时 toImageBitmap() 从录制内容出图
                            .drawWithCache {
                                onDrawWithContent {
                                    if (!recordedOnce[0]) {
                                        graphicsLayer.record {
                                            this@onDrawWithContent.drawContent()
                                        }
                                        recordedOnce[0] = true
                                    }
                                    drawLayer(graphicsLayer)
                                }
                            },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, enabled = !sharing) {
                    Text(stringResource(R.string.share_card_cancel))
                }
                Button(
                    onClick = {
                        if (sharing) return@Button
                        sharing = true
                        scope.launch {
                            var raw: Bitmap? = null
                            var copy: Bitmap? = null
                            try {
                                // toImageBitmap 需要在主线程读取 GraphicsLayer 渲染结果；
                                // 之后的位图拷贝、PNG 压缩与文件写全部移到后台，避免卡住 UI 线程
                                val image = graphicsLayer.toImageBitmap()
                                val uri =
                                    withContext(Dispatchers.Default) {
                                        raw = image.asAndroidBitmap()
                                        // 硬件位图无法 compress；统一拷成软件 ARGB_8888 再导出
                                        copy =
                                            raw?.copy(Bitmap.Config.ARGB_8888, false)
                                                ?: throw IllegalStateException("bitmap copy failed")
                                        withContext(Dispatchers.IO) {
                                            writeShareCardImage(context, copy!!)
                                        }
                                    }
                                launchShareCard(context, uri)
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.share_card_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            } finally {
                                // 导出结束（无论成败）立即回收，避免大图滞留内存
                                copy?.recycle()
                                raw?.recycle()
                                sharing = false
                            }
                        }
                    },
                    enabled = !sharing,
                ) {
                    Text(stringResource(R.string.share_card_send))
                }
            }
        }
    }
}

private data class BearArtwork(
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val captionRes: Int,
)

/** 与日出日落弧同一套片刻逻辑：节日优先，其余按钟点/周末选取。 */
private fun shareBearArtwork(snapshot: WeatherSnapshot): BearArtwork {
    val now = parseFlexibleDateTime(snapshot.current.time.orEmpty())
    val date = now?.toLocalDate() ?: LocalDate.now()
    val time = now?.toLocalTime() ?: LocalTime.now()
    val today = snapshot.daily.firstOrNull()
    val rise = today?.sunrise?.let { parseFlexibleDateTime(it) }
    val set = today?.sunset?.let { parseFlexibleDateTime(it) }
    // DST 跳变日按真实时区换算，日出日落弧与钟点插画口径一致
    val zoneId = runCatching { snapshot.timezone?.let { ZoneId.of(it) } }.getOrNull()
    val phase =
        if (now != null && rise != null && set != null) {
            sunPathPhase(now, rise, set, zoneId)
        } else {
            null
        }
    val moment = sunPathBearMoment(phase, date, time, sunrise = rise?.toLocalTime())
    val holiday = holidayBearOverride(date, moment)
    return if (holiday != null) {
        BearArtwork(holiday.bearResFor(date), holiday.captionResFor(date))
    } else {
        BearArtwork(moment.bearResFor(date), moment.captionResFor(date))
    }
}

/** 压缩并写入缓存目录（IO 线程调用），返回可分享的 content URI。 */
private fun writeShareCardImage(
    context: Context,
    bitmap: Bitmap,
): Uri {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "weather_card.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

/** 拉起系统分享面板（主线程调用）。 */
private fun launchShareCard(
    context: Context,
    uri: Uri,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "weather", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_card_chooser)),
    )
}
