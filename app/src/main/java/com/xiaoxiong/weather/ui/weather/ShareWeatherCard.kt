package com.xiaoxiong.weather.ui.weather

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
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
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 天气分享卡片：太阳熊插画（节日覆盖 > 日常片刻）+ 场景渐变 + 当前天气。
 * 预览即所得——弹窗里看到的就是分享出去的图。
 */
@Composable
fun ShareWeatherCard(
    snapshot: WeatherSnapshot,
    quote: String,
    modifier: Modifier = Modifier
) {
    val current = snapshot.current
    val precipRate = WeatherCodes.intensityPrecipMmPerHour(current)
    val displayCode = WeatherCodes.displayCode(current.weatherCode, precipRate)
    val style = weatherSceneStyle(displayCode, current.isDay, appDark = false)
    val tempUnit = LocalTemperatureUnit.current

    val bear = remember(snapshot.current.time) { shareBearArtwork(snapshot) }
    val dateLabel = remember(snapshot.current.time) {
        val date = parseFlexibleDateTime(snapshot.current.time)?.toLocalDate() ?: LocalDate.now()
        date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
        )
    }
    val today = snapshot.daily.firstOrNull()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(style.gradient))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = style.mutedContentColor
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = style.contentColor
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        val bearShape = RoundedCornerShape(20.dp)
        Image(
            painter = painterResource(bear.drawableRes),
            contentDescription = stringResource(bear.captionRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .clip(bearShape)
                .align(Alignment.CenterHorizontally)
                .background(style.contentColor.copy(alpha = 0.06f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(bear.captionRes),
            style = MaterialTheme.typography.titleMedium,
            color = style.contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherIcon(
                code = displayCode,
                isDay = current.isDay ?: true,
                modifier = Modifier.size(44.dp),
                precipMmPerHour = precipRate
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.city.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = style.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = weatherCodeDescription(displayCode, precipRate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.mutedContentColor
                )
            }
            Text(
                text = formatTemperature(current.temperatureC, tempUnit),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = style.contentColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.high_low_temps,
                formatTemperature(today?.tempMaxC, tempUnit),
                formatTemperature(today?.tempMinC, tempUnit)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = style.mutedContentColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = style.mutedContentColor.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = quote,
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedContentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 分享卡片预览弹窗：卡片内容实时录进 GraphicsLayer，点分享时导出 PNG。 */
@Composable
fun ShareWeatherCardDialog(snapshot: WeatherSnapshot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    // 每次打开弹窗随机抽一句，预览与导出同一句
    val quotes = stringArrayResource(R.array.share_card_quotes)
    val quote = remember(quotes.size) {
        if (quotes.isEmpty()) "" else quotes.random()
    }

    Dialog(onDismissRequest = { if (!sharing) onDismiss() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShareWeatherCard(
                snapshot = snapshot,
                quote = quote,
                modifier = Modifier
                    .width(300.dp)
                    // 每帧把卡片内容录进 layer，分享时 toImageBitmap() 直接出图
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                    }
            )
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
                            try {
                                val raw = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                // 硬件位图无法 compress；统一拷成软件 ARGB_8888 再导出
                                val bitmap = raw.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: throw IllegalStateException("bitmap copy failed")
                                shareWeatherCardImage(context, bitmap)
                                onDismiss()
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    R.string.share_card_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                sharing = false
                            }
                        }
                    },
                    enabled = !sharing
                ) {
                    Text(stringResource(R.string.share_card_send))
                }
            }
        }
    }
}

private data class BearArtwork(
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val captionRes: Int
)

/** 与日出日落弧同一套片刻逻辑：节日优先，其余按钟点/周末选取。 */
private fun shareBearArtwork(snapshot: WeatherSnapshot): BearArtwork {
    val now = parseFlexibleDateTime(snapshot.current.time.orEmpty())
    val date = now?.toLocalDate() ?: LocalDate.now()
    val time = now?.toLocalTime() ?: LocalTime.now()
    val today = snapshot.daily.firstOrNull()
    val rise = today?.sunrise?.let { parseFlexibleDateTime(it) }
    val set = today?.sunset?.let { parseFlexibleDateTime(it) }
    val phase = if (now != null && rise != null && set != null) {
        sunPathPhase(now, rise, set)
    } else {
        null
    }
    val moment = sunPathBearMoment(phase, date, time)
    val holiday = holidayBearOverride(date, moment)
    return if (holiday != null) {
        BearArtwork(holiday.bearResFor(date), holiday.captionResFor(date))
    } else {
        BearArtwork(moment.bearResFor(date), moment.captionResFor(date))
    }
}

private fun shareWeatherCardImage(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "weather_card.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_card_chooser))
    )
}
