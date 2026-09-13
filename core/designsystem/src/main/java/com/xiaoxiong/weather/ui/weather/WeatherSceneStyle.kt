package com.xiaoxiong.weather.ui.weather

import androidx.compose.ui.graphics.Color

data class WeatherSceneStyle(
    val scene: WeatherScene,
    val gradient: List<Color>,
    val contentColor: Color,
    val mutedContentColor: Color,
    /**
     * 浅色渐变场景在白字下叠加的黑色暗角透明度（0 = 不叠加，深色场景默认）。
     * 卡片在动画层之上、内容之下按此值铺一层纯黑，保证白字对复合后背景 ≥3:1。
     */
    val scrimAlpha: Float = 0f,
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
                    // 亮蓝渐变整体加深（保持蓝青色相）再叠 0.30 黑scrim：
                    // 白字对复合后最亮 stop 4.80:1、muted 0.9 白字 4.23:1，均是白字方案
                    gradient = listOf(Color(0xFF2B8FD0), Color(0xFF1C64B8), Color(0xFF43AEDF)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.9f),
                    scrimAlpha = 0.30f,
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
                    // 亮堇紫渐变整体加深（保持色相）再叠 0.30 黑scrim：
                    // 白字对复合后最亮 stop 4.88:1、muted 0.9 白字 4.30:1
                    gradient = listOf(Color(0xFF5FA9DB), Color(0xFF5570D6), Color(0xFF8296E8)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.9f),
                    scrimAlpha = 0.30f,
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
                    // 浅灰蓝雾天加深为石板灰（保持色相）再叠 0.30 黑scrim：
                    // 白字对复合后最亮 stop 4.56:1、muted 0.9 白字 4.04:1
                    gradient = listOf(Color(0xFF7E939E), Color(0xFF647A86), Color(0xFF9AABB4)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.9f),
                    scrimAlpha = 0.30f,
                )
            }

        WeatherScene.DRIZZLE ->
            WeatherSceneStyle(
                scene = scene,
                // 蓝灰渐变加深（保持色相）再叠 0.25 黑scrim：
                // 白字对复合后最亮 stop 5.26:1、muted 0.9 白字 4.62:1
                gradient = listOf(Color(0xFF4F86AE), Color(0xFF3B6D95), Color(0xFF6096BB)),
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.9f),
                scrimAlpha = 0.25f,
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
                gradient = listOf(Color(0xFF5B7C8A), Color(0xFF3E5A66), Color(0xFF6C8FA0)),
                // 末端亮 stop 从 #7FA0AE 稍加深：白字由 2.78:1 → 3.46:1（大号/图标可辨）
                contentColor = Color.White,
                mutedContentColor = Color.White.copy(alpha = 0.9f),
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
                    // 浅雪蓝渐变适度加深（保持色相）再叠 0.35 黑scrim：
                    // 白字对复合后最亮 stop 3.97:1、muted 0.9 白字 3.55:1
                    gradient = listOf(Color(0xFFA9C9DE), Color(0xFF86B0CC), Color(0xFF679BC6)),
                    contentColor = Color.White,
                    mutedContentColor = Color.White.copy(alpha = 0.9f),
                    scrimAlpha = 0.35f,
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
