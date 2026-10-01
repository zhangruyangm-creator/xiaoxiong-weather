package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle

/**
 * 小熊战机 HUD / 渲染共用主题：深色玻璃 + 冰蓝描边 + 暖色警示，
 * 与冰球 / 台球保持同一套「深色玻璃」视觉语言。
 */
internal object PlaneUi {
    val glass = Color(0xE8152433)
    val glassDeep = Color(0xF20B1622)
    val skyBorder = Color(0x998FD9F2)
    val textSecondary = Color(0xB3C3DCEF)
    val accent = Color(0xFF7FE3F2)
    val accentWarm = Color(0xFFFFC46B)
    val danger = Color(0xFFFF8A80)
    val warn = Color(0xFFFFB74D)
    val ok = Color(0xFF69F0AE)
}

// ---------------------------------------------------------------------
// 实体调色板
// ---------------------------------------------------------------------

internal val planePlayerCream = Color(0xFFFFF4DC)
internal val planePlayerCreamShade = Color(0xFFE6D2AC)
internal val planePlayerBrown = Color(0xFF8A5A2C)
internal val planePlayerBrownDark = Color(0xFF5A3818)

/** 玩家机涂装调色板（烘焙进精灵）。 */
internal data class PlanePlayerPalette(
    val cream: Color,
    val shade: Color,
    val brown: Color,
    val brownDark: Color,
)

internal fun planePlayerPalette(tint: PlaneShipTint): PlanePlayerPalette =
    when (tint) {
        PlaneShipTint.CLASSIC ->
            PlanePlayerPalette(planePlayerCream, planePlayerCreamShade, planePlayerBrown, planePlayerBrownDark)
        PlaneShipTint.GOLD ->
            PlanePlayerPalette(
                cream = Color(0xFFFFF0B8),
                shade = Color(0xFFE0C56A),
                brown = Color(0xFFB8860B),
                brownDark = Color(0xFF6B4E0A),
            )
        PlaneShipTint.MIDNIGHT ->
            PlanePlayerPalette(
                cream = Color(0xFFD7E3F4),
                shade = Color(0xFF9AABB8),
                brown = Color(0xFF3D5A80),
                brownDark = Color(0xFF1B2838),
            )
        PlaneShipTint.ROSE ->
            PlanePlayerPalette(
                cream = Color(0xFFFFE4EC),
                shade = Color(0xFFE8B0C0),
                brown = Color(0xFFC2185B),
                brownDark = Color(0xFF6A0F33),
            )
        PlaneShipTint.AURORA ->
            PlanePlayerPalette(
                cream = Color(0xFFE8FFF6),
                shade = Color(0xFFA8E6CF),
                brown = Color(0xFF26A69A),
                brownDark = Color(0xFF0D4740),
            )
    }

internal val planePlayerGlass = Color(0xFF7FD4FF)
internal val planeBearFur = Color(0xFFF2C79A)
internal val planeBearEar = Color(0xFFDCA36A)
internal val planeBearMuzzle = Color(0xFFFFF3DC)
internal val planeBearBlush = Color(0x66F48FB1)

/** 敌机配色：机身 / 亮面板 / 警示点缀 / 深色描边。 */
internal data class PlaneEnemyPalette(
    val body: Color,
    val panel: Color,
    val accent: Color,
    val dark: Color,
)

internal fun planeEnemyPalette(
    type: PlaneEnemyType,
    bossPhase: Int = 0,
): PlaneEnemyPalette =
    when (type) {
        PlaneEnemyType.GRUNT ->
            PlaneEnemyPalette(
                body = Color(0xFF8E9BB3),
                panel = Color(0xFFC2CEE2),
                accent = Color(0xFFFF8A65),
                dark = Color(0xFF47536A),
            )
        PlaneEnemyType.DIVER ->
            PlaneEnemyPalette(
                body = Color(0xFF7E8ACF),
                panel = Color(0xFFB4BEF5),
                accent = Color(0xFFFFD54F),
                dark = Color(0xFF3B4378),
            )
        PlaneEnemyType.WEAVER ->
            PlaneEnemyPalette(
                body = Color(0xFF6FAE72),
                panel = Color(0xFFBCE0BC),
                accent = Color(0xFFFFB74D),
                dark = Color(0xFF36603A),
            )
        PlaneEnemyType.TURRET ->
            PlaneEnemyPalette(
                body = Color(0xFFC39B4B),
                panel = Color(0xFFEBD494),
                accent = Color(0xFFE53935),
                dark = Color(0xFF6E5520),
            )
        PlaneEnemyType.TRANSPORT ->
            PlaneEnemyPalette(
                body = Color(0xFFC6BF9F),
                panel = Color(0xFFF0EAD2),
                accent = Color(0xFFFFD740),
                dark = Color(0xFF6A6549),
            )
        PlaneEnemyType.HELI ->
            PlaneEnemyPalette(
                body = Color(0xFF4E8C86),
                panel = Color(0xFFA7D8D2),
                accent = Color(0xFFFF7043),
                dark = Color(0xFF234744),
            )
        PlaneEnemyType.TANK ->
            PlaneEnemyPalette(
                body = Color(0xFF6E7A5A),
                panel = Color(0xFFB9C49B),
                accent = Color(0xFFFFB300),
                dark = Color(0xFF333B26),
            )
        PlaneEnemyType.SCOUT ->
            PlaneEnemyPalette(
                body = Color(0xFF5B8DB8),
                panel = Color(0xFFA8D0F0),
                accent = Color(0xFFFFEB3B),
                dark = Color(0xFF2A4A66),
            )
        PlaneEnemyType.BOMBER ->
            PlaneEnemyPalette(
                body = Color(0xFF8B6F5A),
                panel = Color(0xFFD4B896),
                accent = Color(0xFFFF7043),
                dark = Color(0xFF4A3528),
            )
        PlaneEnemyType.SNIPER ->
            PlaneEnemyPalette(
                body = Color(0xFF6A5B8A),
                panel = Color(0xFFB5A6D4),
                accent = Color(0xFF69F0AE),
                dark = Color(0xFF352A4A),
            )
        PlaneEnemyType.BOSS ->
            when (bossPhase.coerceIn(0, 2)) {
                0 ->
                    PlaneEnemyPalette(
                        body = Color(0xFF56687A),
                        panel = Color(0xFF93A9BD),
                        accent = Color(0xFFFF7043),
                        dark = Color(0xFF2B3746),
                    )
                1 ->
                    PlaneEnemyPalette(
                        body = Color(0xFF6E5A44),
                        panel = Color(0xFFB0906C),
                        accent = Color(0xFFFFA726),
                        dark = Color(0xFF3C2F1F),
                    )
                else ->
                    PlaneEnemyPalette(
                        body = Color(0xFF6E3636),
                        panel = Color(0xFFB06565),
                        accent = Color(0xFFFF5252),
                        dark = Color(0xFF3A1A1A),
                    )
            }
    }

// ---------------------------------------------------------------------
// 背景调色
// ---------------------------------------------------------------------

/**
 * 天空底色：天气渐变统一压暗并偏冷，保证弹幕 / 白字对比度，
 * 同时保留天气与昼夜的色相差异（雪天偏蓝、雷暴偏墨、夜晚偏靛）。
 */
internal fun planeBackdrop(style: WeatherSceneStyle): List<Color> = style.gradient.map { lerp(it, Color(0xFF050B16), 0.46f) }
