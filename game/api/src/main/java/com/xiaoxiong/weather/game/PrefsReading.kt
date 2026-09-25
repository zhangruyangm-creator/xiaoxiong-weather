package com.xiaoxiong.weather.game

import android.content.SharedPreferences

/**
 * 容错的 SharedPreferences 读取。
 *
 * `SharedPreferences.getInt` 等在**类型不匹配**时抛 `ClassCastException`：把
 * `bear_game.xml` 里某个 `<int name="best">` 改成 `<string name="best">`（手改档、
 * 跨版本改键类型、旧迁移写错），进游戏时 `remember { prefs.intOr("best", 0) }`
 * 就会在组合期直接崩掉整app。
 *
 * 这些读取大量发生在 `remember` / 组合期，没有 try/catch 兜底，因此统一走这里：
 * 类型不符或解析失败一律回退默认值。正常存档的行为与直接调用完全一致。
 */
fun SharedPreferences.intOr(
    key: String,
    default: Int = 0,
): Int = runCatching { getInt(key, default) }.getOrDefault(default)

fun SharedPreferences.longOr(
    key: String,
    default: Long = 0L,
): Long = runCatching { getLong(key, default) }.getOrDefault(default)

fun SharedPreferences.floatOr(
    key: String,
    default: Float = 0f,
): Float = runCatching { getFloat(key, default) }.getOrDefault(default)

fun SharedPreferences.booleanOr(
    key: String,
    default: Boolean = false,
): Boolean = runCatching { getBoolean(key, default) }.getOrDefault(default)

fun SharedPreferences.stringOr(key: String): String? = runCatching { getString(key, null) }.getOrNull()

fun SharedPreferences.stringOr(
    key: String,
    default: String,
): String = runCatching { getString(key, default) }.getOrNull() ?: default
