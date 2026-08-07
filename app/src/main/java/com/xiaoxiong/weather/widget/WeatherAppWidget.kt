package com.xiaoxiong.weather.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xiaoxiong.weather.MainActivity
import com.xiaoxiong.weather.R

class WeatherAppWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_WIDE, SIZE_TALL)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = WeatherWidgetData.load(context)
        provideContent {
            GlanceTheme {
                WeatherWidgetContent(ui = ui)
            }
        }
    }

    companion object {
        val SIZE_SMALL = DpSize(110.dp, 110.dp)
        val SIZE_MEDIUM = DpSize(180.dp, 110.dp)
        val SIZE_WIDE = DpSize(300.dp, 110.dp)
        val SIZE_TALL = DpSize(180.dp, 220.dp)
    }
}

class WeatherAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherAppWidget()
}

@Composable
private fun WeatherWidgetContent(ui: WeatherWidgetUi?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )
    val showWide = size.width >= 260.dp
    val showTall = size.height >= 180.dp
    val showCompact = size.width < 150.dp && size.height < 150.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(Color(0xFF2F80ED)))
            .padding(if (showCompact) 10.dp else 14.dp)
            .clickable(openApp),
        verticalAlignment = Alignment.Top
    ) {
        if (ui == null) {
            EmptyWidgetBody()
            return@Column
        }

        if (showCompact) {
            CompactWidgetBody(ui)
            return@Column
        }

        MainWidgetHeader(ui = ui, largeTemp = showWide || showTall)

        if (showWide && ui.hours.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            HourlyStrip(ui.hours)
        }

        if (showTall) {
            if (ui.tomorrowHighLowText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                TomorrowRow(ui)
            }
            if (ui.hours.isNotEmpty() && !showWide) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                HourlyStrip(ui.hours)
            }
            if (ui.tipText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = ui.tipText,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFDCEBFF)),
                        fontSize = 11.sp
                    ),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun EmptyWidgetBody() {
    val ctx = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_bear_sunny),
            contentDescription = ctx.getString(R.string.app_name),
            modifier = GlanceModifier.size(44.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ctx.getString(R.string.app_name),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = ctx.getString(R.string.widget_empty),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE3F2FD)),
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun CompactWidgetBody(ui: WeatherWidgetUi) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(ui.bearRes),
            contentDescription = ui.description,
            modifier = GlanceModifier.size(36.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = ui.temperatureText,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Text(
            text = ui.cityName,
            style = TextStyle(
                color = ColorProvider(Color(0xFFE8F4FF)),
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun MainWidgetHeader(ui: WeatherWidgetUi, largeTemp: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(ui.bearRes),
            contentDescription = ui.description,
            modifier = GlanceModifier.size(if (largeTemp) 56.dp else 52.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ui.cityName,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE8F4FF)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.temperatureText,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = if (largeTemp) 32.sp else 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = ui.emoji,
                    style = TextStyle(fontSize = 20.sp)
                )
            }
            Text(
                text = "${ui.description} · ${ui.highLowText}",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFDCEBFF)),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = if (ui.isOfflineHint) {
                    LocalContext.current.getString(R.string.widget_cache_prefix, ui.updatedText)
                } else {
                    ui.updatedText
                },
                style = TextStyle(
                    color = ColorProvider(Color(0xFFB3D4FC)),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TomorrowRow(ui: WeatherWidgetUi) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(ColorProvider(Color(0x332156D9)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ui.tomorrowLabel,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = ui.tomorrowEmoji,
            style = TextStyle(fontSize = 14.sp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "${ui.tomorrowDescription} · ${ui.tomorrowHighLowText}",
            style = TextStyle(
                color = ColorProvider(Color(0xFFDCEBFF)),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun HourlyStrip(hours: List<WeatherWidgetHourUi>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hours.take(5).forEach { hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = hour.label,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB3D4FC)),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = hour.emoji,
                    style = TextStyle(fontSize = 12.sp)
                )
                Text(
                    text = hour.tempText,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
