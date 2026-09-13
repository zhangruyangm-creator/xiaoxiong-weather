package com.xiaoxiong.weather.ui.weather

import androidx.compose.ui.graphics.Color

data class WeatherSceneStyle(
    val scene: WeatherScene,
    val gradient: List<Color>,
    val contentColor: Color,
    val mutedContentColor: Color,
)

fun weatherSceneStyle(
    code: Int?,
    isDay: Boolean?,
    appDark: Boolean = false,
): WeatherSceneStyle {
    val scene = weatherSceneOf(code, isDay)
    val day = isDay != false
    return when (scene) {
        // 天气码 1（大体晴）与 0 共用 CLEAR 场景；启动图标层单独区分 FEW_CLOUDS，
        // 应用内背景/图标不单列，避免多一套渐变而收益有限。
        WeatherScene.CLEAR ->
            if (day) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF56CCF2), Color(0xFF2F80ED), Color(0xFF6DD5FA)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.82f),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
                    contentColor = Color(0xFFEAF2FF),
                    mutedContentColor = Color(0xFFEAF2FF).copy(alpha = 0.8f),
                )
            }

        // 少云夜间与 CLEAR 夜间同色，供 atmosphere 侧按 isDay=false 取深色渐变；
        // 若仍用白天的浅蓝会在夜间出现「白天蓝」违和。
        WeatherScene.PARTLY_CLOUDY ->
            if (day) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF89CFF0), Color(0xFF7F9CF5), Color(0xFFB8C6FF)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.85f),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
                    contentColor = Color(0xFFEAF2FF),
                    mutedContentColor = Color(0xFFEAF2FF).copy(alpha = 0.8f),
                )
            }

        WeatherScene.OVERCAST ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF6B7B8C), Color(0xFF4A5568), Color(0xFF718096)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.FOG ->
            if (appDark) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF3A4550), Color(0xFF2A3340), Color(0xFF4A5568)),
                    contentColor = Color(0xFFECEFF1),
                    mutedContentColor = Color(0xFFCFD8DC),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE), Color(0xFFCFD8DC)),
                    contentColor = Color(0xFF263238),
                    mutedContentColor = Color(0xFF37474F),
                )
            }

        WeatherScene.DRIZZLE ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF74A9CF), Color(0xFF5C8DB8), Color(0xFF89B4D4)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF3D5A80), Color(0xFF293241), Color(0xFF4A6FA5)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.HEAVY_RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF1B263B), Color(0xFF0D1B2A), Color(0xFF415A77)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.THUNDER ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF232526), Color(0xFF414345), Color(0xFF1A1A2E)),
                contentColor = Color(0xFFFFF3E0),
                mutedContentColor = Color(0xFFFFE0B2),
            )

        WeatherScene.HAIL ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF4A6572), Color(0xFF344955), Color(0xFF6B8A99)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.FREEZING_RAIN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF5B7C8A), Color(0xFF3E5A66), Color(0xFF7FA0AE)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )

        WeatherScene.SNOW ->
            if (appDark) {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFF3E5160), Color(0xFF2C3A47), Color(0xFF5A7180)),
                    contentColor = Color(0xFFE3F2FD),
                    mutedContentColor = Color(0xFFBBDEFB),
                )
            } else {
                WeatherSceneStyle(
                    scene = scene,
                    gradient = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9)),
                    contentColor = Color(0xFF1A237E),
                    mutedContentColor = Color(0xFF283593),
                )
            }

        WeatherScene.HEAVY_SNOW ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF546E7A), Color(0xFF37474F), Color(0xFF78909C)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.9f),
            )

        WeatherScene.UNKNOWN ->
            WeatherSceneStyle(
                scene = scene,
                gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.85f),
            )
    }
}
