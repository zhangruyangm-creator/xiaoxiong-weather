package com.xiaoxiong.weather.game

/** 口令别名 → 当前主口令（旧名仍可进入）。 */
private val WHITE_TILES_PASSWORD_ALIASES =
    mapOf(
        EASTER_EGG_BEAR_RAIN_LEGACY to EASTER_EGG_BEAR_WHITE,
        EASTER_EGG_BEAR_TILES to EASTER_EGG_BEAR_WHITE,
    )

fun GameEntry.matchesGamePassword(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed == password) return true
    return WHITE_TILES_PASSWORD_ALIASES[trimmed] == password
}
