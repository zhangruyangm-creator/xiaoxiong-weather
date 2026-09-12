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

/**
 * 2x2 默认小组件。支持响应式拉伸：拖大后自动切换为 中/宽/大 布局，
 * 具体尺寸档位由 [WeatherAppWidget.SIZES] 枚举（与 [WeatherAppWideWidget] 共享）。
 */
class WeatherAppWidget : WeatherAppWidgetBase() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                WeatherWidgetSizes.SMALL,
                WeatherWidgetSizes.MEDIUM,
                WeatherWidgetSizes.WIDE,
                WeatherWidgetSizes.EXTRA_WIDE,
                WeatherWidgetSizes.TALL,
                WeatherWidgetSizes.LARGE,
            ),
        )

    companion object {
        val SIZES = WeatherWidgetSizes
    }
}

/**
 * 3x2 默认小组件，复用同一套 Glance 渲染内容；添加器里额外出现一个更大选项。
 */
class WeatherAppWideWidget : WeatherAppWidgetBase() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                WeatherWidgetSizes.MEDIUM,
                WeatherWidgetSizes.WIDE,
                WeatherWidgetSizes.EXTRA_WIDE,
                WeatherWidgetSizes.TALL,
                WeatherWidgetSizes.LARGE,
            ),
        )
}

/**
 * 4x2 默认小组件，复用同一套 Glance 渲染内容；添加器里的最宽横档选项。
 */
class WeatherAppWide4x2Widget : WeatherAppWidgetBase() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                WeatherWidgetSizes.WIDE,
                WeatherWidgetSizes.EXTRA_WIDE,
                WeatherWidgetSizes.LARGE,
            ),
        )
}

/** 各尺寸档位集中定义，各 widget 类共用，避免 Layout 只能看到默认档。 */
object WeatherWidgetSizes {
    /** 2x2 最小可用 */
    val SMALL = DpSize(110.dp, 110.dp)

    /** 3x2 */
    val MEDIUM = DpSize(180.dp, 110.dp)

    /** 4x2 */
    val WIDE = DpSize(300.dp, 110.dp)

    /** 5x2 */
    val EXTRA_WIDE = DpSize(400.dp, 110.dp)

    /** 2x3 */
    val TALL = DpSize(180.dp, 220.dp)

    /** 4x4 */
    val LARGE = DpSize(300.dp, 220.dp)
}

abstract class WeatherAppWidgetBase : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val ui = WeatherWidgetData.load(context)
        provideContent {
            GlanceTheme {
                WeatherWidgetContent(ui = ui)
            }
        }
    }
}

class WeatherAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherAppWidget()
}

/** 大尺寸 widget 的 receiver。 */
class WeatherAppWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherAppWideWidget()
}

/** 4x2 超宽 widget 的 receiver。 */
class WeatherAppWide4x2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherAppWide4x2Widget()
}

@Composable
private fun WeatherWidgetContent(ui: WeatherWidgetUi?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openApp =
        actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
    val showWide = size.width >= 260.dp
    val showTall = size.height >= 180.dp
    val showCompact = size.width < 150.dp && size.height < 150.dp
    val showLarge = size.width >= 280.dp && size.height >= 200.dp
    val showExtraWide = size.width >= 380.dp

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(
                    ImageProvider(ui?.bgRes?.takeIf { it != 0 } ?: R.drawable.widget_bg_sunny),
                ).padding(if (showCompact) 10.dp else 14.dp)
                .clickable(openApp),
        verticalAlignment = Alignment.Top,
    ) {
        if (ui == null) {
            EmptyWidgetBody()
            return@Column
        }

        if (showCompact) {
            CompactWidgetBody(ui)
            return@Column
        }

        if (showLarge) {
            LargeWidgetBody(ui)
            return@Column
        }

        MainWidgetHeader(ui = ui, largeTemp = showWide || showTall)

        if (showWide && ui.hours.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            HourlyStrip(ui.hours, count = if (showExtraWide) 8 else 5)
        }

        if (showTall) {
            if (ui.tomorrowHighLowText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                TomorrowRow(ui)
            }
            if (ui.hours.isNotEmpty() && !showWide) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                HourlyStrip(ui.hours, count = 5)
            }
            if (ui.tipText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = ui.tipText,
                    style =
                        TextStyle(
                            color = ColorProvider(Color(0xFFDCEBFF)),
                            fontSize = 11.sp,
                        ),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun LargeWidgetBody(ui: WeatherWidgetUi) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        MainWidgetHeader(ui = ui, largeTemp = true)
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            MetricTile(
                modifier = GlanceModifier.defaultWeight(),
                label = context.getString(R.string.metric_feels_like),
                value = ui.feelsLikeText,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            MetricTile(
                modifier = GlanceModifier.defaultWeight(),
                label = context.getString(R.string.metric_humidity),
                value = ui.humidityText,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            MetricTile(
                modifier = GlanceModifier.defaultWeight(),
                label = context.getString(R.string.metric_wind),
                value = ui.windText,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            MetricTile(
                modifier = GlanceModifier.defaultWeight(),
                label = context.getString(R.string.metric_pressure),
                value = ui.pressureText,
            )
        }
        if (ui.hours.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            HourlyStrip(ui.hours, count = 7)
        }
        if (ui.tomorrowHighLowText.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            TomorrowRow(ui)
        }
        if (ui.tipText.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = ui.tipText,
                style =
                    TextStyle(
                        color = ColorProvider(Color(0xFFDCEBFF)),
                        fontSize = 11.sp,
                    ),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: GlanceModifier = GlanceModifier,
    label: String,
    value: String,
) {
    Column(
        modifier =
            modifier
                .cornerRadius(12.dp)
                .background(ColorProvider(Color(0x332156D9)))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style =
                TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = label,
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFB3D4FC)),
                    fontSize = 10.sp,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyWidgetBody() {
    val ctx = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_bear_sunny),
            contentDescription = ctx.getString(R.string.app_name),
            modifier = GlanceModifier.size(44.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ctx.getString(R.string.app_name),
                style =
                    TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = ctx.getString(R.string.widget_empty),
                style =
                    TextStyle(
                        color = ColorProvider(Color(0xFFE3F2FD)),
                        fontSize = 12.sp,
                    ),
            )
        }
    }
}

@Composable
private fun CompactWidgetBody(ui: WeatherWidgetUi) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(ui.bearRes),
            contentDescription = ui.description,
            modifier = GlanceModifier.size(36.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = ui.temperatureText,
            style =
                TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Text(
            text = ui.cityName,
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFE8F4FF)),
                    fontSize = 11.sp,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MainWidgetHeader(
    ui: WeatherWidgetUi,
    largeTemp: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(ui.bearRes),
            contentDescription = ui.description,
            modifier = GlanceModifier.size(if (largeTemp) 56.dp else 52.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ui.cityName,
                style =
                    TextStyle(
                        color = ColorProvider(Color(0xFFE8F4FF)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.temperatureText,
                    style =
                        TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = if (largeTemp) 32.sp else 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = ui.emoji,
                    style = TextStyle(fontSize = 20.sp),
                )
            }
            Text(
                text = "${ui.description} · ${ui.highLowText}",
                style =
                    TextStyle(
                        color = ColorProvider(Color(0xFFDCEBFF)),
                        fontSize = 12.sp,
                    ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text =
                    if (ui.isOfflineHint) {
                        LocalContext.current.getString(R.string.widget_cache_prefix, ui.updatedText)
                    } else {
                        ui.updatedText
                    },
                style =
                    TextStyle(
                        color = ColorProvider(Color(0xFFB3D4FC)),
                        fontSize = 11.sp,
                    ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TomorrowRow(ui: WeatherWidgetUi) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(ColorProvider(Color(0x332156D9)))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ui.tomorrowLabel,
            style =
                TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = ui.tomorrowEmoji,
            style = TextStyle(fontSize = 14.sp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "${ui.tomorrowDescription} · ${ui.tomorrowHighLowText}",
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFDCEBFF)),
                    fontSize = 12.sp,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun HourlyStrip(
    hours: List<WeatherWidgetHourUi>,
    count: Int = 5,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hours.take(count).forEach { hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = hour.label,
                    style =
                        TextStyle(
                            color = ColorProvider(Color(0xFFB3D4FC)),
                            fontSize = 10.sp,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = hour.emoji,
                    style = TextStyle(fontSize = 12.sp),
                )
                Text(
                    text = hour.tempText,
                    style =
                        TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}
