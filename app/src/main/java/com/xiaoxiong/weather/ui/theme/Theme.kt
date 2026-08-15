package com.xiaoxiong.weather.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(
        primary = SkyBlue,
        onPrimary = Color.White,
        primaryContainer = SoftTeal.copy(alpha = 0.35f),
        onPrimaryContainer = DeepNavy,
        secondary = Leaf,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7F3EF),
        onSecondaryContainer = Color(0xFF063B36),
        tertiary = AccentCoral,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE0D6),
        onTertiaryContainer = Color(0xFF4A1B0C),
        background = Mist,
        onBackground = DeepNavy,
        surface = Color.White,
        onSurface = DeepNavy,
        surfaceVariant = Cloud,
        onSurfaceVariant = Color(0xFF5A6B7A),
        outline = Color(0xFFB7C4D1),
        error = Color(0xFFE53935),
        errorContainer = Color(0xFFFFEBEE),
        onErrorContainer = Color(0xFFB71C1C),
    )

private val DarkColors =
    darkColorScheme(
        primary = SkyBlueDark,
        onPrimary = Color(0xFF06233B),
        primaryContainer = Color(0xFF1E3A5F),
        onPrimaryContainer = Color(0xFFE8F3FF),
        secondary = SoftTealDark,
        onSecondary = Color(0xFF00363A),
        secondaryContainer = Color(0xFF1A3C40),
        onSecondaryContainer = Color(0xFFE4FBFD),
        tertiary = Color(0xFFFFAB91),
        onTertiary = Color(0xFF3E1408),
        tertiaryContainer = Color(0xFF5C3A28),
        onTertiaryContainer = Color(0xFFFFE8D6),
        background = MistDark,
        onBackground = Color(0xFFF7FAFC),
        // 卡片略亮一点，避免深底上字发灰
        surface = Color(0xFF2A3648),
        onSurface = Color(0xFFF7FAFC),
        surfaceVariant = Color(0xFF3B4A5E),
        // 副文案提亮，暗色模式下更易读
        onSurfaceVariant = Color(0xFFE4ECF4),
        outline = Color(0xFFA8B6C6),
        outlineVariant = Color(0xFF556578),
        error = Color(0xFFFF8A80),
        errorContainer = Color(0xFF6B2424),
        onErrorContainer = Color(0xFFFFE0E0),
    )

@Composable
fun XiaoxiongWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 跟随系统取色（Material You）；仅 Android 12+ 有系统色时生效，旧版本回退自定义配色。默认关闭。 */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            darkTheme -> DarkColors
            else -> LightColors
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
