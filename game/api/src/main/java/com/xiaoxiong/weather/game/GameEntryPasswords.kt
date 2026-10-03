package com.xiaoxiong.weather.game

/** 口令别名 → 当前主口令（旧名仍可进入）。 */
private val TILES_PASSWORD_ALIASES =
    mapOf(
        EASTER_EGG_BEAR_RAIN_LEGACY to EASTER_EGG_BEAR_TILES,
    )

fun GameEntry.matchesGamePassword(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed == password) return true
    return TILES_PASSWORD_ALIASES[trimmed] == password
}
