package com.xiaoxiong.weather.ui.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.data.DebugWeatherOverride
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.weather.AppScreenBackground
import com.xiaoxiong.weather.ui.weather.SectionHeader
import com.xiaoxiong.weather.ui.weather.SoftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugWeatherScreen(
    baseWeather: WeatherSnapshot?,
    initialOverride: DebugWeatherOverride,
    onBack: () -> Unit,
    onApply: (DebugWeatherOverride) -> Unit,
    onExitDebug: () -> Unit,
    onClearOverride: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val current = baseWeather?.current
    var temperature by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.temperatureC, current?.temperatureC))
    }
    var feels by remember(initialOverride, current) {
        mutableStateOf(
            fieldText(initialOverride.apparentTemperatureC, current?.apparentTemperatureC),
        )
    }
    var humidity by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.humidityPercent, current?.humidityPercent))
    }
    var weatherCode by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.weatherCode, current?.weatherCode))
    }
    var precip by remember(initialOverride, current) {
        mutableStateOf(
            fieldText(
                initialOverride.precipitationRateMmPerHour,
                current?.precipitationRateMmPerHour,
            ),
        )
    }
    var wind by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.windSpeedKmh, current?.windSpeedKmh))
    }
    var gust by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.windGustsKmh, current?.windGustsKmh))
    }
    var visibility by remember(initialOverride, current) {
        mutableStateOf(fieldText(initialOverride.visibilityMeters, current?.visibilityMeters))
    }
    var uv by remember(initialOverride, baseWeather) {
        mutableStateOf(fieldText(initialOverride.uvIndex, baseWeather?.airQuality?.uvIndex))
    }
    var aqi by remember(initialOverride, baseWeather) {
        mutableStateOf(
            fieldText(initialOverride.europeanAqi, baseWeather?.airQuality?.europeanAqi),
        )
    }
    var dust by remember(initialOverride, baseWeather) {
        mutableStateOf(fieldText(initialOverride.dustUgM3, baseWeather?.airQuality?.dustUgM3))
    }

    fun buildOverride(): DebugWeatherOverride =
        DebugWeatherOverride(
            temperatureC = temperature.toDoubleOrNull(),
            apparentTemperatureC = feels.toDoubleOrNull(),
            humidityPercent = humidity.toIntOrNull(),
            weatherCode = weatherCode.toIntOrNull(),
            precipitationRateMmPerHour = precip.toDoubleOrNull(),
            windSpeedKmh = wind.toDoubleOrNull(),
            windGustsKmh = gust.toDoubleOrNull(),
            visibilityMeters = visibility.toDoubleOrNull(),
            uvIndex = uv.toDoubleOrNull(),
            europeanAqi = aqi.toIntOrNull(),
            dustUgM3 = dust.toDoubleOrNull(),
        )

    AppScreenBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            stringResource(R.string.debug_title),
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
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.debug_editor_title),
                            subtitle =
                                baseWeather?.city?.displayName
                                    ?: stringResource(R.string.debug_no_city),
                            icon = Icons.Filled.BugReport,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.debug_editor_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DebugField(
                                stringResource(R.string.debug_field_temp),
                                temperature,
                            ) { temperature = it }
                            DebugField(
                                stringResource(R.string.debug_field_feels),
                                feels,
                            ) { feels = it }
                            DebugField(
                                stringResource(R.string.debug_field_humidity),
                                humidity,
                                KeyboardType.Number,
                            ) { humidity = it }
                            DebugField(
                                stringResource(R.string.debug_field_weather_code),
                                weatherCode,
                                KeyboardType.Number,
                            ) { weatherCode = it }
                            DebugField(
                                stringResource(R.string.debug_field_precip),
                                precip,
                            ) { precip = it }
                            DebugField(
                                stringResource(R.string.debug_field_wind),
                                wind,
                            ) { wind = it }
                            DebugField(
                                stringResource(R.string.debug_field_gust),
                                gust,
                            ) { gust = it }
                            DebugField(
                                stringResource(R.string.debug_field_visibility),
                                visibility,
                            ) { visibility = it }
                            DebugField(
                                stringResource(R.string.debug_field_uv),
                                uv,
                            ) { uv = it }
                            DebugField(
                                stringResource(R.string.debug_field_aqi),
                                aqi,
                                KeyboardType.Number,
                            ) { aqi = it }
                            DebugField(
                                stringResource(R.string.debug_field_dust),
                                dust,
                            ) { dust = it }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { onApply(buildOverride()) },
                            modifier = Modifier.weight(1f),
                            enabled = baseWeather != null,
                        ) {
                            Text(stringResource(R.string.debug_apply))
                        }
                        OutlinedButton(
                            onClick = onClearOverride,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.debug_clear_override))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onExitDebug,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.debug_exit))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DebugField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun fieldText(
    override: Double?,
    fallback: Double?,
): String = (override ?: fallback)?.let { trimNum(it) } ?: ""

private fun fieldText(
    override: Int?,
    fallback: Int?,
): String = (override ?: fallback)?.toString() ?: ""

private fun trimNum(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
