package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene

/**
 * 接雨雪天空配色与 HUD 顶部暗角，渲染与对比度回归测试共用同一份数据。
 *
 * [hudScrimAlpha] 按「HUD 区间内最亮天空色」反推：HUD 位于屏幕顶部，取天空渐变在
 * 0..[CATCH_HUD_TEXT_BAND] 范围内的最亮色，叠黑 alpha 后纯白主文字满足 WCAG
 * 小号文本 AA 4.5:1（留约 0.02 余量）；深色场景顶色本身已达标，alpha=0，
 * 整层跳过不产生多余合成。
 */
internal data class CatchSkyStyle(
    val top: Color,
    val bottom: Color,
    val hudScrimAlpha: Float,
) {
    /** 天空三色渐变；按样式缓存，避免每帧新建 Brush。 */
    val skyBrush: Brush by lazy { Brush.verticalGradient(skyGradient(top, bottom)) }

    /** HUD 顶部暗角：全强度覆盖 HUD 区间后向下渐隐。 */
    val hudScrimBrush: Brush by lazy {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = hudScrimAlpha),
            HUD_SCRIM_PLATEAU to Color.Black.copy(alpha = hudScrimAlpha),
            1f to Color.Transparent,
        )
    }
}

/** HUD 暗角覆盖的屏幕高度占比（约含状态栏 + 两行 HUD）。 */
internal const val CATCH_HUD_SCRIM_BAND = 0.42f

/** 参与 alpha 反推的 HUD 文本区间高度占比（状态栏 + 分数行 + 心形行）。 */
internal const val CATCH_HUD_TEXT_BAND = 0.20f

/** 暗角全强度段占暗角层的比例：保证 HUD 文本区间（0..[CATCH_HUD_TEXT_BAND]）不被渐隐削弱。 */
internal const val HUD_SCRIM_PLATEAU = 0.55f

internal val catchSkySunnyDay = CatchSkyStyle(Color(0xFF4FC3F7), Color(0xFFB3E5FC), hudScrimAlpha = 0.40f)

internal val catchSkySunnyNight = CatchSkyStyle(Color(0xFF1A237E), Color(0xFF3F51B5), hudScrimAlpha = 0f)

internal val catchSkyCloudy = CatchSkyStyle(Color(0xFF92A1AD), Color(0xFFCAD1D8), hudScrimAlpha = 0.32f)

internal val catchSkyRainLight = CatchSkyStyle(Color(0xFF7C94A8), Color(0xFFB4C7D2), hudScrimAlpha = 0.26f)

internal val catchSkyRain = CatchSkyStyle(Color(0xFF5D7B94), Color(0xFF9DB4C3), hudScrimAlpha = 0.12f)

internal val catchSkyRainHeavy = CatchSkyStyle(Color(0xFF47657F), Color(0xFF8198AC), hudScrimAlpha = 0f)

internal val catchSkySnow = CatchSkyStyle(Color(0xFFBEC7D3), Color(0xFFE8EDF2), hudScrimAlpha = 0.45f)

internal val catchSkyStorm = CatchSkyStyle(Color(0xFF33414F), Color(0xFF6B7B8A), hudScrimAlpha = 0f)

internal val catchSkyFreezingRain = CatchSkyStyle(Color(0xFF5A6E86), Color(0xFFA7BCC8), hudScrimAlpha = 0.06f)

internal val catchSkyHail = CatchSkyStyle(Color(0xFF3D5568), Color(0xFF8BA2B5), hudScrimAlpha = 0f)

internal fun catchSceneSky(
    scene: CatchScene,
    isDay: Boolean,
): CatchSkyStyle =
    when (scene) {
        CatchScene.SUNNY -> if (isDay) catchSkySunnyDay else catchSkySunnyNight
        CatchScene.CLOUDY -> catchSkyCloudy
        CatchScene.RAIN_LIGHT -> catchSkyRainLight
        CatchScene.RAIN -> catchSkyRain
        CatchScene.RAIN_HEAVY -> catchSkyRainHeavy
        CatchScene.SNOW -> catchSkySnow
        CatchScene.STORM -> catchSkyStorm
        CatchScene.FREEZING_RAIN -> catchSkyFreezingRain
        CatchScene.HAIL -> catchSkyHail
    }
